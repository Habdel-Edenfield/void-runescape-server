package content.social.house

import content.entity.player.dialogue.type.choice
import content.skill.construction.House.Companion.freeBuild
import world.gregs.voidps.engine.Script
import world.gregs.voidps.engine.client.message
import world.gregs.voidps.engine.client.ui.close
import world.gregs.voidps.engine.client.ui.open
import world.gregs.voidps.engine.data.config.RowDefinition
import world.gregs.voidps.engine.data.definition.ItemDefinitions
import world.gregs.voidps.engine.data.definition.Rows
import world.gregs.voidps.engine.data.definition.Tables
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.entity.character.player.skill.exp.exp
import world.gregs.voidps.engine.entity.character.player.skill.level.Level.has
import world.gregs.voidps.engine.entity.item.Item
import world.gregs.voidps.engine.inv.contains
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.engine.inv.remove
import world.gregs.voidps.engine.inv.transact.operation.ClearItem.clear
import world.gregs.voidps.type.Tile

/**
 * Furniture of a [WorldHouse]: the owner swaps a piece for Construction furniture with the same window, levels,
 * materials and experience as in a player-owned house, and removes it again; anyone inside lights and puts out fires.
 */
class WorldHouseFurniture : Script {

    init {
        for (piece in WorldHouses.pieces().distinctBy { it.ownId }) {
            if (!WorldHouses.hasObjects(piece)) {
                continue
            }
            if (piece.light) {
                objectOperate("Extinguish", piece.ownId) { (target) ->
                    val house = WorldHouses.get(piece.house) ?: return@objectOperate
                    val at = WorldHouses.piece(target.tile) ?: return@objectOperate
                    if (house.canEnter(this)) {
                        WorldHouses.light(house, at, false)
                        message("You put out the fire.")
                    }
                }
                objectOperate("Light", piece.unlitId) { (target) ->
                    val house = WorldHouses.get(piece.house) ?: return@objectOperate
                    val at = WorldHouses.piece(target.tile) ?: return@objectOperate
                    if (house.canEnter(this)) {
                        WorldHouses.light(house, at, true)
                        message("You light the fire.")
                    }
                }
            } else {
                objectOperate("Build", piece.buildId) { (target) ->
                    val house = WorldHouses.get(piece.house) ?: return@objectOperate
                    val at = WorldHouses.piece(target.tile) ?: return@objectOperate
                    if (house.isOwner(this)) {
                        buildMenu(at)
                    }
                }
            }
        }

        // Construction furniture all has "Remove", which Construction's own handler ignores outside its houses
        objectOperate("Remove") { (target) ->
            val piece = WorldHouses.piece(target.tile) ?: return@objectOperate
            val house = WorldHouses.get(piece.house) ?: return@objectOperate
            if (piece.tile !in house.furniture) {
                return@objectOperate
            }
            if (!house.isOwner(this)) {
                message("Only the owner can change the furniture.")
                return@objectOperate
            }
            choice("Really remove it?") {
                option("Yes") {
                    if (house.isOwner(this)) {
                        WorldHouses.removeFurniture(house, piece)
                        anim("construction_remove")
                    }
                }
                option("No")
            }
        }

        interfaceOption("Build", "furniture_creation:items") { (item) ->
            val tile: Int = get("world_house_piece") ?: return@interfaceOption
            close("furniture_creation")
            val piece = WorldHouses.piece(Tile(tile)) ?: return@interfaceOption
            build(piece, item.id)
        }

        interfaceClosed("furniture_creation") {
            clear("world_house_piece")
        }
    }

    private fun Player.buildMenu(piece: WorldHousePiece) {
        val furniture = Tables.itemList("house_hotspots.${piece.hotspot}.furniture")
        inventories.inventory("poh_furniture_menu_inv").transaction {
            clear()
            for ((index, id) in furniture.withIndex()) {
                // Menu items are listed down each column
                set(index % 4 * 2 + index / 4, Item(id))
            }
        }
        set("world_house_piece", piece.tile.id)
        open("furniture_creation")
        for (slot in 1..7) {
            sendMenuSlot(slot, furniture.getOrNull(slot - 1))
        }
    }

    private fun Player.sendMenuSlot(slot: Int, furniture: String?) {
        val row = if (furniture == null) null else Rows.get("house_furniture.$furniture")
        val materials = if (row == null) emptyList() else materials(row)
        interfaces.sendText("furniture_creation", "name_$slot", if (furniture == null) "" else ItemDefinitions.get(furniture).name)
        for (line in 1..4) {
            val item = materials.getOrNull(line - 1)
            interfaces.sendText("furniture_creation", "material_${slot}_$line", if (item == null) "" else "${item.amount} ${item.def.name}")
        }
        interfaces.sendText("furniture_creation", "level_$slot", if (row == null) "" else "Level ${row.int("level")}")
        set("furniture_creation_hide_cross_$slot", row == null || freeBuild || (has(Skill.Construction, row.int("level")) && inventory.contains(materials)))
    }

    private fun Player.build(piece: WorldHousePiece, furniture: String) {
        val house = WorldHouses.get(piece.house) ?: return
        if (!house.isOwner(this) || furniture !in Tables.itemList("house_hotspots.${piece.hotspot}.furniture")) {
            return
        }
        val row = Rows.getOrNull("house_furniture.$furniture") ?: return
        if (!canBuild(row)) {
            return
        }
        if (!freeBuild && !inventory.remove(materials(row))) {
            message("You don't have the right materials.")
            return
        }
        if (!WorldHouses.build(house, piece, furniture)) {
            return
        }
        anim("construction_build")
        // Free building doesn't give experience so it can't be used for training
        if (!freeBuild) {
            exp(Skill.Construction, row.int("xp") / 10.0)
        }
    }

    private fun Player.canBuild(row: RowDefinition): Boolean {
        if (freeBuild) {
            return true
        }
        if (!has(Skill.Construction, row.int("level"), message = true)) {
            return false
        }
        if (!inventory.contains("hammer", "saw")) {
            message("You need a hammer and saw to build furniture.")
            return false
        }
        if (!inventory.contains(materials(row))) {
            message("You don't have the right materials.")
            return false
        }
        return true
    }

    /**
     * Materials needed to build [row], using the first type of nails the player has enough of
     */
    private fun Player.materials(row: RowDefinition): List<Item> {
        val materials = row.itemList("materials").zip(row.intList("amounts")) { id, amount -> Item(id, amount) }
        val nails = row.int("nails")
        if (nails == 0) {
            return materials
        }
        val type = nailTypes.firstOrNull { inventory.contains(it, nails) } ?: nailTypes.first()
        return materials + Item(type, nails)
    }

    companion object {
        private val nailTypes = listOf("bronze_nails", "iron_nails", "steel_nails", "black_nails", "mithril_nails", "adamant_nails", "rune_nails")
    }
}
