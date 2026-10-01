package dev.conclave.storage

import dev.conclave.core.CatalogCompiler
import dev.conclave.core.CompiledCatalog
import dev.conclave.core.DefinitionId
import dev.conclave.core.SourceDocument
import dev.conclave.core.Validation
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AttemptStoreTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `offline player recovery retains its lock and revision without holding world cleanup`() {
        val path = directory.resolve("store.db")
        val attempt = snapshot()
        val player = attempt.roster.single()
        val intent =
            ResourceIntent(
                UUID.randomUUID(),
                attempt.id,
                OwnedResourceKind.PLAYER_STATE,
                player.toString(),
                byteArrayOf(9),
            )
        Database.open(path).use { db ->
            val store = AttemptStore(db)
            store.prepare(attempt, catalog()).join()
            assertFails {
                store
                    .record(
                        ResourceIntent(
                            UUID.randomUUID(),
                            attempt.id,
                            OwnedResourceKind.PLAYER_STATE,
                            UUID.randomUUID().toString(),
                            byteArrayOf(),
                        )
                    )
                    .join()
            }
            store.record(intent).join()
            store.beginCleanup(attempt.id).join()
            assertTrue(store.releaseArena(attempt.id).join())
            assertEquals(
                StoredAttemptState.RECOVERY_PENDING,
                store.playerAttempt(player).join()?.state,
            )
            assertFails { store.recoveredPlayer(attempt.id, player).join() }
            assertIs<AttemptPreparation.Prepared>(store.prepare(snapshot(), catalog()).join())
        }
        Database.open(path).use { db ->
            val store = AttemptStore(db)
            val saved = checkNotNull(store.playerAttempt(player).join())
            assertEquals(attempt.id, saved.snapshot.id)
            assertContentEquals(
                byteArrayOf(9),
                store.resources(attempt.id).join().single().intent.recovery,
            )
            assertNotNull(ContentStore(db).revision(saved.revision).join())
            store.resolved(intent.id).join()
            store.recoveredPlayer(attempt.id, player).join()
            assertNull(store.playerAttempt(player).join())
            assertEquals(StoredAttemptState.ENDED, store.attempt(attempt.id).join()?.state)
        }
    }

    @Test
    fun `restart finds a reservation whose cleanup acknowledgement was interrupted`() {
        val path = directory.resolve("store.db")
        val attempt = snapshot()
        Database.open(path).use { db ->
            val store = AttemptStore(db)
            store.prepare(attempt, catalog()).join()
            assertFails { store.prepare(attempt, catalog(), completionBytes = 4096).join() }
            assertTrue(store.releaseUntouched(attempt.id).join())
            assertEquals(StoredAttemptState.ENDED, store.attempt(attempt.id).join()?.state)
        }
        Database.open(path).use { db ->
            val store = AttemptStore(db)
            assertEquals(attempt.id, store.interrupted().join().single().snapshot.id)
            RewardLedger(db).releaseReservation(attempt.id).join()
            assertTrue(store.interrupted().join().isEmpty())
        }
    }

    private fun catalog(): CompiledCatalog =
        assertIs<Validation.Valid<CompiledCatalog>>(
                CatalogCompiler()
                    .compile(
                        listOf(
                            SourceDocument(
                                "test.yaml",
                                """
                                schema: 1
                                encounter:
                                  id: test
                                  start: wait
                                  phases: [{id: wait, duration: 1s, success: {complete: true}}]
                                """
                                    .trimIndent(),
                            )
                        )
                    )
            )
            .value

    private fun snapshot(player: UUID = UUID.randomUUID(), arena: String = "hall") =
        AttemptSnapshot(
            UUID.randomUUID(),
            DefinitionId("local", arena),
            DefinitionId("local", "test"),
            listOf(player),
            byteArrayOf(1, 2, 3),
        )

    @Test
    fun `completion capacity is admitted in the same transaction as arena and player ownership`() {
        Database.open(directory.resolve("store.db")).use { database ->
            val ledger = RewardLedger(database, StorageBudget(bytes = 2048))
            val store = AttemptStore(database, rewards = ledger)
            val first = snapshot()
            assertIs<AttemptPreparation.Prepared>(store.prepare(first, catalog()).join())
            assertEquals(2048, ledger.capacity().join().reservedBytes)
            val next = snapshot(arena = "other")
            assertEquals(AttemptPreparation.CapacityExceeded, store.prepare(next, catalog()).join())
            assertNull(store.attempt(next.id).join())
            assertTrue(store.releaseUntouched(first.id).join())
            ledger.releaseReservation(first.id).join()
            assertIs<AttemptPreparation.Prepared>(store.prepare(next, catalog()).join())
        }
    }

    @Test
    fun `cleanup cannot overtake an enqueued frozen completion or erase its receipt`() {
        Database.open(directory.resolve("store.db")).use { database ->
            val ledger = RewardLedger(database)
            val store = AttemptStore(database, rewards = ledger)
            val attempt = snapshot()
            val compiled = catalog()
            store.prepare(attempt, compiled).join()
            store.activate(attempt.id).join()
            val decision = CompletionDecision(attempt.id, compiled.revision, 20, true, emptyList())
            val commit = ledger.commit(decision)
            val cleanup = store.releaseUntouched(attempt.id)
            commit.join()
            assertTrue(cleanup.join())
            assertTrue(ledger.committed(decision).join())
            assertEquals(StoredAttemptState.ENDED, store.attempt(attempt.id).join()?.state)
            assertEquals(0, ledger.capacity().join().reservedBytes)
        }
    }

    @Test
    fun `attempt admission reserves the arena roster and captured revision atomically`() {
        Database.open(directory.resolve("store.db")).use { database ->
            val store = AttemptStore(database)
            val catalog = catalog()
            val first = snapshot()
            assertTrue(
                assertIs<AttemptPreparation.Prepared>(store.prepare(first, catalog).join())
                    .newlyPrepared
            )
            assertFalse(
                assertIs<AttemptPreparation.Prepared>(store.prepare(first, catalog).join())
                    .newlyPrepared
            )
            assertEquals(
                "arena",
                assertIs<AttemptPreparation.Conflict>(store.prepare(snapshot(), catalog).join())
                    .kind,
            )
            assertEquals(
                "player",
                assertIs<AttemptPreparation.Conflict>(
                        store.prepare(snapshot(first.roster.single(), "second"), catalog).join()
                    )
                    .kind,
            )
            assertNotNull(ContentStore(database).revision(catalog.revision).join())
            assertNull(
                ContentStore(database).current().join(),
                "A Test snapshot must not publish its revision",
            )
            assertTrue(store.activate(first.id).join())
            assertTrue(store.activate(first.id).join())
        }
    }

    @Test
    fun `recovery retains intents and cleanup wins late native creation acknowledgment`() {
        val path = directory.resolve("store.db")
        val attempt = snapshot()
        val original = byteArrayOf(4, 5)
        val intent =
            ResourceIntent(
                UUID.randomUUID(),
                attempt.id,
                OwnedResourceKind.NPC,
                UUID.randomUUID().toString(),
                original,
            )
        original[0] = 99
        Database.open(path).use { database ->
            val store = AttemptStore(database)
            store.prepare(attempt, catalog()).join()
            store.activate(attempt.id).join()
            assertTrue(store.record(intent).join())
            assertFalse(store.record(intent).join())
        }
        Database.open(path).use { database ->
            val store = AttemptStore(database)
            assertEquals(StoredAttemptState.RECOVERING, store.interrupted().join().single().state)
            assertFalse(store.created(intent.id).join())
            assertEquals(
                OwnedResourceState.CLEANUP,
                store.resources(attempt.id).join().single().state,
            )
            assertContentEquals(
                byteArrayOf(4, 5),
                store.resources(attempt.id).join().single().intent.recovery,
            )
            assertFalse(store.releaseArena(attempt.id).join())
            assertTrue(store.resolved(intent.id).join())
            assertTrue(store.resolved(intent.id).join())
            assertTrue(store.releaseArena(attempt.id).join())
            assertEquals(
                StoredAttemptState.RECOVERY_PENDING,
                store.attempt(attempt.id).join()?.state,
            )
            assertIs<AttemptPreparation.Prepared>(
                store.prepare(snapshot(), catalog()).join(),
                "Offline player recovery must not hold the arena",
            )
            assertEquals(
                "player",
                assertIs<AttemptPreparation.Conflict>(
                        store.prepare(snapshot(attempt.roster.single(), "other"), catalog()).join()
                    )
                    .kind,
            )
            assertTrue(store.recoveredPlayer(attempt.id, attempt.roster.single()).join())
            assertFalse(store.recoveredPlayer(attempt.id, attempt.roster.single()).join())
            assertEquals(StoredAttemptState.ENDED, store.attempt(attempt.id).join()?.state)
        }
    }

    @Test
    fun `resource budgets fail before creation and a closed attempt cannot add intents`() {
        Database.open(directory.resolve("store.db")).use { database ->
            val store =
                AttemptStore(
                    database,
                    RecoveryBudget(resourcesPerAttempt = 1, bytesPerAttempt = 2048),
                )
            val attempt = snapshot()
            store.prepare(attempt, catalog()).join()
            val first =
                ResourceIntent(
                    UUID.randomUUID(),
                    attempt.id,
                    OwnedResourceKind.RELIC,
                    "orb",
                    byteArrayOf(1),
                )
            assertTrue(store.record(first).join())
            assertFails {
                store
                    .record(
                        ResourceIntent(
                            UUID.randomUUID(),
                            attempt.id,
                            OwnedResourceKind.RELIC,
                            "second",
                            byteArrayOf(2),
                        )
                    )
                    .join()
            }
            assertEquals(1, store.resources(attempt.id).join().size)
            store.beginCleanup(attempt.id).join()
            assertFalse(store.activate(attempt.id).join())
            assertFails {
                store
                    .record(
                        ResourceIntent(
                            UUID.randomUUID(),
                            attempt.id,
                            OwnedResourceKind.GRAVE,
                            "grave",
                            byteArrayOf(3),
                        )
                    )
                    .join()
            }
        }
    }

    @Test
    fun `capacity refusal leaves no roster lock or partial preparation`() {
        Database.open(directory.resolve("store.db")).use { database ->
            val small = AttemptStore(database, RecoveryBudget(totalBytes = 1024))
            val attempt = snapshot()
            assertEquals(
                AttemptPreparation.CapacityExceeded,
                small.prepare(attempt, catalog()).join(),
            )
            assertNull(small.attempt(attempt.id).join())
            assertIs<AttemptPreparation.Prepared>(
                AttemptStore(database).prepare(attempt, catalog()).join()
            )
        }
    }
}
