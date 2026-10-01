package dev.conclave.core

import java.math.BigDecimal
import java.util.PriorityQueue
import kotlin.math.abs

/** Stable nearby feet positions. Incremental search never materializes a radius-sized cube. */
class RecoveryCandidates(
    private val origin: Position,
    radius: BigDecimal,
    private val limit: Int = 16_384,
) : Iterator<Position> {
    private data class Offset(val x: Int, val y: Int, val z: Int) {
        val distance = 4L * x * x + y.toLong() * y + 4L * z * z
    }

    private val radiusSquared = radius.multiply(radius).multiply(BigDecimal(4))
    private val pending =
        PriorityQueue(
            compareBy<Offset> { it.distance }
                .thenBy { abs(it.y) }
                .thenBy { it.y }
                .thenBy { it.x }
                .thenBy { it.z }
        )
    private val seen = hashSetOf<Offset>()
    private var emitted = 0

    init {
        require(radius >= BigDecimal.ZERO && radius <= BigDecimal(64) && limit > 0)
        pending += Offset(0, 0, 0)
        seen += Offset(0, 0, 0)
    }

    override fun hasNext() = pending.isNotEmpty()

    override fun next(): Position {
        if (!hasNext()) throw NoSuchElementException()
        check(emitted++ < limit) { "Recovery search exceeds its candidate capacity" }
        val next = pending.remove()
        for (candidate in
            listOf(
                Offset(next.x - 1, next.y, next.z),
                Offset(next.x + 1, next.y, next.z),
                Offset(next.x, next.y - 1, next.z),
                Offset(next.x, next.y + 1, next.z),
                Offset(next.x, next.y, next.z - 1),
                Offset(next.x, next.y, next.z + 1),
            )) if (BigDecimal(candidate.distance) <= radiusSquared && seen.add(candidate))
            pending += candidate
        return origin +
            Position(
                BigDecimal(next.x),
                BigDecimal(next.y).divide(BigDecimal(2)),
                BigDecimal(next.z),
            )
    }
}
