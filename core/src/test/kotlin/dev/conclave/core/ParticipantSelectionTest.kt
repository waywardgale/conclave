package dev.conclave.core

import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test

class ParticipantSelectionTest {
    private fun compile(selection: String?) =
        CatalogCompiler()
            .compile(
                listOf(
                    SourceDocument(
                        "encounter.yaml",
                        timedManifest()
                            .replace(
                                "  start: waiting",
                                (selection?.let { "  participants: $it\n" } ?: "") +
                                    "  start: waiting",
                            ),
                    )
                )
            )

    @Test
    fun `prestart defaults select the entire online raider set including dead players`() {
        val default = assertIs<Validation.Valid<CompiledCatalog>>(compile(null)).value
        val explicit =
            assertIs<Validation.Valid<CompiledCatalog>>(
                    compile("{from: online_raiders, state: any}")
                )
                .value
        assertEquals(default.revision, explicit.revision)
        val alive = PlayerObservation(UUID.randomUUID(), true, LifeState.ALIVE, null, false)
        val dead = PlayerObservation(UUID.randomUUID(), true, LifeState.DEAD, null, false)
        val gm = PlayerObservation(UUID.randomUUID(), true, LifeState.ALIVE, null, true)
        val offline = PlayerObservation(UUID.randomUUID(), false, LifeState.ALIVE, null, false)
        assertEquals(
            listOf(alive.id, dead.id),
            default.encounters.values
                .single()
                .participants
                .select(PlayerFrame(listOf(alive, dead, gm, offline), emptySet()))
                .map { it.id },
        )
        val narrowed = assertIs<Validation.Valid<CompiledCatalog>>(compile("{state: alive}")).value
        assertNotEquals(default.revision, narrowed.revision)
        assertEquals(
            listOf(alive.id),
            narrowed.encounters.values
                .single()
                .participants
                .select(PlayerFrame(listOf(alive, dead, gm, offline), emptySet()))
                .map { it.id },
        )
    }

    @Test
    fun `prestart rejects nonexistent attempt state throughout nested predicates`() {
        for (selection in
            listOf(
                "{from: participants}",
                "{role: reader}",
                "{participation: any}",
                "{where: {not: {has_role: reader}}}",
                "{where: {player_state: {participation: active}}}",
            )) {
            assertEquals("prestart_state", diagnostic(compile(selection)).code, selection)
        }
        assertIs<Validation.Invalid>(compile("{online: false}"))
        assertIs<Validation.Valid<CompiledCatalog>>(
            compile("{from: online_players, where: {player_state: {online: true}}}")
        )
    }
}
