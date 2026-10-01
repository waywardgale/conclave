package dev.conclave.core

import java.util.UUID

enum class GraveStatus {
    OPPORTUNITY,
    PASSED_OUT,
    CLOSED,
}

enum class RevivalMethod {
    SELF,
    ASSISTED,
    ADMINISTRATIVE,
}

enum class GraveClosure {
    REVIVED,
    NORMAL_RESPAWN,
    ATTEMPT_ENDED,
}

sealed interface GraveEvent {
    data class Died(val player: UUID, val grave: UUID) : GraveEvent

    data class PassedOut(val player: UUID, val grave: UUID) : GraveEvent

    data class Revived(
        val player: UUID,
        val grave: UUID,
        val method: RevivalMethod,
        val helper: UUID?,
    ) : GraveEvent

    data class Closed(val player: UUID, val grave: UUID, val reason: GraveClosure) : GraveEvent
}

data class AssistanceIdentity(val helper: UUID, val connection: UUID, val gesture: UUID)

data class GraveView(
    val status: GraveStatus,
    val remainingCombat: SimulationDuration?,
    val delayRemaining: SimulationDuration,
    val helpProgress: SimulationDuration,
    val helper: UUID?,
    val combat: Boolean,
)

/**
 * One real death's immutable policy and terminal decision. Native adapters journal ownership and
 * validate online state, assistance, input freshness, and safe placement before committing revival.
 * Open each simulation step before ready result reactions, then settle expiry after those
 * reactions.
 */
class GraveLifecycle(
    val grave: UUID,
    val player: UUID,
    val attempt: UUID?,
    val policy: RevivalPolicy,
    val prohibitSelfRevival: Boolean,
    val diedAt: Long,
) {
    private val owner = Thread.currentThread()
    private val events = ArrayDeque<GraveEvent>()
    private var tick = diedAt
    private var stepOpen = false
    private var combat = false
    private var windowStarted = false
    private var remaining = policy.combatWindow.ticks

    private data class Hold(val identity: AssistanceIdentity, val began: Long)

    private var assistance: Hold? = null
    var status = GraveStatus.OPPORTUNITY
        private set

    var closure: GraveClosure? = null
        private set

    init {
        require(diedAt >= 0 && (!prohibitSelfRevival || policy.encountersMayDisableSelfRevival))
        events += GraveEvent.Died(player, grave)
        if (attempt != null && !policy.anyMethod(prohibitSelfRevival)) passOut()
    }

    fun beginStep(now: Long, inCombat: Boolean) {
        checkThread()
        check(!stepOpen && now >= tick && now - tick <= 1) {
            "Graves advance one simulation step at a time"
        }
        if (status == GraveStatus.CLOSED) return
        val elapsed = now - tick
        tick = now
        stepOpen = true
        combat = attempt != null && inCombat
        if (status == GraveStatus.OPPORTUNITY && combat) {
            windowStarted = true
            remaining = maxOf(0, remaining - elapsed)
        }
    }

    fun settleExpiry() {
        checkThread()
        if (status == GraveStatus.CLOSED) return
        check(stepOpen)
        stepOpen = false
        if (status == GraveStatus.OPPORTUNITY && windowStarted && remaining == 0L) passOut()
    }

    fun beginAssistance(id: AssistanceIdentity, physicallyEligible: Boolean): Boolean {
        checkThread()
        if (
            !ordinaryReady() ||
                !policy.assistanceAllowed ||
                !physicallyEligible ||
                id.helper == player
        )
            return false
        val hold = assistance
        if (hold != null) return hold.identity == id
        assistance = Hold(id, tick)
        return true
    }

    /** Call after each authoritative target/input observation, including release and damage. */
    fun observeAssistance(id: AssistanceIdentity?, stillEligible: Boolean) {
        checkThread()
        if (!stillEligible || assistance?.identity != id) assistance = null
    }

    fun interruptAssistance(helper: UUID? = null) {
        checkThread()
        if (helper == null || assistance?.identity?.helper == helper) assistance = null
    }

    /**
     * Requires current native eligibility and prepared safe placement. One accepted result closes
     * the grave; duplicate, stale, disconnected, or late requests cannot create another revival.
     */
    fun revive(
        targetGrave: UUID,
        method: RevivalMethod,
        targetOnline: Boolean,
        placementReady: Boolean,
        helper: AssistanceIdentity? = null,
        helperEligible: Boolean = false,
    ): Boolean {
        if (!canRevive(targetGrave, method, targetOnline, placementReady, helper, helperEligible))
            return false
        status = GraveStatus.CLOSED
        closure = GraveClosure.REVIVED
        assistance = null
        stepOpen = false
        events += GraveEvent.Revived(player, grave, method, helper?.helper)
        return true
    }

    /** Read-only authorization immediately before the synchronous native respawn operation. */
    fun canRevive(
        targetGrave: UUID,
        method: RevivalMethod,
        targetOnline: Boolean,
        placementReady: Boolean,
        helper: AssistanceIdentity? = null,
        helperEligible: Boolean = false,
    ): Boolean {
        checkThread()
        if (
            targetGrave != grave || status == GraveStatus.CLOSED || !targetOnline || !placementReady
        )
            return false
        when (method) {
            RevivalMethod.SELF ->
                if (!ordinaryReady() || !policy.selfAllowed(prohibitSelfRevival) || helper != null)
                    return false
            RevivalMethod.ASSISTED -> {
                val hold = assistance ?: return false
                if (
                    !ordinaryReady() ||
                        !policy.assistanceAllowed ||
                        !helperEligible ||
                        helper != hold.identity ||
                        tick - hold.began < policy.helpTime.ticks
                )
                    return false
            }
            RevivalMethod.ADMINISTRATIVE -> if (attempt == null || helper != null) return false
        }
        return true
    }

    fun normalRespawn(targetGrave: UUID): Boolean {
        checkThread()
        if (targetGrave != grave || attempt != null || status == GraveStatus.CLOSED) return false
        close(GraveClosure.NORMAL_RESPAWN)
        return true
    }

    fun endAttempt() {
        checkThread()
        if (status != GraveStatus.CLOSED) close(GraveClosure.ATTEMPT_ENDED)
    }

    /** A possible self-revival can keep a party alive; an assisted-only dead party cannot. */
    fun selfRevivalOpportunity(): Boolean {
        checkThread()
        return status == GraveStatus.OPPORTUNITY && policy.selfAllowed(prohibitSelfRevival)
    }

    fun view(): GraveView {
        checkThread()
        return GraveView(
            status,
            if (windowStarted) SimulationDuration(remaining) else null,
            SimulationDuration(maxOf(0, policy.delay.ticks - (tick - diedAt))),
            SimulationDuration(
                assistance?.let { minOf(policy.helpTime.ticks, tick - it.began) } ?: 0
            ),
            assistance?.identity?.helper,
            combat,
        )
    }

    fun drainEvents(): List<GraveEvent> {
        checkThread()
        return events.toList().also { events.clear() }
    }

    private fun ordinaryReady() =
        status == GraveStatus.OPPORTUNITY && tick - diedAt >= policy.delay.ticks

    private fun passOut() {
        status = GraveStatus.PASSED_OUT
        assistance = null
        events += GraveEvent.PassedOut(player, grave)
    }

    private fun close(reason: GraveClosure) {
        status = GraveStatus.CLOSED
        closure = reason
        assistance = null
        stepOpen = false
        events += GraveEvent.Closed(player, grave, reason)
    }

    private fun checkThread() = check(Thread.currentThread() === owner)
}
