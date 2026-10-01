package dev.conclave.fabric

import dev.conclave.core.*
import dev.conclave.storage.*
import java.util.UUID
import java.util.concurrent.CompletableFuture
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks

class EncounterNativeTest {
    @GameTest(maxTicks = 5000)
    fun capture_and_sequential_phases_pin_publication_and_commit_before_cleanup(
        helper: GameTestHelper
    ) {
        val session = checkNotNull(ServerSession.get(helper.level.server))
        helper.setBlock(BlockPos(1, 0, 1), Blocks.STONE)
        val center = helper.absolutePos(BlockPos(1, 1, 1))
        val x = center.x + 0.5
        val y = center.y.toDouble()
        val z = center.z + 0.5
        val player = helper.makeMockServerPlayerInLevel()
        player.snapTo(x, y, z)
        val documents =
            listOf(
                SourceDocument(
                    "settings.yaml",
                    "schema: 1\nsettings:\n  recovery: {health: 50%, hunger: 12}",
                ),
                SourceDocument(
                    "encounter.yaml",
                    """
                    schema: 1
                    encounter:
                      id: native_test
                      participants: {area: entry}
                      start: capture
                      recovery: {location: entrance}
                      phases:
                        - id: capture
                          counters: [{id: confirmed, max: 1}]
                          complete_when: {counter: {id: confirmed, equals: 1}}
                          rules:
                            - id: public_completion
                              on: {source: {mechanic: hold}, event: holds_completed}
                              if: {event_value: {field: elapsed, equals: 1050ms}}
                              do: [{add_counter: {counter: confirmed, value: 1}}]
                          objectives:
                            - id: hold
                              use: test_tools:plate
                              with: {area: entry}
                          success: {next: wait}
                        - id: wait
                          duration: 200ms
                          success: {complete: true}
                    """
                        .trimIndent(),
                ),
                SourceDocument(
                    "plate.yaml",
                    """
                    schema: 1
                    namespace: test_tools
                    mechanic:
                      id: plate
                      parameters:
                        area: {type: area}
                      type: sequence
                      export:
                        events:
                          holds_completed:
                            on: {source: {mechanic: repeated}, event: completed}
                            data: {elapsed: {event: elapsed}}
                      steps:
                        - id: repeated
                          type: repeat
                          count: 2
                          body:
                            id: iteration
                            type: layers
                            counters: [{id: completed, max: 1}]
                            objectives:
                              - id: together
                                type: parallel
                                steps:
                                  - id: held
                                    type: capture
                                    area: {parameter: area}
                                    duration: 500ms
                            complete_when: {counter: {id: completed, equals: 1}}
                            rules:
                              - id: count_completion
                                once: true
                                on: {source: {mechanic: together}, event: completed}
                                do: [{add_counter: {counter: completed, value: 1}}]
                    """
                        .trimIndent(),
                ),
                SourceDocument(
                    "arena.yaml",
                    """
                schema: 1
                arena:
                  id: native_test
                  dimension: minecraft:overworld
                  boundary:
                    type: box
                    position: {x: $x, y: $y, z: $z}
                    width: 4
                    depth: 4
                    height: 5
                  areas:
                    - id: actual_entry
                      type: box
                      position: {x: $x, y: $y, z: $z}
                      width: 2
                      depth: 2
                      height: 3
                  locations:
                    - id: entrance
                      position: {x: $x, y: $y, z: $z}
                  encounters:
                    - encounter: native_test
                      bindings:
                        areas: {entry: actual_entry}
            """
                        .trimIndent(),
                ),
            )
        val compiled = (CatalogCompiler().compile(documents) as Validation.Valid).value
        val changed =
            (CatalogCompiler()
                    .compile(
                        documents.map {
                            it.copy(
                                text =
                                    it.text
                                        .replace("duration: 500ms", "duration: 60s")
                                        .replace(
                                            "health: 50%, hunger: 12",
                                            "health: 75%, hunger: 18",
                                        )
                            )
                        }
                    ) as Validation.Valid)
                .value
        var failure: String? = null
        var started = false
        var updated = false
        var secondStarted = false
        var stopRequested = false
        var verified = false
        var first: UUID? = null
        var second: UUID? = null
        var lookup: CompletableFuture<StoredAttempt?>? = null
        var receipt: CompletableFuture<Boolean>? = null
        var secondRecovery: CompletableFuture<StoredAttempt?>? = null
        var observations = 0
        var third: UUID? = null
        var thirdStarted = false
        var killed = false
        var thirdRecovery: CompletableFuture<StoredAttempt?>? = null
        var lifecycleScenario = false
        var lifecycleTick: (() -> Unit)? = null
        session.complete(
            session.content.publish(UUID.randomUUID(), "native_test", null, compiled)
        ) { publication, error ->
            if (error != null || publication !is SavedPublication.Published) {
                failure = "Initial publication failed: $error"
                return@complete
            }
            session.installPublished(compiled)
            session.server.commands.performPrefixedCommand(
                session.server.createCommandSourceStack(),
                "conclave start local:native_test local:native_test",
            )
            first =
                session.runtime
                    .views()
                    .singleOrNull { it.encounter == DefinitionId("local", "native_test") }
                    ?.id
            if (first == null) failure = "Native start command did not create a preparation"
        }
        helper.onEachTick {
            if (verified || failure != null) return@onEachTick
            if (lifecycleScenario) {
                lifecycleTick?.invoke()
                return@onEachTick
            }
            session.server.playerList.getPlayer(player.uuid)?.snapTo(x, y, z)
            val id = first ?: return@onEachTick
            val view = session.runtime.views().firstOrNull { it.id == id }
            if (!started && view?.state == "running") {
                started = true
                player.health = 3f
                player.foodData.foodLevel = 2
                session.complete(
                    session.content.publish(
                        UUID.randomUUID(),
                        "native_test",
                        compiled.revision,
                        changed,
                    )
                ) { result, error ->
                    if (error != null || result !is SavedPublication.Published)
                        failure = "Hotfix publication failed"
                    else {
                        session.installPublished(changed)
                        updated = true
                    }
                }
            }
            if (!started || !updated) return@onEachTick
            if (view != null) {
                if (view.revision != compiled.revision) failure = "Active attempt adopted a hotfix"
                return@onEachTick
            }
            if (lookup == null) lookup = session.attempts.attempt(id)
            val query = checkNotNull(lookup)
            if (!query.isDone) return@onEachTick
            val record = query.join()
            if (record?.state != StoredAttemptState.ENDED) {
                lookup = null
                return@onEachTick
            }
            if (session.chunks.owns(id)) {
                failure = "First attempt leaked its native chunk claim"
                return@onEachTick
            }
            if (receipt == null)
                receipt =
                    session.rewards.committed(
                        // Two 10-tick captures, one repeat boundary, one phase boundary, four wait
                        // ticks.
                        CompletionDecision(id, compiled.revision, 26, true, emptyList())
                    )
            val confirmation = checkNotNull(receipt)
            if (!confirmation.isDone) return@onEachTick
            if (!confirmation.join()) {
                failure = "First attempt has no durable completion"
                return@onEachTick
            }
            if (second == null) {
                if (player.health != player.maxHealth / 2f || player.foodData.foodLevel != 12) {
                    failure =
                        "First recovery did not retain its captured health and hunger settings"
                    return@onEachTick
                }
                second =
                    session.runtime.start(
                        DefinitionId("local", "native_test"),
                        DefinitionId("local", "native_test"),
                        "native_test",
                        { true },
                    ) { success, message ->
                        if (!success) failure = message else secondStarted = true
                    }
                if (second == null) failure = failure ?: "Second admission failed"
            }
            if (secondStarted && !stopRequested) {
                val active = session.runtime.views().singleOrNull { it.id == second }
                if (active?.revision != changed.revision) {
                    failure = "Next attempt did not adopt the hotfix"
                    return@onEachTick
                }
                observations++
                if (observations > 20) {
                    stopRequested = true
                    session.runtime.stop(checkNotNull(second))
                }
            }
            if (
                stopRequested && third == null && session.runtime.views().none { it.id == second }
            ) {
                if (secondRecovery == null)
                    secondRecovery = session.attempts.attempt(checkNotNull(second))
                val recovered = checkNotNull(secondRecovery)
                if (!recovered.isDone) return@onEachTick
                if (recovered.join()?.state != StoredAttemptState.ENDED) {
                    secondRecovery = null
                    return@onEachTick
                }
                if (player.health != player.maxHealth * 0.75f || player.foodData.foodLevel != 18) {
                    failure = "Second recovery did not use the next attempt's captured settings"
                    return@onEachTick
                }
                if (third == null) {
                    third =
                        session.runtime.start(
                            DefinitionId("local", "native_test"),
                            DefinitionId("local", "native_test"),
                            "native_test",
                            { true },
                        ) { success, message ->
                            if (!success) failure = message else thirdStarted = true
                        }
                    if (third == null) failure = failure ?: "Third admission failed"
                }
            }
            if (thirdStarted && !killed) {
                killed = true
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL)
                player.connection.handleAcceptPlayerLoad(
                    net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket()
                )
                player.hurtServer(
                    helper.level,
                    player.damageSources().genericKill(),
                    Float.MAX_VALUE,
                )
                if (player.isAlive || session.graves.view(player.uuid)?.inAttempt != true) {
                    failure = "A participant death did not create its captured attempt grave"
                    return@onEachTick
                }
                player.connection.handleClientCommand(
                    net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                        net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action
                            .PERFORM_RESPAWN
                    )
                )
                if (session.server.playerList.getPlayer(player.uuid) !== player)
                    failure = "An active participant bypassed revival with normal respawn"
            }
            if (killed && session.runtime.views().none { it.id == third }) {
                if (thirdRecovery == null)
                    thirdRecovery = session.attempts.attempt(checkNotNull(third))
                val recovered = checkNotNull(thirdRecovery)
                if (!recovered.isDone) return@onEachTick
                if (recovered.join()?.state != StoredAttemptState.ENDED) {
                    thirdRecovery = null
                    return@onEachTick
                }
                val current = checkNotNull(session.server.playerList.getPlayer(player.uuid))
                if (
                    current === player ||
                        !current.isAlive ||
                        current.health != current.maxHealth * 0.75f ||
                        session.graves.view(player.uuid) != null
                ) {
                    failure =
                        "Party defeat did not close the grave and recover the actual dead player"
                    return@onEachTick
                }
                lifecycleScenario = true
                lifecycleTick =
                    ParticipantNativeScenario.run(helper, session, changed, current) { error ->
                        if (error != null) failure = error else verified = true
                    }
            }
        }
        helper.succeedWhen {
            helper.assertTrue(failure == null, failure ?: "No failure")
            helper.assertTrue(
                verified,
                "Waiting for native capture, pinned hotfix, committed completion, fresh admission and cleanup. Attempts: ${session.runtime.views()}; recovery: ${session.recovery.messages()}",
            )
        }
    }
}
