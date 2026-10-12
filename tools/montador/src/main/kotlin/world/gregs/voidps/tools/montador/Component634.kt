package world.gregs.voidps.tools.montador

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * One rev 634 interface component (IF3, version byte -1), read and written byte for byte in the order the
 * client reads it (Class46.method433 in void-client).
 *
 * Unlike InterfaceDecoderFull this keeps everything needed to write the same bytes back: signed positions,
 * unsigned sizes, raw flag bytes, cp1252 strings (the client's charset; Void reads them as UTF-8), the key
 * bindings and mouse icons in the order they were stored. `encode(decode(bytes))` gives `bytes` back for every
 * component of the 634 cache (checked by MontadorExport.verify).
 */
class Component634 {
    var type = 0
    var typeName: String? = null
    var contentType = 0
    var x = 0
    var y = 0
    var width = 0
    var height = 0
    var widthMode = 0
    var heightMode = 0
    var xMode = 0
    var yMode = 0
    var parent = -1
    var flags = 0

    // type 0
    var scrollWidth = 0
    var scrollHeight = 0
    var blocksMouse = 0

    // types 3, 4, 5, 9
    var colour = 0
    var filled = 0
    var alpha = 0

    // type 4
    var font = -1
    var text = ""
    var lineHeight = 0
    var horizontalAlign = 0
    var verticalAlign = 0
    var shaded = 0

    // type 5
    var sprite = -1
    var angle = 0
    var spriteFlags = 0
    var outline = 0
    var shadowColour = 0
    var flipVertical = 0
    var flipHorizontal = 0
    var tint = 0

    // type 6
    var model = -1
    var modelFlags = 0
    var modelValues = IntArray(0)
    var animation = -1
    var viewportWidth = 0
    var viewportHeight = 0

    // type 9
    var lineWidth = 1
    var mirrored = 0

    var setting = 0

    /** Key bindings as stored: [first byte, second byte, repeat, code] each. */
    var keys = mutableListOf<IntArray>()
    var name = ""
    var options = mutableListOf<String>()

    /** Mouse icons as stored: [index, sprite] pairs, the first one being the array size. */
    var mouseIcons = mutableListOf<IntArray>()
    var optionOverride = ""
    var dragDeadZone = 0
    var dragDeadTime = 0
    var dragRender = 0
    var useOption = ""
    var settingData = intArrayOf(-1, -1, -1)
    val hooks = arrayOfNulls<List<Any>>(HOOKS.size)
    val triggers = arrayOfNulls<IntArray>(TRIGGERS.size)

    var hidden: Boolean
        get() = flags and 0x1 != 0
        set(value) {
            flags = if (value) flags or 0x1 else flags and 0x1.inv()
        }

    val tiling: Boolean get() = spriteFlags and 0x1 != 0

    fun encode(): ByteArray {
        val out = Out()
        out.byte(-1)
        if (typeName != null) {
            out.byte(type or 0x80)
            out.string(typeName!!)
        } else {
            out.byte(type)
        }
        out.short(contentType)
        out.short(x)
        out.short(y)
        out.short(width)
        out.short(height)
        out.byte(widthMode)
        out.byte(heightMode)
        out.byte(xMode)
        out.byte(yMode)
        out.short(parent)
        out.byte(flags)
        when (type) {
            0 -> {
                out.short(scrollWidth)
                out.short(scrollHeight)
                out.byte(blocksMouse)
            }
            3 -> {
                out.int(colour)
                out.byte(filled)
                out.byte(alpha)
            }
            4 -> {
                out.short(font)
                out.string(text)
                out.byte(lineHeight)
                out.byte(horizontalAlign)
                out.byte(verticalAlign)
                out.byte(shaded)
                out.int(colour)
                out.byte(alpha)
            }
            5 -> {
                out.int(sprite)
                out.short(angle)
                out.byte(spriteFlags)
                out.byte(alpha)
                out.byte(outline)
                out.int(shadowColour)
                out.byte(flipVertical)
                out.byte(flipHorizontal)
                out.int(tint)
            }
            6 -> {
                out.short(model)
                out.byte(modelFlags)
                for (value in modelValues) {
                    out.short(value)
                }
                out.short(animation)
                if (widthMode != 0) {
                    out.short(viewportWidth)
                }
                if (heightMode != 0) {
                    out.short(viewportHeight)
                }
            }
            9 -> {
                out.byte(lineWidth)
                out.int(colour)
                out.byte(mirrored)
            }
        }
        out.medium(setting)
        for (key in keys) {
            out.byte(key[0])
            out.byte(key[1])
            out.byte(key[2])
            out.byte(key[3])
        }
        out.byte(0)
        out.string(name)
        out.byte(options.size or (mouseIcons.size shl 4))
        for (option in options) {
            out.string(option)
        }
        if (mouseIcons.isNotEmpty()) {
            out.byte(mouseIcons[0][0])
            out.short(mouseIcons[0][1])
        }
        if (mouseIcons.size > 1) {
            out.byte(mouseIcons[1][0])
            out.short(mouseIcons[1][1])
        }
        out.string(optionOverride)
        out.byte(dragDeadZone)
        out.byte(dragDeadTime)
        out.byte(dragRender)
        out.string(useOption)
        if (hasSettingData(setting)) {
            for (value in settingData) {
                out.short(value)
            }
        }
        for (hook in hooks) {
            if (hook == null) {
                out.byte(0)
                continue
            }
            out.byte(hook.size)
            for (value in hook) {
                if (value is String) {
                    out.byte(1)
                    out.string(value)
                } else {
                    out.byte(0)
                    out.int((value as Number).toInt())
                }
            }
        }
        for (trigger in triggers) {
            if (trigger == null) {
                out.byte(0)
                continue
            }
            out.byte(trigger.size)
            for (value in trigger) {
                out.int(value)
            }
        }
        return out.toByteArray()
    }

    companion object {
        val CHARSET: Charset = Charset.forName("windows-1252")

        /** Hook names in the order they are stored, as the client fires them. */
        val HOOKS = listOf(
            "onLoad", "onMouseOver", "onMouseLeave", "onTargetLeave", "onTargetEnter", "onVarTransmit", "onInvTransmit",
            "onStatTransmit", "onTimer", "onOp", "onMouseRepeat", "onClick", "onClickRepeat", "onRelease", "onHold", "onDrag",
            "onDragComplete", "onScrollWheel", "onVarcTransmit", "onVarcStrTransmit",
        )

        /** Trigger lists in the order they are stored. */
        val TRIGGERS = listOf("varp", "inventory", "stat", "varc", "varcString")

        fun hasSettingData(setting: Int) = (setting and 0x3fda8) shr 11 != 0

        fun decode(data: ByteArray): Component634 {
            val input = In(data)
            val c = Component634()
            val version = input.byte()
            require(version == 0xff) { "not an IF3 (version -1) component" }
            val type = input.byte()
            if (type and 0x80 != 0) {
                c.type = type and 0x7f
                c.typeName = input.string()
            } else {
                c.type = type
            }
            c.contentType = input.short()
            c.x = input.signedShort()
            c.y = input.signedShort()
            c.width = input.short()
            c.height = input.short()
            c.widthMode = input.signedByte()
            c.heightMode = input.signedByte()
            c.xMode = input.signedByte()
            c.yMode = input.signedByte()
            c.parent = input.short().let { if (it == 0xffff) -1 else it }
            c.flags = input.byte()
            when (c.type) {
                0 -> {
                    c.scrollWidth = input.short()
                    c.scrollHeight = input.short()
                    c.blocksMouse = input.byte()
                }
                3 -> {
                    c.colour = input.int()
                    c.filled = input.byte()
                    c.alpha = input.byte()
                }
                4 -> {
                    c.font = input.short().let { if (it == 0xffff) -1 else it }
                    c.text = input.string()
                    c.lineHeight = input.byte()
                    c.horizontalAlign = input.byte()
                    c.verticalAlign = input.byte()
                    c.shaded = input.byte()
                    c.colour = input.int()
                    c.alpha = input.byte()
                }
                5 -> {
                    c.sprite = input.int()
                    c.angle = input.short()
                    c.spriteFlags = input.byte()
                    c.alpha = input.byte()
                    c.outline = input.byte()
                    c.shadowColour = input.int()
                    c.flipVertical = input.byte()
                    c.flipHorizontal = input.byte()
                    c.tint = input.int()
                }
                6 -> {
                    c.model = input.short().let { if (it == 0xffff) -1 else it }
                    c.modelFlags = input.byte()
                    val count = when {
                        c.modelFlags and 0x1 != 0 -> 6
                        c.modelFlags and 0x2 != 0 -> 7
                        else -> 0
                    }
                    c.modelValues = IntArray(count) { input.short() }
                    c.animation = input.short().let { if (it == 0xffff) -1 else it }
                    if (c.widthMode != 0) {
                        c.viewportWidth = input.short()
                    }
                    if (c.heightMode != 0) {
                        c.viewportHeight = input.short()
                    }
                }
                9 -> {
                    c.lineWidth = input.byte()
                    c.colour = input.int()
                    c.mirrored = input.byte()
                }
            }
            c.setting = input.medium()
            var key = input.byte()
            while (key != 0) {
                c.keys.add(intArrayOf(key, input.byte(), input.byte(), input.byte()))
                key = input.byte()
            }
            c.name = input.string()
            val mouse = input.byte()
            repeat(mouse and 0xf) { c.options.add(input.string()) }
            val icons = mouse shr 4
            if (icons > 0) {
                c.mouseIcons.add(intArrayOf(input.byte(), input.short()))
            }
            if (icons > 1) {
                c.mouseIcons.add(intArrayOf(input.byte(), input.short()))
            }
            c.optionOverride = input.string()
            c.dragDeadZone = input.byte()
            c.dragDeadTime = input.byte()
            c.dragRender = input.byte()
            c.useOption = input.string()
            if (hasSettingData(c.setting)) {
                c.settingData = IntArray(3) { input.short().let { if (it == 0xffff) -1 else it } }
            }
            for (i in HOOKS.indices) {
                val length = input.byte()
                if (length == 0) {
                    continue
                }
                c.hooks[i] = List(length) { if (input.byte() == 1) input.string() else input.int() }
            }
            for (i in TRIGGERS.indices) {
                val length = input.byte()
                if (length == 0) {
                    continue
                }
                c.triggers[i] = IntArray(length) { input.int() }
            }
            require(input.position == data.size) { "${data.size - input.position} bytes left after the component" }
            return c
        }
    }

    private class In(val data: ByteArray) {
        var position = 0
        fun byte() = data[position++].toInt() and 0xff
        fun signedByte() = data[position++].toInt()
        fun short() = (byte() shl 8) or byte()
        fun signedShort() = short().toShort().toInt()
        fun medium() = (byte() shl 16) or (byte() shl 8) or byte()
        fun int() = (byte() shl 24) or (byte() shl 16) or (byte() shl 8) or byte()
        fun string(): String {
            val start = position
            while (data[position] != 0.toByte()) {
                position++
            }
            val value = String(data, start, position - start, CHARSET)
            position++
            return value
        }
    }

    private class Out : ByteArrayOutputStream() {
        fun byte(value: Int) = write(value and 0xff)
        fun short(value: Int) {
            byte(value shr 8)
            byte(value)
        }
        fun medium(value: Int) {
            byte(value shr 16)
            short(value)
        }
        fun int(value: Int) {
            short(value shr 16)
            short(value)
        }
        fun string(value: String) {
            write(value.toByteArray(CHARSET))
            byte(0)
        }
    }
}
