# Game modes — implemented interpretation

The original game has no official loop or distance scoring, so these are this project's own documented rules.
All values live in `ModeRules` (engine) and can be changed without touching other code.

Shared rules (every mode): 6x6 board, 35 tiles, 3 tiles per hand, simultaneous movement, elimination by leaving the board, by
meeting another stone (same path or same point), or by being trapped in a loop. The game ends when at most one stone is left.

| Mode | Players | Winner |
|------|---------|--------|
| Classic | 2-8, any human/AI mix | Last stone standing. If the last stones die on the same move they share the win. |
| Solo | 1 human + AI, difficulty selectable | Same as Classic. |
| Loop Battle | 2-8 | Highest score. Closing a loop: 1 point. Each opponent stone trapped in a loop you closed: 3 points. Stones alive at the end: 2 bonus points. All scores 0 -> normal survival winner. Ties share. |
| Longest Path | 2-8 | Greatest number of tile paths travelled. Eliminated players keep their distance, so they can still win. Ties share. |

Loop detection (`LoopDetector`): placing a tile closes a loop when following one of its paths forward returns to the same path.
Each distinct loop is counted once per placement. A stone whose own path closes into a loop can never reach an end and is eliminated.
Defaults: `ModeRules(loopPoints = 1, trapPoints = 3, survivorBonus = 2)`.

Not part of the official game: the Dragon tile is not implemented; tiles of eliminated players return to the bottom of the deck.
