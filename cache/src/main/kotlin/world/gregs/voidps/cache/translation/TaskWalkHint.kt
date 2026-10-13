package world.gregs.voidps.cache.translation

import world.gregs.voidps.cache.definition.data.ClientScriptDefinition
import world.gregs.voidps.cache.definition.data.EnumDefinition
import world.gregs.voidps.cache.translation.CacheTranslationPatch.Builder
import world.gregs.voidps.cache.translation.CacheTranslationPatch.ENUM_MARKER
import world.gregs.voidps.cache.translation.CacheTranslationPatch.INT_MARKER
import world.gregs.voidps.cache.translation.CacheTranslationPatch.KEY_INT
import world.gregs.voidps.cache.translation.CacheTranslationPatch.Op
import world.gregs.voidps.cache.translation.CacheTranslationPatch.VALUE_STRING
import java.io.File

/**
 * Map icon on a Task System tab hint which walks the player to the task's NPC (`task_npcs` table, the server walks).
 *
 * The tab's hint drawing script ([DRAW_HINTS]) only shows a hint's map icon when the task has a map tile for that
 * line (`task_hint_tile_<n>`), so its `struct_param` of those params becomes a call to [HINT], which also reports
 * a tile for the line [TaskWalkHint] lists and keeps the struct shown in [STRUCT_VARC].
 * The hover text of every hint icon is the same cache constant, so the tooltip script (38) gets one more call,
 * [TOOLTIP], which swaps it for "Click to walk to ..." on that line's icon.
 * The same drawing script also gets its back button shown on the tutorial set's task ([progress]).
 * Quest journal lines naming an npc get a "Walk to ..." option from the server through [questLineOp].
 */
object TaskWalkHint {
    const val TABLE_PATH = "data/achievement/task_npcs.tables.toml"
    private const val STRUCTS_PATH = "data/client/structs.toml"

    const val PREFIX = "void_"
    const val HINT = "void_task_walk_hint"
    const val TOOLTIP = "void_task_walk_tooltip"
    const val PROGRESS = "void_task_tutorial_progress"
    const val QUEST_LINE_OP = "void_quest_line_op"

    /**
     * [questLineOp]'s id, fixed as the server calls it by this id (`QuestJournalWalk`), past the ids the rest of the
     * patch counts up from the cache's last script
     */
    const val QUEST_LINE_OP_ID = 6000

    /** Unstable Foundations' progress varp, which hides the tab's back button on tutorial set tasks below [BACK_STAGE] */
    const val TUTORIAL_VARP = 281
    private const val BACK_STAGE = 135

    /** Draws the selected task's hints in the tab (interface 1056) */
    const val DRAW_HINTS = 3977

    /** Client only varc holding the struct of the task the tab last drew, past the cache's varcs like `portuguese_dialogue` */
    const val STRUCT_VARC = 2001

    private const val TASK_SYSTEM = 1056
    private const val FIRST_ICON = 77
    private const val FIRST_TILE = 1282
    private const val LINES = 6

    /** Task [struct]'s hint [line] (1-6) walks to the npc, shown as [english]/[portuguese] in the tooltip */
    data class Hint(val struct: Int, val line: Int, val english: String, val portuguese: String)

