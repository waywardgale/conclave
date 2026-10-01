package dev.conclave.core

import java.math.BigDecimal

/**
 * A parameter is configuration. Its schema cannot accept callbacks, expressions, or arbitrary maps.
 */
class MechanicParameter
internal constructor(
    val id: String,
    val type: String,
    val name: String?,
    val help: String?,
    internal val schema: ConfigSchema<*>,
    internal val default: YamlValue?,
    internal val declaration: YamlValue.Mapping,
    private val context: SchemaContext,
) {
    val required: Boolean
        get() = default == null

    val description: SchemaDescription
        get() =
            schema.description.copy(
                help = help ?: schema.description.help,
                parameterType = type,
                defaultJson = default?.let { canonicalParameter(schema, it, context) },
                valueConstraints =
                    java.util.Map.copyOf(
                        declaration.entries
                            .filterKeys {
                                it in
                                    setOf(
                                        "min",
                                        "max",
                                        "max_length",
                                        "min_length",
                                        "min_items",
                                        "max_items",
                                    )
                            }
                            .mapValues { it.value.canonicalData() }
                    ),
            )

    internal fun validate(value: YamlValue, context: SchemaContext) {
        rejectParameterReferences(value)
        schema.decode(value, context)
    }
}

@Suppress("UNCHECKED_CAST")
private fun canonicalParameter(
    schema: ConfigSchema<*>,
    value: YamlValue,
    context: SchemaContext,
): String {
    val typed = schema as ConfigSchema<Any?>
    return typed.encode(typed.decode(value, context))
}

internal object MechanicParameters {
    fun read(value: YamlValue?, context: SchemaContext): Map<String, MechanicParameter> {
        if (value == null) return emptyMap()
        val entries = value.mapping().entries
        if (entries.size > 128)
            invalid("parameter_limit", "Use at most 128 parameters", value.source)
        return java.util.Collections.unmodifiableMap(
            entries.mapValues { (id, node) ->
                if (!isAuthoredName(id))
                    invalid("parameter_id", "Use a snake_case parameter ID", node.source)
                val fields = Fields(node.mapping())
                val type = fields.required("type").text()
                val name =
                    fields.optional("name")?.let {
                        ConfigSchemas.text("Parameter display name").decode(it)
                    }
                val help =
                    fields.optional("description")?.let {
                        ConfigSchemas.text("Parameter help", 2048).decode(it)
                    }
                val default = fields.optional("default")
                val schema: ConfigSchema<*> =
                    when (type) {
                        "boolean" -> ConfigSchemas.flag("Boolean value")
                        "integer" -> {
                            val min = fields.optional("min")?.integer() ?: Long.MIN_VALUE
                            val max = fields.optional("max")?.integer() ?: Long.MAX_VALUE
                            bounds(min <= max, node)
                            ConfigSchemas.integer("Whole number", min, max)
                        }
                        "number" -> {
                            val min = fields.optional("min")?.let(::number) ?: BigDecimal("-1e128")
                            val max = fields.optional("max")?.let(::number) ?: BigDecimal("1e128")
                            bounds(min <= max, node)
                            ConfigSchemas.decimal("Finite number", min, max)
                        }
                        "duration" -> {
                            val base = ConfigSchemas.duration("Simulation duration", true)
                            val min = fields.optional("min")?.let { base.decode(it).ticks } ?: 0
                            val max =
                                fields.optional("max")?.let { base.decode(it).ticks }
                                    ?: Long.MAX_VALUE
                            bounds(min <= max, node)
                            base.checked { duration, at ->
                                if (duration.ticks !in min..max)
                                    invalid(
                                        "parameter_bounds",
                                        "Duration is outside the parameter's bounds",
                                        at.source,
                                    )
                            }
                        }
                        "percentage" -> {
                            val min = fields.optional("min")?.let(::percentage) ?: BigDecimal.ZERO
                            val max =
                                fields.optional("max")?.let(::percentage) ?: BigDecimal("1e128")
                            bounds(min <= max, node)
                            object : ConfigSchema<BigDecimal> {
                                override val description =
                                    SchemaDescription(
                                        "string",
                                        "Explicit percentage",
                                        format = "percentage",
                                        pattern = "^[0-9]+(?:\\.[0-9]+)?%$",
                                    )

                                override fun decode(value: YamlValue, context: SchemaContext) =
                                    percentage(value).also {
                                        if (it < min || it > max)
                                            invalid(
                                                "parameter_bounds",
                                                "Percentage is outside the parameter's bounds",
                                                value.source,
                                            )
                                    }

                                override fun encode(value: BigDecimal) =
                                    jsonString(value.stripTrailingZeros().toPlainString() + "%")
                            }
                        }
                        "string" -> {
                            val max =
                                fields.optional("max_length")?.let { boundedInt(it, 0, 32768) }
                                    ?: 256
                            ConfigSchemas.text("Literal text", max)
                        }
                        "enum" -> {
                            val options =
                                fields.required("values").sequence().map {
                                    ConfigSchemas.text("Choice", 256).decode(it)
                                }
                            if (
                                options.isEmpty() ||
                                    options.size > 256 ||
                                    options.distinct().size != options.size
                            )
                                invalid(
                                    "parameter_choices",
                                    "Use 1 to 256 distinct string choices",
                                    node.source,
                                )
                            ConfigSchemas.choice("Declared choice", options.associateWith { it })
                        }
                        "area",
                        "location",
                        "group",
                        "role" ->
                            ConfigSchemas.identifier("Typed $type reference").parameterType(type)
                        "aura" -> DefinitionReferenceSchema.parameterType(type)
                        "effect",
                        "damage_type" -> NativeReferenceSchema.parameterType(type)
                        "players" -> PlayerSelectionSchema
                        "pattern_tokens" -> {
                            val min =
                                fields.optional("min_items")?.let { boundedInt(it, 1, 1024) } ?: 1
                            val max =
                                fields.optional("max_items")?.let { boundedInt(it, 1, 1024) }
                                    ?: 1024
                            bounds(min <= max, node)
                            ConfigSchemas.list(
                                    "Distinct pattern tokens",
                                    ConfigSchemas.identifier("Token"),
                                    min,
                                    max,
                                )
                                .checked { tokens, at ->
                                    if (tokens.distinct().size != tokens.size)
                                        invalid(
                                            "parameter_tokens",
                                            "Token vocabulary must be unique",
                                            at.source,
                                        )
                                }
                                .parameterType(type)
                        }
                        "pattern" -> {
                            val min =
                                fields.optional("min_length")?.let { boundedInt(it, 1, 1024) } ?: 1
                            val max =
                                fields.optional("max_length")?.let { boundedInt(it, 1, 1024) }
                                    ?: 1024
                            bounds(min <= max, node)
                            BuiltinMechanics.recipe
                                .checked { recipe, at ->
                                    val lengths =
                                        recipe.fixed?.let { listOf(it.size) }
                                            ?: recipe.choices?.map { it.size }
                                            ?: listOf(checkNotNull(recipe.length))
                                    if (lengths.any { it !in min..max })
                                        invalid(
                                            "parameter_bounds",
                                            "Every possible answer must fit the parameter's length bounds",
                                            at.source,
                                        )
                                    recipe.pool?.let { pool ->
                                        if (
                                            pool.distinct().size != pool.size ||
                                                !recipe.repeats &&
                                                    checkNotNull(recipe.length) > pool.size
                                        )
                                            invalid(
                                                "parameter_pattern",
                                                "Sample requires a unique pool with enough tokens",
                                                at.source,
                                            )
                                    }
                                }
                                .parameterType(type)
                        }
                        "pattern_inputs" -> {
                            val min =
                                fields.optional("min_items")?.let { boundedInt(it, 1, 256) } ?: 1
                            val max =
                                fields.optional("max_items")?.let { boundedInt(it, 1, 256) } ?: 256
                            bounds(min <= max, node)
                            ConfigSchemas.list(
                                    "Physical token inputs",
                                    BuiltinMechanics.patternInput,
                                    min,
                                    max,
                                )
                                .parameterType(type)
                        }
                        else ->
                            invalid(
                                "parameter_type",
                                "Parameter type '$type' has no installed consuming capability",
                                fields.node.entries.getValue("type").source,
                            )
                    }
                fields.finish()
                MechanicParameter(id, type, name, help, schema, default, node.mapping(), context)
                    .also {
                        default?.let { value -> it.validate(value, context) }
                    }
            }
        )
    }

