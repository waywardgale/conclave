package dev.conclave.core

import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class ManifestTest {
    @Test
    fun `scope and phase declarations reject excessive expansion before graph traversal`() {
        val objectives =
            (0..256).joinToString("\n") {
                "        - {id: item_$it, type: capture, area: plate, duration: 1s}"
            }
        val source =
            "schema: 1\nencounter:\n  id: limit\n  start: run\n  phases:\n    - id: run\n      objectives:\n$objectives\n      success: {complete: true}"
        assertEquals(
            "scope_entry_limit",
            diagnostic(CatalogCompiler().compile(listOf(SourceDocument("large.yaml", source))))
                .code,
        )
        val phases =
            (0..256).joinToString("\n") {
                "    - {id: phase_$it, duration: 1s, success: {complete: true}}"
            }
        assertEquals(
            "phase_limit",
            diagnostic(
                    CatalogCompiler()
                        .compile(
                            listOf(
                                SourceDocument(
                                    "phases.yaml",
                                    "schema: 1\nencounter:\n  id: limit\n  start: phase_0\n  phases:\n$phases",
                                )
                            )
                        )
                )
                .code,
        )
    }

    @ParameterizedTest
    @CsvSource("1ms,1", "50ms,1", "51ms,2", "1.001s,21", "1.5m,1800", "2h,144000")
    fun `durations never expire before authored time`(input: String, ticks: Long) {
        assertEquals(ticks, SimulationDuration.parse(input).ticks)
    }

    @ParameterizedTest
    @ValueSource(
        strings =
            ["0s", "-1s", "10", "1m 30s", "1e5s", ".5s", "Infinity", "NaNs", "9223372036854775807h"]
    )
    fun `invalid durations fail without clamping`(input: String) {
        assertFailsWith<IllegalArgumentException> { SimulationDuration.parse(input) }
    }

    @Test
    fun `zero requires a field that explicitly permits it`() {
        assertEquals(0L, SimulationDuration.parse("0s", allowZero = true).ticks)
    }

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "a: 1\na: 2",
                "a: &saved 1",
                "a: *saved",
                "a: !!java.lang.ProcessBuilder []",
                "a: !custom value",
                "a: {<<: {x: 1}}",
                "a: {!!merge '<<': {x: 1}}",
                "a: !!bool not_a_boolean",
                "---\na: 1\n---\na: 2",
                "a: .nan",
                "a: .inf",
                "a: 1e999999999",
                "? [a,b]\n: value",
                "a: [unterminated",
                "[not, a, mapping]",
            ]
    )
    fun `unsupported or unsafe yaml is rejected as a diagnostic`(text: String) {
        val problem = diagnostic(YamlDocumentReader().read(SourceDocument("input.yaml", text)))
        assertEquals("input.yaml", problem.source.file)
        assertTrue(problem.source.line > 0)
        assertTrue(problem.source.column > 0)
    }

    @Test
    fun `parser limits apply before composition`() {
        val depth = YamlDocumentReader(ContentLimits(nesting = 4))
        assertEquals(
            "nesting_limit",
            diagnostic(
                    depth.read(
                        SourceDocument("deep", "a: " + "[".repeat(100) + "1" + "]".repeat(100))
                    )
                )
                .code,
        )
        val size = YamlDocumentReader(ContentLimits(documentBytes = 20))
        assertEquals(
            "document_limit",
            diagnostic(size.read(SourceDocument("large", "a: " + "界".repeat(7)))).code,
        )
        val nodes = YamlDocumentReader(ContentLimits(nodesPerDocument = 5))
        assertEquals(
            "node_limit",
            diagnostic(nodes.read(SourceDocument("many", "a: [1, 2, 3, 4]"))).code,
        )
    }

    @Test
    fun `syntax errors do not echo private source contents`() {
        val problem =
            diagnostic(
                YamlDocumentReader().read(SourceDocument("private.yaml", "a: [private_secret"))
            )
        assertFalse(problem.message.contains("private_secret"))
    }

    @Test
    fun `unknown fields report exact source and path`() {
        val source = timedManifest().replace("duration: 1s", "duration: 1s\n      duraton: 2s")
        val problem =
            diagnostic(CatalogCompiler().compile(listOf(SourceDocument("typo.yaml", source))))
        assertEquals("unknown_field", problem.code)
        assertEquals("$.encounter.phases[0].duraton", problem.source.path)
        assertEquals(8, problem.source.line)
        assertEquals(16, problem.source.column)
    }

    @Test
    fun `bad references and any invalid file reject the entire catalog`() {
        val bad = timedManifest("other").replace("complete: true", "next: absent")
        val result =
            CatalogCompiler()
                .compile(
                    listOf(
                        SourceDocument("ok.yaml", timedManifest()),
                        SourceDocument("bad.yaml", bad),
                    )
                )
        assertEquals("unknown_phase", diagnostic(result).code)
        assertEquals("$.encounter.phases[0].success.next", diagnostic(result).source.path)
    }

    @Test
    fun `duplicate identities and duplicate input paths are errors`() {
        val compiler = CatalogCompiler()
        assertEquals(
            "duplicate_definition",
            diagnostic(
                    compiler.compile(
                        listOf(
                            SourceDocument("a", timedManifest()),
                            SourceDocument("b", timedManifest()),
                        )
                    )
                )
                .code,
        )
        assertEquals(
            "duplicate_file",
            diagnostic(
                    compiler.compile(
                        listOf(
                            SourceDocument("a", timedManifest()),
                            SourceDocument("a", timedManifest("second")),
                        )
                    )
                )
                .code,
        )
    }

    @Test
    fun `accepted features awaiting implementation fail explicitly`() {
        val source = timedManifest().replace("start: waiting", "start: waiting\n  roles: []")
        assertEquals(
            "capability_unavailable",
            diagnostic(CatalogCompiler().compile(listOf(SourceDocument("future", source)))).code,
        )
        val untimed = timedManifest().replace("      duration: 1s\n", "")
        assertEquals(
            "phase_completion",
            diagnostic(CatalogCompiler().compile(listOf(SourceDocument("untimed", untimed)))).code,
        )
    }

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "{complete: false}",
                "{wipe: true}",
                "{next: waiting, complete: true}",
                "{complete: 'true'}",
            ]
    )
    fun `success routing is explicit and typed`(route: String) {
        val source = timedManifest().replace("{complete: true}", route)
        assertEquals(
            "phase_route",
            diagnostic(CatalogCompiler().compile(listOf(SourceDocument("route", source)))).code,
        )
    }

    @Test
    fun `revision identity ignores formatting filenames defaults and equivalent units`() {
        val other =
            """
            # Same effective definition, moved and reformatted.
            encounter:
              phases: [{success: {complete: true}, failure: {wipe: true}, duration: 1000ms, id: waiting}]
              outcome_precedence: success
              start: waiting
              id: test
            namespace: local
            schema: 1
            """
                .trimIndent()
        val compiled =
            assertIs<Validation.Valid<CompiledCatalog>>(
                    CatalogCompiler().compile(listOf(SourceDocument("moved.yaml", other)))
                )
                .value
        assertEquals(catalog().revision, compiled.revision)
        assertNotEquals(catalog().revision, catalog(duration = "2s").revision)
        assertEquals(64, compiled.revision.length)
    }

    @Test
    fun `catalog order is canonical and compiled maps cannot be mutated`() {
        val sources =
            listOf(
                SourceDocument("a", timedManifest("first")),
                SourceDocument("b", timedManifest("second")),
            )
        val first =
            assertIs<Validation.Valid<CompiledCatalog>>(CatalogCompiler().compile(sources)).value
        val second =
            assertIs<Validation.Valid<CompiledCatalog>>(
                    CatalogCompiler().compile(sources.reversed())
                )
                .value
        assertEquals(first.revision, second.revision)
        assertFailsWith<UnsupportedOperationException> { (first.encounters as MutableMap).clear() }
        assertFailsWith<UnsupportedOperationException> {
            (first.encounters.values.first().phases as MutableList).clear()
        }
    }
}
