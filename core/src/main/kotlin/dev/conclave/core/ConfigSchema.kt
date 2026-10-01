package dev.conclave.core

import java.math.BigDecimal

/** A single description drives decoding, editor controls, help, and exported JSON Schema. */
data class SchemaDescription(
    val type: String,
    val help: String,
    val properties: Map<String, SchemaDescription> = emptyMap(),
    val required: Set<String> = emptySet(),
    val items: SchemaDescription? = null,
    val choices: List<String> = emptyList(),
    val minimum: BigDecimal? = null,
    val maximum: BigDecimal? = null,
    val minItems: Int? = null,
    val maxItems: Int? = null,
    val maxLength: Int? = null,
    val pattern: String? = null,
    val format: String? = null,
    val alternatives: List<SchemaDescription> = emptyList(),
    val defaultJson: String? = null,
    val reference: String? = null,
    val definitions: Map<String, SchemaDescription> = emptyMap(),
    val parameterType: String? = null,
    val valueConstraints: Map<String, String> = emptyMap(),
) {
    fun json(): String {
        val collected = linkedMapOf<String, SchemaDescription>()
        fun collect(node: SchemaDescription) {
            node.definitions.forEach { (key, value) ->
                val old = collected.putIfAbsent(key, value)
                require(old == null || old == value) { "Conflicting schema definition $key" }
                if (old == null) collect(value)
            }
            node.properties.values.forEach(::collect)
            node.items?.let(::collect)
            node.alternatives.forEach(::collect)
        }
        collect(this)
        return render(collected)
    }

    private fun render(rootDefinitions: Map<String, SchemaDescription> = emptyMap()): String {
        val entries = linkedMapOf<String, String>()
        if (type.isNotEmpty()) entries["type"] = jsonString(type)
        reference?.let { entries["\$ref"] = jsonString(it) }
        if (rootDefinitions.isNotEmpty())
            entries["\$defs"] =
                rootDefinitions.entries.joinToString(",", "{", "}") {
                    jsonString(it.key) + ":" + it.value.render()
                }
        entries["description"] = jsonString(help)
        if (type == "object") {
            entries["properties"] =
                properties.entries.joinToString(",", "{", "}") {
                    jsonString(it.key) + ":" + it.value.render()
                }
            entries["required"] = required.joinToString(",", "[", "]", transform = ::jsonString)
            entries["additionalProperties"] = "false"
        }
        items?.let { entries["items"] = it.render() }
        if (choices.isNotEmpty())
            entries["enum"] = choices.joinToString(",", "[", "]", transform = ::jsonString)
        minimum?.let { entries["minimum"] = it.toPlainString() }
        maximum?.let { entries["maximum"] = it.toPlainString() }
        minItems?.let { entries["minItems"] = it.toString() }
        maxItems?.let { entries["maxItems"] = it.toString() }
        maxLength?.let { entries["maxLength"] = it.toString() }
        pattern?.let { entries["pattern"] = jsonString(it) }
        format?.let { entries["x-conclave-format"] = jsonString(it) }
        parameterType?.let { entries["x-conclave-parameter-type"] = jsonString(it) }
        if (valueConstraints.isNotEmpty())
            entries["x-conclave-constraints"] =
                valueConstraints.toSortedMap().entries.joinToString(",", "{", "}") {
                    jsonString(it.key) + ":" + it.value
                }
        if (alternatives.isNotEmpty())
            entries["oneOf"] = alternatives.joinToString(",", "[", "]") { it.render() }
        defaultJson?.let { entries["default"] = it }
        return entries.entries.joinToString(",", "{", "}") { jsonString(it.key) + ":" + it.value }
    }
}

data class SchemaContext(
    val namespace: String = "local",
    val event: EventContract? = null,
    val phases: Set<String> = emptySet(),
    val beforeAttempt: Boolean = false,
    val sourceNamespaces: Map<String, String> = emptyMap(),
    val mechanicCompiler: ((YamlValue, SchemaContext) -> MechanicOccurrence)? = null,
) {
    fun namespaceOf(value: YamlValue): String = sourceNamespaces[value.source.file] ?: namespace
}

interface ConfigSchema<T> {
    val description: SchemaDescription

    fun decode(value: YamlValue, context: SchemaContext = SchemaContext()): T

    fun encode(value: T): String

    fun validate(value: YamlValue, context: SchemaContext = SchemaContext()): Validation<T> =
        try {
            Validation.Valid(decode(value, context))
        } catch (failure: InvalidInput) {
            Validation.Invalid(listOf(failure.diagnostic))
        }
}

class ConfigField<T>
internal constructor(
    val name: String,
    val schema: ConfigSchema<T>,
    internal val fallback: (() -> T)?,
)

