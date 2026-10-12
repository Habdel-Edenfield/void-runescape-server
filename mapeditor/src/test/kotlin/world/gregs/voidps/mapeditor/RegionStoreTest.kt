package world.gregs.voidps.mapeditor

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import world.gregs.voidps.cache.Index
import world.gregs.voidps.cache.definition.data.MapObject
import world.gregs.voidps.type.Region
import java.io.File

internal class RegionStoreTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `Write a region into a copy of the cache`() {
        File("../data/cache/").canonicalFile.copyRecursively(dir)
        val lumbridge = Region(12850)
        val neighbour = Region(12851)
        val store = RegionStore(dir.path)
        val referenceTable = store.cache.sector(255, Index.MAPS)!!.copyOf()
        val untouched = store.cache.data(Index.MAPS, RegionStore.terrain(neighbour))!!.copyOf()
        val edited = store.load(lumbridge)!!
        edited.objects.add(MapObject(1276, 10, 20, 0, 10, 0))

        store.write(edited)

        val reopened = RegionStore(dir.path)
        val loaded = reopened.load(lumbridge)!!
        assertEquals(edited.objects.size, loaded.objects.size)
        assertArrayEquals(reopened.encodeObjects(edited), reopened.cache.data(Index.MAPS, RegionStore.locations(lumbridge)))
        assertArrayEquals(untouched, reopened.cache.data(Index.MAPS, RegionStore.terrain(neighbour)))
        assertFalse(referenceTable.contentEquals(reopened.cache.sector(255, Index.MAPS)), "Index reference table should change so clients re-download")
    }
}
