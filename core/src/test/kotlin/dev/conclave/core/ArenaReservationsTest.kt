package dev.conclave.core

import java.math.BigDecimal
import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test

class ArenaReservationsTest {
    private fun box(x: Double, z: Double = 0.0, width: Int = 10) =
        Geometry.Box(Position(x, 60.0, z), BigDecimal(width), BigDecimal(10), BigDecimal(10))

    @Test
    fun `reservations preserve the old placement and stable identity across moved publication`() =
        GeometryEngine(500).use { geometry ->
            val reservations = ArenaReservations(geometry)
            val first = UUID.randomUUID()
            assertIs<ArenaAdmission.Reserved>(
                reservations.reserve(
                    ArenaReservation(
                        first,
                        DefinitionId("local", "hall"),
                        "minecraft:overworld",
                        box(0.0),
                        setOf(UUID.randomUUID()),
                    )
                )
            )
            assertIs<ArenaAdmission.Conflict>(
                reservations.reserve(
                    ArenaReservation(
                        UUID.randomUUID(),
                        DefinitionId("local", "hall"),
                        "minecraft:overworld",
                        box(1000.0),
                        setOf(UUID.randomUUID()),
                    )
                )
            )
            assertIs<ArenaAdmission.Conflict>(
                reservations.reserve(
                    ArenaReservation(
                        UUID.randomUUID(),
                        DefinitionId("local", "next"),
                        "minecraft:overworld",
                        box(10.0),
                        setOf(UUID.randomUUID()),
                    )
                )
            )
            assertIs<ArenaAdmission.Reserved>(
                reservations.reserve(
                    ArenaReservation(
                        UUID.randomUUID(),
                        DefinitionId("local", "nether"),
                        "minecraft:the_nether",
                        box(0.0),
                        setOf(UUID.randomUUID()),
                    )
                )
            )
            assertTrue(reservations.release(first))
            assertIs<ArenaAdmission.Reserved>(
                reservations.reserve(
                    ArenaReservation(
                        UUID.randomUUID(),
                        DefinitionId("local", "hall"),
                        "minecraft:overworld",
                        box(1000.0),
                        setOf(UUID.randomUUID()),
                    )
                )
            )
        }

    @Test
    fun `disconnected regions retain their own neighborhoods and preserve excluded chunks`() =
        GeometryEngine(500).use { geometry ->
            val separate = Geometry.Composite(listOf(box(8.0), box(1608.0)))
            val footprint = ChunkFootprint.plan(separate, geometry, 2)
            assertEquals(4, footprint.primary.size)
            assertFalse(ChunkCoordinate(50, 0) in footprint.retained)
            val removed = Geometry.Composite(listOf(box(8.0), box(1608.0)), listOf(box(1608.0)))
            assertEquals(
                setOf(ChunkCoordinate(0, -1), ChunkCoordinate(0, 0)),
                ChunkFootprint.plan(removed, geometry, 0).primary,
            )
        }

    @Test
    fun `footprint limits apply before large native chunk work`() =
        GeometryEngine().use { geometry ->
            assertFailsWith<GeometryCapacityException> {
                ChunkFootprint.plan(box(0.0, width = 10000), geometry, 2, candidateLimit = 100)
            }
            assertFailsWith<GeometryCapacityException> {
                ChunkFootprint.plan(box(8.0), geometry, 14, retainedLimit = 50)
            }
        }
}
