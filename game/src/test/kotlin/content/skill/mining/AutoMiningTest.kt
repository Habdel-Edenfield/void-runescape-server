package content.skill.mining

import WorldTest
import containsMessage
import objectOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import walk
import world.gregs.voidps.engine.data.definition.Areas
import world.gregs.voidps.engine.entity.character.move.tele
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.entity.obj.GameObject
import world.gregs.voidps.engine.entity.obj.GameObjects
import world.gregs.voidps.engine.entity.obj.ObjectLayer
import world.gregs.voidps.engine.inv.add
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.network.login.protocol.encode.HintType
import world.gregs.voidps.type.Tile
import world.gregs.voidps.type.area.Rectangle
import world.gregs.voidps.type.setRandom
import kotlin.random.Random

internal class AutoMiningTest : WorldTest() {

    private var rock = GameObject(0, Tile.EMPTY, 0, 0)
    private var standing = Tile.EMPTY

    @BeforeEach
    fun findRock() {
        setRandom(Random)
        // The copper rocks of Lumbridge Swamp, in data/skill/mining/auto_mining.areas.toml
        val area = Areas.tagged("auto_mining").first { Tile(3229, 3146) in it.area }.area
        rock = area.firstNotNullOf { tile -> GameObjects.getLayer(tile, ObjectLayer.GROUND)?.takeIf { it.id.startsWith("copper_rocks") } }
        standing = listOf(rock.tile.addX(-1), rock.tile.addX(1), rock.tile.addY(-1), rock.tile.addY(1))
            .first { it in area && GameObjects.getLayer(it, ObjectLayer.GROUND) == null }
    }

    private fun miner(spaces: Int = 3): Player {
        val player = createPlayer(standing)
        player.levels.set(Skill.Mining, 99)
        player.inventory.add("bronze_pickaxe")
        player.inventory.add("logs", player.inventory.spaces - spaces)
        return player
    }

    @Test
    fun `Auto-mine mines the same rock until the inventory is full`() {
        val player = miner(spaces = 3)
        assertTrue(rock.def(player).options!!.contains("Auto-mine"))

        player.objectOption(rock, "Auto-mine")
        tick()
        assertTrue(player.containsMessage("Auto-mining on"))
        tickIf(limit = 1000) { !player.inventory.isFull() }

        tick(10)
        assertEquals(3, player.inventory.count("copper_ore"))
        assertTrue(player.containsMessage("Your inventory is too full to hold any more ore."))
        assertFalse(player.softTimers.contains("auto_mining"))
    }

    @Test
    fun `Auto-mine starts mining straight away`() {
        val player = miner()
        player.objectOption(rock, "Auto-mine")
        tick(3)
        assertTrue(player.softTimers.contains("mining"))
    }

    @Test
    fun `Auto-mine messages are short and coloured in Portuguese`() {
        val player = miner()
        player["portuguese_dialogue"] = true
        player.objectOption(rock, "Auto-mine")
        tick()
        player.objectOption(rock, "Auto-mine")
        tick(2)

        assertTrue(player.containsMessage("<green>Mineração automática ligada."))
        assertTrue(player.containsMessage("<orange>Mineração automática desligada."))
    }

    @Test
    fun `Auto-mine moves on to the next rock of the same ore when it runs out`() {
        val player = miner(spaces = 10)
        player.objectOption(rock, "Auto-mine")
        tickIf(limit = 200) { GameObjects.contains(rock) }
        tick(5)

        val next: GameObject = player["auto_mining_rock"]!!
        assertNotEquals(rock, next)
        assertTrue(next.id.startsWith("copper_rocks"))
        assertTrue(player.softTimers.contains("mining"))
        assertEquals(HintType.TILE, player.viewport!!.hints[1])
    }

    @Test
    fun `Auto-mine marks the rock with an arrow until turned off`() {
        val player = miner()
        player.objectOption(rock, "Auto-mine")
        tick()
        assertEquals(HintType.TILE, player.viewport!!.hints[1])

        player.objectOption(rock, "Auto-mine")
        tick(2)
        assertEquals(0, player.viewport!!.hints[1])
    }

    @Test
    fun `Auto-mine stops on leaving the area`() {
        val player = miner()
        player.objectOption(rock, "Auto-mine")
        tick(2)

        player.tele(3222, 3218)
        tick(2)

        assertTrue(player.containsMessage("Auto-mining off."))
        assertFalse(player.softTimers.contains("auto_mining"))
        assertFalse(rock.def(player).options!!.contains("Auto-mine"))
    }

    @Test
    fun `Auto-mine stops on walking away`() {
        val player = miner()
        player.objectOption(rock, "Auto-mine")
        tick(2)

        player.walk(standing.add(standing.delta(rock.tile)))
        tick(2)

        assertTrue(player.containsMessage("Auto-mining off."))
        assertFalse(player.softTimers.contains("auto_mining"))
    }

    @Test
    fun `Choosing Auto-mine again turns it off`() {
        val player = miner()
        player.objectOption(rock, "Auto-mine")
        tick(2)
        player.objectOption(rock, "Auto-mine")
        tick(2)

        assertTrue(player.containsMessage("Auto-mining off."))
        assertFalse(player.softTimers.contains("auto_mining"))
        tick(10)
        assertFalse(player.softTimers.contains("mining"))
    }

    @Test
    fun `Every mine has the Auto-mine option`() {
        // Varrock south east mine
        val rocks = Rectangle(3280, 3360, 3292, 3372).firstNotNullOf { tile -> GameObjects.getLayer(tile, ObjectLayer.GROUND)?.takeIf { it.id.startsWith("copper_rocks") } }
        val player = createPlayer(rocks.tile.addY(-1))
        assertTrue(rocks.def(player).options!!.contains("Auto-mine"))
    }

    @Test
    fun `Rocks outside the areas refuse Auto-mine`() {
        val player = miner()
        // A rock placed away from every mine, seen from inside one
        val far = createObject(rock.id, Tile(3215, rock.tile.y))
        player.objectOption(far, "Auto-mine")
        tickIf(limit = 200) { !player.containsMessage("You can't auto-mine here.") }
        assertFalse(player.softTimers.contains("auto_mining"))
    }

    @Test
    fun `Tutorial Island has no Auto-mine option`() {
        assertFalse(AutoMining.inArea(Tile(3076, 9504))) // Mining instructor's cave
    }
}
