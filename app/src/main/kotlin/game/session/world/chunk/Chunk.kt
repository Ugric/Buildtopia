package dev.wbell.buildtopia.app.game.session.world.chunk

import dev.wbell.buildtopia.app.game.Game
import dev.wbell.buildtopia.app.game.session.world.World
import dev.wbell.buildtopia.app.game.session.world.chunk.Block.Block
import dev.wbell.buildtopia.app.game.session.world.chunk.Block.addFace
import dev.wbell.buildtopia.app.game.session.world.chunk.ChunkSection.Companion.LENGTH
import dev.wbell.buildtopia.app.game.session.world.floorDiv
import dev.wbell.buildtopia.app.game.session.world.floorMod
import org.joml.Matrix4d
import org.joml.Vector2i
import org.lwjgl.glfw.GLFW.glfwGetTime
import org.lwjgl.opengl.ARBVertexArrayObject.glBindVertexArray
import org.lwjgl.opengl.ARBVertexArrayObject.glGenVertexArrays
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL15.glBufferData
import org.lwjgl.opengl.GL15.glGenBuffers
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL20.glVertexAttribPointer
import org.lwjgl.opengl.GL30.*
import java.util.Collections


interface BlockAccessor {
    operator fun get(x: Int, y: Int, z: Int): Block?
    operator fun set(x: Int, y: Int, z: Int, value: Block?)
}

interface LightAccessor {
    operator fun get(x: Int, y: Int, z: Int): Int
    operator fun set(x: Int, y: Int, z: Int, value: Int)
}

class ChunkSection(val blockData: Array<Block?>, val chunk: Chunk, val index: Int) {
    companion object {
        const val LENGTH = 16
        const val SIZE = LENGTH * LENGTH * LENGTH
    }

    val sunLightData = ByteArray(LENGTH * LENGTH * LENGTH / 2) { 0 }
    val blockLightData = ByteArray(LENGTH * LENGTH * LENGTH / 2) { 0 }

    var sunlightDataCalculated = false

    fun calculateSunLight(): Boolean {
        if (sunlightDataCalculated) return false
        // 2. BFS propagation
        val queue: ArrayDeque<Triple<Int, Int, Int>> = ArrayDeque() // store coordinates only
        val propagation = arrayOf(
            intArrayOf(1, 0, 0),
            intArrayOf(-1, 0, 0),
            intArrayOf(0, 1, 0),
            intArrayOf(0, -1, 0),
            intArrayOf(0, 0, 1),
            intArrayOf(0, 0, -1)
        )

        // Seed the queue with all blocks that have sunlight > 1
        var blocked = Array(LENGTH*LENGTH){1}
        var toBreak = false
        for (y in LENGTH - 1 downTo 0) {
            val worldY = y + index * LENGTH - chunk.ySectionsOffset * LENGTH
            for (x in 0 until LENGTH) {
                for (z in 0 until LENGTH) {
                    val posInBlocked = x+z*LENGTH
                    if (blocked[posInBlocked]==0) continue
                    val worldX = x + chunk.coords.x * LENGTH
                    val worldZ = z + chunk.coords.y * LENGTH
                    val light = sunLights[x, y, z]
                    if (blocks[x,y,z] != null) {
                        blocked[posInBlocked] = 0
                        if (blocked.sum()==0) {
                            toBreak = true
                            break
                        }
                    }
                    if (light > 1) {
                        queue.addLast(Triple(worldX, worldY, worldZ))
                    }
                }
                if (toBreak) break
            }
            if (toBreak) break
        }

        // BFS
        while (queue.isNotEmpty()) {
            val (x, y, z) = queue.removeFirst()
            val currentLight = chunk.world.sunLights[x, y, z]

            for (dir in propagation) {
                val nx = x + dir[0]
                val ny = y + dir[1]
                val nz = z + dir[2]
                var newLightLevel = currentLight - 1

                if (dir[1] == -1) {
                    newLightLevel = currentLight
                }
                if (newLightLevel <= 0) continue

                // skip if block is solid or light is already >= newLightLevel
                if (chunk.world.blocks[nx, ny, nz] != null) continue
                if (chunk.world.sunLights[nx, ny, nz] >= newLightLevel) continue

                // update light immediately and enqueue
                chunk.world.sunLights[nx, ny, nz] = newLightLevel
                queue.addLast(Triple(nx, ny, nz))
            }
        }
        sunlightDataCalculated = true
        return toBreak
    }


