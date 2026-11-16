package game.animation

import game.board.Position
import game.engine.GameEvent
import game.engine.GameState
import game.rules.Segment
import game.tiles.Port
import game.tiles.PortGeometry
import game.tiles.Pt
import game.tiles.Tile
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class StepKind { TILE_PLACED, STONES_MOVE, ELIMINATION, VICTORY }
data class AnimStep(val kind: StepKind, val startMs: Long, val durationMs: Long)

data class TileAnim(val x: Int, val y: Int, val tile: Tile, val progress: Float)
data class StonePose(val x: Float, val y: Float, val scale: Float, val alpha: Float)

/** What the renderer draws at one instant. The board underneath is the state from BEFORE the move. */
data class AnimFrame(
    val tile: TileAnim?,
    val stones: Map<Int, StonePose>,
    val victory: Float,
    val winners: List<Int>,
    val finished: Boolean
)

class StonePlan(val playerId: Int, val polyline: List<Pt>, val eliminated: Boolean, val moveMs: Long) {
    private val cumulative: FloatArray = FloatArray(polyline.size).also { c ->
        for (i in 1 until polyline.size) c[i] = c[i - 1] + hypot(polyline[i].x - polyline[i - 1].x, polyline[i].y - polyline[i - 1].y)
    }
    val length: Float get() = cumulative.last()
    val start: Pt get() = polyline.first()
    val end: Pt get() = polyline.last()

    fun pointAt(distance: Float): Pt {
        if (polyline.size == 1 || distance <= 0f) return start
        if (distance >= length) return end
        var i = 1
        while (cumulative[i] < distance) i++
        val seg = cumulative[i] - cumulative[i - 1]
        val f = if (seg == 0f) 0f else (distance - cumulative[i - 1]) / seg
        return Pt(polyline[i - 1].x + (polyline[i].x - polyline[i - 1].x) * f, polyline[i - 1].y + (polyline[i].y - polyline[i - 1].y) * f)
    }
}

class AnimationPlan(
    val steps: List<AnimStep>,
    val placed: GameEvent.TilePlaced?,
    val stones: List<StonePlan>,
    val stationary: Map<Int, Pt>,
    val eliminated: Set<Int>,
    val winners: List<Int>,
    val totalMs: Long
) {
    fun step(kind: StepKind): AnimStep? = steps.firstOrNull { it.kind == kind }
}

object AnimationPlanner {
    const val TILE_MS = 300L
    const val MS_PER_CELL = 260f
    const val MIN_MOVE_MS = 200L
    const val ELIMINATION_MS = 550L
    const val VICTORY_MS = 1400L
    private const val SAMPLES_PER_SEGMENT = 10

    /** [speed] > 1 is faster. Steps run one after another: tile, movement, eliminations, victory. */
    fun plan(before: GameState, events: List<GameEvent>, speed: Float = 1f): AnimationPlan {
        require(speed > 0f) { "speed must be positive" }
        fun ms(base: Long) = max(1L, (base / speed).toLong())

        val placed = events.filterIsInstance<GameEvent.TilePlaced>().firstOrNull()
        val moves = events.filterIsInstance<GameEvent.StoneMoved>()
        val eliminated = events.filterIsInstance<GameEvent.PlayerEliminated>().map { it.player }.toSet()
        val over = events.filterIsInstance<GameEvent.GameOver>().firstOrNull()

        val stones = moves.map { e ->
            val line = polyline(e.from, e.segments)
            val probe = StonePlan(e.player, line, e.player in eliminated, 0)
            StonePlan(e.player, line, e.player in eliminated, max(ms(MIN_MOVE_MS), (probe.length * MS_PER_CELL / speed).toLong()))
        }
        val movedIds = moves.map { it.player }.toSet()
        val stationary = before.players.filter { it.alive && it.id !in movedIds && it.stone != null }
            .associate { it.id to PortGeometry.point(it.stone!!.x, it.stone.y, it.stone.port) }

        val steps = ArrayList<AnimStep>()
        var t = 0L
        fun add(kind: StepKind, d: Long) { steps += AnimStep(kind, t, d); t += d }
        if (placed != null) add(StepKind.TILE_PLACED, ms(TILE_MS))
        if (stones.isNotEmpty()) add(StepKind.STONES_MOVE, stones.maxOf { it.moveMs })
        if (eliminated.isNotEmpty()) add(StepKind.ELIMINATION, ms(ELIMINATION_MS))
        if (over != null) add(StepKind.VICTORY, ms(VICTORY_MS))
        return AnimationPlan(steps, placed, stones, stationary, eliminated, over?.winners ?: emptyList(), t)
    }

    /** Sampled curve the stone actually travels, in board coordinates. */
    internal fun polyline(from: Position, segments: List<Segment>): List<Pt> {
        val pts = ArrayList<Pt>()
        pts += PortGeometry.point(from.x, from.y, from.port)
        var port = from.port
        for (s in segments) {
            val entry = if (s.a == port) s.a else s.b
            val exit = if (entry == s.a) s.b else s.a
            for (i in 1..SAMPLES_PER_SEGMENT) pts += PortGeometry.curve(s.x, s.y, entry, exit, i / SAMPLES_PER_SEGMENT.toFloat())
            port = Port.facing(exit)
        }
        return pts
    }

    fun frameAt(plan: AnimationPlan, tMs: Long): AnimFrame {
        fun progress(kind: StepKind): Float {
            val s = plan.step(kind) ?: return 0f
            return min(1f, max(0f, (tMs - s.startMs).toFloat() / s.durationMs))
        }
        val tile = plan.placed?.let { TileAnim(it.x, it.y, it.tile, progress(StepKind.TILE_PLACED)) }
        val move = plan.step(StepKind.STONES_MOVE)
        val elim = progress(StepKind.ELIMINATION)
        val poses = HashMap<Int, StonePose>()

        fun fade(p: Pt, id: Int) = if (id in plan.eliminated) StonePose(p.x, p.y, 1f - 0.7f * elim, 1f - elim) else StonePose(p.x, p.y, 1f, 1f)
        for (s in plan.stones) {
            val elapsed = if (move == null) 0f else min(1f, max(0f, (tMs - move.startMs).toFloat() / s.moveMs))
            poses[s.playerId] = fade(s.pointAt(elapsed * s.length), s.playerId)
        }
        for ((id, p) in plan.stationary) poses[id] = fade(p, id)
        return AnimFrame(tile, poses, progress(StepKind.VICTORY), plan.winners, tMs >= plan.totalMs)
    }
}
