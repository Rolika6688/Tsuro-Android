package game.rules

import game.CORNER
import game.STRAIGHT
import game.board.Board
import game.board.Position
import game.tileOf
import org.junit.Assert.*
import org.junit.Test

class PathResolverTest {
    @Test fun stoneOnEmptyCellDoesNotMove() {
        val t = PathResolver.trace(Board.empty(), Position(2, 2, 0))
        assertTrue(t.segments.isEmpty()); assertEquals(Position(2, 2, 0), t.end); assertFalse(t.exited)
    }

    @Test fun followsChainOfTilesToNextEmptyCell() {
        val b = Board.empty().place(2, 0, STRAIGHT).place(2, 1, STRAIGHT)
        val t = PathResolver.trace(b, Position(2, 0, 0))
        assertEquals(2, t.segments.size); assertEquals(Position(2, 2, 0), t.end); assertFalse(t.exited)
    }

    @Test fun leavingTheBoardIsDetected() {
        val t = PathResolver.trace(Board.empty().place(0, 0, CORNER), Position(0, 0, 0))
        assertTrue(t.exited); assertNull(t.end); assertEquals(1, t.segments.size)
    }

    @Test fun fullColumnOfStraightTilesExitsAtTheFarEdge() {
        var b = Board.empty()
        for (y in 0..5) b = b.place(2, y, STRAIGHT)
        val t = PathResolver.trace(b, Position(2, 0, 0))
        assertEquals(6, t.segments.size); assertTrue(t.exited)
    }

    @Test fun aPathThatClosesOnItselfIsDetectedAsALoop() {
        // The corner arc 0-1 of (1,1) ends at the point the stone sits on; a 4-5 tile at (1,0) closes the circle.
        val b = Board.empty().place(1, 1, CORNER).place(1, 0, tileOf(7, 4 to 5))
        val t = PathResolver.trace(b, Position(1, 0, 4))
        assertTrue(t.looped); assertFalse(t.exited); assertNull(t.end); assertEquals(2, t.segments.size)
        assertEquals(mapOf(0 to EliminationReason.LOOPED), CollisionResolver.resolve(mapOf(0 to t), emptyMap()))
    }

    @Test fun segmentsAreDirectionIndependent() {
        val b = Board.empty().place(2, 2, STRAIGHT)
        assertEquals(PathResolver.trace(b, Position(2, 2, 0)).segments[0], PathResolver.trace(b, Position(2, 2, 5)).segments[0])
    }
}

class CollisionResolverTest {
    private val b = Board.empty().place(2, 2, STRAIGHT)

    @Test fun headOnOnSamePathEliminatesBoth() {
        val r = CollisionResolver.resolve(mapOf(0 to PathResolver.trace(b, Position(2, 2, 0)), 1 to PathResolver.trace(b, Position(2, 2, 5))), emptyMap())
        assertEquals(mapOf(0 to EliminationReason.COLLISION, 1 to EliminationReason.COLLISION), r)
    }

    @Test fun differentPathsOnSameTileSurvive() {
        val r = CollisionResolver.resolve(mapOf(0 to PathResolver.trace(b, Position(2, 2, 0)), 1 to PathResolver.trace(b, Position(2, 2, 2))), emptyMap())
        assertTrue(r.isEmpty())
    }

    @Test fun endingOnAStationaryStoneEliminatesBoth() {
        val board = Board.empty().place(2, 2, tileOf(1, 0 to 3))
        val r = CollisionResolver.resolve(mapOf(0 to PathResolver.trace(board, Position(2, 2, 0))), mapOf(2 to Position(3, 2, 6)))
        assertEquals(setOf(0, 2), r.keys); assertTrue(r.values.all { it == EliminationReason.COLLISION })
    }

    @Test fun exitingTheBoardIsReportedAsExit() {
        val board = Board.empty().place(0, 0, CORNER)
        val r = CollisionResolver.resolve(mapOf(0 to PathResolver.trace(board, Position(0, 0, 0))), emptyMap())
        assertEquals(mapOf(0 to EliminationReason.EXITED_BOARD), r)
    }
}