    val sunLights = object : LightAccessor {
        override operator fun set(x: Int, y: Int, z: Int, value: Int) {
            val index = x + y * LENGTH + z * LENGTH * LENGTH
            val byteIndex = index / 2
            val high = index % 2 == 0
            val v = value.coerceIn(0, 15)
            val oldData = get(x, y, z)
            if (high) {
                sunLightData[byteIndex] = ((v shl 4) or (sunLightData[byteIndex].toInt() and 0x0F)).toByte()
            } else {
                sunLightData[byteIndex] = ((sunLightData[byteIndex].toInt() and 0xF0) or v).toByte()
            }
            if (x == 0) {
                chunk.world.getChunk(chunk.coords.x - 1, chunk.coords.y)?.getChunkSectionByIndex(index)?.updateMesh =
                    true
            }
            if (oldData != v  || !sunlightDataCalculated) {
                updateMesh = true
                sunlightDataCalculated = false
                var c: ChunkSection? = null
                if (z == 0) {
                    c = chunk.world.getChunk(chunk.coords.x, chunk.coords.y - 1)
                        ?.getChunkSectionByIndex(index)
                    c?.updateMesh = true
                    c?.sunlightDataCalculated = false
                    if (x == 0) {
                        c = chunk.world.getChunk(chunk.coords.x - 1, chunk.coords.y - 1)
                            ?.getChunkSectionByIndex(index)
                        c?.updateMesh = true
                        c?.sunlightDataCalculated = false
                    }
                    if (x == LENGTH - 1) {
                        c = chunk.world.getChunk(chunk.coords.x + 1, chunk.coords.y - 1)
                            ?.getChunkSectionByIndex(index)
                        c?.updateMesh = true
                        c?.sunlightDataCalculated = false
                    }
                }
                if (x == 0) {
                    c = chunk.world.getChunk(chunk.coords.x - 1, chunk.coords.y)
                        ?.getChunkSectionByIndex(index)
                    c?.updateMesh = true
                    c?.sunlightDataCalculated = false
                    if (z == 0) {
                        c = chunk.world.getChunk(chunk.coords.x - 1, chunk.coords.y - 1)
                            ?.getChunkSectionByIndex(index)
                        c?.updateMesh = true
                        c?.sunlightDataCalculated = false
                    }
                    if (z == LENGTH - 1) {
                        c = chunk.world.getChunk(chunk.coords.x - 1, chunk.coords.y + 1)
                            ?.getChunkSectionByIndex(index)
                        c?.updateMesh = true
                        c?.sunlightDataCalculated = false
                    }
                }
                if (x == LENGTH - 1) {
                    c = chunk.world.getChunk(chunk.coords.x + 1, chunk.coords.y)
                        ?.getChunkSectionByIndex(index)
                    if (z == 0) {
                        c = chunk.world.getChunk(chunk.coords.x + 1, chunk.coords.y - 1)
                            ?.getChunkSectionByIndex(index)
                        c?.updateMesh = true
                        c?.sunlightDataCalculated = false
                    }
                    if (z == LENGTH - 1) {
                        c = chunk.world.getChunk(chunk.coords.x + 1, chunk.coords.y + 1)
                            ?.getChunkSectionByIndex(index)
                        c?.updateMesh = true
                        c?.sunlightDataCalculated = false
                    }
                }
                if (z == LENGTH - 1) {
                    c = chunk.world.getChunk(chunk.coords.x, chunk.coords.y + 1)
                        ?.getChunkSectionByIndex(index)
                    c?.updateMesh = true
                    c?.sunlightDataCalculated = false
                    if (x == 0) {
                        c = chunk.world.getChunk(chunk.coords.x - 1, chunk.coords.y + 1)
                            ?.getChunkSectionByIndex(index)
                        c?.updateMesh = true
                        c?.sunlightDataCalculated = false
                    }
                    if (x == LENGTH - 1) {
                        c = chunk.world.getChunk(chunk.coords.x + 1, chunk.coords.y + 1)
                            ?.getChunkSectionByIndex(index)
                        c?.updateMesh = true
                        c?.sunlightDataCalculated = false
                    }
                }
                if (y == 0) {
                    c = chunk.getChunkSectionByIndex(index - 1)
                    c?.updateMesh = true
                    c?.sunlightDataCalculated = false
                    if (z == 0) {
                        c = chunk.world.getChunk(chunk.coords.x, chunk.coords.y - 1)
                            ?.getChunkSectionByIndex(index - 1)
                        c?.updateMesh = true
                        c?.sunlightDataCalculated = false
                    }
                    if (x == 0) {
                        c = chunk.world.getChunk(chunk.coords.x - 1, chunk.coords.y)
                            ?.getChunkSectionByIndex(index - 1)
                        c?.updateMesh = true
                        c?.sunlightDataCalculated = false
                    }
                    if (z == LENGTH - 1) {
                        c = chunk.world.getChunk(chunk.coords.x, chunk.coords.y + 1)
                            ?.getChunkSectionByIndex(index - 1)
                        c?.updateMesh = true
                        c?.sunlightDataCalculated = false
                    }
                    if (x == LENGTH - 1) {
                        c = chunk.world.getChunk(chunk.coords.x + 1, chunk.coords.y)
                            ?.getChunkSectionByIndex(index - 1)
                        c?.updateMesh = true
                        c?.sunlightDataCalculated = false
                    }
                    if (z == 0 && x == 0) {
                        c = chunk.world.getChunk(chunk.coords.x - 1, chunk.coords.y - 1)
                            ?.getChunkSectionByIndex(index - 1)
                        c?.updateMesh = true
                        c?.sunlightDataCalculated = false
                    }
                    if (z == LENGTH - 1 && x == LENGTH - 1) {
                        c = chunk.world.getChunk(chunk.coords.x + 1, chunk.coords.y + 1)
                            ?.getChunkSectionByIndex(index - 1)
                        c?.updateMesh = true
                        c?.sunlightDataCalculated = false
                    }
                }
                if (y == LENGTH - 1) {
                    c = chunk.getChunkSectionByIndex(index + 1)
                    c?.updateMesh = true
                    c?.sunlightDataCalculated = false

                    if (z == 0) {
                        c = chunk.world.getChunk(chunk.coords.x, chunk.coords.y - 1)
                            ?.getChunkSectionByIndex(index + 1)
                        c?.updateMesh = true
                        c?.sunlightDataCalculated = false
                    }
                    if (x == 0) {
                        c = chunk.world.getChunk(chunk.coords.x - 1, chunk.coords.y)
                            ?.getChunkSectionByIndex(index + 1)
                        c?.updateMesh = true
                        c?.sunlightDataCalculated = false
                    }
                    if (z == LENGTH - 1) {
                        c = chunk.world.getChunk(chunk.coords.x, chunk.coords.y + 1)
                            ?.getChunkSectionByIndex(index + 1)
                        c?.updateMesh = true
                        c?.sunlightDataCalculated = false
                    }
                    if (x == LENGTH - 1) {
                        c = chunk.world.getChunk(chunk.coords.x + 1, chunk.coords.y)
                            ?.getChunkSectionByIndex(index + 1)
                        c?.updateMesh = true
                        c?.sunlightDataCalculated = false
                    }
                    if (z == 0 && x == 0) {
                        c = chunk.world.getChunk(chunk.coords.x - 1, chunk.coords.y - 1)
                            ?.getChunkSectionByIndex(index + 1)
                        c?.updateMesh = true
                        c?.sunlightDataCalculated = false
                    }
                    if (z == LENGTH - 1 && x == LENGTH - 1) {
                        c = chunk.world.getChunk(chunk.coords.x + 1, chunk.coords.y + 1)
                            ?.getChunkSectionByIndex(index + 1)
                        c?.updateMesh = true
                        c?.sunlightDataCalculated = false
                    }
                }
            }
        }

        override operator fun get(x: Int, y: Int, z: Int): Int {
            val index = x + y * LENGTH + z * LENGTH * LENGTH
            val byteIndex = index / 2
            val high = index % 2 == 0
            return if (high) {
                (sunLightData[byteIndex].toInt() shr 4) and 0x0F
            } else {
                sunLightData[byteIndex].toInt() and 0x0F
            }
        }
    }

