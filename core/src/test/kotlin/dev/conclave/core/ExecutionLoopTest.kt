package dev.conclave.core

import java.util.concurrent.Executors
import kotlin.test.*
import org.junit.jupiter.api.Test

class ExecutionLoopTest {
    @Test
    fun `reactions queue behind ordinary work before due timers`() {
        val loop = ExecutionLoop()
        val scope = assertNotNull(loop.open("attempt"))
        val seen = mutableListOf<String>()
        loop.schedule(scope, SimulationDuration(1)) {
            seen += "timer"
            loop.submit(scope) { seen += "timer reaction" }
        }
        loop.schedule(scope, SimulationDuration(1)) { seen += "second timer" }
        loop.submit(scope) {
            seen += "operation begins"
            loop.submit(scope) { seen += "reaction" }
            seen += "operation ends"
        }
        loop.submit(scope) { seen += "observation" }
        loop.advance()
        assertEquals(
            listOf(
                "operation begins",
                "operation ends",
                "observation",
                "reaction",
                "timer",
                "timer reaction",
                "second timer",
            ),
            seen,
        )
    }

    @Test
    fun `closing a phase cancels children without retargeting its replacement`() {
        val loop = ExecutionLoop()
        val attempt = assertNotNull(loop.open("attempt"))
        val oldPhase = assertNotNull(loop.open("ritual", attempt))
        val child = assertNotNull(loop.open("capture", oldPhase))
        val seen = mutableListOf<String>()
        loop.schedule(attempt, SimulationDuration(1)) { seen += "encounter survives" }
        loop.schedule(child, SimulationDuration(1)) { fail("stale timer ran") }
        loop.submit(child) { fail("stale event ran") }
        loop.onClose(child) { seen += "child cleaned" }
        loop.onClose(oldPhase) { seen += "phase cleaned" }
        loop.close(oldPhase)
        val replacement = assertNotNull(loop.open("ritual", attempt))
        assertNotEquals(oldPhase.activation, replacement.activation)
        assertFalse(loop.submit(oldPhase) { fail("stale callback admitted") })
        assertNull(loop.open("new_child", oldPhase))
        loop.submit(replacement) { seen += "new phase" }
        loop.advance()
        assertEquals(
            listOf("child cleaned", "phase cleaned", "new phase", "encounter survives"),
            seen,
        )
    }

    @Test
    fun `zero delay scheduled within a callback waits for the following tick`() {
        val loop = ExecutionLoop()
        val scope = assertNotNull(loop.open("attempt"))
        var fired = false
        loop.submit(scope) { loop.schedule(scope, SimulationDuration(0)) { fired = true } }
        loop.advance()
        assertFalse(fired)
        loop.advance()
        assertTrue(fired)
    }

    @Test
    fun `runaway reactions terminate only their attempt and still clean up`() {
        val bad = ExecutionLoop(ExecutionLimits(workPerTick = 3))
        val scope = assertNotNull(bad.open("attempt"))
        var cleaned = false
        bad.onClose(scope) { cleaned = true }
        fun repeat() {
            bad.submit(scope, ::repeat)
        }
        repeat()
        val good = ExecutionLoop()
        val other = assertNotNull(good.open("unrelated"))
        var healthy = false
        good.submit(other) { healthy = true }
        bad.advance()
        good.advance()
        assertTrue(bad.isClosed)
        assertEquals(RuntimeFaultKind.WORK_LIMIT, bad.faults().single().kind)
        assertTrue(cleaned)
        assertTrue(healthy)
        assertFalse(good.isClosed)
    }

    @Test
    fun `required callback errors cancel future work and exhaust cleanup even if a hook fails`() {
        val loop = ExecutionLoop()
        val scope = assertNotNull(loop.open("attempt"))
        var cleaned = 0
        loop.onClose(scope) { cleaned++ }
        loop.onClose(scope) { error("cleanup failure") }
        loop.onClose(scope) { cleaned++ }
        loop.submit(scope) { error("required work failed") }
        loop.submit(scope) { fail("work survived failure") }
        loop.advance()
        loop.close()
        assertEquals(2, cleaned)
        assertEquals(
            listOf(RuntimeFaultKind.REQUIRED_WORK, RuntimeFaultKind.CLEANUP),
            loop.faults().map { it.kind },
        )
    }

    @Test
    fun `cleanup failure during phase close interrupts remaining gameplay`() {
        val loop = ExecutionLoop()
        val attempt = assertNotNull(loop.open("attempt"))
        val phase = assertNotNull(loop.open("phase", attempt))
        loop.onClose(phase) { error("required cleanup failed") }
        loop.close(phase)
        assertTrue(loop.isClosed)
        assertFalse(attempt.isOpen)
    }

    @Test
    fun `queue timer scope and cleanup ceilings each fail before admitting excess work`() {
        val cases: List<Pair<RuntimeFaultKind, (ExecutionLoop, ExecutionLoop.Scope) -> Unit>> =
            listOf(
                RuntimeFaultKind.QUEUE_LIMIT to
                    { loop, scope ->
                        repeat(2) { loop.submit(scope) {} }
                    },
                RuntimeFaultKind.TIMER_LIMIT to
                    { loop, scope ->
                        repeat(2) { loop.schedule(scope, SimulationDuration(10)) {} }
                    },
                RuntimeFaultKind.SCOPE_LIMIT to { loop, scope -> loop.open("child", scope) },
                RuntimeFaultKind.CLEANUP_LIMIT to
                    { loop, scope ->
                        repeat(2) { loop.onClose(scope) {} }
                    },
            )
        for ((kind, action) in cases) {
            val loop =
                ExecutionLoop(
                    ExecutionLimits(queuedWork = 1, timers = 1, scopes = 1, cleanupHooks = 1)
                )
            action(loop, assertNotNull(loop.open("attempt")))
            assertTrue(loop.isClosed, kind.name)
            assertEquals(kind, loop.faults().first().kind)
        }
    }

    @Test
    fun `foreign activations and worker thread mutation are rejected`() {
        val loop = ExecutionLoop()
        val foreign = assertNotNull(ExecutionLoop().open("foreign"))
        assertFailsWith<IllegalArgumentException> { loop.submit(foreign) {} }
        Executors.newSingleThreadExecutor().use { pool ->
            pool.submit { assertFailsWith<IllegalStateException> { loop.advance() } }.get()
        }
    }
}
