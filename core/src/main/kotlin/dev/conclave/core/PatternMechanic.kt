package dev.conclave.core

import java.util.UUID
import java.util.random.RandomGenerator

sealed interface PatternAnswer {
    fun resolve(random: RandomGenerator): List<String>

    class Fixed(tokens: List<String>) : PatternAnswer {
        val tokens: List<String> = java.util.List.copyOf(tokens).also { require(it.isNotEmpty()) }

        override fun resolve(random: RandomGenerator) = tokens
    }

    class Shuffle(tokens: List<String>) : PatternAnswer {
        val tokens: List<String> = java.util.List.copyOf(tokens).also { require(it.isNotEmpty()) }

        override fun resolve(random: RandomGenerator): List<String> {
            val result = tokens.toMutableList()
            for (index in result.lastIndex downTo 1) {
                val other = random.nextInt(index + 1)
                val item = result[index]
                result[index] = result[other]
                result[other] = item
            }
            return java.util.List.copyOf(result)
        }
    }

    class Sample(tokens: List<String>, val count: Int, val replacement: Boolean = false) :
        PatternAnswer {
        val tokens: List<String> = java.util.List.copyOf(tokens)

        init {
            require(
                tokens.isNotEmpty() &&
                    tokens.distinct().size == tokens.size &&
                    count > 0 &&
                    (replacement || count <= tokens.size)
            )
        }

        override fun resolve(random: RandomGenerator): List<String> =
            if (replacement) List(count) { tokens[random.nextInt(tokens.size)] }
            else Shuffle(tokens).resolve(random).take(count)
    }

    class Choose(alternatives: List<List<String>>) : PatternAnswer {
        val alternatives: List<List<String>> =
            java.util.List.copyOf(alternatives.map { java.util.List.copyOf(it) })

        init {
            require(alternatives.isNotEmpty() && alternatives.all { it.isNotEmpty() })
        }

        override fun resolve(random: RandomGenerator): List<String> =
            alternatives[random.nextInt(alternatives.size)]
    }
}

class PatternConfiguration(
    vocabulary: Set<String>,
    val answer: PatternAnswer,
    val ordered: Boolean = true,
    val perPlayer: Boolean = false,
    val independentAnswers: Boolean = false,
    val completion: CompletionRequirement = CompletionRequirement.All,
    val players: PlayerSelection = PlayerSelection(),
    inputs: List<PatternInputConfiguration> = emptyList(),
    val useCooldown: SimulationDuration = SimulationDuration(0),
) {
    val vocabulary: Set<String> = java.util.Set.copyOf(vocabulary)
    val inputs: List<PatternInputConfiguration> = java.util.List.copyOf(inputs)

    init {
        require(vocabulary.isNotEmpty() && vocabulary.all(::isAuthoredName))
        require(!independentAnswers || perPlayer)
        require(perPlayer || completion == CompletionRequirement.All)
        require(inputs.all { it.token in vocabulary })
        val targets = inputs.flatMap { it.targets }
        require(targets.distinct().size == targets.size) {
            "Pattern inputs must have distinct targets"
        }
        require(inputs.isNotEmpty() || useCooldown.ticks == 0L)
        if (answer is PatternAnswer.Choose) {
            val equivalents = answer.alternatives.map { if (ordered) it else it.sorted() }
            require(equivalents.distinct().size == equivalents.size)
        }
    }
}

class PatternInputConfiguration(
    val token: String,
    targets: List<TargetReference>,
    val players: PlayerSelection = PlayerSelection(),
    val hold: SimulationDuration = SimulationDuration(0),
    val reach: Double? = null,
    val interruptOnDamage: Boolean = false,
    val consume: Boolean = false,
) {
    val targets: List<TargetReference> = java.util.List.copyOf(targets)

    init {
        require(
            isAuthoredName(token) && targets.isNotEmpty() && targets.distinct().size == targets.size
        )
        require(reach == null || reach.isFinite() && reach > 0)
    }
}

/** Declared matcher capabilities for typed actions and validation; no resolved private answer. */
class PatternInterface internal constructor(configuration: PatternConfiguration) {
    val tokens: Set<String> = configuration.vocabulary
    val possibleTokens: Set<String> =
        java.util.Set.copyOf(
            when (val answer = configuration.answer) {
                is PatternAnswer.Fixed -> answer.tokens
                is PatternAnswer.Shuffle -> answer.tokens
                is PatternAnswer.Sample -> answer.tokens
                is PatternAnswer.Choose -> answer.alternatives.flatten()
            }
        )
    val perPlayer = configuration.perPlayer
    val inputTokens: Set<String> = java.util.Set.copyOf(configuration.inputs.map { it.token })
    val inputTargets: List<List<TargetReference>> =
        java.util.List.copyOf(configuration.inputs.map { it.targets })
}

