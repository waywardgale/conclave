package dev.conclave.core

/** Public immutable authoring metadata; runtime instances receive only compiled configuration. */
class ReusableMechanicDefinition
internal constructor(
    val id: DefinitionId,
    val name: String?,
    parameters: Map<String, MechanicParameter>,
    internal val body: YamlValue.Mapping,
    internal val context: SchemaContext,
) {
    val parameters: Map<String, MechanicParameter> =
        java.util.Collections.unmodifiableMap(LinkedHashMap(parameters))

    internal fun canonical(): String =
        "{\"id\":${jsonString(id.toString())},\"name\":${name?.let(::jsonString) ?: "null"},\"parameters\":" +
            parameters.toSortedMap().entries.joinToString(",", "{", "}") {
                jsonString(it.key) + ":" + it.value.declaration.canonicalData()
            } +
            ",\"body\":${body.canonicalData()}}"
}

/** One catalog-local linker. All definitions are collected before any occurrence is bound. */
internal class ReusableMechanics(private val registry: MechanicRegistry) {
    private val descriptions = registry.descriptions()
    private val definitions = linkedMapOf<DefinitionId, ReusableMechanicDefinition>()
    private val budget = ExpansionBudget()
    private val templateBudget = ExpansionBudget()
    private val checkedDepth = mutableMapOf<DefinitionId, Int>()
    private var compilationDepth = 0

    fun definitions(): Map<DefinitionId, ReusableMechanicDefinition> =
        java.util.Map.copyOf(definitions)

    fun declare(node: YamlValue.Mapping, context: SchemaContext) {
        val fields = Fields(node)
        val id =
            DefinitionId(
                context.namespace,
                ConfigSchemas.identifier("Mechanic definition ID").decode(fields.required("id")),
            )
        val name =
            fields.optional("name")?.let { ConfigSchemas.text("Mechanic display name").decode(it) }
        val parameters = MechanicParameters.read(fields.optional("parameters"), context)
        val body =
            YamlValue.Mapping(
                node.entries.filterKeys { it !in setOf("id", "name", "parameters") },
                node.source,
            )
        val definition = ReusableMechanicDefinition(id, name, parameters, body, context)
        if (definitions.putIfAbsent(id, definition) != null)
            invalid(
                "duplicate_definition",
                "Mechanic '$id' is declared more than once",
                node.source,
            )
    }

