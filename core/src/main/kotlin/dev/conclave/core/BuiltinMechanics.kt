package dev.conclave.core

import java.math.BigDecimal

/**
 * Built-ins are registered through the same typed description and factory as installed extensions.
 */
object BuiltinMechanics {
    fun registry(configure: (MechanicRegistry.Builder) -> Unit = {}): MechanicRegistry =
        MechanicRegistry.Builder()
            .apply {
                builtin(type("capture", capture, { MechanicEvents.capture() }, ::CaptureMechanic))
                builtin(
                    type("interact", interact, { MechanicEvents.interact() }, ::InteractMechanic)
                )
                builtin(type("deliver", deliver, { MechanicEvents.deliver() }, ::DeliverMechanic))
                builtin(type("defeat", defeat, { MechanicEvents.defeat() }, ::DefeatMechanic))
                builtin(type("match_pattern", pattern, MechanicEvents::pattern, ::PatternMechanic))
                CompositionMechanics.register(this)
                builtin(LayersMechanic)
                configure(this)
            }
            .freeze()

    private fun <C : Any> type(
        name: String,
        config: ConfigSchema<C>,
        contracts: (C) -> Map<String, EventContract>,
        factory: (C, MechanicContext) -> MechanicInstance,
    ) =
        object : MechanicType<C> {
            override val id = DefinitionId("conclave", name)
            override val schema = config
            override val help = config.description.help

            override fun events(configuration: C) = contracts(configuration)

            override fun interactionTargets(configuration: C): List<TargetReference> =
                when (configuration) {
                    is InteractConfiguration -> configuration.targets
                    is PatternConfiguration -> configuration.inputs.flatMap { it.targets }
                    else -> emptyList()
                }

            override fun patternInterface(configuration: C) =
                (configuration as? PatternConfiguration)?.let(::PatternInterface)

            override fun spatialReferences(configuration: C): SpatialReferences =
                when (configuration) {
                    is CaptureConfiguration ->
                        SpatialReferences(setOf(configuration.area)) +
                            configuration.players.spatialReferences()
                    is InteractConfiguration ->
                        SpatialReferences(
                            locations =
                                configuration.targets
                                    .filter { it.kind == TargetKind.BLOCK }
                                    .map { it.id }
                                    .toSet()
                        ) + configuration.players.spatialReferences()
                    is DeliverConfiguration ->
                        configuration.players.spatialReferences() +
                            when (val destination = configuration.destination) {
                                is DeliveryDestination.Area ->
                                    SpatialReferences(setOf(destination.area))
                                is DeliveryDestination.Interaction ->
                                    SpatialReferences(
                                        locations =
                                            if (destination.target.kind == TargetKind.BLOCK)
                                                setOf(destination.target.id)
                                            else emptySet()
                                    )
                            }
                    is PatternConfiguration ->
                        configuration.inputs.fold(configuration.players.spatialReferences()) {
                            result,
                            input ->
                            result +
                                input.players.spatialReferences() +
                                SpatialReferences(
                                    locations =
                                        input.targets
                                            .filter { it.kind == TargetKind.BLOCK }
                                            .map { it.id }
                                            .toSet()
                                )
                        }
                    else -> SpatialReferences.EMPTY
                }

            override fun create(configuration: C, context: MechanicContext) =
                factory(configuration, context)
        }

    val target: ConfigSchema<TargetReference> =
        object : ConfigSchema<TargetReference> {
            private val local = ConfigSchemas.identifier("Bound location or group")
            override val description =
                SchemaDescription(
                    "",
                    "A physical interaction target",
                    alternatives =
                        listOf(
                            variantDescription(
                                "block",
                                local.description.copy(parameterType = "location"),
                            ),
                            variantDescription(
                                "group",
                                local.description.copy(parameterType = "group"),
                            ),
                        ),
                )

