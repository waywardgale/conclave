package dev.conclave.core

import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test

private class EncounterWorld : AttemptWorld {
    val player = UUID.randomUUID()
    var inArea = true

    override fun players() =
        PlayerFrame(
            listOf(
                PlayerObservation(
                    player,
                    true,
                    LifeState.ALIVE,
                    Participation.ACTIVE,
                    false,
                    areas = if (inArea) setOf("plate") else emptySet(),
                )
            ),
            setOf(player),
        )

    override fun validTarget(
        scope: RuntimeScopeIdentity,
        player: UUID,
        target: TargetHandle,
        maximumReach: Double?,
    ) = true

    override fun group(scope: RuntimeScopeIdentity, id: String): GroupObservation? = null

    override fun relic(scope: RuntimeScopeIdentity, id: String): RelicObservation? = null

    override fun selectedRelic(player: UUID): UUID? = null

    override fun deliver(scope: RuntimeScopeIdentity, relic: UUID, generation: Long, holder: UUID) =
        false
}

class AttemptEngineTest {
    private fun compile(phases: String, root: String = ""): CompiledCatalog {
        val source =
            "schema: 1\nencounter:\n  id: test\n  start: first\n" +
                (if (root.isNotEmpty()) root.prependIndent("  ") + "\n" else "") +
                "  phases:\n" +
                phases.prependIndent("    ")
        val result = CatalogCompiler().compile(listOf(SourceDocument("encounter.yaml", source)))
        return assertIs<Validation.Valid<CompiledCatalog>>(result, result.toString()).value
    }

    private fun engine(
        catalog: CompiledCatalog,
        world: EncounterWorld = EncounterWorld(),
    ): AttemptEngine =
        AttemptEngine(
                UUID.randomUUID(),
                catalog.revision,
                catalog.encounters.getValue(DefinitionId("local", "test")),
                world,
            )
            .also { it.start() }

    @Test
    fun `conditional route is frozen before surviving encounter listeners change state`() {
        val catalog =
            compile(
                """
                - id: first
                  duration: 50ms
                  success:
                    choose:
                      - if: {counter: {id: cycles, scope: encounter, at_least: 1}}
                        complete: true
                    otherwise: {next: first}
                """
                    .trimIndent(),
                """
                counters: [{id: cycles}]
                rules:
                  - id: count_cycles
                    on: {source: {phase: first}, event: completed}
                    do: [{add_counter: {counter: cycles, value: 1}}]
                """
                    .trimIndent(),
            )
        val runtime = engine(catalog)
        runtime.advance()
        assertEquals(ProgressionState.Transition("first"), runtime.state)
        runtime.advance()
        assertIs<ProgressionState.Running>(runtime.state)
        runtime.advance()
        assertIs<ProgressionState.Finishing>(runtime.state)
        val ended =
            runtime
                .drainEvents()
                .filterIsInstance<AttemptEvent.Progression>()
                .map { it.effect }
                .filterIsInstance<ProgressionEffect.PhaseEnded>()
        assertEquals(PhaseRoute.Next("first"), ended.first().route)
        assertEquals(PhaseRoute.Complete, ended.last().route)
    }

    @Test
    fun `final phase does not run ordinary encounter listeners after encounter closure`() {
        val catalog =
            compile(
                """
                - id: first
                  duration: 50ms
                  success: {complete: true}
                """
                    .trimIndent(),
                """
                counters: [{id: protected, max: 0}]
                rules:
                  - id: should_not_run
                    on: {source: {phase: first}, event: completed}
                    do: [{add_counter: {counter: protected, value: 1}}]
                """
                    .trimIndent(),
            )
        val runtime = engine(catalog)
        runtime.advance()
        assertIs<ProgressionState.Finishing>(runtime.state)
        assertTrue(runtime.drainEvents().none { it is AttemptEvent.Fault })
    }

    @Test
    fun `startup actions and child-result reactions affect parent outcome in the same tick`() {
        val catalog =
            compile(
                """
                - id: first
                  counters: [{id: captures}]
                  objectives:
                    - id: plate
                      type: capture
                      area: plate
                      duration: 50ms
                  complete_when: {counter: {id: captures, equals: 3}}
                  rules:
                    - id: setup
                      on: {source: self, event: started}
                      do: [{set_counter: {counter: captures, value: 2}}]
                    - id: count_capture
                      on: {source: {mechanic: plate}, event: completed}
                      if: {event_value: {field: elapsed, equals: 50ms}}
                      do: [{add_counter: {counter: captures, value: 1}}]
                  success: {complete: true}
                """
                    .trimIndent()
            )
        val runtime = engine(catalog)
        runtime.advance()
        assertIs<ProgressionState.Finishing>(runtime.state)
    }

