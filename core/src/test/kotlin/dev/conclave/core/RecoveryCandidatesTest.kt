package dev.conclave.core

import java.math.BigDecimal
import kotlin.test.*
import org.junit.jupiter.api.Test

class RecoveryCandidatesTest {
    @Test
    fun `search is stable bounded and starts at the exact authored feet position`() {
        val origin = Position(0.25, 1.5, -0.25)
        fun points() = RecoveryCandidates(origin, BigDecimal.ONE).asSequence().toList()
        val positions = points()
        assertEquals(positions, points())
        assertEquals(origin, positions.first())
        assertEquals(positions.distinct().size, positions.size)
        assertEquals(9, positions.size)
        val distances = positions.map {
            (it.x - origin.x).pow(2) + (it.y - origin.y).pow(2) + (it.z - origin.z).pow(2)
        }
        assertEquals(distances.sorted(), distances)
        assertTrue(distances.all { it <= BigDecimal.ONE })
        assertEquals(
            listOf(origin),
            RecoveryCandidates(origin, BigDecimal.ZERO).asSequence().toList(),
        )
        val limited = RecoveryCandidates(origin, BigDecimal(64), 8)
        repeat(8) { limited.next() }
        assertFailsWith<IllegalStateException> { limited.next() }
    }

    @Test
    fun `recovery destinations are ordered typed local dependencies and change identity`() {
        fun compile(recovery: String) =
            CatalogCompiler()
                .compile(
                    listOf(
                        SourceDocument(
                            "encounter.yaml",
                            timedManifest().replace("  start:", "  recovery: $recovery\n  start:"),
                        )
                    )
                )
        val primary =
            assertIs<Validation.Valid<CompiledCatalog>>(
                    compile("{location: entrance, fallbacks: [balcony]}")
                )
                .value
        val definition = primary.encounters.getValue(DefinitionId("local", "test"))
        assertEquals(listOf("entrance", "balcony"), definition.recovery!!.locations)
        assertEquals(setOf("entrance", "balcony"), definition.spatialReferences().locations)
        val changed =
            assertIs<Validation.Valid<CompiledCatalog>>(
                    compile("{location: balcony, fallbacks: [entrance]}")
                )
                .value
        assertNotEquals(primary.revision, changed.revision)
        assertEquals(
            "recovery_locations",
            diagnostic(compile("{location: entrance, fallbacks: [entrance]}")).code,
        )
        assertIs<Validation.Invalid>(compile("{location: {id: entrance, scope: world}}"))
    }
}
