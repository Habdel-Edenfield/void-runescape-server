package world.gregs.voidps.cache.translation

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import world.gregs.voidps.cache.CacheDelegate
import world.gregs.voidps.cache.definition.data.ClientScriptDefinition
import world.gregs.voidps.cache.translation.CacheTranslationPatch.Op
import world.gregs.voidps.cache.translation.CacheTranslationPatch.Source
import world.gregs.voidps.cache.translation.CacheTranslationPatch.hooks
import java.io.File

/**
 * The Task System interfaces (task list, sidebar tab, pop-up) and the level up pop-up are drawn by the client
 * from the cache, so these check what the client would show a Portuguese player: every text in scope has
 * Portuguese, no interface script reads the English directly once patched, and data/cache has the patch.
 * The level up pop-up's list of unlocks comes from the skill guides, which aren't translated yet.
 */
class CacheTranslationPatchTest {

    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { it.resolve("data/cache").isDirectory }
    private val source = Source(CacheDelegate(root.resolve("data/cache").path + "/"))
    private val rows = CacheTranslationPatch.readTable(root.resolve(CacheTranslationPatch.TABLE_PATH))

    private val kinds = mapOf(
        "name" to listOf("name_"),
        "text" to listOf("desc_", "hint", "set_"),
        "difficulty" to listOf("difficulty_"),
        "level_up" to listOf("level_up_"),
        "prayer" to listOf("prayer_"),
        "month" to listOf("month_"),
        "area" to listOf("area_"),
        "script" to listOf("script_"),
        "tooltip" to listOf("tooltip_"),
        "chat" to listOf("chat_"),
    )

    @Test
    fun `Every text the cache interfaces show in scope has Portuguese`() {
        val missing = mutableListOf<String>()
        for (text in CacheTranslationPatch.texts(source, all = false)) {
            val found = if (text.kind == "label") {
                rows.any { it.id == text.row && it.english == text.english }
            } else {
                text.english in CacheTranslationPatch.kind(rows, *kinds.getValue(text.kind).toTypedArray())
            }
            if (!found) {
                missing.add("${text.kind}: ${text.english}")
            }
        }
        assertTrue(missing.isEmpty(), "Left in English (add them to tools/translation/sources/pt_tasks_*.json):\n${missing.joinToString("\n")}")
    }

    @Test
    fun `Every fixed label is translated or kept on purpose`() {
        // Components a script names may be written by it, so only the ones no script names have to be listed
        val scripts = source.scriptIds().mapNotNull { source.script(it) }
        val referenced = scripts.flatMap { script -> script.instructions.indices.filter { script.instructions[it] == Op.PUSH_INT }.map { script.intOperands!![it] } }.toSet()
        val unknown = mutableListOf<String>()
        for (id in CacheTranslationPatch.LABELS.keys) {
            val listed = CacheTranslationPatch.LABELS.getValue(id) + CacheTranslationPatch.LABELS_KEPT.getValue(id)
            for (component in source.interfaces[id].components!!) {
                val index = component.id and 0xffff
                if (component.type == 4 && component.text.isNotBlank() && ((id shl 16) or index) !in referenced && index !in listed) {
                    unknown.add("$id:$index \"${component.text}\"")
                }
            }
        }
        assertTrue(unknown.isEmpty(), "Labels nothing translates:\n${unknown.joinToString("\n")}")
    }

    @Test
    fun `Interface scripts read no English directly once patched`() {
        val patch = CacheTranslationPatch.patch(source, rows)
        val view = source.scriptIds().associateWith { patch.scripts[it] ?: source.script(it)!! } + patch.scripts
        val ours = setOf(patch.paramText, patch.taskEnum, patch.enumString, patch.tooltip) + patch.scripts.filterValues { it.name == CacheTranslationPatch.STRING }.keys
        val english = mutableListOf<String>()
        for ((id, script) in reachable(view)) {
            if (id in ours) {
                continue
            }
            val ints = script.intOperands ?: IntArray(script.instructions.size)
            for ((i, op) in script.instructions.withIndex()) {
                if (op == Op.STRUCT_PARAM && i > 0 && script.instructions[i - 1] == Op.PUSH_INT && ints[i - 1] in CacheTranslationPatch.TEXT_PARAMS) {
                    english.add("script $id reads task text param ${ints[i - 1]} at $i")
                }
                if (op == Op.PUSH_INT && ints[i] in setOf(CacheTranslationPatch.AREA_NAMES, CacheTranslationPatch.DIFFICULTY_NAMES) && i > 1 && ints[i - 1] == 115) {
                    val next = (i + 1 until script.instructions.size).first { script.instructions[it] == Op.ENUM || script.instructions[it] == Op.GOSUB }
                    if (script.instructions[next] == Op.ENUM) {
                        english.add("script $id reads enum ${ints[i]} at $next")
                    }
                }
                if (op == Op.ENUM_STRING && i > 1 && script.instructions[i - 2] == Op.PUSH_INT && ints[i - 2] == CacheTranslationPatch.LEVEL_UP_MESSAGES) {
                    english.add("script $id reads enum ${CacheTranslationPatch.LEVEL_UP_MESSAGES} at $i")
                }
                val string = script.stringOperands?.get(i)
                if (op == Op.PUSH_STRING && string != null && string !in CacheTranslationPatch.SCRIPT_TEXT_KEPT && !CacheTranslationPatch.isSignature(string)) {
                    english.add("script $id shows \"$string\" at $i")
                }
            }
        }
        for (hook in CacheTranslationPatch.HOOK_SCRIPTS.keys) {
            val script = view.getValue(hook)
            if (script.instructions.indices.none { script.instructions[it] == Op.GOSUB && script.intOperands!![it] == patch.tooltip }) {
                english.add("hook script $hook doesn't translate")
            }
        }
        val translatedTooltips = patch.scripts.getValue(patch.tooltip).stringOperands.orEmpty().toSet()
        for (text in CacheTranslationPatch.hookTexts(source)) {
            if (text !in translatedTooltips) {
                english.add("tooltip \"$text\"")
            }
        }
        val inScope = source.tasks().filter { source.area(it) in CacheTranslationPatch.AREAS } +
            source.structs.filter { it.params?.containsKey(CacheTranslationPatch.PRAYER_DESCRIPTION) == true }
        for (struct in inScope) {
            for (param in CacheTranslationPatch.TEXT_PARAMS) {
                val text = struct.params?.get(param) as? String
                val translated = patch.paramEnums[param]?.let { patch.enums.getValue(it).map!!.containsKey(struct.id) } ?: false
                if (!text.isNullOrEmpty() && !translated) {
                    english.add("struct ${struct.id} param $param: $text")
                }
            }
        }
        // The tabs' own scripts, checked whole as they don't hang off the interfaces the way the task scripts do
        for ((id, kept) in CacheTranslationPatch.CHECKED_SCRIPTS) {
            val script = view.getValue(id)
            val signatures = signatures(script)
            for ((i, string) in script.stringOperands.orEmpty().withIndex()) {
                if (string == null || i in signatures) {
                    continue
                }
                if (string !in CacheTranslationPatch.SCRIPT_TEXT_KEPT && string !in kept && !CacheTranslationPatch.isSignature(string) && !string.isMarkup()) {
                    english.add("script $id shows \"$string\"")
                }
            }
        }
        assertTrue(english.isEmpty(), "English a Portuguese player would see:\n${english.joinToString("\n")}")
    }

