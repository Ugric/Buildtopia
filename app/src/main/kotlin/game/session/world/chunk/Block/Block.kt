package dev.wbell.buildtopia.app.game.session.world.chunk.Block


// @formatter:off
fun addFace(vertices: MutableList<Float>, x: Int, y: Int, z: Int, cubeScale: Float, face: String) {
    val half = cubeScale / 2f
    when(face) {
        "front" -> vertices.addAll(listOf(
            x - half, y - half, z + half, 0f, 1f,   // bottom-left
            x + half, y - half, z + half, 1f, 1f,   // bottom-right
            x + half, y + half, z + half, 1f, 0f,   // top-right

            x + half, y + half, z + half, 1f, 0f,
            x - half, y + half, z + half, 0f, 0f,   // top-left
            x - half, y - half, z + half, 0f, 1f
        ))
        "back" -> vertices.addAll(listOf(
            x - half, y - half, z - half, 1f, 1f,
            x + half, y - half, z - half, 0f, 1f,
            x + half, y + half, z - half, 0f, 0f,

            x + half, y + half, z - half, 0f, 0f,
            x - half, y + half, z - half, 1f, 0f,
            x - half, y - half, z - half, 1f, 1f
        ))
        "left" -> vertices.addAll(listOf(
            x - half, y - half, z - half, 0f, 1f,
            x - half, y - half, z + half, 1f, 1f,
            x - half, y + half, z + half, 1f, 0f,

            x - half, y + half, z + half, 1f, 0f,
            x - half, y + half, z - half, 0f, 0f,
            x - half, y - half, z - half, 0f, 1f
        ))
        "right" -> vertices.addAll(listOf(
            x + half, y - half, z - half, 1f, 1f,
            x + half, y - half, z + half, 0f, 1f,
            x + half, y + half, z + half, 0f, 0f,

            x + half, y + half, z + half, 0f, 0f,
            x + half, y + half, z - half, 1f, 0f,
            x + half, y - half, z - half, 1f, 1f
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
            x - half, y - half, z - half, 1f, 0f,
            x - half, y - half, z + half, 1f, 1f,
            x + half, y - half, z + half, 0f, 1f,

            x + half, y - half, z + half, 0f, 1f,
            x + half, y - half, z - half, 0f, 0f,
            x - half, y - half, z - half, 1f, 0f
        ))
    }
}
// @formatter:on



class Block {
}