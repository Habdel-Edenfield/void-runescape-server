package world.gregs.voidps.tools.cache

import com.displee.cache.CacheLibrary
import world.gregs.voidps.buffer.write.ArrayWriter
import world.gregs.voidps.cache.Cache
import world.gregs.voidps.cache.CacheDelegate
import world.gregs.voidps.cache.Config
import world.gregs.voidps.cache.Index
import world.gregs.voidps.cache.definition.data.MapDefinition
import world.gregs.voidps.cache.definition.data.MapObject
import world.gregs.voidps.cache.definition.data.ObjectDefinitionFull
import world.gregs.voidps.cache.definition.decoder.NPCDecoderFull
import world.gregs.voidps.cache.definition.decoder.ObjectDecoderFull
import world.gregs.voidps.cache.definition.encoder.MapObjectEncoder
import world.gregs.voidps.tools.map.MapObjectDefinitionDecoder
import world.gregs.voidps.type.Region
import java.io.File
import java.net.ServerSocket
import kotlin.system.exitProcess

/**
 * Gives the houses in [HOUSES] (content.social.house) objects of their own on the map, so their door and furniture
 * have options for the player looking at them without changing objects alike elsewhere.
 *
 * Door, for each house:
 *  - copies `visitor` ("Open", "Knock-at"), `sale` (named after the house, "Buy") and `owner` ("Open", "Manage")
 *  - the house's own door, put on the map in place of its door, which turns into those copies when the house's
 *    player variable ([varpId], in [VARPS_FILE]) is 0, 1 or 2; the server sets it for each player
 *
 * Furniture, for each piece in [FURNITURE] (pieces alike in a house share their objects):
 *  - with a hotspot: a `build` copy with a "Build" option, and the house's own piece on the map, which turns into the
 *    `build` copy for the owner (the same variable at 2) and stays itself for everyone else
 *  - with an unlit object: the house's own piece on the map with "Extinguish", and an `unlit` copy with "Light"
 *    looking like the unlit object, which the server swaps them for
 *
 * Copies are the original bytes with opcodes added before the terminating 0, so they look the same. A map region is
 * only rewritten when encoding it unchanged gives back the same bytes. Ids are kept in [OBJECTS_FILE] and the varps
 * in [VARPS_FILE], so running it again only reapplies missing changes.
 *
 * Usage (from the repository root, with the server stopped for `patch`):
 *   check - lists what would change
 *   patch - writes the changes to the cache and the two data files
 * `--cache <dir>` points at another cache, e.g. a copy to try a change on (the data files aren't written then).
 */
object WorldHouseObjects {

    private const val HOUSES = "data/social/house/world_houses.tables.toml"
    private const val FURNITURE = "data/social/house/world_house_furniture.tables.toml"
    const val OBJECTS_FILE = "data/social/house/world_houses.objs.toml"
    const val VARPS_FILE = "data/social/house/world_houses.varps.toml"

    val VISITOR_OPTIONS = listOf("Open", "Knock-at")
    val SALE_OPTIONS = listOf("Buy")
    val OWNER_OPTIONS = listOf("Open", "Manage")

    /** The copies a house's door turns into, by varp value */
    val KINDS = listOf("visitor", "sale", "owner")

    data class House(val id: String, val name: String, val door: String, val x: Int, val y: Int)

    data class Piece(val house: String, val piece: String, val x: Int, val y: Int, val mapObject: Int, val hotspot: String?, val unlit: Int?)

    /** The house's own door on the map, ending in _closed like every door content opens */
    fun doorId(house: String) = "world_house_${house}_door_closed"

    fun copyId(house: String, kind: String) = "world_house_${house}_${kind}_door"

    fun pieceId(house: String, piece: String) = "world_house_${house}_$piece"

    fun buildId(house: String, piece: String) = "world_house_${house}_${piece}_build"

    fun unlitId(house: String, piece: String) = "world_house_${house}_${piece}_unlit"

    fun varpId(house: String) = "world_house_${house}_view"

    /** Rows of a tables file as row id to its values */
    private fun rows(file: File): List<Pair<String, Map<String, String>>> {
        val rows = mutableListOf<Pair<String, Map<String, String>>>()
        var values = mutableMapOf<String, String>()
        for (line in file.readLines()) {
            val header = Regex("^\\[\\.(\\w+)]").find(line)
            if (header != null) {
                values = mutableMapOf()
                rows.add(header.groupValues[1] to values)
                continue
            }
            val pair = Regex("^(\\w+)\\s*=\\s*\"?(.+?)\"?\\s*$").find(line) ?: continue
            values[pair.groupValues[1]] = pair.groupValues[2]
        }
        return rows
    }

