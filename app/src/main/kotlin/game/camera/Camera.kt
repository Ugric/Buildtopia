package dev.wbell.buildtopia.app.game.camera

import org.joml.Vector3d

class Camera(
    val position: Vector3d,
    var pitch: Float,
    var yaw: Float,
    var roll: Float
) {
}