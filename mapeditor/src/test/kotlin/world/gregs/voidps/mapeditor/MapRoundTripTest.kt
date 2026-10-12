package world.gregs.voidps.mapeditor

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import world.gregs.voidps.cache.Cache
import world.gregs.voidps.cache.CacheDelegate
import world.gregs.voidps.cache.Index
import world.gregs.voidps.cache.definition.data.MapDefinition
import world.gregs.voidps.cache.definition.encoder.MapObjectEncoder
import world.gregs.voidps.cache.definition.encoder.MapTileEncoder
import world.gregs.voidps.tools.map.MapDecoder
import world.gregs.voidps.type.Region

/**
 * Checks every map in the cache survives decoding and re-encoding byte for byte,
 * both directly and through the editable [RegionFile] text.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class MapRoundTripTest {

    private val cache: Cache = CacheDelegate("../data/cache/")
    private val definitions: List<MapDefinition> by lazy {
        val decoder = MapDecoder()
        decoder.modified = false
        decoder.load(cache).filter { it.id != -1 }
    }

    @Test
    fun `Re-encode all maps byte for byte`() {
        assertAllMatch { it }
    }

    @Test
    fun `Export and import all maps as text byte for byte`() {
        assertAllMatch { RegionFile.fromText(RegionFile.toText(it)) }
    }

    private fun assertAllMatch(transform: (MapDefinition) -> MapDefinition) {
        val mismatches = mutableListOf<String>()
        for (original in definitions) {
            val definition = transform(original)
            val region = Region(original.id)
            val terrain = RegionStore.terrain(region)
            if (!cache.data(Index.MAPS, terrain).contentEquals(RegionStore.encode(MapTileEncoder(), definition))) {
                mismatches.add(terrain)
            }
            val locations = RegionStore.locations(region)
            val objects = cache.data(Index.MAPS, locations) ?: continue
            if (!objects.contentEquals(RegionStore.encode(MapObjectEncoder(), definition))) {
                mismatches.add(locations)
            }
        }
        assertTrue(definitions.size > 1600, "Expected all maps, found ${definitions.size}")
        assertTrue(mismatches.isEmpty(), "${mismatches.size} maps differ: ${mismatches.take(10)}")
    }
}
