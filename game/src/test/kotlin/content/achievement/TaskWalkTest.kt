package content.achievement

import WorldTest
import containsMessage
import content.entity.player.dialogue.translate
import content.entity.player.modal.map.MarkerWalk
import interfaceOption
import messages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.pathfinder.flag.CollisionFlag
import walk
import world.gregs.voidps.engine.client.ui.open
import world.gregs.voidps.engine.data.definition.EnumDefinitions
import world.gregs.voidps.engine.data.definition.StructDefinitions
import world.gregs.voidps.engine.entity.character.mode.move.target.CharacterTargetStrategy
import world.gregs.voidps.engine.entity.character.move.running
import world.gregs.voidps.engine.entity.character.npc.NPCs
import world.gregs.voidps.engine.map.collision.Collisions
import world.gregs.voidps.type.Tile

class TaskWalkTest : WorldTest() {

    override var loadNpcs: Boolean = true

    private fun index(task: String): Int {
        val structs = EnumDefinitions.get("task_structs")
        return (0 until 5000).first { structs.intOrNull(it)?.let { id -> StructDefinitions.getOrNull(id)?.stringId } == task }
    }

    @Test
    fun `Map icon of Explorer Jack's hint walks next to him`() {
        val player = createPlayer(Tile(3222, 3218))
        player.running = true
        player.open("task_system")
        tick()
        // Talk to Explorer Jack is pinned in the first slot until Unstable Foundations is done
        assertEquals(3520, player["task_pinned", -1])

        player.interfaceOption("task_system", "hint_1", "Hint")
        tick(80)

        val jack = NPCs.first { it.id == "explorer_jack" }
        assertTrue(CharacterTargetStrategy(jack).reached(player), "Stopped at ${player.tile}, Jack at ${jack.tile}")
        assertFalse(player.messages.any { it.contains("couldn't reach") }, "Messages: ${player.messages}")
    }

    @Test
    fun `Hint without an npc doesn't walk`() {
        val player = createPlayer(Tile(3222, 3218))

        assertFalse(TaskWalk.walk(player, index("shrimpin_aint_easy_task"), 1))
        // Another line of a task with an npc
        assertFalse(TaskWalk.walk(player, index("doom_task"), 1))
        assertFalse(player.mode is MarkerWalk)
    }

    @Test
    fun `Npc on another floor is walked under and told`() {
        val player = createPlayer(Tile(3222, 3218))
        player.running = true

        assertTrue(TaskWalk.walk(player, index("hail_to_the_duke_baby_task"), 1))
        tick(40)

        val duke = NPCs.first { it.id == "duke_horacio" }
        assertEquals(0, player.tile.level)
        assertTrue(player.tile.within(duke.tile.copy(level = 0), 12), "Stopped at ${player.tile}, Duke at ${duke.tile}")
        assertTrue(player.containsMessage("You couldn't reach Duke Horacio, who is on another floor."))
    }

    @Test
    fun `Walks far, around the Al Kharid toll gate`() {
        // Ellis is past the toll gate, which only opens by paying, so the way goes round the north of Al Kharid
        val player = createPlayer(Tile(3222, 3218))
        player.running = true

        assertTrue(TaskWalk.walk(player, index("tan_your_hide_task"), 1))
        tick(300)

        val ellis = NPCs.first { it.id == "ellis" }
        assertTrue(CharacterTargetStrategy(ellis).reached(player), "Stopped at ${player.tile}, Ellis at ${ellis.tile}")
    }

    @Test
    fun `Walks up to a closed way and tells it's blocked`() {
        val player = createPlayer(Tile(3222, 3218))
        player.running = true
        tick()
        val jack = NPCs.first { it.id == "explorer_jack" }
        val ring = jack.tile.toCuboid(4).filter { it.x == jack.tile.x - 4 || it.x == jack.tile.x + 4 || it.y == jack.tile.y - 4 || it.y == jack.tile.y + 4 }
        for (tile in ring) {
            Collisions.add(tile.x, tile.y, tile.level, CollisionFlag.FLOOR)
        }
        try {
            assertTrue(TaskWalk.walk(player, index("introducing_explorer_jack_task"), 1))
            tick(60)

            assertFalse(CharacterTargetStrategy(jack).reached(player))
            assertTrue(player.tile.within(jack.tile, 15), "Stopped at ${player.tile}, Jack at ${jack.tile}")
            assertTrue(player.containsMessage("You couldn't reach Explorer Jack: the way is blocked."), "Messages: ${player.messages}")
        } finally {
            for (tile in ring) {
                Collisions.remove(tile.x, tile.y, tile.level, CollisionFlag.FLOOR)
            }
        }
    }

    @Test
    fun `Walks to any npc by id`() {
        val player = createPlayer(Tile(3222, 3218))
        player.running = true

        TaskWalk.walkTo(player, "cook_lumbridge", Tile(3209, 3215), "the Cook", "quest:cooks_assistant")
        tick(40)

        val cook = NPCs.first { it.id == "cook_lumbridge" }
        assertTrue(CharacterTargetStrategy(cook).reached(player), "Stopped at ${player.tile}, Cook at ${cook.tile}")
    }

    @Test
    fun `Clicking somewhere else stops the walk`() {
        val player = createPlayer(Tile(3222, 3218))
        assertTrue(TaskWalk.walk(player, index("introducing_explorer_jack_task"), 1))
        tick(3)

        val tile = player.tile.addY(-2)
        player.walk(tile)
        tick(15)

        assertTrue(player.tile.distanceTo(tile) <= 1, "Stopped at ${player.tile}")
        assertFalse(player.softTimers.contains("task_walk"))
        assertTrue(player.messages.none { it.contains("Explorer Jack") }, "Messages: ${player.messages}")
    }

    @Test
    fun `Messages read in Portuguese`() {
        val player = createPlayer(Tile(3222, 3218))
        player["portuguese_dialogue"] = true

        assertEquals("Não deu para chegar até o Duke Horacio, que está em outro andar.", player.translate("You couldn't reach o Duke Horacio, who is on another floor."))
        assertEquals("Não deu para chegar até o Explorer Jack: o caminho está fechado.", player.translate("You couldn't reach o Explorer Jack: the way is blocked."))
        assertEquals("Você não encontrou um caminho a pé até o Ellis.", player.translate("You can't find a way to walk to o Ellis."))
        assertEquals("É aqui que o Town Crier costuma ficar.", player.translate("This is where you'd usually find o Town Crier."))
    }
}
