package content.skill.mining

import content.entity.combat.inCombat
import world.gregs.voidps.engine.Script
import world.gregs.voidps.engine.client.instruction.handle.interactObject
import world.gregs.voidps.engine.client.message
import world.gregs.voidps.engine.client.ui.dialogue
import world.gregs.voidps.engine.client.ui.menu
import world.gregs.voidps.engine.data.definition.Areas
import world.gregs.voidps.engine.data.definition.Tables
import world.gregs.voidps.engine.entity.character.mode.EmptyMode
import world.gregs.voidps.engine.entity.character.mode.interact.PlayerOnObjectInteract
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.obj.GameObject
import world.gregs.voidps.engine.entity.obj.GameObjects
import world.gregs.voidps.engine.entity.obj.ObjectLayer
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.engine.timer.Timer
import world.gregs.voidps.type.Tile

/**
 * "Auto-mine" on a rock inside an area tagged `auto_mining` (data/skill/mining/auto_mining.areas.toml) mines that rock
 * again whenever the player stands idle near it, until the inventory is full. When it runs out, the closest rock of
 * the same ore is mined instead, if there is one free. It only repeats the "Mine" click, so
 * experience, ore chance and speed are those of normal mining.
 *
 * The option comes from a copy of each rock the client switches to while [AREA] is set
 * (tools/.../cache/AutoMiningObjects.kt writes them to the cache).
 */
class AutoMining : Script {

    init {
        entered("*") { area ->
            if (area.tags.contains(TAG)) {
                set(AREA, true)
            }
        }

        exited("*") { area ->
            if (area.tags.contains(TAG) && !inArea(tile)) {
                clear(AREA)
                stopAutoMining(this)
            }
        }

        objectOperate("Auto-mine") { (target) ->
            if (get<GameObject>(ROCK) == target) {
                // Choosing the option replaced the mining in progress, so it stops too
                softTimers.stop("mining")
                stopAutoMining(this)
                return@objectOperate
            }
            if (!inArea(target.tile)) {
                message("You can't auto-mine here.")
                return@objectOperate
            }
            if (inventory.isFull()) {
                message(FULL)
                return@objectOperate
            }
            set(ROCK, target)
            // Mine on the next tick rather than after standing still
            set(IDLE, IDLE_TICKS - 1)
            softTimers.start(TIMER)
            message(ON)
        }

        timerStart(TIMER) { 1 }

        timerTick(TIMER) {
            val rock: GameObject = get(ROCK) ?: return@timerTick Timer.CANCEL
            if (!inArea(tile) || inCombat) {
                return@timerTick stop(this)
            }
            val action = mode
            if (action is PlayerOnObjectInteract && action.target == rock) {
                set(IDLE, 0)
                set(MINING, true)
                return@timerTick Timer.CONTINUE
            }
            if (action != EmptyMode) {
                return@timerTick stop(this)
            }
            if (inventory.isFull()) {
                // Mining says so itself when it stops on a full inventory, not when the last ore also emptied the rock
                if (!get(MINING, false) || !GameObjects.contains(rock)) {
                    message(FULL)
                }
                return@timerTick stop(this, quiet = true)
            }
            clear(MINING)
            if (!GameObjects.contains(rock) && menu == null && dialogue == null) {
                // Move on to the nearest rock of the same ore rather than waiting for this one to come back
                val next = nextRock(this, rock)
                if (next != null) {
                    set(ROCK, next)
                    set(IDLE, 0)
                    interactObject(next, "Mine")
                    return@timerTick Timer.CONTINUE
                }
            }
            if (!GameObjects.contains(rock) || !tile.within(rock.tile, 3) || menu != null || dialogue != null) {
                set(IDLE, 0)
                return@timerTick Timer.CONTINUE
            }
            if (inc(IDLE) >= IDLE_TICKS) {
                set(IDLE, 0)
                interactObject(rock, "Mine")
            }
            Timer.CONTINUE
        }

        timerStop(TIMER) {
            clear(ROCK)
            clear(IDLE)
            clear(MINING)
        }
    }

    /**
     * The closest rock to [player] with the same ores as [rock] which isn't depleted, within [NEXT_ROCK_RANGE] tiles of it
     */
    private fun nextRock(player: Player, rock: GameObject): GameObject? {
        val ores = Tables.itemListOrNull("rocks.${rock.id}.ores") ?: return null
        var closest: GameObject? = null
        var distance = Int.MAX_VALUE
        for (x in rock.tile.x - NEXT_ROCK_RANGE..rock.tile.x + NEXT_ROCK_RANGE) {
            for (y in rock.tile.y - NEXT_ROCK_RANGE..rock.tile.y + NEXT_ROCK_RANGE) {
                val tile = Tile(x, y, rock.tile.level)
                val other = GameObjects.getLayer(tile, ObjectLayer.GROUND) ?: continue
                if (other == rock || Tables.itemListOrNull("rocks.${other.id}.ores") != ores || !inArea(tile)) {
                    continue
                }
                val between = player.tile.distanceTo(tile)
                if (between < distance) {
                    closest = other
                    distance = between
                }
            }
        }
        return closest
    }

    private fun stop(player: Player, quiet: Boolean = false): Int {
        player.clear(ROCK)
        if (!quiet) {
            player.message(OFF)
        }
        return Timer.CANCEL
    }

    private fun stopAutoMining(player: Player) {
        if (player.contains(ROCK)) {
            player.softTimers.stop(TIMER)
            player.message(OFF)
        }
    }

    companion object {
        const val TAG = "auto_mining"
        const val AREA = "auto_mining_area"
        private const val ROCK = "auto_mining_rock"
        private const val IDLE = "auto_mining_idle"
        private const val MINING = "auto_mining_mining"
        private const val TIMER = "auto_mining"
        private const val FULL = "Your inventory is too full to hold any more ore."
        private const val ON = "<green>Auto-mining on."
        private const val OFF = "<orange>Auto-mining off."

        /** How far from a depleted rock to look for another one of the same ore */
        private const val NEXT_ROCK_RANGE = 8

        /** Ticks standing still before mining again once the rock is back (about 3 seconds) */
        const val IDLE_TICKS = 5

        fun inArea(tile: Tile): Boolean = Areas.tagged(TAG).any { tile in it.area }
    }
}
