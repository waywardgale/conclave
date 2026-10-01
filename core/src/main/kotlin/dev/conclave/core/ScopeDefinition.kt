package dev.conclave.core

data class MechanicOccurrence(val id: String, val name: String?, val mechanic: CompiledMechanic) {
    init {
        require(isAuthoredName(id))
    }
}

sealed interface ObjectiveDefinition {
    val id: String

    data class Mechanic(val occurrence: MechanicOccurrence) : ObjectiveDefinition {
        override val id
            get() = occurrence.id
    }

    data class Check(
        override val id: String,
        val name: String?,
        val condition: Condition,
        val latch: Boolean = false,
    ) : ObjectiveDefinition {
        init {
            require(isAuthoredName(id))
        }
    }
}

class ScopeDefinition(
    objectives: List<ObjectiveDefinition> = emptyList(),
    mechanics: List<MechanicOccurrence> = emptyList(),
    counters: List<CounterDefinition> = emptyList(),
    timers: List<TimerDefinition> = emptyList(),
    val completeWhen: Condition? = null,
    val failWhen: Condition? = null,
    rules: List<RuleDefinition> = emptyList(),
) {
    val objectives: List<ObjectiveDefinition> = java.util.List.copyOf(objectives)
    val mechanics: List<MechanicOccurrence> = java.util.List.copyOf(mechanics)
    val counters: List<CounterDefinition> = java.util.List.copyOf(counters)
    val timers: List<TimerDefinition> = java.util.List.copyOf(timers)
    val rules: List<RuleDefinition> = java.util.List.copyOf(rules)
    val allMechanics: List<MechanicOccurrence> =
        java.util.List.copyOf(
            objectives.filterIsInstance<ObjectiveDefinition.Mechanic>().map { it.occurrence } +
                mechanics
        )

    init {
        require(objectives.map { it.id }.distinct().size == objectives.size)
        require(allMechanics.map { it.id }.distinct().size == allMechanics.size)
        require(
            counters.map { it.id }.distinct().size == counters.size &&
                timers.map { it.id }.distinct().size == timers.size
        )
        require(rules.map { it.id }.distinct().size == rules.size)
    }

    fun declares(kind: ReferenceKind, id: String) =
        when (kind) {
            ReferenceKind.MECHANIC -> allMechanics.any { it.id == id }
            ReferenceKind.OBJECTIVE -> objectives.any { it.id == id }
            ReferenceKind.COUNTER -> counters.any { it.id == id }
            ReferenceKind.TIMER -> timers.any { it.id == id }
        }
}

internal class ScopePlan(
    val content: ScopeDefinition,
    rules: List<YamlValue>,
    private val context: SchemaContext,
    private val source: SourceLocation,
) {
    private val rules = java.util.List.copyOf(rules)

    fun validateLocals(compiler: ScopeCompiler): ScopeDefinition {
        val ids = rules.map {
            ConfigSchemas.identifier("Rule ID").decode(Fields(it.mapping()).required("id"), context)
        }
        if (ids.distinct().size != ids.size)
            invalid("duplicate_rule", "Rule IDs must be unique in their scope", source)
        val ruleCompiler = RuleCompiler(content, ScopeDefinition(), context, deferEncounter = true)
        val localRules = rules.mapNotNull {
            try {
                ruleCompiler.compile(it)
            } catch (_: UnboundEncounterEvent) {
                null
            }
        }
        val checked =
            ScopeDefinition(
                content.objectives,
                content.mechanics,
                content.counters,
                content.timers,
                content.completeWhen,
                content.failWhen,
                localRules,
            )
        compiler.validate(checked, ScopeDefinition(), source, checkEncounter = false)
        return checked
    }

    fun link(encounter: ScopeDefinition): ScopeDefinition {
        fun bind(occurrence: MechanicOccurrence) =
            occurrence.copy(mechanic = occurrence.mechanic.bindScope(encounter))
        val base =
            ScopeDefinition(
                content.objectives.map {
                    if (it is ObjectiveDefinition.Mechanic)
                        ObjectiveDefinition.Mechanic(bind(it.occurrence))
                    else it
                },
                content.mechanics.map(::bind),
                content.counters,
                content.timers,
                content.completeWhen,
                content.failWhen,
            )
        val compiler = RuleCompiler(base, encounter, context)
        val linked = rules.map(compiler::compile)
        if (linked.map { it.id }.distinct().size != linked.size)
            invalid("duplicate_rule", "Rule IDs must be unique in their scope", source)
        return ScopeDefinition(
            base.objectives,
            base.mechanics,
            base.counters,
            base.timers,
            base.completeWhen,
            base.failWhen,
            linked,
        )
    }
}