    private fun tile(value: String): Pair<Int, Int> {
        val match = Regex("x\\s*=\\s*(\\d+)\\s*,\\s*y\\s*=\\s*(\\d+)").find(value) ?: error("Not a tile: '$value'.")
        return match.groupValues[1].toInt() to match.groupValues[2].toInt()
    }

    fun houses(file: File = File(HOUSES)): List<House> = rows(file).map { (id, values) ->
        val (x, y) = tile(values["door"] ?: error("House $id has no door."))
        House(id, values.getValue("name"), values.getValue("door_object"), x, y)
    }

    fun pieces(file: File = File(FURNITURE)): List<Piece> = rows(file).map { (id, values) ->
        val (x, y) = tile(values["tile"] ?: error("Furniture $id has no tile."))
        Piece(values.getValue("house"), values.getValue("piece"), x, y, values.getValue("map_object").toInt(), values["hotspot"], values["unlit_object"]?.toInt())
    }

    /** Numeric ids of every object in *.objs.toml */
    fun objectIds(data: File = File("data")): Map<String, Int> {
        val ids = mutableMapOf<String, Int>()
        val section = Regex("^\\[(\\w+)]([^\\[]*)", RegexOption.MULTILINE)
        val id = Regex("^id\\s*=\\s*(\\d+)", RegexOption.MULTILINE)
        for (file in data.walkTopDown().filter { it.name.endsWith(".objs.toml") }) {
            for (match in section.findAll(file.readText())) {
                ids[match.groupValues[1]] = id.find(match.groupValues[2])?.groupValues?.get(1)?.toInt() ?: continue
            }
        }
        return ids
    }

