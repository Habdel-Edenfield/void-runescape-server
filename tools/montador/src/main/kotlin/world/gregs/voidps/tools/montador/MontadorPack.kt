package world.gregs.voidps.tools.montador

import com.displee.cache.CacheLibrary
import world.gregs.voidps.cache.CacheDelegate
import world.gregs.voidps.cache.Index
import world.gregs.voidps.cache.definition.decoder.InterfaceDecoder
import world.gregs.voidps.cache.definition.decoder.InterfaceDecoderFull
import java.io.File
import java.util.Base64
import kotlin.system.exitProcess

/**
 * Packs one interface made in the Montador de Interfaces into the cache (index 3, one archive, one file per
 * component). Run by the tool's packing command (jogos/runescape/exportador.py), which writes the package file,
 * makes sure the game server is stopped and backs the cache up first.
 *
 * Usage: `MontadorPack <package.json> <cache dir> [undo.json]`
 *
 * With `undo.json`, the archive as it was before (its files, or that it didn't exist) is saved there first. Packing
 * that file back (`{"interface": id, "raw": [...]}` or `{"interface": id, "remove": true}`) undoes just this
 * interface, leaving every other change made to the cache since then untouched.
 *
 * Package: `{"interface": id, "replace": bool, "components": list}`, each component with the keys the
 * exporter writes for interfaces (type, parent, x, y, width, height, the modes, sprite, text, colour, hooks...)
 * plus `base: [interface, component]` when it starts from a component already in the cache: then everything the
 * package doesn't set (menu settings, key bindings, mouse icons, drag settings...) stays as it was there.
 *
 * After writing, the archive is read back and compared byte for byte, and decoded with Void's own decoders
 * (the server decodes every interface at startup).
 */
