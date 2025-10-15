package dev.wbell.buildtopia.app.Resource_Pack_Loader.TextureAtlas

import org.joml.Vector2f
import org.lwjgl.BufferUtils
import java.awt.image.BufferedImage
import kotlin.math.ceil
import kotlin.math.sqrt
import org.lwjgl.opengl.GL11.*
import java.awt.Image

interface ResourceProvider {
    fun listPngFiles(): List<String> // e.g. "block/grass_block.png"
    fun loadImage(path: String): BufferedImage?
}

class TextureAtlas(
    private val resources: ResourceProvider,
    private val tileSize: Int = 16
) {
    data class UV(val topLeft: Vector2f, val bottomRight: Vector2f)

    val textureMap = mutableMapOf<String, UV>()
    var textureId = 0
        private set
    private var atlasWidth = 0
    private var atlasHeight = 0
    private val missingTexture = createMissingTexture()

    fun buildAtlas() {
        val files = resources.listPngFiles()
        val count = files.size
        val padding = 1 // 1 pixel padding around each tile
        val paddedTileSize = tileSize + 2 * padding
        val gridSize = ceil(sqrt(count.toDouble() + 1)).toInt()
        atlasWidth = gridSize * paddedTileSize
        atlasHeight = gridSize * paddedTileSize

        val atlasImage = BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB)
        val g = atlasImage.createGraphics()

        var index = 0
        for (path in files) {
            val image = resources.loadImage(path) ?: missingTexture

            // Scale to tile size
            val scaled = image.getScaledInstance(tileSize, tileSize, Image.SCALE_SMOOTH)
            val finalImage = BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB)
            val g2 = finalImage.createGraphics()
            g2.drawImage(scaled, 0, 0, null)
            g2.dispose()

            val gridX = index % gridSize
            val gridY = index / gridSize
            val x = gridX * paddedTileSize
            val y = gridY * paddedTileSize

            // Draw the main tile in the center of the padded area
            g.drawImage(finalImage, x + padding, y + padding, null)

            // Duplicate edges for padding (top, bottom, left, right)
            // left/right columns
            g.drawImage(finalImage.getSubimage(0, 0, 1, tileSize), x, y + padding, null) // left
            g.drawImage(finalImage.getSubimage(tileSize - 1, 0, 1, tileSize), x + padding + tileSize, y + padding, null) // right
            // top/bottom rows
            g.drawImage(finalImage.getSubimage(0, 0, tileSize, 1), x + padding, y, null) // top
            g.drawImage(finalImage.getSubimage(0, tileSize - 1, tileSize, 1), x + padding, y + padding + tileSize, null) // bottom
            // corners
            g.drawImage(finalImage.getSubimage(0, 0, 1, 1), x, y, null) // top-left
            g.drawImage(finalImage.getSubimage(tileSize - 1, 0, 1, 1), x + padding + tileSize, y, null) // top-right
            g.drawImage(finalImage.getSubimage(0, tileSize - 1, 1, 1), x, y + padding + tileSize, null) // bottom-left
            g.drawImage(finalImage.getSubimage(tileSize - 1, tileSize - 1, 1, 1), x + padding + tileSize, y + padding + tileSize, null) // bottom-right

            // Compute UVs only for inner tile area
            val u1 = (x + padding).toFloat() / atlasWidth
            val v1 = (y + padding).toFloat() / atlasHeight
            val u2 = (x + padding + tileSize).toFloat() / atlasWidth
            val v2 = (y + padding + tileSize).toFloat() / atlasHeight
            textureMap[path] = UV(Vector2f(u1, v1), Vector2f(u2, v2))

            index++
        }

        // Add missing texture the same way
        val gridX = index % gridSize
        val gridY = index / gridSize
        val x = gridX * paddedTileSize
        val y = gridY * paddedTileSize
        g.drawImage(missingTexture, x + padding, y + padding, null)

        val u1 = (x + padding).toFloat() / atlasWidth
        val v1 = (y + padding).toFloat() / atlasHeight
        val u2 = (x + padding + tileSize).toFloat() / atlasWidth
        val v2 = (y + padding + tileSize).toFloat() / atlasHeight
        textureMap["missing"] = UV(Vector2f(u1, v1), Vector2f(u2, v2))

        g.dispose()
        uploadToGL(atlasImage)
    }

    private fun createMissingTexture(): BufferedImage {
        val img = BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB)
        val magenta = 0xFFFF00FF.toInt()
        val black = 0xFF000000.toInt()
        for (y in 0 until tileSize)
            for (x in 0 until tileSize)
                img.setRGB(x, y, if (((x / (tileSize / 2)) + (y / (tileSize / 2))) % 2 == 0) magenta else black)
        return img
    }

    private fun uploadToGL(image: BufferedImage) {
        val pixels = IntArray(image.width * image.height)
        image.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)
        val buffer = BufferUtils.createByteBuffer(image.width * image.height * 4)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val p = pixels[y * image.width + x]
                buffer.put(((p shr 16) and 0xFF).toByte())
                buffer.put(((p shr 8) and 0xFF).toByte())
                buffer.put((p and 0xFF).toByte())
                buffer.put(((p shr 24) and 0xFF).toByte())
            }
        }
        buffer.flip()
        textureId = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, textureId)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, image.width, image.height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glBindTexture(GL_TEXTURE_2D, 0)
    }

    fun getUV(path: String): UV = textureMap[path] ?: textureMap["missing"]!!//UV(Vector2f(0f,0f), Vector2f(1f,1f))//
}