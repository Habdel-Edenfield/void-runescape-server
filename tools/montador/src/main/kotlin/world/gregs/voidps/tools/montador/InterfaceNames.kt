package world.gregs.voidps.tools.montador

import world.gregs.config.Config
import java.io.File

/**
 * The names Void gives to interfaces and components (every `*.ifaces.toml` under the data directory)
 * and where each interface type is opened (`interface_types.toml`), read the same way as
 * `InterfaceDefinitions.load` but without loading the cache definitions.
 */
class InterfaceNames(dataDir: File) {
    /** Interface id -> string id. */
    val names = mutableMapOf<Int, String>()

    /** Interface id -> type ("main_screen", "inventory_tab", ...). */
    val types = mutableMapOf<Int, String>()

    /** Interface id -> component id -> string id. */
    val components = mutableMapOf<Int, MutableMap<Int, String>>()

    /** Interface id -> the `*.ifaces.toml` file that names it (relative to the data directory). */
    val files = mutableMapOf<Int, String>()

    /** Type -> where interfaces of that type are opened. */
    val slots = mutableMapOf<String, Slot>()

    data class Slot(val fixedParent: Int, val fixedIndex: Int, val resizeParent: Int, val resizeIndex: Int)

    init {
        val ids = mutableMapOf<String, Int>()
        val tomls = dataDir.walkTopDown().filter { it.isFile && it.name.endsWith(".ifaces.toml") }.sortedBy { it.path }.toList()
        for (file in tomls) {
            var parent = -1
            Config.fileReader(file) {
                while (nextSection()) {
                    val section = section()
                    if (section.contains(".")) {
                        val key = section.substringAfter(".")
                        while (nextPair()) {
                            if (key() != "id") {
                                value()
                                continue
                            }
                            val id = value()
                            if (parent == -1) {
                                continue
                            }
                            val map = components.getOrPut(parent) { mutableMapOf() }
                            if (id is Int) {
                                map[id] = key
                                continue
                            }
                            // "1-5" ranges name key1..key5 from the digits at the end of the key
                            val range = id.toString().split("-").mapNotNull { it.trim().toIntOrNull() }
                            if (range.size != 2) {
                                continue
                            }
                            val digits = key.takeLastWhile { it.isDigit() }
                            val start = digits.toIntOrNull() ?: 0
                            val prefix = key.removeSuffix(digits)
                            for ((index, componentId) in (range[0]..range[1]).withIndex()) {
                                map[componentId] = "$prefix${start + index}"
                            }
                        }
                        continue
                    }
                    parent = -1
                    var type: String? = null
                    while (nextPair()) {
                        when (key()) {
                            "id" -> parent = int()
                            "type" -> type = string()
                            else -> value()
                        }
                    }
                    if (parent >= 0) {
                        names[parent] = section
                        ids[section] = parent
                        files[parent] = file.relativeTo(dataDir).path
                        if (type != null) {
                            types[parent] = type
                        }
                    }
                }
            }
        }
        val typesFile = dataDir.walkTopDown().firstOrNull { it.isFile && it.name == "interface_types.toml" }
        if (typesFile != null) {
            val fixedFrame = ids["toplevel"] ?: -1
            val resizeFrame = ids["toplevel_full"] ?: -1
            Config.fileReader(typesFile) {
                while (nextSection()) {
                    val type = section()
                    var fixedParent = fixedFrame
                    var resizeParent = resizeFrame
                    var fixedIndex = -1
                    var resizeIndex = -1
                    while (nextPair()) {
                        when (key()) {
                            "index" -> {
                                fixedIndex = int()
                                resizeIndex = fixedIndex
                            }
                            "parent" -> {
                                fixedParent = ids[string()] ?: -1
                                resizeParent = fixedParent
                            }
                            "fixedIndex" -> fixedIndex = int()
                            "resizeIndex" -> resizeIndex = int()
                            else -> value()
                        }
                    }
                    slots[type] = Slot(fixedParent, fixedIndex, resizeParent, resizeIndex)
                }
            }
        }
    }

    companion object {
        const val DEFAULT_TYPE = "main_screen"
    }
}
