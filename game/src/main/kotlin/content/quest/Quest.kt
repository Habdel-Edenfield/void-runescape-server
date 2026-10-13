package content.quest

import content.entity.player.dialogue.translate
import content.entity.player.dialogue.translateLines
import world.gregs.voidps.engine.client.sendScript
import world.gregs.voidps.engine.client.ui.open
import world.gregs.voidps.engine.data.Settings
import world.gregs.voidps.engine.data.definition.VariableDefinitions
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.item.Item

val quests = setOf(
    // free
    "blood_pact",
    "unstable_foundations",
    "cooks_assistant",
    "demon_slayer",
    "dorics_quest",
    "goblin_diplomacy",
    "gunnars_ground",
    "the_restless_ghost",
    "rune_mysteries",
    "the_knights_sword",
    "witches_potion_miniquest",
    "prince_ali_rescue",
    "imp_catcher",
    "pirates_treasure",
    // members
    "biohazard",
    "creature_of_fenkenstrain",
    "druidic_ritual",
    "hand_in_the_sand",
    "in_search_of_the_myreque",
    "jungle_potion",
    "nature_spirit",
    "plague_city",
    "priest_in_peril",
    "rat_catchers",
    "rum_deal",
    "swan_song",
    "lost_city",
    "one_small_favour",
    "tears_of_guthix",
    "the_golem",
    "zogre_flesh_eaters",
    // mini-quests
    "enter_the_abyss",
)

fun Player.quest(name: String): String = this[name, "unstarted"]

fun Player.questStage(name: String): Int {
    val value = quest(name)
    val def = VariableDefinitions.get(name) ?: return 0
    return def.values.toInt(value)
}

fun questStage(name: String, stage: String): Int {
    val def = VariableDefinitions.get(name) ?: return 0
    return def.values.toInt(stage)
}

fun Player.questCompleted(name: String): Boolean {
    if (!quests.contains(name) && Settings["quests.requirements.skipMissing", false]) {
        return true
    }
    return quest(name).startsWith("completed")
}

fun Player.refreshQuestJournal() {
    sendScript("quest_journal_refresh")
}

private const val QUEST_SCROLL_ID = "quest_scroll"

/** [translated] false for lists of names and numbers (commands, kill counts) which have no sentences to translate */
fun Player.questJournal(name: String, lines: List<String>, translated: Boolean = true) {
    val shown = if (translated) translateJournal(lines) else lines
    journalShown(lines, shown)
    interfaces.open(QUEST_SCROLL_ID)
    sendScript("quest_journal_length", shown.size + 1)
    interfaces.sendText(QUEST_SCROLL_ID, "quest_name", name)
    interfaces.sendText(QUEST_SCROLL_ID, "line0", "")
    for (i in 0..301) {
        interfaces.sendText(QUEST_SCROLL_ID, "line${i + 1}", shown.getOrNull(i) ?: "")
    }
    QuestJournalWalk.mark(this, shown)
}

/** Called with a journal's English lines and the lines shown, e.g. so tests can measure them */
var journalShown: (english: List<String>, shown: List<String>) -> Unit = { _, _ -> }

private val tags = "<[^>]*>".toRegex()

/**
 * Journal sentences run over several lines, so each one is translated whole (lines joined by `<br>`)
 * and the Portuguese breaks its own lines. A sentence ends at a blank line, a line ending in . ! ? or :,
 * or before a line starting with a capital or a number (lists like "1 x Steel Sword" have no full stops),
 * unless the line before ends in a word that needs the next one ("in the", "Lumbridge cemetery.").
 */
fun Player.translateJournal(lines: List<String>): List<String> {
    if (!get("portuguese_dialogue", false)) {
        return lines
    }
    val result = mutableListOf<String>()
    val sentence = mutableListOf<String>()
    fun flush() {
        if (sentence.isNotEmpty()) {
            result.addAll(translateLines(sentence))
            sentence.clear()
        }
    }
    for (line in lines) {
        if (line.isBlank()) {
            flush()
            result.add(line)
            continue
        }
        val text = line.replace(tags, "").trim()
        val previous = sentence.lastOrNull()?.replace(tags, "")?.trim()
        if (previous != null && text.first().let { it.isUpperCase() || it.isDigit() } && previous.substringAfterLast(' ') !in joining) {
            flush()
        }
        sentence.add(line)
        if (text.lastOrNull() in sentenceEnds) {
            flush()
        }
    }
    flush()
    return result
}

private val sentenceEnds = setOf('.', '!', '?', ':')

/** Words which carry a sentence on to the next line even when it starts with a name */
private val joining = setOf("the", "a", "an", "to", "of", "in", "into", "on", "at", "by", "for", "from", "with", "and", "or", "but", "me", "my", "his", "her", "their", "is", "was", "named", "called", "near", "north", "south", "east", "west", "about", "as")

fun Player.questComplete(name: String, vararg lines: String, item: String = "") {
    questComplete(name, lines.toList(), if (item == "") Item.EMPTY else Item(item))
}

fun Player.questComplete(name: String, lines: List<String>, item: Item = Item.EMPTY) {
    open("quest_complete")
    interfaces.sendText("quest_complete", "quest_name", translate("You have completed $name!"))
    interfaces.sendText("quest_complete", "quest_points", get("quest_points", 0).toString())
    if (item != Item.EMPTY) {
        interfaces.sendItem("quest_complete", "item_slot", item)
    }
    // Rewards are translated as a whole, as a sentence often runs over two lines
    val rewards = if (lines.isEmpty()) lines else translate(lines.joinToString("<br>")).split("<br>")
    for (i in 0 until 8) {
        interfaces.sendText("quest_complete", "line${i + 1}", rewards.getOrNull(i) ?: "")
    }
}

fun Player.letterScroll(name: String, lines: List<String>) {
    if (!interfaces.open("letter_scroll")) {
        return
    }
    sendScript("message_scroll_max", lines.size + 1)
    interfaces.sendText("letter_scroll", "title", name)
    interfaces.sendText("letter_scroll", "line0", "")
    for (i in 0..87) {
        interfaces.sendText("letter_scroll", "line${i + 1}", lines.getOrNull(i) ?: "")
    }
}

fun Player.wiseOldManScroll(lines: List<String>) {
    if (!interfaces.open("wise_old_man_scroll")) {
        return
    }
    for (i in 0..16) {
        interfaces.sendText("wise_old_man_scroll", "line$i", lines.getOrNull(i) ?: "")
    }
}

fun Player.messageScroll(lines: List<String>, handwriting: Boolean = false) {
    val id = "message_scroll${if (handwriting) "_handwriting" else ""}"
    if (!interfaces.open(id)) {
        return
    }
    for (i in 0..if (handwriting) 10 else 14) {
        interfaces.sendText(id, "line$i", lines.getOrNull(i) ?: "")
    }
}
