package content.area.misthalin.tutorial_island

import WorldTest
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.client.instruction.handle.interactOn
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.inv.add
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.type.Tile

class ZzChickenRepro : WorldTest() {
    @Test
    fun repro() = kotlinx.coroutines.test.runTest {
        for (start in listOf(Tile(3141, 3089), Tile(3139, 3091), Tile(3141, 3091), Tile(3137, 3091), Tile(3143, 3090))) {
            val player = createPlayer(start) { it.startTutorial(66) }
            player.inventory.add("air_rune", 5)
            player.inventory.add("mind_rune", 5)
            val npc = createNPC("chicken_tutorial_island", Tile(3138, 3094))
            println("START $start chicken=${npc.tile}")
            player.interactOn(npc, "modern_spellbook", "wind_strike")
            for (i in 0 until 15) {
                tick(1)
                print("${player.tile.x},${player.tile.y} ")
            }
            println()
            println("RESULT $start chickenNow=${npc.tile} hp=${npc.levels.get(Skill.Constitution)} xp=${player.experience.get(Skill.Magic)} stage=${player.tutorialStage} runes=${player.inventory.count("air_rune")}")
        }
    }

    private fun world.gregs.voidps.engine.entity.character.player.Player.startTutorial(stage: Int) {
        set("tutorial_stage", stage)
        set("tutorial_designed", true)
    }
}
