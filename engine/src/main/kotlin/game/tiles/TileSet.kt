package game.tiles

/**
 * The standard tile set is generated, not hand-drawn: all 105 perfect matchings of 8 ports,
 * reduced modulo rotation. This yields exactly 35 distinct tiles (the same count as the real game).
 */
object TileSet {
    private fun matchings(): List<List<Int>> {
        val out = ArrayList<List<Int>>()
        fun gen(cur: IntArray) {
            val first = cur.indexOf(-1)
            if (first < 0) { out.add(cur.toList()); return }
            for (q in first + 1 until Port.COUNT) if (cur[q] == -1) {
                cur[first] = q; cur[q] = first
                gen(cur)
                cur[first] = -1; cur[q] = -1
            }
        }
        gen(IntArray(Port.COUNT) { -1 })
        return out
    }

    private fun key(links: List<Int>): Long = links.fold(0L) { a, b -> a * 8 + b }

    val allMatchingsCount: Int get() = matchings().size

    /** All 105 distinct ways a tile can lie on the board (every tile in every rotation). Used by the AI to model an unknown tile. */
    val allOrientations: List<Tile> by lazy { matchings().map { Tile(-1, it) } }

    val standard: List<Tile> by lazy {
        val canon = HashMap<Long, List<Int>>()
        for (m in matchings()) {
            val t = Tile(0, m)
            val best = (0..3).map { t.rotated(it).links }.minByOrNull { key(it) }!!
            canon.putIfAbsent(key(best), best)
        }
        canon.entries.sortedBy { it.key }.mapIndexed { i, e -> Tile(i, e.value) }
    }
}
