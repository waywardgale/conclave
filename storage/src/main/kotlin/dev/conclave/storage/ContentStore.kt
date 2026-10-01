package dev.conclave.storage

import dev.conclave.core.CompiledCatalog
import dev.conclave.core.SourceDocument
import dev.conclave.storage.DraftStore.Companion.snapshot
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

data class SavedRevision(val id: String, val sources: List<SourceDocument>)

data class SavedDraft(val version: Long, val baseline: String?, val sources: List<SourceDocument>)

sealed interface SavedPublication {
    data class Published(val revision: String) : SavedPublication

    data class Conflict(val current: String?) : SavedPublication

    data class DraftConflict(val currentVersion: Long?) : SavedPublication
}

data class DraftPublication(val id: UUID, val version: Long)

sealed interface DraftWrite {
    data class Saved(val version: Long) : DraftWrite

    data class Conflict(val current: SavedDraft?) : DraftWrite
}

/**
 * Durable complete-catalog activation. Authorization is checked by the server before enqueueing.
 */
class ContentStore(private val database: Database, private val retained: Int = 10) {
    init {
        require(retained > 0)
    }

    fun current(): CompletableFuture<SavedRevision?> = database.read {
        val current = meta("current_revision") ?: return@read null
        query("SELECT id,sources,source_hash FROM revisions WHERE id=?", current) {
                val bytes = it.getBytes(2)
                check(digest(bytes) == it.getString(3)) { "Corrupt catalog source record" }
                SavedRevision(it.getString(1), StoredData.sources(bytes))
            }
            .single()
    }

    fun revision(id: String): CompletableFuture<SavedRevision?> = database.read {
        query("SELECT id,sources,source_hash FROM revisions WHERE id=?", id) {
                val bytes = it.getBytes(2)
                check(digest(bytes) == it.getString(3)) { "Corrupt catalog source record" }
                SavedRevision(it.getString(1), StoredData.sources(bytes))
            }
            .singleOrNull()
    }

    fun publish(
        operation: UUID,
        actor: String,
        expected: String?,
        compiled: CompiledCatalog,
        draft: DraftPublication? = null,
    ): CompletableFuture<SavedPublication> {
        val bytes = StoredData.sources(compiled.sources)
        val revision = compiled.revision
        val sourceHash = digest(bytes)
        val requestHash =
            digest(
                "$actor\u0000$expected\u0000$revision\u0000$sourceHash\u0000${draft?.id}\u0000${draft?.version}"
                    .toByteArray()
            )
        return database.transaction {
            query(
                    "SELECT request_hash,result,current FROM publications WHERE operation=?",
                    operation.toString(),
                ) {
                    check(it.getString(1) == requestHash) {
                        "Operation identity reused for a different publication"
                    }
                    when (it.getString(2)) {
                        "published" -> SavedPublication.Published(revision)
                        "draft_conflict" ->
                            SavedPublication.DraftConflict(it.getString(3)?.toLong())
                        else -> SavedPublication.Conflict(it.getString(3))
                    }
                }
                .singleOrNull()
                ?.let {
                    return@transaction it
                }
            val current = meta("current_revision")
            val snapshot = draft?.let { snapshot(it.id) }
            val draftMatches =
                draft == null ||
                    snapshot != null &&
                        snapshot.summary.version == draft.version &&
                        snapshot.summary.baseline == expected &&
                        digest(StoredData.sources(snapshot.sources())) == sourceHash
            val result: SavedPublication =
                if (!draftMatches) SavedPublication.DraftConflict(snapshot?.summary?.version)
                else if (current != expected) SavedPublication.Conflict(current)
                else {
                    val sequence = Math.incrementExact(meta("activation_sequence")?.toLong() ?: 0L)
                    // Same canonical revision retains its original immutable authored source
                    // snapshot.
                    update(
                        "INSERT INTO revisions(id,sources,source_hash,activated) VALUES(?,?,?,?) ON CONFLICT(id) DO UPDATE SET activated=excluded.activated",
                        revision,
                        bytes,
                        sourceHash,
                        sequence,
                    )
                    meta("activation_sequence", sequence.toString())
                    meta("current_revision", revision)
                    draft?.let {
                        update(
                            "UPDATE draft_catalogs SET baseline=?,version=version+1 WHERE id=?",
                            revision,
                            it.id.toString(),
                        )
                    }
                    prune()
                    SavedPublication.Published(revision)
                }
            val kind =
                when (result) {
                    is SavedPublication.Published -> "published"
                    is SavedPublication.DraftConflict -> "draft_conflict"
                    else -> "conflict"
                }
            val utc = Instant.now().toString()
            update(
                "INSERT INTO publications(operation,request_hash,revision,result,current,utc) VALUES(?,?,?,?,?,?)",
                operation.toString(),
                requestHash,
                revision,
                kind,
                if (result is SavedPublication.DraftConflict) result.currentVersion?.toString()
                else current,
                utc,
            )
            update(
                "INSERT INTO audit(utc,actor,operation,target,result) VALUES(?,?,?,?,?)",
                utc,
                actor,
                "publish",
                revision,
                kind,
            )
            result
        }
    }

    fun pin(consumer: UUID, revision: String): CompletableFuture<Boolean> = database.transaction {
        val existing =
            query("SELECT revision FROM revision_pins WHERE consumer=?", consumer.toString()) {
                    it.getString(1)
                }
                .singleOrNull()
        if (existing != null) {
            check(existing == revision)
            return@transaction true
        }
        if (query("SELECT id FROM revisions WHERE id=?", revision) { it.getString(1) }.isEmpty())
            return@transaction false
        update(
            "INSERT INTO revision_pins(consumer,revision) VALUES(?,?)",
            consumer.toString(),
            revision,
        )
        true
    }

    fun release(consumer: UUID): CompletableFuture<Unit> = database.transaction {
        update("DELETE FROM revision_pins WHERE consumer=?", consumer.toString())
        prune()
    }

    fun history(): CompletableFuture<List<String>> = database.read {
        query("SELECT id FROM revisions ORDER BY activated DESC") { it.getString(1) }
    }

    fun draft(owner: UUID): CompletableFuture<SavedDraft?> = database.read {
        draft(owner.toString())
    }

    fun saveDraft(
        owner: UUID,
        expectedVersion: Long?,
        baseline: String?,
        documents: List<SourceDocument>,
    ): CompletableFuture<DraftWrite> {
        val bytes = StoredData.sources(documents)
        return database.transaction {
            val existing = draft(owner.toString())
            if (existing?.version != expectedVersion)
                return@transaction DraftWrite.Conflict(existing)
            val next = Math.incrementExact(expectedVersion ?: 0L)
            update(
                "INSERT INTO drafts(owner,version,baseline,sources) VALUES(?,?,?,?) ON CONFLICT(owner) DO UPDATE SET version=excluded.version,baseline=excluded.baseline,sources=excluded.sources",
                owner.toString(),
                next,
                baseline,
                bytes,
            )
            DraftWrite.Saved(next)
        }
    }

    private fun java.sql.Connection.draft(owner: String): SavedDraft? =
        query("SELECT version,baseline,sources FROM drafts WHERE owner=?", owner) {
                SavedDraft(it.getLong(1), it.getString(2), StoredData.sources(it.getBytes(3)))
            }
            .singleOrNull()

    private fun java.sql.Connection.prune() {
        update(
            "DELETE FROM revisions WHERE id NOT IN (SELECT id FROM revisions ORDER BY activated DESC LIMIT ?) AND id NOT IN (SELECT revision FROM revision_pins)",
            retained,
        )
    }
}
