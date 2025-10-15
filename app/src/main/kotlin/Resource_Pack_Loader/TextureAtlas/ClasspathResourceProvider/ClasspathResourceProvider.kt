package dev.wbell.buildtopia.app.Resource_Pack_Loader.TextureAtlas.ClasspathResourceProvider

import dev.wbell.buildtopia.app.Resource_Pack_Loader.TextureAtlas.ResourceProvider
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import java.util.jar.JarFile
import javax.imageio.ImageIO

class ClasspathResourceProvider(
    private val basePath: String, // e.g. "/assets/Blocktopia/textures"
    private val resourceOwner: Any // usually `this`
) : ResourceProvider {

    override fun listPngFiles(): List<String> {
        val uri = resourceOwner.javaClass.getResource(basePath)?.toURI() ?: return emptyList()

        return when {
            uri.scheme == "file" -> { // Running unpacked (IDE/dev mode)
                println(File(uri).walkTopDown()
                    .filter { it.extension == "png" }
                    .map { it.relativeTo(File(uri)).path.replace("\\", "/") }
                    .toList())
                File(uri).walkTopDown()
                    .filter { it.extension == "png" }
                    .map { it.relativeTo(File(uri)).path.replace("\\", "/") }
                    .toList()
            }

            uri.scheme == "jar" -> { // Running from packaged .jar
                val jarPath = uri.schemeSpecificPart.substringBefore("!")
                    .removePrefix("file:")
                val jar = JarFile(jarPath)
                val entries = jar.entries().toList()
                val prefix = basePath.removePrefix("/").trimEnd('/') + "/"
                entries
                    .filter { it.name.startsWith(prefix) && it.name.endsWith(".png") }
                    .map { it.name.removePrefix(prefix) }
            }

            else -> emptyList()
        }
    }

    override fun loadImage(path: String): BufferedImage? {
        val stream: InputStream? = resourceOwner.javaClass.getResourceAsStream("$basePath/$path")
        return stream?.use { ImageIO.read(it) }
    }
}