package content.social.house

import com.github.michaelbull.logging.InlineLogger
import world.gregs.config.Config
import world.gregs.config.writePair
import world.gregs.voidps.engine.client.message
import world.gregs.voidps.engine.data.Settings
import world.gregs.voidps.engine.data.definition.AccountDefinitions
import world.gregs.voidps.engine.data.definition.Areas
import world.gregs.voidps.engine.data.definition.ObjectDefinitions
import world.gregs.voidps.engine.data.definition.Tables
import world.gregs.voidps.engine.entity.World
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.Players
import world.gregs.voidps.engine.entity.character.player.chat.ChatType
import world.gregs.voidps.engine.entity.item.floor.FloorItem
import world.gregs.voidps.engine.entity.item.floor.FloorItems
import world.gregs.voidps.engine.entity.obj.GameObject
import world.gregs.voidps.engine.entity.obj.GameObjects
import world.gregs.voidps.engine.entity.obj.ObjectLayer
import world.gregs.voidps.engine.entity.obj.remove
import world.gregs.voidps.engine.entity.obj.replace
import world.gregs.voidps.type.Tile
import java.io.File

/**
 * The houses listed in world_houses.tables.toml and who owns them.
 * Each house is saved to its own file: owner, guests, the owner's items left on the floor inside (which never
 * disappear and come back after a restart), the furniture built and the fires put out.
 */
object WorldHouses {
    private val logger = InlineLogger()
    const val MAX_GUESTS = 20
    private var houses: Map<String, WorldHouse>? = null
    private var loading = false

    fun all(): Collection<WorldHouse> = definitions().values

    fun get(id: String): WorldHouse? = definitions()[id]

    fun at(tile: Tile): WorldHouse? = all().firstOrNull { tile in it.area }

    fun byDoor(tile: Tile): WorldHouse? = all().firstOrNull { it.door == tile }

    fun ownedBy(accountName: String): WorldHouse? = all().firstOrNull { it.owner == accountName }

    private fun definitions(): Map<String, WorldHouse> {
        houses?.let { return it }
        val table = Tables.getOrNull("world_houses") ?: return emptyMap()
        val loaded = table.rows().associate { row ->
            val area = row.string("area")
            row.rowId to WorldHouse(
                id = row.rowId,
                name = row.string("name"),
                areaName = area,
                area = Areas[area],
                door = row.tile("door"),
                doorObject = row.obj("door_object"),
                entrance = row.tile("entrance"),
                price = row.int("price"),
            )
        }
        val furniture = Tables.getOrNull("world_house_furniture")
        if (furniture != null) {
            for (row in furniture.rows()) {
                val house = loaded[row.string("house")] ?: continue
                house.pieces.add(WorldHousePiece(house.id, row.string("piece"), row.tile("tile"), row.stringOrNull("hotspot"), row.intOrNull("unlit_object") != null))
            }
        }
        houses = loaded
        return loaded
    }

    fun pieces(): List<WorldHousePiece> = all().flatMap { it.pieces }

    fun piece(tile: Tile): WorldHousePiece? = all().firstNotNullOfOrNull { house -> house.pieces.firstOrNull { it.tile == tile } }

    /**
     * Whether the cache has the house's own objects (tools WorldHouseObjects)
     */
    fun hasObjects(piece: WorldHousePiece) = ObjectDefinitions.contains(piece.ownId)

    private fun pieceObject(piece: WorldHousePiece): GameObject? = GameObjects.getLayer(piece.tile, ObjectLayer.GROUND)

    /**
     * Puts the Construction [furniture] in place of [piece]
     */
    fun build(house: WorldHouse, piece: WorldHousePiece, furniture: String): Boolean {
        val current = pieceObject(piece) ?: return false
        if (current.id != piece.ownId) {
            return false
        }
        val id = Tables.objList("house_furniture.$furniture.objects").first()
        val rotation = Tables.intListOrNull("house_furniture.$furniture.rotations")?.firstOrNull() ?: 0
        // Face the same way as the map's piece, which is reached from the same sides
        val sides = current.def.blockFlag
        val flag = ObjectDefinitions.get(id).blockFlag
        val turn = if (sides == 0) 0 else (0 until 4).firstOrNull { rotate(flag, it) == sides } ?: 0
        current.replace(id, rotation = (current.rotation + rotation + turn) and 0x3)
        house.furniture[piece.tile] = furniture
        changed(house)
        return true
    }

    /**
     * The sides an object can't be reached from, [flag] turned [rotation] times
     */
    private fun rotate(flag: Int, rotation: Int): Int = if (rotation == 0) flag else ((flag shl rotation) and 0xf) or (flag shr (4 - rotation))

    /**
     * Puts [piece] back in place of the furniture built there
     */
    fun removeFurniture(house: WorldHouse, piece: WorldHousePiece) {
        if (house.furniture.remove(piece.tile) == null) {
            return
        }
        val current = pieceObject(piece)
        if (current != null && current.id != piece.ownId) {
            // Removing a replacement brings back the map's object
            current.remove()
        }
        changed(house)
    }

    /**
     * Lights or puts out the fire [piece]
     */
    fun light(house: WorldHouse, piece: WorldHousePiece, lit: Boolean) {
        val current = pieceObject(piece) ?: return
        if (lit && current.id == piece.unlitId) {
            current.remove()
            house.unlit.remove(piece.tile)
        } else if (!lit && current.id == piece.ownId) {
            current.replace(piece.unlitId)
            house.unlit.add(piece.tile)
        } else {
            return
        }
        changed(house)
    }