    @Test
    fun `ordinary input can stop due expiry before timed work runs`() {
        val catalog =
            compile(
                """
                - id: first
                  timers: [{id: danger, duration: 100ms}]
                  mechanics:
                    - id: console
                      type: interact
                      targets: [{block: button}]
                  complete_when: {timer: {id: danger, state: idle}}
                  fail_when: {timer: {id: danger, state: expired}}
                  rules:
                    - id: prevent_expiry
                      on: {source: {mechanic: console}, event: used}
                      do: [{stop_timer: {timer: danger}}]
                  success: {complete: true}
                """
                    .trimIndent()
            )
        val world = EncounterWorld()
        val runtime = engine(catalog, world)
        runtime.advance()
        runtime.advance(
            listOf(
                MechanicSubmission(
                    runtime.mechanics().single(),
                    MechanicInput.Press(
                        world.player,
                        UUID.randomUUID(),
                        TargetHandle(
                            TargetReference(TargetKind.BLOCK, "button"),
                            UUID.randomUUID(),
                        ),
                    ),
                )
            )
        )
        assertIs<ProgressionState.Finishing>(runtime.state)
        assertTrue(runtime.drainEvents().none { it is AttemptEvent.TimerExpired })
    }

    @Test
    fun `false guards do not consume once limits and event integers keep their type`() {
        val catalog =
            compile(
                """
                - id: first
                  counters: [{id: credits}]
                  mechanics:
                    - id: console
                      type: interact
                      targets: [{block: button}]
                      uses: 10
                  complete_when: {counter: {id: credits, equals: 2}}
                  rules:
                    - id: credit_second_use
                      on: {source: {mechanic: console}, event: used}
                      if: {event_value: {field: uses_after, at_least: 2}}
                      once: true
                      per_player: true
                      do: [{add_counter: {counter: credits, value: {event: uses_after}}}]
                  success: {complete: true}
                """
                    .trimIndent()
            )
        val world = EncounterWorld()
        val runtime = engine(catalog, world)
        val target = TargetHandle(TargetReference(TargetKind.BLOCK, "button"), UUID.randomUUID())
        repeat(2) {
            runtime.advance(
                listOf(
                    MechanicSubmission(
                        runtime.mechanics().single(),
                        MechanicInput.Press(world.player, UUID.randomUUID(), target),
                    )
                )
            )
        }
        assertIs<ProgressionState.Finishing>(runtime.state)
    }

    @Test
    fun `required rule errors end technically instead of following gameplay failure routes`() {
        val catalog =
            compile(
                """
                - id: first
                  duration: 50ms
                  counters: [{id: count, initial: 1, max: 1}]
                  rules:
                    - id: invalid_addition
                      on: {source: self, event: started}
                      do: [{add_counter: {counter: count, value: 1}}]
                  success: {complete: true}
                  failure: {next: first}
                """
                    .trimIndent()
            )
        val runtime = engine(catalog)
        assertEquals(ProgressionState.Ended(AttemptResult.TECHNICAL_ERROR), runtime.state)
        runtime.advance()
        assertTrue(
            runtime.drainEvents().filterIsInstance<AttemptEvent.Progression>().none {
                it.effect is ProgressionEffect.CommitCompletion
            }
        )
    }

    @Test
    fun `yaml capture progresses through untimed phases and commits success only after durable acknowledgment`() {
        val catalog =
            compile(
                """
                - id: first
                  objectives:
                    - id: capture_plate
                      type: capture
                      area: plate
                      duration: 100ms
                  success: {next: second}
                - id: second
                  duration: 100ms
                  success: {complete: true}
                """
                    .trimIndent()
            )
        val runtime = engine(catalog)
        runtime.drainEvents()
        runtime.advance()
        assertIs<ProgressionState.Running>(runtime.state)
        runtime.advance()
        assertEquals(ProgressionState.Transition("second"), runtime.state)
        assertTrue(
            runtime.drainEvents().filterIsInstance<AttemptEvent.MechanicChanged>().any {
                it.notice is MechanicNotice.Result
            }
        )
        runtime.advance()
        assertEquals("second", assertIs<ProgressionState.Running>(runtime.state).activation.phase)
        runtime.advance()
        assertIs<ProgressionState.Running>(runtime.state)
        runtime.advance()
        val finishing = assertIs<ProgressionState.Finishing>(runtime.state)
        assertFalse(
            runtime.drainEvents().filterIsInstance<AttemptEvent.Progression>().any {
                it.effect is ProgressionEffect.Ended
            }
        )
        runtime.advance()
        assertEquals(5, runtime.tick)
        assertTrue(runtime.acknowledge(finishing.operation, CommitStatus.COMMITTED))
        assertEquals(ProgressionState.Ended(AttemptResult.SUCCESS), runtime.state)
    }

