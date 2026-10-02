package dev.conclave.core

import java.math.RoundingMode

/** Checks configured routes, without claiming guards or physical controls will become reachable. */
internal object PatternValidation {
    private class Matcher(val occurrence: MechanicOccurrence, val source: SourceLocation) {
        val contract = checkNotNull(occurrence.mechanic.patternInterface)
        val routed = contract.inputTokens.toMutableSet()
    }

    private class Site(val content: ScopeDefinition, val matchers: Map<String, Matcher>)

    private fun sites(encounter: EncounterDefinition, source: SourceLocation): List<Site> {
        val sites = mutableListOf<Site>()
        fun visit(
            content: ScopeDefinition,
            at: SourceLocation,
            children: List<Pair<MechanicOccurrence, SourceLocation>>? = null,
        ) {
            val matchers = linkedMapOf<String, Matcher>()
            sites += Site(content, matchers)
            val occurrences =
                children
                    ?: (content.objectives.mapIndexedNotNull { index, objective ->
                        (objective as? ObjectiveDefinition.Mechanic)?.occurrence?.let {
                            it to at.field("objectives").index(index)
                        }
                    } +
                        content.mechanics.mapIndexed { index, occurrence ->
                            occurrence to at.field("mechanics").index(index)
                        })
            for ((occurrence, origin) in occurrences) {
                if (occurrence.mechanic.patternInterface != null)
                    matchers[occurrence.id] = Matcher(occurrence, origin)
                occurrence.mechanic.layers?.let { visit(it.content, origin) }
                occurrence.mechanic.composition?.let {
                    val steps =
                        it.steps.mapIndexed { index, child ->
                            child to
                                if (it.mode == CompositionConfiguration.Mode.REPEAT)
                                    origin.field("body")
                                else origin.field("steps").index(index)
                        }
                    visit(ScopeDefinition(mechanics = it.steps), origin, steps)
                }
            }
        }
        visit(encounter.content, source)
        encounter.phases.forEachIndexed { index, phase ->
            visit(phase.content, source.field("phases").index(index))
        }
        return sites
    }

    fun routes(encounter: EncounterDefinition, source: SourceLocation): List<Diagnostic> {
        val sites = sites(encounter, source)
        val root = sites.first()
        for (site in sites) for (rule in site.content.rules) for (action in rule.actions) {
            if (action !is RuleAction.SubmitToken) continue
            val owner = if (action.target.scope == StateScope.ENCOUNTER) root else site
            owner.matchers[action.target.id]?.routed?.add(action.token)
        }
        return sites
            .flatMap { it.matchers.values }
            .mapNotNull { matcher ->
                val missing = matcher.contract.possibleTokens - matcher.routed
                if (missing.isEmpty()) null
                else
                    Diagnostic(
                        "pattern_input_coverage",
                        "Matcher '${matcher.occurrence.id}' has no declared input route for: ${missing.sorted().joinToString()}",
                        matcher.source,
                    )
            }
    }

    fun bindings(
        encounter: EncounterDefinition,
        arena: ArenaDefinition,
        pairing: ArenaEncounter,
    ): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        for (matcher in sites(encounter, pairing.source).flatMap { it.matchers.values }) {
            val cells = mutableMapOf<List<Int>, String>()
            for (target in matcher.contract.inputTargets.flatten()) {
                if (target.kind != TargetKind.BLOCK) continue
                val position =
                    arena.locations[pairing.location(target.id)]?.placement?.position ?: continue
                val cell =
                    listOf(position.x, position.y, position.z).map {
                        it.setScale(0, RoundingMode.FLOOR).intValueExact()
                    }
                val other = cells.putIfAbsent(cell, target.id) ?: continue
                diagnostics +=
                    Diagnostic(
                        "ambiguous_pattern_binding",
                        "Matcher '${matcher.occurrence.id}' targets '$other' and '${target.id}' resolve to the same block cell",
                        pairing.source,
                    )
            }
        }
        return diagnostics
    }
}
