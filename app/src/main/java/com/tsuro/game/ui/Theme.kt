package com.tsuro.game.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import game.players.PlayerColor
import game.storage.AppTheme

class Palette(
    val navy: Color, val navyLight: Color, val gold: Color, val wood: Color, val woodDark: Color,
    val boardSurface: Color, val cream: Color, val ink: Color, val text: Color,
    val good: Color = Color(0xFF5FAF6B), val bad: Color = Color(0xFFD2554B)
)

object Palettes {
    private val midnight = Palette(Color(0xFF0B1426), Color(0xFF14233F), Color(0xFFC9A24B), Color(0xFF8B5A2B), Color(0xFF5E3A1A), Color(0xFFA9763F), Color(0xFFF1E3C0), Color(0xFF2B1B0E), Color(0xFFEDE6D3))
    private val walnut = Palette(Color(0xFF1E1410), Color(0xFF2E2019), Color(0xFFD08A4E), Color(0xFF6B4226), Color(0xFF3E2514), Color(0xFF8A5A35), Color(0xFFEFE2C6), Color(0xFF261811), Color(0xFFEADFCB))
    private val jade = Palette(Color(0xFF0C1F1B), Color(0xFF143029), Color(0xFFD4B35A), Color(0xFF9A7B4F), Color(0xFF5A4527), Color(0xFFB38F5A), Color(0xFFF0E6C8), Color(0xFF22190F), Color(0xFFE6EBDD))
    fun of(theme: AppTheme): Palette = when (theme) { AppTheme.MIDNIGHT -> midnight; AppTheme.WALNUT -> walnut; AppTheme.JADE -> jade }
}

/** Colour names stay the same everywhere; they read the active palette, so switching theme recolours the whole UI. */
object TsuroColors {
    var palette by mutableStateOf(Palettes.of(AppTheme.MIDNIGHT))
    val Navy get() = palette.navy
    val NavyLight get() = palette.navyLight
    val Gold get() = palette.gold
    val Wood get() = palette.wood
    val WoodDark get() = palette.woodDark
    val BoardSurface get() = palette.boardSurface
    val Cream get() = palette.cream
    val Ink get() = palette.ink
    val Text get() = palette.text
    val Good get() = palette.good
    val Bad get() = palette.bad
}

fun playerColor(c: PlayerColor): Color = when (c) {
    PlayerColor.CRIMSON -> Color(0xFFC62E3A)
    PlayerColor.AZURE -> Color(0xFF2F7FD1)
    PlayerColor.EMERALD -> Color(0xFF2E9E62)
    PlayerColor.GOLDEN -> Color(0xFFE0B13A)
    PlayerColor.VIOLET -> Color(0xFF7B4FC4)
    PlayerColor.TURQUOISE -> Color(0xFF2FB5B0)
    PlayerColor.ORANGE -> Color(0xFFE5782B)
    PlayerColor.WHITE -> Color(0xFFF4F1EA)
}

@Composable
fun TsuroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TsuroColors.Gold, onPrimary = TsuroColors.Ink,
            background = TsuroColors.Navy, onBackground = TsuroColors.Text,
            surface = TsuroColors.NavyLight, onSurface = TsuroColors.Text
        ),
        content = content
    )
}
