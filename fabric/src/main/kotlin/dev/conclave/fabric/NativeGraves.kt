package dev.conclave.fabric

import dev.conclave.core.*
import java.util.UUID
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/** Native death runs first. This module owns only revival decisions and grave presentation. */
internal class NativeGraves(private val session: ServerSession) : AutoCloseable {
    data class AdministrativeTarget(val attempt: UUID, val player: ServerPlayer, val grave: UUID)

    data class AdministrativeResult(val revived: Boolean, val message: String)

    fun administrativeTarget(attempt: UUID, player: ServerPlayer): AdministrativeTarget? {
        check(session.server.isSameThread)
        val grave = graves[player.uuid] ?: return null
        if (
            grave.lifecycle.attempt != attempt ||
                grave.lifecycle.status == GraveStatus.CLOSED ||
                session.server.playerList.getPlayer(player.uuid) !== player ||
                player.hasDisconnected() ||
                session.runtime.deathContext(player.uuid)?.attempt != attempt
        )
            return null
        return AdministrativeTarget(attempt, player, grave.lifecycle.grave)
    }

    fun reviveAdministrative(target: AdministrativeTarget): AdministrativeResult {
        val current = administrativeTarget(target.attempt, target.player)
        if (current != target)
            return AdministrativeResult(
                false,
                "The selected death or connection changed. Choose its current target again.",
            )
        val grave = graves.getValue(target.player.uuid)
        return try {
            if (revive(grave, RevivalMethod.ADMINISTRATIVE)) {
                val participation =
                    grave.context!!
                        .world
                        .participantFacts(target.player.uuid)
                        .participation!!
                        .name
                        .lowercase()
                AdministrativeResult(
                    true,
                    "${grave.name} revived in ${target.attempt}; participation remains $participation.",
                )
            } else
                AdministrativeResult(
                    false,
                    grave.message.ifEmpty {
                        "The captured grave is not ready for safe revival. Try again when its placement is available."
                    },
                )
        } catch (failure: Exception) {
            session.runtime.interrupt(
                target.attempt,
                "Administrative revival could not complete safely.",
            )
            throw failure
        }
    }

    private class Grave(
        val lifecycle: GraveLifecycle,
        val context: NativeDeathContext?,
        val catalog: CompiledCatalog?,
        val death: NativeDestination,
        val destinations: List<NativeDestination>,
        val name: String,
    ) {
        var visual: GraveVisual? = null
        var assistant: AssistanceIdentity? = null
        var destinationIndex = 0
        var exact = true
        var candidates: RecoveryCandidates? = null
        var retryAt = 0L
        var message = "Finding safe footing for your grave."
    }

    private class Input(val native: ServerPlayer) {
        val gesture = GestureSession(UUID.randomUUID())
        var lease: GestureLease? = null
        var assistant: AssistanceIdentity? = null
        var requests = 0
        var hovering: UUID? = null
        var observedAt = 0L
        var helpSent: GraveHelpPayload? = null
    }

    private val graves = linkedMapOf<UUID, Grave>()
    private val inputs = mutableMapOf<UUID, Input>()
    private val standing = mutableMapOf<UUID, NativeDestination>()
    private val visuals = GraveVisuals(session)
    private val sent = mutableMapOf<UUID, Pair<ServerPlayer, GraveStatePayload>>()
    private val replacing = mutableSetOf<UUID>()
    private var outsideTick = 0L
    private var frameRequests = 0
    private var searchCursor = 0
    private var standingCursor = 0

    fun lifecycles(attempt: UUID) =
        graves.values.map { it.lifecycle }.filter { it.attempt == attempt }

    fun life(player: UUID): LifeState? =
        when (graves[player]?.lifecycle?.status) {
            GraveStatus.OPPORTUNITY -> LifeState.DEAD
            GraveStatus.PASSED_OUT -> LifeState.PASSED_OUT
            else ->
                session.server.playerList
                    .getPlayer(player)
                    ?.let { (it as ConclavePlayerViewing).conclaveViewing() }
                    ?.let { if (it.living) LifeState.ALIVE else LifeState.PASSED_OUT }
        }

