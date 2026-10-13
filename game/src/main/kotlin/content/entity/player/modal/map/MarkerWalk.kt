package content.entity.player.modal.map

import content.entity.obj.door.Door.isDiagonal
import content.entity.obj.door.Door.isDoor
import org.rsmod.game.pathfinder.PathFinder
import world.gregs.voidps.engine.client.instruction.handle.interactObject
import world.gregs.voidps.engine.entity.character.mode.Mode
import world.gregs.voidps.engine.entity.character.mode.move.Step
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.obj.GameObject
import world.gregs.voidps.engine.entity.obj.GameObjects
import world.gregs.voidps.engine.entity.obj.ObjectLayer
import world.gregs.voidps.engine.map.collision.Collisions
import world.gregs.voidps.engine.map.collision.GameObjectCollisionAdd
import world.gregs.voidps.engine.map.collision.GameObjectCollisionRemove
import world.gregs.voidps.type.Tile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import world.gregs.voidps.engine.entity.character.mode.move.Movement as MovementMode

/**
 * Walks a route longer than [Player.steps] can hold by queueing it [MAX_STEPS] turns at a time.
 * Plans again from where the player is when a step is blocked or a part of the way is walked.
 * Engine [MovementMode] is imported under another name so script metadata doesn't mistake it for content.entity.Movement.
 */
