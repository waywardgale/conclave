package dev.conclave.core

internal fun PlayerSelection.spatialReferences() =
    SpatialReferences(setOfNotNull(area)) + where.spatialReferences()

internal fun PlayerPredicate.spatialReferences(): SpatialReferences =
    when (this) {
        is PlayerPredicate.InArea -> SpatialReferences(setOf(area))
        is PlayerPredicate.And ->
            children.fold(SpatialReferences.EMPTY) { result, child ->
                result + child.spatialReferences()
            }
        is PlayerPredicate.Or ->
            children.fold(SpatialReferences.EMPTY) { result, child ->
                result + child.spatialReferences()
            }
        is PlayerPredicate.Not -> condition.spatialReferences()
        else -> SpatialReferences.EMPTY
    }

internal fun Condition.spatialReferences(): SpatialReferences =
    when (this) {
        is Condition.And ->
            children.fold(SpatialReferences.EMPTY) { result, child ->
                result + child.spatialReferences()
            }
        is Condition.Or ->
            children.fold(SpatialReferences.EMPTY) { result, child ->
                result + child.spatialReferences()
            }
        is Condition.Not -> child.spatialReferences()
        is Condition.Players -> players.spatialReferences() + satisfy.spatialReferences()
        is Condition.Count -> players.spatialReferences()
        else -> SpatialReferences.EMPTY
    }

internal fun ScopeDefinition.spatialReferences(): SpatialReferences {
    val conditions =
        objectives.filterIsInstance<ObjectiveDefinition.Check>().map { it.condition } +
            listOfNotNull(completeWhen, failWhen) +
            rules.mapNotNull { it.guard }
    return allMechanics.fold(SpatialReferences.EMPTY) { result, occurrence ->
        result + occurrence.mechanic.spatialReferences
    } +
        conditions.fold(SpatialReferences.EMPTY) { result, condition ->
            result + condition.spatialReferences()
        } +
        rules.fold(SpatialReferences.EMPTY) { result, rule ->
            result + (rule.source.players?.spatialReferences() ?: SpatialReferences.EMPTY)
        }
}

fun EncounterDefinition.spatialReferences(): SpatialReferences =
    phases.fold(
        content.spatialReferences() +
            participants.spatialReferences() +
            SpatialReferences(locations = recovery?.locations?.toSet() ?: emptySet())
    ) { result, phase ->
        val guards =
            listOf(phase.success, phase.failure).filterIsInstance<PhaseRoute.Choose>().flatMap {
                it.branches.map { it.first }
            }
        result +
            phase.content.spatialReferences() +
            guards.fold(SpatialReferences.EMPTY) { references, condition ->
                references + condition.spatialReferences()
            }
    }
