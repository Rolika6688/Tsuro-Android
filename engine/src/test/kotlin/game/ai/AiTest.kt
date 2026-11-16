package game.ai

import game.STRAIGHT
import game.board.Board
import game.board.Position
import game.engine.*
import game.player
import game.stateOf
import game.tileOf
import game.tiles.TileSet
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class EvaluationTest {
    @Test fun thereAre105OrientationsAndAllAreValid() {
        assertEquals(105, TileSet.allOrientations.size)
        assertEquals(105, TileSet.allOrientations.map { it.links }.distinct().size)
    }

    @Test fun survivalProbabilityReflectsBoardGeometry() {
        val b = Board.empty()
        assertEquals(1.0, Evaluation.survivalProbability(b, Position(2, 2, 0)), 1e-9)
        assertEquals(4.0 / 7.0, Evaluation.survivalProbability(b, Position(0, 0, 0)), 1e-9) // exits via ports 1, 6 or 7
        assertEquals(6.0 / 7.0, Evaluation.survivalProbability(b, Position(0, 2, 6)), 1e-9) // exits only via the other west port
    }

    @Test fun existingTilesCanLeadAStoneOffTheBoard() {
        val b = Board.empty().place(0, 1, STRAIGHT) // the stone at (0,0,5) would run (0,0)->(0,1)->... south, not off the board
        val open = Evaluation.survivalProbability(Board.empty(), Position(0, 0, 4))
        assertTrue(Evaluation.survivalProbability(b, Position(0, 0, 4)) <= open)
    }

    @Test fun loopClosingOrientationsCountAsDeadly() {
        val b = Board.empty().place(1, 1, game.CORNER)
        assertTrue(Evaluation.survivalProbability(b, Position(1, 0, 4)) < Evaluation.survivalProbability(Board.empty(), Position(1, 0, 4)))
    }

    @Test fun edgeDistanceAndSharedCells() {
        assertEquals(0, Evaluation.edgeDistance(Position(0, 3, 6)))
        assertEquals(2, Evaluation.edgeDistance(Position(2, 3, 0)))
        val s = stateOf(listOf(player(0, Position(2, 2, 0), emptyList()), player(1, Position(2, 2, 5), emptyList()), player(2, Position(4, 4, 0), emptyList())))
        assertEquals(1, Evaluation.sharedCellOpponents(s, 0)); assertEquals(0, Evaluation.sharedCellOpponents(s, 2))
    }

    @Test fun evaluationIsDeterministic() {
        val s = GameEngine.newGame(GameConfig(4, 0, 3))
        val m = GameEngine.legalMoves(s)[0]
        val r = GameEngine.resolve(s, m).state
        val ctx = Evaluation.Context(0, 3, setOf(0))
        assertEquals(Evaluation.evaluate(r, ctx, Evaluation.HARD), Evaluation.evaluate(r, ctx, Evaluation.HARD), 0.0)
    }
}

class AiTest {
    private fun allAis(seed: Long) = AiDifficulty.values().map { it to AiFactory.create(it, seed) }

    @Test fun everyDifficultyAlwaysPicksALegalMoveAndFinishesGames() {
        for (n in listOf(2, 3, 5, 8)) for (seed in 0L until 3L) for ((d, ai) in allAis(seed)) {
            var s = GameEngine.newGame(GameConfig(n, 0, seed * 13 + n))
            while (s.phase == GamePhase.PLAYING) {
                val m = ai.chooseMove(s)
                assertTrue("$d chose an illegal move", m in GameEngine.legalMoves(s))
                s = GameEngine.applyMove(s, m).state
            }
            assertTrue(s.winners.isNotEmpty())
        }
    }

    @Test fun noDifficultyWalksIntoAvoidableElimination() {
        for ((d, ai) in allAis(1)) for (seed in 0L until 6L) {
            var s = GameEngine.newGame(GameConfig(4, 0, seed))
            while (s.phase == GamePhase.PLAYING) {
                val hasSafe = s.players[s.current].hand.indices.any { h -> (0..3).any { GameEngine.resolve(s, Move(h, it)).state.players[s.current].alive } }
                val m = ai.chooseMove(s)
                if (hasSafe) assertTrue("$d chose a fatal move although a safe one existed", GameEngine.resolve(s, m).state.players[s.current].alive)
                s = GameEngine.applyMove(s, m).state
            }
        }
    }