    @Test
    fun `Scripts the patch changes are written back byte for byte`() {
        val patch = CacheTranslationPatch.patch(source, rows)
        // Only the original scripts: the added ones hold Portuguese, which the decoder can't read back (UTF-8 vs Latin-1)
        for ((id, script) in patch.scripts) {
            if (script.name.orEmpty().startsWith("pt_br_")) {
                continue
            }
            val bytes = source.scriptBytes(id) ?: continue
            assertArrayEquals(bytes, CacheTranslationPatch.encode(source.script(id)!!), "Script $id")
        }
    }

    @Test
    fun `The cache has the current translation`() {
        val differences = CacheTranslationPatch.differences(source, CacheTranslationPatch.patch(source, rows))
        assertTrue(
            differences.isEmpty(),
            "data/cache is missing the current interface translation; with the server stopped run " +
                "./gradlew :tools:translateInterfaces --args=patch\nOut of date: ${differences.joinToString()}",
        )
    }

    /** Indices of the argument types pushed right before each hook a script registers ("iz", "IisiiIIIf"...) */
    private fun signatures(script: ClientScriptDefinition): Set<Int> {
        val ops = script.instructions
        val found = mutableSetOf<Int>()
        for ((i, op) in ops.withIndex()) {
            if (op in 1400..1499 || op in 2400..2499) {
                (i - 1 downTo maxOf(0, i - 2)).firstOrNull { ops[it] == Op.PUSH_STRING }?.let { found.add(it) }
            }
        }
        return found
    }

    /** Tags, numbers and punctuation: nothing to translate */
    private fun String.isMarkup() = replace(Regex("<[^>]+>"), "").none { it.isLetter() }

    /** The level up pop-up's own scripts; the skill guide scripts it borrows its list of unlocks from are left out */
    private val levelUp = setOf(972, 973, 974, 975, 976, 1023, 1025)

    /** Task scripts the three interfaces and the server can run, following calls and hooks set at runtime */
    private fun reachable(scripts: Map<Int, ClientScriptDefinition>): Map<Int, ClientScriptDefinition> {
        val queue = ArrayDeque(listOf(3983, 4101, 3971, 4002)) // run by the server
        for (id in CacheTranslationPatch.INTERFACES.values) {
            for (component in source.interfaces[id].components!!) {
                component.hooks().mapNotNullTo(queue) { it.firstOrNull() as? Int }
            }
        }
        val found = mutableMapOf<Int, ClientScriptDefinition>()
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (id in found || (id !in 3222..3227 && id !in 3960..scripts.keys.max() && id !in levelUp)) {
                continue
            }
            val script = scripts[id] ?: continue
            found[id] = script
            val ints = script.intOperands ?: continue
            for ((i, op) in script.instructions.withIndex()) {
                if (op == Op.GOSUB) {
                    queue.add(ints[i])
                }
                if (op in 1400..1499 || op in 2400..2499) {
                    hookScript(script, i)?.let { queue.add(it) }
                }
            }
        }
        return found
    }

    /** The script a hook instruction at [index] registers: pushed before its arguments, which the signature string counts */
    private fun hookScript(script: ClientScriptDefinition, index: Int): Int? {
        val ops = script.instructions
        val ints = script.intOperands!!
        val strings = script.stringOperands ?: return null
        val signature = (index - 1 downTo maxOf(0, index - 2)).firstOrNull { ops[it] == Op.PUSH_STRING } ?: return null
        var arguments = strings[signature]!!.count { it != 's' }
        for (i in signature - 1 downTo 0) {
            when (ops[i]) {
                Op.PUSH_INT, Op.ILOAD, Op.GET_VARC, Op.GET_VARBIT, Op.GET_VARP -> {
                    if (arguments-- == 0) {
                        return if (ops[i] == Op.PUSH_INT) ints[i] else null
                    }
                }
                Op.PUSH_STRING, Op.SLOAD -> {}
                else -> error("Can't follow the arguments of the hook in script ${script.id} at $index")
            }
        }
        return null
    }
}
