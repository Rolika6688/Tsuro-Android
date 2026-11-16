package game.engine

import game.CORNER
import game.STRAIGHT
import game.ai.AiDifficulty
import game.ai.AiFactory
import game.board.Board
import game.board.Position
import game.player
import game.rules.LoopDetector
import game.stateOf
import game.tileOf
import game.ui.ranking
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class LoopDetectorTest {
    @Test fun openChainsAreNotLoops() {
        val b = Board.empty().place(1, 1, STRAIGHT).place(1, 2, STRAIGHT)
        assertTrue(LoopDetector.loopsClosedBy(b, 1, 2).isEmpty())
    }

    @Test fun twoTilesCanCloseASmallLoop() {
        val b = Board.empty().place(1, 1, CORNER).place(1, 0, tileOf(7, 4 to 5))
        val loops = LoopDetector.loopsClosedBy(b, 1, 0)
        assertEquals(1, loops.size); assertEquals(2, loops[0].size)
        assertTrue(LoopDetector.loopsClosedBy(Board.empty().place(1, 1, CORNER), 1, 1).isEmpty())
    }

    @Test fun aRingAroundAPointOfFourTilesIsDetectedOnlyWhenTheLastTileClosesIt() {
        val ring = listOf(Triple(0, 0, tileOf(1, 3 to 4)), Triple(1, 0, tileOf(2, 6 to 5)), Triple(1, 1, tileOf(3, 0 to 7)), Triple(0, 1, tileOf(4, 2 to 1)))
        var b = Board.empty()
        ring.dropLast(1).forEach { (x, y, t) -> b = b.place(x, y, t); assertTrue(LoopDetector.loopsClosedBy(b, x, y).isEmpty()) }
        val (x, y, t) = ring.last(); b = b.place(x, y, t)
        val loops = LoopDetector.loopsClosedBy(b, x, y)
        assertEquals(1, loops.size); assertEquals(4, loops[0].size)
    }
}

class ModesTest {
    private fun loopState(mode: GameMode, third: Boolean) = stateOf(
        listOf(player(0, Position(1, 0, 0), listOf(tileOf(7, 4 to 5, 0 to 3), STRAIGHT, STRAIGHT.copy(id = 102))), player(1, Position(1, 0, 4), listOf(tileOf(8)))) +
            (if (third) listOf(player(2, Position(5, 5, 4), listOf(tileOf(9)))) else emptyList()),
        Board.empty().place(1, 1, CORNER)
    ).copy(mode = mode)

    @Test fun loopBattleScoresLoopsAndTrappedOpponents() {
        val r = GameEngine.resolve(loopState(GameMode.LOOP_BATTLE, third = true), Move(0, 0))
        assertTrue(r.events.contains(GameEvent.LoopClosed(0, 2)))
        assertFalse(r.state.players[1].alive)
        assertEquals(1 * 1 + 1 * 3, r.state.players[0].score) // one loop + one trapped opponent
        assertEquals(GamePhase.PLAYING, r.state.phase)
    }

    @Test fun classicModeDoesNotScoreLoops() {
        val r = GameEngine.resolve(loopState(GameMode.CLASSIC, third = true), Move(0, 0))
        assertEquals(0, r.state.players[0].score)
    }

    @Test fun closingALoopAroundYourOwnStoneScoresTheLoopButNoTrap() {
        val s = stateOf(listOf(player(0, Position(1, 0, 4), listOf(tileOf(7, 4 to 5))), player(1, Position(5, 5, 4), listOf(tileOf(8)))), Board.empty().place(1, 1, CORNER)).copy(mode = GameMode.LOOP_BATTLE)
        val r = GameEngine.resolve(s, Move(0, 0)).state
        assertFalse(r.players[0].alive); assertEquals(1, r.players[0].score)
        assertEquals(2, r.players[1].score) // survivor bonus only
        assertEquals(listOf(1), r.winners)
    }

