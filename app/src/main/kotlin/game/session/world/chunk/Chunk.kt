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


interface BlockAccessor {
    operator fun get(x: Int, y: Int, z: Int): Block?
    operator fun set(x: Int, y: Int, z: Int, value: Block?)
}

class ChunkSection(val blockData: Array<Block?>, val chunk: Chunk, val index: Int) {
    companion object {
        const val LENGTH = 16
        const val SIZE = LENGTH * LENGTH * LENGTH
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
            if (x == 0) chunk.world.getChunk(chunk.coords.x - 1, chunk.coords.y)?.updateChunk = true
            if (x == LENGTH - 1) chunk.world.getChunk(chunk.coords.x + 1, chunk.coords.y)?.updateChunk = true
            if (z == 0) chunk.world.getChunk(chunk.coords.x, chunk.coords.y - 1)?.updateChunk = true
            if (z == LENGTH - 1) chunk.world.getChunk(chunk.coords.x, chunk.coords.y + 1)?.updateChunk = true
        }
    }

    fun setVertices(vertices: MutableList<Float>) {
        for (x in 0..<LENGTH) {
            for (sectionY in 0..<LENGTH) {
                val y = sectionY + LENGTH * index - chunk.ySectionsOffset * LENGTH
                for (z in 0..<LENGTH) {
                    if (blocks[x, sectionY, z] == null) continue;
                    val worldX = x + chunk.coords.x * LENGTH
                    val worldZ = z + chunk.coords.y * LENGTH
                    if (chunk.world.blocks[worldX, y + 1, worldZ] == null) addFace(vertices, x, y, z, 1f, "top")
                    if (chunk.world.blocks[worldX, y - 1, worldZ] == null) addFace(vertices, x, y, z, 1f, "bottom")
                    if (chunk.world.blocks[worldX + 1, y, worldZ] == null) addFace(vertices, x, y, z, 1f, "right")
                    if (chunk.world.blocks[worldX - 1, y, worldZ] == null) addFace(vertices, x, y, z, 1f, "left")
                    if (chunk.world.blocks[worldX, y, worldZ + 1] == null) addFace(vertices, x, y, z, 1f, "front")
                    if (chunk.world.blocks[worldX, y, worldZ - 1] == null) addFace(vertices, x, y, z, 1f, "back")
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
    private val sections: Array<ChunkSection> = Array(numberOfSections) { sectionIndex ->
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

    private var meshExists: Boolean = false
    var vao: Int = 0
    var vaoSize: Int = 0
    var vbo: Int = 0
    var updateChunk: Boolean = true
    var verticesForUpload:MutableList<Float>? = null


    fun renderMesh() {
        verticesForUpload = mutableListOf<Float>()
        for (i in 0..<numberOfSections) {
            sections[i].setVertices(verticesForUpload!!)
        }
        updateChunk = false
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
        if (meshExists) {
            glDeleteBuffers(vbo)
            glDeleteVertexArrays(vao)
        }
        this.vao = glGenVertexArrays()
        this.vbo = glGenBuffers()
        glBindVertexArray(vao)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferData(GL_ARRAY_BUFFER, verticesForUpload!!.toFloatArray(), GL_STATIC_DRAW)

        val stride = 5 * 4 // 5 floats * 4 bytes
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0)       // position
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * 4)   // tex coords
        glEnableVertexAttribArray(1)

        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindVertexArray(0)

        this.vaoSize = verticesForUpload!!.size;
        meshExists = true
        verticesForUpload = null
    }


    fun render(deltaTime: Double) {

        // Upload to VBO/VAO


        if (meshExists) {
            // Draw the grid
            val vertexCount = vaoSize / 5

            // Normal cube
            glBindTexture(GL_TEXTURE_2D, Game.textureId)
            glUniform1i(glGetUniformLocation(Game.shaderProgram, "texture1"), 0)
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL)
            val model = Matrix4f().identity().translate(
                Vector3f(
                    (coords.x * ChunkSection.LENGTH).toFloat(),
                    -(ChunkSection.LENGTH * ySectionsOffset).toFloat(),
                    (coords.y * ChunkSection.LENGTH).toFloat()
                )
            )  // adjust position/scale if needed
            val modelBuffer = FloatArray(16)
            model.get(modelBuffer)
            glUniformMatrix4fv(Game.modelLoc, false, modelBuffer)

            glBindVertexArray(vao)
            glDrawArrays(GL_TRIANGLES, 0, vertexCount)
            glBindVertexArray(0)
        }
    }

    fun unload() {}
}