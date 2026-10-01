package dev.conclave.core

/** Validates symbolic configuration against the same descriptions used by concrete decoders. */
internal class MechanicTemplateSchema(
    private val root: SchemaDescription,
    private val budget: ExpansionBudget,
    private val occurrence: ((YamlValue, Map<String, MechanicParameter>) -> Unit)? = null,
) {
    private val definitions = linkedMapOf<String, SchemaDescription>()

    init {
        fun collect(node: SchemaDescription) {
            node.definitions.forEach { (id, definition) ->
                val old = definitions.putIfAbsent(id, definition)
                check(old == null || old == definition)
                if (old == null) collect(definition)
            }
            node.properties.values.forEach(::collect)
            node.items?.let(::collect)
            node.alternatives.forEach(::collect)
        }
        collect(root)
    }

    fun validate(value: YamlValue, parameters: Map<String, MechanicParameter>) {
        check(value, root, parameters, 0)
    }

    private fun check(
        value: YamlValue,
        schema: SchemaDescription,
        parameters: Map<String, MechanicParameter>,
        depth: Int,
    ) {
        budget.visit(value)
        if (depth > 64)
            invalid("template_depth", "Configuration exceeds 64 schema levels", value.source)
        if (schema.format == "mechanic_occurrence") {
            checkNotNull(occurrence) { "A child template requires its catalog linker" }(
                value,
                parameters,
            )
            return
        }
        val name = parameterName(value)
        if (name != null) {
            val parameter =
                parameters[name]
                    ?: invalid(
                        "unknown_parameter",
                        "Parameter '$name' is not declared",
                        value.source,
                    )
            if (!compatible(parameter.type, schema))
                invalid(
                    "parameter_type",
                    "Parameter '$name' of type '${parameter.type}' cannot supply this field",
                    value.source,
                )
            return
        }
        if (schema.reference != null) {
            val target =
                definitions[schema.reference.substringAfterLast('/')]
                    ?: error("Unregistered schema reference ${schema.reference}")
            check(value, target, parameters, depth + 1)
            return
        }
        if (schema.alternatives.isNotEmpty()) {
            val failures =
                schema.alternatives.map { alternative ->
                    try {
                        check(value, alternative, parameters, depth + 1)
                        return
                    } catch (failure: InvalidInput) {
                        failure
                    }
                }
            // Keep the most specific source position, especially a parameter nested in a variant.
            throw failures.maxBy { it.diagnostic.source.path.length }
        }
        when (schema.type) {
            "object" -> {
                val fields = value.mapping().entries
                fields.keys
                    .firstOrNull { it !in schema.properties }
                    ?.let {
                        invalid("unknown_field", "Unknown field '$it'", fields.getValue(it).source)
                    }
                schema.required
                    .firstOrNull { it !in fields }
                    ?.let {
                        invalid(
                            "missing_field",
                            "Required field '$it' is missing",
                            value.source.field(it),
                        )
                    }
                fields.forEach { (id, child) ->
                    check(child, schema.properties.getValue(id), parameters, depth + 1)
                }
            }
            "array" -> {
                val children = value.sequence()
                if (
                    children.size < (schema.minItems ?: 0) ||
                        children.size > (schema.maxItems ?: Int.MAX_VALUE)
                )
                    invalid(
                        "list_size",
                        "List does not fit the receiving field's bounds",
                        value.source,
                    )
                children.forEach { check(it, checkNotNull(schema.items), parameters, depth + 1) }
            }
            "string" -> {
                val text = value.text()
                if (
                    text.length > (schema.maxLength ?: Int.MAX_VALUE) ||
                        schema.pattern?.let { !Regex(it).matches(text) } == true
                )
                    invalid("string_value", schema.help, value.source)
                if (schema.choices.isNotEmpty() && text !in schema.choices)
                    invalid("enum_value", "Choose ${schema.choices.joinToString()}", value.source)
                if (schema.format == "simulation_duration")
                    ConfigSchemas.duration(schema.help, true).decode(value)
            }
            "integer",
            "number" -> {
                if (schema.type == "integer") value.integer()
                val number =
                    (value as? YamlValue.Number)?.value
                        ?: invalid("field_type", "Expected a number", value.source)
                if (
                    schema.minimum?.let { number < it } == true ||
                        schema.maximum?.let { number > it } == true
                )
                    invalid("number_range", schema.help, value.source)
            }
            "boolean" ->
                if (value !is YamlValue.Flag)
                    invalid("field_type", "Expected true or false", value.source)
            else -> error("Capability schema must describe its configuration: ${schema.help}")
        }
    }

    private fun compatible(type: String, schema: SchemaDescription): Boolean {
        schema.parameterType?.let {
            return type == it || type == "integer" && it == "number"
        }
        schema.reference?.let {
            return compatible(type, checkNotNull(definitions[it.substringAfterLast('/')]))
        }
        if (schema.alternatives.isNotEmpty())
            return schema.alternatives.any { compatible(type, it) }
        return when (schema.format) {
            "simulation_duration" -> type == "duration"
            "percentage" -> type == "percentage"
            null ->
                when (schema.type) {
                    "integer" -> type == "integer"
                    "number" -> type == "number" || type == "integer"
                    "boolean" -> type == "boolean"
                    "string" -> type == "string" || type == "enum"
                    else -> false
                }
            else -> false
        }
    }
}

internal class ExpansionBudget(private val maximum: Int = 262_144) {
    private var remaining = maximum

    fun visit(value: YamlValue) {
        if (--remaining < 0)
            invalid(
                "expansion_limit",
                "Reusable configuration exceeds $maximum expanded nodes in this catalog",
                value.source,
            )
    }
}

/** Immutable replacement preserves the argument's file namespace and diagnostic provenance. */
internal fun substituteParameters(
    value: YamlValue,
    arguments: Map<String, YamlValue>,
    budget: ExpansionBudget,
    types: Map<String, String> = emptyMap(),
): YamlValue {
    budget.visit(value)
    parameterName(value)?.let { name ->
        val argument =
            arguments[name]
                ?: invalid("missing_argument", "Missing required parameter '$name'", value.source)
        val result = substituteParameters(argument, emptyMap(), budget)
        val type = types[name] ?: result.parameterType
        return when (result) {
            is YamlValue.Text -> result.copy(parameterType = type)
            is YamlValue.Number -> result.copy(parameterType = type)
            is YamlValue.Flag -> result.copy(parameterType = type)
            else -> result
        }
    }
    return when (value) {
        is YamlValue.Mapping ->
            YamlValue.Mapping(
                value.entries.mapValues {
                    substituteParameters(it.value, arguments, budget, types)
                },
                value.source,
            )
        is YamlValue.Sequence ->
            YamlValue.Sequence(
                value.entries.map { substituteParameters(it, arguments, budget, types) },
                value.source,
            )
        else -> value
    }
}

internal fun YamlValue.canonicalData(): String =
    when (this) {
        is YamlValue.Mapping ->
            entries.toSortedMap().entries.joinToString(",", "{", "}") {
                jsonString(it.key) + ":" + it.value.canonicalData()
            }
        is YamlValue.Sequence -> entries.joinToString(",", "[", "]") { it.canonicalData() }
        is YamlValue.Text -> jsonString(value)
        is YamlValue.Number ->
            value.stripTrailingZeros().toPlainString() +
                if (!integer && value.stripTrailingZeros().scale() <= 0) ".0" else ""
        is YamlValue.Flag -> value.toString()
        is YamlValue.Null -> "null"
    }
