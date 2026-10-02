package dev.conclave.fabric

import dev.conclave.core.*
import dev.conclave.storage.*
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import org.slf4j.LoggerFactory

internal data class AttemptView(
    val id: UUID,
    val arena: DefinitionId,
    val encounter: DefinitionId,
    val revision: String,
    val state: String,
)

internal data class NativeDeathContext(
    val attempt: UUID,
    val catalog: CompiledCatalog,
    val world: NativeAttemptWorld,
    val deathTick: Long,
    val roster: List<UUID>,
)

internal data class NativeInteractionContext(
    val engine: AttemptEngine,
    val world: NativeAttemptWorld,
)

/** Server-thread ownership; worker results never create gameplay effects without revalidation. */
internal class NativeAttempts(private val session: ServerSession) : AutoCloseable {
    private enum class Stage {
        PLANNING,
        PERSISTING,
        LOADING,
        ACTIVATING,
        RUNNING,
        FINISHING,
        CLEANING,
        RECOVERY,
    }

    private class Entry(
        val snapshot: AttemptSnapshot,
        val catalog: CompiledCatalog,
        val world: NativeAttemptWorld,
        val connections: Map<UUID, net.minecraft.server.level.ServerPlayer>,
        val authorized: () -> Boolean,
        val actor: String,
        val reply: (Boolean, String) -> Unit,
    ) {
        var stage = Stage.PLANNING
        var deadline = System.nanoTime() + 30_000_000_000L
        var durable = false
        var cancellation: String? = null
        var engine: AttemptEngine? = null
        var activatedServerTick: Int? = null
        var cleaned = false
        var cleanupStarted = false
        var reported = false
        var completion: CompletionDecision? = null
        var reconcileNeeded = false
        var reconciling = false
        var nextReconciliation = 0L
        var afterCleanup: (() -> Unit)? = null
    }

