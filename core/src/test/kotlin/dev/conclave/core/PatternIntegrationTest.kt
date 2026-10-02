package dev.conclave.core

import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test

class PatternIntegrationTest {
    private class World : AttemptWorld {
        val players = listOf(UUID.randomUUID(), UUID.randomUUID())

        override fun players() =
            PlayerFrame(
                players.map {
                    PlayerObservation(it, true, LifeState.ALIVE, Participation.ACTIVE, false)
                },
                players.toSet(),
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

        override fun deliver(
            scope: RuntimeScopeIdentity,
            relic: UUID,
            generation: Long,
            holder: UUID,
        ) = false
    }

    private fun source(body: String, root: String = "") =
        SourceDocument(
            "encounter.yaml",
            "schema: 1\nencounter:\n  id: puzzle\n  start: run\n" +
                (if (root.isEmpty()) "" else root.trimIndent().prependIndent("  ") + "\n") +
                "  phases:\n    - id: run\n      success: {complete: true}\n" +
                body.trimIndent().prependIndent("      "),
        )

    private fun compile(body: String, root: String = ""): CompiledCatalog {
        val result = CatalogCompiler().compile(listOf(source(body, root)))
        return assertIs<Validation.Valid<CompiledCatalog>>(result, result.toString()).value
    }

    private fun engine(catalog: CompiledCatalog, world: World = World()) =
        AttemptEngine(
                UUID.randomUUID(),
                catalog.revision,
                catalog.encounters.values.single(),
                world,
            )
            .also { it.start() }

    private fun target(name: String, physical: UUID = UUID.randomUUID()) =
        TargetHandle(TargetReference(TargetKind.BLOCK, name), physical)

    private fun notices(runtime: AttemptEngine) =
        runtime.drainEvents().filterIsInstance<AttemptEvent.MechanicChanged>().map { it.notice }

    @Test
    fun `physical pattern admission uses binding hold cooldown and server token with replay rejection`() {
        val world = World()
        val runtime =
            engine(
                compile(
                    """
            objectives:
              - id: puzzle
                type: match_pattern
                tokens: [sun, moon]
                pattern: [sun, moon]
                use_cooldown: 100ms
                inputs:
                  - {token: sun, targets: [{block: left}], hold: 100ms, consume_interaction: true}
                  - {token: moon, targets: [{block: right}]}
        """
                ),
                world,
            )
        val player = world.players.first()
        fun claim(name: String) =
            runtime.interactionClaims(player, UUID.randomUUID(), listOf(target(name)))
        val wrong = claim("right")
        assertEquals(SimulationDuration(0), wrong.single().hold)
        assertFalse(wrong.single().consume)
        runtime.admitInteraction(wrong)
        assertTrue(claim("left").isEmpty())
        repeat(2) { runtime.advance() }
        val mismatch = notices(runtime).filterIsInstance<MechanicNotice.PatternInput>().single()
        assertEquals("moon", mismatch.token)
        assertFalse(mismatch.matched)
        assertEquals("interaction", mismatch.origin)
        val interrupted = claim("left")
        assertEquals(SimulationDuration(2), interrupted.single().hold)
        assertTrue(interrupted.single().consume)
        runtime.admitInteraction(interrupted)
        runtime.releaseInteraction(interrupted)
        assertTrue(runtime.interactionProgress(interrupted).isEmpty())
        val held = claim("left")
        runtime.admitInteraction(held)
        runtime.advance()
        assertEquals(
            InteractionProgress(SimulationDuration(1), SimulationDuration(2)),
            runtime.interactionProgress(held).single(),
        )
        runtime.advance()
        assertTrue(runtime.admitInteraction(held).isEmpty())
        assertTrue(claim("right").isEmpty())
        repeat(2) { runtime.advance() }
        runtime.admitInteraction(claim("right"))
        runtime.advance()
        assertIs<ProgressionState.Finishing>(runtime.state)
        assertEquals(
            listOf("sun", "moon"),
            notices(runtime).filterIsInstance<MechanicNotice.PatternInput>().map { it.token },
        )
    }