    val blockLights = object : LightAccessor {
        override operator fun set(x: Int, y: Int, z: Int, value: Int) {
            val index = x + y * LENGTH + z * LENGTH * LENGTH
            val byteIndex = index / 2
            val high = index % 2 == 0
            val v = value.coerceIn(0, 15)

            if (high) {
                blockLightData[byteIndex] = ((v shl 4) or (blockLightData[byteIndex].toInt() and 0x0F)).toByte()
            } else {
                blockLightData[byteIndex] = ((blockLightData[byteIndex].toInt() and 0xF0) or v).toByte()
            }
            updateMesh = true
            if (z == 0) chunk.world.getChunk(chunk.coords.x, chunk.coords.y - 1)
                ?.getChunkSectionByIndex(index)?.updateMesh = true
            if (x == LENGTH - 1) chunk.world.getChunk(chunk.coords.x + 1, chunk.coords.y)
                ?.getChunkSectionByIndex(index)?.updateMesh = true
            if (z == LENGTH - 1) chunk.world.getChunk(chunk.coords.x, chunk.coords.y + 1)
                ?.getChunkSectionByIndex(index)?.updateMesh = true
            if (y == 0) chunk.getChunkSectionByIndex(index - 1)?.updateMesh = true
            if (y == LENGTH - 1) chunk.getChunkSectionByIndex(index + 1)?.updateMesh = true
        }

        override operator fun get(x: Int, y: Int, z: Int): Int {
            val index = x + y * LENGTH + z * LENGTH * LENGTH
            val byteIndex = index / 2
            val high = index % 2 == 0
            return if (high) {
                (blockLightData[byteIndex].toInt() shr 4) and 0x0F
            } else {
                blockLightData[byteIndex].toInt() and 0x0F
            }
        }
    }

