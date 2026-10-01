package dev.conclave.core

enum class StateScope {
    LOCAL,
    ENCOUNTER,
}

data class StateReference(val id: String, val scope: StateScope = StateScope.LOCAL) {
    init {
        require(isAuthoredName(id))
    }
}

enum class TimerStatus {
    IDLE,
    RUNNING,
    PAUSED,
    EXPIRED,
}

data class TimerObservation(val status: TimerStatus, val remaining: SimulationDuration)

/** One evaluation reads a fixed observation; predicates cannot mutate state or advance time. */
class ConditionFrame(
    val players: PlayerFrame,
    mechanics: Map<StateReference, MechanicState> = emptyMap(),
    objectives: Map<StateReference, Boolean> = emptyMap(),
    counters: Map<StateReference, Long> = emptyMap(),
    timers: Map<StateReference, TimerObservation> = emptyMap(),
    event: Map<String, EventDatum> = emptyMap(),
) {
    val mechanics: Map<StateReference, MechanicState> = java.util.Map.copyOf(mechanics)
    val objectives: Map<StateReference, Boolean> = java.util.Map.copyOf(objectives)
    val counters: Map<StateReference, Long> = java.util.Map.copyOf(counters)
    val timers: Map<StateReference, TimerObservation> = java.util.Map.copyOf(timers)
    val event: Map<String, EventDatum> = java.util.Map.copyOf(event)

    fun withEvent(payload: EventPayload) =
        ConditionFrame(players, mechanics, objectives, counters, timers, payload.values)
}

sealed interface Condition {
    fun test(frame: ConditionFrame): Boolean

    class And(children: List<Condition>) : Condition {
        val children = java.util.List.copyOf(children).also { require(it.isNotEmpty()) }

        override fun test(frame: ConditionFrame) = children.all { it.test(frame) }
    }

    class Or(children: List<Condition>) : Condition {
        val children = java.util.List.copyOf(children).also { require(it.isNotEmpty()) }

        override fun test(frame: ConditionFrame) = children.any { it.test(frame) }
    }

    data class Not(val child: Condition) : Condition {
        override fun test(frame: ConditionFrame) = !child.test(frame)
    }

    data class Completed(val mechanic: StateReference) : Condition {
        override fun test(frame: ConditionFrame) =
            frame.mechanics[mechanic] == MechanicState.SUCCEEDED
    }

    data class Satisfied(val objective: StateReference) : Condition {
        override fun test(frame: ConditionFrame) = frame.objectives.getValue(objective)
    }

    data class Counter(val counter: StateReference, val comparison: Comparison) : Condition {
        override fun test(frame: ConditionFrame) = comparison.test(frame.counters.getValue(counter))
    }

    data class Timer(
        val timer: StateReference,
        val status: TimerStatus? = null,
        val remaining: Comparison? = null,
    ) : Condition {
        init {
            require(status != null || remaining != null)
        }

        override fun test(frame: ConditionFrame): Boolean {
            val value = frame.timers.getValue(timer)
            return (status == null || value.status == status) &&
                (remaining == null || remaining.test(value.remaining.ticks))
        }
    }

    data class Players(
        val players: PlayerSelection,
        val satisfy: PlayerPredicate,
        val all: Boolean,
    ) : Condition {
        override fun test(frame: ConditionFrame): Boolean {
            val selected = players.select(frame.players)
            return if (all) allSelected(selected, satisfy) else anySelected(selected, satisfy)
        }
    }

    data class Count(val players: PlayerSelection, val comparison: Comparison) : Condition {
        override fun test(frame: ConditionFrame) =
            comparison.test(players.select(frame.players).size.toLong())
    }

