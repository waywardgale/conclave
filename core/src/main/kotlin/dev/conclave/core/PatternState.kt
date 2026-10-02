package dev.conclave.core

import java.math.BigDecimal
import java.util.UUID

/** Counts only. Successful summaries do not retain answers or submitted token histories. */
data class PatternRecordState(val progress: Int, val length: Int, val completed: Boolean) {
    init {
        require(length > 0 && progress in 0..length && completed == (progress == length))
    }
}

class PatternObservation(
    val shared: PatternRecordState? = null,
    records: Map<UUID, PatternRecordState> = emptyMap(),
) {
    val records: Map<UUID, PatternRecordState> = java.util.Map.copyOf(records)
    val completedPlayers: Long = records.values.count { it.completed }.toLong()

    init {
        require((shared != null) != records.isNotEmpty())
    }

    fun record(player: UUID? = null): PatternRecordState? = shared ?: player?.let(records::get)
}

data class PatternProgressComparison(
    val comparator: Comparator,
    val count: Long? = null,
    val percentage: BigDecimal? = null,
) {
    init {
        require((count != null) != (percentage != null))
        require(count == null || count >= 0)
        require(
            percentage == null || percentage >= BigDecimal.ZERO && percentage <= BigDecimal(100)
        )
    }

    fun test(record: PatternRecordState): Boolean {
        if (count != null) return Comparison(comparator, count).test(record.progress.toLong())
        // Cross multiplication preserves exact ratios, including repeating decimal fractions.
        val result =
            BigDecimal(record.progress)
                .multiply(BigDecimal(100))
                .compareTo(checkNotNull(percentage).multiply(BigDecimal(record.length)))
        return when (comparator) {
            Comparator.EQUALS -> result == 0
            Comparator.AT_LEAST -> result >= 0
            Comparator.AT_MOST -> result <= 0
            Comparator.GREATER_THAN -> result > 0
            Comparator.LESS_THAN -> result < 0
        }
    }
}

data class PatternStateQuery(
    val mechanic: StateReference,
    val player: PlayerOperand? = null,
    val progress: PatternProgressComparison? = null,
    val completed: Boolean? = null,
    val completedPlayers: Comparison? = null,
) {
    init {
        require(progress != null || completed != null || completedPlayers != null)
        require(completedPlayers == null || player == null && progress == null && completed == null)
    }

    fun test(frame: ConditionFrame, implicitPlayer: UUID? = null): Boolean {
        val state = frame.patterns[mechanic] ?: return false
        if (completedPlayers != null)
            return state.shared == null && completedPlayers.test(state.completedPlayers)
        val subject =
            if (player != null)
                (frame.event[player.field] as? EventDatum.Player)?.value ?: return false
            else implicitPlayer
        val record = state.record(subject) ?: return false
        return (progress == null || progress.test(record)) &&
            (completed == null || record.completed == completed)
    }

    internal fun validate(
        contract: PatternInterface,
        implicitPlayer: Boolean,
        source: SourceLocation,
    ) {
        if (!contract.perPlayer && (player != null || completedPlayers != null))
            invalid(
                "pattern_state_subject",
                "Shared pattern state does not accept player or completed_players",
                source,
            )
        if (contract.perPlayer && completedPlayers == null && player == null && !implicitPlayer)
            invalid(
                "pattern_state_subject",
                "Per-player pattern state requires player or an enclosing member condition",
                source,
            )
    }
}

internal object PatternStateQuerySchema : ConfigSchema<PatternStateQuery> {
    private val count = ConfigSchemas.integer("Nonnegative count", 0).description
    private val player =
        variantDescription("event", ConfigSchemas.identifier("Player event field").description)
    private val progress =
        comparisonDescription(
            SchemaDescription(
                "",
                "Token count or explicit percentage",
                alternatives =
                    listOf(
                        count,
                        SchemaDescription(
                            "string",
                            "Exact percentage from 0% to 100%",
                            maxLength = 128,
                            pattern = "^[0-9]+(?:\\.[0-9]+)?%$",
                            format = "percentage",
                        ),
                    ),
            )
        )
    override val description =
        SchemaDescription(
            "",
            "Current or retained successful pattern state",
            alternatives =
                listOf(
                    objectDescription(
                        "Compare progress and optional completion",
                        mapOf(
                            "mechanic" to StateReferenceSchema.description,
                            "player" to player,
                            "progress" to progress,
                            "completed" to ConfigSchemas.flag("Record completion").description,
                        ),
                        setOf("mechanic", "progress"),
                    ),
                    objectDescription(
                        "Compare record completion",
                        mapOf(
                            "mechanic" to StateReferenceSchema.description,
                            "player" to player,
                            "completed" to ConfigSchemas.flag("Record completion").description,
                        ),
                        setOf("mechanic", "completed"),
                    ),
                    objectDescription(
                        "Count completed captured players",
                        mapOf(
                            "mechanic" to StateReferenceSchema.description,
                            "completed_players" to comparisonDescription(count),
                        ),
                    ),
                ),
        )

