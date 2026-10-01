package dev.conclave.storage

import dev.conclave.core.CompiledCatalog
import dev.conclave.core.DefinitionId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.CompletableFuture

enum class StoredAttemptState {
    PREPARING,
    RUNNING,
    FINISHING,
    RECOVERING,
    RECOVERY_PENDING,
    ENDED,
}

enum class OwnedResourceState {
    INTENT,
    ACTIVE,
    CLEANUP,
    RESOLVED,
}

enum class OwnedResourceKind {
    NPC,
    RELIC,
    GRAVE,
    AURA,
    BLOCK_EDIT,
    PRESENTATION,
    PLAYER_STATE,
}

data class RecoveryBudget(
    val totalBytes: Long = 268_435_456,
    val totalRecords: Long = 100_000,
    val resourcesPerAttempt: Int = 1024,
    val bytesPerAttempt: Int = 16_777_216,
) {
    init {
        require(
            totalBytes > 0 && totalRecords > 0 && resourcesPerAttempt > 0 && bytesPerAttempt > 0
        )
    }
}

class AttemptSnapshot(
    val id: UUID,
    val arena: DefinitionId,
    val encounter: DefinitionId,
    roster: List<UUID>,
    placement: ByteArray,
) {
    val roster: List<UUID> = java.util.List.copyOf(roster)
    private val retainedPlacement = placement.copyOf()
    val placement: ByteArray
        get() = retainedPlacement.copyOf()

    init {
        require(
            roster.isNotEmpty() &&
                roster.size <= 1024 &&
                roster.distinct().size == roster.size &&
                placement.size <= 1_048_576
        )
    }

    internal fun encode(): ByteArray =
        ByteArrayOutputStream()
            .also { bytes ->
                DataOutputStream(bytes).use { out ->
                    out.writeInt(1)
                    out.writeInt(roster.size)
                    roster.forEach {
                        out.writeLong(it.mostSignificantBits)
                        out.writeLong(it.leastSignificantBits)
                    }
                    out.writeInt(retainedPlacement.size)
                    out.write(retainedPlacement)
                }
            }
            .toByteArray()

    companion object {
        internal fun decode(
            id: UUID,
            arena: DefinitionId,
            encounter: DefinitionId,
            bytes: ByteArray,
        ): AttemptSnapshot =
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                check(input.readInt() == 1) { "Unsupported attempt snapshot" }
                val size = input.readInt()
                check(size in 1..1024)
                val roster = List(size) { UUID(input.readLong(), input.readLong()) }
                val length = input.readInt()
                check(length in 0..1_048_576 && length <= input.available())
                val result = AttemptSnapshot(id, arena, encounter, roster, input.readNBytes(length))
                check(input.available() == 0)
                result
            }
    }
}

data class StoredAttempt(
    val snapshot: AttemptSnapshot,
    val revision: String,
    val state: StoredAttemptState,
)

sealed interface AttemptPreparation {
    data class Prepared(val record: StoredAttempt, val newlyPrepared: Boolean) : AttemptPreparation

    data class Conflict(val kind: String, val identity: String) : AttemptPreparation

    data object CapacityExceeded : AttemptPreparation
}

class ResourceIntent(
    val id: UUID,
    val attempt: UUID,
    val kind: OwnedResourceKind,
    val identity: String,
    recovery: ByteArray,
) {
    private val retained = recovery.copyOf()
    val recovery
        get() = retained.copyOf()

    init {
        require(
            identity.isNotBlank() &&
                identity.length <= 256 &&
                identity.none(Char::isISOControl) &&
                recovery.size <= 65_536
        )
    }
}

data class StoredResource(val intent: ResourceIntent, val state: OwnedResourceState)

