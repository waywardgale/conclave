package dev.conclave.core

import java.util.UUID

sealed interface DeliveryDestination {
    data class Interaction(val target: TargetReference) : DeliveryDestination

    data class Area(val area: String) : DeliveryDestination
}

data class DeliverConfiguration(
    val relic: String,
    val destination: DeliveryDestination,
    val players: PlayerSelection = PlayerSelection(),
    val hold: SimulationDuration = SimulationDuration(0),
    val reach: Double? = null,
    val interruptOnDamage: Boolean = false,
    val consume: Boolean = false,
) {
    init {
        require(isAuthoredName(relic))
        require(reach == null || reach.isFinite() && reach > 0)
        require(
            destination !is DeliveryDestination.Area ||
                hold.ticks == 0L && reach == null && !interruptOnDamage && !consume
        )
    }
}

/**
 * Binds one logical instance, then revalidates each availability generation at delivery commitment.
 */
class DeliverMechanic(private val config: DeliverConfiguration, context: MechanicContext) :
    StatefulMechanic(context) {
    private data class Hold(
        val player: UUID,
        val gesture: UUID,
        val target: TargetHandle,
        val generation: Long,
        val began: Long,
    )

    private var bound: UUID? = null
    private var hold: Hold? = null
    private val gestures = mutableMapOf<UUID, UUID>()
    override val consumesInput
        get() = config.consume

    override fun initialize() {
        current()
    }

    override fun input(value: MechanicInput): Boolean {
        if (state != MechanicState.RUNNING) return false
        when (value) {
            is MechanicInput.Press -> {
                val destination =
                    config.destination as? DeliveryDestination.Interaction ?: return false
                if (
                    value.target.reference != destination.target ||
                        gestures[value.player] == value.gesture
                )
                    return false
                val relic = current() ?: return false
                if (
                    !carrier(relic, value.player, selected = true) ||
                        !context.validTarget(value.player, value.target, config.reach)
                )
                    return false
                gestures[value.player] = value.gesture
                if (config.hold.ticks == 0L) commit(relic, value.player)
                else
                    hold =
                        Hold(
                            value.player,
                            value.gesture,
                            value.target,
                            relic.generation,
                            context.tick,
                        )
                return true
            }
            is MechanicInput.AreaEntered -> {
                val destination = config.destination as? DeliveryDestination.Area ?: return false
                if (value.area != destination.area) return false
                val relic = current() ?: return false
                if (!carrier(relic, value.player, selected = false)) return false
                return commit(relic, value.player)
            }
            is MechanicInput.Release ->
                if (hold?.gesture == value.gesture && hold?.player == value.player) hold = null
            is MechanicInput.Damaged ->
                if (config.interruptOnDamage && hold?.player == value.player) hold = null
            else -> Unit
        }
        return false
    }

    override fun tick() {
        if (state != MechanicState.RUNNING) return
        val current = current()
        val pending = hold ?: return
        if (
            current == null ||
                current.generation != pending.generation ||
                !carrier(current, pending.player, true) ||
                !context.validTarget(pending.player, pending.target, config.reach)
        ) {
            hold = null
        } else if (context.tick - pending.began >= config.hold.ticks) {
            hold = null
            commit(current, pending.player)
        }
    }

    private fun current(): RelicObservation? {
        val observed = context.relic(config.relic) ?: return null
        if (bound == null) bound = observed.identity
        check(bound == observed.identity) { "Bound logical relic was replaced" }
        return observed
    }

    private fun carrier(relic: RelicObservation, player: UUID, selected: Boolean): Boolean =
        relic.holder == player &&
            eligible(player, config.players) &&
            (!selected || context.selectedRelic(player) == relic.identity)

    private fun commit(relic: RelicObservation, player: UUID): Boolean {
        if (!context.deliver(relic.identity, relic.generation, player)) return false
        hold = null
        finish(true, player)
        return true
    }

    override fun cancel() {
        super.cancel()
        hold = null
    }
}
