package dev.conclave.fabric

import dev.conclave.storage.DraftChange
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class LocalAuthorFilesTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `reopening reads the last queued recovery and saved buffers remove it`() {
        LocalAuthorFiles().use { files ->
            val draft = UUID.randomUUID()
            val path = directory.resolve("nested/$draft.bin")
            val first = listOf(DraftChange("a.yaml", null, "name: first"))
            val second = listOf(DraftChange("a.yaml", null, "name: Привет"))
            val savedFirst = files.saveRecovery(path, draft, first)
            val savedSecond = files.saveRecovery(path, draft, second)
            val recovered = files.readRecovery(path, draft)
            savedFirst.result()
            savedSecond.result()
            assertEquals(second, recovered.result()!!.changes)
            files.saveRecovery(path, draft, emptyList()).result()
            assertNull(files.readRecovery(path, draft).result())
            assertFalse(Files.exists(path))
        }
    }

    @Test
    fun `file operations return before disk work and retain an immutable snapshot`() {
        val executor = executor(8)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        executor.execute {
            entered.countDown()
            release.await()
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        LocalAuthorFiles(executor).use { files ->
            try {
                val draft = UUID.randomUUID()
                val path = directory.resolve("recovery.bin")
                val changes = mutableListOf(DraftChange("a.yaml", null, "schema: 1"))
                val saved = files.saveRecovery(path, draft, changes)
                changes.clear()
                assertFalse(saved.isDone)
                assertFalse(Files.exists(path))
                release.countDown()
                saved.result()
                assertEquals(
                    "schema: 1",
                    files.readRecovery(path, draft).result()!!.changes.single().text,
                )
            } finally {
                release.countDown()
            }
        }
    }

    @Test
    fun `full queue rejects extra work without running it on the calling thread`() {
        val executor = executor(1)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        executor.execute {
            entered.countDown()
            release.await()
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        LocalAuthorFiles(executor).use { files ->
            try {
                val queued = files.export(directory, "queued.yaml", "schema: 1")
                val rejected = files.export(directory, "rejected.yaml", "schema: 1")
                assertIs<RejectedExecutionException>(
                    assertFailsWith<ExecutionException> { rejected.result() }.cause
                )
                assertFalse(queued.isDone)
                release.countDown()
                assertTrue(Files.isRegularFile(queued.result()))
                Files.list(directory).use { assertEquals(1, it.count()) }
            } finally {
                release.countDown()
            }
        }
    }

    @Test
    fun `failed replacement and invalid recovery preserve the original data`() {
        LocalAuthorFiles().use { files ->
            val path = directory.resolve("occupied.bin")
            Files.createDirectory(path)
            Files.writeString(path.resolve("keep"), "original")
            assertFailsWith<ExecutionException> {
                files
                    .saveRecovery(path, UUID.randomUUID(), listOf(DraftChange("a.yaml", null, "x")))
                    .result()
            }
            assertEquals("original", Files.readString(path.resolve("keep")))
            Files.list(directory).use { assertEquals(listOf(path), it.toList()) }
            val invalid = directory.resolve("invalid.bin")
            val bytes = byteArrayOf(1, 2, 3)
            Files.write(invalid, bytes)
            assertFailsWith<ExecutionException> {
                files.readRecovery(invalid, UUID.randomUUID()).result()
            }
            assertContentEquals(bytes, Files.readAllBytes(invalid))
        }
    }

    @Test
    fun `imports reject invalid UTF8 oversize files and colliding names`() {
        LocalAuthorFiles().use { files ->
            val a = directory.resolve("a.yaml")
            Files.write(a, byteArrayOf(0xc3.toByte(), 0x28))
            assertFailsWith<ExecutionException> { files.importFiles(listOf(a)).result() }
            Files.writeString(a, "x".repeat(262145))
            assertFailsWith<ExecutionException> { files.importFiles(listOf(a)).result() }
            Files.writeString(a, "name: Привет")
            val second = directory.resolve("nested/a.yaml")
            Files.createDirectories(second.parent)
            Files.writeString(second, "name: second")
            assertFailsWith<ExecutionException> { files.importFiles(listOf(a, second)).result() }
            assertEquals(
                listOf(LocalAuthorFiles.Import("a.yaml", "name: Привет")),
                files.importFiles(listOf(a)).result(),
            )
            assertFailsWith<ExecutionException> { files.importFiles(emptyList()).result() }
        }
    }

    @Test
    fun `exports preserve raw text and never overwrite an earlier export`() {
        LocalAuthorFiles().use { files ->
            val text = "# raw comment\nname: Привет\n"
            val first = files.export(directory, "nested/a.yaml", text).result()
            val second = files.export(directory, "nested/a.yaml", "changed").result()
            assertNotEquals(first, second)
            assertEquals(text, Files.readString(first))
            assertEquals("changed", Files.readString(second))
            assertEquals(directory, first.parent)
            assertFailsWith<ExecutionException> {
                files.export(directory, "../a.yaml", text).result()
            }
        }
    }

    private fun executor(capacity: Int) =
        ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(capacity))

    private fun <T> CompletableFuture<T>.result(): T = get(5, TimeUnit.SECONDS)
}
