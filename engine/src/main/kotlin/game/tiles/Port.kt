package game.tiles

/** Four sides of a tile. dx/dy give the neighbouring cell (y grows downward). */
enum class Side(val dx: Int, val dy: Int) {
    NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0)
}

/**
 * Port model. Every side has TWO connection points (as in the real game), so a tile has 8 ports
 * and 4 paths. Ports are numbered clockwise starting at the north-left point:
 *
 *   0 = N-left   1 = N-right   2 = E-top     3 = E-bottom
 *   4 = S-right  5 = S-left    6 = W-bottom  7 = W-top
 *
 * Rotating a tile 90° clockwise maps port p -> (p + 2) mod 8.
 */
object Port {
    const val COUNT = 8
    private val FACING = intArrayOf(5, 4, 7, 6, 1, 0, 3, 2)

    fun side(port: Int): Side = Side.values()[port / 2]
    fun rotate(port: Int, quarterTurns: Int): Int = Math.floorMod(port + 2 * quarterTurns, COUNT)

    /** The port of the neighbouring tile that touches the same physical point as [port]. */
    fun facing(port: Int): Int = FACING[port]
}
