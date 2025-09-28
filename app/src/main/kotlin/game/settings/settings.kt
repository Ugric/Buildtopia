package dev.wbell.buildtopia.app.game.settings

// Define keys + their types
// Each setting key has its own type parameter
sealed class SettingKey<T>(val key: String) {
    object FOV : SettingKey<Int>("fov")
    object RENDERDISTANCE : SettingKey<Int>("render_distance")
}

object Settings {
    private val data = mutableMapOf<SettingKey<*>, Any>()

    fun <T : Any> set(key: SettingKey<T>, value: T) {
        data[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(key: SettingKey<T>, defaultValue: T? = null): T? {
        val value = data[key]
        return if (value != null) value as T else defaultValue
    }
}