package content.social.house

import WorldTest
import containsMessage
import dialogueOption
import floorItemOption
import itemOption
import kotlinx.coroutines.test.runTest
import objectOption
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.client.command.Commands
import world.gregs.voidps.engine.client.ui.dialogue
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.item.floor.FloorItems
import world.gregs.voidps.engine.entity.obj.GameObjects
import world.gregs.voidps.engine.entity.obj.replace
import world.gregs.voidps.engine.get
import world.gregs.voidps.engine.inv.add
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.engine.suspend.Suspension
import world.gregs.voidps.type.Tile
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldHouseTest : WorldTest() {

    private val house: WorldHouse
        get() = WorldHouses.get("draynor_north")!!

    private val door = Tile(3100, 3276)
    private val outside = Tile(3100, 3275)
    private val inside = Tile(3100, 3278)

    @BeforeEach
    fun setup() {
        // A house file left by an earlier run is loaded with the world
        GameObjects.reset()
        WorldHouses.clear()
    }

    @AfterEach
    fun teardown() {
        WorldHouses.clear()
    }

    private fun houseDoor() = GameObjects.find(door, house.ownDoor)

    /**
     * Left click on the door: "Buy" while it's for sale, "Open" otherwise
     */
    private fun Player.openDoor() {
        val target = houseDoor()
        objectOption(target, target.def(this).options!![0]!!)
        tick(5)
    }

    private fun Player.houseMenu() {
        runTest { Commands.call(this@houseMenu, "casa") }
        tick()
    }

    private fun own(name: String) {
        house.owner = name
        house.ownerName = name
        WorldHouses.showDoors(house)
    }

    @Test
    fun `Buying the house at the door takes the coins and lets the buyer in`() {
        val player = createPlayer(outside)
        player.inventory.add("coins", 150)

        player.openDoor()
        player.dialogueOption("line1")
        tick(5)

        assertEquals(player.accountName, house.owner)
        assertEquals(50, player.inventory.count("coins"))
        assertTrue(player.tile in house.area)
        assertEquals(listOf("Open", "Manage"), houseDoor().def(player).options!!.filterNotNull().dropLast(1))
    }

    @Test
    fun `Can't buy the house without enough coins`() {
        val player = createPlayer(outside)
        player.inventory.add("coins", 99)

        player.openDoor()
        player.dialogueOption("line1")
        tick(5)

        assertNull(house.owner)
        assertEquals(99, player.inventory.count("coins"))
        assertFalse(player.tile in house.area)
    }

    @Test
    fun `Door stays locked for anyone not invited`() {
        own("owner")
        val owner = createPlayer(inside, "owner")
        val stranger = createPlayer(outside, "stranger")

        stranger.openDoor()

        assertFalse(stranger.tile in house.area)
        assertTrue(stranger.containsMessage("The door is locked. Willow Cottage is the house of owner."))
    }

    @Test
    fun `Only the owner sees the owner's options on the door`() {
        assertEquals(listOf("Buy"), houseDoor().def(createPlayer(outside, "buyer")).options!!.filterNotNull().dropLast(1))
        own("owner")
        val owner = createPlayer(inside, "owner")
        val stranger = createPlayer(Tile(3101, 3275), "stranger")

        assertEquals(listOf("Open", "Manage"), houseDoor().def(owner).options!!.filterNotNull().dropLast(1))
        assertEquals(listOf("Open", "Knock-at"), houseDoor().def(stranger).options!!.filterNotNull().dropLast(1))
        assertEquals("Door", houseDoor().def(stranger).name)
    }

    @Test
    fun `House for sale is named on its door`() {
        val buyer = createPlayer(outside, "buyer")

        assertEquals("Willow Cottage", houseDoor().def(buyer).name)
    }

    @Test
    fun `Door opens and closes like any other door`() {
        own("owner")
        val owner = createPlayer(outside, "owner")

        owner.openDoor()
        assertTrue(owner.tile in house.area)
        assertNull(GameObjects.findOrNull(door, "door_14_closed"))
        tick(10)

        assertEquals(house.ownDoor, GameObjects.at(door).single().id)
    }

    @Test
    fun `Owner invites a guest from the door's right click`() {
        own("owner")
        val owner = createPlayer(inside, "owner")
        createPlayer(Tile(3110, 3270), "guest")

        owner.objectOption(houseDoor(), "Manage")
        tick(2)
        owner.dialogueOption("line1")
        tick()
        (owner.suspension as Suspension.NameEntry).resume("guest")
        tick()

        assertTrue("guest" in house.guests)
    }

    @Test
    fun `Knocking at the door tells the owner`() {
        own("owner")
        val owner = createPlayer(inside, "owner")
        val stranger = createPlayer(outside, "stranger")

        stranger.objectOption(houseDoor(), "Knock-at")
        tick(3)

        assertTrue(owner.containsMessage("stranger is knocking at the door of Willow Cottage."))
    }

    @Test
    fun `Examining the door says whose house it is or how to buy it`() {
        val buyer = createPlayer(outside, "buyer")
        assertTrue(WorldHouses.examine(buyer, house.ownDoor))
        assertTrue(buyer.containsMessage("Willow Cottage is for sale for 100 coins. Click the door to buy it."))

        own("owner")
        val owner = createPlayer(inside, "owner")
        WorldHouses.examine(buyer, house.ownDoor)
        WorldHouses.examine(owner, house.ownDoor)

        assertTrue(buyer.containsMessage("Willow Cottage is the house of owner."))
        assertTrue(owner.containsMessage("Willow Cottage is your house. Right-click the door to invite people."))
    }

    @Test
    fun `Map door keeps the house locked without the house's own door`() {
        own("owner")
        houseDoor().replace("door_14_closed")
        val stranger = createPlayer(outside, "stranger")

        stranger.objectOption(GameObjects.find(door, "door_14_closed"), "Open")
        tick(5)

        assertFalse(stranger.tile in house.area)
        assertTrue(stranger.containsMessage("The door is locked. Willow Cottage is the house of owner."))
    }

    @Test
    fun `Owner invites a guest who can then open the door`() {
        own("owner")
        val owner = createPlayer(inside, "owner")
        val guest = createPlayer(outside, "guest")

        owner.houseMenu()
        owner.dialogueOption("line1")
        tick()
        (owner.suspension as Suspension.NameEntry).resume("guest")
        tick()
        guest.openDoor()

        assertTrue("guest" in house.guests)
        assertTrue(guest.tile in house.area)
    }

    @Test
    fun `Removing a guest sends them out of the house`() {
        own("owner")
        val owner = createPlayer(Tile(3099, 3280), "owner")
        house.guests.add("guest")
        val guest = createPlayer(inside, "guest")

        owner.houseMenu()
        owner.dialogueOption("line2")
        tick()
        (owner.suspension as Suspension.NameEntry).resume("guest")
        tick()

        assertFalse("guest" in house.guests)
        assertEquals(house.entrance, guest.tile)
    }

    @Test
    fun `Owner leaves straight through the door`() {
        own("owner")
        val owner = createPlayer(inside, "owner")

        owner.objectOption(houseDoor(), "Open")
        tick(5)

        assertNull(owner.dialogue)
        assertFalse(owner.tile in house.area)
    }

    @Test
    fun `Logging in inside a house you aren't allowed in puts you outside`() {
        own("owner")
        val owner = createPlayer(outside, "owner")

        val stranger = createPlayer(inside, "stranger")
        tick(2)

        assertEquals(house.entrance, stranger.tile)
    }

    @Test
    fun `Owner's items left inside never disappear and only the owner sees them`() {
        own("owner")
        val owner = createPlayer(inside, "owner")
        owner.inventory.add("bronze_sword")

        owner.itemOption("Drop", "bronze_sword")
        tick(1000)

        val item = FloorItems.at(inside).singleOrNull()
        assertNotNull(item)
        assertEquals("owner", item.owner)
    }

    @Test
    fun `Guests can't leave items inside or take the owner's`() {
        own("owner")
        val owner = createPlayer(inside, "owner")
        house.guests.add("guest")
        val guest = createPlayer(Tile(3099, 3278), "guest")
        guest.inventory.add("bronze_sword")
        val item = FloorItems.add(Tile(3099, 3278), "bronze_dagger", revealTicks = FloorItems.NEVER, disappearTicks = FloorItems.NEVER, owner = owner)

        guest.itemOption("Drop", "bronze_sword")
        guest.floorItemOption(item, "Take")
        tick(3)

        assertEquals(1, guest.inventory.count("bronze_sword"))
        assertEquals(0, guest.inventory.count("bronze_dagger"))
        assertTrue(FloorItems.at(Tile(3099, 3278)).contains(item))
    }

    @Test
    fun `House is saved and loaded with its owner, guests and items`() {
        own("owner")
        val owner = createPlayer(inside, "owner")
        house.guests.add("guest")
        owner.inventory.add("coins", 500)
        owner.itemOption("Drop", "coins")
        tick(2)

        WorldHouses.save(house)
        FloorItems.clear()
        WorldHouses.clear()
        WorldHouses.load(get())

        assertEquals("owner", house.owner)
        assertEquals(setOf("guest"), house.guests)
        val item = FloorItems.at(inside).single()
        assertEquals("coins", item.id)
        assertEquals(500, item.amount)
        assertEquals("owner", item.owner)
    }

    @Test
    fun `Giving up the house puts it back on sale`() {
        own("owner")
        val owner = createPlayer(inside, "owner")

        owner.houseMenu()
        owner.dialogueOption("line5")
        tick()
        owner.dialogueOption("line1")
        tick()

        assertNull(house.owner)
        assertEquals(house.entrance, owner.tile)
        assertEquals(listOf("Buy"), houseDoor().def(owner).options!!.filterNotNull().dropLast(1))
    }

    @Test
    fun `Other doors of the same kind still open normally`() {
        val player = createPlayer(Tile(3102, 3258))
        val other = GameObjects.find(Tile(3101, 3258), "door_14_closed")

        player.objectOption(other, "Open")
        tick(3)

        assertNull(GameObjects.findOrNull(Tile(3101, 3258), "door_14_closed"))
    }
}
