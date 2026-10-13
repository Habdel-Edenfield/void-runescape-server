package content.entity.player.dialogue

import WorldTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import world.gregs.voidps.engine.client.ui.chat.an
import world.gregs.voidps.engine.client.ui.chat.plural
import world.gregs.voidps.engine.entity.character.player.Player
import java.io.File

/**
 * Reads every chat message and dialogue line written in the skills' code and data tables and fails,
 * naming the text and file, when a Portuguese player would still see it in English.
 * Text built at runtime is checked with every word the code chooses between ("eat"/"drink", "slot"/"slots")
 * and a stand-in for the rest, so a line only passes when each variation has a translation.
 */
class SkillTranslationTest : WorldTest() {

    @TestFactory
    fun `Text in each skill's code is in Portuguese`(): List<DynamicTest> {
        val skills = File(content, "skill")
        val folders = skills.listFiles()!!.filter { it.isDirectory }.sortedBy { it.name }
        val general = skills.listFiles()!!.filter { it.extension == "kt" }
        return folders.map { folder -> dynamicTest(folder.name) { assertTranslated(folder.walkTopDown().filter { it.extension == "kt" }.toList(), skills) } } +
            dynamicTest("general") { assertTranslated(general, skills) }
    }

    @TestFactory
    fun `Text in shops, trading, the bank and Lumbridge is in Portuguese`(): List<DynamicTest> = FOLDERS.map { path ->
        val folder = File(content, path)
        dynamicTest(path) { assertTranslated(folder.walkTopDown().filter { it.extension == "kt" }.toList(), folder) }
    }

    @TestFactory
    fun `Text in each skill's data tables is in Portuguese`(): List<DynamicTest> {
        val data = File(root, "data")
        val skills = File(data, "skill")
        val tables = skills.walkTopDown().filter { it.name.endsWith(".tables.toml") || it.name.endsWith(".recipes.toml") }
        val items = data.walkTopDown().filter { it.name.endsWith(".items.toml") }
        return (tables + items).sortedBy { it.path }.toList().mapNotNull { file ->
            val columns = if (file.name.endsWith(".items.toml")) ITEM_COLUMNS else TABLE_COLUMNS
            val texts = file.readLines().mapNotNull { line ->
                val match = TOML_TEXT.matchEntire(line) ?: return@mapNotNull null
                // A table's header declares each column's type: cooked_message = "string"
                if (match.groupValues[1] !in columns || match.groupValues[2] == "string") null else unescape(match.groupValues[2])
            }.distinct()
            if (texts.isEmpty()) {
                return@mapNotNull null
            }
            dynamicTest(file.relativeTo(data).path) {
                val player = portuguese()
                val untranslated = texts.filter { !player.translates(it) }
                assertTrue(untranslated.isEmpty(), "Untranslated text in ${file.relativeTo(data)}:\n${untranslated.joinToString("\n")}")
            }
        }
    }

    /** The repository, as tests run from either it or the game module */
    private val root: File
        get() = sequenceOf(File(".."), File(".")).map { it.absoluteFile.normalize() }.first { File(it, "game/src/main/kotlin/content").isDirectory }

    private val content: File
        get() = File(root, "game/src/main/kotlin/content")

    private fun portuguese() = createPlayer().also { it["portuguese_dialogue"] = true }

    private fun Player.translates(text: String): Boolean {
        var found = true
        val original = missingTranslation
        missingTranslation = { found = false }
        try {
            translate(text)
        } finally {
            missingTranslation = original
        }
        return found
    }

    private fun assertTranslated(files: List<File>, skills: File) {
        val player = portuguese()
        val untranslated = linkedSetOf<String>()
        for (file in files.sortedBy { it.path }) {
            if (file.name.endsWith("Commands.kt")) {
                // Admin console commands, and the console is never translated
                continue
            }
            for (template in templates(file.readText())) {
                if (template.parts.none { it is Literal && it.text.any(Char::isLetter) } || template.parts.any { it is Literal && '\n' in it.text }) {
                    continue
                }
                // Tables reject strings over 512 bytes, so the generator leaves these in English (see translation.md)
                if (template.pattern in NOT_TEXT || ID.matches(template.pattern) || template.pattern.toByteArray().size > 500) {
                    continue
                }
                for (sample in template.samples()) {
                    if (!player.translates(sample)) {
                        untranslated.add("${file.relativeTo(skills)}: ${template.pattern}" + if (sample != template.pattern) " (as \"$sample\")" else "")
                        break
                    }
                }
            }
        }
        assertTrue(untranslated.isEmpty(), "Untranslated text:\n${untranslated.joinToString("\n")}")
    }

    private sealed interface Part
    private data class Literal(val text: String) : Part
    private data class Expression(val source: String) : Part

    private class Template(val parts: List<Part>) {
        /** As written in the translation table, with each variable as `%1%`, `%2%`... */
        val pattern: String
            get() {
                var count = 0
                return parts.joinToString("") { if (it is Literal) it.text else "%${++count}%" }
            }

