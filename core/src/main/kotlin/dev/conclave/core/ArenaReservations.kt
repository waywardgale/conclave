package dev.conclave.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class ChunkCoordinate(val x: Int, val z: Int)

class ChunkFootprint
private constructor(primary: Set<ChunkCoordinate>, retained: Set<ChunkCoordinate>) {
    val primary: Set<ChunkCoordinate> = java.util.Set.copyOf(primary)
    val retained: Set<ChunkCoordinate> = java.util.Set.copyOf(retained)

    companion object {
        /**
         * Native generation/loading propagation is accounted for separately from playable chunks.
         */
        fun plan(
            geometry: Geometry,
            engine: GeometryEngine,
            propagationRadius: Int,
            candidateLimit: Int = 16_384,
            retainedLimit: Int = 16_384,
        ): ChunkFootprint {
            require(propagationRadius in 0..32 && candidateLimit > 0 && retainedLimit > 0)
            val candidates = linkedSetOf<ChunkCoordinate>()
            fun chunk(coordinate: BigDecimal) =
                coordinate.divide(BigDecimal(16), 0, RoundingMode.FLOOR).intValueExact()
            fun collect(shape: Geometry) {
                if (shape is Geometry.Composite) {
                    shape.include.forEach(::collect)
                    return
                }
                val bounds = shape.bounds
                val minX = chunk(bounds.min.x)
                val maxX = chunk(bounds.max.x)
                val minZ = chunk(bounds.min.z)
                val maxZ = chunk(bounds.max.z)
                if ((maxX.toLong() - minX + 1) * (maxZ.toLong() - minZ + 1) > candidateLimit)
                    throw GeometryCapacityException(
                        "Arena geometry exceeds the candidate chunk limit"
                    )
                for (x in minX..maxX) for (z in minZ..maxZ) {
                    candidates += ChunkCoordinate(x, z)
                    if (candidates.size > candidateLimit)
                        throw GeometryCapacityException(
                            "Arena geometry exceeds the candidate chunk limit"
                        )
                }
            }
            collect(geometry)
            val primary =
                candidates.filterTo(linkedSetOf()) { chunk ->
                    engine.overlaps(
                        geometry,
                        BodyBounds(
                            Position(
                                BigDecimal(chunk.x * 16L),
                                geometry.bounds.min.y,
                                BigDecimal(chunk.z * 16L),
                            ),
                            Position(
                                BigDecimal(chunk.x * 16L + 16),
                                geometry.bounds.max.y,
                                BigDecimal(chunk.z * 16L + 16),
                            ),
                        ),
                    )
                }
            val retained = linkedSetOf<ChunkCoordinate>()
            for (chunk in primary) for (dx in -propagationRadius..propagationRadius) for (dz in
                -propagationRadius..propagationRadius) {
                retained += ChunkCoordinate(Math.addExact(chunk.x, dx), Math.addExact(chunk.z, dz))
                if (retained.size > retainedLimit)
                    throw GeometryCapacityException(
                        "Arena and required neighboring chunks exceed the retained chunk limit"
                    )
            }
            return ChunkFootprint(primary, retained)
        }
    }
}

class ArenaReservation(
    val attempt: UUID,
    val arena: DefinitionId,
    val dimension: String,
    val boundary: Geometry,
    players: Set<UUID>,
) {
    val players: Set<UUID> = java.util.Set.copyOf(players)

    init {
        require(players.isNotEmpty() && players.size <= 1024)
    }
}

sealed interface ArenaAdmission {
    data object Reserved : ArenaAdmission

    data class Conflict(val attempt: UUID, val reason: String) : ArenaAdmission
}

/**
 * One server decision reserves identity, exact pinned volume, and the selected players together.
 */
class ArenaReservations(private val geometry: GeometryEngine) {
    private val thread = Thread.currentThread()
    private val reservations = linkedMapOf<UUID, ArenaReservation>()

    fun reserve(candidate: ArenaReservation): ArenaAdmission {
        check(Thread.currentThread() === thread)
        require(candidate.attempt !in reservations) { "Attempt already has an arena reservation" }
        for (existing in reservations.values) {
            if (existing.arena == candidate.arena)
                return ArenaAdmission.Conflict(
                    existing.attempt,
                    "The arena already belongs to another attempt or recovery",
                )
            if (existing.players.any { it in candidate.players })
                return ArenaAdmission.Conflict(
                    existing.attempt,
                    "A selected player still belongs to another attempt or recovery",
                )
            if (
                existing.dimension == candidate.dimension &&
                    geometry.intersects(existing.boundary, candidate.boundary)
            )
                return ArenaAdmission.Conflict(
                    existing.attempt,
                    "The arena intersects another attempt's captured boundary",
                )
        }
        reservations[candidate.attempt] = candidate
        return ArenaAdmission.Reserved
    }

    fun release(attempt: UUID): Boolean {
        check(Thread.currentThread() === thread)
        return reservations.remove(attempt) != null
    }

    fun occupied(): List<ArenaReservation> {
        check(Thread.currentThread() === thread)
        return java.util.List.copyOf(reservations.values)
    }
}
