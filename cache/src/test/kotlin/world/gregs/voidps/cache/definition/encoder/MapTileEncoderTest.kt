package world.gregs.voidps.cache.definition.encoder

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import world.gregs.voidps.buffer.write.ArrayWriter
import world.gregs.voidps.cache.definition.data.MapDefinition
import world.gregs.voidps.cache.definition.data.MapTile

internal class MapTileEncoderTest {

    @Test
    fun `Write settings`() {
        val def = MapDefinition(123)
        def.setTile(0, 0, 0, MapTile(settings = 1))
        def.setTile(0, 1, 0, MapTile(settings = 2))

        val data = encode(def)

        assertArrayEquals(byteArrayOf(50, 0, 51, 0, 0), data.sliceArray(0 until 5))
        assertEquals(TILES + 2, data.size)
    }

    @Test
    fun `Write explicit height ends the tile`() {
        val def = MapDefinition(123)
        def.setTile(0, 0, 0, MapTile(height = 5))

        val data = encode(def)

        assertArrayEquals(byteArrayOf(1, 5, 0), data.sliceArray(0 until 3))
        assertEquals(TILES + 1, data.size)
    }

    @Test
    fun `Write overlay, settings then underlay`() {
        val def = MapDefinition(123)
        def.setTile(0, 0, 0, MapTile(height = 1, opcode = 2, overlay = 112, settings = 1, underlay = 65))

        val data = encode(def)

        assertArrayEquals(byteArrayOf(2, 112, 50, -110, 1, 1), data.sliceArray(0 until 6))
    }

    @Test
    fun `Write extras after tiles`() {
        val def = MapDefinition(123, extras = byteArrayOf(0, 7, 9))

        val data = encode(def)

        assertEquals(TILES + 3, data.size)
        assertArrayEquals(byteArrayOf(0, 7, 9), data.sliceArray(TILES until data.size))
    }

    private fun encode(definition: MapDefinition): ByteArray {
        val writer = ArrayWriter(TILES * 2)
        with(MapTileEncoder()) {
            writer.encode(definition)
        }
        return writer.toArray()
    }

    companion object {
        private const val TILES = 4 * 64 * 64
    }
}
