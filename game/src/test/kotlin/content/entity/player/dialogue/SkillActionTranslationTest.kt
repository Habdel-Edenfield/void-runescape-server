package content.entity.player.dialogue

import FakeRandom
import WorldTest
import content.skill.summoning.follower
import dialogueOption
import interfaceOption
import itemOnItem
import itemOnObject
import itemOption
import npcOption
import objectOption
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import skillCreation
import world.gregs.voidps.engine.client.ui.dialogue
import world.gregs.voidps.engine.data.definition.NPCDefinitions
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.combatLevel
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.entity.character.player.skill.level.Level
import world.gregs.voidps.engine.entity.obj.GameObjects
import world.gregs.voidps.engine.inv.add
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.type.Tile
import world.gregs.voidps.type.setRandom
import kotlin.random.Random

/**
 * Plays the most common action of each skill with Portuguese enabled and fails if any text shown
 * is still in English. [SkillTranslationTest] reads the code; this catches text built while playing,
 * such as messages from data tables, item definitions and other scripts the skills call.
 */
class SkillActionTranslationTest : WorldTest() {

    private val untranslated = linkedSetOf<String>()
    private lateinit var original: (String) -> Unit

    @BeforeEach
    fun record() {
        original = missingTranslation
        missingTranslation = { untranslated.add(it) }
    }

    @AfterEach
    fun restore() {
        missingTranslation = original
        untranslated.clear()
    }

    private fun portuguese(tile: Tile = emptyTile): Player {
        val player = createPlayer(tile)
        player["portuguese_dialogue"] = true
        return player
    }

    private fun assertTranslated() {
        assertTrue(untranslated.isEmpty(), "Untranslated text:\n${untranslated.joinToString("\n")}")
    }

    @Test
    fun `Eating and drinking are in Portuguese`() {
        val player = portuguese()
        player.levels.set(Skill.Constitution, 10)
        player.inventory.add("shrimps")
        player.inventory.add("cabbage")
        player.inventory.add("bread")
        player.inventory.add("attack_potion_4")

        for (food in listOf("shrimps", "cabbage", "bread")) {
            player.itemOption("Eat", food)
            tick(3)
        }
        player.itemOption("Drink", "attack_potion_4")
        tick(3)

        assertTrue(player.inventory.contains("attack_potion_3"))
        assertTranslated()
    }

    @Test
    fun `Burying bones is in Portuguese`() {
        val player = portuguese()
        player.inventory.add("bones")

        player.itemOption("Bury", "bones")
        tick(3)

        assertTrue(player.experience.get(Skill.Prayer) > 0)
        assertTranslated()
    }

    @Test
    fun `Reaching a new combat level is in Portuguese`() {
        val player = portuguese()
        player.clear("skip_level_up")

        player.experience.set(Skill.Attack, Level.experience(Skill.Attack, 20))
        tick()

        assertTrue(player.combatLevel > 3)
        assertTranslated()
    }

    @Test
    fun `Woodcutting is in Portuguese`() {
        val player = portuguese()
        player.levels.set(Skill.Woodcutting, 99)
        player.inventory.add("bronze_hatchet")
        val tree = createObject("oak", emptyTile.addY(1))

        player.objectOption(tree, "Chop down")
        tickIf { !player.inventory.contains("oak_logs") }

        assertTranslated()
    }

    @Test
    fun `Firemaking is in Portuguese`() {
        val player = portuguese()
        player.levels.set(Skill.Firemaking, 99)
        player.inventory.add("tinderbox")
        player.inventory.add("oak_logs")

        player.itemOnItem(0, 1)
        tick(10)

        assertTrue(player.experience.get(Skill.Firemaking) > 0)
        assertTranslated()
    }

    @Test
    fun `Fishing is in Portuguese`() {
        setRandom(object : FakeRandom() {
            override fun nextInt(until: Int): Int = 0
        })
        val player = portuguese()
        player.levels.set(Skill.Fishing, 20)
        player.inventory.add("fly_fishing_rod")
        player.inventory.add("feather", 100)
        val spot = createNPC("fishing_spot_lure_bait_lumbridge", emptyTile.addY(1))

        player.npcOption(spot, "Lure")
        tickIf { !player.inventory.contains("raw_trout") }

        assertTranslated()
    }

