package world.gregs.voidps.tools.montador

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Component634Test {

    private fun roundTrip(component: Component634): Component634 {
        val bytes = component.encode()
        val decoded = Component634.decode(bytes)
        assertArrayEquals(bytes, decoded.encode())
        return decoded
    }

    @Test
    fun `Text keeps cp1252 letters and negative positions`() {
        val text = Component634().apply {
            type = 4
            x = -5
            y = 300
            width = 200
            height = 18
            parent = 3
            font = 495
            text = "Ação <col=ff0000>é</col>"
            colour = 0xff981f
            shaded = 1
            hooks[1] = listOf(45, -2147483645, 0xff0000)
            hooks[2] = listOf(45, -2147483645, 0xffffff)
        }
        val decoded = roundTrip(text)
        assertEquals("Ação <col=ff0000>é</col>", decoded.text)
        assertEquals(-5, decoded.x)
        assertEquals(listOf(45, -2147483645, 0xff0000), decoded.hooks[1])
        assertEquals(text.encode()[0].toInt(), -1)
    }

    @Test
    fun `Sprite with menu option, setting data and triggers`() {
        val sprite = Component634().apply {
            type = 5
            sprite = 1431
            spriteFlags = 0x1
            width = 35
            height = 35
            parent = -1
            options = mutableListOf("Select", "Examine")
            setting = 2 or (1 shl 11)
            settingData = intArrayOf(7, -1, -1)
            hooks[9] = listOf(1722, -2147483645, -2147483643, 1433, 1431, 10)
            triggers[0] = intArrayOf(304)
            mouseIcons = mutableListOf(intArrayOf(0, 12))
            keys = mutableListOf(intArrayOf(0x10, 0x01, 1, 65))
        }
        val decoded = roundTrip(sprite)
        assertEquals(true, decoded.tiling)
        assertEquals(listOf("Select", "Examine"), decoded.options)
        assertArrayEquals(intArrayOf(7, -1, -1), decoded.settingData)
        assertArrayEquals(intArrayOf(304), decoded.triggers[0])
    }

    @Test
    fun `Layer, rectangle, model and line`() {
        roundTrip(
            Component634().apply {
                type = 0
                scrollHeight = 400
                hidden = true
            },
        )
        roundTrip(
            Component634().apply {
                type = 3
                colour = 0x123456
                filled = 1
                alpha = 200
            },
        )
        roundTrip(
            Component634().apply {
                type = 6
                widthMode = 1
                model = 42329
                modelFlags = 0x1
                modelValues = IntArray(6) { it }
                animation = 2449
                viewportWidth = 100
            },
        )
        roundTrip(
            Component634().apply {
                type = 9
                lineWidth = 2
                colour = 0xffffff
                mirrored = 1
            },
        )
    }

    @Test
    fun `Json reads what it writes`() {
        val value = mapOf("a" to listOf(1, -2147483645, "x\"y"), "b" to null, "c" to true, "d" to "Ação")
        assertEquals(mapOf("a" to listOf(1L, -2147483645L, "x\"y"), "b" to null, "c" to true, "d" to "Ação"), Json.read(Json.write(value)))
    }
}
