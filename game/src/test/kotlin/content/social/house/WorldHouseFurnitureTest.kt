package content.social.house

import WorldTest
import containsMessage
import dialogueOption
import interfaceOption
import kotlinx.coroutines.test.runTest
import objectOption
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.client.command.Commands
import world.gregs.voidps.engine.client.ui.dialogue
import world.gregs.voidps.engine.client.ui.hasOpen
import world.gregs.voidps.engine.data.Settings
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.entity.item.Item
import world.gregs.voidps.engine.entity.obj.GameObjects
import world.gregs.voidps.engine.entity.obj.ObjectLayer
import world.gregs.voidps.engine.get
import world.gregs.voidps.engine.inv.add
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.type.Tile
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorldHouseFurnitureTest : WorldTest() {

    private val house: WorldHouse
        get() = WorldHouses.get("draynor_north")!!

    private val chair = Tile(3101, 3279)
    private val drawers = Tile(3097, 3277)
    private val fireplace = Tile(3101, 3281)
    private val inside = Tile(3100, 3278)

    @BeforeEach
    fun setup() {
        // A house file left by an earlier run is loaded with the world
        GameObjects.reset()
        WorldHouses.clear()
        house.owner = "owner"
        house.ownerName = "owner"
        house.guests.add("guest")
    }

    @AfterEach
    fun teardown() {
        WorldHouses.clear()
    }

    private fun piece(tile: Tile) = GameObjects.getLayer(tile, ObjectLayer.GROUND)!!

    private fun Player.build(tile: Tile, furniture: String) {
        objectOption(piece(tile), "Build")
        tickIf { !hasOpen("furniture_creation") }
        val slot = inventories.inventory("poh_furniture_menu_inv").indexOf(furniture)
        interfaceOption("furniture_creation", "items", "Build", item = Item(furniture), slot = slot)
        tick()
    }

    private fun builder(): Player {
        val owner = createPlayer(inside, "owner")
        owner.inventory.add("hammer")
        owner.inventory.add("saw")
        owner.inventory.add("plank", 2)
        owner.inventory.add("bronze_nails", 2)
        return owner
    }

    @Test
    fun `Only the owner can build in place of the furniture`() {
        val owner = createPlayer(inside, "owner")
        val guest = createPlayer(Tile(3099, 3278), "guest")

        assertTrue(piece(chair).def(owner).options!!.contains("Build"))
        assertFalse(piece(chair).def(guest).options?.contains("Build") ?: false)
    }

    @Test
    fun `Owner builds a Construction chair in place of a chair`() {
        val owner = builder()

        owner.build(chair, "crude_wooden_chair")

        assertEquals("crude_wooden_chair", piece(chair).id)
        assertEquals(0, owner.inventory.count("plank"))
        assertEquals(0, owner.inventory.count("bronze_nails"))
        assertEquals(58.0, owner.experience.get(Skill.Construction))
        assertEquals("crude_wooden_chair", house.furniture[chair])
    }

    @Test
    fun `Owner removes the nearest furniture from the menu`() {
        val owner = builder()
        owner.build(chair, "crude_wooden_chair")

        runTest { Commands.call(owner, "casa") }
        tick()
        owner.dialogueOption("line4")
        tick()
        owner.dialogueOption("line1")
        tick()

        assertEquals("world_house_draynor_north_chair", piece(chair).id)
        assertFalse(chair in house.furniture)
    }

    @Test
    fun `Built furniture faces the same way as the piece it replaced`() {
        Settings.load(mapOf("construction.freeBuild" to "true"))
        val owner = createPlayer(inside, "owner")
        owner.build(drawers, "oak_drawers")
        assertEquals("oak_drawers", piece(drawers).id)

        owner.objectOption(piece(drawers), "Remove")
        tick(5)

        assertNotNull(owner.dialogue)
    }

    @Test
    fun `Removing built furniture brings the house's piece back`() {
        Settings.load(mapOf("construction.freeBuild" to "true"))
        val owner = createPlayer(inside, "owner")
        owner.build(drawers, "oak_drawers")

        owner.objectOption(piece(drawers), "Remove")
        tickIf { owner.dialogue == null }
        owner.dialogueOption("line1")
        tick()

        assertEquals("world_house_draynor_north_drawers", piece(drawers).id)
        assertFalse(drawers in house.furniture)
    }

    @Test
    fun `Guests can't remove the furniture`() {
        Settings.load(mapOf("construction.freeBuild" to "true"))
        val owner = createPlayer(inside, "owner")
        owner.build(drawers, "oak_drawers")
        val guest = createPlayer(inside, "guest")

        guest.objectOption(piece(drawers), "Remove")
        tick(5)

        assertEquals("oak_drawers", piece(drawers).id)
        assertTrue(guest.containsMessage("Only the owner can change the furniture."))
    }

    @Test
    fun `Fire can be put out and lit again`() {
        val guest = createPlayer(Tile(3100, 3280), "guest")

        guest.objectOption(piece(fireplace), "Extinguish")
        tick(3)
        assertEquals("world_house_draynor_north_fireplace_unlit", piece(fireplace).id)
        assertTrue(fireplace in house.unlit)

        guest.objectOption(piece(fireplace), "Light")
        tick(3)
        assertEquals("world_house_draynor_north_fireplace", piece(fireplace).id)
        assertFalse(fireplace in house.unlit)
    }

    @Test
    fun `Furniture built and fires put out come back after a restart`() {
        val owner = builder()
        owner.build(chair, "crude_wooden_chair")
        owner.objectOption(piece(fireplace), "Extinguish")
        tick(3)

        WorldHouses.save(house)
        GameObjects.reset()
        WorldHouses.clear()
        WorldHouses.load(get())

        assertEquals("crude_wooden_chair", piece(chair).id)
        assertEquals("world_house_draynor_north_fireplace_unlit", piece(fireplace).id)
    }

    @Test
    fun `Giving up the house puts the map's furniture back`() {
        val owner = builder()
        owner.build(chair, "crude_wooden_chair")

        WorldHouses.resetFurniture(house)

        assertEquals("world_house_draynor_north_chair", piece(chair).id)
        assertTrue(house.furniture.isEmpty())
    }
}
