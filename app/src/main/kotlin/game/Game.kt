package dev.wbell.buildtopia.app.game

import dev.wbell.buildtopia.app.createShader
import dev.wbell.buildtopia.app.game.session.Session
import dev.wbell.buildtopia.app.game.session.world.World
import dev.wbell.buildtopia.app.game.session.world.chunk.ChunkSection
import dev.wbell.buildtopia.app.game.session.world.player.Player
import dev.wbell.buildtopia.app.loadResource
import dev.wbell.buildtopia.app.updateProjection
import org.joml.Vector3d
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL20.glGetUniformLocation
import org.lwjgl.opengl.GL20.glUseProgram
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.opengl.GL13.*
import org.lwjgl.stb.STBImage
import java.nio.IntBuffer
import kotlin.math.PI
import kotlin.random.Random


fun loadTexture(path: String): Int {
    val textureId = glGenTextures()
    glBindTexture(GL_TEXTURE_2D, textureId)

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)


    // Load image from resources
    val stream = object {}.javaClass.getResourceAsStream(path)
        ?: throw RuntimeException("Failed to load texture: $path")

    // Read stream into a ByteBuffer
    val bytes = stream.readBytes()
    val buffer = BufferUtils.createByteBuffer(bytes.size)
    buffer.put(bytes)
    buffer.flip()

    val widthBuffer: IntBuffer = BufferUtils.createIntBuffer(1)
    val heightBuffer: IntBuffer = BufferUtils.createIntBuffer(1)
    val channelsBuffer: IntBuffer = BufferUtils.createIntBuffer(1)

    val image = STBImage.stbi_load_from_memory(buffer, widthBuffer, heightBuffer, channelsBuffer, 4)
        ?: throw RuntimeException("Failed to load texture: ${STBImage.stbi_failure_reason()}")

    val width = widthBuffer.get(0)
    val height = heightBuffer.get(0)
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, image)
    STBImage.stbi_image_free(image)

    return textureId
}

object Game {
    val session = Session()
    var width = 854
    var height = 480
    var cursorDisabled = true
    var window: Long? = null

    object Cursor {
        var lastX: Double? = null
        var lastY: Double? = null
    }

    var textureId = 0

    var shaderProgram = 0
    var modelLoc = 0
    var viewLoc = 0
    var projLoc = 0

    fun init() {
        // Initialize GLFW
        if (!glfwInit()) throw RuntimeException("Failed to initialize GLFW")
        glfwWindowHint(GLFW_SAMPLES, 4)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        window = glfwCreateWindow(width, height, "Blocktopia", NULL, NULL)
        if (window == NULL) throw RuntimeException("Failed to create GLFW window")
        glfwSwapInterval(1)
        window?.let { window ->
            glfwMakeContextCurrent(window)
            GL.createCapabilities()
            glEnable(GL_MULTISAMPLE)
            glEnable(GL_DEPTH_TEST)
            glEnable(GL_CULL_FACE)       // turn on face culling
            glCullFace(GL_BACK)          // cull back faces
            glFrontFace(GL_CCW)          // counter-clockwise triangles are front-facing
            glDepthFunc(GL_LESS)

            textureId = loadTexture("/resource_pack/textures/block/cobblestone.png")

            // Simple shaders
            val vertexShaderSrc = loadResource("/shaders/vertex.glsl")

            val fragmentShaderSrc = loadResource("/shaders/fragment.glsl")

            shaderProgram = createShader(vertexShaderSrc, fragmentShaderSrc)
            glUseProgram(shaderProgram)

            modelLoc = glGetUniformLocation(shaderProgram, "model")
            viewLoc = glGetUniformLocation(shaderProgram, "view")
            projLoc = glGetUniformLocation(shaderProgram, "projection")
            session.World = World(Player(Vector3d(0.0, 4.0, 0.0), 0f, 0f), session)
            session.World!!.player.world = session.World
            session.World!!.init()

            // Initial projection

            // GLFW callback for resizing
            glfwSetFramebufferSizeCallback(window) { _, w, h ->
                width = w
                height = h
                updateProjection(shaderProgram, w, h)
            }

            updateProjection(shaderProgram, width, height)

// Initially disable the cursor
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED)

            glfwSetKeyCallback(window) { window, key, scancode, action, mods ->
                if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                    cursorDisabled = !cursorDisabled
                    if (cursorDisabled) {
                        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED)
                        // Optionally recenter the cursor
                        glfwSetCursorPos(window, width / 2.0, height / 2.0)
                        Cursor.lastX = null
                        Cursor.lastY = null
                    } else {
                        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL)
                    }
                }
            }

            glfwSetCursorPosCallback(window) { window, xpos, ypos ->
                if (cursorDisabled && Cursor.lastX != null && Cursor.lastY != null) {
                    val dx = (xpos - Cursor.lastX!!) / width
                    val dy = (ypos - Cursor.lastY!!) / height
                    session.World?.player?.yaw -= dx.toFloat() * 3.5f
                    session.World?.player?.pitch -= dy.toFloat() * 3.5f
                    session.World?.player?.pitch?.let {
                        session.World?.player?.pitch = it.coerceIn(-PI.toFloat() / 2, PI.toFloat() / 2)
                    }
                }
                Cursor.lastX = xpos
                Cursor.lastY = ypos
            }
        }
    }

    fun close() {
        window?.let { glfwDestroyWindow(it) }
        glfwTerminate()
    }

    fun render(deltaTime: Double): Boolean {
        session.World?.render(deltaTime)
        return true
    }
}