    data class EventValue(
        val field: String,
        val present: Boolean? = null,
        val comparator: Comparator? = null,
        val expected: EventDatum? = null,
    ) : Condition {
        init {
            require((present != null) != (comparator != null && expected != null))
        }

        override fun test(frame: ConditionFrame): Boolean {
            val actual = frame.event[field]
            if (present != null) return present == (actual != null)
            if (actual == null) return false
            val comparison =
                when {
                    actual is EventDatum.Integer && expected is EventDatum.Integer ->
                        actual.value.compareTo(expected.value)
                    actual is EventDatum.Number && expected is EventDatum.Number ->
                        actual.value.compareTo(expected.value)
                    actual is EventDatum.Duration && expected is EventDatum.Duration ->
                        actual.value.ticks.compareTo(expected.value.ticks)
                    comparator == Comparator.EQUALS -> return actual == expected
                    else -> error("Incompatible event comparison")
                }
            return when (comparator) {
                Comparator.EQUALS -> comparison == 0
                Comparator.AT_LEAST -> comparison >= 0
                Comparator.AT_MOST -> comparison <= 0
                Comparator.GREATER_THAN -> comparison > 0
                Comparator.LESS_THAN -> comparison < 0
                null -> error("Missing comparison")
            }
        }
    }
}

object StateReferenceSchema : ConfigSchema<StateReference> {
    private val id = ConfigSchemas.identifier("Declared state in this scope")
    override val description =
        SchemaDescription(
            "",
            "Local state ID or an explicit encounter reference",
            alternatives =
                listOf(
                    id.description,
                    objectDescription(
                        "Encounter reference",
                        mapOf(
                            "id" to id.description,
                            "scope" to
                                SchemaDescription(
                                    "string",
                                    "Owning scope",
                                    choices = listOf("encounter"),
                                ),
                        ),
                    ),
                ),
        )

    override fun decode(value: YamlValue, context: SchemaContext): StateReference {
        if (value is YamlValue.Text) return StateReference(id.decode(value, context))
        val fields = Fields(value.mapping())
        val reference =
            StateReference(
                id.decode(fields.required("id"), context),
                scope(fields.required("scope")),
            )
        fields.finish()
        return reference
    }

    internal fun scope(value: YamlValue): StateScope {
        if (value.text() != "encounter")
            invalid("scope", "Explicit state scope must be encounter", value.source)
        return StateScope.ENCOUNTER
    }

    override fun encode(value: StateReference) =
        if (value.scope == StateScope.LOCAL) id.encode(value.id)
        else "{\"id\":${id.encode(value.id)},\"scope\":\"encounter\"}"
}