    /** Native camera transport is not a change to a participant's gameplay position. */
    fun gameplayPosition(player: UUID): NativeDestination? {
        if (session.spectating.ownsCamera(player))
            graves[player]?.death?.let {
                return it
            }
        val native = session.server.playerList.getPlayer(player) ?: return null
        val saved = (native as ConclavePlayerViewing).conclaveViewing() ?: return null
        return resolved(
            saved.dimension,
            LocationPlacement(
                Position(saved.x, saved.y, saved.z),
                java.math.BigDecimal.valueOf(saved.yaw.toDouble()),
                java.math.BigDecimal.valueOf(saved.pitch.toDouble()),
            ),
        )
    }

    fun died(player: ServerPlayer) {
        check(session.server.isSameThread)
        NativeRevivalProtection.clear(player)
        if (player.uuid in graves || session.server.playerList.getPlayer(player.uuid) !== player)
            return
        interrupt(player.uuid)
        val context = session.runtime.deathContext(player.uuid)
        // A pending end-of-attempt recovery already owns the dead body.
        if (
            context == null &&
                (session.runtime.ownsPlayer(player.uuid) ||
                    session.recovery.blocksRespawn(player.uuid))
        )
            return
        val catalog = context?.catalog ?: session.catalog
        val settings = catalog?.settings ?: GameplaySettings()
        val death = session.spectating.died(player) ?: destination(player)
        val choices = mutableListOf(death)
        standing[player.uuid]
            ?.takeIf { context == null || context.world.contains(it) }
            ?.let { choices += it }
        if (context != null) {
            val world = context.world
            val pairing = world.arena.encounters.getValue(world.definition.id)
            world.definition.recovery?.locations?.forEach { id ->
                resolved(
                        world.arena.dimension,
                        world.arena.locations.getValue(pairing.location(id)).placement,
                    )
                    ?.let { choices += it }
            }
        } else {
            settings.recovery.outsideAttemptFallbacks.forEach { id ->
                catalog
                    ?.locations
                    ?.get(id)
                    ?.let { resolved(it.dimension, it.placement) }
                    ?.let { choices += it }
            }
        }
        val grave =
            Grave(
                GraveLifecycle(
                    UUID.randomUUID(),
                    player.uuid,
                    context?.attempt,
                    settings.revival,
                    context?.world?.definition?.prohibitSelfRevival ?: false,
                    context?.deathTick ?: outsideTick + 1,
                ),
                context,
                catalog,
                death,
                choices.distinct(),
                player.scoreboardName,
            )
        graves[player.uuid] = grave
        emit(grave)
        if (context != null && context.deathTick == context.world.currentTick)
            grave.lifecycle.beginStep(context.deathTick, context.world.combat)
    }

    fun beginStep(attempt: UUID, tick: Long, combat: Boolean) {
        for (grave in graves.values.filter { it.lifecycle.attempt == attempt }) grave.lifecycle
            .beginStep(tick, combat)
    }

    fun settleInteractions(attempt: UUID?) {
        for (grave in graves.values.filter { it.lifecycle.attempt == attempt }) {
            val assistant = grave.assistant ?: continue
            val input = inputs[assistant.helper]
            val helper = session.server.playerList.getPlayer(assistant.helper)
            val lease = input?.lease
            val eligible =
                input != null &&
                    helper === input.native &&
                    lease != null &&
                    input.gesture.observing(lease, grave.lifecycle.grave, System.nanoTime()) &&
                    eligible(helper, grave)
            grave.lifecycle.observeAssistance(assistant, eligible)
            if (!eligible) {
                interrupt(assistant.helper)
                continue
            }
            revive(grave, RevivalMethod.ASSISTED, assistant)
        }
    }

    fun settleExpiry(attempt: UUID) {
        for (grave in graves.values.filter { it.lifecycle.attempt == attempt }) {
            grave.lifecycle.settleExpiry()
            emit(grave)
        }
    }

