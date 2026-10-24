package world.gregs.voidps.tools.cadeias

import world.gregs.config.Config
import world.gregs.voidps.cache.Cache
import world.gregs.voidps.cache.FileCache
import world.gregs.voidps.cache.definition.Params
import world.gregs.voidps.cache.definition.decoder.EnumDecoder
import world.gregs.voidps.cache.definition.decoder.ItemDecoderFull
import java.io.File

/**
 * Exporta, para cada item que o Void conhece pelo nome (`*.items.toml`), o que só existe no cache:
 * o nome mostrado no jogo, os bônus de equipamento (os mesmos parâmetros que a janela de equipamento lê),
 * a velocidade de ataque e os ingredientes das bolsas de Summoning.
 *
 * Uso: `CadeiasExport <pasta do cache> <pasta data> <itens.json de saída>`.
 * Só lê; não grava nada no cache nem em `data/`.
 */
object CadeiasExport {

    /** Os mesmos nomes e a mesma ordem de `EquipBonuses.names` no módulo game. */
    private val BONUSES = listOf(
        "stab_attack", "slash_attack", "crush_attack", "magic_attack", "range_attack",
        "stab_defence", "slash_defence", "crush_defence", "magic_defence", "range_defence", "summoning_defence",
        "absorb_melee", "absorb_magic", "absorb_range",
        "strength", "ranged_strength", "prayer_bonus", "magic_damage",
    )

    /** Os parâmetros que `SummoningCrafting` lê para infundir uma bolsa. */
    private val SUMMONING = listOf(
        "summoning_pouch_level",
        "summoning_pouch_id", "summoning_pouch_amount",
        "summoning_shard_id", "summoning_shard_amount",
        "summoning_charm_id", "summoning_charm_amount",
        "summoning_pouch_req_item_id_1", "summoning_pouch_req_item_amount_1",
        "summoning_pouch_req_item_id_2", "summoning_pouch_req_item_amount_2",
    )

