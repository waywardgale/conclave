package dev.conclave.fabric

import dev.conclave.core.*
import java.util.UUID
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer

/** Native viewing transport is separate from the retained life and participation state. */
internal class NativeSpectating(private val session: ServerSession) {
    private class Viewing(
        val context: NativeDeathContext,
        val selection: SpectatorSelection,
        val living: Boolean = false,
    ) {
        var native: ServerPlayer? = null
        var sent: WatchStatePayload? = null
        var returnTo: NativeDestination? = null
        var requests = 0
        val returnClaim = UUID.randomUUID()
        var returning = false
        var candidates: RecoveryCandidates? = null
        var retryAt = 0
        var message = ""
    }

    private val viewers = mutableMapOf<UUID, Viewing>()
    private val cameraChanges = mutableSetOf<UUID>()

    fun cameraChangeAllowed(player: UUID) = player in cameraChanges

    fun returning(player: UUID) = viewers[player]?.returning == true

    private fun camera(player: ServerPlayer, target: net.minecraft.world.entity.Entity) {
        cameraChanges += player.uuid
        try {
            player.setCamera(target)
        } finally {
            cameraChanges -= player.uuid
        }
    }

    fun ownsCamera(player: UUID) =
        viewers[player]?.let {
            !it.living || (it.native as? ConclavePlayerViewing)?.conclaveViewing() != null
        } == true

    /** Offers controls without changing an observer's life, mode, position or admission. */
    fun tickObservers() {
        for (player in session.server.playerList.players.toList()) {
            val context = session.runtime.deathContext(player.uuid) ?: continue
            val own = context.world.players().byId[player.uuid] ?: continue
            if (own.life != LifeState.ALIVE || own.participation != Participation.OBSERVER) continue
            try {
                val viewing =
                    viewers.getOrPut(player.uuid) {
                        Viewing(
                            context,
                            SpectatorSelection(
                                player.uuid,
                                context.roster,
                                context.catalog.settings.spectating.sharePrivateInfo,
                                context.catalog.settings.spectating.mode == SpectatorMode.TEAMMATES,
                            ),
                            true,
                        )
                    }
                if (!viewing.living) continue
                if (viewing.native !== player) {
                    viewing.native = player
                    viewing.sent = null
                }
                viewing.requests = 0
                refresh(viewing)
                val saved = (player as ConclavePlayerViewing).conclaveViewing()
                if (saved != null) {
                    check(saved.attempt == context.attempt && saved.living)
                    viewing.returnTo = PlayerViewingState.destination(session.server, saved)
                    if (!viewing.selection.view().active && !viewing.returning) beginReturn(viewing)
                    if (!player.isSpectator) beginReturn(viewing)
                }
                if (viewing.returning) returnObserver(viewing)
                apply(viewing)
            } catch (failure: Exception) {
                session.runtime.interrupt(
                    context.attempt,
                    "Observer viewing failed: ${failure.message}",
                )
            }
        }
    }

    private fun retainReturn(viewing: Viewing, destination: NativeDestination): Boolean {
        if (session.chunks.owns(viewing.returnClaim)) return true
        val radius =
            viewing.context.catalog.settings.recovery.searchRadius + java.math.BigDecimal(2)
        val region =
            Geometry.Box(
                Position(
                    destination.position.x,
                    destination.position.y - 1,
                    destination.position.z,
                ),
                radius * java.math.BigDecimal(2),
                radius * java.math.BigDecimal(2),
                java.math.BigDecimal(4),
            )
        val footprint =
            GeometryEngine().use {
                ChunkFootprint.plan(region, it, NativeChunkClaims.propagationRadius)
            }
        return session.chunks.retain(
            viewing.returnClaim,
            destination.level.dimension().identifier().toString(),
            footprint,
        )
    }

