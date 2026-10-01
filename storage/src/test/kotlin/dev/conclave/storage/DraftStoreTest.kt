package dev.conclave.storage

import dev.conclave.core.*
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DraftStoreTest {
    @Test
    fun `only operators can create replace or delete a global settings document`() {
        Database.open(directory.resolve("settings.db")).use { db ->
            val store = DraftStore(db)
            val owner = UUID.randomUUID()
            val id = UUID.randomUUID()
            store.create(id, owner, "Policy test").join()
            val text = "schema: 1\nsettings:\n  revival: {self_revival: true}\n"
            assertEquals(
                DraftSave.OperatorRequired,
                store
                    .save(
                        id,
                        owner,
                        false,
                        UUID.randomUUID(),
                        listOf(DraftChange("settings.yaml", null, text)),
                    )
                    .join(),
            )
            assertIs<DraftSave.Saved>(
                store
                    .save(
                        id,
                        owner,
                        true,
                        UUID.randomUUID(),
                        listOf(DraftChange("settings.yaml", null, text)),
                    )
                    .join()
            )
            val file = store.read(id, owner, false).join()!!.files.single()
            assertEquals(
                DraftSave.OperatorRequired,
                store
                    .save(
                        id,
                        owner,
                        false,
                        UUID.randomUUID(),
                        listOf(DraftChange(file.file, file.token, "schema: [unfinished")),
                    )
                    .join(),
            )
            assertEquals(
                DraftSave.OperatorRequired,
                store
                    .save(
                        id,
                        owner,
                        false,
                        UUID.randomUUID(),
                        listOf(DraftChange(file.file, file.token, null)),
                    )
                    .join(),
            )
            assertIs<DraftSave.Saved>(
                store
                    .save(
                        id,
                        owner,
                        false,
                        UUID.randomUUID(),
                        listOf(DraftChange(file.file, file.token, text)),
                    )
                    .join()
            )
            assertEquals(text, store.read(id, owner, false).join()!!.files.single().text)
        }
    }

    @TempDir lateinit var directory: Path

    private fun id() = UUID.randomUUID()

    private val text =
        """
        # Keep this author comment.
        schema: 1
        encounter:
          id: example
          start: first
          phases:
            - id: first
              duration: 1s
              success: {complete: true}
        """
            .trimIndent()

    @Test
    fun `independent files merge while competing edits retain the server text`() =
        Database.open(directory.resolve("draft.db")).use { db ->
            val store = DraftStore(db)
            val owner = id()
            val other = id()
            val draft = id()
            store.create(draft, owner, "Shared draft", true).join()
            assertIs<DraftSave.Saved>(
                store
                    .save(draft, owner, false, id(), listOf(DraftChange("a.yaml", null, text)))
                    .join()
            )
            val initial = store.read(draft, other, false).join()!!
            val token = initial.files.single().token
            store
                .save(
                    draft,
                    other,
                    false,
                    id(),
                    listOf(DraftChange("b.yaml", null, "unfinished: [")),
                )
                .join()
            assertIs<DraftSave.Saved>(
                store
                    .save(
                        draft,
                        owner,
                        false,
                        id(),
                        listOf(DraftChange("a.yaml", token, text + "\n# New note")),
                    )
                    .join()
            )
            val conflict =
                assertIs<DraftSave.Conflict>(
                    store
                        .save(
                            draft,
                            other,
                            false,
                            id(),
                            listOf(DraftChange("a.yaml", token, "old buffer")),
                        )
                        .join()
                )
            assertEquals(2, conflict.actual.files.size)
            assertEquals(text + "\n# New note", conflict.actual.files.first().text)
        }

    @Test
    fun `save all requires its complete version and commits no partial changes`() =
        Database.open(directory.resolve("draft.db")).use { db ->
            val store = DraftStore(db)
            val owner = id()
            val draft = id()
            val initial = store.create(draft, owner, "A").join()
            store.save(draft, owner, false, id(), listOf(DraftChange("a.yaml", null, text))).join()
            assertIs<DraftSave.Conflict>(
                store
                    .save(
                        draft,
                        owner,
                        false,
                        id(),
                        listOf(DraftChange("b.yaml", null, text)),
                        initial.summary.version,
                    )
                    .join()
            )
            assertEquals(
                listOf("a.yaml"),
                store.read(draft, owner, false).join()!!.files.map { it.file },
            )
        }

    @Test
    fun `sharing does not grant ownership and revoked sharing closes subsequent saves`() =
        Database.open(directory.resolve("draft.db")).use { db ->
            val store = DraftStore(db)
            val owner = id()
            val other = id()
            val draft = id()
            store.create(draft, owner, "A").join()
            assertNull(store.read(draft, other, false).join())
            assertFalse(store.share(draft, other, false, 1, true).join())
            assertTrue(store.share(draft, owner, false, 1, true).join())
            assertNotNull(store.read(draft, other, false).join())
            assertTrue(store.share(draft, owner, false, 2, false).join())
            assertIs<DraftSave.Unavailable>(
                store
                    .save(draft, other, false, id(), listOf(DraftChange("a.yaml", null, text)))
                    .join()
            )
            assertNotNull(store.read(draft, other, true).join())
        }

    @Test
    fun `operation replay never reapplies an old save and text survives reopen`() {
        val owner = id()
        val draft = id()
        val operation = id()
        val change = listOf(DraftChange("a.yaml", null, text))
        Database.open(directory.resolve("draft.db")).use { db ->
            val store = DraftStore(db)
            store.create(draft, owner, "A").join()
            store.save(draft, owner, false, operation, change).join()
            val token = store.read(draft, owner, false).join()!!.files.single().token
            store
                .save(
                    draft,
                    owner,
                    false,
                    id(),
                    listOf(DraftChange("a.yaml", token, text + "\n# next")),
                )
                .join()
        }
        Database.open(directory.resolve("draft.db")).use { db ->
            val store = DraftStore(db)
            assertTrue(
                assertIs<DraftSave.Saved>(store.save(draft, owner, false, operation, change).join())
                    .replay
            )
            assertEquals(
                text + "\n# next",
                store.read(draft, owner, false).join()!!.files.single().text,
            )
        }
    }

    @Test
    fun `publication checks the reviewed draft inside the activation transaction`() =
        Database.open(directory.resolve("draft.db")).use { db ->
            val store = DraftStore(db)
            val content = ContentStore(db)
            val owner = id()
            val draft = id()
            store.create(draft, owner, "A").join()
            store.save(draft, owner, false, id(), listOf(DraftChange("a.yaml", null, text))).join()
            val reviewed = store.read(draft, owner, false).join()!!
            val compiled =
                assertIs<Validation.Valid<CompiledCatalog>>(
                        CatalogCompiler().compile(reviewed.sources())
                    )
                    .value
            store
                .save(
                    draft,
                    owner,
                    false,
                    id(),
                    listOf(
                        DraftChange("a.yaml", reviewed.files.single().token, text + "\n# changed")
                    ),
                )
                .join()
            val stale = DraftPublication(draft, reviewed.summary.version)
            assertIs<SavedPublication.DraftConflict>(
                content.publish(id(), "console", null, compiled, stale).join()
            )
            assertNull(content.current().join())
            val current = store.read(draft, owner, false).join()!!
            val next =
                assertIs<Validation.Valid<CompiledCatalog>>(
                        CatalogCompiler().compile(current.sources())
                    )
                    .value
            assertIs<SavedPublication.Published>(
                content
                    .publish(
                        id(),
                        "console",
                        null,
                        next,
                        DraftPublication(draft, current.summary.version),
                    )
                    .join()
            )
            assertEquals(next.revision, store.read(draft, owner, false).join()!!.summary.baseline)
        }

    @Test
    fun `portable file names reject traversal and absolute paths`() {
        listOf(
                "../outside.yaml",
                "/a.yaml",
                "a/../b.yaml",
                "a\\b.yaml",
                "a//b.yaml",
                "a.yaml\u0000",
                "x.txt",
            )
            .forEach { assertFailsWith<IllegalArgumentException> { DraftStore.validateFile(it) } }
        DraftStore.validateFile("encounters/ritual.yaml")
    }
}
