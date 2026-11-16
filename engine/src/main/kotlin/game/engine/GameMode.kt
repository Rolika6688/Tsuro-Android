package game.engine

/** [implemented] is false for modes whose special rules are scheduled for a later phase. */
enum class GameMode(val title: String, val description: String, val implemented: Boolean) {
    CLASSIC("Classic", "Last stone standing wins. 2–8 players, any mix of humans and AI.", true),
    SOLO("Solo", "One human against AI opponents at an adjustable difficulty.", true),
    LOOP_BATTLE("Loop Battle", "Close loops to score; trap opponents inside them for bonus points. Highest score wins.", true),
    LONGEST_PATH("Longest Path", "Distance travelled is what counts. The longest path wins, even if its owner is eliminated.", true)
}