    @Test fun loopBattleWinnerIsTheHighestScoreAndRulesAreConfigurable() {
        val s = loopState(GameMode.LOOP_BATTLE, third = false).copy(rules = ModeRules(loopPoints = 5, trapPoints = 10, survivorBonus = 0))
        val r = GameEngine.resolve(s, Move(0, 0)).state
        assertEquals(15, r.players[0].score); assertEquals(GamePhase.FINISHED, r.phase); assertEquals(listOf(0), r.winners)
    }

    private val corners = listOf(CORNER, CORNER.copy(id = 102), CORNER.copy(id = 103))

    @Test fun longestPathWinnerCanBeAnEliminatedPlayer() {
        val base = listOf(player(0, Position(0, 0, 0), corners).copy(distance = 10), player(1, Position(5, 5, 4), corners).copy(distance = 3))
        val longest = GameEngine.resolve(stateOf(base).copy(mode = GameMode.LONGEST_PATH), Move(0, 0)).state
        assertFalse(longest.players[0].alive); assertEquals(listOf(0), longest.winners); assertEquals(11, longest.players[0].score)
        val classic = GameEngine.resolve(stateOf(base), Move(0, 0)).state
        assertEquals(listOf(1), classic.winners)
    }

    @Test fun longestPathTiesShareTheWin() {
        val base = listOf(player(0, Position(0, 0, 0), corners).copy(distance = 2), player(1, Position(5, 5, 4), corners).copy(distance = 3))
        assertEquals(listOf(0, 1), GameEngine.resolve(stateOf(base).copy(mode = GameMode.LONGEST_PATH), Move(0, 0)).state.winners)
    }

    @Test fun rankingUsesScoresInSpecialModes() {
        val s = stateOf(listOf(player(0, null, emptyList(), false).copy(score = 9, eliminatedOnTurn = 3), player(1, null, emptyList(), false).copy(score = 2, eliminatedOnTurn = 5), player(2, null, emptyList(), false).copy(score = 9, eliminatedOnTurn = 3)))
            .copy(winners = listOf(0, 2), phase = GamePhase.FINISHED, mode = GameMode.LOOP_BATTLE)
        assertEquals(listOf(0 to 1, 2 to 1, 1 to 3), ranking(s).map { it.player.id to it.rank })
    }

    @Test fun allModesPlayToTheEndWithEveryAiAndLoopsActuallyHappen() {
        var loopEvents = 0; var trapped = 0
        for (mode in GameMode.values()) for (n in listOf(2, 4, 8)) for (seed in 0L until 40L) {
            var s = GameEngine.newGame(GameConfig(n, 0, seed, mode)); val rng = Random(seed)
            while (s.phase == GamePhase.PLAYING) {
                val m = GameEngine.legalMoves(s); val r = GameEngine.applyMove(s, m[rng.nextInt(m.size)])
                loopEvents += r.events.count { it is GameEvent.LoopClosed }
                trapped += r.events.count { it is GameEvent.PlayerEliminated && it.reason == game.rules.EliminationReason.LOOPED }
                s = r.state
            }
            assertTrue(s.winners.isNotEmpty()); assertTrue(s.players.all { it.score >= 0 })
        }
        assertTrue("random games should close some loops (saw $loopEvents)", loopEvents > 0)
        println("   [info] loops closed: $loopEvents, stones trapped in loops: $trapped across 360 random games")
        for (mode in listOf(GameMode.LOOP_BATTLE, GameMode.LONGEST_PATH)) for (d in AiDifficulty.values()) {
            var s = GameEngine.newGame(GameConfig(3, 0, 5, mode)); val ai = AiFactory.create(d, 1)
            while (s.phase == GamePhase.PLAYING) { val m = ai.chooseMove(s); assertTrue(m in GameEngine.legalMoves(s)); s = GameEngine.applyMove(s, m).state }
        }
    }
}
