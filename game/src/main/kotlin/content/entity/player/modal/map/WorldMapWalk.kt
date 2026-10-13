package content.entity.player.modal.map

import world.gregs.voidps.engine.Script
import world.gregs.voidps.engine.client.message
import world.gregs.voidps.engine.client.ui.hasOpen
import world.gregs.voidps.engine.entity.character.mode.EmptyMode
import world.gregs.voidps.engine.timer.Timer
import world.gregs.voidps.type.Tile

/**
 * Setting a marker on the world map walks the player to it.
 * Clicking anywhere else, or clearing the marker, stops the walk.
 */
class WorldMapWalk : Script {

    init {
        timerStart(MarkerWalk.OPENING) { 1 }

        // Carries on to the marker once a door on the way is open, unless the player went off somewhere else
        timerTick(MarkerWalk.OPENING) {
            val opening: MarkerWalk.Opening = get(MarkerWalk.OPENING) ?: return@timerTick Timer.CANCEL
            when {
                mode === opening.mode -> Timer.CONTINUE
                mode == EmptyMode -> {
                    val walk = MarkerWalk(this, opening.walk.marker, opening.walk.tried)
                    if (walk.plan()) {
                        mode = walk
                    }
                    Timer.CANCEL
                }
                else -> Timer.CANCEL
            }
        }

        timerStop(MarkerWalk.OPENING) {
            clear(MarkerWalk.OPENING)
        }

        variableSet("world_map_marker_custom") { _, _, to ->
            val marker = to as? Int ?: 0
            if (marker == 0) {
                softTimers.stop(MarkerWalk.OPENING)
                if (mode is MarkerWalk) {
                    steps.clear()
                    mode = EmptyMode
                }
                return@variableSet
            }
            if (viewport == null) {
                return@variableSet
            }
            softTimers.stop(MarkerWalk.OPENING)
            val walk = MarkerWalk(this, Tile(marker))
            if (!walk.plan()) {
                message("You can't find a way to walk to your marker.")
                return@variableSet
            }
            mode = walk
            if (hasOpen("world_map")) {
                softTimers.start("world_map_check")
            }
        }
    }
}
