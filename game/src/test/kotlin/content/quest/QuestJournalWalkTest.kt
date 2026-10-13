package content.quest

import WorldTest
import interfaceOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.client.ui.InterfaceApi
import world.gregs.voidps.engine.client.ui.open
import world.gregs.voidps.engine.data.definition.QuestDefinitions
import world.gregs.voidps.engine.entity.character.mode.move.target.CharacterTargetStrategy
import world.gregs.voidps.engine.entity.character.move.running
import world.gregs.voidps.engine.entity.character.npc.NPCs
import world.gregs.voidps.engine.get
import world.gregs.voidps.type.Tile

class QuestJournalWalkTest : WorldTest() {

    override var loadNpcs: Boolean = true

    @Test
    fun `Clicking Xenia in the Blood Pact journal walks next to her`() {
        val player = createPlayer(Tile(3222, 3218))
        player.running = true
        player.open("quest_journals")
        tick()

        player.interfaceOption("quest_journals", "journals", "View", slot = get<QuestDefinitions>().get("blood_pact").id)

        // "I can start this quest by speaking to Xenia in the" is the first line
        assertEquals(mapOf(1 to "blood_pact_xenia"), player.get<Map<Int, String>>("quest_journal_walks"))
        player.interfaceOption("quest_scroll", "line1", "Walk")
        tick(40)

        val xenia = NPCs.first { it.id == "xenia" }
        assertTrue(CharacterTargetStrategy(xenia).reached(player), "Stopped at ${player.tile}, Xenia at ${xenia.tile}")
    }

    @Test
    fun `Portuguese journal keeps Xenia clickable`() {
        val player = createPlayer(Tile(3222, 3218))
        player["portuguese_dialogue"] = true
        player.open("quest_journals")
        tick()

        player.interfaceOption("quest_journals", "journals", "View", slot = get<QuestDefinitions>().get("blood_pact").id)

        assertEquals(listOf("blood_pact_xenia"), player.get<Map<Int, String>>("quest_journal_walks")?.values?.toList())
    }

    @Test
    fun `Lines of a journal without npcs don't walk`() {
        val player = createPlayer(Tile(3222, 3218))
        player.open("quest_journals")
        tick()
        player.interfaceOption("quest_journals", "journals", "View", slot = get<QuestDefinitions>().get("blood_pact").id)
        assertTrue(player.contains("quest_journal_walks"))

        // Opened without the quest list, like the commands list
        InterfaceApi.openQuestJournal(player, "blood_pact")

        assertTrue(!player.contains("quest_journal_walks"))
    }
}
