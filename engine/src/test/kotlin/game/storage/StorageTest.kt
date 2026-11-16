package game.storage

import game.ai.AiDifficulty
import game.ai.AiFactory
import game.engine.*
import game.expectThrows
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

private fun randomGame(n: Int, seed: Long, stopAtTurn: Int = Int.MAX_VALUE, humans: Int = 0): GameState {
    var s = GameEngine.newGame(GameConfig(n, humans, seed))
    val rng = Random(seed)
    while (s.phase == GamePhase.PLAYING && s.turn < stopAtTurn) { val m = GameEngine.legalMoves(s); s = GameEngine.applyMove(s, m[rng.nextInt(m.size)]).state }
    return s
}

private fun saved(s: GameState) = SavedGame(AiDifficulty.NORMAL, 42L, s)

class JsonTest {
    @Test fun roundTripsNestedValuesAndEscapes() {
        val v = linkedMapOf("a" to listOf(1L, 2.5, true, null, "x\"y\\z\n\t\u0001é"), "b" to linkedMapOf("c" to emptyList<Any?>(), "d" to -7L))
        assertEquals(v, Json.parse(Json.write(v)))
    }

    @Test fun malformedInputOnlyEverRaisesSaveFormatException() {
        for (bad in listOf("", "{", "[1,", "{\"a\":}", "tru", "\"abc", "1 2", "{\"a\" 1}", "[1 2]", "\"\\q\"", "\"\\u12\"", "-", "[".repeat(500)))
            expectThrows<SaveFormatException> { Json.parse(bad) }
        val rng = Random(3)
        val base = GameCodec.encode(saved(randomGame(4, 1, 6)))
        repeat(1500) {
            val chars = base.toCharArray(); repeat(1 + rng.nextInt(4)) { chars[rng.nextInt(chars.size)] = "{}[]\",:x9 -".random(rng) }
            try { Json.parse(String(chars)) } catch (e: SaveFormatException) { /* expected */ }
        }
    }
}

class GameCodecTest {
    @Test fun everyPositionOfManyGamesSurvivesASaveRestoreRoundTrip() {
        for (n in 2..8) for (seed in 0L until 12L) {
            var s = GameEngine.newGame(GameConfig(n, 1, seed, GameMode.values()[(seed % 4).toInt()])); val rng = Random(seed)
            while (true) {
                val g = SavedGame(AiDifficulty.HARD, seed, s)
                assertEquals(g, GameCodec.decode(GameCodec.encode(g)))
                if (s.phase != GamePhase.PLAYING) break
                val m = GameEngine.legalMoves(s); s = GameEngine.applyMove(s, m[rng.nextInt(m.size)]).state
            }
        }
    }

    @Test fun aRestoredGameContinuesExactlyLikeTheOriginal() {
        for (seed in 0L until 15L) {
            val mid = randomGame(4, seed, stopAtTurn = 6)
            if (mid.phase != GamePhase.PLAYING) continue
            val restored = GameCodec.decode(GameCodec.encode(saved(mid))).state
            fun finish(start: GameState): GameState { var s = start; val ai = AiFactory.create(AiDifficulty.NORMAL, 7); while (s.phase == GamePhase.PLAYING) s = GameEngine.applyMove(s, ai.chooseMove(s)).state; return s }
            assertEquals(finish(mid), finish(restored))
        }
    }

    @Test fun truncatedOrGarbageSavesAreRejectedCleanly() {
        val text = GameCodec.encode(saved(randomGame(4, 2, 8)))
        for (cut in listOf(0, 1, 10, text.length / 3, text.length / 2, text.length - 1)) expectThrows<SaveFormatException> { GameCodec.decode(text.substring(0, cut)) }
        expectThrows<SaveFormatException> { GameCodec.decode("hello") }
        expectThrows<SaveFormatException> { GameCodec.decode("[]") }
        expectThrows<SaveFormatException> { GameCodec.decode("{}") }
    }

    @Suppress("UNCHECKED_CAST")
    private fun tampered(edit: (MutableMap<String, Any?>) -> Unit): String {
        val root = Json.parse(GameCodec.encode(saved(randomGame(4, 5, 10)))) as MutableMap<String, Any?>
        edit(root); return Json.write(root)
    }

    @Suppress("UNCHECKED_CAST")
    private fun players(r: MutableMap<String, Any?>) = r["players"] as MutableList<MutableMap<String, Any?>>

