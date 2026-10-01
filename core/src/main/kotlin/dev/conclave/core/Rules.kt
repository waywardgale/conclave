package dev.conclave.core

enum class RuleSourceKind {
    SELF,
    MECHANIC,
    TIMER,
    PHASE,
    PLAYERS,
}

data class RuleSource(
    val kind: RuleSourceKind,
    val reference: StateReference? = null,
    val players: PlayerSelection? = null,
)

sealed interface IntegerOperand {
    data class Literal(val value: Long) : IntegerOperand

    data class Event(val field: String) : IntegerOperand

    fun resolve(payload: EventPayload): Long =
        when (this) {
            is Literal -> value
            is Event -> (payload.values.getValue(field) as EventDatum.Integer).value
        }
}

sealed interface RuleAction {
    val target: StateReference

    data class Counter(
        override val target: StateReference,
        val mutation: CounterMutation,
        val value: IntegerOperand = IntegerOperand.Literal(0),
    ) : RuleAction

    data class Timer(override val target: StateReference, val mutation: TimerMutation) : RuleAction
}

class RuleDefinition(
    val id: String,
    val source: RuleSource,
    val event: String,
    val contract: EventContract,
    val guard: Condition?,
    val once: Boolean,
    val cooldown: SimulationDuration,
    val perPlayer: Boolean,
    val playerField: String?,
    actions: List<RuleAction>,
) {
    val actions: List<RuleAction> = java.util.List.copyOf(actions)

    init {
        require(isAuthoredName(id) && actions.isNotEmpty() && (!perPlayer || playerField != null))
    }
}

data class EventSubscription(
    val source: RuleSource,
    val event: String,
    val contract: EventContract,
    val playerField: String?,
)

internal class UnboundEncounterEvent : RuntimeException()

