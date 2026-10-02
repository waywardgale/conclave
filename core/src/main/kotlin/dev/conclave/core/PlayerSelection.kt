package dev.conclave.core

import java.util.UUID

enum class PlayerCollection {
    PARTICIPANTS,
    ONLINE_RAIDERS,
    ONLINE_PLAYERS,
}

enum class LifeState {
    ALIVE,
    DEAD,
    PASSED_OUT,
}

enum class Participation {
    ACTIVE,
    RECONNECTING,
    OBSERVER,
}

enum class ConnectionFilter {
    ONLINE,
    OFFLINE,
    ANY,
}

enum class LifeFilter {
    ALIVE,
    DEAD,
    ANY,
}

enum class ParticipationFilter {
    ACTIVE,
    RECONNECTING,
    OBSERVER,
    ANY,
}

data class AuraObservation(val stacks: Long, val remaining: SimulationDuration?)

class PlayerObservation(
    val id: UUID,
    val online: Boolean,
    val life: LifeState,
    val participation: Participation?,
    val gameMaster: Boolean,
    areas: Set<String> = emptySet(),
    roles: Set<String> = emptySet(),
    auras: Map<DefinitionId, AuraObservation> = emptyMap(),
    effects: Set<String> = emptySet(),
) {
    val areas: Set<String> = java.util.Set.copyOf(areas)
    val roles: Set<String> = java.util.Set.copyOf(roles)
    val auras: Map<DefinitionId, AuraObservation> = java.util.Map.copyOf(auras)
    val effects: Set<String> = java.util.Set.copyOf(effects)
    val canContribute
        get() = online && life == LifeState.ALIVE && participation == Participation.ACTIVE
}

class PlayerFrame(players: List<PlayerObservation>, roster: Set<UUID>) {
    val players: List<PlayerObservation> = java.util.List.copyOf(players)
    val roster: Set<UUID> = java.util.Set.copyOf(roster)
    val byId: Map<UUID, PlayerObservation> = java.util.Map.copyOf(players.associateBy { it.id })

    init {
        require(byId.size == players.size)
    }
}

data class PlayerSelection(
    val from: PlayerCollection = PlayerCollection.PARTICIPANTS,
    val connection: ConnectionFilter = ConnectionFilter.ONLINE,
    val life: LifeFilter = LifeFilter.ALIVE,
    val participation: ParticipationFilter? = null,
    val area: String? = null,
    val role: String? = null,
    val aura: DefinitionId? = null,
    val where: PlayerPredicate = PlayerPredicate.Always,
) {
    fun select(frame: PlayerFrame): List<PlayerObservation> =
        frame.players.filter { player ->
            val member =
                when (from) {
                    PlayerCollection.PARTICIPANTS -> player.id in frame.roster
                    PlayerCollection.ONLINE_RAIDERS -> player.online && !player.gameMaster
                    PlayerCollection.ONLINE_PLAYERS -> player.online
                }
            member && matches(player)
        }

    fun matches(player: PlayerObservation): Boolean =
        (connection == ConnectionFilter.ANY ||
            player.online == (connection == ConnectionFilter.ONLINE)) &&
            matchesLife(life, player.life) &&
            matchesParticipation(player) &&
            (area == null || area in player.areas) &&
            (role == null || role in player.roles) &&
            (aura == null || (player.auras[aura]?.stacks ?: 0) > 0) &&
            where.test(player)

    private fun matchesParticipation(player: PlayerObservation): Boolean {
        val filter =
            participation
                ?: if (from == PlayerCollection.PARTICIPANTS) ParticipationFilter.ACTIVE
                else ParticipationFilter.ANY
        return filter == ParticipationFilter.ANY || player.participation?.name == filter.name
    }
}

private fun matchesLife(filter: LifeFilter, state: LifeState) =
    when (filter) {
        LifeFilter.ANY -> true
        LifeFilter.ALIVE -> state == LifeState.ALIVE
        LifeFilter.DEAD -> state != LifeState.ALIVE
    }

