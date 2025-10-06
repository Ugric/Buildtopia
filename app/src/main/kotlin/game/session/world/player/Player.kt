package dev.wbell.buildtopia.app.game.session.world.player

import dev.wbell.buildtopia.app.game.Game
import dev.wbell.buildtopia.app.game.camera.Camera
import dev.wbell.buildtopia.app.game.session.world.World
import dev.wbell.buildtopia.app.game.settings.SettingKey
import dev.wbell.buildtopia.app.game.settings.Settings
import org.joml.Vector2d
import org.joml.Vector2f
import org.joml.Vector3d
import org.lwjgl.glfw.GLFW.*
import kotlin.math.*
import kotlin.random.Random


data class AABB(
    var minX: Double, var minY: Double, var minZ: Double,
    var maxX: Double, var maxY: Double, var maxZ: Double
) {
    fun offset(dx: Double, dy: Double, dz: Double) =
        AABB(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz)

    fun intersects(other: AABB): Boolean =
        maxX > other.minX && minX < other.maxX &&
                maxY > other.minY && minY < other.maxY &&
                maxZ > other.minZ && minZ < other.maxZ
}

class Player(val position: Vector3d, var pitch: Float, var yaw: Float) {
    var world: World? = null
    val velocity = Vector3d(0.0, 0.0, 0.0)

    private var isOnGround = false
    private var isSprinting = false
    var fovModifierCurrent = 1.0

    // Store last and current positions for interpolation
    private val lastPosition = Vector3d(position)


    val width = 0.6
    val height = 1.8
    fun getBoundingBox(): AABB {
        return AABB(
            position.x - width / 2, position.y,
            position.z - width / 2, position.x + width / 2,
            position.y + height, position.z + width / 2
        )
    }

    fun getNearbyBlockBoxes(bb: AABB): List<AABB> {
        val boxes = mutableListOf<AABB>()

        val minX = floor(bb.minX - 2.0).toInt()
        val minY = floor(bb.minY - 2.0).toInt()
        val minZ = floor(bb.minZ - 2.0).toInt()
        val maxX = floor(bb.maxX + 2.0).toInt()
        val maxY = floor(bb.maxY + 2.0).toInt()
        val maxZ = floor(bb.maxZ + 2.0).toInt()

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val block = world!!.blocks[x, y, z]
                    if (block != null) {
                        boxes.add(
                            AABB(
                                x - 0.5, y.toDouble(), z - 0.5,
                                x + 0.5, y + 1.0, z + 0.5
                            )
                        )
                    }
                }
            }
        }

        return boxes
    }

    fun tick() {
        lastPosition.set(position)
        velocity.y = (velocity.y - 0.08) * 0.98
        val slipperiness = if (isOnGround) 0.6 else 1.0
        val forward = Vector2f(-sin(yaw), -cos(yaw))
        val left = Vector2f(-cos(yaw), sin(yaw))
        val movement = Vector2d(0.0, 0.0)
        if (glfwGetKey(Game.window!!, GLFW_KEY_W) == GLFW_PRESS) {
            movement.x = 1.0
            if (glfwGetKey(Game.window!!, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS) {
                isSprinting=true
            }
        } else {
            isSprinting=false
        }
        if (glfwGetKey(Game.window!!, GLFW_KEY_S) == GLFW_PRESS) {
            movement.x = -1.0
        }
        if (glfwGetKey(Game.window!!, GLFW_KEY_A) == GLFW_PRESS) {
            movement.y = 1.0
        }
        if (glfwGetKey(Game.window!!, GLFW_KEY_D) == GLFW_PRESS) {
            movement.y = -1.0
        }
        if (movement.lengthSquared() != 0.0) {
            movement.normalize()
        }
        movement.mul(0.1)
        if (isSprinting) {
            movement.mul(1.3)
        }
        velocity.x =
            velocity.x * slipperiness * 0.91 + movement.x * (0.6 / slipperiness).pow(3) * forward.x + movement.y * (0.6 / slipperiness).pow(
                3
            ) * left.x
        velocity.z =
            velocity.z * slipperiness * 0.91 + movement.x * (0.6 / slipperiness).pow(3) * forward.y + movement.y * (0.6 / slipperiness).pow(
                3
            ) * left.y
        if (isOnGround && glfwGetKey(Game.window!!, GLFW_KEY_SPACE) == GLFW_PRESS) {
            velocity.y = 0.42
        }
        moveAxis(velocity.x, 0.0, 0.0) // move along X
        moveAxis(0.0, velocity.y, 0.0) // move along Y
        moveAxis(0.0, 0.0, velocity.z) // move along Z
        if (glfwGetKey(Game.window!!, GLFW_KEY_W) == GLFW_PRESS && glfwGetKey(Game.window!!, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS) {
            isSprinting = true
        }
    }


    private fun moveAxis(dx: Double, dy: Double, dz: Double) {
        val steps = ceil(maxOf(abs(dx), abs(dy), abs(dz))).toInt().coerceAtLeast(1)
        val stepX = dx / steps
        val stepY = dy / steps
        val stepZ = dz / steps

        if (stepY != 0.0) isOnGround = false

        repeat(steps) {
            val bb = getBoundingBox().offset(stepX, stepY, stepZ)
            val colliders = getNearbyBlockBoxes(bb)

            var collidedX = false
            var collidedY = false
            var collidedZ = false

            for (blockBB in colliders) {
                if (bb.intersects(blockBB)) {
                    // Y-axis collision
                    if (stepY < 0.0) { // moving down
                        position.y = blockBB.maxY
                        velocity.y = 0.0
                        collidedY = true
                        isOnGround = true
                    } else if (stepY > 0.0) { // moving up
                        position.y = blockBB.minY - height
                        velocity.y = 0.0
                        collidedY = true
                    }

                    // X-axis collision
                    if (stepX != 0.0) {
                        position.x = if (stepX > 0) blockBB.minX - width / 2 else blockBB.maxX + width / 2
                        velocity.x = 0.0
                        collidedX = true
                    }

                    // Z-axis collision
                    if (stepZ != 0.0) {
                        position.z = if (stepZ > 0) blockBB.minZ - width / 2 else blockBB.maxZ + width / 2
                        velocity.z = 0.0
                        collidedZ = true
                    }
                }
            }

            // If no collision on a given axis, move normally
            if (!collidedX) position.x += stepX
            if (!collidedY) position.y += stepY
            if (!collidedZ) position.z += stepZ
            if ((collidedX || collidedZ) && dx*dx+dz*dz > 0.01) isSprinting = false
        }
    }

    fun getCamera(alpha: Double): Camera {
        val targetFovModifier = if (isSprinting) 1.1 else 1.0

        // gradually move current toward target, just like Minecraft
        fovModifierCurrent += (targetFovModifier - fovModifierCurrent) * 0.1

        val interpolatedFovModifier = fovModifierCurrent // already smoothed

        return Camera(
            Vector3d(
                lerp(lastPosition.x, position.x, alpha),
                lerp(lastPosition.y, position.y, alpha) + 1,
                lerp(lastPosition.z, position.z, alpha)
            ),
            pitch,
            yaw,
            0f,
            ((Settings[SettingKey.FOV] ?: 90).toFloat() * interpolatedFovModifier.toFloat())
        )
    }

    private fun lerp(a: Double, b: Double, t: Double): Double {
        return a + (b - a) * t
    }
}