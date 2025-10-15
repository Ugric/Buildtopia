package dev.wbell.buildtopia.app.game.session.world

import OpenSimplex2S.OpenSimplex2S
import dev.wbell.buildtopia.app.game.Game
import dev.wbell.buildtopia.app.game.camera.Camera
import dev.wbell.buildtopia.app.game.session.Session
import dev.wbell.buildtopia.app.game.session.world.chunk.Block.BlockRegistry
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
import org.lwjgl.glfw.GLFW.glfwGetTime
import org.lwjgl.opengl.GL20.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI


//fun main() {
//    val worldSeed: Long = 1L
//    val version = MCVersion.v1_16_5
//    val dimension = Dimension.OVERWORLD
//
//    val biomeSource = BiomeSource.of(dimension, version, worldSeed)
//    val terrainGenerator = TerrainGenerator.of(biomeSource)
//        ?: throw IllegalStateException("Failed to create TerrainGenerator")
//
//    val block: Optional<McBlock?>? = terrainGenerator.getBlockAt(0, 0, 0)
//    val column = terrainGenerator.getColumnAt(0, 0)
//
//    val surfaceGenHeight: Int = terrainGenerator.getFirstHeightInColumn(0, 0, TerrainGenerator.WORLD_SURFACE_WG)
//    val oceanGenHeight: Int = terrainGenerator.getFirstHeightInColumn(0, 0, TerrainGenerator.OCEAN_FLOOR_WG)
//    val surfaceBlockIn: Int = terrainGenerator.getHeightInGround(0, 0)
//    val surfaceBlockAbove: Int = terrainGenerator.getHeightOnGround(0, 0)
//
//    println("Block at (0,0,0): $block")
//    println("Surface height: $surfaceGenHeight")
//    println("Ocean floor height: $oceanGenHeight")
//}

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
    private val chunkRenderQueue = PriorityQueue<Pair<Chunk, Int>>(compareBy { it.second })
    private val chunkUpdateRenderQueue = PriorityQueue<Pair<Chunk, Int>>(compareBy { it.second })
    private val physics = CoroutineScope(Dispatchers.Default)
    private val chunks = ConcurrentHashMap<Long, Chunk>()
    private val toLoadChunks = ConcurrentHashMap<Long, Vector2i>()
    private var isActive = true
    var lastTick = glfwGetTime()
    var dayNightLoc = glGetUniformLocation(Game.shaderProgram, "dayNight")
    var dayNightTick = 12000

    val seed: Long = 1203223


    fun init() {

        startPhysics()
        startChunkRenderer()
        startChunkUpdateRenderer()
        startChunkLoaderRenderer()
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

    private fun markChunkDirtyUpdate(chunk: Chunk) {
        if (!chunk.isQueuedForRender) {
            val dx = chunk.coords.x
            val dz = chunk.coords.y
            val dist2 = dx * dx + dz * dz
            chunkUpdateRenderQueue.add(chunk to dist2)
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
                    var peak = -4 * ChunkSection.SIZE
                    val blockData = Array(ChunkSection.LENGTH * ChunkSection.LENGTH * ChunkSection.LENGTH * 24) { i ->
                        val x = i % ChunkSection.LENGTH
                        val z = (i / ChunkSection.LENGTH) % ChunkSection.LENGTH
                        val y = (i / (ChunkSection.LENGTH * ChunkSection.LENGTH)) - 0 * ChunkSection.LENGTH


                        // World coordinates
                        val worldX = coords.x * ChunkSection.LENGTH + x
                        val worldZ = coords.y * ChunkSection.LENGTH + z
                        val worldY = y

                        // ----- TERRAIN HEIGHT -----
                        val cont = OpenSimplex2S.noise2(seed + 1000, worldX * 0.0008, worldZ * 0.0008) // continents/ocean
                        val ridge = OpenSimplex2S.noise2(seed + 2000, worldX * 0.004, worldZ * 0.004) // mountains
                        val detail = OpenSimplex2S.noise2(seed + 3000, worldX * 0.02, worldZ * 0.02) // small variation

                        // Blend noises together
                        val baseHeight = 64.0 + cont * 30.0
                        val ridgeHeight = ridge * ridge * 50.0 // squared for more contrast
                        val terrainHeight = (baseHeight + ridgeHeight + detail * 3.0).toInt()

                        // ----- CAVES -----
                        fun caveDensity(x: Double, y: Double, z: Double): Double {
                            var total = 0.0
                            var freq = 0.01
                            var amp = 1.0
                            for (octave in 0 until 4) {
                                total += OpenSimplex2S.noise3_ImproveXY(seed + 4000 + octave * 500, x * freq, y * freq, z * freq) * amp
                                freq *= 2.0
                                amp *= 0.5
                            }
                            return total
                        }

                        val density = caveDensity(worldX.toDouble(), worldY.toDouble(), worldZ.toDouble())

                        // Caves taper out near the surface
                        val heightFactor = ((terrainHeight - worldY).toDouble() / 40.0).coerceIn(0.0, 1.0)
                        val isCave = density > 0.6 * heightFactor

                        // ----- BLOCK PLACEMENT -----
                        val block: Int = if (worldY <= terrainHeight && !isCave) {
                            when {
                                worldY == terrainHeight -> BlockRegistry.getIndex("minecraft:grass_block")
                                worldY >= terrainHeight - 4 -> BlockRegistry.getIndex("minecraft:dirt")
                                else -> BlockRegistry.getIndex("minecraft:stone")
                            }
                        } else BlockRegistry.getIndex("minecraft:air")
                        if (block != 0 && y > peak) peak = y
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

    private fun startChunkRenderer() {
        chunkRenderer.launch {
            val tickTime = 50L
            while (isActive) {
                for (chunk in chunks.values) {
                    if (chunk.toRenderMesh()) markChunkDirty(chunk)
                }
                repeat(10) {
                    val next = chunkRenderQueue.poll() ?: return@repeat
                    val (chunk, _) = next
                    chunk.isQueuedForRender = false

                    if (chunk.toRenderMesh()) {
                        chunk.renderMesh()
                        chunkMeshQueue.add { chunk.uploadChunkMesh() }
                    }
                }
                if (chunkRenderQueue.isEmpty()) delay(tickTime)
            }
        }
    }

    private fun startChunkUpdateRenderer() {
        chunkRenderer.launch {
            val tickTime = 50L
            while (isActive) {
                for (chunk in chunks.values) {
                    if (chunk.toUpdateMesh()) markChunkDirtyUpdate(chunk)
                }
                repeat(10) {
                    val next = chunkUpdateRenderQueue.poll() ?: return@repeat
                    val (chunk, _) = next
                    chunk.isQueuedForRender = false

                    if (chunk.toUpdateMesh()) {
                        chunk.renderMesh()
                        chunkMeshQueue.add { chunk.uploadChunkMesh() }
                    }
                }
                if (chunkUpdateRenderQueue.isEmpty()) delay(tickTime)
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
        override operator fun get(x: Int, y: Int, z: Int): Int {
            return getChunk(floorDiv(x, ChunkSection.LENGTH), floorDiv(z, ChunkSection.LENGTH))?.blocks[floorMod(
                x,
                ChunkSection.LENGTH
            ), y, floorMod(z, ChunkSection.LENGTH)]?:0
        }

        override operator fun set(x: Int, y: Int, z: Int, value: Int) {
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
        val renderDistance = Settings[SettingKey.RENDERDISTANCE] ?: 32

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
                if (dx * dx + dz * dz > renderDistance * renderDistance) continue // circular radius
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
            dx * dx + dz * dz
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