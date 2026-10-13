package world.gregs.voidps.cache.translation

import world.gregs.voidps.buffer.read.ArrayReader
import world.gregs.voidps.buffer.write.ArrayWriter
import world.gregs.voidps.cache.Cache
import world.gregs.voidps.cache.Index
import world.gregs.voidps.cache.config.data.StructDefinition
import world.gregs.voidps.cache.config.decoder.StructDecoder
import world.gregs.voidps.cache.definition.data.ClientScriptDefinition
import world.gregs.voidps.cache.definition.data.EnumDefinition
import world.gregs.voidps.cache.definition.data.InterfaceComponentDefinitionFull
import world.gregs.voidps.cache.definition.data.InterfaceDefinitionFull
import world.gregs.voidps.cache.definition.decoder.ClientScriptDecoder
import world.gregs.voidps.cache.definition.decoder.EnumDecoder
import world.gregs.voidps.cache.definition.decoder.InterfaceDecoderFull
import world.gregs.voidps.cache.definition.encoder.ClientScriptEncoder
import world.gregs.voidps.cache.definition.encoder.EnumEncoder
import java.io.File

/**
 * Brazilian Portuguese for interfaces the client draws itself from the cache: the Task System (task list
 * window, sidebar tab and completion pop-up) and the level up pop-up (`skill_level_details`).
 *
 * The server can't reach their text, so the Portuguese is added to the cache
 * next to the English and the interface scripts are changed to pick it when the client variable
 * [LANGUAGE_VARC] (`portuguese_dialogue` on the server) is 1. Each player still chooses their language
 * and the English stays untouched.
 *
 * Every change is a one-for-one instruction swap, so no jump inside an original script moves:
 * - `struct_param` of a task text becomes a call to [TASK_TEXT] (task name, description, hints, set text)
 * - `enum` of the area and difficulty names becomes a call to [TASK_ENUM], `enum_string` of the level up
 *   messages a call to [ENUM_STRING]
 * - each translated string constant becomes a call to a script returning the English or the Portuguese
 * - the generic scripts hooks pass text to (tooltips, the level up link) get one call added to translate it
 *
 * This only works the patch out; `InterfaceTranslation` in tools writes it to the cache.
 */
object CacheTranslationPatch {

    const val LANGUAGE_VARC = 2000
    const val TABLE_PATH = "data/entity/player/dialogue/translation/interface_pt_br.tables.toml"

    /** Translation sources in tools/translation/sources, `<prefix>*.json` mapping English to Portuguese */
    val SOURCE_PREFIXES = listOf("pt_tasks", "pt_level_up", "pt_labels")

    /** Task sets translated so far: Lumbridge/Draynor and the tutorial's Unstable Foundations */
    val AREAS = setOf(1, 60)

    const val TASK_STRUCTS = 3483
    const val AREA_NAMES = 3487
    const val DIFFICULTY_NAMES = 3488
    const val TASK_NAME = 1266
    private const val TASK_AREA = 1267
    const val TASK_DESCRIPTION = 1273
    const val TASK_SET = 1292
    val HINTS = 1274..1279
    val TEXT_PARAMS = listOf(TASK_NAME, TASK_DESCRIPTION) + HINTS + TASK_SET
    const val TOOLTIP_SCRIPT = 38

    /** "You've just advanced a Cooking level!" for each skill, read with `enum_string` */
    const val LEVEL_UP_MESSAGES = 1477

    val INTERFACES = mapOf("task_list" to 917, "task_system" to 1056, "task_popup" to 1055, "skill_level_details" to 741)

    /**
     * Generic scripts hooks pass text to, with the instructions they must still have and where the translation
     * call goes: the tooltip script (38) and the level up link's hover (2381: set text and colour).
     */
    val HOOK_SCRIPTS = mapOf(
        TOOLTIP_SCRIPT to (intArrayOf(Op.ILOAD, Op.PUSH_INT, Op.ILOAD, Op.SLOAD, Op.ILOAD, Op.ILOAD, Op.GOSUB, Op.RETURN) to 4),
        2381 to (intArrayOf(Op.SLOAD, Op.ILOAD, 2112, Op.ILOAD, Op.ILOAD, 2101, Op.RETURN) to 1),
    )

    const val TASK_TEXT = "pt_br_task_text"
    const val TASK_ENUM = "pt_br_task_enum"
    const val ENUM_STRING = "pt_br_enum_string"
    const val STRING = "pt_br_string"
    const val TOOLTIP = "pt_br_tooltip"
    private const val ENUM_MARKER = Int.MIN_VALUE

