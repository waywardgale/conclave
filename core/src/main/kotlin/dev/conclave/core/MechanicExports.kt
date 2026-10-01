package dev.conclave.core

sealed interface ExportValue {
    data class Field(val name: String) : ExportValue

    data class Literal(val value: EventDatum) : ExportValue
}

class MechanicExport(
    val id: String,
    val subscription: EventSubscription,
    val guard: Condition?,
    data: Map<String, ExportValue>,
    val contract: EventContract,
) {
    val data: Map<String, ExportValue> = java.util.Collections.unmodifiableMap(LinkedHashMap(data))

    internal fun payload(source: EventPayload): EventPayload =
        EventPayload(
            contract,
            buildMap {
                for ((name, mapping) in data) {
                    val value =
                        when (mapping) {
                            is ExportValue.Field -> source.values[mapping.name]
                            is ExportValue.Literal -> mapping.value
                        }
                    if (value != null) put(name, value)
                }
            },
        )

    internal fun canonical(): String =
        "{\"id\":${jsonString(id)},\"source\":${subscription.source.canonical()},\"event\":${jsonString(subscription.event)},\"player\":${subscription.playerField?.let(::jsonString) ?: "null"},\"if\":${guard?.let(ConditionSchema::encode) ?: "null"},\"data\":" +
            data.entries.joinToString(",", "{", "}") { (name, value) ->
                jsonString(name) +
                    ":" +
                    when (value) {
                        is ExportValue.Field -> "{\"event\":${jsonString(value.name)}}"
                        is ExportValue.Literal ->
                            "{\"kind\":${jsonString(value.value.kind.name)},\"value\":${eventLiteral(value.value)}}"
                    }
            } +
            "}"
}

internal class ExportedMechanic(private val body: CompiledMechanic, exports: List<MechanicExport>) :
    CompiledMechanic by body {
    override val exports: List<MechanicExport> = java.util.List.copyOf(exports)
    override val events =
        java.util.Map.copyOf(body.events + exports.associate { it.id to it.contract })
    override val canonical =
        "{\"body\":${body.canonical},\"export\":" +
            exports.joinToString(",", "[", "]", transform = MechanicExport::canonical) +
            "}"
    override val spatialReferences =
        exports.fold(body.spatialReferences) { references, export ->
            references +
                (export.guard?.spatialReferences() ?: SpatialReferences.EMPTY) +
                (export.subscription.source.players?.spatialReferences() ?: SpatialReferences.EMPTY)
        }

    override fun bindScope(encounter: ScopeDefinition): CompiledMechanic =
        ExportedMechanic(body.bindScope(encounter), exports)
}

internal object MechanicExports {
    private val reserved = setOf("started", "completed", "failed")
    private val provenance = setOf("attempt", "activation", "operation", "generation", "ordering")
    private val literalParameters =
        setOf("boolean", "integer", "number", "string", "enum", "duration")

    fun attach(
        value: YamlValue?,
        body: CompiledMechanic,
        context: SchemaContext,
    ): CompiledMechanic {
        if (value == null) return body
        val scope =
            body.layers?.content
                ?: body.composition?.let { ScopeDefinition(mechanics = it.steps) }
                ?: invalid(
                    "export_scope",
                    "Public forwarding requires a composition or layers body",
                    value.source,
                )
        val compiler = RuleCompiler(scope, ScopeDefinition(), context.copy(phases = emptySet()))
        val exports =
            entries(value).map { (id, entry) ->
                val fields = Fields(entry.mapping())
                val subscription = compiler.subscription(fields.required("on"))
                if (subscription.source.reference?.scope == StateScope.ENCOUNTER)
                    invalid(
                        "export_source",
                        "Forward a source inside this occurrence",
                        entry.source,
                    )
                val guard =
                    fields.optional("if")?.let {
                        ConditionSchema.decode(it, context.copy(event = subscription.contract))
                    }
                val data =
                    data(fields.optional("data")).mapValues { (_, node) ->
                        if (node is YamlValue.Mapping) ExportValue.Field(eventField(node))
                        else ExportValue.Literal(literal(node))
                    }
                fields.finish()
                val contract =
                    EventContract(
                        data.mapValues { (_, mapping) ->
                            when (mapping) {
                                is ExportValue.Literal -> EventField(mapping.value.kind)
                                is ExportValue.Field -> {
                                    val field =
                                        subscription.contract.fields[mapping.name]
                                            ?: invalid(
                                                "export_field",
                                                "Source event does not declare '${mapping.name}'",
                                                entry.source,
                                            )
                                    if (field.kind == EventValueKind.TIMER)
                                        invalid(
                                            "export_lifetime",
                                            "Private timer handles cannot leave their activation",
                                            entry.source,
                                        )
                                    field
                                }
                            }
                        },
                        data.entries
                            .firstOrNull { (_, mapping) ->
                                mapping is ExportValue.Field &&
                                    mapping.name == subscription.playerField &&
                                    subscription.contract.fields[mapping.name]?.let {
                                        it.required && it.kind == EventValueKind.PLAYER
                                    } == true
                            }
                            ?.key,
                    )
                MechanicExport(id, subscription, guard, data, contract)
            }
        ScopeCompiler(checkNotNull(context.mechanicCompiler))
            .validate(
                scope,
                ScopeDefinition(),
                value.source,
                exports.mapNotNull { it.guard },
                checkEncounter = false,
            )
        return ExportedMechanic(body, exports)
    }