class PatternMechanic(private val config: PatternConfiguration, context: MechanicContext) :
    StatefulMechanic(context) {
    private val bindings =
        config.inputs.flatMap { input -> input.targets.map { it to input } }.toMap()

    private class Record(val answer: List<String>) {
        val submitted = mutableListOf<String>()
        var done = false
    }

    private val records = linkedMapOf<UUID?, Record>()
    private var observation: PatternObservation? = null
    private val operations = mutableSetOf<UUID>()

    private data class Hold(
        val input: PatternInputConfiguration,
        val press: MechanicInput.Press,
        val began: Long,
    )

    private val holds = linkedMapOf<UUID, Hold>()
    private val gestures = mutableMapOf<UUID, UUID>()
    private val lastUse = mutableMapOf<UUID, Long>()

    override fun initialize() {
        val frame = context.players()
        val selected = config.players.select(frame)
        if (config.perPlayer)
            check(selected.all { it.id in frame.roster }) {
                "Pattern solvers must belong to the attempt"
            }
        if (
            config.perPlayer &&
                (selected.isEmpty() || config.completion.count(selected.size) > selected.size)
        ) {
            throw MechanicInitializationException("insufficient_players")
        }
        val shared = if (!config.independentAnswers) answer(null) else null
        if (config.perPlayer) selected.forEach { records[it.id] = Record(shared ?: answer(it.id)) }
        else records[null] = Record(checkNotNull(shared))
    }

    private fun answer(player: UUID?): List<String> =
        config.answer.resolve(context.random(player)).also {
            check(
                it.isNotEmpty() && it.size <= 1024 && it.all { token -> token in config.vocabulary }
            ) {
                "Pattern answer does not match its declared vocabulary"
            }
        }

    override fun input(value: MechanicInput): Boolean {
        if (state != MechanicState.RUNNING) return false
        return when (value) {
            is MechanicInput.Token -> submit(value)
            is MechanicInput.ResetPattern -> reset(value)
            is MechanicInput.Press -> press(value)
            is MechanicInput.Release -> {
                if (holds[value.player]?.press?.gesture == value.gesture) holds.remove(value.player)
                false
            }
            is MechanicInput.Damaged -> {
                if (holds[value.player]?.input?.interruptOnDamage == true)
                    holds.remove(value.player)
                false
            }
            else -> false
        }
    }

    override fun consumes(value: MechanicInput) =
        value is MechanicInput.Press && bindings[value.target.reference]?.consume == true

    override fun interactionHold(value: MechanicInput.Press) =
        bindings[value.target.reference]?.hold ?: SimulationDuration(0)

    override fun interactionProgress(player: UUID, gesture: UUID): InteractionProgress? =
        holds[player]
            ?.takeIf { it.press.gesture == gesture && state == MechanicState.RUNNING }
            ?.let {
                InteractionProgress(
                    SimulationDuration((context.tick - it.began).coerceIn(0, it.input.hold.ticks)),
                    it.input.hold,
                )
            }

    override fun acceptsPress(value: MechanicInput.Press): Boolean {
        val input = bindings[value.target.reference] ?: return false
        return state == MechanicState.RUNNING &&
            gestures[value.player] != value.gesture &&
            physicalEligibility(input, value) &&
            lastUse[value.player]?.let { context.tick - it < config.useCooldown.ticks } != true
    }

    override fun interactionPress(
        player: UUID,
        gesture: UUID,
        targets: List<TargetHandle>,
    ): MechanicInput.Press? {
        if (state != MechanicState.RUNNING || !eligible(player, config.players)) return null
        val candidates = targets.mapNotNull { target ->
            bindings[target.reference]?.let { it to target }
        }
        check(candidates.map { it.first }.distinct().size <= 1) {
            "Ambiguous physical pattern input"
        }
        return candidates
            .asSequence()
            .map { MechanicInput.Press(player, gesture, it.second) }
            .firstOrNull(::acceptsPress)
    }

    private fun press(value: MechanicInput.Press): Boolean {
        if (gestures[value.player] == value.gesture) return false
        holds.remove(value.player)
        val input = bindings[value.target.reference] ?: return false
        gestures[value.player] = value.gesture
        if (
            !physicalEligibility(input, value) ||
                lastUse[value.player]?.let { context.tick - it < config.useCooldown.ticks } == true
        )
            return false
        if (input.hold.ticks == 0L) return physicalSubmit(input, value)
        holds[value.player] = Hold(input, value, context.tick)
        return true
    }

    private fun physicalEligibility(
        input: PatternInputConfiguration,
        value: MechanicInput.Press,
    ): Boolean =
        eligible(value.player, config.players) &&
            eligible(value.player, input.players) &&
            records[if (config.perPlayer) value.player else null]?.done == false &&
            context.validTarget(value.player, value.target, input.reach)

    private fun physicalSubmit(
        input: PatternInputConfiguration,
        press: MechanicInput.Press,
    ): Boolean {
        val accepted =
            submit(MechanicInput.Token(input.token, press.player, press.gesture), "interaction")
        if (accepted) lastUse[press.player] = context.tick
        return accepted
    }

    override fun tick() {
        if (state != MechanicState.RUNNING) return
        for ((player, pending) in holds.toMap()) {
            if (!physicalEligibility(pending.input, pending.press)) holds.remove(player)
            else if (context.tick - pending.began >= pending.input.hold.ticks) {
                holds.remove(player)
                physicalSubmit(pending.input, pending.press)
                if (state != MechanicState.RUNNING) break
            }
        }
    }

    private fun submit(value: MechanicInput.Token, origin: String = "action"): Boolean {
        require(value.token in config.vocabulary) { "Undeclared pattern token" }
        if (value.operation in operations) return false
        val frame = context.players()
        if (
            value.player != null &&
                (value.player !in frame.roster ||
                    config.players.select(frame).none { it.id == value.player })
        )
            return false
        if (config.perPlayer && value.player == null) return false
        val key = if (config.perPlayer) value.player else null
        val record = records[key] ?: return false
        if (record.done) return false
        remember(value.operation)
        val before = record.submitted.size
        val matches =
            if (config.ordered) record.answer[before] == value.token
            else
                record.submitted.count { it == value.token } <
                    record.answer.count { it == value.token }
        if (matches) record.submitted += value.token else record.submitted.clear()
        record.done = record.submitted.size == record.answer.size
        observation = null
        val notices =
            mutableListOf<MechanicNotice>(
                MechanicNotice.PatternInput(
                    value.player,
                    value.token,
                    matches,
                    before,
                    record.submitted.size,
                    record.answer.size,
                    record.done,
                    origin,
                )
            )
        if (record.done && config.perPlayer)
            notices +=
                MechanicNotice.PlayerCompleted(checkNotNull(value.player), record.answer.size)
        val completed = records.values.count { it.done } >= config.completion.count(records.size)
        if (completed) {
            holds.clear()
            observation = snapshot()
            records.clear()
            operations.clear()
            gestures.clear()
            lastUse.clear()
            finish(true, preceding = notices)
        } else notices.forEach(context::notice)
        return true
    }

    private fun reset(value: MechanicInput.ResetPattern): Boolean {
        if (value.operation in operations) return false
        require(config.perPlayer || value.players == null)
        remember(value.operation)
        holds.keys.removeIf { !config.perPlayer || value.players == null || it in value.players }
        val notices = mutableListOf<MechanicNotice.PatternReset>()
        for ((player, record) in records) {
            if (record.done || value.players != null && player !in value.players) continue
            val before = record.submitted.size
            record.submitted.clear()
            if (before > 0)
                notices += MechanicNotice.PatternReset(player, before, record.answer.size)
        }
        observation = null
        notices.forEach(context::notice)
        return true
    }

    private fun remember(operation: UUID) {
        check(operations.size < 65_536) { "Pattern operation retention limit exceeded" }
        operations += operation
    }

    override fun patternState(): PatternObservation? =
        when (state) {
            MechanicState.RUNNING -> observation ?: snapshot().also { observation = it }
            MechanicState.SUCCEEDED -> observation
            else -> null
        }

    private fun snapshot(): PatternObservation {
        fun state(record: Record) =
            PatternRecordState(record.submitted.size, record.answer.size, record.done)
        return if (config.perPlayer)
            PatternObservation(
                records = records.mapKeys { checkNotNull(it.key) }.mapValues { state(it.value) }
            )
        else PatternObservation(shared = state(records.getValue(null)))
    }

    fun progress(player: UUID? = null): Int? = patternState()?.record(player)?.progress

    fun completed(player: UUID? = null): Boolean = patternState()?.record(player)?.completed == true

    /**
     * Authorized presentation adapter only; never include this in public progress events or
     * ordinary logs.
     */
    fun expected(player: UUID? = null): List<String>? =
        records[if (config.perPlayer) player else null]?.answer?.let { java.util.List.copyOf(it) }

    override fun cancel() {
        super.cancel()
        holds.clear()
        if (state == MechanicState.CANCELLED) {
            records.clear()
            observation = null
            operations.clear()
            gestures.clear()
            lastUse.clear()
        }
    }
}

class MechanicInitializationException(val reason: String) :
    IllegalStateException("Mechanic initialization failed: $reason")
