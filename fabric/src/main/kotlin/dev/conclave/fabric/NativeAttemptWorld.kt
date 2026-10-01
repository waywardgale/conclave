package dev.conclave.fabric

import dev.conclave.core.*
import java.util.UUID
import net.minecraft.server.level.ServerPlayer

/** Captures coherent player facts against one immutable arena/encounter binding. */
internal class NativeAttemptWorld(
    private val session: ServerSession,
    val arena: ArenaDefinition,
    val definition: EncounterDefinition,
    private val geometry: GeometryEngine,
    roster: Set<UUID> = emptySet(),
    private val transition: (ParticipantNotice) -> Unit = {},
    private val attempt: UUID? = null,
) : AttemptWorld {
    private val roster = java.util.Set.copyOf(roster)
    private val pairing = arena.encounters.getValue(definition.id)
    val blocks = NativeBlockTargets(arena, definition)
    private val observedAreas =
        definition.spatialReferences().areas.sorted().associateWith {
            arena.areas.getValue(pairing.area(it))
        }
    private var frame = PlayerFrame(emptyList(), roster)
    var currentTick = 0L
        private set

    var combat = definition.combat
        private set

    private class Tracked(
        val lifecycle: ParticipantLifecycle,
        var native: ServerPlayer?,
        var connection: UUID,
    ) {
        var disconnectedNative: ServerPlayer? = null
    }

    private val tracked = roster.associateWith { id ->
        val connection = UUID.randomUUID()
        Tracked(
            ParticipantLifecycle(id, connection, definition.reconnectGrace),
            session.server.playerList.getPlayer(id),
            connection,
        )
    }

    fun disconnected(player: ServerPlayer, now: Long) {
        check(session.server.isSameThread)
        val record = tracked[player.uuid] ?: return
        if (record.native !== player) return
        val before = participantFacts(player.uuid)
        record.lifecycle.disconnected(record.connection, now)
        emit(record, before)
        record.disconnectedNative = player
        record.native = null
    }

    /** Vanilla respawn replaces the player object on the same live connection. */
    fun replaced(previous: ServerPlayer, current: ServerPlayer) {
        check(session.server.isSameThread)
        require(previous.uuid == current.uuid && previous.connection === current.connection)
        val record = tracked[current.uuid] ?: return
        if (record.native !== previous) return
        record.native = current
        record.disconnectedNative = null
    }

    fun end() {
        tracked.values.forEach { it.lifecycle.end() }
    }

    override fun beginStep(tick: Long, combat: Boolean) {
        currentTick = tick
        this.combat = combat
        attempt?.let { session.graves.beginStep(it, tick, combat) }
        observe()
    }

    override fun settleInteractions(tick: Long) {
        attempt?.let { session.graves.settleInteractions(it) }
        observe()
    }

    override fun finishResults(tick: Long): Boolean {
        val id = attempt ?: return false
        session.graves.settleExpiry(id)
        return !PartySurvival.hasOpportunity(id, observe(), session.graves.lifecycles(id))
    }

    fun contains(destination: NativeDestination): Boolean =
        destination.level.dimension().identifier().toString() == arena.dimension &&
            arena.boundary.contains(
                Position(destination.position.x, destination.position.y, destination.position.z)
            )

    fun observe(): PlayerFrame {
        check(session.server.isSameThread)
        val players =
            session.server.playerList.players.filter { tracked[it.uuid]?.disconnectedNative !== it }
        val now = System.nanoTime()
        val connected = players.associateBy { it.uuid }
        for ((id, record) in tracked) {
            val before = participantFacts(id)
            val current = connected[id]
            if (record.native !== current) {
                if (record.native != null) record.lifecycle.disconnected(record.connection, now)
                record.native = current
                if (current != null) {
                    record.connection = UUID.randomUUID()
                    record.lifecycle.placed(record.connection)
                }
            }
            if (current != null) {
                // This build has no authored client assets yet. World placement remains a real
                // gate.
                if (current.level().areEntitiesActuallyLoadedAndTicking(current.chunkPosition()))
                    record.lifecycle.admit(record.connection, now, resourcesApplied = true)
            }
            record.lifecycle.expire(now)
            // Consumers enqueue these snapshots; they must not dispatch rules during observation.
            emit(record, before)
        }
        check(players.size.toLong() * maxOf(1, observedAreas.size) <= 8192) {
            "Player and area observation capacity exceeded"
        }
        val online = players.map { player ->
            observation(player.uuid, player, tracked[player.uuid]?.lifecycle?.snapshot)
        }
        val present = online.mapTo(hashSetOf()) { it.id }
        val offline =
            (roster - present).map { id ->
                observation(id, null, tracked.getValue(id).lifecycle.snapshot)
            }
        return PlayerFrame(online + offline, roster).also { frame = it }
    }

    fun participantFacts(id: UUID): PlayerObservation {
        val record = tracked.getValue(id)
        return observation(id, record.native, record.lifecycle.snapshot)
    }

    /**
     * Called from committed grave operations, so a death and revival between ticks remain visible.
     */
    fun grave(event: GraveEvent, captured: PlayerObservation? = null) {
        val id =
            when (event) {
                is GraveEvent.Died -> event.player
                is GraveEvent.PassedOut -> event.player
                is GraveEvent.Revived -> event.player
                is GraveEvent.Closed -> return
            }
        val record = tracked[id] ?: return
        val before = captured ?: participantFacts(id)
        when (event) {
            is GraveEvent.Died -> record.lifecycle.life(LifeState.DEAD)
            is GraveEvent.PassedOut -> record.lifecycle.life(LifeState.PASSED_OUT)
            is GraveEvent.Revived ->
                record.lifecycle.life(LifeState.ALIVE, event.method, event.helper)
            is GraveEvent.Closed -> return
        }
        emit(record, before)
    }

    private fun emit(record: Tracked, facts: PlayerObservation) {
        for (event in record.lifecycle.drainEvents()) transition(
            ParticipantNotice(
                event,
                PlayerObservation(
                    event.before.player,
                    event.before.online,
                    event.before.life,
                    event.before.participation,
                    facts.gameMaster,
                    if (event.before.online) facts.areas else emptySet(),
                    facts.roles,
                    facts.auras,
                    if (event.before.online) facts.effects else emptySet(),
                ),
            )
        )
    }

    private fun observation(
        id: UUID,
        player: ServerPlayer?,
        snapshot: ParticipantSnapshot?,
    ): PlayerObservation {
        val online = snapshot?.online ?: (player != null)
        val logical = session.graves.gameplayPosition(id)
        val body =
            if (player == null || !online) null
            else {
                val pos = logical?.position ?: player.position()
                val box = player.boundingBox.move(pos.subtract(player.position()))
                SpatialBody(
                    (logical?.level ?: player.level()).dimension().identifier().toString(),
                    Position(pos.x, pos.y, pos.z),
                    BodyBounds(
                        Position(box.minX, box.minY, box.minZ),
                        Position(box.maxX, box.maxY, box.maxZ),
                    ),
                )
            }
        return PlayerObservation(
            id,
            online,
            snapshot?.life
                ?: session.graves.life(id)
                ?: if (player?.isAlive == true) LifeState.ALIVE else LifeState.DEAD,
            snapshot?.participation,
            id in session.gameMasters,
            if (body == null) emptySet()
            else observedAreas.filterValues { geometry.member(it, arena.dimension, body) }.keys,
            effects =
                if (player == null || !online) emptySet()
                else
                    player.activeEffects
                        .map {
                            it.effect.unwrapKey().orElseThrow().identifier().toString()
                        }
                        .toSet(),
        )
    }

    fun selected() = definition.participants.select(observe()).mapTo(linkedSetOf()) { it.id }

    override fun players() = frame

    override fun validTarget(
        scope: RuntimeScopeIdentity,
        player: UUID,
        target: TargetHandle,
        maximumReach: Double?,
    ): Boolean {
        val native = tracked[player]?.native ?: return false
        return session.server.playerList.getPlayer(player) === native &&
            blocks.valid(native, target, maximumReach)
    }

    override fun group(scope: RuntimeScopeIdentity, id: String): GroupObservation? = null

    override fun relic(scope: RuntimeScopeIdentity, id: String): RelicObservation? = null

    override fun selectedRelic(player: UUID): UUID? = null

    override fun deliver(scope: RuntimeScopeIdentity, relic: UUID, generation: Long, holder: UUID) =
        false

    companion object {
        fun eligible(player: ServerPlayer?) = player != null && player.isAlive && !player.isRemoved
    }
}
