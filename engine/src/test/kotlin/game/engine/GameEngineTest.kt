package game.engine

import game.CORNER
import game.STRAIGHT
import game.board.Board
import game.board.Position
import game.expectThrows
import game.players.PlayerKind
import game.player
import game.rules.EliminationReason
import game.stateOf
import game.tileOf
import org.junit.Assert.*
import org.junit.Test

class NewGameTest {
    @Test fun defaultIsFourPlayersWithThreeTilesEach() {
        val s = GameEngine.newGame(GameConfig())
        assertEquals(4, s.players.size)
        assertTrue(s.players.all { it.hand.size == 3 && it.alive })
        assertEquals(35 - 12, s.deck.size)
        assertEquals(PlayerKind.HUMAN, s.players[0].kind); assertEquals(PlayerKind.AI, s.players[1].kind)
        assertEquals(GamePhase.PLAYING, s.phase)
    }

    @Test fun everyPlayerCountFrom2To8Works() {
        for (n in 2..8) {
            val s = GameEngine.newGame(GameConfig(n, 1, 7))
            assertEquals(n, s.players.map { it.stone }.distinct().size)
            assertEquals(35 - 3 * n, s.deck.size)
            assertEquals(n, s.players.map { it.color }.distinct().size)
        }
    }

    @Test fun invalidConfigsAreRejected() {
        expectThrows<IllegalArgumentException> { GameEngine.newGame(GameConfig(1)) }
        expectThrows<IllegalArgumentException> { GameEngine.newGame(GameConfig(9)) }
        expectThrows<IllegalArgumentException> { GameEngine.newGame(GameConfig(4, 5)) }
    }

    @Test fun sameSeedGivesSameGame() {
        assertEquals(GameEngine.newGame(GameConfig(4, 1, 42)).players, GameEngine.newGame(GameConfig(4, 1, 42)).players)
        assertNotEquals(GameEngine.newGame(GameConfig(4, 1, 1)).players.map { it.hand }, GameEngine.newGame(GameConfig(4, 1, 2)).players.map { it.hand })
    }
}

class MovementTest {
    private val spare = listOf(tileOf(102), tileOf(103))

    @Test fun twoStonesOnTheSameTileMoveSimultaneously() {
        val s = stateOf(listOf(player(0, Position(2, 2, 0), listOf(STRAIGHT) + spare), player(1, Position(2, 2, 2), spare)))
        val r = GameEngine.applyMove(s, Move(0, 0))
        assertEquals(Position(2, 3, 0), r.state.players[0].stone)
        assertEquals(Position(1, 2, 2), r.state.players[1].stone)
        assertEquals(1, r.state.players[0].distance); assertEquals(1, r.state.players[1].distance)
        assertEquals(STRAIGHT, r.state.board.tileAt(2, 2))
        assertEquals(2, r.events.count { it is GameEvent.StoneMoved })
    }

    @Test fun stoneContinuesThroughExistingTiles() {
        val board = Board.empty().place(2, 3, STRAIGHT).place(2, 4, STRAIGHT)
        val s = stateOf(listOf(player(0, Position(2, 2, 0), listOf(STRAIGHT) + spare), player(1, Position(5, 5, 4), spare)), board)
        val r = GameEngine.applyMove(s, Move(0, 0))
        assertEquals(Position(2, 5, 0), r.state.players[0].stone)
        assertEquals(3, r.state.players[0].distance)
    }

    @Test fun otherStonesNotFacingTheCellStayPut() {
        val s = stateOf(listOf(player(0, Position(2, 2, 0), listOf(STRAIGHT) + spare), player(1, Position(5, 5, 4), spare)))
        val r = GameEngine.applyMove(s, Move(0, 0))
        assertEquals(Position(5, 5, 4), r.state.players[1].stone); assertEquals(0, r.state.players[1].distance)
    }
}

class EliminationTest {
    private val corners = listOf(CORNER, CORNER.copy(id = 102), CORNER.copy(id = 103))

    @Test fun reachingTheBoardEdgeEliminatesWhenForced() {
        val s = stateOf(listOf(player(0, Position(0, 0, 0), corners), player(1, Position(5, 5, 4), corners)))
        assertEquals(12, GameEngine.legalMoves(s).size)
        val r = GameEngine.applyMove(s, Move(0, 0))
        val p0 = r.state.players[0]
        assertFalse(p0.alive); assertNull(p0.stone); assertEquals(1, p0.eliminatedOnTurn)
        assertTrue(r.events.contains(GameEvent.PlayerEliminated(0, EliminationReason.EXITED_BOARD)))
        assertEquals(GamePhase.FINISHED, r.state.phase); assertEquals(listOf(1), r.state.winners)
    }

    @Test fun suicideMovesAreIllegalWhenASafeMoveExists() {
        val s = stateOf(listOf(player(0, Position(0, 0, 0), listOf(CORNER, STRAIGHT, tileOf(102, 0 to 5))), player(1, Position(5, 5, 4), corners)))
        val legal = GameEngine.legalMoves(s)
        assertTrue(legal.isNotEmpty()); assertTrue(legal.none { it.handIndex == 0 })
        expectThrows<IllegalMoveException> { GameEngine.applyMove(s, Move(0, 0)) }
    }

