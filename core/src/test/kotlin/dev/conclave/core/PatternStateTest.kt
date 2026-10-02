package dev.conclave.core

import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test

class PatternStateTest {
    private val first = UUID.randomUUID()
    private val second = UUID.randomUUID()
    private val reference = StateReference("puzzle")
    private val players =
        PlayerFrame(
            listOf(first, second).map {
                PlayerObservation(it, true, LifeState.ALIVE, Participation.ACTIVE, false)
            },
            setOf(first, second),
        )
    private val context =
        SchemaContext(
            event =
                EventContract(
                    mapOf("player" to EventField(EventValueKind.PLAYER, required = false))
                )
        )

    private fun query(text: String): Condition =
        ConditionSchema.decode(
            assertIs<Validation.Valid<YamlValue.Mapping>>(
                    YamlDocumentReader().read(SourceDocument("condition.yaml", text))
                )
                .value,
            context,
        )

    private fun frame(shared: PatternRecordState) =
        ConditionFrame(players, patterns = mapOf(reference to PatternObservation(shared)))

    @Test
    fun `percentage comparisons use each answer length and do not round recurring fractions`() {
        val state = frame(PatternRecordState(1, 3, false))
        assertTrue(
            query(
                    "pattern_state: {mechanic: puzzle, progress: {greater_than: 33.3333333333333333333333333333333333333333333333333%}}"
                )
                .test(state)
        )
        assertFalse(
            query(
                    "pattern_state: {mechanic: puzzle, progress: {equals: 33.3333333333333333333333333333333333333333333333333%}}"
                )
                .test(state)
        )
        assertTrue(
            query(
                    "pattern_state: {mechanic: puzzle, progress: {less_than: 33.34%}, completed: false}"
                )
                .test(state)
        )
        assertTrue(query("pattern_state: {mechanic: puzzle, progress: {equals: 1}}").test(state))
        assertFalse(
            query("pattern_state: {mechanic: puzzle, progress: {equals: 1}, completed: true}")
                .test(state)
        )
        assertTrue(
            query("pattern_state: {mechanic: puzzle, progress: {equals: 50%}}")
                .test(frame(PatternRecordState(1, 2, false)))
        )
    }

    @Test
    fun `missing state is never a zero or incomplete record and explicit missing player has no implicit fallback`() {
        val empty = ConditionFrame(players)
        val zero =
            query("pattern_state: {mechanic: puzzle, progress: {equals: 0}, completed: false}")
        assertFalse(zero.test(empty))
        assertTrue(Condition.Not(zero).test(empty))
        val personal = PatternObservation(records = mapOf(first to PatternRecordState(0, 2, false)))
        val frame = ConditionFrame(players, patterns = mapOf(reference to personal))
        val explicit =
            query("pattern_state: {mechanic: puzzle, player: {event: player}, completed: false}")
        assertFalse(explicit.test(frame))
        assertTrue(
            explicit.test(
                frame.withEvent(
                    EventPayload(
                        checkNotNull(context.event),
                        mapOf("player" to EventDatum.Player(first)),
                    )
                )
            )
        )
        assertFalse(
            explicit.test(
                frame.withEvent(
                    EventPayload(
                        checkNotNull(context.event),
                        mapOf("player" to EventDatum.Player(second)),
                    )
                )
            )
        )
        val member =
            query(
                "any: {players: {}, satisfy: {pattern_state: {mechanic: puzzle, player: {event: player}, completed: false}}}"
            )
        assertFalse(member.test(frame))
    }

    @Test
    fun `implicit record subject is per member and aggregate ignores that implicit subject`() {
        val state =
            ConditionFrame(
                players,
                patterns =
                    mapOf(
                        reference to
                            PatternObservation(
                                records =
                                    mapOf(
                                        first to PatternRecordState(2, 2, true),
                                        second to PatternRecordState(1, 2, false),
                                    )
                            )
                    ),
            )
        assertFalse(
            query(
                    "all: {players: {}, satisfy: {pattern_state: {mechanic: puzzle, completed: true}}}"
                )
                .test(state)
        )
        assertTrue(
            query(
                    "any: {players: {}, satisfy: {pattern_state: {mechanic: puzzle, completed: true}}}"
                )
                .test(state)
        )
        assertTrue(
            query(
                    "all: {players: {}, satisfy: {pattern_state: {mechanic: puzzle, completed_players: {equals: 1}}}}"
                )
                .test(state)
        )
        assertTrue(
            query(
                    "all: {players: {}, satisfy: {pattern_state: {mechanic: puzzle, player: {event: player}, completed: true}}}"
                )
                .test(
                    state.withEvent(
                        EventPayload(
                            checkNotNull(context.event),
                            mapOf("player" to EventDatum.Player(first)),
                        )
                    )
                )
        )
    }

