package content.entity.player.bank

import WorldTest
import containsMessage
import interfaceOption
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.client.ui.hasOpen
import world.gregs.voidps.engine.client.variable.PlayerVariables
import world.gregs.voidps.engine.data.definition.InterfaceDefinitions
import world.gregs.voidps.engine.data.definition.ItemDefinitions
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.item.Item
import world.gregs.voidps.engine.inv.add
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.network.client.Client
import world.gregs.voidps.network.login.protocol.encode.sendInterfaceSettings

internal class BankAnywhereTest : WorldTest() {

    private fun Player.bankOption(option: String, item: String, slot: Int) {
        interfaceOption("inventory", "inventory", option, item = Item(item), slot = slot)
        tick()
    }

    @Test
    fun `Bank-1 sends one ore from a stack`() {
        val player = createPlayer(emptyTile)
        player.inventory.add("tin_ore_noted", 574)

        player.bankOption("Bank-1", "tin_ore_noted", 0)

        assertEquals(573, player.inventory.count("tin_ore_noted"))
        assertEquals(1, player.bank.count("tin_ore"))
    }

    @Test
    fun `Bank-All sends the whole stack`() {
        val player = createPlayer(emptyTile)
        player.inventory.add("tin_ore_noted", 574)

        player.bankOption("Bank-All", "tin_ore_noted", 0)

        assertFalse(player.inventory.contains("tin_ore_noted"))
        assertEquals(574, player.bank.count("tin_ore"))
    }

    @Test
    fun `Bank-5 sends five unstacked logs`() {
        val player = createPlayer(emptyTile)
        player.inventory.add("logs", 7)

        player.bankOption("Bank-5", "logs", 3)

        assertEquals(2, player.inventory.count("logs"))
        assertEquals(5, player.bank.count("logs"))
    }

    @Test
    fun `Bank-5 sends what there is when less than five`() {
        val player = createPlayer(emptyTile)
        player.inventory.add("logs", 3)

        player.bankOption("Bank-5", "logs", 0)

        assertFalse(player.inventory.contains("logs"))
        assertEquals(3, player.bank.count("logs"))
    }

    @Test
    fun `Nothing is banked in the wilderness`() {
        val player = createPlayer(emptyTile)
        player.inventory.add("logs", 2)
        player["in_wilderness"] = true

        player.bankOption("Bank-All", "logs", 0)

        assertEquals(2, player.inventory.count("logs"))
        assertEquals(0, player.bank.count("logs"))
    }

    @Test
    fun `Bank options are hidden in the wilderness and shown again outside`() {
        val player = createPlayer(emptyTile)
        val client: Client = mockk(relaxed = true)
        player.client = client
        (player.variables as PlayerVariables).client = client
        val component = InterfaceDefinitions.getComponent("inventory", "inventory")!!.id
        val bankBits = (2 shl 3) or (2 shl 4) or (2 shl 5)

        mockkStatic("world.gregs.voidps.network.login.protocol.encode.InterfaceEncodersKt")
        try {
            player["in_wilderness"] = true
            verify { client.sendInterfaceSettings(component, 0, 27, match { it and bankBits == 0 && it != 0 }) }

            player.clear("in_wilderness")
            verify { client.sendInterfaceSettings(component, 0, 27, match { it and bankBits == bankBits }) }
        } finally {
            unmockkStatic("world.gregs.voidps.network.login.protocol.encode.InterfaceEncodersKt")
        }
    }

    @Test
    fun `Event items stay in the inventory`() {
        val player = createPlayer(emptyTile)
        player.inventory.add("raw_fish_like_thing")

        player.bankOption("Bank-1", "raw_fish_like_thing", 0)

        assertTrue(player.inventory.contains("raw_fish_like_thing"))
        assertTrue(player.containsMessage("This item cannot be banked."))
    }

    @Test
    fun `Full bank keeps the item in the inventory`() {
        val player = createPlayer(emptyTile)
        var id = 0
        while (!player.bank.isFull() && id < ItemDefinitions.size) {
            val item = ItemDefinitions.get(id++)
            if (item.stringId.isNotEmpty() && !item.stringId.startsWith("tin_ore") && !item.noted) {
                player.bank.add(item.stringId)
            }
        }
        player.inventory.add("tin_ore_noted", 10)

        player.bankOption("Bank-All", "tin_ore_noted", 0)

        assertEquals(10, player.inventory.count("tin_ore_noted"))
        assertTrue(player.containsMessage("Your bank is too full to deposit any more."))
    }

    @Test
    fun `Pin is asked before banking`() {
        val player = createPlayer(emptyTile)
        player["bank_pin"] = "1739"
        player.inventory.add("logs", 2)

        player.bankOption("Bank-1", "logs", 0)

        assertTrue(player.hasOpen("bank_pin"))
        assertEquals(2, player.inventory.count("logs"))
        assertEquals(0, player.bank.count("logs"))

        for (digit in "1739") {
            // The keypad shuffles after every digit
            val layout: List<Int> = player["bank_pin_layout"]!!
            player.interfaceOption("bank_pin", "digit_${layout.indexOf(digit.digitToInt()) + 1}", "Enter digit")
        }
        tick()

        assertEquals(1, player.inventory.count("logs"))
        assertEquals(1, player.bank.count("logs"))
    }

    @Test
    fun `Unlocked pin banks straight away`() {
        val player = createPlayer(emptyTile)
        player["bank_pin"] = "1739"
        player["bank_pin_unlocked"] = true
        player.inventory.add("logs", 2)

        player.bankOption("Bank-1", "logs", 0)

        assertEquals(1, player.inventory.count("logs"))
        assertEquals(1, player.bank.count("logs"))
    }
}