    fun accept(player: ServerPlayer, payload: GraveInputPayload) {
        check(session.server.isSameThread)
        // ASVS 2.2.2, 2.3.1, 8.3.1: connection, lifecycle, timing and reach are server decisions.
        if (session.server.playerList.getPlayer(player.uuid) !== player || player.hasDisconnected())
            return
        var input = inputs[player.uuid]
        if (input?.native !== player) {
            input?.gesture?.close()
            interrupt(player.uuid)
            input = Input(player)
            inputs[player.uuid] = input
        }
        // Excess from one connection must not spend other players' admission budget.
        if (input.requests >= 16 || frameRequests >= 1024) {
            interrupt(player.uuid)
            return
        }
        input.requests++
        frameRequests++
        val target = GestureTarget(payload.grave, payload.grave)
        val lease = GestureLease(input.gesture.connection, payload.sequence, target)
        if (payload.action == GraveInputAction.RELEASE) {
            if (input.gesture.release(lease)) interrupt(player.uuid)
            return
        }
        val grave = graves.values.firstOrNull { it.lifecycle.grave == payload.grave } ?: return
        if (payload.action == GraveInputAction.LOOK) {
            input.hovering = grave.lifecycle.player
            input.observedAt = System.nanoTime()
            return
        }
        if (payload.action == GraveInputAction.CONTINUE) {
            if (
                !input.gesture.continuation(lease, target, System.nanoTime()) ||
                    !eligible(player, grave)
            )
                interrupt(player.uuid)
            return
        }
        val admitted =
            input.gesture.press(
                input.gesture.connection,
                payload.sequence,
                target,
                System.nanoTime(),
            ) ?: return
        input.lease = admitted
        if (payload.action == GraveInputAction.SELF) {
            input.gesture.admit(admitted, emptyList())
            input.gesture.release(admitted)
            if (player.uuid == grave.lifecycle.player) revive(grave, RevivalMethod.SELF)
            return
        }
        val identity = AssistanceIdentity(player.uuid, input.gesture.connection, UUID.randomUUID())
        val accepted = grave.lifecycle.beginAssistance(identity, eligible(player, grave))
        input.gesture.admit(
            admitted,
            if (accepted) listOf(GestureRecipient(grave.lifecycle.grave, true)) else emptyList(),
        )
        if (accepted) {
            input.assistant = identity
            grave.assistant = identity
            // Zero-time assistance is an immediate operation. Longer holds advance only on ticks.
            revive(grave, RevivalMethod.ASSISTED, identity)
        }
    }

    private fun eligible(helper: ServerPlayer, grave: Grave): Boolean {
        if (
            !helper.isAlive ||
                helper.isSpectator ||
                helper.uuid == grave.lifecycle.player ||
                helper.hasDisconnected()
        )
            return false
        val target = session.server.playerList.getPlayer(grave.lifecycle.player) ?: return false
        if (target.isAlive || target.hasDisconnected()) return false
        if (grave.context != null) {
            if (session.runtime.deathContext(helper.uuid)?.attempt != grave.context.attempt)
                return false
            if (
                grave.context.world.players().byId[helper.uuid]?.participation !=
                    Participation.ACTIVE
            )
                return false
        } else if (
            session.runtime.ownsPlayer(helper.uuid) || session.recovery.blocksRespawn(helper.uuid)
        )
            return false
        val visual = grave.visual ?: return false
        if (visual.destination.level !== helper.level()) return false
        val eye = helper.eyePosition
        val reach = grave.lifecycle.policy.helpReach.toDouble()
        val end = eye.add(helper.lookAngle.scale(reach))
        val hit = visual.target.boundingBox.clip(eye, end).orElse(null) ?: return false
        // A reach ray must never implicitly load terrain between helper and target.
        for (x in
            Math.floorDiv(kotlin.math.floor(minOf(eye.x, hit.x)).toInt(), 16)..Math.floorDiv(
                    kotlin.math.floor(maxOf(eye.x, hit.x)).toInt(),
                    16,
                )) for (z in
            Math.floorDiv(kotlin.math.floor(minOf(eye.z, hit.z)).toInt(), 16)..Math.floorDiv(
                    kotlin.math.floor(maxOf(eye.z, hit.z)).toInt(),
                    16,
                )) if (
            !helper
                .level()
                .areEntitiesActuallyLoadedAndTicking(net.minecraft.world.level.ChunkPos(x, z))
        )
            return false
        val obstruction =
            helper
                .level()
                .clip(
                    ClipContext(
                        eye,
                        hit,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        helper,
                    )
                )
        return obstruction.type == HitResult.Type.MISS ||
            obstruction.location.distanceToSqr(eye) + 0.000001 >= hit.distanceToSqr(eye)
    }