/** Durable engine ownership only. A native adapter must verify world ownership before cleanup. */
class AttemptStore(
    private val database: Database,
    private val budget: RecoveryBudget = RecoveryBudget(),
    private val rewards: RewardLedger = RewardLedger(database),
) {
    init {
        require(rewards.database === database)
    }

    /** Called after native preflight, before any entity creation or participant mutation. */
    fun prepare(
        snapshot: AttemptSnapshot,
        catalog: CompiledCatalog,
        completionBytes: Long = 2048,
        completionRecipients: Map<UUID, Int> = emptyMap(),
    ): CompletableFuture<AttemptPreparation> {
        require(snapshot.encounter in catalog.encounters)
        require(completionBytes >= 2048 && completionRecipients.keys.all { it in snapshot.roster })
        val recipients = completionRecipients.toMap()
        val completionIdentity =
            digest("$completionBytes:${recipients.toSortedMap()}".toByteArray(Charsets.UTF_8))
        val encoded = snapshot.encode()
        val sources = StoredData.sources(catalog.sources)
        val revision = catalog.revision
        val reservedBytes =
            Math.addExact(
                budget.bytesPerAttempt.toLong(),
                encoded.size.toLong() + snapshot.roster.size * 256L + 2048,
            )
        val reservedRecords = budget.resourcesPerAttempt.toLong() + snapshot.roster.size + 1
        return database.transaction {
            readAttempt(snapshot.id)?.let { existing ->
                check(meta("completion_admission/${snapshot.id}") == completionIdentity) {
                    "Attempt identity reused with different completion capacity"
                }
                check(
                    existing.revision == revision &&
                        existing.snapshot.arena == snapshot.arena &&
                        existing.snapshot.encounter == snapshot.encounter &&
                        existing.snapshot.encode().contentEquals(encoded)
                ) {
                    "Attempt identity reused with a different preparation"
                }
                return@transaction AttemptPreparation.Prepared(existing, false)
            }
            val arenaConflict =
                query(
                        "SELECT id FROM attempts WHERE arena=? AND state IN ('preparing','running','finishing','recovering')",
                        snapshot.arena.toString(),
                    ) {
                        it.getString(1)
                    }
                    .firstOrNull()
            if (arenaConflict != null)
                return@transaction AttemptPreparation.Conflict("arena", arenaConflict)
            for (player in snapshot.roster) {
                val previous =
                    query(
                            "SELECT payload FROM player_records WHERE player=? AND kind='attempt_lock' AND id='active'",
                            player.toString(),
                        ) {
                            it.getBytes(1).toString(Charsets.US_ASCII)
                        }
                        .singleOrNull()
                if (previous != null)
                    return@transaction AttemptPreparation.Conflict("player", player.toString())
            }
            val reservations =
                query("SELECT value FROM metadata WHERE key LIKE 'recovery_reservation/%'") {
                    it.getString(1).split(':').let { fields ->
                        fields[0].toLong() to fields[1].toLong()
                    }
                }
            if (
                reservedBytes > budget.totalBytes - reservations.sumOf { it.first } ||
                    reservedRecords > budget.totalRecords - reservations.sumOf { it.second }
            )
                return@transaction AttemptPreparation.CapacityExceeded
            if (!rewards.reserveIn(this, snapshot.id, completionBytes, recipients))
                return@transaction AttemptPreparation.CapacityExceeded
            meta("completion_admission/${snapshot.id}", completionIdentity)
            update(
                "INSERT INTO revisions(id,sources,source_hash,activated) VALUES(?,?,?,0) ON CONFLICT(id) DO NOTHING",
                revision,
                sources,
                digest(sources),
            )
            update(
                "INSERT INTO revision_pins(consumer,revision) VALUES(?,?)",
                snapshot.id.toString(),
                revision,
            )
            update(
                "INSERT INTO attempts(id,revision,arena,encounter,snapshot,state) VALUES(?,?,?,?,?,'preparing')",
                snapshot.id.toString(),
                revision,
                snapshot.arena.toString(),
                snapshot.encounter.toString(),
                encoded,
            )
            for (player in snapshot.roster) update(
                "INSERT INTO player_records(player,kind,id,version,payload) VALUES(?,'attempt_lock','active',1,?)",
                player.toString(),
                snapshot.id.toString().toByteArray(Charsets.US_ASCII),
            )
            meta(
                "recovery_reservation/${snapshot.id}",
                "$reservedBytes:$reservedRecords:${budget.bytesPerAttempt}:${budget.resourcesPerAttempt}",
            )
            AttemptPreparation.Prepared(
                StoredAttempt(snapshot, revision, StoredAttemptState.PREPARING),
                true,
            )
        }
    }

    fun activate(id: UUID): CompletableFuture<Boolean> = database.transaction {
        val existing = checkNotNull(readAttempt(id))
        if (existing.state == StoredAttemptState.RUNNING) true
        else {
            val activated =
                update(
                    "UPDATE attempts SET state='running' WHERE id=? AND state='preparing'",
                    id.toString(),
                ) == 1
            if (activated) meta("attempt_activated/$id", "1")
            activated
        }
    }

    fun wasActivated(id: UUID): CompletableFuture<Boolean> = database.read {
        meta("attempt_activated/$id") == "1"
    }

    fun finishing(id: UUID): CompletableFuture<Boolean> = database.transaction {
        val existing = checkNotNull(readAttempt(id))
        if (existing.state == StoredAttemptState.FINISHING) true
        else
            update(
                "UPDATE attempts SET state='finishing' WHERE id=? AND state='running'",
                id.toString(),
            ) == 1
    }

    fun attempt(id: UUID): CompletableFuture<StoredAttempt?> = database.read { readAttempt(id) }

    /** A false result means this resource intent is already recorded; do not repeat creation. */
    fun record(intent: ResourceIntent): CompletableFuture<Boolean> {
        val data = intent.recovery
        return database.transaction {
            query(
                    "SELECT attempt,kind,identity,recovery FROM owned_resources WHERE id=?",
                    intent.id.toString(),
                ) {
                    check(
                        it.getString(1) == intent.attempt.toString() &&
                            it.getString(2) == intent.kind.name.lowercase() &&
                            it.getString(3) == intent.identity &&
                            it.getBytes(4).contentEquals(data)
                    ) {
                        "Resource identity reused"
                    }
                    false
                }
                .singleOrNull()
                ?.let {
                    return@transaction it
                }
            val owner = checkNotNull(readAttempt(intent.attempt))
            if (intent.kind == OwnedResourceKind.PLAYER_STATE) {
                val player = UUID.fromString(intent.identity)
                require(player.toString() == intent.identity && player in owner.snapshot.roster) {
                    "Player recovery must identify a captured participant"
                }
            }
            check(owner.state in setOf(StoredAttemptState.PREPARING, StoredAttemptState.RUNNING)) {
                "Resource owner no longer accepts creation"
            }
            val reservation =
                checkNotNull(meta("recovery_reservation/${intent.attempt}")).split(':')
            val used =
                query(
                        "SELECT count(*),coalesce(sum(length(recovery)+1024),0) FROM owned_resources WHERE attempt=?",
                        intent.attempt.toString(),
                    ) {
                        it.getInt(1) to it.getLong(2)
                    }
                    .single()
            check(
                used.first < reservation[3].toInt() &&
                    data.size + 1024L <= reservation[2].toLong() - used.second
            ) {
                "Owned-resource recovery budget exceeded"
            }
            update(
                "INSERT INTO owned_resources(id,attempt,kind,identity,recovery,state) VALUES(?,?,?,?,?,'intent')",
                intent.id.toString(),
                intent.attempt.toString(),
                intent.kind.name.lowercase(),
                intent.identity,
                data,
            )
            true
        }
    }

    fun created(resource: UUID): CompletableFuture<Boolean> = database.transaction {
        // Cleanup may have won while the native creation was pending. Keep that cleanup obligation.
        update(
            "UPDATE owned_resources SET state='active' WHERE id=? AND state='intent'",
            resource.toString(),
        ) == 1
    }

    fun beginCleanup(id: UUID): CompletableFuture<Unit> = database.transaction { cleanup(id) }

    fun resources(id: UUID): CompletableFuture<List<StoredResource>> = database.read {
        query(
            "SELECT id,kind,identity,recovery,state FROM owned_resources WHERE attempt=? ORDER BY rowid",
            id.toString(),
        ) {
            StoredResource(
                ResourceIntent(
                    UUID.fromString(it.getString(1)),
                    id,
                    OwnedResourceKind.valueOf(it.getString(2).uppercase()),
                    it.getString(3),
                    it.getBytes(4),
                ),
                OwnedResourceState.valueOf(it.getString(5).uppercase()),
            )
        }
    }

    /** No native state was changed if the attempt never journaled a resource intent. */
    fun releaseUntouched(id: UUID): CompletableFuture<Boolean> = database.transaction {
        val record = checkNotNull(readAttempt(id))
        if (
            query("SELECT count(*) FROM owned_resources WHERE attempt=?", id.toString()) {
                    it.getInt(1)
                }
                .single() != 0
        )
            return@transaction false
        cleanup(id)
        update(
            "DELETE FROM player_records WHERE kind='attempt_lock' AND id='active' AND payload=?",
            id.toString().toByteArray(Charsets.US_ASCII),
        )
        if (record.state != StoredAttemptState.ENDED)
            update("UPDATE attempts SET state='recovery_pending' WHERE id=?", id.toString())
        finishIfRecovered(id)
        true
    }

    fun resolved(resource: UUID): CompletableFuture<Boolean> = database.transaction {
        val state =
            query("SELECT state FROM owned_resources WHERE id=?", resource.toString()) {
                    it.getString(1)
                }
                .single()
        if (state == "resolved") true
        else {
            check(state == "cleanup") { "Resource must be in cleanup before resolving it" }
            update("UPDATE owned_resources SET state='resolved' WHERE id=?", resource.toString()) ==
                1
        }
    }

    /** Releases arena ownership after world cleanup, independently of offline player recovery. */
    fun releaseArena(id: UUID): CompletableFuture<Boolean> = database.transaction {
        val record = checkNotNull(readAttempt(id))
        if (record.state in setOf(StoredAttemptState.RECOVERY_PENDING, StoredAttemptState.ENDED))
            return@transaction true
        check(record.state == StoredAttemptState.RECOVERING)
        val remaining =
            query(
                    "SELECT count(*) FROM owned_resources WHERE attempt=? AND kind!='player_state' AND state!='resolved'",
                    id.toString(),
                ) {
                    it.getInt(1)
                }
                .single()
        if (remaining != 0) return@transaction false
        update("UPDATE attempts SET state='recovery_pending' WHERE id=?", id.toString())
        finishIfRecovered(id)
        true
    }

    fun recoveredPlayer(id: UUID, player: UUID): CompletableFuture<Boolean> = database.transaction {
        val record = checkNotNull(readAttempt(id))
        check(
            record.state in
                setOf(
                    StoredAttemptState.RECOVERING,
                    StoredAttemptState.RECOVERY_PENDING,
                    StoredAttemptState.ENDED,
                )
        )
        require(player in record.snapshot.roster)
        check(
            query(
                    "SELECT count(*) FROM owned_resources WHERE attempt=? AND kind='player_state' AND identity=? AND state!='resolved'",
                    id.toString(),
                    player.toString(),
                ) {
                    it.getInt(1)
                }
                .single() == 0
        ) {
            "Native player recovery still has unresolved obligations"
        }
        val removed =
            update(
                "DELETE FROM player_records WHERE player=? AND kind='attempt_lock' AND id='active' AND payload=?",
                player.toString(),
                id.toString().toByteArray(Charsets.US_ASCII),
            )
        finishIfRecovered(id)
        removed == 1
    }

    /** A returning player remains excluded from new attempts until this record is recovered. */
    fun playerAttempt(player: UUID): CompletableFuture<StoredAttempt?> = database.read {
        val id =
            query(
                    "SELECT payload FROM player_records WHERE player=? AND kind='attempt_lock' AND id='active'",
                    player.toString(),
                ) {
                    UUID.fromString(it.getBytes(1).toString(Charsets.US_ASCII))
                }
                .singleOrNull()
        id?.let { checkNotNull(readAttempt(it)) }
    }

    /** Startup never resumes these attempts or replays gameplay outcomes. */
    fun interrupted(): CompletableFuture<List<StoredAttempt>> = database.transaction {
        val ids =
            query(
                "SELECT id FROM attempts WHERE state IN ('preparing','running','finishing','recovering','recovery_pending') OR id IN (SELECT attempt FROM reservations) ORDER BY rowid"
            ) {
                UUID.fromString(it.getString(1))
            }
        ids.forEach { cleanup(it) }
        ids.map { checkNotNull(readAttempt(it)) }
    }

    private fun Connection.cleanup(id: UUID) {
        val record = checkNotNull(readAttempt(id))
        if (record.state in setOf(StoredAttemptState.ENDED, StoredAttemptState.RECOVERY_PENDING))
            return
        update("UPDATE attempts SET state='recovering' WHERE id=?", id.toString())
        update(
            "UPDATE owned_resources SET state='cleanup' WHERE attempt=? AND state!='resolved'",
            id.toString(),
        )
    }

    private fun Connection.finishIfRecovered(id: UUID) {
        if (readAttempt(id)?.state != StoredAttemptState.RECOVERY_PENDING) return
        val pending =
            query(
                    "SELECT count(*) FROM player_records WHERE kind='attempt_lock' AND id='active' AND payload=?",
                    id.toString().toByteArray(Charsets.US_ASCII),
                ) {
                    it.getInt(1)
                }
                .single()
        if (pending != 0) return
        update("UPDATE attempts SET state='ended' WHERE id=?", id.toString())
        update("DELETE FROM revision_pins WHERE consumer=?", id.toString())
        // Retained history still counts against admission. Its later compaction needs an explicit
        // policy.
        val used =
            query(
                    "SELECT count(*),coalesce(sum(length(recovery)+1024),0) FROM owned_resources WHERE attempt=?",
                    id.toString(),
                ) {
                    it.getLong(1) to it.getLong(2)
                }
                .single()
        val snapshot = checkNotNull(readAttempt(id)).snapshot.encode().size
        meta("recovery_reservation/$id", "${used.second + snapshot + 2048}:${used.first + 1}:0:0")
    }

    private fun Connection.readAttempt(id: UUID): StoredAttempt? =
        query(
                "SELECT revision,arena,encounter,snapshot,state FROM attempts WHERE id=?",
                id.toString(),
            ) {
                StoredAttempt(
                    AttemptSnapshot.decode(
                        id,
                        DefinitionId.parse(it.getString(2)),
                        DefinitionId.parse(it.getString(3)),
                        it.getBytes(4),
                    ),
                    it.getString(1),
                    StoredAttemptState.valueOf(it.getString(5).uppercase()),
                )
            }
            .singleOrNull()
}
