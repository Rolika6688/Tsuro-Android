# AI design (Phase 4)

Common: every AI returns a move from `GameEngine.legalMoves(state)`. Choices are deterministic for (state, seed).
Hidden information is never read: opponents' hands and the deck are not used for decisions (Normal ignores them;
Hard re-deals them randomly in every simulation).

| Level  | Class          | Method |
|--------|----------------|--------|
| Easy   | RandomAi       | Random legal move. Legal moves already exclude avoidable eliminations. |
| Normal | HeuristicAi    | One ply: scores the resulting position with `Evaluation.NORMAL`. |
| Hard   | MonteCarloAi   | Ranks moves with `Evaluation.HARD`, then simulates 24 complete games for each of the best 6 moves (hidden tiles re-dealt each time) and picks the best estimated win chance. |

Evaluation factors (`Evaluation.kt`): survival probability of the next placement (over all 105 tile orientations),
distance to the board edge, share of safe moves left with tiles still in hand, stones sharing my cell, distance travelled,
(Hard) opponents eliminated and opponents' danger.

Measured strength (seat-rotated, fixed seeds, 2 players, shared wins excluded):
- Normal beats Easy ~216 : 56 (300 games)
- Hard beats Normal 125 : 50, Hard beats Easy 179 : 15 (200 games each)
In 4-player games Hard is also clearly above a single Normal/Easy seat.

Findings during development: a hand-tuned two-ply "best follow-up" search made Hard WEAKER than Normal (it was dropped);
the Monte Carlo approach replaced it. Speed: Hard roughly 25 ms/move on a desktop JVM (not yet measured on a phone).
