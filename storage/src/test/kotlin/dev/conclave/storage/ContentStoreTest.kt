package dev.conclave.storage

import dev.conclave.core.CatalogCompiler
import dev.conclave.core.CompiledCatalog
import dev.conclave.core.SourceDocument
import dev.conclave.core.Validation
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ContentStoreTest {
    @TempDir lateinit var directory: Path

    private fun compiled(duration: Int = 1): CompiledCatalog =
        assertIs<Validation.Valid<CompiledCatalog>>(
                CatalogCompiler()
                    .compile(
                        listOf(
                            SourceDocument(
                                "encounter.yaml",
                                """
        schema: 1
        encounter:
          id: test
          start: phase
          phases:
            - id: phase
              duration: ${duration}s
              success: {complete: true}
    """
                                    .trimIndent(),
                            )
                        )
                    )
            )
            .value

    @Test
    fun `publication is durable and retries keep their original result`() {
        val operation = UUID.randomUUID()
        val catalog = compiled()
        val path = directory.resolve("conclave.db")
        Database.open(path).use { database ->
            val store = ContentStore(database)
            assertEquals(
                SavedPublication.Published(catalog.revision),
                store.publish(operation, "console", null, catalog).join(),
            )
            assertEquals(
                SavedPublication.Published(catalog.revision),
                store.publish(operation, "console", null, catalog).join(),
            )
        }
        Database.open(path).use { database ->
            val store = ContentStore(database)
            assertEquals(catalog.sources, store.current().join()?.sources)
            val next = compiled(2)
            store.publish(UUID.randomUUID(), "console", catalog.revision, next).join()
            assertEquals(
                SavedPublication.Published(catalog.revision),
                store.publish(operation, "console", null, catalog).join(),
            )
            assertEquals(next.revision, store.current().join()?.id)
            assertFails { store.publish(operation, "console", next.revision, next).join() }
        }
    }

    @Test
    fun `concurrent publishers compare baseline inside the database transaction`() {
        Database.open(directory.resolve("conclave.db")).use { database ->
            val store = ContentStore(database)
            val results =
                (1..10).map { store.publish(UUID.randomUUID(), "console", null, compiled(it)) }
            CompletableFuture.allOf(*results.toTypedArray()).join()
            assertEquals(1, results.count { it.join() is SavedPublication.Published })
            assertEquals(9, results.count { it.join() is SavedPublication.Conflict })
        }
    }

    @Test
    fun `pinned revisions survive restart and pruning until explicit release`() {
        val path = directory.resolve("conclave.db")
        val pin = UUID.randomUUID()
        val first = compiled()
        Database.open(path).use { db ->
            val store = ContentStore(db, retained = 1)
            store.publish(UUID.randomUUID(), "console", null, first).join()
            assertTrue(store.pin(pin, first.revision).join())
            store.publish(UUID.randomUUID(), "console", first.revision, compiled(2)).join()
        }
        Database.open(path).use { db ->
            val store = ContentStore(db, retained = 1)
            assertNotNull(store.revision(first.revision).join())
            store.release(pin).join()
            store.release(pin).join()
            assertNull(store.revision(first.revision).join())
        }
    }

    @Test
    fun `stale draft saves preserve both the current contents and the rejected edit`() {
        Database.open(directory.resolve("conclave.db")).use { db ->
            val store = ContentStore(db)
            val owner = UUID.randomUUID()
            val original = compiled().sources
            val edit = compiled(2).sources
            assertEquals(DraftWrite.Saved(1), store.saveDraft(owner, null, null, original).join())
            assertEquals(DraftWrite.Saved(2), store.saveDraft(owner, 1, null, edit).join())
            val rejected =
                assertIs<DraftWrite.Conflict>(store.saveDraft(owner, 1, null, original).join())
            assertEquals(edit, rejected.current?.sources)
            assertEquals(original, compiled().sources)
            assertEquals(edit, store.draft(owner).join()?.sources)
        }
    }

    @Test
    fun `GM membership and its audit survive content publication`() {
        Database.open(directory.resolve("conclave.db")).use { db ->
            val authority = AuthorityStore(db)
            val player = UUID.randomUUID()
            authority.setGameMaster("console", player, true).join()
            ContentStore(db).publish(UUID.randomUUID(), "console", null, compiled()).join()
            assertEquals(setOf(player), authority.members().join())
            authority.setGameMaster("console", player, false).join()
            assertTrue(authority.members().join().isEmpty())
            assertEquals(
                listOf("gm_grant", "publish", "gm_revoke"),
                authority.audit().join().map { it.operation },
            )
        }
    }

    @Test
    fun `unknown format is rejected without conversion`() {
        val path = directory.resolve("conclave.db")
        Database.open(path).use { db ->
            db.transaction { createStatement().use { it.execute("PRAGMA user_version=900") } }
                .join()
        }
        assertFailsWith<IllegalStateException> { Database.open(path) }
    }

    @Test
    fun `failed transactions roll back all effects`() {
        Database.open(directory.resolve("conclave.db")).use { db ->
            assertFails {
                db.transaction {
                        meta("probe", "changed")
                        error("fail before commit")
                    }
                    .join()
            }
            assertNull(db.read { meta("probe") }.join())
        }
    }
}