    val blocks = object : BlockAccessor {
        override operator fun get(x: Int, y: Int, z: Int): Block? {
            val index = x + z * LENGTH + y * LENGTH * LENGTH
            if ((x !in 0..<LENGTH) || (z !in 0..<LENGTH) || (y !in 0..<LENGTH)) return null
            return blockData[index]
        }

        override operator fun set(x: Int, y: Int, z: Int, value: Block?) {
            val index = x + z * LENGTH + y * LENGTH * LENGTH
            if (index < 0 || index >= blockData.size) return
            blockData[index] = value
            updateMesh = true
            if (z == 0) chunk.world.getChunk(chunk.coords.x, chunk.coords.y - 1)
                ?.getChunkSectionByIndex(index)?.updateMesh = true
            if (x == LENGTH - 1) chunk.world.getChunk(chunk.coords.x + 1, chunk.coords.y)
                ?.getChunkSectionByIndex(index)?.updateMesh = true
            if (z == LENGTH - 1) chunk.world.getChunk(chunk.coords.x, chunk.coords.y + 1)
                ?.getChunkSectionByIndex(index)?.updateMesh = true
            if (y == 0) chunk.getChunkSectionByIndex(index - 1)?.updateMesh = true
            if (y == LENGTH - 1) chunk.getChunkSectionByIndex(index + 1)?.updateMesh = true
        }
    }

