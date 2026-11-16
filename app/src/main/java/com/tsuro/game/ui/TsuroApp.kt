package com.tsuro.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tsuro.game.audio.GameAudio
import game.engine.GameMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

enum class Screen { MENU, MODES, SETUP, HOW_TO, SETTINGS, STATS, ABOUT, GAME, END }

@Composable
fun TsuroApp(audio: GameAudio, vm: GameViewModel = viewModel()) {
    var screen by rememberSaveable { mutableStateOf(Screen.MENU) }
    var setupMode by rememberSaveable { mutableStateOf(GameMode.CLASSIC) }
    val settings = vm.settings

    LaunchedEffect(Unit) { audio.startMusic() }
    LaunchedEffect(settings.theme) { TsuroColors.palette = Palettes.of(settings.theme) }
    // Settings are saved shortly after the last change.
    LaunchedEffect(Unit) { snapshotFlow { settings.toData() }.collectLatest { d -> delay(400); vm.persistSettings(d) } }
    LaunchedEffect(settings.music, settings.sfx) { audio.musicVolume = settings.music; audio.sfxVolume = settings.sfx }

    // If the process was recreated while on a game screen there is no game to show.
    val effective = if ((screen == Screen.GAME || screen == Screen.END) && vm.ui == null) Screen.MENU else screen

    BackHandler(enabled = effective != Screen.MENU && effective != Screen.GAME) {
        screen = when (effective) { Screen.SETUP -> Screen.MODES; Screen.END -> Screen.MENU; else -> Screen.MENU }
    }

    Box(Modifier.fillMaxSize().background(TsuroColors.Navy)) {
        Crossfade(targetState = effective, animationSpec = tween(260), label = "screens") { target ->
            when (target) {
                Screen.MENU -> MenuScreen(
                    canContinue = vm.hasSavedGame, onContinue = { if (vm.resume()) screen = Screen.GAME },
                    onPlay = { setupMode = GameMode.CLASSIC; screen = Screen.SETUP },
                    onModes = { screen = Screen.MODES }, onHowTo = { screen = Screen.HOW_TO },
                    onSettings = { screen = Screen.SETTINGS }, onStats = { screen = Screen.STATS }, onAbout = { screen = Screen.ABOUT }
                )
                Screen.MODES -> ModesScreen(onPick = { setupMode = it; screen = Screen.SETUP }, onBack = { screen = Screen.MENU })
                Screen.SETUP -> SetupScreen(setupMode, settings, vm, onStart = { screen = Screen.GAME }, onBack = { screen = Screen.MODES })
                Screen.HOW_TO -> HowToScreen { screen = Screen.MENU }
                Screen.SETTINGS -> SettingsScreen(settings, onRules = { screen = Screen.HOW_TO }, onResetStats = { vm.resetStats() }, onBack = { screen = Screen.MENU })
                Screen.STATS -> StatsScreen(vm.stats) { screen = Screen.MENU }
                Screen.ABOUT -> AboutScreen { screen = Screen.MENU }
                Screen.GAME -> GameScreen(vm, settings, audio, onMenu = { screen = Screen.MENU }, onFinished = { screen = Screen.END })
                Screen.END -> EndScreen(vm, onReplay = { vm.restart(); screen = Screen.GAME }, onMenu = { screen = Screen.MENU })
            }
        }
    }
}
