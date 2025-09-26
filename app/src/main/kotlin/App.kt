package dev.wbell.buildtopia.app

import org.joml.Matrix4d
import org.joml.Matrix4f
import org.joml.Vector3d
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.ARBVertexArrayObject.glBindVertexArray
import org.lwjgl.opengl.ARBVertexArrayObject.glGenVertexArrays
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.system.MemoryUtil.NULL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.lwjgl.stb.STBImage
import java.nio.ByteBuffer
import java.nio.IntBuffer

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

fun loadResource(path: String): String {
    return object {}.javaClass.getResource(path)?.readText() ?: throw RuntimeException("Resource not found: $path")
}

fun createShader(vertexSrc: String, fragmentSrc: String): Int {
    val vertexShader = glCreateShader(GL_VERTEX_SHADER)
    glShaderSource(vertexShader, vertexSrc)
    glCompileShader(vertexShader)

    val fragmentShader = glCreateShader(GL_FRAGMENT_SHADER)
    glShaderSource(fragmentShader, fragmentSrc)
    glCompileShader(fragmentShader)

    val program = glCreateProgram()
    glAttachShader(program, vertexShader)
    glAttachShader(program, fragmentShader)
    glLinkProgram(program)

    // Clean up
    glDeleteShader(vertexShader)
    glDeleteShader(fragmentShader)

    return program
}

fun rotateMatrix(angleDeg: Float, axis: Vector3f, dest: Matrix4f = Matrix4f()): Matrix4f {
    val radians = Math.toRadians(angleDeg.toDouble()).toFloat()
    return dest.identity().rotate(radians, axis)
}

fun getCameraMatrix(position: Vector3d, pitch: Double, yaw: Double, roll: Double): Matrix4d {
    val view = Matrix4d()
    view.identity().rotateX(-pitch)  // pitch = X
        .rotateY(-yaw)    // yaw   = Y
        .rotateZ(-roll)   // roll  = Z
        .translate(-position.x, -position.y, -position.z)      // move world opposite to camera
    return view
}

