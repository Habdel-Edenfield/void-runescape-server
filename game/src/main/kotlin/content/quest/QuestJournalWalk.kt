package content.quest

import content.achievement.TaskWalk
import world.gregs.voidps.cache.translation.TaskWalkHint
import world.gregs.voidps.engine.Script
import world.gregs.voidps.engine.client.sendInterfaceSettings
import world.gregs.voidps.engine.client.sendScript
import world.gregs.voidps.engine.data.definition.InterfaceDefinitions
import world.gregs.voidps.engine.data.definition.Tables
import world.gregs.voidps.engine.entity.character.player.Player

/**
 * A quest journal line naming one of the quest's npcs (`quest_walks` table) walks the player there when clicked,
 * the same walk as the Task System's hints ([TaskWalk.walkTo]).
 * The journal lines have no option in the cache, so the server gives them one (a script the cache patch adds,
 * called by id as it's past the scripts the server names) and unlocks it only on those lines.
 */
class QuestJournalWalk : Script {

    init {
        interfaceOption("Walk", "quest_scroll:line*") {
            val line = it.component.removePrefix("line").toIntOrNull() ?: return@interfaceOption
            val row = get<Map<Int, String>>(LINES)?.get(line) ?: return@interfaceOption
            val path = "$TABLE.$row"
            val name = Tables.string(if (get("portuguese_dialogue", false)) "$path.pt" else "$path.name")
            TaskWalk.walkTo(this, Tables.npc("$path.npc"), Tables.tile("$path.tile"), name, "quest:$row")
        }
    }

    companion object {
        private const val TABLE = "quest_walks"
        private const val QUEST = "quest_journal_quest"

        // Line number to the table row it walks to, for the journal shown last
        private const val LINES = "quest_journal_walks"
        private const val SCROLL = "quest_scroll"

        // Interface settings unlocking the first option
        private const val FIRST_OPTION = 2

        private val maroon = "<maroon>([^<]*)".toRegex()

        /** Opens [quest]'s journal, remembering which quest it is for [mark] */
        fun open(player: Player, quest: String, open: () -> Unit) {
            player[QUEST] = quest
            try {
                open()
            } finally {
                player.clear(QUEST)
            }
        }

        /**
         * Gives the [shown] journal lines (line 1 onwards) which name one of the quest's npcs a walk option,
         * and takes it off the lines the journal shown before had.
         */
        fun mark(player: Player, shown: List<String>) {
            for (line in player.get<Map<Int, String>>(LINES)?.keys.orEmpty()) {
                val component = InterfaceDefinitions.getComponent(SCROLL, "line$line") ?: continue
                player.sendInterfaceSettings(component.id, -1, -1, 0)
            }
            player.clear(LINES)
            val quest: String = player[QUEST] ?: return
            val rows = Tables.getOrNull(TABLE)?.rows()?.filter { it.string("quest") == quest }.orEmpty()
            if (rows.isEmpty()) {
                return
            }
            val portuguese = player["portuguese_dialogue", false]
            val lines = mutableMapOf<Int, String>()
            for ((index, text) in shown.withIndex()) {
                // Names are coloured in the journal, in both languages
                val names = maroon.findAll(text).map { it.groupValues[1] }.toList()
                val row = rows.firstOrNull { row -> names.any { contains(it, row.string("text")) } } ?: continue
                val line = index + 1
                val component = InterfaceDefinitions.getComponent(SCROLL, "line$line") ?: continue
                val name = row.string(if (portuguese) "pt" else "name")
                player.sendScript(TaskWalkHint.QUEST_LINE_OP_ID, listOf(component.id, if (portuguese) "Andar até $name" else "Walk to $name"))
                player.sendInterfaceSettings(component.id, -1, -1, FIRST_OPTION)
                lines[line] = row.rowId
            }
            if (lines.isNotEmpty()) {
                player[LINES] = lines
            }
        }

        private fun contains(text: String, name: String) = Regex("\\b${Regex.escape(name)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }
}