    /** A string constant a task script shows; [occurrence] picks one copy when the same text means different things */
    data class ScriptText(val script: Int, val english: String, val occurrence: Int? = null)

    /** Scripts whose every string constant is shown text, apart from [SCRIPT_TEXT_KEPT] */
    private val WHOLE_SCRIPTS = listOf(3223, 3995, 976)

    private val SCRIPT_TEXTS = listOf(
        ScriptText(3997, "Progress:"),
        ScriptText(4002, "Progress:  "),
        ScriptText(4017, "Click to hide completed Tasks."),
        ScriptText(4017, "Click to show completed Tasks."),
        ScriptText(4017, "Click to hide Tasks which are not in a set."),
        ScriptText(4017, "Click to show Tasks which are not in a set."),
        ScriptText(3991, "Difficulty : "),
        ScriptText(3991, "Area : "),
        ScriptText(3991, "ten"),
        ScriptText(3991, "forty"),
        ScriptText(3991, "Completing this Task will earn you "),
        ScriptText(3991, " coins."),
        ScriptText(3991, "You're already owed a reward! You may claim it from Explorer Jack or any other taskmaster, or increase it by completing more Tasks. "),
        ScriptText(3991, "You are also owed one or more items."),
        ScriptText(3977, "ten"),
        ScriptText(3977, "forty"),
        ScriptText(3977, "Completing this Task will earn you "),
        ScriptText(3977, " coins."),
        ScriptText(3977, "    Pin"),
        ScriptText(3977, "     Back"),
        ScriptText(3977, "You have already earned a sum of money, which you may claim by talking to any taskmaster or increase by continuing to complete Tasks."),
        ScriptText(3222, "This Task requires "),
        // "This Task requires 5 Attack." -> "Esta tarefa exige nível 5 em Attack."
        ScriptText(3222, " ", occurrence = 0),
        ScriptText(3222, "To complete this Task, you must complete the "),
        ScriptText(3222, " quest."),
        // Level up pop-up
        ScriptText(973, "You've just advanced 2 Herblore levels!"),
        ScriptText(973, "You've just advanced 3 Summoning levels!"),
        ScriptText(973, "You have now reached level "),
        ScriptText(973, "You've now reached the highest skill level that you can achieve during the tutorial. Once you finish the tutorial, you can advance this skill even further!"),
        ScriptText(973, "Well done! You've reached the total level "),
        ScriptText(973, "Well done! You've reached the Combat level "),
        ScriptText(973, " milestone!"),
        ScriptText(973, "Check out the skill advance guide to see what you'll be able to do when you reach even higher levels..."),
        // "Level 5 Cooking is one of the requirements for Cook's Assistant." -> "Nível 5 em Cooking é um dos requisitos da missão Cook's Assistant."
        ScriptText(1025, "Level "),
        ScriptText(1025, " ", occurrence = 0),
        ScriptText(1025, " is one of the requirements for "),
        ScriptText(1025, "You now have all the levels you need to complete "),
    )

    /**
     * String constants in the task scripts which aren't shown or stay in English: tags, numbers,
     * right-click options (kept in English like every other option), and a debug message.
     * Hook signatures ("iii", "Is"...) are recognised by [isSignature].
     */
    val SCRIPT_TEXT_KEPT = setOf(
        "", "<str>", "<br>", " -", ".", "null", "id", "160", "640", "2560", "<col=000080>", "<col=800000>",
        "Summary for", "Summary", "Pin", "Unpin", "Pin/Unpin Task", "Turn-on", "Turn-off",
        "Couldn't find the Task's struct.",
    )

    /** The argument types a script hook is registered with, e.g. "IIsii" */
    fun isSignature(text: String) = text.isNotEmpty() && text.all { it in "iIsl1Y" }

    /**
     * Fixed interface labels (interface to components) the server replaces when the interface opens, as no script
     * writes them. Any interface listed here is checked for labels nobody translates; see [LABELS_KEPT].
     */
    val LABELS = mapOf(
        917 to listOf(16, 20, 24, 28, 59, 95, 141, 145, 147),
        1056 to listOf(10, 15, 20, 25, 30, 35, 43, 48, 53, 113, 170, 172, 176, 178, 179, 180),
        1055 to listOf(4),
        741 to listOf(5, 10),
        371 to listOf(2, 27), // tutorial_overlay
        277 to listOf(3, 6, 9), // quest_complete
        178 to listOf(15, 16, 17, 18, 19, 30, 57, 67, 72, 76, 77, 78), // quest_intro
        335 to listOf(17, 19, 36, 37, 40, 41, 83, 87), // trade_main
        334 to listOf(31, 32, 34, 35, 36, 45), // trade_confirm
        667 to listOf(27, 28, 29, 65), // equipment_bonuses
        102 to listOf(25), // items_kept_on_death
        13 to listOf(30), // bank_pin
        14 to listOf(60, 61, 62, 63, 64, 65, 68, 70), // bank_pin_settings
        620 to listOf(21, 22, 23, 24, 30, 31), // shop
    )

