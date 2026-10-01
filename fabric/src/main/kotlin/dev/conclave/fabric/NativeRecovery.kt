package dev.conclave.fabric

import dev.conclave.core.*
import dev.conclave.storage.*
import java.io.*
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CompletableFuture
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory

/** Fully resolved recovery values, independent of later publication and live anchor edits. */
internal class NativeRecoveryPlan(
    val dimension: String,
    locations: List<LocationPlacement>,
    val health: HealthPercentage,
    val hunger: Int,
    val radius: BigDecimal,
) {
    val locations = java.util.List.copyOf(locations)

    init {
        require(
            locations.size in 1..65 &&
                hunger in 0..20 &&
                radius >= BigDecimal.ZERO &&
                radius <= BigDecimal(64)
        )
        require(Identifier.tryParse(dimension) != null)
    }

    fun encode(): ByteArray =
        ByteArrayOutputStream()
            .also { bytes ->
                DataOutputStream(bytes).use { output ->
                    output.writeInt(1)
                    output.writeUTF(dimension)
                    output.writeUTF(health.value.toPlainString())
                    output.writeInt(hunger)
                    output.writeUTF(radius.toPlainString())
                    output.writeInt(locations.size)
                    for (location in locations) {
                        for (value in
                            listOf(
                                location.position.x,
                                location.position.y,
                                location.position.z,
                                location.yaw,
                                location.pitch,
                            )) output.writeUTF(value.toPlainString())
                    }
                }
            }
            .toByteArray()

    companion object {
        fun capture(
            arena: ArenaDefinition,
            encounter: EncounterDefinition,
            settings: GameplaySettings,
        ): NativeRecoveryPlan {
            val pairing = arena.encounters.getValue(encounter.id)
            val recovery =
                checkNotNull(encounter.recovery) {
                    "Encounter recovery.location is required for native execution"
                }
            return NativeRecoveryPlan(
                arena.dimension,
                recovery.locations.map { arena.locations.getValue(pairing.location(it)).placement },
                settings.recovery.health,
                settings.recovery.hunger,
                settings.recovery.searchRadius,
            )
        }

        fun decode(bytes: ByteArray): NativeRecoveryPlan =
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                check(bytes.size <= 65_536 && input.readInt() == 1) {
                    "Unsupported player recovery format"
                }
                fun number(): BigDecimal {
                    val value = input.readUTF()
                    check(value.length <= 128)
                    return value.toBigDecimal().also { check(it.scale() in -16..64) }
                }
                val dimension = input.readUTF()
                val health = HealthPercentage(number())
                val hunger = input.readInt()
                val radius = number()
                val count = input.readInt()
                check(count in 1..65)
                val locations =
                    List(count) {
                        val position = Position(number(), number(), number())
                        val yaw = number()
                        val pitch = number()
                        check(pitch >= BigDecimal(-90) && pitch <= BigDecimal(90))
                        LocationPlacement(position, yaw, pitch)
                    }
                check(input.available() == 0)
                NativeRecoveryPlan(dimension, locations, health, hunger, radius)
            }
    }
}

/** World ownership can close while individual offline or obstructed player recovery stays owed. */
internal class NativeRecovery(private val session: ServerSession) : AutoCloseable {
    private class Pending(
        val player: UUID,
        val resources: List<StoredResource>,
        val plan: NativeRecoveryPlan,
    ) {
        var saving = false
        var retryAt = 0
        var location = 0
        var candidates: RecoveryCandidates? = null
    }

    private class Job(
        val record: StoredAttempt,
        val arena: ArenaDefinition,
        val footprint: ChunkFootprint,
        val pending: MutableList<Pending>,
    ) {
        val claim = UUID.randomUUID()
        var message: String? = null
    }

    private val jobs = linkedMapOf<UUID, Job>()
    private val blockedPlayers = mutableMapOf<UUID, MutableSet<UUID>>()

    fun blocksRespawn(player: UUID): Boolean = blockedPlayers.values.any { player in it }

    private var nextJob: UUID? = null
    private val preparing = mutableSetOf<UUID>()
    private val geometry = GeometryEngine()
    private val worker =
        java.util.concurrent.ThreadPoolExecutor(
            1,
            1,
            0,
            java.util.concurrent.TimeUnit.SECONDS,
            java.util.concurrent.ArrayBlockingQueue(64),
            { task -> Thread(task, "Conclave recovery preparation").apply { isDaemon = true } },
            java.util.concurrent.ThreadPoolExecutor.AbortPolicy(),
        )
    private val logger = LoggerFactory.getLogger("Conclave recovery")

