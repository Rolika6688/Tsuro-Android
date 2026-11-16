package game.engine

import game.board.BOARD_SIZE
import game.board.Board
import game.board.Position
import game.players.PlayerColor
import game.players.PlayerKind
import game.players.PlayerState
import game.rules.CollisionResolver
import game.rules.EliminationReason
import game.rules.LoopDetector
import game.rules.PathResolver
import game.rules.Segment
import game.rules.Trace
import game.tiles.Tile
import game.tiles.TileSet
import kotlin.random.Random

enum class GamePhase { PLAYING, FINISHED }

/** Scoring knobs of the special modes (documented in MODES.md). Defaults are the shipped interpretation. */
data class ModeRules(val loopPoints: Int = 1, val trapPoints: Int = 3, val survivorBonus: Int = 2)

data class GameConfig(val playerCount: Int = 4, val humanCount: Int = 1, val seed: Long = 1L, val mode: GameMode = GameMode.CLASSIC, val rules: ModeRules = ModeRules())

/** handIndex: which tile of the current player's hand; rotation: quarter turns clockwise (0..3). */
data class Move(val handIndex: Int, val rotation: Int)

data class GameState(
    val board: Board,
    val players: List<PlayerState>,
    val deck: List<Tile>,
    val current: Int,
    val turn: Int,
    val phase: GamePhase,
    val winners: List<Int>,
    val mode: GameMode = GameMode.CLASSIC,
    val rules: ModeRules = ModeRules()
) {
    val alivePlayers: List<PlayerState> get() = players.filter { it.alive }
}

sealed interface GameEvent {
    data class TilePlaced(val player: Int, val x: Int, val y: Int, val tile: Tile) : GameEvent
    data class StoneMoved(val player: Int, val from: Position, val to: Position?, val segments: List<Segment>) : GameEvent
    data class PlayerEliminated(val player: Int, val reason: EliminationReason) : GameEvent
    data class LoopClosed(val player: Int, val length: Int) : GameEvent
    data class GameOver(val winners: List<Int>) : GameEvent
}

data class MoveResult(val state: GameState, val events: List<GameEvent>)

class IllegalMoveException(message: String) : RuntimeException(message)

object GameEngine {
    const val HAND_SIZE = 3

    fun newGame(config: GameConfig): GameState {
        require(config.playerCount in 2..8) { "players must be 2..8" }
        require(config.humanCount in 0..config.playerCount) { "invalid human count" }
        val rng = Random(config.seed)
        val deck = TileSet.standard.shuffled(rng).toMutableList()
        val perimeter = Board.perimeter()
        val colors = PlayerColor.values()
        val players = (0 until config.playerCount).map { i ->
            val hand = List(HAND_SIZE) { deck.removeAt(0) }
            PlayerState(
                id = i, name = "Player ${i + 1}", color = colors[i],
                kind = if (i < config.humanCount) PlayerKind.HUMAN else PlayerKind.AI,
                stone = perimeter[i * perimeter.size / config.playerCount], hand = hand
            )
        }
        return GameState(Board.empty(), players, deck, 0, 0, GamePhase.PLAYING, emptyList(), config.mode, config.rules)
    }

    /** Legal moves: any hand tile x 4 rotations, except moves that eliminate the mover when a safe move exists. */
    fun legalMoves(state: GameState): List<Move> {
        if (state.phase != GamePhase.PLAYING) return emptyList()
        val cur = state.players[state.current]
        if (!cur.alive) return emptyList()
        val all = cur.hand.indices.flatMap { h -> (0..3).map { Move(h, it) } }
        val safe = all.filter { resolve(state, it).state.players[state.current].alive }
        return if (safe.isNotEmpty()) safe else all
    }

    fun applyMove(state: GameState, move: Move): MoveResult {
        if (move !in legalMoves(state)) throw IllegalMoveException("illegal move $move")
        return resolve(state, move)
    }