    /**
     * Fixed labels which stay as they are: interface names (Task List, Equipment Bonuses...), place names, numbers,
     * attack types and stat names, and placeholders the server or a script overwrites (left for when those are done).
     */
    val LABELS_KEPT = mapOf(
        917 to listOf(70, 107, 119, 121, 123, 125, 127, 129, 131, 143),
        1056 to listOf(107, 117),
        1055 to listOf(),
        741 to listOf(),
        371 to listOf(1), // server: percent
        277 to listOf(4, 7) + (10..17), // server: quest name, points, rewards
        178 to (9..14) + 70, // "( ? )", divider, server: status
        335 to listOf(15, 38, 43, 44, 45), // server: title, status; script 694: wealth limit
        334 to listOf(33, 37, 38, 39, 41, 42, 43), // server: status and item lists
        667 to (30..47) + 75, // server: bonus lines; window name
        102 to listOf(15, 17, 20, 34), // window name; script 118
        13 to (11..20) + listOf(28, 31), // digits; server: instruction, deletion
        14 to listOf(40, 41, 69, 71, 73, 86, 89, 91), // window name; server: messages, status, confirm
        620 to listOf(20), // server: shop name
    )

    /** Text the server puts into task chat messages next to the task name and difficulty (see TaskSystem.kt) */
    val CHAT = listOf(
        "Lumbridge and Draynor",
        "Explorer Jack in Lumbridge",
        "Bob in Bob's Axes in Lumbridge",
        "Ned in Draynor Village",
        "someone somewhere",
    )

    /*
        Reading the cache
     */

    class Source(val cache: Cache) {
        val structs: Array<StructDefinition> = StructDecoder().load(cache)
        val enums: Array<EnumDefinition> = EnumDecoder().load(cache)
        val interfaces: Array<InterfaceDefinitionFull> = InterfaceDecoderFull().load(cache)

        fun scriptIds(): List<Int> = cache.archives(Index.CLIENT_SCRIPTS).toList()

        fun scriptBytes(id: Int): ByteArray? = cache.data(Index.CLIENT_SCRIPTS, id, 0)

        fun script(id: Int): ClientScriptDefinition? {
            val data = scriptBytes(id) ?: return null
            val definition = ClientScriptDefinition(id)
            ClientScriptDecoder().readLoop(definition, ArrayReader(data))
            return definition
        }

        fun enumBytes(id: Int): ByteArray? = cache.data(Index.ENUMS, id ushr 8, id and 0xff)

        fun enumIds(): List<Int> = cache.archives(Index.ENUMS).flatMap { archive -> cache.files(Index.ENUMS, archive).map { (archive shl 8) or it } }

        /** Task structs in task index order */
        fun tasks(): List<StructDefinition> {
            val structs = enums[TASK_STRUCTS].map!!
            return structs.keys.sorted().mapNotNull { this.structs.getOrNull(structs[it] as Int) }
        }

        fun area(task: StructDefinition): Int = task.params?.get(TASK_AREA) as? Int ?: -1

        fun label(id: Int, component: Int): String = interfaces[id].components!!.first { it.id and 0xffff == component }.text
    }

    /** One text to translate: [row] is its id in the table */
    data class Text(val row: String, val kind: String, val english: String)

