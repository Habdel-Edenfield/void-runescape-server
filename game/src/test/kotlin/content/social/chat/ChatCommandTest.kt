package content.social.chat

import WorldTest
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.client.ui.dialogue
import world.gregs.voidps.network.client.instruction.ChatPublic
import world.gregs.voidps.network.login.protocol.encode.publicChat
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ChatCommandTest : WorldTest() {

    @BeforeAll
    fun start() {
        mockkStatic("world.gregs.voidps.network.login.protocol.encode.ChatEncoderKt")
    }

    @Test
    fun `Commands can be typed into public chat`() = runTest {
        val (player, playerClient) = createClient("player")
        val (_, listenerClient) = createClient("listener")

        player.instructions.send(ChatPublic("::distancia", 0))
        tick()

        assertTrue(player.dialogue!!.startsWith("dialogue_multi"))
        verify(exactly = 0) {
            playerClient.publicChat(any(), any(), any(), any())
            listenerClient.publicChat(any(), any(), any(), any())
        }
    }

    @Test
    fun `Public chat without the command prefix is still said out loud`() = runTest {
        val (player, playerClient) = createClient("player")

        player.instructions.send(ChatPublic("distancia", 0))
        tick()

        assertEquals(null, player.dialogue)
        verify {
            playerClient.publicChat(any(), any(), any(), any())
        }
    }
}
