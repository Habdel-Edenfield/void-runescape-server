package content.entity.player.dialogue

import world.gregs.voidps.engine.Script
import world.gregs.voidps.engine.client.command.playerCommand
import world.gregs.voidps.engine.client.message
import world.gregs.voidps.engine.data.config.RowDefinition
import world.gregs.voidps.engine.data.definition.Rows
import world.gregs.voidps.engine.data.definition.Tables
import world.gregs.voidps.engine.entity.character.player.Player

/**
 * Brazilian Portuguese text for players who turn it on with `::portugues`.
 * Anything without a translation is left in English.
 */
class Translation : Script {

    init {
        playerCommand("portugues", desc = "Toggle Brazilian Portuguese dialogue") {
            if (toggle("portuguese_dialogue")) {
                message("Diálogos em português ativados.")
            } else {
                message("Portuguese dialogue disabled.")
            }
        }
    }
}

/**
 * The `dialogue_pt_br` translation of [text], or [text] unchanged when the player hasn't
 * enabled Portuguese or the line hasn't been translated yet.
 */
fun Player.translate(text: String): String {
    if (!get("portuguese_dialogue", false)) {
        return text
    }
    val row = Tables.get("dialogue_pt_br").rows().firstOrNull { it.string("en") == text } ?: return text
    return row.string("pt")
}

/**
 * The `pt_br` copy of [row] (e.g. `tutorial_island_pt_br.stage_0` for `tutorial_island.stage_0`)
 * when the player has enabled Portuguese and it exists, otherwise [row].
 */
fun Player.translate(row: RowDefinition): RowDefinition {
    if (!get("portuguese_dialogue", false)) {
        return row
    }
    val table = row.stringId.substringBeforeLast('.')
    return Rows.getOrNull("${table}_pt_br.${row.rowId}") ?: row
}
