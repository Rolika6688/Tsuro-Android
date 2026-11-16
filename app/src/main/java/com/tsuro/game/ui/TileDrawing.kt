package com.tsuro.game.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import game.tiles.PortGeometry
import game.tiles.Tile

fun portPoint(port: Int, origin: Offset, size: Float): Offset =
    Offset(origin.x + PortGeometry.fx(port) * size, origin.y + PortGeometry.fy(port) * size)

/** Draws the tile exactly as its data says: one curve per (a, b) pair. Same geometry the animation follows. */
fun DrawScope.drawTile(tile: Tile, origin: Offset, size: Float, alpha: Float = 1f) {
    val radius = CornerRadius(size * 0.06f, size * 0.06f)
    drawRoundRect(TsuroColors.Cream.copy(alpha = alpha), origin, Size(size, size), radius)
    drawRoundRect(TsuroColors.WoodDark.copy(alpha = alpha), origin, Size(size, size), radius, style = Stroke(size * 0.025f))
    val center = Offset(origin.x + size / 2, origin.y + size / 2)
    val pull = PortGeometry.CONTROL_PULL
    for ((a, b) in tile.pairs()) {
        val pa = portPoint(a, origin, size)
        val pb = portPoint(b, origin, size)
        val c1 = Offset(pa.x + (center.x - pa.x) * pull, pa.y + (center.y - pa.y) * pull)
        val c2 = Offset(pb.x + (center.x - pb.x) * pull, pb.y + (center.y - pb.y) * pull)
        val path = Path().apply { moveTo(pa.x, pa.y); cubicTo(c1.x, c1.y, c2.x, c2.y, pb.x, pb.y) }
        drawPath(path, TsuroColors.Ink.copy(alpha = alpha), style = Stroke(size * 0.075f, cap = StrokeCap.Round))
        drawPath(path, TsuroColors.Gold.copy(alpha = alpha), style = Stroke(size * 0.04f, cap = StrokeCap.Round))
    }
}

fun DrawScope.drawStone(center: Offset, radius: Float, color: Color, ring: Boolean, alpha: Float = 1f) {
    drawCircle(Color.Black.copy(alpha = 0.35f * alpha), radius, Offset(center.x + radius * 0.12f, center.y + radius * 0.18f))
    drawCircle(color.copy(alpha = alpha), radius, center)
    drawCircle(Color.White.copy(alpha = 0.35f * alpha), radius * 0.35f, Offset(center.x - radius * 0.3f, center.y - radius * 0.3f))
    drawCircle(TsuroColors.Ink.copy(alpha = 0.6f * alpha), radius, center, style = Stroke(radius * 0.12f))
    if (ring) drawCircle(TsuroColors.Gold.copy(alpha = alpha), radius * 1.35f, center, style = Stroke(radius * 0.18f))
}
