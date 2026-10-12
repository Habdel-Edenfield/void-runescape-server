package world.gregs.voidps.mapeditor

import world.gregs.voidps.tools.map.render.WorldMapDumper

object RenderMinimap {
    @JvmStatic
    fun main(args: Array<String>) {
        WorldMapDumper.dump("./images/")
    }
}
