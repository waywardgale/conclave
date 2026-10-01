package dev.conclave.fabric

import dev.conclave.core.SimulationDuration
import java.util.UUID
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer

/** Native world simulation time persists across shutdown and advances while a player is offline. */
data class RevivalProtection(val began: Long, val until: Long) {
    init {
        require(began >= 0 && until > began)
    }
}

interface ConclavePlayerProtection {
    fun conclaveProtection(): RevivalProtection?

    fun conclaveProtection(value: RevivalProtection?)
}

data class ProtectionPayload(val remaining: Long) : CustomPacketPayload {
    init {
        require(remaining >= 0)
    }

    override fun type() = TYPE

    companion object {
        val TYPE =
            CustomPacketPayload.Type<ProtectionPayload>(
                Identifier.fromNamespaceAndPath("conclave", "revival_protection")
            )
        val CODEC =
            object : StreamCodec<RegistryFriendlyByteBuf, ProtectionPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf) =
                    ProtectionPayload(buffer.readVarLong())

                override fun encode(buffer: RegistryFriendlyByteBuf, value: ProtectionPayload) {
                    buffer.writeVarLong(value.remaining)
                }
            }
    }
}

internal class NativeRevivalProtection(private val session: ServerSession) {
    private data class Sent(val native: ServerPlayer, val remaining: Long)

    private val sent = mutableMapOf<UUID, Sent>()

    fun tick() {
        val online = session.server.playerList.players
        sent.keys.retainAll(online.map { it.uuid }.toSet())
        for (player in online) {
            val next = Sent(player, remaining(player))
            if (
                sent[player.uuid] != next &&
                    ServerPlayNetworking.canSend(player, ProtectionPayload.TYPE)
            ) {
                sent[player.uuid] = next
                ServerPlayNetworking.send(player, ProtectionPayload(next.remaining))
            }
        }
    }

    companion object {
        fun register() {
            PayloadTypeRegistry.clientboundPlay()
                .register(ProtectionPayload.TYPE, ProtectionPayload.CODEC)
        }

        fun grant(player: ServerPlayer, duration: SimulationDuration) {
            (player as ConclavePlayerProtection).conclaveProtection(capture(player, duration))
        }

        fun capture(player: ServerPlayer, duration: SimulationDuration): RevivalProtection? {
            val now = player.level().server.overworld().gameTime
            return if (duration.ticks == 0L) null
            else RevivalProtection(now, Math.addExact(now, duration.ticks))
        }

        fun clear(player: ServerPlayer) {
            (player as ConclavePlayerProtection).conclaveProtection(null)
        }

        fun remaining(player: ServerPlayer): Long {
            val access = player as ConclavePlayerProtection
            val protection = access.conclaveProtection() ?: return 0
            val now = player.level().server.overworld().gameTime
            if (!player.isAlive || now >= protection.until) {
                access.conclaveProtection(null)
                return 0
            }
            return protection.until - now
        }
    }
}

/**
 * Entry points are native accepted actions; damage attribution and old projectiles never call
 * these.
 */
object RevivalProtectionHooks {
    @JvmStatic
    fun prevent(player: ServerPlayer): Boolean = NativeRevivalProtection.remaining(player) > 0

    @JvmStatic
    fun hostile(player: net.minecraft.world.entity.LivingEntity) {
        if (player is ServerPlayer) NativeRevivalProtection.clear(player)
    }

    @JvmStatic
    fun stab(player: ServerPlayer) {
        val protection = (player as ConclavePlayerProtection).conclaveProtection() ?: return
        // A held kinetic attack that predates this revival is not new hostile input.
        val beganUse = player.level().server.overworld().gameTime - player.ticksUsingItem
        if (!player.isUsingItem || beganUse >= protection.began) hostile(player)
    }

    @JvmStatic
    fun administrativeKill(
        source: net.minecraft.commands.CommandSourceStack,
        entities: Collection<net.minecraft.world.entity.Entity>,
    ) {
        if (dev.conclave.core.AuthorityPolicy.operator(NativeAuthority.principal(source)))
            entities.filterIsInstance<ServerPlayer>().forEach(NativeRevivalProtection::clear)
    }
}
