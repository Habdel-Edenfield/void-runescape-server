package content.social.house

import world.gregs.voidps.engine.Script
import world.gregs.voidps.engine.client.message
import world.gregs.voidps.engine.data.definition.AccountDefinitions
import world.gregs.voidps.engine.entity.character.move.tele
import world.gregs.voidps.engine.entity.item.floor.FloorItems
import world.gregs.voidps.engine.queue.strongQueue

/**
 * Keeps a [WorldHouse] private and its owner's things in place:
 * anyone not allowed in is put outside, only the owner can leave items inside,
 * and those items never disappear and are saved with the house.
 */
class WorldHouseKeeping(val accounts: AccountDefinitions) : Script {

    init {
        worldSpawn {
            WorldHouses.load(accounts)
        }

        worldDespawn {
            WorldHouses.saveAll()
        }

        playerSpawn {
            for (house in WorldHouses.all()) {
                WorldHouses.showDoor(this, house)
            }
        }

        for (house in WorldHouses.all()) {
            // Teleporting in or logging in inside without being allowed
            entered(house.areaName) {
                if (house.canEnter(this)) {
                    return@entered
                }
                strongQueue("world_house_expel", 1) {
                    if (tile in house.area && !house.canEnter(this)) {
                        tele(house.entrance)
                        message("You aren't allowed in this house.")
                    }
                }
            }
        }

        droppable {
            val house = WorldHouses.at(tile) ?: return@droppable true
            if (!house.isOwner(this)) {
                message("You can only leave items in your own house.")
                return@droppable false
            }
            // Dropping onto a pile only adds to it, so save after the drop
            WorldHouses.changed(house)
            true
        }

        floorItemSpawn {
            val house = WorldHouses.at(tile) ?: return@floorItemSpawn
            if (owner == null || owner != house.ownerName) {
                return@floorItemSpawn
            }
            reset(revealTicks = FloorItems.NEVER, disappearTicks = FloorItems.NEVER)
            WorldHouses.changed(house)
        }

        floorItemDespawn {
            val house = WorldHouses.at(tile) ?: return@floorItemDespawn
            if (owner != null && owner == house.ownerName) {
                WorldHouses.changed(house)
            }
        }
    }
}
