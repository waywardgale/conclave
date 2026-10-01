package dev.conclave.fabric

import dev.conclave.core.*
import java.util.UUID
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.block.state.BlockState

/** Joins fresh client intent to the actual server Use hook before any native cancellation. */
internal class NativeInteractions(private val session: ServerSession) : AutoCloseable {
    private data class Offer(
        val id: UUID,
        val attempt: UUID,
        val position: BlockPos,
        val target: GestureTarget,
        val claims: List<InteractionClaim>,
        var refreshed: Long,
    ) {
        fun payload() =
            InteractionOfferPayload(
                id,
                position,
                claims.any { it.consume },
                claims.any { it.hold.ticks > 0 },
            )
    }

    private class Press(val offer: Offer, val lease: GestureLease) {
        val gesture = UUID.randomUUID()
        var dispatched = false
        var engine: AttemptEngine? = null
        var claims: List<InteractionClaim> = emptyList()
        var progress: InteractionProgressPayload? = null

        fun interrupt() {
            engine?.releaseInteraction(claims)
            claims = emptyList()
            engine = null
        }
    }

    private class Input(val native: ServerPlayer) {
        val gestures = GestureSession(UUID.randomUUID())
        var offer: Offer? = null
        var press: Press? = null
        var requests = 0
    }

    private val inputs = mutableMapOf<UUID, Input>()
    private var requests = 0

    fun accept(player: ServerPlayer, payload: InteractionInputPayload) {
        check(session.server.isSameThread)
        // ASVS 2.2.2, 2.3.1, 8.3.1: identity, sequence, target and current eligibility are
        // server-owned.
        if (
            session.server.playerList.getPlayer(player.uuid) !== player ||
                !player.connection.isAcceptingMessages
        )
            return
        if (requests >= 1024) return
        val existing = inputs[player.uuid]
        if (existing != null && existing.native !== player) disconnected(existing.native)
        if (inputs.size >= 1024 && player.uuid !in inputs) return
        val input = inputs.getOrPut(player.uuid) { Input(player) }
        if (input.requests >= 16) return
        input.requests++
        requests++
        val now = System.nanoTime()
        when (payload.action) {
            InteractionAction.LOOK -> {
                if (input.press != null) return
                val context = session.runtime.interaction(player)
                val handles =
                    context?.world?.blocks?.handles(player.level(), payload.position).orEmpty()
                val claims =
                    if (handles.size in 1..128 && context != null)
                        context.engine.interactionClaims(player.uuid, UUID.randomUUID(), handles)
                    else emptyList()
                val old = input.offer
                val offer =
                    if (claims.isEmpty() || context == null) null
                    else {
                        val target =
                            GestureTarget(
                                NativeBlockTargets.physical(player.level(), payload.position),
                                handles.first().identity,
                            )
                        if (
                            old != null &&
                                old.attempt == context.engine.attempt &&
                                old.position == payload.position &&
                                old.target == target &&
                                old.claims.map { Triple(it.mechanic, it.consume, it.hold) } ==
                                    claims.map { Triple(it.mechanic, it.consume, it.hold) }
                        ) {
                            old.refreshed = now
                            old
                        } else
                            Offer(
                                UUID.randomUUID(),
                                context.engine.attempt,
                                payload.position.immutable(),
                                target,
                                claims,
                                now,
                            )
                    }
                input.offer = offer
                if (offer != null || old != null)
                    send(player, offer?.payload() ?: InteractionOfferPayload(null))
            }
            InteractionAction.PRESS -> {
                val offer =
                    input.offer?.takeIf {
                        it.id == payload.offer &&
                            it.position == payload.position &&
                            now - it.refreshed < FRESHNESS
                    } ?: return
                val lease =
                    input.gestures.press(
                        input.gestures.connection,
                        payload.sequence,
                        offer.target,
                        now,
                    ) ?: return
                input.press = Press(offer, lease)
                input.offer = null
                // No credit here. Earlier native cancellation must still be able to prevent
                // admission.
            }
            InteractionAction.CONTINUE,
            InteractionAction.RELEASE -> {
                val press =
                    input.press?.takeIf {
                        it.offer.id == payload.offer &&
                            it.lease.sequence == payload.sequence &&
                            it.offer.position == payload.position
                    } ?: return
                if (payload.action == InteractionAction.RELEASE) {
                    press.interrupt()
                    input.gestures.release(press.lease)
                    input.press = null
                } else {
                    if (!input.gestures.continuation(press.lease, press.offer.target, now)) {
                        press.interrupt()
                        input.press = null
                    }
                }
            }
        }
    }

    fun use(player: ServerPlayer, position: BlockPos): Boolean {
        check(session.server.isSameThread)
        val input = inputs[player.uuid]?.takeIf { it.native === player } ?: return false
        val press = input.press ?: return false
        val now = System.nanoTime()
        if (input.gestures.expire(now)) {
            press.interrupt()
            input.press = null
            return false
        }
        val physical = NativeBlockTargets.physical(player.level(), position)
        if (physical != press.offer.target.physical) return false
        if (!press.dispatched) {
            press.dispatched = true
            val context =
                session.runtime.interaction(player)?.takeIf {
                    it.engine.attempt == press.offer.attempt
                }
            if (context != null) {
                val targets =
                    context.world.blocks.handles(player.level(), position).filter {
                        it.identity == press.offer.target.generation
                    }
                val allowed = press.offer.claims.mapTo(hashSetOf()) { it.mechanic }
                val claims =
                    context.engine.interactionClaims(player.uuid, press.gesture, targets).filter {
                        it.mechanic in allowed
                    }
                press.engine = context.engine
                press.claims = context.engine.admitInteraction(claims)
            }
            input.gestures.admit(
                press.lease,
                press.claims.map { GestureRecipient(recipient(it), it.consume) },
            )
            progress(input)
        }
        return input.gestures.consumes(physical, now)
    }