    /** The rows of the server's `task_npcs` table under [root] */
    fun read(root: File): List<Hint> {
        val structs = root.resolve(STRUCTS_PATH).readLines()
            .mapNotNull { line -> line.split("=").takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim().toIntOrNull() } }
            .filter { it.second != null }
            .associate { it.first to it.second!! }
        val hints = mutableListOf<Hint>()
        var row: String? = null
        val values = mutableMapOf<String, String>()
        fun add() {
            val id = row ?: return
            val struct = structs[id] ?: error("Unknown task struct '$id' in $TABLE_PATH.")
            val line = values["hint"]?.toInt() ?: error("Task '$id' has no hint line.")
            check(line in 1..LINES) { "Task '$id' hint line $line isn't 1-$LINES." }
            hints.add(Hint(struct, line, "Click to walk to ${values.getValue("name")}.", "Clique para andar até ${values.getValue("pt")}."))
        }
        for (text in root.resolve(TABLE_PATH).readLines(Charsets.UTF_8)) {
            val line = text.trim()
            if (line.startsWith("[.")) {
                add()
                row = line.removePrefix("[.").removeSuffix("]")
                values.clear()
            } else if (row != null && "=" in line && !line.startsWith("#")) {
                val key = line.substringBefore("=").trim()
                values[key] = line.substringAfter("=").trim().removeSurrounding("\"")
            }
        }
        add()
        return hints
    }

    /** Struct to hint line, and struct to the English and Portuguese tooltips */
    fun enums(hints: List<Hint>, lines: Int, english: Int, portuguese: Int): Map<Int, EnumDefinition> {
        val lineMap = hints.associate<Hint, Int, Any> { it.struct to it.line }.toMutableMap()
        lineMap[ENUM_MARKER] = INT_MARKER
        return mapOf(
            lines to EnumDefinition(id = lines, keyType = 'i', valueType = 'i', defaultInt = 0, length = lineMap.size, map = lineMap),
            english to texts(english, hints.associate { it.struct to it.english }),
            portuguese to texts(portuguese, hints.associate { it.struct to it.portuguese }),
        )
    }

    private fun texts(id: Int, texts: Map<Int, String>): EnumDefinition {
        val map = texts.toMutableMap<Int, Any>()
        map[ENUM_MARKER] = "pt_br"
        return EnumDefinition(id = id, keyType = 'i', valueType = 's', defaultString = "", length = map.size, map = map)
    }

    /** (struct, param) -> struct_param, or 0 for the tile param of the line [lines] lists, so its icon shows */
    fun hint(id: Int, lines: Int) = Builder(HINT).apply {
        op(Op.ILOAD, 0)
        op(Op.SET_VARC, STRUCT_VARC)
        op(Op.PUSH_INT, KEY_INT)
        op(Op.PUSH_INT, KEY_INT)
        op(Op.PUSH_INT, lines)
        op(Op.ILOAD, 0)
        op(Op.ENUM)
        op(Op.ILOAD, 1)
        op(Op.PUSH_INT, FIRST_TILE - 1)
        op(Op.SUB)
        val other = branch(Op.IF_NE)
        op(Op.PUSH_INT, 0)
        op(Op.RETURN)
        land(other)
        op(Op.ILOAD, 0)
        op(Op.ILOAD, 1)
        op(Op.STRUCT_PARAM)
        op(Op.RETURN)
    }.build(id, intArgs = 2)

    /** (component, text) -> the walk tooltip when hovering the icon of the walk line of the task last drawn, else text */
    fun tooltip(id: Int, lines: Int, english: Int, portuguese: Int) = Builder(TOOLTIP).apply {
        op(Op.GET_VARC, STRUCT_VARC)
        op(Op.ISTORE, 1)
        for (line in 1..LINES) {
            op(Op.ILOAD, 0)
            op(Op.PUSH_INT, (TASK_SYSTEM shl 16) or (FIRST_ICON + (line - 1) * 2))
            val otherIcon = branch(Op.IF_NE)
            op(Op.PUSH_INT, KEY_INT)
            op(Op.PUSH_INT, KEY_INT)
            op(Op.PUSH_INT, lines)
            op(Op.ILOAD, 1)
            op(Op.ENUM)
            op(Op.PUSH_INT, line)
            val otherLine = branch(Op.IF_NE)
            val translated = languageIs(portuguese = true)
            text(english)
            land(translated)
            text(portuguese)
            land(otherIcon)
            land(otherLine)
        }
        op(Op.SLOAD, 0)
        op(Op.RETURN)
    }.build(id, intArgs = 1, stringArgs = 1, intVars = 2)

    /** Returns the struct's text in [enum] */
    private fun Builder.text(enum: Int) {
        op(Op.PUSH_INT, KEY_INT)
        op(Op.PUSH_INT, VALUE_STRING)
        op(Op.PUSH_INT, enum)
        op(Op.ILOAD, 1)
        op(Op.ENUM)
        op(Op.RETURN)
    }

    /**
     * () -> past [BACK_STAGE]. The tab hides its back button on a tutorial set task (Talk to Explorer Jack) until
     * Unstable Foundations reaches [BACK_STAGE], which kept players on that task in the original game; here they come
     * from Tutorial Island instead, so the button always shows.
     */
    fun progress(id: Int) = Builder(PROGRESS).apply {
        op(Op.PUSH_INT, BACK_STAGE)
        op(Op.RETURN)
    }.build(id)

    /** (component, text) -> sets the first option of a quest journal line, which has none in the cache */
    fun questLineOp(id: Int) = Builder(QUEST_LINE_OP).apply {
        op(Op.PUSH_INT, 1)
        op(Op.SLOAD, 0)
        op(Op.ILOAD, 0)
        op(Op.IF_SETOP)
        op(Op.RETURN)
    }.build(id, intArgs = 1, stringArgs = 1)

    /** [script] with the progress read deciding the back button, `get_varp 281, push_int 135`, swapped for a call to [progress] */
    fun callProgress(script: ClientScriptDefinition, progress: Int): ClientScriptDefinition {
        val ints = script.intOperands!!.copyOf()
        val instructions = script.instructions.copyOf()
        val at = instructions.indices.single { instructions[it] == Op.GET_VARP && ints[it] == TUTORIAL_VARP && instructions[it + 1] == Op.PUSH_INT && ints[it + 1] == BACK_STAGE }
        instructions[at] = Op.GOSUB
        ints[at] = progress
        return script.copy(instructions = instructions, intOperands = ints)
    }

    /** [script] with its `struct_param` of the hint tiles swapped for calls to [hint], one for one so no jump moves */
    fun callHint(script: ClientScriptDefinition, hint: Int): ClientScriptDefinition {
        val instructions = script.instructions.copyOf()
        val ints = script.intOperands!!.copyOf()
        var swapped = 0
        for (i in 1 until instructions.size) {
            if (instructions[i] == Op.STRUCT_PARAM && instructions[i - 1] == Op.PUSH_INT && ints[i - 1] in FIRST_TILE until FIRST_TILE + LINES) {
                instructions[i] = Op.GOSUB
                ints[i] = hint
                swapped++
            }
        }
        check(swapped == LINES) { "Script ${script.id} reads $swapped hint tiles, expected $LINES." }
        return script.copy(instructions = instructions, intOperands = ints)
    }

    /** The tooltip [script] with `iload 0, gosub walkTooltip` after its call to the translation [tooltip] */
    fun callTooltip(script: ClientScriptDefinition, tooltip: Int, walkTooltip: Int): ClientScriptDefinition {
        val at = script.instructions.indices.single { script.instructions[it] == Op.GOSUB && script.intOperands!![it] == tooltip } + 1
        val strings = script.stringOperands ?: arrayOfNulls(script.instructions.size)
        return script.copy(
            instructions = script.instructions.toMutableList().apply { addAll(at, listOf(Op.ILOAD, Op.GOSUB)) }.toIntArray(),
            intOperands = script.intOperands!!.toMutableList().apply { addAll(at, listOf(0, walkTooltip)) }.toIntArray(),
            stringOperands = strings.toMutableList().apply { addAll(at, listOf(null, null)) }.toTypedArray(),
        )
    }
}
