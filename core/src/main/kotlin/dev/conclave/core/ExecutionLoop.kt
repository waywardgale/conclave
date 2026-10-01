package dev.conclave.core

import java.util.ArrayDeque
import java.util.PriorityQueue

data class ExecutionLimits(
    val workPerTick: Int = 4096,
    val queuedWork: Int = 8192,
    val timers: Int = 4096,
    val scopes: Int = 1024,
    val cleanupHooks: Int = 4096,
) {
    init {
        require(listOf(workPerTick, queuedWork, timers, scopes, cleanupHooks).all { it > 0 })
    }
}

enum class RuntimeFaultKind {
    WORK_LIMIT,
    QUEUE_LIMIT,
    TIMER_LIMIT,
    SCOPE_LIMIT,
    CLEANUP_LIMIT,
    REQUIRED_WORK,
    CLEANUP,
}

data class RuntimeFault(
    val kind: RuntimeFaultKind,
    val activation: Long?,
    val cause: Throwable? = null,
)

/** Per-attempt, server-thread work queue. It owns callback lifetimes, not native persistence. */
class ExecutionLoop(private val limits: ExecutionLimits = ExecutionLimits()) {
    class Scope
    internal constructor(
        internal val loop: ExecutionLoop,
        val activation: Long,
        val name: String,
        internal val parent: Scope?,
    ) {
        var isOpen: Boolean = true
            internal set

        internal val children = mutableListOf<Scope>()
        internal val cleanup = mutableListOf<() -> Unit>()
    }

    private data class Work(val scope: Scope, val run: () -> Unit)

    private data class Scheduled(val due: Long, val order: Long, val work: Work)

    private val thread = Thread.currentThread()
    private val ready = ArrayDeque<Work>()
    private val scheduled = PriorityQueue(compareBy<Scheduled> { it.due }.thenBy { it.order })
    private val roots = mutableListOf<Scope>()
    private val recordedFaults = mutableListOf<RuntimeFault>()
    private var identity = 0L
    private var sequence = 0L
    private var scopeCount = 0
    private var cleanupCount = 0
    private var advancing = false
    var tick: Long = 0
        private set

    var isClosed: Boolean = false
        private set

    fun faults(): List<RuntimeFault> {
        checkThread()
        return java.util.List.copyOf(recordedFaults)
    }

    fun open(name: String, parent: Scope? = null): Scope? {
        checkThread()
        if (parent != null) require(parent.loop === this) { "Foreign activation" }
        if (isClosed || parent?.isOpen == false) return null
        if (scopeCount >= limits.scopes) {
            fail(RuntimeFaultKind.SCOPE_LIMIT, parent)
            return null
        }
        val scope = Scope(this, Math.incrementExact(identity).also { identity = it }, name, parent)
        (parent?.children ?: roots).add(scope)
        scopeCount++
        return scope
    }

    fun submit(scope: Scope, work: () -> Unit): Boolean {
        checkScope(scope)
        if (isClosed || !scope.isOpen) return false
        if (ready.size >= limits.queuedWork) {
            fail(RuntimeFaultKind.QUEUE_LIMIT, scope)
            return false
        }
        ready.addLast(Work(scope, work))
        return true
    }

    /**
     * Zero delay still begins on a later tick; use submit for ordinary same-tick queued reactions.
     */
    fun schedule(scope: Scope, delay: SimulationDuration, work: () -> Unit): Boolean {
        checkScope(scope)
        if (isClosed || !scope.isOpen) return false
        if (scheduled.size >= limits.timers) {
            fail(RuntimeFaultKind.TIMER_LIMIT, scope)
            return false
        }
        val due =
            try {
                Math.addExact(tick, maxOf(1, delay.ticks))
            } catch (failure: ArithmeticException) {
                fail(RuntimeFaultKind.REQUIRED_WORK, scope, failure)
                return false
            }
        scheduled.add(
            Scheduled(due, Math.incrementExact(sequence).also { sequence = it }, Work(scope, work))
        )
        return true
    }

    /**
     * VM-owned teardown such as listener removal. Durable native resources require their own
     * recovery adapter.
     */
    fun onClose(scope: Scope, cleanup: () -> Unit): Boolean {
        checkScope(scope)
        if (isClosed || !scope.isOpen) return false
        if (cleanupCount >= limits.cleanupHooks) {
            fail(RuntimeFaultKind.CLEANUP_LIMIT, scope)
            return false
        }
        scope.cleanup += cleanup
        cleanupCount++
        return true
    }

    /**
     * Ordinary queued observations settle before due timers. The host advances this only on
     * simulation steps.
     */
    fun advance() {
        checkThread()
        check(!advancing) { "Simulation cannot be advanced recursively" }
        if (isClosed) return
        advancing = true
        try {
            tick = Math.incrementExact(tick)
            var remaining = limits.workPerTick
            fun drain() {
                while (ready.isNotEmpty() && !isClosed) {
                    if (remaining-- <= 0) {
                        fail(RuntimeFaultKind.WORK_LIMIT, ready.first.scope)
                        break
                    }
                    val item = ready.removeFirst()
                    if (item.scope.isOpen) run(item)
                }
            }
            drain()
            while (!isClosed && scheduled.isNotEmpty() && scheduled.peek().due <= tick) {
                if (remaining-- <= 0) {
                    fail(RuntimeFaultKind.WORK_LIMIT, scheduled.peek().work.scope)
                    break
                }
                val next = scheduled.remove().work
                if (next.scope.isOpen) run(next)
                drain()
            }
        } finally {
            advancing = false
        }
    }

    fun close(scope: Scope) {
        checkScope(scope)
        if (!scope.isOpen) return
        // ASVS 2.3.1: revoke first, before cleanup can enqueue work or observe replacement
        // activations.
        scope.isOpen = false
        scope.children.toList().forEach(::close)
        ready.removeIf { it.scope === scope }
        scheduled.removeIf { it.work.scope === scope }
        val cleanup = scope.cleanup.toList().asReversed()
        scope.cleanup.clear()
        cleanupCount -= cleanup.size
        (scope.parent?.children ?: roots).remove(scope)
        scopeCount--
        var failed = false
        cleanup.forEach { action ->
            try {
                action()
            } catch (failure: Exception) {
                failed = true
                recordedFaults += RuntimeFault(RuntimeFaultKind.CLEANUP, scope.activation, failure)
            }
        }
        if (failed) close()
    }

    fun close() {
        checkThread()
        if (isClosed) return
        isClosed = true
        roots.toList().forEach(::close)
        ready.clear()
        scheduled.clear()
    }

    private fun run(work: Work) {
        try {
            work.run()
        } catch (failure: Exception) {
            fail(RuntimeFaultKind.REQUIRED_WORK, work.scope, failure)
        }
    }

    private fun fail(kind: RuntimeFaultKind, scope: Scope?, cause: Throwable? = null) {
        recordedFaults += RuntimeFault(kind, scope?.activation, cause)
        close()
    }

    private fun checkScope(scope: Scope) {
        checkThread()
        require(scope.loop === this) { "Foreign activation" }
    }

    private fun checkThread() {
        // ASVS 15.4.1: gameplay mutation is confined to its owning simulation thread.
        check(Thread.currentThread() === thread) { "ExecutionLoop belongs to another thread" }
    }
}
