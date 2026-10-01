package dev.conclave.fabric

import dev.conclave.fabric.mixin.PlayerListSaveAccess
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.LevelResource

/** A native save receipt, carried through ordinary native body replacement. */
interface ConclavePlayerReceipt {
    fun conclaveRecoveryReceipt(): UUID?

    fun conclaveRecoveryReceipt(value: UUID?)
}

/**
 * A native save returning void is not evidence of success. Confirm the operation in the actual
 * player file and force that file and its directory before releasing the durable player lock. The
 * native snapshot is produced on the server thread; bounded verification runs off-thread.
 */
internal class NativePlayerSave(private val server: MinecraftServer) : AutoCloseable {
    private val worker =
        ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(64),
            { task ->
                Thread(task, "Conclave player save confirmation").apply { isDaemon = true }
            },
            ThreadPoolExecutor.AbortPolicy(),
        )

    fun confirm(player: ServerPlayer, operation: UUID): CompletableFuture<Unit> {
        check(server.isSameThread)
        require(server.playerList.getPlayer(player.uuid) === player)
        val result = CompletableFuture<Unit>()
        try {
            (player as ConclavePlayerReceipt).conclaveRecoveryReceipt(operation)
            (server.playerList as PlayerListSaveAccess).conclaveSavePlayer(player)
            val path =
                server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve("${player.uuid}.dat")
            worker.execute {
                try {
                    FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE).use {
                        channel ->
                        val size = channel.size()
                        check(size in 1..16_777_216) {
                            "Native player file exceeds confirmation limits"
                        }
                        val buffer = ByteBuffer.allocate(size.toInt())
                        while (buffer.hasRemaining()) check(channel.read(buffer) >= 0) {
                            "Native player file changed during confirmation"
                        }
                        val saved =
                            NbtIo.readCompressed(
                                ByteArrayInputStream(buffer.array()),
                                NbtAccounter.create(16_777_216),
                            )
                        check(saved.getStringOr(RECEIPT_TAG, "") == operation.toString()) {
                            "Native player save did not contain its recovery receipt"
                        }
                        channel.force(true)
                    }
                    FileChannel.open(path.parent, StandardOpenOption.READ).use { it.force(true) }
                    result.complete(Unit)
                } catch (failure: Exception) {
                    result.completeExceptionally(failure)
                }
            }
        } catch (failure: Exception) {
            result.completeExceptionally(failure)
        }
        return result
    }

    override fun close() {
        worker.shutdown()
        check(worker.awaitTermination(30, TimeUnit.SECONDS)) {
            "Native player confirmations remain pending"
        }
    }

    companion object {
        const val RECEIPT_TAG = "conclave:recovery_receipt"
    }
}
