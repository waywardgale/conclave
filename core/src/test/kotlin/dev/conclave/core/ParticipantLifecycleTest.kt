package dev.conclave.core

import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test

class ParticipantLifecycleTest {
    private fun id() = UUID.randomUUID()

    @Test
    fun `world entry does not regain participation until captured resources are applied`() {
        val first = id()
        val lifecycle = ParticipantLifecycle(id(), first, RealtimeDuration(60))
        assertTrue(lifecycle.disconnected(first, 100))
        assertFalse(lifecycle.disconnected(first, 110))
        assertEquals(
            listOf(ParticipantTransition.DISCONNECTED, ParticipantTransition.PARTICIPATION_CHANGED),
            lifecycle.drainEvents().map { it.type },
        )
        val second = id()
        assertTrue(lifecycle.placed(second))
        assertFalse(lifecycle.admit(second, 130, false))
        assertTrue(lifecycle.snapshot.online)
        assertEquals(Participation.RECONNECTING, lifecycle.snapshot.participation)
        assertEquals(
            listOf(ParticipantTransition.RECONNECTED),
            lifecycle.drainEvents().map { it.type },
        )
        assertTrue(lifecycle.admit(second, 160, true))
        assertFalse(lifecycle.expire(160))
        assertEquals(
            listOf(ParticipantTransition.PARTICIPATION_CHANGED),
            lifecycle.drainEvents().map { it.type },
        )
    }

    @Test
    fun `repeated unqualified connections cannot extend the original deadline`() {
        val first = id()
        val lifecycle = ParticipantLifecycle(id(), first, RealtimeDuration(60))
        lifecycle.disconnected(first, 100)
        val second = id()
        lifecycle.placed(second)
        lifecycle.disconnected(second, 159)
        val third = id()
        lifecycle.placed(third)
        assertFalse(lifecycle.admit(third, 161, true))
        assertTrue(lifecycle.expire(161))
        assertFalse(
            lifecycle.admit(third, 160, true),
            "A committed expiry cannot be reversed by a stale timestamp",
        )
        assertEquals(Participation.OBSERVER, lifecycle.snapshot.participation)
        assertFalse(lifecycle.survivor())
        lifecycle.disconnected(third, 200)
        lifecycle.placed(id())
        assertEquals(Participation.OBSERVER, lifecycle.snapshot.participation)
        assertEquals(
            1,
            lifecycle.drainEvents().count {
                it.type == ParticipantTransition.RECONNECT_GRACE_EXPIRED
            },
        )
    }

    @Test
    fun `life state survives reconnect and revival does not grant admission`() {
        val first = id()
        val lifecycle = ParticipantLifecycle(id(), first, RealtimeDuration(60))
        lifecycle.life(LifeState.DEAD)
        lifecycle.disconnected(first, 100)
        val second = id()
        lifecycle.placed(second)
        assertEquals(LifeState.DEAD, lifecycle.snapshot.life)
        lifecycle.life(LifeState.ALIVE, RevivalMethod.SELF)
        assertEquals(Participation.RECONNECTING, lifecycle.snapshot.participation)
        assertTrue(lifecycle.admit(second, 140, true))
        lifecycle.disconnected(second, 200)
        assertFalse(
            lifecycle.expire(259),
            "A genuine readmission permits a new disconnection window",
        )
        assertTrue(lifecycle.expire(260))
        lifecycle.life(LifeState.DEAD)
        lifecycle.life(LifeState.PASSED_OUT)
        lifecycle.life(LifeState.ALIVE, RevivalMethod.SELF)
        assertEquals(Participation.OBSERVER, lifecycle.snapshot.participation)
        assertFalse(lifecycle.survivor())
    }

    @Test
    fun `stale connections and ended attempts cannot change the retained identity`() {
        val first = id()
        val lifecycle = ParticipantLifecycle(id(), first)
        assertFalse(lifecycle.disconnected(id(), 1))
        lifecycle.disconnected(first, 2)
        val second = id()
        lifecycle.placed(second)
        assertFalse(lifecycle.admit(first, 3, true))
        lifecycle.end()
        assertFalse(lifecycle.admit(second, 3, true))
        assertFalse(lifecycle.life(LifeState.DEAD))
        assertFalse(lifecycle.survivor())
        assertTrue(lifecycle.drainEvents().isEmpty())
    }

    @Test
    fun `monotonic wrapping and real durations preserve sub-tick precision`() {
        val connection = id()
        val lifecycle = ParticipantLifecycle(id(), connection, RealtimeDuration(20))
        lifecycle.disconnected(connection, Long.MAX_VALUE - 10)
        val returned = id()
        lifecycle.placed(returned)
        assertTrue(lifecycle.admit(returned, Long.MIN_VALUE + 9, true))
        assertEquals(1_000_000, RealtimeDuration.parse("1ms").nanos)
        assertEquals(1, RealtimeDuration.parse("0.000000001s").nanos)
        assertFails { RealtimeDuration.parse("999999999999999h") }
    }
}
