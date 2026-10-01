package dev.conclave.core

import java.math.BigDecimal
import java.math.RoundingMode

/** A count of simulation steps, rounded up once so a requested duration never ends early. */
@JvmInline
value class SimulationDuration(val ticks: Long) {
    init {
        require(ticks >= 0)
    }

    companion object {
        private val syntax = Regex("([0-9]+(?:\\.[0-9]+)?)(ms|s|m|h)")
        private val factors =
            mapOf(
                "ms" to BigDecimal("0.02"),
                "s" to BigDecimal(20),
                "m" to BigDecimal(1200),
                "h" to BigDecimal(72000),
            )

        fun parse(text: String, allowZero: Boolean = false): SimulationDuration {
            require(text.length <= 64) { "Duration is too long" }
            val match =
                requireNotNull(syntax.matchEntire(text)) {
                    "Use a duration such as 500ms, 10s, 2m or 1h"
                }
            val steps =
                try {
                    BigDecimal(match.groupValues[1])
                        .multiply(factors.getValue(match.groupValues[2]))
                        .setScale(0, RoundingMode.CEILING)
                        .longValueExact()
                } catch (_: ArithmeticException) {
                    throw IllegalArgumentException(
                        "Duration exceeds the supported simulation range"
                    )
                }
            require(allowZero || steps > 0) { "Duration must be positive" }
            return SimulationDuration(steps)
        }
    }
}
