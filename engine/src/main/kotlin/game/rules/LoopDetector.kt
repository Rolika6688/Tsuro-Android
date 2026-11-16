package game.rules

import game.board.Board
import game.tiles.Port

/**
 * Loop rule (used by every mode): a loop is a closed chain of tile paths. A placement closes a loop when
 * following one of the new tile's paths forward comes back to that same path. Each distinct loop is reported once,
 * as the set of paths it consists of.
 */
object LoopDetector {
    fun loopsClosedBy(board: Board, x: Int, y: Int): List<Set<Segment>> {
        val tile = board.tileAt(x, y) ?: return emptyList()
        val found = LinkedHashSet<Set<Segment>>()
        for ((a, _) in tile.pairs()) walk(board, x, y, a)?.let { found += it }
        return found.toList()
    }

    private fun walk(board: Board, x0: Int, y0: Int, a0: Int): Set<Segment>? {
        var x = x0; var y = y0; var entry = a0
        val segs = LinkedHashSet<Segment>()
        repeat(4 * 36 + 1) {
            val t = board.tileAt(x, y) ?: return null
            val out = t.partner(entry)
            segs += Segment(x, y, minOf(entry, out), maxOf(entry, out))
            val side = Port.side(out)
            val nx = x + side.dx; val ny = y + side.dy
            if (!board.inBounds(nx, ny)) return null
            x = nx; y = ny; entry = Port.facing(out)
            if (x == x0 && y == y0 && entry == a0) return segs
        }
        return null
    }
}