    /** Every text the Task System interfaces can show, in table order; texts outside [AREAS] only when [all] */
    fun texts(source: Source, all: Boolean = true): List<Text> {
        val texts = mutableListOf<Text>()
        val seen = mutableSetOf<String>()
        fun add(row: String, kind: String, english: String) {
            if (english.isNotEmpty() && seen.add("$kind:$english")) {
                texts.add(Text(row, kind, english))
            }
        }
        for (task in source.tasks()) {
            if (!all && source.area(task) !in AREAS) {
                continue
            }
            val id = task.id
            add("name_$id", "name", task.text(TASK_NAME))
            add("desc_$id", "text", task.text(TASK_DESCRIPTION))
            for ((i, hint) in HINTS.withIndex()) {
                add("hint${i + 1}_$id", "text", task.text(hint))
            }
            add("set_$id", "text", task.text(TASK_SET))
        }
        for ((key, value) in source.enums[DIFFICULTY_NAMES].map!!.toSortedMap()) {
            add("difficulty_$key", "difficulty", value as String)
        }
        for ((key, value) in source.enums[AREA_NAMES].map!!.toSortedMap()) {
            add("area_$key", "area", value as String)
        }
        for ((key, value) in source.enums[LEVEL_UP_MESSAGES].map!!.toSortedMap()) {
            add("level_up_$key", "level_up", value as String)
        }
        for ((script, english) in scriptTexts(source)) {
            add("script_${script}_${seen.count { it.startsWith("script:") }}", "script", english)
        }
        for ((i, tooltip) in hookTexts(source).withIndex()) {
            add("tooltip_$i", "tooltip", tooltip)
        }
        for ((id, components) in LABELS) {
            for (component in components) {
                // Labels repeat across tabs, so each component gets its own row for the server to find
                texts.add(Text("label_${id}_$component", "label", source.label(id, component)))
            }
        }
        for ((i, chat) in CHAT.withIndex()) {
            add("chat_$i", "chat", chat)
        }
        return texts
    }

    private fun StructDefinition.text(param: Int): String = params?.get(param) as? String ?: ""

    /** The (script, english) of every string constant to translate, checking each still exists */
    fun scriptTexts(source: Source): List<Pair<Int, String>> {
        val texts = mutableListOf<Pair<Int, String>>()
        for (script in WHOLE_SCRIPTS) {
            val definition = source.script(script)!!.original(source)
            for (string in definition.stringOperands.orEmpty().filterNotNull().distinct()) {
                if (string !in SCRIPT_TEXT_KEPT) {
                    texts.add(script to string)
                }
            }
        }
        for (text in SCRIPT_TEXTS) {
            val definition = source.script(text.script)!!.original(source)
            check(definition.stringOperands.orEmpty().contains(text.english)) { "Script ${text.script} no longer has '${text.english}'." }
            texts.add(text.script to text.english)
        }
        return texts
    }

    /** Text the interfaces pass to [HOOK_SCRIPTS]: tooltips and the level up link */
    fun hookTexts(source: Source): List<String> {
        val tooltips = linkedSetOf<String>()
        for (id in INTERFACES.values) {
            for (component in source.interfaces[id].components!!) {
                for (hook in component.hooks()) {
                    if (hook.firstOrNull() in HOOK_SCRIPTS) {
                        hook.filterIsInstance<String>().forEach { tooltips.add(it) }
                    }
                }
            }
        }
        return tooltips.toList()
    }

    /** Every script hook of a component, e.g. `[38, -2147483645, 60096601, "Tooltip", 45, 135]` */
    fun InterfaceComponentDefinitionFull.hooks(): List<Array<Any>> = javaClass.declaredFields
        .filter { it.type == Array<Any>::class.java }
        .mapNotNull { field ->
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            field.get(this) as Array<Any>?
        }

    /*
        The table
     */

    data class Row(val id: String, val english: String, val portuguese: String)

    /** Reads `interface_pt_br.tables.toml`, which only ever holds the simple lines `writeTable` produces */
    fun readTable(file: File): List<Row> {
        val rows = mutableListOf<Row>()
        var id: String? = null
        var english: String? = null
        for (line in file.readLines(Charsets.UTF_8)) {
            when {
                line.startsWith("[.") -> id = line.removePrefix("[.").removeSuffix("]")
                id == null -> continue // Column types
                line.startsWith("en = ") -> english = unquote(line.removePrefix("en = "))
                line.startsWith("pt = ") -> rows.add(Row(id, english!!, unquote(line.removePrefix("pt = "))))
            }
        }
        return rows
    }

    private fun unquote(value: String): String = value.removePrefix("\"").removeSuffix("\"").replace("\\\"", "\"")

    /** Rows of one kind of text keyed by their English, e.g. `kind(rows, "name_")` for task names */
    fun kind(rows: List<Row>, vararg prefixes: String) = rows.filter { row -> prefixes.any { row.id.startsWith(it) } }.associate { it.english to it.portuguese }

    /*
        The patch
     */

