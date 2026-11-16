import game.board.Board
import game.engine.*
import game.tiles.*
import kotlin.random.Random

fun check(c: Boolean, msg: String) { if (!c) { println("FAIL: $msg"); System.exit(1) } else println("ok   : $msg") }

fun main() {
    check(TileSet.allMatchingsCount == 105, "105 perfect matchings of 8 ports")
    val tiles = TileSet.standard
    check(tiles.size == 35, "35 distinct tiles after rotation reduction")
    check(tiles.all { t -> (0..3).all { t.rotated(it).pairs().size == 4 } }, "every rotation of every tile is a valid 4-path matching")
    check(tiles.all { it.rotated(4) == it && it.rotated(1).rotated(3) == it }, "4 quarter turns = identity")
    check(Board.perimeter().distinct().size == 48, "48 distinct perimeter points")

    var games = 0; var ties = 0; var maxTurns = 0; var totalTurns = 0L
    for (n in 2..8) for (seed in 0L until 300L) {
        var s = GameEngine.newGame(GameConfig(n, 0, seed * 31 + n))
        val rng = Random(seed)
        var guard = 0
        while (s.phase == GamePhase.PLAYING) {
            val moves = GameEngine.legalMoves(s)
            if (moves.isEmpty()) { println("FAIL: no legal moves for player ${s.current} n=$n seed=$seed"); return }
            val r = GameEngine.applyMove(s, moves[rng.nextInt(moves.size)])
            s = r.state
            val pts = s.alivePlayers.map { it.stone }
            if (pts.distinct().size != pts.size) { println("FAIL: two alive stones share a point"); return }
            val ids = s.board.placedTiles().map { it.id } + s.deck.map { it.id } + s.players.flatMap { p -> p.hand.map { it.id } }
            if (ids.distinct().size != 35 || ids.size != 35) { println("FAIL: tile conservation ${ids.size}"); return }
            if (++guard > 100) { println("FAIL: game too long"); return }
        }
        if (s.winners.isEmpty()) { println("FAIL: finished without winner"); return }
        games++; if (s.winners.size > 1) ties++; maxTurns = maxOf(maxTurns, s.turn); totalTurns += s.turn
    }
    println("ok   : $games random full games (2-8 players) finished; invariants held every move")
    println("       shared-win games: $ties, avg turns: ${totalTurns / games}, max turns: $maxTurns")
}
