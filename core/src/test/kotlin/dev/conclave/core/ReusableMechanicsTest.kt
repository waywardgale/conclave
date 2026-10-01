package dev.conclave.core

import java.util.Random
import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test

class ReusableMechanicsTest {
    private fun definition(body: String, id: String = "plate", namespace: String = "tools") =
        SourceDocument(
            "$namespace/$id.yaml",
            "schema: 1\nnamespace: $namespace\nmechanic:\n  id: $id\n" +
                body.trimIndent().prependIndent("  "),
        )

    private fun encounter(occurrences: String, namespace: String = "raid") =
        SourceDocument(
            "$namespace/encounter.yaml",
            "schema: 1\nnamespace: $namespace\nencounter:\n  id: test\n  start: run\n  phases:\n    - id: run\n      success: {complete: true}\n      objectives:\n" +
                occurrences.trimIndent().prependIndent("        "),
        )

    private val plate =
        definition(
            """
        name: Capture plate
        parameters:
          area: {type: area}
          duration: {type: duration, default: 100ms, min: 50ms, max: 1m}
          count: {type: integer, default: 1, min: 1, max: 6}
        type: capture
        area: {parameter: area}
        duration: {parameter: duration}
        players_required: {parameter: count}
    """
        )

    private fun compile(vararg docs: SourceDocument): CompiledCatalog {
        val result = CatalogCompiler().compile(docs.toList())
        return assertIs<Validation.Valid<CompiledCatalog>>(result, result.toString()).value
    }

    private fun occurrence(catalog: CompiledCatalog, index: Int = 0) =
        catalog.encounters.values.single().phases.single().content.allMechanics[index]

    @Test
    fun `required defaults and independent occurrences compile regardless of file order`() {
        val use =
            encounter(
                """
            - id: north
              use: tools:plate
              with: {area: north}
            - id: south
              name: South plate
              use: tools:plate
              with: {area: south, duration: 500ms, count: 2}
        """
            )
        val catalog = compile(use, plate)
        assertEquals(catalog.revision, compile(plate, use).revision)
        assertEquals("Capture plate", occurrence(catalog).name)
        assertEquals("South plate", occurrence(catalog, 1).name)
        assertEquals(
            setOf("north", "south"),
            catalog.encounters.values.single().spatialReferences().areas,
        )
        assertContains(occurrence(catalog).mechanic.canonical, "\"duration\":\"0.1s\"")
        val world = ReuseWorld()
        val north = occurrence(catalog).mechanic.create(world)
        val south = occurrence(catalog, 1).mechanic.create(world)
        north.start()
        south.start()
        repeat(2) {
            world.tick++
            north.tick()
            south.tick()
        }
        assertEquals(MechanicState.SUCCEEDED, north.state)
        assertEquals(MechanicState.RUNNING, south.state)
        south.cancel()
        assertEquals(MechanicState.SUCCEEDED, north.state)
    }

    @Test
    fun `binding reports caller argument and consuming body without weakening field bounds`() {
        val bad = plate.copy(text = plate.text.replace("min: 1, max: 6", "min: 0, max: 6"))
        val use = encounter("- id: north\n  use: tools:plate\n  with: {area: north, count: 0}")
        val problem = diagnostic(CatalogCompiler().compile(listOf(bad, use)))
        assertEquals("number_range", problem.code)
        assertEquals(use.file, problem.source.file)
        assertContains(problem.message, plate.file)
        assertContains(problem.message, use.file)
    }

    @Test
    fun `reference kinds cannot be interchanged even when their scalar spelling matches`() {
        for (type in listOf("location", "group", "string", "enum")) {
            val invalid =
                plate.copy(
                    text =
                        plate.text.replace(
                            "area: {type: area}",
                            "area: {type: $type${if (type == "enum") ", values: [north]" else ""}}",
                        )
                )
            assertEquals(
                "parameter_type",
                diagnostic(CatalogCompiler().compile(listOf(invalid))).code,
                type,
            )
        }
        val unresolved =
            plate.copy(text = plate.text.replace("{parameter: area}", "{parameter: missing}"))
        assertEquals(
            "unknown_parameter",
            diagnostic(CatalogCompiler().compile(listOf(unresolved))).code,
        )
    }

