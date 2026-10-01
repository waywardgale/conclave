package dev.conclave.core

import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test

class GestureSessionTest {
    private fun target() = GestureTarget(UUID.randomUUID(), UUID.randomUUID())

    @Test
    fun `sealed use snapshot prevents duplicates and holds cannot subscribe to replacement phases`() {
        val connection = UUID.randomUUID()
        val input = GestureSession(connection)
        val target = target()
        val mechanic = UUID.randomUUID()
        val first = assertNotNull(input.press(connection, 1, target, 0))
        assertTrue(input.admit(first, listOf(GestureRecipient(mechanic, true))))
        assertNull(input.press(connection, 1, target, 1))
        assertFalse(input.admit(first, listOf(GestureRecipient(UUID.randomUUID(), true))))
        input.interrupt(mechanic)
        assertFalse(input.observing(first, mechanic, 2))
        assertTrue(input.consumes(target.physical, 2))
        assertFalse(input.consumes(UUID.randomUUID(), 2))
        assertNull(input.press(connection, 2, target, 3), "A new counter cannot skip release")
        assertTrue(input.release(first))
        assertNull(
            input.press(connection, 2, target, 4),
            "A previously rejected press is not retried later",
        )
        assertNotNull(input.press(connection, 3, target, 4))
        assertFalse(input.release(first), "An old release cannot end the new gesture")
    }

    @Test
    fun `target replacement interrupts progress and cannot restore it by looking back`() {
        val connection = UUID.randomUUID()
        val input = GestureSession(connection)
        val target = target()
        val mechanic = UUID.randomUUID()
        val first = assertNotNull(input.press(connection, 1, target, 0))
        input.admit(first, listOf(GestureRecipient(mechanic, true)))
        assertTrue(input.continuation(first, target.copy(generation = UUID.randomUUID()), 1))
        assertFalse(input.observing(first, mechanic, 1))
        assertTrue(input.continuation(first, target, 2))
        assertFalse(input.observing(first, mechanic, 2))
        assertTrue(
            input.consumes(target.physical, 2),
            "Native repeats still belong to the consumed physical Use",
        )
    }

    @Test
    fun `freshness and connection closure prevent stale progress without client time credit`() {
        val connection = UUID.randomUUID()
        val input = GestureSession(connection, RealtimeDuration(10))
        val target = target()
        assertNull(input.press(UUID.randomUUID(), 1, target, 0))
        val first = assertNotNull(input.press(connection, 1, target, 0))
        val mechanic = UUID.randomUUID()
        input.admit(first, listOf(GestureRecipient(mechanic, false)))
        repeat(100) { assertTrue(input.continuation(first, target, 5)) }
        assertTrue(input.observing(first, mechanic, 14))
        assertFalse(input.continuation(first, target, 15))
        assertFalse(input.observing(first, mechanic, 15))
        assertNull(input.press(connection, 1, target, 16))
        val next = assertNotNull(input.press(connection, 2, target, 16))
        input.close()
        assertFalse(input.continuation(next, target, 17))
        assertNull(input.press(connection, 3, target, 18))
    }

    @Test
    fun `monotonic freshness tolerates native clock wrap`() {
        val connection = UUID.randomUUID()
        val input = GestureSession(connection, RealtimeDuration(10))
        val first = assertNotNull(input.press(connection, 1, target(), Long.MAX_VALUE - 5))
        assertTrue(input.continuation(first, first.target, Long.MIN_VALUE + 1))
        assertFalse(input.expire(Long.MIN_VALUE + 10))
        assertTrue(input.expire(Long.MIN_VALUE + 11))
    }
}