    private fun revive(
        grave: Grave,
        method: RevivalMethod,
        assistant: AssistanceIdentity? = null,
    ): Boolean {
        val lifecycle = grave.lifecycle
        if (
            grave.context != null &&
                session.runtime.deathContext(lifecycle.player)?.attempt != grave.context.attempt
        )
            return false
        grave.context?.world?.observe()
        val player = session.server.playerList.getPlayer(lifecycle.player) ?: return false
        val placement = grave.visual?.destination ?: return false
        val helper = assistant?.let { session.server.playerList.getPlayer(it.helper) }
        val helperEligible = helper != null && eligible(helper, grave)
        if (
            !lifecycle.canRevive(
                lifecycle.grave,
                method,
                !player.hasDisconnected(),
                true,
                assistant,
                helperEligible,
            )
        )
            return false
        if (!NativePlayerPlacement.usable(player, placement)) {
            grave.visual = null
            grave.destinationIndex = 0
            grave.exact = true
            grave.candidates = null
            grave.message = "The grave is obstructed. Finding safe footing."
            return false
        }
        val protection =
            try {
                NativeRevivalProtection.capture(player, lifecycle.policy.damageProtection)
            } catch (_: ArithmeticException) {
                grave.message =
                    "The captured protection duration exceeds the remaining simulation clock range."
                return false
            }
        val before = grave.context?.world?.participantFacts(player.uuid)
        replacing += player.uuid
        try {
            val replacement =
                if (!player.isAlive)
                    NativeRespawn.revive(player, placement, lifecycle.policy.health) ?: return false
                else {
                    val viewing = (player as ConclavePlayerViewing).conclaveViewing()
                    if (
                        method != RevivalMethod.ADMINISTRATIVE ||
                            lifecycle.status != GraveStatus.PASSED_OUT ||
                            viewing == null ||
                            viewing.attempt != lifecycle.attempt ||
                            viewing.living
                    )
                        return false
                    player.setCamera(player)
                    if (
                        !player.teleportTo(
                            placement.level,
                            placement.position.x,
                            placement.position.y,
                            placement.position.z,
                            emptySet(),
                            placement.yaw,
                            placement.pitch,
                            true,
                        )
                    )
                        return false
                    player.health =
                        lifecycle.policy.health
                            .of(player.maxHealth.toDouble())
                            .toFloat()
                            .coerceIn(Float.MIN_VALUE, player.maxHealth)
                    player
                }
            lifecycle.attempt?.let { PlayerViewingState.restore(replacement, it) }
            check(lifecycle.revive(lifecycle.grave, method, true, true, assistant, helperEligible))
            (replacement as ConclavePlayerProtection).conclaveProtection(protection)
            emit(grave, before)
        } finally {
            replacing -= player.uuid
        }
        close(grave)
        return true
    }

    fun interrupt(helper: UUID) {
        inputs[helper]?.let {
            it.gesture.interrupt()
            it.assistant = null
        }
        for (grave in graves.values) if (grave.assistant?.helper == helper) {
            grave.lifecycle.interruptAssistance(helper)
            grave.assistant = null
        }
    }

    fun disconnected(player: ServerPlayer) {
        session.spectating.disconnected(player.uuid)
        interrupt(player.uuid)
        inputs.remove(player.uuid)?.gesture?.close()
        visuals.disconnected(player.uuid)
        sent.remove(player.uuid)
    }

    fun replaced(previous: ServerPlayer, current: ServerPlayer) {
        if (current.uuid in replacing) return
        val grave = graves[current.uuid] ?: return
        if (grave.lifecycle.attempt == null && current.isAlive) {
            grave.lifecycle.normalRespawn(grave.lifecycle.grave)
            close(grave)
        }
    }

    fun endAttempt(attempt: UUID) {
        graves.values
            .filter { it.lifecycle.attempt == attempt }
            .forEach {
                it.lifecycle.endAttempt()
                close(it)
            }
    }

    private fun close(grave: Grave) {
        session.spectating.close(grave.lifecycle.player)
        grave.assistant?.let { interrupt(it.helper) }
        graves.remove(grave.lifecycle.player)
        grave.lifecycle.drainEvents()
        publish(grave.lifecycle.player, GraveStatePayload(null))
    }

    private fun emit(grave: Grave, before: PlayerObservation? = null) {
        for (event in grave.lifecycle.drainEvents()) grave.context?.world?.grave(event, before)
    }