    @Test
    fun `unsupported constraints defaults and unsafe arguments fail before use`() {
        val replacements =
            listOf(
                "type: area" to "type: area, min: 1",
                "type: area" to "type: any",
                "default: 100ms" to "default: 0s",
                "min: 1, max: 6" to "min: 7, max: 6",
                "type: area" to "type: area, default: {parameter: duration}",
                "type: integer, default: 1" to "type: integer, default: 1.0",
            )
        for ((old, replacement) in replacements) assertIs<Validation.Invalid>(
            CatalogCompiler()
                .compile(listOf(plate.copy(text = plate.text.replace(old, replacement)))),
            replacement,
        )
        for (body in
            listOf(
                "use: tools:plate",
                "use: tools:plate\nwith: {area: north, typo: 1}",
                "use: tools:plate\nwith: {area: north, count: 7}",
                "use: tools:plate\nwith: {area: north, duration: 4}",
                "use: tools:plate\nwith: {area: {parameter: external}}",
                "use: tools:plate\narea: north",
                "use: tools:plate\ntype: capture\nwith: {area: north}",
            )) assertIs<Validation.Invalid>(
            CatalogCompiler()
                .compile(listOf(plate, encounter("- id: bad\n" + body.prependIndent("  ")))),
            body,
        )
    }

    @Test
    fun `forwarded selections retain caller and default namespaces at every leaf`() {
        val inner =
            definition(
                """
            parameters:
              players: {type: players, default: {aura: default_mark}}
            type: capture
            area: north
            duration: 100ms
            players: {parameter: players}
        """,
                "selected",
                "inner",
            )
        val outer =
            definition(
                """
            parameters:
              players: {type: players, default: {aura: outer_mark}}
            use: inner:selected
            with: {players: {parameter: players}}
        """,
                "selected",
                "outer",
            )
        val use =
            encounter(
                """
            - id: caller
              use: outer:selected
              with: {players: {aura: caller_mark, where: {has_aura: another_mark}}}
            - id: outer_default
              use: outer:selected
            - id: inner_default
              use: inner:selected
        """
            )
        val catalog = compile(use, outer, inner)
        assertContains(occurrence(catalog).mechanic.canonical, "raid:caller_mark")
        assertContains(occurrence(catalog).mechanic.canonical, "raid:another_mark")
        assertContains(occurrence(catalog, 1).mechanic.canonical, "outer:outer_mark")
        assertContains(occurrence(catalog, 2).mechanic.canonical, "inner:default_mark")
        assertContains(
            catalog.reusableMechanics
                .getValue(DefinitionId("inner", "selected"))
                .parameters
                .getValue("players")
                .description
                .defaultJson!!,
            "inner:default_mark",
        )
    }

    @Test
    fun `nested use checks every argument constraint and never resolves names by file order`() {
        val alias =
            definition(
                """
            parameters:
              area: {type: area}
              duration: {type: duration, default: 2m}
            use: tools:plate
            with: {area: {parameter: area}, duration: {parameter: duration}}
        """,
                "alias",
                "wrappers",
            )
        val use = encounter("- id: bad\n  use: wrappers:alias\n  with: {area: north}")
        assertEquals(
            "parameter_bounds",
            diagnostic(CatalogCompiler().compile(listOf(alias, plate, use))).code,
        )
        val unqualified = use.copy(text = use.text.replace("wrappers:alias", "alias"))
        assertEquals(
            "unknown_reusable_mechanic",
            diagnostic(CatalogCompiler().compile(listOf(alias, plate, unqualified))).code,
        )
    }

    @Test
    fun `unused cycles unknown fields and missing definitions are rejected`() {
        val a = definition("use: tools:b", "a")
        val b = definition("use: tools:a", "b")
        assertEquals("mechanic_cycle", diagnostic(CatalogCompiler().compile(listOf(a, b))).code)
        assertEquals(
            "unknown_reusable_mechanic",
            diagnostic(CatalogCompiler().compile(listOf(a))).code,
        )
        assertEquals(
            "unknown_field",
            diagnostic(
                    CatalogCompiler().compile(listOf(plate.copy(text = plate.text + "\n  typo: 1")))
                )
                .code,
        )
        val tooDeep =
            (0..33).map { index ->
                definition(
                    if (index == 33) "type: capture\narea: north\nduration: 1s"
                    else "use: tools:item_${index + 1}",
                    "item_$index",
                )
            }
        assertEquals("reuse_depth", diagnostic(CatalogCompiler().compile(tooDeep)).code)
        assertEquals("reuse_depth", diagnostic(CatalogCompiler().compile(tooDeep.reversed())).code)
    }

    @Test
    fun `stored uninstantiated required templates affect identity without including filenames`() {
        val initial = compile(plate)
        assertTrue(initial.reusableMechanics.values.single().parameters.getValue("area").required)
        assertNotEquals(
            initial.revision,
            compile(plate.copy(text = plate.text.replace("100ms", "200ms"))).revision,
        )
        assertEquals(
            initial.revision,
            compile(plate.copy(file = "renamed.yaml", text = "# comment\n" + plate.text)).revision,
        )
        assertEquals(
            "area",
            initial.reusableMechanics.values
                .single()
                .parameters
                .getValue("area")
                .description
                .parameterType,
        )
    }