    @Test
    fun `Cooking is in Portuguese`() {
        val player = portuguese()
        player.levels.set(Skill.Cooking, 99)
        player.inventory.add("raw_trout", 2)
        val fire = createObject("fire_orange", emptyTile.addY(1))

        player.itemOnObject(fire, 0)
        tick()
        player.interfaceOption("skill_creation_amount", "all", "All")
        player.dialogueOption(id = "dialogue_skill_creation", component = "choice1")
        tick(10)

        assertTrue(player.inventory.contains("trout"))
        assertTranslated()
    }

    @Test
    fun `Mining is in Portuguese`() {
        setRandom(Random)
        val player = portuguese()
        player.levels.set(Skill.Mining, 99)
        player.inventory.add("bronze_pickaxe")
        val rocks = createObject("tin_rocks_rock_1", emptyTile.addY(1))

        player.objectOption(rocks, "Prospect")
        tick(6)
        player.objectOption(rocks, "Mine")
        tickIf { !player.inventory.contains("tin_ore") }

        assertTranslated()
    }

    @Test
    fun `Smelting and smithing are in Portuguese`() {
        val player = portuguese(Tile(3227, 3255))
        player.levels.set(Skill.Smithing, 99)
        player.inventory.add("iron_ore")
        val furnace = GameObjects.find(Tile(3226, 3256), "furnace_lumbridge")

        player.objectOption(furnace, "Smelt")
        tick()
        player.skillCreation("Iron bar", 1)
        tick(4)

        assertTranslated()
    }

    @Test
    fun `Crafting is in Portuguese`() {
        val player = portuguese(Tile(3082, 3430))
        player.inventory.add("needle")
        player.inventory.add("thread")
        player.inventory.add("leather")
        player.inventory.add("chisel")
        player.inventory.add("uncut_sapphire")
        player.levels.set(Skill.Crafting, 99)

        player.itemOnItem(0, 2)
        tick()
        player.dialogueOption(id = "dialogue_skill_creation", component = "choice1")
        tick(3)
        player.itemOnItem(player.inventory.indexOf("chisel"), player.inventory.indexOf("uncut_sapphire"))
        tick()
        player.dialogueOption(id = "dialogue_skill_creation", component = "choice1")
        tick(3)

        assertTrue(player.inventory.contains("leather_gloves"))
        assertTrue(player.inventory.contains("sapphire"))
        assertTranslated()
    }

    @Test
    fun `Fletching is in Portuguese`() {
        val player = portuguese()
        player.levels.set(Skill.Fletching, 99)
        player.inventory.add("knife")
        player.inventory.add("logs")

        player.itemOnItem(0, 1)
        tick()
        player.dialogueOption(id = "dialogue_skill_creation", component = "choice1")
        tick(3)

        assertTrue(player.inventory.contains("arrow_shaft"))
        assertTranslated()
    }

    @Test
    fun `Herblore is in Portuguese`() {
        val player = portuguese()
        player.levels.set(Skill.Herblore, 99)
        player.inventory.add("grimy_guam")
        player.inventory.add("vial_of_water")
        player.inventory.add("eye_of_newt")

        player.itemOption("Clean", "grimy_guam")
        tick(2)
        player.itemOnItem(player.inventory.indexOf("clean_guam"), player.inventory.indexOf("vial_of_water"))
        tick()
        player.dialogueOption(id = "dialogue_skill_creation", component = "choice1")
        tick(3)
        player.itemOnItem(player.inventory.indexOf("eye_of_newt"), player.inventory.indexOf("guam_potion_unf"))
        tick()
        player.dialogueOption(id = "dialogue_skill_creation", component = "choice1")
        tick(3)

        assertTrue(player.inventory.contains("attack_potion_3"))
        assertTranslated()
    }

    @Test
    fun `Runecrafting is in Portuguese`() {
        val altarTile = Tile(2843, 4833)
        val player = portuguese(altarTile.addY(-1))
        player.levels.set(Skill.Runecrafting, 99)
        player.inventory.add("rune_essence")

        player.objectOption(GameObjects.find(altarTile, "air_altar"), "Craft-rune")
        tick()
        tickIf { player.visuals.moved }

        assertTrue(player.inventory.contains("air_rune"))
        assertTranslated()
    }

    @Test
    fun `Thieving is in Portuguese`() {
        setRandom(object : FakeRandom() {
            override fun nextInt(until: Int) = 0
        })
        val player = portuguese()
        val man = createNPC("man", emptyTile.addY(1))

        player.npcOption(man, "Pickpocket")
        tick(4)

        assertTrue(player.inventory.contains("coins"))
        assertTranslated()
    }

