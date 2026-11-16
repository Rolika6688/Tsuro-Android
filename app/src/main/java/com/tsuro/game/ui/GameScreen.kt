package com.tsuro.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tsuro.game.audio.GameAudio
import com.tsuro.game.audio.Sfx
import game.animation.AnimFrame
import game.animation.AnimationPlanner
import game.animation.StepKind
import game.engine.GamePhase
import game.players.PlayerKind
import game.ui.GameUiState
import kotlinx.coroutines.delay

@Composable
fun GameScreen(vm: GameViewModel, settings: SettingsState, audio: GameAudio, onMenu: () -> Unit, onFinished: () -> Unit) {
    val ui = vm.ui ?: return
    val anim = vm.anim
    val busy = anim != null
    val haptic = LocalHapticFeedback.current
    var paused by remember { mutableStateOf(false) }
    var frame by remember { mutableStateOf<AnimFrame?>(null) }
    BackHandler { paused = true }

    // Plays the animation of the last move frame by frame, firing each step's sound/haptic as it starts.
    LaunchedEffect(anim) {
        if (anim == null) { frame = null; return@LaunchedEffect }
        val plan = anim.plan
        var fired = 0
        val startNs = withFrameNanos { it }
        while (true) {
            val t = (withFrameNanos { it } - startNs) / 1_000_000L
            while (fired < plan.steps.size && t >= plan.steps[fired].startMs) {
                when (plan.steps[fired].kind) {
                    StepKind.TILE_PLACED -> audio.play(Sfx.PLACE)
                    StepKind.STONES_MOVE -> audio.play(Sfx.MOVE)
                    StepKind.ELIMINATION -> { audio.play(Sfx.ELIMINATE); if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                    StepKind.VICTORY -> audio.play(Sfx.VICTORY)
                }
                fired++
            }
            frame = AnimationPlanner.frameAt(plan, t)
            if (t >= plan.totalMs) break
        }
        vm.finishAnimation()
    }

    // AI plays on its own after a short pause, never while an animation is running.
    LaunchedEffect(ui.state.turn, paused, busy) {
        if (!paused && !busy && ui.state.phase == GamePhase.PLAYING && !ui.humanTurn) { delay(500); vm.aiMove(settings.animationSpeed) }
    }
    LaunchedEffect(ui.state.phase, busy) {
        if (ui.state.phase == GamePhase.FINISHED && !busy) { delay(900); onFinished() }
    }

    val display = anim?.before ?: ui.state
    val shownFrame = if (busy) frame else null
    val onConfirm = {
        if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.confirm(settings.animationSpeed)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(TsuroColors.Navy).padding(12.dp)) {
        if (maxWidth >= maxHeight) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BoardView(ui, display, shownFrame, Modifier.fillMaxHeight())
                SidePanel(ui, vm, busy, onConfirm, { paused = true }, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BoardView(ui, display, shownFrame, Modifier.fillMaxWidth())
                SidePanel(ui, vm, busy, onConfirm, { paused = true }, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }

    if (paused) {
        AlertDialog(
            onDismissRequest = { paused = false },
            title = { Text("Paused") },
            text = { Text("The game is on hold.") },
            confirmButton = { GoldButton("Resume", { paused = false }) },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineButton("Restart", { paused = false; vm.restart() })
                    OutlineButton("Main menu", { paused = false; onMenu() })
                }
            },
            containerColor = TsuroColors.NavyLight
        )
    }
}

@Composable
private fun SidePanel(ui: GameUiState, vm: GameViewModel, busy: Boolean, onConfirm: () -> Unit, onPause: () -> Unit, modifier: Modifier) {
    val s = ui.state
    val cur = s.players[s.current]
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        s.phase == GamePhase.FINISHED -> "Game over"
                        busy -> "Moving…"
                        ui.humanTurn -> "${cur.name}: choose a tile"
                        else -> "${cur.name} (AI) is thinking…"
                    },
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TsuroColors.Gold
                )
                Text("Turn ${s.turn + 1} · ${s.alivePlayers.size} of ${s.players.size} players remain", fontSize = 13.sp, color = TsuroColors.Text)
            }
            OutlineButton("Pause", onPause)
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (p in s.players) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (p.id == s.current && s.phase == GamePhase.PLAYING) TsuroColors.NavyLight else TsuroColors.Navy, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(16.dp).background(playerColor(p.color), CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        p.name + (if (p.kind == PlayerKind.AI) " · AI" else " · Human") +
                            when (s.mode) { game.engine.GameMode.LOOP_BATTLE -> " · ${p.score} pts"; game.engine.GameMode.LONGEST_PATH -> " · path ${p.distance}"; else -> "" },
                        color = TsuroColors.Text, fontSize = 14.sp, modifier = Modifier.weight(1f)
                    )
                    Text(if (p.alive) "in play" else "eliminated", color = if (p.alive) TsuroColors.Good else TsuroColors.Bad, fontSize = 12.sp)
                }
            }
        }

        if (ui.humanTurn) {
            Text("Your tiles", fontSize = 13.sp, color = TsuroColors.Text)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                cur.hand.forEachIndexed { i, t -> TileCard(t, ui.selected == i, vm.rotationTurns, 84.dp) { vm.select(i) } }
            }
            ui.preview?.let {
                Text(
                    when {
                        it.legal && !it.eliminatesMover -> "Preview: safe placement"
                        it.eliminatesMover && !it.legal -> "Not allowed: this move eliminates you while a safe move exists"
                        else -> "Warning: every move eliminates you"
                    },
                    fontSize = 13.sp, color = if (it.legal && !it.eliminatesMover) TsuroColors.Good else TsuroColors.Bad
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton("Rotate", { vm.rotate() }, enabled = ui.selected != null && !busy)
                GoldButton("Confirm move", onConfirm, enabled = ui.canConfirm && !busy)
            }
        }
    }
}
