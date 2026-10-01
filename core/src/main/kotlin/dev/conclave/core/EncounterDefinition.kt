package dev.conclave.core

enum class OutcomePrecedence {
    SUCCESS,
    FAILURE,
}

enum class GameplayOutcome {
    SUCCESS,
    FAILURE,
}

sealed interface PhaseRoute {
    data class Next(val phase: String) : PhaseRoute

    data object Complete : PhaseRoute

    data object Wipe : PhaseRoute

    class Choose(branches: List<Pair<Condition, PhaseRoute>>, val otherwise: PhaseRoute) :
        PhaseRoute {
        val branches: List<Pair<Condition, PhaseRoute>> = java.util.List.copyOf(branches)

        init {
            require(
                branches.isNotEmpty() &&
                    branches.none { it.second is Choose } &&
                    otherwise !is Choose
            )
        }
    }
}

fun PhaseRoute.destinations(): List<PhaseRoute> =
    when (this) {
        is PhaseRoute.Choose -> branches.map { it.second } + otherwise
        else -> listOf(this)
    }

data class PhaseDefinition(
    val id: String,
    val duration: SimulationDuration?,
    val deadline: SimulationDuration?,
    val success: PhaseRoute,
    val failure: PhaseRoute = PhaseRoute.Wipe,
    val content: ScopeDefinition = ScopeDefinition(),
    val name: String? = null,
    val combat: Boolean? = null,
) {
    init {
        require(isAuthoredName(id))
        require(duration == null || duration.ticks > 0)
        require(deadline == null || deadline.ticks > 0)
        require(
            PhaseRoute.Wipe !in success.destinations() &&
                PhaseRoute.Complete !in failure.destinations()
        )
    }
}

/** Immutable input to phase progression. Mechanics/rules request outcomes through typed signals. */
class EncounterDefinition(
    val id: DefinitionId,
    val name: String?,
    val start: String,
    phases: List<PhaseDefinition>,
    val precedence: OutcomePrecedence = OutcomePrecedence.SUCCESS,
    val content: ScopeDefinition = ScopeDefinition(),
    val combat: Boolean = false,
    val participants: PlayerSelection =
        PlayerSelection(from = PlayerCollection.ONLINE_RAIDERS, life = LifeFilter.ANY),
    val prohibitSelfRevival: Boolean = false,
    val reconnectGrace: RealtimeDuration = RealtimeDuration(60_000_000_000),
    val recovery: EncounterRecovery? = null,
) {
    val phases: List<PhaseDefinition> = java.util.List.copyOf(phases)
    val byId: Map<String, PhaseDefinition> =
        java.util.Collections.unmodifiableMap(this.phases.associateBy { it.id })

    init {
        require(participants.from != PlayerCollection.PARTICIPANTS)
        require(this.phases.isNotEmpty())
        require(byId.size == this.phases.size) { "Duplicate phase ID" }
        require(start in byId) { "Unknown initial phase" }
        this.phases.forEach { phase ->
            listOf(phase.success, phase.failure)
                .flatMap { it.destinations() }
                .filterIsInstance<PhaseRoute.Next>()
                .forEach {
                    require(it.phase in byId) { "Unknown phase route" }
                }
        }
    }
}

class EncounterRecovery(val location: String, fallbacks: List<String> = emptyList()) {
    val fallbacks: List<String> = java.util.List.copyOf(fallbacks)
    val locations: List<String> = java.util.List.copyOf(listOf(location) + fallbacks)

    init {
        require(
            locations.all(::isAuthoredName) &&
                locations.distinct().size == locations.size &&
                fallbacks.size <= 64
        )
    }
}
