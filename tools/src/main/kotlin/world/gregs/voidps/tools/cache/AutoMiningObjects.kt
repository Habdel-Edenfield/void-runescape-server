package world.gregs.voidps.tools.cache

import com.displee.cache.CacheLibrary
import world.gregs.voidps.buffer.write.ArrayWriter
import world.gregs.voidps.cache.Cache
import world.gregs.voidps.cache.CacheDelegate
import world.gregs.voidps.cache.Config
import world.gregs.voidps.cache.Index
import world.gregs.voidps.cache.definition.data.ObjectDefinitionFull
import world.gregs.voidps.cache.definition.decoder.ClientScriptDecoder
import world.gregs.voidps.cache.definition.decoder.MapObjectDecoder
import world.gregs.voidps.cache.definition.decoder.ObjectDecoderFull
import world.gregs.voidps.cache.definition.decoder.VarBitDecoder
import world.gregs.voidps.engine.data.definition.AreaDefinition
import world.gregs.voidps.engine.data.definition.Areas
import world.gregs.voidps.type.Distance
import world.gregs.voidps.type.Tile
import java.io.File
import java.net.ServerSocket
import kotlin.math.abs
import kotlin.system.exitProcess

/**
 * Gives every rock with ore an "Auto-mine" right click option which only shows inside the areas tagged `auto_mining`.
 *
 * The option list of an object is the same everywhere, so each rock gets a copy with the extra option appended to the
 * end of the object list, and the rock itself turns into the copy while the player variable [VARP] is 1. The server
 * sets it on entering an `auto_mining` area and clears it on leaving (content.skill.mining.AutoMining).
 *
 * Both are made from the original bytes with opcodes added before the terminating 0, so nothing else about the rock
 * changes. Running it again only reapplies missing changes.
 *
 * Usage (from the repository root, with the server stopped for `patch`):
 *   check - lists what would change
 *   patch - writes the changes to the cache
 *   areas - rewrites [AREAS_FILE] with one area around every group of rocks on the map (every mine)
 * `--cache <dir>` points at another cache, e.g. a copy to try a change on.
 */
object AutoMiningObjects {

    const val OPTION = "Auto-mine"

    /** `auto_mining_area` in data/skill/mining/mining.varps.toml */
    const val VARP = 2042

    private const val ROCKS_TABLE = "data/skill/mining/rocks.tables.toml"
    const val AREAS_FILE = "data/skill/mining/auto_mining.areas.toml"

    /** Rocks closer than this many tiles belong to the same mine */
    private const val MINE_GAP = 10

    /** Tiles around the outer rocks of a mine which still count as inside it */
    private const val MARGIN = 4

    data class Rock(val name: String, val x: Int, val y: Int, val level: Int)

    /** Every rock with ore placed by the map */
    fun mapRocks(cache: Cache, rocks: Map<String, Int>): List<Rock> {
        val names = rocks.entries.associate { (name, id) -> id to name }
        val found = mutableListOf<Rock>()
        val decoder = object : MapObjectDecoder() {
            override fun add(objectId: Int, localX: Int, localY: Int, level: Int, shape: Int, rotation: Int, regionTileX: Int, regionTileY: Int) {
                val name = names[objectId] ?: return
                found.add(Rock(name, regionTileX + localX, regionTileY + localY, level))
            }
        }
        val settings = ByteArray(16384)
        for (regionX in 0 until 256) {
            for (regionY in 0 until 256) {
                val data = cache.data(Index.MAPS, "l${regionX}_$regionY") ?: continue
                decoder.decode(data, settings, regionX shl 6, regionY shl 6)
            }
        }
        return found
    }

    /** Groups [rocks] into mines: rocks on the same level less than [MINE_GAP] tiles from another rock of the mine */
    fun mines(rocks: List<Rock>): List<List<Rock>> {
        val mines = mutableListOf<MutableList<Rock>>()
        for (rock in rocks.sortedWith(compareBy({ it.level }, { it.x }, { it.y }))) {
            val near = mines.filter { mine -> mine.any { it.level == rock.level && abs(it.x - rock.x) < MINE_GAP && abs(it.y - rock.y) < MINE_GAP } }
            val mine = near.firstOrNull() ?: mutableListOf<Rock>().also { mines.add(it) }
            for (other in near.drop(1)) {
                mine.addAll(other)
                mines.remove(other)
            }
            mine.add(rock)
        }
        return mines.sortedWith(compareBy({ it.minOf { r -> r.x } }, { it.minOf { r -> r.y } }))
    }