    @Test
    fun `Agility is in Portuguese`() {
        val player = portuguese(Tile(2474, 3437))
        val log = GameObjects.find(Tile(2474, 3435), "gnome_log_balance")

        player.objectOption(log, "Walk-across")
        tick(10)

        assertTrue(player.experience.get(Skill.Agility) > 0)
        assertTranslated()
    }

    @Test
    fun `Magic is in Portuguese`() {
        val player = portuguese()
        player.levels.set(Skill.Magic, 99)
        val tile = player.tile

        player.interfaceOption("modern_spellbook", "varrock_teleport", "Cast")
        tick(2)
        player.inventory.add("law_rune")
        player.inventory.add("air_rune", 3)
        player.inventory.add("fire_rune")
        player.interfaceOption("modern_spellbook", "varrock_teleport", "Cast")
        tickIf { player.tile == tile }

        assertTranslated()
    }

    @Test
    fun `Farming is in Portuguese`() {
        setRandom(object : FakeRandom() {
            override fun nextInt(until: Int) = 0
        })
        val tile = Tile(3054, 3310)
        val player = portuguese(tile)
        player.levels.set(Skill.Farming, 99)
        player.inventory.add("rake")
        player.inventory.add("seed_dibber")
        player.inventory.add("potato_seed", 3)
        val patch = GameObjects.find(tile.addY(1), "farming_veg_patch_falador_nw")

        player.objectOption(patch, "Rake")
        tick(12)
        player.itemOnObject(patch, player.inventory.indexOf("potato_seed"))
        tick(10)

        assertTrue(player.inventory.contains("weeds"))
        assertTrue(player["farming_veg_patch_falador_nw", "empty"].startsWith("potato"))
        assertTranslated()
    }

    @Test
    fun `Hunter is in Portuguese`() {
        val player = portuguese()
        player.inventory.add("bird_snare")

        player.itemOption("Lay", "bird_snare")
        tick(3)

        assertTrue(GameObjects.at(emptyTile).any { it.id == "bird_snare" })
        assertTranslated()
    }

    @Test
    fun `Slayer is in Portuguese`() {
        val player = portuguese(Tile(3511, 3510))
        for (skill in Skill.entries) {
            player.levels.set(skill, skill.maximum())
            player.experience.set(skill, Level.experience(skill, skill.maximum()))
        }
        val master = createNPC("mazchna_canifis", Tile(3511, 3509))

        player.npcOption(master, "Get-task")
        tick(2)
        player.talkThrough()
        player.npcOption(master, "Talk-to")
        tick(2)
        player.talkThrough()

        assertTranslated()
    }

    @Test
    fun `Construction is in Portuguese`() {
        val player = portuguese()

        player.interfaceOption("options", "house", "Open House Options")
        tick()

        assertTranslated()
    }

    @Test
    fun `Summoning is in Portuguese`() {
        val player = portuguese()
        player.experience.set(Skill.Summoning, Level.experience(Skill.Summoning, 99))
        player.levels.set(Skill.Summoning, 99)
        player.inventory.add("spirit_wolf_pouch")

        player.itemOption("Summon", "spirit_wolf_pouch")
        tick(3)

        assertNotNull(player.follower)
        assertTranslated()
    }

    @Test
    fun `Every familiar talks in Portuguese`() {
        setRandom(Random(0))
        val familiars = NPCDefinitions.ids.keys.filter { it.endsWith("_familiar") && NPCDefinitions.get(it).options.contains("Interact") }.sorted()
        assertTrue(familiars.size > 50, "Expected familiars, found ${familiars.size}")
        val failures = mutableListOf<String>()
        for (id in familiars) {
            val player = portuguese()
            val familiar = createNPC(id, player.tile.addY(1))
            player.follower = familiar
            // Each conversation is picked at random, so talk a few times to see more of them
            repeat(6) {
                try {
                    player.npcOption(familiar, "Interact")
                    tick(2)
                    player.talkThrough()
                } catch (e: Exception) {
                    failures.add("$id: ${e.message}")
                }
            }
        }

        assertTrue(failures.isEmpty(), "Conversations that failed:\n${failures.joinToString("\n")}")
        assertTranslated()
    }

    /** Continues every box and picks the first option in every choice until the conversation ends */
    private fun Player.talkThrough() {
        repeat(30) {
            val id = dialogue ?: return
            if (id.startsWith("dialogue_multi")) {
                dialogueOption(1)
            } else {
                dialogueOption("continue")
            }
            tick()
        }
    }
}
