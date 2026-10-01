package dev.conclave.core

import java.util.UUID

data class PhaseActivation(val attempt: UUID, val generation: Long, val phase: String)

enum class PhaseFailureReason {
    DEADLINE,
    CONDITION,
    OBJECTIVE_FAILED,
}

data class OutcomeSignal(
    val activation: PhaseActivation,
    val outcome: GameplayOutcome,
    val reason: PhaseFailureReason? = null,
)

data class CompletionOperation(
    val attempt: UUID,
    val revision: String,
    val elapsed: SimulationDuration,
)

enum class CommitStatus {
    COMMITTED,
    ABSENT,
    UNKNOWN,
}

enum class AttemptResult {
    SUCCESS,
    WIPE,
    STOPPED,
    TECHNICAL_ERROR,
}

sealed interface ProgressionState {
    data class Running(val activation: PhaseActivation, val entered: Long) : ProgressionState

    data class Transition(val next: String) : ProgressionState

    /** A terminal phase route is frozen; grave expiry and party defeat still need settlement. */
    data class Closing(val route: PhaseRoute) : ProgressionState

    data class Finishing(val operation: CompletionOperation, val cleanupReleased: Boolean) :
        ProgressionState

    data class Ended(val result: AttemptResult) : ProgressionState
}

sealed interface ProgressionEffect {
    data class PhaseStarted(val activation: PhaseActivation) : ProgressionEffect

    data class PhaseEnded(
        val activation: PhaseActivation,
        val outcome: GameplayOutcome,
        val elapsed: SimulationDuration,
        val route: PhaseRoute,
        val reason: PhaseFailureReason?,
    ) : ProgressionEffect

    data class ClosePhase(val activation: PhaseActivation) : ProgressionEffect

    data class CommitCompletion(val operation: CompletionOperation) : ProgressionEffect

    data object CleanupAttempt : ProgressionEffect

    data class Ended(val result: AttemptResult, val announce: Boolean) : ProgressionEffect
}

/**
 * Phase routing and the success commit gate, independent of native effects and author rule
 * execution. A host supplies already-settled outcome signals, persists CommitCompletion, and
 * applies cleanup.
 */
