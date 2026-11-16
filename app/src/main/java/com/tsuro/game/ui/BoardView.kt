package com.tsuro.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import game.animation.AnimFrame
import game.board.BOARD_SIZE
import game.engine.GamePhase
import game.engine.GameState
import game.ui.GameUiState
import kotlin.math.max
import com.tsuro.game.ui.TsuroColors as C

/**
 * [display] is the state to draw tiles from: the state BEFORE the move while an animation runs, otherwise the current one.
 * [frame] (non-null only while animating) supplies the animated tile, stone poses and the victory pulse.
 */
@Composable
fun BoardView(ui: GameUiState, display: GameState, frame: AnimFrame?, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val total = size.minDimension
        val frameW = total * 0.035f
        val cell = (total - 2 * frameW) / BOARD_SIZE
        val origin = Offset(frameW, frameW)
        fun cellOrigin(x: Int, y: Int) = Offset(origin.x + x * cell, origin.y + y * cell)
        fun boardPoint(x: Float, y: Float) = Offset(origin.x + x * cell, origin.y + y * cell)

        drawRoundRect(C.WoodDark, Offset.Zero, Size(total, total), CornerRadius(total * 0.03f))
        drawRoundRect(C.BoardSurface, origin, Size(cell * BOARD_SIZE, cell * BOARD_SIZE), CornerRadius(total * 0.01f))
        for (i in 1 until BOARD_SIZE * 3) {
            val y = origin.y + i * (cell * BOARD_SIZE) / (BOARD_SIZE * 3)
            drawLine(C.Wood.copy(alpha = 0.35f), Offset(origin.x, y), Offset(origin.x + cell * BOARD_SIZE, y), cell * 0.012f)
        }
        for (i in 0..BOARD_SIZE) {
            drawLine(C.WoodDark.copy(alpha = 0.6f), Offset(origin.x + i * cell, origin.y), Offset(origin.x + i * cell, origin.y + cell * BOARD_SIZE), cell * 0.02f)
            drawLine(C.WoodDark.copy(alpha = 0.6f), Offset(origin.x, origin.y + i * cell), Offset(origin.x + cell * BOARD_SIZE, origin.y + i * cell), cell * 0.02f)
        }

        for (y in 0 until BOARD_SIZE) for (x in 0 until BOARD_SIZE) display.board.tileAt(x, y)?.let { drawTile(it, cellOrigin(x, y), cell) }

        if (frame == null) {
            ui.preview?.let { p ->
                val o = cellOrigin(p.x, p.y)
                drawTile(p.tile, o, cell, alpha = 0.7f)
                val col = if (p.legal && !p.eliminatesMover) C.Good else C.Bad
                drawRoundRect(col, o, Size(cell, cell), CornerRadius(cell * 0.06f), style = Stroke(cell * 0.06f))
            }
            for (pl in display.players) {
                val s = pl.stone ?: continue
                drawStone(portPoint(s.port, cellOrigin(s.x, s.y), cell), cell * 0.17f, playerColor(pl.color), ring = pl.id == display.current && display.phase == GamePhase.PLAYING)
            }
        } else {
            // the placed tile settles onto the board: drops in, grows and fades up
            frame.tile?.let { t ->
                val p = t.progress
                val sz = cell * (0.9f + 0.1f * p)
                val o = cellOrigin(t.x, t.y)
                val shifted = Offset(o.x + (cell - sz) / 2, o.y + (cell - sz) / 2 - (1f - p) * cell * 0.25f)
                drawTile(t.tile, shifted, sz, alpha = max(0.05f, p))
            }
            for ((id, pose) in frame.stones) {
                if (pose.alpha <= 0f) continue
                drawStone(boardPoint(pose.x, pose.y), cell * 0.17f * pose.scale, playerColor(display.players[id].color), ring = false, alpha = pose.alpha)
            }
            if (frame.victory > 0f) for (id in frame.winners) {
                val pose = frame.stones[id] ?: continue
                val pulse = (frame.victory * 3f) % 1f
                drawCircle(C.Gold.copy(alpha = 1f - pulse), cell * 0.17f * (1.4f + 1.8f * pulse), boardPoint(pose.x, pose.y), style = Stroke(cell * 0.05f))
            }
        }
    }
}
