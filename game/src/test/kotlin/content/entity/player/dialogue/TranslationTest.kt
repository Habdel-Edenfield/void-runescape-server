package content.entity.player.dialogue

import WorldTest
import containsMessage
import content.area.misthalin.tutorial_island.inTutorial
import content.area.misthalin.tutorial_island.tutorialStage
import dialogueOption
import io.mockk.mockkStatic
import io.mockk.verify
import npcOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import skipDialogues
import world.gregs.voidps.engine.client.message
import world.gregs.voidps.engine.client.ui.dialogue
import world.gregs.voidps.engine.data.Settings
import world.gregs.voidps.engine.data.definition.FontDefinitions
import world.gregs.voidps.engine.data.definition.Rows
import world.gregs.voidps.engine.data.definition.Tables
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.chat.ChatType
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.get
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.network.login.protocol.encode.interfaceText
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
    fun `Server name placeholder is filled in both languages`() {
        val player = createPlayer()
        player["portuguese_dialogue"] = true
        val name = Settings["server.name"]

        assertEquals("Bem-vindo a $name.", player.translate("Welcome to $name."))
    }

    @Test
    fun `Placeholders carry variable text into the translation`() {
        val player = createPlayer()
        player["portuguese_dialogue"] = true

        assertEquals("Parabéns! Você subiu um nível em Agility!", player.translate("Congratulations! You've just advanced an Agility level!"))
        assertEquals("Agora você está no nível 42!", player.translate("You have now reached level 42!"))
    }

    @Test
    fun `Glossary words are translated only where the line asks for them`() {
        val player = createPlayer()
        player["portuguese_dialogue"] = true

        assertEquals("Você retira uma barra de ferro (iron).", player.translate("You retrieve a bar of iron."))
        assertEquals("O talismã está puxando para o nordeste.", player.translate("The talisman is pulling towards the north-east."))
        // "rune" is a metal in the glossary but rune essence here, so this line doesn't use it
        assertEquals(false, player.translate("This pouch contains rune essence, so you can only fill it with more rune essence.").contains("runita"))
    }

    @Test
    fun `The line with the most fixed text wins`() {
        val player = createPlayer()
        player["portuguese_dialogue"] = true

        assertEquals("Você martela a barra de ferro (iron) e faz: iron axe.", player.translate("You hammer the iron and make an iron axe."))
        assertEquals("Você martela a barra de bronze e faz: bronze dagger.", player.translate("You hammer the bronze and make a bronze dagger."))
    }

    @Test
    fun `Level up is announced in Portuguese`() {
        val player = createPlayer()
        player["portuguese_dialogue"] = true
        player.clear("skip_level_up")

        player.experience.add(Skill.Cooking, 100.0)
        tick()

        assertTrue(player.containsMessage("Você subiu um nível em Cooking! Agora você está no nível 2."))
        assertEquals("dialogue_level_up", player.dialogue)
    }

    @Test
    fun `Continue prompt is translated in dialogue boxes`() {
        mockkStatic("world.gregs.voidps.network.login.protocol.encode.InterfaceEncodersKt")
        val (player, client) = createClient("translated", Tile(3095, 3107))
        player["portuguese_dialogue"] = true
        val guide = createNPC("runescape_guide", Tile(3094, 3107))

        player.npcOption(guide, "Talk-to")
        tick(3)

        verify { client.interfaceText(any(), "Clique aqui para continuar") }
    }

    @Test
    fun `Continue prompt is left to the client in English`() {
        mockkStatic("world.gregs.voidps.network.login.protocol.encode.InterfaceEncodersKt")
        val (player, client) = createClient("english", Tile(3095, 3107))
        val guide = createNPC("runescape_guide", Tile(3094, 3107))

        player.npcOption(guide, "Talk-to")
        tick(3)

        verify(exactly = 0) { client.interfaceText(any(), "Click here to continue") }
        verify(exactly = 0) { client.interfaceText(any(), "Clique aqui para continuar") }
    }

    @Test
    fun `Chat messages are translated once Portuguese is enabled`() {
        val player = createPlayer()
        val name = Settings["server.name"]

        player.message("Welcome to $name.")
        assertTrue(player.containsMessage("Welcome to $name."))

        player["portuguese_dialogue"] = true
        player.message("Welcome to $name.")
        assertTrue(player.containsMessage("Bem-vindo a $name."))
    }

    @Test
    fun `Console output is never translated`() {
        val player = createPlayer()
        player["portuguese_dialogue"] = true

        player.message("Welcome to ${Settings["server.name"]}.", ChatType.Console)

        assertFalse(player.containsMessage("Bem-vindo"))
    }

    @Test
    fun `Banker speaks Portuguese`() {
        mockkStatic("world.gregs.voidps.network.login.protocol.encode.InterfaceEncodersKt")
        val (player, client) = createClient("customer", Tile(3120, 3123))
        player["portuguese_dialogue"] = true
        val banker = createNPC("banker_tutorial_island", Tile(3120, 3125))

        player.npcOption(banker, "Talk-to")
        tick(3)

        verify { client.interfaceText(any(), "Bom dia. Como posso ajudar?") }
    }

    @Test
    fun `Task messages translate the task name and keep the count`() {
        val player = createPlayer()
        player["portuguese_dialogue"] = true

        assertEquals(
            "Você completou a tarefa 'Na correria' do conjunto de Lumbridge/Draynor (Iniciante)!",
            player.translate("You have completed the Task 'On the Run' in the Beginner Lumbridge/Draynor set!"),
        )
        assertEquals("Agora você completou 1 tarefa no total.", player.translate("You have now completed 1 Task in total."))
        assertEquals("Agora você completou 2 tarefas no total.", player.translate("You have now completed 2 Tasks in total."))
        assertEquals("Esta rocha não contém minério.", player.translate("This rock contains no ore."))
    }

    @Test
    fun `How many box and progress bar are translated`() {
        val player = createPlayer()
        player["portuguese_dialogue"] = true

        assertEquals("Quantos você quer cozinhar?", player.translate("How many would you like to cook?"))
        assertEquals("19% concluído", player.translate("19% Done"))
    }

    @Test
    fun `Tutorial instructions use the Portuguese row once enabled`() {
        val player = createPlayer()
        val stage = Rows.get("tutorial_island.stage_0")

        assertEquals("Getting started", player.translate(stage).string("title"))
        player["portuguese_dialogue"] = true
        assertEquals("Primeiros passos", player.translate(stage).string("title"))
        assertEquals("npc_ranges.hans", player.translate(Rows.get("npc_ranges.hans")).stringId)
    }

    @Test
    fun `Guide still advances the tutorial in Portuguese`() {
        val player = createPlayer(Tile(3095, 3107)) { it.startTutorialInPortuguese(0) }
        val guide = createNPC("runescape_guide", Tile(3094, 3107))

        player.npcOption(guide, "Talk-to")
        tick(3)
        player.skipDialogues()

        assertEquals(1, player.tutorialStage)
    }

    @Test
    fun `Survival expert still hands over the kit in Portuguese`() {
        val player = createPlayer(Tile(3104, 3095)) { it.startTutorialInPortuguese(4) }
        val expert = createNPC("survival_expert", Tile(3103, 3095))

        player.npcOption(expert, "Talk-to")
        tick(3)
        player.skipDialogues()

        assertEquals(5, player.tutorialStage)
        assertTrue(player.inventory.contains("bronze_hatchet"))
        assertTrue(player.inventory.contains("tinderbox"))
    }

    @Test
    fun `Leaving the island still works in Portuguese`() {
        settings.setProperty("world.start.tutorial.skippable", "true")
        val player = createPlayer(Tile(3094, 3106)) { it.startTutorialInPortuguese(0) }
        val guide = createNPC("runescape_guide", Tile(3094, 3107))

        player.npcOption(guide, "Talk-to")
        tick(1)
        player.skipDialogues()
        player.dialogueOption(2) // Skip
        player.skipDialogues()
        tick(1)

        assertFalse(player.inTutorial)
    }

    @Test
    fun `Every translated stage replaces an existing one`() {
        for (row in Tables.get("tutorial_island_pt_br").rows()) {
            assertNotNull(Rows.getOrNull("tutorial_island.${row.rowId}"), "No English stage for ${row.stringId}")
        }
    }

    @Test
    fun `Translated instructions fit in the box`() {
        val font = get<FontDefinitions>().get("q8_full")
        val english = Tables.get("tutorial_island").rows()
        val maxLine = english.flatMap { it.stringList("lines") }.maxOf { font.textWidth(it) }
        val maxTitle = english.maxOf { font.textWidth(it.string("title")) }
        val tooWide = mutableListOf<String>()
        for (row in Tables.get("tutorial_island_pt_br").rows()) {
            val lines = row.stringList("lines")
            assertTrue(lines.size <= 6, "${row.stringId} has ${lines.size} lines")
            tooWide += lines.filter { font.textWidth(it) > maxLine }
            if (font.textWidth(row.string("title")) > maxTitle) {
                tooWide += row.string("title")
            }
        }
        assertTrue(tooWide.isEmpty(), "Too wide (max $maxLine px):\n${tooWide.joinToString("\n") { "${font.textWidth(it)}px $it" }}")
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

    private fun Player.startTutorialInPortuguese(stage: Int) {
        set("tutorial_stage", stage)
        set("tutorial_designed", true)
        set("portuguese_dialogue", true)
    }
}
