package content.social.house

import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.type.Area
import world.gregs.voidps.type.Tile

/**
 * A building in the open world that one player owns: only the [owner] and their [guests] get through the [door].
 * Not to be confused with Construction's instanced player-owned house.
 */
class WorldHouse(
    val id: String,
    val name: String,
    val areaName: String,
    val area: Area,
    val door: Tile,
    val doorObject: String,
    val entrance: Tile,
    val price: Int,
) {
    /**
     * The house's own door, put on the map in place of [doorObject] by tools WorldHouseObjects, turns into
     * [visitorDoor], [saleDoor] or [ownerDoor] for each player depending on the player variable [varp],
     * which also gives the owner the "Build" option on its [pieces]
     */
    val ownDoor = "world_house_${id}_door_closed"
    val visitorDoor = "world_house_${id}_visitor_door"
    val saleDoor = "world_house_${id}_sale_door"
    val ownerDoor = "world_house_${id}_owner_door"
    val varp = "world_house_${id}_view"

    /**
     * Furniture the owner can change or use, from world_house_furniture.tables.toml
     */
    val pieces: MutableList<WorldHousePiece> = mutableListOf()

    /**
     * Construction furniture (house_furniture rows) the owner built in place of a piece, by the piece's tile
     */
    val furniture: MutableMap<Tile, String> = mutableMapOf()

    /**
     * Tiles of the fires put out
     */
    val unlit: MutableSet<Tile> = mutableSetOf()

    /**
     * Account name of the owner, null while the house is for sale
     */
    var owner: String? = null

    /**
     * Display name of the owner, which owns the items kept on the floor
     */
    var ownerName: String? = null

    /**
     * Account names of the players the owner let in
     */
    val guests: MutableSet<String> = linkedSetOf()

    fun isOwner(player: Player) = owner != null && owner == player.accountName

    fun canEnter(player: Player) = isOwner(player) || player.accountName in guests

    fun clearOwner() {
        owner = null
        ownerName = null
        guests.clear()
    }

    override fun toString() = "WorldHouse($id, owner=$owner)"
}

/**
 * A piece of a [WorldHouse]'s furniture at [tile], which the owner can swap for Construction furniture of [hotspot]
 * or, being a fire ([light]), anyone inside can put out and light again.
 * tools WorldHouseObjects puts [ownId] on the map, turning into [buildId] for the owner, and makes the [unlitId] fire.
 */
class WorldHousePiece(
    val house: String,
    val piece: String,
    val tile: Tile,
    val hotspot: String?,
    val light: Boolean,
) {
    val ownId = "world_house_${house}_$piece"
    val buildId = "${ownId}_build"
    val unlitId = "${ownId}_unlit"
}
