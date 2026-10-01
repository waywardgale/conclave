package dev.conclave.core

data class CounterDefinition(
    val id: String,
    val name: String? = null,
    val initial: Long = 0,
    val minimum: Long = Long.MIN_VALUE,
    val maximum: Long = Long.MAX_VALUE,
) {
    init {
        require(isAuthoredName(id) && minimum <= maximum && initial in minimum..maximum)
    }
}

data class TimerDefinition(
    val id: String,
    val duration: SimulationDuration,
    val name: String? = null,
    val autoStart: Boolean = true,
) {
    init {
        require(isAuthoredName(id) && duration.ticks > 0)
    }
}

enum class CounterMutation {
    SET,
    ADD,
    RESET,
}

enum class TimerMutation {
    START,
    RESTART,
    PAUSE,
    RESUME,
    STOP,
}

data class TimerExpiry(
    val id: String,
    val generation: Long,
    val duration: SimulationDuration,
    val elapsed: SimulationDuration,
)

/**
 * Scope-local state. A host invokes due timers in stable activation order and queues their
 * reactions.
 */
class ScopeState(
    counters: List<CounterDefinition>,
    timers: List<TimerDefinition>,
    initialTick: Long = 0,
    private val order: () -> Long,
) {
    private class Clock(val definition: TimerDefinition) {
        var status = TimerStatus.IDLE
        var remaining = definition.duration.ticks
        var resumed = 0L
        var generation = 0L
        var order = 0L

        fun remaining(tick: Long): Long =
            if (status == TimerStatus.RUNNING) (remaining - (tick - resumed)).coerceAtLeast(0)
            else remaining
    }

    private val counterDefinitions = counters.associateBy { it.id }
    private val values = counters.associate { it.id to it.initial }.toMutableMap()
    private val clocks = timers.associate { it.id to Clock(it) }

    init {
        require(counters.size == values.size && timers.size == clocks.size && initialTick >= 0)
        timers.filter { it.autoStart }.forEach { mutate(it.id, TimerMutation.START, initialTick) }
    }

    fun counter(id: String) = values.getValue(id)

    fun counters(): Map<String, Long> = java.util.Map.copyOf(values)

    fun timers(tick: Long): Map<String, TimerObservation> = clocks.mapValues { (_, clock) ->
        TimerObservation(clock.status, SimulationDuration(clock.remaining(tick)))
    }

    fun mutate(id: String, mutation: CounterMutation, value: Long = 0) {
        val definition = counterDefinitions.getValue(id)
        val next =
            when (mutation) {
                CounterMutation.SET -> value
                CounterMutation.ADD -> Math.addExact(values.getValue(id), value)
                CounterMutation.RESET -> definition.initial
            }
        check(next in definition.minimum..definition.maximum) {
            "Counter '$id' exceeds its declared bounds"
        }
        values[id] = next
    }

    fun mutate(id: String, mutation: TimerMutation, tick: Long) {
        val clock = clocks.getValue(id)
        when (mutation) {
            TimerMutation.START,
            TimerMutation.RESTART -> {
                if (
                    mutation == TimerMutation.START &&
                        clock.status in setOf(TimerStatus.RUNNING, TimerStatus.PAUSED)
                )
                    return
                clock.generation = Math.incrementExact(clock.generation)
                clock.order = order()
                clock.remaining = clock.definition.duration.ticks
                clock.resumed = tick
                Math.addExact(tick, clock.remaining)
                clock.status = TimerStatus.RUNNING
            }
            TimerMutation.PAUSE ->
                if (clock.status == TimerStatus.RUNNING) {
                    clock.remaining = clock.remaining(tick)
                    clock.status = TimerStatus.PAUSED
                }
            TimerMutation.RESUME ->
                if (clock.status == TimerStatus.PAUSED) {
                    clock.resumed = tick
                    clock.status = TimerStatus.RUNNING
                }
            TimerMutation.STOP -> {
                clock.status = TimerStatus.IDLE
                clock.remaining = clock.definition.duration.ticks
            }
        }
    }

    data class Due(val id: String, val generation: Long, val due: Long, val order: Long)

    fun due(tick: Long): List<Due> =
        clocks.values
            .filter { it.status == TimerStatus.RUNNING && it.remaining(tick) == 0L }
            .map {
                Due(
                    it.definition.id,
                    it.generation,
                    Math.addExact(it.resumed, it.remaining),
                    it.order,
                )
            }
            .sortedWith(compareBy<Due> { it.due }.thenBy { it.order })

    fun expire(operation: Due, tick: Long): TimerExpiry? {
        val clock = clocks[operation.id] ?: return null
        if (
            clock.generation != operation.generation ||
                clock.status != TimerStatus.RUNNING ||
                clock.remaining(tick) != 0L
        )
            return null
        clock.status = TimerStatus.EXPIRED
        clock.remaining = 0
        return TimerExpiry(
            operation.id,
            operation.generation,
            clock.definition.duration,
            clock.definition.duration,
        )
    }
}