            override fun decode(value: YamlValue, context: SchemaContext): TargetReference {
                val (kind, id) = singleField(value)
                val type =
                    when (kind) {
                        "block" -> TargetKind.BLOCK
                        "group" -> TargetKind.GROUP
                        else -> invalid("target_kind", "Choose block or group", value.source)
                    }
                return TargetReference(type, local.decode(id, context))
            }

            override fun encode(value: TargetReference) =
                "{${jsonString(value.kind.name.lowercase())}:${local.encode(value.id)}}"
        }

    val completion: ConfigSchema<CompletionRequirement> =
        object : ConfigSchema<CompletionRequirement> {
            private val count =
                ConfigSchemas.integer("Positive completion count", 1, Int.MAX_VALUE.toLong())
            override val description =
                SchemaDescription(
                    "",
                    "Required successes",
                    alternatives =
                        listOf(
                            SchemaDescription(
                                "string",
                                "All or any",
                                choices = listOf("all", "any"),
                            ),
                            variantDescription("count", count.description),
                        ),
                )

            override fun decode(value: YamlValue, context: SchemaContext): CompletionRequirement =
                when (value) {
                    is YamlValue.Text ->
                        when (value.value) {
                            "all" -> CompletionRequirement.All
                            "any" -> CompletionRequirement.Any
                            else ->
                                invalid(
                                    "completion",
                                    "Choose all, any, or {count: N}",
                                    value.source,
                                )
                        }
                    else -> {
                        val (key, node) = singleField(value)
                        if (key != "count") invalid("completion", "Expected count", value.source)
                        CompletionRequirement.Count(count.decode(node, context).toInt())
                    }
                }

            override fun encode(value: CompletionRequirement) =
                when (value) {
                    CompletionRequirement.All -> "\"all\""
                    CompletionRequirement.Any -> "\"any\""
                    is CompletionRequirement.Count -> "{\"count\":${value.count}}"
                }
        }

    val interruption: ConfigSchema<CaptureInterruption> =
        object : ConfigSchema<CaptureInterruption> {
            private val time = ConfigSchemas.duration("Time needed to drain a full capture meter")
            override val description =
                SchemaDescription(
                    "",
                    "Behavior while too few players qualify",
                    alternatives =
                        listOf(
                            SchemaDescription(
                                "string",
                                "Reset or pause",
                                choices = listOf("reset", "pause"),
                            ),
                            variantDescription("decay", time.description),
                        ),
                )

            override fun decode(value: YamlValue, context: SchemaContext): CaptureInterruption =
                when (value) {
                    is YamlValue.Text ->
                        when (value.value) {
                            "reset" -> CaptureInterruption.Reset
                            "pause" -> CaptureInterruption.Pause
                            else ->
                                invalid(
                                    "on_interrupt",
                                    "Choose reset, pause, or {decay: duration}",
                                    value.source,
                                )
                        }
                    else -> {
                        val (key, node) = singleField(value)
                        if (key != "decay") invalid("on_interrupt", "Expected decay", value.source)
                        CaptureInterruption.Decay(time.decode(node, context))
                    }
                }

            override fun encode(value: CaptureInterruption) =
                when (value) {
                    CaptureInterruption.Reset -> "\"reset\""
                    CaptureInterruption.Pause -> "\"pause\""
                    is CaptureInterruption.Decay -> "{\"decay\":${time.encode(value.duration)}}"
                }
        }

    val capture: ConfigSchema<CaptureConfiguration> =
        ConfigRecordBuilder().run {
            val area =
                required(
                    "area",
                    ConfigSchemas.identifier("Logical arena area").parameterType("area"),
                )
            val duration =
                required("duration", ConfigSchemas.duration("Required qualifying simulation time"))
            val playersRequired =
                defaulted(
                    "players_required",
                    ConfigSchemas.integer("Distinct eligible occupants", 1, Int.MAX_VALUE.toLong()),
                    1L,
                )
            val players = defaulted("players", PlayerSelectionSchema, PlayerSelection())
            val interrupt = defaulted("on_interrupt", interruption, CaptureInterruption.Reset)
            build(
                "Maintain enough eligible players inside an area",
                { v ->
                    CaptureConfiguration(
                        v[area],
                        v[duration],
                        v[playersRequired].toInt(),
                        v[players],
                        v[interrupt],
                    )
                },
                { v ->
                    mapOf(
                        area to v.area,
                        duration to v.duration,
                        playersRequired to v.required.toLong(),
                        players to v.players,
                        interrupt to v.interruption,
                    )
                },
            )
        }

