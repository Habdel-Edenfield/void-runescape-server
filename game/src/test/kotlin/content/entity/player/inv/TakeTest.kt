package content.entity.player.inv

import WorldTest
import floorItemOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.entity.item.floor.FloorItems
import world.gregs.voidps.engine.inv.add
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.type.Tile

internal class TakeTest : WorldTest() {

    @Test
    fun `Take item off the floor`() {
        val tile = emptyTile
        val player = createPlayer(tile)
        val item = FloorItems.add(tile.add(0, 2), "bronze_sword")

        player.floorItemOption(item, "Take")
        tick(5)

        assertTrue(player.inventory.contains("bronze_sword"))
        assertTrue(FloorItems.at(tile.add(0, 2)).isEmpty())
    }

    @Test
    fun `Take item up off a table`() {
        val tile = Tile(3212, 3218, 1)
        val player = createPlayer(tile)
        val item = FloorItems.add(tile.add(1, 0), "bronze_sword")

        player.floorItemOption(item, "Take")
        tick(5)

        assertTrue(player.inventory.contains("bronze_sword"))
        assertTrue(FloorItems.at(tile.add(1, 0)).isEmpty())
        assertEquals(tile, player.tile)
    }

    @Test
    fun `Take all items in a pile`() {
        val tile = emptyTile
        val player = createPlayer(tile)
        val pile = tile.add(0, 2)
        val arrows = FloorItems.add(pile, "bronze_arrow", 5)
        FloorItems.add(pile, "coins", 20)
        FloorItems.add(pile, "bones")

        player.floorItemOption(arrows, "Take all")
        tick(5)

        assertEquals(5, player.inventory.count("bronze_arrow"))
        assertEquals(20, player.inventory.count("coins"))
        assertTrue(player.inventory.contains("bones"))
        assertTrue(FloorItems.at(pile).isEmpty())
    }

    @Test
    fun `Take all leaves other players' private loot and what doesn't fit`() {
        val tile = emptyTile
        val player = createPlayer(tile)
        player.inventory.add("coins", 1)
        player.inventory.add("bronze_sword", 27)
        val pile = tile.add(0, 2)
        val coins = FloorItems.add(pile, "coins", 20)
        FloorItems.add(pile, "bones")
        FloorItems.add(pile, "bronze_arrow", 5, revealTicks = 100, owner = "someone_else")

        player.floorItemOption(coins, "Take all")
        tick(5)

        assertEquals(21, player.inventory.count("coins"))
        assertFalse(player.inventory.contains("bones"))
        assertEquals(listOf("bones", "bronze_arrow"), FloorItems.at(pile).map { it.id }.sorted())
    }
}