    fun tick() {
        outsideTick++
        frameRequests = 0
        inputs.values.forEach { it.requests = 0 }
        val online = session.server.playerList.players.toList()
        for (player in online) if (!player.isAlive) died(player)
        standing.keys.retainAll(online.map { it.uuid }.toSet() + graves.keys)
        // Recording recent footing has a fixed budget independent of server player capacity.
        repeat(minOf(online.size, 32)) {
            val player = online[standingCursor.mod(online.size)]
            standingCursor = (standingCursor + 1).mod(online.size)
            if (player.isAlive && player.uuid !in graves && player.onGround()) {
                val candidate = destination(player)
                if (NativePlayerPlacement.usable(player, candidate))
                    standing[player.uuid] = candidate
            }
        }
        val outside = graves.values.filter { it.lifecycle.attempt == null }
        for (grave in outside) if (grave.lifecycle.diedAt <= outsideTick)
            grave.lifecycle.beginStep(outsideTick, false)
        settleInteractions(null)
        for (grave in outside) if (grave.lifecycle.diedAt <= outsideTick) {
            grave.lifecycle.settleExpiry()
            grave.lifecycle.drainEvents()
        }
        prepareVisuals()
        for ((id, grave) in graves.toMap()) if (
            grave.lifecycle.status == GraveStatus.PASSED_OUT && grave.context != null
        )
            session.spectating.sync(id, grave.context, grave.visual?.destination)
        visuals.update(graves.values.mapNotNull { it.visual })
        helpPrompts()
        for (player in graves.keys) publish(player, checkNotNull(view(player)))
    }

    private fun helpPrompts() {
        val now = System.nanoTime()
        for ((id, input) in inputs) {
            if (
                session.server.playerList.getPlayer(id) !== input.native ||
                    !ServerPlayNetworking.canSend(input.native, GraveHelpPayload.TYPE)
            )
                continue
            val grave =
                input.assistant?.let { identity ->
                    graves.values.firstOrNull { it.assistant == identity }
                }
                    ?: input.hovering
                        ?.takeIf { now - input.observedAt < 1_000_000_000 }
                        ?.let(graves::get)
            val state =
                if (
                    grave != null &&
                        grave.lifecycle.status == GraveStatus.OPPORTUNITY &&
                        grave.lifecycle.policy.assistanceAllowed &&
                        grave.lifecycle.view().delayRemaining.ticks == 0L &&
                        eligible(input.native, grave)
                ) {
                    val view = grave.lifecycle.view()
                    GraveHelpPayload(
                        grave.lifecycle.grave,
                        grave.name.take(64),
                        if (view.helper == id) view.helpProgress.ticks else 0,
                        grave.lifecycle.policy.helpTime.ticks,
                        view.helper == id,
                    )
                } else GraveHelpPayload(null)
            if (state != input.helpSent) {
                input.helpSent = state
                ServerPlayNetworking.send(input.native, state)
            }
        }
    }

    fun view(player: UUID): GraveStatePayload? =
        graves[player]?.let { grave ->
            val view = grave.lifecycle.view()
            GraveStatePayload(
                grave.lifecycle.grave,
                view.status,
                grave.lifecycle.attempt != null,
                view.combat,
                view.remainingCombat?.ticks ?: -1,
                view.delayRemaining.ticks,
                grave.lifecycle.policy.selfAllowed(grave.lifecycle.prohibitSelfRevival),
                grave.message,
            )
        }

