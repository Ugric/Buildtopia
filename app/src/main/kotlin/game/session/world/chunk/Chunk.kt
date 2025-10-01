package dev.wbell.buildtopia.app.game.session.world.chunk

import dev.wbell.buildtopia.app.game.Game
import dev.wbell.buildtopia.app.game.session.world.World
import dev.wbell.buildtopia.app.game.session.world.chunk.Block.Block
import dev.wbell.buildtopia.app.game.session.world.chunk.Block.addFace
import dev.wbell.buildtopia.app.game.session.world.floorDiv
import dev.wbell.buildtopia.app.game.session.world.floorMod
import jogamp.common.os.elf.Section
import org.joml.Matrix4d
import org.joml.Matrix4f
import org.joml.Vector2d
import org.joml.Vector2i
import org.joml.Vector3d
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.glfwGetTime
import org.lwjgl.opengl.ARBVertexArrayObject.glBindVertexArray
import org.lwjgl.opengl.ARBVertexArrayObject.glGenVertexArrays
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.GL_STATIC_DRAW
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL15.glBufferData
import org.lwjgl.opengl.GL15.glGenBuffers
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL20.glVertexAttribPointer
import org.lwjgl.opengl.GL30.*
import kotlin.div
import kotlin.random.Random


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

    val sunLightData = ByteArray(LENGTH * LENGTH * LENGTH / 2)
    val blockLightData = ByteArray(LENGTH * LENGTH * LENGTH / 2)


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
//            if (x == 0) chunk.world.getChunk(chunk.coords.x - 1, chunk.coords.y)?.updateChunk = true
//            if (x == LENGTH - 1) chunk.world.getChunk(chunk.coords.x + 1, chunk.coords.y)?.updateChunk = true
//            if (z == 0) chunk.world.getChunk(chunk.coords.x, chunk.coords.y - 1)?.updateChunk = true
//            if (z == LENGTH - 1) chunk.world.getChunk(chunk.coords.x, chunk.coords.y + 1)?.updateChunk = true
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
        if (meshExists) {
            glDeleteBuffers(vbo)
            glDeleteVertexArrays(vao)
        }
        if (verticesForUpload!!.isEmpty()) {
            meshExists = false
            updateMesh = false
            verticesForUpload = null
            timeMeshCreated = glfwGetTime()
            this.vao = 0
            this.vbo = 0
            return
        }
        this.vao = glGenVertexArrays()
        this.vbo = glGenBuffers()
        glBindVertexArray(vao)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferData(GL_ARRAY_BUFFER, verticesForUpload!!.toFloatArray(), GL_STATIC_DRAW)

        val stride = 7 * 4 // 7 floats * 4 bytes
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0)       // position
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * 4)   // tex coords
        glEnableVertexAttribArray(1)


        // Block light
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, (5 * 4).toLong())
        glEnableVertexAttribArray(2)

        // Sun light
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, (6 * 4).toLong())
        glEnableVertexAttribArray(3)

        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindVertexArray(0)

        this.vaoSize = verticesForUpload!!.size;
        meshExists = true
        timeMeshCreated = glfwGetTime()
        verticesForUpload = null
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
                    if (chunk.world.blocks[worldX, y + 1, worldZ] == null) addFace(vertices, x, y, z, chunk.coords, 1f, "top")
                    if (chunk.world.blocks[worldX, y - 1, worldZ] == null) addFace(vertices, x, y, z, chunk.coords, 1f, "bottom")
                    if (chunk.world.blocks[worldX + 1, y, worldZ] == null) addFace(vertices, x, y, z, chunk.coords, 1f, "right")
                    if (chunk.world.blocks[worldX - 1, y, worldZ] == null) addFace(vertices, x, y, z, chunk.coords, 1f, "left")
                    if (chunk.world.blocks[worldX, y, worldZ + 1] == null) addFace(vertices, x, y, z, chunk.coords, 1f, "front")
                    if (chunk.world.blocks[worldX, y, worldZ - 1] == null) addFace(vertices, x, y, z, chunk.coords, 1f, "back")
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
    private var sections: Array<ChunkSection> = Array(numberOfSections) { sectionIndex ->
        ChunkSection(Array(ChunkSection.SIZE) { i ->
            val x = i % ChunkSection.LENGTH
            val z = (i / ChunkSection.LENGTH) % ChunkSection.LENGTH
            val y = (i / (ChunkSection.LENGTH * ChunkSection.LENGTH)) + sectionIndex * ChunkSection.LENGTH
            blockData[x + z * ChunkSection.LENGTH + y * ChunkSection.LENGTH * ChunkSection.LENGTH]
        }, this, sectionIndex)
    }
    val blocks = object : BlockAccessor {
        override operator fun get(x: Int, y: Int, z: Int): Block? {
            val sectionIndex = floorDiv((y + ySectionsOffset * ChunkSection.LENGTH), ChunkSection.LENGTH)
            if (sectionIndex !in 0..<numberOfSections) return null
            return sections[sectionIndex].blocks[x, floorMod(y, ChunkSection.LENGTH), z]
        }

        override operator fun set(x: Int, y: Int, z: Int, value: Block?) {
            val sectionIndex = floorDiv((y - ySectionsOffset * ChunkSection.LENGTH), ChunkSection.LENGTH)
            if (sectionIndex !in 0..<numberOfSections) return
            sections[sectionIndex].blocks[x, floorMod(y, ChunkSection.LENGTH), z] = value
        }
    }

    fun initializeSunlight() {
        for (x in 0 until ChunkSection.LENGTH) {
            for (z in 0 until ChunkSection.LENGTH) {
                var sunLevel = 15
                for (sectionIndex in (numberOfSections - 1) downTo 0) {
                    val section = sections[sectionIndex]
                    for (y in ChunkSection.LENGTH - 1 downTo 0) {
                        val globalY = sectionIndex * ChunkSection.LENGTH + y - ySectionsOffset * ChunkSection.LENGTH
                        val block = section.blocks[x, y, z]
                        if (block != null) sunLevel = 0
                        section.sunLights[x, y, z] = sunLevel
                    }
                }
            }
        }
    }

    val sunLights = object : LightAccessor {
        override operator fun set(x: Int, y: Int, z: Int, value: Int) {
            val sectionIndex = floorDiv((y - ySectionsOffset * ChunkSection.LENGTH), ChunkSection.LENGTH)
            if (sectionIndex !in 0..<numberOfSections) return
            sections[sectionIndex].sunLights[x, floorMod(y, ChunkSection.LENGTH), z] = value
        }

        override operator fun get(x: Int, y: Int, z: Int): Int {
            val sectionIndex = floorDiv((y + ySectionsOffset * ChunkSection.LENGTH), ChunkSection.LENGTH)
            if (sectionIndex !in 0..<numberOfSections) return 0
            return sections[sectionIndex].sunLights[x, floorMod(y, ChunkSection.LENGTH), z]
        }
    }

    val blockLights = object : LightAccessor {
        override operator fun set(x: Int, y: Int, z: Int, value: Int) {
            val sectionIndex = floorDiv((y - ySectionsOffset * ChunkSection.LENGTH), ChunkSection.LENGTH)
            if (sectionIndex !in 0..<numberOfSections) return
            sections[sectionIndex].blockLights[x, floorMod(y, ChunkSection.LENGTH), z] = value
        }

        override operator fun get(x: Int, y: Int, z: Int): Int {
            val sectionIndex = floorDiv((y + ySectionsOffset * ChunkSection.LENGTH), ChunkSection.LENGTH)
            if (sectionIndex !in 0..<numberOfSections) return 0
            return sections[sectionIndex].blockLights[x, floorMod(y, ChunkSection.LENGTH), z]
        }
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