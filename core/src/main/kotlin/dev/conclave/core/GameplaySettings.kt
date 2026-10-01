package dev.conclave.core

import java.math.BigDecimal

class HealthPercentage(value: BigDecimal = BigDecimal(100)) {
    val value = value.stripTrailingZeros()

    init {
        require(value > BigDecimal.ZERO && value <= BigDecimal(100))
    }

    override fun equals(other: Any?) = other is HealthPercentage && value == other.value

    override fun hashCode() = value.hashCode()

    fun of(maximumHealth: Double): Double {
        require(maximumHealth.isFinite() && maximumHealth > 0)
        return maximumHealth * value.toDouble() / 100.0
    }
}

data class RevivalPolicy(
    val enabled: Boolean = true,
    val selfRevival: Boolean = false,
    val assistedRevival: Boolean = true,
    val encountersMayDisableSelfRevival: Boolean = true,
    val combatWindow: SimulationDuration = SimulationDuration(300),
    val delay: SimulationDuration = SimulationDuration(0),
    val helpTime: SimulationDuration = SimulationDuration(0),
    val helpReach: BigDecimal = BigDecimal(3),
    val health: HealthPercentage = HealthPercentage(),
    val damageProtection: SimulationDuration = SimulationDuration(0),
) {
    init {
        require(combatWindow.ticks > 0 && helpReach > BigDecimal.ZERO)
    }

    fun selfAllowed(prohibited: Boolean) = enabled && selfRevival && !prohibited

    val assistanceAllowed
        get() = enabled && assistedRevival

    fun anyMethod(prohibited: Boolean) = selfAllowed(prohibited) || assistanceAllowed
}

enum class SpectatorMode {
    TEAMMATES,
    FREE,
}

data class SpectatingPolicy(
    val mode: SpectatorMode = SpectatorMode.TEAMMATES,
    val sharePrivateInfo: Boolean = false,
)

class RecoveryPolicy(
    val health: HealthPercentage = HealthPercentage(),
    val hunger: Int = 20,
    val searchRadius: BigDecimal = BigDecimal(3),
    outsideAttemptFallbacks: List<DefinitionId> = emptyList(),
) {
    val outsideAttemptFallbacks: List<DefinitionId> = java.util.List.copyOf(outsideAttemptFallbacks)

    init {
        require(
            hunger in 0..20 && searchRadius >= BigDecimal.ZERO && outsideAttemptFallbacks.size <= 64
        )
    }
}

class GameplaySettings(
    val revival: RevivalPolicy = RevivalPolicy(),
    val spectating: SpectatingPolicy = SpectatingPolicy(),
    val recovery: RecoveryPolicy = RecoveryPolicy(),
)

object HealthPercentageSchema : ConfigSchema<HealthPercentage> {
    override val description =
        SchemaDescription(
            "string",
            "Positive percentage of effective maximum health, at most 100%",
            maxLength = 64,
            pattern = "^[0-9]+(?:\\.[0-9]+)?%$",
            format = "percentage",
        )

    override fun decode(value: YamlValue, context: SchemaContext): HealthPercentage {
        val text = value.text()
        if (text.length > 64 || !Regex(checkNotNull(description.pattern)).matches(text))
            invalid("health_percentage", description.help, value.source)
        val number = text.dropLast(1).toBigDecimal().stripTrailingZeros()
        if (number <= BigDecimal.ZERO || number > BigDecimal(100))
            invalid("health_percentage", description.help, value.source)
        return HealthPercentage(number)
    }

    override fun encode(value: HealthPercentage) =
        jsonString(value.value.stripTrailingZeros().toPlainString() + "%")
}

