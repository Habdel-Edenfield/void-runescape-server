package world.gregs.voidps.tools.montador

import com.displee.cache.CacheLibrary
import world.gregs.voidps.cache.CacheDelegate
import world.gregs.voidps.cache.Index
import world.gregs.voidps.cache.definition.data.IndexedSprite
import world.gregs.voidps.cache.definition.decoder.FontDecoder
import world.gregs.voidps.cache.definition.decoder.SpriteDecoder
import java.awt.image.BufferedImage
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.system.exitProcess

/**
 * Exports what the "Void - Montador de Interfaces" tool needs from the cache. Read only: nothing is written to the cache.
 *
 * Usage: `MontadorExport <cache dir> <data dir> <output dir>`
 *
 * Output:
 * - `sprites/<id>.png` every sprite archive drawn on its full canvas (frame 0), and `sprites/<id>_<frame>.png` for the other frames
 * - `sprites.json` id, name (when the archive name hash is known), frames with canvas and trimmed sizes, content hash, usage
 * - `fonts/<id>.png` 16x16 atlas of the glyph masks (white, alpha = coverage) and `fonts.json` the metrics of each font
 * - `interfaces/<id>.json` every component of an interface with its raw fields, and `interfaces.json` the index
 * - `export.json` counts and versions, written last (the tool only trusts an export that has it)
 */
object MontadorExport {

