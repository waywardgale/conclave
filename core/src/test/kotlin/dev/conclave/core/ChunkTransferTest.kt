package dev.conclave.core

import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test

class ChunkTransferTest {
    @Test
    fun `staged bytes become visible only after complete validated transfer`() {
        val source = ByteArray(90_000) { (it % 251).toByte() }
        val frames = TransferChunk.split(UUID.randomUUID(), source)
        val assembler = ChunkAssembler()
        val connection = UUID.randomUUID()
        frames.dropLast(1).forEach { assertNull(assembler.accept(connection, it, 0)) }
        assertEquals(source.size, assembler.reservedBytes)
        assertContentEquals(source, assembler.accept(connection, frames.last(), 1))
        assertEquals(0, assembler.reservedBytes)
    }

    @Test
    fun `tampering order and timeouts discard reservations`() {
        val source = ByteArray(30_000) { 1 }
        val frames = TransferChunk.split(UUID.randomUUID(), source)
        val connection = UUID.randomUUID()
        val assembler = ChunkAssembler(timeoutTicks = 10)
        assertNull(assembler.accept(connection, frames.first(), 0))
        assertFailsWith<IllegalArgumentException> {
            assembler.accept(connection, frames.first(), 1)
        }
        assertEquals(0, assembler.reservedBytes)
        assembler.accept(connection, frames.first(), 1)
        assembler.expire(11)
        assertEquals(0, assembler.reservedBytes)
        assembler.accept(connection, frames.first(), 12)
        val last = frames.last()
        val bad = last.bytes.copyOf().also { it[0] = 2 }
        assertFailsWith<IllegalArgumentException> {
            assembler.accept(
                connection,
                TransferChunk(last.transfer, last.index, last.totalBytes, last.hash, bad),
                13,
            )
        }
        assertEquals(0, assembler.reservedBytes)
    }

    @Test
    fun `multiple connections share a hard capacity bound`() {
        val assembler = ChunkAssembler(50_000)
        val frames = TransferChunk.split(UUID.randomUUID(), ByteArray(30_000))
        assembler.accept(UUID.randomUUID(), frames.first(), 0)
        assertFailsWith<IllegalArgumentException> {
            assembler.accept(UUID.randomUUID(), frames.first(), 0)
        }
        assertEquals(30_000, assembler.reservedBytes)
    }
}
