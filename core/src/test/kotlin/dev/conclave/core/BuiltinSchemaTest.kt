package dev.conclave.core

import kotlin.test.*
import org.junit.jupiter.api.Test

class BuiltinSchemaTest {
    private val reader = YamlDocumentReader()

    private fun yaml(text: String): YamlValue.Mapping =
        assertIs<Validation.Valid<YamlValue.Mapping>>(
                reader.read(SourceDocument("test.yaml", text))
            )
            .value

    private fun compile(type: String, text: String): Validation<CompiledMechanic> =
        BuiltinMechanics.registry().compile(DefinitionId("conclave", type), yaml(text))

    @Test
    fun `all registered builtins round trip their effective typed configuration`() {
        val fixtures =
            mapOf(
                "capture" to "area: plate\nduration: 0.05s\non_interrupt: {decay: 500ms}",
                "interact" to "targets: [{block: console}]\nuses: 3\nhold: 1s\nuse_cooldown: 2s",
                "deliver" to "relic: orb\ndestination: {area: altar}\ntrigger: enter",
                "defeat" to
                    "group: guards\ncompletion: {count: 2}\ncauses: [death]\ndamage_types: [minecraft:arrow]",
                "match_pattern" to
                    "tokens: [sun, moon]\npattern: {sample: {length: 2}}\ninputs: [{token: sun, targets: [{block: left}]}, {token: moon, targets: [{block: right}]}]",
            )
        for ((type, source) in fixtures) {
            val result =
                assertIs<Validation.Valid<CompiledMechanic>>(compile(type, source), type).value
            val roundTrip =
                assertIs<Validation.Valid<CompiledMechanic>>(compile(type, result.canonical), type)
                    .value
            assertEquals(result.canonical, roundTrip.canonical, type)
        }
    }

    @Test
    fun `builtin validation rejects contradictory and inapplicable configuration`() {
        val fixtures =
            listOf(
                "capture" to "area: plate\nduration: 0s",
                "interact" to
                    "targets: [{block: console}]\ndistinct_players: true\nuse_cooldown: 1s",
                "interact" to "targets: [{block: console}, {block: console}]",
                "deliver" to "relic: orb\ndestination: {area: altar}\ntrigger: enter\nhold: 0s",
                "defeat" to "group: guards\ncauses: [death, death]",
                "match_pattern" to
                    "tokens: [sun, moon]\npattern: {choose: [[sun, moon], [moon, sun]]}\nordered: false",
                "match_pattern" to "tokens: [sun, moon]\npattern: {sample: {length: 3}}",
                "match_pattern" to "tokens: [sun]\npattern: [sun]\ncompletion: all",
                "match_pattern" to "tokens: [sun]\npattern: [sun]\nuse_cooldown: 1s",
            )
        fixtures.forEach { (type, text) ->
            assertIs<Validation.Invalid>(compile(type, text), "$type: $text")
        }
    }

    @Test
    fun `selectors preserve contextual defaults namespaces and nonempty collection truth`() {
        val selection =
            PlayerSelectionSchema.decode(
                yaml(
                    "from: online_raiders\nstate: any\nwhere:\n  and:\n    - has_aura: charged\n    - not: {in_area: prison}"
                ),
                SchemaContext("raid"),
            )
        assertEquals(null, selection.participation)
        val predicate = assertIs<PlayerPredicate.And>(selection.where)
        assertEquals(
            DefinitionId("raid", "charged"),
            assertIs<PlayerPredicate.HasAura>(predicate.children.first()).aura,
        )
        assertEquals(
            PlayerSelectionSchema.encode(selection),
            PlayerSelectionSchema.encode(
                PlayerSelectionSchema.decode(yaml(PlayerSelectionSchema.encode(selection)))
            ),
        )
        assertIs<Validation.Invalid>(
            PlayerSelectionSchema.validate(yaml("from: online_players\nonline: false"))
        )
        assertIs<Validation.Invalid>(PlayerSelectionSchema.validate(yaml("where: {and: []}")))
        assertFalse(allSelected(emptyList(), PlayerPredicate.Always))
        assertFalse(anySelected(emptyList(), PlayerPredicate.Always))
    }

    @Test
    fun `editor descriptions resolve recursive predicates from root definitions`() {
        val source = yaml(BuiltinMechanics.capture.description.json())
        assertTrue("\$defs" in source.entries)
        val definitions = source.entries.getValue("\$defs").mapping()
        assertTrue("player_predicate" in definitions.entries)
        assertEquals(
            setOf("area", "duration"),
            source.entries.getValue("required").sequence().map { it.text() }.toSet(),
        )
    }
}