    fun writeAreas(mines: List<List<Rock>>, out: File = File(AREAS_FILE)) {
        val paths = File("data").walkTopDown().filter { it.name.endsWith(".areas.toml") && it.path != out.path }.map { it.path }.toList()
        val (tutorial, places) = Areas.load(paths).getAll().partition { it.name.startsWith("tutorial_island") }
        val lines = mutableListOf(
            "# Areas where rocks show the \"Auto-mine\" option (content.skill.mining.AutoMining).",
            "# Made for every mine on the map by `./gradlew :tools:autoMiningObjects --args=areas`, which rewrites this file.",
            "#",
            "# To turn a mine off: delete its block. To add an area: copy a block, give it a new name and change the corners,",
            "# x = [west, east] and y = [south, north] (the coordinates the game shows for the corner tiles), and level for",
            "# the floor (0 ground, 1 first floor...). Keep the tag line as it is, then restart the server.",
            "# Cover the whole mine: while a player is inside, every rock they can see shows the option, and rocks outside the",
            "# area only answer \"You can't auto-mine here.\".",
        )
        val used = mutableMapOf<String, Int>()
        var count = 0
        for (mine in mines) {
            val minX = mine.minOf { it.x } - MARGIN
            val maxX = mine.maxOf { it.x } + MARGIN
            val minY = mine.minOf { it.y } - MARGIN
            val maxY = mine.maxOf { it.y } + MARGIN
            val level = mine.first().level
            val centre = Tile((minX + maxX) / 2, (minY + maxY) / 2, level)
            if (tutorial.any { centre in it.area }) {
                continue // Tutorial Island teaches mining step by step
            }
            val ores = mine.groupingBy { it.name.substringBefore("_rocks").substringBefore("_deposit").removePrefix("mineral_") }.eachCount()
            val ore = ores.maxBy { it.value }.key
            val base = "auto_mining_" + (place(places, centre)?.let { if (it.endsWith(ore)) it else "${it}_$ore" } ?: "${ore}_${centre.x}_${centre.y}")
            val index = used.merge(base, 1, Int::plus)!!
            val name = if (index == 1) base else "${base}_$index"
            lines += ""
            lines += "# " + ores.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${it.value}" }
            lines += "[$name]"
            lines += "x = [$minX, $maxX]"
            lines += "y = [$minY, $maxY]"
            lines += "level = $level"
            lines += "tags = [\"$TAG\"]"
            count++
        }
        out.writeText(lines.joinToString("\n") + "\n")
        println("$count mines written to $out")
    }

    private const val TAG = "auto_mining"

    /** Words of area names which say what the area is for rather than where it is */
    private val NOT_PLACES = listOf("_multi_area", "_no_cannon", "_teleport", "_hunter_area", "greater_", "_mine")

    /** The smallest named area around [tile], or else the closest one up to 64 tiles away */
    private fun place(areas: List<AreaDefinition>, tile: Tile): String? {
        val around = areas.filter { tile in it.area }.minByOrNull { it.area.area }
        val area = around ?: areas
            .map { it to it.area.toZones(tile.level).minOfOrNull { zone -> Distance.chebyshev(tile.x, tile.y, zone.tile.x + 4, zone.tile.y + 4) } }
            .filter { (_, distance) -> distance != null && distance <= 64 }
            .minByOrNull { it.second!! }?.first
        var name = area?.name ?: return null
        for (word in NOT_PLACES) {
            name = name.replace(word, "")
        }
        return if (around == null) "near_$name" else name
    }

    /** Numeric object ids of the rock rows of [ROCKS_TABLE], read from every *.objs.toml */
    fun rockIds(data: File = File("data")): Map<String, Int> {
        val rows = Regex("^\\[\\.(\\w+)]", RegexOption.MULTILINE).findAll(File(ROCKS_TABLE).readText()).map { it.groupValues[1] }.toSet()
        val ids = mutableMapOf<String, Int>()
        val section = Regex("^\\[(\\w+)]([^\\[]*)", RegexOption.MULTILINE)
        val id = Regex("^id\\s*=\\s*(\\d+)", RegexOption.MULTILINE)
        for (file in data.walkTopDown().filter { it.name.endsWith(".objs.toml") }) {
            for (match in section.findAll(file.readText())) {
                val name = match.groupValues[1]
                if (name in rows) {
                    ids[name] = id.find(match.groupValues[2])?.groupValues?.get(1)?.toInt() ?: continue
                }
            }
        }
        return ids.filterKeys { !it.startsWith("depleted") && !it.startsWith("crashed_star") }.toSortedMap()
    }

    data class Change(val rock: String, val id: Int, val copy: Int, val slot: Int)

    fun changes(cache: Cache, definitions: Array<ObjectDefinitionFull>, rocks: Map<String, Int>): List<Change> {
        val last = cache.lastArchiveId(Index.OBJECTS)
        var next = last * 256 + cache.lastFileId(Index.OBJECTS, last) + 1
        val changes = mutableListOf<Change>()
        for ((name, id) in rocks) {
            val definition = definitions.getOrNull(id) ?: continue
            val transforms = definition.transforms
            if (transforms != null) {
                // Already patched: keep the copy made before
                if (definition.varp == VARP && transforms[0] == id) {
                    val copy = transforms[1]
                    changes.add(Change(name, id, copy, definitions[copy].options!!.indexOf(OPTION)))
                }
                continue
            }
            val options = definition.options ?: continue
            if (options[0] != "Mine") {
                continue
            }
            val slot = (0 until 5).firstOrNull { options[it] == null } ?: continue
            changes.add(Change(name, id, next++, slot))
        }
        return changes
    }

