package dev.conclave.core

import java.util.UUID
import java.util.random.RandomGenerator

enum class MechanicState {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

data class MechanicIdentity(val attempt: UUID, val activation: Long, val id: String)

data class MechanicResult(
    val state: MechanicState,
    val player: UUID? = null,
    val reason: String? = null,
    val elapsed: SimulationDuration,
)

enum class TargetKind {
    BLOCK,
    GROUP,
}

data class TargetReference(val kind: TargetKind, val id: String)

data class TargetHandle(val reference: TargetReference, val identity: UUID)

data class InteractionProgress(val elapsed: SimulationDuration, val required: SimulationDuration)

/**
 * Typed observations are admitted by the native adapter. They carry actual target and gesture
 * identities.
 */
sealed interface MechanicInput {
    data class Press(val player: UUID, val gesture: UUID, val target: TargetHandle) : MechanicInput

    data class Release(val player: UUID, val gesture: UUID) : MechanicInput

    data class Damaged(val player: UUID) : MechanicInput

    data class Delivered(val player: UUID, val relic: UUID, val destination: String) : MechanicInput

    data class AreaEntered(val player: UUID, val area: String) : MechanicInput

    data class Token(val token: String, val player: UUID?, val operation: UUID) : MechanicInput

    data class ResetPattern(val players: Set<UUID>? = null, val operation: UUID) : MechanicInput
}

sealed interface MechanicNotice {
    data class Result(val result: MechanicResult) : MechanicNotice

    data class Used(val player: UUID, val before: Long, val after: Long, val required: Long) :
        MechanicNotice

    data class Delivery(
        val player: UUID,
        val relic: UUID,
        val before: Long,
        val after: Long,
        val required: Long,
    ) : MechanicNotice

    data class PatternInput(
        val player: UUID?,
        val token: String,
        val matched: Boolean,
        val before: Int,
        val after: Int,
        val required: Int,
        val recordCompleted: Boolean,
        val origin: String = "action",
    ) : MechanicNotice

    data class PlayerCompleted(val player: UUID, val required: Int) : MechanicNotice

    data class PatternReset(val player: UUID?, val before: Int, val required: Int) : MechanicNotice
}

/**
 * All gameplay methods are invoked by the owning server thread and are bounded by its activation.
 */
interface MechanicContext {
    val identity: MechanicIdentity
    val tick: Long

    fun players(): PlayerFrame

    fun notice(value: MechanicNotice)

    fun validTarget(player: UUID, target: TargetHandle, maximumReach: Double? = null): Boolean

    fun group(id: String): GroupObservation?

    fun random(player: UUID? = null): RandomGenerator

    fun relic(id: String): RelicObservation?

    fun selectedRelic(player: UUID): UUID?

    fun deliver(relic: UUID, generation: Long, holder: UUID): Boolean
}

data class RelicObservation(val identity: UUID, val generation: Long, val holder: UUID?)

enum class MemberOutcome {
    ALIVE,
    DEFEATED,
    DESPAWNED,
}

enum class DefeatCause {
    DEATH,
    SELF_DESTRUCT,
}

enum class ObservedTruth {
    TRUE,
    FALSE,
    UNAVAILABLE,
}

data class QualificationId(val id: UUID)

class GroupMember(
    val id: UUID,
    val outcome: MemberOutcome,
    val killer: UUID? = null,
    val cause: DefeatCause? = null,
    val damageType: String? = null,
    qualifications: Map<QualificationId, ObservedTruth> = emptyMap(),
) {
    val qualifications: Map<QualificationId, ObservedTruth> = java.util.Map.copyOf(qualifications)

    init {
        require(outcome != MemberOutcome.DEFEATED || cause != null)
    }
}

class GroupObservation(val activation: UUID, val closed: Boolean, members: List<GroupMember>) {
    val members: List<GroupMember> = java.util.List.copyOf(members)

    init {
        require(members.map { it.id }.distinct().size == members.size)
    }
}

interface MechanicInstance {
    val state: MechanicState
    val consumesInput: Boolean
        get() = false

    fun consumes(value: MechanicInput): Boolean = consumesInput

    /** Side-effect-free eligibility for one fresh native Use observation. */
    fun acceptsPress(value: MechanicInput.Press): Boolean = false

    val interactionHold: SimulationDuration
        get() = SimulationDuration(0)

    fun interactionProgress(player: UUID, gesture: UUID): InteractionProgress? = null

    fun start()

    fun input(value: MechanicInput): Boolean

    fun tick()

    fun cancel()
}

interface MechanicType<C : Any> {
    val id: DefinitionId
    val schema: ConfigSchema<C>
    val help: String

    fun events(configuration: C): Map<String, EventContract> = MechanicEvents.standard

    fun spatialReferences(configuration: C): SpatialReferences = SpatialReferences.EMPTY

    fun interactionTargets(configuration: C): List<TargetReference> = emptyList()

    fun composition(configuration: C): CompositionConfiguration? = null

    fun layers(configuration: C): LayersConfiguration? = null