    fun validateTemplate(
        value: YamlValue?,
        parameters: Map<String, MechanicParameter>,
        budget: ExpansionBudget,
    ) {
        if (value == null) return
        for ((_, entry) in entries(value)) {
            val fields = Fields(entry.mapping())
            MechanicTemplateSchema(ScopeDescriptions.subscription, budget)
                .validate(fields.required("on"), parameters)
            fields.optional("if")?.let {
                MechanicTemplateSchema(ConditionSchema.description, budget).validate(it, parameters)
            }
            for (node in data(fields.optional("data")).values) {
                budget.visit(node)
                val parameter = parameterName(node)
                if (parameter != null) {
                    val kind =
                        parameters[parameter]?.type
                            ?: invalid(
                                "unknown_parameter",
                                "Parameter '$parameter' is not declared",
                                node.source,
                            )
                    if (kind !in literalParameters)
                        invalid(
                            "export_parameter",
                            "This parameter kind has no supported public event value: $kind",
                            node.source,
                        )
                } else if (node is YamlValue.Mapping) eventField(node) else literal(node)
            }
            fields.finish()
        }
    }

    private fun entries(value: YamlValue): Map<String, YamlValue> {
        val fields = Fields(value.mapping())
        val events = fields.required("events").mapping().entries
        fields.finish()
        if (events.size !in 1..128)
            invalid("export_limit", "Declare 1 to 128 exported events", value.source)
        for ((id, node) in events) {
            if (!isAuthoredName(id) || id in reserved)
                invalid(
                    "export_name",
                    "Use a local event name other than started, completed or failed",
                    node.source,
                )
        }
        return events
    }

    private fun data(value: YamlValue?): Map<String, YamlValue> {
        val fields = value?.mapping()?.entries ?: return emptyMap()
        if (fields.size > 128)
            invalid("export_limit", "Map at most 128 public fields", value.source)
        for ((id, node) in fields) if (!isAuthoredName(id) || id in provenance)
            invalid(
                "export_field",
                "Use a local field name; engine provenance cannot be overwritten",
                node.source,
            )
        return fields
    }

    private fun eventField(value: YamlValue): String {
        val fields = Fields(value.mapping())
        val name =
            ConfigSchemas.identifier("A flat source event field").decode(fields.required("event"))
        fields.finish()
        return name
    }

    private fun literal(value: YamlValue): EventDatum {
        val type = value.parameterType
        if (type != null && type !in literalParameters)
            invalid(
                "export_parameter",
                "This parameter kind has no supported public event value: $type",
                value.source,
            )
        if (type == "duration")
            return EventDatum.Duration(
                ConfigSchemas.duration("Exported duration", true).decode(value)
            )
        return when (value) {
            is YamlValue.Text ->
                EventDatum.Text(ConfigSchemas.text("Public text value", 2048).decode(value))
            is YamlValue.Flag -> EventDatum.Flag(value.value)
            is YamlValue.Number ->
                if (value.integer && type != "number") EventDatum.Integer(value.integer())
                else EventDatum.Number(value.value)
            else ->
                invalid(
                    "export_value",
                    "Use a scalar literal, bound scalar parameter or whole event field",
                    value.source,
                )
        }
    }
}