    @Test
    fun `pattern constructors lists and scalar entries bind as complete typed values`() {
        val pattern =
            definition(
                """
            parameters:
              tokens: {type: pattern_tokens, min_items: 2, max_items: 3}
              answer: {type: pattern, min_length: 2, max_length: 2}
              controls: {type: pattern_inputs}
            type: match_pattern
            tokens: {parameter: tokens}
            pattern: {parameter: answer}
            inputs: {parameter: controls}
        """,
                "pattern",
            )
        val use =
            encounter(
                """
            - id: puzzle
              use: tools:pattern
              with:
                tokens: [sun, moon]
                answer: {choose: [[sun, moon], [moon, sun]]}
                controls:
                  - token: sun
                    targets: [{block: left}]
                  - token: moon
                    targets: [{block: right}]
        """
            )
        val catalog = compile(use, pattern)
        assertEquals(
            setOf("left", "right"),
            catalog.encounters.values.single().spatialReferences().locations,
        )
        assertIs<Validation.Invalid>(
            CatalogCompiler()
                .compile(
                    listOf(pattern, use.copy(text = use.text.replace("[moon, sun]", "[moon]")))
                )
        )
        assertIs<Validation.Invalid>(
            CatalogCompiler()
                .compile(
                    listOf(
                        pattern,
                        use.copy(
                            text = use.text.replace("tokens: [sun, moon]", "tokens: [sun, sun]")
                        ),
                    )
                )
        )
        val leaves =
            definition(
                """
            parameters:
              first: {type: enum, values: [sun, moon]}
              length: {type: integer, min: 1, max: 2}
            type: match_pattern
            tokens: [{parameter: first}, star]
            pattern: {sample: {length: {parameter: length}}}
        """,
                "leaves",
            )
        compile(
            leaves,
            encounter("- id: puzzle\n  use: tools:leaves\n  with: {first: sun, length: 2}"),
        )
    }

    @Test
    fun `scalar defaults validate in their own units and text stays literal`() {
        val params =
            """
            enabled: {type: boolean, default: true}
            distance: {type: number, default: 1.5, min: 0, max: 2}
            health: {type: percentage, default: 100%, min: 50%, max: 200%}
            text: {type: string, default: '{event: player}', max_length: 40}
            """
                .trimIndent()
        val source =
            definition(
                "parameters:\n" +
                    params.prependIndent("  ") +
                    "\ntype: capture\narea: north\nduration: 1s"
            )
        val catalog = compile(source)
        assertEquals(
            "\"{event: player}\"",
            catalog.reusableMechanics.values
                .single()
                .parameters
                .getValue("text")
                .description
                .defaultJson,
        )
        assertIs<Validation.Invalid>(
            CatalogCompiler()
                .compile(
                    listOf(source.copy(text = source.text.replace("default: 100%", "default: 100")))
                )
        )
    }

    @Test
    fun `expansion capacity counts repeated whole values and resets for the next compile`() {
        val tokens = (0..1023).joinToString(", ") { "token_$it" }
        val pattern =
            definition(
                "parameters:\n  vocabulary: {type: pattern_tokens, default: [$tokens]}\ntype: match_pattern\ntokens: {parameter: vocabulary}\npattern: [token_0]",
                "large",
            )
        val occurrences = (0..149).joinToString("\n") { "- id: use_$it\n  use: tools:large" }
        val compiler = CatalogCompiler()
        assertEquals(
            "expansion_limit",
            diagnostic(
                    compiler.compile(
                        listOf(
                            pattern,
                            encounter(occurrences, "first"),
                            encounter(occurrences, "second"),
                        )
                    )
                )
                .code,
        )
        assertIs<Validation.Valid<CompiledCatalog>>(
            compiler.compile(listOf(pattern, encounter("- id: single\n  use: tools:large")))
        )
    }

    private class ReuseWorld : MechanicContext {
        override val identity = MechanicIdentity(UUID.randomUUID(), 1, "test")
        override var tick = 0L
        private val player = UUID.randomUUID()

        override fun players() =
            PlayerFrame(
                listOf(
                    PlayerObservation(
                        player,
                        true,
                        LifeState.ALIVE,
                        Participation.ACTIVE,
                        false,
                        areas = setOf("north"),
                    )
                ),
                setOf(player),
            )

        override fun notice(value: MechanicNotice) {}

        override fun validTarget(player: UUID, target: TargetHandle, maximumReach: Double?) = true

        override fun group(id: String): GroupObservation? = null

        override fun random(player: UUID?) = Random(1)

        override fun relic(id: String): RelicObservation? = null

        override fun selectedRelic(player: UUID): UUID? = null

        override fun deliver(relic: UUID, generation: Long, holder: UUID) = false
    }
}