    object Op {
        const val PUSH_INT = 0
        const val GET_VARP = 1
        const val PUSH_STRING = 3
        const val IF_NE = 7
        const val IF_EQ = 8
        const val RETURN = 21
        const val GET_VARBIT = 25
        const val ILOAD = 33
        const val ISTORE = 34
        const val SLOAD = 35
        const val SSTORE = 36
        const val GOSUB = 40
        const val GET_VARC = 42
        const val ENUM_STRING = 3400
        const val ENUM = 3408
        const val ADD = 4000
        const val SUB = 4001
        const val MUL = 4002
        const val COMPARE = 4107
        const val STRUCT_PARAM = 4500
        val BRANCHES = setOf(6, 7, 8, 9, 10, 31, 32, 51)
    }

    private const val KEY_INT = 105 // 'i'
    private const val VALUE_STRING = 115 // 's'

    /** Key of a task text in the Portuguese text enum: params 1266..1292 fit in 5 bits */
    fun textKey(struct: Int, param: Int) = struct * 32 + (param - TASK_NAME)

    /** Scripts and enums an earlier patch added, found by name and by a marker entry */
    private class Ours(source: Source) {
        val scripts = mutableMapOf<Int, ClientScriptDefinition>()
        val enums = mutableListOf<Int>()

        init {
            for (id in source.scriptIds()) {
                val name = scriptName(source.scriptBytes(id) ?: continue)
                if (name.startsWith("pt_br_")) {
                    scripts[id] = source.script(id)!!
                }
            }
            for (id in source.enumIds()) {
                val definition = EnumDefinition(id)
                EnumDecoder().readLoop(definition, ArrayReader(source.enumBytes(id) ?: continue))
                if (definition.map?.get(ENUM_MARKER) == "pt_br") {
                    enums.add(id)
                }
            }
        }
    }

    /** Name at the start of a script, read as Latin-1 so translated text never trips the decoder */
    private fun scriptName(data: ByteArray): String = String(data, 0, data.indexOf(0), Charsets.ISO_8859_1)

    private val ours = mutableMapOf<Cache, Ours>()

    private fun ours(source: Source) = ours.getOrPut(source.cache) { Ours(source) }

    /** The script as it was before any patch, undoing the call swaps */
    fun ClientScriptDefinition.original(source: Source): ClientScriptDefinition {
        val ours = ours(source)
        if (ours.scripts.isEmpty() || instructions.none { it == Op.GOSUB }) {
            return this
        }
        val instructions = instructions.toMutableList()
        val ints = (intOperands ?: IntArray(instructions.size)).toMutableList()
        val strings = (stringOperands ?: arrayOfNulls(instructions.size)).toMutableList()
        var i = 0
        while (i < instructions.size) {
            val target = if (instructions[i] == Op.GOSUB) ours.scripts[ints[i]] else null
            when (target?.name) {
                TASK_TEXT -> {
                    instructions[i] = Op.STRUCT_PARAM
                    ints[i] = 0
                }
                TASK_ENUM -> {
                    instructions[i] = Op.ENUM
                    ints[i] = 0
                }
                ENUM_STRING -> {
                    instructions[i] = Op.ENUM_STRING
                    ints[i] = 0
                }
                STRING -> {
                    instructions[i] = Op.PUSH_STRING
                    ints[i] = 0
                    strings[i] = target.stringOperands!![3]
                }
                TOOLTIP -> {
                    check(instructions.none { it in Op.BRANCHES }) { "Can't remove an instruction from script $id with jumps." }
                    instructions.removeAt(i)
                    ints.removeAt(i)
                    strings.removeAt(i)
                    continue
                }
            }
            i++
        }
        return copy(instructions = instructions.toIntArray(), intOperands = ints.toIntArray(), stringOperands = strings.toTypedArray())
    }

    private class Builder(val name: String) {
        val instructions = mutableListOf<Int>()
        val ints = mutableListOf<Int>()
        val strings = mutableListOf<String?>()

        fun op(op: Int, operand: Int = 0) {
            instructions.add(op)
            ints.add(operand)
            strings.add(null)
        }

        fun string(value: String) {
            instructions.add(Op.PUSH_STRING)
            ints.add(0)
            strings.add(value)
        }

        /** A branch to be pointed at a later instruction with [land] */
        fun branch(op: Int): Int {
            op(op)
            return instructions.lastIndex
        }

        fun land(branch: Int) {
            ints[branch] = instructions.size - branch - 1
        }

        fun languageIs(portuguese: Boolean): Int {
            op(Op.GET_VARC, LANGUAGE_VARC)
            op(Op.PUSH_INT, 1)
            return branch(if (portuguese) Op.IF_EQ else Op.IF_NE)
        }

