package world.gregs.voidps.cache.translation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import world.gregs.voidps.cache.CacheDelegate
import world.gregs.voidps.cache.translation.CacheTranslationPatch.Op
import world.gregs.voidps.cache.translation.CacheTranslationPatch.Source
import java.io.File

/**
 * The Skill Advance Guide and the level up pop-up's list of unlocks read their text from the guide's data scripts,
 * which the patch copies with the Portuguese in place: every text has Portuguese, the guide only ever calls the
 * copies, and a copy shows the same as the original apart from the text.
 */
class SkillGuideTranslationTest {

    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { it.resolve("data/cache").isDirectory }
    private val source = Source(CacheDelegate(root.resolve("data/cache").path + "/"))
    private val rows = CacheTranslationPatch.readTable(root.resolve(CacheTranslationPatch.TABLE_PATH))
    private val guide = CacheTranslationPatch.readTable(root.resolve(SkillGuideTranslation.TABLE_PATH))

    @Test
    fun `Every skill guide text has Portuguese`() {
        val translated = guide.map { it.english }.toSet()
        val missing = SkillGuideTranslation.texts(source).filter { it.second !in translated }.map { "script ${it.first}: ${it.second}" }
        assertTrue(missing.isEmpty(), "Left in English (add them to tools/translation/sources/pt_guide_*.json):\n${missing.joinToString("\n")}")
    }

    @Test
    fun `The guide only calls the Portuguese copies`() {
        val patch = CacheTranslationPatch.patch(source, rows, guide)
        val calls = mutableListOf<String>()
        for (id in SkillGuideTranslation.CALLERS) {
            val script = patch.scripts.getValue(id)
            for ((i, op) in script.instructions.withIndex()) {
                if (op == Op.GOSUB && script.intOperands!![i] in SkillGuideTranslation.DATA_SCRIPTS) {
                    calls.add("script $id calls ${script.intOperands!![i]} at $i")
                }
            }
        }
        assertTrue(calls.isEmpty(), "Calls reading the English:\n${calls.joinToString("\n")}")
        assertEquals(SkillGuideTranslation.DATA_SCRIPTS.toSet(), patch.guideCopies.keys)
    }

    @Test
    fun `A copy shows the Portuguese joined the same way`() {
        val patch = CacheTranslationPatch.patch(source, rows, guide)
        val portuguese = guide.associate { it.english to it.portuguese }
        val wrong = mutableListOf<String>()
        for ((id, copyId) in patch.guideCopies) {
            val original = source.script(id)!!
            val copy = patch.scripts.getValue(copyId)
            assertEquals(id, SkillGuideTranslation.originalOf(copy))
            val offset = copy.instructions.size - original.instructions.size
            assertTrue(copy.instructions.drop(offset) == original.instructions.toList(), "Script $id's copy runs different instructions.")
            for (unit in SkillGuideTranslation.units(original)) {
                val shown = unit.pushes.joinToString("") { copy.stringOperands!![it + offset]!! }
                val expected = portuguese[unit.english] ?: unit.english
                if (shown != expected) {
                    wrong.add("script $id: \"$shown\" instead of \"$expected\"")
                }
            }
        }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
    }
}
