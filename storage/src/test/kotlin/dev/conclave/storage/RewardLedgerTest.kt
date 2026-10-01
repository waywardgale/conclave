package dev.conclave.storage

import java.nio.file.Path
import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class RewardLedgerTest {
    @TempDir lateinit var directory: Path
    private val player = UUID.randomUUID()
    private val item = FrozenItem("minecraft:diamond", 3, byteArrayOf(1, 2, 3))

    @Test
    fun `failed transfer retries reserve receipt capacity before creating another intent`() {
        Database.open(directory.resolve("ledger.db")).use { db ->
            val saved = decision(contents = RewardContents(listOf(item.withCount(1))))
            val bound = 2048 + saved.allocations.single().contents.reservedBytes()
            val ledger = RewardLedger(db, StorageBudget(bytes = bound))
            reserve(ledger, saved)
            ledger.commit(saved).join()
            val allocation = ledger.allocations(player).join().single()
            val transfer = UUID.randomUUID()
            ledger
                .beginTransfer(
                    transfer,
                    allocation.id,
                    player,
                    allocation.version,
                    allocation.contents!!,
                )
                .join()
            ledger.finishTransfer(transfer, false).join()
            val pending = ledger.allocations(player).join().single()
            assertFails {
                ledger
                    .beginTransfer(
                        UUID.randomUUID(),
                        pending.id,
                        player,
                        pending.version,
                        pending.contents!!,
                    )
                    .join()
            }
            assertEquals(AllocationState.PENDING, ledger.allocations(player).join().single().state)
            assertEquals(bound, ledger.capacity().join().usedBytes)
            assertFalse(
                ledger
                    .beginTransfer(
                        transfer,
                        allocation.id,
                        player,
                        allocation.version,
                        allocation.contents,
                    )
                    .join()
                    .newlyPrepared
            )
            val expanded = RewardLedger(db, StorageBudget(bytes = bound + 1024))
            val retry =
                expanded
                    .beginTransfer(
                        UUID.randomUUID(),
                        pending.id,
                        player,
                        pending.version,
                        pending.contents!!,
                    )
                    .join()
            assertTrue(retry.newlyPrepared)
            assertEquals(bound + 1024, expanded.capacity().join().usedBytes)
            assertEquals(4L, expanded.capacity().join().records)
            expanded.finishTransfer(retry.id, true).join()
            assertEquals(AllocationState.PAID, expanded.allocations(player).join().single().state)
        }
    }

    private fun decision(
        payable: Boolean = true,
        contents: RewardContents = RewardContents(listOf(item), 20),
    ) =
        CompletionDecision(
            UUID.randomUUID(),
            "a".repeat(64),
            120,
            payable,
            listOf(FrozenAllocation(UUID.randomUUID(), player, "completion", contents)),
        )

    private fun reserve(ledger: RewardLedger, decision: CompletionDecision) {
        assertTrue(
            ledger
                .reserve(
                    decision.attempt,
                    2048 + decision.allocations.sumOf { it.contents.reservedBytes() },
                    decision.allocations.groupingBy { it.player }.eachCount(),
                )
                .join()
        )
    }

    @Test
    fun `success and all allocations commit together and cannot be rewritten`() {
        val saved = decision()
        val path = directory.resolve("ledger.db")
        Database.open(path).use { db ->
            val ledger = RewardLedger(db)
            assertFails { ledger.commit(saved).join() }
            assertFalse(ledger.committed(saved).join())
            assertTrue(ledger.allocations(player).join().isEmpty())
            reserve(ledger, saved)
            ledger.commit(saved).join()
            ledger.commit(saved).join()
            assertTrue(ledger.committed(saved).join())
            assertEquals(1, ledger.allocations(player).join().size)
            assertEquals(0L, ledger.capacity().join().reservedBytes)
        }
        Database.open(path).use { db ->
            val ledger = RewardLedger(db)
            assertTrue(ledger.committed(saved).join())
            assertEquals(
                saved.allocations.single().contents,
                ledger.allocations(player).join().single().contents,
            )
            assertFails {
                ledger
                    .commit(
                        CompletionDecision(
                            saved.attempt,
                            saved.revision,
                            121,
                            true,
                            saved.allocations,
                        )
                    )
                    .join()
            }
        }
    }

    @Test
    fun `rewardless success uses the same durable commitment`() {
        Database.open(directory.resolve("ledger.db")).use { db ->
            val ledger = RewardLedger(db)
            val saved = CompletionDecision(UUID.randomUUID(), "a".repeat(64), 5, true, emptyList())
            reserve(ledger, saved)
            ledger.commit(saved).join()
            assertTrue(ledger.committed(saved).join())
            assertEquals(2048, ledger.capacity().join().usedBytes)
        }
    }

    @Test
    fun `capacity is reserved across concurrent starts without deleting existing obligations`() {
        Database.open(directory.resolve("ledger.db")).use { db ->
            val ledger =
                RewardLedger(db, StorageBudget(bytes = 10_000, records = 100, perPlayer = 1))
            assertTrue(ledger.reserve(UUID.randomUUID(), 4000, mapOf(player to 1)).join())
            assertFalse(ledger.reserve(UUID.randomUUID(), 4000, mapOf(player to 1)).join())
            assertFalse(ledger.reserve(UUID.randomUUID(), 8000, emptyMap()).join())
            assertEquals(4000L, ledger.capacity().join().reservedBytes)
        }
    }

    @Test
    fun `inspection Test allocations never become payable`() {
        Database.open(directory.resolve("ledger.db")).use { db ->
            val ledger = RewardLedger(db)
            val saved = decision(payable = false)
            reserve(ledger, saved)
            ledger.commit(saved).join()
            val allocation = ledger.allocations(player).join().single()
            assertEquals(AllocationState.PREVIEW, allocation.state)
            assertFails {
                ledger
                    .beginTransfer(
                        UUID.randomUUID(),
                        allocation.id,
                        player,
                        allocation.version,
                        saved.allocations.single().contents,
                    )
                    .join()
            }
            assertFails {
                ledger
                    .commit(
                        CompletionDecision(
                            saved.attempt,
                            saved.revision,
                            saved.elapsed,
                            true,
                            saved.allocations,
                        )
                    )
                    .join()
            }
        }
    }

    @Test
    fun `partial transfers preserve exact remainder and retries cannot authorize another mutation`() {
        Database.open(directory.resolve("ledger.db")).use { db ->
            val ledger = RewardLedger(db)
            val saved = decision()
            reserve(ledger, saved)
            ledger.commit(saved).join()
            val allocation = ledger.allocations(player).join().single()
            val transfer = UUID.randomUUID()
            val taken = RewardContents(listOf(item.withCount(1)))
            assertTrue(
                ledger
                    .beginTransfer(transfer, allocation.id, player, allocation.version, taken)
                    .join()
                    .newlyPrepared
            )
            assertFalse(
                ledger
                    .beginTransfer(transfer, allocation.id, player, allocation.version, taken)
                    .join()
                    .newlyPrepared
            )
            assertFails {
                ledger
                    .beginTransfer(
                        UUID.randomUUID(),
                        allocation.id,
                        player,
                        allocation.version,
                        taken,
                    )
                    .join()
            }
            ledger.finishTransfer(transfer, true).join()
            ledger.finishTransfer(transfer, true).join()
            val remaining = ledger.allocations(player).join().single()
            assertEquals(RewardContents(listOf(item.withCount(2)), 20), remaining.contents)
            assertFalse(
                ledger
                    .beginTransfer(transfer, allocation.id, player, allocation.version, taken)
                    .join()
                    .newlyPrepared
            )
            val last =
                ledger
                    .beginTransfer(
                        UUID.randomUUID(),
                        remaining.id,
                        player,
                        remaining.version,
                        remaining.contents!!,
                    )
                    .join()
            ledger.finishTransfer(last.id, true).join()
            assertEquals(AllocationState.PAID, ledger.allocations(player).join().single().state)
            assertFails {
                ledger
                    .beginTransfer(
                        UUID.randomUUID(),
                        remaining.id,
                        player,
                        remaining.version,
                        taken,
                    )
                    .join()
            }
        }
    }

    @Test
    fun `interrupted native transfer is held until an audited exact-state operator resolution`() {
        val path = directory.resolve("ledger.db")
        val transfer = UUID.randomUUID()
        val saved = decision()
        Database.open(path).use { db ->
            val ledger = RewardLedger(db)
            reserve(ledger, saved)
            ledger.commit(saved).join()
            ledger
                .beginTransfer(
                    transfer,
                    saved.allocations.single().id,
                    player,
                    1,
                    saved.allocations.single().contents,
                )
                .join()
        }
        Database.open(path).use { db ->
            val ledger = RewardLedger(db)
            assertEquals(1, ledger.recoverTransfers().join())
            assertEquals(0, ledger.recoverTransfers().join())
            val held = ledger.allocations(player).join().single()
            assertEquals(AllocationState.REVIEW, held.state)
            assertFails { ledger.finishTransfer(transfer, true).join() }
            assertFails {
                ledger
                    .resolveReview(
                        transfer,
                        held.version - 1,
                        ReviewResolution.DELIVERED,
                        "console",
                        "Verified receipt",
                    )
                    .join()
            }
            ledger
                .resolveReview(
                    transfer,
                    held.version,
                    ReviewResolution.PENDING,
                    "console",
                    "Verified native save did not contain the transfer",
                )
                .join()
            assertEquals(AllocationState.PENDING, ledger.allocations(player).join().single().state)
            assertEquals(
                saved.allocations.single().contents,
                ledger.allocations(player).join().single().contents,
            )
            assertEquals("reward_review", AuthorityStore(db).audit().join().single().operation)
            assertFails {
                ledger
                    .resolveReview(
                        transfer,
                        held.version,
                        ReviewResolution.DELIVERED,
                        "console",
                        "Stale request",
                    )
                    .join()
            }
        }
    }

    @Test
    fun `transfer cannot change player item properties quantity or split XP into unbounded receipts`() {
        Database.open(directory.resolve("ledger.db")).use { db ->
            val ledger = RewardLedger(db)
            val saved = decision()
            reserve(ledger, saved)
            ledger.commit(saved).join()
            val allocation = saved.allocations.single()
            val bad =
                listOf(
                    RewardContents(listOf(item.withCount(4))),
                    RewardContents(listOf(FrozenItem(item.item, 1, byteArrayOf(9)))),
                    RewardContents(emptyList(), 21),
                    RewardContents(emptyList(), 1),
                )
            bad.forEach { contents ->
                assertFails {
                    ledger
                        .beginTransfer(UUID.randomUUID(), allocation.id, player, 1, contents)
                        .join()
                }
            }
            assertFails {
                ledger
                    .beginTransfer(
                        UUID.randomUUID(),
                        allocation.id,
                        UUID.randomUUID(),
                        1,
                        allocation.contents,
                    )
                    .join()
            }
            assertEquals(AllocationState.PENDING, ledger.allocations(player).join().single().state)
        }
    }

    @Test
    fun `stored item data is immutable and bounded`() {
        val bytes = byteArrayOf(1)
        val frozen = FrozenItem("minecraft:stone", 1, bytes)
        bytes[0] = 9
        frozen.components()[0] = 7
        assertContentEquals(byteArrayOf(1), frozen.components())
        val contents = RewardContents(listOf(frozen), 2)
        assertEquals(contents, RewardContents.decode(contents.encode()))
        assertFails { RewardContents.decode(contents.encode() + 0.toByte()) }
        assertFails { RewardContents.decode(ByteArray(RewardContents.MAX_BYTES + 1)) }
    }
}
