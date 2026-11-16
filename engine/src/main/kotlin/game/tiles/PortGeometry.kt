package game.tiles

/** A point in board coordinates measured in cell units: (0,0) is the top-left corner, (6,6) the bottom-right. */
data class Pt(val x: Float, val y: Float)

/** Single source of truth for where ports sit on a tile and how the path between two ports is drawn. */
object PortGeometry {
    private val FX = floatArrayOf(1f / 3f, 2f / 3f, 1f, 1f, 2f / 3f, 1f / 3f, 0f, 0f)
    private val FY = floatArrayOf(0f, 0f, 1f / 3f, 2f / 3f, 1f, 1f, 2f / 3f, 1f / 3f)
    const val CONTROL_PULL = 0.55f

    fun fx(port: Int) = FX[port]
    fun fy(port: Int) = FY[port]

    fun point(cellX: Int, cellY: Int, port: Int) = Pt(cellX + FX[port], cellY + FY[port])

    /** Cubic Bezier between two ports of a cell, t in 0..1; control points are pulled toward the cell centre. */
    fun curve(cellX: Int, cellY: Int, a: Int, b: Int, t: Float): Pt {
        val pa = point(cellX, cellY, a); val pb = point(cellX, cellY, b)
        val cx = cellX + 0.5f; val cy = cellY + 0.5f
        val c1x = pa.x + (cx - pa.x) * CONTROL_PULL; val c1y = pa.y + (cy - pa.y) * CONTROL_PULL
        val c2x = pb.x + (cx - pb.x) * CONTROL_PULL; val c2y = pb.y + (cy - pb.y) * CONTROL_PULL
        val u = 1f - t
        val w0 = u * u * u; val w1 = 3 * u * u * t; val w2 = 3 * u * t * t; val w3 = t * t * t
        return Pt(w0 * pa.x + w1 * c1x + w2 * c2x + w3 * pb.x, w0 * pa.y + w1 * c1y + w2 * c2y + w3 * pb.y)
    }
}
