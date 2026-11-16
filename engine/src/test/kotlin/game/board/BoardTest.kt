package game.board

import game.STRAIGHT
import game.expectThrows
import game.tiles.Port
import org.junit.Assert.*
import org.junit.Test

class BoardTest {
    @Test fun boardIs6x6() { assertEquals(6, BOARD_SIZE) }

    @Test fun boundsAreChecked() {
        val b = Board.empty()
        assertTrue(b.inBounds(0, 0)); assertTrue(b.inBounds(5, 5))
        assertFalse(b.inBounds(-1, 0)); assertFalse(b.inBounds(6, 0)); assertFalse(b.inBounds(0, 6))
        assertNull(b.tileAt(9, 9))
    }

    @Test fun placingIsImmutableAndRejectsOccupiedOrOutOfBounds() {
        val b = Board.empty()
        val b2 = b.place(2, 3, STRAIGHT)
        assertNull(b.tileAt(2, 3)); assertEquals(STRAIGHT, b2.tileAt(2, 3)); assertEquals(1, b2.placedTiles().size)
        expectThrows<IllegalArgumentException> { b2.place(2, 3, STRAIGHT) }
        expectThrows<IllegalArgumentException> { b.place(6, 0, STRAIGHT) }
    }

    @Test fun perimeterHas48DistinctOutwardFacingPoints() {
        val b = Board.empty()
        val per = Board.perimeter()
        assertEquals(48, per.size); assertEquals(48, per.distinct().size)
        for (p in per) {
            assertTrue(b.inBounds(p.x, p.y))
            val s = Port.side(p.port)
            assertFalse("point must face outside the board", b.inBounds(p.x + s.dx, p.y + s.dy))
        }
    }
}