    private val reach =
        ConfigSchemas.mapped(
            ConfigSchemas.decimal(
                "Maximum reach in blocks; cannot extend native reach",
                BigDecimal("0.000001"),
                BigDecimal("30000000"),
            ),
            BigDecimal::toDouble,
            BigDecimal::valueOf,
        )
    val interact: ConfigSchema<InteractConfiguration> =
        ConfigRecordBuilder().run {
            val targets = required("targets", ConfigSchemas.list("Physical targets", target, 1))
            val uses = defaulted("uses", ConfigSchemas.integer("Number of completed uses", 1), 1L)
            val distinct =
                defaulted("distinct_players", ConfigSchemas.flag("Credit each player once"), false)
            val hold =
                defaulted(
                    "hold",
                    ConfigSchemas.duration("Uninterrupted interaction time", true),
                    SimulationDuration(0),
                )
            val cooldown =
                defaulted(
                    "use_cooldown",
                    ConfigSchemas.duration("Per-player cooldown after a credited use", true),
                    SimulationDuration(0),
                )
            val players = defaulted("players", PlayerSelectionSchema, PlayerSelection())
            val reach = optional("reach", reach)
            val damage =
                defaulted(
                    "interrupt_on_damage",
                    ConfigSchemas.flag("Interrupt unfinished holds when damaged"),
                    false,
                )
            val consume =
                defaulted(
                    "consume_interaction",
                    ConfigSchemas.flag("Suppress native interaction for admitted input"),
                    false,
                )
            build(
                "Complete deliberate interactions with physical targets",
                { v ->
                    require(v[targets].distinct().size == v[targets].size) {
                        "Duplicate interaction target"
                    }
                    require(!v[distinct] || v[cooldown].ticks == 0L) {
                        "distinct_players cannot have a nonzero use_cooldown"
                    }
                    InteractConfiguration(
                        v[targets],
                        v[uses],
                        v[distinct],
                        v[hold],
                        v[cooldown],
                        v[players],
                        v[reach],
                        v[damage],
                        v[consume],
                    )
                },
                { v ->
                    mapOf(
                        targets to v.targets,
                        uses to v.uses,
                        distinct to v.distinctPlayers,
                        hold to v.hold,
                        cooldown to v.cooldown,
                        players to v.players,
                        reach to v.reach,
                        damage to v.interruptOnDamage,
                        consume to v.consume,
                    )
                },
            )
        }

    private val destination =
        object : ConfigSchema<DeliveryDestination> {
            private val area =
                ConfigSchemas.identifier("Logical destination area").parameterType("area")
            override val description =
                SchemaDescription(
                    "",
                    "Delivery destination",
                    alternatives =
                        target.description.alternatives +
                            variantDescription("area", area.description),
                )

            override fun decode(value: YamlValue, context: SchemaContext): DeliveryDestination {
                val (kind, node) = singleField(value)
                return if (kind == "area") DeliveryDestination.Area(area.decode(node, context))
                else DeliveryDestination.Interaction(target.decode(value, context))
            }

