package dev.wbell.buildtopia.app.game.session.world.chunk.Block

import dev.wbell.buildtopia.app.game.Game
import dev.wbell.buildtopia.app.game.session.world.chunk.ChunkSection
import org.joml.Vector2i

fun getVertexLight(x: Int, y: Int, z: Int, chunk: Vector2i, offsets: List<Triple<Int, Int, Int>>): Pair<Float, Float> {
    var sunTotal = 0
    var blockTotal = 0
    var count = 0

    for ((dx, dy, dz) in offsets) {
        val nx = x + dx + chunk.x* ChunkSection.LENGTH
        val ny = y + dy
        val nz = z + dz + chunk.y* ChunkSection.LENGTH

        sunTotal += Game.session.World!!.sunLights[nx, ny, nz]
        blockTotal += Game.session.World!!.blockLights[nx, ny, nz]
        count++
    }

    val avgSun = (sunTotal.toFloat() / count) / 15f
    val avgBlock = (blockTotal.toFloat() / count) / 15f
    return Pair(avgBlock, avgSun)
}

// @formatter:off
fun addFace(vertices: MutableList<Float>, x: Int, y: Int, z: Int, chunk: Vector2i, cubeScale: Float, face: String) {
    val half = cubeScale / 2f

    val lightR = 0f
    val lightG = 1f

    when(face) {
        "front" -> {
            // Offsets for each corner of the "front" face
            val frontBottomLeft = listOf(
                Triple(0, 0, 1), Triple(-1, 0, 1), Triple(0, -1, 1), Triple(-1, -1, 1)
            )
            val frontBottomRight = listOf(
                Triple(0, 0, 1), Triple(1, 0, 1), Triple(0, -1, 1), Triple(1, -1, 1)
            )
            val frontTopRight = listOf(
                Triple(0, 0, 1), Triple(1, 0, 1), Triple(0, 1, 1), Triple(1, 1, 1)
            )
            val frontTopLeft = listOf(
                Triple(0, 0, 1), Triple(-1, 0, 1), Triple(0, 1, 1), Triple(-1, 1, 1)
            )
            val (blR, blG) = getVertexLight(x, y, z, chunk, frontBottomLeft)
            val (brR, brG) = getVertexLight(x, y, z, chunk, frontBottomRight)
            val (trR, trG) = getVertexLight(x, y, z, chunk, frontTopRight)
            val (tlR, tlG) = getVertexLight(x, y, z, chunk, frontTopLeft)
            vertices.addAll(listOf(
            x - half, y - half, z + half, 0f, 1f, blR, blG,   // bottom-left
            x + half, y - half, z + half, 1f, 1f, brR, brG,   // bottom-right
            x + half, y + half, z + half, 1f, 0f, trR, trG,   // top-right

            x + half, y + half, z + half, 1f, 0f, trR, trG,
            x - half, y + half, z + half, 0f, 0f, tlR, tlG,   // top-left
            x - half, y - half, z + half, 0f, 1f, blR, blG,
        ))}
        // === BACK FACE ===
        "back" -> {
            val backBottomLeft = listOf(
                Triple(0, 0, -1), Triple(-1, 0, -1), Triple(0, -1, -1), Triple(-1, -1, -1)
            )
            val backTopLeft = listOf(
                Triple(0, 0, -1), Triple(-1, 0, -1), Triple(0, 1, -1), Triple(-1, 1, -1)
            )
            val backTopRight = listOf(
                Triple(0, 0, -1), Triple(1, 0, -1), Triple(0, 1, -1), Triple(1, 1, -1)
            )
            val backBottomRight = listOf(
                Triple(0, 0, -1), Triple(1, 0, -1), Triple(0, -1, -1), Triple(1, -1, -1)
            )

            val (blR, blG) = getVertexLight(x, y, z, chunk, backBottomLeft)
            val (tlR, tlG) = getVertexLight(x, y, z, chunk, backTopLeft)
            val (trR, trG) = getVertexLight(x, y, z, chunk, backTopRight)
            val (brR, brG) = getVertexLight(x, y, z, chunk, backBottomRight)

            vertices.addAll(listOf(
                x - half, y - half, z - half, 1f, 1f, blR, blG,
                x - half, y + half, z - half, 1f, 0f, tlR, tlG,
                x + half, y + half, z - half, 0f, 0f, trR, trG,

                x + half, y + half, z - half, 0f, 0f, trR, trG,
                x + half, y - half, z - half, 0f, 1f, brR, brG,
                x - half, y - half, z - half, 1f, 1f, blR, blG,
            ))
        }

        // === LEFT FACE ===
        "left" -> {
            val leftBottomBack = listOf(
                Triple(-1, 0, 0), Triple(-1, -1, 0), Triple(-1, 0, -1), Triple(-1, -1, -1)
            )
            val leftBottomFront = listOf(
                Triple(-1, 0, 0), Triple(-1, -1, 0), Triple(-1, 0, 1), Triple(-1, -1, 1)
            )
            val leftTopFront = listOf(
                Triple(-1, 0, 0), Triple(-1, 1, 0), Triple(-1, 0, 1), Triple(-1, 1, 1)
            )
            val leftTopBack = listOf(
                Triple(-1, 0, 0), Triple(-1, 1, 0), Triple(-1, 0, -1), Triple(-1, 1, -1)
            )

            val (bbR, bbG) = getVertexLight(x, y, z, chunk, leftBottomBack)
            val (bfR, bfG) = getVertexLight(x, y, z, chunk, leftBottomFront)
            val (tfR, tfG) = getVertexLight(x, y, z, chunk, leftTopFront)
            val (tbR, tbG) = getVertexLight(x, y, z, chunk, leftTopBack)

            vertices.addAll(listOf(
                x - half, y - half, z - half, 0f, 1f, bbR, bbG,
                x - half, y - half, z + half, 1f, 1f, bfR, bfG,
                x - half, y + half, z + half, 1f, 0f, tfR, tfG,

                x - half, y + half, z + half, 1f, 0f, tfR, tfG,
                x - half, y + half, z - half, 0f, 0f, tbR, tbG,
                x - half, y - half, z - half, 0f, 1f, bbR, bbG,
            ))
        }

        // === RIGHT FACE ===
        "right" -> {
            val rightBottomBack = listOf(
                Triple(1, 0, 0), Triple(1, -1, 0), Triple(1, 0, -1), Triple(1, -1, -1)
            )
            val rightBottomFront = listOf(
                Triple(1, 0, 0), Triple(1, -1, 0), Triple(1, 0, 1), Triple(1, -1, 1)
            )
            val rightTopFront = listOf(
                Triple(1, 0, 0), Triple(1, 1, 0), Triple(1, 0, 1), Triple(1, 1, 1)
            )
            val rightTopBack = listOf(
                Triple(1, 0, 0), Triple(1, 1, 0), Triple(1, 0, -1), Triple(1, 1, -1)
            )

            val (bbR, bbG) = getVertexLight(x, y, z, chunk, rightBottomBack)
            val (bfR, bfG) = getVertexLight(x, y, z, chunk, rightBottomFront)
            val (tfR, tfG) = getVertexLight(x, y, z, chunk, rightTopFront)
            val (tbR, tbG) = getVertexLight(x, y, z, chunk, rightTopBack)

            vertices.addAll(listOf(
                x + half, y - half, z - half, 1f, 1f, bbR, bbG,
                x + half, y + half, z + half, 0f, 0f, tfR, tfG,
                x + half, y - half, z + half, 0f, 1f, bfR, bfG,

                x + half, y + half, z + half, 0f, 0f, tfR, tfG,
                x + half, y - half, z - half, 1f, 1f, bbR, bbG,
                x + half, y + half, z - half, 1f, 0f, tbR, tbG,
            ))
        }

        // === TOP FACE ===
        "top" -> {
            val topBackLeft = listOf(
                Triple(0, 1, 0), Triple(-1, 1, 0), Triple(0, 1, -1), Triple(-1, 1, -1)
            )
            val topFrontLeft = listOf(
                Triple(0, 1, 0), Triple(-1, 1, 0), Triple(0, 1, 1), Triple(-1, 1, 1)
            )
            val topFrontRight = listOf(
                Triple(0, 1, 0), Triple(1, 1, 0), Triple(0, 1, 1), Triple(1, 1, 1)
            )
            val topBackRight = listOf(
                Triple(0, 1, 0), Triple(1, 1, 0), Triple(0, 1, -1), Triple(1, 1, -1)
            )

            val (blR, blG) = getVertexLight(x, y, z, chunk, topBackLeft)
            val (flR, flG) = getVertexLight(x, y, z, chunk, topFrontLeft)
            val (frR, frG) = getVertexLight(x, y, z, chunk, topFrontRight)
            val (brR, brG) = getVertexLight(x, y, z, chunk, topBackRight)

            vertices.addAll(listOf(
                x - half, y + half, z - half, 0f, 0f, blR, blG,
                x - half, y + half, z + half, 0f, 1f, flR, flG,
                x + half, y + half, z + half, 1f, 1f, frR, frG,

                x + half, y + half, z + half, 1f, 1f, frR, frG,
                x + half, y + half, z - half, 1f, 0f, brR, brG,
                x - half, y + half, z - half, 0f, 0f, blR, blG,
            ))
        }

        // === BOTTOM FACE ===
        "bottom" -> {
            val bottomBackLeft = listOf(
                Triple(0, -1, 0), Triple(-1, -1, 0), Triple(0, -1, -1), Triple(-1, -1, -1)
            )
            val bottomFrontRight = listOf(
                Triple(0, -1, 0), Triple(1, -1, 0), Triple(0, -1, 1), Triple(1, -1, 1)
            )
            val bottomFrontLeft = listOf(
                Triple(0, -1, 0), Triple(-1, -1, 0), Triple(0, -1, 1), Triple(-1, -1, 1)
            )
            val bottomBackRight = listOf(
                Triple(0, -1, 0), Triple(1, -1, 0), Triple(0, -1, -1), Triple(1, -1, -1)
            )

            val (blR, blG) = getVertexLight(x, y, z, chunk, bottomBackLeft)
            val (frR, frG) = getVertexLight(x, y, z, chunk, bottomFrontRight)
            val (flR, flG) = getVertexLight(x, y, z, chunk, bottomFrontLeft)
            val (brR, brG) = getVertexLight(x, y, z, chunk, bottomBackRight)

            vertices.addAll(listOf(
                x - half, y - half, z - half, 1f, 0f, blR, blG,
                x + half, y - half, z + half, 0f, 1f, frR, frG,
                x - half, y - half, z + half, 1f, 1f, flR, flG,

                x - half, y - half, z - half, 1f, 0f, blR, blG,
                x + half, y - half, z - half, 0f, 0f, brR, brG,
                x + half, y - half, z + half, 0f, 1f, frR, frG,
            ))
        }
    }
}
// @formatter:on


class Block {
}