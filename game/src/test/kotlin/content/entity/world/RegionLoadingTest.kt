package content.entity.world

import WorldTest
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.entity.character.move.tele
import world.gregs.voidps.network.client.Client
import world.gregs.voidps.network.login.protocol.encode.mapRegion
import world.gregs.voidps.type.Tile
import kotlin.test.assertEquals

class RegionLoadingTest : WorldTest() {

    @BeforeEach
    fun setup() {
        mockkStatic("world.gregs.voidps.network.login.protocol.encode.MapRegionEncodersKt")
    }

    @Test
    fun `Players are sent the map size from settings`() {
        val player = createPlayer(Tile(3221, 3219))

        assertEquals(168, player.viewport!!.tileSize)
    }

    @Test
    fun `Map reloads after moving more than four zones from the last load`() {
        val player = createPlayer(Tile(3221, 3219))
        val client: Client = mockk(relaxed = true)
        player.client = client
        player.viewport!!.lastLoadZone = player.tile.zone

        player.tele(Tile(3253, 3219))
        tick()

        verify(exactly = 0) {
            client.mapRegion(any(), any(), any(), any(), any(), any(), any(), any())
        }

        player.tele(Tile(3261, 3219))
        tick()

        verify {
            client.mapRegion(407, 402, false, 3, any(), null, null, null)
        }
    }
}