class MarkerWalk(
    override val character: Player,
    val marker: Tile,
    // Doors already tried on the way, left closed when planning again so a locked one isn't tried forever
    val tried: MutableSet<Int> = mutableSetOf(),
) : MovementMode(character) {
    private val waypoints = ArrayDeque<Tile>()
    private var stretchEnd = Tile.EMPTY
    private var plans = 0
    private var closest = Int.MAX_VALUE
    private var heading = Tile.EMPTY

    /**
     * @param closer only accept a route ending nearer the marker than the last one did,
     * so walking stops once the nearest reachable point is reached
     */
    fun plan(closer: Boolean = false): Boolean {
        if (plans++ > MAX_PLANS) {
            return false
        }
        val route = route(character.tile, marker, tried) ?: return false
        val distance = route.last().distanceTo(marker)
        if (closer && distance >= closest) {
            return false
        }
        closest = minOf(closest, distance)
        waypoints.clear()
        waypoints.addAll(route)
        queueStretch()
        return true
    }

    private fun queueStretch() {
        val stretch = List(minOf(MAX_STEPS, waypoints.size)) { waypoints.removeFirst() }
        stretchEnd = stretch.last()
        character.steps.queueSteps(stretch)
    }

    override fun getTarget(): Step? {
        val target = super.getTarget()
        if (target != null) {
            heading = Tile(target.x, target.y, target.level)
            return target
        }
        if (character.tile != stretchEnd) {
            // Blocked part way through, usually by a door the route goes through
            val door = doorAhead()
            if (door != null) {
                openDoor(door)
                return null
            }
            return if (plan()) super.getTarget() else null
        }
        if (waypoints.isNotEmpty()) {
            queueStretch()
            return super.getTarget()
        }
        if (character.tile.distanceTo(marker) > ARRIVED && plan(closer = true)) {
            // Only part of the way fitted in the search area
            return super.getTarget()
        }
        return null
    }

    /**
     * A closed door on the edge of the player's tile in the direction they were heading
     */
    private fun doorAhead(): GameObject? {
        val tile = character.tile
        val dx = (heading.x - tile.x).sign
        val dy = (heading.y - tile.y).sign
        for (candidate in listOf(tile, tile.add(dx, dy), tile.addX(dx), tile.addY(dy))) {
            val door = GameObjects.getLayer(candidate, ObjectLayer.WALL) ?: continue
            if (openable(door) && door.tile.id !in tried) {
                return door
            }
        }
        return null
    }

    /**
     * Opens [door] with its own "Open" option so any requirement still applies,
     * then [WorldMapWalk] carries on to the marker once it's done
     */
    private fun openDoor(door: GameObject) {
        tried.add(door.tile.id)
        character.interactObject(door, "Open")
        character[OPENING] = Opening(character.mode, this)
        character.softTimers.start(OPENING)
    }

    data class Opening(val mode: Mode, val walk: MarkerWalk)

    companion object {
        const val OPENING = "marker_walk_door"

        // Furthest a walk can go with the normal path finder (128 tile search area centred on the player)
        const val NEAR = 60
        private const val SEARCH_SIZE = 512
        private const val MAX_STEPS = 25
        private const val MAX_PLANS = 50
        private const val ARRIVED = 10
        private const val MAX_WAYPOINTS = 4096

        // Furthest point to aim for at once, leaving room to walk around obstacles inside the search area
        private const val REACH = SEARCH_SIZE / 2 - 32
        private const val REACH_STEP = 32

        private val pathFinder by lazy { PathFinder(flags = Collisions.map, searchMapSize = SEARCH_SIZE, ringBufferSize = 1 shl 16, useRouteBlockerFlags = true) }

        /**
         * Turning points from [from] towards [marker], or null if there's no way there on foot.
         * Markers further than [REACH] are approached through a point on the way, moved closer until one is reachable.
         */
        internal fun route(from: Tile, marker: Tile, tried: Set<Int> = emptySet()): List<Tile>? {
            if (from.level != marker.level) {
                return null
            }
            val deltaX = marker.x - from.x
            val deltaY = marker.y - from.y
            val distance = max(abs(deltaX), abs(deltaY))
            if (distance == 0) {
                return null
            }
            val reach = minOf(distance, REACH)
            // Plan through closed doors on the way, they're opened on reaching them
            val doors = doors(from, marker, tried)
            for (door in doors) {
                removeCollision.modify(door)
            }
            try {
                return search(from, deltaX, deltaY, distance, reach)
            } finally {
                for (door in doors) {
                    addCollision.modify(door)
                }
            }
        }

        private fun search(from: Tile, deltaX: Int, deltaY: Int, distance: Int, start: Int): List<Tile>? {
            var reach = start
            while (reach > 0) {
                val x = from.x + deltaX * reach / distance
                val y = from.y + deltaY * reach / distance
                val route = pathFinder.findPath(level = from.level, srcX = from.x, srcZ = from.y, destX = x, destZ = y, moveNear = true, maxWaypoints = MAX_WAYPOINTS)
                if (route.success && route.waypoints.isNotEmpty()) {
                    return route.waypoints.map { Tile(it.x, it.z, from.level) }
                }
                reach -= REACH_STEP
            }
            return null
        }

        private val removeCollision = GameObjectCollisionRemove()
        private val addCollision = GameObjectCollisionAdd()

        /** Tiles around the way from start to marker searched for doors */
        private const val DOOR_MARGIN = 32

        /**
         * Closed doors between [from] and [marker] which the player can open by walking into them
         */
        private fun doors(from: Tile, marker: Tile, tried: Set<Int>): List<GameObject> {
            val reachX = minOf(abs(marker.x - from.x), REACH) * (marker.x - from.x).sign
            val reachY = minOf(abs(marker.y - from.y), REACH) * (marker.y - from.y).sign
            val minX = minOf(from.x, from.x + reachX) - DOOR_MARGIN
            val maxX = maxOf(from.x, from.x + reachX) + DOOR_MARGIN
            val minY = minOf(from.y, from.y + reachY) - DOOR_MARGIN
            val maxY = maxOf(from.y, from.y + reachY) + DOOR_MARGIN
            val doors = mutableListOf<GameObject>()
            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    val tile = Tile(x, y, from.level)
                    val door = GameObjects.getLayer(tile, ObjectLayer.WALL) ?: continue
                    if (openable(door) && tile.id !in tried) {
                        doors.add(door)
                    }
                }
            }
            return doors
        }

        /** Ordinary closed doors and gates; diagonal ones need the player to step aside first */
        internal fun openable(door: GameObject): Boolean {
            val def = door.def
            return def.stringId.endsWith("_closed") && def.isDoor() && !door.isDiagonal() && def.options?.contains("Open") == true
        }
    }
}
