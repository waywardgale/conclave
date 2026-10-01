package dev.conclave.core

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID

class TransferChunk(
    val transfer: UUID,
    val index: Int,
    val totalBytes: Int,
    hash: ByteArray,
    bytes: ByteArray,
) {
    val hash = hash.copyOf()
    val bytes = bytes.copyOf()

    init {
        require(
            index >= 0 &&
                totalBytes in 1..MAX_TOTAL &&
                hash.size == 32 &&
                bytes.size in 1..CHUNK_BYTES
        )
    }

    companion object {
        const val CHUNK_BYTES = 24_576
        const val MAX_TOTAL = 4_718_592

        fun split(id: UUID, bytes: ByteArray): List<TransferChunk> {
            require(bytes.size in 1..MAX_TOTAL)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            return (bytes.indices step CHUNK_BYTES).mapIndexed { index, offset ->
                TransferChunk(
                    id,
                    index,
                    bytes.size,
                    hash,
                    bytes.copyOfRange(offset, minOf(offset + CHUNK_BYTES, bytes.size)),
                )
            }
        }
    }
}

/**
 * One ordered transfer per authenticated connection. Complete data is returned only after its
 * digest matches.
 */
class ChunkAssembler(
    private val totalCapacity: Int = 33_554_432,
    private val timeoutTicks: Long = 1200,
) {
    private class Upload(val id: UUID, val size: Int, val hash: ByteArray, val deadline: Long) {
        val bytes = ByteArrayOutputStream(minOf(size, TransferChunk.CHUNK_BYTES))
        var next = 0
    }

    private val uploads = mutableMapOf<UUID, Upload>()
    private var reserved = 0

    init {
        require(totalCapacity >= TransferChunk.CHUNK_BYTES && timeoutTicks > 0)
    }

    fun accept(connection: UUID, chunk: TransferChunk, tick: Long): ByteArray? {
        expire(tick)
        var upload = uploads[connection]
        if (upload == null) {
            require(chunk.index == 0) { "Transfer must begin at its first chunk" }
            require(chunk.totalBytes <= totalCapacity - reserved) {
                "Transfer staging capacity is full"
            }
            upload =
                Upload(
                    chunk.transfer,
                    chunk.totalBytes,
                    chunk.hash.copyOf(),
                    Math.addExact(tick, timeoutTicks),
                )
            uploads[connection] = upload
            reserved += upload.size
        }
        try {
            require(
                upload.id == chunk.transfer &&
                    upload.size == chunk.totalBytes &&
                    upload.hash.contentEquals(chunk.hash)
            ) {
                "Another transfer is already in progress"
            }
            require(chunk.index == upload.next) { "Transfer chunks must arrive once and in order" }
            val expected = minOf(TransferChunk.CHUNK_BYTES, upload.size - upload.bytes.size())
            require(chunk.bytes.size == expected) { "Transfer chunk has an invalid length" }
            upload.bytes.write(chunk.bytes)
            upload.next++
            if (upload.bytes.size() != upload.size) return null
            val result = upload.bytes.toByteArray()
            require(
                MessageDigest.isEqual(
                    upload.hash,
                    MessageDigest.getInstance("SHA-256").digest(result),
                )
            ) {
                "Transfer content hash does not match"
            }
            discard(connection)
            return result
        } catch (failure: Exception) {
            discard(connection)
            throw failure
        }
    }

    fun discard(connection: UUID) {
        uploads.remove(connection)?.let { reserved -= it.size }
    }

    fun expire(tick: Long) {
        uploads.filterValues { tick >= it.deadline }.keys.toList().forEach(::discard)
    }

    fun connections(): Set<UUID> = uploads.keys.toSet()

    val reservedBytes
        get() = reserved
}
