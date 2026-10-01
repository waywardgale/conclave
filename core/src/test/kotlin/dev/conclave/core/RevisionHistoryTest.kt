package dev.conclave.core

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.*
import org.junit.jupiter.api.Test

class RevisionHistoryTest {
    @Test
    fun `pinning current is atomic with activation and pruning`() {
        val history = RevisionHistory(retained = 1)
        val first = catalog("first")
        val second = catalog("second")
        history.publish(first, null)
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val publisher = pool.submit {
                start.await()
                repeat(2000) {
                    val next = if (it % 2 == 0) second else first
                    assertIs<PublicationResult.Published>(history.publish(next, history.current()))
                }
            }
            val consumer = pool.submit {
                start.await()
                repeat(2000) {
                    assertNotNull(history.pin()).use { pin ->
                        assertTrue(pin.catalog.revision in setOf(first.revision, second.revision))
                    }
                }
            }
            start.countDown()
            publisher.get()
            consumer.get()
        }
    }

    @Test
    fun `active attempts stay pinned while future attempts see publication`() {
        val history = RevisionHistory(retained = 1)
        val first = catalog(duration = "1s")
        val second = catalog(duration = "2s")
        assertIs<PublicationResult.Published>(history.publish(first, expected = null))
        val attempt = assertNotNull(history.pin())
        assertIs<PublicationResult.Published>(history.publish(second, expected = first.revision))
        history.pin().use { assertEquals(second.revision, it?.catalog?.revision) }
        assertEquals(first.revision, attempt.catalog.revision)
        assertTrue(first.revision in history.revisions())
        attempt.close()
        attempt.close()
        assertNull(history.pin(first.revision))
        assertEquals(listOf(second.revision), history.revisions())
    }

    @Test
    fun `simultaneous publishers cannot overwrite a changed baseline`() {
        val history = RevisionHistory()
        val baseline = catalog("baseline")
        history.publish(baseline, null)
        val candidates = listOf(catalog("first"), catalog("second"))
        val start = CountDownLatch(1)
        val results =
            Executors.newFixedThreadPool(2).use { pool ->
                val futures = candidates.map { candidate ->
                    pool.submit<PublicationResult> {
                        start.await()
                        history.publish(candidate, baseline.revision)
                    }
                }
                start.countDown()
                futures.map { it.get() }
            }
        assertEquals(1, results.count { it is PublicationResult.Published })
        assertEquals(1, results.count { it is PublicationResult.Conflict })
        assertEquals(
            results.filterIsInstance<PublicationResult.Published>().single().revision,
            history.current(),
        )
    }

    @Test
    fun `rollback retains distinct activations and respects baseline`() {
        val history = RevisionHistory(retained = 2)
        val first = catalog("first")
        val second = catalog("second")
        history.publish(first, null)
        assertEquals(
            PublicationResult.Published(first.revision, false),
            history.publish(first, first.revision),
        )
        history.publish(second, first.revision)
        assertIs<PublicationResult.Conflict>(history.rollback(first, first.revision))
        assertIs<PublicationResult.UnknownRevision>(
            history.rollback(catalog("unknown"), second.revision)
        )
        assertIs<PublicationResult.Published>(history.rollback(catalog("first"), second.revision))
        assertEquals(listOf(first.revision, second.revision), history.revisions())
    }
}
