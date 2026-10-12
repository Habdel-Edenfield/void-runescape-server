package content.entity.player.dialogue

import WorldTest
import dialogueOption
import interfaceOption
import itemOnItem
import itemOnObject
import npcOption
import objectOption
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.client.messageTranslator
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.inv.add
import world.gregs.voidps.engine.inv.equipment
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.network.login.protocol.visual.update.player.EquipSlot
import world.gregs.voidps.type.Tile

/**
 * Plays each Tutorial Island activity with Portuguese enabled and fails if any chat message is
 * left in English, so text added to these activities later isn't silently missed.
 */
class TutorialIslandTranslationTest : WorldTest() {

    private val untranslated = linkedSetOf<String>()
    private lateinit var original: (Player, String) -> String

    @BeforeEach
    fun record() {
        original = messageTranslator
        messageTranslator = { player, text ->
            val translated = original(player, text)
            if (translated == text && player["portuguese_dialogue", false]) {
                untranslated.add(text)
            }
            translated
        }
    }

    @AfterEach
    fun restore() {
        messageTranslator = original
        untranslated.clear()
    }

    private fun islander(stage: Int, tile: Tile = emptyTile): Player {
        val player = createPlayer(tile)
        player["tutorial_stage"] = stage
        player["tutorial_designed"] = true
        player["portuguese_dialogue"] = true
        return player
    }

    private fun assertTranslated() {
        assertTrue(untranslated.isEmpty(), "Untranslated chat messages:\n${untranslated.joinToString("\n")}")
    }

    @Test
    fun `Woodcutting is in Portuguese`() {
        val player = islander(6)
        val tree = createObject("tree", emptyTile.addY(1))
        player.objectOption(tree, "Chop down")
        tick(2)
        player.levels.set(Skill.Woodcutting, 99)
        player.inventory.add("bronze_hatchet")
        player.objectOption(tree, "Chop down")
        tickIf { !player.inventory.contains("logs") }

        assertTranslated()
    }

    @Test
    fun `Firemaking is in Portuguese`() {
        val player = islander(7)
        player.levels.set(Skill.Firemaking, 99)
        player.inventory.add("tinderbox")
        player.inventory.add("logs")
        player.itemOnItem(0, 1)
        tick(10)

        assertTranslated()
    }

    @Test
    fun `Fishing is in Portuguese`() {
        val player = islander(10)
        val spot = createNPC("fishing_spot_tutorial_island", emptyTile.addY(1))
        player.npcOption(spot, "Net")
        tick(2)
        player.levels.set(Skill.Fishing, 99)
        player.inventory.add("small_fishing_net")
        player.npcOption(spot, "Net")
        tickIf { !player.inventory.contains("raw_shrimps") }

        assertTranslated()
    }

    @Test
    fun `Cooking is in Portuguese`() {
        val player = islander(11)
        player.inventory.add("raw_shrimps", 2)
        val fire = createObject("fire_orange", emptyTile.addY(1))
        player.itemOnObject(fire, 0)
        tick()
        player.interfaceOption("skill_creation_amount", "all", "All")
        player.dialogueOption(id = "dialogue_skill_creation", component = "choice1")
        tick(10)

        assertTranslated()
    }

    @Test
    fun `Bread making is in Portuguese`() {
        val player = islander(16)
        player.levels.set(Skill.Cooking, 99)
        player.inventory.add("pot_of_flour")
        player.inventory.add("bucket_of_water")
        player.itemOnItem(1, 0)
        tick(3)
        player.dialogueOption(id = "dialogue_skill_creation", component = "choice1")
        tick(3)
        val range = createObject("cooking_range_tutorial_island", emptyTile.addY(1))
        player.itemOnObject(range, player.inventory.indexOf("bread_dough"))
        tick()
        player.dialogueOption(id = "dialogue_skill_creation", component = "choice1")
        tick(6)

        assertTranslated()
    }

    @Test
    fun `Mining is in Portuguese`() {
        val player = islander(29)
        val tin = createObject("tin_rocks_tutorial_island_1", emptyTile.addY(1))
        player.objectOption(tin, "Prospect")
        tick(8)
        player.objectOption(tin, "Mine")
        tick(3)
        player.levels.set(Skill.Mining, 99)
        player.inventory.add("bronze_pickaxe")
        player.objectOption(tin, "Mine")
        tick(20)

        assertTranslated()
    }

    @Test
    fun `Smelting is in Portuguese`() {
        val player = islander(34, Tile(3227, 3255))
        player.inventory.add("tin_ore")
        player.inventory.add("copper_ore")
        val furnace = createObject("furnace_tutorial_island", Tile(3226, 3256))
        player.itemOnObject(furnace, 0)
        tick(2)
        player.dialogueOption(id = "dialogue_skill_creation", component = "choice1")
        tick(6)

        assertTranslated()
    }

    @Test
    fun `Smithing is in Portuguese`() {
        val player = islander(36, Tile(3187, 3426))
        player.inventory.add("hammer")
        player.inventory.add("bronze_bar")
        val anvil = createObject("anvil", Tile(3188, 3426))
        player.itemOnObject(anvil, 1)
        tick()
        player.interfaceOption("smithing", "dagger_1", "Make 1 Dagger", optionIndex = 0)
        tick(5)

        assertTranslated()
    }

    @Test
    fun `Combat is in Portuguese`() {
        val player = islander(50)
        player.equipment.set(EquipSlot.Weapon.index, "bronze_sword")
        val rat = createNPC("giant_rat_tutorial_island", emptyTile.addY(1))
        player.npcOption(rat, "Attack")
        tick(10)
        player.equipment.set(EquipSlot.Weapon.index, "shortbow")
        player.npcOption(rat, "Attack")
        tick(4)

        assertTranslated()
    }

    @Test
    fun `Locked tutorial doors are in Portuguese`() {
        val player = islander(0, Tile(3097, 3107))
        val door = createObject("door_87_closed", Tile(3098, 3107))
        player.objectOption(door, "Open")
        tick(3)

        assertTranslated()
    }
}