fun updateProjection(shaderProgram: Int, width: Int, height: Int) {
    glViewport(0, 0, width, height)

    val fov = 90f
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

// @formatter:off
fun addFace(vertices: MutableList<Float>, x: Int, y: Int, z: Int, cubeScale: Float, face: String) {
    val half = cubeScale / 2f
    when(face) {
        "front" -> vertices.addAll(listOf(
            x - half, y - half, z + half, 0f, 0f,   // bottom-left
            x + half, y - half, z + half, 1f, 0f,   // bottom-right
            x + half, y + half, z + half, 1f, 1f,   // top-right

            x + half, y + half, z + half, 1f, 1f,   // top-right
            x - half, y + half, z + half, 0f, 1f,   // top-left
            x - half, y - half, z + half, 0f, 0f    // bottom-left
        ))
        "back" -> vertices.addAll(listOf(
            x - half, y - half, z - half, 0f, 0f,
            x + half, y - half, z - half, 1f, 0f,
            x + half, y + half, z - half, 1f, 1f,

            x + half, y + half, z - half, 1f, 1f,
            x - half, y + half, z - half, 0f, 1f,
            x - half, y - half, z - half, 0f, 0f
        ))
        "left" -> vertices.addAll(listOf(
            x - half, y - half, z - half, 0f, 0f,
            x - half, y - half, z + half, 1f, 0f,
            x - half, y + half, z + half, 1f, 1f,

            x - half, y + half, z + half, 1f, 1f,
            x - half, y + half, z - half, 0f, 1f,
            x - half, y - half, z - half, 0f, 0f
        ))
        "right" -> vertices.addAll(listOf(
            x + half, y - half, z - half, 0f, 0f,
            x + half, y - half, z + half, 1f, 0f,
            x + half, y + half, z + half, 1f, 1f,

            x + half, y + half, z + half, 1f, 1f,
            x + half, y + half, z - half, 0f, 1f,
            x + half, y - half, z - half, 0f, 0f
        ))
        "top" -> vertices.addAll(listOf(
            x - half, y + half, z - half, 0f, 0f,
            x - half, y + half, z + half, 0f, 1f,
            x + half, y + half, z + half, 1f, 1f,

            x + half, y + half, z + half, 1f, 1f,
            x + half, y + half, z - half, 1f, 0f,
            x - half, y + half, z - half, 0f, 0f
        ))
        "bottom" -> vertices.addAll(listOf(
            x - half, y - half, z - half, 0f, 0f,
            x - half, y - half, z + half, 0f, 1f,
            x + half, y - half, z + half, 1f, 1f,

            x + half, y - half, z + half, 1f, 1f,
            x + half, y - half, z - half, 1f, 0f,
            x - half, y - half, z - half, 0f, 0f
        ))
    }
}
// @formatter:on

fun main() {
    // Initialize GLFW
    if (!glfwInit()) throw RuntimeException("Failed to initialize GLFW")
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)

    var width = 854
    var height = 480
    val window = glfwCreateWindow(width, height, "Blocktopia", NULL, NULL)
    if (window == NULL) throw RuntimeException("Failed to create GLFW window")

    glfwMakeContextCurrent(window)
    GL.createCapabilities()
    glEnable(GL_DEPTH_TEST)

    // Simple shaders
    val vertexShaderSrc = loadResource("/shaders/vertex.glsl")

    val fragmentShaderSrc = loadResource("/shaders/fragment.glsl")

    val shaderProgram = createShader(vertexShaderSrc, fragmentShaderSrc)
    glUseProgram(shaderProgram)

    val modelLoc = glGetUniformLocation(shaderProgram, "model")
    val viewLoc = glGetUniformLocation(shaderProgram, "view")
    val projLoc = glGetUniformLocation(shaderProgram, "projection")

    // Initial projection

    // GLFW callback for resizing
    glfwSetFramebufferSizeCallback(window) { _, w, h ->
        width = w
        height = h
        updateProjection(shaderProgram, w, h)
    }

    updateProjection(shaderProgram, width, height)

    // Camera parameters

    var surfaceLevel = 64
    val xSize = 16;
    val ySize = 384;
    val yOffset = 65;
    val zSize = 16;
    val cameraPos = Vector3d(0.0, surfaceLevel+2.0, 0.0)
    var pitch = 0.0
    var yaw = 0.0
    var roll = 0.0
    var startTime = glfwGetTime()
    var lastTime = startTime


    val vertices = mutableListOf<Float>()
    for (y in -yOffset..ySize-yOffset) {
        for (x in -xSize/2..xSize/2) {
            for (z in -zSize/2..zSize/2) {
                if (y > surfaceLevel) continue;
                // Only draw "outer" faces
                if (x == -xSize/2) addFace(vertices, x, y, z, 1f, "left")
                if (x == xSize/2) addFace(vertices, x, y, z, 1f, "right")
                if (z == -zSize/2) addFace(vertices, x, y, z, 1f, "back")
                if (z == zSize/2) addFace(vertices, x, y, z, 1f, "front")

                // Always draw top and bottom
                if (y == surfaceLevel) addFace(vertices, x, y, z, 1f, "top")
                if (y == -yOffset) addFace(vertices, x, y, z, 1f, "bottom")
            }
        }
    }
// Upload to VBO/VAO
    val vao = glGenVertexArrays()
    val vbo = glGenBuffers()
    glBindVertexArray(vao)
    glBindBuffer(GL_ARRAY_BUFFER, vbo)
    glBufferData(GL_ARRAY_BUFFER, vertices.toFloatArray(), GL_STATIC_DRAW)

    val stride = 5 * 4 // 5 floats * 4 bytes
    glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0)       // position
    glEnableVertexAttribArray(0)
    glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * 4)   // tex coords
    glEnableVertexAttribArray(1)

    glBindBuffer(GL_ARRAY_BUFFER, 0)
    glBindVertexArray(0)

    val vertices_size = vertices.size;
    vertices.clear()

    var lastX = width / 2.0
    var lastY = height / 2.0

    val sensitivity = 5f

    var default_speed = 5f

    var cursorDisabled = true

    var textureId = loadTexture("/textures/default_grass.png")

