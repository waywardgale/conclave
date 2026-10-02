package dev.conclave.core

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Collections

/** Whole-catalog compilation. Unimplemented capabilities fail closed with a source diagnostic. */
class CatalogCompiler(
    private val limits: ContentLimits = ContentLimits(),
    private val mechanics: MechanicRegistry = BuiltinMechanics.registry(),
) {
    private val yaml = YamlDocumentReader(limits)

    fun compile(documents: List<SourceDocument>): Validation<CompiledCatalog> {
        val origin = SourceLocation("<catalog>")
        if (documents.size > limits.files)
            return Validation.Invalid(
                listOf(Diagnostic("file_limit", "Catalog exceeds its file limit", origin))
            )
        var bytes = 0L
        for (doc in documents) {
            if (doc.text.length > limits.documentBytes)
                return Validation.Invalid(
                    listOf(
                        Diagnostic(
                            "document_limit",
                            "Manifest exceeds its size limit",
                            SourceLocation(doc.file),
                        )
                    )
                )
            bytes += doc.text.toByteArray(Charsets.UTF_8).size
            if (bytes > limits.catalogBytes)
                return Validation.Invalid(
                    listOf(Diagnostic("catalog_limit", "Catalog exceeds its size limit", origin))
                )
        }
        val diagnostics = mutableListOf<Diagnostic>()
        val definitions = linkedMapOf<DefinitionId, EncounterDefinition>()
        val arenas = linkedMapOf<DefinitionId, ArenaDefinition>()
        val locations = linkedMapOf<DefinitionId, WorldLocationDefinition>()
        var settings: GameplaySettings? = null
        var settingsSource: SourceLocation? = null
        val encounterOrigins = mutableMapOf<DefinitionId, SourceLocation>()
        val arenaCompiler = ArenaCompiler()
        val geometry = GeometryEngine(250, 2_000_000)
        val files = mutableSetOf<String>()
        val parsed = mutableListOf<Triple<String, String, YamlValue.Mapping>>()
        for (doc in documents) {
            if (!files.add(doc.file)) {
                diagnostics +=
                    Diagnostic(
                        "duplicate_file",
                        "Source file is supplied more than once",
                        SourceLocation(doc.file),
                    )
                continue
            }
            when (val result = yaml.read(doc)) {
                is Validation.Invalid -> diagnostics += result.diagnostics
                is Validation.Valid ->
                    try {
                        parsed += header(result.value)
                    } catch (failure: InvalidInput) {
                        diagnostics += failure.diagnostic
                    }
            }
        }
        val namespaces =
            java.util.Map.copyOf(parsed.associate { it.third.source.file to it.second })
        val reusable = ReusableMechanics(mechanics)
        val scopes = ScopeCompiler(reusable)
        for ((kind, namespace, body) in parsed) if (kind == "mechanic")
            try {
                reusable.declare(body, SchemaContext(namespace, sourceNamespaces = namespaces))
            } catch (failure: InvalidInput) {
                diagnostics += failure.diagnostic
            }
        diagnostics += reusable.validate()
        try {
            for ((kind, namespace, body) in parsed) try {
                val duplicate =
                    when (kind) {
                        "mechanic" -> false
                        "encounter" ->
                            decodeEncounter(
                                    body,
                                    SchemaContext(namespace, sourceNamespaces = namespaces),
                                    scopes,
                                )
                                .let {
                                    encounterOrigins[it.id] = body.source
                                    definitions.putIfAbsent(it.id, it) != null
                                }
                        "settings" -> {
                            if (settings != null)
                                invalid(
                                    "duplicate_settings",
                                    "Use one settings manifest for the complete catalog",
                                    body.source,
                                )
                            settings = GameplaySettingsSchema.decode(body)
                            settingsSource = body.source
                            false
                        }
                        "arena" ->
                            arenaCompiler.arena(body, namespace, geometry).let {
                                arenas.putIfAbsent(it.id, it) != null
                            }
                        "location" ->
                            arenaCompiler.location(body, namespace).let {
                                locations.putIfAbsent(it.id, it) != null
                            }
                        else ->
                            invalid(
                                "capability_unavailable",
                                "Definition kind '$kind' is not compiled by this build",
                                body.source,
                            )
                    }
                if (duplicate)
                    invalid(
                        "duplicate_definition",
                        "Definition identity is declared more than once for this kind",
                        body.source,
                    )
            } catch (failure: InvalidInput) {
                diagnostics += failure.diagnostic
            }
        } finally {
            geometry.close()
        }
        val policy = settings ?: GameplaySettings()
        val warnings = mutableListOf<Diagnostic>()
        val revival = policy.revival
        if (
            revival.enabled &&
                ((revival.selfRevival && revival.delay.ticks > revival.combatWindow.ticks) ||
                    (revival.assistedRevival &&
                        (revival.helpTime.ticks > revival.combatWindow.ticks ||
                            revival.delay.ticks >
                                revival.combatWindow.ticks - revival.helpTime.ticks)))
        ) {
            warnings +=
                Diagnostic(
                    "revival_timing_warning",
                    "Warning: the delay or assistance time cannot fit within continuous combat for an enabled revival method. Noncombat still pauses the window.",
                    checkNotNull(settingsSource).field("revival"),
                )
        }
        for (encounter in definitions.values) if (
            encounter.prohibitSelfRevival && !policy.revival.encountersMayDisableSelfRevival
        )
            diagnostics +=
                Diagnostic(
                    "revival_override",
                    "Global policy does not permit encounters to prohibit self-revival",
                    encounterOrigins
                        .getValue(encounter.id)
                        .field("revival")
                        .field("prohibit_self_revival"),
                )
        policy.recovery.outsideAttemptFallbacks.forEachIndexed { index, location ->
            if (location !in locations)
                diagnostics +=
                    Diagnostic(
                        "unknown_world_location",
                        "Recovery refers to undeclared world location '$location'",
                        checkNotNull(settingsSource)
                            .field("recovery")
                            .field("outside_attempt_fallbacks")
                            .index(index),
                    )
        }
        for (encounter in definitions.values) diagnostics +=
            PatternValidation.routes(encounter, encounterOrigins.getValue(encounter.id))
        for (arena in arenas.values) for (pairing in arena.encounters.values) {
            val source = pairing.source
            val encounter = definitions[pairing.encounter]
            if (encounter == null) {
                diagnostics +=
                    Diagnostic(
                        "unknown_encounter",
                        "Arena refers to undeclared encounter '${pairing.encounter}'",
                        source,
                    )
                continue
            }
            val references = encounter.spatialReferences()
            for (logical in references.areas + pairing.areas.keys) if (
                pairing.area(logical) !in arena.areas
            )
                diagnostics +=
                    Diagnostic(
                        "unknown_area_binding",
                        "Area '$logical' resolves to missing arena area '${pairing.area(logical)}'",
                        source,
                    )
            for (logical in references.locations + pairing.locations.keys) if (
                pairing.location(logical) !in arena.locations
            )
                diagnostics +=
                    Diagnostic(
                        "unknown_location_binding",
                        "Location '$logical' resolves to missing arena location '${pairing.location(logical)}'",
                        source,
                    )
            diagnostics += PatternValidation.bindings(encounter, arena, pairing)
        }
        return if (diagnostics.isEmpty())
            Validation.Valid(
                CompiledCatalog(
                    definitions,
                    documents,
                    arenas,
                    locations,
                    policy,
                    warnings,
                    reusable.definitions(),
                )
            )
        else Validation.Invalid(java.util.List.copyOf(diagnostics))
    }

    private fun header(root: YamlValue.Mapping): Triple<String, String, YamlValue.Mapping> {
        val fields = Fields(root)
        val schema = fields.required("schema")
        if (schema.integer() != 1L)
            invalid("schema_version", "Supported manifest schema is 1", schema.source)
        val namespaceValue = fields.optional("namespace")
        val namespace = namespaceValue?.text() ?: "local"
        if (!isAuthoredName(namespace) || namespace == "conclave") {
            invalid(
                "namespace",
                "Use a snake_case author namespace; 'conclave' is reserved",
                namespaceValue?.source ?: root.source.field("namespace"),
            )
        }
        val kinds = root.entries.keys - setOf("schema", "namespace")
        if (kinds.size != 1)
            invalid("definition_count", "Use exactly one definition wrapper per file", root.source)
        val kind = kinds.single()
        if (kind == "settings" && namespaceValue != null)
            invalid(
                "settings_namespace",
                "The singleton settings manifest has no namespace",
                namespaceValue.source,
            )
        val body = fields.required(kind).mapping()
        fields.finish()
        return Triple(kind, namespace, body)
    }

    private fun decodeEncounter(
        body: YamlValue.Mapping,
        sourceContext: SchemaContext,
        scopes: ScopeCompiler,
    ): EncounterDefinition {
        val namespace = sourceContext.namespace
        val input = Fields(body)
        val idValue = input.required("id")
        val id = localName(idValue)
        val nameValue = input.optional("name")
        val name = nameValue?.text()
        if (name != null && name.length > 256)
            invalid("name_length", "Display name exceeds 256 characters", nameValue.source)
        val startValue = input.required("start")
        val start = localName(startValue)
        val combat =
            input.optional("combat")?.let {
                ConfigSchemas.flag("Whether this encounter is combat").decode(it)
            } ?: false
        val participants =
            input.optional("participants")?.let {
                PlayerSelectionSchema.decode(it, sourceContext.copy(beforeAttempt = true))
            } ?: PlayerSelection(from = PlayerCollection.ONLINE_RAIDERS, life = LifeFilter.ANY)
        val prohibitSelfRevival =
            input.optional("revival")?.let {
                val fields = Fields(it.mapping())
                val prohibit =
                    fields.optional("prohibit_self_revival")?.let { node ->
                        ConfigSchemas.flag("Prohibit self-revival in this encounter").decode(node)
                    } ?: false
                fields.finish()
                prohibit
            } ?: false
        val precedenceValue = input.optional("outcome_precedence")
        val reconnectGrace =
            input.optional("reconnect_grace")?.let { RealtimeDurationSchema.decode(it) }
                ?: RealtimeDuration(60_000_000_000)
        val recovery =
            input.optional("recovery")?.let { node ->
                val fields = Fields(node.mapping())
                val location = localName(fields.required("location"))
                val fallbacks =
                    fields.optional("fallbacks")?.sequence()?.map(::localName) ?: emptyList()
                fields.finish()
                if (
                    fallbacks.size > 64 ||
                        (listOf(location) + fallbacks).distinct().size != fallbacks.size + 1
                )
                    invalid(
                        "recovery_locations",
                        "Use at most 64 distinct fallback locations; do not repeat the primary location",
                        node.source,
                    )
                EncounterRecovery(location, fallbacks)
            }
        val precedence =
            when (precedenceValue?.text()) {
                null,
                "success" -> OutcomePrecedence.SUCCESS
                "failure" -> OutcomePrecedence.FAILURE
                else ->
                    invalid(
                        "outcome_precedence",
                        "Choose 'success' or 'failure'",
                        precedenceValue.source,
                    )
            }
        val phasesValue = input.required("phases")
        val phaseEntries = phasesValue.sequence()
        if (phaseEntries.size > 256)
            invalid("phase_limit", "Use at most 256 phases in one encounter", phasesValue.source)
        val phaseNodes = phaseEntries.map { it.mapping() }
        val context =
            sourceContext.copy(
                phases = phaseNodes.map { localName(Fields(it).required("id")) }.toSet()
            )
        val content = scopes.compile(input, context)
        val phases = phaseNodes.map {
            phase(it, context.copy(phases = emptySet()), content, scopes)
        }
        if (
            content.objectives.isNotEmpty() ||
                content.completeWhen != null ||
                content.failWhen != null
        )
            invalid("encounter_outcome", "Encounter progression belongs to its phases", body.source)
        unavailable(input, setOf("roles", "rewards", "start_encounter", "arenas"))
        input.finish()
        if (phases.isEmpty())
            invalid("empty_phases", "Encounter needs at least one phase", phasesValue.source)
        val names = phases.map { it.id }
        if (names.toSet().size != names.size)
            invalid("duplicate_phase", "Phase IDs must be unique", phasesValue.source)
        if (start !in names)
            invalid("unknown_phase", "Initial phase is not declared", startValue.source)
        for ((index, phase) in phases.withIndex()) {
            scopes.validate(
                phase.content,
                content,
                phaseNodes[index].source,
                listOf(phase.success, phase.failure).filterIsInstance<PhaseRoute.Choose>().flatMap {
                    it.branches.map { it.first }
                },
            )
            for ((side, route) in listOf("success" to phase.success, "failure" to phase.failure)) {
                for (destination in route.destinations()) if (
                    destination is PhaseRoute.Next && destination.phase !in names
                ) {
                    val at =
                        phaseNodes[index].entries.getValue(side).mapping().entries["next"]?.source
                            ?: phaseNodes[index].entries.getValue(side).source
                    invalid(
                        "unknown_phase",
                        "Route from '${phase.id}' refers to an undeclared phase",
                        at,
                    )
                }
            }
        }
        scopes.validate(content, content, body.source)
        return EncounterDefinition(
            DefinitionId(namespace, id),
            name,
            start,
            phases,
            precedence,
            content,
            combat,
            participants,
            prohibitSelfRevival,
            reconnectGrace,
            recovery,
        )
    }

    private fun phase(
        node: YamlValue.Mapping,
        context: SchemaContext,
        encounter: ScopeDefinition,
        scopes: ScopeCompiler,
    ): PhaseDefinition {
        val fields = Fields(node)
        val id = localName(fields.required("id"))
        val name =
            fields.optional("name")?.let { ConfigSchemas.text("Phase display name").decode(it) }
        val combat =
            fields.optional("combat")?.let {
                ConfigSchemas.flag("Override inherited combat state").decode(it)
            }
        val duration = fields.optional("duration")?.let(::duration)
        val deadline = fields.optional("deadline")?.let(::duration)
        val success = route(fields.required("success"), success = true, context)
        val failure =
            fields.optional("failure")?.let { route(it, success = false, context) }
                ?: PhaseRoute.Wipe
        val content = scopes.compile(fields, context, encounter)
        fields.finish()
        if (duration == null && content.objectives.isEmpty() && content.completeWhen == null)
            invalid(
                "phase_completion",
                "Phase needs objectives, complete_when, or a success duration",
                node.source,
            )
        if (duration != null && (content.objectives.isNotEmpty() || content.completeWhen != null))
            invalid(
                "ambiguous_success",
                "Express combined timing and objectives through complete_when and a named timer",
                node.source,
            )
        return PhaseDefinition(id, duration, deadline, success, failure, content, name, combat)
    }

    private fun unavailable(fields: Fields, known: Set<String>) {
        for (key in known) if (key in fields.node.entries) {
            invalid(
                "capability_unavailable",
                "'$key' is not implemented by this development compiler",
                fields.node.entries.getValue(key).source,
            )
        }
    }

    private fun route(
        value: YamlValue,
        success: Boolean,
        context: SchemaContext,
        allowChoice: Boolean = true,
    ): PhaseRoute {
        val fields = Fields(value.mapping())
        if ("choose" in fields.node.entries) {
            if (!allowChoice)
                invalid("nested_route", "Nested route choices are not supported", value.source)
            val choices = fields.required("choose")
            val branches =
                choices.sequence().map { branch ->
                    val node = branch.mapping()
                    val guard =
                        node.entries["if"]
                            ?: invalid(
                                "missing_field",
                                "Conditional branch requires if",
                                branch.source,
                            )
                    ConditionSchema.decode(guard, context) to
                        route(
                            YamlValue.Mapping(node.entries - "if", node.source),
                            success,
                            context,
                            false,
                        )
                }
            if (branches.isEmpty() || branches.size > 256)
                invalid("route_choices", "Use between 1 and 256 route branches", choices.source)
            val fallback = route(fields.required("otherwise"), success, context, false)
            fields.finish()
            return PhaseRoute.Choose(branches, fallback)
        }
        if (fields.node.entries.size != 1)
            invalid("phase_route", "Choose exactly one phase route", value.source)
        val route =
            when {
                "next" in fields.node.entries -> PhaseRoute.Next(localName(fields.required("next")))
                success && "complete" in fields.node.entries -> {
                    if (
                        fields.required("complete") !is YamlValue.Flag ||
                            !(fields.node.entries.getValue("complete") as YamlValue.Flag).value
                    ) {
                        invalid("phase_route", "Success completion must be true", value.source)
                    }
                    PhaseRoute.Complete
                }
                !success && "wipe" in fields.node.entries -> {
                    if (
                        fields.required("wipe") !is YamlValue.Flag ||
                            !(fields.node.entries.getValue("wipe") as YamlValue.Flag).value
                    ) {
                        invalid("phase_route", "Failure wipe must be true", value.source)
                    }
                    PhaseRoute.Wipe
                }
                else ->
                    invalid(
                        "phase_route",
                        "Use next, success complete, or failure wipe",
                        value.source,
                    )
            }
        fields.finish()
        return route
    }

    private fun localName(value: YamlValue): String =
        value.text().also {
            if (!isAuthoredName(it))
                invalid("identifier", "Use a snake_case identifier", value.source)
        }

    private fun duration(value: YamlValue): SimulationDuration =
        try {
            SimulationDuration.parse(value.text())
        } catch (_: IllegalArgumentException) {
            invalid(
                "duration",
                "Use a positive bounded duration such as 500ms or 10s",
                value.source,
            )
        }
}

