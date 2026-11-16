package com.tsuro.game.ui

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import game.ai.AiDifficulty
import game.engine.GameConfig
import game.engine.GameMode
import game.players.PlayerKind
import game.storage.AppTheme
import game.storage.Statistics
import game.ui.ranking

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), content = content)
}

@Composable
fun MenuScreen(
    canContinue: Boolean, onContinue: () -> Unit,
    onPlay: () -> Unit, onModes: () -> Unit, onHowTo: () -> Unit, onSettings: () -> Unit, onStats: () -> Unit, onAbout: () -> Unit
) {
    Row(Modifier.fillMaxSize().padding(32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TSURO", fontFamily = FontFamily.Serif, fontSize = 64.sp, fontWeight = FontWeight.Bold, color = TsuroColors.Gold, letterSpacing = 12.sp)
            Text("A game of paths", fontFamily = FontFamily.Serif, fontSize = 18.sp, color = TsuroColors.Text)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (canContinue) GoldButton("Continue game", onContinue, Modifier.fillMaxWidth())
            if (canContinue) OutlineButton("New game", onPlay, Modifier.fillMaxWidth()) else GoldButton("Play", onPlay, Modifier.fillMaxWidth())
            OutlineButton("Game Modes", onModes, Modifier.fillMaxWidth())
            OutlineButton("How to Play", onHowTo, Modifier.fillMaxWidth())
            OutlineButton("Settings", onSettings, Modifier.fillMaxWidth())
            OutlineButton("Statistics", onStats, Modifier.fillMaxWidth())
            OutlineButton("About", onAbout, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ModesScreen(onPick: (GameMode) -> Unit, onBack: () -> Unit) = Page {
    BackBar("Game Modes", onBack)
    for (m in GameMode.values()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 5.dp).background(TsuroColors.NavyLight, RoundedCornerShape(12.dp)).padding(16.dp)
        ) {
            Text(m.title + if (m.implemented) "" else "  ·  coming in a later phase", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TsuroColors.Gold)
            Text(m.description, color = TsuroColors.Text, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            GoldButton("Select", { onPick(m) }, enabled = m.implemented)
        }
    }
}

@Composable
fun SetupScreen(mode: GameMode, settings: SettingsState, vm: GameViewModel, onStart: () -> Unit, onBack: () -> Unit) {
    var players by remember { mutableIntStateOf(settings.playerCount) }
    var humans by remember { mutableIntStateOf(1) }
    var difficulty by remember { mutableStateOf(settings.difficulty) }
    val solo = mode == GameMode.SOLO
    val humanCount = if (solo) 1 else minOf(humans, players)
    Page {
        BackBar(mode.title, onBack)
        Text("Players", color = TsuroColors.Text)
        ChoiceChips((2..8).map { it.toString() }, players - 2, { players = it + 2 })
        Spacer(Modifier.height(14.dp))
        if (!solo) {
            Text("Human players (the rest are AI)", color = TsuroColors.Text)
            ChoiceChips((1..players).map { it.toString() }, humanCount - 1, { humans = it + 1 })
            Spacer(Modifier.height(14.dp))
        }
        Text("AI difficulty", color = TsuroColors.Text)
        ChoiceChips(AiDifficulty.values().map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, difficulty.ordinal, { difficulty = AiDifficulty.values()[it] })
        Spacer(Modifier.height(20.dp))
        GoldButton("Start game", {
            vm.start(mode, GameConfig(players, humanCount, System.nanoTime(), mode), difficulty)
            onStart()
        })
    }
}

@Composable
fun HowToScreen(onBack: () -> Unit) = Page {
    BackBar("How to Play", onBack)
    val rules = listOf(
        "Goal" to "Keep your stone on the board longer than everyone else. The last stone standing wins.",
        "Your turn" to "Pick one of your three tiles, rotate it with the Rotate button, check the preview on the board, then confirm.",
        "Movement" to "The tile is placed in front of your stone. Every stone touching that tile follows the path it now sits on, and keeps going across any tiles already laid until it reaches an empty space.",
        "Elimination" to "A stone is out when it leaves the board, when it meets another stone (on the same path or at the same spot), or when its path closes into a loop so it can never reach an end.",
        "Safe moves" to "You may not choose a move that eliminates you if a safe move exists. If every move is fatal, you must still play one.",
        "Drawing" to "After placing you draw back up to three tiles. Tiles of eliminated players go back to the bottom of the deck.",
        "Simultaneous endings" to "If the last remaining stones are eliminated on the same move, they share the win.",
        "Loop Battle" to "A loop is a closed ring of paths. Closing one scores 1 point; a stone trapped inside a loop you closed scores 3 more. Survivors get 2 bonus points when the game ends. Highest score wins.",
        "Longest Path" to "Every stone counts the paths it travels. When the game ends the longest journey wins, even if that stone was eliminated. Ties are shared."
    )
    for ((h, t) in rules) {
        Text(h, color = TsuroColors.Gold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(t, color = TsuroColors.Text, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
    }
}

@Composable
fun SettingsScreen(s: SettingsState, onRules: () -> Unit, onResetStats: () -> Unit, onBack: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    Page {
        BackBar("Settings", onBack)
        Text("Music volume", color = TsuroColors.Text); Slider(s.music, { s.music = it })
        Text("Sound effects volume", color = TsuroColors.Text); Slider(s.sfx, { s.sfx = it })
        Text("Animation speed", color = TsuroColors.Text); Slider(s.animationSpeed, { s.animationSpeed = it }, valueRange = 0.5f..2f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Haptic feedback", color = TsuroColors.Text, modifier = Modifier.weight(1f)); Switch(s.haptics, { s.haptics = it })
        }
        Spacer(Modifier.height(8.dp))
        Text("Default AI difficulty", color = TsuroColors.Text)
        ChoiceChips(AiDifficulty.values().map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, s.difficulty.ordinal, { s.difficulty = AiDifficulty.values()[it] })
        Spacer(Modifier.height(8.dp))
        Text("Default player count", color = TsuroColors.Text)
        ChoiceChips((2..8).map { it.toString() }, s.playerCount - 2, { s.playerCount = it + 2 })
        Spacer(Modifier.height(8.dp))
        Text("Theme", color = TsuroColors.Text)
        ChoiceChips(AppTheme.values().map { it.title }, s.theme.ordinal, { s.theme = AppTheme.values()[it] })
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("Rules explanation", onRules)
            OutlineButton("Reset statistics", { confirmReset = true })
        }
        Spacer(Modifier.height(12.dp))
        Text("Settings are saved automatically.", color = TsuroColors.Text.copy(alpha = 0.6f), fontSize = 12.sp)
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset statistics?") },
            text = { Text("All recorded games and totals will be deleted. This cannot be undone.") },
            confirmButton = { GoldButton("Reset", { confirmReset = false; onResetStats() }) },
            dismissButton = { OutlineButton("Cancel", { confirmReset = false }) },
            containerColor = TsuroColors.NavyLight
        )
    }
}

@Composable
fun StatsScreen(stats: Statistics, onBack: () -> Unit) = Page {
    BackBar("Statistics", onBack)
    if (stats.gamesPlayed == 0) {
        Text("No games recorded yet. Finish a game and it will appear here.", color = TsuroColors.Text)
        return@Page
    }
    val avg = stats.totalTurns / stats.gamesPlayed
    for ((label, value) in listOf(
        "Games played" to "${stats.gamesPlayed}", "Wins (human)" to "${stats.humanWins}", "Losses (human)" to "${stats.humanLosses}",
        "Average game length" to "$avg turns", "Longest game" to "${stats.longestGameTurns} turns", "Longest path ever" to "${stats.bestLongestPath}"
    )) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(label, color = TsuroColors.Text, modifier = Modifier.weight(1f)); Text(value, color = TsuroColors.Gold, fontWeight = FontWeight.SemiBold)
        }
    }
    Spacer(Modifier.height(14.dp))
    Text("Recent games", color = TsuroColors.Gold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    val fmt = remember { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT) }
    for (r in stats.recent.take(15)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(TsuroColors.NavyLight, RoundedCornerShape(8.dp)).padding(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("${r.mode.title} · ${r.players} players · ${r.turns} turns", color = TsuroColors.Text, fontSize = 14.sp)
                Text(fmt.format(java.util.Date(r.endedAtMs)), color = TsuroColors.Text.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            Text(
                if (r.humans == 0) "AI only" else if (r.humanWon) "Won" else "Lost",
                color = if (r.humans > 0 && r.humanWon) TsuroColors.Good else if (r.humans > 0) TsuroColors.Bad else TsuroColors.Text
            )
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) = Page {
    BackBar("About", onBack)
    Text("Tsuro-inspired path game with original artwork and code.", color = TsuroColors.Text)
    Text("Version 1.0.0 (source release, unbuilt)", color = TsuroColors.Text.copy(alpha = 0.6f), fontSize = 12.sp)
}

@Composable
fun EndScreen(vm: GameViewModel, onReplay: () -> Unit, onMenu: () -> Unit) {
    val s = vm.ui?.state ?: return
    val rows = ranking(s)
    val winners = s.winners.map { s.players[it].name }
    val longest = s.players.maxByOrNull { it.distance }
    Page {
        ScreenTitle(if (winners.size == 1) "${winners[0]} wins!" else "Shared victory: ${winners.joinToString(" & ")}")
        Text(s.mode.title, color = TsuroColors.Text.copy(alpha = 0.7f), fontSize = 13.sp)
        Text("Game lasted ${s.turn} turns" + (longest?.let { " · longest path: ${it.distance} (${it.name})" } ?: ""), color = TsuroColors.Text, modifier = Modifier.padding(vertical = 8.dp))
        for (r in rows) {
            val p = r.player
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(TsuroColors.NavyLight, RoundedCornerShape(8.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("#${r.rank}", color = TsuroColors.Gold, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                Box(Modifier.size(16.dp).background(playerColor(p.color), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(p.name + if (p.kind == PlayerKind.AI) " (AI)" else "", color = TsuroColors.Text, modifier = Modifier.weight(1f))
                Text(
                    (if (p.alive) "survived" else "out on turn ${p.eliminatedOnTurn}") + " · path ${p.distance}" + (if (s.mode == game.engine.GameMode.LOOP_BATTLE) " · ${p.score} pts" else ""),
                    color = TsuroColors.Text.copy(alpha = 0.8f), fontSize = 13.sp
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GoldButton("Replay", onReplay)
            OutlineButton("Main menu", onMenu)
        }
    }
}
