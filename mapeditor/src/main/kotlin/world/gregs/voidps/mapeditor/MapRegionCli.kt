package world.gregs.voidps.mapeditor

import world.gregs.voidps.cache.definition.data.MapDefinition
import world.gregs.voidps.type.Region
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

/**
 * Exports a map region to an editable toml file and imports it back into a cache.
 *
 * export <region id | x,y> [--cache dir] [--out file]
 * import <file> --cache <dir>
 * import <file> --live          (writes data/cache/ after backing it up to mapeditor/backups/)
 */
object MapRegionCli {

    private const val LIVE_CACHE = "./data/cache/"
    private const val REGIONS = "./mapeditor/regions/"
    private const val BACKUPS = "./mapeditor/backups/"

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            run(args.toList())
        } catch (e: IllegalArgumentException) {
            System.err.println(e.message)
            System.err.println(USAGE)
            exitProcess(1)
        }
    }

    fun run(args: List<String>) {
        val command = args.firstOrNull() ?: throw IllegalArgumentException("Missing command.")
        val target = args.getOrNull(1) ?: throw IllegalArgumentException("Missing region or file.")
        val options = options(args.drop(2))
        when (command) {
            "export" -> export(parseRegion(target), options["cache"] ?: LIVE_CACHE, File(options["out"] ?: "$REGIONS${parseRegion(target).id}.toml"))
            "import" -> import(File(target), options["cache"], options.containsKey("live"))
            else -> throw IllegalArgumentException("Unknown command '$command'.")
        }
    }

    private fun export(region: Region, cachePath: String, out: File) {
        val definition = RegionStore(cachePath).load(region) ?: throw IllegalArgumentException("Region ${region.id} has no map in $cachePath")
        RegionFile.write(definition, out)
        println("Exported region ${region.id} (${definition.objects.size} objects) to ${out.path}")
    }

    private fun import(file: File, cachePath: String?, live: Boolean) {
        require(file.exists()) { "File not found: ${file.path}" }
        val path = when {
            live && cachePath != null -> throw IllegalArgumentException("Use either --cache or --live, not both.")
            live -> LIVE_CACHE
            cachePath == null -> throw IllegalArgumentException("Pick the cache to write: --cache <copy of the cache> or --live for data/cache/.")
            isLive(cachePath) -> throw IllegalArgumentException("$cachePath is the server cache; use --live to write it.")
            else -> cachePath
        }
        val edited = RegionFile.read(file)
        val region = Region(edited.id)
        if (live) {
            val backup = File(BACKUPS, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")))
            File(LIVE_CACHE).canonicalFile.copyRecursively(backup)
            println("Backed up $LIVE_CACHE to ${backup.path}")
        }
        val store = RegionStore(path)
        val original = store.load(region)
        if (original != null) {
            println(describeChanges(original, edited))
        }
        store.write(edited)
        println("Wrote region ${region.id} to $path. Restart the server so clients download it.")
    }

    fun describeChanges(original: MapDefinition, edited: MapDefinition): String {
        val levels = (0 until 4).map { level ->
            var count = 0
            for (x in 0 until 64) {
                for (y in 0 until 64) {
                    if (original.getTile(x, y, level) != edited.getTile(x, y, level)) {
                        count++
                    }
                }
            }
            count
        }
        val before = original.objects.groupingBy { it }.eachCount()
        val after = edited.objects.groupingBy { it }.eachCount()
        val removed = before.entries.sumOf { (obj, count) -> (count - (after[obj] ?: 0)).coerceAtLeast(0) }
        val added = after.entries.sumOf { (obj, count) -> (count - (before[obj] ?: 0)).coerceAtLeast(0) }
        val extras = if (original.extras.contentEquals(edited.extras)) "unchanged" else "changed"
        return "Tiles changed per level: ${levels.joinToString()}; objects added: $added, removed: $removed; extras $extras"
    }

    private fun isLive(path: String) = File(path).canonicalFile == File(LIVE_CACHE).canonicalFile

    private fun parseRegion(value: String): Region {
        val parts = value.split(",")
        return when (parts.size) {
            1 -> Region(value.toIntOrNull() ?: throw IllegalArgumentException("Invalid region '$value'."))
            2 -> {
                val x = parts[0].trim().toIntOrNull() ?: throw IllegalArgumentException("Invalid x in '$value'.")
                val y = parts[1].trim().toIntOrNull() ?: throw IllegalArgumentException("Invalid y in '$value'.")
                Region(x / 64, y / 64)
            }
            else -> throw IllegalArgumentException("Invalid region '$value', use an id like 12850 or a tile like 3222,3218.")
        }
    }

    private fun options(args: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            require(arg.startsWith("--")) { "Unexpected argument '$arg'." }
            val key = arg.removePrefix("--")
            if (key == "live") {
                map[key] = "true"
                i++
            } else {
                map[key] = args.getOrNull(i + 1) ?: throw IllegalArgumentException("Missing value for $arg.")
                i += 2
            }
        }
        return map
    }

    private const val USAGE = """Usage:
  export <region id | x,y> [--cache dir] [--out file]
  import <file> --cache <dir>
  import <file> --live"""
}