        fun build(id: Int, intArgs: Int = 0, stringArgs: Int = 0, intVars: Int = intArgs, stringVars: Int = stringArgs) = ClientScriptDefinition(
            id = id,
            name = name,
            intArgumentCount = intArgs,
            stringArgumentCount = stringArgs,
            intVariableCount = intVars,
            stringVariableCount = stringVars,
            instructions = instructions.toIntArray(),
            intOperands = ints.toIntArray(),
            stringOperands = strings.toTypedArray(),
        )
    }

    /** (struct, param) -> struct_param, or the Portuguese from [textEnum] when the language varc is 1 and it has one */
    private fun taskText(id: Int, textEnum: Int) = Builder(TASK_TEXT).apply {
        val english = languageIs(portuguese = false)
        op(Op.PUSH_INT, KEY_INT)
        op(Op.PUSH_INT, VALUE_STRING)
        op(Op.PUSH_INT, textEnum)
        op(Op.ILOAD, 0)
        op(Op.PUSH_INT, 32)
        op(Op.MUL)
        op(Op.ILOAD, 1)
        op(Op.PUSH_INT, TASK_NAME)
        op(Op.SUB)
        op(Op.ADD)
        op(Op.ENUM)
        op(Op.SSTORE, 0)
        op(Op.SLOAD, 0)
        string("")
        op(Op.COMPARE)
        op(Op.PUSH_INT, 0)
        val empty = branch(Op.IF_EQ)
        op(Op.SLOAD, 0)
        op(Op.RETURN)
        land(english)
        land(empty)
        op(Op.ILOAD, 0)
        op(Op.ILOAD, 1)
        op(Op.STRUCT_PARAM)
        op(Op.RETURN)
    }.build(id, intArgs = 2, stringVars = 1)

    /** (key type, value type, enum, key) -> enum, reading the Portuguese copy of [copies] when the language varc is 1 */
    private fun taskEnum(id: Int, copies: Map<Int, Int>) = Builder(TASK_ENUM).apply {
        val english = languageIs(portuguese = false)
        for ((original, copy) in copies) {
            op(Op.ILOAD, 2)
            op(Op.PUSH_INT, original)
            val other = branch(Op.IF_NE)
            op(Op.PUSH_INT, copy)
            op(Op.ISTORE, 2)
            land(other)
        }
        land(english)
        op(Op.ILOAD, 0)
        op(Op.ILOAD, 1)
        op(Op.ILOAD, 2)
        op(Op.ILOAD, 3)
        op(Op.ENUM)
        op(Op.RETURN)
    }.build(id, intArgs = 4)

    /** (enum, key) -> enum_string, reading the Portuguese copy of [copies] when the language varc is 1 */
    private fun enumString(id: Int, copies: Map<Int, Int>) = Builder(ENUM_STRING).apply {
        val english = languageIs(portuguese = false)
        for ((original, copy) in copies) {
            op(Op.ILOAD, 0)
            op(Op.PUSH_INT, original)
            val other = branch(Op.IF_NE)
            op(Op.PUSH_INT, copy)
            op(Op.ISTORE, 0)
            land(other)
        }
        land(english)
        op(Op.ILOAD, 0)
        op(Op.ILOAD, 1)
        op(Op.ENUM_STRING)
        op(Op.RETURN)
    }.build(id, intArgs = 2)

    /** () -> [english] or [portuguese]; the English must stay the 4th instruction so the patch can be undone */
    private fun string(id: Int, english: String, portuguese: String) = Builder(STRING).apply {
        val translated = languageIs(portuguese = true)
        string(english)
        op(Op.RETURN)
        land(translated)
        string(portuguese)
        op(Op.RETURN)
    }.build(id)

    /** (text) -> its translation when the language varc is 1 and it's one of [tooltips] */
    private fun tooltip(id: Int, tooltips: Map<String, String>) = Builder(TOOLTIP).apply {
        val translate = languageIs(portuguese = true)
        op(Op.SLOAD, 0)
        op(Op.RETURN)
        land(translate)
        for ((english, portuguese) in tooltips) {
            op(Op.SLOAD, 0)
            string(english)
            op(Op.COMPARE)
            op(Op.PUSH_INT, 0)
            val other = branch(Op.IF_NE)
            string(portuguese)
            op(Op.RETURN)
            land(other)
        }
        op(Op.SLOAD, 0)
        op(Op.RETURN)
    }.build(id, stringArgs = 1)

    /** Everything a patch writes, keyed by id, what it removes, and where it put things */
    class Patch(
        val scripts: Map<Int, ClientScriptDefinition>,
        val enums: Map<Int, EnumDefinition>,
        val removeScripts: Set<Int>,
        val removeEnums: Set<Int>,
        val textEnum: Int,
        val taskText: Int,
        val taskEnum: Int,
        val enumString: Int,
        val tooltip: Int,
    )

