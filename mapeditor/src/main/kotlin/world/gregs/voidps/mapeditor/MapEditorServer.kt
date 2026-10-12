package world.gregs.voidps.mapeditor

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import world.gregs.voidps.cache.Index
import world.gregs.voidps.cache.definition.data.MapDefinition
import world.gregs.voidps.cache.definition.data.MapObject
import world.gregs.voidps.cache.definition.data.MapTile
import world.gregs.voidps.type.Region
import java.io.File
import java.net.InetSocketAddress
import java.util.Base64

/**
 * Local http api for the 3D map editor (void-editor-de-mapas).
 *
 * Edits are written to a working copy of the cache (mapeditor/work/cache/), never to data/cache/.
 * The working copy is made from data/cache/ the first time the server starts.
 *
 * POST /void-api/region/<id>/edit    {"tiles": [...], "objects": [...]} changed tiles and/or the full object list
 * POST /void-api/region/<id>/revert  copies the region back from data/cache/
 * GET  /void-api/status
 *
 * Responses carry the region's new terrain and location archives, base64 encoded.
 */
class MapEditorServer(
    private val livePath: String,
    private val work: RegionStore,
    private val workPath: String,
) {
    private val mapper: ObjectMapper = jacksonObjectMapper()

    // Only opened to revert regions, and only read from
    private val live: RegionStore by lazy { RegionStore(livePath) }

    data class TileEdit(
        val level: Int,
        val x: Int,
        val y: Int,
        val height: Int,
        val overlay: Int,
        val overlayShape: Int,
        val overlayRotation: Int,
        val settings: Int,
        val underlay: Int,
    )

    data class ObjectEdit(val id: Int, val x: Int, val y: Int, val level: Int, val shape: Int, val rotation: Int)

    data class RegionEdit(val tiles: List<TileEdit> = emptyList(), val objects: List<ObjectEdit>? = null)

    data class RegionData(val region: Int, val terrain: String, val locations: String)

    fun start(port: Int): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        server.createContext("/void-api/") { exchange ->
            try {
                handle(exchange)
            } catch (e: IllegalArgumentException) {
                respond(exchange, 400, mapOf("error" to e.message))
            } catch (e: Exception) {
                e.printStackTrace()
                respond(exchange, 500, mapOf("error" to e.toString()))
            }
        }
        server.start()
        return server
    }

    private fun handle(exchange: HttpExchange) {
        val parts = exchange.requestURI.path.removePrefix("/void-api/").split("/")
        when {
            parts == listOf("status") -> respond(exchange, 200, mapOf("work" to workPath))
            parts.size == 3 && parts[0] == "region" && exchange.requestMethod == "POST" -> {
                val region = Region(parts[1].toIntOrNull() ?: throw IllegalArgumentException("Invalid region '${parts[1]}'"))
                when (parts[2]) {
                    "edit" -> respond(exchange, 200, synchronized(this) { edit(region, mapper.readValue(exchange.requestBody)) })
                    "revert" -> respond(exchange, 200, synchronized(this) { revert(region) })
                    else -> respond(exchange, 404, mapOf("error" to "Unknown action '${parts[2]}'"))
                }
            }
            else -> respond(exchange, 404, mapOf("error" to "Not found"))
        }
    }

    fun edit(region: Region, edit: RegionEdit): RegionData {
        val definition = work.load(region) ?: throw IllegalArgumentException("Region ${region.id} has no map")
        apply(definition, edit)
        work.write(definition)
        println("Edited region ${region.id}: ${edit.tiles.size} tiles${edit.objects?.let { ", ${it.size} objects" } ?: ""}")
        return data(region)
    }

    fun revert(region: Region): RegionData {
        val original = live.load(region) ?: throw IllegalArgumentException("Region ${region.id} has no map in the server cache")
        work.write(original)
        println("Reverted region ${region.id}")
        return data(region)
    }

    private fun data(region: Region): RegionData {
        val encoder = Base64.getEncoder()
        val terrain = work.cache.data(Index.MAPS, RegionStore.terrain(region)) ?: ByteArray(0)
        val locations = work.cache.data(Index.MAPS, RegionStore.locations(region)) ?: ByteArray(0)
        return RegionData(region.id, encoder.encodeToString(terrain), encoder.encodeToString(locations))
    }

    private fun respond(exchange: HttpExchange, code: Int, body: Any) {
        val bytes = mapper.writeValueAsBytes(body)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    companion object {
        fun apply(definition: MapDefinition, edit: RegionEdit) {
            for (tile in edit.tiles) {
                require(tile.level in 0 until 4 && tile.x in 0 until 64 && tile.y in 0 until 64) { "Tile $tile is outside the region" }
                require(tile.overlayShape in 0 until 12 && tile.overlayRotation in 0 until 4) { "Invalid overlay shape or rotation in $tile" }
                require(listOf(tile.height, tile.overlay, tile.settings, tile.underlay).all { it in 0..255 }) { "Tile values must be 0-255: $tile" }
                require(tile.settings <= 32 && tile.underlay <= 174) { "Settings must be 0-32 and underlay 0-174: $tile" }
                val opcode = if (tile.overlay != 0 || tile.overlayShape != 0 || tile.overlayRotation != 0) 2 + tile.overlayShape * 4 + tile.overlayRotation else 0
                definition.setTile(tile.x, tile.y, tile.level, MapTile(tile.height, opcode, tile.overlay, tile.overlayShape, tile.overlayRotation, tile.settings, tile.underlay))
            }
            val objects = edit.objects ?: return
            definition.objects.clear()
            for (obj in objects) {
                require(obj.level in 0 until 4 && obj.x in 0 until 64 && obj.y in 0 until 64) { "Object $obj is outside the region" }
                require(obj.shape in 0..22 && obj.rotation in 0 until 4 && obj.id >= 0) { "Invalid object $obj" }
                definition.objects.add(MapObject(obj.id, obj.x, obj.y, obj.level, obj.shape, obj.rotation))
            }
        }

        @JvmStatic
        fun main(args: Array<String>) {
            val port = args.getOrNull(0)?.toIntOrNull() ?: 8391
            val livePath = "./data/cache/"
            val workPath = "./mapeditor/work/cache/"
            val work = File(workPath)
            if (!work.resolve("main_file_cache.dat2").exists()) {
                println("Copying $livePath to $workPath...")
                File(livePath).canonicalFile.copyRecursively(work, overwrite = true)
            }
            MapEditorServer(livePath, RegionStore(workPath), work.canonicalPath).start(port)
            println("Map editor api on http://localhost:$port/void-api/ writing to ${work.canonicalPath}")
        }
    }
}
