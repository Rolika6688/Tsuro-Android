package game.ai

import game.engine.GameEngine
import game.engine.GamePhase
import game.engine.GameState
import game.engine.Move
import kotlin.random.Random

/**
 * Hard AI. Estimates each candidate move's win probability by simulating complete games.
 *
 * - Hidden information is respected: for every simulation the tiles the AI cannot see (opponents' hands and
 *   the deck) are reshuffled and re-dealt, so it never peeks at real opponent hands.
 * - Candidates are first ranked by the heuristic [Evaluation] (survival, board edges, future options, kills,
 *   opponent danger) and only the best [candidates] are simulated.
 * - Deterministic: every random choice derives from (seed, turn, candidate, simulation index).
 */
class MonteCarloAi(
    private val simulations: Int = 24,
    private val candidates: Int = 6,
    private val seed: Long = 0L,
    private val prior: Evaluation.Weights = Evaluation.HARD
) : AiPlayer {
    private val heuristic = HeuristicAi(prior, seed)

    override fun chooseMove(state: GameState): Move {
        val legal = GameEngine.legalMoves(state)
        require(legal.isNotEmpty()) { "no legal moves" }
        if (legal.size == 1) return legal[0]
        val ranked = heuristic.scoreMoves(state, legal).sortedByDescending { it.second }.take(candidates)
        val me = state.current
        var bestMove = ranked[0].first
        var bestValue = Double.NEGATIVE_INFINITY
        ranked.forEachIndexed { ci, (move, prior) ->
            var total = 0.0
            for (k in 0 until simulations) {
                val rng = Random(seed * 7_919L + state.turn * 104_729L + ci * 1_299_709L + k)
                total += simulate(determinize(state, rng), move, me, rng)
            }
            val value = total / simulations + prior * 1e-5 // heuristic only breaks (near) ties
            if (value > bestValue) { bestValue = value; bestMove = move }
        }
        return bestMove
    }

    /** Re-deal everything the mover cannot see, keeping hand sizes and the deck size unchanged. */
    internal fun determinize(state: GameState, rng: Random): GameState {
        val hidden = (state.players.filter { it.id != state.current }.flatMap { it.hand } + state.deck).shuffled(rng)
        var next = 0
        val players = state.players.map { p ->
            if (p.id == state.current) p else p.copy(hand = hidden.subList(next, next + p.hand.size).also { next += p.hand.size })
        }
        return state.copy(players = players, deck = hidden.subList(next, hidden.size))
    }

    private fun simulate(start: GameState, first: Move, me: Int, rng: Random): Double {
        var s = GameEngine.resolve(start, first).state
        while (s.phase == GamePhase.PLAYING) {
            val moves = GameEngine.legalMoves(s)
            s = GameEngine.resolve(s, moves[rng.nextInt(moves.size)]).state
        }
        return if (me in s.winners) 1.0 / s.winners.size else 0.0
    }
}