    @Test
    fun `pattern actions commit a whole ordered action list before queued feedback and final reset cannot undo success`() {
        val runtime =
            engine(
                compile(
                    """
            counters: [{id: action_list_finished}]
            complete_when: {counter: {id: action_list_finished, equals: 2}}
            objectives:
              - {id: puzzle, type: match_pattern, tokens: [sun, moon], pattern: [sun, moon]}
            rules:
              - id: solve
                on: {source: {mechanic: puzzle}, event: started}
                do:
                  - {submit_token: {mechanic: puzzle, token: sun}}
                  - {reset_pattern: {mechanic: puzzle}}
                  - {submit_token: {mechanic: puzzle, token: sun}}
                  - {submit_token: {mechanic: puzzle, token: moon}}
                  - {reset_pattern: {mechanic: puzzle}}
                  - {set_counter: {counter: action_list_finished, value: 1}}
              - id: observe
                on: {source: {mechanic: puzzle}, event: matched}
                if: {counter: {id: action_list_finished, equals: 1}}
                do: [{add_counter: {counter: action_list_finished, value: 1}}]
        """
                )
            )
        val notices = notices(runtime)
        assertEquals(3, notices.filterIsInstance<MechanicNotice.PatternInput>().size)
        assertTrue(
            notices.filterIsInstance<MechanicNotice.PatternInput>().all {
                it.origin == "action" && it.player == null
            }
        )
        assertEquals(
            listOf(1),
            notices.filterIsInstance<MechanicNotice.PatternReset>().map { it.before },
        )
        assertEquals(1, notices.filterIsInstance<MechanicNotice.Result>().size)
        runtime.advance()
        assertIs<ProgressionState.Finishing>(runtime.state)
    }

    @Test
    fun `typed player action completes only that captured player and personal completion stays latched`() {
        val world = World()
        val runtime =
            engine(
                compile(
                    """
            objectives:
              - {id: puzzle, type: match_pattern, tokens: [sun], pattern: [sun], progress: per_player}
            mechanics:
              - {id: button, type: interact, targets: [{block: console}], uses: 3}
            rules:
              - id: contribute
                on: {source: {mechanic: button}, event: used}
                do:
                  - {submit_token: {mechanic: puzzle, token: sun, player: {event: player}}}
                  - {reset_pattern: {mechanic: puzzle, player: {event: player}}}
        """
                ),
                world,
            )
        fun press(player: UUID) {
            runtime.admitInteraction(
                runtime.interactionClaims(player, UUID.randomUUID(), listOf(target("console")))
            )
            runtime.advance()
        }
        press(world.players[0])
        press(world.players[0])
        assertIs<ProgressionState.Running>(runtime.state)
        assertEquals(
            listOf(world.players[0]),
            notices(runtime).filterIsInstance<MechanicNotice.PlayerCompleted>().map { it.player },
        )
        press(world.players[1])
        assertIs<ProgressionState.Finishing>(runtime.state)
        assertEquals(
            listOf(world.players[1]),
            notices(runtime).filterIsInstance<MechanicNotice.PlayerCompleted>().map { it.player },
        )
    }

    @Test
    fun `optional shared attribution needs subscription narrowing before supplying an action player`() {
        val body =
            """
            objectives:
              - {id: puzzle, type: match_pattern, tokens: [sun], pattern: [sun], progress: per_player, completion: any}
            mechanics:
              - {id: input, type: match_pattern, tokens: [sun], pattern: [sun], inputs: [{token: sun, targets: [{block: console}]}]}
            rules:
              - id: forward
                on: {source: {mechanic: input}, event: matched}
                do: [{submit_token: {mechanic: puzzle, token: sun, player: {event: player}}}]
        """
        assertEquals(
            "event_player",
            diagnostic(CatalogCompiler().compile(listOf(source(body)))).code,
        )
        val world = World()
        val runtime =
            engine(
                compile(body.replace("event: matched}", "event: matched, player: player}")),
                world,
            )
        runtime.admitInteraction(
            runtime.interactionClaims(
                world.players[0],
                UUID.randomUUID(),
                listOf(target("console")),
            )
        )
        runtime.advance()
        assertIs<ProgressionState.Finishing>(runtime.state)
        assertEquals(
            listOf(world.players[0]),
            notices(runtime).filterIsInstance<MechanicNotice.PlayerCompleted>().map { it.player },
        )
    }