            override fun encode(value: DeliveryDestination) =
                when (value) {
                    is DeliveryDestination.Area -> "{\"area\":${area.encode(value.area)}}"
                    is DeliveryDestination.Interaction -> target.encode(value.target)
                }
        }
    val deliver: ConfigSchema<DeliverConfiguration> =
        ConfigRecordBuilder()
            .run {
                val relic =
                    required(
                        "relic",
                        ConfigSchemas.identifier("Logical relic instance")
                            .parameterType("relic_instance"),
                    )
                val destination = required("destination", destination)
                val trigger =
                    defaulted(
                        "trigger",
                        ConfigSchemas.choice(
                            "Delivery input",
                            mapOf("interact" to false, "enter" to true),
                        ),
                        false,
                    )
                val hold =
                    defaulted(
                        "hold",
                        ConfigSchemas.duration("Uninterrupted interaction time", true),
                        SimulationDuration(0),
                    )
                val players = defaulted("players", PlayerSelectionSchema, PlayerSelection())
                val reach = optional("reach", reach)
                val damage =
                    defaulted(
                        "interrupt_on_damage",
                        ConfigSchemas.flag("Interrupt a delivery hold on damage"),
                        false,
                    )
                val consume =
                    defaulted(
                        "consume_interaction",
                        ConfigSchemas.flag("Suppress native interaction for admitted input"),
                        false,
                    )
                build(
                    "Deliver one logical relic instance to a destination",
                    { v ->
                        require(v[trigger] == (v[destination] is DeliveryDestination.Area)) {
                            "Area delivery requires trigger: enter; interaction delivery requires trigger: interact"
                        }
                        require(
                            !v[trigger] ||
                                v[hold].ticks == 0L && v[reach] == null && !v[damage] && !v[consume]
                        ) {
                            "Area delivery does not accept interaction controls"
                        }
                        DeliverConfiguration(
                            v[relic],
                            v[destination],
                            v[players],
                            v[hold],
                            v[reach],
                            v[damage],
                            v[consume],
                        )
                    },
                    { v ->
                        mapOf(
                            relic to v.relic,
                            destination to v.destination,
                            trigger to (v.destination is DeliveryDestination.Area),
                            hold to
                                v.hold.takeIf { v.destination is DeliveryDestination.Interaction },
                            players to v.players,
                            reach to v.reach,
                            damage to
                                v.interruptOnDamage.takeIf {
                                    v.destination is DeliveryDestination.Interaction
                                },
                            consume to
                                v.consume.takeIf {
                                    v.destination is DeliveryDestination.Interaction
                                },
                        )
                    },
                )
            }
            .checked { config, node ->
                if (config.destination is DeliveryDestination.Area)
                    for (field in
                        listOf("hold", "reach", "interrupt_on_damage", "consume_interaction")) {
                        node.mapping().entries[field]?.let {
                            invalid(
                                "inapplicable_field",
                                "Area-entry delivery has no '$field' control",
                                it.source,
                            )
                        }
                    }
            }

    val defeat: ConfigSchema<DefeatConfiguration> =
        ConfigRecordBuilder().run {
            val group =
                required(
                    "group",
                    ConfigSchemas.identifier("Declared logical spawn group").parameterType("group"),
                )
            val completion = defaulted("completion", completion, CompletionRequirement.All)
            val causes =
                defaulted(
                    "causes",
                    ConfigSchemas.list(
                        "Qualifying terminal causes",
                        ConfigSchemas.choice(
                            "Cause",
                            DefeatCause.entries.associateBy { it.name.lowercase() },
                        ),
                        1,
                        2,
                    ),
                    DefeatCause.entries.toList(),
                )
            val damage =
                optional(
                    "damage_types",
                    ConfigSchemas.list(
                        "Qualifying fatal damage types",
                        NativeReferenceSchema.parameterType("damage_type"),
                        1,
                    ),
                )
            build(
                "Complete from retained terminal records of one group activation",
                { v ->
                    require(v[causes].distinct().size == v[causes].size) {
                        "Duplicate defeat cause"
                    }
                    require(v[damage] == null || v[damage]!!.distinct().size == v[damage]!!.size) {
                        "Duplicate damage type"
                    }
                    require(v[damage] == null || DefeatCause.DEATH in v[causes]) {
                        "damage_types requires death among the accepted causes"
                    }
                    DefeatConfiguration(
                        v[group],
                        v[completion],
                        v[causes].toSet(),
                        v[damage]?.toSet(),
                    )
                },
                { v ->
                    mapOf(
                        group to v.group,
                        completion to v.completion,
                        causes to v.causes.sortedBy { it.name },
                        damage to v.damageTypes?.sorted(),
                    )
                },
            )
        }