    fun apply(library: CacheLibrary, cache: Cache, changes: List<Change>): Int {
        var written = 0
        for (change in changes) {
            val base = strip(cache.data(Index.OBJECTS, change.id ushr 8, change.id and 0xff)!!)
            check(base.last() == 0.toByte()) { "Object ${change.id} doesn't end in opcode 0." }
            val copy = append(base) {
                writeByte(30 + change.slot)
                writeString(OPTION)
            }
            val morph = append(base) {
                writeByte(92)
                writeShort(65535) // no varbit
                writeShort(VARP)
                writeShort(change.id) // any other value: stay the rock
                writeByte(1) // two values
                writeShort(change.id)
                writeShort(change.copy)
            }
            written += put(library, cache, change.copy, copy) + put(library, cache, change.id, morph)
        }
        library.update()
        return written
    }

    private fun put(library: CacheLibrary, cache: Cache, id: Int, data: ByteArray): Int {
        if (data.contentEquals(cache.data(Index.OBJECTS, id ushr 8, id and 0xff))) {
            return 0
        }
        library.put(Index.OBJECTS, id ushr 8, id and 0xff, data)
        return 1
    }

    /** The original bytes, without a morph added by an earlier run */
    private fun strip(data: ByteArray): ByteArray = if (isPatched(data)) data.copyOf(data.size - MORPH_SIZE).also { it[it.lastIndex] = 0 } else data

    private const val MORPH_SIZE = 1 + 2 + 2 + 2 + 1 + 2 + 2

    private fun isPatched(data: ByteArray): Boolean {
        if (data.size <= MORPH_SIZE) {
            return false
        }
        val start = data.size - 1 - MORPH_SIZE
        val varp = ((data[start + 3].toInt() and 0xff) shl 8) or (data[start + 4].toInt() and 0xff)
        return data[start] == 92.toByte() && varp == VARP && data.last() == 0.toByte()
    }

    /** [data] with the opcodes of [block] before its terminating 0 */
    private fun append(data: ByteArray, block: ArrayWriter.() -> Unit): ByteArray {
        val writer = ArrayWriter(data.size + 64)
        writer.writeBytes(data, 0, data.size - 1)
        block(writer)
        writer.writeByte(0)
        return writer.toArray()
    }

    /** Varps no varbit, client script, object or server variable uses, besides [VARP] by the rocks */
    fun freeVarps(cache: Cache, definitions: Array<ObjectDefinitionFull>, varps: Int, data: File = File("data")): List<Int> {
        val used = mutableSetOf<Int>()
        for (varbit in VarBitDecoder().load(cache)) {
            used.add(varbit.index)
        }
        for (script in ClientScriptDecoder().load(cache)) {
            val operands = script.intOperands ?: continue
            for ((i, instruction) in script.instructions.withIndex()) {
                if (instruction == 1 || instruction == 2) { // get_varp, set_varp
                    used.add(operands[i])
                }
            }
        }
        for (definition in definitions) {
            if (definition.transforms?.get(0) != definition.id) { // Skip the rocks patched before
                used.add(definition.varp)
            }
        }
        val id = Regex("^id\\s*=\\s*(\\d+)", RegexOption.MULTILINE)
        for (file in data.walkTopDown().filter { it.name.endsWith(".varps.toml") && it.name != "mining.varps.toml" }) {
            id.findAll(file.readText()).forEach { used.add(it.groupValues[1].toInt()) }
        }
        return (0 until varps).filter { it !in used }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.firstOrNull() ?: "check"
        val path = args.indexOf("--cache").takeIf { it != -1 }?.let { args[it + 1] } ?: "./data/cache/"
        val library = CacheLibrary(path)
        val cache = CacheDelegate(library)
        val definitions = ObjectDecoderFull(members = true, lowDetail = false).load(cache)
        val varps = cache.lastFileId(Index.CONFIGS, Config.VARP) + 1
        val free = freeVarps(cache, definitions, varps)
        println("Unused varps from 1900: ${free.filter { it >= 1900 }}")
        check(VARP < varps) { "Varp $VARP is outside the $varps varps the client keeps." }
        check(VARP in free) { "Varp $VARP is already used." }
        val selfMorphs = definitions.filter { it.transforms?.get(0) == it.id && it.varp != VARP }
        println("Other objects turning into themselves: ${selfMorphs.map { "${it.id} ${it.name} -> ${it.transforms!!.toList()}" }}")
        val changes = changes(cache, definitions, rockIds())
        println("${definitions.size} objects, $varps varps, ${changes.size} rocks:")
        for (change in changes) {
            println("  ${change.rock} ${change.id} -> ${change.copy} option ${change.slot + 1}")
        }
        when (mode) {
            "check" -> return
            "areas" -> writeAreas(mines(mapRocks(cache, rockIds())))
            "patch" -> {
                if (path == "./data/cache/" && !portFree(43594)) {
                    System.err.println("The game server is running: stop it before changing the cache it serves.")
                    exitProcess(1)
                }
                println("Updated ${apply(library, cache, changes)} cache files.")
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
