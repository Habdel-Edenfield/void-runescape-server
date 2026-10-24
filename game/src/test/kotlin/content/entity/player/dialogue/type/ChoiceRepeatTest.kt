package content.entity.player.dialogue.type

import WorldTest
import dialogueContinue
import dialogueOption
import npcOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.client.ui.dialogue
import world.gregs.voidps.type.Tile

class ChoiceRepeatTest : WorldTest() {

    @Test
    fun `Picked option isn't repeated by the player`() {
        val player = createPlayer(Tile(3237, 3219))
        val sage = createNPC("lumbridge_sage", Tile(3238, 3219))

        player.npcOption(sage, "Talk-to")
        tick()
        player.dialogueContinue()
        tick()
        assertEquals("dialogue_multi3", player.dialogue)
        player.dialogueOption(2)
        tick()

        assertTrue(player.dialogue!!.startsWith("dialogue_npc_chat"), "Expected the npc to answer, found ${player.dialogue}")
    }

    @Test
    fun `Only option is repeated by the player when the menu is skipped`() {
        val player = createPlayer(Tile(3191, 3361))
        val guildmaster = createNPC("guildmaster", Tile(3191, 3362))

        player.npcOption(guildmaster, "Talk-to")
        tick()
        player.dialogueContinue()
        tick()

        assertTrue(player.dialogue!!.startsWith("dialogue_chat"), "Expected the player to ask, found ${player.dialogue}")
    }
}