class ConfigValues internal constructor(private val values: Map<ConfigField<*>, Any?>) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(field: ConfigField<T>): T {
        check(values.containsKey(field)) { "Field does not belong to this description" }
        return values[field] as T
    }
}

class ConfigRecordBuilder {
    private val fields = mutableListOf<ConfigField<*>>()

    fun <T> required(name: String, schema: ConfigSchema<T>): ConfigField<T> =
        field(name, schema, null)

    fun <T> defaulted(name: String, schema: ConfigSchema<T>, value: T): ConfigField<T> =
        field(name, schema) { value }

    fun <T> optional(name: String, schema: ConfigSchema<T>): ConfigField<T?> =
        field(
            name,
            object : ConfigSchema<T?> {
                override val description = schema.description

                override fun decode(value: YamlValue, context: SchemaContext): T =
                    schema.decode(value, context)

                override fun encode(value: T?) = if (value == null) "null" else schema.encode(value)
            },
        ) {
            null
        }

    private fun <T> field(
        name: String,
        schema: ConfigSchema<T>,
        fallback: (() -> T)?,
    ): ConfigField<T> {
        require(isAuthoredName(name) && fields.none { it.name == name })
        return ConfigField(name, schema, fallback).also(fields::add)
    }

    fun <T> build(
        help: String,
        make: (ConfigValues) -> T,
        split: (T) -> Map<ConfigField<*>, Any?>,
    ): ConfigSchema<T> {
        val declarations = java.util.List.copyOf(fields)
        return object : ConfigSchema<T> {
            override val description =
                SchemaDescription(
                    "object",
                    help,
                    properties =
                        java.util.Collections.unmodifiableMap(
                            declarations.associate { it.name to fieldDescription(it) }
                        ),
                    required = declarations.filter { it.fallback == null }.map { it.name }.toSet(),
                )

            override fun decode(value: YamlValue, context: SchemaContext): T {
                val input = Fields(value.mapping())
                val values = declarations.associateWith { field ->
                    val node = input.optional(field.name)
                    if (node == null) {
                        if (field.fallback == null)
                            invalid(
                                "missing_field",
                                "Required field '${field.name}' is missing",
                                value.source.field(field.name),
                            )
                        field.fallback.invoke()
                    } else field.schema.decode(node, context)
                }
                input.finish()
                return try {
                    make(ConfigValues(values))
                } catch (failure: IllegalArgumentException) {
                    invalid(
                        "configuration",
                        failure.message ?: "Invalid configuration",
                        value.source,
                    )
                }
            }

            override fun encode(value: T): String {
                val values = split(value)
                check(values.keys == declarations.toSet()) {
                    "Configuration encoder must supply every declared field"
                }
                return declarations
                    .filter { values[it] != null }
                    .sortedBy { it.name }
                    .joinToString(",", "{", "}") {
                        jsonString(it.name) + ":" + encodeField(it, values[it])
                    }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun encodeField(field: ConfigField<*>, value: Any?): String =
        (field.schema as ConfigSchema<Any?>).encode(value)

    private fun <T> fieldDescription(field: ConfigField<T>): SchemaDescription =
        field.schema.description.copy(
            defaultJson = field.fallback?.let { field.schema.encode(it()) }
        )
}

object ConfigSchemas {
    fun text(help: String, maxLength: Int = 256, pattern: Regex? = null): ConfigSchema<String> =
        object : ConfigSchema<String> {
            override val description =
                SchemaDescription("string", help, maxLength = maxLength, pattern = pattern?.pattern)

            override fun decode(value: YamlValue, context: SchemaContext): String =
                value.text().also {
                    if (it.length > maxLength || pattern?.matches(it) == false)
                        invalid("string_value", help, value.source)
                }

            override fun encode(value: String): String {
                require(value.length <= maxLength && pattern?.matches(value) != false)
                return jsonString(value)
            }
        }

    fun identifier(help: String) = text(help, 128, Regex("[a-z][a-z0-9]*(?:_[a-z0-9]+)*"))

    fun integer(
        help: String,
        min: Long = Long.MIN_VALUE,
        max: Long = Long.MAX_VALUE,
    ): ConfigSchema<Long> =
        object : ConfigSchema<Long> {
            override val description =
                SchemaDescription(
                    "integer",
                    help,
                    minimum = BigDecimal(min),
                    maximum = BigDecimal(max),
                )

            override fun decode(value: YamlValue, context: SchemaContext): Long =
                value.integer().also {
                    if (it !in min..max)
                        invalid(
                            "number_range",
                            "Expected a whole number between $min and $max",
                            value.source,
                        )
                }

            override fun encode(value: Long): String {
                require(value in min..max)
                return value.toString()
            }
        }

    fun decimal(help: String, min: BigDecimal, max: BigDecimal): ConfigSchema<BigDecimal> =
        object : ConfigSchema<BigDecimal> {
            override val description =
                SchemaDescription("number", help, minimum = min, maximum = max)

            override fun decode(value: YamlValue, context: SchemaContext): BigDecimal {
                val number =
                    (value as? YamlValue.Number)?.value
                        ?: invalid("field_type", "Expected a finite number", value.source)
                if (number < min || number > max) invalid("number_range", help, value.source)
                return number
            }

            override fun encode(value: BigDecimal): String {
                require(value >= min && value <= max)
                return value.stripTrailingZeros().toPlainString()
            }
        }

    fun flag(help: String): ConfigSchema<Boolean> =
        object : ConfigSchema<Boolean> {
            override val description = SchemaDescription("boolean", help)

            override fun decode(value: YamlValue, context: SchemaContext) =
                (value as? YamlValue.Flag)?.value
                    ?: invalid("field_type", "Expected true or false", value.source)

            override fun encode(value: Boolean) = value.toString()
        }

    fun duration(help: String, zero: Boolean = false): ConfigSchema<SimulationDuration> =
        object : ConfigSchema<SimulationDuration> {
            override val description =
                SchemaDescription(
                    "string",
                    help,
                    pattern = "^[0-9]+(?:\\.[0-9]+)?(?:ms|s|m|h)$",
                    format = "simulation_duration",
                )

            override fun decode(value: YamlValue, context: SchemaContext): SimulationDuration =
                try {
                    SimulationDuration.parse(value.text(), zero)
                } catch (_: IllegalArgumentException) {
                    invalid("duration", help, value.source)
                }

            override fun encode(value: SimulationDuration): String {
                require(zero || value.ticks > 0)
                return jsonString(
                    BigDecimal(value.ticks)
                        .divide(BigDecimal(20))
                        .stripTrailingZeros()
                        .toPlainString() + "s"
                )
            }
        }

    fun <T> choice(help: String, choices: Map<String, T>): ConfigSchema<T> =
        object : ConfigSchema<T> {
            private val options =
                choices.toMap().also {
                    require(it.isNotEmpty() && it.values.distinct().size == it.size)
                }
            override val description =
                SchemaDescription("string", help, choices = options.keys.toList())

            override fun decode(value: YamlValue, context: SchemaContext): T =
                options[value.text()]
                    ?: invalid("enum_value", "Choose ${options.keys.joinToString()}", value.source)

            override fun encode(value: T) =
                jsonString(options.entries.single { it.value == value }.key)
        }

    fun <T> list(
        help: String,
        element: ConfigSchema<T>,
        min: Int = 0,
        max: Int = 256,
    ): ConfigSchema<List<T>> =
        object : ConfigSchema<List<T>> {
            override val description =
                SchemaDescription(
                    "array",
                    help,
                    items = element.description,
                    minItems = min,
                    maxItems = max,
                )

            override fun decode(value: YamlValue, context: SchemaContext): List<T> {
                val entries = value.sequence()
                if (entries.size !in min..max)
                    invalid("list_size", "Expected between $min and $max entries", value.source)
                return java.util.List.copyOf(entries.map { element.decode(it, context) })
            }

            override fun encode(value: List<T>): String {
                require(value.size in min..max)
                return value.joinToString(",", "[", "]", transform = element::encode)
            }
        }

    fun <A, B> mapped(
        schema: ConfigSchema<A>,
        forward: (A) -> B,
        reverse: (B) -> A,
    ): ConfigSchema<B> =
        object : ConfigSchema<B> {
            override val description = schema.description

            override fun decode(value: YamlValue, context: SchemaContext): B =
                forward(schema.decode(value, context))

            override fun encode(value: B) = schema.encode(reverse(value))
        }
}

internal fun <T> ConfigSchema<T>.checked(check: (T, YamlValue) -> Unit): ConfigSchema<T> {
    val schema = this
    return object : ConfigSchema<T> {
        override val description
            get() = schema.description

        override fun decode(value: YamlValue, context: SchemaContext): T =
            schema.decode(value, context).also { check(it, value) }

        override fun encode(value: T) = schema.encode(value)
    }
}

/** A receiving field's semantic type, distinct from its YAML representation. */
fun <T> ConfigSchema<T>.parameterType(type: String): ConfigSchema<T> {
    require(isAuthoredName(type))
    val schema = this
    return object : ConfigSchema<T> {
        override val description = schema.description.copy(parameterType = type)

        override fun decode(value: YamlValue, context: SchemaContext): T =
            schema.decode(value, context)

        override fun encode(value: T): String = schema.encode(value)
    }
}

internal fun jsonString(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else ->
                if (char.code < 32) append("\\u" + char.code.toString(16).padStart(4, '0'))
                else append(char)
        }
    }
    append('"')
}