// Initially disable the cursor
    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED)

    glfwSetKeyCallback(window) { window, key, scancode, action, mods ->
        if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
            cursorDisabled = !cursorDisabled
            if (cursorDisabled) {
                glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED)
                // Optionally recenter the cursor
                glfwSetCursorPos(window, width / 2.0, height / 2.0)
                lastX = width / 2.0
                lastY = height / 2.0
            } else {
                glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL)
            }
        }
    }

    glfwSetCursorPosCallback(window) { window, xpos, ypos ->
        if (cursorDisabled) {
            val dx = (xpos - lastX) / width
            val dy = (ypos - lastY) / height
            yaw -= dx * sensitivity
            pitch -= dy * sensitivity
            pitch = pitch.coerceIn(-PI/2,PI/2)
        }
        lastX = xpos
        lastY = ypos
    }

    while (!glfwWindowShouldClose(window)) {

        val currentTime = glfwGetTime()
        val deltaTime = (currentTime - lastTime).toFloat() // in seconds
        lastTime = currentTime

        //yaw += 0.5f
        //cameraPos.y += 0.25f*deltaTime
        // Forward vector (direction camera is looking in XZ plane)
        val forwardX = sin(yaw)
        val forwardZ = cos(yaw)

// Right vector (perpendicular to forward)
        val rightX = -cos(yaw)
        val rightZ = sin(yaw)
        var speed = 5f
        if (glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS) {
            speed *=2
        }
        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
            cameraPos.x -= (forwardX * speed * deltaTime).toFloat()
            cameraPos.z -= (forwardZ * speed * deltaTime).toFloat()
        }
        if (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) {
            cameraPos.y -= speed*deltaTime
        }
        if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) {
            cameraPos.y += speed*deltaTime
        }
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
            cameraPos.x += (forwardX * speed * deltaTime).toFloat()
            cameraPos.z += (forwardZ * speed * deltaTime).toFloat()
        }
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
            cameraPos.x += (rightX * speed * deltaTime).toFloat()
            cameraPos.z += (rightZ * speed * deltaTime).toFloat()
        }
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
            cameraPos.x -= (rightX * speed * deltaTime).toFloat()
            cameraPos.z -= (rightZ * speed * deltaTime).toFloat()
        }
        //yaw += 0.1f*deltaTime
        glEnable(GL_DEPTH_TEST)
        glClearColor(0.2f, 1f, 1f, 1f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        // Update camera
        val view = getCameraMatrix(cameraPos, pitch, yaw, roll)
        val viewBuffer = FloatArray(16)
        view.get(viewBuffer)
        glUniformMatrix4fv(viewLoc, false, viewBuffer)

        // Draw the grid
        val vertexCount = vertices_size / 5
        glPolygonMode(GL_FRONT_AND_BACK, GL_LINE)
        glBindVertexArray(vao)
        glDrawArrays(GL_TRIANGLES, 0, vertexCount)
        glBindVertexArray(0)

        // Normal cube
        glBindTexture(GL_TEXTURE_2D, textureId)
        glUniform1i(glGetUniformLocation(shaderProgram, "texture1"), 0)
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL)
        val model = Matrix4f().identity()  // adjust position/scale if needed
        val modelBuffer = FloatArray(16)
        model.get(modelBuffer)
        glUniformMatrix4fv(modelLoc, false, modelBuffer)

        glBindVertexArray(vao)
        glDrawArrays(GL_TRIANGLES, 0, vertexCount)
        glBindVertexArray(0)


        glfwSwapBuffers(window)
        glfwPollEvents()
    }

    glfwDestroyWindow(window)
    glfwTerminate()
}