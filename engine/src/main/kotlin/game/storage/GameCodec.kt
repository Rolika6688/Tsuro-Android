package game.storage

import game.ai.AiDifficulty
import game.board.BOARD_SIZE
import game.board.Board
import game.board.Position
import game.engine.GameEngine
import game.engine.GameMode
import game.engine.ModeRules
import game.engine.GamePhase
import game.engine.GameState
import game.players.PlayerColor
import game.players.PlayerKind
import game.players.PlayerState
import game.tiles.Tile
import game.tiles.TileSet

/** Everything needed to resume a game. The AI's private random stream restarts on restore (its choices stay legal and deterministic per state). */
data class SavedGame(val difficulty: AiDifficulty, val seed: Long, val state: GameState)

/**
 * Save format v1. decode() re-validates every invariant of a live game and throws SaveFormatException
 * if anything is inconsistent, so a corrupted or hand-edited save can never produce an unplayable state.
 */
object GameCodec {
    private const val VERSION = 1L

    fun encode(g: SavedGame): String {
        val s = g.state
        fun tile(t: Tile): List<Any?> = listOf(t.id.toLong()) + t.links.map { it.toLong() }
        val board = ArrayList<Any?>()
        for (y in 0 until BOARD_SIZE) for (x in 0 until BOARD_SIZE) board += s.board.tileAt(x, y)?.let(::tile)
        return Json.write(linkedMapOf(
            "v" to VERSION, "mode" to s.mode.name, "rules" to listOf(s.rules.loopPoints.toLong(), s.rules.trapPoints.toLong(), s.rules.survivorBonus.toLong()), "difficulty" to g.difficulty.name, "seed" to g.seed,
            "turn" to s.turn.toLong(), "current" to s.current.toLong(), "phase" to s.phase.name, "winners" to s.winners.map { it.toLong() },
            "board" to board, "deck" to s.deck.map(::tile),
            "players" to s.players.map { p -> linkedMapOf(
                "id" to p.id.toLong(), "name" to p.name, "color" to p.color.name, "kind" to p.kind.name,
                "stone" to p.stone?.let { listOf(it.x.toLong(), it.y.toLong(), it.port.toLong()) },
                "hand" to p.hand.map(::tile), "alive" to p.alive, "elim" to p.eliminatedOnTurn?.toLong(), "dist" to p.distance.toLong(), "score" to p.score.toLong()) }
        ))
    }

    fun decode(text: String): SavedGame {
        try { return decodeUnchecked(text) }
        catch (e: SaveFormatException) { throw e }
        catch (e: RuntimeException) { throw SaveFormatException("invalid save: ${e.message}") }
    }

    private inline fun <reified E : Enum<E>> enumOf(v: Any?): E = enumValues<E>().firstOrNull { it.name == v.asString() } ?: throw SaveFormatException("unknown ${E::class.simpleName}")
    private fun bad(m: String): Nothing = throw SaveFormatException(m)

    private fun decodeUnchecked(text: String): SavedGame {
        val root = Json.parse(text).asMap()
        if (root["v"].asLong() != VERSION) bad("unsupported version")
        val standard = TileSet.standard

        fun tile(v: Any?): Tile {
            val l = v.asList(); if (l.size != 9) bad("tile needs 9 numbers")
            val id = l[0].asInt(); if (id !in standard.indices) bad("tile id")
            return Tile(id, l.drop(1).map { it.asInt() })
        }
        fun isRotationOfStandard(t: Tile) = (0..3).any { standard[t.id].rotated(it).links == t.links }

        var board = Board.empty()
        val boardCells = root["board"].asList(); if (boardCells.size != BOARD_SIZE * BOARD_SIZE) bad("board size")
        boardCells.forEachIndexed { i, c -> if (c != null) { val t = tile(c); if (!isRotationOfStandard(t)) bad("placed tile altered"); board = board.place(i % BOARD_SIZE, i / BOARD_SIZE, t) } }

        val deck = root["deck"].asList().map { tile(it).also { t -> if (t != standard[t.id]) bad("deck tile altered") } }
        val pl = root["players"].asList(); if (pl.size !in 2..8) bad("player count")
        val players = pl.mapIndexed { idx, pv ->
            val m = pv.asMap()
            if (m["id"].asInt() != idx) bad("player ids")
            val stone = m["stone"]?.let { val a = it.asList(); if (a.size != 3) bad("stone"); Position(a[0].asInt(), a[1].asInt(), a[2].asInt()) }
            val hand = m["hand"].asList().map { tile(it).also { t -> if (t != standard[t.id]) bad("hand tile altered") } }
            PlayerState(idx, m["name"].asString(), enumOf<PlayerColor>(m["color"]), enumOf<PlayerKind>(m["kind"]), stone, hand,
                m["alive"].asBool(), m["elim"]?.asInt(), m["dist"].asInt(), m["score"].asInt())
        }
        val phase = enumOf<GamePhase>(root["phase"])
        val r = root["rules"].asList(); if (r.size != 3) bad("rules")
        val rules = ModeRules(r[0].asInt(), r[1].asInt(), r[2].asInt())
        if (rules.loopPoints < 0 || rules.trapPoints < 0 || rules.survivorBonus < 0) bad("rules")
        val state = GameState(board, players, deck, root["current"].asInt(), root["turn"].asInt(), phase, root["winners"].asList().map { it.asInt() }, enumOf<GameMode>(root["mode"]), rules)
        validate(state)
        return SavedGame(enumOf(root["difficulty"]), root["seed"].asLong(), state)
    }

    private fun validate(s: GameState) {
        val ids = s.board.placedTiles().map { it.id } + s.deck.map { it.id } + s.players.flatMap { p -> p.hand.map { it.id } }
        if (ids.size != 35 || ids.distinct().size != 35) bad("tile set is not complete and unique")
        if (s.turn < 0 || s.current !in s.players.indices) bad("turn/current")
        if (s.winners.any { it !in s.players.indices } || s.winners.distinct().size != s.winners.size) bad("winners")
        for (p in s.players) {
            if (p.hand.size > GameEngine.HAND_SIZE || p.distance < 0 || p.score < 0) bad("player data")
            if (p.alive) {
                val st = p.stone ?: bad("alive player without stone")
                if (!s.board.inBounds(st.x, st.y) || st.port !in 0..7) bad("stone position")
                if (s.board.tileAt(st.x, st.y) != null) bad("stone faces an occupied cell")
            } else if (p.stone != null || p.hand.isNotEmpty()) bad("eliminated player keeps stone or tiles")
        }
        val points = s.players.filter { it.alive }.map { it.stone }
        if (points.distinct().size != points.size) bad("two stones on one point")
        if (s.phase == GamePhase.PLAYING) {
            if (s.players.count { it.alive } < 2) bad("playing with fewer than two survivors")
            if (!s.players[s.current].alive || s.players[s.current].hand.isEmpty()) bad("current player cannot move")
            if (s.winners.isNotEmpty()) bad("winners while playing")
        } else if (s.winners.isEmpty()) bad("finished without winner")
    }
}
