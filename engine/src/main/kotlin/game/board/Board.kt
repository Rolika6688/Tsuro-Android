package game.board

import game.tiles.Tile

const val BOARD_SIZE = 6

/** A stone sits on a tile-edge point, described as (cell it is facing, port of that cell). */
data class Position(val x: Int, val y: Int, val port: Int)

class Board private constructor(private val cells: List<Tile?>) {
    fun inBounds(x: Int, y: Int) = x in 0 until BOARD_SIZE && y in 0 until BOARD_SIZE
    fun tileAt(x: Int, y: Int): Tile? = if (inBounds(x, y)) cells[y * BOARD_SIZE + x] else null
    fun placedTiles(): List<Tile> = cells.filterNotNull()

    override fun equals(other: Any?) = other is Board && cells == other.cells
    override fun hashCode() = cells.hashCode()

    fun place(x: Int, y: Int, tile: Tile): Board {
        require(inBounds(x, y)) { "cell out of bounds" }
        require(tileAt(x, y) == null) { "cell already occupied" }
        val n = cells.toMutableList(); n[y * BOARD_SIZE + x] = tile
        return Board(n)
    }

    companion object {
        fun empty() = Board(List(BOARD_SIZE * BOARD_SIZE) { null })

        /** The 48 outer edge points in clockwise order, starting at the top-left. */
        fun perimeter(): List<Position> {
            val l = ArrayList<Position>()
            for (x in 0 until BOARD_SIZE) { l += Position(x, 0, 0); l += Position(x, 0, 1) }
            for (y in 0 until BOARD_SIZE) { l += Position(BOARD_SIZE - 1, y, 2); l += Position(BOARD_SIZE - 1, y, 3) }
            for (x in BOARD_SIZE - 1 downTo 0) { l += Position(x, BOARD_SIZE - 1, 4); l += Position(x, BOARD_SIZE - 1, 5) }
            for (y in BOARD_SIZE - 1 downTo 0) { l += Position(0, y, 6); l += Position(0, y, 7) }
            return l
        }
    }
}