    private val geometry = GeometryEngine()
    private val reservations = ArenaReservations(geometry)
    private val planner = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Conclave arena preparation").apply { isDaemon = true }
    }
    private val entries = linkedMapOf<UUID, Entry>()
    private var recovering = 0
    private var recoveryFailure = false
    private var stopping = false
    private val logger = LoggerFactory.getLogger("Conclave attempts")

    fun initialize() {
        checkThread()
        recovering = session.interruptedAttempts.size
        for (record in session.interruptedAttempts) {
            val id = record.snapshot.id
            session.recovery.cleanup(record, null) { error ->
                if (error != null) {
                    recoveryFailure = true
                    recovering--
                    logger.error("Attempt {} still requires owned-resource recovery", id, error)
                } else
                    session.complete(session.rewards.releaseReservation(id)) { _, releaseError ->
                        recovering--
                        if (releaseError != null) {
                            recoveryFailure = true
                            logger.error(
                                "Attempt {} completion reservation could not be reconciled",
                                id,
                                releaseError,
                            )
                        }
                    }
            }
        }
    }

    fun views(): List<AttemptView> {
        checkThread()
        return entries.values.map {
            AttemptView(
                it.snapshot.id,
                it.snapshot.arena,
                it.snapshot.encounter,
                it.catalog.revision,
                it.stage.name.lowercase(),
            )
        }
    }

    fun recoveryAvailable(attempt: UUID, dimension: String, bounds: BodyBounds): Boolean =
        reservations.occupied().none {
            it.attempt != attempt &&
                it.dimension == dimension &&
                geometry.overlaps(it.boundary, bounds)
        }

    fun ownsPlayer(player: UUID): Boolean = entries.values.any { player in it.snapshot.roster }

    fun interaction(player: net.minecraft.server.level.ServerPlayer): NativeInteractionContext? {
        checkThread()
        if (stopping || session.server.playerList.getPlayer(player.uuid) !== player) return null
        val entry =
            entries.values.firstOrNull {
                it.stage == Stage.RUNNING && player.uuid in it.snapshot.roster
            } ?: return null
        entry.world.observe()
        return NativeInteractionContext(checkNotNull(entry.engine), entry.world)
    }

    fun blockChanged(
        level: net.minecraft.server.level.ServerLevel,
        position: net.minecraft.core.BlockPos,
        before: net.minecraft.world.level.block.state.BlockState,
        after: net.minecraft.world.level.block.state.BlockState,
    ) {
        checkThread()
        for (entry in entries.values) entry.world.blocks.changed(level, position, before, after)
    }

    fun interrupt(id: UUID, reason: String) {
        val entry = entries[id] ?: return
        entry.engine?.technicalError(IllegalStateException(reason)) ?: return
        events(entry)
    }

    fun deathContext(player: UUID): NativeDeathContext? =
        entries.values
            .firstOrNull {
                it.stage == Stage.RUNNING && player in it.snapshot.roster
            }
            ?.let {
                val engine = checkNotNull(it.engine)
                NativeDeathContext(
                    it.snapshot.id,
                    it.catalog,
                    it.world,
                    engine.tick + if (engine.isAdvancing) 0 else 1,
                    it.snapshot.roster,
                )
            }

    fun disconnected(player: net.minecraft.server.level.ServerPlayer) {
        checkThread()
        val now = System.nanoTime()
        entries.values.forEach { it.world.disconnected(player, now) }
    }

    fun replaced(
        previous: net.minecraft.server.level.ServerPlayer,
        current: net.minecraft.server.level.ServerPlayer,
    ) {
        checkThread()
        entries.values.forEach { it.world.replaced(previous, current) }
    }

    fun start(
        arenaId: DefinitionId,
        encounterId: DefinitionId,
        actor: String,
        authorized: () -> Boolean,
        reply: (Boolean, String) -> Unit,
    ): UUID? {
        checkThread()
        fun refused(reason: String): UUID? {
            reply(false, reason)
            return null
        }
        if (!authorized()) return refused("Game master authority is required.")
        if (stopping || session.failure != null || recovering > 0 || recoveryFailure)
            return refused(
                "Conclave admission is unavailable while storage or recovery is unresolved."
            )
        if (entries.size >= 16) return refused("The active attempt capacity has been reached.")
        val catalog =
            session.catalog
                ?: return refused("Publish a valid catalog before starting an encounter.")
        val arena = catalog.arenas[arenaId] ?: return refused("Unknown published arena: $arenaId")
        val definition =
            catalog.encounters[encounterId]
                ?: return refused("Unknown published encounter: $encounterId")
        if (definition.recovery == null)
            return refused("Set recovery.location in the encounter before native execution.")
        if (encounterId !in arena.encounters)
            return refused("This encounter is not bound to this arena.")
        if (
            session.server.getLevel(
                ResourceKey.create(Registries.DIMENSION, Identifier.parse(arena.dimension))
            ) == null
        )
            return refused("The arena dimension is unavailable: ${arena.dimension}")
        // This gate is removed per capability when its native ownership/input adapter is installed.
        fun unsupported(mechanic: CompiledMechanic): CompiledMechanic? {
            val children = mechanic.composition?.steps ?: mechanic.layers?.content?.allMechanics
            if (children != null) return children.firstNotNullOfOrNull { unsupported(it.mechanic) }
            return mechanic.takeUnless {
                it.type == DefinitionId("conclave", "capture") ||
                    it.type in
                        setOf(
                            DefinitionId("conclave", "interact"),
                            DefinitionId("conclave", "match_pattern"),
                        ) && it.interactionTargets.all { target -> target.kind == TargetKind.BLOCK }
            }
        }
        val unsupported =
            (listOf(definition.content) + definition.phases.map { it.content })
                .flatMap { it.allMechanics }
                .firstNotNullOfOrNull { unsupported(it.mechanic) }
        if (unsupported != null)
            return refused(
                "Native execution for ${unsupported.type} is not installed in this development build."
            )
        val id = UUID.randomUUID()
        return try {
            val selection = NativeAttemptWorld(session, arena, definition, geometry).selected()
            if (selection.isEmpty()) return refused("The participant selection is empty.")
            if (selection.size > 1024)
                return refused("The participant selection exceeds the roster capacity.")
            val connections = selection.associateWith { session.server.playerList.getPlayer(it) }
            if (connections.values.any { !NativeAttemptWorld.eligible(it) })
                return refused(
                    "Every selected participant must be connected and alive. The selection has not been reduced."
                )
            val reserved =
                reservations.reserve(
                    ArenaReservation(id, arena.id, arena.dimension, arena.boundary, selection)
                )
            if (reserved is ArenaAdmission.Conflict)
                return refused("${reserved.reason}: ${reserved.attempt}")
            // YAML arenas have no mutable native placements yet; retain their complete pinned
            // source revision.
            val snapshot =
                AttemptSnapshot(
                    id,
                    arenaId,
                    encounterId,
                    selection.toList(),
                    "yaml-arena-v1".toByteArray(Charsets.US_ASCII),
                )
            val entry =
                Entry(
                    snapshot,
                    catalog,
                    NativeAttemptWorld(
                        session,
                        arena,
                        definition,
                        geometry,
                        selection,
                        transition = { notice -> participant(id, notice) },
                        attempt = id,
                    ),
                    connections.mapValues { checkNotNull(it.value) },
                    authorized,
                    actor,
                    reply,
                )
            entries[id] = entry
            session.complete(
                CompletableFuture.supplyAsync(
                    {
                        GeometryEngine(250, 2_000_000).use {
                            ChunkFootprint.plan(
                                arena.boundary,
                                it,
                                NativeChunkClaims.propagationRadius,
                            )
                        }
                    },
                    planner,
                )
            ) { footprint, error ->
                if (entries[id] !== entry) return@complete
                if (error != null) {
                    failPreparation(entry, "The arena footprint could not be prepared.", error)
                    return@complete
                }
                val invalid = invalidated(entry)
                if (invalid != null) {
                    failPreparation(entry, invalid)
                    return@complete
                }
                persist(entry, checkNotNull(footprint))
            }
            id
        } catch (failure: Exception) {
            reservations.release(id)
            entries.remove(id)
            logger.warn("Arena admission failed for {} and {}", arenaId, encounterId, failure)
            refused(
                "Arena admission failed: ${failure.message ?: "native observation unavailable"}"
            )
        }
    }

    private fun persist(entry: Entry, footprint: ChunkFootprint) {
        entry.stage = Stage.PERSISTING
        session.complete(session.attempts.prepare(entry.snapshot, entry.catalog)) { prepared, error
            ->
            if (error != null) {
                // A lost acknowledgment cannot establish that no durable locks were written.
                session.complete(session.attempts.attempt(entry.snapshot.id)) { stored, lookupError
                    ->
                    if (lookupError != null)
                        quarantine(entry, "Admission storage needs recovery.", lookupError)
                    else {
                        entry.durable = stored != null
                        failPreparation(entry, "Admission storage failed.", error)
                    }
                }
                return@complete
            }
            when (prepared) {
                is AttemptPreparation.Prepared -> entry.durable = true
                is AttemptPreparation.Conflict -> {
                    failPreparation(entry, "${prepared.kind} is reserved: ${prepared.identity}")
                    return@complete
                }
                else -> {
                    failPreparation(entry, "Durable attempt/completion capacity is exhausted.")
                    return@complete
                }
            }
            val invalid = invalidated(entry)
            if (invalid != null) {
                failPreparation(entry, invalid)
                return@complete
            }
            session.complete(
                session.recovery.prepare(entry.snapshot, entry.catalog)
            ) recoveryPrepared@{ _, recoveryError ->
                if (recoveryError != null) {
                    failPreparation(entry, "Player recovery could not be recorded.", recoveryError)
                    return@recoveryPrepared
                }
                val changed = invalidated(entry)
                if (changed != null) {
                    failPreparation(entry, changed)
                    return@recoveryPrepared
                }
                try {
                    if (
                        !session.chunks.retain(
                            entry.snapshot.id,
                            entry.world.arena.dimension,
                            footprint,
                        )
                    ) {
                        failPreparation(entry, "The arena's native chunk capacity is unavailable.")
                        return@recoveryPrepared
                    }
                    entry.stage = Stage.LOADING
                } catch (failure: Exception) {
                    failPreparation(entry, "Native chunk preparation failed.", failure)
                }
            }
        }
    }

    private fun invalidated(entry: Entry): String? {
        entry.cancellation?.let {
            return it
        }
        if (stopping || !entry.authorized())
            return "Start cancelled because administrative authority is no longer available."
        if (System.nanoTime() - entry.deadline >= 0) return "Encounter preparation timed out."
        if (session.catalog?.revision != entry.catalog.revision)
            return "Published content changed during preparation; start again against the current revision."
        if (
            entry.connections.any { (id, player) ->
                session.server.playerList.getPlayer(id) !== player ||
                    !NativeAttemptWorld.eligible(player)
            }
        )
            return "A selected participant disconnected or is no longer alive."
        try {
            if (entry.world.selected() != entry.snapshot.roster.toSet())
                return "The participant selection changed during preparation."
        } catch (failure: Exception) {
            logger.warn("Participant observation failed for {}", entry.snapshot.id, failure)
            return "The participant selection could not be observed within capacity."
        }
        return null
    }

    fun tick() {
        checkThread()
        if (stopping) return
        for (entry in entries.values.toList()) try {
            if (
                entry.reconcileNeeded &&
                    !entry.reconciling &&
                    System.nanoTime() - entry.nextReconciliation >= 0
            )
                reconcile(entry)
            when (entry.stage) {
                Stage.PLANNING,
                Stage.PERSISTING,
                Stage.LOADING,
                Stage.ACTIVATING -> {
                    val invalid = invalidated(entry)
                    if (invalid != null) cancel(entry, invalid)
                    else if (
                        entry.stage == Stage.LOADING && session.chunks.ready(entry.snapshot.id)
                    )
                        activate(entry)
                }
                Stage.RUNNING -> {
                    if (entry.activatedServerTick == session.server.tickCount) continue
                    check(session.chunks.ready(entry.snapshot.id)) {
                        "The arena lost native entity-ticking readiness"
                    }
                    entry.world.observe()
                    checkNotNull(entry.engine).advance()
                    events(entry)
                }
                Stage.FINISHING ->
                    if (System.nanoTime() - entry.deadline >= 0 && !entry.cleanupStarted) {
                        checkNotNull(entry.engine).releasePendingCleanup()
                        events(entry)
                    }
                else -> Unit
            }
        } catch (failure: Exception) {
            if (entry.engine != null) {
                entry.engine!!.technicalError(failure)
                events(entry)
            } else cancel(entry, "Native preparation failed: ${failure.message}")
            logger.error("Native attempt {} failed", entry.snapshot.id, failure)
        }
    }

    private fun activate(entry: Entry) {
        entry.stage = Stage.ACTIVATING
        session.complete(session.attempts.activate(entry.snapshot.id)) { active, error ->
            if (error != null || active != true) {
                failPreparation(
                    entry,
                    "The attempt could not enter its durable running state.",
                    error,
                )
                return@complete
            }
            try {
                val invalid = invalidated(entry)
                if (invalid != null) {
                    failPreparation(entry, invalid)
                    return@complete
                }
                if (!session.chunks.ready(entry.snapshot.id)) {
                    failPreparation(entry, "The arena lost native entity-ticking readiness.")
                    return@complete
                }
                entry.world.observe()
                entry.engine =
                    AttemptEngine(
                        entry.snapshot.id,
                        entry.catalog.revision,
                        entry.world.definition,
                        entry.world,
                    )
                entry.stage = Stage.RUNNING
                entry.activatedServerTick = session.server.tickCount
                checkNotNull(entry.engine).start()
                events(entry)
                if (entry.engine?.state is ProgressionState.Ended)
                    report(entry, false, "Encounter startup ended with a technical error.")
                else
                    report(
                        entry,
                        true,
                        "Started ${entry.snapshot.encounter} in ${entry.snapshot.arena}; attempt ${entry.snapshot.id}, revision ${entry.catalog.revision.take(12)}.",
                    )
            } catch (failure: Exception) {
                entry.engine?.let {
                    it.technicalError(failure)
                    events(entry)
                }
                failPreparation(entry, "Encounter startup failed.", failure)
            }
        }
    }

    fun stop(id: UUID): Boolean {
        checkThread()
        val entry = entries[id] ?: return false
        if (entry.engine != null) {
            entry.engine!!.stop()
            events(entry)
        } else cancel(entry, "Encounter preparation was stopped administratively.")
        return true
    }

    fun restart(
        id: UUID,
        actor: String,
        authorized: () -> Boolean,
        reply: (Boolean, String) -> Unit,
    ): Boolean {
        checkThread()
        val entry = entries[id] ?: return false
        if (entry.afterCleanup != null) {
            reply(false, "A restart is already waiting for this attempt's cleanup.")
            return true
        }
        val restart = {
            start(entry.snapshot.arena, entry.snapshot.encounter, actor, authorized, reply)
            Unit
        }
        if (entry.cleaned) restart()
        else {
            entry.afterCleanup = restart
            stop(id)
        }
        return true
    }

    private fun events(entry: Entry) {
        val engine = checkNotNull(entry.engine)
        for (event in engine.drainEvents()) when (event) {
            is AttemptEvent.Fault ->
                logger.error("Encounter runtime {} failed", entry.snapshot.id, event.cause)
            is AttemptEvent.Progression ->
                when (val effect = event.effect) {
                    is ProgressionEffect.CommitCompletion -> commit(entry, effect.operation)
                    ProgressionEffect.CleanupAttempt -> cleanup(entry)
                    is ProgressionEffect.Ended ->
                        if (effect.announce) {
                            val message =
                                Component.literal(
                                    "${entry.world.definition.name ?: entry.snapshot.encounter}: ${effect.result.name.lowercase().replace('_', ' ')}"
                                )
                            entry.snapshot.roster.forEach {
                                session.server.playerList.getPlayer(it)?.sendSystemMessage(message)
                            }
                        }
                    else -> Unit
                }
            else -> Unit
        }
        if (engine.state is ProgressionState.Ended) {
            cleanup(entry)
            if (entry.cleaned) entries.remove(entry.snapshot.id)
        }
    }

    private fun participant(attempt: UUID, notice: ParticipantNotice) {
        val entry = entries[attempt]?.takeIf { it.stage == Stage.RUNNING } ?: return
        val engine = entry.engine ?: return
        try {
            engine.participant(notice)
        } catch (failure: Exception) {
            if (engine.isAdvancing) throw failure
            engine.technicalError(failure)
            events(entry)
        }
    }

    private fun commit(entry: Entry, operation: CompletionOperation) {
        check(entry.completion == null)
        entry.stage = Stage.FINISHING
        entry.deadline = System.nanoTime() + 30_000_000_000L
        val decision =
            CompletionDecision(
                operation.attempt,
                operation.revision,
                operation.elapsed.ticks,
                true,
                emptyList(),
            )
        entry.completion = decision
        // Enqueue this complete frozen transaction before any independently requested cleanup.
        // Splitting it across future callbacks would let a stop overtake the original decision.
        val transaction = session.rewards.commit(decision)
        session.complete(transaction) { _, error ->
            if (error == null) acknowledge(entry, operation, CommitStatus.COMMITTED)
            else {
                entry.reconcileNeeded = true
                reconcile(entry)
            }
        }
    }

    private fun reconcile(entry: Entry) {
        if (entry.reconciling) return
        entry.reconciling = true
        val decision = checkNotNull(entry.completion)
        val operation =
            CompletionOperation(
                decision.attempt,
                decision.revision,
                SimulationDuration(decision.elapsed),
            )
        session.complete(session.rewards.committed(decision)) { committed, error ->
            entry.reconciling = false
            if (error != null) {
                checkNotNull(entry.engine).acknowledge(operation, CommitStatus.UNKNOWN)
                if (entry.nextReconciliation == 0L)
                    logger.error(
                        "Completion {} is uncertain; its original decision is retained",
                        decision.attempt,
                        error,
                    )
                entry.nextReconciliation = System.nanoTime() + 5_000_000_000L
            } else
                acknowledge(
                    entry,
                    operation,
                    if (committed == true) CommitStatus.COMMITTED else CommitStatus.ABSENT,
                )
        }
    }

    private fun acknowledge(entry: Entry, operation: CompletionOperation, status: CommitStatus) {
        entry.reconcileNeeded = false
        if (!checkNotNull(entry.engine).acknowledge(operation, status)) return
        events(entry)
        session.complete(session.rewards.releaseReservation(entry.snapshot.id)) { _, error ->
            if (error != null) {
                recoveryFailure = true
                logger.error("Completion reservation {} needs recovery", entry.snapshot.id, error)
            }
        }
    }

    private fun cancel(entry: Entry, reason: String) {
        if (entry.cancellation == null) entry.cancellation = reason
        if (entry.stage != Stage.PERSISTING && entry.stage != Stage.ACTIVATING)
            failPreparation(entry, reason)
    }

    private fun failPreparation(entry: Entry, reason: String, error: Throwable? = null) {
        if (error != null) logger.warn("Attempt preparation {} failed", entry.snapshot.id, error)
        report(entry, false, reason)
        if (entry.durable) cleanup(entry)
        else {
            session.chunks.release(entry.snapshot.id)
            reservations.release(entry.snapshot.id)
            entries.remove(entry.snapshot.id)
            entry.afterCleanup?.invoke()
        }
    }

    private fun cleanup(entry: Entry) {
        if (entry.cleanupStarted) return
        entry.cleanupStarted = true
        session.graves.endAttempt(entry.snapshot.id)
        session.spectating.endAttempt(entry.snapshot.id)
        entry.world.end()
        entry.stage = Stage.CLEANING
        session.recovery.cleanup(
            StoredAttempt(entry.snapshot, entry.catalog.revision, StoredAttemptState.RECOVERING),
            entry.catalog,
        ) { error ->
            if (error != null) {
                quarantine(entry, "Owned cleanup needs recovery.", error)
                return@cleanup
            }
            session.chunks.release(entry.snapshot.id)
            reservations.release(entry.snapshot.id)
            entry.cleaned = true
            if (entry.engine?.state !is ProgressionState.Finishing) {
                entries.remove(entry.snapshot.id)
                session.complete(session.rewards.releaseReservation(entry.snapshot.id)) {
                    _,
                    releaseError ->
                    if (releaseError != null) {
                        recoveryFailure = true
                        logger.error(
                            "Attempt reservation {} needs recovery",
                            entry.snapshot.id,
                            releaseError,
                        )
                    }
                }
            } else entry.stage = Stage.FINISHING
            entry.afterCleanup?.invoke()
            entry.afterCleanup = null
        }
    }

    private fun quarantine(entry: Entry, reason: String, error: Throwable?) {
        entry.stage = Stage.RECOVERY
        recoveryFailure = true
        logger.error("Attempt {} requires recovery: {}", entry.snapshot.id, reason, error)
        report(entry, false, reason)
    }

    private fun report(entry: Entry, success: Boolean, message: String) {
        if (entry.reported) return
        entry.reported = true
        try {
            if (entry.authorized()) entry.reply(success, message)
        } catch (failure: Exception) {
            logger.warn("Could not deliver attempt {} status", entry.snapshot.id, failure)
        }
        session.complete(
            session.authority.record(
                entry.actor,
                "attempt_start",
                entry.snapshot.id.toString(),
                if (success) "started" else "rejected",
            )
        ) { _, error ->
            if (error != null) {
                recoveryFailure = true
                logger.error("Administrative attempt audit failed", error)
            }
        }
    }

    fun shutdown() {
        checkThread()
        stopping = true
        entries.keys.toList().forEach(::stop)
    }

    override fun close() {
        planner.shutdownNow()
        geometry.close()
    }

    private fun checkThread() = check(session.server.isSameThread)
}
