package dev.conclave.fabric

import dev.conclave.core.*
import dev.conclave.storage.*
import java.util.UUID
import java.util.concurrent.CompletableFuture
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks

class RecoveryNativeTest {
    @GameTest(maxTicks = 10_000)
    fun offline_dead_player_recovers_from_the_captured_plan_after_arena_release(
        helper: GameTestHelper
    ) = recover(helper, false)

    @GameTest(maxTicks = 10_000)
    fun offline_spectator_restores_native_mode_and_clears_persisted_viewing(
        helper: GameTestHelper
    ) = recover(helper, true)

    private fun recover(helper: GameTestHelper, viewing: Boolean) {
        val session = checkNotNull(ServerSession.get(helper.level.server))
        val level = helper.level
        val feet = helper.absolutePos(BlockPos(1, 1, 1))
        for (x in -1..2) for (z in -1..2) {
            level.setBlockAndUpdate(feet.offset(x, -1, z), Blocks.STONE.defaultBlockState())
            for (y in 0..2) level.setBlockAndUpdate(
                feet.offset(x, y, z),
                Blocks.AIR.defaultBlockState(),
            )
        }
        val x = feet.x + .5
        val y = feet.y.toDouble()
        val z = feet.z + .5
        val online =
            helper.makeMockServerPlayerInLevel().also {
                it.snapTo(x + 1, y, z)
                it.health = 3f
                it.foodData.foodLevel = 2
            }
        NativeRevivalProtection.grant(online, SimulationDuration(100_000))
        var returning =
            helper.makeMockServerPlayerInLevel().also {
                it.snapTo(x, y, z)
                it.setGameMode(GameType.SURVIVAL)
            }
        val profile = returning.gameProfile
        val name = if (viewing) "view_recovery_test" else "recovery_test"
        val id = DefinitionId("local", name)
        val documents =
            listOf(
                SourceDocument(
                    "settings.yaml",
                    "schema: 1\nsettings:\n  recovery: {health: 25%, hunger: 8, search_radius: 1}",
                ),
                SourceDocument(
                    "encounter.yaml",
                    "schema: 1\nencounter:\n  id: $name\n  recovery: {location: entrance}\n  start: wait\n  phases: [{id: wait, duration: 1s, success: {complete: true}}]",
                ),
                SourceDocument(
                    "arena.yaml",
                    """
                schema: 1
                arena:
                  id: $name
                  dimension: minecraft:overworld
                  boundary: {type: box, position: {x: $x, y: $y, z: $z}, width: 4, depth: 4, height: 4}
                  locations: [{id: entrance, position: {x: $x, y: $y, z: $z}}]
                  encounters: [{encounter: $name}]
            """
                        .trimIndent(),
                ),
            )
        val catalog = (CatalogCompiler().compile(documents) as Validation.Valid).value
        val snapshot =
            AttemptSnapshot(
                UUID.randomUUID(),
                id,
                id,
                listOf(online.uuid, returning.uuid),
                byteArrayOf(),
            )
        var capturedView: PlayerViewingState? = null
        var failure: Throwable? = null
        var cleanupFinished = false
        var rejoined = false
        var verified = false
        var onlineLock: CompletableFuture<StoredAttempt?>? = null
        var offlineLock: CompletableFuture<StoredAttempt?>? = null
        var finished: CompletableFuture<StoredAttempt?>? = null
        session.complete(session.attempts.prepare(snapshot, catalog)) prepared@{ result, error ->
            if (error != null || result !is AttemptPreparation.Prepared) {
                failure = error ?: IllegalStateException("Preparation refused")
                return@prepared
            }
            session.complete(session.recovery.prepare(snapshot, catalog)) recorded@{ _, recordError
                ->
                if (recordError != null) {
                    failure = recordError
                    return@recorded
                }
                session.complete(session.attempts.activate(snapshot.id)) activated@{
                    active,
                    activationError ->
                    if (activationError != null || active != true) {
                        failure = activationError ?: IllegalStateException("Activation refused")
                        return@activated
                    }
                    returning.connection.handleAcceptPlayerLoad(
                        net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket()
                    )
                    returning.hurtServer(
                        level,
                        returning.damageSources().genericKill(),
                        Float.MAX_VALUE,
                    )
                    check(!returning.isAlive)
                    if (viewing) {
                        capturedView =
                            PlayerViewingState.capture(
                                returning,
                                snapshot.id,
                                SpectatorMode.TEAMMATES,
                            )
                        (returning as ConclavePlayerViewing).conclaveViewing(capturedView)
                        returning =
                            checkNotNull(
                                NativeRespawn.revive(
                                    returning,
                                    NativeDestination(
                                        level,
                                        net.minecraft.world.phys.Vec3(x, y, z),
                                        0f,
                                        0f,
                                    ),
                                    HealthPercentage(),
                                )
                            )
                        returning.setGameMode(GameType.SPECTATOR)
                        check(
                            (returning as ConclavePlayerViewing).conclaveViewing() == capturedView
                        )
                    }
                    session.server.playerList.remove(returning)
                    if (viewing) {
                        val saved =
                            session.server.playerList
                                .loadPlayerData(net.minecraft.server.players.NameAndId(profile))
                                .orElseThrow()
                        check(saved.getIntOr("playerGameType", -1) == GameType.SPECTATOR.id) {
                            "Native file lost spectator mode"
                        }
                        check(saved.getCompound(PlayerViewingState.TAG).isPresent) {
                            "Native file lost viewing ownership"
                        }
                    }
                    // Null forces recovery to read its pinned revision from durable storage.
                    session.recovery.cleanup(
                        StoredAttempt(snapshot, catalog.revision, StoredAttemptState.RUNNING),
                        null,
                    ) { cleanupError ->
                        if (cleanupError != null) failure = cleanupError
                        else {
                            cleanupFinished = true
                            session.rewards.releaseReservation(snapshot.id)
                        }
                    }
                }
            }
        }
        helper.onEachTick {
            if (failure != null || verified || !cleanupFinished) return@onEachTick
            if (!rejoined) {
                if (onlineLock == null) onlineLock = session.attempts.playerAttempt(online.uuid)
                val first = checkNotNull(onlineLock)
                if (!first.isDone) return@onEachTick
                if (first.join() != null) {
                    onlineLock = null
                    return@onEachTick
                }
                if (offlineLock == null)
                    offlineLock = session.attempts.playerAttempt(returning.uuid)
                val second = checkNotNull(offlineLock)
                if (!second.isDone) return@onEachTick
                if (second.join()?.state != StoredAttemptState.RECOVERY_PENDING) {
                    failure =
                        IllegalStateException(
                            "Offline player lost its independent recovery obligation"
                        )
                    return@onEachTick
                }
                if (
                    online.health != online.maxHealth / 4f ||
                        online.foodData.foodLevel != 8 ||
                        NativeRevivalProtection.remaining(online) != 0L
                ) {
                    failure = IllegalStateException("Online recovery ignored captured policy")
                    return@onEachTick
                }
                val cookie =
                    net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)
                returning = ServerPlayer(session.server, level, profile, cookie.clientInformation())
                // In 26.2 the configuration-stage spawn task loads data before placeNewPlayer.
                // Exercise the real saved file rather than constructing a fresh default body.
                returning.load(
                    net.minecraft.world.level.storage.TagValueInput.create(
                        net.minecraft.util.ProblemReporter.DISCARDING,
                        session.server.registryAccess(),
                        session.server.playerList
                            .loadPlayerData(net.minecraft.server.players.NameAndId(profile))
                            .orElseThrow(),
                    )
                )
                val connection =
                    net.minecraft.network.Connection(
                        net.minecraft.network.protocol.PacketFlow.SERVERBOUND
                    )
                io.netty.channel.embedded.EmbeddedChannel(connection)
                session.server.playerList.placeNewPlayer(connection, returning, cookie)
                rejoined = true
                if (viewing) {
                    val expectedMode = session.server.forcedGameType ?: GameType.SPECTATOR
                    check(returning.gameMode.gameModeForPlayer == expectedMode) {
                        "Native loading changed the saved mode without a forced server mode"
                    }
                    check((returning as ConclavePlayerViewing).conclaveViewing() == capturedView) {
                        "Native save lost the viewing ownership or original mode"
                    }
                    check(session.graves.life(returning.uuid) == LifeState.PASSED_OUT)
                    check(SpectatorNativeHooks.constrained(returning))
                }
            }
            if (finished == null) finished = session.attempts.attempt(snapshot.id)
            val result = checkNotNull(finished)
            if (!result.isDone) return@onEachTick
            if (result.join()?.state != StoredAttemptState.ENDED) {
                finished = null
                return@onEachTick
            }
            returning = checkNotNull(session.server.playerList.getPlayer(profile.id()))
            if (
                !returning.isAlive ||
                    returning.health != returning.maxHealth / 4f ||
                    returning.foodData.foodLevel != 8
            ) {
                failure =
                    IllegalStateException(
                        "Returned dead player was not recovered with its captured policy"
                    )
                return@onEachTick
            }
            val expectedMode = session.server.forcedGameType ?: GameType.SURVIVAL
            if (
                viewing &&
                    (returning.gameMode.gameModeForPlayer != expectedMode ||
                        (returning as ConclavePlayerViewing).conclaveViewing() != null)
            ) {
                failure =
                    IllegalStateException(
                        "Recovery failed to restore the original mode and remove viewing ownership"
                    )
                return@onEachTick
            }
            if (online.boundingBox.intersects(returning.boundingBox)) {
                failure = IllegalStateException("Recovered participants overlap")
                return@onEachTick
            }
            if ((returning as ConclavePlayerReceipt).conclaveRecoveryReceipt() != snapshot.id) {
                failure =
                    IllegalStateException("Returned player is missing its native recovery receipt")
                return@onEachTick
            }
            session.server.playerList.remove(online)
            session.server.playerList.remove(returning)
            verified = true
        }
        helper.succeedWhen {
            helper.assertTrue(failure == null, "Recovery failed: $failure")
            helper.assertTrue(
                verified,
                "Waiting for independent offline recovery: ${session.recovery.messages()}",
            )
        }
    }
}
