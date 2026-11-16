package game.storage

import game.ai.AiDifficulty

enum class AppTheme(val title: String) { MIDNIGHT("Midnight"), WALNUT("Walnut"), JADE("Jade") }

data class SettingsData(
    val music: Float = 0.5f,
    val sfx: Float = 0.7f,
    val animationSpeed: Float = 1f,
    val haptics: Boolean = true,
    val difficulty: AiDifficulty = AiDifficulty.NORMAL,
    val playerCount: Int = 4,
    val theme: AppTheme = AppTheme.MIDNIGHT
)

/** Never throws: missing, malformed or out-of-range values fall back to defaults / are clamped. */
object SettingsCodec {
    fun encode(s: SettingsData): String = Json.write(linkedMapOf(
        "music" to s.music, "sfx" to s.sfx, "speed" to s.animationSpeed, "haptics" to s.haptics,
        "difficulty" to s.difficulty.name, "players" to s.playerCount.toLong(), "theme" to s.theme.name
    ))

    fun decode(text: String?): SettingsData {
        val d = SettingsData()
        val m = try { Json.parse(text ?: return d).asMap() } catch (e: SaveFormatException) { return d }
        fun <T> field(key: String, default: T, read: (Any?) -> T): T = try { if (m.containsKey(key)) read(m[key]) else default } catch (e: SaveFormatException) { default }
        return SettingsData(
            music = field("music", d.music) { it.asDouble().toFloat().coerceIn(0f, 1f) },
            sfx = field("sfx", d.sfx) { it.asDouble().toFloat().coerceIn(0f, 1f) },
            animationSpeed = field("speed", d.animationSpeed) { it.asDouble().toFloat().coerceIn(0.5f, 2f) },
            haptics = field("haptics", d.haptics) { it.asBool() },
            difficulty = field("difficulty", d.difficulty) { v -> AiDifficulty.values().firstOrNull { it.name == v.asString() } ?: d.difficulty },
            playerCount = field("players", d.playerCount) { it.asInt().coerceIn(2, 8) },
            theme = field("theme", d.theme) { v -> AppTheme.values().firstOrNull { it.name == v.asString() } ?: d.theme }
        )
    }
}
