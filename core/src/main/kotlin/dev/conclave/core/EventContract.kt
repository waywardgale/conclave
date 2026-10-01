package dev.conclave.core

import java.math.BigDecimal
import java.util.UUID

enum class EventValueKind {
    INTEGER,
    NUMBER,
    BOOLEAN,
    STRING,
    DURATION,
    PLAYER,
    TIMER,
}

data class EventField(
    val kind: EventValueKind,
    val required: Boolean = true,
    val choices: Set<String> = emptySet(),
    val help: String = "",
)

class EventContract(fields: Map<String, EventField>, val triggeringPlayer: String? = null) {
    val fields: Map<String, EventField> =
        java.util.Map.copyOf(
            fields.mapValues {
                it.value.copy(choices = java.util.Set.copyOf(it.value.choices))
            }
        )

    init {
        require(
            triggeringPlayer == null ||
                fields[triggeringPlayer]?.let { it.kind == EventValueKind.PLAYER && it.required } ==
                    true
        )
    }
}

sealed interface EventDatum {
    val kind: EventValueKind

    data class Integer(val value: Long) : EventDatum {
        override val kind = EventValueKind.INTEGER
    }

    data class Number(val value: BigDecimal) : EventDatum {
        override val kind = EventValueKind.NUMBER
    }

    data class Flag(val value: Boolean) : EventDatum {
        override val kind = EventValueKind.BOOLEAN
    }

    data class Text(val value: String) : EventDatum {
        override val kind = EventValueKind.STRING
    }

    data class Duration(val value: SimulationDuration) : EventDatum {
        override val kind = EventValueKind.DURATION
    }

    data class Player(val value: UUID) : EventDatum {
        override val kind = EventValueKind.PLAYER
    }

    data class Timer(val scope: RuntimeScopeIdentity, val id: String, val generation: Long) :
        EventDatum {
        override val kind = EventValueKind.TIMER
    }
}

class EventPayload(contract: EventContract, values: Map<String, EventDatum>) {
    val values: Map<String, EventDatum> = java.util.Map.copyOf(values)

    init {
        require(values.keys.all { it in contract.fields }) { "Undeclared event field" }
        require(contract.fields.all { (name, field) -> !field.required || name in values }) {
            "Missing required event field"
        }
        values.forEach { (name, value) ->
            val field = contract.fields.getValue(name)
            require(field.kind == value.kind) { "Event field type mismatch" }
            require(
                field.choices.isEmpty() || value is EventDatum.Text && value.value in field.choices
            ) {
                "Unregistered event value"
            }
        }
    }
}

internal object MechanicEvents {
    private val elapsed =
        EventField(EventValueKind.DURATION, help = "Simulation time since this activation started")
    private val player = EventField(EventValueKind.PLAYER)
    private val integer = EventField(EventValueKind.INTEGER)
    val standard =
        mapOf(
            "started" to EventContract(emptyMap()),
            "completed" to EventContract(mapOf("elapsed" to elapsed)),
        )

    fun composition(reasons: Set<String> = setOf("child_failed")) =
        standard +
            ("failed" to
                EventContract(
                    mapOf(
                        "elapsed" to elapsed,
                        "reason" to EventField(EventValueKind.STRING, choices = reasons),
                    )
                ))

    fun capture() = standard

    fun interact(): Map<String, EventContract> {
        val fields =
            mapOf(
                "player" to player,
                "uses_before" to integer,
                "uses_after" to integer,
                "uses_required" to integer,
            )
        return standard +
            mapOf(
                "used" to EventContract(fields, "player"),
                "completed" to EventContract(fields + ("elapsed" to elapsed), "player"),
            )
    }

    fun deliver() =
        standard +
            ("completed" to
                EventContract(mapOf("elapsed" to elapsed, "player" to player), "player"))

    fun defeat() =
        standard +
            ("failed" to
                EventContract(
                    mapOf(
                        "elapsed" to elapsed,
                        "reason" to
                            EventField(EventValueKind.STRING, choices = setOf("unreachable")),
                    )
                ))

    fun pattern(configuration: PatternConfiguration): Map<String, EventContract> {
        val attributed = if (configuration.perPlayer) player else player.copy(required = false)
        val fields =
            mapOf(
                "token" to EventField(EventValueKind.STRING, choices = configuration.vocabulary),
                "origin" to
                    EventField(EventValueKind.STRING, choices = setOf("interaction", "action")),
                "progress_before" to integer,
                "progress_after" to integer,
                "pattern_length" to integer,
                "player" to attributed,
            )
        val triggering = "player".takeIf { configuration.perPlayer }
        val reset =
            mapOf(
                "progress_before" to integer,
                "progress_after" to integer,
                "pattern_length" to integer,
            ) + if (configuration.perPlayer) mapOf("player" to player) else emptyMap()
        return standard +
            mapOf(
                "matched" to
                    EventContract(
                        fields + ("record_completed" to EventField(EventValueKind.BOOLEAN)),
                        triggering,
                    ),
                "mismatched" to EventContract(fields, triggering),
                "progress_reset" to EventContract(reset, triggering),
            ) +
            if (configuration.perPlayer)
                mapOf(
                    "player_completed" to
                        EventContract(
                            mapOf("player" to player, "pattern_length" to integer),
                            "player",
                        )
                )
            else emptyMap()
    }
}
