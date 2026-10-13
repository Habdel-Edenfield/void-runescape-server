package content.entity.player.modal.map

import WorldTest
import containsMessage
import interfaceOption
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import walk
import world.gregs.voidps.engine.client.ui.open
import world.gregs.voidps.engine.entity.character.move.running
import world.gregs.voidps.engine.entity.obj.GameObjects
import world.gregs.voidps.engine.entity.obj.ObjectLayer
import world.gregs.voidps.engine.entity.obj.ObjectShape
import world.gregs.voidps.network.client.instruction.Walk
import world.gregs.voidps.network.client.instruction.WorldMapClick
import world.gregs.voidps.type.Direction
import world.gregs.voidps.type.Tile
import world.gregs.voidps.type.area.Rectangle

class WorldMapWalkTest : WorldTest() {

    @Test
    fun `Setting a marker walks the player to it`() = runTest {
        val player = createPlayer(Tile(3222, 3218))
        player.running = true
        val marker = Tile(3093, 3245) // Draynor Village

        player.instructions.send(WorldMapClick(marker.id))
        player.instructions.send(WorldMapClick(marker.id))
        tick()
        assertTrue(player.mode is MarkerWalk)

        tick(150)

        assertTrue(player.tile.distanceTo(marker) <= 1, "Stopped at ${player.tile}")
    }

    @Test
    fun `Walk to a marker further than one search reaches`() {
        val player = createPlayer(Tile(3222, 3218))
        player.running = true
        val marker = Tile(2965, 3380) // Falador

        player["world_map_marker_custom"] = marker.id
        tick(300)

        assertTrue(player.tile.distanceTo(marker) <= 1, "Stopped at ${player.tile}")
    }

    @Test
    fun `Clicking far away on a zoomed out minimap walks there`() = runTest {
        val player = createPlayer(Tile(3222, 3218))
        player.running = true
        val tile = Tile(3093, 3245)

        player.instructions.send(Walk(tile.x, tile.y, minimap = true))
        tick(150)

        assertTrue(player.tile.distanceTo(tile) <= 1, "Stopped at ${player.tile}")
    }

    @Test
    fun `Doors on the way are opened`() {
        // A closed door in Lumbridge, walked through from its own tile to a few tiles past it
        val door = Rectangle(3200, 3200, 3240, 3240).firstNotNullOf { tile -> GameObjects.getLayer(tile, ObjectLayer.WALL)?.takeIf { MarkerWalk.openable(it) && it.shape == ObjectShape.WALL_STRAIGHT } }
        val side = Direction.cardinal[(door.rotation + 3) and 0x3]
        val player = createPlayer(door.tile)
        val marker = door.tile.add(side.delta.x * 3, side.delta.y * 3)

        player["world_map_marker_custom"] = marker.id
        tick(20)

        assertTrue(player.tile.distanceTo(marker) <= 1, "Stopped at ${player.tile} going from ${door.tile} to $marker through ${door.id}")
        assertFalse(GameObjects.getLayer(door.tile, ObjectLayer.WALL)?.id == door.id && GameObjects.contains(door), "Door ${door.id} still closed")
    }

    @Test
    fun `Walking somewhere else stops the walk to the marker`() {
        val player = createPlayer(Tile(3222, 3218))
        player["world_map_marker_custom"] = Tile(3093, 3245).id
        tick(5)

        val tile = player.tile.addY(-2)
        player.walk(tile)
        tick(10)

        assertFalse(player.mode is MarkerWalk)
        assertTrue(player.tile.distanceTo(tile) <= 1, "Stopped at ${player.tile}")
    }

    @Test
    fun `Clearing the marker stops the walk`() {
        val player = createPlayer(Tile(3222, 3218))
        player["world_map_marker_custom"] = Tile(3093, 3245).id
        player.open("world_map")
        tick(5)

        player.interfaceOption("world_map", "marker", "Clear marker")
        val tile = player.tile
        tick(5)

        assertFalse(player.mode is MarkerWalk)
        assertTrue(player.tile == tile)
    }

    @Test
    fun `Marker on another floor can't be walked to`() {
        val player = createPlayer(Tile(3222, 3218))

        player["world_map_marker_custom"] = Tile(3093, 3245, 1).id

        assertFalse(player.mode is MarkerWalk)
        assertTrue(player.containsMessage("You can't find a way to walk to your marker."))
    }
}