object ConditionSchema : ConfigSchema<Condition> {
    private val recursive = SchemaDescription("", "A condition", reference = "#/\$defs/condition")
    private val number = ConfigSchemas.integer("Whole number").description
    private val referenceProperties =
        mapOf(
            "id" to ConfigSchemas.identifier("Declared state ID").description,
            "scope" to
                SchemaDescription("string", "Encounter scope", choices = listOf("encounter")),
        )
    private val body by lazy {
        SchemaDescription(
            "",
            "A typed condition",
            alternatives =
                listOf(
                    variantDescription(
                        "and",
                        SchemaDescription(
                            "array",
                            "All conditions",
                            items = recursive,
                            minItems = 1,
                            maxItems = 256,
                        ),
                    ),
                    variantDescription(
                        "or",
                        SchemaDescription(
                            "array",
                            "At least one condition",
                            items = recursive,
                            minItems = 1,
                            maxItems = 256,
                        ),
                    ),
                    variantDescription("not", recursive),
                    variantDescription(
                        "completed",
                        variantDescription("mechanic", StateReferenceSchema.description),
                    ),
                    variantDescription(
                        "satisfied",
                        variantDescription("objective", StateReferenceSchema.description),
                    ),
                    variantDescription(
                        "counter",
                        SchemaDescription(
                            "",
                            "Counter comparison",
                            alternatives =
                                comparatorNames.keys.map { key ->
                                    objectDescription(
                                        "Compare a counter",
                                        referenceProperties + (key to number),
                                        setOf("id", key),
                                    )
                                },
                        ),
                    ),
                    variantDescription(
                        "timer",
                        objectDescription(
                            "Timer state",
                            referenceProperties +
                                mapOf(
                                    "state" to
                                        SchemaDescription(
                                            "string",
                                            "Timer state",
                                            choices =
                                                TimerStatus.entries.map { it.name.lowercase() },
                                        ),
                                    "remaining" to
                                        comparisonDescription(
                                            ConfigSchemas.duration(
                                                    "Remaining simulation time",
                                                    true,
                                                )
                                                .description
                                        ),
                                ),
                            setOf("id"),
                        ),
                    ),
                    variantDescription(
                        "any",
                        objectDescription(
                            "At least one selected player",
                            mapOf(
                                "players" to PlayerSelectionSchema.description,
                                "satisfy" to PlayerPredicateSchema.description,
                            ),
                        ),
                    ),
                    variantDescription(
                        "all",
                        objectDescription(
                            "Every player in a nonempty selection",
                            mapOf(
                                "players" to PlayerSelectionSchema.description,
                                "satisfy" to PlayerPredicateSchema.description,
                            ),
                        ),
                    ),
                    variantDescription(
                        "count",
                        SchemaDescription(
                            "",
                            "Count selected players",
                            alternatives =
                                comparatorNames.keys.map { key ->
                                    objectDescription(
                                        "Compare a player count",
                                        mapOf(
                                            "players" to PlayerSelectionSchema.description,
                                            key to number,
                                        ),
                                    )
                                },
                        ),
                    ),
                    variantDescription(
                        "event_value",
                        objectDescription(
                            "Compare a documented field in this rule's event contract",
                            mapOf(
                                "field" to
                                    ConfigSchemas.identifier("Documented event field").description,
                                "present" to
                                    ConfigSchemas.flag("Whether the field is present").description,
                            ) +
                                comparatorNames.keys.associateWith {
                                    SchemaDescription(
                                        "",
                                        "A literal matching the event field's type and units",
                                        alternatives =
                                            listOf(
                                                SchemaDescription("number", "Finite number"),
                                                SchemaDescription(
                                                    "string",
                                                    "String, enum, or duration",
                                                ),
                                                SchemaDescription("boolean", "Boolean"),
                                            ),
                                    )
                                },
                            setOf("field"),
                        ),
                    ),
                ),
        )
    }
    override val description
        get() = recursive.copy(definitions = mapOf("condition" to body))

    override fun decode(value: YamlValue, context: SchemaContext) = decode(value, context, 0)

    private fun decode(value: YamlValue, context: SchemaContext, depth: Int): Condition {
        if (depth > 32)
            invalid("condition_depth", "Condition nesting exceeds 32 levels", value.source)
        val (kind, node) = singleField(value)
        return when (kind) {
            "and",
            "or" -> {
                val list = node.sequence()
                if (list.isEmpty() || list.size > 256)
                    invalid("condition_count", "Use between 1 and 256 conditions", node.source)
                val children = list.map { decode(it, context, depth + 1) }
                if (kind == "and") Condition.And(children) else Condition.Or(children)
            }
            "not" -> Condition.Not(decode(node, context, depth + 1))
            "completed",
            "satisfied" -> {
                val fields = Fields(node.mapping())
                val ref =
                    StateReferenceSchema.decode(
                        fields.required(if (kind == "completed") "mechanic" else "objective"),
                        context,
                    )
                fields.finish()
                if (kind == "completed") Condition.Completed(ref) else Condition.Satisfied(ref)
            }
            "counter" -> {
                val fields = Fields(node.mapping())
                val reference = reference(fields)
                val comparison = comparison(fields)
                fields.finish()
                Condition.Counter(reference, comparison)
            }
            "timer" -> {
                val fields = Fields(node.mapping())
                val reference = reference(fields)
                val status =
                    fields.optional("state")?.let {
                        ConfigSchemas.choice(
                                "Timer state",
                                TimerStatus.entries.associateBy { it.name.lowercase() },
                            )
                            .decode(it, context)
                    }
                val remaining =
                    fields.optional("remaining")?.let {
                        comparisonValue(it) { time ->
                            ConfigSchemas.duration("Remaining time", true)
                                .decode(time, context)
                                .ticks
                        }
                    }
                fields.finish()
                if (status == null && remaining == null)
                    invalid("timer_condition", "Supply state or remaining", node.source)
                Condition.Timer(reference, status, remaining)
            }
            "any",
            "all" -> {
                val fields = Fields(node.mapping())
                val players = PlayerSelectionSchema.decode(fields.required("players"), context)
                val satisfy = PlayerPredicateSchema.decode(fields.required("satisfy"), context)
                fields.finish()
                Condition.Players(players, satisfy, kind == "all")
            }
            "count" -> {
                val fields = Fields(node.mapping())
                val players = PlayerSelectionSchema.decode(fields.required("players"), context)
                val count =
                    comparison(fields) {
                        it.integer().also { count ->
                            if (count < 0)
                                invalid("count", "Player count cannot be negative", it.source)
                        }
                    }
                fields.finish()
                Condition.Count(players, count)
            }
            "event_value" -> eventValue(node, context)
            else ->
                invalid(
                    "unknown_condition",
                    "Condition '$kind' is not registered in this build",
                    value.source.field(kind),
                )
        }
    }

