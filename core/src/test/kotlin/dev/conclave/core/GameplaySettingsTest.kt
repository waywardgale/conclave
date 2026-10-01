package dev.conclave.core

import kotlin.test.*
import org.junit.jupiter.api.Test

class GameplaySettingsTest {
    @Test
    fun `long revival timing warns without rejecting useful noncombat configurations`() {
        val result =
            assertIs<Validation.Valid<CompiledCatalog>>(
                    compile("  revival: {combat_window: 1s, delay: 2s}\n")
                )
                .value
        assertEquals("revival_timing_warning", result.warnings.single().code)
        val finalTick =
            assertIs<Validation.Valid<CompiledCatalog>>(
                    compile("  revival: {combat_window: 1s, delay: 500ms, help_time: 500ms}\n")
                )
                .value
        assertTrue(finalTick.warnings.isEmpty())
    }

    private fun compile(settings: String) =
        CatalogCompiler()
            .compile(listOf(SourceDocument("settings.yaml", "schema: 1\nsettings:\n$settings")))

    @Test
    fun `omitted and explicitly materialized policies have the same captured revision`() {
        val omitted =
            assertIs<Validation.Valid<CompiledCatalog>>(CatalogCompiler().compile(emptyList()))
                .value
        val encoded = GameplaySettingsSchema.encode(omitted.settings)
        val explicit =
            assertIs<Validation.Valid<CompiledCatalog>>(
                    CatalogCompiler()
                        .compile(
                            listOf(
                                SourceDocument(
                                    "settings.yaml",
                                    "{\"schema\":1,\"settings\":$encoded}",
                                )
                            )
                        )
                )
                .value
        assertEquals(omitted.revision, explicit.revision)
        assertEquals(300, explicit.settings.revival.combatWindow.ticks)
        assertFalse(explicit.settings.revival.selfAllowed(false))
        assertTrue(explicit.settings.revival.assistanceAllowed)
        assertEquals(20.0, explicit.settings.revival.health.of(20.0))
        assertEquals(SpectatorMode.TEAMMATES, explicit.settings.spectating.mode)
        assertFalse(explicit.settings.spectating.sharePrivateInfo)
    }

    @Test
    fun `policy switches preserve their values and cannot bypass the master switch`() {
        val disabled =
            assertIs<Validation.Valid<CompiledCatalog>>(
                    compile(
                        "  revival: {enabled: false, self_revival: true, assisted_revival: true}\n"
                    )
                )
                .value
        assertTrue(disabled.settings.revival.selfRevival)
        assertFalse(disabled.settings.revival.selfAllowed(false))
        assertFalse(disabled.settings.revival.assistanceAllowed)
        val changed =
            assertIs<Validation.Valid<CompiledCatalog>>(
                    compile("  revival: {enabled: true, self_revival: true}\n")
                )
                .value
        assertNotEquals(disabled.revision, changed.revision)
        assertTrue(changed.settings.revival.selfAllowed(false))
        assertFalse(changed.settings.revival.selfAllowed(true))
        assertFalse(
            disabled.settings.revival.selfAllowed(false),
            "A newer policy cannot mutate a captured one",
        )
    }

    @Test
    fun `global shape units and encounter override authority are validated`() {
        for (text in
            listOf(
                "  id: policy",
                "  revival: {combat_window: 0s}",
                "  revival: {delay: 0}",
                "  revival: {health: 100}",
                "  recovery: {health: 101%}",
                "  recovery: {hunger: 21}",
                "  recovery: {search_radius: -1}",
            )) assertIs<Validation.Invalid>(compile(text), text)
        val settings =
            SourceDocument(
                "settings.yaml",
                "schema: 1\nsettings:\n  revival: {encounters_may_disable_self_revival: false}",
            )
        val encounter =
            SourceDocument(
                "encounter.yaml",
                timedManifest()
                    .replace(
                        "  start: waiting",
                        "  revival: {prohibit_self_revival: true}\n  start: waiting",
                    ),
            )
        assertEquals(
            "revival_override",
            diagnostic(CatalogCompiler().compile(listOf(settings, encounter))).code,
        )
        assertEquals(
            "duplicate_settings",
            diagnostic(
                    CatalogCompiler().compile(listOf(settings, settings.copy(file = "second.yaml")))
                )
                .code,
        )
        assertEquals(
            "settings_namespace",
            diagnostic(
                    CatalogCompiler()
                        .compile(listOf(settings.copy(text = "namespace: local\n" + settings.text)))
                )
                .code,
        )
    }

    @Test
    fun `outside recovery resolves explicit world locations without borrowing arena locations`() {
        val settings =
            SourceDocument(
                "settings.yaml",
                """
                schema: 1
                settings:
                  recovery:
                    outside_attempt_fallbacks:
                      - location: {id: hub, scope: world}
                """
                    .trimIndent(),
            )
        val location =
            SourceDocument(
                "location.yaml",
                """
                schema: 1
                location:
                  id: hub
                  dimension: minecraft:overworld
                  position: {x: 0, y: 64, z: 0}
                """
                    .trimIndent(),
            )
        assertEquals(
            "unknown_world_location",
            diagnostic(CatalogCompiler().compile(listOf(settings))).code,
        )
        assertIs<Validation.Valid<CompiledCatalog>>(
            CatalogCompiler().compile(listOf(settings, location))
        )
        assertIs<Validation.Invalid>(
            CatalogCompiler()
                .compile(
                    listOf(
                        settings.copy(
                            text = settings.text.replace("scope: world", "scope: encounter")
                        ),
                        location,
                    )
                )
        )
    }
}
