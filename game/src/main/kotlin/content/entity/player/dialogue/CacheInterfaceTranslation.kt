package content.entity.player.dialogue

import world.gregs.voidps.cache.definition.data.InterfaceDefinition
import world.gregs.voidps.engine.Script
import world.gregs.voidps.engine.client.sendScript
import world.gregs.voidps.engine.client.ui.close
import world.gregs.voidps.engine.client.ui.open
import world.gregs.voidps.engine.data.config.TableDefinition
import world.gregs.voidps.engine.data.definition.InterfaceDefinitions
import world.gregs.voidps.engine.data.definition.Tables
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.network.login.protocol.encode.interfaceText

/**
 * Portuguese for interface text the client draws from the cache.
 *
 * Fixed labels no script writes ("Descrição", "Parabéns!", "Aceitar"...) are sent when any interface with
 * `interface_pt_br` label rows opens. The Task System and level up pop-up scripts were changed by
 * `CacheTranslationPatch` (cache module) to read the `portuguese_dialogue` varc themselves, so the server
 * only redraws the task tab when the language changes.
 */
class CacheInterfaceTranslation : Script {

    init {
        interfaceOpened("*") { id ->
            if (get("portuguese_dialogue", false)) {
                sendLabels(id, portuguese = true)
            }
        }

        playerSpawn {
            // The tab can be drawn before the varc arrives at login
            if (get("portuguese_dialogue", false) && interfaces.contains("task_system")) {
                redrawTaskSystem()
            }
        }

        variableSet("portuguese_dialogue") { _, _, to ->
            if (interfaces.contains("task_list")) {
                close("task_list")
                open("task_list")
            }
            if (interfaces.contains("task_system")) {
                sendLabels("task_system", portuguese = to == true)
                redrawTaskSystem()
            }
        }
    }

    /** Sends the label rows of interface [id], in Portuguese or back to the cache's English */
    private fun Player.sendLabels(id: String, portuguese: Boolean) {
        val interfaceId = InterfaceDefinitions.getOrNull(id)?.id ?: return
        for (label in labels()[interfaceId] ?: return) {
            client?.interfaceText(InterfaceDefinition.pack(interfaceId, label.component), if (portuguese) label.portuguese else label.english)
        }
    }

    private fun Player.redrawTaskSystem() {
        sendScript("task_system_populate")
        sendScript("task_system_progress")
    }
}

private class Label(val component: Int, val english: String, val portuguese: String)

private var labelTable: TableDefinition? = null
private var labelIndex: Map<Int, List<Label>> = emptyMap()

/** Label rows of `interface_pt_br` by interface id, rebuilt when the table is reloaded */
private fun labels(): Map<Int, List<Label>> {
    val table = Tables.getOrNull("interface_pt_br") ?: return emptyMap()
    if (table !== labelTable) {
        labelIndex = table.rows()
            .filter { it.int("interface") != 0 && it.string("pt") != it.string("en") }
            .groupBy({ it.int("interface") }, { Label(it.int("component"), it.string("en"), it.string("pt")) })
        labelTable = table
    }
    return labelIndex
}
