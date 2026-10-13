package content.entity.player.dialogue

import WorldTest
import content.entity.player.bank.pin.BankPin
import interfaceOption
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.client.ui.close
import world.gregs.voidps.engine.client.ui.open
import world.gregs.voidps.engine.client.variable.start
import world.gregs.voidps.engine.data.definition.FontDefinitions
import world.gregs.voidps.engine.data.definition.InterfaceDefinitions
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.get
import world.gregs.voidps.engine.timer.epochSeconds
import world.gregs.voidps.network.client.Client
import world.gregs.voidps.network.login.protocol.encode.interfaceText

/**
 * Goes through the Bank PIN settings with Portuguese enabled and fails if any text is left in English or a
 * message line is wider than the English ones: the server breaks these messages into one line per component.
 */
class BankPinTranslationTest : WorldTest() {

    @Test
    fun `Bank PIN settings are in Portuguese and fit their lines`() {
        mockkStatic("world.gregs.voidps.network.login.protocol.encode.InterfaceEncodersKt")
        val untranslated = linkedSetOf<String>()
        val original = missingTranslation
        missingTranslation = { untranslated.add(it) }
        try {
            val english = messageLines(portuguese = false)
            val portuguese = messageLines(portuguese = true)

            assertTrue(untranslated.isEmpty(), "Untranslated text:\n${untranslated.joinToString("\n")}")
            val font = get<FontDefinitions>().get("p12_full")
            val limit = english.maxOf { font.textWidth(it) }
            val tooWide = portuguese.filter { font.textWidth(it) > limit }
            assertTrue(tooWide.isEmpty(), "Wider than the English lines ($limit px):\n${tooWide.joinToString("\n") { "${font.textWidth(it)}px $it" }}")
            assertTrue(portuguese.any { it.startsWith("Lembramos") }, "Expected the Portuguese messages, got:\n${portuguese.joinToString("\n")}")
        } finally {
            missingTranslation = original
        }
    }

    /** Plays every way the settings box shows a message and returns the lines sent to the message components */
    private fun messageLines(portuguese: Boolean): List<String> {
        val (player, client) = createClient(if (portuguese) "portuguese" else "english", emptyTile)
        player["portuguese_dialogue"] = portuguese
        // Default messages, recovery delay without a pin, and cancelling
        player.open("bank_pin_settings")
        player.interfaceOption("bank_pin_settings", "recovery_new", "Change your recovery delay")
        player.interfaceOption("bank_pin_settings", "set_pin", "Set a PIN")
        player.interfaceOption("bank_pin_settings", "cancel", "Cancel")
        // An easily guessed pin, then a mismatch, then a pin set
        player.choose("1111", "1111")
        player.choose("1739", "2846")
        player.choose("1739", "1739")
        player.interfaceOption("bank_pin_settings", "recovery", "Change your recovery delay")
        // Deleting it
        player.interfaceOption("bank_pin_settings", "delete_pin", "Delete your PIN")
        player.interfaceOption("bank_pin_settings", "confirm", "Confirm")
        // A change and a deletion waiting for the recovery delay, for one day and for several
        for (days in listOf(1, 3)) {
            BankPin.setPin(player, "1739")
            BankPin.setPending(player, "2846")
            player.start("bank_pin_change", days * 86400, epochSeconds())
            player.reopen()
            player.interfaceOption("bank_pin_settings", "cancel_pending", "Cancel your PIN")
            BankPin.requestForgottenDelete(player)
            player.start("bank_pin_change", days * 86400, epochSeconds())
            player.reopen()
            player.interfaceOption("bank_pin_settings", "cancel_pending", "Cancel your PIN")
        }
        return client.sentTo("bank_pin_settings", (1..13).map { "message_$it" }).filter { it.isNotEmpty() }
    }

    private fun Player.choose(first: String, second: String) {
        interfaceOption("bank_pin_settings", "set_pin", "Set a PIN")
        interfaceOption("bank_pin_settings", "confirm", "Confirm")
        enterPin(first)
        enterPin(second)
    }

    private fun Player.reopen() {
        close("bank_pin_settings")
        open("bank_pin_settings")
    }

    private fun Player.enterPin(pin: String) {
        for (digit in pin) {
            val layout: List<Int> = get("bank_pin_layout") ?: return
            interfaceOption("bank_pin", "digit_${layout.indexOf(digit.digitToInt()) + 1}", "Enter digit")
        }
    }

    private fun Client.sentTo(id: String, components: List<String>): List<String> {
        val packed = components.map { InterfaceDefinitions.getComponent(id, it)!!.id }.toSet()
        val texts = mutableListOf<String>()
        verify(atLeast = 0) { interfaceText(match { it in packed }, capture(texts)) }
        return texts
    }
}
