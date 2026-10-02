package dev.conclave.core

import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test

class CompositionTest {
    private class World : AttemptWorld {
        val player = UUID.randomUUID()
        var areas = setOf("north", "south")
        val groupId = UUID.randomUUID()
        var groups = emptyMap<String, GroupObservation>()
        var begin: () -> Unit = {}

        override fun beginStep(tick: Long, combat: Boolean) = begin()

        override fun players() =
            PlayerFrame(
                listOf(
                    PlayerObservation(
                        player,
                        true,
                        LifeState.ALIVE,
                        Participation.ACTIVE,
                        false,
                        areas = areas,
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

        override fun group(scope: RuntimeScopeIdentity, id: String) = groups[id]

        override fun relic(scope: RuntimeScopeIdentity, id: String): RelicObservation? = null

        override fun selectedRelic(player: UUID): UUID? = null

        override fun deliver(
            scope: RuntimeScopeIdentity,
            relic: UUID,
            generation: Long,
            holder: UUID,
        ) = false

        fun failed() = GroupObservation(groupId, true, emptyList())

        fun succeeded() =
            GroupObservation(
                groupId,
                true,
                listOf(
                    GroupMember(
                        UUID.randomUUID(),
                        MemberOutcome.DEFEATED,
                        cause = DefeatCause.DEATH,
                    )
                ),
            )
    }

    private fun document(body: String, root: String = "") =
        SourceDocument(
            "encounter.yaml",
            "schema: 1\nencounter:\n  id: test\n  start: run\n" +
                (if (root.isEmpty()) "" else root.trimIndent().prependIndent("  ") + "\n") +
                "  phases:\n    - id: run\n      success: {complete: true}\n" +
                body.trimIndent().prependIndent("      "),
        )

    private fun compile(
        body: String,
        root: String = "",
        definitions: List<SourceDocument> = emptyList(),
    ): CompiledCatalog {
        val result = CatalogCompiler().compile(definitions + document(body, root))
        return assertIs<Validation.Valid<CompiledCatalog>>(result, result.toString()).value
    }

    private fun engine(
        catalog: CompiledCatalog,
        world: World = World(),
        limits: ExecutionLimits = ExecutionLimits(),
    ) =
        AttemptEngine(
                UUID.randomUUID(),
                catalog.revision,
                catalog.encounters.values.single(),
                world,
                limits,
            )
            .also { it.start() }

    private fun results(events: List<AttemptEvent>) =
        events.filterIsInstance<AttemptEvent.MechanicChanged>().mapNotNull {
            (it.notice as? MechanicNotice.Result)?.let { result -> it.identity.id to result.result }
        }

    private fun started(events: List<AttemptEvent>) =
        events.filterIsInstance<AttemptEvent.MechanicStarted>().map { it.identity }

    @Test
    fun `sequence starts parent before children and defers each replacement by a tick`() {
        val runtime =
            engine(
                compile(
                    """
            objectives:
              - id: ordered
                type: sequence
                steps:
                  - {id: first, type: capture, area: north, duration: 50ms}
                  - {id: second, type: capture, area: south, duration: 50ms}
        """
                )
            )
        assertEquals(listOf("ordered", "first"), started(runtime.drainEvents()).map { it.id })
        runtime.advance()
        assertEquals(listOf("first"), results(runtime.drainEvents()).map { it.first })
        assertFalse(runtime.mechanics().any { it.id == "second" })
        runtime.advance()
        assertEquals(listOf("second"), started(runtime.drainEvents()).map { it.id })
        assertIs<ProgressionState.Running>(runtime.state)
        runtime.advance()
        assertEquals(listOf("second", "ordered"), results(runtime.drainEvents()).map { it.first })
        assertIs<ProgressionState.Finishing>(runtime.state)
    }

    @Test
    fun `parallel any aggregates simultaneous success and failure independently of declaration order`() {
        for (reverse in listOf(false, true)) {
            val children =
                listOf(
                        "- {id: lost, type: defeat, group: lost}",
                        "- {id: won, type: defeat, group: won}",
                    )
                    .let { if (reverse) it.reversed() else it }
            val world = World()
            val runtime =
                engine(
                    compile(
                        "objectives:\n  - id: choices\n    type: parallel\n    completion: any\n    steps:\n" +
                            children.joinToString("\n").prependIndent("      ")
                    ),
                    world,
                )
            runtime.drainEvents()
            world.groups = mapOf("lost" to world.failed(), "won" to world.succeeded())
            runtime.advance()
            val observed = results(runtime.drainEvents())
            assertEquals(2, observed.count { it.first in setOf("lost", "won") })
            assertEquals(MechanicState.SUCCEEDED, observed.last().second.state)
            assertEquals("choices", observed.last().first)
            assertIs<ProgressionState.Finishing>(runtime.state)
        }
    }

    @Test
    fun `parallel all fails once and a sequence never starts children after a failure`() {
        for (type in listOf("parallel", "sequence")) {
            val world = World()
            val runtime =
                engine(
                    compile(
                        """
                objectives:
                  - id: composed
                    type: $type
                    steps:
                      - {id: lost, type: defeat, group: lost}
                      - {id: waiting, type: capture, area: absent, duration: 1s}
            """
                    ),
                    world,
                )
            val initial = started(runtime.drainEvents())
            assertEquals(type == "parallel", initial.any { it.id == "waiting" })
            world.groups = mapOf("lost" to world.failed())
            runtime.advance()
            val observed = results(runtime.drainEvents())
            assertEquals(listOf("lost", "composed"), observed.map { it.first })
            assertEquals("child_failed", observed.last().second.reason)
            assertIs<ProgressionState.Ended>(runtime.state)
            assertTrue(runtime.mechanics().isEmpty())
        }
    }

    @Test
    fun `counted repeat uses fresh identities and measures delay after completion`() {
        val runtime =
            engine(
                compile(
                    """
            objectives:
              - id: twice
                type: repeat
                count: 2
                delay: 100ms
                body: {id: plate, type: capture, area: north, duration: 50ms}
        """
                )
            )
        val first = started(runtime.drainEvents()).single { it.id == "plate" }
        runtime.advance()
        assertEquals(listOf("plate"), results(runtime.drainEvents()).map { it.first })
        runtime.advance()
        assertTrue(started(runtime.drainEvents()).isEmpty())
        runtime.advance()
        val second = started(runtime.drainEvents()).single { it.id == "plate" }
        assertNotEquals(first, second)
        runtime.advance(
            listOf(MechanicSubmission(first, MechanicInput.Token("stale", null, UUID.randomUUID())))
        )
        assertEquals(listOf("plate", "twice"), results(runtime.drainEvents()).map { it.first })
        assertIs<ProgressionState.Finishing>(runtime.state)
    }

    @Test
    fun `zero repeat delay still requires a later tick and stopping before startup creates no body`() {
        val runtime =
            engine(
                compile(
                    """
            objectives:
              - id: twice
                type: repeat
                count: 2
                body: {id: plate, type: capture, area: north, duration: 50ms}
        """
                )
            )
        runtime.drainEvents()
        runtime.advance()
        assertTrue(started(runtime.drainEvents()).isEmpty())
        runtime.advance()
        assertEquals(listOf("plate"), started(runtime.drainEvents()).map { it.id })
        runtime.advance()
        assertIs<ProgressionState.Finishing>(runtime.state)
        val stopped =
            engine(
                compile(
                    """
            objectives:
              - id: stopped
                type: repeat
                until: {counter: {id: stop, scope: encounter, equals: 1}}
                body: {id: never, type: capture, area: north, duration: 50ms}
        """,
                    "counters: [{id: stop, initial: 1}]",
                )
            )
        assertEquals(listOf("stopped"), started(stopped.drainEvents()).map { it.id })
        stopped.advance()
        assertIs<ProgressionState.Finishing>(stopped.state)
    }

    @Test
    fun `repeat stop and body failure use encounter precedence`() {
        for (precedence in listOf("success", "failure")) {
            val world = World()
            val runtime =
                engine(
                    compile(
                        """
                objectives:
                  - id: repeating
                    type: repeat
                    until: {count: {players: {area: south}, at_least: 1}}
                    body: {id: defeated, type: defeat, group: lost}
            """,
                        "outcome_precedence: $precedence",
                    ),
                    world.apply { areas = setOf("north") },
                )
            runtime.drainEvents()
            world.areas = setOf("south")
            world.groups = mapOf("lost" to world.failed())
            runtime.advance()
            val parent = results(runtime.drainEvents()).single { it.first == "repeating" }.second
            assertEquals(
                if (precedence == "success") MechanicState.SUCCEEDED else MechanicState.FAILED,
                parent.state,
            )
        }
    }

    @Test
    fun `nested reusable compositions keep child instances separate and preserve namespaces`() {
        val reuse =
            SourceDocument(
                "mechanic.yaml",
                """
                schema: 1
                namespace: tools
                mechanic:
                  id: paired
                  parameters:
                    area: {type: area}
                  type: sequence
                  steps:
                    - id: repeated
                      type: repeat
                      count: 2
                      body: {id: plate, type: capture, area: {parameter: area}, duration: 50ms}
                """
                    .trimIndent(),
            )
        val catalog =
            compile(
                """
            objectives:
              - {id: north, use: tools:paired, with: {area: north}}
              - {id: south, use: tools:paired, with: {area: south}}
        """,
                definitions = listOf(reuse),
            )
        assertEquals(
            setOf("north", "south"),
            catalog.encounters.values.single().spatialReferences().areas,
        )
        val world = World().apply { areas = setOf("north") }
        val runtime = engine(catalog, world)
        val starts = started(runtime.drainEvents())
        assertEquals(2, starts.count { it.id == "plate" })
        assertEquals(starts.size, starts.distinct().size)
        repeat(3) { runtime.advance() }
        assertEquals(
            listOf("plate", "plate", "repeated", "north"),
            results(runtime.drainEvents()).map { it.first },
        )
        assertIs<ProgressionState.Running>(runtime.state)
        world.areas = setOf("south")
        repeat(4) { runtime.advance() }
        assertIs<ProgressionState.Finishing>(runtime.state)
    }

    @Test
    fun `result rules settle before the enclosing phase reads counters and private starts do not impersonate a phase`() {
        val runtime =
            engine(
                compile(
                    """
            objectives:
              - id: composed
                type: parallel
                steps: [{id: plate, type: capture, area: north, duration: 50ms}]
            complete_when: {counter: {id: finished, scope: encounter, equals: 1}}
            rules:
              - id: finish
                on: {source: {mechanic: composed}, event: completed}
                do: [{add_counter: {counter: {id: finished, scope: encounter}, value: 1}}]
        """,
                    """
            counters: [{id: finished}, {id: phase_starts, max: 1}]
            rules:
              - id: one_phase
                on: {source: {phase: run}, event: started}
                do: [{add_counter: {counter: phase_starts, value: 1}}]
        """,
                )
            )
        runtime.advance()
        assertIs<ProgressionState.Finishing>(runtime.state)
        assertTrue(runtime.drainEvents().none { it is AttemptEvent.Fault })
    }

    @Test
    fun `forever background ends with its owner and impossible objectives are rejected`() {
        val body =
            "type: repeat\nforever: true\nbody: {id: plate, type: capture, area: north, duration: 50ms}"
        assertIs<Validation.Invalid>(
            CatalogCompiler()
                .compile(
                    listOf(document("objectives:\n  - id: endless\n" + body.prependIndent("    ")))
                )
        )
        val runtime =
            engine(
                compile(
                    "duration: 100ms\nmechanics:\n  - id: endless\n" + body.prependIndent("    ")
                )
            )
        runtime.drainEvents()
        repeat(2) { runtime.advance() }
        assertIs<ProgressionState.Finishing>(runtime.state)
        val observed = results(runtime.drainEvents())
        assertFalse(observed.any { it.first == "endless" })
        assertTrue(runtime.mechanics().isEmpty())
    }

    @Test
    fun `recursive child references and invalid composition shapes fail during catalog validation`() {
        val cycle =
            SourceDocument(
                "cycle.yaml",
                """
                schema: 1
                mechanic:
                  id: recursive
                  parameters: {area: {type: area}}
                  type: sequence
                  steps: [{id: again, use: recursive, with: {area: {parameter: area}}}]
                """
                    .trimIndent(),
            )
        val invalid = assertIs<Validation.Invalid>(CatalogCompiler().compile(listOf(cycle)))
        assertEquals("mechanic_cycle", invalid.diagnostics.first().code)
        for (shape in
            listOf(
                "type: sequence\nsteps: []",
                "type: parallel\nsteps: [{id: same, type: capture, area: north, duration: 1s}, {id: same, type: capture, area: south, duration: 1s}]",
                "type: repeat\nbody: {id: plate, type: capture, area: north, duration: 1s}",
                "type: repeat\ncount: 2\nforever: true\nbody: {id: plate, type: capture, area: north, duration: 1s}",
                "type: repeat\nuntil: {counter: {id: missing, equals: 1}}\nbody: {id: plate, type: capture, area: north, duration: 1s}",
            )) assertIs<Validation.Invalid>(
            CatalogCompiler()
                .compile(
                    listOf(document("objectives:\n  - id: invalid\n" + shape.prependIndent("    ")))
                ),
            shape,
        )
    }

    @Test
    fun `scope exhaustion is a technical failure instead of a composition gameplay failure`() {
        val runtime =
            engine(
                compile(
                    """
            objectives:
              - id: composed
                type: parallel
                steps: [{id: plate, type: capture, area: north, duration: 1s}]
        """
                ),
                limits = ExecutionLimits(scopes = 2),
            )
        val events = runtime.drainEvents()
        assertEquals(1, events.count { it is AttemptEvent.Fault })
        assertTrue(results(events).isEmpty())
        assertIs<ProgressionState.Ended>(runtime.state)
    }

    @Test
    fun `layers initialize private state and startup rules before children and settle result reactions`() {
        val runtime =
            engine(
                compile(
                    """
            objectives:
              - id: ritual
                type: layers
                counters: [{id: progress}]
                objectives: [{id: plate, type: capture, area: north, duration: 50ms}]
                complete_when: {counter: {id: progress, equals: 3}}
                rules:
                  - id: initialize
                    on: {source: self, event: started}
                    do: [{set_counter: {counter: progress, value: 2}}]
                  - id: react
                    on: {source: {mechanic: plate}, event: completed}
                    do: [{add_counter: {counter: progress, value: 1}}]
        """
                )
            )
        assertEquals(listOf("ritual", "plate"), started(runtime.drainEvents()).map { it.id })
        runtime.advance()
        assertEquals(listOf("plate", "ritual"), results(runtime.drainEvents()).map { it.first })
        assertIs<ProgressionState.Finishing>(runtime.state)
    }

    @Test
    fun `repeated layers get fresh counters timers and once subscriptions`() {
        val runtime =
            engine(
                compile(
                    """
            objectives:
              - id: twice
                type: repeat
                count: 2
                body:
                  id: local
                  type: layers
                  counters: [{id: progress, max: 1}]
                  timers: [{id: pulse, duration: 50ms}]
                  complete_when: {counter: {id: progress, equals: 1}}
                  rules:
                    - id: increment
                      once: true
                      on: {source: {timer: pulse}, event: expired}
                      do: [{add_counter: {counter: progress, value: 1}}]
        """
                )
            )
        runtime.drainEvents()
        runtime.advance()
        assertEquals(listOf("local"), results(runtime.drainEvents()).map { it.first })
        runtime.advance()
        assertEquals(listOf("local"), started(runtime.drainEvents()).map { it.id })
        runtime.advance()
        assertEquals(listOf("local", "twice"), results(runtime.drainEvents()).map { it.first })
        assertIs<ProgressionState.Finishing>(runtime.state)
    }

    @Test
    fun `private listener order follows activation age across persistent and phase scopes`() {
        val runtime =
            engine(
                compile(
                    """
            complete_when: {counter: {id: order, scope: encounter, equals: 4}}
            rules:
              - id: phase
                on: {source: {timer: {id: pulse, scope: encounter}}, event: expired}
                if: {counter: {id: order, scope: encounter, equals: 2}}
                do: [{set_counter: {counter: {id: order, scope: encounter}, value: 3}}]
            mechanics:
              - id: private
                type: layers
                rules:
                  - id: phase_child
                    on: {source: {timer: {id: pulse, scope: encounter}}, event: expired}
                    if: {counter: {id: order, scope: encounter, equals: 3}}
                    do: [{set_counter: {counter: {id: order, scope: encounter}, value: 4}}]
        """,
                    """
            counters: [{id: order}]
            timers: [{id: pulse, duration: 50ms}]
            rules:
              - id: encounter
                on: {source: {timer: pulse}, event: expired}
                do: [{set_counter: {counter: order, value: 1}}]
            mechanics:
              - id: persistent
                type: layers
                rules:
                  - id: encounter_child
                    on: {source: {timer: {id: pulse, scope: encounter}}, event: expired}
                    if: {counter: {id: order, scope: encounter, equals: 1}}
                    do: [{set_counter: {counter: {id: order, scope: encounter}, value: 2}}]
        """,
                )
            )
        runtime.advance()
        assertIs<ProgressionState.Finishing>(runtime.state)
        assertTrue(runtime.drainEvents().none { it is AttemptEvent.Fault })
    }

    @Test
    fun `layers use encounter precedence for simultaneous duration and deadline`() {
        for (precedence in listOf("success", "failure")) {
            val runtime =
                engine(
                    compile(
                        """
                objectives:
                  - {id: timed, type: layers, duration: 50ms, deadline: 50ms}
            """,
                        "outcome_precedence: $precedence",
                    )
                )
            runtime.advance()
            val result = results(runtime.drainEvents()).single().second
            assertEquals(
                if (precedence == "success") MechanicState.SUCCEEDED else MechanicState.FAILED,
                result.state,
            )
            assertEquals(if (precedence == "success") null else "deadline", result.reason)
        }
    }

    @Test
    fun `layers required failure differs from background failure and a private deadline cancels children`() {
        val world = World()
        val runtime =
            engine(
                compile(
                    """
            objectives:
              - id: outer
                type: layers
                duration: 100ms
                mechanics:
                  - {id: lost, type: defeat, group: lost}
                  - {id: waiting, type: capture, area: missing, duration: 1s}
        """
                ),
                world,
            )
        runtime.drainEvents()
        world.groups = mapOf("lost" to world.failed())
        runtime.advance()
        assertIs<ProgressionState.Running>(runtime.state)
        runtime.advance()
        val observed = results(runtime.drainEvents())
        assertEquals(listOf("lost", "outer"), observed.map { it.first })
        assertEquals(MechanicState.SUCCEEDED, observed.last().second.state)
        assertTrue(runtime.mechanics().isEmpty())
        val deadline =
            engine(
                compile(
                    """
            objectives:
              - id: timed
                type: layers
                deadline: 50ms
                objectives: [{id: waiting, type: capture, area: missing, duration: 1s}]
        """
                )
            )
        deadline.advance()
        assertEquals("deadline", results(deadline.drainEvents()).single().second.reason)
        assertTrue(deadline.mechanics().isEmpty())
    }

    @Test
    fun `reusable layers bind typed rule parameters and validation resolves the actual encounter`() {
        val reusable =
            SourceDocument(
                "layer.yaml",
                """
                schema: 1
                mechanic:
                  id: scripted
                  parameters: {amount: {type: integer}}
                  type: layers
                  duration: 50ms
                  rules:
                    - id: initialize
                      on: {source: self, event: started}
                      do: [{add_counter: {counter: {id: shared, scope: encounter}, value: {parameter: amount}}}]
                """
                    .trimIndent(),
            )
        val body = "objectives: [{id: invocation, use: scripted, with: {amount: 3}}]"
        val catalog = compile(body, "counters: [{id: shared, max: 3}]", listOf(reusable))
        val runtime = engine(catalog)
        runtime.advance()
        assertIs<ProgressionState.Finishing>(runtime.state)
        assertTrue(runtime.drainEvents().none { it is AttemptEvent.Fault })
        assertIs<Validation.Invalid>(CatalogCompiler().compile(listOf(reusable, document(body))))
        val modified =
            compile(
                body,
                "counters: [{id: shared, max: 3}]",
                listOf(reusable.copy(text = reusable.text.replace("add_counter", "set_counter"))),
            )
        assertNotEquals(catalog.revision, modified.revision)
    }

    @Test
    fun `a child reaction can stop an older repeat during the same settlement`() {
        val runtime =
            engine(
                compile(
                    """
            complete_when: {completed: {mechanic: {id: older, scope: encounter}}}
        """,
                    """
            counters: [{id: stop}]
            mechanics:
              - id: older
                type: repeat
                until: {counter: {id: stop, scope: encounter, equals: 1}}
                body: {id: waiting, type: capture, area: missing, duration: 1s}
              - id: younger
                type: parallel
                steps: [{id: plate, type: capture, area: north, duration: 50ms}]
            rules:
              - id: stop_repeat
                on: {source: {mechanic: younger}, event: completed}
                do: [{set_counter: {counter: stop, value: 1}}]
        """,
                )
            )
        runtime.drainEvents()
        runtime.advance()
        assertEquals(
            listOf("plate", "younger", "older"),
            results(runtime.drainEvents()).map { it.first },
        )
        assertIs<ProgressionState.Finishing>(runtime.state)
    }

    @Test
    fun `layers reject routes unreachable objectives private escapes and ambiguous timing`() {
        for (body in
            listOf(
                "duration: 1s\nsuccess: {complete: true}",
                "duration: 1s\nobjectives: [{id: one, type: capture, area: north, duration: 1s}]",
                "complete_when: {counter: {id: unknown, equals: 1}}",
                "complete_when: {satisfied: missing}",
                "rules: [{id: invalid, on: {source: {phase: run}, event: started}, do: [{set_counter: {counter: unknown, value: 1}}]}]",
                "objectives: []",
            )) assertIs<Validation.Invalid>(
            CatalogCompiler()
                .compile(
                    listOf(
                        document(
                            "objectives:\n  - id: invalid\n    type: layers\n" +
                                body.prependIndent("    ")
                        )
                    )
                ),
            body,
        )
    }

    @Test
    fun `exports observe prior local rules and forward only explicit typed fields`() {
        val runtime =
            engine(
                compile(
                    """
            counters: [{id: public_count}]
            complete_when: {counter: {id: public_count, equals: 3}}
            mechanics:
              - id: ritual
                type: layers
                counters: [{id: private_count}]
                objectives: [{id: plate, type: capture, area: north, duration: 50ms}]
                rules:
                  - id: update
                    on: {source: {mechanic: plate}, event: completed}
                    do: [{set_counter: {counter: private_count, value: 1}}]
                export:
                  events:
                    captured:
                      on: {source: {mechanic: plate}, event: completed}
                      if: {counter: {id: private_count, equals: 1}}
                      data: {side: north, amount: 3, time: {event: elapsed}}
            rules:
              - id: receive
                on: {source: {mechanic: ritual}, event: captured}
                if: {event_value: {field: time, equals: 50ms}}
                do: [{add_counter: {counter: public_count, value: {event: amount}}}]
        """
                )
            )
        runtime.drainEvents()
        runtime.advance()
        val events = runtime.drainEvents()
        val forwarded = events.filterIsInstance<AttemptEvent.Exported>().single()
        assertEquals("ritual", forwarded.identity.id)
        assertEquals(
            mapOf(
                "side" to EventDatum.Text("north"),
                "amount" to EventDatum.Integer(3),
                "time" to EventDatum.Duration(SimulationDuration(1)),
            ),
            forwarded.payload.values,
        )
        val child = events.indexOfFirst {
            it is AttemptEvent.MechanicChanged && it.identity.id == "plate"
        }
        val parent = events.indexOfFirst {
            it is AttemptEvent.MechanicChanged && it.identity.id == "ritual"
        }
        assertTrue(child < events.indexOf(forwarded) && events.indexOf(forwarded) < parent)
        assertIs<ProgressionState.Finishing>(runtime.state)
    }

    @Test
    fun `nested exports retain optional identity and an explicit player subscription narrows it`() {
        for (narrow in listOf(false, true)) {
            val catalog =
                compile(
                    """
                objectives:
                  - id: public
                    type: sequence
                    steps:
                      - id: inner
                        type: layers
                        objectives:
                          - {id: matcher, type: match_pattern, tokens: [sun], pattern: [sun], inputs: [{token: sun, targets: [{block: console}]}]}
                        export:
                          events:
                            accepted:
                              on: {source: {mechanic: matcher}, event: matched${if (narrow) ", player: player" else ""}}
                              data: {who: {event: player}, token: {event: token}}
                    export:
                      events:
                        forwarded:
                          on: {source: {mechanic: inner}, event: accepted}
                          data: {who: {event: who}}
            """
                )
            val contract =
                catalog.encounters.values
                    .single()
                    .phases
                    .single()
                    .content
                    .allMechanics
                    .single()
                    .mechanic
                    .events
                    .getValue("forwarded")
            assertEquals(narrow, contract.fields.getValue("who").required)
            assertEquals(if (narrow) "who" else null, contract.triggeringPlayer)
            val runtime = engine(catalog)
            val matcher = runtime.mechanics().single { it.id == "matcher" }
            runtime.drainEvents()
            runtime.advance(
                listOf(
                    MechanicSubmission(matcher, MechanicInput.Token("sun", null, UUID.randomUUID()))
                )
            )
            val exports = runtime.drainEvents().filterIsInstance<AttemptEvent.Exported>()
            assertEquals(if (narrow) 0 else 2, exports.size)
            if (!narrow) assertTrue(exports.last().payload.values.isEmpty())
            assertIs<ProgressionState.Finishing>(runtime.state)
        }
    }

    @Test
    fun `forwarded player aliases keep one triggering identity and receiving per player limits`() {
        val world = World()
        val catalog =
            compile(
                """
            counters: [{id: observed, max: 1}]
            complete_when: {counter: {id: observed, equals: 1}}
            mechanics:
              - id: public
                type: layers
                mechanics:
                  - id: button
                    type: interact
                    uses: 2
                    targets: [{block: button}]
                export:
                  events:
                    used:
                      on: {source: {mechanic: button}, event: used}
                      data: {who: {event: player}, alias: {event: player}, count: {event: uses_after}}
            rules:
              - id: once
                on: {source: {mechanic: public}, event: used}
                once: true
                per_player: true
                do: [{add_counter: {counter: observed, value: 1}}]
        """
            )
        val runtime = engine(catalog, world)
        val button = runtime.mechanics().single { it.id == "button" }
        val target = TargetHandle(TargetReference(TargetKind.BLOCK, "button"), UUID.randomUUID())
        runtime.drainEvents()
        runtime.advance(
            List(2) {
                MechanicSubmission(
                    button,
                    MechanicInput.Press(world.player, UUID.randomUUID(), target),
                )
            }
        )
        val exported = runtime.drainEvents().filterIsInstance<AttemptEvent.Exported>()
        assertEquals(2, exported.size)
        exported.forEach {
            assertEquals(EventDatum.Player(world.player), it.payload.values["who"])
            assertEquals(it.payload.values["who"], it.payload.values["alias"])
        }
        assertIs<ProgressionState.Finishing>(runtime.state)
    }

    @Test
    fun `scalar parameters preserve duration and numeric types through public export mapping`() {
        val reusable =
            SourceDocument(
                "export.yaml",
                """
                schema: 1
                mechanic:
                  id: values
                  parameters:
                    wait: {type: duration}
                    amount: {type: number}
                  type: layers
                  duration: 50ms
                  export:
                    events:
                      configured:
                        on: {source: self, event: started}
                        data: {wait: {parameter: wait}, amount: {parameter: amount}}
                """
                    .trimIndent(),
            )
        val catalog =
            compile(
                "objectives: [{id: instance, use: values, with: {wait: 2s, amount: 3}}]",
                definitions = listOf(reusable),
            )
        val runtime = engine(catalog)
        val payload =
            runtime.drainEvents().filterIsInstance<AttemptEvent.Exported>().single().payload
        assertEquals(EventDatum.Duration(SimulationDuration(40)), payload.values["wait"])
        assertEquals(EventDatum.Number(java.math.BigDecimal(3)), payload.values["amount"])
        runtime.advance()
        assertIs<ProgressionState.Finishing>(runtime.state)
    }

    @Test
    fun `a repeated public event retains each child activation and never replays a retired one`() {
        val runtime =
            engine(
                compile(
                    """
            objectives:
              - id: repeated
                type: repeat
                count: 2
                body:
                  id: timed
                  type: layers
                  duration: 50ms
                  export:
                    events:
                      entered:
                        on: {source: self, event: started}
                export:
                  events:
                    iteration:
                      on: {source: {mechanic: timed}, event: entered}
        """
                )
            )
        val first = runtime.drainEvents().filterIsInstance<AttemptEvent.Exported>()
        assertEquals(listOf("entered", "iteration"), first.map { it.name })
        runtime.advance()
        assertTrue(runtime.drainEvents().filterIsInstance<AttemptEvent.Exported>().isEmpty())
        runtime.advance()
        val second = runtime.drainEvents().filterIsInstance<AttemptEvent.Exported>()
        assertNotEquals(first.first().identity, second.first().identity)
        assertEquals(first.last().identity, second.last().identity)
        runtime.advance()
        assertIs<ProgressionState.Finishing>(runtime.state)
        assertTrue(runtime.drainEvents().filterIsInstance<AttemptEvent.Exported>().isEmpty())
    }

    @Test
    fun `exports reject forged lifecycle private handles unknown fields and private grandchildren`() {
        val declarations =
            listOf(
                "completed: {on: {source: {timer: pulse}, event: expired}}",
                "public: {on: {source: {timer: pulse}, event: expired}, data: {timer: {event: timer}}}",
                "public: {on: {source: {timer: pulse}, event: expired}, data: {x: {event: absent}}}",
                "public: {on: {source: {mechanic: nested.plate}, event: completed}}",
                "public: {on: {source: self, event: started}, data: {activation: 1}}",
                "public: {on: {source: self, event: started}, if: {counter: {id: missing, equals: 1}}}",
            )
        for (declaration in declarations) assertIs<Validation.Invalid>(
            CatalogCompiler()
                .compile(
                    listOf(
                        document(
                            "objectives:\n  - id: invalid\n    type: layers\n    duration: 1s\n    timers: [{id: pulse, duration: 50ms}]\n    export:\n      events:\n" +
                                declaration.prependIndent("        ")
                        )
                    )
                ),
            declaration,
        )
    }

    @Test
    fun `native gesture admission freezes all matching recipients before queued rules run`() {
        val world = World()
        val runtime =
            engine(
                compile(
                    """
            counters: [{id: observed}]
            complete_when: {counter: {id: observed, equals: 2}}
            mechanics:
              - {id: first, type: interact, targets: [{block: console}], consume_interaction: true}
              - {id: second, type: interact, targets: [{block: alias}]}
            rules:
              - id: first_used
                on: {source: {mechanic: first}, event: used}
                do: [{add_counter: {counter: observed, value: 1}}]
              - id: second_used
                on: {source: {mechanic: second}, event: used}
                do: [{add_counter: {counter: observed, value: 1}}]
        """
                ),
                world,
            )
        runtime.drainEvents()
        val physical = UUID.randomUUID()
        val targets =
            listOf("console", "alias").map {
                TargetHandle(TargetReference(TargetKind.BLOCK, it), physical)
            }
        val claims = runtime.interactionClaims(world.player, UUID.randomUUID(), targets)
        assertEquals(listOf("first", "second"), claims.map { it.mechanic.id })
        assertEquals(listOf(true, false), claims.map { it.consume })
        assertEquals(claims, runtime.admitInteraction(claims))
        assertTrue(runtime.drainEvents().isEmpty())
        assertIs<ProgressionState.Running>(runtime.state)
        assertTrue(runtime.admitInteraction(claims).isEmpty())
        runtime.advance()
        assertIs<ProgressionState.Finishing>(runtime.state)
        val events = runtime.drainEvents()
        assertEquals(listOf("first", "second"), results(events).map { it.first })
        assertEquals(
            2,
            events.filterIsInstance<AttemptEvent.MechanicChanged>().count {
                it.notice is MechanicNotice.Used
            },
        )
    }

    @Test
    fun `released held input needs a fresh gesture and stale claims cannot enter a new activation`() {
        val world = World()
        val catalog =
            compile(
                """
            objectives:
              - id: twice
                type: repeat
                count: 2
                body: {id: button, type: interact, hold: 50ms, targets: [{block: console}]}
        """
            )
        val runtime = engine(catalog, world)
        val target = TargetHandle(TargetReference(TargetKind.BLOCK, "console"), UUID.randomUUID())
        val claims = runtime.interactionClaims(world.player, UUID.randomUUID(), listOf(target))
        assertEquals(1, runtime.admitInteraction(claims).size)
        assertEquals(
            listOf(InteractionProgress(SimulationDuration(0), SimulationDuration(1))),
            runtime.interactionProgress(claims),
        )
        assertTrue(
            runtime
                .interactionProgress(
                    claims.map { it.copy(input = it.input.copy(gesture = UUID.randomUUID())) }
                )
                .isEmpty()
        )
        runtime.releaseInteraction(claims)
        assertTrue(runtime.interactionProgress(claims).isEmpty())
        runtime.advance()
        assertTrue(results(runtime.drainEvents()).isEmpty())
        assertTrue(runtime.admitInteraction(claims).isEmpty())
        val fresh = runtime.interactionClaims(world.player, UUID.randomUUID(), listOf(target))
        runtime.admitInteraction(fresh)
        runtime.advance()
        assertEquals(listOf("button"), results(runtime.drainEvents()).map { it.first })
        runtime.advance()
        assertTrue(runtime.admitInteraction(fresh).isEmpty())
        val replacement = runtime.interactionClaims(world.player, UUID.randomUUID(), listOf(target))
        assertNotEquals(fresh.single().mechanic, replacement.single().mechanic)
        runtime.admitInteraction(replacement)
        runtime.advance()
        assertIs<ProgressionState.Finishing>(runtime.state)
    }

    @Test
    fun `native lifecycle interruption during a simulation step cancels held input without reentry failure`() {
        for (damaged in listOf(false, true)) {
            val world = World()
            val runtime =
                engine(
                    compile(
                        "objectives: [{id: button, type: interact, hold: 50ms, interrupt_on_damage: true, targets: [{block: console}]}]"
                    ),
                    world,
                )
            val target =
                TargetHandle(TargetReference(TargetKind.BLOCK, "console"), UUID.randomUUID())
            val claims = runtime.interactionClaims(world.player, UUID.randomUUID(), listOf(target))
            assertEquals(1, runtime.admitInteraction(claims).size)
            world.begin = {
                if (damaged) runtime.interactionDamage(world.player)
                else runtime.releaseInteraction(claims)
            }
            runtime.advance()
            assertIs<ProgressionState.Running>(runtime.state)
            assertTrue(runtime.interactionProgress(claims).isEmpty())
            assertTrue(
                runtime.drainEvents().none {
                    it is AttemptEvent.Fault || it is AttemptEvent.MechanicChanged
                }
            )
            world.begin = {}
            runtime.admitInteraction(
                runtime.interactionClaims(world.player, UUID.randomUUID(), listOf(target))
            )
            runtime.advance()
            assertIs<ProgressionState.Finishing>(runtime.state)
        }
    }
}
