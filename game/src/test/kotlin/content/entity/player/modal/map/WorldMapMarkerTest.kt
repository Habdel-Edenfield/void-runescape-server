package content.entity.player.modal.map

import WorldTest
import interfaceOption
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.client.ui.open
import world.gregs.voidps.network.client.instruction.WorldMapClick
import world.gregs.voidps.network.login.protocol.encode.HintArrow
import world.gregs.voidps.network.login.protocol.encode.HintType
import world.gregs.voidps.network.login.protocol.encode.arrowHint
import world.gregs.voidps.type.Tile

class WorldMapMarkerTest : WorldTest() {

    @BeforeEach
    fun setup() {
        mockkStatic("world.gregs.voidps.network.login.protocol.encode.HintEncoderKt")
    }

    @Test
    fun `Double clicking the world map points the minimap at the marker`() = runTest {
        val (player, client) = createClient("player", Tile(3222, 3218))
        val marker = Tile(2965, 3380)

        player.instructions.send(WorldMapClick(marker.id))
        player.instructions.send(WorldMapClick(marker.id))
        tick()

        assertEquals(marker.id, player["world_map_marker_custom", 0])
        assertEquals(HintType.TILE, player.viewport!!.hints[6])
        verify {
            client.arrowHint(HintType.TILE, 6, HintArrow.BLUE_MINIMAP, 0, marker.x, marker.y, marker.level, 0, any(), any())
        }
    }

    @Test
    fun `Clearing the marker removes the minimap arrow`() = runTest {
        val (player, client) = createClient("player", Tile(3222, 3218))
        player["world_map_marker_custom"] = Tile(2965, 3380).id
        player.open("world_map")

        player.interfaceOption("world_map", "marker", "Clear marker")
        tick()

        assertEquals(0, player.viewport!!.hints[6])
        verify {
            client.arrowHint(HintType.CLEAR, 6, any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `Marker arrow comes back after logging in`() {
        val marker = Tile(2965, 3380)
        val player = createPlayer(Tile(3222, 3218)) {
            it["world_map_marker_custom"] = marker.id
            it.viewport!!.hints[6] = 0
        }

        assertEquals(HintType.TILE, player.viewport!!.hints[6])
    }
}
