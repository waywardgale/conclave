package dev.conclave.core

import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test

class PartySurvivalTest {
    @Test
    fun `assisted-only dead parties end but self revival and living reconnects can preserve opportunity`() {
        val attempt = UUID.randomUUID()
        val player = UUID.randomUUID()
        fun frame(life: LifeState, participation: Participation, online: Boolean = true) =
            PlayerFrame(
                listOf(PlayerObservation(player, online, life, participation, false)),
                setOf(player),
            )
        fun grave(self: Boolean) =
            GraveLifecycle(
                UUID.randomUUID(),
                player,
                attempt,
                RevivalPolicy(selfRevival = self, combatWindow = SimulationDuration(1)),
                false,
                0,
            )
        assertFalse(
            PartySurvival.hasOpportunity(
                attempt,
                frame(LifeState.DEAD, Participation.ACTIVE),
                listOf(grave(false)),
            )
        )
        val allowed = grave(true)
        assertTrue(
            PartySurvival.hasOpportunity(
                attempt,
                frame(LifeState.DEAD, Participation.ACTIVE),
                listOf(allowed),
            )
        )
        assertFalse(
            PartySurvival.hasOpportunity(
                UUID.randomUUID(),
                frame(LifeState.DEAD, Participation.ACTIVE),
                listOf(allowed),
            )
        )
        assertFalse(
            PartySurvival.hasOpportunity(
                attempt,
                frame(LifeState.DEAD, Participation.OBSERVER),
                listOf(allowed),
            )
        )
        assertTrue(
            PartySurvival.hasOpportunity(
                attempt,
                frame(LifeState.ALIVE, Participation.RECONNECTING, false),
                emptyList(),
            )
        )
        assertFalse(
            PartySurvival.hasOpportunity(
                attempt,
                frame(LifeState.ALIVE, Participation.OBSERVER),
                emptyList(),
            )
        )
        allowed.beginStep(1, true)
        allowed.settleExpiry()
        assertFalse(
            PartySurvival.hasOpportunity(
                attempt,
                frame(LifeState.PASSED_OUT, Participation.ACTIVE),
                listOf(allowed),
            )
        )
    }
}