    /** Works out the patched scripts and enums for [rows] from the original cache, without writing anything */
    fun patch(source: Source, rows: List<Row>): Patch {
        val ours = ours(source)
        val portuguese = rows.associate { it.id to it.portuguese }
        val names = kind(rows, "name_")
        val taskTexts = kind(rows, "desc_", "hint", "set_")
        val scriptStrings = kind(rows, "script_")
        val tooltipTexts = kind(rows, "tooltip_")

        // Fresh ids after the original cache
        var nextScript = source.scriptIds().filter { it !in ours.scripts }.max() + 1
        var nextEnum = source.enumIds().filter { it !in ours.enums }.max() + 1
        val textEnum = nextEnum++
        val areaEnum = nextEnum++
        val difficultyEnum = nextEnum++
        val levelUpEnum = nextEnum++
        val taskText = nextScript++
        val taskEnum = nextScript++
        val tooltip = nextScript++
        val enumString = nextScript++

        val enums = mutableMapOf<Int, EnumDefinition>()
        val texts = mutableMapOf<Int, Any>(ENUM_MARKER to "pt_br")
        for (task in source.tasks()) {
            for (param in TEXT_PARAMS) {
                val english = task.text(param)
                val pt = (if (param == TASK_NAME) names[english] else taskTexts[english]) ?: continue
                if (english.isNotEmpty()) {
                    texts[textKey(task.id, param)] = pt
                }
            }
        }
        enums[textEnum] = EnumDefinition(id = textEnum, keyType = 'i', valueType = 's', defaultString = "", length = texts.size, map = texts)
        val copies = listOf(Triple(areaEnum, AREA_NAMES, "area"), Triple(difficultyEnum, DIFFICULTY_NAMES, "difficulty"), Triple(levelUpEnum, LEVEL_UP_MESSAGES, "level_up"))
        for ((copy, original, prefix) in copies) {
            val enum = source.enums[original]
            val map = enum.map!!.mapValues { (key, value) -> portuguese["${prefix}_$key"] ?: value }.toMutableMap()
            map[ENUM_MARKER] = "pt_br"
            enums[copy] = enum.copy(id = copy, length = map.size, map = map)
        }

        val scripts = mutableMapOf<Int, ClientScriptDefinition>()
        scripts[taskText] = taskText(taskText, textEnum)
        scripts[taskEnum] = taskEnum(taskEnum, mapOf(AREA_NAMES to areaEnum, DIFFICULTY_NAMES to difficultyEnum))
        scripts[enumString] = enumString(enumString, mapOf(LEVEL_UP_MESSAGES to levelUpEnum))
        val tooltips = hookTexts(source).associateWith { tooltipTexts[it] }.filterValues { it != null }.mapValues { it.value!! }
        scripts[tooltip] = tooltip(tooltip, tooltips)

        // One string script per translated constant
        val stringScripts = mutableMapOf<Pair<String, String>, Int>()
        val wanted = mutableMapOf<Int, MutableList<ScriptText>>()
        for ((script, english) in scriptTexts(source)) {
            val text = SCRIPT_TEXTS.firstOrNull { it.script == script && it.english == english } ?: ScriptText(script, english)
            wanted.getOrPut(script) { mutableListOf() }.add(text)
        }

        for (id in source.scriptIds()) {
            if (id in ours.scripts) {
                continue
            }
            val current = source.script(id) ?: continue
            val original = current.original(source)
            val instructions = original.instructions.copyOf()
            val ints = (original.intOperands ?: IntArray(instructions.size)).copyOf()
            val strings = (original.stringOperands ?: arrayOfNulls(instructions.size)).copyOf()
            var changed = false
            for (i in instructions.indices) {
                // Task texts: push_int <param>, struct_param
                if (instructions[i] == Op.STRUCT_PARAM && i > 0 && instructions[i - 1] == Op.PUSH_INT && ints[i - 1] in TEXT_PARAMS) {
                    instructions[i] = Op.GOSUB
                    ints[i] = taskText
                    changed = true
                }
                // Area and difficulty names: push_int 'i', push_int 's', push_int <enum>, <key>, enum
                if (instructions[i] == Op.PUSH_INT &&
                    ints[i] in setOf(AREA_NAMES, DIFFICULTY_NAMES) &&
                    i > 1 &&
                    instructions[i - 1] == Op.PUSH_INT &&
                    ints[i - 1] == VALUE_STRING &&
                    instructions[i - 2] == Op.PUSH_INT &&
                    ints[i - 2] == KEY_INT
                ) {
                    var j = i + 1
                    while (instructions[j] != Op.ENUM) {
                        check(instructions[j] in setOf(Op.PUSH_INT, Op.ILOAD, Op.GET_VARBIT, Op.GET_VARP, Op.GET_VARC, Op.STRUCT_PARAM)) {
                            "Unexpected key for enum ${ints[i]} in script $id at $j."
                        }
                        j++
                    }
                    instructions[j] = Op.GOSUB
                    ints[j] = taskEnum
                    changed = true
                }
                // Level up messages: push_int <enum>, <key>, enum_string
                if (instructions[i] == Op.ENUM_STRING && i > 1 && instructions[i - 2] == Op.PUSH_INT && ints[i - 2] == LEVEL_UP_MESSAGES &&
                    instructions[i - 1] in setOf(Op.PUSH_INT, Op.ILOAD, Op.GET_VARBIT, Op.GET_VARP, Op.GET_VARC)
                ) {
                    instructions[i] = Op.GOSUB
                    ints[i] = enumString
                    changed = true
                }
            }
            for (text in wanted[id].orEmpty()) {
                val pt = scriptStrings[text.english] ?: continue
                val call = stringScripts.getOrPut(text.english to pt) { nextScript++ }
                var occurrence = 0
                var replaced = false
                for (i in instructions.indices) {
                    if (instructions[i] == Op.PUSH_STRING && strings[i] == text.english) {
                        if (text.occurrence == null || text.occurrence == occurrence) {
                            instructions[i] = Op.GOSUB
                            ints[i] = call
                            strings[i] = null
                            replaced = true
                        }
                        occurrence++
                    }
                }
                check(replaced) { "Script $id no longer has '${text.english}'." }
                changed = true
            }
            if (!changed) {
                continue
            }
            check(original.stringOperands.orEmpty().filterNotNull().all { s -> s.all { it.code < 0x80 } }) {
                "Script $id has text outside ASCII, which the decoder would change when written back."
            }
            scripts[id] = original.copy(instructions = instructions, intOperands = ints, stringOperands = strings)
        }

        // Text hooks pass in: translated right after the script loads its string argument
        for ((id, shape) in HOOK_SCRIPTS) {
            val (expected, at) = shape
            val generic = source.script(id)!!.original(source)
            check(generic.instructions.contentEquals(expected)) { "Hook script $id changed." }
            scripts[id] = generic.copy(
                instructions = generic.instructions.toMutableList().apply { add(at, Op.GOSUB) }.toIntArray(),
                intOperands = generic.intOperands!!.toMutableList().apply { add(at, tooltip) }.toIntArray(),
                stringOperands = (generic.stringOperands ?: arrayOfNulls(generic.instructions.size)).toMutableList().apply { add(at, null) }.toTypedArray(),
            )
        }

        for ((key, id) in stringScripts) {
            scripts[id] = string(id, key.first, key.second)
        }
        return Patch(
            scripts = scripts,
            enums = enums,
            removeScripts = ours.scripts.keys - scripts.keys,
            removeEnums = ours.enums.toSet() - enums.keys,
            textEnum = textEnum,
            taskText = taskText,
            taskEnum = taskEnum,
            enumString = enumString,
            tooltip = tooltip,
        )
    }

    fun encode(script: ClientScriptDefinition): ByteArray {
        val writer = ArrayWriter(4096 + script.instructions.size * 8)
        with(ClientScriptEncoder()) { writer.encode(script) }
        return writer.toArray()
    }

    fun encode(enum: EnumDefinition): ByteArray {
        val writer = ArrayWriter(1024 + enum.length * 256)
        with(EnumEncoder()) { writer.encode(enum) }
        return writer.toArray()
    }

    /** Scripts and enums whose bytes in the cache differ from [patch], i.e. empty when the cache is up to date */
    fun differences(source: Source, patch: Patch): List<String> {
        val differences = mutableListOf<String>()
        for ((id, script) in patch.scripts) {
            if (!encode(script).contentEquals(source.scriptBytes(id))) {
                differences.add("script $id")
            }
        }
        for ((id, enum) in patch.enums) {
            if (!encode(enum).contentEquals(source.enumBytes(id))) {
                differences.add("enum $id")
            }
        }
        patch.removeScripts.mapTo(differences) { "old script $it" }
        patch.removeEnums.mapTo(differences) { "old enum $it" }
        return differences
    }
}
