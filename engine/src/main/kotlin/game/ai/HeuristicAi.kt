package game.ai

import game.engine.GameEngine
import game.engine.GameState
import game.engine.Move
import kotlin.random.Random

/**
 * One-ply AI (Normal): scores the position that results from every legal move with [Evaluation]
 * and plays the best. Ties are broken by a seeded RNG, so the choice is a pure function of (state, seed).
 */
class HeuristicAi(private val weights: Evaluation.Weights, private val seed: Long = 0L) : AiPlayer {

    override fun chooseMove(state: GameState): Move {
        val scored = scoreMoves(state, GameEngine.legalMoves(state))
        val best = scored.maxOf { it.second }
        val ties = scored.filter { it.second >= best - 1e-9 }
        return ties[Random(seed * 1_000_003L + state.turn).nextInt(ties.size)].first
    }

    fun scoreMoves(state: GameState, moves: List<Move>): List<Pair<Move, Double>> {
        require(moves.isNotEmpty()) { "no legal moves" }
        return moves.map { it to score(state, it) }
    }

    fun score(state: GameState, move: Move): Double {
        val me = state.current
        val remaining = state.players[me].hand.filterIndexed { i, _ -> i != move.handIndex }.map { it.id }.toSet()
        val result = GameEngine.resolve(state, move).state
        return Evaluation.evaluate(result, Evaluation.Context(me, state.alivePlayers.size - 1, remaining), weights)
    }
}
