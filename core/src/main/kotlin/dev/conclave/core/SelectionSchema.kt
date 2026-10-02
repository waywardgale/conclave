package dev.conclave.core

internal fun objectDescription(
    help: String,
    properties: Map<String, SchemaDescription>,
    required: Set<String> = properties.keys,
) = SchemaDescription("object", help, properties = properties, required = required)

internal fun variantDescription(name: String, value: SchemaDescription) =
    objectDescription(name, mapOf(name to value))

internal fun singleField(value: YamlValue): Pair<String, YamlValue> {
    val mapping = value.mapping()
    if (mapping.entries.size != 1)
        invalid("one_operation", "Supply exactly one operation", value.source)
    return mapping.entries.entries.single().let { it.key to it.value }
}

object DefinitionReferenceSchema : ConfigSchema<DefinitionId> {
    override val description =
        SchemaDescription(
            "string",
            "An authored definition in this namespace, or namespace:id",
            maxLength = 257,
            pattern = "^(?:[a-z][a-z0-9]*(?:_[a-z0-9]+)*:)?[a-z][a-z0-9]*(?:_[a-z0-9]+)*$",
        )

    override fun decode(value: YamlValue, context: SchemaContext): DefinitionId =
        try {
            DefinitionId.parse(value.text(), context.namespaceOf(value))
        } catch (_: IllegalArgumentException) {
            invalid("definition_reference", description.help, value.source)
        }

    override fun encode(value: DefinitionId) = jsonString(value.toString())
}

object NativeReferenceSchema : ConfigSchema<String> {
    override val description =
        SchemaDescription(
            "string",
            "A namespaced Minecraft registry ID",
            maxLength = 256,
            pattern = "^[a-z0-9_.-]+:[a-z0-9/._-]+$",
        )
    private val schema =
        ConfigSchemas.text(description.help, 256, Regex(checkNotNull(description.pattern)))

    override fun decode(value: YamlValue, context: SchemaContext) = schema.decode(value, context)

    override fun encode(value: String) = schema.encode(value)
}

internal val comparatorNames =
    linkedMapOf(
        "equals" to Comparator.EQUALS,
        "at_least" to Comparator.AT_LEAST,
        "at_most" to Comparator.AT_MOST,
        "greater_than" to Comparator.GREATER_THAN,
        "less_than" to Comparator.LESS_THAN,
    )

internal fun comparison(fields: Fields, value: (YamlValue) -> Long = { it.integer() }): Comparison {
    val present = fields.node.entries.keys.intersect(comparatorNames.keys)
    if (present.size != 1)
        invalid("comparison", "Supply exactly one numeric comparison", fields.node.source)
    val name = present.single()
    return Comparison(comparatorNames.getValue(name), value(fields.required(name)))
}

internal fun comparisonJson(value: Comparison, encode: (Long) -> String = Long::toString): String =
    jsonString(comparatorNames.entries.single { it.value == value.comparator }.key) +
        ":" +
        encode(value.value)

/** Player predicates share their decoder with collection conditions and selector where clauses. */
object PlayerPredicateSchema : ConfigSchema<PlayerPredicate> {
    private val local = ConfigSchemas.identifier("A declared local ID")
    private val number = ConfigSchemas.integer("Whole-number comparison").description
    private val recursive =
        SchemaDescription("", "A player predicate", reference = "#/$" + "defs/player_predicate")
    private val memberRecursive =
        SchemaDescription("", "A member condition", reference = "#/\$defs/member_predicate")
    private val body by lazy { body(recursive, false) }
    private val memberBody by lazy { body(memberRecursive, true) }

