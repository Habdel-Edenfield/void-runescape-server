package content.entity.player.dialogue

import WorldTest
import dialogueOption
import npcOption
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.client.ui.dialogue
import world.gregs.voidps.engine.entity.character.npc.NPCs
import world.gregs.voidps.engine.entity.character.player.Player
import java.io.File

/**
 * Talks to every npc spawned by Varrock's spawn files (data/area/misthalin/varrock, museum, palace, Grand Exchange,
 * dig site...) with Portuguese enabled, once for each option position so more branches are seen, and fails if any
 * text shown is still in English or a conversation throws.
 */
class VarrockTranslationTest : WorldTest() {

    override var loadNpcs: Boolean = true

    @Test
    fun `Varrock npcs speak Portuguese`() {
        val untranslated = linkedSetOf<String>()
        val original = missingTranslation
        missingTranslation = { untranslated.add(it) }
        val failures = mutableListOf<String>()
        try {
            // Spawns are queued until the first tick
            tick()
            val ids = spawnIds()
            val npcs = NPCs.filter { it.id in ids && it.def.options.contains("Talk-to") }.distinctBy { it.id }
            assertTrue(npcs.size > 40, "Expected Varrock npcs to be spawned, found ${npcs.size}")
            for (option in 1..5) {
                for (npc in npcs) {
                    val player = createPlayer(npc.tile)
                    player["portuguese_dialogue"] = true
                    try {
                        player.npcOption(npc, "Talk-to")
                        tick(2)
                        player.talkThrough(option)
                    } catch (e: Exception) {
                        failures.add("${npc.id} (option $option): ${e.message}")
                    }
                }
            }
        } finally {
            missingTranslation = original
        }

        assertTrue(failures.isEmpty(), "Conversations that failed:\n${failures.joinToString("\n")}")
        assertTrue(untranslated.isEmpty(), "Untranslated text:\n${untranslated.joinToString("\n")}")
    }

    /** The npc ids in every spawn file under Varrock's data folder */
    private fun spawnIds(): Set<String> {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { it.resolve("data/area").isDirectory }
        return root.resolve("data/area/misthalin/varrock").walk()
            .filter { it.name.endsWith(".npc-spawns.toml") }
            .flatMap { file -> Regex("""id = "([a-z0-9_]+)"""").findAll(file.readText()).map { it.groupValues[1] } }
            .toSet()
    }

    /** Continues every box and picks [option] (or the last one) in every choice until the conversation ends */
    private fun Player.talkThrough(option: Int) {
        repeat(60) {
            val id = dialogue ?: return
            if (id.startsWith("dialogue_multi")) {
                val options = id.filter { it.isDigit() }.toInt()
                dialogueOption(option.coerceAtMost(options))
            } else {
                dialogueOption("continue")
            }
            tick()
        }
    }
}
