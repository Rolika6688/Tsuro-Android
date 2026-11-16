package game.ui

import game.CORNER
import game.STRAIGHT
import game.ai.RandomAi
import game.board.Position
import game.engine.*
import game.player
import game.players.PlayerKind
import game.stateOf
import game.tileOf
import org.junit.Assert.*
import org.junit.Test

class GameControllerTest {
    private val spare = listOf(tileOf(102), tileOf(103))
    private fun twoHumans() = stateOf(listOf(player(0, Position(2, 2, 0), listOf(STRAIGHT) + spare), player(1, Position(5, 5, 4), spare)))

    @Test fun nothingIsPreviewedUntilATileIsSelected() {
        val c = GameController(twoHumans(), RandomAi())
        assertNull(c.preview()); assertFalse(c.snapshot().canConfirm); assertNull(c.confirm())
    }

    @Test fun selectingShowsPreviewAtTheStonesCell() {
        val c = GameController(twoHumans(), RandomAi())
        c.select(0)
        val p = c.preview()!!
        assertEquals(2, p.x); assertEquals(2, p.y); assertEquals(STRAIGHT, p.tile); assertTrue(p.legal); assertFalse(p.eliminatesMover)
    }

    @Test fun rotationCyclesAndResetsOnNewSelection() {
        val c = GameController(twoHumans(), RandomAi())
        c.rotate(); assertEquals(0, c.snapshot().rotation) // no tile selected yet
        c.select(1); repeat(3) { c.rotate() }; assertEquals(3, c.snapshot().rotation)
        c.rotate(); assertEquals(0, c.snapshot().rotation)
        c.rotate(); c.select(2); assertEquals(0, c.snapshot().rotation)
        assertEquals(tileOf(103), c.preview()!!.tile)
    }

    @Test fun rotatedPreviewMatchesTheTileRotation() {
        val t = tileOf(1, 0 to 1)
        val s = stateOf(listOf(player(0, Position(2, 2, 0), listOf(t, STRAIGHT, STRAIGHT)), player(1, Position(5, 5, 4), spare)))
        val c = GameController(s, RandomAi()); c.select(0); c.rotate()
        assertEquals(t.rotated(1), c.preview()!!.tile)
    }

    @Test fun confirmAppliesTheMoveAndClearsSelection() {
        val c = GameController(twoHumans(), RandomAi())
        c.select(0)
        val r = c.confirm()!!
        assertEquals(STRAIGHT, c.state.board.tileAt(2, 2)); assertEquals(1, c.state.turn)
        assertNull(c.snapshot().selected); assertNull(c.preview())
        assertTrue(r.events.isNotEmpty()); assertEquals(r.events, c.snapshot().lastEvents)
    }

    @Test fun illegalSuicidePlacementCannotBeConfirmed() {
        val s = stateOf(listOf(player(0, Position(0, 0, 0), listOf(CORNER, STRAIGHT, tileOf(102, 0 to 5))), player(1, Position(5, 5, 4), spare)))
        val c = GameController(s, RandomAi()); c.select(0)
        val p = c.preview()!!
        assertFalse(p.legal); assertTrue(p.eliminatesMover); assertFalse(c.snapshot().canConfirm)
        assertNull(c.confirm()); assertEquals(0, c.state.turn)
    }

    @Test fun humanActionsAreIgnoredOnAnAiTurn() {
        val s = twoHumans().let { it.copy(players = it.players.mapIndexed { i, p -> if (i == 0) p.copy(kind = PlayerKind.AI) else p }) }
        val c = GameController(s, RandomAi())
        assertFalse(c.isHumanTurn); assertTrue(c.isAiTurn)
        c.select(0); assertNull(c.snapshot().selected); assertNull(c.confirm())
        assertNotNull(c.runAiTurn()); assertEquals(1, c.state.turn)
        assertNull(c.runAiTurn()) // now player 1 (human) is up
    }

    @Test fun aiTurnAlwaysUsesALegalMove() {
        for (seed in 0L until 30L) {
            val c = GameController(GameEngine.newGame(GameConfig(4, 0, seed)).let { st -> st.copy(players = st.players.map { it.copy(kind = PlayerKind.AI) }) }, RandomAi(seed))
            while (c.isAiTurn) { val before = c.state; val r = c.runAiTurn()!!; assertEquals(before.turn + 1, r.state.turn) }
            assertEquals(GamePhase.FINISHED, c.state.phase)
            assertFalse(c.isHumanTurn); assertFalse(c.isAiTurn)
        }
    }

    @Test fun rankingPutsWinnerFirstThenLaterEliminationsAndSharesRanksOnTies() {
        val corners = listOf(CORNER, CORNER.copy(id = 102), CORNER.copy(id = 103))
        var s = stateOf(listOf(player(0, Position(0, 0, 0), corners), player(1, Position(5, 5, 4), corners), player(2, Position(5, 0, 2), corners)))
        s = GameEngine.applyMove(s, GameEngine.legalMoves(s)[0]).state
        s = GameEngine.applyMove(s, GameEngine.legalMoves(s)[0]).state
        assertEquals(listOf(2 to 1, 1 to 2, 0 to 3), ranking(s).map { it.player.id to it.rank })
        val tie = GameEngine.resolve(stateOf(listOf(player(0, Position(2, 2, 0), listOf(tileOf(1, 0 to 5))), player(1, Position(2, 2, 5), listOf(tileOf(2))))), Move(0, 0)).state
        assertEquals(listOf(1, 1), ranking(tie).map { it.rank })
    }
}
