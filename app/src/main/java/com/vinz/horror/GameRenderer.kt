package com.vinz.horror

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

interface GameEvents {
    fun onJumpscare()
    fun onBatteryChanged(pct: Float)
    fun onRespawn()
}

class GameRenderer(private val context: Context, private val events: GameEvents) : GLSurfaceView.Renderer {

    @Volatile var moveX = 0f
    @Volatile var moveZ = 0f
    @Volatile var lookDX = 0f
    @Volatile var lookDY = 0f
    @Volatile var flashlightToggle = false
    @Volatile var quality: GraphicsQuality = GraphicsQuality.MEDIUM

    private var program = 0
    private var aPosition = 0
    private var aNormal = 0
    private var aUV = 0
    private var uModel = 0
    private var uView = 0
    private var uProj = 0
    private var uNormalMatrix = 0
    private var uDiffuseMap = 0
    private var uNormalMap = 0
    private var uCameraPos = 0
    private var uFlashDir = 0
    private var uFlashOn = 0
    private var uFlashIntensity = 0
    private var uUseNormalMap = 0
    private var uFogColor = 0
    private var uFogDensity = 0
    private var uTintColor = 0
    private var uAlpha = 0
    private var uTime = 0
    private var uResolution = 0

    private lateinit var floorMesh: GpuMesh
    private lateinit var ceilingMesh: GpuMesh
    private lateinit var wallMesh: GpuMesh
    private lateinit var monsterMesh: GpuMesh
    private lateinit var flashlightMesh: GpuMesh
    private lateinit var pipeMesh: GpuMesh

    private var texWallD = 0
    private var texWallN = 0
    private var texFloorD = 0
    private var texFloorN = 0
    private var texCeilD = 0
    private var texCeilN = 0
    private var texMonsterD = 0
    private var texMonsterN = 0
    private var texPipeD = 0
    private var texPipeN = 0

    val mazeSize = 12
    private lateinit var maze: Maze
    private lateinit var player: Player
    private lateinit var monster: Monster
    private var gameOver = false
    private var respawnTimer = 0f
    private var elapsedTime = 0f
    private val rnd = Random(System.currentTimeMillis())
    private var propCells: List<Triple<Int, Int, Float>> = emptyList() // x, y, rotationDeg

    private val modelMat = FloatArray(16)
    private val viewMat = FloatArray(16)
    private val projMat = FloatArray(16)
    private val normalMat3 = FloatArray(9)

    private var lastFrameNanos = 0L
    private var width = 1
    private var height = 1
    private var lastBatteryReported = -1f

    init {
        newGame()
    }

