package dev.wbell.buildtopia.app.game

import dev.wbell.buildtopia.app.Resource_Pack_Loader.TextureAtlas.ClasspathResourceProvider.ClasspathResourceProvider
import dev.wbell.buildtopia.app.Resource_Pack_Loader.TextureAtlas.TextureAtlas
import dev.wbell.buildtopia.app.createShader
import dev.wbell.buildtopia.app.game.session.Session
import dev.wbell.buildtopia.app.game.session.world.World
import dev.wbell.buildtopia.app.game.session.world.chunk.Block.BlockRegistry
import dev.wbell.buildtopia.app.game.session.world.chunk.Block.BlockType
import dev.wbell.buildtopia.app.game.session.world.player.Player
import dev.wbell.buildtopia.app.loadResource
import org.joml.Matrix4f
import org.joml.Vector3d
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL20.glGetUniformLocation
import org.lwjgl.opengl.GL20.glUseProgram
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL20.glUniformMatrix4fv
import kotlin.math.PI

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

    val block_atlas = TextureAtlas(ClasspathResourceProvider("/assets/Blocktopia/textures/block", this))

    var shaderProgram = 0
    var modelLoc = 0
    var viewLoc = 0
    var projLoc = 0

    fun updateFov(fov: Float) {
        glViewport(0, 0, width, height)

        val aspect = width.toFloat() / height
        val near = 0.1f
        val far = 10000f

        val projection = Matrix4f().perspective(Math.toRadians(fov.toDouble()).toFloat(), aspect, near, far)
        val projBuffer = FloatArray(16)
        projection.get(projBuffer)

        val projLoc = glGetUniformLocation(shaderProgram, "projection")
        glUseProgram(shaderProgram)
        glUniformMatrix4fv(projLoc, false, projBuffer)
    }

    fun init() {
        // Initialize GLF
        BlockRegistry.register(BlockType("minecraft:air", "Air", "", transparent = true, solid = false))
        println(BlockRegistry.getIndex("minecraft:air"))
        BlockRegistry.register(BlockType("minecraft:stone", "Stone", "block/stone.png"))
        BlockRegistry.register(BlockType("minecraft:dirt", "Dirt", "block/dirt.png", transparent = true))
        BlockRegistry.register(BlockType("minecraft:glass_block", "Glass Block", "block/grass_block.png", transparent = true))
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
            glEnable(GL_BLEND)
            glEnable(GL_SAMPLE_ALPHA_TO_COVERAGE);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

            block_atlas.buildAtlas()

            // Simple shaders
            val vertexShaderSrc = loadResource("/assets/Blocktopia/shaders/core/terrain.vsh")

            val fragmentShaderSrc = loadResource("/assets/Blocktopia/shaders/core/terrain.fsh")

            shaderProgram = createShader(vertexShaderSrc, fragmentShaderSrc)
            glUseProgram(shaderProgram)

            modelLoc = glGetUniformLocation(shaderProgram, "model")
            viewLoc = glGetUniformLocation(shaderProgram, "view")
            projLoc = glGetUniformLocation(shaderProgram, "projection")
            session.World = World(Player(Vector3d(0.0, 500.0, 0.0), 0f, 0f), session)
            session.World!!.player.world = session.World
            session.World!!.init()

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
        val widthBuffer = IntArray(1)
        val heightBuffer = IntArray(1)
        glfwGetFramebufferSize(window!!, widthBuffer, heightBuffer)
        width = widthBuffer[0]
        height = heightBuffer[0]
        session.render(deltaTime)
        return true
    }
}