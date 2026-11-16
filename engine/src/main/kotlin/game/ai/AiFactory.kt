package game.ai

object AiFactory {
    fun create(difficulty: AiDifficulty, seed: Long = 0L): AiPlayer = when (difficulty) {
        AiDifficulty.EASY -> RandomAi(seed)
        AiDifficulty.NORMAL -> HeuristicAi(Evaluation.NORMAL, seed)
        AiDifficulty.HARD -> MonteCarloAi(seed = seed)
    }
}
