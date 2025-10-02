package dev.wbell.buildtopia.app.game.session.world

import dev.wbell.buildtopia.app.game.Game
import dev.wbell.buildtopia.app.game.Game.window
import dev.wbell.buildtopia.app.game.camera.Camera
import dev.wbell.buildtopia.app.game.session.Session
import dev.wbell.buildtopia.app.game.session.world.chunk.Block.Block
import dev.wbell.buildtopia.app.game.session.world.chunk.BlockAccessor
import dev.wbell.buildtopia.app.game.session.world.chunk.Chunk
import dev.wbell.buildtopia.app.game.session.world.chunk.ChunkSection
import dev.wbell.buildtopia.app.game.session.world.chunk.LightAccessor
import dev.wbell.buildtopia.app.game.session.world.player.Player
import dev.wbell.buildtopia.app.game.settings.SettingKey
import dev.wbell.buildtopia.app.game.settings.Settings
import org.joml.Vector2i
import org.joml.Vector3d
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL20.*
import kotlinx.coroutines.*
import org.joml.Matrix4f
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

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
    var dayNightLoc = glGetUniformLocation(Game.shaderProgram, "dayNight")
    var dayNightTick = 0

    init {
        val centerChunk = Vector2i(
            (player.position.x / ChunkSection.LENGTH).toInt(),
            (player.position.z / ChunkSection.LENGTH).toInt()
        )
        val renderDistance = Settings.get(SettingKey.RENDERDISTANCE) ?: 12
        for (x in -renderDistance..<renderDistance) {
            for (z in -renderDistance..<renderDistance) {

                chunks[packChunkKey(centerChunk.x + x, centerChunk.y + z)] =
                    Chunk(
                        this,
                        Vector2i(centerChunk.x + x, centerChunk.y + z),
                        24,
                        4,
                        Array(ChunkSection.LENGTH * ChunkSection.LENGTH * ChunkSection.LENGTH * 24) { i ->
                            val x = i % ChunkSection.LENGTH
                            val z = (i / ChunkSection.LENGTH) % ChunkSection.LENGTH
                            val y = (i / (ChunkSection.LENGTH * ChunkSection.LENGTH)) - 4 * ChunkSection.LENGTH
                            if (y == 0 || (y == 10 && Random.nextFloat()>0.01)) Block() else null
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
                val renderDistance = Settings.get(SettingKey.RENDERDISTANCE) ?: 12
                val chunksToUpdate = mutableListOf<Pair<Chunk, Int>>() // Pair(chunk, distance^2)

                for (dx in -renderDistance - 1..renderDistance) {
                    for (dz in -renderDistance - 1..renderDistance) {
                        val chunk = getChunk(centerChunk.x + dx, centerChunk.y + dz)
                        if (chunk != null) {
                            val dist2 = dx * dx + dz * dz
                            chunksToUpdate.add(chunk to dist2)
                        }
                    }
                }

// Sort by distance
                chunksToUpdate.sortBy { it.second }
                for ((chunk, _) in chunksToUpdate) {
                    if (chunk.toRenderMesh()) {
                        chunk.renderMesh()
                        chunkMeshQueue.add { chunk.uploadChunkMesh() }
                    } // main thread upload
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

    var camera: Camera = Camera(Vector3d(0.0, 0.0, 0.0), 0f, 0f, 0f)

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


    val sunLights = object : LightAccessor {
        override operator fun get(x: Int, y: Int, z: Int): Int {
            return getChunk(floorDiv(x, ChunkSection.LENGTH), floorDiv(z, ChunkSection.LENGTH))?.sunLights[floorMod(
                x,
                ChunkSection.LENGTH
            ), y, floorMod(z, ChunkSection.LENGTH)] ?: 15
        }

        override operator fun set(x: Int, y: Int, z: Int, value: Int) {
            getChunk(floorDiv(x, ChunkSection.LENGTH), floorDiv(z, ChunkSection.LENGTH))?.sunLights[floorMod(
                x,
                ChunkSection.LENGTH
            ), y, floorMod(z, ChunkSection.LENGTH)] = value
        }
    }

    val blockLights = object : LightAccessor {
        override operator fun get(x: Int, y: Int, z: Int): Int {
            return getChunk(floorDiv(x, ChunkSection.LENGTH), floorDiv(z, ChunkSection.LENGTH))?.blockLights[floorMod(
                x,
                ChunkSection.LENGTH
            ), y, floorMod(z, ChunkSection.LENGTH)] ?: 0
        }

        override operator fun set(x: Int, y: Int, z: Int, value: Int) {
            getChunk(floorDiv(x, ChunkSection.LENGTH), floorDiv(z, ChunkSection.LENGTH))?.blockLights[floorMod(
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
        val alpha = ((glfwGetTime() - lastTick) / 0.05).coerceIn(0.0, 1.0)
        val dayNight = (((dayNightTick + alpha).toFloat() / 24000) * 2 * PI.toFloat()).coerceIn(0f, 1f)
        glEnable(GL_DEPTH_TEST)
        glClearColor(0f, 0.6f * dayNight, 1f * dayNight, 1f * dayNight)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glUniform1f(dayNightLoc, dayNight)

        camera = player.getCamera(alpha)

        val view = Matrix4f().identity().rotateX(-camera.pitch).rotateY(-camera.yaw) // copy the camera matrix

// Now upload
        val buf = FloatArray(16)
        view.get(buf)
        glUniformMatrix4fv(Game.viewLoc, false, buf)

        val centerChunk = Vector2i(
            (player.position.x / ChunkSection.LENGTH).toInt(),
            (player.position.z / ChunkSection.LENGTH).toInt()
        )
        val renderDistance = Settings.get(SettingKey.RENDERDISTANCE) ?: 12
        for (dx in -renderDistance - 1..renderDistance) {
            for (dz in -renderDistance - 1..renderDistance) {
                val chunk = getChunk(centerChunk.x + dx, centerChunk.y + dz)
                chunk?.render(alpha)
            }
        }
    }

    fun tick() {
        dayNightTick += 1
        player.tick()
    }
}