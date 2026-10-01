package dev.conclave.core

import java.math.BigInteger

sealed interface CaptureInterruption {
    data object Reset : CaptureInterruption

    data object Pause : CaptureInterruption

    data class Decay(val duration: SimulationDuration) : CaptureInterruption {
        init {
            require(duration.ticks > 0)
        }
    }
}

data class CaptureConfiguration(
    val area: String,
    val duration: SimulationDuration,
    val required: Int = 1,
    val players: PlayerSelection = PlayerSelection(),
    val interruption: CaptureInterruption = CaptureInterruption.Reset,
) {
    init {
        require(isAuthoredName(area) && duration.ticks > 0 && required > 0)
    }
}

class CaptureMechanic(private val config: CaptureConfiguration, context: MechanicContext) :
    StatefulMechanic(context) {
    private val scale =
        BigInteger.valueOf(
            (config.interruption as? CaptureInterruption.Decay)?.duration?.ticks ?: 1
        )
    private val complete = BigInteger.valueOf(config.duration.ticks) * scale
    private var progress = BigInteger.ZERO
    private var lastTick = 0L
    private var previouslyQualified = false
    val progressTicks: Double
        get() = progress.toDouble() / scale.toDouble()

    override fun initialize() {
        lastTick = context.tick
        previouslyQualified = qualified()
    }

    override fun tick() {
        if (state != MechanicState.RUNNING || context.tick == lastTick) return
        check(context.tick == lastTick + 1) { "Capture must observe every simulation step" }
        lastTick = context.tick
        val qualifies = qualified()
        if (qualifies && previouslyQualified) progress += scale
        else if (!qualifies)
            progress =
                when (config.interruption) {
                    CaptureInterruption.Reset -> BigInteger.ZERO
                    CaptureInterruption.Pause -> progress
                    is CaptureInterruption.Decay ->
                        (progress - BigInteger.valueOf(config.duration.ticks)).max(BigInteger.ZERO)
                }
        previouslyQualified = qualifies
        if (progress >= complete) {
            progress = complete
            finish(true)
        }
    }

    private fun qualified(): Boolean {
        val frame = context.players()
        return config.players.select(frame).count {
            it.id in frame.roster && it.canContribute && config.area in it.areas
        } >= config.required
    }
}