    private fun newGame() {
        maze = Maze(mazeSize, System.currentTimeMillis())
        player = Player()
        player.x = TILE * 0.5f
        player.z = TILE * 0.5f

        val dist = Array(mazeSize) { IntArray(mazeSize) { -1 } }
        dist[0][0] = 0
        val q = ArrayDeque<Pair<Int, Int>>()
        q.addLast(0 to 0)
        var far = 0 to 0
        val reachable = ArrayList<Pair<Int, Int>>()
        while (q.isNotEmpty()) {
            val (cx, cy) = q.removeFirst()
            reachable.add(cx to cy)
            if (dist[cy][cx] > dist[far.second][far.first]) far = cx to cy
            for (d in 0..3) {
                if (maze.hasWall(cx, cy, d)) continue
                val (nx, ny) = when (d) {
                    0 -> cx to cy - 1
                    1 -> cx + 1 to cy
                    2 -> cx to cy + 1
                    else -> cx - 1 to cy
                }
                if (nx < 0 || ny < 0 || nx >= mazeSize || ny >= mazeSize) continue
                if (dist[ny][nx] != -1) continue
                dist[ny][nx] = dist[cy][cx] + 1
                q.addLast(nx to ny)
            }
        }
        monster = Monster(far.first, far.second)
        gameOver = false
        respawnTimer = 0f
        elapsedTime = 0f

        // Scatter a handful of pipe props as haunted-house clutter, density
        // from the current graphics quality. Skip the start/monster cells.
        val props = ArrayList<Triple<Int, Int, Float>>()
        for ((cx, cy) in reachable) {
            if (cx == 0 && cy == 0) continue
            if (cx == far.first && cy == far.second) continue
            if (rnd.nextFloat() < 0.12f * quality.propDensity) {
                props.add(Triple(cx, cy, rnd.nextFloat() * 360f))
            }
        }
        propCells = props
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.01f, 0.01f, 0.015f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        // No face culling: a few hand-built boxes have mixed winding, and
        // correctness beats the small fill-rate cost on a maze this size.

        program = ShaderUtil.link(Shaders.VERTEX, Shaders.FRAGMENT)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aNormal = GLES20.glGetAttribLocation(program, "aNormal")
        aUV = GLES20.glGetAttribLocation(program, "aUV")
        uModel = GLES20.glGetUniformLocation(program, "uModel")
        uView = GLES20.glGetUniformLocation(program, "uView")
        uProj = GLES20.glGetUniformLocation(program, "uProj")
        uNormalMatrix = GLES20.glGetUniformLocation(program, "uNormalMatrix")
        uDiffuseMap = GLES20.glGetUniformLocation(program, "uDiffuseMap")
        uNormalMap = GLES20.glGetUniformLocation(program, "uNormalMap")
        uCameraPos = GLES20.glGetUniformLocation(program, "uCameraPos")
        uFlashDir = GLES20.glGetUniformLocation(program, "uFlashDir")
        uFlashOn = GLES20.glGetUniformLocation(program, "uFlashOn")
        uFlashIntensity = GLES20.glGetUniformLocation(program, "uFlashIntensity")
        uUseNormalMap = GLES20.glGetUniformLocation(program, "uUseNormalMap")
        uFogColor = GLES20.glGetUniformLocation(program, "uFogColor")
        uFogDensity = GLES20.glGetUniformLocation(program, "uFogDensity")
        uTintColor = GLES20.glGetUniformLocation(program, "uTintColor")
        uAlpha = GLES20.glGetUniformLocation(program, "uAlpha")
        uTime = GLES20.glGetUniformLocation(program, "uTime")
        uResolution = GLES20.glGetUniformLocation(program, "uResolution")

        val assets = context.assets
        floorMesh = GpuMesh(ObjLoader.load(assets, "models/floor_tile.obj"))
        ceilingMesh = GpuMesh(ObjLoader.load(assets, "models/ceiling_tile.obj"))
        wallMesh = GpuMesh(ObjLoader.load(assets, "models/wall_segment.obj"))
        monsterMesh = GpuMesh(ObjLoader.load(assets, "models/monster.obj"))
        flashlightMesh = GpuMesh(ObjLoader.load(assets, "models/flashlight.obj"))
        pipeMesh = GpuMesh(ObjLoader.load(assets, "models/pipe_prop.obj"))

        texWallD = TextureUtil.loadFromAssets(assets, "textures/wall_diffuse.png")
        texWallN = TextureUtil.loadFromAssets(assets, "textures/wall_normal.png")
        texFloorD = TextureUtil.loadFromAssets(assets, "textures/floor_diffuse.png")
        texFloorN = TextureUtil.loadFromAssets(assets, "textures/floor_normal.png")
        texCeilD = TextureUtil.loadFromAssets(assets, "textures/ceiling_diffuse.png")
        texCeilN = TextureUtil.loadFromAssets(assets, "textures/ceiling_normal.png")
        texMonsterD = TextureUtil.loadFromAssets(assets, "textures/monster_diffuse.png")
        texMonsterN = TextureUtil.loadFromAssets(assets, "textures/monster_normal.png")
        texPipeD = TextureUtil.loadFromAssets(assets, "textures/pipe_diffuse.png")
        texPipeN = TextureUtil.loadFromAssets(assets, "textures/pipe_normal.png")

        lastFrameNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w
        height = h
        GLES20.glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        var dt = (now - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = now
        if (dt > 0.05f) dt = 0.05f
        elapsedTime += dt

        if (!gameOver) {
            updateGame(dt)
        } else {
            respawnTimer -= dt
            if (respawnTimer <= 0f) {
                newGame()
                events.onRespawn()
            }
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        val eyeX = player.x
        val eyeY = player.eyeHeight
        val eyeZ = player.z
        val fx = sin(player.yaw) * cos(player.pitch)
        val fy = sin(player.pitch)
        val fz = cos(player.yaw) * cos(player.pitch)
        Matrix.setLookAtM(viewMat, 0, eyeX, eyeY, eyeZ, eyeX + fx, eyeY + fy, eyeZ + fz, 0f, 1f, 0f)

        val aspect = width.toFloat() / height.toFloat()
        val farPlane = quality.drawDistanceCells * TILE + 4f
        Matrix.perspectiveM(projMat, 0, 65f, aspect, 0.05f, farPlane)

        GLES20.glUniformMatrix4fv(uView, 1, false, viewMat, 0)
        GLES20.glUniformMatrix4fv(uProj, 1, false, projMat, 0)
        GLES20.glUniform3f(uCameraPos, eyeX, eyeY, eyeZ)
        GLES20.glUniform3f(uFlashDir, fx, fy, fz)
        GLES20.glUniform1f(uFlashOn, if (player.flashlightOn) 1f else 0f)
        GLES20.glUniform1f(uFlashIntensity, 2.2f * player.battery.coerceIn(0.15f, 1f))
        GLES20.glUniform1f(uUseNormalMap, if (quality.useNormalMap) 1f else 0f)
        GLES20.glUniform3f(uFogColor, 0.01f, 0.01f, 0.015f)
        GLES20.glUniform1f(uFogDensity, quality.fogDensity)
        GLES20.glUniform1f(uAlpha, 1f)
        GLES20.glUniform1f(uTime, elapsedTime)
        GLES20.glUniform2f(uResolution, width.toFloat(), height.toFloat())

        val (pcx, pcz) = maze.worldToCell(player.x, player.z)
        val range = quality.drawDistanceCells
        for (cy in (pcz - range)..(pcz + range)) {
            if (cy < 0 || cy >= mazeSize) continue
            for (cx in (pcx - range)..(pcx + range)) {
                if (cx < 0 || cx >= mazeSize) continue
                val ddx = cx - pcx
                val ddz = cy - pcz
                if (ddx * ddx + ddz * ddz > range * range) continue
                drawCell(cx, cy)
            }
        }

        drawProps()
        drawMonster()
        drawFlashlightViewmodel()

        updateBatteryAndEvents(dt)
    }

    private fun bindMaterial(diffuse: Int, normal: Int, tint: FloatArray = floatArrayOf(1f, 1f, 1f)) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, diffuse)
        GLES20.glUniform1i(uDiffuseMap, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, normal)
        GLES20.glUniform1i(uNormalMap, 1)
        GLES20.glUniform3f(uTintColor, tint[0], tint[1], tint[2])
    }

