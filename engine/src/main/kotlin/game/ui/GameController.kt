package game.ui

import game.ai.AiPlayer
import game.engine.*
import game.players.PlayerKind
import game.players.PlayerState
import game.tiles.Tile

/** What the board draws as a ghost tile for the currently selected tile and rotation. */
data class Preview(val tile: Tile, val x: Int, val y: Int, val legal: Boolean, val eliminatesMover: Boolean)

/** Immutable snapshot the UI renders. */
data class GameUiState(
    val state: GameState,
    val selected: Int?,
    val rotation: Int,
    val preview: Preview?,
    val humanTurn: Boolean,
    val canConfirm: Boolean,
    val lastEvents: List<GameEvent>
)

data class RankEntry(val rank: Int, val player: PlayerState)

/** Winners first, then higher score (special modes), then later eliminations; equal keys share a rank. */
fun ranking(state: GameState): List<RankEntry> {
    fun key(p: PlayerState): Triple<Int, Int, Int> =
        Triple(if (p.id in state.winners) 1 else 0, p.score, p.eliminatedOnTurn ?: Int.MAX_VALUE)
    val cmp = compareByDescending<PlayerState> { key(it).first }.thenByDescending { key(it).second }.thenByDescending { key(it).third }.thenBy { it.id }
    val sorted = state.players.sortedWith(cmp)
    return sorted.mapIndexed { i, p -> RankEntry(sorted.indexOfFirst { key(it) == key(p) } + 1, p) }
}

/** UI-independent turn flow: select tile -> rotate -> preview -> confirm. Contains no Android code. */
class GameController(initial: GameState, private val ai: AiPlayer) {
    var state: GameState = initial
        private set
    private var selected: Int? = null
    private var rotation = 0
    private var lastEvents: List<GameEvent> = emptyList()

    private val current: PlayerState get() = state.players[state.current]
    val isHumanTurn: Boolean get() = state.phase == GamePhase.PLAYING && current.kind == PlayerKind.HUMAN
    val isAiTurn: Boolean get() = state.phase == GamePhase.PLAYING && current.kind == PlayerKind.AI

    fun select(handIndex: Int) {
        if (!isHumanTurn || handIndex !in current.hand.indices) return
        if (selected != handIndex) { selected = handIndex; rotation = 0 }
    }

    fun rotate() {
        if (isHumanTurn && selected != null) rotation = (rotation + 1) % 4
    }

    private fun currentMove(): Move? = selected?.let { Move(it, rotation) }

    fun preview(): Preview? {
        if (!isHumanTurn) return null
        val m = currentMove() ?: return null
        val stone = current.stone ?: return null
        val legal = m in GameEngine.legalMoves(state)
        val dies = !GameEngine.resolve(state, m).state.players[state.current].alive
        return Preview(current.hand[m.handIndex].rotated(m.rotation), stone.x, stone.y, legal, dies)
    }

    fun confirm(): MoveResult? {
        if (!isHumanTurn) return null
        val m = currentMove() ?: return null
        if (m !in GameEngine.legalMoves(state)) return null
        return commit(GameEngine.applyMove(state, m))
    }

    fun runAiTurn(): MoveResult? {
        if (!isAiTurn) return null
        return commit(GameEngine.applyMove(state, ai.chooseMove(state)))
    }

    private fun commit(r: MoveResult): MoveResult {
        state = r.state; lastEvents = r.events; selected = null; rotation = 0
        return r
    }

    fun snapshot(): GameUiState {
        val p = preview()
        return GameUiState(state, selected, rotation, p, isHumanTurn, p?.legal == true, lastEvents)
    }
}
