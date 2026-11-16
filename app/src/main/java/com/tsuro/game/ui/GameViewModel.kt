package com.tsuro.game.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tsuro.game.storage.AppStorage
import game.ai.AiDifficulty
import game.ai.AiFactory
import game.animation.AnimationPlan
import game.animation.AnimationPlanner
import game.engine.GameConfig
import game.engine.GameEngine
import game.engine.GameMode
import game.engine.GamePhase
import game.engine.GameState
import game.engine.MoveResult
import game.players.PlayerKind
import game.storage.AppTheme
import game.storage.SavedGame
import game.storage.SettingsData
import game.storage.Statistics
import game.storage.StatisticsManager
import game.ui.GameController
import game.ui.GameUiState
import kotlinx.coroutines.launch

/** A move that has been applied to the rules but is still being shown. [before] is the board the animation plays on. */
class ActiveAnimation(val plan: AnimationPlan, val before: GameState)

/** Settings held as Compose state; converted to/from the tested [SettingsData] for storage. */
class SettingsState {
    var music by mutableFloatStateOf(0.5f)
    var sfx by mutableFloatStateOf(0.7f)
    var animationSpeed by mutableFloatStateOf(1f)
    var haptics by mutableStateOf(true)
    var difficulty by mutableStateOf(AiDifficulty.NORMAL)
    var playerCount by mutableIntStateOf(4)
    var theme by mutableStateOf(AppTheme.MIDNIGHT)

    fun toData() = SettingsData(music, sfx, animationSpeed, haptics, difficulty, playerCount, theme)
    fun apply(d: SettingsData) { music = d.music; sfx = d.sfx; animationSpeed = d.animationSpeed; haptics = d.haptics; difficulty = d.difficulty; playerCount = d.playerCount; theme = d.theme }
}

/**
 * Holds the running game, settings and statistics. All rules live in :engine; all storage formats are validated there.
 * The game is autosaved after every move and removed when it finishes (then it is added to the statistics).
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = AppStorage(app)
    private var controller: GameController? = null
    private var pendingSave: SavedGame? = null

    val settings = SettingsState()
    var settingsLoaded = false
        private set
    var stats by mutableStateOf(Statistics.EMPTY)
        private set
    var hasSavedGame by mutableStateOf(false)
        private set
    var ui by mutableStateOf<GameUiState?>(null)
        private set
    var anim by mutableStateOf<ActiveAnimation?>(null)
        private set
    /** Cumulative quarter turns of the selected tile, only used to animate the rotation. */
    var rotationTurns by mutableIntStateOf(0)
        private set
    var lastConfig: GameConfig? = null
        private set
    var lastDifficulty: AiDifficulty = AiDifficulty.NORMAL
        private set
    var lastMode: GameMode = GameMode.CLASSIC
        private set

    init {
        viewModelScope.launch {
            settings.apply(storage.settings())
            stats = storage.stats()
            pendingSave = storage.savedGame()
            hasSavedGame = pendingSave != null
            settingsLoaded = true
        }
    }

    fun persistSettings(d: SettingsData) { if (settingsLoaded) viewModelScope.launch { storage.saveSettings(d) } }
    fun resetStats() { stats = Statistics.EMPTY; viewModelScope.launch { storage.resetStats() } }

    fun start(mode: GameMode, config: GameConfig, difficulty: AiDifficulty) {
        lastMode = mode; lastConfig = config; lastDifficulty = difficulty
        controller = GameController(GameEngine.newGame(config), AiFactory.create(difficulty, config.seed))
        anim = null; rotationTurns = 0
        publish(); autosave()
    }

    fun restart() {
        val c = lastConfig ?: return
        start(lastMode, c.copy(seed = System.nanoTime()), lastDifficulty)
    }

    /** Continue a game in progress: the live one if it is still in memory, otherwise the one restored from storage. */
    fun resume(): Boolean {
        if (controller != null && ui?.state?.phase == GamePhase.PLAYING) return true
        val g = pendingSave ?: return false
        controller = GameController(g.state, AiFactory.create(g.difficulty, g.seed))
        lastMode = g.state.mode; lastDifficulty = g.difficulty
        lastConfig = GameConfig(g.state.players.size, g.state.players.count { it.kind == PlayerKind.HUMAN }, g.seed)
        anim = null; rotationTurns = 0
        publish()
        return true
    }

    fun select(i: Int) { if (anim != null) return; controller?.select(i); rotationTurns = 0; publish() }
    fun rotate() { if (anim != null || ui?.selected == null) return; controller?.rotate(); rotationTurns++; publish() }

    fun confirm(speed: Float) = act(speed) { it.confirm() }
    fun aiMove(speed: Float) = act(speed) { it.runAiTurn() }

    fun finishAnimation() { anim = null }

    private fun act(speed: Float, move: (GameController) -> MoveResult?) {
        val c = controller ?: return
        if (anim != null) return
        val before = c.state
        val result = move(c) ?: return
        anim = ActiveAnimation(AnimationPlanner.plan(before, result.events, speed), before)
        rotationTurns = 0
        publish()
        if (result.state.phase == GamePhase.FINISHED) finishGame(result.state) else autosave()
    }

    private fun autosave() {
        val c = controller ?: return
        val g = SavedGame(lastDifficulty, lastConfig?.seed ?: 0L, c.state)
        pendingSave = g; hasSavedGame = true
        viewModelScope.launch { storage.saveGame(g) }
    }

    private fun finishGame(state: GameState) {
        stats = StatisticsManager.record(stats, StatisticsManager.recordOf(state, lastMode, lastDifficulty, System.currentTimeMillis()))
        pendingSave = null; hasSavedGame = false
        val s = stats
        viewModelScope.launch { storage.saveStats(s); storage.clearGame() }
    }

    private fun publish() { ui = controller?.snapshot() }
}
