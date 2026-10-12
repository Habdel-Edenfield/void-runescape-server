package world.gregs.voidps.mapeditor

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import world.gregs.voidps.cache.CacheDelegate
import world.gregs.voidps.cache.Index
import world.gregs.voidps.type.Region
import java.io.File
import java.util.Base64

internal class MapEditorServerTest {

    @TempDir
    lateinit var dir: File

    private val region = Region(12850)

    private fun server(): Pair<MapEditorServer, RegionStore> {
        File("../data/cache/").canonicalFile.copyRecursively(dir)
        val work = RegionStore(dir.path)
        return MapEditorServer("../data/cache/", work, dir.path) to work
    }

    @Test
    fun `Edit tiles and objects then revert`() {
        val (server, work) = server()
        val original = work.load(region)!!
        val objects = original.objects.map { MapEditorServer.ObjectEdit(it.id, it.x, it.y, it.level, it.shape, it.rotation) }.drop(1) +
            MapEditorServer.ObjectEdit(1276, 10, 20, 0, 10, 2)
        val tile = MapEditorServer.TileEdit(0, 5, 6, height = 40, overlay = 10, overlayShape = 1, overlayRotation = 2, settings = 1, underlay = 50)

        val data = server.edit(region, MapEditorServer.RegionEdit(listOf(tile), objects))

        val edited = RegionStore(dir.path).load(region)!!
        assertEquals(original.objects.size, edited.objects.size)
        assertEquals(1, edited.objects.count { it.id == 1276 && it.x == 10 && it.y == 20 && it.rotation == 2 })
        val saved = edited.getTile(5, 6, 0)
        assertEquals(listOf(40, 10, 1, 2, 1, 50), listOf(saved.height, saved.overlayId, saved.overlayPath, saved.overlayRotation, saved.settings, saved.underlayId))
        assertArrayEquals(original.extras, edited.extras)
        assertArrayEquals(work.cache.data(Index.MAPS, RegionStore.terrain(region)), Base64.getDecoder().decode(data.terrain))

        server.revert(region)

        val live = CacheDelegate("../data/cache/")
        val reverted = RegionStore(dir.path).cache
        assertArrayEquals(live.data(Index.MAPS, RegionStore.terrain(region)), reverted.data(Index.MAPS, RegionStore.terrain(region)))
        assertArrayEquals(live.data(Index.MAPS, RegionStore.locations(region)), reverted.data(Index.MAPS, RegionStore.locations(region)))
    }

    @Test
    fun `Reject objects outside the region`() {
        val (server, _) = server()
        assertThrows<IllegalArgumentException> {
            server.edit(region, MapEditorServer.RegionEdit(objects = listOf(MapEditorServer.ObjectEdit(1276, 64, 0, 0, 10, 0))))
        }
    }
}
