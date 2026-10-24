package content.entity.player.command

import world.gregs.voidps.engine.Script
import world.gregs.voidps.engine.client.command.adminCommand
import world.gregs.voidps.engine.client.command.commandAlias
import world.gregs.voidps.engine.client.command.intArg
import world.gregs.voidps.engine.client.message
import world.gregs.voidps.engine.client.ui.chat.toSILong
import world.gregs.voidps.engine.inv.add
import world.gregs.voidps.engine.inv.inventory

/**
 * Portuguese shortcuts for the owner to test with: `::ouro 10k` for coins, `::criar <item> <amount>` for any item.
 */
class TestShortcuts : Script {

    init {
        adminCommand("ouro", intArg("amount", "number of coins (e.g. 1000, 10k, 1m, default 1000)", optional = true), desc = "Spawn coins into your inventory") { args ->
            val amount = (args.getOrNull(0) ?: "1000").toSILong().coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
            if (inventory.add("coins", amount)) {
                message("Você recebeu $amount moedas.")
            } else {
                message("Não coube na mochila.")
            }
        }
        commandAlias("ouro", "gold")
        commandAlias("item", "criar")
    }
}
