package dev.conclave.core

/** Local orchestration with no phase routes or access to another occurrence's private state. */
class LayersConfiguration
internal constructor(
    val content: ScopeDefinition,
    val duration: SimulationDuration?,
    val deadline: SimulationDuration?,
    private val plan: ScopePlan? = null,
) {
    val canComplete
        get() = duration != null || content.completeWhen != null || content.objectives.isNotEmpty()

    internal fun bind(encounter: ScopeDefinition) =
        plan?.let {
            LayersConfiguration(it.link(encounter), duration, deadline)
        } ?: this
}

internal object LayersMechanic : MechanicType<LayersConfiguration> {
    override val id = DefinitionId("conclave", "layers")
    override val help =
        "Mechanic with private objectives, background mechanics, counters, timers and rules"
    override val schema =
        object : ConfigSchema<LayersConfiguration> {
            override val description = ScopeDescriptions.layers

            override fun decode(value: YamlValue, context: SchemaContext): LayersConfiguration {
                val fields = Fields(value.mapping())
                val duration =
                    fields.optional("duration")?.let {
                        ConfigSchemas.duration("Timed success").decode(it, context)
                    }
                val deadline =
                    fields.optional("deadline")?.let {
                        ConfigSchemas.duration("Timed failure").decode(it, context)
                    }
                val compiler = ScopeCompiler(checkNotNull(context.mechanicCompiler))
                val plan = compiler.prepare(fields, context.copy(phases = emptySet()))
                fields.finish()
                if (
                    duration != null &&
                        (plan.content.objectives.isNotEmpty() || plan.content.completeWhen != null)
                )
                    invalid(
                        "ambiguous_success",
                        "Express combined timing and objectives through complete_when and a named timer",
                        value.source,
                    )
                return LayersConfiguration(plan.validateLocals(compiler), duration, deadline, plan)
            }

            override fun encode(value: LayersConfiguration): String =
                "{\"content\":${value.content.canonical()},\"duration\":${value.duration?.ticks ?: "null"},\"deadline\":${value.deadline?.ticks ?: "null"}}"
        }

    override fun layers(configuration: LayersConfiguration) = configuration

    override fun bindScope(configuration: LayersConfiguration, encounter: ScopeDefinition) =
        configuration.bind(encounter)

    override fun events(configuration: LayersConfiguration) =
        MechanicEvents.composition(setOf("child_failed", "condition", "deadline"))

    override fun spatialReferences(configuration: LayersConfiguration) =
        configuration.content.spatialReferences()

    override fun create(
        configuration: LayersConfiguration,
        context: MechanicContext,
    ): MechanicInstance = error("Layers require the attempt coordinator's scoped lifecycle")
}

/** Structural descriptions also validate symbolic reusable bodies before their caller is known. */
internal object ScopeDescriptions {
    private val id = ConfigSchemas.identifier("Local ID").description
    private val name = ConfigSchemas.text("Display name").description
    private val flag = ConfigSchemas.flag("Enabled").description
    private val integer = ConfigSchemas.integer("Integer value").description
    private val duration = ConfigSchemas.duration("Positive simulation time").description
    private val reference = StateReferenceSchema.description
    private val child =
        SchemaDescription("", "Named child mechanic", format = "mechanic_occurrence")

    private fun record(
        help: String,
        properties: Map<String, SchemaDescription>,
        vararg required: String,
    ) = SchemaDescription("object", help, properties = properties, required = required.toSet())

    private fun entries(help: String, item: SchemaDescription, minimum: Int = 0) =
        SchemaDescription("array", help, items = item, minItems = minimum, maxItems = 256)

    private fun either(help: String, vararg variants: SchemaDescription) =
        SchemaDescription("", help, alternatives = variants.toList())

    private val conditionObjective =
        record(
            "Condition objective",
            mapOf(
                "id" to id,
                "name" to name,
                "condition" to ConditionSchema.description,
                "latch" to flag,
            ),
            "id",
            "condition",
        )
    private val counter =
        record(
            "Private named counter",
            mapOf(
                "id" to id,
                "name" to name,
                "initial" to integer,
                "min" to integer,
                "max" to integer,
            ),
            "id",
        )
    private val timer =
        record(
            "Private named timer",
            mapOf("id" to id, "name" to name, "duration" to duration, "auto_start" to flag),
            "id",
            "duration",
        )
    private val operand =
        either(
            "Integer literal or typed event field",
            integer,
            record("Event field", mapOf("event" to id), "event"),
        )
    private val source =
        either(
            "Event source",
            SchemaDescription("string", "The containing activation", choices = listOf("self")),
            record("Mechanic source", mapOf("mechanic" to reference), "mechanic"),
            record("Timer source", mapOf("timer" to reference), "timer"),
            record(
                "Participant source",
                mapOf("players" to LifecycleSelectionSchema.description),
                "players",
            ),
        )
    private val action =
        either(
            "Supported immediate state mutation",
            *buildList {
                for (verb in listOf("set", "add", "reset")) {
                    val fields =
                        if (verb == "reset") mapOf("counter" to reference)
                        else mapOf("counter" to reference, "value" to operand)
                    val key = "${verb}_counter"
                    add(
                        record(
                            key,
                            mapOf(key to record(key, fields, *fields.keys.toTypedArray())),
                            key,
                        )
                    )
                }
                for (verb in listOf("start", "restart", "pause", "resume", "stop")) {
                    val key = "${verb}_timer"
                    add(
                        record(
                            key,
                            mapOf(key to record(key, mapOf("timer" to reference), "timer")),
                            key,
                        )
                    )
                }
                val player =
                    record("Required typed player event field", mapOf("event" to id), "event")
                add(
                    record(
                        "Submit a declared pattern token",
                        mapOf(
                            "submit_token" to
                                record(
                                    "Submit token",
                                    mapOf(
                                        "mechanic" to reference,
                                        "token" to id,
                                        "player" to player,
                                    ),
                                    "mechanic",
                                    "token",
                                )
                        ),
                        "submit_token",
                    )
                )
                add(
                    record(
                        "Clear unfinished pattern progress",
                        mapOf(
                            "reset_pattern" to
                                record(
                                    "Reset progress",
                                    mapOf(
                                        "mechanic" to reference,
                                        "player" to player,
                                        "all" to flag,
                                    ),
                                    "mechanic",
                                )
                        ),
                        "reset_pattern",
                    )
                )
            }
                .toTypedArray(),
        )
    val subscription =
        record(
            "Subscription",
            mapOf("source" to source, "event" to id, "player" to id),
            "source",
            "event",
        )
    private val rule =
        record(
            "Private typed event reaction",
            mapOf(
                "id" to id,
                "on" to subscription,
                "if" to ConditionSchema.description,
                "once" to flag,
                "per_player" to flag,
                "cooldown" to ConfigSchemas.duration("Rule cooldown", true).description,
                "do" to entries("Ordered actions", action, 1),
            ),
            "id",
            "on",
            "do",
        )
    val layers =
        record(
            "Private mechanic scope",
            mapOf(
                "objectives" to
                    entries(
                        "Required objectives",
                        either("Mechanic or condition", conditionObjective, child),
                    ),
                "mechanics" to entries("Background mechanics", child),
                "counters" to entries("Private counters", counter),
                "timers" to entries("Private timers", timer),
                "rules" to entries("Private rules", rule),
                "complete_when" to ConditionSchema.description,
                "fail_when" to ConditionSchema.description,
                "duration" to duration,
                "deadline" to duration,
            ),
        )
}
