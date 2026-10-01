package dev.conclave.fabric

import dev.conclave.core.TransferChunk
import dev.conclave.storage.DraftChange
import dev.conclave.storage.DraftStore
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Ordered, bounded local file work. It never reads client widgets or sends server requests. */
class LocalAuthorFiles internal constructor(private val executor: ThreadPoolExecutor = worker()) :
    AutoCloseable {
    data class Import(val file: String, val text: String)

    init {
        require(executor.corePoolSize == 1 && executor.maximumPoolSize == 1) {
            "Local file work requires one ordered worker"
        }
    }

    private val hashes = linkedMapOf<Path, String>() // Accessed only by the one worker.

    fun saveRecovery(path: Path, draft: UUID, changes: List<DraftChange>): CompletableFuture<Unit> {
        val snapshot = java.util.List.copyOf(changes)
        return submit {
            if (snapshot.isEmpty()) {
                Files.deleteIfExists(path)
                hashes.remove(path)
            } else {
                val bytes = AuthorProtocol.encode(AuthorRequest.Save(draft, snapshot))
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes).contentToString()
                if (hashes[path] != hash) {
                    atomicWrite(path, bytes, replace = true)
                    hashes.remove(path)
                    hashes[path] = hash
                    if (hashes.size > 256) hashes.remove(hashes.keys.first())
                }
            }
        }
    }

    fun readRecovery(path: Path, draft: UUID): CompletableFuture<AuthorRequest.Save?> = submit {
        if (!Files.exists(path)) null
        else {
            val request = AuthorProtocol.request(readBounded(path, TransferChunk.MAX_TOTAL))
            require(request is AuthorRequest.Save && request.draft == draft) {
                "Recovery belongs to another draft or operation"
            }
            request.copy(changes = java.util.List.copyOf(request.changes))
        }
    }

    fun export(directory: Path, file: String, text: String): CompletableFuture<Path> = submit {
        DraftStore.validateFile(file)
        require(text.length <= 262144)
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 262144)
        val path = directory.resolve("${UUID.randomUUID()}-${file.substringAfterLast('/')}")
        atomicWrite(path, bytes, replace = false)
        path
    }

    fun importFiles(paths: List<Path>): CompletableFuture<List<Import>> {
        if (paths.size !in 1..256)
            return CompletableFuture.failedFuture(
                IllegalArgumentException("Select 1 to 256 YAML files")
            )
        val selected = java.util.List.copyOf(paths)
        return submit {
            var total = 0L
            val names = mutableSetOf<String>()
            java.util.List.copyOf(
                selected.map { path ->
                    val name = path.fileName.toString()
                    DraftStore.validateFile(name)
                    require(names.add(name)) { "Selected filenames collide" }
                    val bytes = readBounded(path, 262144)
                    total += bytes.size
                    require(total <= 4_194_304) { "Import exceeds the catalog byte limit" }
                    val text =
                        Charsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString()
                    Import(name, text)
                }
            )
        }
    }

    private fun readBounded(path: Path, maximum: Int): ByteArray {
        require(Files.isRegularFile(path)) { "Select a regular file" }
        return Files.newInputStream(path).use { stream ->
            stream.readNBytes(maximum + 1).also {
                require(it.size <= maximum) { "File exceeds its byte limit" }
            }
        }
    }

    private fun atomicWrite(path: Path, bytes: ByteArray, replace: Boolean) {
        Files.createDirectories(path.parent)
        val temporary = Files.createTempFile(path.parent, "conclave-", ".tmp")
        try {
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            val options =
                if (replace)
                    arrayOf(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                else arrayOf(StandardCopyOption.ATOMIC_MOVE)
            Files.move(temporary, path, *options)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private class Work<T>(val result: CompletableFuture<T>, val operation: () -> T) : Runnable {
        override fun run() {
            try {
                result.complete(operation())
            } catch (failure: Exception) {
                result.completeExceptionally(failure)
            }
        }
    }

    private fun <T> submit(operation: () -> T): CompletableFuture<T> {
        val result = CompletableFuture<T>()
        try {
            executor.execute(Work(result, operation))
        } catch (failure: RejectedExecutionException) {
            result.completeExceptionally(failure)
        }
        return result
    }

    /** Only called during application shutdown or tests, never when closing an editor screen. */
    override fun close() {
        executor.shutdown()
        try {
            if (executor.awaitTermination(5, TimeUnit.SECONDS)) return
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        for (work in executor.shutdownNow()) if (work is Work<*>)
            work.result.completeExceptionally(
                RejectedExecutionException("Client stopped before local file work completed")
            )
    }

    companion object {
        val shared: LocalAuthorFiles by lazy { LocalAuthorFiles() }

        private fun worker() =
            ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.SECONDS,
                ArrayBlockingQueue(8),
                { task ->
                    Thread(task, "Conclave local author files").apply { isDaemon = true }
                },
                ThreadPoolExecutor.AbortPolicy(),
            )
    }
}
