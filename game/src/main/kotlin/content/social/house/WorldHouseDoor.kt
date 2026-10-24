package content.social.house

import content.entity.obj.door.enterDoor
import content.entity.obj.door.openDoor
import content.entity.player.bank.bank
import content.entity.player.dialogue.translate
import content.entity.player.dialogue.type.choice
import content.entity.player.dialogue.type.nameEntry
import world.gregs.voidps.engine.Script
import world.gregs.voidps.engine.client.command.commandAlias
import world.gregs.voidps.engine.client.command.playerCommand
import world.gregs.voidps.engine.client.message
import world.gregs.voidps.engine.client.variable.hasClock
import world.gregs.voidps.engine.client.variable.start
import world.gregs.voidps.engine.data.definition.AccountDefinitions
import world.gregs.voidps.engine.data.definition.ItemDefinitions
import world.gregs.voidps.engine.entity.character.move.tele
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.Players
import world.gregs.voidps.engine.entity.character.player.name
import world.gregs.voidps.engine.entity.obj.GameObject
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.engine.inv.remove
import world.gregs.voidps.engine.queue.strongQueue

/**
 * The door of a [WorldHouse]: sells the house while it has no owner and lets the owner and guests through.
 * With the house's own door in the cache (tools WorldHouseObjects) visitors can knock and the owner manages guests
 * from the door's "Manage" option; the `house` command does the same anywhere.
 */
class WorldHouseDoor(val accounts: AccountDefinitions) : Script {

    init {
        // The map door, while the cache doesn't have the house's own door
        for (doorObject in WorldHouses.all().map { it.doorObject }.toSet()) {
            objectOperate("Open", doorObject) { (target) ->
                val house = WorldHouses.byDoor(target.tile)
                if (house == null) {
                    // Same door used elsewhere in the world
                    openDoor(target)
                    return@objectOperate
                }
                open(house, target)
            }
        }

        for (house in WorldHouses.all()) {
            if (!WorldHouses.hasDoors(house)) {
                continue
            }
            objectOperate("Open", house.visitorDoor) { (target) ->
                open(house, target)
            }
            objectOperate("Knock-at", house.visitorDoor) {
                knock(house)
            }
            objectOperate("Buy", house.saleDoor) { (target) ->
                open(house, target)
            }
            objectOperate("Open", house.saleDoor) { (target) ->
                open(house, target)
            }
            objectOperate("Open", house.ownerDoor) { (target) ->
                open(house, target)
            }
            objectOperate("Manage", house.ownerDoor) {
                if (house.isOwner(this)) {
                    ownerMenu(house)
                }
            }
        }

        playerCommand("house", desc = "Invite people to your house or send them out") {
            val house = WorldHouses.ownedBy(accountName)
            if (house == null) {
                message("You don't own a house.")
                return@playerCommand
            }
            strongQueue("house_menu") {
                ownerMenu(house)
            }
        }
        commandAlias("house", "casa")
    }

    private suspend fun Player.open(house: WorldHouse, door: GameObject) {
        when {
            // Anyone inside can always leave
            tile in house.area -> enterDoor(door)
            house.owner == null -> buy(house, door)
            house.canEnter(this) -> enterDoor(door)
            else -> message("The door is locked. ${house.name} is the house of ${house.ownerName}.")
        }
    }

    private suspend fun Player.buy(house: WorldHouse, door: GameObject) {
        if (WorldHouses.ownedBy(accountName) != null) {
            message("The door is locked. You already own a house.")
            return
        }
        choice("${house.name} is for sale for ${house.price} coins. Buy it?") {
            option("Yes, buy the house.") {
                if (house.owner != null || WorldHouses.ownedBy(accountName) != null) {
                    return@option
                }
                if (!inventory.remove("coins", house.price) && !bank.remove("coins", house.price)) {
                    message("You need ${house.price} coins to buy this house.")
                    return@option
                }
                house.owner = accountName
                house.ownerName = name
                house.guests.clear()
                WorldHouses.save(house)
                WorldHouses.showDoors(house)
                message("You bought ${house.name}! Only you and the people you invite can open its door.")
                enterDoor(door)
            }
            option("No thanks.")
        }
    }