    @Test fun inconsistentSavesAreRefused() {
        val cases = mapOf<String, (MutableMap<String, Any?>) -> Unit>(
            "future version" to { r -> r["v"] = 2L },
            "unknown mode" to { r -> r["mode"] = "NOPE" },
            "board too small" to { r -> (r["board"] as MutableList<Any?>).removeAt(0) },
            "duplicate tile" to { r -> val p = players(r)[0]; (p["hand"] as MutableList<Any?>).let { if (it.isNotEmpty()) it[0] = (r["deck"] as List<Any?>)[0] } },
            "altered deck tile" to { r -> ((r["deck"] as MutableList<Any?>)[0] as MutableList<Any?>).let { it[1] = it[2].also { _ -> it[2] = it[1] } } },
            "missing tile" to { r -> (r["deck"] as MutableList<Any?>).removeAt(0) },
            "one player" to { r -> (r["players"] as MutableList<Any?>).subList(1, 4).clear() },
            "current out of range" to { r -> r["current"] = 99L },
            "stone out of bounds" to { r -> players(r).first { it["alive"] == true }["stone"] = listOf(9L, 9L, 0L) },
            "bad port" to { r -> players(r).first { it["alive"] == true }["stone"] = listOf(1L, 1L, 8L) },
            "alive without stone" to { r -> players(r).first { it["alive"] == true }["stone"] = null },
            "negative turn" to { r -> r["turn"] = -1L },
            "winners while playing" to { r -> r["winners"] = listOf(0L) },
            "oversized hand" to { r -> val p = players(r)[0]; (p["hand"] as MutableList<Any?>).add((r["deck"] as MutableList<Any?>).removeAt(0)); (p["hand"] as MutableList<Any?>).add((r["deck"] as MutableList<Any?>).removeAt(0)) }
        )
        for ((name, edit) in cases) { try { GameCodec.decode(tampered(edit)); fail("accepted: $name") } catch (e: SaveFormatException) { /* ok */ } }
    }

    @Test fun twoStonesOnOnePointOrOntoATileAreRefused() {
        for (seed in 0L until 20L) {
            val s = randomGame(4, seed, 10); if (s.phase != GamePhase.PLAYING) continue
            @Suppress("UNCHECKED_CAST") val root = Json.parse(GameCodec.encode(saved(s))) as MutableMap<String, Any?>
            val alive = players(root).filter { it["alive"] == true }
            alive[1]["stone"] = alive[0]["stone"]
            expectThrows<SaveFormatException> { GameCodec.decode(Json.write(root)) }
            return
        }
    }
}

class SettingsAndStatsTest {
    @Test fun settingsRoundTripAndFallBackToDefaults() {
        val s = SettingsData(0.2f, 0.9f, 1.5f, false, AiDifficulty.HARD, 6, AppTheme.JADE)
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
        assertEquals(SettingsData(), SettingsCodec.decode(null)); assertEquals(SettingsData(), SettingsCodec.decode("garbage")); assertEquals(SettingsData(), SettingsCodec.decode("[]"))
        assertEquals(SettingsData(), SettingsCodec.decode("{}"))
    }

    @Test fun settingsAreClampedAndBadFieldsIgnoredIndividually() {
        val d = SettingsCodec.decode("{\"music\":7,\"sfx\":-3,\"speed\":99,\"players\":50,\"theme\":\"PINK\",\"haptics\":\"yes\",\"difficulty\":\"HARD\"}")
        assertEquals(1f, d.music, 0f); assertEquals(0f, d.sfx, 0f); assertEquals(2f, d.animationSpeed, 0f); assertEquals(8, d.playerCount)
        assertEquals(AppTheme.MIDNIGHT, d.theme); assertTrue(d.haptics); assertEquals(AiDifficulty.HARD, d.difficulty)
    }

    @Test fun statisticsCountWinsLossesAndKeepOnlyTheNewestFifty() {
        val win = StatisticsManager.recordOf(randomGame(2, 1, humans = 1).let { it }, GameMode.SOLO, AiDifficulty.NORMAL, 1000L)
        var s = Statistics.EMPTY
        s = StatisticsManager.record(s, win.copy(humanWon = true, turns = 12, longestPath = 9))
        s = StatisticsManager.record(s, win.copy(humanWon = false, turns = 20, longestPath = 4, endedAtMs = 2000))
        assertEquals(2, s.gamesPlayed); assertEquals(1, s.humanWins); assertEquals(1, s.humanLosses)
        assertEquals(32L, s.totalTurns); assertEquals(9, s.bestLongestPath); assertEquals(20, s.longestGameTurns)
        assertEquals(2, s.gamesByMode["SOLO"]); assertEquals(2000L, s.recent[0].endedAtMs)
        repeat(60) { s = StatisticsManager.record(s, win.copy(endedAtMs = 5000L + it)) }
        assertEquals(50, s.recent.size); assertEquals(5059L, s.recent[0].endedAtMs); assertEquals(62, s.gamesPlayed)
    }

    @Test fun gamesWithoutHumansDoNotCountAsWinsOrLosses() {
        val r = StatisticsManager.recordOf(randomGame(3, 2), GameMode.CLASSIC, AiDifficulty.EASY, 1L)
        val s = StatisticsManager.record(Statistics.EMPTY, r)
        assertEquals(1, s.gamesPlayed); assertEquals(0, s.humanWins); assertEquals(0, s.humanLosses)
    }

    @Test fun statisticsRoundTripAndDamagedDataGivesEmpty() {
        var s = Statistics.EMPTY
        for (seed in 0L until 5L) s = StatisticsManager.record(s, StatisticsManager.recordOf(randomGame(4, seed, humans = 1), GameMode.CLASSIC, AiDifficulty.HARD, seed))
        assertEquals(s, StatisticsManager.decode(StatisticsManager.encode(s)))
        assertEquals(Statistics.EMPTY, StatisticsManager.decode("{oops")); assertEquals(Statistics.EMPTY, StatisticsManager.decode(null))
        assertEquals(Statistics.EMPTY, StatisticsManager.decode("{\"played\":\"x\"}"))
    }
}