object GameplaySettingsSchema : ConfigSchema<GameplaySettings> {
    val revival: ConfigSchema<RevivalPolicy> =
        ConfigRecordBuilder().run {
            val enabled = defaulted("enabled", ConfigSchemas.flag("Enable ordinary revival"), true)
            val self =
                defaulted(
                    "self_revival",
                    ConfigSchemas.flag("Allow an explicit self-revival action"),
                    false,
                )
            val assisted =
                defaulted(
                    "assisted_revival",
                    ConfigSchemas.flag("Allow another eligible player to revive a grave"),
                    true,
                )
            val encounterOverride =
                defaulted(
                    "encounters_may_disable_self_revival",
                    ConfigSchemas.flag("Allow an encounter to prohibit self-revival"),
                    true,
                )
            val window =
                defaulted(
                    "combat_window",
                    ConfigSchemas.duration("Time available for revival, spent only during combat"),
                    SimulationDuration(300),
                )
            val delay =
                defaulted(
                    "delay",
                    ConfigSchemas.duration("Delay from death before either revival method", true),
                    SimulationDuration(0),
                )
            val help =
                defaulted(
                    "help_time",
                    ConfigSchemas.duration(
                        "Continuous assistance from one helper; zero is instant",
                        true,
                    ),
                    SimulationDuration(0),
                )
            val reach =
                defaulted(
                    "help_reach",
                    ConfigSchemas.decimal(
                        "Positive helper reach in blocks",
                        BigDecimal("0.000001"),
                        BigDecimal(64),
                    ),
                    BigDecimal(3),
                )
            val health = defaulted("health", HealthPercentageSchema, HealthPercentage())
            val protection =
                defaulted(
                    "damage_protection",
                    ConfigSchemas.duration(
                        "Protection after revival; hostile action ends it early",
                        true,
                    ),
                    SimulationDuration(0),
                )
            build(
                "Global revival policy captured by each attempt or outside death",
                { v ->
                    RevivalPolicy(
                        v[enabled],
                        v[self],
                        v[assisted],
                        v[encounterOverride],
                        v[window],
                        v[delay],
                        v[help],
                        v[reach],
                        v[health],
                        v[protection],
                    )
                },
                { v ->
                    mapOf(
                        enabled to v.enabled,
                        self to v.selfRevival,
                        assisted to v.assistedRevival,
                        encounterOverride to v.encountersMayDisableSelfRevival,
                        window to v.combatWindow,
                        delay to v.delay,
                        help to v.helpTime,
                        reach to v.helpReach,
                        health to v.health,
                        protection to v.damageProtection,
                    )
                },
            )
        }
    val spectating: ConfigSchema<SpectatingPolicy> =
        ConfigRecordBuilder().run {
            val mode =
                defaulted(
                    "mode",
                    ConfigSchemas.choice(
                        "Viewing mode after the grave opportunity",
                        SpectatorMode.entries.associateBy { it.name.lowercase() },
                    ),
                    SpectatorMode.TEAMMATES,
                )
            val share =
                defaulted(
                    "share_private_info",
                    ConfigSchemas.flag(
                        "Permit mirroring eligible teammates' Conclave presentation"
                    ),
                    false,
                )
            build(
                "Captured spectator and private-presentation policy",
                { v -> SpectatingPolicy(v[mode], v[share]) },
                { v -> mapOf(mode to v.mode, share to v.sharePrivateInfo) },
            )
        }
    private val fallback: ConfigSchema<DefinitionId> =
        object : ConfigSchema<DefinitionId> {
            override val description =
                objectDescription(
                    "Outside-attempt world location",
                    mapOf(
                        "location" to
                            objectDescription(
                                "Explicit world scope",
                                mapOf(
                                    "id" to DefinitionReferenceSchema.description,
                                    "scope" to
                                        SchemaDescription(
                                            "string",
                                            "World location scope",
                                            choices = listOf("world"),
                                        ),
                                ),
                            )
                    ),
                )

            override fun decode(value: YamlValue, context: SchemaContext): DefinitionId {
                val fields = Fields(value.mapping())
                val location = Fields(fields.required("location").mapping())
                val id = DefinitionReferenceSchema.decode(location.required("id"), context)
                val scope = location.required("scope")
                if (scope.text() != "world")
                    invalid(
                        "world_location",
                        "Outside recovery requires scope: world",
                        scope.source,
                    )
                location.finish()
                fields.finish()
                return id
            }

            override fun encode(value: DefinitionId) =
                "{\"location\":{\"id\":" +
                    DefinitionReferenceSchema.encode(value) +
                    ",\"scope\":\"world\"}}"
        }
    val recovery: ConfigSchema<RecoveryPolicy> =
        ConfigRecordBuilder().run {
            val health = defaulted("health", HealthPercentageSchema, HealthPercentage())
            val hunger =
                defaulted(
                    "hunger",
                    ConfigSchemas.integer(
                        "Native food level; saturation and exhaustion are not refilled",
                        0,
                        20,
                    ),
                    20L,
                )
            val radius =
                defaulted(
                    "search_radius",
                    ConfigSchemas.decimal(
                        "Nearby safe-location search radius; zero tries only the destination",
                        BigDecimal.ZERO,
                        BigDecimal(64),
                    ),
                    BigDecimal(3),
                )
            val fallbacks =
                defaulted(
                    "outside_attempt_fallbacks",
                    ConfigSchemas.list("Ordered fallback world locations", fallback, 0, 64),
                    emptyList(),
                )
            build(
                "Attempt-end recovery and outside grave fallback policy",
                { v -> RecoveryPolicy(v[health], v[hunger].toInt(), v[radius], v[fallbacks]) },
                { v ->
                    mapOf(
                        health to v.health,
                        hunger to v.hunger.toLong(),
                        radius to v.searchRadius,
                        fallbacks to v.outsideAttemptFallbacks,
                    )
                },
            )
        }
    private val record =
        ConfigRecordBuilder().run {
            val revival = defaulted("revival", GameplaySettingsSchema.revival, RevivalPolicy())
            val spectating =
                defaulted("spectating", GameplaySettingsSchema.spectating, SpectatingPolicy())
            val recovery = defaulted("recovery", GameplaySettingsSchema.recovery, RecoveryPolicy())
            build(
                "Singleton global gameplay policy; only operators may edit it",
                { v -> GameplaySettings(v[revival], v[spectating], v[recovery]) },
                { v ->
                    mapOf(revival to v.revival, spectating to v.spectating, recovery to v.recovery)
                },
            )
        }
    override val description
        get() = record.description

    override fun decode(value: YamlValue, context: SchemaContext) = record.decode(value, context)

    override fun encode(value: GameplaySettings) = record.encode(value)
}