    private fun enterObserver(viewing: Viewing): Boolean {
        val player = viewing.native ?: return false
        val access = player as ConclavePlayerViewing
        if (access.conclaveViewing() != null) return true
        val state =
            PlayerViewingState.capture(
                player,
                viewing.context.attempt,
                viewing.context.catalog.settings.spectating.mode,
                true,
            )
        val origin = PlayerViewingState.destination(session.server, state) ?: return false
        if (!NativePlayerPlacement.usable(player, origin)) {
            viewing.message = "Stand in a safe place before entering the view."
            return false
        }
        if (!retainReturn(viewing, origin)) {
            viewing.message = "Viewing is waiting for return-location chunk capacity."
            return false
        }
        // The attempt's durable player plan already owns recovery before native mode changes.
        access.conclaveViewing(state)
        viewing.returnTo = origin
        viewing.message = ""
        player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR)
        return true
    }

    private fun beginReturn(viewing: Viewing) {
        if (viewing.returning) return
        viewing.returning = true
        viewing.selection.leave()
        viewing.native?.let { if (it.camera !== it) camera(it, it) }
        viewing.message = "Returning to your own position."
    }

    private fun returnObserver(viewing: Viewing) {
        val player = viewing.native ?: return
        val saved = (player as ConclavePlayerViewing).conclaveViewing()
        if (saved == null) {
            viewing.returning = false
            return
        }
        val origin = viewing.returnTo ?: return
        if (
            !retainReturn(viewing, origin) ||
                !session.chunks.ready(viewing.returnClaim) ||
                session.server.tickCount < viewing.retryAt
        )
            return
        val candidates =
            viewing.candidates
                ?: RecoveryCandidates(
                        Position(origin.position.x, origin.position.y, origin.position.z),
                        viewing.context.catalog.settings.recovery.searchRadius,
                    )
                    .also { viewing.candidates = it }
        val began = System.nanoTime()
        repeat(32) {
            if (System.nanoTime() - began > 1_000_000) return
            if (!candidates.hasNext()) {
                viewing.candidates = null
                viewing.retryAt = session.server.tickCount + 20
                viewing.message =
                    "Your return position is obstructed. Waiting for a safe nearby position."
                return
            }
            val position = candidates.next()
            val destination =
                origin.copy(
                    position =
                        net.minecraft.world.phys.Vec3(
                            position.x.toDouble(),
                            position.y.toDouble(),
                            position.z.toDouble(),
                        )
                )
            if (!NativePlayerPlacement.usable(player, destination)) return@repeat
            if (
                !player.teleportTo(
                    destination.level,
                    destination.position.x,
                    destination.position.y,
                    destination.position.z,
                    emptySet(),
                    destination.yaw,
                    destination.pitch,
                    false,
                )
            )
                return@repeat
            PlayerViewingState.restore(player, viewing.context.attempt)
            session.chunks.release(viewing.returnClaim)
            viewing.returning = false
            viewing.candidates = null
            viewing.returnTo = null
            viewing.message = ""
            viewing.sent = null
            return
        }
    }

    /** Actual death while voluntarily viewing retains its logical world position. */
    fun died(player: ServerPlayer): NativeDestination? {
        val saved =
            (player as ConclavePlayerViewing).conclaveViewing()?.takeIf { it.living } ?: return null
        val position = PlayerViewingState.destination(session.server, saved)
        viewers.remove(player.uuid)?.let {
            it.selection.close()
            session.chunks.release(it.returnClaim)
        }
        PlayerViewingState.restore(player, saved.attempt)
        return position
    }

    fun endAttempt(attempt: UUID) {
        viewers.filterValues { it.context.attempt == attempt }.keys.toList().forEach(::close)
    }

    fun sync(player: UUID, context: NativeDeathContext, destination: NativeDestination?) {
        try {
            syncPassedOut(player, context, destination)
        } catch (failure: Exception) {
            session.runtime.interrupt(
                context.attempt,
                "Spectator viewing failed: ${failure.message}",
            )
        }
    }

    private fun syncPassedOut(
        player: UUID,
        context: NativeDeathContext,
        destination: NativeDestination?,
    ) {
        var native = session.server.playerList.getPlayer(player) ?: return
        if (
            session.graves.life(player) != LifeState.PASSED_OUT ||
                session.runtime.deathContext(player)?.attempt != context.attempt
        )
            return
        val access = native as ConclavePlayerViewing
        val retained = access.conclaveViewing()
        if (retained == null) {
            val landing = destination ?: return
            if (!NativePlayerPlacement.usable(native, landing)) return
            access.conclaveViewing(
                PlayerViewingState.capture(
                    native,
                    context.attempt,
                    context.catalog.settings.spectating.mode,
                )
            )
            cameraChanges += player
            try {
                if (!native.isAlive) {
                    val replacement = NativeRespawn.revive(native, landing, HealthPercentage())
                    if (replacement == null) {
                        access.conclaveViewing(null)
                        return
                    }
                    native = replacement
                }
                if (session.runtime.deathContext(player)?.attempt != context.attempt) return
                native.setGameMode(net.minecraft.world.level.GameType.SPECTATOR)
            } finally {
                cameraChanges -= player
            }
        } else if (retained.attempt != context.attempt || !native.isAlive || !native.isSpectator) {
            session.runtime.interrupt(
                context.attempt,
                "The owned spectator body changed outside its supported viewing lifecycle",
            )
            return
        }
        val viewing =
            viewers.getOrPut(player) {
                Viewing(
                    context,
                    SpectatorSelection(
                        player,
                        context.roster,
                        context.catalog.settings.spectating.sharePrivateInfo,
                    ),
                )
            }
        check(viewing.context.attempt == context.attempt)
        if (viewing.native !== native) {
            viewing.native = native
            viewing.sent = null
        }
        viewing.returnTo = destination
        viewing.requests = 0
        refresh(viewing)
        apply(viewing)
    }

    private fun refresh(viewing: Viewing) {
        val available =
            viewing.context.roster.filterTo(hashSetOf()) { id ->
                val target = session.server.playerList.getPlayer(id)
                target != null &&
                    target.isAlive &&
                    !target.hasDisconnected() &&
                    target.level().areEntitiesActuallyLoadedAndTicking(target.chunkPosition())
            }
        viewing.selection.refresh(viewing.context.world.players(), available)
    }

    fun accept(player: ServerPlayer, request: WatchInputPayload) {
        check(session.server.isSameThread)
        val viewing = viewers[player.uuid] ?: return
        // ASVS 8.3.1, 8.3.2: only this connection's passed-out participant can choose a live,
        // actively admitted teammate in its captured roster. Client lists are not authority.
        if (
            viewing.context.attempt != request.attempt ||
                session.server.playerList.getPlayer(player.uuid) !== player ||
                session.runtime.deathContext(player.uuid)?.attempt != request.attempt ||
                ++viewing.requests > 16
        )
            return
        viewing.context.world.observe()
        refresh(viewing)
        val own = viewing.context.world.players().byId[player.uuid] ?: return
        if (viewing.living) {
            if (
                own.life != LifeState.ALIVE ||
                    own.participation != Participation.OBSERVER ||
                    viewing.returning
            )
                return
            if (request.action == WatchAction.LEAVE) {
                if ((player as ConclavePlayerViewing).conclaveViewing() != null)
                    beginReturn(viewing)
                apply(viewing)
                return
            }
        } else if (
            own.life != LifeState.PASSED_OUT ||
                request.action in setOf(WatchAction.LEAVE, WatchAction.WATCH)
        )
            return
        val selected =
            when (request.action) {
                WatchAction.PREVIOUS -> viewing.selection.cycle(-1)
                WatchAction.NEXT -> viewing.selection.cycle(1)
                WatchAction.SELECT -> request.target?.let(viewing.selection::select) ?: false
                WatchAction.WATCH -> viewing.selection.watch()
                WatchAction.LEAVE -> false
            }
        if (selected && viewing.living && !enterObserver(viewing)) viewing.selection.leave()
        if (viewing.living && (player as ConclavePlayerViewing).conclaveViewing() == null) {
            apply(viewing)
            return
        }
        if (
            selected &&
                request.action != WatchAction.WATCH &&
                viewing.context.catalog.settings.spectating.mode == SpectatorMode.FREE
        )
            viewing.selection.view().target?.let(session.server.playerList::getPlayer)?.let {
                camera(player, it)
            }
        apply(viewing)
    }

    private fun apply(viewing: Viewing) {
        val player = viewing.native ?: return
        val view = viewing.selection.view()
        val choices =
            view.eligible.mapNotNull { id ->
                session.server.playerList.getPlayer(id)?.let {
                    WatchChoice(id, it.scoreboardName.take(64))
                }
            }
        if (viewing.living && (!view.active || viewing.returning)) {
            send(
                viewing,
                player,
                WatchStatePayload(
                    viewing.context.attempt,
                    choices = choices,
                    message = viewing.message,
                    free =
                        viewing.context.catalog.settings.spectating.mode == SpectatorMode.FREE &&
                            !viewing.returning,
                    living = true,
                    active = viewing.returning,
                ),
            )
            return
        }
        if (viewing.context.catalog.settings.spectating.mode == SpectatorMode.FREE) {
            val state =
                WatchStatePayload(
                    viewing.context.attempt,
                    player.camera.uuid.takeIf { it in view.eligible },
                    choices,
                    free = true,
                    living = viewing.living,
                )
            send(viewing, player, state)
            return
        }
        val target = view.target?.let(session.server.playerList::getPlayer)
        var message = ""
        if (target != null) {
            if (player.camera !== target || player.level() !== target.level()) {
                if (player.level() !== target.level()) camera(player, player)
                camera(player, target)
            }
            if (player.level() !== target.level() || player.camera !== target) {
                camera(player, player)
                message = "The teammate camera is unavailable. Waiting at your grave."
            }
        } else {
            if (player.camera !== player) camera(player, player)
            val destination = viewing.returnTo
            if (
                destination != null &&
                    (player.level() !== destination.level ||
                        player.position().distanceToSqr(destination.position) > 0.01)
            ) {
                if (
                    destination.level.areEntitiesActuallyLoadedAndTicking(
                        net.minecraft.world.level.ChunkPos(
                            kotlin.math.floor(destination.position.x).toInt() shr 4,
                            kotlin.math.floor(destination.position.z).toInt() shr 4,
                        )
                    )
                ) {
                    player.teleportTo(
                        destination.level,
                        destination.position.x,
                        destination.position.y,
                        destination.position.z,
                        emptySet(),
                        destination.yaw,
                        destination.pitch,
                        false,
                    )
                }
            }
            message = "No teammate is currently available to watch."
        }
        val state =
            WatchStatePayload(
                viewing.context.attempt,
                target?.uuid?.takeIf { message.isEmpty() },
                view.eligible.mapNotNull { id ->
                    session.server.playerList.getPlayer(id)?.let {
                        WatchChoice(id, it.scoreboardName.take(64))
                    }
                },
                message,
                living = viewing.living,
            )
        send(viewing, player, state)
    }

    private fun send(viewing: Viewing, player: ServerPlayer, state: WatchStatePayload) {
        if (viewing.sent != state && ServerPlayNetworking.canSend(player, WatchStatePayload.TYPE)) {
            viewing.sent = state
            ServerPlayNetworking.send(player, state)
        }
    }

    fun close(player: UUID) {
        val viewing = viewers.remove(player) ?: return
        viewing.selection.close()
        session.chunks.release(viewing.returnClaim)
        session.server.playerList.getPlayer(player)?.let {
            if (it.camera !== it) camera(it, it)
            if (ServerPlayNetworking.canSend(it, WatchStatePayload.TYPE))
                ServerPlayNetworking.send(
                    it,
                    if ((it as ConclavePlayerViewing).conclaveViewing() == null)
                        WatchStatePayload(null)
                    else
                        WatchStatePayload(
                            viewing.context.attempt,
                            message = "Returning after the attempt.",
                        ),
                )
        }
    }

    fun disconnected(player: UUID) {
        viewers[player]?.let {
            it.native = null
            it.sent = null
        }
    }

    companion object {
        fun register() {
            PayloadTypeRegistry.serverboundPlay()
                .register(WatchInputPayload.TYPE, WatchInputPayload.CODEC)
            PayloadTypeRegistry.clientboundPlay()
                .register(WatchStatePayload.TYPE, WatchStatePayload.CODEC)
            ServerPlayNetworking.registerGlobalReceiver(WatchInputPayload.TYPE) { payload, context
                ->
                ServerSession.get(context.server())?.spectating?.accept(context.player(), payload)
            }
        }
    }
}

object SpectatorNativeHooks {
    @JvmStatic
    fun constrained(player: ServerPlayer): Boolean {
        if (!player.level().server.isSameThread) return false
        val state = (player as ConclavePlayerViewing).conclaveViewing() ?: return false
        val session = ServerSession.get(player.level().server)
        return state.mode == SpectatorMode.TEAMMATES ||
            session?.recovery?.blocksRespawn(player.uuid) == true ||
            session?.spectating?.returning(player.uuid) == true
    }

    @JvmStatic
    fun blockCamera(player: ServerPlayer): Boolean =
        constrained(player) &&
            ServerSession.get(player.level().server)
                ?.spectating
                ?.cameraChangeAllowed(player.uuid) != true
}