internal class RuleCompiler(
    private val local: ScopeDefinition,
    private val encounter: ScopeDefinition,
    private val context: SchemaContext,
    private val deferEncounter: Boolean = false,
) {
    fun compile(value: YamlValue): RuleDefinition {
        val fields = Fields(value.mapping())
        val id = ConfigSchemas.identifier("Rule ID").decode(fields.required("id"), context)
        val subscription = subscription(fields.required("on"))
        val source = subscription.source
        val event = subscription.event
        val narrowed = subscription.contract
        val player = subscription.playerField
        val guard =
            fields.optional("if")?.let {
                ConditionSchema.decode(it, context.copy(event = narrowed))
            }
        val once =
            fields.optional("once")?.let {
                ConfigSchemas.flag("Run once in this activation").decode(it, context)
            } ?: false
        val cooldown =
            fields.optional("cooldown")?.let {
                ConfigSchemas.duration("Rule cooldown", true).decode(it, context)
            } ?: SimulationDuration(0)
        val perPlayer =
            fields.optional("per_player")?.let {
                ConfigSchemas.flag("Separate invocation limits for each triggering player")
                    .decode(it, context)
            } ?: false
        if (perPlayer && player == null)
            invalid(
                "triggering_player",
                "This event has no guaranteed triggering player; use on.player where supported",
                value.source,
            )
        val actionsNode = fields.required("do")
        val actions = actionsNode.sequence().map { action(it, narrowed) }
        if (actions.isEmpty() || actions.size > 256)
            invalid("action_count", "Use between 1 and 256 actions", actionsNode.source)
        fields.finish()
        return RuleDefinition(
            id,
            source,
            event,
            narrowed,
            guard,
            once,
            cooldown,
            perPlayer,
            player,
            actions,
        )
    }

    fun subscription(value: YamlValue): EventSubscription {
        val on = Fields(value.mapping())
        val source = source(on.required("source"))
        val eventNode = on.required("event")
        val event = eventNode.text()
        val contract = contract(source, event, eventNode.source)
        val requestedPlayer = on.optional("player")
        val player = requestedPlayer?.text() ?: contract.triggeringPlayer
        if (player != null && contract.fields[player]?.kind != EventValueKind.PLAYER)
            invalid(
                "event_player",
                "Choose a player identity supplied by this event",
                requestedPlayer?.source ?: eventNode.source,
            )
        on.finish()
        val narrowed =
            if (requestedPlayer != null)
                EventContract(
                    contract.fields +
                        (checkNotNull(player) to
                            contract.fields.getValue(player).copy(required = true)),
                    player,
                )
            else contract
        return EventSubscription(source, event, narrowed, player)
    }

    private fun source(value: YamlValue): RuleSource {
        if (value is YamlValue.Text) {
            if (value.value != "self")
                invalid("event_source", "The only standalone source is self", value.source)
            return RuleSource(RuleSourceKind.SELF)
        }
        val (kind, reference) = singleField(value)
        if (kind == "players")
            return RuleSource(
                RuleSourceKind.PLAYERS,
                players = LifecycleSelectionSchema.decode(reference, context),
            )
        val type =
            when (kind) {
                "mechanic" -> RuleSourceKind.MECHANIC
                "timer" -> RuleSourceKind.TIMER
                "phase" -> RuleSourceKind.PHASE
                else ->
                    invalid(
                        "capability_unavailable",
                        "Event source '$kind' is not installed in this build",
                        value.source,
                    )
            }
        if (type == RuleSourceKind.PHASE) {
            val id =
                ConfigSchemas.identifier("A phase declared in this encounter")
                    .decode(reference, context)
            if (id !in context.phases)
                invalid(
                    "phase_source",
                    "Named phases can only be observed from their encounter's rules",
                    reference.source,
                )
            return RuleSource(type, StateReference(id))
        }
        return RuleSource(type, StateReferenceSchema.decode(reference, context))
    }

    private fun owner(ref: StateReference) =
        if (ref.scope == StateScope.ENCOUNTER) encounter else local

    private fun contract(source: RuleSource, event: String, at: SourceLocation): EventContract {
        return when (source.kind) {
            RuleSourceKind.SELF -> if (event == "started") EventContract(emptyMap()) else null
            RuleSourceKind.MECHANIC -> {
                val ref = checkNotNull(source.reference)
                if (deferEncounter && ref.scope == StateScope.ENCOUNTER)
                    throw UnboundEncounterEvent()
                val producer =
                    owner(ref).allMechanics.firstOrNull { it.id == ref.id }
                        ?: invalid("unknown_mechanic", "Rule source mechanic is not declared", at)
                producer.mechanic.events[event]
            }
            RuleSourceKind.TIMER -> {
                val ref = checkNotNull(source.reference)
                if (
                    !(deferEncounter && ref.scope == StateScope.ENCOUNTER) &&
                        owner(ref).timers.none { it.id == ref.id }
                )
                    invalid("unknown_timer", "Rule source timer is not declared", at)
                if (event == "expired")
                    EventContract(
                        mapOf(
                            "timer" to EventField(EventValueKind.TIMER),
                            "duration" to EventField(EventValueKind.DURATION),
                        )
                    )
                else null
            }
            RuleSourceKind.PHASE -> phaseEventContract(event)
            RuleSourceKind.PLAYERS -> participantEventContract(event)
        } ?: invalid("event_unavailable", "Event '$event' is not declared by this source", at)
    }

    private fun action(value: YamlValue, event: EventContract): RuleAction {
        val (name, node) = singleField(value)
        val fields = Fields(node.mapping())
        val counter =
            when (name) {
                "set_counter" -> CounterMutation.SET
                "add_counter" -> CounterMutation.ADD
                "reset_counter" -> CounterMutation.RESET
                else -> null
            }
        val timer =
            when (name) {
                "start_timer" -> TimerMutation.START
                "restart_timer" -> TimerMutation.RESTART
                "pause_timer" -> TimerMutation.PAUSE
                "resume_timer" -> TimerMutation.RESUME
                "stop_timer" -> TimerMutation.STOP
                else -> null
            }
        val result =
            when {
                counter != null -> {
                    val reference = StateReferenceSchema.decode(fields.required("counter"), context)
                    if (
                        !(deferEncounter && reference.scope == StateScope.ENCOUNTER) &&
                            !owner(reference).declares(ReferenceKind.COUNTER, reference.id)
                    )
                        invalid(
                            "unknown_counter",
                            "Action counter is not declared in its selected scope",
                            node.source,
                        )
                    val number =
                        if (counter == CounterMutation.RESET) IntegerOperand.Literal(0)
                        else integer(fields.required("value"), event)
                    RuleAction.Counter(reference, counter, number)
                }
                timer != null -> {
                    val reference = StateReferenceSchema.decode(fields.required("timer"), context)
                    if (
                        !(deferEncounter && reference.scope == StateScope.ENCOUNTER) &&
                            !owner(reference).declares(ReferenceKind.TIMER, reference.id)
                    )
                        invalid(
                            "unknown_timer",
                            "Action timer is not declared in its selected scope",
                            node.source,
                        )
                    RuleAction.Timer(reference, timer)
                }
                else ->
                    invalid(
                        "capability_unavailable",
                        "Action '$name' is not installed in this build",
                        value.source,
                    )
            }
        fields.finish()
        return result
    }

    private fun integer(value: YamlValue, contract: EventContract): IntegerOperand {
        if (value is YamlValue.Number) return IntegerOperand.Literal(value.integer())
        val (kind, node) = singleField(value)
        if (kind != "event")
            invalid("event_reference", "Use an integer or a whole event field", value.source)
        val field = node.text()
        if (
            contract.fields[field]?.let { it.kind == EventValueKind.INTEGER && it.required } != true
        )
            invalid("event_reference", "Event field must be a required integer", node.source)
        return IntegerOperand.Event(field)
    }
}

