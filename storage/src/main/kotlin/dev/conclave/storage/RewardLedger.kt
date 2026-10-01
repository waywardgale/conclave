package dev.conclave.storage

import java.sql.Connection
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

data class StorageBudget(
    val bytes: Long = 268_435_456,
    val records: Int = 100_000,
    val perPlayer: Int = 1000,
) {
    init {
        require(bytes > 0 && records > 0 && perPlayer > 0)
    }
}

data class Capacity(
    val usedBytes: Long,
    val reservedBytes: Long,
    val records: Long,
    val reservedRecords: Long,
)

enum class AllocationState {
    PENDING,
    TRANSFERRING,
    REVIEW,
    PAID,
    PREVIEW,
    COMPACTED,
}

enum class TransferState {
    TRANSFERRING,
    REVIEW,
    DELIVERED,
    NOT_DELIVERED,
}

enum class ReviewResolution {
    DELIVERED,
    PENDING,
}

data class AllocationRecord(
    val id: UUID,
    val attempt: UUID,
    val player: UUID,
    val reward: String,
    val contents: RewardContents?,
    val state: AllocationState,
    val version: Long,
)

data class TransferRecord(
    val id: UUID,
    val allocation: UUID,
    val state: TransferState,
    val contents: RewardContents?,
    val newlyPrepared: Boolean,
)

/**
 * Success/allocation accounting is atomic here. This module never changes native inventory or XP.
 */