    @Test
    fun `phase reentry resets owned mechanics and rejects input for the earlier activation`() {
        val catalog =
            compile(
                """
                - id: first
                  objectives:
                    - id: console
                      type: interact
                      targets: [{block: button}]
                  success: {next: first}
                """
                    .trimIndent()
            )
        val world = EncounterWorld()
        val runtime = engine(catalog, world)
        val original = runtime.mechanics().single()
        val input =
            MechanicInput.Press(
                world.player,
                UUID.randomUUID(),
                TargetHandle(TargetReference(TargetKind.BLOCK, "button"), UUID.randomUUID()),
            )
        runtime.advance(listOf(MechanicSubmission(original, input)))
        assertIs<ProgressionState.Transition>(runtime.state)
        runtime.advance(
            listOf(MechanicSubmission(original, input.copy(gesture = UUID.randomUUID())))
        )
        assertIs<ProgressionState.Running>(runtime.state)
        assertNotEquals(original, runtime.mechanics().single())
    }

    @Test
    fun `condition objectives are live by default while explicit latches retain earlier satisfaction`() {
        fun test(latch: Boolean): ProgressionState {
            val catalog =
                compile(
                    """
                - id: first
                  timers: [{id: wait, duration: 150ms}]
                  objectives:
                    - id: occupy
                      latch: $latch
                      condition: {count: {players: {area: plate}, at_least: 1}}
                    - id: elapsed
                      condition: {timer: {id: wait, state: expired}}
                  success: {complete: true}
            """
                        .trimIndent()
                )
            val world = EncounterWorld()
            val runtime = engine(catalog, world)
            runtime.advance()
            world.inArea = false
            runtime.advance()
            runtime.advance()
            return runtime.state
        }
        assertIs<ProgressionState.Running>(test(false))
        assertIs<ProgressionState.Finishing>(test(true))
    }

    @Test
    fun `unknown state and recursive objective dependencies reject publication`() {
        val phases =
            """
            - id: first
              objectives:
                - id: a
                  condition: {satisfied: {objective: b}}
                - id: b
                  condition: {satisfied: {objective: a}}
              success: {complete: true}
            """
                .trimIndent()
        val source =
            "schema: 1\nencounter:\n  id: test\n  start: first\n  phases:\n" +
                phases.prependIndent("    ")
        assertEquals(
            "objective_cycle",
            diagnostic(CatalogCompiler().compile(listOf(SourceDocument("cycle.yaml", source))))
                .code,
        )
        assertEquals(
            "unknown_objective",
            diagnostic(
                    CatalogCompiler()
                        .compile(
                            listOf(
                                SourceDocument(
                                    "missing.yaml",
                                    source.replace("objective: b", "objective: missing"),
                                )
                            )
                        )
                )
                .code,
        )
    }

    @Test
    fun `timer pause preserves time and restarting rejects old scheduled work`() {
        var order = 0L
        val state =
            ScopeState(
                listOf(CounterDefinition("cycles", initial = 2, minimum = 0, maximum = 5)),
                listOf(TimerDefinition("pulse", SimulationDuration(3))),
            ) {
                ++order
            }
        state.mutate("cycles", CounterMutation.ADD, 2)
        assertEquals(4, state.counter("cycles"))
        assertFailsWith<IllegalStateException> { state.mutate("cycles", CounterMutation.ADD, 3) }
        assertEquals(4, state.counter("cycles"))
        state.mutate("pulse", TimerMutation.PAUSE, 1)
        assertTrue(state.due(100).isEmpty())
        state.mutate("pulse", TimerMutation.RESUME, 100)
        assertTrue(state.due(101).isEmpty())
        val due = state.due(102).single()
        state.mutate("pulse", TimerMutation.RESTART, 102)
        assertNull(state.expire(due, 102))
        assertEquals(3, state.timers(102).getValue("pulse").remaining.ticks)
        assertNotNull(state.expire(state.due(105).single(), 105))
        assertTrue(state.due(106).isEmpty())
    }
}