    @JvmStatic
    fun main(args: Array<String>) {
        val cacheDir = args.getOrElse(0) { "./data/cache/" }
        val dataDir = File(args.getOrElse(1) { "./data/" })
        val output = File(args.getOrElse(2) { "./temp/cadeias/itens.json" })

        val cache: Cache = FileCache(cacheDir)
        val decoder = ItemDecoderFull()
        val definitions = decoder.load(cache)
        // Notas e empréstimos são gerados na ordem dos ids; reaplica agora que todos os moldes foram lidos
        for (definition in definitions) {
            decoder.changeValues(definitions, definition)
        }
        val enums = EnumDecoder().load(cache)
        val enumIds = enumIds(dataDir)
        val pouchLevels = enums.getOrNull(enumIds["summoning_pouch_levels"] ?: -1)?.map ?: emptyMap()
        val pouchScrolls = enums.getOrNull(enumIds["summoning_scroll_ids_2"] ?: -1)?.map ?: emptyMap()

        val ids = stringIds(dataDir)
        val byId = HashMap<Int, String>(ids.size)
        for ((sid, id) in ids) {
            byId.putIfAbsent(id, sid)
        }

        // Depuração: um quarto argumento com o nome de um item imprime todos os parâmetros dele
        if (args.size > 3) {
            val id = ids[args[3]] ?: -1
            println("$id ${definitions.getOrNull(id)?.name}: ${definitions.getOrNull(id)?.params}")
        }
        val sb = StringBuilder(ids.size * 160)
        sb.append("[\n")
        var first = true
        for ((sid, id) in ids) {
            val def = definitions.getOrNull(id) ?: continue
            if (!first) sb.append(",\n")
            first = false
            sb.append("{\"sid\":").append(json(sid))
            sb.append(",\"id\":").append(id)
            sb.append(",\"name\":").append(json(def.name))
            sb.append(",\"members\":").append(def.members)
            sb.append(",\"stackable\":").append(def.stackable == 1)
            sb.append(",\"noted\":").append(def.notedTemplateId != -1)
            sb.append(",\"equip\":").append(def.primaryMaleModel != -1 || def.primaryFemaleModel != -1)
            val params = def.params
            if (params != null) {
                val bonus = BONUSES.mapNotNull { name -> (params[Params.id(name)] as? Number)?.let { name to it } }
                if (bonus.isNotEmpty()) {
                    sb.append(",\"bonus\":{")
                    sb.append(bonus.joinToString(",") { (name, value) -> "${json(name)}:$value" })
                    sb.append("}")
                }
                (params[Params.id("attack_speed")] as? Int)?.let { sb.append(",\"attack_speed\":").append(it) }
                val pouch = SUMMONING.mapNotNull { name -> (params[Params.id(name)] as? Int)?.let { name to it } }.toMap()
                if (pouch.containsKey("summoning_shard_id")) {
                    sb.append(",\"pouch\":{")
                    sb.append("\"level\":").append(pouch["summoning_pouch_level"] ?: (pouchLevels[id] as? Int) ?: 1)
                    sb.append(",\"ingredients\":[")
                    val parts = mutableListOf<String>()
                    fun ingredient(idKey: String, amountKey: String) {
                        val itemId = pouch[idKey] ?: return
                        val amount = pouch[amountKey] ?: return
                        if (itemId <= 0 || amount <= 0) return
                        parts.add("[${json(byId[itemId] ?: "item_$itemId")},$amount]")
                    }
                    ingredient("summoning_pouch_id", "summoning_pouch_amount")
                    ingredient("summoning_shard_id", "summoning_shard_amount")
                    ingredient("summoning_charm_id", "summoning_charm_amount")
                    ingredient("summoning_pouch_req_item_id_1", "summoning_pouch_req_item_amount_1")
                    ingredient("summoning_pouch_req_item_id_2", "summoning_pouch_req_item_amount_2")
                    sb.append(parts.joinToString(","))
                    sb.append("]")
                    val scroll = (pouchScrolls[id] as? Int)?.let { byId[it] }
                    if (scroll != null) sb.append(",\"scroll\":").append(json(scroll))
                    sb.append("}")
                }
            }
            sb.append("}")
        }
        sb.append("\n]\n")
        output.parentFile?.mkdirs()
        output.writeText(sb.toString())
        println("CadeiasExport: ${ids.size} itens em ${output.absolutePath}")
    }

    /** Nome do item no Void -> id no cache, lendo todos os `*.items.toml` de `data/`. */
    private fun stringIds(dataDir: File): Map<String, Int> {
        val ids = LinkedHashMap<String, Int>(18_000)
        val files = dataDir.walkTopDown().filter { it.isFile && it.name.endsWith(".items.toml") }.sortedBy { it.path }
        for (file in files) {
            Config.fileReader(file, 256) {
                while (nextSection()) {
                    val sid = section()
                    var id = -1
                    while (nextPair()) {
                        if (key() == "id") {
                            id = int()
                        } else {
                            value()
                        }
                    }
                    if (id != -1 && !ids.containsKey(sid)) {
                        ids[sid] = id
                    }
                }
            }
        }
        return ids
    }

    /** Nome do enum no Void -> id no cache, lendo todos os `*.enums.toml` de `data/`. */
    private fun enumIds(dataDir: File): Map<String, Int> {
        val ids = HashMap<String, Int>()
        val files = dataDir.walkTopDown().filter { it.isFile && it.name.endsWith(".enums.toml") }.sortedBy { it.path }
        for (file in files) {
            Config.fileReader(file, 256) {
                while (nextSection()) {
                    val name = section()
                    while (nextPair()) {
                        if (key() == "id") {
                            ids[name] = int()
                        } else {
                            value()
                        }
                    }
                }
            }
        }
        return ids
    }

    private fun json(text: String): String {
        val sb = StringBuilder(text.length + 2)
        sb.append('"')
        for (c in text) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
