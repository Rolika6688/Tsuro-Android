package game.rules

import game.board.Board
import game.board.Position
import game.tiles.Port

/** One traversed tile path, normalised so a < b (direction-independent). */
data class Segment(val x: Int, val y: Int, val a: Int, val b: Int)

/** Result of following a stone: the segments walked, where it stopped, or whether it left the board. */
/** [looped]: the path closes on itself and returns to the stone's own starting point, so the stone can never reach an end. */
data class Trace(val segments: List<Segment>, val end: Position?, val exited: Boolean, val looped: Boolean = false)

object PathResolver {
    fun trace(board: Board, start: Position): Trace {
        val segs = ArrayList<Segment>()
        var pos = start
        while (true) {
            val tile = board.tileAt(pos.x, pos.y) ?: return Trace(segs, pos, false)
            val out = tile.partner(pos.port)
            segs += Segment(pos.x, pos.y, minOf(pos.port, out), maxOf(pos.port, out))
            val side = Port.side(out)
            val nx = pos.x + side.dx
            val ny = pos.y + side.dy
            if (!board.inBounds(nx, ny)) return Trace(segs, null, true)
            pos = Position(nx, ny, Port.facing(out))
            if (pos == start) return Trace(segs, null, false, looped = true)
            check(segs.size <= 4 * 36) { "path loop detected" }
        }
    }
}
