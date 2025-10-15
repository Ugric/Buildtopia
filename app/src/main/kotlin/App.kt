package dev.wbell.buildtopia.app

import dev.wbell.buildtopia.app.game.Game
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL20.*

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

fun main() {


    // Camera parameters
    val startTime = glfwGetTime()
    var lastTime = startTime
//
//
//
//    var lastX: Double? = null
//    var lastY: Double? = null
//
//    val sensitivity = 5f
//
//    var cursorDisabled = true
//
//    var textureId = loadTexture("/resource_pack/textures/block/cobblestone.png")


    Game.init()

    Game.window?.let { window ->
        while (!glfwWindowShouldClose(window)) {
            val currentTime = glfwGetTime()
            val deltaTime = currentTime - lastTime // in seconds
            if (Game.render(deltaTime)) {
                lastTime = currentTime
                glfwSwapBuffers(window)
                glfwPollEvents()
            }
//        isOnGround = cameraPos.y <= surfaceLevel + 2
//        //yaw += 0.5f
//        //cameraPos.y += 0.25f*deltaTime
//        // Forward vector (direction camera is looking in XZ plane)
//        val forwardX = sin(yaw)
//        val forwardZ = cos(yaw)
//
//// Right vector (perpendicular to forward)
//        val rightX = -cos(yaw)
//        val rightZ = sin(yaw)
//        val ticksPerSecond = 20.0
//        val slipperiness = if (isOnGround) 0.6 else 1.0
//        val baseAccelerationPerTick = 0.1
//        val sprintMultiplier = if (glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS) 1.3 else 1.0
//        val acceleration = (baseAccelerationPerTick * sprintMultiplier*(0.6/slipperiness).pow(3))*2.5
//
//// Apply friction
//        val frictionPerTick = 0.91 * slipperiness      // combined tick friction + slipperiness
//        val friction = frictionPerTick.pow(ticksPerSecond * deltaTime)
//
//        velocity.x *= friction
//        velocity.z *= friction
//
//// --- Horizontal movement ---
//        var moveX = 0.0
//        var moveZ = 0.0
//        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) moveZ -= 1.0
//        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) moveZ += 1.0
//        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) moveX += 1.0
//        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) moveX -= 1.0
//
//        val lengthSquared = moveX * moveX + moveZ * moveZ
//        if (lengthSquared > 0) {
//            val len = sqrt(lengthSquared)
//            moveX /= len
//            moveZ /= len
//            velocity.x += (forwardX * moveZ + rightX * moveX) * acceleration * deltaTime
//            velocity.z += (forwardZ * moveZ + rightZ * moveX) * acceleration * deltaTime
//        }
//
//        // Apply Gravity
//        // Gravity in blocks/s²
//        if (!isOnGround) {
//            if (!isFlying) {
//                val gravityPerSecond = 0.08
//                velocity.y -= gravityPerSecond * deltaTime
//            }
//        } else if (velocity.y<0) {
//            if (isFlying) isFlying = false
//            velocity.y = 0.0
//            cameraPos.y = surfaceLevel + 2.0
//        }
//        isOnGround = (cameraPos.y+velocity.y) <= surfaceLevel + 2
//
//// --- Vertical movement (flying) ---
//        if (isFlying) {
//            if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) {
//                velocity.y += acceleration * deltaTime
//            }
//            if (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) {
//                velocity.y -= acceleration * deltaTime
//            }
//        } else if (isOnGround) {
//            if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) {
//                velocity.y = 0.42/15
//            }
//        } else {
//            if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) {
//                //isFlying = true
//            }
//        }
//        velocity.y *= (1-(0.2*deltaTime))
//
//// Update position
//        cameraPos.add(velocity)
//        //yaw += 0.1f*deltaTime
//        glEnable(GL_DEPTH_TEST)
//        glClearColor(0.2f, 1f, 1f, 1f)
//        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
//
//        // Update camera
//        val view = getCameraMatrix(cameraPos, pitch, yaw, roll)
//        val viewBuffer = FloatArray(16)
//        view.get(viewBuffer)
//        glUniformMatrix4fv(viewLoc, false, viewBuffer)
//
//        // Draw the grid
//        val vertexCount = vertices_size / 5
//
//        // Normal cube
//        glBindTexture(GL_TEXTURE_2D, textureId)
//        glUniform1i(glGetUniformLocation(shaderProgram, "texture1"), 0)
//        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL)
//        val model = Matrix4f().identity()  // adjust position/scale if needed
//        val modelBuffer = FloatArray(16)
//        model.get(modelBuffer)
//        glUniformMatrix4fv(modelLoc, false, modelBuffer)
//
//        glBindVertexArray(vao)
//        glDrawArrays(GL_TRIANGLES, 0, vertexCount)
//        glBindVertexArray(0)
//
//
//        glfwSwapBuffers(window)
//        glfwPollEvents()
        }
    }
    Game.close()
}