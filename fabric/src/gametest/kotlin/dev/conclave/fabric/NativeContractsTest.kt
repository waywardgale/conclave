package dev.conclave.fabric

import dev.conclave.core.AuthorityPolicy
import dev.conclave.core.Principal
import java.util.UUID
import java.util.concurrent.CompletableFuture
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.commands.CommandSource
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.players.NameAndId
import net.minecraft.server.rcon.RconConsoleSource
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.CommandBlockEntity

class NativeContractsTest {
    @GameTest
    fun disconnected_native_entity_cannot_readmit_itself_before_removal(helper: GameTestHelper) {
        val session = checkNotNull(ServerSession.get(helper.level.server))
        var player = helper.makeMockServerPlayerInLevel()
        val id = dev.conclave.core.DefinitionId("local", "connection_test")
        val definition =
            dev.conclave.core.EncounterDefinition(
                id,
                null,
                "wait",
                listOf(
                    dev.conclave.core.PhaseDefinition(
                        "wait",
                        dev.conclave.core.SimulationDuration(20),
                        null,
                        dev.conclave.core.PhaseRoute.Complete,
                    )
                ),
                reconnectGrace = dev.conclave.core.RealtimeDuration(0),
            )
        val arena =
            dev.conclave.core.ArenaDefinition(
                id,
                null,
                "minecraft:overworld",
                dev.conclave.core.Geometry.Box(
                    dev.conclave.core.Position.ZERO,
                    java.math.BigDecimal.ONE,
                    java.math.BigDecimal.ONE,
                    java.math.BigDecimal.ONE,
                ),
                emptyList(),
                emptyList(),
                listOf(dev.conclave.core.ArenaEncounter(id, emptyMap(), emptyMap())),
            )
        dev.conclave.core.GeometryEngine().use { geometry ->
            val transitions = mutableListOf<dev.conclave.core.ParticipantEvent>()
            val world =
                NativeAttemptWorld(
                    session,
                    arena,
                    definition,
                    geometry,
                    setOf(player.uuid),
                    { transitions.add(it.event) },
                )
            helper.assertTrue(
                world.observe().byId.getValue(player.uuid).canContribute,
                "Admitted native player starts active",
            )
            val replacement =
                session.server.playerList.respawn(
                    player,
                    true,
                    net.minecraft.world.entity.Entity.RemovalReason.DISCARDED,
                )
            replacement.connection.player = replacement
            replacement.connection.resetPosition()
            world.replaced(player, replacement)
            player = replacement
            helper.assertTrue(
                world.observe().byId.getValue(player.uuid).canContribute,
                "Native object replacement preserves admission",
            )
            helper.assertTrue(
                transitions.isEmpty(),
                "Native object replacement must not fabricate disconnect/reconnect events",
            )
            world.disconnected(player, System.nanoTime())
            // The native disconnect event can precede removal from PlayerList.
            helper.assertTrue(
                session.server.playerList.getPlayer(player.uuid) === player,
                "Native entity is still listed",
            )
            val after = world.observe().byId.getValue(player.uuid)
            helper.assertFalse(
                after.online,
                "A closed connection must remain offline before native removal",
            )
            helper.assertTrue(
                after.participation == dev.conclave.core.Participation.OBSERVER,
                "Zero grace must expire without inventing a return",
            )
            helper.assertFalse(
                after.canContribute,
                "An observer cannot contribute physical progress",
            )
            world.end()
        }
        session.server.playerList.remove(player)
        helper.succeed()
    }