internal class ScopeCompiler(
    private val occurrenceCompiler: (YamlValue, SchemaContext) -> MechanicOccurrence
) {
    constructor(reusable: ReusableMechanics) : this(reusable::occurrence)

    fun prepare(fields: Fields, context: SchemaContext): ScopePlan = declarations(fields, context)

    fun compile(
        fields: Fields,
        context: SchemaContext,
        encounter: ScopeDefinition? = null,
    ): ScopeDefinition {
        val plan = declarations(fields, context)
        return plan.link(encounter ?: plan.content)
    }

    private fun declarations(fields: Fields, context: SchemaContext): ScopePlan {
        fun entries(name: String): List<YamlValue> {
            val value = fields.optional(name) ?: return emptyList()
            val entries = value.sequence()
            if (entries.size > 256)
                invalid("scope_entry_limit", "Use at most 256 $name in one scope", value.source)
            return entries
        }
        val objectives = entries("objectives").map { objective(it, context) }
        val mechanics = entries("mechanics").map { occurrence(it, context) }
        val counters = entries("counters").map { counter(it, context) }
        val timers = entries("timers").map { timer(it, context) }
        val complete = fields.optional("complete_when")?.let { ConditionSchema.decode(it, context) }
        val fail = fields.optional("fail_when")?.let { ConditionSchema.decode(it, context) }
        unique(objectives.map { it.id }, "objective", fields.node.source)
        unique(
            objectives.filterIsInstance<ObjectiveDefinition.Mechanic>().map { it.id } +
                mechanics.map { it.id },
            "mechanic",
            fields.node.source,
        )
        unique(counters.map { it.id }, "counter", fields.node.source)
        unique(timers.map { it.id }, "timer", fields.node.source)
        val base = ScopeDefinition(objectives, mechanics, counters, timers, complete, fail)
        return ScopePlan(base, entries("rules"), context, fields.node.source)
    }

    fun validate(
        scope: ScopeDefinition,
        encounter: ScopeDefinition,
        source: SourceLocation,
        additional: List<Condition> = emptyList(),
        checkEncounter: Boolean = true,
    ) {
        scope.objectives.filterIsInstance<ObjectiveDefinition.Mechanic>().forEach {
            if (!it.occurrence.mechanic.canComplete)
                invalid(
                    "impossible_objective",
                    "This mechanic has no natural success; put it under mechanics",
                    source,
                )
        }
        for (occurrence in scope.allMechanics) {
            occurrence.mechanic.layers?.let {
                validate(
                    it.content,
                    encounter,
                    source,
                    occurrence.mechanic.exports.mapNotNull { export -> export.guard },
                    checkEncounter,
                )
            }
            val composition = occurrence.mechanic.composition ?: continue
            validate(
                ScopeDefinition(mechanics = composition.steps),
                encounter,
                source,
                listOfNotNull(composition.until) +
                    occurrence.mechanic.exports.mapNotNull { it.guard },
                checkEncounter,
            )
        }
        val conditions =
            scope.objectives.filterIsInstance<ObjectiveDefinition.Check>().map { it.condition } +
                listOfNotNull(scope.completeWhen, scope.failWhen) +
                scope.rules.mapNotNull { it.guard } +
                additional
        for (condition in conditions) for ((kind, ref) in condition.references()) {
            if (!checkEncounter && ref.scope == StateScope.ENCOUNTER) continue
            val owner = if (ref.scope == StateScope.ENCOUNTER) encounter else scope
            if (!owner.declares(kind, ref.id))
                invalid(
                    "unknown_${kind.name.lowercase()}",
                    "${kind.name.lowercase()} '${ref.id}' is not declared in its selected scope",
                    source,
                )
        }
        val checks =
            scope.objectives.filterIsInstance<ObjectiveDefinition.Check>().associateBy { it.id }
        val visiting = mutableSetOf<String>()
        val checked = mutableSetOf<String>()
        fun visit(id: String) {
            if (id in checked) return
            if (!visiting.add(id))
                invalid(
                    "objective_cycle",
                    "Condition objectives cannot recursively depend on one another",
                    source,
                )
            checks[id]
                ?.condition
                ?.references()
                ?.filter {
                    it.first == ReferenceKind.OBJECTIVE &&
                        (it.second.scope == StateScope.LOCAL || scope === encounter)
                }
                ?.forEach { visit(it.second.id) }
            visiting.remove(id)
            checked += id
        }
        checks.keys.forEach(::visit)
    }

    private fun objective(value: YamlValue, context: SchemaContext): ObjectiveDefinition {
        if ("condition" !in value.mapping().entries)
            return ObjectiveDefinition.Mechanic(occurrence(value, context))
        val fields = Fields(value.mapping())
        val id = local(fields.required("id"), context)
        val name =
            fields.optional("name")?.let { ConfigSchemas.text("Display name").decode(it, context) }
        val condition = ConditionSchema.decode(fields.required("condition"), context)
        val latch =
            fields.optional("latch")?.let {
                ConfigSchemas.flag("Retain satisfaction").decode(it, context)
            } ?: false
        fields.finish()
        return ObjectiveDefinition.Check(id, name, condition, latch)
    }

    private fun occurrence(value: YamlValue, context: SchemaContext): MechanicOccurrence =
        occurrenceCompiler(value, context)

    private fun counter(value: YamlValue, context: SchemaContext): CounterDefinition {
        val fields = Fields(value.mapping())
        val id = local(fields.required("id"), context)
        val name =
            fields.optional("name")?.let { ConfigSchemas.text("Display name").decode(it, context) }
        val initial = fields.optional("initial")?.integer() ?: 0
        val minimum = fields.optional("min")?.integer() ?: Long.MIN_VALUE
        val maximum = fields.optional("max")?.integer() ?: Long.MAX_VALUE
        fields.finish()
        if (minimum > maximum || initial !in minimum..maximum)
            invalid(
                "counter_bounds",
                "Counter initial value must be within its declared min and max",
                value.source,
            )
        return CounterDefinition(id, name, initial, minimum, maximum)
    }

    private fun timer(value: YamlValue, context: SchemaContext): TimerDefinition {
        val fields = Fields(value.mapping())
        val id = local(fields.required("id"), context)
        val name =
            fields.optional("name")?.let { ConfigSchemas.text("Display name").decode(it, context) }
        val duration =
            ConfigSchemas.duration("Positive timer duration")
                .decode(fields.required("duration"), context)
        val auto =
            fields.optional("auto_start")?.let {
                ConfigSchemas.flag("Start on scope activation").decode(it, context)
            } ?: true
        fields.finish()
        return TimerDefinition(id, duration, name, auto)
    }

    private fun local(value: YamlValue, context: SchemaContext) =
        ConfigSchemas.identifier("Local ID").decode(value, context)

    private fun unique(ids: List<String>, kind: String, source: SourceLocation) {
        if (ids.distinct().size != ids.size)
            invalid(
                "duplicate_$kind",
                "${kind.replaceFirstChar { it.uppercase() }} IDs must be unique in their scope",
                source,
            )
    }
}