    @Test
    fun `reset all clears every unfinished record and empty holds without refunding input cooldown`() {
        val world = World()
        val runtime =
            engine(
                compile(
                    """
            objectives:
              - id: puzzle
                type: match_pattern
                tokens: [sun, moon]
                pattern: [sun, moon]
                progress: per_player
                use_cooldown: 500ms
                inputs:
                  - {token: sun, targets: [{block: left}]}
                  - {token: moon, targets: [{block: right}], hold: 100ms}
            mechanics:
              - {id: button, type: interact, targets: [{block: reset}], uses: 2}
            rules:
              - id: clear
                on: {source: {mechanic: button}, event: used}
                do: [{reset_pattern: {mechanic: puzzle, all: true}}]
        """
                ),
                world,
            )
        fun claims(player: UUID, block: String) =
            runtime.interactionClaims(player, UUID.randomUUID(), listOf(target(block)))
        world.players.forEach { runtime.admitInteraction(claims(it, "left")) }
        runtime.advance()
        runtime.admitInteraction(claims(world.players[0], "reset"))
        runtime.advance()
        val cleared = notices(runtime).filterIsInstance<MechanicNotice.PatternReset>()
        assertEquals(world.players, cleared.map { it.player })
        assertEquals(listOf(1, 1), cleared.map { it.before })
        world.players.forEach { assertTrue(claims(it, "left").isEmpty()) }
        repeat(8) { runtime.advance() }
        val held = world.players.flatMap { claims(it, "right") }
        held.forEach { runtime.admitInteraction(listOf(it)) }
        assertEquals(2, runtime.interactionProgress(held).size)
        runtime.admitInteraction(claims(world.players[0], "reset"))
        runtime.advance()
        assertTrue(runtime.interactionProgress(held).isEmpty())
        repeat(3) { runtime.advance() }
        assertTrue(
            notices(runtime).none {
                it is MechanicNotice.PatternInput || it is MechanicNotice.PatternReset
            }
        )
        assertIs<ProgressionState.Running>(runtime.state)
    }

    @Test
    fun `private layer rules may route an encounter matcher without exposing a wrapper input`() {
        val runtime =
            engine(
                compile(
                    """
            objectives:
              - id: supplier
                type: layers
                duration: 50ms
                rules:
                  - id: supply
                    on: {source: self, event: started}
                    do: [{submit_token: {mechanic: {id: puzzle, scope: encounter}, token: sun}}]
        """,
                    """
            mechanics:
              - {id: puzzle, type: match_pattern, tokens: [sun], pattern: [sun]}
        """,
                )
            )
        assertEquals(
            listOf("sun"),
            notices(runtime).filterIsInstance<MechanicNotice.PatternInput>().map { it.token },
        )
        runtime.advance()
        assertIs<ProgressionState.Finishing>(runtime.state)
    }

    @Test
    fun `required action cannot initialize a pending matcher from scope startup`() {
        val runtime =
            engine(
                compile(
                    """
            objectives:
              - {id: puzzle, type: match_pattern, tokens: [sun], pattern: [sun]}
            rules:
              - id: too_early
                on: {source: self, event: started}
                do: [{submit_token: {mechanic: puzzle, token: sun}}]
        """
                )
            )
        assertEquals(ProgressionState.Ended(AttemptResult.TECHNICAL_ERROR), runtime.state)
    }

    @Test
    fun `coverage follows possible answers and scoped declared actions rather than the entire vocabulary`() {
        val body =
            """
            objectives:
              - id: puzzle
                type: match_pattern
                tokens: [sun, moon, decoy]
                pattern: [sun]
                inputs: [{token: sun, targets: [{block: console}]}]
        """
        compile(body)
        for (pattern in
            listOf(
                "[sun, moon]",
                "{choose: [[sun], [moon]]}",
                "{sample: {from: [sun, moon], length: 1}}",
            )) {
            assertEquals(
                "pattern_input_coverage",
                diagnostic(
                        CatalogCompiler()
                            .compile(
                                listOf(source(body.replace("pattern: [sun]", "pattern: $pattern")))
                            )
                    )
                    .code,
            )
        }
        compile(
            body.replace("pattern: [sun]", "pattern: [moon]") +
                """

            rules:
              - id: possible_route
                on: {source: {mechanic: puzzle}, event: started}
                if: {count: {players: {online: true}, at_least: 500}}
                do: [{submit_token: {mechanic: puzzle, token: moon}}]
        """
        )
    }