    private fun eventValue(node: YamlValue, context: SchemaContext): Condition.EventValue {
        val fields = Fields(node.mapping())
        val fieldNode = fields.required("field")
        val name = ConfigSchemas.identifier("Documented event field").decode(fieldNode, context)
        val definition =
            context.event?.fields?.get(name)
                ?: invalid(
                    "event_field",
                    "Field is unavailable in this event context",
                    fieldNode.source,
                )
        val present =
            fields.optional("present")?.let {
                ConfigSchemas.flag("Field presence").decode(it, context)
            }
        val operators = fields.node.entries.keys.intersect(comparatorNames.keys)
        if (present != null) {
            if (operators.isNotEmpty())
                invalid("event_comparison", "Choose presence or one comparison", node.source)
            fields.finish()
            return Condition.EventValue(name, present)
        }
        if (operators.size != 1)
            invalid("event_comparison", "Supply exactly one comparison", node.source)
        val operator = operators.single()
        val expected = fields.required(operator)
        if (
            operator != "equals" &&
                definition.kind !in
                    setOf(EventValueKind.INTEGER, EventValueKind.NUMBER, EventValueKind.DURATION)
        )
            invalid("event_comparison", "This event field supports equality only", expected.source)
        val literal: EventDatum =
            when (definition.kind) {
                EventValueKind.INTEGER -> EventDatum.Integer(expected.integer())
                EventValueKind.NUMBER ->
                    EventDatum.Number(
                        (expected as? YamlValue.Number)?.value
                            ?: invalid(
                                "event_value_type",
                                "Expected a finite number",
                                expected.source,
                            )
                    )
                EventValueKind.BOOLEAN ->
                    EventDatum.Flag(ConfigSchemas.flag("Boolean").decode(expected, context))
                EventValueKind.STRING ->
                    EventDatum.Text(
                        expected.text().also {
                            if (definition.choices.isNotEmpty() && it !in definition.choices)
                                invalid(
                                    "event_enum",
                                    "Unregistered value for this event field",
                                    expected.source,
                                )
                        }
                    )
                EventValueKind.DURATION ->
                    EventDatum.Duration(
                        ConfigSchemas.duration("Simulation duration", true)
                            .decode(expected, context)
                    )
                EventValueKind.PLAYER,
                EventValueKind.TIMER ->
                    invalid(
                        "event_value_type",
                        "Typed identities support presence checks here",
                        expected.source,
                    )
            }
        fields.finish()
        return Condition.EventValue(
            name,
            comparator = comparatorNames.getValue(operator),
            expected = literal,
        )
    }

