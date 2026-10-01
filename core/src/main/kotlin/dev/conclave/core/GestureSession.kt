package dev.conclave.core

import java.util.UUID

/** Physical identity survives phase changes; generation changes when the actual target changes. */
data class GestureTarget(val physical: UUID, val generation: UUID)

data class GestureRecipient(val activation: UUID, val consume: Boolean)

data class GestureLease(val connection: UUID, val sequence: Long, val target: GestureTarget)

/**
 * One connection's deliberate Use intent. Time comes from the host's monotonic clock. A sealed
 * recipient snapshot cannot acquire a new mechanic when a phase changes under a held key.
 */
class GestureSession(
    val connection: UUID,
    private val freshness: RealtimeDuration = RealtimeDuration(1_000_000_000),
) {
    private val thread = Thread.currentThread()

    private class Held(val lease: GestureLease, var refreshed: Long) {
        var sealed = false
        var consume = false
        var recipients: Set<UUID> = emptySet()
    }

    private var highest = 0L
    private var held: Held? = null
    private var closed = false

    init {
        require(freshness.nanos > 0)
    }

    fun press(connection: UUID, sequence: Long, target: GestureTarget, now: Long): GestureLease? {
        checkThread()
        if (closed || connection != this.connection || sequence <= highest) return null
        highest = sequence
        expire(now)
        // A distinct counter is not a release. One press cannot replace an ongoing hold.
        if (held != null) return null
        return GestureLease(connection, sequence, target).also { held = Held(it, now) }
    }

    /** Called exactly once after all matches are assessed against the pre-dispatch observation. */
    fun admit(lease: GestureLease, recipients: List<GestureRecipient>): Boolean {
        checkThread()
        require(
            recipients.size <= 128 &&
                recipients.map { it.activation }.distinct().size == recipients.size
        )
        val active = held?.takeIf { it.lease == lease && !it.sealed } ?: return false
        active.sealed = true
        active.recipients = java.util.Set.copyOf(recipients.map { it.activation })
        active.consume = recipients.any { it.consume }
        return true
    }

    fun continuation(lease: GestureLease, target: GestureTarget, now: Long): Boolean {
        checkThread()
        expire(now)
        val active = held?.takeIf { it.lease == lease } ?: return false
        active.refreshed = now
        if (target != lease.target) active.recipients = emptySet()
        return true
    }

    fun release(lease: GestureLease): Boolean {
        checkThread()
        if (held?.lease != lease) return false
        held = null
        return true
    }

    /** Completion/interruption removes gameplay interest while retaining native-use consumption. */
    fun interrupt(activation: UUID? = null) {
        checkThread()
        held?.let {
            it.recipients = if (activation == null) emptySet() else it.recipients - activation
        }
    }

    fun observing(lease: GestureLease, activation: UUID, now: Long): Boolean {
        checkThread()
        expire(now)
        return held?.let { it.lease == lease && activation in it.recipients } == true
    }

    /** Only the original physical target is consumed, including both hands and native repeats. */
    fun consumes(physicalTarget: UUID, now: Long): Boolean {
        checkThread()
        expire(now)
        return held?.let { it.consume && it.lease.target.physical == physicalTarget } == true
    }

    fun expire(now: Long): Boolean {
        checkThread()
        val active = held ?: return false
        val elapsed = now - active.refreshed
        check(elapsed >= 0) { "Input freshness clock moved backwards" }
        if (elapsed < freshness.nanos) return false
        held = null
        return true
    }

    fun close() {
        checkThread()
        closed = true
        held = null
    }

    private fun checkThread() = check(Thread.currentThread() === thread)
}
