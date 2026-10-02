package dev.conclave.core

import java.util.ArrayDeque
import java.util.Random
import java.util.UUID
import java.util.random.RandomGenerator

data class RuntimeScopeIdentity(val attempt: UUID, val generation: Long, val phase: String?)

/**
 * Native facts and operations. Implementations retain the captured arena and resource activations.
 */
interface AttemptWorld {
    /** Called once at the start of an admitted simulation step, before its new phase starts. */
    fun beginStep(tick: Long, combat: Boolean) {}

    /** Ready native held interactions settle before phase outcomes are frozen. */
    fun settleInteractions(tick: Long) {}

    /** Commit remaining grave expirations after ready result reactions; return party defeat. */
    fun finishResults(tick: Long): Boolean = false

    fun players(): PlayerFrame

    fun validTarget(
        scope: RuntimeScopeIdentity,
        player: UUID,
        target: TargetHandle,
        maximumReach: Double?,
    ): Boolean

    fun group(scope: RuntimeScopeIdentity, id: String): GroupObservation?

    fun relic(scope: RuntimeScopeIdentity, id: String): RelicObservation?

    fun selectedRelic(player: UUID): UUID?

    fun deliver(scope: RuntimeScopeIdentity, relic: UUID, generation: Long, holder: UUID): Boolean
}

sealed interface AttemptEvent {
    data class ScopeStarted(val scope: RuntimeScopeIdentity) : AttemptEvent

    data class MechanicStarted(val identity: MechanicIdentity) : AttemptEvent

    data class MechanicChanged(val identity: MechanicIdentity, val notice: MechanicNotice) :
        AttemptEvent

    data class TimerExpired(val scope: RuntimeScopeIdentity, val expiry: TimerExpiry) : AttemptEvent

    data class ParticipantChanged(val notice: ParticipantNotice) : AttemptEvent

    data class Exported(
        val identity: MechanicIdentity,
        val name: String,
        val payload: EventPayload,
        val origin: RuntimeScopeIdentity,
    ) : AttemptEvent

    data class Progression(val effect: ProgressionEffect) : AttemptEvent

    data class Fault(val cause: Exception) : AttemptEvent
}

data class MechanicSubmission(val target: MechanicIdentity, val input: MechanicInput)

data class InteractionClaim(
    val mechanic: MechanicIdentity,
    val input: MechanicInput.Press,
    val consume: Boolean,
    val hold: SimulationDuration = SimulationDuration(0),
)

