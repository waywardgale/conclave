# Phase and rule authoring

Status: Q83-Q87 are accepted. Their syntax builds on the accepted single active phase, typed conditions, registered capabilities, reusable definitions, event queue, and simulation clock. Examples are fragments for reviewing the framework and are not shipped encounter content. A development compiler and phase state machine implement a limited timed-phase subset; [implementation status](implementation-status.md) distinguishes that coverage from the full contract below.

## Multiple sequential phases

Confirmed by the user after Q151: an encounter may define multiple sequential phases, with one active phase at a time. This retains the existing Q6, Q86, and Q91 model. Multiple independently active phases are not requested.

Authors can create a sequence of phases, branch to different phases through conditions, and loop back to an earlier phase. For example, an encounter can progress through setup, ritual, damage, another ritual/damage cycle, and a final phase. Each phase can contain simultaneous objectives and background mechanics, and any phase can be untimed. Encounter-owned state can persist across these transitions; re-entering a phase creates fresh phase-owned state. Explicit routes, not declaration order, determine progression.

## Q83: objectives and background mechanics

Accepted: allow a phase's `objectives` list to contain named mechanic occurrences directly, using the accepted `type` or `use` forms. Put behavior that should run without blocking progression in a separate `mechanics` list. Both lists use the same underlying mechanic contract; placing an occurrence in `objectives` additionally makes its completion a phase requirement.

For example, this fragment declares one required capture:

```yaml
objectives:
  - id: north_capture
    type: capture
    area: north
    duration: 10s
```

An occurrence is declared once. Inline objective mechanics are addressable by their mechanic ID for events and completion checks. Mechanic IDs must be unique across both lists in the same scope. A condition objective can refer to an already declared mechanic's completion instead of creating a second copy, including an explicitly encounter-owned mechanic. Exact completion-predicate fields belong to the condition catalog.

Also allow a named objective with a `condition` containing a typed check, as an alternative to `type` or `use`. Such an objective stays live by default: if the condition stops being true before phase outcome evaluation, that objective is no longer satisfied. An explicit `latch: true` records a condition that has been satisfied and keeps that objective satisfied for the current phase activation. This latch setting belongs to condition objectives; it does not replace a mechanic's own accepted completion contract.

The distinction matters for simultaneous requirements. "All runners currently have an aura" is a live condition. "A delivery completed" follows the mechanic's recorded completion. Capture retains its accepted uninterrupted-progress behavior and latched completion until reset or restart. Re-entering a phase creates fresh phase-local objective state.

The default successful completion still requires all objectives. An explicit phase `complete_when` can replace that default with a typed condition tree. A phase without objectives needs `complete_when` or its accepted simple timed `duration`; an empty objectives list cannot succeed automatically. When both time and objectives participate in success, require an explicit completion expression rather than guessing their relationship.

An optional `fail_when` adds an authored failure condition. It does not disable deadlines, terminal failure of a required objective, the global party-defeat policy, or engine-error handling. A mechanic declared as background emits its normal gameplay events without automatically deciding phase success or failure. An engine error still follows Q69 even when the affected mechanic is background. A mismatch or incomplete progress is not a terminal failure unless that capability explicitly defines it as one.

## Q84: event rules

Accepted: each rule has an `id`, `on`, optional `if`, and an ordered `do` list. `on` names an explicit typed `source` and an `event`. `if` contains a typed condition tree. `do` contains registered action names and their typed arguments, with one action per list entry. Reject an empty action list.

For illustration, a rule could read:

```yaml
rules:
  - id: expose_boss
    on:
      source:
        mechanic: north_capture
      event: completed
    do:
      - set_vulnerable:
          group: boss
          value: true
```

