package dev.wbell.buildtopia.app.game.camera

import org.joml.Vector3d

class Camera(
    val position: Vector3d,
    var pitch: Double,
    var yaw: Double,
    var roll: Double
) {
}