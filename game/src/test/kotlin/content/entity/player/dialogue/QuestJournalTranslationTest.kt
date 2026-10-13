package content.entity.player.dialogue

import WorldTest
import content.quest.journalShown
import content.quest.quests
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.client.ui.InterfaceApi
import world.gregs.voidps.engine.client.variable.ListValues
import world.gregs.voidps.engine.client.variable.MapValues
import world.gregs.voidps.engine.data.definition.FontDefinitions
import world.gregs.voidps.engine.data.definition.VariableDefinitions
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.entity.character.player.skill.exp.Experience
import world.gregs.voidps.engine.get
import java.io.File

/**
 * Opens every quest's journal at every stage of the quest with Portuguese enabled and fails if any
 * sentence is left in English or a Portuguese line is wider than the widest English journal line.
 * With `TRANSLATION_DUMP=<file>` set it also writes the untranslated sentences there, one per line after their quest and a tab.
 */
class QuestJournalTranslationTest : WorldTest() {

    @Test
    fun `Quest journals are in Portuguese and fit their lines`() {
        val untranslated = linkedMapOf<String, String>()
        var current = ""
        val english = mutableListOf<String>()
        val portuguese = mutableListOf<String>()
        val originalMissing = missingTranslation
        val originalShown = journalShown
        missingTranslation = { untranslated.putIfAbsent(it, current) }
        journalShown = { en, shown ->
            english += en
            portuguese += shown
        }
        try {
            // Ghosts Ahoy has a journal but isn't in the quest list yet
            for (quest in quests + "ghosts_ahoy") {
                current = quest
                for (stage in stages(quest)) {
                    // A new player, then one who meets every requirement so they show crossed out
                    for (experienced in listOf(false, true)) {
                        val player = createPlayer(emptyTile, "$quest-$stage-$experienced")
                        player["portuguese_dialogue"] = true
                        if (experienced) {
                            for (skill in Skill.all) {
                                player.experience.set(skill, Experience.MAXIMUM_EXPERIENCE)
                            }
                            for (other in quests) {
                                player[other] = "completed"
                            }
                        }
                        player[quest] = stage
                        InterfaceApi.openQuestJournal(player, quest)
                    }
                }
            }
        } finally {
            missingTranslation = originalMissing
            journalShown = originalShown
        }
        System.getenv("TRANSLATION_DUMP")?.let { File(it).writeText(untranslated.entries.joinToString("\n") { (text, quest) -> "$quest\t$text" }) }

        assertTrue(english.size > 200, "Expected the quest journals to be opened, found ${english.size} lines")
        assertTrue(untranslated.isEmpty(), "Untranslated text:\n${untranslated.keys.joinToString("\n")}")
        val font = get<FontDefinitions>().get("p12_full")
        val limit = english.maxOf { font.textWidth(it) }
        val tooWide = portuguese.distinct().filter { font.textWidth(it) > limit }
        assertTrue(tooWide.isEmpty(), "Wider than the English lines ($limit px):\n${tooWide.joinToString("\n") { "${font.textWidth(it)}px $it" }}")
    }

    /** Every value the quest's variable can hold, "unstarted" and "completed" included */
    private fun stages(quest: String): List<Any> = when (val values = VariableDefinitions.get(quest)?.values) {
        is MapValues -> values.values.keys.toList()
        is ListValues -> values.values
        else -> listOf("unstarted", "completed")
    }
}