sealed interface PlayerPredicate {
    fun test(player: PlayerObservation): Boolean

    fun test(player: PlayerObservation, frame: ConditionFrame): Boolean = test(player)

    data object Always : PlayerPredicate {
        override fun test(player: PlayerObservation) = true
    }

    class And(children: List<PlayerPredicate>) : PlayerPredicate {
        val children = java.util.List.copyOf(children).also { require(it.isNotEmpty()) }

        override fun test(player: PlayerObservation) = children.all { it.test(player) }

        override fun test(player: PlayerObservation, frame: ConditionFrame) = children.all {
            it.test(player, frame)
        }
    }

    class Or(children: List<PlayerPredicate>) : PlayerPredicate {
        val children = java.util.List.copyOf(children).also { require(it.isNotEmpty()) }

        override fun test(player: PlayerObservation) = children.any { it.test(player) }

        override fun test(player: PlayerObservation, frame: ConditionFrame) = children.any {
            it.test(player, frame)
        }
    }

    data class Not(val condition: PlayerPredicate) : PlayerPredicate {
        override fun test(player: PlayerObservation) = !condition.test(player)

        override fun test(player: PlayerObservation, frame: ConditionFrame) =
            !condition.test(player, frame)
    }

    data class PatternState(val query: PatternStateQuery) : PlayerPredicate {
        override fun test(player: PlayerObservation): Boolean =
            error("Pattern state requires an enclosing condition frame")

        override fun test(player: PlayerObservation, frame: ConditionFrame) =
            query.test(frame, player.id)
    }

    data class Identity(val player: UUID) : PlayerPredicate {
        override fun test(player: PlayerObservation) = this.player == player.id
    }

    data class InArea(val area: String) : PlayerPredicate {
        override fun test(player: PlayerObservation) = area in player.areas
    }

    data class HasRole(val role: String) : PlayerPredicate {
        override fun test(player: PlayerObservation) = role in player.roles
    }

    data class HasEffect(val effect: String) : PlayerPredicate {
        override fun test(player: PlayerObservation) = effect in player.effects
    }

    data class HasAura(
        val aura: DefinitionId,
        val stacks: Comparison? = null,
        val remaining: Comparison? = null,
        val timed: Boolean? = null,
    ) : PlayerPredicate {
        override fun test(player: PlayerObservation): Boolean {
            val state = player.auras[aura] ?: return false
            return state.stacks > 0 &&
                (stacks == null || stacks.test(state.stacks)) &&
                (timed == null || timed == (state.remaining != null)) &&
                (remaining == null ||
                    state.remaining != null && remaining.test(state.remaining.ticks))
        }
    }

    data class State(
        val online: Boolean? = null,
        val life: LifeFilter? = null,
        val participation: ParticipationFilter? = null,
    ) : PlayerPredicate {
        override fun test(player: PlayerObservation) =
            (online == null || player.online == online) &&
                (life == null || matchesLife(life, player.life)) &&
                (participation == null ||
                    participation == ParticipationFilter.ANY ||
                    player.participation?.name == participation.name)
    }
}

enum class Comparator {
    EQUALS,
    AT_LEAST,
    AT_MOST,
    GREATER_THAN,
    LESS_THAN,
}

data class Comparison(val comparator: Comparator, val value: Long) {
    fun test(actual: Long): Boolean =
        when (comparator) {
            Comparator.EQUALS -> actual == value
            Comparator.AT_LEAST -> actual >= value
            Comparator.AT_MOST -> actual <= value
            Comparator.GREATER_THAN -> actual > value
            Comparator.LESS_THAN -> actual < value
        }
}

/** Collection truth deliberately requires at least one member, including all. */
fun allSelected(players: List<PlayerObservation>, condition: PlayerPredicate): Boolean =
    players.isNotEmpty() && players.all(condition::test)

fun anySelected(players: List<PlayerObservation>, condition: PlayerPredicate): Boolean =
    players.isNotEmpty() && players.any(condition::test)
