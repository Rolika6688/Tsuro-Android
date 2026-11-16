package game.ai

import game.engine.GameEngine
import game.engine.GameState
import game.engine.Move
import kotlin.random.Random

enum class AiDifficulty { EASY, NORMAL, HARD }

interface AiPlayer {
    /** Must always return a move contained in GameEngine.legalMoves(state). */
    fun chooseMove(state: GameState): Move
}

/** Phase-3 placeholder: random legal move (the engine already filters out avoidable suicides). Replaced in Phase 4. */
class RandomAi(seed: Long = 0L) : AiPlayer {
    private val rng = Random(seed)
    override fun chooseMove(state: GameState): Move {
        val moves = GameEngine.legalMoves(state)
        require(moves.isNotEmpty()) { "no legal moves" }
        return moves[rng.nextInt(moves.size)]
    }
}
