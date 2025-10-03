package dev.wbell.buildtopia.app.game.session.world.chunk

import dev.wbell.buildtopia.app.game.Game
import dev.wbell.buildtopia.app.game.session.world.World
import dev.wbell.buildtopia.app.game.session.world.chunk.Block.Block
import dev.wbell.buildtopia.app.game.session.world.chunk.Block.addFace
import dev.wbell.buildtopia.app.game.session.world.floorDiv
import dev.wbell.buildtopia.app.game.session.world.floorMod
import org.joml.Matrix4d
import org.joml.Vector2i
import org.joml.Vector3i
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

    val sunLightData = ByteArray(LENGTH * LENGTH * LENGTH / 2) { 0xff.toByte() }
    val blockLightData = ByteArray(LENGTH * LENGTH * LENGTH / 2) { 0 }

    fun calculateVerticalSunlight() {
        // 1. Initialize sunlight in this chunk top-down
        for (y in LENGTH - 1 downTo 0) {
            val worldY = y + index * LENGTH - chunk.ySectionsOffset * LENGTH
            for (x in 0 until LENGTH) {
                for (z in 0 until LENGTH) {
                    sunLights[x, y, z] = if (blocks[x, y, z] != null) 0 else chunk.sunLights[x, worldY + 1, z]
                }
            }
        }
    }

    fun calculateLighting() {
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
        for (y in LENGTH - 1 downTo 0) {
            val worldY = y + index * LENGTH - chunk.ySectionsOffset * LENGTH
            for (x in 0 until LENGTH) {
                for (z in 0 until LENGTH) {
                    val worldX = x + chunk.coords.x * LENGTH
                    val worldZ = z + chunk.coords.y * LENGTH
                    val light = chunk.world.sunLights[worldX, worldY, worldZ]
                    if (light > 1) {
                        queue.addLast(Triple(worldX, worldY, worldZ))
                    }
                }
            }
        }

        // BFS
        while (queue.isNotEmpty()) {
            val (x, y, z) = queue.removeFirst()
            val currentLight = chunk.world.sunLights[x, y, z]
            val newLightLevel = currentLight - 1
            if (newLightLevel <= 0) continue

            for (dir in propagation) {
                val nx = x + dir[0]
                val ny = y + dir[1]
                val nz = z + dir[2]

                // skip if block is solid or light is already >= newLightLevel
                if (chunk.world.blocks[nx, ny, nz] != null) continue
                if (chunk.world.sunLights[nx, ny, nz] >= newLightLevel) continue

                // update light immediately and enqueue
                chunk.world.sunLights[nx, ny, nz] = newLightLevel
                queue.addLast(Triple(nx, ny, nz))
            }
        }
    }


    val sunLights = object : LightAccessor {
        override operator fun set(x: Int, y: Int, z: Int, value: Int) {
            val index = x + y * LENGTH + z * LENGTH * LENGTH
            val byteIndex = index / 2
            val high = index % 2 == 0
            val v = value.coerceIn(0, 15)

            if (high) {
                sunLightData[byteIndex] = ((v shl 4) or (sunLightData[byteIndex].toInt() and 0x0F)).toByte()
            } else {
                sunLightData[byteIndex] = ((sunLightData[byteIndex].toInt() and 0xF0) or v).toByte()
            }
            updateMesh = true
            if (x == 0) {
                chunk.world.getChunk(chunk.coords.x - 1, chunk.coords.y)?.getChunkSectionByIndex(index)?.updateMesh =
                    true
            }
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
    var vao: Int = 0
    var vaoSize: Int = 0
    var vbo: Int = 0
    var timeMeshCreated = 0.0
    var updateMesh: Boolean = true
    var verticesForUpload: MutableList<Float>? = null

    fun renderMesh(): Boolean {
        if (!updateMesh) return false
        verticesForUpload = mutableListOf<Float>()
        setVertices(verticesForUpload!!)
        updateMesh = false
        return true
    }

    fun uploadMesh() {
        if (verticesForUpload == null) return

        val newVertexCount = verticesForUpload!!.size

        if (verticesForUpload!!.isEmpty()) {
            // If no vertices, clear mesh
            if (meshExists) {
                glDeleteBuffers(vbo)
                glDeleteVertexArrays(vao)
            }
            meshExists = false
            verticesForUpload = null
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
            glBufferData(GL_ARRAY_BUFFER, verticesForUpload!!.toFloatArray(), GL_DYNAMIC_DRAW)
        } else {
            // Just update existing buffer
            glBufferSubData(GL_ARRAY_BUFFER, 0, verticesForUpload!!.toFloatArray())
        }
        glBindBuffer(GL_ARRAY_BUFFER, 0)

        vaoSize = newVertexCount
        updateMesh = false
        verticesForUpload = null
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
}

class Chunk(
    val world: World,
    val coords: Vector2i,
    val numberOfSections: Int,
    val ySectionsOffset: Int,
    val blockData: Array<Block?>
) {
    var sections: Array<ChunkSection> = Array(numberOfSections) { sectionIndex ->
        ChunkSection(Array(ChunkSection.SIZE) { i ->
            val x = i % ChunkSection.LENGTH
            val z = (i / ChunkSection.LENGTH) % ChunkSection.LENGTH
            val y = (i / (ChunkSection.LENGTH * ChunkSection.LENGTH)) + sectionIndex * ChunkSection.LENGTH
            blockData[x + z * ChunkSection.LENGTH + y * ChunkSection.LENGTH * ChunkSection.LENGTH]
        }, this, sectionIndex)
    }

    fun init() {
        sections.forEach { it.calculateVerticalSunlight() }
    }

    fun getChunkSectionByIndex(index: Int): ChunkSection? {
        if (index !in 0..<numberOfSections) return null
        return sections[index]
    }

    fun getChunkSection(y: Int): ChunkSection? {
        val sectionIndex = floorDiv((y + ySectionsOffset * ChunkSection.LENGTH), ChunkSection.LENGTH)
        if (sectionIndex !in 0..<numberOfSections) return null
        return sections[sectionIndex]
    }

    val blocks = object : BlockAccessor {
        override operator fun get(x: Int, y: Int, z: Int): Block? {
            return getChunkSection(y)?.blocks[x, floorMod(y, ChunkSection.LENGTH), z]
        }

        override operator fun set(x: Int, y: Int, z: Int, value: Block?) {
            getChunkSection(y)?.blocks[x, floorMod(y, ChunkSection.LENGTH), z] = value
        }
    }

    fun initializeSunlight() {
        for (sectionIndex in (numberOfSections - 1) downTo 0) {
            getChunkSection(sectionIndex)?.calculateLighting()
        }
    }

    val sunLights = object : LightAccessor {
        override operator fun set(x: Int, y: Int, z: Int, value: Int) {
            getChunkSection(y)?.sunLights[x, floorMod(y, ChunkSection.LENGTH), z] = value
        }

        override operator fun get(x: Int, y: Int, z: Int): Int {
            return getChunkSection(y)?.sunLights[x, floorMod(y, ChunkSection.LENGTH), z] ?: if (y > 0) 15 else 0
        }
    }

    val blockLights = object : LightAccessor {
        override operator fun set(x: Int, y: Int, z: Int, value: Int) {
            getChunkSection(y)?.blockLights[x, floorMod(y, ChunkSection.LENGTH), z] = value
        }

        override operator fun get(x: Int, y: Int, z: Int): Int {
            return getChunkSection(y)?.blockLights[x, floorMod(y, ChunkSection.LENGTH), z] ?: 0
        }
    }

    fun toRenderMesh(): Boolean {
        var toRender = false
        for (i in 0..<numberOfSections) {
            if (sections[i].updateMesh) toRender = true
        }
        return toRender
    }

    fun renderMesh(): Boolean {
        var hasRendered = false
        for (i in 0..<numberOfSections) {
            if (sections[i].renderMesh()) hasRendered = true
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
        for (i in 0..<numberOfSections) {
            sections[i].uploadMesh()
        }
    }


    fun render(alpha: Double) {
        for (i in 0..<numberOfSections) {
            sections[i].render(alpha)
        }
    }

    fun unload() {}
}