package dev.conclave.fabric

import dev.conclave.core.HealthPercentage
import java.math.BigDecimal
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.phys.Vec3

class RevivalNativeTest {
    @GameTest(maxTicks = 200)
    fun recovery_receipt_is_confirmed_in_the_actual_native_player_file(helper: GameTestHelper) {
        val session = checkNotNull(ServerSession.get(helper.level.server))
        val player = helper.makeMockServerPlayerInLevel()
        val operation = java.util.UUID.randomUUID()
        val confirmation = session.playerSaves.confirm(player, operation)
        helper
            .startSequence()
            .thenWaitUntil {
                helper.assertTrue(
                    confirmation.isDone,
                    "Waiting for the native player file to be confirmed and forced",
                )
            }
            .thenExecute {
                confirmation.join()
                val saved =
                    session.server.playerList
                        .loadPlayerData(net.minecraft.server.players.NameAndId(player.gameProfile))
                        .orElseThrow()
                helper.assertTrue(
                    saved.getStringOr(NativePlayerSave.RECEIPT_TAG, "") == operation.toString(),
                    "Native loading must read the committed recovery receipt",
                )
                session.server.playerList.remove(player)
            }
            .thenSucceed()
    }

    @GameTest(maxTicks = 200)
    fun native_revival_preserves_both_inventory_gamerules_and_cannot_repeat(
        helper: GameTestHelper
    ) {
        val level = helper.level
        val server = level.server
        val original = level.gameRules.get(GameRules.KEEP_INVENTORY)
        val feet = helper.absolutePos(BlockPos(1, 1, 1))
        for (x in -1..1) for (z in -1..1) level.setBlockAndUpdate(
            feet.offset(x, -1, z),
            Blocks.STONE.defaultBlockState(),
        )
        level.setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState())
        level.setBlockAndUpdate(feet.above(), Blocks.AIR.defaultBlockState())
        val destination = NativeDestination(level, Vec3.atBottomCenterOf(feet), 75f, 0f)
        helper
            .startSequence()
            .thenWaitUntil {
                helper.assertTrue(
                    level.areEntitiesActuallyLoadedAndTicking(
                        net.minecraft.world.level.ChunkPos(feet.x shr 4, feet.z shr 4)
                    ),
                    "Waiting for native placement readiness",
                )
            }
            .thenExecute {
                try {
                    for (keep in listOf(false, true)) {
                        level.gameRules.set(GameRules.KEEP_INVENTORY, keep, server)
                        var player = helper.makeMockServerPlayerInLevel()
                        player.setGameMode(GameType.SURVIVAL)
                        player.teleportTo(
                            destination.position.x,
                            destination.position.y,
                            destination.position.z,
                        )
                        player.connection.handleAcceptPlayerLoad(
                            net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket()
                        )
                        player.inventory.setItem(0, ItemStack(Items.DIAMOND, 7))
                        player.giveExperiencePoints(100)
                        val oldXp = player.totalExperience
                        val dropsBefore =
                            level
                                .getEntitiesOfClass(
                                    ItemEntity::class.java,
                                    player.boundingBox.inflate(3.0),
                                )
                                .sumOf { it.item.count }
                        player.hurtServer(
                            level,
                            player.damageSources().genericKill(),
                            Float.MAX_VALUE,
                        )
                        helper.assertFalse(player.isAlive, "Death must run before revival")
                        val dropsAfterDeath =
                            level
                                .getEntitiesOfClass(
                                    ItemEntity::class.java,
                                    player.boundingBox.inflate(3.0),
                                )
                                .sumOf { it.item.count }
                        helper.assertTrue(
                            dropsAfterDeath - dropsBefore == if (keep) 0 else 7,
                            "Vanilla must drop inventory exactly once according to the gamerule",
                        )
                        val previous = player
                        val previousRecovery = java.util.UUID.randomUUID()
                        (previous as ConclavePlayerReceipt).conclaveRecoveryReceipt(
                            previousRecovery
                        )
                        player =
                            checkNotNull(
                                NativeRespawn.revive(
                                    player,
                                    destination,
                                    HealthPercentage(BigDecimal(50)),
                                )
                            )
                        helper.assertTrue(
                            player !== previous && player.connection === previous.connection,
                            "Revival must replace only the native body on the same connection",
                        )
                        helper.assertTrue(
                            player.connection.player === player,
                            "Incoming packets must address the replacement body",
                        )
                        helper.assertTrue(
                            (player as ConclavePlayerReceipt).conclaveRecoveryReceipt() ==
                                previousRecovery,
                            "Native body replacement must retain a prior confirmed recovery receipt",
                        )
                        helper.assertTrue(
                            player.health == player.maxHealth / 2f,
                            "Captured revival health must be applied",
                        )
                        helper.assertTrue(
                            player.position() == destination.position,
                            "Revival must use the prepared grave destination directly",
                        )
                        helper.assertTrue(
                            player.inventory.getItem(0).count == if (keep) 7 else 0,
                            "Revival must not copy an inventory dropped by vanilla",
                        )
                        helper.assertTrue(
                            player.totalExperience == if (keep) oldXp else 0,
                            "Revival must not restore already dropped XP",
                        )
                        helper.assertTrue(
                            level
                                .getEntitiesOfClass(
                                    ItemEntity::class.java,
                                    player.boundingBox.inflate(3.0),
                                )
                                .sumOf { it.item.count } == dropsAfterDeath,
                            "Revival must not produce another death's drops",
                        )
                        helper.assertTrue(
                            NativeRespawn.revive(previous, destination, HealthPercentage()) == null,
                            "A stale body cannot respawn again",
                        )
                        helper.assertTrue(
                            NativeRespawn.revive(player, destination, HealthPercentage()) == null,
                            "A living player cannot revive again",
                        )
                        player.connection.handleAcceptPlayerLoad(
                            net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket()
                        )
                        helper.assertTrue(
                            player.hurtServer(level, player.damageSources().generic(), 1f),
                            "Zero revival protection must permit ordinary damage",
                        )
                        server.playerList.remove(player)
                    }
                } finally {
                    level.gameRules.set(GameRules.KEEP_INVENTORY, original, server)
                }
            }
            .thenSucceed()
    }

    @GameTest
    fun placement_rejects_danger_and_obstruction_without_mutating_the_world(
        helper: GameTestHelper
    ) {
        val player = helper.makeMockServerPlayerInLevel()
        val level = helper.level
        val feet = helper.absolutePos(BlockPos(1, 1, 1))
        val destination = NativeDestination(level, Vec3.atBottomCenterOf(feet), 0f, 0f)
        level.setBlockAndUpdate(feet.below(), Blocks.STONE.defaultBlockState())
        level.setBlockAndUpdate(feet, Blocks.STONE.defaultBlockState())
        helper.assertFalse(
            NativePlayerPlacement.usable(player, destination),
            "Obstructed placement must fail",
        )
        helper.assertTrue(
            level.getBlockState(feet).`is`(Blocks.STONE),
            "Placement validation must not clear blocks",
        )
        level.setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState())
        level.setBlockAndUpdate(feet.above(), Blocks.AIR.defaultBlockState())
        level.setBlockAndUpdate(feet.below(), Blocks.MAGMA_BLOCK.defaultBlockState())
        helper.assertFalse(
            NativePlayerPlacement.usable(player, destination),
            "Damaging footing must fail",
        )
        level.server.playerList.remove(player)
        helper.succeed()
    }
}