    private fun bounds(valid: Boolean, value: YamlValue) {
        if (!valid)
            invalid("parameter_bounds", "Parameter minimum exceeds its maximum", value.source)
    }

    private fun number(value: YamlValue): BigDecimal =
        (value as? YamlValue.Number)?.value
            ?: invalid("field_type", "Expected a finite number", value.source)

    private fun percentage(value: YamlValue): BigDecimal {
        val text = value.text()
        if (text.length > 128 || !Regex("[0-9]+(?:\\.[0-9]+)?%").matches(text))
            invalid(
                "parameter_percentage",
                "Use an explicit nonnegative percentage such as 25%",
                value.source,
            )
        return BigDecimal(text.dropLast(1))
    }

    private fun boundedInt(value: YamlValue, min: Int, max: Int) =
        ConfigSchemas.integer("Expected $min to $max", min.toLong(), max.toLong())
            .decode(value)
            .toInt()
}

internal fun parameterName(value: YamlValue): String? {
    if (value !is YamlValue.Mapping || "parameter" !in value.entries) return null
    if (value.entries.size != 1)
        invalid(
            "parameter_reference",
            "A parameter reference occupies one whole value",
            value.source,
        )
    return ConfigSchemas.identifier("Declared parameter ID")
        .decode(value.entries.getValue("parameter"))
}

internal fun rejectParameterReferences(value: YamlValue) {
    if (parameterName(value) != null)
        invalid(
            "parameter_reference",
            "A concrete argument or default cannot contain an unresolved parameter",
            value.source,
        )
    when (value) {
        is YamlValue.Mapping -> value.entries.values.forEach(::rejectParameterReferences)
        is YamlValue.Sequence -> value.entries.forEach(::rejectParameterReferences)
        else -> Unit
    }
}
