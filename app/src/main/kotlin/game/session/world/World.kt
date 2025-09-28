package dev.wbell.buildtopia.app.game.session.world

import dev.wbell.buildtopia.app.game.Game
import dev.wbell.buildtopia.app.game.Game.window
import dev.wbell.buildtopia.app.game.camera.Camera
import dev.wbell.buildtopia.app.game.session.Session
import dev.wbell.buildtopia.app.game.session.world.chunk.Block.Block
import dev.wbell.buildtopia.app.game.session.world.chunk.BlockAccessor
import dev.wbell.buildtopia.app.game.session.world.chunk.Chunk
import dev.wbell.buildtopia.app.game.session.world.chunk.ChunkSection
import dev.wbell.buildtopia.app.game.session.world.player.Player
import dev.wbell.buildtopia.app.game.settings.SettingKey
import dev.wbell.buildtopia.app.game.settings.Settings
import dev.wbell.buildtopia.app.getCameraMatrix
import org.joml.Vector2i
import org.joml.Vector3d
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL20.*
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue

fun floorDiv(a: Int, b: Int): Int {
    // rounds down (towards -∞), unlike / which rounds toward 0
    var div = a / b
    if ((a xor b) < 0 && a % b != 0) {
        div -= 1
    }
    return div
}

fun floorMod(a: Int, b: Int): Int {
    // always returns a result in 0 until b-1
    val mod = a % b
    return if (mod < 0) mod + b else mod
}

class World(val player: Player, val session: Session) {
    private val chunkRenderer = CoroutineScope(Dispatchers.Default)
    val chunkMeshQueue = ConcurrentLinkedQueue<() -> Unit>()
    private val physics = CoroutineScope(Dispatchers.Default)
    private val chunks = mutableMapOf<Long, Chunk>()
    private var isActive = true
    var lastTick = glfwGetTime()

    init {
        val renderDistance = Settings.get(SettingKey.RENDERDISTANCE, 32)!!
        for (x in -renderDistance..<renderDistance) {
            for (z in -renderDistance..<renderDistance) {
                chunks[packChunkKey(x, z)] =
                    Chunk(
                        this,
                        Vector2i(x, z),
                        24,
                        4,
                        Array(ChunkSection.LENGTH * ChunkSection.LENGTH * ChunkSection.LENGTH * 24) {
                            if (it < 34000) Block() else null
                        })
            }
        }
        startPhysics()
        startChunkRenderer()
    }

    private fun startChunkRenderer() {
        chunkRenderer.launch {
            val tickTime = 50L // ms per tick (20 TPS)
            while (isActive) {
                val centerChunk = Vector2i(
                    (player.position.x / ChunkSection.LENGTH).toInt(),
                    (player.position.z / ChunkSection.LENGTH).toInt()
                )
                val renderDistance = Settings.get(SettingKey.RENDERDISTANCE, 12)!!
                val chunksToUpdate = mutableListOf<Pair<Chunk, Int>>() // Pair(chunk, distance^2)

                for (dx in -renderDistance - 1..renderDistance) {
                    for (dz in -renderDistance - 1..renderDistance) {
                        val chunk = getChunk(centerChunk.x + dx, centerChunk.y + dz)
                        if (chunk != null && chunk.updateChunk) {
                            val dist2 = dx * dx + dz * dz
                            chunksToUpdate.add(chunk to dist2)
                        }
                    }
                }

// Sort by distance
                chunksToUpdate.sortBy { it.second }
                for ((chunk, _) in chunksToUpdate) {
                    chunk.renderMesh() // background mesh generation
                    chunkMeshQueue.add { chunk.uploadChunkMesh() } // main thread upload
                }
                delay(tickTime)
            }
        }
    }

    private fun startPhysics() {
        physics.launch {
            val tickTime = 50L // ms per tick (20 TPS)
            while (isActive) {
                tick()
                lastTick = glfwGetTime()
                delay(tickTime)
            }
        }
    }

    var camera: Camera = Camera(Vector3d(0.0, 0.0, 0.0), 0.0, 0.0, 0.0)

    private fun packChunkKey(x: Int, z: Int): Long =
        x.toLong() and 0xFFFFFFFFL or ((z.toLong() and 0xFFFFFFFFL) shl 32)

    fun getChunk(x: Int, z: Int): Chunk? {
        //if (chunks[packChunkKey(x, z)] == null) chunks[packChunkKey(x, z)] = Chunk(this, Vector2i(x,z), 24, 4, Array(16*16*16*24){ Block()})
        return chunks[packChunkKey(x, z)]
    }

    fun setChunk(x: Int, z: Int, chunk: Chunk) {
        chunks[packChunkKey(x, z)]?.unload()
        chunks[packChunkKey(x, z)] = chunk
    }


    val blocks = object : BlockAccessor {
        override operator fun get(x: Int, y: Int, z: Int): Block? {
            return getChunk(floorDiv(x, ChunkSection.LENGTH), floorDiv(z, ChunkSection.LENGTH))?.blocks[floorMod(
                x,
                ChunkSection.LENGTH
            ), y, floorMod(z, ChunkSection.LENGTH)]
        }

        override operator fun set(x: Int, y: Int, z: Int, value: Block?) {
            getChunk(floorDiv(x, ChunkSection.LENGTH), floorDiv(z, ChunkSection.LENGTH))?.blocks[floorMod(
                x,
                ChunkSection.LENGTH
            ), y, floorMod(z, ChunkSection.LENGTH)] = value
        }
    }

    fun clean() {

    }

    fun render(deltaTime: Double) {
        while (chunkMeshQueue.isNotEmpty()) {
            chunkMeshQueue.poll()?.invoke()
        }
        val deltaTickTime = ((glfwGetTime() - lastTick) / 0.05).coerceIn(0.0, 1.0)
        glEnable(GL_DEPTH_TEST)
        glClearColor(0f, 0.6f, 1f, 1f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        val forwardX = sin(player.yaw)
        val forwardZ = cos(player.yaw)
//        if (glfwGetKey(Game.window!!, GLFW_KEY_W) == GLFW_PRESS) {
//            player.position.x-=forwardX*deltaTime*10
//            player.position.z-=forwardZ*deltaTime*10
//        }

        if (glfwGetKey(Game.window!!, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) {
            player.position.y -= deltaTime * 10
        }

        camera = player.getCamera(deltaTickTime)

        val view = getCameraMatrix(camera.position, camera.pitch, camera.yaw, camera.roll)
        val viewBuffer = FloatArray(16)
        view.get(viewBuffer)
        glUniformMatrix4fv(Game.viewLoc, false, viewBuffer)

        val centerChunk = Vector2i(
            (player.position.x / ChunkSection.LENGTH).toInt(),
            (player.position.z / ChunkSection.LENGTH).toInt()
        )
        val renderDistance = Settings.get(SettingKey.RENDERDISTANCE, 12)!!
        for (dx in -renderDistance - 1..renderDistance) {
            for (dz in -renderDistance - 1..renderDistance) {
                val chunk = getChunk(centerChunk.x + dx, centerChunk.y + dz)
                chunk?.render(deltaTickTime)
            }
        }


        window?.let { window ->
            glfwSwapBuffers(window)
            glfwPollEvents()
        }
    }

    fun tick() {
        player.tick()
    }
}