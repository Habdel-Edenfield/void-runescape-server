package world.gregs.voidps.mapeditor

import world.gregs.voidps.buffer.write.ArrayWriter
import world.gregs.voidps.cache.Cache
import world.gregs.voidps.cache.CacheDelegate
import world.gregs.voidps.cache.DefinitionEncoder
import world.gregs.voidps.cache.Index
import world.gregs.voidps.cache.definition.data.MapDefinition
import world.gregs.voidps.cache.definition.encoder.MapObjectEncoder
import world.gregs.voidps.cache.definition.encoder.MapTileEncoder
import world.gregs.voidps.tools.map.MapDecoder
import world.gregs.voidps.type.Region

/**
 * Reads and writes single map regions ("m" terrain and "l" location archives) in a cache.
 * Maps in the void cache are stored without xtea encryption, so they're written without it too.
 */
class RegionStore(val cache: CacheDelegate) {

    constructor(path: String) : this(CacheDelegate(path))

    fun load(region: Region): MapDefinition? = load(cache, region)

    fun encodeTiles(definition: MapDefinition): ByteArray = encode(MapTileEncoder(), definition)

    fun encodeObjects(definition: MapDefinition): ByteArray = encode(MapObjectEncoder(), definition)

    /**
     * Writes [definition] and updates the index reference table so clients see the new crc
     */
    fun write(definition: MapDefinition) {
        val region = Region(definition.id)
        val tiles = encodeTiles(definition)
        val objects = encodeObjects(definition)
        cache.write(Index.MAPS, terrain(region), tiles, null)
        cache.write(Index.MAPS, locations(region), objects, null)
        check(cache.update()) { "Cache update failed." }
        check(cache.data(Index.MAPS, terrain(region)).contentEquals(tiles)) { "Terrain for region ${region.id} didn't read back the same." }
        check(cache.data(Index.MAPS, locations(region)).contentEquals(objects)) { "Objects for region ${region.id} didn't read back the same." }
    }

    companion object {
        private const val BUFFER_SIZE = 150_000

        fun terrain(region: Region) = "m${region.x}_${region.y}"

        fun locations(region: Region) = "l${region.x}_${region.y}"

        fun load(cache: Cache, region: Region): MapDefinition? {
            val archive = cache.archiveId(Index.MAPS, terrain(region))
            if (archive == -1) {
                return null
            }
            val decoder = MapDecoder()
            decoder.modified = false
            decoder.regionHashes[archive] = region.id
            val definitions = decoder.create(archive + 1)
            decoder.load(definitions, cache, archive)
            return definitions[archive]
        }

        fun encode(encoder: DefinitionEncoder<MapDefinition>, definition: MapDefinition): ByteArray {
            val writer = ArrayWriter(BUFFER_SIZE)
            with(encoder) {
                writer.encode(definition)
            }
            return writer.toArray()
        }
    }
}