    @GameTest(maxTicks = 400)
    fun chunk_claims_share_native_tickets_without_removing_vanilla_forcing(helper: GameTestHelper) {
        val level = helper.level
        val claims = checkNotNull(ServerSession.get(level.server)).chunks
        val position = helper.absolutePos(BlockPos(1, 1, 1))
        val chunk =
            net.minecraft.world.level.ChunkPos(
                Math.floorDiv(position.x, 16),
                Math.floorDiv(position.z, 16),
            )
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val shape =
            dev.conclave.core.Geometry.Box(
                dev.conclave.core.Position(
                    chunk.x * 16.0 + 8,
                    position.y.toDouble(),
                    chunk.z * 16.0 + 8,
                ),
                java.math.BigDecimal.ONE,
                java.math.BigDecimal.ONE,
                java.math.BigDecimal.ONE,
            )
        val footprint =
            dev.conclave.core.GeometryEngine().use {
                dev.conclave.core.ChunkFootprint.plan(
                    shape,
                    it,
                    NativeChunkClaims.propagationRadius,
                )
            }
        level.setChunkForced(chunk.x, chunk.z, true)
        helper.assertTrue(
            claims.retain(first, level.dimension().identifier().toString(), footprint),
            "First Conclave footprint must fit",
        )
        helper.assertTrue(
            claims.retain(second, level.dimension().identifier().toString(), footprint),
            "Shared footprint must fit without double accounting",
        )
        helper.assertTrue(
            claims.retainedCount >= footprint.retained.size,
            "Overlapping native resource claims must be reference counted",
        )
        var released = false
        helper.succeedWhen {
            helper.assertTrue(claims.ready(second), "Waiting for native entity ticking readiness")
            if (!released) {
                helper.assertTrue(claims.release(first), "First owner must release")
                helper.assertTrue(claims.ready(second), "Second owner must retain simulation")
                helper.assertTrue(claims.release(second), "Final Conclave owner must release")
                helper.assertTrue(
                    !claims.owns(first) && !claims.owns(second),
                    "Both claim owners must release",
                )
                helper.assertTrue(
                    level.chunkSource.forceLoadedChunks.contains(chunk.pack()),
                    "Vanilla forcing must survive Conclave cleanup",
                )
                level.setChunkForced(chunk.x, chunk.z, false)
                released = true
            }
        }
    }

    @GameTest
    fun authority_uses_the_original_native_source(helper: GameTestHelper) {
        val server = helper.level.server
        val player = helper.makeMockServerPlayerInLevel()
        val profile = NameAndId(player.gameProfile)
        val console = server.createCommandSourceStack()
        helper.assertTrue(
            AuthorityPolicy.operator(NativeAuthority.principal(console)),
            "Local console must retain operator authority",
        )
        helper.assertFalse(
            AuthorityPolicy.operator(NativeAuthority.principal(player.createCommandSourceStack())),
            "An ordinary player must not gain operator authority",
        )
        server.playerList.op(profile)
        helper.assertTrue(
            AuthorityPolicy.operator(NativeAuthority.principal(player.createCommandSourceStack())),
            "Native operator status must be observed",
        )
        val rcon = RconConsoleSource(server).createCommandSourceStack().withEntity(player)
        helper.assertTrue(
            NativeAuthority.principal(rcon) == Principal.Unsupported,
            "An RCON source cannot gain authority by executing as an operator",
        )
        val unknown = console.withSource(CommandSource.NULL).withEntity(player)
        helper.assertTrue(
            NativeAuthority.principal(unknown) == Principal.Unsupported,
            "An unknown source cannot borrow its entity's authority",
        )
        helper.setBlock(BlockPos(0, 1, 0), Blocks.COMMAND_BLOCK)
        val block = helper.getBlockEntity(BlockPos(0, 1, 0), CommandBlockEntity::class.java)
        val blockSource =
            block.commandBlock
                .createCommandSourceStack(helper.level, CommandSource.NULL)
                .withEntity(player)
        helper.assertTrue(
            NativeAuthority.principal(blockSource) == Principal.Unsupported,
            "Command blocks must not gain Conclave authority",
        )
        server.playerList.deop(profile)
        helper.assertFalse(
            AuthorityPolicy.operator(NativeAuthority.principal(player.createCommandSourceStack())),
            "Native de-op must take effect immediately",
        )
        server.playerList.remove(player)
        helper.succeed()
    }

    @GameTest(maxTicks = 200)
    fun delayed_grant_cannot_undo_a_newer_revocation(helper: GameTestHelper) {
        val session =
            checkNotNull(ServerSession.get(helper.level.server)) {
                "World session did not initialize"
            }
        helper.assertTrue(
            session.failure == null,
            "Administrative storage must open in the native world",
        )
        val player = UUID.randomUUID()
        val grant = CompletableFuture<Boolean>()
        val revoke = CompletableFuture<Boolean>()
        session.setGameMaster("console", player, true, grant::complete)
        session.setGameMaster("console", player, false, revoke::complete)
        helper.assertFalse(
            player in session.gameMasters,
            "Revocation must close live access before storage completes",
        )
        val durable = session.authority.members()
        helper.succeedWhen {
            helper.assertTrue(
                grant.isDone && revoke.isDone && durable.isDone,
                "Waiting for durable administrative operations",
            )
            helper.assertTrue(
                grant.join() && revoke.join(),
                "Both administrative operations must commit",
            )
            helper.assertFalse(
                player in session.gameMasters,
                "An older grant completion must not restore access",
            )
            helper.assertFalse(
                player in durable.join(),
                "Durable GM membership must retain the revocation",
            )
        }
    }
}
