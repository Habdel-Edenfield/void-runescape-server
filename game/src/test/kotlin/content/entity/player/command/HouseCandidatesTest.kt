package content.entity.player.command

import WorldTest
import dialogueOption
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.rsmod.game.pathfinder.flag.CollisionFlag
import world.gregs.voidps.engine.client.command.Commands
import world.gregs.voidps.engine.entity.character.player.PlayerRights
import world.gregs.voidps.engine.entity.character.player.rights
import world.gregs.voidps.engine.entity.obj.GameObjects
import world.gregs.voidps.engine.map.collision.Collisions
import world.gregs.voidps.type.Tile
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HouseCandidatesTest : WorldTest() {

    @Test
    fun `Each house candidate lands on a free tile next to a door`() {
        for ((index, candidate) in HouseCandidates.CANDIDATES.withIndex()) {
            val (name, tile) = candidate
            val player = createPlayer(Tile(3221, 3219)) { it.rights = PlayerRights.Admin }

            runTest { Commands.call(player, "casas") }
            tick()
            player.dialogueOption("line${index + 1}")
            tick()

            assertEquals(tile, player.tile, name)
            val blocked = CollisionFlag.FLOOR or CollisionFlag.FLOOR_DECORATION or CollisionFlag.OBJECT
            assertEquals(0, Collisions[tile.x, tile.y, tile.level] and blocked, "$name: tile is blocked")
            val door = (-1..1).flatMap { dx -> (-1..1).map { dy -> tile.add(dx, dy) } }
                .flatMap { GameObjects.at(it) }
                .any { it.def.containsOption("Open") || it.def.containsOption("Buy") }
            assertTrue(door, "$name: no door next to ${tile.x}, ${tile.y}")
        }
    }
}
