package game.rules

import game.board.Position

enum class EliminationReason { EXITED_BOARD, COLLISION, LOOPED }

/**
 * Simultaneous resolution. A stone is eliminated when
 *  - it leaves the board, or
 *  - its path closes into a loop (no survival path remains), or
 *  - it ends on the same edge point as another stone (moving or stationary), or
 *  - it walks along a tile path that another stone also walks (head-on or same-direction merge).
 */
object CollisionResolver {
    fun resolve(traces: Map<Int, Trace>, stationary: Map<Int, Position>): Map<Int, EliminationReason> {
        val result = HashMap<Int, EliminationReason>()
        for ((id, t) in traces) if (t.exited) result[id] = EliminationReason.EXITED_BOARD else if (t.looped) result[id] = EliminationReason.LOOPED

        val segOwners = HashMap<Segment, MutableSet<Int>>()
        for ((id, t) in traces) for (s in t.segments) segOwners.getOrPut(s) { HashSet() }.add(id)
        for (owners in segOwners.values) if (owners.size > 1) for (id in owners) result.putIfAbsent(id, EliminationReason.COLLISION)

        val pointOwners = HashMap<Position, MutableSet<Int>>()
        for ((id, t) in traces) t.end?.let { pointOwners.getOrPut(it) { HashSet() }.add(id) }
        for ((id, p) in stationary) pointOwners.getOrPut(p) { HashSet() }.add(id)
        for (owners in pointOwners.values) if (owners.size > 1) for (id in owners) result.putIfAbsent(id, EliminationReason.COLLISION)
        return result
    }
}
