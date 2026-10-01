package dev.conclave.core

import java.util.UUID

class InteractConfiguration(
    targets: List<TargetReference>,
    val uses: Long = 1,
    val distinctPlayers: Boolean = false,
    val hold: SimulationDuration = SimulationDuration(0),
    val cooldown: SimulationDuration = SimulationDuration(0),
    val players: PlayerSelection = PlayerSelection(),
    val reach: Double? = null,
    val interruptOnDamage: Boolean = false,
    val consume: Boolean = false,
) {
    val targets: List<TargetReference> = java.util.List.copyOf(targets)

    init {
        require(targets.isNotEmpty() && targets.distinct().size == targets.size && uses > 0)
        require(!distinctPlayers || cooldown.ticks == 0L)
        require(reach == null || reach.isFinite() && reach > 0)
    }
}

class InteractMechanic(private val config: InteractConfiguration, context: MechanicContext) :
    StatefulMechanic(context) {
    private data class Hold(val gesture: UUID, val target: TargetHandle, val began: Long)

    private val holds = linkedMapOf<UUID, Hold>()
    private val lastGesture = mutableMapOf<UUID, UUID>()
    private val lastUse = mutableMapOf<UUID, Long>()
    private val credited = mutableSetOf<UUID>()
    var uses = 0L
        private set

    override val consumesInput
        get() = config.consume

    override val interactionHold
        get() = config.hold

    override fun interactionProgress(player: UUID, gesture: UUID): InteractionProgress? =
        holds[player]
            ?.takeIf { it.gesture == gesture && state == MechanicState.RUNNING }
            ?.let {
                InteractionProgress(
                    SimulationDuration((context.tick - it.began).coerceIn(0, config.hold.ticks)),
                    config.hold,
                )
            }

    override fun acceptsPress(value: MechanicInput.Press): Boolean =
        state == MechanicState.RUNNING &&
            lastGesture[value.player] != value.gesture &&
            value.target.reference in config.targets &&
            eligible(value.player, config.players) &&
            (!config.distinctPlayers || value.player !in credited) &&
            lastUse[value.player]?.let { context.tick - it < config.cooldown.ticks } != true &&
            context.validTarget(value.player, value.target, config.reach)

    override fun input(value: MechanicInput): Boolean {
        if (state != MechanicState.RUNNING) return false
        when (value) {
            is MechanicInput.Press -> {
                if (lastGesture[value.player] == value.gesture) return false
                if (
                    value.target.reference !in config.targets ||
                        !eligible(value.player, config.players)
                )
                    return false
                lastGesture[value.player] = value.gesture
                holds.remove(value.player)
                if (config.distinctPlayers && value.player in credited) return false
                if (
                    lastUse[value.player]?.let { context.tick - it < config.cooldown.ticks } == true
                )
                    return false
                if (!context.validTarget(value.player, value.target, config.reach)) return false
                if (config.hold.ticks == 0L) credit(value.player)
                else holds[value.player] = Hold(value.gesture, value.target, context.tick)
                return true
            }
            is MechanicInput.Release ->
                if (holds[value.player]?.gesture == value.gesture) holds.remove(value.player)
            is MechanicInput.Damaged -> if (config.interruptOnDamage) holds.remove(value.player)
            else -> Unit
        }
        return false
    }

    override fun tick() {
        if (state != MechanicState.RUNNING) return
        for ((player, hold) in holds.toMap()) {
            if (
                !eligible(player, config.players) ||
                    !context.validTarget(player, hold.target, config.reach)
            )
                holds.remove(player)
            else if (context.tick - hold.began >= config.hold.ticks) {
                holds.remove(player)
                credit(player)
                if (state != MechanicState.RUNNING) break
            }
        }
    }

    private fun credit(player: UUID) {
        val before = uses
        uses++
        credited += player
        lastUse[player] = context.tick
        val notice = MechanicNotice.Used(player, before, uses, config.uses)
        if (uses >= config.uses) {
            holds.clear()
            finish(true, player, preceding = listOf(notice))
        } else context.notice(notice)
    }

    override fun cancel() {
        super.cancel()
        holds.clear()
    }
}