object MontadorPack {

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 2) {
            System.err.println("Usage: MontadorPack <package.json> <cache dir>")
            exitProcess(2)
        }
        @Suppress("UNCHECKED_CAST")
        val pack = Json.read(File(args[0]).readText()) as Map<String, Any?>
        val id = (pack["interface"] as Number).toInt()
        val replace = pack["replace"] == true

        val library = CacheLibrary(args[1])
        val index = library.index(Index.INTERFACES)
        val exists = index.archive(id) != null
        if (exists && !replace && pack["raw"] == null && pack["remove"] != true) {
            System.err.println("Interface $id already exists in the cache; refusing to overwrite it.")
            exitProcess(3)
        }
        if (args.size > 2) {
            saveUndo(library, id, exists, File(args[2]))
        }
        if (pack["remove"] == true) {
            index.remove(id)
            library.update()
            library.close()
            val check = CacheLibrary(args[1])
            require(check.index(Index.INTERFACES).archive(id) == null) { "interface $id is still in the cache" }
            check.close()
            println("Removed interface $id")
            return
        }
        val encoded = if (pack["raw"] != null) {
            (pack["raw"] as List<*>).map { Base64.getDecoder().decode(it as String) }
        } else {
            @Suppress("UNCHECKED_CAST")
            (pack["components"] as List<Map<String, Any?>>).map { build(library, it).encode() }
        }
        index.archive(id)?.clear()
        for ((file, data) in encoded.withIndex()) {
            library.put(Index.INTERFACES, id, file, data)
        }
        library.update()
        library.close()

        check(args[1], id, encoded)
        println("Packed interface $id with ${encoded.size} components (${encoded.sumOf { it.size }} bytes)${if (exists) ", replacing the previous one" else ""}")
    }

    /** The archive as it is now, so that packing this file back undoes only this interface. */
    private fun saveUndo(library: CacheLibrary, id: Int, exists: Boolean, file: File) {
        val undo = if (!exists) {
            mapOf("interface" to id, "remove" to true)
        } else {
            val files = library.index(Index.INTERFACES).archive(id)?.fileIds() ?: intArrayOf()
            require(files.toList() == files.indices.toList()) { "interface $id has files ${files.toList()}, not 0..n; can't save it to undo" }
            val raw = files.map { Base64.getEncoder().encodeToString(library.data(Index.INTERFACES, id, it) ?: ByteArray(0)) }
            mapOf("interface" to id, "replace" to true, "raw" to raw)
        }
        file.parentFile?.mkdirs()
        file.writeText(Json.write(undo))
    }

    /** Reads the archive back from disk and decodes it the ways the server and the tool do. */
    private fun check(cacheDir: String, id: Int, encoded: List<ByteArray>) {
        val library = CacheLibrary(cacheDir)
        val files = library.index(Index.INTERFACES).archive(id)?.fileIds() ?: intArrayOf()
        require(files.toList() == encoded.indices.toList()) { "the archive has files ${files.toList()} instead of 0..${encoded.size - 1}" }
        for ((file, data) in encoded.withIndex()) {
            val read = library.data(Index.INTERFACES, id, file)
            require(read != null && read.contentEquals(data)) { "component $file was not written as encoded" }
            require(Component634.decode(read).encode().contentEquals(read)) { "component $file does not read back" }
        }
        val cache = CacheDelegate(library)
        val full = InterfaceDecoderFull().load(cache)
        require((full.getOrNull(id)?.components?.size ?: 0) == encoded.size) { "Void's full decoder doesn't see the components" }
        val server = InterfaceDecoder().load(cache)
        require(server.getOrNull(id) != null) { "Void's server decoder doesn't see the interface" }
        library.close()
    }

    private fun build(library: CacheLibrary, entry: Map<String, Any?>): Component634 {
        val base = entry["base"] as? List<*>
        val c = if (base != null) {
            val data = library.data(Index.INTERFACES, (base[0] as Number).toInt(), (base[1] as Number).toInt())
                ?: throw IllegalArgumentException("base component $base not found in the cache")
            Component634.decode(data)
        } else {
            Component634()
        }
        fun int(key: String) = (entry[key] as? Number)?.toInt()
        fun flag(key: String) = (entry[key] as? Boolean)?.let { if (it) 1 else 0 }
        int("type")?.let {
            if (base != null && it != c.type) {
                throw IllegalArgumentException("component type can't change from ${c.type} to $it")
            }
            c.type = it
        }
        int("contentType")?.let { c.contentType = it }
        int("parent")?.let { c.parent = it }
        int("x")?.let { c.x = it }
        int("y")?.let { c.y = it }
        int("width")?.let { c.width = it }
        int("height")?.let { c.height = it }
        int("widthMode")?.let { c.widthMode = it }
        int("heightMode")?.let { c.heightMode = it }
        int("xMode")?.let { c.xMode = it }
        int("yMode")?.let { c.yMode = it }
        (entry["hidden"] as? Boolean)?.let { c.hidden = it }
        int("scrollWidth")?.let { c.scrollWidth = it }
        int("scrollHeight")?.let { c.scrollHeight = it }
        flag("blocksMouse")?.let { c.blocksMouse = it }
        int("colour")?.let { c.colour = it }
        flag("filled")?.let { c.filled = it }
        int("alpha")?.let { c.alpha = it }
        int("font")?.let { c.font = it }
        (entry["text"] as? String)?.let { c.text = it }
        int("lineHeight")?.let { c.lineHeight = it }
        int("horizontalAlign")?.let { c.horizontalAlign = it }
        int("verticalAlign")?.let { c.verticalAlign = it }
        flag("shaded")?.let { c.shaded = it }
        int("sprite")?.let { c.sprite = it }
        int("angle")?.let { c.angle = it }
        (entry["tiling"] as? Boolean)?.let { c.spriteFlags = if (it) c.spriteFlags or 0x1 else c.spriteFlags and 0x1.inv() }
        int("outline")?.let { c.outline = it }
        int("shadowColour")?.let { c.shadowColour = it }
        flag("flipVertical")?.let { c.flipVertical = it }
        flag("flipHorizontal")?.let { c.flipHorizontal = it }
        int("tint")?.let { c.tint = it }
        int("model")?.let { c.model = it }
        int("animation")?.let { c.animation = it }
        int("lineWidth")?.let { c.lineWidth = it }
        flag("mirrored")?.let { c.mirrored = it }
        int("setting")?.let { c.setting = it }
        (entry["componentName"] as? String)?.let { c.name = it }
        (entry["options"] as? List<*>)?.let { list -> c.options = list.map { it.toString() }.toMutableList() }
        if (c.type == 6 && c.widthMode != 0 && c.viewportWidth == 0) {
            c.viewportWidth = c.width
        }
        if (c.type == 6 && c.heightMode != 0 && c.viewportHeight == 0) {
            c.viewportHeight = c.height
        }
        val hooks = entry["hooks"] as? Map<*, *>
        if (hooks != null) {
            c.hooks.fill(null)
            for ((name, value) in hooks) {
                val i = Component634.HOOKS.indexOf(name)
                require(i >= 0) { "unknown hook $name" }
                c.hooks[i] = (value as List<*>).map { if (it is String) it else (it as Number).toInt() }
            }
        }
        val triggers = entry["triggers"] as? Map<*, *>
        if (triggers != null) {
            c.triggers.fill(null)
            for ((name, value) in triggers) {
                val i = Component634.TRIGGERS.indexOf(name)
                require(i >= 0) { "unknown trigger list $name" }
                c.triggers[i] = (value as List<*>).map { (it as Number).toInt() }.toIntArray()
            }
        }
        require(c.options.size <= 15) { "a component has at most 15 options" }
        if (!Component634.hasSettingData(c.setting)) {
            c.settingData = intArrayOf(-1, -1, -1)
        }
        return c
    }
}