    /** Varps kept in [VARPS_FILE] by name */
    fun varps(file: File = File(VARPS_FILE)): Map<String, Int> {
        if (!file.exists()) {
            return emptyMap()
        }
        return Regex("^\\[(\\w+)]\\s*\\nid\\s*=\\s*(\\d+)", RegexOption.MULTILINE).findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2].toInt() }
    }

    /** Varps from 1900 nothing uses */
    fun freeVarps(cache: Cache, definitions: Array<ObjectDefinitionFull>, varps: Int, ours: Collection<Int>): List<Int> {
        val npcs = NPCDecoderFull(members = true).load(cache).map { it.varp }.toSet()
        return AutoMiningObjects.freeVarps(cache, definitions, varps).filter { it >= 1900 && it != AutoMiningObjects.VARP && it !in npcs && it !in ours }
    }

    data class Change(val name: String, val id: Int, val data: ByteArray)

    /** The map has [from] at [x], [y] which becomes [to] */
    data class Swap(val x: Int, val y: Int, val from: Int, val to: String)

    class Plan(val changes: List<Change>, val swaps: List<Swap>)

    fun plan(cache: Cache, definitions: Array<ObjectDefinitionFull>, houses: List<House>, pieces: List<Piece>, objects: Map<String, Int>, varps: Map<String, Int>): Plan {
        val last = cache.lastArchiveId(Index.OBJECTS)
        var next = last * 256 + cache.lastFileId(Index.OBJECTS, last) + 1
        val given = mutableMapOf<String, Int>()
        fun id(name: String) = objects[name] ?: given.getOrPut(name) {
            // Never write over an object which isn't one of ours
            while (cache.data(Index.OBJECTS, next ushr 8, next and 0xff) != null) {
                next++
            }
            next++
        }
        val changes = mutableListOf<Change>()
        val swaps = mutableListOf<Swap>()
        for (house in houses) {
            val varp = varps.getValue(varpId(house.id))
            val original = objects[house.door] ?: error("Unknown door object '${house.door}' for house ${house.id}.")
            val base = data(cache, original)
            val copies = KINDS.map { kind ->
                val data = append(base) {
                    when (kind) {
                        "visitor" -> options(VISITOR_OPTIONS)
                        "sale" -> {
                            writeByte(2)
                            writeString(house.name)
                            options(SALE_OPTIONS)
                        }
                        "owner" -> options(OWNER_OPTIONS)
                    }
                }
                Change(copyId(house.id, kind), id(copyId(house.id, kind)), data)
            }
            changes.addAll(copies)
            changes.add(Change(doorId(house.id), id(doorId(house.id)), append(base) { morph(varp, copies[0].id, copies.map { it.id }) }))
            swaps.add(Swap(house.x, house.y, original, doorId(house.id)))
        }
        for ((key, group) in pieces.groupBy { it.house to it.piece }) {
            val (house, piece) = key
            val first = group.first()
            check(group.all { it.mapObject == first.mapObject && it.hotspot == first.hotspot && it.unlit == first.unlit }) { "Pieces '$piece' of house $house differ." }
            val base = data(cache, first.mapObject)
            val own = id(pieceId(house, piece))
            if (first.unlit != null) {
                changes.add(Change(pieceId(house, piece), own, append(base) { option(firstFree(definitions[first.mapObject]), "Extinguish") }))
                val unlit = withoutBrightness(data(cache, first.unlit))
                changes.add(Change(unlitId(house, piece), id(unlitId(house, piece)), append(unlit) { option(firstFree(definitions[first.unlit]), "Light") }))
            } else {
                checkNotNull(first.hotspot) { "Pieces '$piece' of house $house have neither a hotspot nor an unlit object." }
                val build = id(buildId(house, piece))
                changes.add(Change(buildId(house, piece), build, append(base) { option(firstFree(definitions[first.mapObject]), "Build") }))
                // Turns into itself for visitors and while the house is for sale, into the build copy for the owner
                changes.add(Change(pieceId(house, piece), own, append(base) { morph(varps.getValue(varpId(house)), own, listOf(own, own, build)) }))
            }
            for (each in group) {
                swaps.add(Swap(each.x, each.y, each.mapObject, pieceId(house, piece)))
            }
        }
        return Plan(changes, swaps)
    }

    private fun data(cache: Cache, id: Int): ByteArray {
        val data = cache.data(Index.OBJECTS, id ushr 8, id and 0xff) ?: error("Object $id isn't in the cache.")
        check(data.last() == 0.toByte()) { "Object $id doesn't end in opcode 0." }
        return data
    }

    private fun firstFree(definition: ObjectDefinitionFull): Int {
        val options = definition.options ?: return 0
        return (0 until 5).first { options[it] == null }
    }

    /** [data] without the brightness and contrast opcodes it starts with */
    private fun withoutBrightness(data: ByteArray): ByteArray {
        var start = 0
        while (data[start] == 29.toByte() || data[start] == 39.toByte()) {
            start += 2
        }
        return data.copyOfRange(start, data.size)
    }

    private fun ArrayWriter.morph(varp: Int, default: Int, values: List<Int>) {
        writeByte(92)
        writeShort(65535) // no varbit
        writeShort(varp)
        writeShort(default)
        writeByte(values.size - 1)
        for (value in values) {
            writeShort(value)
        }
    }

    private fun ArrayWriter.options(options: List<String>) {
        for ((slot, option) in options.withIndex()) {
            option(slot, option)
        }
    }

    private fun ArrayWriter.option(slot: Int, option: String) {
        writeByte(30 + slot)
        writeString(option)
    }

    /** [data] with the opcodes of [block] before its terminating 0 */
    private fun append(data: ByteArray, block: ArrayWriter.() -> Unit): ByteArray {
        val writer = ArrayWriter(data.size + 128)
        writer.writeBytes(data, 0, data.size - 1)
        block(writer)
        writer.writeByte(0)
        return writer.toArray()
    }

    data class MapChange(val region: Region, val data: ByteArray, val changed: List<Swap>)

    /** The map regions with each [Swap] applied */
    fun maps(cache: Cache, swaps: List<Swap>, ids: Map<String, Int>): List<MapChange> {
        val decoder = MapObjectDefinitionDecoder()
        val encoder = MapObjectEncoder()
        return swaps.groupBy { Region(it.x shr 6, it.y shr 6) }.map { (region, inRegion) ->
            val original = cache.data(Index.MAPS, "l${region.x}_${region.y}") ?: error("No map objects for region ${region.id}.")
            val definition = MapDefinition(region.id)
            decoder.decode(cache, definition, modified = false)
            check(encode(encoder, definition).contentEquals(original)) { "Region ${region.id} doesn't encode back to the same bytes; not touching it." }
            val changed = mutableListOf<Swap>()
            for (swap in inRegion) {
                val to = ids.getValue(swap.to)
                val localX = swap.x - region.tile.x
                val localY = swap.y - region.tile.y
                val index = definition.objects.indexOfFirst { it.x == localX && it.y == localY && it.level == 0 && (it.id == swap.from || it.id == to) }
                check(index != -1) { "No object ${swap.from} at ${swap.x}, ${swap.y}." }
                val obj = definition.objects[index]
                if (obj.id != to) {
                    definition.objects[index] = MapObject(to, obj.x, obj.y, obj.level, obj.shape, obj.rotation)
                    changed.add(swap)
                }
            }
            MapChange(region, if (changed.isEmpty()) original else encode(encoder, definition), changed)
        }
    }

    private fun encode(encoder: MapObjectEncoder, definition: MapDefinition): ByteArray {
        val writer = ArrayWriter(100_000)
        with(encoder) {
            writer.encode(definition)
        }
        return writer.toArray()
    }

    fun apply(library: CacheLibrary, cache: Cache, changes: List<Change>, maps: List<MapChange>): Int {
        var written = 0
        for (change in changes) {
            if (change.data.contentEquals(cache.data(Index.OBJECTS, change.id ushr 8, change.id and 0xff))) {
                continue
            }
            library.put(Index.OBJECTS, change.id ushr 8, change.id and 0xff, change.data)
            written++
        }
        for (map in maps.filter { it.changed.isNotEmpty() }) {
            library.put(Index.MAPS, "l${map.region.x}_${map.region.y}", map.data)
            written++
        }
        library.update()
        return written
    }

    fun writeObjects(changes: List<Change>, file: File = File(OBJECTS_FILE)) {
        val lines = mutableListOf(
            "# Objects of the houses in world_houses.tables.toml and world_house_furniture.tables.toml,",
            "# written by `./gradlew :tools:worldHouseObjects --args=patch` (see WorldHouseObjects).",
        )
        for (change in changes) {
            lines += ""
            lines += "[${change.name}]"
            lines += "id = ${change.id}"
            if (change.name.endsWith("_door_closed")) {
                lines += "opened = \"door_opened\""
            }
        }
        file.writeText(lines.joinToString("\n") + "\n")
    }

    fun writeVarps(varps: Map<String, Int>, file: File = File(VARPS_FILE)) {
        val lines = mutableListOf("# How each house's door and furniture look to the player: 0 visitor, 1 for sale, 2 owner (WorldHouseObjects)")
        for ((name, id) in varps) {
            lines += ""
            lines += "[$name]"
            lines += "id = $id"
            lines += "format = \"int\""
        }
        file.writeText(lines.joinToString("\n") + "\n")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.firstOrNull() ?: "check"
        val path = args.indexOf("--cache").takeIf { it != -1 }?.let { args[it + 1] } ?: "./data/cache/"
        val library = CacheLibrary(path)
        val cache = CacheDelegate(library)
        val definitions = ObjectDecoderFull(members = true, lowDetail = false).load(cache)
        val houses = houses()
        val pieces = pieces()
        val objects = objectIds()
        val kept = varps()
        val count = cache.lastFileId(Index.CONFIGS, Config.VARP) + 1
        val free: MutableList<Int> = if (houses.all { varpId(it.id) in kept }) mutableListOf() else freeVarps(cache, definitions, count, kept.values).toMutableList()
        val varps = houses.associate { varpId(it.id) to (kept[varpId(it.id)] ?: free.removeAt(0)) }
        check(varps.values.all { it < count }) { "Varps $varps go past the $count varps the client keeps." }
        val plan = plan(cache, definitions, houses, pieces, objects, varps)
        val ids = plan.changes.associate { it.name to it.id }
        val maps = maps(cache, plan.swaps, ids)
        println("Varps $varps")
        for (change in plan.changes) {
            val changed = !change.data.contentEquals(cache.data(Index.OBJECTS, change.id ushr 8, change.id and 0xff))
            println("  ${change.name} -> ${change.id}${if (changed) " (changes)" else ""}")
        }
        for (map in maps) {
            println("  map l${map.region.x}_${map.region.y}: ${map.changed.size} of ${plan.swaps.count { Region(it.x shr 6, it.y shr 6) == map.region }} objects to swap")
        }
        when (mode) {
            "check" -> return
            "patch" -> {
                if (path == "./data/cache/" && !portFree(43594)) {
                    System.err.println("The game server is running: stop it before changing the cache it serves.")
                    exitProcess(1)
                }
                println("Updated ${apply(library, cache, plan.changes, maps)} cache files.")
                if (path != "./data/cache/") {
                    println("Not writing $OBJECTS_FILE: it would name objects the server's cache doesn't have.")
                    return
                }
                writeObjects(plan.changes)
                writeVarps(varps)
                println("Wrote $OBJECTS_FILE and $VARPS_FILE.")
            }
            else -> {
                System.err.println("Unknown mode '$mode', expected check or patch.")
                exitProcess(1)
            }
        }
    }

    private fun portFree(port: Int): Boolean = try {
        ServerSocket(port).close()
        true
    } catch (e: Exception) {
        false
    }
}
