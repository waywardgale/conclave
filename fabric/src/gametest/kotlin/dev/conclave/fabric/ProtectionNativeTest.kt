package dev.conclave.fabric

import dev.conclave.core.SimulationDuration
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType

class ProtectionNativeTest {
    @GameTest(maxTicks = 200)
    fun damage_is_prevented_before_absorption_but_accepted_hostile_input_and_operator_kill_end_protection(
        helper: GameTestHelper
    ) {
        val level = helper.level
        val player = helper.makeMockServerPlayerInLevel()
        val feet = helper.absolutePos(BlockPos(1, 1, 1))
        player.setGameMode(GameType.SURVIVAL)
        player.snapTo(feet.x + .5, feet.y.toDouble(), feet.z + .5)
        player.connection.handleAcceptPlayerLoad(
            net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket()
        )
        player.getAttribute(Attributes.MAX_ABSORPTION)!!.baseValue = 8.0
        player.absorptionAmount = 8f
        NativeRevivalProtection.grant(player, SimulationDuration(100))
        val initial = player.health
        val sources = player.damageSources()
        for (source in
            listOf(
                sources.generic(),
                sources.onFire(),
                sources.lava(),
                sources.drown(),
                sources.inWall(),
                sources.fall(),
                sources.fellOutOfWorld(),
                sources.genericKill(),
            )) {
            check(!player.hurtServer(level, source, Float.MAX_VALUE))
            check(player.health == initial && player.absorptionAmount == 8f && player.isAlive) {
                "Protection consumed health or absorption for $source"
            }
        }
        player.swing(InteractionHand.MAIN_HAND)
        check(RevivalProtectionHooks.prevent(player)) { "An empty swing ended protection" }
        val trident = ItemStack(Items.TRIDENT)
        check(
            !Items.TRIDENT.releaseUsing(
                trident,
                level,
                player,
                Items.TRIDENT.getUseDuration(trident, player) - 1,
            )
        )
        check(RevivalProtectionHooks.prevent(player)) { "An unfinished charge ended protection" }
        player.setItemInHand(InteractionHand.MAIN_HAND, trident)
        check(
            Items.TRIDENT.releaseUsing(
                trident,
                level,
                player,
                Items.TRIDENT.getUseDuration(trident, player) - 20,
            )
        )
        check(!RevivalProtectionHooks.prevent(player)) {
            "An accepted trident release retained protection"
        }
        NativeRevivalProtection.grant(player, SimulationDuration(100))
        val bow = ItemStack(Items.BOW)
        player.setItemInHand(InteractionHand.MAIN_HAND, bow)
        player.inventory.setItem(1, ItemStack(Items.ARROW, 5))
        check(
            Items.BOW.releaseUsing(bow, level, player, Items.BOW.getUseDuration(bow, player) - 20)
        )
        check(!RevivalProtectionHooks.prevent(player)) { "A bow release retained protection" }
        NativeRevivalProtection.grant(player, SimulationDuration(100))
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.SNOWBALL, 2))
        Items.SNOWBALL.use(level, player, InteractionHand.MAIN_HAND)
        check(!RevivalProtectionHooks.prevent(player))
        NativeRevivalProtection.grant(player, SimulationDuration(100))
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
        val target = helper.spawn(EntityTypes.PIG, BlockPos(1, 1, 2))
        target.isInvulnerable = true
        player.connection.handleAttack(
            net.minecraft.network.protocol.game.ServerboundAttackPacket(Int.MAX_VALUE)
        )
        check(RevivalProtectionHooks.prevent(player)) {
            "An unknown attack target ended protection"
        }
        player.connection.handleAttack(
            net.minecraft.network.protocol.game.ServerboundAttackPacket(target.id)
        )
        check(!RevivalProtectionHooks.prevent(player)) {
            "An accepted attack against an immune target retained protection"
        }
        NativeRevivalProtection.grant(player, SimulationDuration(100))
        check(!player.hurtServer(level, sources.genericKill(), Float.MAX_VALUE))
        level.server.commands.performPrefixedCommand(
            level.server.createCommandSourceStack(),
            "kill ${player.uuid}",
        )
        check(
            !player.isAlive && (player as ConclavePlayerProtection).conclaveProtection() == null
        ) {
            "The authorized kill command was blocked"
        }
        level.server.playerList.remove(player)
        helper.succeed()
    }

    @GameTest(maxTicks = 200)
    fun saved_protection_keeps_its_original_expiry_and_counts_offline_simulation_time(
        helper: GameTestHelper
    ) {
        val level = helper.level
        val server = level.server
        var player = helper.makeMockServerPlayerInLevel()
        player.setGameMode(GameType.SURVIVAL)
        NativeRevivalProtection.grant(player, SimulationDuration(8))
        val original = (player as ConclavePlayerProtection).conclaveProtection()
        val profile = player.gameProfile
        server.playerList.remove(player)
        helper
            .startSequence()
            .thenExecuteAfter(3) {
                val cookie =
                    net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)
                player =
                    net.minecraft.server.level.ServerPlayer(
                        server,
                        level,
                        profile,
                        cookie.clientInformation(),
                    )
                player.load(
                    net.minecraft.world.level.storage.TagValueInput.create(
                        net.minecraft.util.ProblemReporter.DISCARDING,
                        server.registryAccess(),
                        server.playerList
                            .loadPlayerData(net.minecraft.server.players.NameAndId(profile))
                            .orElseThrow(),
                    )
                )
                check((player as ConclavePlayerProtection).conclaveProtection() == original)
                val connection =
                    net.minecraft.network.Connection(
                        net.minecraft.network.protocol.PacketFlow.SERVERBOUND
                    )
                io.netty.channel.embedded.EmbeddedChannel(connection)
                server.playerList.placeNewPlayer(connection, player, cookie)
                player.setGameMode(GameType.SURVIVAL)
                player.connection.handleAcceptPlayerLoad(
                    net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket()
                )
                check(NativeRevivalProtection.remaining(player) in 1..7)
                check(!player.hurtServer(level, player.damageSources().generic(), 1f))
            }
            .thenExecuteAfter(10) {
                check(NativeRevivalProtection.remaining(player) == 0L)
                check(player.hurtServer(level, player.damageSources().generic(), 1f))
                server.playerList.remove(player)
            }
            .thenSucceed()
    }
}
