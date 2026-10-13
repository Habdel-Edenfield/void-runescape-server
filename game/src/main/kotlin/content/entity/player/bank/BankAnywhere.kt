package content.entity.player.bank

import content.area.wilderness.inWilderness
import content.entity.player.bank.pin.verifyBankAccess
import world.gregs.voidps.engine.Script
import world.gregs.voidps.engine.client.message
import world.gregs.voidps.engine.data.definition.InterfaceDefinitions
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.item.Item
import world.gregs.voidps.engine.inv.inventory

/**
 * Bank-1, Bank-5 and Bank-All on inventory items send them to the bank from anywhere outside the Wilderness.
 * The client draws the options between Drop and Examine; they use inventory options 3-5, which no item uses.
 */
class BankAnywhere : Script {

    init {
        interfaceOption("Bank-1", "inventory:inventory") { (item, slot) ->
            bankAnywhere(item, slot, 1)
        }

        interfaceOption("Bank-5", "inventory:inventory") { (item, slot) ->
            bankAnywhere(item, slot, 5)
        }

        interfaceOption("Bank-All", "inventory:inventory") { (item, slot) ->
            bankAnywhere(item, slot, Int.MAX_VALUE)
        }

        variableSet("in_wilderness") { _, _, _ ->
            if (interfaces.contains("inventory")) {
                unlockInventoryOptions()
            }
        }
    }

    suspend fun Player.bankAnywhere(item: Item, slot: Int, amount: Int) {
        if (inWilderness || !verifyBankAccess() || inventory.count(item.id) == 0) {
            return
        }
        if (item.def["unbankable", 0] != 0) {
            // The bank destroys event items instead of storing them, here they stay in the inventory
            message("This item cannot be banked.")
            return
        }
        BankDeposit.deposit(this, inventory, item, amount, slot, check = false)
    }

    companion object {
        private val bankOptions = setOf("Bank-1", "Bank-5", "Bank-All")

        /**
         * Enables the inventory item options, leaving out the bank options while in the Wilderness.
         */
        fun Player.unlockInventoryOptions() {
            val options: Array<String> = InterfaceDefinitions.getComponent("inventory", "inventory")?.getOrNull("options") ?: return
            val enabled = options.filter { it != "" && !(inWilderness && it in bankOptions) }.toSet()
            interfaceOptions.unlock("inventory", "inventory", 0 until 28, enabled)
        }
    }
}
