package dev.conclave.core

import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test

class GraveLifecycleTest {
    private fun grave(
        policy: RevivalPolicy = RevivalPolicy(),
        outside: Boolean = false,
        prohibit: Boolean = false,
    ) =
        GraveLifecycle(
            UUID.randomUUID(),
            UUID.randomUUID(),
            if (outside) null else UUID.randomUUID(),
            policy,
            prohibit,
            0,
        )

    private fun helper() =
        AssistanceIdentity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

    private fun tick(grave: GraveLifecycle, step: Long, combat: Boolean = true) {
        grave.beginStep(step, combat)
        grave.settleExpiry()
    }

    @Test
    fun `window spends only combat simulation time and never resets on reentry`() {
        val grave = grave(RevivalPolicy(combatWindow = SimulationDuration(3)))
        tick(grave, 1, false)
        assertNull(grave.view().remainingCombat)
        tick(grave, 2)
        assertEquals(2, grave.view().remainingCombat!!.ticks)
        tick(grave, 3, false)
        assertEquals(2, grave.view().remainingCombat!!.ticks)
        tick(grave, 4)
        tick(grave, 5)
        assertEquals(GraveStatus.PASSED_OUT, grave.status)
        tick(grave, 6, false)
        assertFalse(grave.beginAssistance(helper(), true))
        assertEquals(
            listOf(GraveEvent.Died::class, GraveEvent.PassedOut::class),
            grave.drainEvents().map { it::class },
        )
    }

    @Test
    fun `eligible final-tick revival wins expiry once and stale grave identities fail`() {
        val grave = grave(RevivalPolicy(combatWindow = SimulationDuration(2)))
        tick(grave, 1)
        grave.beginStep(2, true)
        val helper = helper()
        assertTrue(grave.beginAssistance(helper, true))
        assertFalse(
            grave.revive(UUID.randomUUID(), RevivalMethod.ASSISTED, true, true, helper, true)
        )
        assertTrue(grave.revive(grave.grave, RevivalMethod.ASSISTED, true, true, helper, true))
        grave.settleExpiry()
        assertEquals(GraveClosure.REVIVED, grave.closure)
        assertFalse(grave.revive(grave.grave, RevivalMethod.ADMINISTRATIVE, true, true))
        assertEquals(1, grave.drainEvents().count { it is GraveEvent.Revived })
    }

    @Test
    fun `delay and continuous single-helper progress do not pool across interruptions`() {
        val grave =
            grave(RevivalPolicy(delay = SimulationDuration(2), helpTime = SimulationDuration(2)))
        val first = helper()
        val second = helper()
        assertFalse(grave.beginAssistance(first, true))
        tick(grave, 1, false)
        tick(grave, 2, false)
        assertTrue(grave.beginAssistance(first, true))
        assertFalse(grave.beginAssistance(second, true))
        tick(grave, 3, false)
        grave.observeAssistance(first, false)
        assertTrue(grave.beginAssistance(second, true))
        tick(grave, 4, false)
        assertFalse(grave.revive(grave.grave, RevivalMethod.ASSISTED, true, true, second, true))
        tick(grave, 5, false)
        assertFalse(grave.revive(grave.grave, RevivalMethod.ASSISTED, false, true, second, true))
        assertFalse(grave.revive(grave.grave, RevivalMethod.ASSISTED, true, false, second, true))
        assertTrue(grave.revive(grave.grave, RevivalMethod.ASSISTED, true, true, second, true))
    }

    @Test
    fun `no ordinary method passes out immediately while admin revival preserves captured policy`() {
        val grave = grave(RevivalPolicy(enabled = false, delay = SimulationDuration(100)))
        assertEquals(GraveStatus.PASSED_OUT, grave.status)
        assertFalse(grave.selfRevivalOpportunity())
        assertEquals(2, grave.drainEvents().size)
        assertTrue(grave.revive(grave.grave, RevivalMethod.ADMINISTRATIVE, true, true))
        assertEquals(100.0, grave.policy.health.of(100.0))
    }

    @Test
    fun `outside attempts have no combat expiry and normal respawn abandons the grave`() {
        val grave = grave(RevivalPolicy(combatWindow = SimulationDuration(1)), outside = true)
        for (tick in 1L..5) tick(grave, tick)
        assertNull(grave.view().remainingCombat)
        assertEquals(GraveStatus.OPPORTUNITY, grave.status)
        assertFalse(grave.revive(grave.grave, RevivalMethod.SELF, true, true))
        assertTrue(grave.normalRespawn(grave.grave))
        assertFalse(grave.beginAssistance(helper(), true))
        assertEquals(GraveClosure.NORMAL_RESPAWN, grave.closure)
        assertFalse(grave().normalRespawn(UUID.randomUUID()))
    }

    @Test
    fun `self revival respects encounter prohibition and an ended attempt cannot reopen`() {
        val allowed = grave(RevivalPolicy(selfRevival = true))
        assertTrue(allowed.selfRevivalOpportunity())
        assertTrue(allowed.revive(allowed.grave, RevivalMethod.SELF, true, true))
        val prohibited = grave(RevivalPolicy(selfRevival = true), prohibit = true)
        assertFalse(prohibited.selfRevivalOpportunity())
        assertFalse(prohibited.revive(prohibited.grave, RevivalMethod.SELF, true, true))
        prohibited.endAttempt()
        assertFalse(prohibited.revive(prohibited.grave, RevivalMethod.ADMINISTRATIVE, true, true))
    }
}
