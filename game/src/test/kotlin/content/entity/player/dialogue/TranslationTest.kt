package content.entity.player.dialogue

import WorldTest
import content.area.misthalin.tutorial_island.tutorialStage
import npcOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import skipDialogues
import world.gregs.voidps.engine.data.definition.Rows
import world.gregs.voidps.engine.data.definition.Tables
import world.gregs.voidps.type.Tile

class TranslationTest : WorldTest() {

    @Test
    fun `Dialogue stays in English by default`() {
        val player = createPlayer()

        assertEquals("Thanks, I'll do that.", player.translate("Thanks, I'll do that."))
    }

    @Test
    fun `Dialogue is translated once Portuguese is enabled`() {
        val player = createPlayer()
        player["portuguese_dialogue"] = true

        assertEquals("Obrigado, vou fazer isso.", player.translate("Thanks, I'll do that."))
    }

    @Test
    fun `Untranslated dialogue falls back to English`() {
        val player = createPlayer()
        player["portuguese_dialogue"] = true

        assertEquals("Nobody has translated this.", player.translate("Nobody has translated this."))
    }

    @Test
    fun `Tutorial instructions use the Portuguese row once enabled`() {
        val player = createPlayer()
        val stage = Rows.get("tutorial_island.stage_0")

        assertEquals("Getting started", player.translate(stage).string("title"))
        player["portuguese_dialogue"] = true
        assertEquals("Primeiros passos", player.translate(stage).string("title"))
        assertEquals("tutorial_island.stage_4", player.translate(Rows.get("tutorial_island.stage_4")).stringId)
    }

    @Test
    fun `Guide still advances the tutorial in Portuguese`() {
        val player = createPlayer(Tile(3095, 3107)) {
            it["tutorial_stage"] = 0
            it["tutorial_designed"] = true
            it["portuguese_dialogue"] = true
        }
        val guide = createNPC("runescape_guide", Tile(3094, 3107))

        player.npcOption(guide, "Talk-to")
        tick(3)
        player.skipDialogues()

        assertEquals(1, player.tutorialStage)
    }

    @Test
    fun `Translations only use characters the client can display`() {
        // Strings are sent one byte per character, so anything past Latin-1 (curly quotes, em dashes) would be garbled
        val text = Tables.get("dialogue_pt_br").rows().map { it.string("pt") } +
            Tables.get("tutorial_island_pt_br").rows().flatMap { it.stringList("lines") + it.string("title") }
        for (line in text) {
            assertTrue(line.all { it.code in 0x20..0xFF }, "Unsupported character in: $line")
        }
    }
}
