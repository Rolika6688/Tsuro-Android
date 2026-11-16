package game.players

import game.board.Position
import game.tiles.Tile

enum class PlayerColor { CRIMSON, AZURE, EMERALD, GOLDEN, VIOLET, TURQUOISE, ORANGE, WHITE }
enum class PlayerKind { HUMAN, AI }

data class PlayerState(
    val id: Int,
    val name: String,
    val color: PlayerColor,
    val kind: PlayerKind,
    val stone: Position?,
    val hand: List<Tile>,
    val alive: Boolean = true,
    val eliminatedOnTurn: Int? = null,
    val distance: Int = 0,
    val score: Int = 0
)