    var meshExists: Boolean = false
    var meshRendered: Boolean = false
    var vao: Int = 0
    var vaoSize: Int = 0
    var vbo: Int = 0
    var timeMeshCreated = 0.0
    var updateMesh: Boolean = true
    var verticesForUpload = Collections.synchronizedList(mutableListOf<Float>())
    var verticesReadyForUpload = false

    fun renderMesh(): Boolean {
        if (!updateMesh) return false
        verticesForUpload.clear()
        setVertices(verticesForUpload)
        updateMesh = false
        verticesReadyForUpload = true
        return true
    }

    fun uploadMesh() {
        if (!verticesReadyForUpload) return

        val newVertexCount = verticesForUpload.size

        if (verticesForUpload.isEmpty()) {
            // If no vertices, clear mesh
            if (meshExists) {
                glDeleteBuffers(vbo)
                glDeleteVertexArrays(vao)
            }
            meshExists = false
            meshRendered = true
            verticesReadyForUpload = false
            timeMeshCreated = glfwGetTime()
            vao = 0
            vbo = 0
            vaoSize = 0
            return
        }

        if (!meshExists) {
            // Create VAO/VBO first time
            vao = glGenVertexArrays()
            vbo = glGenBuffers()
            glBindVertexArray(vao)
            glBindBuffer(GL_ARRAY_BUFFER, vbo)

            // allocate buffer large enough
            glBufferData(GL_ARRAY_BUFFER, newVertexCount.toLong() * 4, GL_DYNAMIC_DRAW)

            val stride = 7 * 4
            glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0)
            glEnableVertexAttribArray(0)
            glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * 4)
            glEnableVertexAttribArray(1)
            glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 5 * 4L)
            glEnableVertexAttribArray(2)
            glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 6 * 4L)
            glEnableVertexAttribArray(3)

            glBindBuffer(GL_ARRAY_BUFFER, 0)
            glBindVertexArray(0)

            meshExists = true
        }

        // Update contents
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        if (newVertexCount > vaoSize) {
            // Reallocate if new data won't fit
            synchronized(verticesForUpload) {
                glBufferData(GL_ARRAY_BUFFER, verticesForUpload.toFloatArray(), GL_DYNAMIC_DRAW)
            }
        } else {
            // Just update existing buffer
            synchronized(verticesForUpload) {
                glBufferSubData(GL_ARRAY_BUFFER, 0, verticesForUpload.toFloatArray())
            }
        }
        glBindBuffer(GL_ARRAY_BUFFER, 0)

        vaoSize = newVertexCount
        updateMesh = false
        verticesReadyForUpload = false
        meshRendered = true
        verticesForUpload.clear()
        timeMeshCreated = glfwGetTime()
    }

    fun render(alpha: Double) {
        if (!meshExists) return
        val vertexCount = vaoSize / 7  // <-- changed

        // Bind texture
        glBindTexture(GL_TEXTURE_2D, Game.textureId)
        glUniform1i(glGetUniformLocation(Game.shaderProgram, "texture1"), 0)
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL)

        // Build model matrix per chunk
        val model = Matrix4d()
            .identity()
            .translate(
                chunk.coords.x * LENGTH - chunk.world.camera.position.x,
                -chunk.world.camera.position.y,
                chunk.coords.y * LENGTH - chunk.world.camera.position.z
            )

        // Upload model matrix
        val modelBuf = DoubleArray(16)
        model.get(modelBuf)
        val modelFloatBuf = FloatArray(16) { modelBuf[it].toFloat() }
        glUniformMatrix4fv(Game.modelLoc, false, modelFloatBuf)

        // Draw
        glBindVertexArray(vao)
        glDrawArrays(GL_TRIANGLES, 0, vertexCount)
        glBindVertexArray(0)
    }

    fun setVertices(vertices: MutableList<Float>) {
        for (x in 0..<LENGTH) {
            for (sectionY in 0..<LENGTH) {
                val y = sectionY + LENGTH * index - chunk.ySectionsOffset * LENGTH
                for (z in 0..<LENGTH) {
                    if (blocks[x, sectionY, z] == null) continue;
                    val worldX = x + chunk.coords.x * LENGTH
                    val worldZ = z + chunk.coords.y * LENGTH
                    if (chunk.world.blocks[worldX, y + 1, worldZ] == null) addFace(
                        vertices,
                        x,
                        y,
                        z,
                        chunk.coords,
                        1f,
                        "top"
                    )
                    if (chunk.world.blocks[worldX, y - 1, worldZ] == null) addFace(
                        vertices,
                        x,
                        y,
                        z,
                        chunk.coords,
                        1f,
                        "bottom"
                    )
                    if (chunk.world.blocks[worldX + 1, y, worldZ] == null) addFace(
                        vertices,
                        x,
                        y,
                        z,
                        chunk.coords,
                        1f,
                        "right"
                    )
                    if (chunk.world.blocks[worldX - 1, y, worldZ] == null) addFace(
                        vertices,
                        x,
                        y,
                        z,
                        chunk.coords,
                        1f,
                        "left"
                    )
                    if (chunk.world.blocks[worldX, y, worldZ + 1] == null) addFace(
                        vertices,
                        x,
                        y,
                        z,
                        chunk.coords,
                        1f,
                        "front"
                    )
                    if (chunk.world.blocks[worldX, y, worldZ - 1] == null) addFace(
                        vertices,
                        x,
                        y,
                        z,
                        chunk.coords,
                        1f,
                        "back"
                    )
                }
            }
        }
    }

    fun unload() {
        if (meshExists) {
            glDeleteBuffers(vbo)
            glDeleteVertexArrays(vao)
            meshExists = false
        }
        updateMesh = false
    }
}