    fun prepare(snapshot: AttemptSnapshot, catalog: CompiledCatalog): CompletableFuture<Void> {
        check(session.server.isSameThread)
        val plan =
            NativeRecoveryPlan.capture(
                    catalog.arenas.getValue(snapshot.arena),
                    catalog.encounters.getValue(snapshot.encounter),
                    catalog.settings,
                )
                .encode()
        return CompletableFuture.allOf(
            *snapshot.roster
                .map { player ->
                    session.attempts.record(
                        ResourceIntent(
                            UUID.randomUUID(),
                            snapshot.id,
                            OwnedResourceKind.PLAYER_STATE,
                            player.toString(),
                            plan,
                        )
                    )
                }
                .toTypedArray()
        )
    }

    fun cleanup(record: StoredAttempt, captured: CompiledCatalog?, done: (Throwable?) -> Unit) {
        check(session.server.isSameThread)
        val id = record.snapshot.id
        blockedPlayers.getOrPut(id) { record.snapshot.roster.toMutableSet() }
        if (id in jobs || !preparing.add(id))
            return done(IllegalStateException("Recovery is already pending"))
        if (preparing.size + jobs.size > 64) {
            preparing.remove(id)
            return done(IllegalStateException("Recovery preparation capacity exceeded"))
        }
        fun fail(error: Throwable) {
            preparing.remove(id)
            done(error)
        }
        session.complete(session.attempts.releaseUntouched(id)) { untouched, untouchedError ->
            if (untouchedError != null) {
                fail(untouchedError)
                return@complete
            }
            if (untouched == true) {
                blockedPlayers.remove(id)
                preparing.remove(id)
                done(null)
                return@complete
            }
            val read =
                session.attempts.beginCleanup(id).thenCompose { session.attempts.resources(id) }
            session.complete(read) resources@{ resources, resourceError ->
                if (resourceError != null) {
                    fail(resourceError)
                    return@resources
                }
                val retained = checkNotNull(resources)
                if (
                    retained.any {
                        it.intent.kind != OwnedResourceKind.PLAYER_STATE &&
                            it.state != OwnedResourceState.RESOLVED
                    }
                ) {
                    fail(
                        IllegalStateException("An owned resource has no installed cleanup adapter")
                    )
                    return@resources
                }
                session.complete(session.attempts.wasActivated(id)) activated@{
                    active,
                    activationError ->
                    if (activationError != null) {
                        fail(activationError)
                        return@activated
                    }
                    if (active != true) {
                        val cleared =
                            CompletableFuture.allOf(
                                    *retained
                                        .filter { it.state != OwnedResourceState.RESOLVED }
                                        .map { session.attempts.resolved(it.intent.id) }
                                        .toTypedArray()
                                )
                                .thenCompose {
                                    CompletableFuture.allOf(
                                        *record.snapshot.roster
                                            .map { session.attempts.recoveredPlayer(id, it) }
                                            .toTypedArray()
                                    )
                                }
                                .thenCompose { session.attempts.releaseArena(id) }
                        session.complete(cleared) { released, error ->
                            if (error == null && released == true) blockedPlayers.remove(id)
                            preparing.remove(id)
                            done(
                                error
                                    ?: if (released == true) null
                                    else
                                        IllegalStateException("Preparation cleanup remains pending")
                            )
                        }
                        return@activated
                    }
                    val catalog =
                        if (captured != null) CompletableFuture.completedFuture(captured)
                        else
                            session.content
                                .revision(record.revision)
                                .thenApplyAsync(
                                    { saved ->
                                        val compiled =
                                            CatalogCompiler().compile(checkNotNull(saved).sources)
                                        check(
                                            compiled is Validation.Valid &&
                                                compiled.value.revision == record.revision
                                        ) {
                                            "Captured recovery definitions need compatibility repair"
                                        }
                                        compiled.value
                                    },
                                    worker,
                                )
                    val planned =
                        catalog.thenApplyAsync(
                            { value ->
                                val arena = value.arenas.getValue(record.snapshot.arena)
                                val pending =
                                    retained
                                        .filter {
                                            it.intent.kind == OwnedResourceKind.PLAYER_STATE &&
                                                it.state != OwnedResourceState.RESOLVED
                                        }
                                        .groupBy { UUID.fromString(it.intent.identity) }
                                        .map { (player, records) ->
                                            check(records.size == 1) {
                                                "Unsupported overlapping player recovery plans"
                                            }
                                            Pending(
                                                player,
                                                records,
                                                NativeRecoveryPlan.decode(
                                                    records.single().intent.recovery
                                                ),
                                            )
                                        }
                                        .toMutableList()
                                // Every current player plan shares the same resolved destination
                                // set.
                                val first = pending.firstOrNull()
                                check(
                                    pending.all {
                                        it.plan.dimension == arena.dimension &&
                                            it.plan.locations == first?.plan?.locations &&
                                            it.plan.radius == first.plan.radius
                                    }
                                ) {
                                    "Recovery plans disagree on the captured destinations"
                                }
                                // Chunk admission can conservatively enclose the search.
                                // Axis-aligned boxes
                                // avoid spending exact curved-geometry work on a non-gameplay
                                // footprint.
                                val boxes =
                                    first?.plan?.locations?.map {
                                        val radius = first.plan.radius + BigDecimal(2)
                                        Geometry.Box(
                                            it.position +
                                                Position(BigDecimal.ZERO, -radius, BigDecimal.ZERO),
                                            radius * BigDecimal(2),
                                            radius * BigDecimal(2),
                                            radius * BigDecimal(2) + BigDecimal(2),
                                        )
                                    } ?: emptyList()
                                val region =
                                    when (boxes.size) {
                                        0 -> arena.boundary
                                        1 -> boxes.single()
                                        else -> Geometry.Composite(boxes, emptyList())
                                    }
                                val footprint =
                                    GeometryEngine(250, 2_000_000).use {
                                        ChunkFootprint.plan(
                                            region,
                                            it,
                                            NativeChunkClaims.propagationRadius,
                                        )
                                    }
                                Job(record, arena, footprint, pending)
                            },
                            worker,
                        )
                    session.complete(planned) planned@{ job, planError ->
                        if (planError != null) {
                            fail(planError)
                            return@planned
                        }
                        val ready = checkNotNull(job)
                        jobs[id] = ready
                        val playersWithPlans = ready.pending.mapTo(hashSetOf()) { it.player }
                        val release =
                            CompletableFuture.allOf(
                                    *record.snapshot.roster
                                        .filter { it !in playersWithPlans }
                                        .map { session.attempts.recoveredPlayer(id, it) }
                                        .toTypedArray()
                                )
                                .thenCompose { session.attempts.releaseArena(id) }
                        session.complete(release) { released, releaseError ->
                            if (releaseError == null && released == true)
                                blockedPlayers[id]?.retainAll(playersWithPlans)
                            preparing.remove(id)
                            if (releaseError != null || released != true) {
                                jobs.remove(id)
                                done(
                                    releaseError
                                        ?: IllegalStateException("World cleanup remains pending")
                                )
                            } else done(null)
                        }
                    }
                }
            }
        }
    }