    private fun setModelAndDraw(mesh: GpuMesh, mat: FloatArray) {
        GLES20.glUniformMatrix4fv(uModel, 1, false, mat, 0)
        computeNormalMatrix(mat)
        GLES20.glUniformMatrix3fv(uNormalMatrix, 1, false, normalMat3, 0)
        mesh.draw(aPosition, aNormal, aUV)
    }

    private fun computeNormalMatrix(mat: FloatArray) {
        normalMat3[0] = mat[0]; normalMat3[1] = mat[1]; normalMat3[2] = mat[2]
        normalMat3[3] = mat[4]; normalMat3[4] = mat[5]; normalMat3[5] = mat[6]
        normalMat3[6] = mat[8]; normalMat3[7] = mat[9]; normalMat3[8] = mat[10]
    }

    private fun drawCell(cx: Int, cy: Int) {
        val (ccx, ccz) = maze.cellCenter(cx, cy)

        bindMaterial(texFloorD, texFloorN)
        Matrix.setIdentityM(modelMat, 0)
        Matrix.translateM(modelMat, 0, ccx, 0f, ccz)
        setModelAndDraw(floorMesh, modelMat)

        bindMaterial(texCeilD, texCeilN)
        Matrix.setIdentityM(modelMat, 0)
        Matrix.translateM(modelMat, 0, ccx, 0f, ccz)
        setModelAndDraw(ceilingMesh, modelMat)

        bindMaterial(texWallD, texWallN)
        if (maze.hasWall(cx, cy, 0)) {
            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, ccx, 0f, cy * TILE)
            setModelAndDraw(wallMesh, modelMat)
        }
        if (maze.hasWall(cx, cy, 2)) {
            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, ccx, 0f, (cy + 1) * TILE)
            setModelAndDraw(wallMesh, modelMat)
        }
        if (maze.hasWall(cx, cy, 1)) {
            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, (cx + 1) * TILE, 0f, ccz)
            Matrix.rotateM(modelMat, 0, 90f, 0f, 1f, 0f)
            setModelAndDraw(wallMesh, modelMat)
        }
        if (maze.hasWall(cx, cy, 3)) {
            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, cx * TILE, 0f, ccz)
            Matrix.rotateM(modelMat, 0, 90f, 0f, 1f, 0f)
            setModelAndDraw(wallMesh, modelMat)
        }
    }

    private fun drawProps() {
        if (propCells.isEmpty()) return
        val (pcx, pcz) = maze.worldToCell(player.x, player.z)
        val range = quality.drawDistanceCells
        bindMaterial(texPipeD, texPipeN)
        for ((cx, cy, rotDeg) in propCells) {
            val ddx = cx - pcx
            val ddz = cy - pcz
            if (ddx * ddx + ddz * ddz > range * range) continue
            val (ccx, ccz) = maze.cellCenter(cx, cy)
            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, ccx, 0.9f, ccz)
            Matrix.rotateM(modelMat, 0, rotDeg, 0f, 0f, 1f)
            setModelAndDraw(pipeMesh, modelMat)
        }
    }

    private fun drawMonster() {
        val dx = monster.x - player.x
        val dz = monster.z - player.z
        val dist = sqrt(dx * dx + dz * dz)
        if (dist > quality.drawDistanceCells * TILE) return
        val facing = atan2(-dx, -dz)
        val tint = if (monster.isPhasing) floatArrayOf(0.55f, 0.62f, 0.75f) else floatArrayOf(0.7f, 0.65f, 0.6f)
        bindMaterial(texMonsterD, texMonsterN, tint)
        Matrix.setIdentityM(modelMat, 0)
        Matrix.translateM(modelMat, 0, monster.x, 0f, monster.z)
        Matrix.rotateM(modelMat, 0, Math.toDegrees(facing.toDouble()).toFloat(), 0f, 1f, 0f)
        setModelAndDraw(monsterMesh, modelMat)
    }

    private fun drawFlashlightViewmodel() {
        if (!player.flashlightOn) return
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT)
        bindMaterial(texWallD, texWallN, floatArrayOf(0.25f, 0.25f, 0.25f))
        Matrix.setIdentityM(modelMat, 0)
        Matrix.translateM(modelMat, 0, player.x, player.eyeHeight, player.z)
        Matrix.rotateM(modelMat, 0, Math.toDegrees(player.yaw.toDouble()).toFloat(), 0f, 1f, 0f)
        Matrix.translateM(modelMat, 0, 0.16f, -0.18f, 0.35f)
        setModelAndDraw(flashlightMesh, modelMat)
    }

    private fun updateGame(dt: Float) {
        val dLookX = lookDX
        lookDX = 0f
        val dLookY = lookDY
        lookDY = 0f
        player.yaw -= dLookX * 0.0025f
        player.pitch = (player.pitch - dLookY * 0.0025f).coerceIn(-1.3f, 1.3f)

        if (flashlightToggle) {
            player.flashlightOn = !player.flashlightOn
            flashlightToggle = false
        }

        val mx = moveX
        val mz = moveZ
        val mLen = sqrt(mx * mx + mz * mz)
        if (mLen > 0.02f) {
            val nx = mx / mLen.coerceAtLeast(1f)
            val nz = mz / mLen.coerceAtLeast(1f)
            val speed = 2.6f
            val fwd = player.forward()
            val rgt = player.right()
            val vx = (fwd[0] * nz + rgt[0] * nx) * speed * dt
            val vz = (fwd[2] * nz + rgt[2] * nx) * speed * dt
            val (newX, newZ) = tryMove(player.x + vx, player.z + vz)
            player.x = newX
            player.z = newZ
        }

        monster.update(dt, maze, player, mazeSize, rnd)

        if (monster.distanceTo(player) < 0.6f) {
            gameOver = true
            respawnTimer = 2.2f
            events.onJumpscare()
        }
    }

    private fun tryMove(newX: Float, newZ: Float): Pair<Float, Float> {
        var x = newX
        var z = newZ
        val (ccx, ccy) = maze.worldToCell(player.x, player.z)
        val r = player.radius

        val (nx1, _) = maze.worldToCell(x, player.z)
        if (nx1 != ccx) {
            val dir = if (nx1 > ccx) 1 else 3
            if (maze.hasWall(ccx, ccy, dir)) {
                x = if (dir == 1) (ccx + 1) * TILE - r else ccx * TILE + r
            }
        }
        val (_, ny2) = maze.worldToCell(x, z)
        if (ny2 != ccy) {
            val dir = if (ny2 > ccy) 2 else 0
            if (maze.hasWall(ccx, ccy, dir)) {
                z = if (dir == 2) (ccy + 1) * TILE - r else ccy * TILE + r
            }
        }
        return x to z
    }

    private fun updateBatteryAndEvents(dt: Float) {
        player.battery = if (player.flashlightOn) {
            (player.battery - dt * 0.012f).coerceIn(0f, 1f)
        } else {
            (player.battery + dt * 0.02f).coerceIn(0f, 1f)
        }
        if (kotlin.math.abs(player.battery - lastBatteryReported) > 0.01f) {
            lastBatteryReported = player.battery
            events.onBatteryChanged(player.battery)
        }
    }
}
