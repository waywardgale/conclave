package dev.conclave.core

import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test

class ParticipantEventsTest {
    private class World : AttemptWorld {
        val player = UUID.randomUUID()
        var observation =
            PlayerObservation(
                player,
                true,
                LifeState.ALIVE,
                Participation.ACTIVE,
                false,
                areas = setOf("plate"),
                roles = setOf("runner"),
            )

        override fun players() = PlayerFrame(listOf(observation), setOf(player))

        override fun validTarget(
            scope: RuntimeScopeIdentity,
            player: UUID,
            target: TargetHandle,
            maximumReach: Double?,
        ) = false

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

    private fun runtime(world: World, rules: String, completion: Int = 1): AttemptEngine {
        val text =
            """
            schema: 1
            encounter:
              id: lifecycle
              start: run
              phases:
                - id: run
                  counters: [{id: seen}]
                  complete_when: {counter: {id: seen, equals: $completion}}
                  rules:
        """
                .trimIndent() +
                "\n" +
                rules.trimIndent().prependIndent("        ") +
                "\n      success: {complete: true}"
        val compiled = CatalogCompiler().compile(listOf(SourceDocument("lifecycle.yaml", text)))
        val catalog =
            assertIs<Validation.Valid<CompiledCatalog>>(compiled, compiled.toString()).value
        return AttemptEngine(
                UUID.randomUUID(),
                catalog.revision,
                catalog.encounters.values.single(),
                world,
            )
            .also { it.start() }
    }

    private fun admit(runtime: AttemptEngine, world: World, lifecycle: ParticipantLifecycle) {
        val previous = world.observation
        val next = lifecycle.snapshot
        world.observation =
            PlayerObservation(world.player, next.online, next.life, next.participation, false)
        lifecycle.drainEvents().forEach { event ->
            assertTrue(
                runtime.participant(
                    ParticipantNotice(
                        event,
                        PlayerObservation(
                            world.player,
                            event.before.online,
                            event.before.life,
                            event.before.participation,
                            false,
                            if (event.before.online) previous.areas else emptySet(),
                            previous.roles,
                        ),
                    )
                )
            )
        }
    }

    @Test
    fun `source sees pre-death area and life while guard sees current death`() {
        val world = World()
        val runtime =
            runtime(
                world,
                """
            - id: death
              on: {source: {players: {state: alive, area: plate}}, event: died}
              if:
                and:
                  - event_value: {field: state_before, equals: alive}
                  - count: {players: {state: dead}, equals: 1}
              do: [{add_counter: {counter: seen, value: 1}}]
        """,
            )
        val life = ParticipantLifecycle(world.player, UUID.randomUUID())
        life.life(LifeState.DEAD)
        admit(runtime, world, life)
        assertIs<ProgressionState.Running>(
            runtime.state,
            "Admission must not recursively run the listener",
        )
        runtime.advance()
        assertIs<ProgressionState.Finishing>(runtime.state)
    }

    @Test
    fun `lifecycle defaults observe offline dead grace expiry once and preserve causal order`() {
        val world = World()
        val runtime =
            runtime(
                world,
                """
            - id: expiry
              on: {source: {players: {}}, event: reconnect_grace_expired}
              per_player: true
              once: true
              if: {event_value: {field: online_before, equals: false}}
              do: [{add_counter: {counter: seen, value: 1}}]
        """,
            )
        val connection = UUID.randomUUID()
        val life = ParticipantLifecycle(world.player, connection, RealtimeDuration(10))
        life.life(LifeState.DEAD)
        admit(runtime, world, life)
        life.disconnected(connection, 0)
        admit(runtime, world, life)
        life.expire(10)
        val events = life.drainEvents()
        assertEquals(
            listOf(
                ParticipantTransition.RECONNECT_GRACE_EXPIRED,
                ParticipantTransition.PARTICIPATION_CHANGED,
            ),
            events.map { it.type },
        )
        world.observation =
            PlayerObservation(world.player, false, LifeState.DEAD, Participation.OBSERVER, false)
        val before =
            PlayerObservation(
                world.player,
                false,
                LifeState.DEAD,
                Participation.RECONNECTING,
                false,
            )
        events.forEach { event ->
            assertTrue(runtime.participant(ParticipantNotice(event, before)))
            assertFalse(runtime.participant(ParticipantNotice(event, before)))
        }
        runtime.advance()
        assertIs<ProgressionState.Finishing>(runtime.state)
    }