    fun messages(): Map<UUID, String> {
        check(session.server.isSameThread)
        return preparing.associateWith { "Preparing captured recovery." } +
            jobs.mapValues { (_, job) ->
                job.message
                    ?: "Recovery owed to ${job.pending.size} participant(s): ${job.pending.take(10).joinToString { session.server.playerList.getPlayer(it.player)?.plainTextName ?: it.player.toString() }}${if (job.pending.size > 10) "; ${job.pending.size - 10} more" else ""}"
            }
    }

    fun tick() {
        check(session.server.isSameThread)
        val began = System.nanoTime()
        var checks = 0
        val order = jobs.keys.toList()
        val start = order.indexOf(nextJob).coerceAtLeast(0)
        for (offset in order.indices) {
            val id = order[(start + offset) % order.size]
            val job = jobs.getValue(id)
            nextJob = order[(start + offset + 1) % order.size]
            if (System.nanoTime() - began > 3_000_000) return
            if (id in preparing) continue
            if (job.pending.isEmpty()) {
                session.chunks.release(job.claim)
                jobs.remove(id)
                blockedPlayers.remove(id)
                continue
            }
            val online =
                job.pending.filter {
                    session.server.playerList.getPlayer(it.player)?.hasDisconnected() == false
                }
            if (online.isEmpty()) {
                session.chunks.release(job.claim)
                continue
            }
            try {
                if (
                    !session.chunks.owns(job.claim) &&
                        !session.chunks.retain(job.claim, job.arena.dimension, job.footprint)
                ) {
                    job.message = "Recovery is waiting for chunk capacity."
                    continue
                }
                if (!session.chunks.ready(job.claim)) continue
                for (pending in online) {
                    if (pending.saving || session.server.tickCount < pending.retryAt) continue
                    if (++checks > 32 || System.nanoTime() - began > 3_000_000) return
                    var player = session.server.playerList.getPlayer(pending.player) ?: continue
                    if ((player as ConclavePlayerReceipt).conclaveRecoveryReceipt() != id) {
                        val level =
                            session.server.getLevel(
                                ResourceKey.create(
                                    Registries.DIMENSION,
                                    Identifier.parse(pending.plan.dimension),
                                )
                            ) ?: continue
                        var destination: NativeDestination? = null
                        while (
                            destination == null && pending.location < pending.plan.locations.size
                        ) {
                            if (++checks > 32 || System.nanoTime() - began > 3_000_000) return
                            val location = pending.plan.locations[pending.location]
                            val candidates =
                                pending.candidates
                                    ?: RecoveryCandidates(location.position, pending.plan.radius)
                                        .also { pending.candidates = it }
                            if (!candidates.hasNext()) {
                                pending.location++
                                pending.candidates = null
                                continue
                            }
                            val position = candidates.next()
                            val candidate =
                                NativeDestination(
                                    level,
                                    Vec3(
                                        position.x.toDouble(),
                                        position.y.toDouble(),
                                        position.z.toDouble(),
                                    ),
                                    location.yaw.toFloat(),
                                    location.pitch.toFloat(),
                                )
                            val box =
                                EntityTypes.PLAYER.dimensions
                                    .scale(player.scale)
                                    .makeBoundingBox(candidate.position)
                            val body =
                                BodyBounds(
                                    Position(box.minX, box.minY, box.minZ),
                                    Position(box.maxX, box.maxY, box.maxZ),
                                )
                            if (
                                geometry.contains(job.arena.boundary, body) &&
                                    session.runtime.recoveryAvailable(
                                        id,
                                        pending.plan.dimension,
                                        body,
                                    ) &&
                                    NativePlayerPlacement.usable(player, candidate)
                            )
                                destination = candidate
                        }
                        if (destination == null) {
                            pending.location = 0
                            pending.candidates = null
                            pending.retryAt = session.server.tickCount + 20
                            job.message =
                                "No safe recovery position is available for ${player.name.string}. Check the captured recovery locations and nearby blocks."
                            continue
                        }
                        if (!player.isAlive)
                            player =
                                NativeRespawn.revive(player, destination, pending.plan.health)
                                    ?: continue
                        else {
                            if (
                                !player.teleportTo(
                                    destination.level,
                                    destination.position.x,
                                    destination.position.y,
                                    destination.position.z,
                                    emptySet(),
                                    destination.yaw,
                                    destination.pitch,
                                    true,
                                )
                            )
                                continue
                            player.health =
                                pending.plan.health
                                    .of(player.maxHealth.toDouble())
                                    .toFloat()
                                    .coerceIn(Float.MIN_VALUE, player.maxHealth)
                        }
                        player.foodData.foodLevel = pending.plan.hunger
                    }
                    PlayerViewingState.restore(player, id)
                    NativeRevivalProtection.clear(player)
                    pending.saving = true
                    session.complete(session.playerSaves.confirm(player, id)) saved@{ _, error ->
                        if (error != null) {
                            pending.saving = false
                            pending.retryAt = session.server.tickCount + 100
                            job.message =
                                "Player recovery is applied, but its native save needs confirmation."
                            logger.error(
                                "Recovery save {} / {} is unconfirmed",
                                id,
                                pending.player,
                                error,
                            )
                            return@saved
                        }
                        val complete =
                            CompletableFuture.allOf(
                                    *pending.resources
                                        .map { session.attempts.resolved(it.intent.id) }
                                        .toTypedArray()
                                )
                                .thenCompose {
                                    session.attempts.recoveredPlayer(id, pending.player)
                                }
                        session.complete(complete) { _, storageError ->
                            if (storageError == null) {
                                job.pending.remove(pending)
                                blockedPlayers[job.record.snapshot.id]?.remove(pending.player)
                                job.message = null
                            } else {
                                pending.saving = false
                                pending.retryAt = session.server.tickCount + 100
                                job.message =
                                    "Player recovery is saved; its journal acknowledgement needs retry."
                            }
                        }
                    }
                }
            } catch (failure: Exception) {
                job.message = "Recovery is waiting: ${failure.message}"
                online.forEach {
                    it.retryAt = session.server.tickCount + 100
                    it.candidates = null
                    it.location = 0
                }
                logger.error("Recovery {} requires attention", id, failure)
            }
        }
    }

    override fun close() {
        worker.shutdown()
        check(worker.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS))
        geometry.close()
    }
}
