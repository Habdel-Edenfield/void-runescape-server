package content.entity.player.command

import content.area.wilderness.inWilderness
import content.entity.player.dialogue.type.choice
import world.gregs.voidps.engine.Script
import world.gregs.voidps.engine.client.command.adminCommand
import world.gregs.voidps.engine.client.command.commandAlias
import world.gregs.voidps.engine.client.message
import world.gregs.voidps.engine.entity.character.move.tele
import world.gregs.voidps.engine.queue.strongQueue
import world.gregs.voidps.type.Tile

/**
 * Temporary: takes an admin to the door of each building that was considered for the first
 * house in the open world (content.social.house), so the owner can look at them in game.
 */
class HouseCandidates : Script {

    init {
        adminCommand("house_candidates", desc = "Go to the door of a building being considered for a house") {
            val player = this
            if (player.inWilderness) {
                player.message("Não dá para usar este comando na Wilderness.")
                return@adminCommand
            }
            player.strongQueue("house_candidates") {
                val choice = player.choice(CANDIDATES.map { it.first }, "Ir até qual casa?")
                if (choice == -1) {
                    return@strongQueue
                }
                val (name, tile) = CANDIDATES[choice - 1]
                player.tele(tile)
                player["world_map_centre"] = player.tile.id
                player["world_map_marker_player"] = player.tile.id
                player.message("$name: a porta fica bem à sua frente.")
            }
        }
        commandAlias("house_candidates", "casas")
    }

    companion object {
        val CANDIDATES = listOf(
            "Draynor, ao norte da casa do Morgan" to Tile(3100, 3275),
            "Taverley, cabana redonda do sudoeste" to Tile(2885, 3423),
            "Taverley, cabana redonda do norte" to Tile(2884, 3439),
        )
    }
}