    internal val tokens =
        ConfigSchemas.list("Pattern token IDs", ConfigSchemas.identifier("Local token"), 1, 1024)

    internal data class PatternRecipe(
        val fixed: List<String>? = null,
        val pool: List<String>? = null,
        val length: Int? = null,
        val repeats: Boolean = false,
        val choices: List<List<String>>? = null,
    ) {
        fun bind(vocabulary: List<String>, ordered: Boolean): PatternAnswer {
            val supplied = fixed ?: choices?.flatten() ?: pool ?: vocabulary
            require(supplied.all { it in vocabulary }) { "Pattern contains an undeclared token" }
            if (choices != null) {
                val normalized = choices.map { if (ordered) it else it.sorted() }
                require(normalized.distinct().size == normalized.size) {
                    "Duplicate equivalent pattern alternatives"
                }
                return PatternAnswer.Choose(choices)
            }
            if (fixed != null) return PatternAnswer.Fixed(fixed)
            val source = pool ?: vocabulary
            require(source.distinct().size == source.size) {
                "Sample pool must contain unique tokens"
            }
            require(repeats || checkNotNull(length) <= source.size) {
                "Sample length exceeds the pool without repeats"
            }
            return PatternAnswer.Sample(source, checkNotNull(length), repeats)
        }
    }

    internal val recipe: ConfigSchema<PatternRecipe> =
        object : ConfigSchema<PatternRecipe> {
            private val sample =
                ConfigRecordBuilder().run {
                    val from = optional("from", tokens.parameterType("pattern_tokens"))
                    val length = required("length", ConfigSchemas.integer("Answer length", 1, 1024))
                    val repeats =
                        defaulted("repeats", ConfigSchemas.flag("Sample with replacement"), false)
                    build(
                        "Sample one stable answer",
                        { v ->
                            PatternRecipe(
                                pool = v[from],
                                length = v[length].toInt(),
                                repeats = v[repeats],
                            )
                        },
                        { v ->
                            mapOf(
                                from to v.pool,
                                length to v.length?.toLong(),
                                repeats to v.repeats,
                            )
                        },
                    )
                }
            private val alternatives = ConfigSchemas.list("Complete alternatives", tokens, 1, 256)
            override val description =
                SchemaDescription(
                    "",
                    "Fixed, sampled, or chosen answer",
                    alternatives =
                        listOf(
                            tokens.description,
                            variantDescription("sample", sample.description),
                            variantDescription("choose", alternatives.description),
                        ),
                )

            override fun decode(value: YamlValue, context: SchemaContext): PatternRecipe {
                if (value is YamlValue.Sequence)
                    return PatternRecipe(fixed = tokens.decode(value, context))
                val (kind, node) = singleField(value)
                return when (kind) {
                    "sample" -> sample.decode(node, context)
                    "choose" -> PatternRecipe(choices = alternatives.decode(node, context))
                    else -> invalid("pattern", "Use a token list, sample, or choose", value.source)
                }
            }

