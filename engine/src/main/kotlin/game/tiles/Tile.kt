package game.tiles

/** links[p] is the port connected to port p. Must be a perfect matching without fixed points. */
data class Tile(val id: Int, val links: List<Int>) {
    init {
        require(links.size == Port.COUNT) { "tile needs ${Port.COUNT} ports" }
        for (p in 0 until Port.COUNT) {
            val q = links[p]
            require(q in 0 until Port.COUNT && q != p && links[q] == p) { "invalid pairing at port $p" }
        }
    }

    fun partner(port: Int): Int = links[port]

    fun rotated(quarterTurns: Int): Tile {
        val n = IntArray(Port.COUNT)
        for (p in 0 until Port.COUNT) n[Port.rotate(p, quarterTurns)] = Port.rotate(links[p], quarterTurns)
        return Tile(id, n.toList())
    }

    fun pairs(): List<Pair<Int, Int>> = (0 until Port.COUNT).filter { it < links[it] }.map { it to links[it] }
}
