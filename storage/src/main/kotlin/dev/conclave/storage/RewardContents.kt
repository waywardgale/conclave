package dev.conclave.storage

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID

/**
 * Native adapter-validated, self-contained item components; not a reference to mutable authored
 * loot.
 */
class FrozenItem(val item: String, val count: Int, components: ByteArray) {
    private val data = components.copyOf()

    init {
        require(item.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")) && item.length <= 256)
        require(count in 1..1_000_000 && data.size <= 65_536)
    }

    fun components(): ByteArray = data.copyOf()

    fun withCount(value: Int) = FrozenItem(item, value, data)

    fun sameItem(other: FrozenItem): Boolean = item == other.item && data.contentEquals(other.data)

    override fun equals(other: Any?): Boolean =
        other is FrozenItem && count == other.count && sameItem(other)

    override fun hashCode(): Int = 31 * (31 * item.hashCode() + count) + data.contentHashCode()
}

class RewardContents(items: List<FrozenItem>, val xp: Int = 0) {
    val items: List<FrozenItem> = java.util.List.copyOf(items)

    init {
        require(items.size <= 512 && xp >= 0)
    }

    val isEmpty
        get() = items.isEmpty() && xp == 0

    /**
     * Validate the exact multiset before reserving a native transfer. No caller can increase the
     * debt.
     */
    fun subtract(taken: RewardContents): RewardContents {
        require(taken.xp <= xp)
        val counts = items.map { it.count }.toMutableList()
        for (item in taken.items) {
            var needed = item.count
            for ((index, available) in items.withIndex()) {
                if (!item.sameItem(available)) continue
                val quantity = minOf(counts[index], needed)
                counts[index] -= quantity
                needed -= quantity
            }
            require(needed == 0) { "Transfer exceeds recorded contents" }
        }
        return RewardContents(
            items.mapIndexedNotNull { index, item ->
                counts[index].takeIf { it > 0 }?.let(item::withCount)
            },
            xp - taken.xp,
        )
    }

    fun encode(): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(1)
            data.writeInt(xp)
            data.writeInt(items.size)
            for (item in items) {
                data.writeUTF(item.item)
                data.writeInt(item.count)
                val components = item.components()
                data.writeInt(components.size)
                data.write(components)
                require(output.size() <= MAX_BYTES)
            }
        }
        return output.toByteArray()
    }

    /** Reserves bounded transfer receipts, one per item at worst and one for native XP delivery. */
    fun reservedBytes(): Long {
        val transfers = transferReceipts()
        return Math.addExact(2048L + encode().size * 3L, Math.multiplyExact(transfers, 1024L))
    }

    internal fun transferReceipts(): Long =
        Math.addExact(items.sumOf { it.count.toLong() }, if (xp > 0) 1L else 0L)

    override fun equals(other: Any?): Boolean =
        other is RewardContents && items == other.items && xp == other.xp

    override fun hashCode(): Int = 31 * items.hashCode() + xp

    companion object {
        const val MAX_BYTES = 1_048_576

        fun decode(bytes: ByteArray): RewardContents {
            require(bytes.size <= MAX_BYTES)
            return DataInputStream(ByteArrayInputStream(bytes)).use { data ->
                check(data.readInt() == 1) { "Unsupported reward record" }
                val xp = data.readInt()
                val count = data.readInt()
                require(count in 0..512)
                val items =
                    List(count) {
                        val item = data.readUTF()
                        val quantity = data.readInt()
                        val length = data.readInt()
                        require(length in 0..65_536 && length <= data.available())
                        FrozenItem(item, quantity, data.readNBytes(length))
                    }
                check(data.available() == 0)
                RewardContents(items, xp)
            }
        }
    }
}

data class FrozenAllocation(
    val id: UUID,
    val player: UUID,
    val reward: String,
    val contents: RewardContents,
) {
    init {
        require(reward.length in 1..256 && reward.none(Char::isISOControl))
    }
}

class CompletionDecision(
    val attempt: UUID,
    val revision: String,
    val elapsed: Long,
    val payable: Boolean,
    allocations: List<FrozenAllocation>,
) {
    val allocations: List<FrozenAllocation> = java.util.List.copyOf(allocations)

    init {
        require(revision.matches(Regex("[0-9a-f]{64}")) && elapsed >= 0)
        require(
            allocations.size <= 4096 &&
                allocations.map { it.id }.distinct().size == allocations.size
        )
    }

    internal fun identity(): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeUTF(attempt.toString())
            out.writeUTF(revision)
            out.writeLong(elapsed)
            out.writeBoolean(payable)
            for (allocation in allocations.sortedBy { it.id }) {
                out.writeUTF(allocation.id.toString())
                out.writeUTF(allocation.player.toString())
                out.writeUTF(allocation.reward)
                out.writeUTF(digest(allocation.contents.encode()))
            }
        }
        return digest(bytes.toByteArray())
    }
}
