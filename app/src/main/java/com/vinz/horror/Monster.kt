package com.vinz.horror

import kotlin.math.sqrt
import kotlin.random.Random

enum class MonsterState { PATROL, CHASE }

class Monster(startX: Int, startY: Int) {
    var cellX = startX
    var cellY = startY
    var x = (startX + 0.5f) * TILE
    var z = (startY + 0.5f) * TILE
    var state = MonsterState.PATROL
    private var path: List<Pair<Int, Int>> = emptyList()
    private var pathIndex = 0
    private val speed = 1.6f
    private var repathCooldown = 0f
    private var patrolTarget: Pair<Int, Int>? = null

    fun update(dt: Float, maze: Maze, player: Player, size: Int, rnd: Random) {
        repathCooldown -= dt
        val playerCell = maze.worldToCell(player.x, player.z)
        val myCell = cellX to cellY

        val dx = player.x - x
        val dz = player.z - z
        val dist = sqrt(dx * dx + dz * dz)

        val heard = dist < 3.5f
        var seen = false
        if (player.flashlightOn && dist < 9f) {
            val tx = dx / (dist + 0.0001f)
            val tz = dz / (dist + 0.0001f)
            val fwd = player.forward()
            val dot = fwd[0] * tx + fwd[2] * tz
            if (dot > 0.9f) seen = true
        }
        if (heard || seen) state = MonsterState.CHASE

        if (state == MonsterState.CHASE) {
            if (repathCooldown <= 0f) {
                path = maze.findPath(myCell.first, myCell.second, playerCell.first, playerCell.second)
                pathIndex = 0
                repathCooldown = 0.6f
            }
        } else {
            if (patrolTarget == null || myCell == patrolTarget) {
                var tx: Int
                var ty: Int
                do {
                    tx = rnd.nextInt(size)
                    ty = rnd.nextInt(size)
                } while (tx == myCell.first && ty == myCell.second)
                patrolTarget = tx to ty
                path = maze.findPath(myCell.first, myCell.second, tx, ty)
                pathIndex = 0
            }
        }

        if (pathIndex < path.size) {
            val (tx, ty) = path[pathIndex]
            val (cx, cz) = maze.cellCenter(tx, ty)
            val ddx = cx - x
            val ddz = cz - z
            val d = sqrt(ddx * ddx + ddz * ddz)
            val spd = if (state == MonsterState.CHASE) speed * 1.6f else speed
            if (d < 0.08f) {
                cellX = tx
                cellY = ty
                pathIndex++
            } else {
                x += ddx / d * spd * dt
                z += ddz / d * spd * dt
            }
        }
    }

    fun distanceTo(player: Player): Float {
        val dx = player.x - x
        val dz = player.z - z
        return sqrt(dx * dx + dz * dz)
    }
}
