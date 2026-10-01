package dev.conclave.storage

import dev.conclave.core.ContentLimits
import dev.conclave.core.SourceDocument
import dev.conclave.core.Validation
import dev.conclave.core.YamlDocumentReader
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.CompletableFuture

data class DraftFile(val file: String, val token: UUID, val text: String)

data class DraftSummary(
    val id: UUID,
    val owner: UUID,
    val name: String,
    val shared: Boolean,
    val version: Long,
    val baseline: String?,
)

class DraftSnapshot(val summary: DraftSummary, files: List<DraftFile>) {
    val files: List<DraftFile> = java.util.List.copyOf(files)

    fun sources(): List<SourceDocument> = files.map { SourceDocument(it.file, it.text) }
}

data class DraftChange(val file: String, val expected: UUID?, val text: String?)

sealed interface DraftSave {
    data class Saved(val version: Long, val replay: Boolean = false) : DraftSave

    data class Conflict(val actual: DraftSnapshot) : DraftSave

    data object Unavailable : DraftSave

    data object OperatorRequired : DraftSave
}

/**
 * Saved YAML stays byte-for-byte text. Tokens prevent overwrites across editors, forms, and
 * imports.
 */
class DraftStore(
    private val database: Database,
    private val limits: ContentLimits = ContentLimits(),
) {
    fun list(editor: UUID, operator: Boolean): CompletableFuture<List<DraftSummary>> =
        database.read {
            query(
                "SELECT id,owner,name,shared,version,baseline FROM draft_catalogs WHERE owner=? OR shared=1 OR ?=1 ORDER BY name,id",
                editor.toString(),
                if (operator) 1 else 0,
                row = ::summary,
            )
        }

    fun read(id: UUID, editor: UUID, operator: Boolean): CompletableFuture<DraftSnapshot?> =
        database.read {
            snapshot(id)?.takeIf { it.summary.accessible(editor, operator) }
        }

    fun create(
        id: UUID,
        editor: UUID,
        name: String,
        shared: Boolean = false,
    ): CompletableFuture<DraftSnapshot> {
        require(name.isNotBlank() && name.length <= 64 && name.none(Char::isISOControl))
        return database.transaction {
            snapshot(id)?.let {
                check(
                    it.summary.owner == editor &&
                        it.summary.name == name &&
                        it.summary.shared == shared
                ) {
                    "Draft creation identity was reused"
                }
                return@transaction it
            }
            check(query("SELECT count(*) FROM draft_catalogs") { it.getInt(1) }.single() < 64) {
                "Server draft capacity is full"
            }
            check(
                query("SELECT count(*) FROM draft_catalogs WHERE owner=?", editor.toString()) {
                        it.getInt(1)
                    }
                    .single() < 16
            ) {
                "Author draft capacity is full"
            }
            val baseline = meta("current_revision")
            val sources =
                baseline?.let { revision ->
                    query("SELECT sources,source_hash FROM revisions WHERE id=?", revision) { row ->
                            row.getBytes(1).also { check(digest(it) == row.getString(2)) }
                        }
                        .single()
                        .let(StoredData::sources)
                } ?: emptyList()
            update(
                "INSERT INTO draft_catalogs(id,owner,name,shared,version,baseline) VALUES(?,?,?,?,1,?)",
                id.toString(),
                editor.toString(),
                name,
                if (shared) 1 else 0,
                baseline,
            )
            sources.forEach {
                update(
                    "INSERT INTO draft_files(draft,file,token,text) VALUES(?,?,?,?)",
                    id.toString(),
                    it.file,
                    UUID.randomUUID().toString(),
                    it.text,
                )
            }
            checkNotNull(snapshot(id))
        }
    }

    fun save(
        id: UUID,
        editor: UUID,
        operator: Boolean,
        operation: UUID,
        changes: List<DraftChange>,
        expectedVersion: Long? = null,
    ): CompletableFuture<DraftSave> {
        val immutable = java.util.List.copyOf(changes)
        require(
            immutable.isNotEmpty() &&
                immutable.size <= limits.files &&
                immutable.map { it.file }.distinct().size == immutable.size
        )
        immutable.forEach {
            validateFile(it.file)
            it.text?.let { text ->
                require(text.toByteArray(Charsets.UTF_8).size <= limits.documentBytes)
            }
        }
        val requestHash =
            digest(
                buildString {
                        append(editor)
                        append(':')
                        append(expectedVersion)
                        immutable
                            .sortedBy { it.file }
                            .forEach {
                                append('\u0000')
                                append(it.file)
                                append('\u0000')
                                append(it.expected)
                                append('\u0000')
                                append(
                                    it.text?.let { text ->
                                        digest(text.toByteArray(Charsets.UTF_8))
                                    } ?: "delete"
                                )
                            }
                    }
                    .toByteArray(Charsets.UTF_8)
            )
        return database.transaction {
            val current =
                snapshot(id)?.takeIf { it.summary.accessible(editor, operator) }
                    ?: return@transaction DraftSave.Unavailable
            query(
                    "SELECT request_hash,version FROM draft_operations WHERE draft=? AND operation=?",
                    id.toString(),
                    operation.toString(),
                ) {
                    check(it.getString(1) == requestHash) { "Save operation identity was reused" }
                    DraftSave.Saved(it.getLong(2), true)
                }
                .singleOrNull()
                ?.let {
                    return@transaction it
                }
            if (expectedVersion != null && current.summary.version != expectedVersion)
                return@transaction DraftSave.Conflict(current)
            val files = current.files.associateBy { it.file }.toMutableMap()
            if (immutable.any { files[it.file]?.token != it.expected })
                return@transaction DraftSave.Conflict(current)
            if (
                !operator &&
                    immutable.any { change ->
                        val previous = files[change.file]?.text
                        previous != change.text &&
                            (settingsDocument(change.file, previous) ||
                                settingsDocument(change.file, change.text))
                    }
            )
                return@transaction DraftSave.OperatorRequired
            immutable.forEach { change ->
                if (change.text == null) files.remove(change.file)
                else files[change.file] = DraftFile(change.file, UUID.randomUUID(), change.text)
            }
            require(
                files.size <= limits.files &&
                    files.values.sumOf { it.text.toByteArray(Charsets.UTF_8).size.toLong() } <=
                        limits.catalogBytes
            ) {
                "Draft content capacity exceeded"
            }
            check(
                query("SELECT count(*) FROM draft_operations WHERE draft=?", id.toString()) {
                        it.getInt(1)
                    }
                    .single() < 100_000
            ) {
                "Draft operation retention is full; create a reviewed replacement draft"
            }
            val version = Math.incrementExact(current.summary.version)
            immutable.forEach { change ->
                if (change.text == null)
                    update(
                        "DELETE FROM draft_files WHERE draft=? AND file=?",
                        id.toString(),
                        change.file,
                    )
                else
                    update(
                        "INSERT INTO draft_files(draft,file,token,text) VALUES(?,?,?,?) ON CONFLICT(draft,file) DO UPDATE SET token=excluded.token,text=excluded.text",
                        id.toString(),
                        change.file,
                        files.getValue(change.file).token.toString(),
                        change.text,
                    )
            }
            update("UPDATE draft_catalogs SET version=? WHERE id=?", version, id.toString())
            update(
                "INSERT INTO draft_operations(draft,operation,request_hash,version) VALUES(?,?,?,?)",
                id.toString(),
                operation.toString(),
                requestHash,
                version,
            )
            DraftSave.Saved(version)
        }
    }

    fun share(
        id: UUID,
        editor: UUID,
        operator: Boolean,
        version: Long,
        shared: Boolean,
    ): CompletableFuture<Boolean> = database.transaction {
        update(
            "UPDATE draft_catalogs SET shared=?,version=version+1 WHERE id=? AND version=? AND (owner=? OR ?=1)",
            if (shared) 1 else 0,
            id.toString(),
            version,
            editor.toString(),
            if (operator) 1 else 0,
        ) == 1
    }

    companion object {
        private fun settingsDocument(file: String, text: String?): Boolean {
            if (text == null) return false
            val parsed = YamlDocumentReader().read(SourceDocument(file, text))
            return parsed is Validation.Valid && "settings" in parsed.value.entries
        }

        /** A portable authoring identifier, never a server filesystem path. */
        fun validateFile(file: String) {
            require(
                file.length in 1..256 &&
                    file.none(Char::isISOControl) &&
                    '\\' !in file &&
                    ':' !in file &&
                    file.split('/').all { it.isNotEmpty() && it != "." && it != ".." } &&
                    (file.endsWith(".yaml") || file.endsWith(".yml"))
            ) {
                "Use a relative YAML filename without traversal or control characters"
            }
        }

        private fun summary(row: java.sql.ResultSet) =
            DraftSummary(
                UUID.fromString(row.getString(1)),
                UUID.fromString(row.getString(2)),
                row.getString(3),
                row.getInt(4) != 0,
                row.getLong(5),
                row.getString(6),
            )

        internal fun Connection.snapshot(id: UUID): DraftSnapshot? {
            val summary =
                query(
                        "SELECT id,owner,name,shared,version,baseline FROM draft_catalogs WHERE id=?",
                        id.toString(),
                        row = ::summary,
                    )
                    .singleOrNull() ?: return null
            return DraftSnapshot(
                summary,
                query(
                    "SELECT file,token,text FROM draft_files WHERE draft=? ORDER BY file",
                    id.toString(),
                ) {
                    DraftFile(it.getString(1), UUID.fromString(it.getString(2)), it.getString(3))
                },
            )
        }

        private fun DraftSummary.accessible(editor: UUID, operator: Boolean) =
            operator || shared || owner == editor
    }
}