The accepted action name is `set_vulnerable` for supported NPC vulnerability control. The source mechanic and target group must exist in the applicable scope. [Q149](combat-rules-and-health.md#q149-explicit-npc-vulnerability) accepts its detailed behavior; supported entity adapters still require verification. The fragment is not a complete encounter.

A matching event invokes the rule only when its optional condition holds at evaluation time. A false guard does not create a suspended rule that will run if the condition becomes true later. Later eligible events can invoke it again under Q85. Rules in scope are ordered as accepted in Q68, and actions within a rule run in written order.

Permit typed event-value references such as `{event: player}` only where that source/event contract actually supplies a value of the required type. Reject an action that needs a player when its event supplies no player. Reference whole documented fields; do not add string interpolation, arbitrary property traversal, or embedded expressions. Accepted [Q164](event-conditions.md) adds `event_value` guards for comparisons and optional-field presence checks against that same documented payload. Events retain their originating attempt and activation in engine provenance. Q275 does not require common public payload keys or an attempt on a pre-start event. Accepted [Q167](mechanic-start-and-parameters.md#q167-a-started-event-before-child-mechanics-begin) adds `source: self` for the containing scope and a standard `started` event, processed after initialization and before that scope's child mechanics begin.

An `on` source denotes a subscription to the declared producer during the rule's owning scope. This is distinct from binding an objective or action to one spawned group activation. A subscription can receive valid events from a new producer activation within that scope, but each event retains its original activation identity. It must never relabel an old event as belonging to a replacement. Delayed work that captures a target remains bound to that target under Q81. Detailed scheduling and cancellation stages remain part of the execution contract.

## Q85: invocation limits

Accepted: a rule runs for each eligible matching event by default. An explicit `once: true` limits it to one invocation during its owning scope activation. A phase-owned rule resets on phase re-entry; an encounter-owned rule resets only with a new attempt. Completed mechanics still emit events according to their own contracts, so this default does not create repeated completion events by polling.

An optional `cooldown`, such as `5s`, spaces invocations using the simulation clock. A rejected client input, nonmatching event, or false `if` condition consumes neither the once limit nor the cooldown. Reserve the invocation before its actions begin, so emitted events cannot re-enter it before its limit takes effect. A handled action failure does not refund a consumed invocation and accidentally duplicate earlier effects.

Limits apply to the rule as a whole by default. `per_player: true` keeps separate limits for each triggering player's identity. This option is valid only when the source/event contract supplies an unambiguous triggering player or explicit [Q223 `on.player`](event-players-and-mechanic-results.md#q223-an-explicit-player-field-for-a-subscription) establishes one for the subscription; reject it otherwise. Reconnection within the same owning activation does not reset that player's limit. This bucket uses the triggering player, not every recipient later selected by an action.

Rules react to admitted events rather than running every tick while a condition happens to be true. [Q89-Q92](conditions-and-composition.md) define sequence, parallel and repeat composition, conditional routes, and named counters and timers. Invocation limits supplement the accepted execution ceilings and do not replace them.

[Q171-Q172](role-and-player-events.md) define role and participant lifecycle sources. Per-member role events and player lifecycle events supply the affected `player` for `per_player` limits. Aggregate role `changed` has no single triggering player.

## Q86: explicit phase routing

Accepted: require encounter-level `start` to name the initial phase. Every phase declares its success route. Use `success: {next: damage}` to advance or `success: {complete: true}` to finish the encounter successfully. Neither phase list order nor a missing target chooses a destination.

For example:

```yaml
start: ritual
```

And on a phase:

```yaml
success:
  next: damage
failure:
  wipe: true
```

A phase's omitted failure route defaults to `wipe: true`. Authors may explicitly use `failure: {next: recovery}` to continue the same attempt after an authored phase failure. Validate exactly one outcome operation per route. `complete: true` belongs to a success route, while `wipe: true` belongs to a failure route; a false flag is invalid. [Q91](conditions-and-composition.md#q91-conditional-phase-routing) defines the accepted `choose` list and mandatory `otherwise` route.

Here a wipe ends the attempt as failed and performs accepted cleanup and participant recovery. It does not invent an additional death or repeat vanilla death consequences. A deliberate authored damage effect is a separate gameplay action. Technical errors, administrative stops, and an otherwise defeated party retain their accepted distinct handling; a phase failure route cannot silently prevent mandatory attempt termination.

Explicit routes can return to an earlier phase. Re-entry creates new phase-owned mechanics, objective state, counters, timers, and rule limits. Encounter-owned state persists until attempt end unless explicitly changed. This permits repeated damage cycles without copying phase definitions. The existing limit of one phase transition per tick prevents a chain of immediate routes from completing indefinitely within one tick.

Resolve simultaneous success and failure under the accepted encounter precedence policy. A valid failure recovery route does not turn a failure into success for tie handling. Transition cleanup, next-tick phase start, and stale-activation checks continue to follow Q68.

## Q87: durations and numeric values

Accepted: require units in every duration value. Accept lowercase `ms`, `s`, `m`, and `h`, for example `500ms`, `10s`, `2m`, or `1h`. Permit a finite decimal with one unit, such as `1.5s`; defer compound expressions such as `1m 30s`. Reject bare numbers in duration fields and arithmetic strings.

Gameplay durations use the accepted simulation clock. Quantize them to simulation steps without expiring before the requested duration. Operational durations, such as reconnect grace, keep their documented real-time clock. Units identify a duration's magnitude; the field's contract identifies its clock. A client countdown remains a presentation of server state.

Omit optional phase timing fields for an untimed phase. Zero is permitted only where the field documents a zero-duration meaning, such as instant assistance or no post-revival protection. Never interpret `0s` as an undocumented infinity. Negative values and non-finite numbers are invalid for durations. Exact per-field maximums still need implementation budgets.

Require whole numbers for counts and document distance values in blocks. Fields accepting percentages use an explicit form such as `100%`, not an ambiguous mixture of `1`, `100`, and a percentage string. Each field declares its own valid range; percentage fields are not all assumed to have identical bounds. Health and damage fields must state their units in the schema and editor. Detailed NPC attribute units remain part of the capability catalog.

Preserve finite fractional coordinates and dimensions under the accepted area contract. Never silently clamp an invalid value or convert a string into a different scalar type to make a manifest pass validation. Editor controls show units, bounds, defaults, and validation beside the field. These validation rules do not promise that every numeric field or unit is already cataloged.

[Q243-Q244](phase-events-and-rule-order.md) accept a named phase event source for encounter rules and cross-scope reaction order. [Q245](ending-scope-presentation.md) separately accepts presentation-only self-terminal reactions for encounter, phase, and layers bodies. Ordinary listeners stop when their owner ends, including encounter listeners observing a final phase.

[Q275](event-catalog-conventions.md) accepts the remaining common event conventions and fields for existing area, interaction, aura, timer and health-floor events. It preserves the typed queue and export contracts above.