/** Coordinates compiled scopes, mechanic progress, conditions, and the durable-success gate. */
class AttemptEngine(
    val attempt: UUID,
    val revision: String,
    private val definition: EncounterDefinition,
    private val world: AttemptWorld,
    private val limits: ExecutionLimits = ExecutionLimits(),
    seed: Long = Random().nextLong(),
) {
    private val ownerThread = Thread.currentThread()
    private val progression = PhaseProgression(attempt, revision, definition)
    private val random = Random(seed)

    private data class Listener(
        val scope: ActiveScope,
        val rule: RuleDefinition? = null,
        val export: MechanicExport? = null,
    )

    private data class Admitted(
        val event: AttemptEvent,
        val payload: EventPayload?,
        val listeners: List<Listener>,
    )

    private val ready = ArrayDeque<Admitted>()
    private val output = ArrayDeque<AttemptEvent>()
    private var generation = 0L
    private var order = 0L
    private var work = 0
    private var started = false
    private var advancing = false
    private var root: ActiveScope? = null
    private var phase: ActiveScope? = null
    private val activeScopes = linkedMapOf<Long, ActiveScope>()
    private val participantSequences = mutableMapOf<UUID, Long>()
    val state
        get() = progression.state

    val tick
        get() = progression.tick

    val isAdvancing
        get() = advancing

    private inner class ActiveMechanic(val scope: ActiveScope, val occurrence: MechanicOccurrence) {
        val identity = MechanicIdentity(attempt, nextGeneration(), occurrence.id)
        val order = nextOrder()
        private val randomSeed = random.nextLong()
        var lastUse: MechanicNotice.Used? = null
        private var initializing = false
        private val initializedNotices = mutableListOf<MechanicNotice>()
        private val context =
            object : MechanicContext {
                override val identity = this@ActiveMechanic.identity
                override val tick
                    get() = this@AttemptEngine.tick

                override fun players() = world.players()

                override fun notice(value: MechanicNotice) {
                    if (value is MechanicNotice.Used) lastUse = value
                    if (initializing) initializedNotices += value
                    else queue(AttemptEvent.MechanicChanged(identity, value))
                }

                override fun validTarget(
                    player: UUID,
                    target: TargetHandle,
                    maximumReach: Double?,
                ) = world.validTarget(scope.identity, player, target, maximumReach)

                override fun group(id: String) = world.group(scope.identity, id)

                override fun random(player: UUID?): RandomGenerator =
                    Random(
                        randomSeed xor
                            (player?.mostSignificantBits ?: 0) xor
                            (player?.leastSignificantBits ?: 0)
                    )

                override fun relic(id: String) = world.relic(scope.identity, id)

                override fun selectedRelic(player: UUID) = world.selectedRelic(player)

                override fun deliver(relic: UUID, generation: Long, holder: UUID) =
                    world.deliver(scope.identity, relic, generation, holder)
            }
        val instance: MechanicInstance =
            occurrence.mechanic.composition?.let {
                ActiveComposition(this, it, context)
            }
                ?: occurrence.mechanic.layers?.let { ActiveLayers(this, it, context) }
                ?: occurrence.mechanic.create(context)

        fun start() {
            spend()
            initializing = true
            try {
                instance.start()
            } finally {
                initializing = false
            }
            queue(AttemptEvent.MechanicStarted(identity))
            initializedNotices.forEach { queue(AttemptEvent.MechanicChanged(identity, it)) }
            initializedNotices.clear()
        }
    }

    private inner class ActiveScope(
        val identity: RuntimeScopeIdentity,
        val definition: ScopeDefinition,
        val parent: ActiveMechanic? = null,
    ) {
        val depth: Int = parent?.scope?.depth?.plus(1) ?: 0

        init {
            check(activeScopes.size < limits.scopes) { "Scope limit exceeded" }
            check(
                activeScopes.values.sumOf { it.definition.timers.size } + definition.timers.size <=
                    limits.timers
            ) {
                "Timer limit exceeded"
            }
            activeScopes[identity.generation] = this
        }

        var open = true
        val state = ScopeState(definition.counters, definition.timers, tick, ::nextOrder)
        val mechanics = linkedMapOf<String, ActiveMechanic>()
        val latched = mutableSetOf<String>()
        val invocations = mutableMapOf<String, MutableMap<UUID?, Long>>()

        fun start(children: List<MechanicOccurrence> = definition.allMechanics) {
            queue(AttemptEvent.ScopeStarted(identity))
            drainReady()
            children.forEach(::activate)
        }

        fun activate(occurrence: MechanicOccurrence) {
            check(open)
            check(activeScopes.values.sumOf { it.mechanics.size } < limits.scopes) {
                "Active mechanic limit exceeded"
            }
            val old = mechanics[occurrence.id]
            check(
                old == null ||
                    old.instance.state !in setOf(MechanicState.PENDING, MechanicState.RUNNING)
            )
            val active = ActiveMechanic(this, occurrence)
            mechanics[occurrence.id] = active
            active.start()
            drainReady()
            (active.instance as? ActiveNested)?.startChildren()
            drainReady()
        }

        fun close() {
            if (!open) return
            open = false
            var failure: Exception? = null
            for (mechanic in mechanics.values) try {
                mechanic.instance.cancel()
            } catch (cause: Exception) {
                if (failure == null) failure = cause else failure.addSuppressed(cause)
            }
            activeScopes.remove(identity.generation)
            failure?.let { throw it }
        }

        fun observation(): ConditionFrame {
            val mechanics =
                this.mechanics
                    .mapKeys { StateReference(it.key) }
                    .mapValues { it.value.instance.state }
                    .toMutableMap()
            val counters = state.counters().mapKeys { StateReference(it.key) }.toMutableMap()
            val timers = state.timers(tick).mapKeys { StateReference(it.key) }.toMutableMap()
            val encounter = checkNotNull(root)
            val patterns = linkedMapOf<StateReference, PatternObservation>()
            for ((id, mechanic) in this.mechanics) mechanic.instance.patternState()?.let {
                patterns[StateReference(id)] = it
            }
            for ((id, mechanic) in encounter.mechanics) mechanic.instance.patternState()?.let {
                patterns[StateReference(id, StateScope.ENCOUNTER)] = it
            }
            mechanics +=
                encounter.mechanics
                    .mapKeys { StateReference(it.key, StateScope.ENCOUNTER) }
                    .mapValues { it.value.instance.state }
            counters +=
                encounter.state.counters().mapKeys { StateReference(it.key, StateScope.ENCOUNTER) }
            timers +=
                encounter.state.timers(tick).mapKeys {
                    StateReference(it.key, StateScope.ENCOUNTER)
                }
            val players = world.players()
            val objectives = linkedMapOf<StateReference, Boolean>()
            val definitions = definition.objectives.associateBy { it.id }
            fun resolve(id: String): Boolean {
                val reference = StateReference(id)
                objectives[reference]?.let {
                    return it
                }
                val objective = definitions.getValue(id)
                val satisfied =
                    when (objective) {
                        is ObjectiveDefinition.Mechanic ->
                            mechanics[reference] == MechanicState.SUCCEEDED
                        is ObjectiveDefinition.Check -> {
                            if (id in latched) true
                            else {
                                objective.condition
                                    .references()
                                    .filter {
                                        it.first == ReferenceKind.OBJECTIVE &&
                                            it.second.scope == StateScope.LOCAL
                                    }
                                    .forEach { resolve(it.second.id) }
                                spend()
                                objective.condition
                                    .test(
                                        ConditionFrame(
                                            players,
                                            mechanics,
                                            objectives,
                                            counters,
                                            timers,
                                            patterns = patterns,
                                        )
                                    )
                                    .also { if (it && objective.latch) latched += id }
                            }
                        }
                    }
                objectives[reference] = satisfied
                return satisfied
            }
            definitions.keys.forEach(::resolve)
            return ConditionFrame(
                players,
                mechanics,
                objectives,
                counters,
                timers,
                patterns = patterns,
            )
        }
    }

    private abstract inner class ActiveNested(context: MechanicContext) :
        StatefulMechanic(context) {
        abstract fun startChildren()

        open fun beginTick() {}

        abstract fun settle(): Boolean
    }

    private inner class ActiveLayers(
        private val owner: ActiveMechanic,
        private val configuration: LayersConfiguration,
        context: MechanicContext,
    ) : ActiveNested(context) {
        private var children: ActiveScope? = null
        private var began = 0L

        override fun startChildren() {
            check(children == null && state == MechanicState.RUNNING)
            began = tick
            children =
                ActiveScope(
                        RuntimeScopeIdentity(attempt, nextGeneration(), owner.scope.identity.phase),
                        configuration.content,
                        owner,
                    )
                    .also { it.start() }
        }

        override fun settle(): Boolean {
            if (state != MechanicState.RUNNING) return false
            val scope = checkNotNull(children)
            val frame = scope.observation()
            val body = scope.definition
            val succeeded =
                configuration.duration?.let { tick - began >= it.ticks }
                    ?: body.completeWhen?.test(frame)
                    ?: (body.objectives.isNotEmpty() &&
                        body.objectives.all { frame.objectives.getValue(StateReference(it.id)) })
            val childFailed =
                body.objectives.filterIsInstance<ObjectiveDefinition.Mechanic>().any {
                    frame.mechanics[StateReference(it.id)] == MechanicState.FAILED
                }
            val conditionFailed = body.failWhen?.test(frame) == true
            val expired = configuration.deadline?.let { tick - began >= it.ticks } == true
            if (!succeeded && !childFailed && !conditionFailed && !expired) return false
            val success =
                succeeded &&
                    (!(childFailed || conditionFailed || expired) ||
                        definition.precedence == OutcomePrecedence.SUCCESS)
            val reason =
                when {
                    success -> null
                    childFailed -> "child_failed"
                    conditionFailed -> "condition"
                    else -> "deadline"
                }
            finish(success, reason = reason)
            scope.close()
            return true
        }

        override fun cancel() {
            super.cancel()
            children?.close()
        }
    }

    /** Composite outcomes settle after sibling operations and queued reactions. */
    private inner class ActiveComposition(
        private val owner: ActiveMechanic,
        private val configuration: CompositionConfiguration,
        context: MechanicContext,
    ) : ActiveNested(context) {
        private var children: ActiveScope? = null
        private var current = 0
        private var completed = 0L
        private var pendingAt: Long? = null

        override fun startChildren() {
            check(children == null && state == MechanicState.RUNNING)
            val scope =
                ActiveScope(
                    RuntimeScopeIdentity(attempt, nextGeneration(), owner.scope.identity.phase),
                    ScopeDefinition(mechanics = configuration.steps),
                    owner,
                )
            children = scope
            scope.start(emptyList())
            if (stopping()) {
                end(true)
                return
            }
            val initial =
                if (configuration.mode == CompositionConfiguration.Mode.PARALLEL)
                    configuration.steps
                else listOf(configuration.steps.first())
            initial.forEach(scope::activate)
        }

        override fun beginTick() {
            if (state != MechanicState.RUNNING || pendingAt?.let { it <= tick } != true) return
            if (stopping()) {
                end(true)
                return
            }
            pendingAt = null
            checkNotNull(children).activate(configuration.steps[current])
        }

        private fun stopping() =
            configuration.until?.test(checkNotNull(children).observation()) == true

        override fun settle(): Boolean {
            if (state != MechanicState.RUNNING) return false
            val scope = checkNotNull(children)
            val states = scope.mechanics.values.map { it.instance.state }
            val failed =
                if (
                    configuration.mode == CompositionConfiguration.Mode.PARALLEL &&
                        configuration.any
                )
                    states.size == configuration.steps.size &&
                        states.all { it == MechanicState.FAILED }
                else states.any { it == MechanicState.FAILED }
            val stop = stopping()
            if (stop || failed) {
                val success =
                    stop && (!failed || definition.precedence == OutcomePrecedence.SUCCESS)
                end(success)
                return true
            }
            if (pendingAt != null) return false
            when (configuration.mode) {
                CompositionConfiguration.Mode.PARALLEL -> {
                    val success =
                        if (configuration.any) states.any { it == MechanicState.SUCCEEDED }
                        else
                            states.size == configuration.steps.size &&
                                states.all { it == MechanicState.SUCCEEDED }
                    if (success) {
                        end(true)
                        return true
                    }
                }
                CompositionConfiguration.Mode.SEQUENCE -> {
                    if (
                        scope.mechanics[configuration.steps[current].id]?.instance?.state !=
                            MechanicState.SUCCEEDED
                    )
                        return false
                    if (++current == configuration.steps.size) end(true)
                    else pendingAt = Math.addExact(tick, 1)
                    return true
                }
                CompositionConfiguration.Mode.REPEAT -> {
                    if (
                        scope.mechanics[configuration.steps.single().id]?.instance?.state !=
                            MechanicState.SUCCEEDED
                    )
                        return false
                    completed = Math.incrementExact(completed)
                    if (configuration.count == completed) end(true)
                    else pendingAt = Math.addExact(tick, maxOf(1, configuration.delay.ticks))
                    return true
                }
            }
            return false
        }

        private fun end(success: Boolean) {
            finish(success, reason = "child_failed".takeUnless { success })
            children?.close()
            pendingAt = null
        }

        override fun cancel() {
            super.cancel()
            children?.close()
            pendingAt = null
        }
    }

    private fun settleCompositions() {
        var changed: Boolean
        do {
            changed = false
            val compositions =
                scopes()
                    .flatMap { it.mechanics.values }
                    .filter {
                        it.instance is ActiveNested && it.instance.state == MechanicState.RUNNING
                    }
                    .sortedWith(
                        compareByDescending<ActiveMechanic> { it.scope.depth }.thenBy { it.order }
                    )
            for (mechanic in compositions) {
                spend()
                if ((mechanic.instance as ActiveNested).settle()) changed = true
                drainReady()
            }
        } while (changed)
    }

    fun start() {
        checkThread()
        check(!started)
        started = true
        work = 0
        guarded {
            root =
                ActiveScope(
                    RuntimeScopeIdentity(attempt, nextGeneration(), null),
                    definition.content,
                )
            root!!.start()
            progressionEffects()
            drainReady()
        }
    }

    /**
     * Input is already bound to its connection, target and mechanic generation by the native
     * adapter.
     */
    fun advance(inputs: List<MechanicSubmission> = emptyList()) {
        checkThread()
        check(started)
        check(!advancing)
        if (!progression.beginStep()) return
        advancing = true
        work = 0
        guarded {
            val phaseId = (progression.state as? ProgressionState.Running)?.activation?.phase
            world.beginStep(
                tick,
                phaseId?.let { definition.byId.getValue(it).combat } ?: definition.combat,
            )
            drainReady()
            progressionEffects()
            for (mechanic in scopes().flatMap { it.mechanics.values }) {
                (mechanic.instance as? ActiveNested)?.beginTick()
                drainReady()
            }
            check(inputs.size <= limits.queuedWork) { "Input queue limit exceeded" }
            for (submission in inputs) {
                spend()
                val target =
                    scopes()
                        .flatMap { it.mechanics.values }
                        .firstOrNull { it.identity == submission.target } ?: continue
                target.instance.input(submission.input)
                drainReady()
            }
            data class Due(val time: Long, val order: Long, val run: () -> Unit)
            val operations =
                scopes()
                    .flatMap { scope ->
                        scope.mechanics.values
                            .filter { it.instance.state == MechanicState.RUNNING }
                            .map { mechanic ->
                                Due(tick, mechanic.order) {
                                    if (
                                        scope.open &&
                                            mechanic.instance.state == MechanicState.RUNNING
                                    )
                                        mechanic.instance.tick()
                                }
                            } +
                            scope.state.due(tick).map { due ->
                                Due(due.due, due.order) {
                                    if (scope.open)
                                        scope.state.expire(due, tick)?.let {
                                            queue(AttemptEvent.TimerExpired(scope.identity, it))
                                        }
                                }
                            }
                    }
                    .sortedWith(compareBy<Due> { it.time }.thenBy { it.order })
            for (operation in operations) {
                spend()
                operation.run()
                drainReady()
            }
            world.settleInteractions(tick)
            drainReady()
            settleCompositions()
            val active = phase
            val signals = mutableListOf<OutcomeSignal>()
            val phaseState = progression.state as? ProgressionState.Running
            if (active != null && phaseState != null) {
                val observation = active.observation()
                val definition = active.definition
                val succeeded =
                    definition.completeWhen?.test(observation)
                        ?: (definition.objectives.isNotEmpty() &&
                            definition.objectives.all {
                                observation.objectives.getValue(StateReference(it.id))
                            })
                val objectiveFailed =
                    definition.objectives.filterIsInstance<ObjectiveDefinition.Mechanic>().any {
                        observation.mechanics[StateReference(it.id)] == MechanicState.FAILED
                    }
                val failed = definition.failWhen?.test(observation) == true || objectiveFailed
                if (succeeded)
                    signals += OutcomeSignal(phaseState.activation, GameplayOutcome.SUCCESS)
                if (failed)
                    signals +=
                        OutcomeSignal(
                            phaseState.activation,
                            GameplayOutcome.FAILURE,
                            if (objectiveFailed) PhaseFailureReason.OBJECTIVE_FAILED
                            else PhaseFailureReason.CONDITION,
                        )
            }
            progression.settlePhase(signals) { it.test(checkNotNull(active).observation()) }
            progressionEffects()
            drainReady()
            val partyDefeated = world.finishResults(tick)
            drainReady()
            progression.finishStep(partyDefeated)
            progressionEffects()
            drainReady()
        }
        advancing = false
    }

    /**
     * The host supplies one authoritative observation and actual physical target identities.
     * Matching never dispatches rules. A native Use hook commits these claims before deciding
     * whether Minecraft should also handle that same gesture.
     */
    fun interactionClaims(
        player: UUID,
        gesture: UUID,
        targets: List<TargetHandle>,
    ): List<InteractionClaim> {
        checkThread()
        check(!advancing)
        require(targets.size <= 128)
        if (
            !started ||
                root?.open != true ||
                state !is ProgressionState.Running && state !is ProgressionState.Transition
        )
            return emptyList()
        val result = mutableListOf<InteractionClaim>()
        guarded {
            for (mechanic in scopes().flatMap { it.mechanics.values }) {
                spend()
                val input = mechanic.instance.interactionPress(player, gesture, targets) ?: continue
                check(result.size < 128) { "Interaction recipient limit exceeded" }
                result +=
                    InteractionClaim(
                        mechanic.identity,
                        input,
                        mechanic.instance.consumes(input),
                        mechanic.instance.interactionHold(input),
                    )
            }
        }
        return if (state is ProgressionState.Ended) emptyList() else java.util.List.copyOf(result)
    }

    /** Commit a frozen recipient list without recursively running its queued reactions. */
    fun admitInteraction(claims: List<InteractionClaim>): List<InteractionClaim> {
        checkThread()
        check(!advancing)
        require(claims.size <= 128 && claims.map { it.mechanic }.distinct().size == claims.size)
        if (
            !started ||
                root?.open != true ||
                state !is ProgressionState.Running && state !is ProgressionState.Transition
        )
            return emptyList()
        val accepted = mutableListOf<InteractionClaim>()
        guarded {
            val active = scopes().flatMap { it.mechanics.values }.associateBy { it.identity }
            for (claim in claims) {
                spend()
                val target = active[claim.mechanic] ?: continue
                val consume = target.instance.consumes(claim.input)
                if (target.instance.input(claim.input)) accepted += claim.copy(consume = consume)
            }
        }
        return if (state is ProgressionState.Ended) emptyList() else java.util.List.copyOf(accepted)
    }

    fun releaseInteraction(claims: List<InteractionClaim>) {
        checkThread()
        // Native revival can replace a player during world settlement. Release only removes held
        // input; it must take effect immediately without recursively dispatching queued reactions.
        require(claims.size <= 128)
        guarded {
            val active = scopes().flatMap { it.mechanics.values }.associateBy { it.identity }
            for (claim in claims) {
                spend()
                active[claim.mechanic]
                    ?.instance
                    ?.input(MechanicInput.Release(claim.input.player, claim.input.gesture))
            }
        }
    }

    /** Only the holder's native adapter requests progress for its already admitted claims. */
    fun interactionProgress(claims: List<InteractionClaim>): List<InteractionProgress> {
        checkThread()
        check(!advancing)
        require(claims.size <= 128)
        val active = scopes().flatMap { it.mechanics.values }.associateBy { it.identity }
        return claims.mapNotNull { claim ->
            active[claim.mechanic]
                ?.instance
                ?.interactionProgress(claim.input.player, claim.input.gesture)
        }
    }

    /** A positive committed native damage outcome, never an attempted or blocked hit. */
    fun interactionDamage(player: UUID) {
        checkThread()
        guarded {
            for (mechanic in scopes().flatMap { it.mechanics.values }) {
                spend()
                mechanic.instance.input(MechanicInput.Damaged(player))
            }
        }
    }

    fun mechanics(): List<MechanicIdentity> {
        checkThread()
        return scopes().flatMap { it.mechanics.values.map { m -> m.identity } }
    }

    /**
     * Admit immediately after a committed native transition; never run its listeners recursively.
     */
    fun participant(notice: ParticipantNotice): Boolean {
        checkThread()
        if (
            !started ||
                root?.open != true ||
                state !is ProgressionState.Running && state !is ProgressionState.Transition
        )
            return false
        val event = notice.event
        val player = event.after.player
        if (
            player !in world.players().roster ||
                event.sequence <= (participantSequences[player] ?: 0)
        )
            return false
        queue(AttemptEvent.ParticipantChanged(notice))
        participantSequences[player] = event.sequence
        return true
    }

    fun drainEvents(): List<AttemptEvent> {
        checkThread()
        val result = output.toList()
        output.clear()
        return result
    }

    fun acknowledge(operation: CompletionOperation, status: CommitStatus): Boolean {
        checkThread()
        check(!advancing)
        work = 0
        val accepted = progression.acknowledge(operation, status)
        progressionEffects()
        drainReady()
        return accepted
    }

    fun releasePendingCleanup() {
        checkThread()
        check(!advancing)
        work = 0
        progression.releasePendingCleanup()
        progressionEffects()
        drainReady()
    }

    fun stop() {
        checkThread()
        check(!advancing)
        work = 0
        progression.stop()
        progressionEffects()
        drainReady()
    }

    /** Native observation/ownership failures use the same terminal cleanup path as core faults. */
    fun technicalError(cause: Exception) {
        checkThread()
        check(!advancing)
        guarded { throw cause }
    }

    private fun progressionEffects() {
        for (effect in progression.drainEffects()) {
            when (effect) {
                is ProgressionEffect.PhaseStarted -> {
                    val content = definition.byId.getValue(effect.activation.phase).content
                    phase =
                        ActiveScope(
                            RuntimeScopeIdentity(
                                attempt,
                                nextGeneration(),
                                effect.activation.phase,
                            ),
                            content,
                        )
                    phase!!.start()
                }
                is ProgressionEffect.ClosePhase -> {
                    phase?.close()
                    phase = null
                }
                ProgressionEffect.CleanupAttempt -> scopes().forEach { it.close() }
                else -> Unit
            }
            queue(AttemptEvent.Progression(effect))
        }
    }

    private fun scopes(): List<ActiveScope> = activeScopes.values.filter { it.open }

    private data class Produced(
        val scope: ActiveScope,
        val kind: RuleSourceKind,
        val id: String?,
        val name: String,
        val payload: EventPayload,
    )

    private fun queue(event: AttemptEvent) {
        check(ready.size < limits.queuedWork) { "Reaction queue limit exceeded" }
        val produced = produced(event)
        val listeners =
            if (produced == null) emptyList()
            else
                buildList {
                    for (scope in scopes()) {
                        fun admit(listener: Listener) {
                            check(size < limits.queuedWork) { "Event subscriber limit exceeded" }
                            add(listener)
                        }
                        for (rule in scope.definition.rules) if (
                            matches(scope, rule.source, rule.event, produced, event)
                        )
                            admit(Listener(scope, rule = rule))
                        for (export in scope.parent?.occurrence?.mechanic?.exports.orEmpty()) if (
                            matches(
                                scope,
                                export.subscription.source,
                                export.subscription.event,
                                produced,
                                event,
                            )
                        )
                            admit(Listener(scope, export = export))
                    }
                }
        ready += Admitted(event, produced?.payload, listeners)
    }

    private fun matches(
        listener: ActiveScope,
        source: RuleSource,
        name: String,
        produced: Produced,
        event: AttemptEvent,
    ): Boolean {
        if (name != produced.name) return false
        if (source.kind == RuleSourceKind.PLAYERS)
            return event is AttemptEvent.ParticipantChanged &&
                checkNotNull(source.players)
                    .select(PlayerFrame(listOf(event.notice.before), world.players().roster))
                    .isNotEmpty()
        if (source.kind == RuleSourceKind.PHASE)
            return listener === root &&
                produced.scope === phase &&
                produced.scope.identity.phase == source.reference?.id &&
                (produced.kind == RuleSourceKind.PHASE || produced.kind == RuleSourceKind.SELF) &&
                progression.state !is ProgressionState.Finishing &&
                progression.state !is ProgressionState.Closing &&
                progression.state !is ProgressionState.Ended
        if (source.kind != produced.kind) return false
        if (source.kind == RuleSourceKind.SELF) return produced.scope === listener
        val reference = checkNotNull(source.reference)
        val owner = if (reference.scope == StateScope.ENCOUNTER) root else listener
        return produced.scope === owner && produced.id == reference.id
    }

    private fun drainReady() {
        while (ready.isNotEmpty()) {
            spend()
            check(output.size < limits.queuedWork) { "Undelivered event limit exceeded" }
            val admitted = ready.removeFirst()
            output += admitted.event
            for (listener in admitted.listeners) {
                spend()
                val scope = listener.scope
                if (!scope.open) continue
                val payload = checkNotNull(admitted.payload)
                val export = listener.export
                if (export != null) {
                    val playerField = export.subscription.playerField
                    if (playerField != null && payload.values[playerField] !is EventDatum.Player)
                        continue
                    if (export.guard?.test(scope.observation().withEvent(payload)) == false)
                        continue
                    export.data.forEach { _ -> spend() }
                    queue(
                        AttemptEvent.Exported(
                            checkNotNull(scope.parent).identity,
                            export.id,
                            export.payload(payload),
                            scope.identity,
                        )
                    )
                    continue
                }
                val rule = checkNotNull(listener.rule)
                val player =
                    rule.playerField?.let { (payload.values[it] as? EventDatum.Player)?.value }
                if (rule.playerField != null && player == null) continue
                val key = player.takeIf { rule.perPlayer }
                val buckets = scope.invocations.getOrPut(rule.id) { mutableMapOf() }
                val previous = buckets[key]
                if (previous != null && (rule.once || tick - previous < rule.cooldown.ticks))
                    continue
                if (rule.guard?.test(scope.observation().withEvent(payload)) == false) continue
                buckets[key] = tick
                for (action in rule.actions) {
                    spend()
                    if (!scope.open) break
                    val owner =
                        if (action.target.scope == StateScope.ENCOUNTER) checkNotNull(root)
                        else scope
                    check(owner.open) { "Action owner has ended" }
                    when (action) {
                        is RuleAction.Counter ->
                            owner.state.mutate(
                                action.target.id,
                                action.mutation,
                                action.value.resolve(payload),
                            )
                        is RuleAction.Timer ->
                            owner.state.mutate(action.target.id, action.mutation, tick)
                        is RuleAction.SubmitToken,
                        is RuleAction.ResetPattern -> {
                            val target =
                                checkNotNull(owner.mechanics[action.target.id]) {
                                    "Required matcher has not initialized"
                                }
                            val operation = UUID.randomUUID()
                            val input =
                                when (action) {
                                    is RuleAction.SubmitToken ->
                                        MechanicInput.Token(
                                            action.token,
                                            action.player?.resolve(payload),
                                            operation,
                                        )
                                    is RuleAction.ResetPattern ->
                                        MechanicInput.ResetPattern(
                                            action.player?.let { setOf(it.resolve(payload)) },
                                            operation,
                                        )
                                }
                            target.instance.input(input)
                        }
                    }
                }
            }
        }
    }

    private fun produced(event: AttemptEvent): Produced? {
        when (event) {
            is AttemptEvent.Exported -> {
                val owner =
                    scopes()
                        .flatMap { it.mechanics.values }
                        .single { it.identity == event.identity }
                return Produced(
                    owner.scope,
                    RuleSourceKind.MECHANIC,
                    event.identity.id,
                    event.name,
                    event.payload,
                )
            }
            is AttemptEvent.ParticipantChanged ->
                return Produced(
                    checkNotNull(root),
                    RuleSourceKind.PLAYERS,
                    null,
                    event.notice.event.type.name.lowercase(),
                    event.notice.event.payload(),
                )
            is AttemptEvent.ScopeStarted -> {
                val owner = scopes().single { it.identity == event.scope }
                return Produced(
                    owner,
                    RuleSourceKind.SELF,
                    null,
                    "started",
                    EventPayload(EventContract(emptyMap()), emptyMap()),
                )
            }
            is AttemptEvent.TimerExpired -> {
                val owner = scopes().single { it.identity == event.scope }
                val contract =
                    EventContract(
                        mapOf(
                            "timer" to EventField(EventValueKind.TIMER),
                            "duration" to EventField(EventValueKind.DURATION),
                        )
                    )
                val payload =
                    EventPayload(
                        contract,
                        mapOf(
                            "timer" to
                                EventDatum.Timer(
                                    event.scope,
                                    event.expiry.id,
                                    event.expiry.generation,
                                ),
                            "duration" to EventDatum.Duration(event.expiry.duration),
                        ),
                    )
                return Produced(owner, RuleSourceKind.TIMER, event.expiry.id, "expired", payload)
            }
            is AttemptEvent.MechanicStarted,
            is AttemptEvent.MechanicChanged -> {
                val identity =
                    when (event) {
                        is AttemptEvent.MechanicStarted -> event.identity
                        is AttemptEvent.MechanicChanged -> event.identity
                    }
                val active =
                    scopes().flatMap { it.mechanics.values }.single { it.identity == identity }
                val values = linkedMapOf<String, EventDatum>()
                fun player(id: UUID?) {
                    id?.let { values["player"] = EventDatum.Player(it) }
                }
                fun uses(value: MechanicNotice.Used) {
                    player(value.player)
                    values["uses_before"] = EventDatum.Integer(value.before)
                    values["uses_after"] = EventDatum.Integer(value.after)
                    values["uses_required"] = EventDatum.Integer(value.required)
                }
                val name =
                    if (event is AttemptEvent.MechanicStarted) "started"
                    else
                        when (val notice = (event as AttemptEvent.MechanicChanged).notice) {
                            is MechanicNotice.Result -> {
                                values["elapsed"] = EventDatum.Duration(notice.result.elapsed)
                                player(notice.result.player)
                                if (
                                    active.occurrence.mechanic.type ==
                                        DefinitionId("conclave", "interact")
                                )
                                    uses(checkNotNull(active.lastUse))
                                if (notice.result.state == MechanicState.FAILED) {
                                    values["reason"] =
                                        EventDatum.Text(checkNotNull(notice.result.reason))
                                    "failed"
                                } else "completed"
                            }
                            is MechanicNotice.Used -> {
                                uses(notice)
                                "used"
                            }
                            is MechanicNotice.PatternInput -> {
                                player(notice.player)
                                values["token"] = EventDatum.Text(notice.token)
                                values["origin"] = EventDatum.Text(notice.origin)
                                values["progress_before"] =
                                    EventDatum.Integer(notice.before.toLong())
                                values["progress_after"] = EventDatum.Integer(notice.after.toLong())
                                values["pattern_length"] =
                                    EventDatum.Integer(notice.required.toLong())
                                if (notice.matched) {
                                    values["record_completed"] =
                                        EventDatum.Flag(notice.recordCompleted)
                                    "matched"
                                } else "mismatched"
                            }
                            is MechanicNotice.PlayerCompleted -> {
                                player(notice.player)
                                values["pattern_length"] =
                                    EventDatum.Integer(notice.required.toLong())
                                "player_completed"
                            }
                            is MechanicNotice.PatternReset -> {
                                player(notice.player)
                                values["pattern_length"] =
                                    EventDatum.Integer(notice.required.toLong())
                                values["progress_before"] =
                                    EventDatum.Integer(notice.before.toLong())
                                values["progress_after"] = EventDatum.Integer(0)
                                "progress_reset"
                            }
                            is MechanicNotice.Delivery ->
                                error("Delivery progress is not a registered event")
                        }
                val contract =
                    active.occurrence.mechanic.events[name]
                        ?: error("Mechanic emitted an undeclared event")
                return Produced(
                    active.scope,
                    RuleSourceKind.MECHANIC,
                    identity.id,
                    name,
                    EventPayload(contract, values),
                )
            }
            is AttemptEvent.Progression -> {
                val effect = event.effect as? ProgressionEffect.PhaseEnded ?: return null
                val owner = checkNotNull(phase)
                val name = if (effect.outcome == GameplayOutcome.SUCCESS) "completed" else "failed"
                val values =
                    linkedMapOf<String, EventDatum>(
                        "elapsed" to EventDatum.Duration(effect.elapsed)
                    )
                when (val route = effect.route) {
                    is PhaseRoute.Next -> {
                        values["route"] = EventDatum.Text("next")
                        values["next_phase"] = EventDatum.Text(route.phase)
                    }
                    PhaseRoute.Complete -> values["route"] = EventDatum.Text("complete")
                    PhaseRoute.Wipe -> values["route"] = EventDatum.Text("wipe")
                    is PhaseRoute.Choose -> error("Terminal phase event requires a frozen route")
                }
                effect.reason?.let { values["reason"] = EventDatum.Text(it.name.lowercase()) }
                return Produced(
                    owner,
                    RuleSourceKind.PHASE,
                    effect.activation.phase,
                    name,
                    EventPayload(checkNotNull(phaseEventContract(name)), values),
                )
            }
            else -> return null
        }
    }

    private inline fun guarded(work: () -> Unit) {
        try {
            work()
        } catch (failure: Exception) {
            ready.clear()
            progression.technicalError()
            scopes().forEach { runCatching { it.close() } }
            if (output.size < limits.queuedWork) output += AttemptEvent.Fault(failure)
            progression.drainEffects().forEach {
                if (output.size < limits.queuedWork) output += AttemptEvent.Progression(it)
            }
        }
    }

    private fun spend() {
        check(++work <= limits.workPerTick) { "Attempt work limit exceeded" }
    }

    private fun nextOrder() = Math.incrementExact(order).also { order = it }

    private fun nextGeneration() = Math.incrementExact(generation).also { generation = it }

    private fun checkThread() {
        check(Thread.currentThread() === ownerThread) { "Attempt engine belongs to another thread" }
    }
}