class Chunk(
    val world: World,
    val coords: Vector2i,
    val numberOfSections: Int,
    val ySectionsOffset: Int,
    var blockData: Array<Block?>?,
    val peak: Int
) {
    var sections: Array<ChunkSection> = Array(numberOfSections) { sectionIndex ->
        ChunkSection(Array(ChunkSection.SIZE) { i ->
            val x = i % LENGTH
            val z = (i / LENGTH) % LENGTH
            val y = (i / (LENGTH * LENGTH)) + sectionIndex * LENGTH
            blockData!![x + z * LENGTH + y * LENGTH * LENGTH]
        }, this, sectionIndex)
    }



    fun resetSunLighting() {
        for (section in sections) {
            section.sunlightDataCalculated = false
            section.updateMesh = true
        }
    }

    init {
        blockData = null
    }

    var loaded = true
    var verticalSunCalculated = false
    fun initVerticalSun() {
        if (!loaded || verticalSunCalculated) return

        val topY = (numberOfSections - ySectionsOffset) * LENGTH - 1
        val bottomY = peak + 1

        for (x in 0 until LENGTH) {
            for (z in 0 until LENGTH) {
                for (y in topY downTo bottomY) {
                    sunLights[x, y, z] = 15
                    if (peak < y - LENGTH) getChunkSection(y)?.sunlightDataCalculated = true
                }
            }
        }

        verticalSunCalculated = true

        for (dx in -1 .. 1) {
            for (dz in -1 .. 1) {
                if (dx == 0 && dz == 0) continue
                world.getChunk(coords.x+dx,coords.y+dz)?.resetSunLighting()
            }
        }
    }

    fun getChunkSectionByIndex(index: Int): ChunkSection? {
        if (!loaded) return null
        if (index !in 0..<numberOfSections) return null
        return sections[index]
    }

    fun getChunkSection(y: Int): ChunkSection? {
        if (!loaded) return null
        val sectionIndex = floorDiv((y + ySectionsOffset * LENGTH), LENGTH)
        if (sectionIndex !in 0..<numberOfSections) return null
        return sections[sectionIndex]
    }

    val blocks = object : BlockAccessor {
        override operator fun get(x: Int, y: Int, z: Int): Block? {
            if (!loaded) return null
            return getChunkSection(y)?.blocks[x, floorMod(y, LENGTH), z]
        }

        override operator fun set(x: Int, y: Int, z: Int, value: Block?) {
            if (!loaded) return
            getChunkSection(y)?.blocks[x, floorMod(y, LENGTH), z] = value
        }
    }

    val sunLights = object : LightAccessor {
        override operator fun set(x: Int, y: Int, z: Int, value: Int) {
            if (!loaded) return
            getChunkSection(y)?.sunLights[x, floorMod(y, LENGTH), z] = value
        }

        override operator fun get(x: Int, y: Int, z: Int): Int {
            if (!loaded) return if (y > 0) 15 else 0
            return getChunkSection(y)?.sunLights[x, floorMod(y, LENGTH), z] ?: if (y > 0) 15 else 0
        }
    }

    val blockLights = object : LightAccessor {
        override operator fun set(x: Int, y: Int, z: Int, value: Int) {
            if (!loaded) return
            getChunkSection(y)?.blockLights[x, floorMod(y, LENGTH), z] = value
        }

        override operator fun get(x: Int, y: Int, z: Int): Int {
            if (!loaded) return 0
            return getChunkSection(y)?.blockLights[x, floorMod(y, LENGTH), z] ?: 0
        }
    }

    var isQueuedForRender = false

    fun toRenderMesh(): Boolean {
        if (!loaded) return false
        var toRender = false
        for (i in 0..<numberOfSections) {
            if (sections[i].updateMesh && !sections[i].meshRendered) toRender = true
        }
        return toRender
    }

    fun toUpdateMesh(): Boolean {
        if (!loaded) return false
        var toRender = false
        for (i in 0..<numberOfSections) {
            if (sections[i].updateMesh && sections[i].meshRendered) toRender = true
        }
        return toRender
    }

    fun renderMesh(): Boolean {
        if (!loaded) return false
        initVerticalSun()
        var hasRendered = false
        var toCalculateSunLight = true
        for (i in (numberOfSections - 1) downTo 0) {
            if (sections[i].updateMesh) {
                if (toCalculateSunLight) toCalculateSunLight = !sections[i].calculateSunLight()
                sections[i].renderMesh()
                hasRendered = true
            }
        }
        return hasRendered
//        if (meshExists) {
//            glDeleteBuffers(vbo)
//            glDeleteVertexArrays(vao)
//        }
//        vao = glGenVertexArrays()
//        vbo = glGenBuffers()
//        glBindVertexArray(vao)
//        glBindBuffer(GL_ARRAY_BUFFER, vbo)
//        glBufferData(GL_ARRAY_BUFFER, vertices.toFloatArray(), GL_STATIC_DRAW)
//
//        val stride = 5 * 4 // 5 floats * 4 bytes
//        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0)       // position
//        glEnableVertexAttribArray(0)
//        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * 4)   // tex coords
//        glEnableVertexAttribArray(1)
//
//        glBindBuffer(GL_ARRAY_BUFFER, 0)
//        glBindVertexArray(0)
//
//        vaoSize = vertices.size;
//        meshExists = true
    }

    fun uploadChunkMesh() {
        if (!loaded) return
        for (i in 0..<numberOfSections) {
            sections[i].uploadMesh()
        }
    }


    fun render(alpha: Double) {
        if (!loaded) return
        for (i in 0..<numberOfSections) {
            sections[i].render(alpha)
        }
    }

    fun unload() {
        sections.forEach { it.unload() }
        loaded = false
    }
}