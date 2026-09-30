# Phase events and rule order

Status: Q243-Q244 are accepted. Q167 already supplies startup events, Q224 supplies ordinary mechanic results, and Q68 supplies queued execution and phase-transition boundaries. These contracts add a named phase event source and define the order of reactions across rule scopes. [Q245](ending-scope-presentation.md) separately accepts the presentation-only ending window and attempt-terminal interface. No implementation exists.

## Q243: observe a named phase from encounter rules

Accepted: let an encounter-level rule observe a phase declared in that encounter through `on.source: {phase: phase_id}`. Expose `started`, `completed`, and `failed`, retaining the ordinary distinction between gameplay outcomes and cancellation or technical interruption.

```yaml
on:
  source:
    phase: ritual
  event: completed
```

This is a subscription fragment for a rule in the encounter's own `rules` list. `ritual` is its encounter-local authored phase ID, using the same identity as `start` and route `next`. It does not identify another encounter's phase or expose a phase's private mechanics. Reject unknown phase IDs, a wildcard phase source, and this source outside the encounter rule body in the initial interface. Reusable mechanics keep their existing explicit public exports.

A subscription can observe later activations of the same phase while its listening encounter activation remains eligible. Every event retains the actual originating phase activation, so a repeated ritual is a new source activation. Do not replay a previous phase result when a rule starts, a player reconnects, or content is published. A late queued event never changes identity to refer to a replacement phase.

### Meaning and fields

`started` uses Q167's existing initialized-state boundary, before the phase starts its dependent child mechanics. This adds a named way to observe the same event, not another startup notification. Preserve startup queue ordering and the child-start barrier.

`completed` means the phase's successful outcome has committed. It does not by itself mean the encounter succeeded. `failed` means the phase's ordinary gameplay failure committed. A failure route may still continue the same attempt in a recovery phase. Each phase activation produces at most one of these terminal gameplay notifications after its accepted success/failure arbitration.

Both terminal events provide nonnegative simulation-time `elapsed`, measured from actual phase activation to its committed result, including supported startup waits. They also provide `route`, with the frozen operation `next`, `complete`, or `wipe`. Only valid outcome combinations occur: completion uses `next` or `complete`, and failure uses `next` or `wipe`. Provide `next_phase` only for `route: next`; it is the destination's authored ID, not a mutable phase handle. Optional-field validation remains unchanged.

For `failed`, also provide required registered `reason`:

| Reason | Committed phase failure |
| --- | --- |
| `deadline` | This phase's own gameplay deadline expired. |
| `condition` | Its authored `fail_when` became decisive. |
| `objective_failed` | A required objective's terminal failure made the phase fail. |

These reasons describe existing failure paths. They do not introduce new triggers or copy a private mechanic's full failure tree into the phase event. If multiple failure requests establish the same committed failure, retain the first applicable cause in stable admitted runtime order and keep other causes in diagnostics. Success/failure precedence is still the separate encounter policy.

Do not fabricate a triggering player, killer, last interactor, or roster list. These events have no player field for `per_player` or `on.player`. Ordinary selectors in a reaction can still select participants under their own defaults and authority. Phase-local state does not become externally readable merely because its result is public.

### Commitment, routing, and cancellation

Resolve the phase outcome, select its route under Q91, and freeze that choice before reporting it. A reaction can affect still-live permitted state, but cannot reverse the committed phase result or cause its route conditions to be evaluated again. For example, increasing an encounter counter in a phase-completed reaction cannot change the route already selected from that counter's earlier value. Authors must update a value before routing if they want it to affect that route.

Preserve causal gameplay notifications before the phase result, the normal event queue, one transition per attempt per tick, cleanup, and next-tick phase startup. A failure reported by a background mechanic does not become a phase failure merely because a rule observes it. An ended listener does not revive to receive a result.

Party defeat, administrative stop/restart, a technical error, and server interruption retain their own attempt-level meaning. Ending an otherwise unfinished phase for one of those reasons does not manufacture `failed.reason`, and unfinished mechanics do not gain invented failure events. If a phase actually committed an ordinary result before a separate interruption, retain that historical result without fabricating a second one or using it to award attempt success.

This contract establishes the named phase source and its schemas. It does not add generic `cancelled` or `errored` gameplay callbacks, a phase-jump action, or attempt-level deadline/failure fields. Q245 separately authorizes presentation-only self-terminal rules. It cancels ordinary named-phase reactions when the encounter also ends; surviving encounter listeners use the ordinary queue and lifetime rules.