class PhaseProgression(
    val attempt: UUID,
    val revision: String,
    private val definition: EncounterDefinition,
) {
    private val thread = Thread.currentThread()
    private val effects = mutableListOf<ProgressionEffect>()
    private var generation = 0L
    private var cleanupRequested = false
    private var stepOpen = false
    private var phaseSettled = false
    var tick = 0L
        private set

    var state: ProgressionState = enter(definition.start)
        private set

    fun drainEffects(): List<ProgressionEffect> {
        checkThread()
        val result = java.util.List.copyOf(effects)
        effects.clear()
        return result
    }

    /** The host calls once per simulation tick, after relevant rules/results have settled. */
    fun advance(signals: List<OutcomeSignal> = emptyList()) {
        if (beginStep()) settle(signals)
    }

    /**
     * Separate start and settlement let the host run the new phase and its reactions on this tick.
     */
    fun beginStep(): Boolean {
        checkThread()
        check(!stepOpen) { "Previous simulation step has not settled" }
        if (state is ProgressionState.Ended || state is ProgressionState.Finishing) return false
        stepOpen = true
        phaseSettled = false
        tick = Math.incrementExact(tick)
        val before = state
        if (before is ProgressionState.Transition) state = enter(before.next)
        return true
    }

    fun settle(
        signals: List<OutcomeSignal> = emptyList(),
        conditions: (Condition) -> Boolean = {
            error("Conditional routing requires a current observation")
        },
    ) {
        settlePhase(signals, conditions)
        finishStep(partyDefeated = false)
    }

    /** Freeze the phase result before final reactions, without committing the attempt yet. */
    fun settlePhase(
        signals: List<OutcomeSignal> = emptyList(),
        conditions: (Condition) -> Boolean = {
            error("Conditional routing requires a current observation")
        },
    ) {
        checkThread()
        check(stepOpen && !phaseSettled) { "No unsettled phase boundary is open" }
        phaseSettled = true
        if (state !is ProgressionState.Running) return
        val active = state as ProgressionState.Running
        val phase = definition.byId.getValue(active.activation.phase)
        val elapsed = tick - active.entered
        val observed = signals.filter { it.activation == active.activation }.map { it.outcome }
        val succeeded =
            GameplayOutcome.SUCCESS in observed ||
                phase.duration?.let { elapsed >= it.ticks } == true
        val failed =
            GameplayOutcome.FAILURE in observed ||
                phase.deadline?.let { elapsed >= it.ticks } == true
        val outcome =
            when {
                succeeded && failed ->
                    if (definition.precedence == OutcomePrecedence.SUCCESS) GameplayOutcome.SUCCESS
                    else GameplayOutcome.FAILURE
                succeeded -> GameplayOutcome.SUCCESS
                failed -> GameplayOutcome.FAILURE
                else -> return
            }
        val requested = if (outcome == GameplayOutcome.SUCCESS) phase.success else phase.failure
        val route =
            if (requested is PhaseRoute.Choose)
                requested.branches.firstOrNull { conditions(it.first) }?.second
                    ?: requested.otherwise
            else requested
        val reason =
            if (outcome == GameplayOutcome.FAILURE)
                signals
                    .firstOrNull {
                        it.activation == active.activation && it.outcome == GameplayOutcome.FAILURE
                    }
                    ?.reason
                    ?: if (GameplayOutcome.FAILURE in observed) PhaseFailureReason.CONDITION
                    else PhaseFailureReason.DEADLINE
            else null
        effects +=
            ProgressionEffect.PhaseEnded(
                active.activation,
                outcome,
                SimulationDuration(elapsed),
                route,
                reason,
            )
        effects += ProgressionEffect.ClosePhase(active.activation)
        when (route) {
            is PhaseRoute.Next -> state = ProgressionState.Transition(route.phase)
            PhaseRoute.Wipe,
            PhaseRoute.Complete -> state = ProgressionState.Closing(route)
            is PhaseRoute.Choose -> error("Phase destination must be frozen before cleanup")
        }
    }

    /** After ready result reactions and remaining grave expirations, settle the final decision. */
    fun finishStep(partyDefeated: Boolean) {
        checkThread()
        check(stepOpen && phaseSettled) {
            "Phase results must be frozen before attempt arbitration"
        }
        stepOpen = false
        val closing = state as? ProgressionState.Closing
        val success = closing?.route == PhaseRoute.Complete
        val failure = closing?.route == PhaseRoute.Wipe || partyDefeated
        if (success && (!failure || definition.precedence == OutcomePrecedence.SUCCESS)) {
            val operation = CompletionOperation(attempt, revision, SimulationDuration(tick))
            state = ProgressionState.Finishing(operation, cleanupReleased = false)
            effects += ProgressionEffect.CommitCompletion(operation)
        } else if (failure) {
            closeCurrentPhase()
            end(AttemptResult.WIPE)
        }
    }

    /**
     * A retry/reconciliation must refer to this exact original operation. Duplicate
     * acknowledgements are no-ops.
     */
    fun acknowledge(operation: CompletionOperation, status: CommitStatus): Boolean {
        checkThread()
        val finishing = state as? ProgressionState.Finishing ?: return false
        if (finishing.operation != operation) return false
        when (status) {
            CommitStatus.UNKNOWN -> return true
            CommitStatus.COMMITTED ->
                end(AttemptResult.SUCCESS, announce = !finishing.cleanupReleased)
            CommitStatus.ABSENT -> end(AttemptResult.TECHNICAL_ERROR)
        }
        return true
    }

    /**
     * Called by the host's operational timeout, not by fabricated gameplay ticks while storage is
     * pending.
     */
    fun releasePendingCleanup() {
        checkThread()
        val finishing = state as? ProgressionState.Finishing ?: return
        requestCleanup()
        state = finishing.copy(cleanupReleased = true)
    }

    fun stop() {
        checkThread()
        if (state is ProgressionState.Finishing) {
            releasePendingCleanup()
            return
        }
        if (state is ProgressionState.Ended) return
        closeCurrentPhase()
        end(AttemptResult.STOPPED)
    }

    fun technicalError() {
        checkThread()
        if (state is ProgressionState.Finishing) {
            releasePendingCleanup()
            return
        }
        if (state is ProgressionState.Ended) return
        closeCurrentPhase()
        end(AttemptResult.TECHNICAL_ERROR)
    }

    private fun enter(id: String): ProgressionState.Running {
        val activation =
            PhaseActivation(attempt, Math.incrementExact(generation).also { generation = it }, id)
        effects += ProgressionEffect.PhaseStarted(activation)
        return ProgressionState.Running(activation, tick)
    }

    private fun closeCurrentPhase() {
        (state as? ProgressionState.Running)?.let {
            effects += ProgressionEffect.ClosePhase(it.activation)
        }
    }

    private fun end(result: AttemptResult, announce: Boolean = true) {
        stepOpen = false
        state = ProgressionState.Ended(result)
        requestCleanup()
        effects += ProgressionEffect.Ended(result, announce)
    }

    private fun requestCleanup() {
        if (!cleanupRequested) {
            cleanupRequested = true
            effects += ProgressionEffect.CleanupAttempt
        }
    }

    private fun checkThread() {
        check(Thread.currentThread() === thread) { "Phase progression belongs to another thread" }
    }
}