    @Test fun eliminatedHandReturnsToDeckBottom() {
        val s = stateOf(listOf(player(0, Position(0, 0, 0), corners), player(1, Position(5, 5, 4), corners)), deck = listOf(tileOf(200)))
        val r = GameEngine.resolve(s, Move(0, 0))
        assertEquals(3, r.state.deck.size); assertEquals(200, r.state.deck[0].id)
    }

    @Test fun headOnCollisionOfTheLastTwoPlayersIsASharedWin() {
        val s = stateOf(listOf(player(0, Position(2, 2, 0), listOf(tileOf(1, 0 to 5))), player(1, Position(2, 2, 5), listOf(tileOf(2)))))
        val r = GameEngine.resolve(s, Move(0, 0))
        assertTrue(r.state.players.none { it.alive })
        assertEquals(GamePhase.FINISHED, r.state.phase); assertEquals(listOf(0, 1), r.state.winners)
        assertTrue(r.events.last() is GameEvent.GameOver)
    }

    @Test fun closingALoopAroundYourOwnStoneEliminatesIt() {
        val board = Board.empty().place(1, 1, CORNER)
        val s = stateOf(listOf(player(0, Position(1, 0, 4), listOf(tileOf(7, 4 to 5))), player(1, Position(5, 5, 4), listOf(tileOf(8)))), board)
        val r = GameEngine.resolve(s, Move(0, 0))
        assertFalse(r.state.players[0].alive)
        assertTrue(r.events.contains(GameEvent.PlayerEliminated(0, EliminationReason.LOOPED)))
        assertEquals(listOf(1), r.state.winners)
    }

    @Test fun movingOntoAStationaryStoneKillsBothAndLeavesTheOtherAsWinner() {
        val s = stateOf(listOf(
            player(0, Position(2, 2, 0), listOf(tileOf(1, 0 to 3))),
            player(1, Position(5, 5, 4), listOf(tileOf(2))),
            player(2, Position(3, 2, 6), listOf(tileOf(3)))
        ))
        val r = GameEngine.resolve(s, Move(0, 0))
        assertEquals(listOf(false, true, false), r.state.players.map { it.alive })
        assertEquals(listOf(1), r.state.winners)
    }
}

class TurnAndVictoryTest {
    private val corners = listOf(CORNER, CORNER.copy(id = 102), CORNER.copy(id = 103))

    @Test fun turnSkipsEliminatedPlayersAndHandIsRefilled() {
        val s = stateOf(
            listOf(player(0, Position(2, 2, 0), listOf(STRAIGHT, tileOf(102), tileOf(103))), player(1, null, emptyList(), alive = false), player(2, Position(5, 5, 4), listOf(tileOf(104)))),
            deck = listOf(tileOf(200), tileOf(201))
        )
        val r = GameEngine.applyMove(s, Move(0, 0)).state
        assertEquals(2, r.current); assertEquals(1, r.turn)
        assertEquals(3, r.players[0].hand.size) // mover refills first
        assertEquals(2, r.players[2].hand.size) // next clockwise takes the last tile
        assertEquals(0, r.deck.size)
        assertEquals(GamePhase.PLAYING, r.phase)
    }

    @Test fun lastSurvivorWinsAfterTwoSuccessiveEliminations() {
        val s = stateOf(listOf(player(0, Position(0, 0, 0), corners), player(1, Position(5, 5, 4), corners), player(2, Position(5, 0, 2), corners)))
        val a = GameEngine.applyMove(s, GameEngine.legalMoves(s)[0]).state
        assertEquals(GamePhase.PLAYING, a.phase); assertEquals(1, a.current); assertEquals(2, a.alivePlayers.size)
        val b = GameEngine.applyMove(a, GameEngine.legalMoves(a)[0]).state
        assertEquals(GamePhase.FINISHED, b.phase); assertEquals(listOf(2), b.winners)
        assertEquals(1, b.players[0].eliminatedOnTurn); assertEquals(2, b.players[1].eliminatedOnTurn)
    }

    @Test fun invalidOrLateMovesAreRejected() {
        val s = stateOf(listOf(player(0, Position(2, 2, 0), listOf(STRAIGHT)), player(1, Position(5, 5, 4), listOf(STRAIGHT))))
        expectThrows<IllegalMoveException> { GameEngine.applyMove(s, Move(5, 0)) }
        expectThrows<IllegalMoveException> { GameEngine.applyMove(s, Move(0, 4)) }
        val done = GameEngine.applyMove(stateOf(listOf(player(0, Position(0, 0, 0), corners), player(1, Position(5, 5, 4), corners))), Move(0, 0)).state
        assertTrue(GameEngine.legalMoves(done).isEmpty())
        expectThrows<IllegalMoveException> { GameEngine.applyMove(done, Move(0, 0)) }
    }

    @Test fun randomFullGamesAlwaysTerminateWithConservedTiles() {
        val rng = kotlin.random.Random(5)
        for (n in 2..8) for (seed in 0L until 40L) {
            var s = GameEngine.newGame(GameConfig(n, 0, seed))
            while (s.phase == GamePhase.PLAYING) {
                val m = GameEngine.legalMoves(s); assertTrue(m.isNotEmpty())
                s = GameEngine.applyMove(s, m[rng.nextInt(m.size)]).state
                val ids = s.board.placedTiles().map { it.id } + s.deck.map { it.id } + s.players.flatMap { p -> p.hand.map { it.id } }
                assertEquals(35, ids.distinct().size); assertEquals(35, ids.size)
                assertTrue(s.turn <= 40)
            }
            assertTrue(s.winners.isNotEmpty())
        }
    }
}
