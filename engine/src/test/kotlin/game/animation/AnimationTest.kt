package game.animation

import game.CORNER
import game.STRAIGHT
import game.board.Position
import game.engine.*
import game.player
import game.stateOf
import game.tileOf
import game.tiles.Port
import game.tiles.PortGeometry
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

private fun close(a: Float, b: Float) = abs(a - b) < 1e-4f

class AnimationTest {
    @Test fun facingPortsAreTheSamePhysicalPointOnNeighbouringCells() {
        for (x in 0..5) for (y in 0..5) for (p in 0..7) {
            val s = Port.side(p)
            val nx = x + s.dx; val ny = y + s.dy
            if (nx !in 0..5 || ny !in 0..5) continue
            val a = PortGeometry.point(x, y, p); val b = PortGeometry.point(nx, ny, Port.facing(p))
            assertTrue("($x,$y,$p)", close(a.x, b.x) && close(a.y, b.y))
        }
    }

    @Test fun curveStartsAndEndsAtItsPorts() {
        val a = PortGeometry.curve(2, 3, 0, 5, 0f); val b = PortGeometry.curve(2, 3, 0, 5, 1f)
        assertEquals(PortGeometry.point(2, 3, 0), a); assertEquals(PortGeometry.point(2, 3, 5), b)
    }

    private val spare = listOf(tileOf(102), tileOf(103))

    @Test fun planOrdersStepsAndScalesWithSpeed() {
        val s = stateOf(listOf(player(0, Position(0, 0, 0), listOf(CORNER) + spare), player(1, Position(5, 5, 4), spare)))
        val r = GameEngine.resolve(s, Move(0, 0))
        val p1 = AnimationPlanner.plan(s, r.events, 1f); val p2 = AnimationPlanner.plan(s, r.events, 2f)
        assertEquals(listOf(StepKind.TILE_PLACED, StepKind.STONES_MOVE, StepKind.ELIMINATION, StepKind.VICTORY), p1.steps.map { it.kind })
        var t = 0L; for (st in p1.steps) { assertEquals(t, st.startMs); t += st.durationMs }
        assertEquals(t, p1.totalMs)
        assertTrue(p2.totalMs <= p1.totalMs / 2 + 4); assertTrue(p2.totalMs >= p1.totalMs / 2 - 4)
        assertEquals(listOf(1), p1.winners)
    }

    @Test fun quietMoveHasNoEliminationOrVictoryStep() {
        val s = stateOf(listOf(player(0, Position(2, 2, 0), listOf(STRAIGHT) + spare), player(1, Position(5, 5, 4), spare)))
        val p = AnimationPlanner.plan(s, GameEngine.applyMove(s, Move(0, 0)).events)
        assertEquals(listOf(StepKind.TILE_PLACED, StepKind.STONES_MOVE), p.steps.map { it.kind })
        assertEquals(setOf(1), p.stationary.keys)
    }

    @Test fun frameAtStartAndEndMatchesStartAndEndPositions() {
        val s = stateOf(listOf(player(0, Position(2, 2, 0), listOf(STRAIGHT) + spare), player(1, Position(5, 5, 4), spare)))
        val r = GameEngine.applyMove(s, Move(0, 0))
        val plan = AnimationPlanner.plan(s, r.events)
        val f0 = AnimationPlanner.frameAt(plan, 0); val fe = AnimationPlanner.frameAt(plan, plan.totalMs)
        val start = PortGeometry.point(2, 2, 0); val end = PortGeometry.point(2, 3, 0)
        assertTrue(close(f0.stones[0]!!.x, start.x) && close(f0.stones[0]!!.y, start.y))
        assertTrue(close(fe.stones[0]!!.x, end.x) && close(fe.stones[0]!!.y, end.y))
        assertEquals(0f, f0.tile!!.progress, 0f); assertEquals(1f, fe.tile!!.progress, 0f); assertTrue(fe.finished); assertFalse(f0.finished)
    }

    @Test fun eliminatedStonesFadeOutAndSurvivorsStayVisible() {
        val corners = listOf(CORNER, CORNER.copy(id = 102), CORNER.copy(id = 103))
        val s = stateOf(listOf(player(0, Position(0, 0, 0), corners), player(1, Position(5, 5, 4), corners)))
        val plan = AnimationPlanner.plan(s, GameEngine.applyMove(s, Move(0, 0)).events)
        val end = AnimationPlanner.frameAt(plan, plan.totalMs)
        assertEquals(0f, end.stones[0]!!.alpha, 1e-6f); assertEquals(1f, end.stones[1]!!.alpha, 1e-6f)
        assertEquals(1f, end.victory, 1e-6f)
        val mid = AnimationPlanner.frameAt(plan, plan.step(StepKind.ELIMINATION)!!.startMs + plan.step(StepKind.ELIMINATION)!!.durationMs / 2)
        assertTrue(mid.stones[0]!!.alpha in 0.3f..0.7f)
    }

    @Test fun animationEndsExactlyWhereTheEngineSaysTheStonesAre() {
        for (n in 2..8) for (seed in 0L until 25L) {
            var s = GameEngine.newGame(GameConfig(n, 0, seed))
            val rng = kotlin.random.Random(seed)
            while (s.phase == GamePhase.PLAYING) {
                val moves = GameEngine.legalMoves(s)
                val r = GameEngine.applyMove(s, moves[rng.nextInt(moves.size)])
                val plan = AnimationPlanner.plan(s, r.events)
                var prev: Map<Int, StonePose>? = null
                var t = 0L
                while (true) {
                    val f = AnimationPlanner.frameAt(plan, t)
                    for (p in f.stones.values) assertTrue("pose out of range ${p.x},${p.y}", p.x in -0.1f..6.1f && p.y in -0.1f..6.1f)
                    prev?.let { pv -> for ((id, p) in f.stones) pv[id]?.let { q -> assertTrue("stone jumps ${hypot(p.x - q.x, p.y - q.y)}", hypot(p.x - q.x, p.y - q.y) < 0.5f) } }
                    prev = f.stones
                    if (f.finished) break
                    t += 16
                }
                val last = AnimationPlanner.frameAt(plan, plan.totalMs)
                for (pl in r.state.players) if (pl.alive) {
                    val pt = PortGeometry.point(pl.stone!!.x, pl.stone.y, pl.stone.port)
                    val pose = last.stones[pl.id]!!
                    assertTrue("stone ${pl.id} animation end (${pose.x},${pose.y}) != state (${pt.x},${pt.y})", close(pose.x, pt.x) && close(pose.y, pt.y) && pose.alpha == 1f)
                }
                for (id in plan.eliminated) assertEquals(0f, last.stones[id]!!.alpha, 1e-6f)
                s = r.state
            }
        }
    }
}