    /**
     * Brings back every piece of [house] as the map has it
     */
    fun resetFurniture(house: WorldHouse) {
        for (piece in house.pieces) {
            removeFurniture(house, piece)
            light(house, piece, true)
        }
    }

    /**
     * Whether the cache has the house's own door (tools WorldHouseObjects)
     */
    fun hasDoors(house: WorldHouse) = ObjectDefinitions.contains(house.ownDoor)

    /**
     * Shows [player] the door of [house] for visitors (0), for sale (1) or for the owner (2)
     */
    fun showDoor(player: Player, house: WorldHouse) {
        player[house.varp] = when {
            house.owner == null -> 1
            house.isOwner(player) -> 2
            else -> 0
        }
    }

    /**
     * Shows every player the door of [house] as it is now
     */
    fun showDoors(house: WorldHouse) {
        for (player in Players) {
            showDoor(player, house)
        }
    }

    /**
     * Examining a house door says who it belongs to, or how to buy it
     */
    fun examine(player: Player, id: String): Boolean {
        val house = all().firstOrNull { id == it.ownDoor || id == it.visitorDoor || id == it.saleDoor || id == it.ownerDoor } ?: return false
        player.message(
            when {
                house.owner == null -> "${house.name} is for sale for ${house.price} coins. Click the door to buy it."
                house.isOwner(player) -> "${house.name} is your house. Right-click the door to invite people."
                else -> "${house.name} is the house of ${house.ownerName}."
            },
            ChatType.Game,
        )
        return true
    }

    /**
     * The owner's items on the floor of [house]
     */
    fun items(house: WorldHouse): List<FloorItem> {
        val owner = house.ownerName ?: return emptyList()
        return house.area.flatMap { tile -> FloorItems.at(tile).filter { it.owner == owner } }
    }

    /**
     * Save [house] at the end of the tick, after a drop or pick up has finished
     */
    fun changed(house: WorldHouse) {
        if (loading) {
            return
        }
        World.queue("world_house_${house.id}", 0) {
            save(house)
        }
    }

    private fun directory() = File(Settings["storage.players.path"]).resolve("world_houses")

    fun save(house: WorldHouse) {
        val file = directory().resolve("${house.id}.toml")
        try {
            file.parentFile.mkdirs()
            val items = items(house)
            Config.atomicFileWriter(file) {
                writePair("owner", house.owner ?: "")
                writePair("guests", house.guests.toList())
                writePair("furniture", house.furniture.entries.associate { (tile, furniture) -> "${tile.x}_${tile.y}" to furniture })
                writePair("unlit", house.unlit.map { "${it.x}_${it.y}" })
                writePair(
                    "items",
                    items.map { item ->
                        val map = linkedMapOf<String, Any>("x" to item.tile.x, "y" to item.tile.y, "level" to item.tile.level, "id" to item.id, "amount" to item.amount)
                        if (item.charges > 0) {
                            map["charges"] = item.charges
                        }
                        map
                    },
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save $house." }
        }
    }

    fun saveAll() {
        for (house in all()) {
            save(house)
        }
    }

    /**
     * Read every house file and put the owners' items back on the floor
     */
    fun load(accounts: AccountDefinitions) {
        loading = true
        try {
            for (house in all()) {
                val file = directory().resolve("${house.id}.toml")
                if (file.exists()) {
                    load(house, file, accounts)
                }
            }
        } finally {
            loading = false
        }
    }

    private fun tile(key: String): Tile {
        val (x, y) = key.split("_")
        return Tile(x.toInt(), y.toInt())
    }

    private fun load(house: WorldHouse, file: File, accounts: AccountDefinitions) {
        house.clearOwner()
        val items = mutableListOf<FloorItem>()
        val furniture = mutableMapOf<Tile, String>()
        val unlit = mutableSetOf<Tile>()
        Config.fileReader(file) {
            while (nextPair()) {
                when (val key = key()) {
                    "owner" -> house.owner = string().ifEmpty { null }
                    "guests" -> while (nextElement()) {
                        house.guests.add(string())
                    }
                    "furniture" -> while (nextEntry()) {
                        val tile = tile(key())
                        furniture[tile] = string()
                    }
                    "unlit" -> while (nextElement()) {
                        unlit.add(tile(string()))
                    }
                    "items" -> while (nextElement()) {
                        var x = 0
                        var y = 0
                        var level = 0
                        var id = ""
                        var amount = 1
                        var charges = 0
                        while (nextEntry()) {
                            when (val itemKey = key()) {
                                "x" -> x = int()
                                "y" -> y = int()
                                "level" -> level = int()
                                "id" -> id = string()
                                "amount" -> amount = int()
                                "charges" -> charges = int()
                                else -> throw IllegalArgumentException("Unexpected house item key: '$itemKey' ${exception()}")
                            }
                        }
                        items.add(FloorItem(Tile(x, y, level), id, amount, charges = charges))
                    }
                    else -> throw IllegalArgumentException("Unexpected house key: '$key' ${exception()}")
                }
            }
        }
        for (piece in house.pieces) {
            val built = furniture[piece.tile]
            if (built != null) {
                build(house, piece, built)
            }
            if (piece.tile in unlit) {
                light(house, piece, false)
            }
        }
        val owner = house.owner ?: return
        val name = accounts.getByAccount(owner)?.displayName ?: owner
        house.ownerName = name
        for (item in items) {
            FloorItems.add(item.tile, item.id, item.amount, revealTicks = FloorItems.NEVER, disappearTicks = FloorItems.NEVER, charges = item.charges, owner = name)
        }
    }

    /**
     * Forget every owner, for tests
     */
    fun clear() {
        for (house in all()) {
            house.clearOwner()
            house.furniture.clear()
            house.unlit.clear()
        }
    }
}