/**
 * Construction is restricted to successful complete-catalog compilation. No mutable YAML nodes
 * escape.
 */
class CompiledCatalog
internal constructor(
    encounters: Map<DefinitionId, EncounterDefinition>,
    sources: List<SourceDocument>,
    arenas: Map<DefinitionId, ArenaDefinition> = emptyMap(),
    locations: Map<DefinitionId, WorldLocationDefinition> = emptyMap(),
    val settings: GameplaySettings = GameplaySettings(),
    warnings: List<Diagnostic> = emptyList(),
    reusableMechanics: Map<DefinitionId, ReusableMechanicDefinition> = emptyMap(),
) {
    val reusableMechanics: Map<DefinitionId, ReusableMechanicDefinition> =
        Collections.unmodifiableMap(reusableMechanics.toSortedMap())
    val warnings: List<Diagnostic> = java.util.List.copyOf(warnings)
    val sources: List<SourceDocument> = java.util.List.copyOf(sources)
    val encounters: Map<DefinitionId, EncounterDefinition> =
        Collections.unmodifiableMap(encounters.toSortedMap())
    val arenas: Map<DefinitionId, ArenaDefinition> =
        Collections.unmodifiableMap(arenas.toSortedMap())
    val locations: Map<DefinitionId, WorldLocationDefinition> =
        Collections.unmodifiableMap(locations.toSortedMap())
    val revision: String

    init {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeUTF("conclave-development-catalog-v8")
            out.text(GameplaySettingsSchema.encode(settings))
            out.writeInt(this.reusableMechanics.size)
            this.reusableMechanics.values.forEach { out.text(it.canonical()) }
            out.writeInt(this.arenas.size)
            this.arenas.values.forEach { out.text(it.canonical()) }
            out.writeInt(this.locations.size)
            this.locations.values.forEach {
                out.writeUTF(it.id.toString())
                out.writeUTF(it.name ?: "")
                out.writeUTF(it.dimension)
                out.text(it.placement.canonical())
            }
            out.writeInt(this.encounters.size)
            for ((id, encounter) in this.encounters) {
                out.writeUTF(id.toString())
                out.writeBoolean(encounter.name != null)
                encounter.name?.let(out::writeUTF)
                out.writeUTF(encounter.start)
                out.writeUTF(encounter.precedence.name)
                out.writeBoolean(encounter.combat)
                out.text(PlayerSelectionSchema.encode(encounter.participants))
                out.writeBoolean(encounter.prohibitSelfRevival)
                out.writeLong(encounter.reconnectGrace.nanos)
                out.writeInt(encounter.recovery?.locations?.size ?: 0)
                encounter.recovery?.locations?.forEach(out::writeUTF)
                out.text(encounter.content.canonical())
                out.writeInt(encounter.phases.size)
                for (phase in encounter.phases) {
                    out.writeUTF(phase.id)
                    out.writeBoolean(phase.name != null)
                    phase.name?.let(out::writeUTF)
                    out.writeByte(
                        when (phase.combat) {
                            null -> 0
                            true -> 1
                            false -> 2
                        }
                    )
                    out.text(phase.content.canonical())
                    out.writeLong(phase.duration?.ticks ?: -1)
                    out.writeLong(phase.deadline?.ticks ?: -1)
                    for (route in listOf(phase.success, phase.failure)) out.route(route)
                }
            }
        }
        // ASVS 11.4.1, 11.4.3: content identity covers normalized configuration, not
        // filenames/comments.
        revision =
            MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString("") {
                "%02x".format(it)
            }
    }
}

private fun DataOutputStream.text(value: String) {
    val encoded = value.toByteArray(Charsets.UTF_8)
    writeInt(encoded.size)
    write(encoded)
}

private fun DataOutputStream.route(value: PhaseRoute) {
    when (value) {
        is PhaseRoute.Next -> {
            writeByte(0)
            writeUTF(value.phase)
        }
        PhaseRoute.Complete -> writeByte(1)
        PhaseRoute.Wipe -> writeByte(2)
        is PhaseRoute.Choose -> {
            writeByte(3)
            writeInt(value.branches.size)
            value.branches.forEach {
                text(ConditionSchema.encode(it.first))
                route(it.second)
            }
            route(value.otherwise)
        }
    }
}
