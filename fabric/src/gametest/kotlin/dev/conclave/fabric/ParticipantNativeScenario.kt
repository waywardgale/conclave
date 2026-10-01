package dev.conclave.fabric

import dev.conclave.core.*
import dev.conclave.storage.*
import java.util.UUID
import java.util.concurrent.CompletableFuture
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks

/**
 * Runs after the publication test, avoiding competing global publications in the same test batch.
 */
internal object ParticipantNativeScenario {
    fun run(
        helper: GameTestHelper,
        session: ServerSession,
        previous: CompiledCatalog,
        victim: ServerPlayer,
        finished: (String?) -> Unit,
    ): () -> Unit {
        val origin = victim.position()
        val feet = victim.blockPosition()
        for (x in -2..3) for (z in -2..2) {
            helper.level.setBlockAndUpdate(feet.offset(x, -1, z), Blocks.STONE.defaultBlockState())
            for (y in 0..2) helper.level.setBlockAndUpdate(
                feet.offset(x, y, z),
                Blocks.AIR.defaultBlockState(),
            )
        }
        val assistant = helper.makeMockServerPlayerInLevel()
        assistant.setGameMode(GameType.SURVIVAL)
        assistant.snapTo(origin.add(0.7, 0.0, 0.0))
        victim.setGameMode(GameType.SURVIVAL)
        victim.connection.handleAcceptPlayerLoad(ServerboundPlayerLoadedPacket())
        assistant.connection.handleAcceptPlayerLoad(ServerboundPlayerLoadedPacket())
        val definition =
            SourceDocument(
                "encounter.yaml",
                """
                schema: 1
                encounter:
                  id: native_test
                  participants: {area: entry}
                  start: revive
                  recovery: {location: entrance}
                  phases:
                    - id: revive
                      counters: [{id: deaths}, {id: revivals}]
                      complete_when: {counter: {id: revivals, equals: 1}}
                      rules:
                        - id: actual_death
                          on: {source: {players: {state: alive, area: entry}}, event: died}
                          do: [{add_counter: {counter: deaths, value: 1}}]
                        - id: helped_revive
                          on: {source: {players: {state: dead}}, event: revived, player: helper}
                          per_player: true
                          once: true
                          if:
                            and:
                              - counter: {id: deaths, equals: 1}
                              - event_value: {field: method, equals: assisted}
                          do: [{add_counter: {counter: revivals, value: 1}}]
                      success: {complete: true}
                """
                    .trimIndent(),
            )
        val compiled =
            (CatalogCompiler()
                    .compile(
                        previous.sources.map { if (it.file == definition.file) definition else it }
                    ) as Validation.Valid)
                .value
        var done = false
        fun finish(message: String?) {
            if (done) return
            done = true
            finished(message)
        }
        var attempt: UUID? = null
        var started = false
        var killed = false
        var revived = false
        var elapsed = 0L
        var recovery: CompletableFuture<StoredAttempt?>? = null
        var receipt: CompletableFuture<Boolean>? = null
        session.complete(
            session.content.publish(
                UUID.randomUUID(),
                "native_lifecycle_test",
                previous.revision,
                compiled,
            )
        ) { result, error ->
            if (error != null || result !is SavedPublication.Published) {
                finish("Lifecycle fixture publication failed: $error")
            } else {
                session.installPublished(compiled)
                attempt =
                    session.runtime.start(
                        DefinitionId("local", "native_test"),
                        DefinitionId("local", "native_test"),
                        "native_lifecycle_test",
                        { true },
                    ) { ok, message ->
                        if (ok) started = true else finish(message)
                    }
                if (attempt == null) finish("Lifecycle fixture admission failed")
            }
        }
        return tick@{
            if (done || !started) return@tick
            try {
                val id = checkNotNull(attempt)
                if (!killed) {
                    assistant.snapTo(origin.add(2.0, 0.0, 0.0))
                    victim.hurtServer(
                        helper.level,
                        victim.damageSources().genericKill(),
                        Float.MAX_VALUE,
                    )
                    check(!victim.isAlive && session.graves.view(victim.uuid)?.inAttempt == true) {
                        "Native lifecycle fixture did not create a grave"
                    }
                    killed = true
                }
                if (!revived) {
                    val grave =
                        session.graves.view(victim.uuid)
                            ?: error("Grave disappeared before assistance")
                    if (grave.message.isNotEmpty()) return@tick
                    val delta = origin.add(0.0, 0.45, 0.0).subtract(assistant.eyePosition)
                    assistant.yRot = Math.toDegrees(kotlin.math.atan2(-delta.x, delta.z)).toFloat()
                    assistant.xRot =
                        -Math.toDegrees(
                                kotlin.math.atan2(delta.y, kotlin.math.hypot(delta.x, delta.z))
                            )
                            .toFloat()
                    elapsed =
                        checkNotNull(session.runtime.deathContext(victim.uuid)).world.currentTick +
                            1
                    session.graves.accept(
                        assistant,
                        GraveInputPayload(checkNotNull(grave.grave), 1, GraveInputAction.PRESS),
                    )
                    val current = checkNotNull(session.server.playerList.getPlayer(victim.uuid))
                    check(current !== victim && current.isAlive) {
                        "Native assistance did not revive the participant"
                    }
                    // Replay must not emit another revival or increment the rule's counter again.
                    session.graves.accept(
                        assistant,
                        GraveInputPayload(grave.grave, 1, GraveInputAction.PRESS),
                    )
                    revived = true
                }
                if (session.runtime.views().any { it.id == id }) return@tick
                if (recovery == null) recovery = session.attempts.attempt(id)
                if (!checkNotNull(recovery).isDone) return@tick
                if (recovery!!.join()?.state != StoredAttemptState.ENDED) {
                    recovery = null
                    return@tick
                }
                if (receipt == null)
                    receipt =
                        session.rewards.committed(
                            CompletionDecision(id, compiled.revision, elapsed, true, emptyList())
                        )
                if (!checkNotNull(receipt).isDone) return@tick
                check(receipt!!.join()) {
                    "Death and assisted-revival rules did not commit native encounter success"
                }
                check(session.graves.view(victim.uuid) == null)
                session.server.playerList
                    .getPlayer(victim.uuid)
                    ?.let(session.server.playerList::remove)
                session.server.playerList
                    .getPlayer(assistant.uuid)
                    ?.let(session.server.playerList::remove)
                finish(null)
            } catch (error: Exception) {
                finish(error.toString())
            }
        }
    }
}
