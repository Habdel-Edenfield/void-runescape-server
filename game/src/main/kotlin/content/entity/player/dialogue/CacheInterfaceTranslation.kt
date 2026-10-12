package content.entity.player.dialogue

import world.gregs.voidps.cache.definition.data.InterfaceDefinition
import world.gregs.voidps.engine.Script
import world.gregs.voidps.engine.client.sendScript
import world.gregs.voidps.engine.client.ui.close
import world.gregs.voidps.engine.client.ui.open
import world.gregs.voidps.engine.data.definition.InterfaceDefinitions
import world.gregs.voidps.engine.data.definition.Tables
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.network.login.protocol.encode.interfaceText

/**
 * Portuguese for the interfaces the client draws from the cache: the Task System (task list, sidebar tab
 * and completion pop-up) and the level up pop-up.
 *
 * `CacheTranslationPatch` (cache module) changed their scripts to read the `portuguese_dialogue` varc,
 * so the server only sends the fixed labels no script writes and redraws the task tab when the language changes.
 */
class CacheInterfaceTranslation : Script {

    init {
        interfaceOpened("task_list,task_system,task_popup,skill_level_details") { id ->
            if (get("portuguese_dialogue", false)) {
                sendTaskLabels(id, portuguese = true)
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
                sendTaskLabels("task_system", portuguese = to == true)
                redrawTaskSystem()
            }
        }
    }

    /** Sends the `interface_pt_br` label rows of interface [id], in Portuguese or back to the cache's English */
    private fun Player.sendTaskLabels(id: String, portuguese: Boolean) {
        val table = Tables.getOrNull("interface_pt_br") ?: return
        val interfaceId = InterfaceDefinitions.get(id).id
        for (row in table.rows()) {
            if (row.int("interface") != interfaceId || row.string("pt") == row.string("en")) {
                continue
            }
            client?.interfaceText(InterfaceDefinition.pack(interfaceId, row.int("component")), row.string(if (portuguese) "pt" else "en"))
        }
    }

    private fun Player.redrawTaskSystem() {
        sendScript("task_system_populate")
        sendScript("task_system_progress")
    }
}