## Q244: stable reaction order across rule scopes

Accepted: for the same admitted event, begin eligible reactions in the order their owning scope activations were created. Within each scope, begin ordinary rules in YAML declaration order, followed by that scope's matching export declarations in their declaration order. Keep each invocation's actions in their written order.

Assign each activation its stable order when it is created through the accepted activation sequence. Parent initialization precedes its children. Objectives precede background mechanics within a body, and siblings follow their existing declaration/start order. Delayed setup completion or native callback registration cannot change that recorded order. An existing encounter-owned mechanic retains its order when phases change; a newly entered phase gets a new position.

This gives a stable older-before-newer order across active scopes, including persistent encounter-owned mechanics. It is not a rule that every phase always precedes every mechanic, or that authored IDs sort alphabetically. Sequence and repeat keep their existing single current child and next-tick progression; ending one activation removes its listeners without transferring their position or limits to the replacement.

### Selection, live guards, and queued work

Capture candidate subscriptions with their actual activation identities when the event is admitted. Match event/source filters at their documented observation boundary. Before starting each reaction, recheck that its listening activation is still eligible. A newly initialized scope cannot subscribe retroactively to an already-admitted event. This does not change the existing allowance for eligible enclosing listeners to observe a committed child result.

Evaluate each rule's `if` when that reaction runs. Earlier committed actions can therefore change whether a later guard passes. Immutable `event_value` fields retain their original observation, and each action still captures its own recipient set before applying that action. An earlier rule cannot rewrite the current event or silently consume it so other eligible rules never see it.

Exports follow the ordinary rules in their own listening scope. Their guards observe the resulting current state, and their explicit mappings preserve immutable source fields and bound values. An accepted export queues a distinct public event. It does not immediately recurse into an enclosing listener or permit private child access. Keep the existing one-source-per-export, declaration order, and reserved lifecycle names.

Finish the ready-to-run reactions for the current event before dispatching newly queued gameplay events. Where a documented action returns pending work, preserve the unfinished invocation and resume its remaining actions through a later admitted continuation. A pending invocation does not hold the entire event queue or server thread. Other ready reactions may run first, so the policy orders invocation starts and committed effects, not completion of asynchronous work. A continuation keeps its original event, invocation reservation, scope identity, and written action order; ended or replaced scopes cannot resume it.

The existing startup barrier still waits for required startup work before starting dependent children. Guards, once/cooldown reservations, recoverable results, and technical errors keep their accepted meanings. No new retry capability or unlimited pending queue is introduced. Invocation, event, continuation, and cleanup work remain bounded by the existing execution contract.

### Limits of the ordering policy

This order resolves ordinary eligible reactions to one event. It does not decide success/failure precedence, change last-tick revival precedence, alter frozen routes, start another phase in the same tick, or grant ending listeners an extra lifetime. [Q246-Q247](simulation-stages-and-outcomes.md) accept simulation stages and same-tick parent-outcome settlement as separate contracts. Q245 supplies the bounded final-presentation exception for an ending scope.

Expose the effective listener order in authorized in-game inspection when diagnosing a rule. Add no numeric rule priority, custom scheduling script, or registration-order setting to YAML. If reactions require a particular sequence of immediate actions, authors can put those actions in one ordered rule rather than relying on separate callbacks to finish asynchronous work in the same order.

## Related contracts

These contracts extend [Q84-Q86 rules and routing](phases-and-rules.md), [Q68 event processing](execution-and-errors.md#q68-event-processing-and-transition-boundaries), [Q91 frozen route selection](conditions-and-composition.md#q91-conditional-phase-routing), [Q165-Q166 reusable scopes and exports](reusable-mechanic-contracts.md), [Q167 startup](mechanic-start-and-parameters.md#q167-a-started-event-before-child-mechanics-begin), and [Q224 mechanic outcomes](event-players-and-mechanic-results.md#q224-common-mechanic-completion-and-failure-fields).

Q243 chooses a public phase source and result schema. Q244 independently orders existing event reactions across scopes. Q245 adds attempt terminal events and explicitly restricts final reactions by ending scopes; Q243 and Q244 do not independently supply that lifetime exception.

[Q245](ending-scope-presentation.md) accepts a bounded presentation-only exception for a scope's own terminal rules. It permits immediate best-effort finite cues after committed ordinary results, with no gameplay continuations or arbitrary cleanup callbacks.
