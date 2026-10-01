package dev.conclave.storage

import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

data class AuditEntry(
    val sequence: Long,
    val utc: Instant,
    val actor: String,
    val operation: String,
    val target: String,
    val result: String,
    val reason: String?,
)

/**
 * Administrative membership is independent from published content and never restored by rollback.
 */
class AuthorityStore(private val database: Database) {
    fun members(): CompletableFuture<Set<UUID>> = database.read {
        query("SELECT player FROM game_masters ORDER BY player") {
                UUID.fromString(it.getString(1))
            }
            .toSet()
    }

    fun setGameMaster(operator: String, player: UUID, granted: Boolean): CompletableFuture<Unit> =
        database.transaction {
            if (granted)
                update("INSERT OR IGNORE INTO game_masters(player) VALUES(?)", player.toString())
            else update("DELETE FROM game_masters WHERE player=?", player.toString())
            audit(
                operator,
                if (granted) "gm_grant" else "gm_revoke",
                player.toString(),
                "committed",
            )
        }

    fun record(
        actor: String,
        operation: String,
        target: String,
        result: String,
    ): CompletableFuture<Unit> = database.transaction {
        audit(actor, operation, target, result)
    }

    fun audit(after: Long = 0, limit: Int = 100): CompletableFuture<List<AuditEntry>> {
        require(after >= 0 && limit in 1..500)
        return database.read {
            query(
                "SELECT sequence,utc,actor,operation,target,result,reason FROM audit WHERE sequence>? ORDER BY sequence LIMIT ?",
                after,
                limit,
            ) {
                AuditEntry(
                    it.getLong(1),
                    Instant.parse(it.getString(2)),
                    it.getString(3),
                    it.getString(4),
                    it.getString(5),
                    it.getString(6),
                    it.getString(7),
                )
            }
        }
    }
}

internal fun java.sql.Connection.audit(
    actor: String,
    operation: String,
    target: String,
    result: String,
    reason: String? = null,
) {
    require(
        listOf(actor, operation, target, result).all {
            it.length in 1..256 && it.none(Char::isISOControl)
        }
    )
    require(reason == null || reason.length in 1..512 && reason.none(Char::isISOControl))
    update(
        "INSERT INTO audit(utc,actor,operation,target,result,reason) VALUES(?,?,?,?,?,?)",
        Instant.now().toString(),
        actor,
        operation,
        target,
        result,
        reason,
    )
}