    private fun body(recursive: SchemaDescription, member: Boolean): SchemaDescription {
        val alternatives =
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
                variantDescription("in_area", local.description.copy(parameterType = "area")),
                variantDescription("has_role", local.description.copy(parameterType = "role")),
                variantDescription(
                    "has_effect",
                    NativeReferenceSchema.description.copy(parameterType = "effect"),
                ),
                variantDescription(
                    "has_aura",
                    SchemaDescription(
                        "",
                        "Aura presence or a state comparison",
                        alternatives =
                            listOf(
                                DefinitionReferenceSchema.description.copy(parameterType = "aura"),
                                objectDescription(
                                    "Aura state",
                                    mapOf(
                                        "aura" to
                                            DefinitionReferenceSchema.description.copy(
                                                parameterType = "aura"
                                            ),
                                        "stacks" to comparisonDescription(number),
                                        "remaining" to
                                            comparisonDescription(
                                                ConfigSchemas.duration(
                                                        "Remaining simulation time",
                                                        true,
                                                    )
                                                    .description
                                            ),
                                        "timed" to
                                            ConfigSchemas.flag("Whether the aura has an expiry")
                                                .description,
                                    ),
                                    setOf("aura"),
                                ),
                            ),
                    ),
                ),
                variantDescription(
                    "player_state",
                    objectDescription(
                        "Current player state",
                        mapOf(
                            "online" to ConfigSchemas.flag("Connected to the server").description,
                            "state" to lifeSchema.description,
                            "participation" to participationSchema.description,
                        ),
                        emptySet(),
                    ),
                ),
            )
        return SchemaDescription(
            "",
            "A side-effect-free player condition",
            alternatives =
                alternatives +
                    if (member)
                        listOf(
                            variantDescription("pattern_state", PatternStateQuerySchema.description)
                        )
                    else emptyList(),
        )
    }

    override val description
        get() = recursive.copy(definitions = mapOf("player_predicate" to body))

    internal val memberDescription
        get() = memberRecursive.copy(definitions = mapOf("member_predicate" to memberBody))

    internal fun decodeMember(value: YamlValue, context: SchemaContext) =
        decode(value, context, 0, true)

    override fun decode(value: YamlValue, context: SchemaContext): PlayerPredicate =
        decode(value, context, 0)

    private fun decode(
        value: YamlValue,
        context: SchemaContext,
        depth: Int,
        member: Boolean = false,
    ): PlayerPredicate {
        if (depth > 32)
            invalid("condition_depth", "Condition nesting exceeds 32 levels", value.source)
        val (kind, node) = singleField(value)
        return when (kind) {
            "and",
            "or" -> {
                val items = node.sequence()
                if (items.isEmpty() || items.size > 256)
                    invalid("condition_count", "Use between 1 and 256 conditions", node.source)
                val children = items.map { decode(it, context, depth + 1, member) }
                if (kind == "and") PlayerPredicate.And(children) else PlayerPredicate.Or(children)
            }
            "not" -> PlayerPredicate.Not(decode(node, context, depth + 1, member))
            "pattern_state" -> {
                if (!member || context.beforeAttempt)
                    invalid(
                        "pattern_state_context",
                        "Pattern state is available in an attempt's any/all member conditions",
                        node.source,
                    )
                PlayerPredicate.PatternState(PatternStateQuerySchema.decode(node, context))
            }
            "in_area" -> PlayerPredicate.InArea(local.decode(node, context))
            "has_role" -> {
                if (context.beforeAttempt)
                    invalid("prestart_state", "Roles do not exist before an attempt", node.source)
                PlayerPredicate.HasRole(local.decode(node, context))
            }
            "has_effect" -> PlayerPredicate.HasEffect(NativeReferenceSchema.decode(node, context))
            "has_aura" -> {
                if (node is YamlValue.Text)
                    PlayerPredicate.HasAura(DefinitionReferenceSchema.decode(node, context))
                else {
                    val fields = Fields(node.mapping())
                    val aura = DefinitionReferenceSchema.decode(fields.required("aura"), context)
                    val stacks =
                        fields.optional("stacks")?.let {
                            comparisonValue(it) { n ->
                                n.integer().also { count ->
                                    if (count < 0)
                                        invalid(
                                            "aura_stacks",
                                            "Stacks cannot be negative",
                                            n.source,
                                        )
                                }
                            }
                        }
                    val remaining =
                        fields.optional("remaining")?.let {
                            comparisonValue(it) { n ->
                                ConfigSchemas.duration("Remaining time", true)
                                    .decode(n, context)
                                    .ticks
                            }
                        }
                    val timed =
                        fields.optional("timed")?.let {
                            ConfigSchemas.flag("Timed").decode(it, context)
                        }
                    fields.finish()
                    PlayerPredicate.HasAura(aura, stacks, remaining, timed)
                }
            }
            "player_state" -> {
                val fields = Fields(node.mapping())
                if (fields.node.entries.isEmpty())
                    invalid("empty_state", "Supply a player state check", node.source)
                val online =
                    fields.optional("online")?.let {
                        ConfigSchemas.flag("Connected").decode(it, context)
                    }
                val state = fields.optional("state")?.let { lifeSchema.decode(it, context) }
                val participation =
                    fields.optional("participation")?.let {
                        if (context.beforeAttempt)
                            invalid(
                                "prestart_state",
                                "Participation does not exist before an attempt",
                                it.source,
                            )
                        participationSchema.decode(it, context)
                    }
                fields.finish()
                PlayerPredicate.State(online, state, participation)
            }
            else ->
                invalid(
                    "unknown_predicate",
                    "Unknown player predicate '$kind'",
                    value.source.field(kind),
                )
        }
    }

    override fun encode(value: PlayerPredicate): String =
        when (value) {
            PlayerPredicate.Always ->
                error("The absent default predicate is omitted from its containing configuration")
            is PlayerPredicate.And ->
                "{\"and\":" + value.children.joinToString(",", "[", "]", transform = ::encode) + "}"
            is PlayerPredicate.Or ->
                "{\"or\":" + value.children.joinToString(",", "[", "]", transform = ::encode) + "}"
            is PlayerPredicate.Not -> "{\"not\":" + encode(value.condition) + "}"
            is PlayerPredicate.Identity ->
                error("Identity predicates are runtime-only; use a declared player source")
            is PlayerPredicate.PatternState ->
                "{\"pattern_state\":${PatternStateQuerySchema.encode(value.query)}}"
            is PlayerPredicate.InArea -> "{\"in_area\":" + local.encode(value.area) + "}"
            is PlayerPredicate.HasRole -> "{\"has_role\":" + local.encode(value.role) + "}"
            is PlayerPredicate.HasEffect ->
                "{\"has_effect\":" + NativeReferenceSchema.encode(value.effect) + "}"
            is PlayerPredicate.HasAura -> {
                val fields =
                    mutableListOf("\"aura\":" + DefinitionReferenceSchema.encode(value.aura))
                value.stacks?.let { fields += "\"stacks\":{${comparisonJson(it)}}" }
                value.remaining?.let {
                    fields +=
                        "\"remaining\":{${comparisonJson(it) { ticks -> ConfigSchemas.duration("Remaining", true).encode(SimulationDuration(ticks)) }}}"
                }
                value.timed?.let { fields += "\"timed\":$it" }
                "{\"has_aura\":{${fields.joinToString(",")}}}"
            }
            is PlayerPredicate.State -> {
                val fields = mutableListOf<String>()
                value.online?.let { fields += "\"online\":$it" }
                value.life?.let { fields += "\"state\":" + lifeSchema.encode(it) }
                value.participation?.let {
                    fields += "\"participation\":" + participationSchema.encode(it)
                }
                "{\"player_state\":{${fields.joinToString(",")}}}"
            }
        }
}

