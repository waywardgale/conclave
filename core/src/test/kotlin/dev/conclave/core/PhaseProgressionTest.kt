package dev.conclave.core

import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test

class PhaseProgressionTest {
    private fun progress(
        vararg phases: PhaseDefinition,
        precedence: OutcomePrecedence = OutcomePrecedence.SUCCESS,
    ): PhaseProgression =
        PhaseProgression(
            UUID.randomUUID(),
            "revision",
            EncounterDefinition(
                DefinitionId("test", "encounter"),
                null,
                phases.first().id,
                phases.toList(),
                precedence,
            ),
        )

    private fun PhaseProgression.activation() = assertIs<ProgressionState.Running>(state).activation

    private fun phase(
        id: String = "ritual",
        next: PhaseRoute = PhaseRoute.Complete,
        duration: Long? = null,
        deadline: Long? = null,
    ) =
        PhaseDefinition(
            id,
            duration?.let(::SimulationDuration),
            deadline?.let(::SimulationDuration),
            next,
        )

    @Test
    fun `final phase completion waits for grave and party arbitration with configured precedence`() {
        for (precedence in OutcomePrecedence.entries) {
            val runtime = progress(phase(duration = 1), precedence = precedence)
            runtime.drainEffects()
            runtime.beginStep()
            runtime.settlePhase()
            assertEquals(ProgressionState.Closing(PhaseRoute.Complete), runtime.state)
            val phaseEffects = runtime.drainEffects()
            assertEquals(1, phaseEffects.filterIsInstance<ProgressionEffect.PhaseEnded>().size)
            assertTrue(
                phaseEffects.none {
                    it is ProgressionEffect.CommitCompletion || it is ProgressionEffect.Ended
                }
            )
            runtime.finishStep(partyDefeated = true)
            if (precedence == OutcomePrecedence.SUCCESS)
                assertIs<ProgressionState.Finishing>(runtime.state)
            else assertEquals(ProgressionState.Ended(AttemptResult.WIPE), runtime.state)
        }
    }

    @Test
    fun `party defeat preserves a committed next route without starting its next phase`() {
        val runtime =
            progress(phase("first", PhaseRoute.Next("second"), duration = 1), phase("second"))
        runtime.drainEffects()
        runtime.beginStep()
        runtime.settlePhase()
        assertEquals(ProgressionState.Transition("second"), runtime.state)
        runtime.finishStep(partyDefeated = true)
        assertEquals(ProgressionState.Ended(AttemptResult.WIPE), runtime.state)
        val effects = runtime.drainEffects()
        assertEquals(
            PhaseRoute.Next("second"),
            effects.filterIsInstance<ProgressionEffect.PhaseEnded>().single().route,
        )
        assertTrue(effects.none { it is ProgressionEffect.PhaseStarted })
    }

    @Test
    fun `party defeat cancels an unfinished phase without fabricating a phase result`() {
        val runtime = progress(phase())
        runtime.drainEffects()
        runtime.beginStep()
        runtime.settlePhase()
        runtime.finishStep(partyDefeated = true)
        assertEquals(ProgressionState.Ended(AttemptResult.WIPE), runtime.state)
        assertTrue(runtime.drainEffects().none { it is ProgressionEffect.PhaseEnded })
    }

    @Test
    fun `untimed phases wait for a signal bound to their current activation`() {
        val runtime = progress(phase())
        val activation = runtime.activation()
        repeat(100) { runtime.advance() }
        runtime.advance(
            listOf(
                OutcomeSignal(activation.copy(attempt = UUID.randomUUID()), GameplayOutcome.SUCCESS)
            )
        )
        assertIs<ProgressionState.Running>(runtime.state)
        runtime.advance(listOf(OutcomeSignal(activation, GameplayOutcome.SUCCESS)))
        assertIs<ProgressionState.Finishing>(runtime.state)
    }

    @Test
    fun `explicit routes wait until next tick and reentry rejects stale signals`() {
        val runtime =
            progress(
                phase("ritual", PhaseRoute.Next("damage")),
                phase("damage", PhaseRoute.Next("ritual"), duration = 1),
            )
        val original = runtime.activation()
        runtime.advance(listOf(OutcomeSignal(original, GameplayOutcome.SUCCESS)))
        assertEquals(ProgressionState.Transition("damage"), runtime.state)
        runtime.advance()
        assertEquals("damage", runtime.activation().phase)
        runtime.advance()
        assertEquals(ProgressionState.Transition("ritual"), runtime.state)
        runtime.advance(listOf(OutcomeSignal(original, GameplayOutcome.SUCCESS)))
        assertEquals("ritual", runtime.activation().phase)
        assertNotEquals(original.generation, runtime.activation().generation)
        assertEquals(
            3,
            runtime.drainEffects().filterIsInstance<ProgressionEffect.PhaseStarted>().size,
        )
    }