    @Test fun choicesAreDeterministicForTheSameStateAndSeed() {
        val s = GameEngine.newGame(GameConfig(4, 0, 9))
        for (d in AiDifficulty.values()) assertEquals(AiFactory.create(d, 5).chooseMove(s), AiFactory.create(d, 5).chooseMove(s))
    }

    @Test fun normalAvoidsMovesThatLeadToTheBorder() {
        // From (1,1,0): STRAIGHT goes south into the interior; the 0-1 tile (and some rotations) sends the stone toward the border.
        val s = stateOf(listOf(player(0, Position(1, 1, 0), listOf(STRAIGHT, tileOf(102, 0 to 1), tileOf(103, 0 to 1))), player(1, Position(5, 5, 4), listOf(STRAIGHT))))
        val m = AiFactory.create(AiDifficulty.NORMAL, 0).chooseMove(s)
        val stone = GameEngine.resolve(s, m).state.players[0].stone!!
        assertTrue(Evaluation.edgeDistance(stone) >= 1)
    }

    @Test fun normalAndHardTakeAnImmediateWin() {
        val killer = tileOf(1, 6 to 7, 2 to 3) // sends player 1 off the west edge while player 0 slides east
        val s = stateOf(listOf(
            player(0, Position(0, 2, 2), listOf(killer, STRAIGHT, STRAIGHT.copy(id = 102))),
            player(1, Position(0, 2, 6), listOf(tileOf(5)))
        ))
        for (d in listOf(AiDifficulty.NORMAL, AiDifficulty.HARD)) {
            val r = GameEngine.applyMove(s, AiFactory.create(d, 0).chooseMove(s)).state
            assertEquals("$d should win at once", listOf(0), r.winners)
        }
    }

    @Test fun determinizationKeepsEverythingVisibleAndConservesTiles() {
        val s = GameEngine.newGame(GameConfig(4, 0, 4))
        val d = MonteCarloAi().determinize(s, Random(1))
        assertEquals(s.players[0].hand, d.players[0].hand)
        assertEquals(s.board.placedTiles(), d.board.placedTiles())
        assertEquals(s.players.map { it.stone }, d.players.map { it.stone })
        assertEquals(s.players.map { it.hand.size }, d.players.map { it.hand.size }); assertEquals(s.deck.size, d.deck.size)
        val ids = d.players.flatMap { p -> p.hand.map { it.id } } + d.deck.map { it.id }
        assertEquals(35, ids.distinct().size)
        val variants = (1L..5L).map { MonteCarloAi().determinize(s, Random(it)).players[1].hand }.distinct()
        assertTrue("hidden hands must be re-dealt", variants.size > 1)
    }

    @Test fun strongerLevelsBeatWeakerOnesInTwoPlayerGames() {
        fun wins(a: AiDifficulty, b: AiDifficulty, games: Int): Pair<Int, Int> {
            var wa = 0; var wb = 0
            for (seed in 0 until games) {
                val seat = seed % 2
                val ais = (0..1).map { if (it == seat) AiFactory.create(a, seed.toLong()) else AiFactory.create(b, seed.toLong() + 7) }
                var s = GameEngine.newGame(GameConfig(2, 0, seed.toLong()))
                while (s.phase == GamePhase.PLAYING) s = GameEngine.applyMove(s, ais[s.current].chooseMove(s)).state
                if (s.winners == listOf(seat)) wa++ else if (s.winners.size == 1) wb++
            }
            return wa to wb
        }
        val (n, e) = wins(AiDifficulty.NORMAL, AiDifficulty.EASY, 80)
        assertTrue("Normal $n vs Easy $e", n > 2 * e)
        val (h, e2) = wins(AiDifficulty.HARD, AiDifficulty.EASY, 40)
        assertTrue("Hard $h vs Easy $e2", h > 2 * e2)
        val (h2, n2) = wins(AiDifficulty.HARD, AiDifficulty.NORMAL, 60)
        assertTrue("Hard $h2 vs Normal $n2", h2 > n2)
    }
}
