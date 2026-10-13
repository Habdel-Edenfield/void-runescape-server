package world.gregs.voidps.cache.translation

import world.gregs.voidps.cache.definition.data.ClientScriptDefinition
import world.gregs.voidps.cache.translation.CacheTranslationPatch.LANGUAGE_VARC
import world.gregs.voidps.cache.translation.CacheTranslationPatch.Op

/**
 * Brazilian Portuguese for the Skill Advance Guide (`skill_guide`) and the list of unlocks in the level up pop-up,
 * which both read their text from the guide's data scripts: one per skill for the menu names and one for the
 * entries (label and "You can now..." sentence), switching on the page and row.
 *
 * They only push constants, so each gets a Portuguese copy, [COPY], with the same instructions: a translated
 * text goes in the first string it's joined from and the others are left empty. The copy starts by calling the
 * original when the language varc isn't 1, and the few scripts calling the data scripts ([CALLERS]) call the copy
 * instead, a one for one swap. Jumps and switches are relative, so the prefix moves none of them.
 */
object SkillGuideTranslation {

    const val TABLE_PATH = "data/entity/player/dialogue/translation/skill_guide_pt_br.guide.toml"
    const val SOURCE_PREFIX = "pt_guide"
    const val COPY = "pt_br_guide"

    /** Menu names (Construction's, then each skill's) and entries, in pairs, plus Prayer's and the level up extras */
    val DATA_SCRIPTS = listOf(2, 3) + (977..1022) + listOf(2787, 2788, 3354, 3355)

    /** The only scripts calling [DATA_SCRIPTS]: the menu (13) and entries (14) by skill, and the level up extras (1567) */
    val CALLERS = listOf(13, 14, 1567)

    private const val JOIN_STRING = 37
    private val DATA_OPS = setOf(Op.ILOAD, 51, 6, Op.PUSH_INT, Op.PUSH_STRING, JOIN_STRING, Op.RETURN, Op.IF_EQ, Op.STRUCT_PARAM)

    /** One text the player sees: the strings at [pushes] joined together */
    data class Unit(val pushes: List<Int>, val english: String)

    /** Every text [script] shows, in order: a run of constants joined by `join_string`, or a constant on its own */
    fun units(script: ClientScriptDefinition): List<Unit> {
        val ops = script.instructions
        val strings = script.stringOperands ?: return emptyList()
        val ints = script.intOperands ?: IntArray(ops.size)
        check(ops.all { it in DATA_OPS }) { "Skill guide script ${script.id} does more than push its text." }
        val units = mutableListOf<Unit>()
        var i = 0
        while (i < ops.size) {
            if (ops[i] != Op.PUSH_STRING) {
                i++
                continue
            }
            var end = i
            while (end < ops.size && ops[end] == Op.PUSH_STRING) {
                end++
            }
            val joined = if (end < ops.size && ops[end] == JOIN_STRING) ints[end] else 0
            check(joined <= end - i) { "Script ${script.id} joins more than the constants before it at $end." }
            for (push in i until end - joined) {
                units.add(Unit(listOf(push), strings[push]!!))
            }
            if (joined > 0) {
                val pushes = (end - joined until end).toList()
                units.add(Unit(pushes, pushes.joinToString("") { strings[it]!! }))
            }
            i = end
        }
        return units.filter { it.english.isNotEmpty() }
    }

    /** The English of every text the guide shows, each once, by script */
    fun texts(source: CacheTranslationPatch.Source): List<Pair<Int, String>> {
        val seen = mutableSetOf<String>()
        return DATA_SCRIPTS.flatMap { id -> units(source.script(id)!!).filter { seen.add(it.english) }.map { id to it.english } }
    }

    /** [original] with [translations] in place, called instead of it; the original's id stays at instruction [ORIGINAL_CALL] */
    fun copy(id: Int, original: ClientScriptDefinition, translations: Map<String, String>): ClientScriptDefinition {
        check(original.stringArgumentCount == 0) { "Skill guide script ${original.id} takes strings." }
        val arguments = original.intArgumentCount
        val strings = (original.stringOperands ?: arrayOfNulls(original.instructions.size)).copyOf()
        for (unit in units(original)) {
            val portuguese = translations[unit.english] ?: continue
            for ((index, push) in unit.pushes.withIndex()) {
                strings[push] = if (index == 0) portuguese else ""
            }
        }
        // Not Portuguese: call the original with the same arguments and leave what it returns on the stack
        val prefix = listOf(Op.GET_VARC to LANGUAGE_VARC, Op.PUSH_INT to 1, Op.IF_EQ to arguments + 2) +
            (0 until arguments).map { Op.ILOAD to it } + listOf(Op.GOSUB to original.id, Op.RETURN to 0)
        check(prefix[ORIGINAL_CALL + arguments].first == Op.GOSUB)
        return original.copy(
            id = id,
            name = COPY,
            instructions = prefix.map { it.first }.toIntArray() + original.instructions,
            intOperands = prefix.map { it.second }.toIntArray() + (original.intOperands ?: IntArray(original.instructions.size)),
            stringOperands = arrayOfNulls<String>(prefix.size) + strings,
        )
    }

    /** Index of the call to the original in a copy, after its [ClientScriptDefinition.intArgumentCount] loads */
    private const val ORIGINAL_CALL = 3

    /** The data script [copy] was made from */
    fun originalOf(copy: ClientScriptDefinition): Int = copy.intOperands!![ORIGINAL_CALL + copy.intArgumentCount]
}