    override fun decode(value: YamlValue, context: SchemaContext): PatternStateQuery {
        val fields = Fields(value.mapping())
        val mechanic = StateReferenceSchema.decode(fields.required("mechanic"), context)
        val player =
            fields.optional("player")?.let {
                val (kind, field) = singleField(it)
                val name = field.text()
                if (
                    kind != "event" ||
                        context.event?.fields?.get(name)?.kind != EventValueKind.PLAYER
                )
                    invalid(
                        "event_player",
                        "Pattern player must reference a player field in this event",
                        it.source,
                    )
                PlayerOperand(name)
            }
        val progress =
            fields.optional("progress")?.let { node ->
                val (operator, threshold) = singleField(node)
                val comparator =
                    comparatorNames[operator]
                        ?: invalid("pattern_comparison", "Use one numeric comparator", node.source)
                when (threshold) {
                    is YamlValue.Number ->
                        PatternProgressComparison(comparator, count = count(threshold))
                    is YamlValue.Text -> {
                        val text = threshold.value
                        if (text.length > 128 || !Regex("[0-9]+(?:\\.[0-9]+)?%").matches(text))
                            invalid(
                                "pattern_percentage",
                                "Use an explicit percentage from 0% to 100%",
                                threshold.source,
                            )
                        val percentage = BigDecimal(text.dropLast(1))
                        if (percentage > BigDecimal(100))
                            invalid(
                                "pattern_percentage",
                                "Percentage cannot exceed 100%",
                                threshold.source,
                            )
                        PatternProgressComparison(comparator, percentage = percentage)
                    }
                    else ->
                        invalid(
                            "pattern_comparison",
                            "Use a whole token count or an explicit percentage",
                            threshold.source,
                        )
                }
            }
        val completed =
            fields.optional("completed")?.let {
                ConfigSchemas.flag("Record completion").decode(it, context)
            }
        val completedPlayers =
            fields.optional("completed_players")?.let { comparisonValue(it, ::count) }
        fields.finish()
        if (progress == null && completed == null && completedPlayers == null)
            invalid(
                "pattern_state_check",
                "Supply progress, completed, or completed_players",
                value.source,
            )
        if (completedPlayers != null && (player != null || progress != null || completed != null))
            invalid(
                "pattern_state_check",
                "completed_players cannot be combined with record checks or player",
                value.source,
            )
        return PatternStateQuery(mechanic, player, progress, completed, completedPlayers)
    }

    private fun count(value: YamlValue) =
        ConfigSchemas.integer("Nonnegative count", 0).decode(value)

    override fun encode(value: PatternStateQuery): String {
        val fields = mutableListOf("\"mechanic\":${StateReferenceSchema.encode(value.mechanic)}")
        value.player?.let { fields += "\"player\":${it.canonical()}" }
        value.progress?.let { comparison ->
            val operator = comparatorNames.entries.single { it.value == comparison.comparator }.key
            val threshold =
                comparison.count?.toString()
                    ?: jsonString(
                        "${checkNotNull(comparison.percentage).stripTrailingZeros().toPlainString()}%"
                    )
            fields += "\"progress\":{${jsonString(operator)}:$threshold}"
        }
        value.completed?.let { fields += "\"completed\":$it" }
        value.completedPlayers?.let { fields += "\"completed_players\":{${comparisonJson(it)}}" }
        return fields.joinToString(",", "{", "}")
    }
}

internal fun Condition.patternQueries(): List<Pair<PatternStateQuery, Boolean>> =
    when (this) {
        is Condition.And -> children.flatMap { it.patternQueries() }
        is Condition.Or -> children.flatMap { it.patternQueries() }
        is Condition.Not -> child.patternQueries()
        is Condition.PatternState -> listOf(query to false)
        is Condition.Players -> satisfy.patternQueries().map { it to true }
        else -> emptyList()
    }

internal fun PlayerPredicate.patternQueries(): List<PatternStateQuery> =
    when (this) {
        is PlayerPredicate.And -> children.flatMap { it.patternQueries() }
        is PlayerPredicate.Or -> children.flatMap { it.patternQueries() }
        is PlayerPredicate.Not -> condition.patternQueries()
        is PlayerPredicate.PatternState -> listOf(query)
        else -> emptyList()
    }
