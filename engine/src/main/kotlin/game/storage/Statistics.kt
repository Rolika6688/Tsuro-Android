package game.storage

import game.ai.AiDifficulty
import game.engine.GameMode
import game.engine.GameState
import game.players.PlayerKind

/** One finished game. */
data class GameRecord(
    val endedAtMs: Long, val mode: GameMode, val players: Int, val humans: Int, val difficulty: AiDifficulty,
    val turns: Int, val winners: List<String>, val humanWon: Boolean, val longestPath: Int
)

data class Statistics(
    val gamesPlayed: Int = 0,
    val humanWins: Int = 0,
    val humanLosses: Int = 0,
    val totalTurns: Long = 0,
    val bestLongestPath: Int = 0,
    val longestGameTurns: Int = 0,
    val gamesByMode: Map<String, Int> = emptyMap(),
    val recent: List<GameRecord> = emptyList()
) { companion object { val EMPTY = Statistics() } }

object StatisticsManager {
    const val KEEP_RECENT = 50

    fun recordOf(state: GameState, mode: GameMode, difficulty: AiDifficulty, nowMs: Long): GameRecord {
        val winners = state.winners.map { state.players[it] }
        return GameRecord(
            nowMs, mode, state.players.size, state.players.count { it.kind == PlayerKind.HUMAN }, difficulty, state.turn,
            winners.map { it.name }, winners.any { it.kind == PlayerKind.HUMAN }, state.players.maxOf { it.distance }
        )
    }

    /** A game counts as won when at least one human is among the winners (a shared win with a human counts). */
    fun record(s: Statistics, r: GameRecord): Statistics = s.copy(
        gamesPlayed = s.gamesPlayed + 1,
        humanWins = s.humanWins + if (r.humans > 0 && r.humanWon) 1 else 0,
        humanLosses = s.humanLosses + if (r.humans > 0 && !r.humanWon) 1 else 0,
        totalTurns = s.totalTurns + r.turns,
        bestLongestPath = maxOf(s.bestLongestPath, r.longestPath),
        longestGameTurns = maxOf(s.longestGameTurns, r.turns),
        gamesByMode = s.gamesByMode + (r.mode.name to (s.gamesByMode[r.mode.name] ?: 0) + 1),
        recent = (listOf(r) + s.recent).take(KEEP_RECENT)
    )

    fun encode(s: Statistics): String = Json.write(linkedMapOf(
        "played" to s.gamesPlayed.toLong(), "wins" to s.humanWins.toLong(), "losses" to s.humanLosses.toLong(),
        "turns" to s.totalTurns, "bestPath" to s.bestLongestPath.toLong(), "longest" to s.longestGameTurns.toLong(),
        "byMode" to s.gamesByMode.mapValues { it.value.toLong() },
        "recent" to s.recent.map { r -> linkedMapOf(
            "at" to r.endedAtMs, "mode" to r.mode.name, "players" to r.players.toLong(), "humans" to r.humans.toLong(),
            "difficulty" to r.difficulty.name, "turns" to r.turns.toLong(), "winners" to r.winners, "humanWon" to r.humanWon, "path" to r.longestPath.toLong()) }
    ))

    /** Never throws: damaged data yields empty statistics. */
    fun decode(text: String?): Statistics = try {
        val m = Json.parse(text ?: return Statistics.EMPTY).asMap()
        Statistics(
            m["played"].asInt(), m["wins"].asInt(), m["losses"].asInt(), m["turns"].asLong(), m["bestPath"].asInt(), m["longest"].asInt(),
            m["byMode"].asMap().mapValues { it.value.asInt() },
            m["recent"].asList().map { rv ->
                val r = rv.asMap()
                GameRecord(r["at"].asLong(), GameMode.values().first { it.name == r["mode"].asString() }, r["players"].asInt(), r["humans"].asInt(),
                    AiDifficulty.values().first { it.name == r["difficulty"].asString() }, r["turns"].asInt(), r["winners"].asList().map { it.asString() },
                    r["humanWon"].asBool(), r["path"].asInt())
            }
        )
    } catch (e: RuntimeException) { Statistics.EMPTY }
}