    @Test
    fun `explicit helper filtering ignores self revival and counts actual assistants`() {
        val world = World()
        val runtime =
            runtime(
                world,
                """
            - id: helpers
              on: {source: {players: {state: dead}}, event: revived, player: helper}
              per_player: true
              once: true
              if: {event_value: {field: method, equals: assisted}}
              do: [{add_counter: {counter: seen, value: 1}}]
        """,
            )
        val life = ParticipantLifecycle(world.player, UUID.randomUUID())
        life.life(LifeState.DEAD)
        admit(runtime, world, life)
        life.life(LifeState.ALIVE, RevivalMethod.SELF)
        admit(runtime, world, life)
        runtime.advance()
        assertIs<ProgressionState.Running>(runtime.state)
        life.life(LifeState.DEAD)
        admit(runtime, world, life)
        val helper = UUID.randomUUID()
        life.life(LifeState.ALIVE, RevivalMethod.ASSISTED, helper)
        admit(runtime, world, life)
        runtime.advance()
        assertIs<ProgressionState.Finishing>(runtime.state)
        val event =
            runtime
                .drainEvents()
                .filterIsInstance<AttemptEvent.ParticipantChanged>()
                .last()
                .notice
                .event
        assertEquals(helper, event.helper)
        assertEquals(
            world.player,
            event.payload().values.getValue("player").let { (it as EventDatum.Player).value },
        )
    }

    @Test
    fun `a newly entered phase never subscribes to an older admitted transition`() {
        val world = World()
        val source =
            SourceDocument(
                "phase.yaml",
                """
                schema: 1
                encounter:
                  id: test
                  start: first
                  phases:
                    - id: first
                      duration: 50ms
                      success: {next: second}
                    - id: second
                      counters: [{id: seen}]
                      complete_when: {counter: {id: seen, equals: 1}}
                      rules:
                        - id: listen
                          on: {source: {players: {}}, event: died}
                          do: [{add_counter: {counter: seen, value: 1}}]
                      success: {complete: true}
                """
                    .trimIndent(),
            )
        val catalog =
            assertIs<Validation.Valid<CompiledCatalog>>(CatalogCompiler().compile(listOf(source)))
                .value
        val runtime =
            AttemptEngine(
                    UUID.randomUUID(),
                    catalog.revision,
                    catalog.encounters.values.single(),
                    world,
                )
                .also { it.start() }
        runtime.advance()
        assertIs<ProgressionState.Transition>(runtime.state)
        val life = ParticipantLifecycle(world.player, UUID.randomUUID())
        life.life(LifeState.DEAD)
        admit(runtime, world, life)
        runtime.advance()
        assertIs<ProgressionState.Running>(runtime.state)
        runtime.advance()
        assertIs<ProgressionState.Running>(runtime.state)
        runtime.stop()
        life.life(LifeState.ALIVE, RevivalMethod.ADMINISTRATIVE)
        val event = life.drainEvents().single()
        assertFalse(
            runtime.participant(
                ParticipantNotice(
                    event,
                    PlayerObservation(
                        world.player,
                        true,
                        LifeState.DEAD,
                        Participation.ACTIVE,
                        false,
                    ),
                )
            )
        )
    }

    @Test
    fun `player sources validate event player fields and source spatial bindings`() {
        val world = World()
        val runtime =
            runtime(
                world,
                """
            - id: listen
              on: {source: {players: {online: false}}, event: reconnected}
              do: [{add_counter: {counter: seen, value: 1}}]
        """,
            )
        val connection = UUID.randomUUID()
        val life = ParticipantLifecycle(world.player, connection)
        life.disconnected(connection, 0)
        admit(runtime, world, life)
        life.placed(UUID.randomUUID())
        admit(runtime, world, life)
        runtime.advance()
        assertIs<ProgressionState.Finishing>(runtime.state)
        val yaml =
            assertIs<Validation.Valid<YamlValue.Mapping>>(
                    YamlDocumentReader()
                        .read(SourceDocument("source", "from: online_players\nonline: false"))
                )
                .value
        assertIs<Validation.Invalid>(LifecycleSelectionSchema.validate(yaml))
        assertNull(participantEventContract("joined"))
        assertNull(participantEventContract("died")!!.fields["helper"])
        assertFalse(participantEventContract("revived")!!.fields.getValue("helper").required)
    }
}