    /** Item-only fallback belongs to the same consumed gesture only while aiming at its block. */
    fun consumesItem(player: ServerPlayer): Boolean {
        val input = inputs[player.uuid]?.takeIf { it.native === player } ?: return false
        val press = input.press ?: return false
        val now = System.nanoTime()
        if (input.gestures.expire(now)) {
            press.interrupt()
            input.press = null
            return false
        }
        return input.gestures.consumes(
            NativeBlockTargets.physical(player.level(), press.offer.position),
            now,
        ) && NativeBlockTargets.aimed(player, press.offer.position)
    }

    fun tick() {
        check(session.server.isSameThread)
        requests = 0
        val now = System.nanoTime()
        for (input in inputs.values.toList()) {
            input.requests = 0
            if (
                session.server.playerList.getPlayer(input.native.uuid) !== input.native ||
                    !input.native.connection.isAcceptingMessages
            ) {
                disconnected(input.native)
                continue
            }
            val press = input.press
            if (press != null) {
                if (input.gestures.expire(now)) {
                    press.interrupt()
                    input.press = null
                } else if (press.engine?.state is ProgressionState.Ended) {
                    press.interrupt()
                    input.gestures.interrupt()
                }
                if (press.dispatched && session.server.tickCount % 4 == 0) progress(input)
            }
            if (input.offer?.let { now - it.refreshed >= FRESHNESS } == true) {
                input.offer = null
                send(input.native, InteractionOfferPayload(null))
            }
        }
    }

    fun disconnected(player: ServerPlayer) {
        val input = inputs[player.uuid]?.takeIf { it.native === player } ?: return
        input.press?.interrupt()
        input.gestures.close()
        inputs.remove(player.uuid)
    }

    override fun close() {
        for (input in inputs.values.toList()) disconnected(input.native)
    }

    private fun send(player: ServerPlayer, payload: InteractionOfferPayload) {
        if (ServerPlayNetworking.canSend(player, InteractionOfferPayload.TYPE))
            ServerPlayNetworking.send(player, payload)
    }

    private fun progress(input: Input) {
        val press = input.press ?: return
        val longest =
            press.engine?.interactionProgress(press.claims)?.maxByOrNull {
                it.required.ticks - it.elapsed.ticks
            }
        val payload =
            InteractionProgressPayload(
                press.offer.id,
                longest != null,
                longest?.elapsed?.ticks ?: 0,
                longest?.required?.ticks ?: 0,
            )
        if (payload == press.progress) return
        press.progress = payload
        if (ServerPlayNetworking.canSend(input.native, InteractionProgressPayload.TYPE))
            ServerPlayNetworking.send(input.native, payload)
    }

    companion object {
        private const val FRESHNESS = 1_000_000_000L

        private fun recipient(claim: InteractionClaim) =
            UUID.nameUUIDFromBytes(
                "${claim.mechanic.attempt}:${claim.mechanic.activation}".toByteArray(Charsets.UTF_8)
            )

        fun register() {
            PayloadTypeRegistry.serverboundPlay()
                .register(InteractionInputPayload.TYPE, InteractionInputPayload.CODEC)
            PayloadTypeRegistry.clientboundPlay()
                .register(InteractionOfferPayload.TYPE, InteractionOfferPayload.CODEC)
            PayloadTypeRegistry.clientboundPlay()
                .register(InteractionProgressPayload.TYPE, InteractionProgressPayload.CODEC)
            ServerPlayNetworking.registerGlobalReceiver(InteractionInputPayload.TYPE) {
                payload,
                context ->
                ServerSession.get(context.server())?.interactions?.accept(context.player(), payload)
            }
            UseBlockCallback.EVENT.register { player, _, _, hit ->
                if (
                    player is ServerPlayer &&
                        ServerSession.get(player.level().server)
                            ?.interactions
                            ?.use(player, hit.blockPos) == true
                )
                    InteractionResult.CONSUME
                else InteractionResult.PASS
            }
            UseItemCallback.EVENT.register { player, _, _ ->
                if (
                    player is ServerPlayer &&
                        ServerSession.get(player.level().server)
                            ?.interactions
                            ?.consumesItem(player) == true
                )
                    InteractionResult.CONSUME
                else InteractionResult.PASS
            }
            ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, _, _, taken, blocked ->
                if (entity is ServerPlayer && taken > 0 && !blocked)
                    ServerSession.get(entity.level().server)
                        ?.runtime
                        ?.interaction(entity)
                        ?.engine
                        ?.interactionDamage(entity.uuid)
            }
        }
    }
}

object InteractionNativeHooks {
    @JvmStatic
    fun blockChanged(
        level: ServerLevel,
        position: BlockPos,
        before: BlockState,
        after: BlockState,
    ) {
        if (level.server.isSameThread)
            ServerSession.get(level.server)?.runtime?.blockChanged(level, position, before, after)
    }
}
