package content.achievement

import content.entity.player.modal.map.MarkerWalk
import world.gregs.voidps.engine.Script
import world.gregs.voidps.engine.client.message
import world.gregs.voidps.engine.client.ui.closeInterfaces
import world.gregs.voidps.engine.data.definition.EnumDefinitions
import world.gregs.voidps.engine.data.definition.StructDefinitions
import world.gregs.voidps.engine.data.definition.Tables
import world.gregs.voidps.engine.entity.character.mode.EmptyMode
import world.gregs.voidps.engine.entity.character.mode.move.target.CharacterTargetStrategy
import world.gregs.voidps.engine.entity.character.mode.move.target.TileTargetStrategy
import world.gregs.voidps.engine.entity.character.npc.NPC
import world.gregs.voidps.engine.entity.character.npc.NPCs
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.timer.Timer
import world.gregs.voidps.type.Tile
import world.gregs.voidps.engine.entity.character.mode.move.Movement as MovementMode

/**
 * The map icon on a Task System tab hint walks the player to the task's NPC (`task_npcs` table): over long
 * distances with [MarkerWalk], the same walk as world map markers which opens doors on the way, then next to the NPC
 * wherever it's moved to. Clicking anywhere else stops it, like any walk.
 * The cache shows the icon and its tooltip (`TaskWalkHint` in the cache module).
 * Anything else that sends the player to an npc can use [walkTo].
 */
class TaskWalk : Script {

    init {
        timerStart(TIMER) { 1 }

        timerTick(TIMER) {
            val walk: Walk = get(KEY) ?: return@timerTick Timer.CANCEL
            val opening: MarkerWalk.Opening? = get(MarkerWalk.OPENING)
            val mode = mode
            when {
                mode is MarkerWalk && mode.marker == walk.marker -> Timer.CONTINUE
                // Opening a door on the way, the marker walk carries on after
                opening != null && opening.walk.marker == walk.marker && softTimers.contains(MarkerWalk.OPENING) -> Timer.CONTINUE
                walk.approach != null && mode === walk.approach -> Timer.CONTINUE
                mode != EmptyMode -> Timer.CANCEL
                walk.approach == null && approach(this, walk) -> Timer.CONTINUE
                else -> {
                    finish(this, walk)
                    Timer.CANCEL
                }
            }
        }

        timerStop(TIMER) {
            clear(KEY)
        }
    }

    /** A walk to [npc], or to [spawn] when it isn't around, towards [marker] on the player's floor */
    class Walk(val npc: NPC?, val spawn: Tile, val marker: Tile, val name: String) {
        var approach: MovementMode? = null
    }

    companion object {
        private const val TIMER = "task_walk"
        private const val KEY = "task_walk"
        private const val TABLE = "task_npcs"

        // The marker set by the last walk, and what started it, so a second click can clear it
        private const val MARKER = "task_walk_marker"
        private const val MARKER_HINT = "task_walk_marker_hint"

        // Close enough to the npc for the normal path finder to go the rest of the way
        private const val NEAR = 12

        /**
         * Walks [player] to the npc of hint [line] of task [index], if that hint has one.
         * @return false when the hint doesn't lead to an npc
         */
        fun walk(player: Player, index: Int, line: Int): Boolean {
            val struct = EnumDefinitions.get("task_structs").intOrNull(index) ?: return false
            val row = "$TABLE.${StructDefinitions.getOrNull(struct)?.stringId ?: return false}"
            if (Tables.intOrNull("$row.hint") != line) {
                return false
            }
            val name = Tables.string(if (player["portuguese_dialogue", false]) "$row.pt" else "$row.name")
            walkTo(player, Tables.npc("$row.npc"), Tables.tile("$row.tile"), name, "task:$index:$line")
            return true
        }

        /**
         * Walks [player] next to the nearest [npcId] npc, wherever it is when they get close, or to [spawn] when it
         * isn't around, marking the way on the minimap. [name] is how messages call the npc, in the player's language
         * ("Explorer Jack", "o Explorer Jack"). Calling again with the same [source] (the hint or line clicked) while
         * its marker is still set clears it and stops instead.
         */
        fun walkTo(player: Player, npcId: String, spawn: Tile, name: String, source: String) {
            if (player.delayed) {
                return
            }
            val npc = nearest(player, npcId)
            val target = npc?.tile ?: spawn
            player.closeInterfaces()
            player.softTimers.stop(MarkerWalk.OPENING)
            player.softTimers.stop(TIMER)
            val marked = player[MARKER, 0]
            if (marked != 0 && marked == player["world_map_marker_custom", 0] && player[MARKER_HINT, ""] == source) {
                // Clicking the same hint again clears its minimap arrow and stops the walk, like the hints without an npc
                player["world_map_marker_custom"] = 0
                return
            }
            val walk = Walk(npc, spawn, target.copy(level = player.tile.level), name)
            // The world map marker shows the blue minimap arrow and does the long walk, opening doors on the way
            player["world_map_marker_custom"] = 0
            player["world_map_marker_custom"] = walk.marker.id
            player[MARKER] = walk.marker.id
            player[MARKER_HINT] = source
            val mode = player.mode
            if (player.tile.distanceTo(walk.marker) > NEAR) {
                if (mode !is MarkerWalk || mode.marker != walk.marker) {
                    // No way there, the world map walk has told the player
                    return
                }
            } else {
                player.mode = EmptyMode
            }
            player[KEY] = walk
            player.softTimers.start(TIMER)
        }

        /** The [id] npc nearest [player], on their floor first */
        private fun nearest(player: Player, id: String): NPC? = NPCs
            .filter { it.id == id && !it.hide }
            .minByOrNull { (if (it.tile.level == player.tile.level) 0 else 1_000_000) + it.tile.distanceTo(player.tile.copy(level = it.tile.level)) }

        /** Walks the rest of the way next to the npc, or onto its spawn when it isn't around, when close by on the same floor */
        private fun approach(player: Player, walk: Walk): Boolean {
            val npc = walk.npc
            val strategy = if (npc == null || npc.hide) TileTargetStrategy(walk.spawn) else CharacterTargetStrategy(npc)
            if (strategy.tile.level != player.tile.level || !player.tile.within(strategy.tile, NEAR * 2) || strategy.reached(player)) {
                return false
            }
            val approach = MovementMode(player, strategy)
            walk.approach = approach
            player.mode = approach
            return true
        }

        private fun finish(player: Player, walk: Walk) {
            val npc = walk.npc
            when {
                npc != null && !npc.hide && CharacterTargetStrategy(npc).reached(player) -> player.face(npc.tile)
                npc != null && !npc.hide && npc.tile.level != player.tile.level -> player.message("You couldn't reach ${walk.name}, who is on another floor.")
                (npc == null || npc.hide) && walk.spawn.level != player.tile.level -> player.message("You couldn't reach ${walk.name}, who is on another floor.")
                (npc == null || npc.hide) && player.tile.within(walk.spawn, 2) -> player.message("This is where you'd usually find ${walk.name}.")
                else -> player.message("You couldn't reach ${walk.name}: the way is blocked.")
            }
        }
    }
}
