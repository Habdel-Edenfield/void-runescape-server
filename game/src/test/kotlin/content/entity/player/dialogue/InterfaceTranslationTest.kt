package content.entity.player.dialogue

import WorldTest
import content.quest.questComplete
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import world.gregs.voidps.cache.definition.data.InterfaceDefinition
import world.gregs.voidps.engine.client.ui.open
import world.gregs.voidps.network.login.protocol.encode.interfaceText
import world.gregs.voidps.type.Tile

/**
 * Interfaces whose fixed labels come from the cache get them in Portuguese from the server when they open,
 * next to the text the server writes into them itself.
 */
class InterfaceTranslationTest : WorldTest() {

    private val lumbridge = Tile(3222, 3218)

    @Test
    fun `Quest complete scroll is in Portuguese`() {
        mockkStatic("world.gregs.voidps.network.login.protocol.encode.InterfaceEncodersKt")
        val (player, client) = createClient("quester", lumbridge)
        player["portuguese_dialogue"] = true

        player.questComplete("Cook's Assistant", "1 Quest Point", "300 Cooking XP", "500 coins", "20 sardines", "Access to the cook's range")

        verify { client.interfaceText(InterfaceDefinition.pack(277, 3), "Parabéns!") }
        verify { client.interfaceText(InterfaceDefinition.pack(277, 6), "Pontos de missão:") }
        verify { client.interfaceText(InterfaceDefinition.pack(277, 4), "Você completou Cook's Assistant!") }
        verify { client.interfaceText(InterfaceDefinition.pack(277, 10), "1 ponto de missão") }
        verify { client.interfaceText(InterfaceDefinition.pack(277, 14), "Acesso ao fogão do cozinheiro") }
    }

    @Test
    fun `Labels are left to the client in English`() {
        mockkStatic("world.gregs.voidps.network.login.protocol.encode.InterfaceEncodersKt")
        val (player, client) = createClient("english", lumbridge)

        player.open("shop")

        verify(exactly = 0) { client.interfaceText(InterfaceDefinition.pack(620, 24), any()) }
    }

    @Test
    fun `Shop labels are sent in Portuguese`() {
        mockkStatic("world.gregs.voidps.network.login.protocol.encode.InterfaceEncodersKt")
        val (player, client) = createClient("shopper", lumbridge)
        player["portuguese_dialogue"] = true

        player.open("shop")

        verify { client.interfaceText(InterfaceDefinition.pack(620, 30), "Preço") }
    }

    @Test
    fun `Trade window text is in Portuguese`() {
        val player = createPlayer()
        player["portuguese_dialogue"] = true

        assertEquals("Trocando com: Zezinho", player.translate("Trading with: Zezinho"))
        assertEquals("tem 28 espaços livres no inventário.", player.translate("has 28 free inventory slots."))
        assertEquals("Esperando o outro jogador..", player.translate("Waiting for other .."))
        assertEquals("Tem certeza de que quer fazer esta troca?", player.translate("Are you sure you want to make this trade?"))
    }
}
