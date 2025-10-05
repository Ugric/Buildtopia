package dev.wbell.buildtopia.app.game.session.world

import OpenSimplex2S.OpenSimplex2S
import dev.wbell.buildtopia.app.game.Game
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
import kotlinx.coroutines.*
import org.joml.Matrix4f
import org.joml.Vector2i
import org.joml.Vector3d
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL20.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.PriorityBlockingQueue
import kotlin.math.PI

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
    private val chunkRenderQueue = PriorityBlockingQueue<Pair<Chunk, Int>>(11, compareBy { it.second })
    private val physics = CoroutineScope(Dispatchers.Default)
    private val chunks = ConcurrentHashMap<Long, Chunk>()
    private val toLoadChunks = ConcurrentHashMap<Long, Vector2i>()
    private var isActive = true
    var lastTick = glfwGetTime()
    var dayNightLoc = glGetUniformLocation(Game.shaderProgram, "dayNight")
    var dayNightTick = 0

    var seed: Long = 12345

    fun init() {
        startPhysics()
        startChunkRenderer()
        startChunkLoaderRenderer()
        startChunkRendererChild()
    }

    private fun markChunkDirty(chunk: Chunk) {
        if (!chunk.isQueuedForRender) {
            val dx = chunk.coords.x
            val dz = chunk.coords.y
            val dist2 = dx * dx + dz * dz
            chunkRenderQueue.add(chunk to dist2)
            chunk.isQueuedForRender = true
        }
    }


    private fun startChunkLoaderRenderer() {
        chunkRenderer.launch {
            val tickTime = 50L
            while (isActive) {
                val centerChunk = Vector2i(
                    (player.position.x / ChunkSection.LENGTH).toInt(),
                    (player.position.z / ChunkSection.LENGTH).toInt()
                )
                val chunksToRender = toLoadChunks.toList().toMutableList() // (dx, dz)
                chunksToRender.sortBy { (_, coords) -> (coords.x - centerChunk.x) * (coords.x - centerChunk.x) + (coords.y - centerChunk.y) * (coords.y - centerChunk.y) }
                var i = 0
                for ((key, coords) in chunksToRender) {
                    var peak = -4* ChunkSection.SIZE
                    val blockData = Array(ChunkSection.LENGTH * ChunkSection.LENGTH * ChunkSection.LENGTH * 24) { i ->
                        val x = i % ChunkSection.LENGTH
                        val z = (i / ChunkSection.LENGTH) % ChunkSection.LENGTH
                        val y = (i / (ChunkSection.LENGTH * ChunkSection.LENGTH)) - 4 * ChunkSection.LENGTH

                        // world-space coordinates
                        val worldX = coords.x * ChunkSection.LENGTH + x
                        val worldZ = coords.y * ChunkSection.LENGTH + z
                        val worldY = y

                        // --- Biome / height noise ---
                        val biomeFactor = OpenSimplex2S.noise2(seed + 1000, worldX * 0.01, worldZ * 0.01)
                        val baseHeight = 64 + (biomeFactor * 8).toInt() // plains ~56-72

                        // Hills / mountains
                        val hillNoise = OpenSimplex2S.noise2(seed + 2000, worldX * 0.02, worldZ * 0.02)
                        val hillHeight = (hillNoise * 16).toInt()
                        val terrainHeight = baseHeight + hillHeight

                        // --- Multi-octave 3D caves ---
                        fun caveDensity(x: Double, y: Double, z: Double): Double {
                            var density = 0.0
                            var frequency = 0.02
                            var amplitude = 1.0
                            for (octave in 0 until 3) { // 3 octaves, can increase for more detail
                                density += OpenSimplex2S.noise3_ImproveXY(seed + 3000 + octave * 1000, x * frequency, y * frequency, z * frequency) * amplitude
                                frequency *= 2.0
                                amplitude /= 2.0
                            }
                            return density
                        }

                        val density = caveDensity(worldX.toDouble(), worldY.toDouble(), worldZ.toDouble())

                        // Optional: reduce cave openings near surface
                        val heightFactor = ((terrainHeight - worldY).toDouble() / 30.0).coerceIn(0.0, 1.0)
                        val isCave = density > 0.5 * heightFactor // adjust threshold for more/less caves

                        // --- Set block ---
                        val block: Block? = if (worldY <= terrainHeight && !isCave) {
                            when {
                                worldY == terrainHeight -> Block()
                                worldY >= terrainHeight - 3 -> Block()
                                else -> Block()
                            }
                        } else null

                        if (terrainHeight > peak) peak = terrainHeight
                        block
                    }
                    chunks[key] = Chunk(
                        session.World!!,
                        coords,
                        24,
                        4,
                        blockData,
                        peak
                    )
                    toLoadChunks.remove(key)
                    i++
                }
                if (i == 0) delay(tickTime)
            }
        }
    }

    private fun startChunkRendererChild() {
        chunkRenderer.launch {
            val tickTime = 50L
            while (isActive) {
                while (chunkRenderQueue.isNotEmpty()) {
                    val next = chunkRenderQueue.poll()
                    val (chunk, _) = next
                    chunk.isQueuedForRender = false

                    if (chunk.toRenderMesh()) {
                        chunk.renderMesh()
                        chunkMeshQueue.add { chunk.uploadChunkMesh() }
                    }
                }
                delay(tickTime)
            }
        }
    }

    private fun startChunkRenderer() {
        chunkRenderer.launch {
            val tickTime = 50L
            while (isActive) {
                for (chunk in chunks.values) {
                    if (chunk.toRenderMesh()) markChunkDirty(chunk)
                }
                if (chunkRenderQueue.isEmpty()) delay(tickTime)
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

    var camera: Camera = Camera(Vector3d(0.0, 0.0, 0.0), 0f, 0f, 0f, 90f)

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
            return getChunk(
                floorDiv(x, ChunkSection.LENGTH),
                floorDiv(z, ChunkSection.LENGTH)
            )?.blockLights[floorMod(
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

    fun render() {
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

        Game.updateFov(camera.fov)

        val view = Matrix4f().identity().rotateX(-camera.pitch).rotateY(-camera.yaw) // copy the camera matrix

// Now upload
        val buf = FloatArray(16)
        view.get(buf)
        glUniformMatrix4fv(Game.viewLoc, false, buf)

        val centerChunk = Vector2i(
            (player.position.x / ChunkSection.LENGTH).toInt(),
            (player.position.z / ChunkSection.LENGTH).toInt()
        )
        val renderDistance = Settings.get(SettingKey.RENDERDISTANCE) ?: 32

// --- Unload distant chunks ---
        val chunksToUnload = chunks.values.filter { chunk ->
            val dx = chunk.coords.x - centerChunk.x
            val dz = chunk.coords.y - centerChunk.y
            val dist2 = dx * dx + dz * dz
            dist2 > renderDistance * renderDistance
        }

        for (chunk in chunksToUnload) {
            chunks.remove(packChunkKey(chunk.coords.x, chunk.coords.y))
            chunk.unload() // optional cleanup
        }

// --- Collect coordinates in a circle ---
        val chunkCoords = mutableListOf<Pair<Long, Vector2i>>()
        for (dx in -renderDistance..renderDistance) {
            for (dz in -renderDistance..renderDistance) {
                if (dx*dx + dz*dz > renderDistance*renderDistance) continue // circular radius
                val chunkX = centerChunk.x + dx
                val chunkZ = centerChunk.y + dz
                val key = packChunkKey(chunkX, chunkZ)
                chunkCoords.add(key to Vector2i(chunkX, chunkZ))
            }
        }

// --- Sort by distance squared (closest first) ---
        chunkCoords.sortBy { (_, coords) ->
            val dx = coords.x - centerChunk.x
            val dz = coords.y - centerChunk.y
            dx*dx + dz*dz
        }

// --- Load/render chunks in order ---
        for ((key, coords) in chunkCoords) {
            val chunk = getChunk(coords.x, coords.y)
            if (chunk == null) {
                toLoadChunks[key] = coords
            } else {
                chunk.render(alpha)
            }
        }
    }

    fun tick() {
        dayNightTick += 1
        player.tick()
    }
}