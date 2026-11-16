package game

import game.board.Board
import game.board.Position
import game.engine.GamePhase
import game.engine.GameState
import game.players.PlayerColor
import game.players.PlayerKind
import game.players.PlayerState
import game.tiles.Tile

/** Builds a tile from the given pairs; remaining ports are paired in order. */
fun tileOf(id: Int, vararg pairs: Pair<Int, Int>): Tile {
    val l = IntArray(8) { -1 }
    for ((a, b) in pairs) { l[a] = b; l[b] = a }
    val free = (0..7).filter { l[it] == -1 }
    for (i in free.indices step 2) { l[free[i]] = free[i + 1]; l[free[i + 1]] = free[i] }
    return Tile(id, l.toList())
}

/** Four small corner arcs (0-1, 2-3, 4-5, 6-7): from a corner stone every rotation exits the board. */
val CORNER = Tile(100, listOf(1, 0, 3, 2, 5, 4, 7, 6))

/** Four straight lines (0-5, 1-4, 2-7, 3-6): every path crosses the tile. */
val STRAIGHT = Tile(101, listOf(5, 4, 7, 6, 1, 0, 3, 2))

fun player(id: Int, stone: Position?, hand: List<Tile>, alive: Boolean = true) =
    PlayerState(id, "P$id", PlayerColor.values()[id], PlayerKind.HUMAN, stone, hand, alive)

fun stateOf(players: List<PlayerState>, board: Board = Board.empty(), deck: List<Tile> = emptyList(), current: Int = 0, turn: Int = 0) =
    GameState(board, players, deck, current, turn, GamePhase.PLAYING, emptyList())

inline fun <reified T : Throwable> expectThrows(block: () -> Unit) {
    try { block() } catch (e: Throwable) {
        if (e is T) return
        throw AssertionError("expected ${T::class.simpleName} but got $e")
    }
    throw AssertionError("expected ${T::class.simpleName} but nothing was thrown")
}
