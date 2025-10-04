package dev.wbell.buildtopia.app.game.session

import dev.wbell.buildtopia.app.game.session.world.World

class Session {
    var World: World? = null
    fun render(deltaTime:Double) {
        World?.render()
    }
}