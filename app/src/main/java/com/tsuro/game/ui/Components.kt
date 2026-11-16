package com.tsuro.game.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.tiles.Tile

/** Buttons shrink slightly while pressed. */
@Composable
private fun pressScale(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(90), label = "press")
    return scale
}

@Composable
fun GoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source)
    Button(
        onClick = onClick, enabled = enabled, interactionSource = source,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TsuroColors.Gold, contentColor = TsuroColors.Ink,
            disabledContainerColor = TsuroColors.NavyLight, disabledContentColor = TsuroColors.Text.copy(alpha = 0.4f)
        )
    ) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source)
    OutlinedButton(
        onClick = onClick, enabled = enabled, interactionSource = source,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (enabled) TsuroColors.Gold else TsuroColors.Text.copy(alpha = 0.25f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TsuroColors.Gold)
    ) { Text(text, fontSize = 16.sp) }
}

@Composable
fun ScreenTitle(text: String) {
    Text(text, fontFamily = FontFamily.Serif, fontSize = 28.sp, color = TsuroColors.Gold, fontWeight = FontWeight.Bold)
}

@Composable
fun BackBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlineButton("Back", onBack)
        Spacer(Modifier.width(16.dp))
        ScreenTitle(title)
    }
}

@Composable
fun ChoiceChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit, enabled: List<Boolean>? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            FilterChip(
                selected = i == selected, onClick = { onSelect(i) }, enabled = enabled?.get(i) ?: true,
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TsuroColors.Gold, selectedLabelColor = TsuroColors.Ink)
            )
        }
    }
}

/**
 * A hand tile. [tile] is the UNROTATED tile; [turns] is the cumulative number of quarter turns applied to it.
 * The card animates the turn (clockwise, matching Port rotation); non-selected cards never animate.
 */
@Composable
fun TileCard(tile: Tile, selected: Boolean, turns: Int, size: Dp, onClick: () -> Unit) {
    val angle by animateFloatAsState(
        targetValue = if (selected) turns * 90f else 0f,
        animationSpec = if (selected) tween(220) else snap(),
        label = "tileTurn"
    )
    Box(
        Modifier.size(size)
            .background(if (selected) TsuroColors.Gold.copy(alpha = 0.25f) else TsuroColors.NavyLight, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = angle }) { drawTile(tile, Offset.Zero, this.size.minDimension) }
    }
}
