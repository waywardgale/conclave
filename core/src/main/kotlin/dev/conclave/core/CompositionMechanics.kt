package dev.conclave.core

/** Child definitions are immutable. The attempt coordinator owns their activation and ordering. */
class CompositionConfiguration(
    val mode: Mode,
    steps: List<MechanicOccurrence>,
    val any: Boolean = false,
    val count: Long? = null,
    val until: Condition? = null,
    val forever: Boolean = false,
    val delay: SimulationDuration = SimulationDuration(0),
) {
    enum class Mode {
        SEQUENCE,
        PARALLEL,
        REPEAT,
    }

    val steps: List<MechanicOccurrence> = java.util.List.copyOf(steps)
    val canComplete: Boolean
        get() =
            when (mode) {
                Mode.SEQUENCE -> steps.all { it.mechanic.canComplete }
                Mode.PARALLEL ->
                    if (any) steps.any { it.mechanic.canComplete }
                    else steps.all { it.mechanic.canComplete }
                Mode.REPEAT -> until != null || count != null && steps.single().mechanic.canComplete
            }

    init {
        require(steps.size in 1..256 && steps.map { it.id }.distinct().size == steps.size)
        if (mode == Mode.REPEAT) {
            require(
                steps.size == 1 && listOf(count != null, until != null, forever).count { it } == 1
            )
            require(count == null || count > 0)
        } else require(count == null && until == null && !forever && delay.ticks == 0L)
    }
}

internal object CompositionMechanics {
    private val occurrence =
        object : ConfigSchema<MechanicOccurrence> {
            override val description =
                SchemaDescription(
                    "",
                    "Named child mechanic using a registered type or reusable definition",
                    format = "mechanic_occurrence",
                )

            override fun decode(value: YamlValue, context: SchemaContext): MechanicOccurrence =
                checkNotNull(context.mechanicCompiler) {
                    "Child compilation requires a catalog linker"
                }(value, context)

            override fun encode(value: MechanicOccurrence): String = value.canonical()
        }

    fun register(builder: MechanicRegistry.Builder) {
        CompositionConfiguration.Mode.entries.forEach { mode ->
            builder.builtin(
                object : MechanicType<CompositionConfiguration> {
                    override val id = DefinitionId("conclave", mode.name.lowercase())
                    override val help = "Compose independently owned child mechanics"
                    override val schema = schema(mode)

                    override fun composition(configuration: CompositionConfiguration) =
                        configuration

                    override fun bindScope(
                        configuration: CompositionConfiguration,
                        encounter: ScopeDefinition,
                    ) =
                        CompositionConfiguration(
                            configuration.mode,
                            configuration.steps.map {
                                it.copy(mechanic = it.mechanic.bindScope(encounter))
                            },
                            configuration.any,
                            configuration.count,
                            configuration.until,
                            configuration.forever,
                            configuration.delay,
                        )

                    override fun events(configuration: CompositionConfiguration) =
                        MechanicEvents.composition()

                    override fun spatialReferences(configuration: CompositionConfiguration) =
                        configuration.steps.fold(
                            configuration.until?.spatialReferences() ?: SpatialReferences.EMPTY
                        ) { result, step ->
                            result + step.mechanic.spatialReferences
                        }

                    override fun create(
                        configuration: CompositionConfiguration,
                        context: MechanicContext,
                    ): MechanicInstance =
                        error("Compositions require the attempt coordinator's child lifecycle")
                }
            )
        }
    }

    private fun schema(
        mode: CompositionConfiguration.Mode
    ): ConfigSchema<CompositionConfiguration> =
        ConfigRecordBuilder().run {
            if (mode == CompositionConfiguration.Mode.REPEAT) {
                val body = required("body", occurrence)
                val count = optional("count", ConfigSchemas.integer("Successful iterations", 1))
                val until = optional("until", ConditionSchema)
                val forever =
                    defaulted(
                        "forever",
                        ConfigSchemas.flag("Repeat until owner cancellation"),
                        false,
                    )
                val delay =
                    defaulted(
                        "delay",
                        ConfigSchemas.duration("Gap between iterations", true),
                        SimulationDuration(0),
                    )
                build(
                    "Repeat one child with fresh state",
                    { values ->
                        if (
                            listOf(values[count] != null, values[until] != null, values[forever])
                                .count { it } != 1
                        )
                            throw IllegalArgumentException(
                                "Repeat requires exactly one of count, until, or forever: true"
                            )
                        CompositionConfiguration(
                            mode,
                            listOf(values[body]),
                            count = values[count],
                            until = values[until],
                            forever = values[forever],
                            delay = values[delay],
                        )
                    },
                    { value ->
                        mapOf(
                            body to value.steps.single(),
                            count to value.count,
                            until to value.until,
                            forever to value.forever,
                            delay to value.delay,
                        )
                    },
                )
            } else {
                val steps =
                    required(
                        "steps",
                        ConfigSchemas.list("Ordered child occurrences", occurrence, 1, 256),
                    )
                val completion =
                    if (mode == CompositionConfiguration.Mode.PARALLEL)
                        defaulted(
                            "completion",
                            ConfigSchemas.choice(
                                "Required successful children",
                                mapOf("all" to "all", "any" to "any"),
                            ),
                            "all",
                        )
                    else null
                build(
                    "${mode.name.lowercase()} composition",
                    { values ->
                        CompositionConfiguration(
                            mode,
                            values[steps],
                            any = completion?.let { values[it] == "any" } ?: false,
                        )
                    },
                    { value ->
                        buildMap {
                            put(steps, value.steps)
                            if (completion != null) put(completion, if (value.any) "any" else "all")
                        }
                    },
                )
            }
        }
}

internal fun MechanicOccurrence.canonical(): String =
    "{\"id\":${jsonString(id)},\"name\":${name?.let(::jsonString) ?: "null"},\"type\":${jsonString(mechanic.type.toString())},\"config\":${mechanic.canonical}}"
