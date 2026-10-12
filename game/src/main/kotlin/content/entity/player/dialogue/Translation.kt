package content.entity.player.dialogue

import com.github.michaelbull.logging.InlineLogger
import world.gregs.voidps.engine.Script
import world.gregs.voidps.engine.client.command.playerCommand
import world.gregs.voidps.engine.client.message
import world.gregs.voidps.engine.client.messageTranslator
import world.gregs.voidps.engine.data.Settings
import world.gregs.voidps.engine.data.config.RowDefinition
import world.gregs.voidps.engine.data.config.TableDefinition
import world.gregs.voidps.engine.data.definition.Rows
import world.gregs.voidps.engine.data.definition.Tables
import world.gregs.voidps.engine.entity.character.player.Player

/**
 * Brazilian Portuguese text for players who turn it on with `::portugues`.
 * Anything without a translation is left in English.
 */
class Translation : Script {

    init {
        messageTranslator = { player, text -> player.translate(text) }

        playerCommand("portugues", desc = "Toggle Brazilian Portuguese dialogue") {
            if (toggle("portuguese_dialogue")) {
                message("Portuguese dialogue enabled.")
            } else {
                message("Portuguese dialogue disabled.")
            }
        }
    }
}

/**
 * The `dialogue_pt_br` translation of [text], or [text] unchanged when the player hasn't
 * enabled Portuguese or the line hasn't been translated yet.
 * `%server%` in either column stands in for the `server.name` setting, and `%1%`, `%2%`...
 * in `en` match any text which is copied into the same placeholder in `pt`.
 */
fun Player.translate(text: String): String {
    if (!get("portuguese_dialogue", false)) {
        return text
    }
    val index = index()
    // Exact lines win over placeholders, so "This rock contains no ore." isn't caught by "This rock contains %1%."
    val exact = index.exact[text]
    if (exact != null) {
        return exact
    }
    for ((english, portuguese) in index.placeholders) {
        val match = english.matchEntire(text) ?: continue
        return placeholder.replace(portuguese) { match.groups["p${it.groupValues[1]}"]?.value ?: it.value }
    }
    missingTranslation(text)
    if (text.isNotBlank() && missing.add(text)) {
        logger.info { "Missing pt_br translation: $text" }
    }
    return text
}

/** Called with every untranslated text shown to a Portuguese player, e.g. so tests can list them */
var missingTranslation: (String) -> Unit = {}

/** Lookups built once per table load, as scanning every row for each line gets slow as the table grows */
private class TranslationIndex(
    val table: TableDefinition,
    val serverName: String,
    val exact: Map<String, String>,
    val placeholders: List<Pair<Regex, String>>,
)

private var index: TranslationIndex? = null

private fun index(): TranslationIndex {
    val table = Tables.get("dialogue_pt_br")
    val name = Settings["server.name"]
    val current = index
    if (current != null && current.table === table && current.serverName == name) {
        return current
    }
    val exact = HashMap<String, String>()
    val placeholders = mutableListOf<Pair<Regex, String>>()
    for (row in table.rows()) {
        val english = serverName(row.string("en"))
        val portuguese = serverName(row.string("pt"))
        if (placeholder.containsMatchIn(english)) {
            placeholders.add(pattern(english) to portuguese)
        } else {
            exact[english] = portuguese
        }
    }
    return TranslationIndex(table, name, exact, placeholders).also { index = it }
}

private val logger = InlineLogger("Translation")

/** Untranslated text already logged, so playing through content lists everything left to translate once */
private val missing = HashSet<String>()

private val placeholder = Regex("%(\\d+)%")

private val patterns = HashMap<String, Regex>()

/** [english] as a regex with each `%n%` placeholder captured as group `pn` */
private fun pattern(english: String): Regex = patterns.getOrPut(english) {
    val regex = StringBuilder()
    var last = 0
    for (match in placeholder.findAll(english)) {
        regex.append(Regex.escape(english.substring(last, match.range.first)))
        regex.append("(?<p${match.groupValues[1]}>.*?)")
        last = match.range.last + 1
    }
    regex.append(Regex.escape(english.substring(last)))
    Regex(regex.toString())
}

private fun serverName(text: String): String = text.replace("%server%", Settings["server.name"])

/** The dialogue box prompt comes from the cache, so is only replaced once translated */
fun Player.sendContinue(id: String) = sendTranslated(id, "continue", "Click here to continue")

/** Sends the translation of [text] to a [component] the client fills in itself, only when it differs */
fun Player.sendTranslated(id: String, component: String, text: String) {
    val translated = translate(text)
    if (translated != text) {
        interfaces.sendText(id, component, translated)
    }
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
    val translated = Rows.getOrNull("${table}_pt_br.${row.rowId}")
    if (translated == null && missing.add(row.stringId)) {
        logger.info { "Missing pt_br translation: row ${row.stringId}" }
    }
    return translated ?: row
}