    /** Round-robin, bounded native checks. No grave search loads or generates terrain. */
    private fun prepareVisuals() {
        val pending =
            graves.values.filter {
                it.visual == null &&
                    outsideTick >= it.retryAt &&
                    it.lifecycle.status != GraveStatus.CLOSED
            }
        if (pending.isEmpty()) return
        var checks = 0
        val began = System.nanoTime()
        while (checks < 32 && System.nanoTime() - began < 3_000_000 && checks < pending.size * 8) {
            val grave = pending[searchCursor.mod(pending.size)]
            searchCursor = (searchCursor + 1).mod(pending.size)
            checks++
            if (grave.visual != null || grave.retryAt > outsideTick) continue
            val player = session.server.playerList.getPlayer(grave.lifecycle.player) ?: continue
            val origin = grave.destinations.getOrNull(grave.destinationIndex)
            if (origin == null) {
                if (grave.exact) {
                    grave.exact = false
                    grave.destinationIndex = 0
                    continue
                }
                grave.retryAt = outsideTick + 20
                grave.destinationIndex = 0
                grave.exact = true
                grave.message =
                    if (grave.context == null)
                        "No safe grave location is ready. You can use normal Respawn."
                    else "No safe grave location is ready. Waiting for a safe position."
                continue
            }
            if (grave.exact) {
                grave.destinationIndex++
                if (NativePlayerPlacement.usable(player, origin)) {
                    grave.visual =
                        GraveVisual(grave.lifecycle.grave, player.uuid, grave.name, origin)
                    grave.message = ""
                }
                continue
            }
            val candidates =
                grave.candidates
                    ?: RecoveryCandidates(
                            Position(origin.position.x, origin.position.y, origin.position.z),
                            grave.catalog?.settings?.recovery?.searchRadius
                                ?: java.math.BigDecimal(3),
                        )
                        .also { grave.candidates = it }
            if (!candidates.hasNext()) {
                grave.destinationIndex++
                grave.candidates = null
                continue
            }
            val position =
                try {
                    candidates.next()
                } catch (_: IllegalStateException) {
                    grave.destinationIndex++
                    grave.candidates = null
                    continue
                }
            val candidate =
                origin.copy(
                    position =
                        Vec3(position.x.toDouble(), position.y.toDouble(), position.z.toDouble())
                )
            if (NativePlayerPlacement.usable(player, candidate)) {
                grave.visual =
                    GraveVisual(grave.lifecycle.grave, player.uuid, grave.name, candidate)
                grave.message = ""
            }
        }
    }

    private fun publish(id: UUID, state: GraveStatePayload) {
        val player = session.server.playerList.getPlayer(id) ?: return
        if (!ServerPlayNetworking.canSend(player, GraveStatePayload.TYPE)) return
        val previous = sent[id]
        if (previous?.first === player && previous.second == state) return
        sent[id] = player to state
        ServerPlayNetworking.send(player, state)
    }

    private fun destination(player: ServerPlayer) =
        NativeDestination(
            player.level(),
            player.position(),
            player.yRot,
            player.xRot.coerceIn(-90f, 90f),
        )

    private fun resolved(dimension: String, placement: LocationPlacement): NativeDestination? {
        val level =
            session.server.getLevel(
                ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension))
            ) ?: return null
        return NativeDestination(
            level,
            Vec3(
                placement.position.x.toDouble(),
                placement.position.y.toDouble(),
                placement.position.z.toDouble(),
            ),
            placement.yaw.toFloat(),
            placement.pitch.toFloat(),
        )
    }

    override fun close() {
        inputs.values.forEach { it.gesture.close() }
        inputs.clear()
        graves.clear()
        standing.clear()
        sent.clear()
    }

    companion object {
        fun register() {
            PayloadTypeRegistry.serverboundPlay()
                .register(GraveInputPayload.TYPE, GraveInputPayload.CODEC)
            PayloadTypeRegistry.clientboundPlay()
                .register(GraveStatePayload.TYPE, GraveStatePayload.CODEC)
            PayloadTypeRegistry.clientboundPlay()
                .register(GraveMarkerPayload.TYPE, GraveMarkerPayload.CODEC)
            PayloadTypeRegistry.clientboundPlay()
                .register(GraveHelpPayload.TYPE, GraveHelpPayload.CODEC)
            ServerPlayNetworking.registerGlobalReceiver(GraveInputPayload.TYPE) { payload, context
                ->
                ServerSession.get(context.server())?.graves?.accept(context.player(), payload)
            }
            ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ ->
                if (entity is ServerPlayer)
                    ServerSession.get(entity.level().server)?.graves?.died(entity)
            }
            ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, _, _, taken, blocked ->
                if (entity is ServerPlayer && taken > 0 && !blocked)
                    ServerSession.get(entity.level().server)?.graves?.interrupt(entity.uuid)
            }
        }
    }
}

/** Public mixin bridge; packet handlers enter only after the native server-thread handoff. */
object GraveNativeHooks {
    @JvmStatic
    fun blockRespawn(player: ServerPlayer): Boolean {
        val server = player.level().server
        if (!server.isSameThread) return false
        val session = ServerSession.get(server) ?: return false
        return !player.isAlive &&
            (session.runtime.ownsPlayer(player.uuid) || session.recovery.blocksRespawn(player.uuid))
    }
}