            override fun encode(value: PatternRecipe): String =
                when {
                    value.fixed != null -> tokens.encode(value.fixed)
                    value.choices != null -> "{\"choose\":${alternatives.encode(value.choices)}}"
                    else -> "{\"sample\":${sample.encode(value)}}"
                }
        }
    val patternInput: ConfigSchema<PatternInputConfiguration> =
        ConfigRecordBuilder().run {
            val token =
                required(
                    "token",
                    ConfigSchemas.identifier("Token submitted by this physical input"),
                )
            val targets = required("targets", ConfigSchemas.list("Physical targets", target, 1))
            val players = defaulted("players", PlayerSelectionSchema, PlayerSelection())
            val hold =
                defaulted(
                    "hold",
                    ConfigSchemas.duration("Uninterrupted input hold", true),
                    SimulationDuration(0),
                )
            val reach = optional("reach", reach)
            val damage =
                defaulted(
                    "interrupt_on_damage",
                    ConfigSchemas.flag("Interrupt holds on damage"),
                    false,
                )
            val consume =
                defaulted(
                    "consume_interaction",
                    ConfigSchemas.flag("Suppress native interaction for admitted input"),
                    false,
                )
            build(
                "A repeatable physical token input",
                { v ->
                    require(v[targets].distinct().size == v[targets].size) {
                        "Duplicate pattern target"
                    }
                    PatternInputConfiguration(
                        v[token],
                        v[targets],
                        v[players],
                        v[hold],
                        v[reach],
                        v[damage],
                        v[consume],
                    )
                },
                { v ->
                    mapOf(
                        token to v.token,
                        targets to v.targets,
                        players to v.players,
                        hold to v.hold,
                        reach to v.reach,
                        damage to v.interruptOnDamage,
                        consume to v.consume,
                    )
                },
            )
        }

    internal val patternInputs =
        ConfigSchemas.list("Physical token inputs", patternInput, 1).parameterType("pattern_inputs")

    val pattern: ConfigSchema<PatternConfiguration> =
        ConfigRecordBuilder().run {
            val tokens = required("tokens", tokens.parameterType("pattern_tokens"))
            val answer = required("pattern", recipe.parameterType("pattern"))
            val ordered =
                defaulted("ordered", ConfigSchemas.flag("Match in the specified order"), true)
            val progress =
                defaulted(
                    "progress",
                    ConfigSchemas.choice(
                        "Shared or individual progress",
                        mapOf("shared" to false, "per_player" to true),
                    ),
                    false,
                )
            val independent =
                defaulted(
                    "pattern_per_player",
                    ConfigSchemas.flag("Resolve independent personal answers"),
                    false,
                )
            val completion = optional("completion", completion)
            val players = defaulted("players", PlayerSelectionSchema, PlayerSelection())
            val inputs = optional("inputs", patternInputs)
            val cooldown =
                defaulted(
                    "use_cooldown",
                    ConfigSchemas.duration("Per-player physical-input cooldown", true),
                    SimulationDuration(0),
                )
            build(
                "Match a retained answer using admitted token submissions",
                { v ->
                    require(v[tokens].distinct().size == v[tokens].size) {
                        "Pattern vocabulary must contain unique tokens"
                    }
                    require(v[progress] || !v[independent]) {
                        "pattern_per_player requires per_player progress"
                    }
                    require(v[progress] || v[completion] == null) {
                        "Shared progress cannot have a player completion threshold"
                    }
                    require(v[inputs] != null || v[cooldown].ticks == 0L) {
                        "use_cooldown requires physical inputs"
                    }
                    PatternConfiguration(
                        v[tokens].toSet(),
                        v[answer].bind(v[tokens], v[ordered]),
                        v[ordered],
                        v[progress],
                        v[independent],
                        v[completion] ?: CompletionRequirement.All,
                        v[players],
                        v[inputs] ?: emptyList(),
                        v[cooldown],
                    )
                },
                { v ->
                    val construction =
                        when (val a = v.answer) {
                            is PatternAnswer.Fixed -> PatternRecipe(fixed = a.tokens)
                            is PatternAnswer.Sample ->
                                PatternRecipe(
                                    pool = a.tokens,
                                    length = a.count,
                                    repeats = a.replacement,
                                )
                            is PatternAnswer.Shuffle ->
                                PatternRecipe(pool = a.tokens, length = a.tokens.size)
                            is PatternAnswer.Choose -> PatternRecipe(choices = a.alternatives)
                        }
                    mapOf(
                        tokens to v.vocabulary.sorted(),
                        answer to construction,
                        ordered to v.ordered,
                        progress to v.perPlayer,
                        independent to v.independentAnswers,
                        completion to v.completion.takeIf { v.perPlayer },
                        players to v.players,
                        inputs to v.inputs.takeIf { it.isNotEmpty() },
                        cooldown to v.useCooldown,
                    )
                },
            )
        }
}
