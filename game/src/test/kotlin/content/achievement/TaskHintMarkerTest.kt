package content.achievement

import WorldTest
import content.entity.player.modal.map.MarkerWalk
import interfaceOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.client.ui.hasOpen
import world.gregs.voidps.engine.client.ui.open
import world.gregs.voidps.engine.data.definition.EnumDefinitions
import world.gregs.voidps.type.Tile

class TaskHintMarkerTest : WorldTest() {

    @Test
    fun `Map icon of a hint without an npc marks the minimap and walks there`() {
        val player = createPlayer(Tile(3222, 3218))
        player.open("task_system")
        tick()
        // Master of All I Survey: its hint points at a place, not an npc
        val slot = (1..6).first { player["task_slot_$it", -1] == 64 }
        player["task_slot_selected"] = slot
        val tile: Int = EnumDefinitions.getStructOrNull("task_structs", 64, "task_hint_tile_1")!!

        player.interfaceOption("task_system", "hint_1", "Hint")

        assertEquals(tile, player["world_map_marker_custom", 0])
        assertFalse(player.hasOpen("world_map"))
        assertTrue(player.mode is MarkerWalk)

        player.interfaceOption("task_system", "hint_1", "Hint")

        assertEquals(0, player["world_map_marker_custom", 0])
        assertFalse(player.mode is MarkerWalk)
    }
}
