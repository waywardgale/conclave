package dev.conclave.core

import java.util.UUID

sealed interface CompletionRequirement {
    data object All : CompletionRequirement

    data object Any : CompletionRequirement

    data class Count(val count: Int) : CompletionRequirement {
        init {
            require(count > 0)
        }
    }

    fun count(total: Int): Int =
        when (this) {
            All -> total
            Any -> 1
            is Count -> count
        }
}

class DefeatConfiguration(
    val group: String,
    val completion: CompletionRequirement = CompletionRequirement.All,
    causes: Set<DefeatCause> = DefeatCause.entries.toSet(),
    damageTypes: Set<String>? = null,
    val qualification: QualificationId? = null,
) {
    val causes: Set<DefeatCause> = java.util.Set.copyOf(causes)
    val damageTypes: Set<String>? = damageTypes?.let { java.util.Set.copyOf(it) }

    init {
        require(isAuthoredName(group) && causes.isNotEmpty())
        require(damageTypes == null || damageTypes.isNotEmpty())
        require((qualification == null && damageTypes == null) || DefeatCause.DEATH in causes)
    }
}

class DefeatMechanic(private val config: DefeatConfiguration, context: MechanicContext) :
    StatefulMechanic(context) {
    private var activation: UUID? = null

    override fun initialize() {
        evaluate()
    }

    override fun tick() {
        if (state == MechanicState.RUNNING) evaluate()
    }

    private fun evaluate() {
        val group = context.group(config.group)
        if (group == null) {
            check(activation == null) { "A bound group's retained observation disappeared" }
            return
        }
        if (activation == null) activation = group.activation
        check(activation == group.activation) { "Bound group activation was replaced" }
        val defeated = group.members.count(::qualifies)
        val possible = group.members.count { it.outcome == MemberOutcome.ALIVE }
        val needed = config.completion.count(group.members.size)
        if (
            needed > 0 &&
                defeated >= needed &&
                (config.completion != CompletionRequirement.All || group.closed)
        )
            finish(true)
        else if (
            (group.closed || config.completion == CompletionRequirement.All) &&
                (needed == 0 && group.closed || defeated + possible < needed)
        )
            finish(false, reason = "unreachable")
    }

    private fun qualifies(member: GroupMember): Boolean {
        if (member.outcome != MemberOutcome.DEFEATED || member.cause !in config.causes) return false
        if (config.damageTypes != null && member.damageType !in config.damageTypes) return false
        if (config.qualification != null) {
            check(config.qualification in member.qualifications) {
                "Required fatal-operation qualification is missing"
            }
            if (member.qualifications.getValue(config.qualification) != ObservedTruth.TRUE)
                return false
        }
        return true
    }
}