class RewardLedger(
    internal val database: Database,
    private val budget: StorageBudget = StorageBudget(),
) {
    fun capacity(): CompletableFuture<Capacity> = database.read { capacityNow() }

    fun reserve(
        attempt: UUID,
        bytes: Long,
        recipients: Map<UUID, Int>,
    ): CompletableFuture<Boolean> = database.transaction {
        reserveIn(this, attempt, bytes, recipients)
    }

    internal fun reserveIn(
        connection: Connection,
        attempt: UUID,
        bytes: Long,
        recipients: Map<UUID, Int>,
    ): Boolean {
        require(bytes >= 2048 && recipients.size <= 1024 && recipients.values.all { it > 0 })
        val players = recipients.toMap()
        // The byte bound also conservatively covers all initial transfer receipt slots.
        val count =
            maxOf(
                Math.addExact(
                    1L,
                    players.values.fold(0L) { sum, value -> Math.addExact(sum, value.toLong()) },
                ),
                Math.addExact(1L, (bytes - 2048) / 1024),
            )
        return with(connection) {
            val previous =
                query(
                        "SELECT bytes,records FROM reservations WHERE attempt=?",
                        attempt.toString(),
                    ) {
                        it.getLong(1) to it.getLong(2)
                    }
                    .singleOrNull()
            if (previous != null) {
                check(previous == bytes to count) {
                    "Reservation identity reused with different bounds"
                }
                val saved =
                    query(
                            "SELECT player,records FROM reservation_players WHERE attempt=?",
                            attempt.toString(),
                        ) {
                            UUID.fromString(it.getString(1)) to it.getInt(2)
                        }
                        .toMap()
                check(saved == players)
                return@with true
            }
            if (
                query("SELECT attempt FROM completions WHERE attempt=?", attempt.toString()) {
                        it.getString(1)
                    }
                    .isNotEmpty()
            )
                return@with false
            val current = capacityNow()
            if (
                bytes > budget.bytes - current.usedBytes - current.reservedBytes ||
                    count > budget.records - current.records - current.reservedRecords
            )
                return@with false
            for ((player, amount) in players) {
                val held =
                    query(
                            "SELECT count(*) FROM allocations WHERE player=? AND state IN ('pending','transferring','review')",
                            player.toString(),
                        ) {
                            it.getInt(1)
                        }
                        .single()
                val reserved =
                    query(
                            "SELECT coalesce(sum(records),0) FROM reservation_players WHERE player=?",
                            player.toString(),
                        ) {
                            it.getInt(1)
                        }
                        .single()
                if (amount > budget.perPlayer - held - reserved) return@with false
            }
            update(
                "INSERT INTO reservations(attempt,bytes,records) VALUES(?,?,?)",
                attempt.toString(),
                bytes,
                count,
            )
            players.forEach { (player, amount) ->
                update(
                    "INSERT INTO reservation_players(attempt,player,records) VALUES(?,?,?)",
                    attempt.toString(),
                    player.toString(),
                    amount,
                )
            }
            true
        }
    }

    /**
     * Only use after preparation failed or non-success is authoritative, never on an uncertain
     * commit.
     */
    fun releaseReservation(attempt: UUID): CompletableFuture<Unit> = database.transaction {
        update("DELETE FROM reservations WHERE attempt=?", attempt.toString())
    }

    fun commit(decision: CompletionDecision): CompletableFuture<Unit> {
        val request = decision.identity()
        val records = decision.allocations.map { it to it.contents.encode() }
        val used =
            records.fold(2048L) { total, entry ->
                Math.addExact(total, entry.first.contents.reservedBytes())
            }
        val recordCount =
            records.fold(1L) { total, entry ->
                Math.addExact(total, 1 + entry.first.contents.transferReceipts())
            }
        return database.transaction {
            val previous =
                query(
                        "SELECT request_hash FROM completions WHERE attempt=?",
                        decision.attempt.toString(),
                    ) {
                        it.getString(1)
                    }
                    .singleOrNull()
            if (previous != null) {
                check(previous == request) { "Attempt completion cannot be rewritten" }
                return@transaction
            }
            val reservation =
                query(
                        "SELECT bytes,records FROM reservations WHERE attempt=?",
                        decision.attempt.toString(),
                    ) {
                        it.getLong(1) to it.getInt(2)
                    }
                    .singleOrNull()
            check(
                reservation != null &&
                    used <= reservation.first &&
                    recordCount <= reservation.second
            ) {
                "Completion exceeds admitted storage"
            }
            val admitted =
                query(
                        "SELECT player,records FROM reservation_players WHERE attempt=?",
                        decision.attempt.toString(),
                    ) {
                        UUID.fromString(it.getString(1)) to it.getInt(2)
                    }
                    .toMap()
            decision.allocations
                .groupingBy { it.player }
                .eachCount()
                .forEach { (player, count) -> check(count <= (admitted[player] ?: 0)) }
            val now = Instant.now().toString()
            update(
                "UPDATE attempts SET state='finishing' WHERE id=? AND state='running'",
                decision.attempt.toString(),
            )
            update(
                "INSERT INTO completions(attempt,request_hash,revision,elapsed,payable,utc) VALUES(?,?,?,?,?,?)",
                decision.attempt.toString(),
                request,
                decision.revision,
                decision.elapsed,
                decision.payable,
                now,
            )
            for ((allocation, encoded) in records) {
                meta(
                    "reward_receipts/${allocation.id}",
                    allocation.contents.transferReceipts().toString(),
                )
                val state =
                    if (!decision.payable) "preview"
                    else if (allocation.contents.isEmpty) "paid" else "pending"
                update(
                    "INSERT INTO allocations(id,attempt,player,reward,contents,xp,state,version,bytes,settled_utc) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    allocation.id.toString(),
                    decision.attempt.toString(),
                    allocation.player.toString(),
                    allocation.reward,
                    encoded,
                    allocation.contents.xp,
                    state,
                    1,
                    allocation.contents.reservedBytes(),
                    if (state == "paid") now else null,
                )
            }
            update("DELETE FROM reservations WHERE attempt=?", decision.attempt.toString())
        }
    }

    fun committed(decision: CompletionDecision): CompletableFuture<Boolean> = database.read {
        val hash =
            query(
                    "SELECT request_hash FROM completions WHERE attempt=?",
                    decision.attempt.toString(),
                ) {
                    it.getString(1)
                }
                .singleOrNull()
        if (hash != null) check(hash == decision.identity()) { "Conflicting completion identity" }
        hash != null
    }

    fun allocations(player: UUID): CompletableFuture<List<AllocationRecord>> = database.read {
        query(
            "SELECT id,attempt,player,reward,contents,state,version FROM allocations WHERE player=? ORDER BY rowid",
            player.toString(),
            row = ::allocation,
        )
    }

    /**
     * Persist intent before the native adapter can mutate a player's inventory. Replays never
     * repeat it.
     */
    fun beginTransfer(
        id: UUID,
        allocation: UUID,
        player: UUID,
        version: Long,
        contents: RewardContents,
    ): CompletableFuture<TransferRecord> {
        require(!contents.isEmpty)
        val encoded = contents.encode()
        val requestHash =
            digest("$allocation\u0000$player\u0000$version\u0000${digest(encoded)}".toByteArray())
        return database.transaction {
            query(
                    "SELECT allocation,request_hash,state,payload FROM transfers WHERE id=?",
                    id.toString(),
                ) {
                    check(it.getString(2) == requestHash) { "Transfer identity reused" }
                    TransferRecord(
                        id,
                        UUID.fromString(it.getString(1)),
                        TransferState.valueOf(it.getString(3).uppercase()),
                        it.getBytes(4)
                            .takeIf { bytes -> bytes.isNotEmpty() }
                            ?.let(RewardContents::decode),
                        false,
                    )
                }
                .singleOrNull()
                ?.let {
                    return@transaction it
                }
            val saved =
                query(
                        "SELECT id,attempt,player,reward,contents,state,version FROM allocations WHERE id=?",
                        allocation.toString(),
                        row = ::allocation,
                    )
                    .single()
            check(
                saved.player == player &&
                    saved.version == version &&
                    saved.state == AllocationState.PENDING
            ) {
                "Allocation is unavailable or changed"
            }
            val remainder = checkNotNull(saved.contents).subtract(contents)
            // XP is transferred as one bounded native operation, preventing unbounded one-point
            // receipts.
            require(contents.xp == 0 || contents.xp == saved.contents.xp)
            val receiptLimit =
                checkNotNull(meta("reward_receipts/$allocation")) {
                        "Reward receipt budget is missing"
                    }
                    .toLong()
            val receiptCount =
                query("SELECT count(*) FROM transfers WHERE allocation=?", allocation.toString()) {
                        it.getLong(1)
                    }
                    .single()
            if (receiptCount >= receiptLimit) {
                // Failed native transfers and operator-reviewed returns do not consume the debt.
                // Their stable receipts still occupy space, so reserve a new slot before mutation.
                val capacity = capacityNow()
                check(
                    1024 <= budget.bytes - capacity.usedBytes - capacity.reservedBytes &&
                        1 <= budget.records - capacity.records - capacity.reservedRecords
                ) {
                    "Reward receipt capacity is exhausted"
                }
                update("UPDATE allocations SET bytes=bytes+1024 WHERE id=?", allocation.toString())
                meta("reward_receipts/$allocation", Math.incrementExact(receiptLimit).toString())
            }
            update(
                "INSERT INTO transfers(id,allocation,request_hash,payload,remainder,xp,remaining_xp,state,evidence,utc) VALUES(?,?,?,?,?,?,?,?,?,?)",
                id.toString(),
                allocation.toString(),
                requestHash,
                encoded,
                remainder.encode(),
                contents.xp,
                remainder.xp,
                "transferring",
                "intent_recorded",
                Instant.now().toString(),
            )
            update(
                "UPDATE allocations SET state='transferring',version=version+1 WHERE id=?",
                allocation.toString(),
            )
            TransferRecord(id, allocation, TransferState.TRANSFERRING, contents, true)
        }
    }

    fun finishTransfer(id: UUID, delivered: Boolean): CompletableFuture<Unit> =
        database.transaction {
            val state =
                query("SELECT state FROM transfers WHERE id=?", id.toString()) { it.getString(1) }
                    .single()
            if (state == if (delivered) "delivered" else "not_delivered") return@transaction
            check(state == "transferring") {
                "Transfer requires review or already has a different result"
            }
            settle(id, delivered)
        }

    /**
     * On startup, an intent without confirmed evidence is held; it is never automatically paid
     * again.
     */
    fun recoverTransfers(): CompletableFuture<Int> = database.transaction {
        val affected =
            query("SELECT id,allocation FROM transfers WHERE state='transferring'") {
                it.getString(1) to it.getString(2)
            }
        for ((id, allocation) in affected) {
            update(
                "UPDATE transfers SET state='review',evidence='interrupted_native_transfer' WHERE id=?",
                id,
            )
            update("UPDATE allocations SET state='review',version=version+1 WHERE id=?", allocation)
        }
        affected.size
    }

    fun resolveReview(
        id: UUID,
        version: Long,
        resolution: ReviewResolution,
        operator: String,
        reason: String,
    ): CompletableFuture<Unit> {
        require(reason.isNotBlank() && reason.length <= 512 && reason.none(Char::isISOControl))
        return database.transaction {
            val record =
                query(
                        "SELECT t.state,a.version,a.state FROM transfers t JOIN allocations a ON a.id=t.allocation WHERE t.id=?",
                        id.toString(),
                    ) {
                        Triple(it.getString(1), it.getLong(2), it.getString(3))
                    }
                    .single()
            check(record == Triple("review", version, "review")) { "Review state changed" }
            settle(id, resolution == ReviewResolution.DELIVERED)
            audit(operator, "reward_review", id.toString(), resolution.name.lowercase(), reason)
        }
    }

    private fun Connection.settle(id: UUID, delivered: Boolean) {
        val record =
            query(
                    "SELECT allocation,remainder,remaining_xp FROM transfers WHERE id=?",
                    id.toString(),
                ) {
                    Triple(it.getString(1), it.getBytes(2), it.getInt(3))
                }
                .single()
        val current =
            query("SELECT contents FROM allocations WHERE id=?", record.first) { it.getBytes(1) }
                .single()
        val remaining = if (delivered) record.second else current
        val contents = RewardContents.decode(remaining)
        update(
            "UPDATE allocations SET contents=?,xp=?,state=?,version=version+1,settled_utc=? WHERE id=?",
            remaining,
            contents.xp,
            if (contents.isEmpty) "paid" else "pending",
            if (contents.isEmpty) Instant.now().toString() else null,
            record.first,
        )
        // Keep the stable receipt and request hash; discard duplicate payloads once delivery is
        // authoritative.
        update(
            "UPDATE transfers SET state=?,payload=?,remainder=?,evidence=? WHERE id=?",
            if (delivered) "delivered" else "not_delivered",
            byteArrayOf(),
            byteArrayOf(),
            "confirmed",
            id.toString(),
        )
    }

    private fun Connection.capacityNow(): Capacity {
        val used =
            query("SELECT coalesce(sum(bytes),0) FROM allocations") { it.getLong(1) }.single()
        val completions = query("SELECT count(*) FROM completions") { it.getLong(1) }.single()
        val records = query("SELECT count(*) FROM allocations") { it.getLong(1) }.single()
        val receiptSlots =
            query("SELECT value FROM metadata WHERE key LIKE 'reward_receipts/%'") {
                    it.getString(1).toLong()
                }
                .fold(0L, Math::addExact)
        val reservations =
            query("SELECT coalesce(sum(bytes),0),coalesce(sum(records),0) FROM reservations") {
                    it.getLong(1) to it.getLong(2)
                }
                .single()
        return Capacity(
            used + completions * 2048,
            reservations.first,
            records + completions + receiptSlots,
            reservations.second,
        )
    }

    private fun allocation(row: java.sql.ResultSet) =
        AllocationRecord(
            UUID.fromString(row.getString(1)),
            UUID.fromString(row.getString(2)),
            UUID.fromString(row.getString(3)),
            row.getString(4),
            row.getBytes(5)?.let(RewardContents::decode),
            AllocationState.valueOf(row.getString(6).uppercase()),
            row.getLong(7),
        )
}