    private fun document(
        condition: String,
        personal: Boolean = false,
        sourceEvent: String = "matched",
    ) =
        SourceDocument(
            "encounter.yaml",
            """
        schema: 1
        encounter:
          id: test
          start: run
          phases:
            - id: run
              objectives:
                - {id: puzzle, type: match_pattern, tokens: [sun], pattern: [sun], ${if (personal) "progress: per_player," else ""} inputs: [{token: sun, targets: [{block: console}]}]}
              counters: [{id: seen}]
              rules:
                - id: inspect
                  on: {source: {mechanic: puzzle}, event: $sourceEvent}
                  if: $condition
                  do: [{add_counter: {counter: seen, value: 1}}]
              success: {complete: true}
    """
                .trimIndent(),
        )

    @Test
    fun `publication rejects invalid check units subjects capabilities and missing event fields`() {
        val compiler = CatalogCompiler()
        val sharedInvalid =
            listOf(
                "{mechanic: puzzle}",
                "{mechanic: puzzle, completed: false, player: {event: player}}",
                "{mechanic: puzzle, completed_players: {equals: 0}}",
                "{mechanic: absent, completed: true}",
                "{mechanic: puzzle, progress: {equals: 1.5}}",
                "{mechanic: puzzle, progress: {equals: -1}}",
                "{mechanic: puzzle, progress: {equals: 101%}}",
                "{mechanic: puzzle, progress: {equals: 1s}}",
                "{mechanic: puzzle, progress: {equals: 1, at_least: 0}}",
            )
        sharedInvalid.forEach {
            assertIs<Validation.Invalid>(
                compiler.compile(listOf(document("{pattern_state: $it}"))),
                it,
            )
        }
        val personalInvalid =
            listOf(
                "{mechanic: puzzle, completed: false}",
                "{mechanic: puzzle, completed: false, player: {event: origin}}",
                "{mechanic: puzzle, completed_players: {equals: 50%}}",
                "{mechanic: puzzle, completed_players: {equals: 1}, progress: {equals: 0}}",
                "{mechanic: puzzle, completed_players: {equals: 1}, player: {event: player}}",
            )
        personalInvalid.forEach {
            assertIs<Validation.Invalid>(
                compiler.compile(listOf(document("{pattern_state: $it}", true))),
                it,
            )
        }
        assertEquals(
            "pattern_target",
            diagnostic(
                    compiler.compile(
                        listOf(
                            document(
                                    "{pattern_state: {mechanic: puzzle, completed: true}}",
                                    sourceEvent = "started",
                                )
                                .let {
                                    it.copy(
                                        text =
                                            it.text.replace(
                                                "type: match_pattern, tokens: [sun], pattern: [sun],  inputs: [{token: sun, targets: [{block: console}]}]",
                                                "type: layers, duration: 1s",
                                            )
                                    )
                                }
                        )
                    )
                )
                .code,
        )
        assertIs<Validation.Invalid>(
            compiler.compile(
                listOf(
                    document(
                        "{any: {players: {where: {pattern_state: {mechanic: puzzle, completed: true}}}, satisfy: {player_state: {online: true}}}}",
                        true,
                    )
                )
            )
        )
        assertIs<Validation.Valid<CompiledCatalog>>(
            compiler.compile(
                listOf(
                    document(
                        "{pattern_state: {mechanic: puzzle, player: {event: player}, completed: false}}",
                        true,
                    )
                )
            )
        )
    }

    @Test
    fun `equivalent percentages share canonical identity and member predicates appear only in their supported schema`() {
        val compiler = CatalogCompiler()
        fun revision(percent: String) =
            assertIs<Validation.Valid<CompiledCatalog>>(
                    compiler.compile(
                        listOf(
                            document(
                                "{pattern_state: {mechanic: puzzle, progress: {equals: $percent}}}"
                            )
                        )
                    )
                )
                .value
                .revision
        assertEquals(revision("50%"), revision("50.000%"))
        assertNotEquals(revision("50%"), revision("25%"))
        assertTrue("pattern_state" in ConditionSchema.description.json())
        assertFalse("pattern_state" in PlayerSelectionSchema.description.json())
    }
}