    @Test
    fun `same tick duration and deadline use configurable precedence`() {
        val success = progress(phase(duration = 1, deadline = 1))
        success.advance()
        assertIs<ProgressionState.Finishing>(success.state)
        val failure =
            progress(phase(duration = 1, deadline = 1), precedence = OutcomePrecedence.FAILURE)
        failure.advance()
        assertEquals(ProgressionState.Ended(AttemptResult.WIPE), failure.state)
    }

    @Test
    fun `failure recovery routes continue the same attempt`() {
        val ritual = phase("ritual").copy(failure = PhaseRoute.Next("recovery"))
        val runtime = progress(ritual, phase("recovery"))
        runtime.advance(listOf(OutcomeSignal(runtime.activation(), GameplayOutcome.FAILURE)))
        assertEquals(ProgressionState.Transition("recovery"), runtime.state)
        runtime.advance()
        assertEquals("recovery", runtime.activation().phase)
        assertTrue(runtime.drainEffects().none { it is ProgressionEffect.Ended })
    }

    @Test
    fun `even rewardless success stays frozen until its exact durable operation commits`() {
        val runtime = progress(phase(duration = 1))
        runtime.advance()
        val operation = assertIs<ProgressionState.Finishing>(runtime.state).operation
        assertTrue(runtime.drainEffects().none { it is ProgressionEffect.Ended })
        assertFalse(runtime.acknowledge(operation.copy(revision = "other"), CommitStatus.COMMITTED))
        assertTrue(runtime.acknowledge(operation, CommitStatus.UNKNOWN))
        repeat(10) { runtime.advance() }
        assertEquals(1L, runtime.tick)
        assertIs<ProgressionState.Finishing>(runtime.state)
        assertTrue(runtime.acknowledge(operation, CommitStatus.COMMITTED))
        assertFalse(runtime.acknowledge(operation, CommitStatus.COMMITTED))
        assertEquals(
            listOf(
                ProgressionEffect.CleanupAttempt,
                ProgressionEffect.Ended(AttemptResult.SUCCESS, true),
            ),
            runtime.drainEffects(),
        )
    }

    @Test
    fun `cleanup after storage timeout does not rewrite an uncertain success or replay presentation`() {
        val runtime = progress(phase(duration = 1))
        runtime.advance()
        val operation = assertIs<ProgressionState.Finishing>(runtime.state).operation
        runtime.drainEffects()
        runtime.releasePendingCleanup()
        runtime.stop()
        runtime.technicalError()
        assertIs<ProgressionState.Finishing>(runtime.state)
        assertEquals(listOf(ProgressionEffect.CleanupAttempt), runtime.drainEffects())
        runtime.acknowledge(operation, CommitStatus.COMMITTED)
        assertEquals(
            listOf(ProgressionEffect.Ended(AttemptResult.SUCCESS, false)),
            runtime.drainEffects(),
        )
    }

    @Test
    fun `proven absent transaction ends as technical error rather than victory or wipe`() {
        val runtime = progress(phase(duration = 1))
        runtime.advance()
        val operation = assertIs<ProgressionState.Finishing>(runtime.state).operation
        runtime.acknowledge(operation, CommitStatus.ABSENT)
        assertEquals(ProgressionState.Ended(AttemptResult.TECHNICAL_ERROR), runtime.state)
    }

    @Test
    fun `stop closes the phase and cleans once without requesting successful completion`() {
        val runtime = progress(phase())
        runtime.drainEffects()
        runtime.stop()
        runtime.stop()
        val effects = runtime.drainEffects()
        assertEquals(1, effects.count { it is ProgressionEffect.ClosePhase })
        assertEquals(1, effects.count { it == ProgressionEffect.CleanupAttempt })
        assertTrue(effects.none { it is ProgressionEffect.CommitCompletion })
        assertEquals(ProgressionState.Ended(AttemptResult.STOPPED), runtime.state)
    }
}