    const val VERSION = 1

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 3) {
            System.err.println("Usage: MontadorExport <cache dir> <data dir> <output dir>")
            exitProcess(2)
        }
        val start = System.currentTimeMillis()
        val output = File(args[2])
        output.mkdirs()
        val library = CacheLibrary(args[0])
        val cache = CacheDelegate(library)
        val names = InterfaceNames(File(args[1]))

        val spriteDefinitions = SpriteDecoder().load(cache)
        val fontIds = library.index(Index.FONT_METRICS).archiveIds().toSet()
        val known = knownNames(names)

        val usage = mutableMapOf<Int, MutableList<String>>()
        val interfaces = exportInterfaces(library, names, File(output, "interfaces"), usage)
        val sprites = exportSprites(library, spriteDefinitions, fontIds, known, usage, File(output, "sprites"))
        val fonts = exportFonts(cache, library, spriteDefinitions, known, File(output, "fonts"))

        File(output, "sprites.json").writeText(Json.write(sprites))
        File(output, "fonts.json").writeText(Json.write(fonts))
        File(output, "interfaces.json").writeText(Json.write(interfaces))
        File(output, "export.json").writeText(
            Json.write(
                mapOf(
                    "version" to VERSION,
                    "revision" to 634,
                    "sprites" to sprites.size,
                    "fonts" to fonts.size,
                    "interfaces" to interfaces.size,
                    "millis" to System.currentTimeMillis() - start,
                ),
            ),
        )
        library.close()
        println("Exported ${sprites.size} sprites, ${fonts.size} fonts and ${interfaces.size} interfaces in ${System.currentTimeMillis() - start}ms")
    }

    /**
     * Names the archives of the sprite and font indices whose name hash matches a known name.
     * The cache only stores the hash; the names come from Void's font config and a list of names the client looks up.
     */
    private fun knownNames(names: InterfaceNames): Map<Int, String> {
        val candidates = mutableSetOf<String>()
        candidates.addAll(KNOWN_SPRITE_NAMES)
        val fonts = File("data/entity/player/modal/fonts.toml")
        if (fonts.exists()) {
            for (line in fonts.readLines()) {
                val name = line.substringBefore("=").trim()
                if (name.isNotEmpty() && !name.startsWith("#")) {
                    candidates.add(name)
                }
            }
        }
        for (name in names.names.values) {
            candidates.add(name)
        }
        return candidates.associateBy { it.hashCode() }
    }

    private fun exportSprites(
        library: CacheLibrary,
        definitions: Array<world.gregs.voidps.cache.definition.data.SpriteDefinition>,
        fontIds: Set<Int>,
        known: Map<Int, String>,
        usage: Map<Int, List<String>>,
        directory: File,
    ): List<Map<String, Any?>> {
        directory.mkdirs()
        val index = library.index(Index.SPRITES)
        val list = mutableListOf<Map<String, Any?>>()
        for (definition in definitions) {
            val frames = definition.sprites ?: continue
            if (frames.isEmpty()) {
                continue
            }
            val id = definition.id
            val hash = index.archive(id)?.hashName ?: 0
            val font = id in fontIds && frames.size == 256
            val frameList = mutableListOf<Map<String, Any?>>()
            for ((frame, sprite) in frames.withIndex()) {
                val width = sprite.scaleWidth()
                val height = sprite.offsetY + sprite.height + sprite.deltaHeight
                val entry = mutableMapOf<String, Any?>(
                    "width" to width,
                    "height" to height,
                    "trim" to listOf(sprite.offsetX, sprite.offsetY, sprite.width, sprite.height),
                    "alpha" to (sprite.alpha != null),
                )
                if (!font && width > 0 && height > 0) {
                    val image = canvas(sprite, width, height)
                    entry["hash"] = contentHash(image)
                    val name = if (frame == 0) "$id.png" else "${id}_$frame.png"
                    ImageIO.write(image, "png", File(directory, name))
                }
                frameList.add(entry)
            }
            list.add(
                mapOf(
                    "id" to id,
                    "name" to known[hash],
                    "nameHash" to hash,
                    "font" to font,
                    "frames" to frameList,
                    "uses" to (usage[id]?.size ?: 0),
                    "examples" to (usage[id]?.take(5) ?: emptyList<String>()),
                ),
            )
        }
        return list
    }

    /** The sprite on its full canvas, with the trimmed pixels at their offset, exactly as decoded. */
    private fun canvas(sprite: IndexedSprite, width: Int, height: Int): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val alpha = sprite.alpha
        for (y in 0 until sprite.height) {
            for (x in 0 until sprite.width) {
                val i = x + y * sprite.width
                val colour = sprite.palette[sprite.raster[i].toInt() and 0xff]
                val argb = if (alpha == null) {
                    if (colour == 0) 0 else (0xff shl 24) or colour
                } else {
                    ((alpha[i].toInt() and 0xff) shl 24) or (colour and 0xffffff)
                }
                val px = sprite.offsetX + x
                val py = sprite.offsetY + y
                if (px in 0 until width && py in 0 until height) {
                    image.setRGB(px, py, argb)
                }
            }
        }
        return image
    }

    private fun contentHash(image: BufferedImage): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        val bytes = ByteArray(8 + pixels.size * 4)
        putInt(bytes, 0, image.width)
        putInt(bytes, 4, image.height)
        for ((i, pixel) in pixels.withIndex()) {
            putInt(bytes, 8 + i * 4, pixel)
        }
        return digest.digest(bytes).take(8).joinToString("") { "%02x".format(it) }
    }

    private fun putInt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value shr 24).toByte()
        bytes[offset + 1] = (value shr 16).toByte()
        bytes[offset + 2] = (value shr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    private fun exportFonts(
        cache: CacheDelegate,
        library: CacheLibrary,
        sprites: Array<world.gregs.voidps.cache.definition.data.SpriteDefinition>,
        known: Map<Int, String>,
        directory: File,
    ): List<Map<String, Any?>> {
        directory.mkdirs()
        val metrics = FontDecoder().load(cache)
        val index = library.index(Index.FONT_METRICS)
        val list = mutableListOf<Map<String, Any?>>()
        for (font in metrics) {
            if (font.glyphWidths.size != 256) {
                continue
            }
            val glyphs = sprites.getOrNull(font.id)?.sprites ?: continue
            if (glyphs.size != 256) {
                continue
            }
            val cellWidth = glyphs.maxOf { it.scaleWidth() }.coerceAtLeast(1)
            val cellHeight = glyphs.maxOf { it.offsetY + it.height + it.deltaHeight }.coerceAtLeast(1)
            val atlas = BufferedImage(cellWidth * 16, cellHeight * 16, BufferedImage.TYPE_INT_ARGB)
            val glyphList = mutableListOf<List<Int>>()
            for ((code, glyph) in glyphs.withIndex()) {
                val cellX = (code % 16) * cellWidth
                val cellY = (code / 16) * cellHeight
                val alpha = glyph.alpha
                for (y in 0 until glyph.height) {
                    for (x in 0 until glyph.width) {
                        val i = x + y * glyph.width
                        val coverage = if (alpha != null) {
                            alpha[i].toInt() and 0xff
                        } else if (glyph.raster[i].toInt() and 0xff != 0) {
                            0xff
                        } else {
                            0
                        }
                        if (coverage != 0) {
                            atlas.setRGB(cellX + glyph.offsetX + x, cellY + glyph.offsetY + y, (coverage shl 24) or 0xffffff)
                        }
                    }
                }
                // [atlas x, atlas y, width, height, offset x, offset y] of the trimmed glyph
                glyphList.add(listOf(cellX + glyph.offsetX, cellY + glyph.offsetY, glyph.width, glyph.height, glyph.offsetX, glyph.offsetY))
            }
            ImageIO.write(atlas, "png", File(directory, "${font.id}.png"))
            val raw = cache.data(Index.FONT_METRICS, font.id, 0)
            val kerning = font.kerningAdjustments?.let { table ->
                val bytes = ByteArray(256 * 256)
                for (left in 0 until 256) {
                    System.arraycopy(table[left], 0, bytes, left * 256, 256)
                }
                Base64.getEncoder().encodeToString(bytes)
            }
            list.add(
                mapOf(
                    "id" to font.id,
                    "name" to known[index.archive(font.id)?.hashName ?: 0],
                    "cell" to listOf(cellWidth, cellHeight),
                    "glyphs" to glyphList,
                    "advances" to font.glyphWidths.map { it.toInt() and 0xff },
                    // Void calls these verticalSpacing/topPadding/bottomPadding; the client uses them as baseline/ascent/descent
                    "baseline" to font.verticalSpacing,
                    "ascent" to font.topPadding,
                    "descent" to font.bottomPadding,
                    "unused" to raw?.let { skippedBytes(it) },
                    "kerning" to kerning,
                ),
            )
        }
        return list
    }

    /** The two bytes the client reads and discards before ascent and descent. */
    private fun skippedBytes(data: ByteArray): List<Int>? {
        if (data.size < 4) {
            return null
        }
        return listOf(data[data.size - 4].toInt() and 0xff, data[data.size - 3].toInt() and 0xff)
    }

    private fun exportInterfaces(library: CacheLibrary, names: InterfaceNames, directory: File, usage: MutableMap<Int, MutableList<String>>): List<Map<String, Any?>> {
        directory.mkdirs()
        val index = library.index(Index.INTERFACES)
        val list = mutableListOf<Map<String, Any?>>()
        for (archive in index.archiveIds()) {
            val fileIds = index.archive(archive)?.fileIds() ?: continue
            if (fileIds.isEmpty()) {
                continue
            }
            val componentNames = names.components[archive] ?: emptyMap()
            val components = mutableListOf<Map<String, Any?>>()
            var oldFormat = 0
            var failed = 0
            val texts = mutableListOf<String>()
            val spriteIds = mutableSetOf<Int>()
            for (file in fileIds) {
                val data = library.data(Index.INTERFACES, archive, file) ?: continue
                if (data.isEmpty()) {
                    continue
                }
                if (data[0] != (-1).toByte()) {
                    oldFormat++
                    components.add(mapOf("index" to file, "format" to "if1", "size" to data.size))
                    continue
                }
                val component = try {
                    Component634.decode(data)
                } catch (exception: Exception) {
                    failed++
                    components.add(mapOf("index" to file, "format" to "error", "error" to exception.toString()))
                    continue
                }
                if (component.type == 5 && component.sprite >= 0) {
                    spriteIds.add(component.sprite)
                    usage.getOrPut(component.sprite) { mutableListOf() }.add("$archive:$file")
                }
                if (component.type == 4 && component.text.isNotBlank() && texts.size < 12) {
                    texts.add(component.text)
                }
                components.add(componentJson(file, component, componentNames[file]))
            }
            val type = names.types[archive] ?: InterfaceNames.DEFAULT_TYPE
            val slot = names.slots[type]
            val entry = mapOf(
                "id" to archive,
                "name" to names.names[archive],
                "type" to type,
                "declared" to names.names.containsKey(archive),
                "file" to names.files[archive],
                "slot" to slot?.let { mapOf("fixed" to listOf(it.fixedParent, it.fixedIndex), "resizable" to listOf(it.resizeParent, it.resizeIndex)) },
                "components" to components.size,
                "oldFormat" to oldFormat,
                "failed" to failed,
                "sprites" to spriteIds.sorted(),
                "texts" to texts,
            )
            File(directory, "$archive.json").writeText(Json.write(entry + mapOf("tree" to components)))
            list.add(entry)
        }
        return list
    }

    private fun componentJson(index: Int, c: Component634, name: String?): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>(
            "index" to index,
            "name" to name,
            "type" to c.type,
            "contentType" to c.contentType,
            "parent" to c.parent,
            "x" to c.x,
            "y" to c.y,
            "width" to c.width,
            "height" to c.height,
            "widthMode" to c.widthMode,
            "heightMode" to c.heightMode,
            "xMode" to c.xMode,
            "yMode" to c.yMode,
            "hidden" to c.hidden,
        )
        if (c.typeName != null) {
            map["typeName"] = c.typeName
        }
        when (c.type) {
            0 -> {
                map["scrollWidth"] = c.scrollWidth
                map["scrollHeight"] = c.scrollHeight
                map["blocksMouse"] = c.blocksMouse != 0
            }
            3 -> {
                map["colour"] = c.colour
                map["filled"] = c.filled != 0
                map["alpha"] = c.alpha
            }
            4 -> {
                map["font"] = c.font
                map["text"] = c.text
                map["lineHeight"] = c.lineHeight
                map["horizontalAlign"] = c.horizontalAlign
                map["verticalAlign"] = c.verticalAlign
                map["shaded"] = c.shaded != 0
                map["colour"] = c.colour
                map["alpha"] = c.alpha
            }
            5 -> {
                map["sprite"] = c.sprite
                map["angle"] = c.angle
                map["tiling"] = c.tiling
                map["flag2"] = c.spriteFlags and 0x2 != 0
                map["alpha"] = c.alpha
                map["outline"] = c.outline
                map["shadowColour"] = c.shadowColour
                map["flipVertical"] = c.flipVertical != 0
                map["flipHorizontal"] = c.flipHorizontal != 0
                map["tint"] = c.tint
            }
            6 -> {
                map["model"] = c.model
                map["orthographic"] = c.modelFlags and 0x4 != 0
                map["animation"] = c.animation
                map["modelFlags"] = c.modelFlags
                map["modelValues"] = c.modelValues
                map["viewport"] = listOf(c.viewportWidth, c.viewportHeight)
            }
            9 -> {
                map["lineWidth"] = c.lineWidth
                map["colour"] = c.colour
                map["mirrored"] = c.mirrored != 0
            }
        }
        if (c.name.isNotEmpty()) {
            map["componentName"] = c.name
        }
        if (c.options.isNotEmpty()) {
            map["options"] = c.options
        }
        if (c.optionOverride.isNotEmpty()) {
            map["optionOverride"] = c.optionOverride
        }
        if (c.useOption.isNotEmpty()) {
            map["useOption"] = c.useOption
        }
        map["setting"] = c.setting
        // hook names as the client fires them (checked in void-client: Class46.method433 and client.method107)
        val hooks = linkedMapOf<String, Any?>()
        for ((i, hook) in c.hooks.withIndex()) {
            if (hook != null) {
                hooks[Component634.HOOKS[i]] = hook
            }
        }
        if (hooks.isNotEmpty()) {
            map["hooks"] = hooks
        }
        val triggers = linkedMapOf<String, Any?>()
        for ((i, trigger) in c.triggers.withIndex()) {
            if (trigger != null) {
                triggers[Component634.TRIGGERS[i]] = trigger
            }
        }
        if (triggers.isNotEmpty()) {
            map["triggers"] = triggers
        }
        return map
    }

    /** Names of sprite archives the client looks up by name (the cache only keeps their hashes). */
    private val KNOWN_SPRITE_NAMES = listOf(
        "logo", "titlebox", "titlebutton", "runes", "mapscene", "mapfunction", "magicon", "magicon2", "magiconoff", "magiconoff2",
        "scrollbar", "headicons_pk", "headicons_prayer", "headicons_hint", "headicons_hinticons", "hitmarks", "hitmark", "mapmarker",
        "mapdots", "mapflag", "cross", "sideicons", "compass", "mapedge", "mod_icons", "name_icons", "staticons", "staticons2",
        "miscgraphics", "miscgraphics2", "miscgraphics3", "chatback", "backbase1", "backbase2", "invback", "floorshadows", "otherlevel",
        "hint_mapmarkers", "hint_mapedge", "hitbar_default", "timerbar_default", "overlay_multiway", "overlay_duel", "steelborder",
        "steelborder2", "sworddecor", "wornicons", "prayerglow", "prayeron", "prayeroff", "combaticons", "combaticons2", "combaticons3",
        "tex_brown", "tex_red", "leftarrow", "rightarrow", "leftarrow_small", "rightarrow_small", "blackmark", "button_brown",
        "button_red", "button_brown_big", "redstone1", "redstone2", "redstone3", "redstone1h", "redstone2h", "redstone1v", "redstone2v",
        "redstone3v", "redstone1hv", "redstone2hv", "chest", "coins", "keys", "options_icons", "options_radio_buttons",
        "options_slider", "lunar_magicon", "ancient_magicon", "dungeoneering_magicon", "loginscreen", "lobby_background",
        "p11_full", "p12_full", "b12_full", "q8_full", "p11", "p12", "b12", "q8", "lunar_alphabet", "lunar_alphabet_lrg",
    )
}
