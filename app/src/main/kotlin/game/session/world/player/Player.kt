package dev.wbell.buildtopia.app.game.session.world.player

import dev.wbell.buildtopia.app.game.Game
import dev.wbell.buildtopia.app.game.camera.Camera
import dev.wbell.buildtopia.app.game.session.world.World
import org.joml.Vector2d
import org.joml.Vector3d
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFW.glfwGetKey
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

class Player(val position: Vector3d, var pitch: Double, var yaw: Double) {
    var world: World? = null
    val velocity = Vector3d(0.0, 0.0, 0.0)

    private var isOnGround = false

    // Store last and current positions for interpolation
    private val lastPosition = Vector3d(position)

    fun tick() {
        lastPosition.set(position)
        val blockUnderneath = world!!.blocks[position.x.toInt(),position.y.toInt()-2,position.z.toInt()]
            isOnGround = position.y + velocity.y <= 4

        velocity.y -= 0.08
        if (position.y + velocity.y <= 4) {
            position.y = 4.0
            velocity.y = 0.0
        }
        if (isOnGround&&glfwGetKey(Game.window!!, GLFW_KEY_SPACE) == GLFW_PRESS) {
            velocity.y = 0.42
        }
        val slipperiness = if (isOnGround) 0.6 else 1.0
        val forward = Vector2d(-sin(yaw), -cos(yaw))
        val left = Vector2d(-cos(yaw), sin(yaw))
        val movement = Vector2d(0.0,0.0)
        if (glfwGetKey(Game.window!!, GLFW_KEY_W) == GLFW_PRESS) {
            movement.x=1.0
        }
        if (glfwGetKey(Game.window!!, GLFW_KEY_S) == GLFW_PRESS) {
            movement.x=-1.0
        }
        if (glfwGetKey(Game.window!!, GLFW_KEY_A) == GLFW_PRESS) {
            movement.y=1.0
        }
        if (glfwGetKey(Game.window!!, GLFW_KEY_D) == GLFW_PRESS) {
            movement.y=-1.0
        }
        if (movement.lengthSquared() != 0.0) {
            movement.normalize()
        }
        movement.mul(0.1)
        if (glfwGetKey(Game.window!!, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS) {
            movement.mul(1.3)
        }
        velocity.x = velocity.x*slipperiness*0.91+movement.x*(0.6/slipperiness).pow(3)*forward.x+movement.y*(0.6/slipperiness).pow(3)*left.x
        velocity.z = velocity.z*slipperiness*0.91+movement.x*(0.6/slipperiness).pow(3)*forward.y+movement.y*(0.6/slipperiness).pow(3)*left.y
        position.add(velocity)
    }

    fun getCamera(alpha: Double): Camera {
        val interpPos = Vector3d(
            lerp(lastPosition.x, position.x, alpha),
            lerp(lastPosition.y, position.y, alpha),
            lerp(lastPosition.z, position.z, alpha)
        )
        return Camera(interpPos.add(0.0, 1.0, 0.0), pitch, yaw, 0.0)
    }

    private fun lerp(a: Double, b: Double, t: Double): Double {
        return a + (b - a) * t
    }
}