    internal fun resolve(state: GameState, move: Move): MoveResult {
        val cur = state.players[state.current]
        val stone = cur.stone!!
        val tile = cur.hand[move.handIndex].rotated(move.rotation)
        val board = state.board.place(stone.x, stone.y, tile)
        val events = ArrayList<GameEvent>()
        events += GameEvent.TilePlaced(cur.id, stone.x, stone.y, tile)

        val traces = HashMap<Int, Trace>()
        val stationary = HashMap<Int, Position>()
        for (p in state.players) if (p.alive) {
            val s = p.stone!!
            if (s.x == stone.x && s.y == stone.y) traces[p.id] = PathResolver.trace(board, s) else stationary[p.id] = s
        }
        val eliminated = CollisionResolver.resolve(traces, stationary)
        val loops = LoopDetector.loopsClosedBy(board, stone.x, stone.y)
        for (l in loops) events += GameEvent.LoopClosed(cur.id, l.size)

        val turnNo = state.turn + 1
        var deck = state.deck.toMutableList()
        val players = state.players.toMutableList()
        for (p in state.players) if (p.alive) {
            val t = traces[p.id]
            var np = p
            if (t != null) {
                events += GameEvent.StoneMoved(p.id, p.stone!!, t.end, t.segments)
                np = np.copy(stone = t.end, distance = p.distance + t.segments.size)
            }
            if (p.id == cur.id) np = np.copy(hand = np.hand.filterIndexed { i, _ -> i != move.handIndex })
            val reason = eliminated[p.id]
            if (reason != null) {
                events += GameEvent.PlayerEliminated(p.id, reason)
                deck.addAll(np.hand) // tiles of eliminated players return to the bottom of the deck
                np = np.copy(alive = false, stone = null, hand = emptyList(), eliminatedOnTurn = turnNo)
            }
            players[p.id] = np
        }

        if (state.mode == GameMode.LOOP_BATTLE) { // loops closed + opponents trapped inside them (not the mover's own stone)
            val gain = loops.size * state.rules.loopPoints + eliminated.count { (id, r) -> r == EliminationReason.LOOPED && id != cur.id } * state.rules.trapPoints
            players[cur.id] = players[cur.id].copy(score = players[cur.id].score + gain)
        }

        // refill hands, starting with the mover, clockwise
        val n = players.size
        for (k in 0 until n) {
            val i = (state.current + k) % n
            val p = players[i]
            if (!p.alive) continue
            val hand = p.hand.toMutableList()
            while (hand.size < HAND_SIZE && deck.isNotEmpty()) hand += deck.removeAt(0)
            players[i] = p.copy(hand = hand)
        }

        val alive = players.filter { it.alive }
        var phase = GamePhase.PLAYING
        var winners = emptyList<Int>()
        var next = state.current
        when {
            alive.size == 1 -> { phase = GamePhase.FINISHED; winners = listOf(alive[0].id) }
            alive.isEmpty() -> { phase = GamePhase.FINISHED; winners = eliminated.keys.sorted() } // simultaneous last elimination: shared win
            else -> {
                val nxt = (1..n).map { (state.current + it) % n }.firstOrNull { players[it].alive && players[it].hand.isNotEmpty() }
                if (nxt == null) { phase = GamePhase.FINISHED; winners = alive.map { it.id } } // nobody can move: survivors share the win
                else next = nxt
            }
        }
        if (phase == GamePhase.FINISHED) { winners = settle(state, players, winners); events += GameEvent.GameOver(winners) }
        return MoveResult(state.copy(board = board, players = players, deck = deck, current = next, turn = turnNo, phase = phase, winners = winners), events)
    }

    /**
     * Final scoring per mode. Classic/Solo: survivors win. Loop Battle: survivors get a bonus, highest score wins
     * (all-zero scores fall back to survival). Longest Path: greatest distance wins, even for an eliminated player. Ties share the win.
     */
    private fun settle(state: GameState, players: MutableList<PlayerState>, survivalWinners: List<Int>): List<Int> = when (state.mode) {
        GameMode.LOOP_BATTLE -> {
            for (i in players.indices) if (players[i].alive) players[i] = players[i].copy(score = players[i].score + state.rules.survivorBonus)
            val best = players.maxOf { it.score }
            if (best > 0) players.filter { it.score == best }.map { it.id } else survivalWinners
        }
        GameMode.LONGEST_PATH -> {
            for (i in players.indices) players[i] = players[i].copy(score = players[i].distance)
            val best = players.maxOf { it.distance }
            players.filter { it.distance == best }.map { it.id }
        }
        else -> survivalWinners
    }
}