    fun validate(): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        for (definition in definitions.values) try {
            validateDefinition(definition, linkedSetOf())
            if (definition.parameters.values.none { it.required }) {
                val args = definition.parameters.mapValues { checkNotNull(it.value.default) }
                expand(definition, args, definition.body.source, emptyList())
            }
        } catch (failure: InvalidInput) {
            diagnostics += failure.diagnostic
        }
        return diagnostics
    }

    private fun validateDefinition(
        definition: ReusableMechanicDefinition,
        stack: LinkedHashSet<DefinitionId>,
    ): Int {
        checkedDepth[definition.id]?.let { depth ->
            if (stack.size + depth > 32)
                invalid(
                    "reuse_depth",
                    "Mechanic reuse exceeds 32 definitions",
                    definition.body.source,
                )
            return depth
        }
        if (!stack.add(definition.id))
            invalid(
                "mechanic_cycle",
                "Recursive mechanic definitions: ${(stack + definition.id).joinToString(" -> ")}",
                definition.body.source,
            )
        if (stack.size > 32)
            invalid("reuse_depth", "Mechanic reuse exceeds 32 definitions", definition.body.source)
        val depth =
            1 +
                validateTemplate(
                    definition.body,
                    definition.parameters,
                    definition.context,
                    stack,
                    0,
                )
        stack.remove(definition.id)
        checkedDepth[definition.id] = depth
        return depth
    }

    private fun validateTemplate(
        body: YamlValue.Mapping,
        parameters: Map<String, MechanicParameter>,
        context: SchemaContext,
        stack: LinkedHashSet<DefinitionId>,
        depth: Int,
    ): Int {
        if (depth > 64)
            invalid("composition_depth", "Mechanics exceed 64 nested children", body.source)
        if (mode(body)) {
            val target = target(body.entries.getValue("use"), context)
            for ((id, value) in arguments(body, target)) {
                val parameter = target.parameters.getValue(id)
                MechanicTemplateSchema(parameter.description, templateBudget)
                    .validate(value, parameters)
                if (!containsParameter(value)) parameter.validate(value, context)
            }
            return validateDefinition(target, stack)
        }
        val type = type(body.entries.getValue("type"))
        val schema =
            descriptions[type]
                ?: invalid(
                    "unknown_mechanic",
                    "Mechanic capability is not installed: $type",
                    body.source,
                )
        var reusedDepth = 0
        MechanicTemplateSchema(schema, templateBudget) { child, declared ->
                val configuration = occurrenceConfiguration(child, context)
                reusedDepth =
                    maxOf(
                        reusedDepth,
                        validateTemplate(configuration, declared, context, stack, depth + 1),
                    )
            }
            .validate(configuration(body), parameters)
        MechanicExports.validateTemplate(body.entries["export"], parameters, templateBudget)
        if (!containsParameter(body)) compileDirect(body, context, stack.toList())
        return reusedDepth
    }

    fun compile(node: YamlValue.Mapping, context: SchemaContext): CompiledMechanic =
        compile(node, context, emptyList())

    private fun compile(
        node: YamlValue.Mapping,
        context: SchemaContext,
        stack: List<DefinitionId>,
    ): CompiledMechanic {
        if (++compilationDepth > 64) {
            compilationDepth--
            invalid("composition_depth", "Mechanics exceed 64 nested children", node.source)
        }
        try {
            if (!mode(node)) return compileDirect(node, context, stack)
            val definition = target(node.entries.getValue("use"), context)
            val values = arguments(node, definition)
            for ((id, value) in values) definition.parameters.getValue(id).validate(value, context)
            return expand(definition, values, node.source, stack)
        } finally {
            compilationDepth--
        }
    }

    fun occurrence(value: YamlValue, context: SchemaContext): MechanicOccurrence =
        occurrence(value, context, emptyList())

    private fun occurrence(
        value: YamlValue,
        context: SchemaContext,
        stack: List<DefinitionId>,
    ): MechanicOccurrence {
        val configuration = occurrenceConfiguration(value, context)
        val fields = value.mapping().entries
        return MechanicOccurrence(
            fields.getValue("id").text(),
            fields["name"]?.text() ?: displayName(configuration, context),
            compile(configuration, context, stack),
        )
    }

    private fun occurrenceConfiguration(
        value: YamlValue,
        context: SchemaContext,
    ): YamlValue.Mapping {
        val node = value.mapping()
        val fields = Fields(node)
        ConfigSchemas.identifier("Local mechanic ID").decode(fields.required("id"), context)
        fields.optional("name")?.let { ConfigSchemas.text("Display name").decode(it, context) }
        return YamlValue.Mapping(node.entries - setOf("id", "name"), node.source)
    }

    fun displayName(node: YamlValue.Mapping, context: SchemaContext): String? =
        node.entries["use"]?.let { target(it, context).name }

    private fun expand(
        definition: ReusableMechanicDefinition,
        args: Map<String, YamlValue>,
        caller: SourceLocation,
        stack: List<DefinitionId>,
    ): CompiledMechanic {
        if (definition.id in stack)
            invalid("mechanic_cycle", "Recursive mechanic definition '${definition.id}'", caller)
        if (stack.size >= 32)
            invalid("reuse_depth", "Mechanic reuse exceeds 32 definitions", caller)
        try {
            val bound =
                substituteParameters(
                        definition.body,
                        args,
                        budget,
                        definition.parameters.mapValues { it.value.type },
                    )
                    .mapping()
            if (!mode(bound)) return compileDirect(bound, definition.context, stack + definition.id)
            val next = target(bound.entries.getValue("use"), definition.context)
            val values = arguments(bound, next)
            for ((id, value) in values) next.parameters
                .getValue(id)
                .validate(value, definition.context)
            return expand(next, values, caller, stack + definition.id)
        } catch (failure: InvalidInput) {
            val diagnostic = failure.diagnostic
            throw InvalidInput(
                diagnostic.copy(
                    message =
                        diagnostic.message +
                            " [use ${definition.id} at ${caller.file}:${caller.line} ${caller.path}; body ${definition.body.source.file}:${definition.body.source.line}]"
                )
            )
        }
    }

    private fun compileDirect(
        body: YamlValue.Mapping,
        context: SchemaContext,
        stack: List<DefinitionId>,
    ): CompiledMechanic {
        val node = body.entries.getValue("type")
        val linked =
            context.copy(mechanicCompiler = { child, source -> occurrence(child, source, stack) })
        return when (val result = registry.compile(type(node), configuration(body), linked)) {
            is Validation.Valid ->
                MechanicExports.attach(body.entries["export"], result.value, linked)
            is Validation.Invalid -> throw InvalidInput(result.diagnostics.first())
        }
    }

    private fun mode(node: YamlValue.Mapping): Boolean {
        val type = "type" in node.entries
        val use = "use" in node.entries
        if (type == use)
            invalid("mechanic_source", "Supply exactly one of type and use", node.source)
        return use
    }

    private fun configuration(node: YamlValue.Mapping) =
        YamlValue.Mapping(node.entries - setOf("type", "export"), node.source)

    private fun target(value: YamlValue, context: SchemaContext): ReusableMechanicDefinition {
        val id = DefinitionReferenceSchema.decode(value, context)
        return definitions[id]
            ?: invalid(
                "unknown_reusable_mechanic",
                "Reusable mechanic '$id' is not declared",
                value.source,
            )
    }

    private fun type(value: YamlValue): DefinitionId =
        try {
            DefinitionId.parse(value.text(), "conclave")
        } catch (_: IllegalArgumentException) {
            invalid("mechanic_type", "Use a registered mechanic type", value.source)
        }

    private fun arguments(
        node: YamlValue.Mapping,
        definition: ReusableMechanicDefinition,
    ): Map<String, YamlValue> {
        val fields = Fields(node)
        fields.required("use")
        val supplied = fields.optional("with")?.mapping()?.entries ?: emptyMap()
        fields.finish()
        supplied.keys
            .firstOrNull { it !in definition.parameters }
            ?.let {
                invalid(
                    "unknown_argument",
                    "Mechanic '${definition.id}' has no parameter '$it'",
                    supplied.getValue(it).source,
                )
            }
        return definition.parameters.mapValues { (id, parameter) ->
            supplied[id]
                ?: parameter.default
                ?: invalid(
                    "missing_argument",
                    "Mechanic '${definition.id}' requires parameter '$id'",
                    node.source.field("with").field(id),
                )
        }
    }
}

private fun containsParameter(value: YamlValue): Boolean =
    parameterName(value) != null ||
        when (value) {
            is YamlValue.Mapping -> value.entries.values.any(::containsParameter)
            is YamlValue.Sequence -> value.entries.any(::containsParameter)
            else -> false
        }