    @Test
    fun `matcher actions reject unknown tokens wrappers and invalid player selection shapes`() {
        val body =
            """
            objectives:
              - {id: puzzle, type: match_pattern, tokens: [sun], pattern: [sun], inputs: [{token: sun, targets: [{block: console}]}]}
            rules:
              - id: invalid
                on: {source: {mechanic: puzzle}, event: started}
                do: [ACTION]
        """
        for (action in
            listOf(
                "{submit_token: {mechanic: puzzle, token: absent}}",
                "{submit_token: {mechanic: puzzle, token: sun, player: {event: elapsed}}}",
                "{reset_pattern: {mechanic: puzzle, all: true}}",
                "{reset_pattern: {mechanic: puzzle, all: false}}",
                "{submit_token: {mechanic: absent, token: sun}}",
            )) assertIs<Validation.Invalid>(
            CatalogCompiler().compile(listOf(source(body.replace("ACTION", action)))),
            action,
        )
        for (action in
            listOf(
                "{submit_token: {mechanic: puzzle, token: sun}}",
                "{reset_pattern: {mechanic: puzzle}}",
            )) assertIs<Validation.Invalid>(
            CatalogCompiler()
                .compile(
                    listOf(
                        source(
                            body
                                .replace(
                                    "pattern: [sun], inputs",
                                    "pattern: [sun], progress: per_player, inputs",
                                )
                                .replace("ACTION", action)
                        )
                    )
                )
        )
        assertIs<Validation.Invalid>(
            CatalogCompiler()
                .compile(
                    listOf(
                        source(
                            body
                                .replace(
                                    "- {id: puzzle, type: match_pattern, tokens: [sun], pattern: [sun], inputs: [{token: sun, targets: [{block: console}]}]}",
                                    "- {id: puzzle, type: layers, duration: 1s}",
                                )
                                .replace("ACTION", "{submit_token: {mechanic: puzzle, token: sun}}")
                        )
                    )
                )
        )
    }

    @Test
    fun `physical alias collisions fail publication independently for every matcher occurrence`() {
        val encounter =
            source(
                """
            objectives:
              - id: wrapper
                type: layers
                objectives:
                  - id: puzzle
                    type: match_pattern
                    tokens: [sun, moon]
                    pattern: [sun, moon]
                    inputs:
                      - {token: sun, targets: [{block: left}]}
                      - {token: moon, targets: [{block: right}]}
        """
            )
        val arena =
            SourceDocument(
                "arena.yaml",
                """
                schema: 1
                arena:
                  id: room
                  dimension: minecraft:overworld
                  boundary: {type: box, position: {x: 0, y: 0, z: 0}, width: 8, depth: 8, height: 8}
                  locations:
                    - {id: left, position: {x: -0.1, y: 1.1, z: 1.1}}
                    - {id: right, position: {x: -0.9, y: 1.9, z: 1.9}}
                  encounters: [{encounter: puzzle}]
                """
                    .trimIndent(),
            )
        assertEquals(
            "ambiguous_pattern_binding",
            diagnostic(CatalogCompiler().compile(listOf(encounter, arena))).code,
        )
        assertIs<Validation.Valid<CompiledCatalog>>(
            CatalogCompiler()
                .compile(
                    listOf(encounter, arena.copy(text = arena.text.replace("x: -0.9", "x: -1.1")))
                )
        )
    }

    @Test
    fun `unexpected physical ambiguity stops the attempt without a fabricated mismatch`() {
        val world = World()
        val runtime =
            engine(
                compile(
                    """
            objectives:
              - id: puzzle
                type: match_pattern
                tokens: [sun, moon]
                pattern: [sun, moon]
                inputs:
                  - {token: sun, targets: [{block: left}]}
                  - {token: moon, targets: [{block: right}]}
        """
                ),
                world,
            )
        val physical = UUID.randomUUID()
        assertTrue(
            runtime
                .interactionClaims(
                    world.players[0],
                    UUID.randomUUID(),
                    listOf(target("left", physical), target("right", physical)),
                )
                .isEmpty()
        )
        assertEquals(ProgressionState.Ended(AttemptResult.TECHNICAL_ERROR), runtime.state)
        assertTrue(notices(runtime).filterIsInstance<MechanicNotice.PatternInput>().isEmpty())
    }

