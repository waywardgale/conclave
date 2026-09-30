# Conclave runtime semantics

Status: Q20-Q30 and Q65 are accepted with the user's revisions recorded below. Detailed schema names remain illustrative. See also [mechanics and participants](mechanics-and-participants.md).

## Accepted foundation

An encounter can contain multiple sequential phases, with one active phase per attempt; the user reaffirmed this after Q151. An attempt retains the content revision it started with. Mechanics can run without being completion objectives. Rules connect events and conditions to supported actions. Conclave owns cleanup of its temporary entities, timers, aura effects, and supported block changes. The accepted [native status-effect actions](aura-effects.md#q158-native-status-effects-as-explicit-actions) explicitly hand effects to Minecraft; those effects keep native lifetimes and are not automatically removed at phase or attempt cleanup.

## Q20: phase completion

A phase with objectives requires all of them by default. Authors can explicitly compose requirements, including any and ordered sequences. The user additionally requires `any`, `or`, and `and` conditions; their logical meanings are addressed in Q25. A phase without objectives needs an explicit completion criterion, which need not be a timer, so an empty objective list never silently completes a phase.

Success follows an explicit next-phase transition or an explicit encounter-completion outcome. [Q83-Q86](phases-and-rules.md) define inline objective mechanics, live or latched condition objectives, the `on`/`if`/`do` rule shape, invocation limits, and explicit phase routes. Declaration order does not imply progression. Phases may be untimed and progress entirely through gameplay conditions. Timed completion and optional deadlines are addressed in Q26.

## Q21: competing outcomes

Resolve terminal success and failure requests for the same gameplay tick together. Success wins by default, with an explicit encounter-level policy to prefer failure. The outcome must not depend on callback registration order.

Example: a boss defeat and the last participant's death happen in the same tick. The default outcome is encounter success. A deliberate encounter policy can instead make failure take precedence.

[Q68](execution-and-errors.md#q68-event-processing-and-transition-boundaries) defines accepted queued events, declaration-order rules and actions, and end-of-tick outcome resolution. [Q244](phase-events-and-rule-order.md#q244-stable-reaction-order-across-rule-scopes) accepts cross-scope reaction order. [Q246-Q247](simulation-stages-and-outcomes.md) accept the tick-stage order and same-tick parent-outcome settlement, including the final revival/expiry boundary.

## Q22: owned lifetimes

Mechanics started by a phase and the temporary state created under them end when that phase exits by default. Explicit encounter scope allows active mechanics and state to persist across phase changes. Attempt end cleans up all temporary state owned by that attempt. The file containing a reusable definition does not determine the lifetime of its active uses.

An aura removed by phase cleanup is not naturally expired. Rules intended for expiry must not run merely because the engine is cleaning up. Removal events may expose their reason, with payload details to be specified.

Example: a temporary aura expires if its timer runs out, but phase cleanup removes it without triggering a timer-expiry punishment. An NPC declared for the whole encounter can survive a phase transition.

## Q23: aura reapplication

Reapplying the same aura to the same holder refreshes the existing duration and preserves one HUD entry. It does not create duplicate gameplay effects.

Authors can explicitly choose to ignore reapplication or enable capped stacks. Source ownership is addressed in Q29. Q65 below defines the accepted death behavior, stack timers, and cap across sources.

## Q24: gameplay clock

Capture progress, phase timers, and aura duration share a server-owned simulation clock. When simulation progress stops, those timers stop too. A display on the client follows the server's time and does not independently decide expiry. [Q87](phases-and-rules.md#q87-durations-and-numeric-values) defines duration units and field-specific zero and range validation.

This keeps gameplay deadlines aligned with the opportunity players have to act. Publication timeouts and disconnect grace are operational policies that may need real elapsed time; they are not decided by this recommendation.

## Q25: condition grammar

Use `and` and `or` to combine condition expressions. Use `any` and `all` to test collections, such as selected participants or named objectives. Use `not` for negation. Expressions can nest and use typed references, with no embedded scripts. [Q88](conditions-and-composition.md#q88-readable-condition-trees) defines accepted collection-plus-`satisfy` syntax and readable numeric comparisons.

For a selected group of three runners, `any` is true if at least one runner satisfies the check; `all` is true only if all three satisfy it. If two runners have an aura and the third does not, "any runner has the aura" is true and "all runners have the aura" is false. The same logic can be described as OR or AND across the group's members; the author-facing distinction is collection testing versus combining separate checks.

Examples of combined checks: all runners have an aura AND the delivery is complete; any runner has an aura OR the specified NPC is defeated. These express the intended distinction without fixing YAML field shapes. [Q61](selection-and-patterns.md#q61-empty-selections-and-condition-groups) defines the accepted empty-collection rule: both `any` and `all` are false for an empty selected collection. Empty `and` and `or` lists fail validation.

## Q26: timed completion and deadlines

A simple timed phase uses `duration` to complete successfully when the time elapses. An objective-driven phase can use `deadline` as a time limit that fails the phase if it remains incomplete. The user requested the name `deadline`, replacing the previously proposed `timeout`. Authors use explicit combined completion conditions when both time and objectives determine success; field presence must not silently choose between competing interpretations.

Both fields are optional. Without `duration` or `deadline`, a phase has no automatic time limit and progresses through its declared objectives or completion conditions. An untimed phase can still contain timed mechanics or auras.

Examples: a 30-second damage window completes when its duration ends; an objective with a 60-second deadline fails if unfinished at that limit. The accepted same-tick success precedence applies if completion coincides with the deadline.

## Q27: capture progress

Capture requires uninterrupted qualifying occupancy by default. Losing the required occupancy resets incomplete progress. Explicit options allow progress to pause or decay instead. Completion is retained once reached until the mechanic is explicitly restarted; maintaining current occupancy is a condition rather than an implied property of completed capture.

Dead and disconnected participants do not contribute, as accepted in the participant policy. Q56 defines area membership, and [Q60](selection-and-patterns.md#q60-selecting-players) defines the accepted general player-selection interface.

[Q173](capture-and-interaction-targets.md#q173-capture-configuration-and-interruption) accepts the concrete count, duration, and interruption fields. Extra occupants do not accelerate capture, and eligible contributors can replace one another while the required count remains satisfied.

## Q28: relic delivery

A relic has one holder at a time. Delivery normally requires interaction at the configured destination, with automatic delivery on entry into an area available explicitly. Successful delivery removes the carried copy and marks the relic delivered; reuse requires an explicit reset or respawn.

The author chooses the holder-death policy: drop the relic, reset it immediately to its initial position, or make it disappear with support for delayed respawn. Dropping remains the default. Disconnect has its own separate setting and drops by default, as accepted in Q35. If no valid drop is possible, or the relic leaves the allowed arena, return it to its configured home location.

Accepted in Q30: the death policy determines what happens immediately, while separate opt-in respawn configuration determines which disappearance causes trigger a return, after what delay, and at which location. Delivery and phase/attempt cleanup never implicitly schedule respawn. Pending respawn timers follow the relic's owning gameplay scope, so cleanup cancels them.

Configuration fragment using the accepted Q180 fields:

```yaml
on_holder_death: despawn
respawn:
  causes: [holder_death]
  after: 10s
  at: initial
```

This example removes the carried copy immediately and schedules one replacement at the initial position after ten seconds of simulation time, provided its owning scope is still active. The fragment is not a complete manifest. Q180 rejects respawn combinations that could create an extra copy while a relic is still held or present.

[Q178-Q180](relic-identity-and-lifecycle.md) accept the managed carry model, explicit instance creation, and concrete lifecycle fields. They retain Q28/Q30's distinction between immediate holder policy and scheduled return.

## Q29: aura sources

Refinement to Q23: repeated application from the same source refreshes that source's contribution. Independent sources of the same aura retain separate ownership, so phase cleanup removes only its contribution. The holder still sees one HUD entry, and a non-stacking aura does not multiply gameplay effects merely because it has multiple sources.

Example: an encounter-wide source and a phase-owned source both apply the same aura. Ending the phase removes the phase contribution and leaves the encounter contribution active. Q65 defines timer display aggregation and capped stacks across sources. [Q117](auras-and-world-lifetimes.md#q117-auras-that-outlive-an-attempt) accepts explicit player-owned applications, without silently promoting existing contributions. General transfer of ownership remains a later decision.

## Q65: aura death behavior, stacks, and display

Accepted: remove Conclave auras on holder death by default. An aura definition can explicitly retain the aura through death. Retained durations continue on the simulation clock while the holder is dead or disconnected. Removal due to death has its own cause and does not invoke natural-expiry behavior. Existing phase and attempt cleanup continues to remove only the state it owns.

For an explicitly stacking aura, each accepted stack has its own duration and source ownership. The authored maximum is a cap across all sources for that aura on that holder. Ignore extra stack applications at the cap rather than retaining hidden reserve stacks. The accepted default refresh mode for non-stacking auras still refreshes the same source's contribution; it is not replaced by the stacking rule.

Keep one HUD entry. A stacking aura displays its effective stack count and time until the next stack expires. A non-stacking aura with independent source contributions displays the time until its last timed contribution ends, or an untimed indication if a contribution has no expiry. Cleanup may remove a contribution sooner and updates the display accordingly. [Q118](auras-and-world-lifetimes.md#q118-aura-lifecycle-events) accepts expiry-event aggregation and the applied/stack-gained event additions. An optional shared-timer stacking mode remains outside the accepted contract. [Q159-Q161](aura-periodic-and-queries.md) accept periodic holder effects, contribution-removal notifications, and structured aura conditions. A due periodic pulse runs before coinciding natural expiration; duration refresh does not reset its cadence, and unavailable holders accumulate no pulse debt.

This policy concerns Conclave auras, including their accepted [owned modifiers](aura-effects.md). Vanilla status effects and unrelated mods' effects retain their own behavior, and restoring a player does not copy old effects or inventory indiscriminately. A native effect granted through `apply_status_effect` also follows Minecraft's lifetime after that explicit handoff.