        /** Every combination of the words the code chooses between, capped so a long line doesn't explode */
        fun samples(): List<String> {
            var samples = listOf("")
            for (part in parts) {
                val options = if (part is Literal) listOf(part.text) else alternatives(part as Expression)
                samples = samples.flatMap { start -> options.map { start + it } }.take(64)
            }
            return samples
        }
    }

    companion object {
        /** Folders of content/ translated whole besides the skills, as in tools/translation/skill_text.py */
        private val FOLDERS = listOf("entity/npc/shop", "social/trade", "entity/player/bank", "area/misthalin/lumbridge", "quest/free/the_restless_ghost")

        /** Columns holding text sent to the player; pets' hungry phrases are said overhead, which isn't translated */
        private val TABLE_COLUMNS = setOf("message", "cooked_message", "burnt_message", "tip", "question", "failure", "requires_message")
        private val ITEM_COLUMNS = setOf("eat_message", "cast")

        /** Ids passed to calls with the same names, e.g. `Tables.npc("traps.$trap.npc")`, `npc("spirit_wolf_familiar")`, `row.npc("caught")` */
        private val ID = Regex("^[a-z0-9_%]+(?:[._][a-z0-9_%]+)+$|^[a-z]+$")

        /** Matches of the call patterns below which aren't text shown to the player */
        private val NOT_TEXT = setOf(
            // Dungeon generation debug output
            "  Room at (%1%, %2%) [%3%] contains key(s): [ %4% ]",
        )

        private val TOML_TEXT = Regex("""^([a-z_]+) = "((?:[^"\\]|\\.)*)"\s*(?:#.*)?$""")

        /** Calls whose string argument is shown to the player: chat messages, then dialogue boxes, as read by tools/translation/skill_text.py */
        private val CHAT = Regex("""\bmessage\(\s*(?:text\s*=\s*)?(?=")""")
        private val DIALOGUE = listOf(
            Regex("""\b(?:npc|player)(?:<\w+>)?\(\s*(?:(?:npcId\s*=\s*)?(?:"[a-z0-9_]+"|[A-Za-z_][\w.]*)\s*,\s*)?(?=")"""),
            Regex("""\bstatement\(\s*(?=")"""),
            Regex("""\bchoice\(\s*(?=")"""),
            Regex("""\boption(?:<\w+>)?\(\s*(?=")"""),
            Regex("""\bitem\(\s*"[a-z0-9_]+"\s*,\s*(?=")"""),
            Regex("""\bitems\(\s*"[a-z0-9_]+"\s*,\s*"[a-z0-9_]+"\s*,\s*(?=")"""),
            // The title of the how-many box: makeAmount(..., text = "How many would you like to make?")
            Regex("""\btext\s*=\s*(?=")"""),
            // Interface text the server translates before sending it: translate("You have a PIN")
            Regex("""\btranslate\(\s*(?=")"""),
        )

        /** `message(when (x) { 1 -> "..." else -> "..." })`: each branch is a chat message */
        private val MESSAGE_WHEN = Regex("""\bmessage\(\s*when\b[^{]*\{""")
        private val BRANCH = Regex("""->\s*(?=")""")
        private val CONSOLE = Regex("""\s*,\s*(?:type\s*=\s*)?ChatType\.Console\b""")

        private fun templates(source: String): List<Template> {
            val code = withoutComments(source)
            val starts = sortedSetOf<Int>()
            for (match in CHAT.findAll(code)) {
                starts.add(match.range.last + 1)
            }
            for (match in MESSAGE_WHEN.findAll(code)) {
                val block = code.substring(0, blockEnd(code, match.range.last + 1))
                for (branch in BRANCH.findAll(block, match.range.last + 1)) {
                    starts.add(branch.range.last + 1)
                }
            }
            for (regex in DIALOGUE) {
                for (match in regex.findAll(code)) {
                    starts.add(match.range.last + 1)
                }
            }
            return starts.mapNotNull { start ->
                if (code.startsWith("\"\"\"", start)) {
                    return@mapNotNull null
                }
                val (parts, end) = read(code, start)
                // The console is never translated
                if (CONSOLE.matchesAt(code, end)) null else Template(parts)
            }.distinctBy { it.pattern }
        }

        /** Blanks out comments, keeping string literals (which may contain "//") intact */
        private fun withoutComments(source: String): String {
            val out = StringBuilder(source.length)
            var i = 0
            var quoted = false
            while (i < source.length) {
                val c = source[i]
                when {
                    quoted && c == '\\' -> {
                        out.append(c).append(source.getOrElse(i + 1) { ' ' })
                        i += 2
                        continue
                    }
                    !quoted && c == '\'' -> {
                        // Char literals, so '"' doesn't open a string
                        val end = charLiteralEnd(source, i)
                        out.append(source, i, end)
                        i = end
                        continue
                    }
                    c == '"' -> quoted = !quoted
                    !quoted && source.startsWith("//", i) -> {
                        val end = source.indexOf('\n', i).let { if (it == -1) source.length else it }
                        i = end
                        continue
                    }
                    !quoted && source.startsWith("/*", i) -> {
                        val end = source.indexOf("*/", i + 2).let { if (it == -1) source.length else it + 2 }
                        out.append(source.substring(i, end).replace(Regex("[^\n]"), " "))
                        i = end
                        continue
                    }
                }
                out.append(c)
                i++
            }
            return out.toString()
        }

        /** The index of the brace closing the block opened just before [start] */
        private fun blockEnd(code: String, start: Int): Int {
            var depth = 1
            var i = start
            while (depth > 0) {
                when (code[i]) {
                    '"' -> {
                        i = read(code, i).second
                        continue
                    }
                    '\'' -> {
                        i = charLiteralEnd(code, i)
                        continue
                    }
                    '{' -> depth++
                    '}' -> depth--
                }
                i++
            }
            return i - 1
        }

        /** The index after the char literal opening at [start] */
        private fun charLiteralEnd(source: String, start: Int): Int {
            val end = source.indexOf('\'', start + if (source.getOrNull(start + 1) == '\\') 3 else 2)
            return if (end == -1) source.length else end + 1
        }

        /** Reads the string literal opening at [start], returning its parts and the index after the closing quote */
        private fun read(code: String, start: Int): Pair<List<Part>, Int> {
            val parts = mutableListOf<Part>()
            val text = StringBuilder()
            fun flush() {
                if (text.isNotEmpty()) {
                    parts.add(Literal(text.toString()))
                    text.clear()
                }
            }
            var i = start + 1
            while (i < code.length) {
                val c = code[i]
                when {
                    c == '"' -> {
                        flush()
                        return parts to i + 1
                    }
                    c == '\\' -> {
                        val next = code[i + 1]
                        when (next) {
                            'n' -> text.append('\n')
                            't' -> text.append('\t')
                            'u' -> {
                                text.append(code.substring(i + 2, i + 6).toInt(16).toChar())
                                i += 4
                            }
                            else -> text.append(next)
                        }
                        i += 2
                    }
                    c == '$' && code.getOrNull(i + 1) == '{' -> {
                        flush()
                        var depth = 1
                        var j = i + 2
                        while (depth > 0) {
                            when (code[j]) {
                                '{' -> depth++
                                '}' -> depth--
                                '"' -> {
                                    j = read(code, j).second
                                    continue
                                }
                                '\'' -> {
                                    j = charLiteralEnd(code, j)
                                    continue
                                }
                            }
                            j++
                        }
                        parts.add(Expression(code.substring(i + 2, j - 1).trim()))
                        i = j
                    }
                    c == '$' && code.getOrNull(i + 1)?.let { it.isLetter() || it == '_' } == true -> {
                        flush()
                        var j = i + 1
                        while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '_')) {
                            j++
                        }
                        parts.add(Expression(code.substring(i + 1, j)))
                        i = j
                    }
                    else -> {
                        text.append(c)
                        i++
                    }
                }
            }
            error("Unterminated string at $start")
        }

        private val IF_ELSE = Regex("""^if\s*\(.*?\)\s*(.+?)\s+else\s+(.+)$""", RegexOption.DOT_MATCHES_ALL)
        private val WHEN_BRANCH = Regex("""->\s*"((?:[^"\\$]|\\.)*)"""")
        private val PLURAL = Regex("""^"([^"$]*)"\.plural\(""")
        private val ELVIS = Regex("""\?:\s*"([^"$]*)"$""")
        private val STRING = Regex("""^"([^"$]*)"$""")

        /** Stand-in for a value the code computes, e.g. a number or an item name */
        private const val VALUE = "5"

        /** The texts a `${...}` or `$name` can produce: each word it chooses between, or [VALUE] */
        private fun alternatives(expression: Expression): List<String> {
            val source = expression.source
            IF_ELSE.matchEntire(source)?.let { match -> return match.groupValues.drop(1).map { STRING.matchEntire(it.trim())?.groupValues?.get(1) ?: VALUE } }
            if (source.startsWith("when")) {
                return WHEN_BRANCH.findAll(source).map { it.groupValues[1] }.toList().ifEmpty { listOf(VALUE) }
            }
            PLURAL.find(source)?.let { match -> return listOf(match.groupValues[1].plural(1), match.groupValues[1].plural(2)) }
            if (source.endsWith(".an()")) {
                return listOf("thing".an(), "item".an(), "things".an()).distinct()
            }
            ELVIS.find(source)?.let { match -> return listOf(match.groupValues[1], VALUE) }
            return listOf(VALUE)
        }

        private fun unescape(text: String) = text.replace(Regex("""\\(.)""")) { it.groupValues[1] }
    }
}