    @Test
    fun `conditions see retained personal summaries after any completion without completing other records`() {
        val world = World()
        val runtime =
            engine(
                compile(
                    """
            objectives:
              - id: puzzle
                type: match_pattern
                tokens: [sun]
                pattern: [sun, sun]
                progress: per_player
                completion: any
                inputs: [{token: sun, targets: [{block: console}]}]
            complete_when:
              and:
                - completed: {mechanic: puzzle}
                - pattern_state: {mechanic: puzzle, completed_players: {equals: 1}}
                - any:
                    players: {}
                    satisfy:
                      and:
                        - pattern_state: {mechanic: puzzle, progress: {equals: 50%}, completed: false}
                        - not: {pattern_state: {mechanic: puzzle, completed: true}}
                - any:
                    players: {}
                    satisfy: {pattern_state: {mechanic: puzzle, completed: true}}
        """
                ),
                world,
            )
        fun press(player: UUID) {
            runtime.admitInteraction(
                runtime.interactionClaims(player, UUID.randomUUID(), listOf(target("console")))
            )
            runtime.advance()
        }
        press(world.players[1])
        press(world.players[0])
        assertIs<ProgressionState.Running>(runtime.state)
        press(world.players[0])
        assertIs<ProgressionState.Finishing>(runtime.state)
    }

    @Test
    fun `pattern guards use committed state while event payload remains historical`() {
        val runtime =
            engine(
                compile(
                    """
            counters: [{id: observed}]
            mechanics:
              - {id: puzzle, type: match_pattern, tokens: [sun], pattern: [sun, sun]}
            complete_when: {counter: {id: observed, equals: 1}}
            rules:
              - id: supply
                on: {source: {mechanic: puzzle}, event: started}
                do:
                  - {submit_token: {mechanic: puzzle, token: sun}}
                  - {reset_pattern: {mechanic: puzzle}}
              - id: see_reset
                on: {source: {mechanic: puzzle}, event: matched}
                if:
                  and:
                    - event_value: {field: progress_after, equals: 1}
                    - pattern_state: {mechanic: puzzle, progress: {equals: 0}, completed: false}
                do: [{add_counter: {counter: observed, value: 1}}]
        """
                )
            )
        runtime.advance()
        assertIs<ProgressionState.Finishing>(runtime.state)
    }

    @Test
    fun `retained successful pattern state expires with its phase and stale input cannot solve its replacement`() {
        val input =
            source(
                    """
            objectives:
              - {id: puzzle, type: match_pattern, tokens: [sun], pattern: [sun], inputs: [{token: sun, targets: [{block: console}]}]}
            complete_when: {pattern_state: {mechanic: puzzle, completed: true}}
        """
                )
                .let {
                    it.copy(
                        text = it.text.replace("success: {complete: true}", "success: {next: run}")
                    )
                }
        val catalog =
            assertIs<Validation.Valid<CompiledCatalog>>(CatalogCompiler().compile(listOf(input)))
                .value
        val world = World()
        val runtime = engine(catalog, world)
        val old =
            runtime.interactionClaims(
                world.players[0],
                UUID.randomUUID(),
                listOf(target("console")),
            )
        runtime.admitInteraction(old)
        runtime.advance()
        assertIs<ProgressionState.Transition>(runtime.state)
        runtime.advance()
        assertIs<ProgressionState.Running>(runtime.state)
        assertTrue(runtime.admitInteraction(old).isEmpty())
        runtime.advance()
        assertIs<ProgressionState.Running>(runtime.state)
        val fresh =
            runtime.interactionClaims(
                world.players[0],
                UUID.randomUUID(),
                listOf(target("console")),
            )
        assertNotEquals(old.single().mechanic, fresh.single().mechanic)
        runtime.admitInteraction(fresh)
        runtime.advance()
        assertIs<ProgressionState.Transition>(runtime.state)
    }

    @Test
    fun `input coverage belongs to its occurrence and does not borrow routes from another scope`() {
        val input =
            source(
                """
            objectives:
              - id: wrapper
                type: repeat
                count: 1
                body: {id: puzzle, type: match_pattern, tokens: [sun], pattern: [sun]}
        """,
                """
            mechanics:
              - {id: puzzle, type: match_pattern, tokens: [sun], pattern: [sun]}
            rules:
              - id: root_route
                on: {source: {mechanic: puzzle}, event: started}
                do: [{submit_token: {mechanic: puzzle, token: sun}}]
        """,
            )
        val missing = diagnostic(CatalogCompiler().compile(listOf(input)))
        assertEquals("pattern_input_coverage", missing.code)
        assertEquals("$.encounter.phases[0].objectives[0].body", missing.source.path)
    }
}