    fun bindScope(configuration: C, encounter: ScopeDefinition): C = configuration

    fun create(configuration: C, context: MechanicContext): MechanicInstance
}

interface CompiledMechanic {
    val interactionTargets: List<TargetReference>
        get() = emptyList()

    val exports: List<MechanicExport>
        get() = emptyList()

    fun bindScope(encounter: ScopeDefinition): CompiledMechanic = this

    val layers: LayersConfiguration?
        get() = null

    val composition: CompositionConfiguration?
        get() = null

    val canComplete: Boolean
        get() = composition?.canComplete ?: layers?.canComplete ?: true

    val type: DefinitionId
    val canonical: String
    val events: Map<String, EventContract>
    val spatialReferences: SpatialReferences
        get() = SpatialReferences.EMPTY

    fun create(context: MechanicContext): MechanicInstance
}

/**
 * Registration is startup-only; built-in and installed-addon types compile and execute identically.
 */
class MechanicRegistry private constructor(private val types: Map<DefinitionId, MechanicType<*>>) {
    fun descriptions(): Map<DefinitionId, SchemaDescription> = types.mapValues {
        it.value.schema.description
    }

    fun compile(
        id: DefinitionId,
        value: YamlValue,
        context: SchemaContext = SchemaContext(),
    ): Validation<CompiledMechanic> {
        val type =
            types[id]
                ?: return Validation.Invalid(
                    listOf(
                        Diagnostic(
                            "unknown_mechanic",
                            "Mechanic capability is not installed: $id",
                            value.source,
                        )
                    )
                )
        val linked =
            if (context.mechanicCompiler == null) {
                val linker = ReusableMechanics(this)
                context.copy(mechanicCompiler = linker::occurrence)
            } else context
        return bind(type, value, linked)
    }

    private fun <C : Any> bind(
        type: MechanicType<C>,
        value: YamlValue,
        context: SchemaContext,
    ): Validation<CompiledMechanic> =
        when (val result = type.schema.validate(value, context)) {
            is Validation.Invalid -> result
            is Validation.Valid -> Validation.Valid(compiled(type, result.value))
        }

    private fun <C : Any> compiled(type: MechanicType<C>, configuration: C): CompiledMechanic =
        object : CompiledMechanic {
            override val composition = type.composition(configuration)
            override val layers = type.layers(configuration)
            override val type = type.id
            override val canonical = type.schema.encode(configuration)
            override val events = java.util.Map.copyOf(type.events(configuration))
            override val spatialReferences = type.spatialReferences(configuration)
            override val interactionTargets =
                java.util.List.copyOf(type.interactionTargets(configuration))

            override fun create(context: MechanicContext) = type.create(configuration, context)

            override fun bindScope(encounter: ScopeDefinition): CompiledMechanic {
                val linked = type.bindScope(configuration, encounter)
                return if (linked === configuration) this else compiled(type, linked)
            }
        }

    class Builder {
        private val types = linkedMapOf<DefinitionId, MechanicType<*>>()
        private var frozen = false

        fun register(provider: String, type: MechanicType<*>) {
            check(!frozen) { "The capability catalog is frozen" }
            require(provider != "conclave" && type.id.namespace == provider) {
                "Addon capabilities must use their own namespace"
            }
            add(type)
        }

        internal fun builtin(type: MechanicType<*>) {
            check(!frozen)
            require(type.id.namespace == "conclave")
            add(type)
        }

        private fun add(type: MechanicType<*>) {
            require(types.putIfAbsent(type.id, type) == null) {
                "Duplicate mechanic capability ${type.id}"
            }
        }

        fun freeze(): MechanicRegistry {
            check(!frozen)
            frozen = true
            return MechanicRegistry(java.util.Map.copyOf(types))
        }
    }
}

abstract class StatefulMechanic(protected val context: MechanicContext) : MechanicInstance {
    private var started = 0L
    final override var state = MechanicState.PENDING
        protected set

    final override fun start() {
        check(state == MechanicState.PENDING)
        started = context.tick
        state = MechanicState.RUNNING
        initialize()
    }

    protected open fun initialize() {}

    override fun input(value: MechanicInput): Boolean = false

    override fun tick() {}

    override fun cancel() {
        if (state == MechanicState.RUNNING || state == MechanicState.PENDING)
            state = MechanicState.CANCELLED
    }

    protected fun finish(
        success: Boolean,
        player: UUID? = null,
        reason: String? = null,
        preceding: List<MechanicNotice> = emptyList(),
    ) {
        if (state != MechanicState.RUNNING) return
        state = if (success) MechanicState.SUCCEEDED else MechanicState.FAILED
        preceding.forEach(context::notice)
        context.notice(
            MechanicNotice.Result(
                MechanicResult(state, player, reason, SimulationDuration(context.tick - started))
            )
        )
    }

    protected fun eligible(player: UUID, selection: PlayerSelection): Boolean {
        val frame = context.players()
        val actual = frame.byId[player] ?: return false
        return actual.id in frame.roster &&
            actual.canContribute &&
            selection.select(frame).any { it.id == player }
    }
}
