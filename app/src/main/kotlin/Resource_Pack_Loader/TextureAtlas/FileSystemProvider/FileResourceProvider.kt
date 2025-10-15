package dev.wbell.buildtopia.app.Resource_Pack_Loader.TextureAtlas.FileSystemProvider

import dev.wbell.buildtopia.app.Resource_Pack_Loader.TextureAtlas.ResourceProvider
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class FileResourceProvider(private val rootPath: String) : ResourceProvider {
    override fun listPngFiles(): List<String> =
        File(rootPath).walkTopDown()
            .filter { it.extension == "png" }
            .map { it.relativeTo(File(rootPath)).path.replace("\\", "/") }
            .toList()

    override fun loadImage(path: String): BufferedImage? {
        val file = File("$rootPath/$path")
        return if (file.exists()) ImageIO.read(file) else null
    }
}