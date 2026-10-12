package world.gregs.voidps.mapeditor

import world.gregs.config.Config
import world.gregs.voidps.cache.definition.data.MapDefinition
import world.gregs.voidps.cache.definition.data.MapObject
import world.gregs.voidps.cache.definition.data.MapTile
import world.gregs.voidps.type.Region
import java.io.File

/**
 * Text form of a map region that can be edited by hand and read back without losing anything.
 *
 * Each level is a set of 64x64 grids with north at the top (first row is local y 63) and west on the left.
 * Objects use world coordinates. [MapDefinition.extras] is kept as a list of bytes.
 */
object RegionFile {

    private val layers = listOf("height", "overlay", "overlay_shape", "overlay_rotation", "settings", "underlay")

    fun write(definition: MapDefinition, file: File) {
        file.absoluteFile.parentFile.mkdirs()
        file.writeText(toText(definition))
    }

    fun toText(definition: MapDefinition): String {
        val region = Region(definition.id)
        val builder = StringBuilder()
        builder.append("# Region ${region.id} covers x ${region.tile.x}..${region.tile.x + 63}, y ${region.tile.y}..${region.tile.y + 63}\n")
        builder.append("# Grids: first row is the north edge (local y 63), first column the west edge (local x 0).\n")
        builder.append("# height 0 lets the client generate the height; overlay 0 means no overlay.\n")
        builder.append("[region]\n")
        builder.append("id = ${region.id}\n")
        val extras = definition.extras
        if (extras != null) {
            builder.append("extras = [").append(extras.joinToString(", ") { (it.toInt() and 0xff).toString() }).append("]\n")
        }
        for (level in 0 until 4) {
            builder.append("\n[level_$level]\n")
            for (layer in layers) {
                builder.append("$layer = [\n")
                for (localY in 63 downTo 0) {
                    builder.append("    [")
                    for (localX in 0 until 64) {
                        if (localX > 0) {
                            builder.append(", ")
                        }
                        builder.append(value(definition.getTile(localX, localY, level), layer))
                    }
                    builder.append("],\n")
                }
                builder.append("]\n")
            }
        }
        builder.append("\n[objects]\n")
        builder.append("list = [\n")
        for (obj in definition.objects.sortedWith(compareBy({ it.level }, { it.x }, { it.y }, { it.id }))) {
            builder.append("    { id = ${obj.id}, x = ${region.tile.x + obj.x}, y = ${region.tile.y + obj.y}, level = ${obj.level}, shape = ${obj.shape}, rotation = ${obj.rotation} },\n")
        }
        builder.append("]\n")
        return builder.toString()
    }

    fun read(file: File): MapDefinition {
        val definition = MapDefinition()
        Config.fileReader(file) {
            read(definition)
        }
        return definition
    }

    fun fromText(text: String): MapDefinition {
        val definition = MapDefinition()
        Config.stringReader(text) {
            read(definition)
        }
        return definition
    }

    private fun world.gregs.config.ConfigReader.read(definition: MapDefinition) {
        val grids = Array(4) { mutableMapOf<String, List<*>>() }
        while (nextSection()) {
            val section = section()
            while (nextPair()) {
                val key = key()
                when {
                    section == "region" && key == "id" -> definition.id = int()
                    section == "region" && key == "extras" -> definition.extras = (value() as List<*>).map { (it as Number).toInt().toByte() }.toByteArray()
                    section.startsWith("level_") -> grids[section.removePrefix("level_").toInt()][key] = value() as List<*>
                    section == "objects" && key == "list" -> readObjects(definition, value() as List<*>)
                    else -> throw IllegalArgumentException("Unknown key '$key' in [$section]")
                }
            }
        }
        require(definition.id != -1) { "Missing [region] id" }
        for (level in 0 until 4) {
            readTiles(definition, level, grids[level])
        }
    }

    private fun readTiles(definition: MapDefinition, level: Int, grids: Map<String, List<*>>) {
        val values = layers.associateWith { layer ->
            val rows = grids[layer] ?: throw IllegalArgumentException("Missing '$layer' in [level_$level]")
            require(rows.size == 64) { "'$layer' in [level_$level] has ${rows.size} rows, expected 64" }
            rows.map { row ->
                row as List<*>
                require(row.size == 64) { "'$layer' in [level_$level] has a row of ${row.size} values, expected 64" }
                row.map { (it as Number).toInt() }
            }
        }
        for (localY in 0 until 64) {
            val row = 63 - localY
            for (localX in 0 until 64) {
                fun get(layer: String) = values.getValue(layer)[row][localX]
                val overlay = get("overlay")
                val shape = get("overlay_shape")
                val rotation = get("overlay_rotation")
                val opcode = if (overlay != 0 || shape != 0 || rotation != 0) 2 + shape * 4 + rotation else 0
                definition.setTile(localX, localY, level, MapTile(get("height"), opcode, overlay, shape, rotation, get("settings"), get("underlay")))
            }
        }
    }

    private fun readObjects(definition: MapDefinition, list: List<*>) {
        val region = Region(definition.id)
        for (entry in list) {
            val obj = entry as Map<*, *>
            fun get(key: String) = (obj[key] as? Number)?.toInt() ?: throw IllegalArgumentException("Object $obj is missing '$key'")
            val x = get("x") - region.tile.x
            val y = get("y") - region.tile.y
            require(x in 0 until 64 && y in 0 until 64) { "Object $obj is outside region ${region.id}" }
            definition.objects.add(MapObject(get("id"), x, y, get("level"), get("shape"), get("rotation")))
        }
    }

    private fun value(tile: MapTile, layer: String): Int = when (layer) {
        "height" -> tile.height
        "overlay" -> tile.overlayId
        "overlay_shape" -> tile.overlayPath
        "overlay_rotation" -> tile.overlayRotation
        "settings" -> tile.settings
        "underlay" -> tile.underlayId
        else -> throw IllegalArgumentException(layer)
    }
}
