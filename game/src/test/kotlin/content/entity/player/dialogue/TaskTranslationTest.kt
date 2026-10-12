package content.entity.player.dialogue

import WorldTest
import containsMessage
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import world.gregs.voidps.cache.definition.data.InterfaceDefinition
import world.gregs.voidps.engine.client.ui.open
import world.gregs.voidps.engine.client.variable.PlayerVariables
import world.gregs.voidps.engine.data.definition.EnumDefinitions
import world.gregs.voidps.engine.data.definition.StructDefinitions
import world.gregs.voidps.engine.data.definition.Tables
import world.gregs.voidps.network.login.protocol.encode.interfaceText
import world.gregs.voidps.network.login.protocol.encode.sendVarc
import world.gregs.voidps.type.Tile

/**
 * The Task System in Portuguese, server side: chat messages name tasks like the Task List does and the
 * fixed labels are sent. The interfaces themselves come from the cache; `CacheTranslationPatchTest` in the
 * cache module checks those.
 */
class TaskTranslationTest : WorldTest() {

    private val lumbridge = Tile(3222, 3218)

    @Test
    fun `Completing a task names it in Portuguese`() {
        val player = createPlayer(lumbridge)
        player["portuguese_dialogue"] = true

        player["on_the_run_task"] = true
        tick()

        assertTrue(player.containsMessage("Você completou a tarefa 'Na correria' do conjunto de Lumbridge/Draynor (Iniciante)!"))
    }

    @Test
    fun `Every task in the translated sets is named in Portuguese in chat`() {
        val player = createPlayer()
        player["portuguese_dialogue"] = true
        val names = Tables.get("interface_pt_br").rows().filter { it.rowId.startsWith("name_") }.associate { it.string("en") to it.string("pt") }

        val untranslated = mutableListOf<String>()
        for ((_, struct) in EnumDefinitions.get("task_structs").map!!) {
            val task = StructDefinitions.getOrNull(struct as Int) ?: continue
            if (task["task_area", -1] !in setOf(1, 60)) {
                continue
            }
            val name = task["task_name", ""]
            val portuguese = names[name]
            if (portuguese == null || player.translate("You have completed the Task '$name'!") != "Você completou a tarefa '$portuguese'!") {
                untranslated.add(name)
            }
        }

        assertTrue(untranslated.isEmpty(), "Task names left in English:\n${untranslated.joinToString("\n")}")
    }

    @Test
    fun `Finishing a set names the set and who gives the reward`() {
        val player = createPlayer()
        player["portuguese_dialogue"] = true

        assertEquals(
            "Parabéns! Você completou todas as tarefas do conjunto de Lumbridge e Draynor (Média).",
            player.translate("Congratulations! You have completed all of the Medium Tasks in the Lumbridge and Draynor"),
        )
        assertEquals(
            "Fale com o Ned, em Draynor Village, para receber sua recompensa.",
            player.translate("set. Speak to Ned in Draynor Village to claim your reward."),
        )
    }

    @Test
    fun `Task list labels are sent in Portuguese`() {
        mockkStatic("world.gregs.voidps.network.login.protocol.encode.InterfaceEncodersKt")
        val (player, client) = createClient("translated", lumbridge)
        player["portuguese_dialogue"] = true

        player.open("task_list")

        verify { client.interfaceText(InterfaceDefinition.pack(917, 28), "Descrição") }
        verify { client.interfaceText(InterfaceDefinition.pack(917, 20), "Dicas") }
    }

    @Test
    fun `Task list labels are left to the client in English`() {
        mockkStatic("world.gregs.voidps.network.login.protocol.encode.InterfaceEncodersKt")
        val (player, client) = createClient("english", lumbridge)

        player.open("task_list")

        verify(exactly = 0) { client.interfaceText(InterfaceDefinition.pack(917, 28), any()) }
    }

    @Test
    fun `Level up pop-up labels are sent in Portuguese`() {
        mockkStatic("world.gregs.voidps.network.login.protocol.encode.InterfaceEncodersKt")
        val (player, client) = createClient("leveller", lumbridge)
        player["portuguese_dialogue"] = true

        player.open("skill_level_details")

        verify { client.interfaceText(InterfaceDefinition.pack(741, 5), "Parabéns!") }
    }

    @Test
    fun `The client is told which language the interfaces use`() {
        mockkStatic("world.gregs.voidps.network.login.protocol.encode.VarcEncoderKt")
        val (player, client) = createClient("switcher", lumbridge)
        // A real login hands the client to the variables too (AccountManager.setup)
        (player.variables as PlayerVariables).client = client

        player["portuguese_dialogue"] = true
        player["portuguese_dialogue"] = false

        verify { client.sendVarc(2000, 1) }
        verify { client.sendVarc(2000, 0) }
    }
}