internal fun comparisonDescription(value: SchemaDescription) =
    SchemaDescription(
        "",
        "One comparison",
        alternatives = comparatorNames.keys.map { variantDescription(it, value) },
    )

internal fun comparisonValue(
    node: YamlValue,
    decode: (YamlValue) -> Long = { it.integer() },
): Comparison {
    val fields = Fields(node.mapping())
    val result = comparison(fields, decode)
    fields.finish()
    return result
}

internal val lifeSchema =
    ConfigSchemas.choice(
        "Life state; dead includes passed-out players",
        mapOf("alive" to LifeFilter.ALIVE, "dead" to LifeFilter.DEAD, "any" to LifeFilter.ANY),
    )
internal val participationSchema =
    ConfigSchemas.choice(
        "Participation in this attempt",
        ParticipationFilter.entries.associateBy { it.name.lowercase() },
    )

object PlayerSelectionSchema : ConfigSchema<PlayerSelection> {
    private val record: ConfigSchema<PlayerSelection> =
        ConfigRecordBuilder().run {
            val from =
                defaulted(
                    "from",
                    ConfigSchemas.choice(
                        "Player collection",
                        PlayerCollection.entries.associateBy { it.name.lowercase() },
                    ),
                    PlayerCollection.PARTICIPANTS,
                )
            val online =
                defaulted(
                    "online",
                    object : ConfigSchema<ConnectionFilter> {
                        override val description =
                            SchemaDescription(
                                "",
                                "Connection filter",
                                alternatives =
                                    listOf(
                                        ConfigSchemas.flag("Online or offline").description,
                                        SchemaDescription(
                                            "string",
                                            "Either",
                                            choices = listOf("any"),
                                        ),
                                    ),
                            )

                        override fun decode(
                            value: YamlValue,
                            context: SchemaContext,
                        ): ConnectionFilter =
                            when (value) {
                                is YamlValue.Flag ->
                                    if (value.value) ConnectionFilter.ONLINE
                                    else ConnectionFilter.OFFLINE
                                is YamlValue.Text ->
                                    if (value.value == "any") ConnectionFilter.ANY
                                    else
                                        invalid(
                                            "online_filter",
                                            "Use true, false, or any",
                                            value.source,
                                        )
                                else ->
                                    invalid(
                                        "online_filter",
                                        "Use true, false, or any",
                                        value.source,
                                    )
                            }

                        override fun encode(value: ConnectionFilter) =
                            when (value) {
                                ConnectionFilter.ONLINE -> "true"
                                ConnectionFilter.OFFLINE -> "false"
                                ConnectionFilter.ANY -> "\"any\""
                            }
                    },
                    ConnectionFilter.ONLINE,
                )
            val state = defaulted("state", lifeSchema, LifeFilter.ALIVE)
            val participation = optional("participation", participationSchema)
            val area =
                optional(
                    "area",
                    ConfigSchemas.identifier("Logical arena area").parameterType("area"),
                )
            val role =
                optional("role", ConfigSchemas.identifier("Declared role").parameterType("role"))
            val aura = optional("aura", DefinitionReferenceSchema.parameterType("aura"))
            val where = optional("where", PlayerPredicateSchema)
            build(
                "Select players from one coherent server observation",
                { values ->
                    require(
                        values[from] == PlayerCollection.PARTICIPANTS ||
                            values[online] != ConnectionFilter.OFFLINE
                    ) {
                        "Online collections cannot select offline players"
                    }
                    PlayerSelection(
                        values[from],
                        values[online],
                        values[state],
                        values[participation],
                        values[area],
                        values[role],
                        values[aura],
                        values[where] ?: PlayerPredicate.Always,
                    )
                },
                { value ->
                    mapOf(
                        from to value.from,
                        online to value.connection,
                        state to value.life,
                        participation to value.participation,
                        area to value.area,
                        role to value.role,
                        aura to value.aura,
                        where to value.where.takeUnless { it == PlayerPredicate.Always },
                    )
                },
            )
        }
    override val description
        get() = record.description.copy(parameterType = "players")

    override fun decode(value: YamlValue, context: SchemaContext): PlayerSelection {
        val mapping = value.mapping()
        if (!context.beforeAttempt) return record.decode(value, context)
        for (field in listOf("role", "participation")) mapping.entries[field]?.let {
            invalid("prestart_state", "'$field' does not exist before an attempt", it.source)
        }
        val decoded = record.decode(value, context)
        val selection =
            decoded.copy(
                from =
                    if ("from" in mapping.entries) decoded.from
                    else PlayerCollection.ONLINE_RAIDERS,
                life = if ("state" in mapping.entries) decoded.life else LifeFilter.ANY,
            )
        if (selection.from == PlayerCollection.PARTICIPANTS)
            invalid(
                "prestart_state",
                "The participant roster cannot select itself before an attempt",
                mapping.entries.getValue("from").source,
            )
        if (selection.connection == ConnectionFilter.OFFLINE)
            invalid(
                "online_filter",
                "Online collections cannot select offline players",
                mapping.entries.getValue("online").source,
            )
        return selection
    }

    override fun encode(value: PlayerSelection) = record.encode(value)
}
