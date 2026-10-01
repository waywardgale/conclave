package dev.conclave.storage

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * One bounded I/O worker owns the connection. Gameplay code receives futures, never a JDBC handle.
 */
class Database private constructor(private val connection: Connection) : AutoCloseable {
    @Volatile private var workerThread: Thread? = null
    private val worker =
        ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(1024),
            { task ->
                Thread(task, "conclave-storage").apply {
                    isDaemon = true
                    workerThread = this
                }
            },
            ThreadPoolExecutor.AbortPolicy(),
        )
    private var closed = false

    @Synchronized
    internal fun <T> read(action: Connection.() -> T): CompletableFuture<T> = enqueue(action)

    @Synchronized
    internal fun <T> transaction(action: Connection.() -> T): CompletableFuture<T> = enqueue {
        // ASVS 15.4.2: a write reservation precedes all state-dependent reads in the transaction.
        createStatement().use { it.execute("BEGIN IMMEDIATE") }
        try {
            val result = action()
            createStatement().use { it.execute("COMMIT") }
            result
        } catch (failure: Throwable) {
            try {
                createStatement().use { it.execute("ROLLBACK") }
            } catch (rollback: Throwable) {
                failure.addSuppressed(rollback)
            }
            throw failure
        }
    }

    private fun <T> enqueue(action: Connection.() -> T): CompletableFuture<T> {
        if (closed)
            return CompletableFuture.failedFuture(
                IllegalStateException("Conclave storage is closed")
            )
        val result = CompletableFuture<T>()
        try {
            worker.execute {
                try {
                    result.complete(connection.action())
                } catch (failure: Throwable) {
                    result.completeExceptionally(failure)
                }
            }
        } catch (failure: RejectedExecutionException) {
            result.completeExceptionally(failure)
        }
        return result
    }

    override fun close() {
        check(Thread.currentThread() !== workerThread) {
            "Storage cannot await its own worker during shutdown"
        }
        synchronized(this) {
            if (closed) return
            closed = true
            worker.shutdown()
        }
        // Host shutdown owns this wait. The simulation path never calls close or blocks on a
        // future.
        check(worker.awaitTermination(30, TimeUnit.SECONDS)) {
            "Storage operations are still pending"
        }
        connection.close()
    }

    companion object {
        const val SCHEMA_VERSION = 2
        private const val APPLICATION_ID = 0x434f4e43

        /** Open during server preparation, before attempts can be admitted. Path is host-owned. */
        fun open(path: Path): Database {
            Files.createDirectories(path.toAbsolutePath().parent)
            Class.forName("org.sqlite.JDBC")
            val connection = DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}")
            try {
                connection.createStatement().use { sql ->
                    val application =
                        sql.executeQuery("PRAGMA application_id").use {
                            it.next()
                            it.getInt(1)
                        }
                    val version =
                        sql.executeQuery("PRAGMA user_version").use {
                            it.next()
                            it.getInt(1)
                        }
                    val empty =
                        sql.executeQuery(
                                "SELECT count(*) FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'"
                            )
                            .use {
                                it.next()
                                it.getInt(1) == 0
                            }
                    check(
                        (application == APPLICATION_ID && version == SCHEMA_VERSION) ||
                            (application == 0 && version == 0 && empty)
                    ) {
                        "Unsupported Conclave storage format; prepare a supported upgrade or restore the complete backup"
                    }
                    sql.execute("PRAGMA busy_timeout=5000")
                    sql.execute("PRAGMA foreign_keys=ON")
                    sql.execute("PRAGMA trusted_schema=OFF")
                    sql.execute("PRAGMA journal_mode=WAL")
                    sql.execute("PRAGMA synchronous=FULL")
                    sql.execute("PRAGMA fullfsync=ON")
                    sql.execute("PRAGMA checkpoint_fullfsync=ON")
                    if (empty) {
                        sql.execute("BEGIN IMMEDIATE")
                        try {
                            schema.forEach(sql::execute)
                            sql.execute("PRAGMA application_id=$APPLICATION_ID")
                            sql.execute("PRAGMA user_version=$SCHEMA_VERSION")
                            sql.execute("COMMIT")
                        } catch (failure: Throwable) {
                            sql.execute("ROLLBACK")
                            throw failure
                        }
                    }
                    check(
                        sql.executeQuery("PRAGMA quick_check").use {
                            it.next() && it.getString(1) == "ok"
                        }
                    ) {
                        "Conclave storage integrity check failed"
                    }
                }
                return Database(connection)
            } catch (failure: Throwable) {
                connection.close()
                throw failure
            }
        }

        private val schema =
            listOf(
                "CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
                "CREATE TABLE revisions (id TEXT PRIMARY KEY, sources BLOB NOT NULL, source_hash TEXT NOT NULL, activated INTEGER NOT NULL)",
                "CREATE TABLE revision_pins (consumer TEXT PRIMARY KEY, revision TEXT NOT NULL REFERENCES revisions(id))",
                "CREATE TABLE publications (operation TEXT PRIMARY KEY, request_hash TEXT NOT NULL, revision TEXT NOT NULL, result TEXT NOT NULL, current TEXT, utc TEXT NOT NULL)",
                "CREATE TABLE drafts (owner TEXT PRIMARY KEY, version INTEGER NOT NULL, baseline TEXT, sources BLOB NOT NULL)",
                "CREATE TABLE draft_catalogs (id TEXT PRIMARY KEY, owner TEXT NOT NULL, name TEXT NOT NULL, shared INTEGER NOT NULL, version INTEGER NOT NULL, baseline TEXT)",
                "CREATE TABLE draft_files (draft TEXT NOT NULL REFERENCES draft_catalogs(id) ON DELETE CASCADE, file TEXT NOT NULL, token TEXT NOT NULL, text TEXT NOT NULL, PRIMARY KEY(draft,file))",
                "CREATE TABLE draft_operations (draft TEXT NOT NULL REFERENCES draft_catalogs(id) ON DELETE CASCADE, operation TEXT NOT NULL, request_hash TEXT NOT NULL, version INTEGER NOT NULL, PRIMARY KEY(draft,operation))",
                "CREATE TABLE game_masters (player TEXT PRIMARY KEY)",
                "CREATE TABLE audit (sequence INTEGER PRIMARY KEY AUTOINCREMENT, utc TEXT NOT NULL, actor TEXT NOT NULL, operation TEXT NOT NULL, target TEXT NOT NULL, result TEXT NOT NULL, reason TEXT)",
                "CREATE TABLE reservations (attempt TEXT PRIMARY KEY, bytes INTEGER NOT NULL CHECK(bytes>=0), records INTEGER NOT NULL CHECK(records>=0))",
                "CREATE TABLE reservation_players (attempt TEXT NOT NULL REFERENCES reservations(attempt) ON DELETE CASCADE, player TEXT NOT NULL, records INTEGER NOT NULL CHECK(records>=0), PRIMARY KEY(attempt,player))",
                "CREATE TABLE completions (attempt TEXT PRIMARY KEY, request_hash TEXT NOT NULL, revision TEXT NOT NULL, elapsed INTEGER NOT NULL CHECK(elapsed>=0), payable INTEGER NOT NULL, utc TEXT NOT NULL)",
                "CREATE TABLE allocations (id TEXT PRIMARY KEY, attempt TEXT NOT NULL REFERENCES completions(attempt), player TEXT NOT NULL, reward TEXT NOT NULL, contents BLOB, xp INTEGER NOT NULL CHECK(xp>=0), state TEXT NOT NULL, version INTEGER NOT NULL, bytes INTEGER NOT NULL, settled_utc TEXT)",
                "CREATE TABLE transfers (id TEXT PRIMARY KEY, allocation TEXT NOT NULL REFERENCES allocations(id), request_hash TEXT NOT NULL, payload BLOB NOT NULL, remainder BLOB NOT NULL, xp INTEGER NOT NULL, remaining_xp INTEGER NOT NULL, state TEXT NOT NULL, evidence TEXT NOT NULL, utc TEXT NOT NULL)",
                "CREATE UNIQUE INDEX one_active_transfer ON transfers(allocation) WHERE state IN ('transferring','review')",
                "CREATE TABLE attempts (id TEXT PRIMARY KEY, revision TEXT NOT NULL, arena TEXT NOT NULL, encounter TEXT NOT NULL, snapshot BLOB NOT NULL, state TEXT NOT NULL)",
                "CREATE UNIQUE INDEX one_arena_attempt ON attempts(arena) WHERE state IN ('preparing','running','finishing','recovering')",
                "CREATE TABLE owned_resources (id TEXT PRIMARY KEY, attempt TEXT NOT NULL REFERENCES attempts(id), kind TEXT NOT NULL, identity TEXT NOT NULL, recovery BLOB NOT NULL, state TEXT NOT NULL)",
                "CREATE TABLE player_records (player TEXT NOT NULL, kind TEXT NOT NULL, id TEXT NOT NULL, version INTEGER NOT NULL, payload BLOB NOT NULL, PRIMARY KEY(player,kind,id))",
            )
    }
}

// ASVS 1.2.4: all caller data uses bindings; table names and SQL are implementation constants.
internal fun Connection.update(sql: String, vararg values: Any?): Int =
    prepareStatement(sql).use {
        it.bind(values)
        it.executeUpdate()
    }

internal fun <T> Connection.query(
    sql: String,
    vararg values: Any?,
    row: (ResultSet) -> T,
): List<T> =
    prepareStatement(sql).use {
        it.bind(values)
        it.executeQuery().use { results -> buildList { while (results.next()) add(row(results)) } }
    }

private fun PreparedStatement.bind(values: Array<out Any?>) =
    values.forEachIndexed { index, value ->
        when (value) {
            is ByteArray -> setBytes(index + 1, value)
            else -> setObject(index + 1, value)
        }
    }

internal fun Connection.meta(key: String): String? =
    query("SELECT value FROM metadata WHERE key=?", key) { it.getString(1) }.singleOrNull()

internal fun Connection.meta(key: String, value: String) {
    update(
        "INSERT INTO metadata(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
        key,
        value,
    )
}
