package com.vinz.horror

import kotlin.random.Random

const val TILE = 4.0f

/**
 * Perfect maze (recursive backtracker) with per-cell wall bitmask.
 * dir: 0 = North(-Z), 1 = East(+X), 2 = South(+Z), 3 = West(-X)
 */
class Maze(val size: Int, seed: Long = System.currentTimeMillis()) {
    val walls = Array(size) { IntArray(size) { 0b1111 } }
    private val rnd = Random(seed)

    init {
        carveIterative(0, 0)
    }

    private fun idx(x: Int, y: Int) = y * size + x

    private fun carveIterative(startX: Int, startY: Int) {
        val visited = BooleanArray(size * size)
        val stack = ArrayDeque<Pair<Int, Int>>()
        visited[idx(startX, startY)] = true
        stack.addLast(startX to startY)
        while (stack.isNotEmpty()) {
            val (x, y) = stack.last()
            val dirs = (0..3).shuffled(rnd)
            var carved = false
            for (d in dirs) {
                val (nx, ny) = neighbor(x, y, d)
                if (nx < 0 || ny < 0 || nx >= size || ny >= size) continue
                if (visited[idx(nx, ny)]) continue
                walls[y][x] = walls[y][x] and (1 shl d).inv()
                val opp = (d + 2) % 4
                walls[ny][nx] = walls[ny][nx] and (1 shl opp).inv()
                visited[idx(nx, ny)] = true
                stack.addLast(nx to ny)
                carved = true
                break
            }
            if (!carved) stack.removeLast()
        }
    }

    private fun neighbor(x: Int, y: Int, d: Int): Pair<Int, Int> = when (d) {
        0 -> x to y - 1
        1 -> x + 1 to y
        2 -> x to y + 1
        else -> x - 1 to y
    }

    fun hasWall(x: Int, y: Int, dir: Int): Boolean {
        if (x < 0 || y < 0 || x >= size || y >= size) return true
        return (walls[y][x] and (1 shl dir)) != 0
    }

    fun worldToCell(wx: Float, wz: Float): Pair<Int, Int> =
        (wx / TILE).toInt().coerceIn(0, size - 1) to (wz / TILE).toInt().coerceIn(0, size - 1)

    fun cellCenter(cx: Int, cy: Int): Pair<Float, Float> = (cx + 0.5f) * TILE to (cy + 0.5f) * TILE

    /** BFS shortest path between two cells, inclusive, start..goal. Empty if unreachable (shouldn't happen on a perfect maze). */
    fun findPath(sx: Int, sy: Int, gx: Int, gy: Int): List<Pair<Int, Int>> {
        if (sx == gx && sy == gy) return listOf(sx to sy)
        val prev = HashMap<Int, Int>()
        val visited = HashSet<Int>()
        val queue = ArrayDeque<Int>()
        val start = idx(sx, sy)
        visited.add(start)
        queue.addLast(start)
        var found = false
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val cx = cur % size
            val cy = cur / size
            if (cx == gx && cy == gy) { found = true; break }
            for (d in 0..3) {
                if (hasWall(cx, cy, d)) continue
                val (nx, ny) = neighbor(cx, cy, d)
                if (nx < 0 || ny < 0 || nx >= size || ny >= size) continue
                val ni = idx(nx, ny)
                if (visited.contains(ni)) continue
                visited.add(ni)
                prev[ni] = cur
                queue.addLast(ni)
            }
        }
        if (!found) return emptyList()
        val path = ArrayList<Pair<Int, Int>>()
        var cur = idx(gx, gy)
        path.add(gx to gy)
        while (cur != start) {
            val p = prev[cur] ?: break
            cur = p
            path.add(cur % size to cur / size)
        }
        path.reverse()
        return path
    }
}