    private fun reference(fields: Fields): StateReference {
        val id = ConfigSchemas.identifier("Declared state ID").decode(fields.required("id"))
        val scope = fields.optional("scope")?.let(StateReferenceSchema::scope) ?: StateScope.LOCAL
        return StateReference(id, scope)
    }

    override fun encode(value: Condition): String =
        when (value) {
            is Condition.And ->
                "{\"and\":" + value.children.joinToString(",", "[", "]", transform = ::encode) + "}"
            is Condition.Or ->
                "{\"or\":" + value.children.joinToString(",", "[", "]", transform = ::encode) + "}"
            is Condition.Not -> "{\"not\":${encode(value.child)}}"
            is Condition.Completed ->
                "{\"completed\":{\"mechanic\":${StateReferenceSchema.encode(value.mechanic)}}}"
            is Condition.Satisfied ->
                "{\"satisfied\":{\"objective\":${StateReferenceSchema.encode(value.objective)}}}"
            is Condition.Counter ->
                "{\"counter\":{${referenceJson(value.counter)},${comparisonJson(value.comparison)}}}"
            is Condition.Timer ->
                "{\"timer\":{${referenceJson(value.timer)}" +
                    (value.status?.let { ",\"state\":${jsonString(it.name.lowercase())}" } ?: "") +
                    (value.remaining?.let {
                        ",\"remaining\":{${comparisonJson(it) { ticks -> ConfigSchemas.duration("Remaining", true).encode(SimulationDuration(ticks)) }}}"
                    } ?: "") +
                    "}}"
            is Condition.Players ->
                "{${jsonString(if (value.all) "all" else "any")}: {\"players\":${PlayerSelectionSchema.encode(value.players)},\"satisfy\":${PlayerPredicateSchema.encode(value.satisfy)}}}"
            is Condition.Count ->
                "{\"count\":{\"players\":${PlayerSelectionSchema.encode(value.players)},${comparisonJson(value.comparison)}}}"
            is Condition.EventValue ->
                "{\"event_value\":{\"field\":${jsonString(value.field)}," +
                    if (value.present != null) "\"present\":${value.present}}}"
                    else
                        jsonString(
                            comparatorNames.entries.single { it.value == value.comparator }.key
                        ) + ":${eventLiteral(checkNotNull(value.expected))}}}"
        }

    private fun referenceJson(value: StateReference) =
        "\"id\":${jsonString(value.id)}" +
            if (value.scope == StateScope.ENCOUNTER) ",\"scope\":\"encounter\"" else ""
}

internal fun eventLiteral(value: EventDatum): String =
    when (value) {
        is EventDatum.Integer -> value.value.toString()
        is EventDatum.Number -> value.value.stripTrailingZeros().toPlainString()
        is EventDatum.Flag -> value.value.toString()
        is EventDatum.Text -> jsonString(value.value)
        is EventDatum.Duration -> ConfigSchemas.duration("Duration", true).encode(value.value)
        is EventDatum.Player,
        is EventDatum.Timer -> error("Runtime identity is not an authored literal")
    }

enum class ReferenceKind {
    MECHANIC,
    OBJECTIVE,
    COUNTER,
    TIMER,
}

fun Condition.references(): Set<Pair<ReferenceKind, StateReference>> =
    when (this) {
        is Condition.And -> children.flatMap { it.references() }.toSet()
        is Condition.Or -> children.flatMap { it.references() }.toSet()
        is Condition.Not -> child.references()
        is Condition.Completed -> setOf(ReferenceKind.MECHANIC to mechanic)
        is Condition.Satisfied -> setOf(ReferenceKind.OBJECTIVE to objective)
        is Condition.Counter -> setOf(ReferenceKind.COUNTER to counter)
        is Condition.Timer -> setOf(ReferenceKind.TIMER to timer)
        is Condition.Players,
        is Condition.Count,
        is Condition.EventValue -> emptySet()
    }
