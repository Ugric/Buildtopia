package dev.wbell.buildtopia.app.game.session.world.chunk.Block

import dev.wbell.buildtopia.app.game.Game
import dev.wbell.buildtopia.app.game.session.world.chunk.ChunkSection
import org.joml.Vector2i


// @formatter:off
fun addFace(vertices: MutableList<Float>, x: Int, y: Int, z: Int, chunk: Vector2i, cubeScale: Float, face: String) {
    val half = cubeScale / 2f


    // Determine the neighbor block position based on face
    val (nx, ny, nz) = when(face) {
        "front" -> Triple(x, y, z + 1)
        "back"  -> Triple(x, y, z - 1)
        "left"  -> Triple(x - 1, y, z)
        "right" -> Triple(x + 1, y, z)
        "top"   -> Triple(x, y + 1, z)
        "bottom"-> Triple(x, y - 1, z)
        else    -> Triple(x, y, z)
    }

    // Get sunlight and block light of the block this face is facing
    val sunLight = Game.session.World!!.sunLights[nx + chunk.x*ChunkSection.LENGTH, ny, nz + chunk.y*ChunkSection.LENGTH] / 15f
    val blockLight = Game.session.World!!.blockLights[nx + chunk.x*ChunkSection.LENGTH, ny, nz + chunk.y*ChunkSection.LENGTH] / 15f

    // Use them in vertex attributes
    val lightR = blockLight
    val lightG = sunLight

    when(face) {
        "front" -> vertices.addAll(listOf(
            x - half, y - half, z + half, 0f, 1f, lightR, lightG,   // bottom-left
            x + half, y - half, z + half, 1f, 1f, lightR, lightG,   // bottom-right
            x + half, y + half, z + half, 1f, 0f, lightR, lightG,   // top-right

            x + half, y + half, z + half, 1f, 0f, lightR, lightG,
            x - half, y + half, z + half, 0f, 0f, lightR, lightG,   // top-left
            x - half, y - half, z + half, 0f, 1f, lightR, lightG,
        ))
        "back" -> vertices.addAll(listOf(
            x - half, y - half, z - half, 1f, 1f, lightR, lightG,
            x - half, y + half, z - half, 1f, 0f, lightR, lightG,
            x + half, y + half, z - half, 0f, 0f, lightR, lightG,

            x + half, y + half, z - half, 0f, 0f, lightR, lightG,
            x + half, y - half, z - half, 0f, 1f, lightR, lightG,
            x - half, y - half, z - half, 1f, 1f, lightR, lightG,
        ))
        "left" -> vertices.addAll(listOf(
            x - half, y - half, z - half, 0f, 1f, lightR, lightG,
            x - half, y - half, z + half, 1f, 1f, lightR, lightG,
            x - half, y + half, z + half, 1f, 0f, lightR, lightG,

            x - half, y + half, z + half, 1f, 0f, lightR, lightG,
            x - half, y + half, z - half, 0f, 0f, lightR, lightG,
            x - half, y - half, z - half, 0f, 1f, lightR, lightG,
        ))
        "right" -> vertices.addAll(listOf(
            // first triangle
            x + half, y - half, z - half, 1f, 1f, lightR, lightG,
            x + half, y + half, z + half, 0f, 0f, lightR, lightG,
            x + half, y - half, z + half, 0f, 1f, lightR, lightG,

            // second triangle
            x + half, y + half, z + half, 0f, 0f, lightR, lightG,
            x + half, y - half, z - half, 1f, 1f, lightR, lightG,
            x + half, y + half, z - half, 1f, 0f, lightR, lightG
        ))
        "top" -> vertices.addAll(listOf(
            x - half, y + half, z - half, 0f, 0f, lightR, lightG,
            x - half, y + half, z + half, 0f, 1f, lightR, lightG,
            x + half, y + half, z + half, 1f, 1f, lightR, lightG,

            x + half, y + half, z + half, 1f, 1f, lightR, lightG,
            x + half, y + half, z - half, 1f, 0f, lightR, lightG,
            x - half, y + half, z - half, 0f, 0f, lightR, lightG
        ))
        "bottom" -> vertices.addAll(listOf(
            x - half, y - half, z - half, 1f, 0f, lightR, lightG,   // bottom-back
            x + half, y - half, z + half, 0f, 1f, lightR, lightG,   // bottom-front
            x - half, y - half, z + half, 1f, 1f, lightR, lightG,   // bottom-front

            x - half, y - half, z - half, 1f, 0f, lightR, lightG,   // bottom-back
            x + half, y - half, z - half, 0f, 0f, lightR, lightG,   // bottom-back
            x + half, y - half, z + half, 0f, 1f, lightR, lightG,   // bottom-front
        ))
    }
}
// @formatter:on


class Block {
}