internal fun phaseEventContract(event: String): EventContract? =
    when (event) {
        "started" -> EventContract(emptyMap())
        "completed",
        "failed" ->
            EventContract(
                mapOf(
                    "elapsed" to EventField(EventValueKind.DURATION),
                    "route" to
                        EventField(
                            EventValueKind.STRING,
                            choices =
                                if (event == "completed") setOf("next", "complete")
                                else setOf("next", "wipe"),
                        ),
                    "next_phase" to EventField(EventValueKind.STRING, required = false),
                ) +
                    if (event == "failed")
                        mapOf(
                            "reason" to
                                EventField(
                                    EventValueKind.STRING,
                                    choices =
                                        PhaseFailureReason.entries
                                            .map { it.name.lowercase() }
                                            .toSet(),
                                )
                        )
                    else emptyMap()
            )
        else -> null
    }

internal fun RuleDefinition.canonical(): String {
    val sourceJson = source.canonical()
    fun operand(value: IntegerOperand): String =
        when (value) {
            is IntegerOperand.Literal -> value.value.toString()
            is IntegerOperand.Event -> "{\"event\":${jsonString(value.field)}}"
        }
    val actionsJson =
        actions.joinToString(",", "[", "]") { action ->
            when (action) {
                is RuleAction.Counter ->
                    "{${jsonString(action.mutation.name.lowercase() + "_counter")}: {\"counter\":${StateReferenceSchema.encode(action.target)}" +
                        (if (action.mutation != CounterMutation.RESET)
                            ",\"value\":${operand(action.value)}"
                        else "") +
                        "}}"
                is RuleAction.Timer ->
                    "{${jsonString(action.mutation.name.lowercase() + "_timer")}: {\"timer\":${StateReferenceSchema.encode(action.target)}}}"
            }
        }
    return "{\"id\":${jsonString(id)},\"on\":{\"source\":$sourceJson,\"event\":${jsonString(event)}" +
        (playerField?.let { ",\"player\":${jsonString(it)}" } ?: "") +
        "},\"if\":${guard?.let(ConditionSchema::encode) ?: "null"},\"once\":$once,\"cooldown\":${cooldown.ticks},\"per_player\":$perPlayer,\"do\":$actionsJson}"
}

internal fun RuleSource.canonical(): String =
    if (kind == RuleSourceKind.SELF) "\"self\""
    else if (kind == RuleSourceKind.PLAYERS)
        "{\"players\":${LifecycleSelectionSchema.encode(checkNotNull(players))}}"
    else
        "{${jsonString(kind.name.lowercase())}:${StateReferenceSchema.encode(checkNotNull(reference))}}"
