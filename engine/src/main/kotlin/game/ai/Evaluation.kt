package game.ai

import game.board.BOARD_SIZE
import game.board.Board
import game.board.Position
import game.engine.GameEngine
import game.engine.GamePhase
import game.engine.GameState
import game.engine.Move
import game.rules.PathResolver
import game.tiles.TileSet

/**
 * Deterministic position evaluation used by the Normal and Hard AI. Scores a state from one player's
 * point of view. Pure functions of the state: same input, same score.
 */
object Evaluation {
    data class Weights(
        val survival: Double,
        val edge: Double,
        val future: Double,
        val sharedCell: Double,
        val kill: Double,
        val opponentDanger: Double,
        val distance: Double,
        val win: Double = 1000.0,
        val loss: Double = -1000.0
    )

    val NORMAL = Weights(survival = 40.0, edge = 3.0, future = 15.0, sharedCell = -5.0, kill = 0.0, opponentDanger = 0.0, distance = 0.5)
    val HARD = Weights(survival = 40.0, edge = 3.0, future = 15.0, sharedCell = -5.0, kill = 60.0, opponentDanger = 12.0, distance = 0.5)

    /** What the evaluator needs to know about the move that produced the state. */
    data class Context(val playerId: Int, val opponentsAliveBefore: Int, val knownTileIds: Set<Int>)

    /**
     * Probability that a stone survives the next tile placed on the cell it faces, assuming the tile is
     * equally likely to be any of the 105 possible orientations (no peeking at hidden hands).
     */
    fun survivalProbability(board: Board, stone: Position): Double {
        val orientations = TileSet.allOrientations
        var ok = 0
        for (t in orientations) PathResolver.trace(board.place(stone.x, stone.y, t), stone).let { if (!it.exited && !it.looped) ok++ }
        return ok.toDouble() / orientations.size
    }

    /** 0 on a border cell, up to 2 in the middle of the 6x6 board. */
    fun edgeDistance(pos: Position): Int = minOf(pos.x, pos.y, BOARD_SIZE - 1 - pos.x, BOARD_SIZE - 1 - pos.y)

    /** Other alive stones that face the same cell as [playerId] (they can collide with it on the next placement). */
    fun sharedCellOpponents(state: GameState, playerId: Int): Int {
        val me = state.players[playerId].stone ?: return 0
        return state.players.count { it.alive && it.id != playerId && it.stone?.x == me.x && it.stone?.y == me.y }
    }

    /** Fraction of (tile, rotation) choices from the player's already-known tiles that are survivable next turn. */
    fun futureSafeFraction(state: GameState, playerId: Int, knownTileIds: Set<Int>): Double {
        val me = state.players[playerId]
        if (!me.alive || me.stone == null) return 0.0
        val hand = me.hand.filter { it.id in knownTileIds }
        if (hand.isEmpty()) return 0.0
        val hyp = state.copy(current = playerId, players = state.players.map { if (it.id == playerId) it.copy(hand = hand) else it })
        var safe = 0; var total = 0
        for (h in hand.indices) for (r in 0..3) {
            total++
            if (GameEngine.resolve(hyp, Move(h, r)).state.players[playerId].alive) safe++
        }
        return safe.toDouble() / total
    }

    fun evaluate(result: GameState, ctx: Context, w: Weights): Double {
        val me = result.players[ctx.playerId]
        if (result.phase == GamePhase.FINISHED) return if (ctx.playerId in result.winners) w.win / result.winners.size else w.loss
        if (!me.alive) return w.loss
        val stone = me.stone!!
        var s = w.survival * survivalProbability(result.board, stone)
        s += w.edge * edgeDistance(stone)
        s += w.future * futureSafeFraction(result, ctx.playerId, ctx.knownTileIds)
        s += w.sharedCell * sharedCellOpponents(result, ctx.playerId)
        s += w.distance * me.distance
        s += when (result.mode) { // special modes reward what they actually score
            game.engine.GameMode.LOOP_BATTLE -> 15.0 * me.score
            game.engine.GameMode.LONGEST_PATH -> 4.0 * me.distance
            else -> 0.0
        }
        val opponentsAlive = result.players.count { it.alive && it.id != ctx.playerId }
        s += w.kill * (ctx.opponentsAliveBefore - opponentsAlive)
        if (w.opponentDanger != 0.0) {
            for (o in result.players) if (o.alive && o.id != ctx.playerId) s += w.opponentDanger * (1.0 - survivalProbability(result.board, o.stone!!))
        }
        return s
    }
}