    private fun Player.knock(house: WorldHouse) {
        if (hasClock("world_house_knock")) {
            return
        }
        start("world_house_knock", 5)
        message("You knock at the door.")
        val owner = Players.firstOrNull { it.accountName == house.owner }
        if (owner == null) {
            message("Nobody answers.")
            return
        }
        owner.message("$name is knocking at the door of ${house.name}.")
    }

    private suspend fun Player.ownerMenu(house: WorldHouse) {
        choice {
            option("Invite someone.") {
                invite(house)
            }
            option("Remove a guest.") {
                removeGuest(house)
            }
            option("Send visitors out.") {
                sendVisitorsOut(house)
            }
            option("Remove the nearest furniture.") {
                removeFurniture(house)
            }
            option("Give up the house.") {
                giveUp(house)
            }
        }
    }

    private suspend fun Player.invite(house: WorldHouse) {
        if (house.guests.size >= WorldHouses.MAX_GUESTS) {
            message("You can invite up to ${WorldHouses.MAX_GUESTS} people.")
            return
        }
        val typed = nameEntry(translate("Enter the name of the player to invite:")).trim()
        if (typed.isEmpty()) {
            return
        }
        val account = accounts.get(typed)
        when {
            account == null -> message("There's no player called $typed.")
            account.accountName == accountName -> message("You can always open your own door.")
            !house.guests.add(account.accountName) -> message("${account.displayName} is already invited.")
            else -> {
                WorldHouses.save(house)
                message("${account.displayName} can now open your door.")
                Players.firstOrNull { it.accountName == account.accountName }?.message("$name invited you to their house.")
            }
        }
    }

    private suspend fun Player.removeGuest(house: WorldHouse) {
        if (house.guests.isEmpty()) {
            message("You haven't invited anyone.")
            return
        }
        message("Guests: ${house.guests.joinToString(", ") { accounts.getByAccount(it)?.displayName ?: it }}")
        val typed = nameEntry(translate("Enter the name of the guest to remove:")).trim()
        if (typed.isEmpty()) {
            return
        }
        val account = accounts.get(typed)
        if (account == null || !house.guests.remove(account.accountName)) {
            message("$typed isn't one of your guests.")
            return
        }
        WorldHouses.save(house)
        message("${account.displayName} can no longer open your door.")
        val guest = Players.firstOrNull { it.accountName == account.accountName } ?: return
        if (guest.tile in house.area) {
            guest.tele(house.entrance)
            guest.message("You are no longer invited to this house.")
        }
    }

    private fun Player.sendVisitorsOut(house: WorldHouse) {
        val visitors = Players.filter { it != this && it.tile in house.area }
        if (visitors.isEmpty()) {
            message("There's no one else in your house.")
            return
        }
        for (visitor in visitors) {
            visitor.tele(house.entrance)
            visitor.message("The owner sent you out of the house.")
        }
        message("You sent ${visitors.size} ${if (visitors.size == 1) "visitor" else "visitors"} out.")
    }

    /**
     * Furniture can be built where it can't be reached, like a chair facing a table, so the owner can also remove
     * the built piece closest to them from the menu
     */
    private suspend fun Player.removeFurniture(house: WorldHouse) {
        val piece = house.pieces.filter { it.tile in house.furniture }.minByOrNull { it.tile.distanceTo(tile) }
        if (piece == null || tile !in house.area) {
            message("There's no furniture you built near you.")
            return
        }
        val name = ItemDefinitions.get(house.furniture.getValue(piece.tile)).name
        choice("Remove the $name?") {
            option("Yes") {
                if (house.isOwner(this)) {
                    WorldHouses.removeFurniture(house, piece)
                    anim("construction_remove")
                }
            }
            option("No")
        }
    }

    private suspend fun Player.giveUp(house: WorldHouse) {
        if (WorldHouses.items(house).isNotEmpty()) {
            message("Pick up your things from the floor before giving up the house.")
            return
        }
        choice("Give up the house? It goes back on sale and the coins aren't returned.") {
            option("Yes, give it up.") {
                if (!house.isOwner(this)) {
                    return@option
                }
                house.clearOwner()
                WorldHouses.resetFurniture(house)
                WorldHouses.save(house)
                WorldHouses.showDoors(house)
                for (player in Players.filter { it.tile in house.area }) {
                    player.tele(house.entrance)
                }
                message("You gave up the house.")
            }
            option("No.")
        }
    }
}
