package game.tiles

import game.expectThrows
import game.tileOf
import org.junit.Assert.*
import org.junit.Test

class TileTest {
    @Test fun standardSetHas105MatchingsAnd35Tiles() {
        assertEquals(105, TileSet.allMatchingsCount)
        assertEquals(35, TileSet.standard.size)
        assertEquals(35, TileSet.standard.map { it.id }.distinct().size)
    }

    @Test fun standardTilesAreUniqueUpToRotation() {
        val classes = TileSet.standard.map { t -> (0..3).map { t.rotated(it).links.toString() }.toSet() }
        for (i in classes.indices) for (j in i + 1 until classes.size) assertTrue(classes[i].intersect(classes[j]).isEmpty())
    }

    @Test fun everyRotationOfEveryTileIsAValidMatching() {
        for (t in TileSet.standard) for (r in 0..3) {
            val rt = t.rotated(r)
            assertEquals(4, rt.pairs().size)
            for (p in 0..7) { assertNotEquals(p, rt.partner(p)); assertEquals(p, rt.partner(rt.partner(p))) }
        }
    }

    @Test fun rotationTransformsConnections() {
        for (t in TileSet.standard) for (r in 0..3) for (p in 0..7)
            assertEquals(Port.rotate(t.partner(p), r), t.rotated(r).partner(Port.rotate(p, r)))
        assertTrue(tileOf(1, 0 to 1).rotated(1).pairs().contains(2 to 3))
    }

    @Test fun fourQuarterTurnsAreIdentity() {
        for (t in TileSet.standard) { assertEquals(t, t.rotated(4)); assertEquals(t, t.rotated(1).rotated(3)); assertEquals(t, t.rotated(-1).rotated(1)) }
    }

    @Test fun distinctRotationsCoverAll105Matchings() {
        val total = TileSet.standard.sumOf { t -> (0..3).map { t.rotated(it).links }.toSet().size }
        assertEquals(105, total)
    }

    @Test fun invalidTilesAreRejected() {
        expectThrows<IllegalArgumentException> { Tile(0, listOf(1, 0, 3, 2, 5, 4, 7, 7)) }
        expectThrows<IllegalArgumentException> { Tile(0, listOf(0, 1, 2, 3, 4, 5, 6, 7)) }
        expectThrows<IllegalArgumentException> { Tile(0, listOf(1, 0, 3, 2)) }
    }

    @Test fun facingPortsAreSymmetricAndOnOppositeSides() {
        for (p in 0..7) {
            assertEquals(p, Port.facing(Port.facing(p)))
            assertEquals((Port.side(p).ordinal + 2) % 4, Port.side(Port.facing(p)).ordinal)
        }
        assertEquals(5, Port.facing(0))
    }
}