internal fun ScopeDefinition.canonical(): String {
    fun occurrence(value: MechanicOccurrence) =
        "{\"id\":${jsonString(value.id)},\"name\":${value.name?.let(::jsonString) ?: "null"},\"type\":${jsonString(value.mechanic.type.toString())},\"config\":${value.mechanic.canonical}}"
    return "{" +
        listOf(
                "\"objectives\":" +
                    objectives.joinToString(",", "[", "]") {
                        when (it) {
                            is ObjectiveDefinition.Mechanic -> occurrence(it.occurrence)
                            is ObjectiveDefinition.Check ->
                                "{\"id\":${jsonString(it.id)},\"name\":${it.name?.let(::jsonString) ?: "null"},\"condition\":${ConditionSchema.encode(it.condition)},\"latch\":${it.latch}}"
                        }
                    },
                "\"mechanics\":" + mechanics.joinToString(",", "[", "]", transform = ::occurrence),
                "\"counters\":" +
                    counters.joinToString(",", "[", "]") {
                        "{\"id\":${jsonString(it.id)},\"name\":${it.name?.let(::jsonString) ?: "null"},\"initial\":${it.initial},\"min\":${it.minimum},\"max\":${it.maximum}}"
                    },
                "\"timers\":" +
                    timers.joinToString(",", "[", "]") {
                        "{\"id\":${jsonString(it.id)},\"name\":${it.name?.let(::jsonString) ?: "null"},\"duration\":${it.duration.ticks},\"auto_start\":${it.autoStart}}"
                    },
                "\"complete_when\":" + (completeWhen?.let(ConditionSchema::encode) ?: "null"),
                "\"fail_when\":" + (failWhen?.let(ConditionSchema::encode) ?: "null"),
                "\"rules\":" +
                    rules.joinToString(",", "[", "]", transform = RuleDefinition::canonical),
            )
            .joinToString(",") +
        "}"
}
