# Conditions and composition

Status: Q88-Q92 are accepted. Typed condition trees, reusable mechanics, explicit scopes, ordered rules, and phase routing are already accepted. These fragments illustrate the accepted authoring contract; no parser, runtime, or bundled encounter is implemented.

## Q88: readable condition trees

Accepted: a condition node contains exactly one logical operator or registered predicate. `and` and `or` contain lists of conditions, while `not` contains one condition. `any` and `all` contain a typed collection selection and a `satisfy` condition applied to each selected member.

For example:

```yaml
and:
  - completed:
      mechanic: north_capture
  - all:
      players:
        role: runner
      satisfy:
        has_aura: charged
```

This requires the capture's recorded completion and the current aura state of every selected runner. The selected members supply the implicit subject of `has_aura`. A predicate used without such a subject must declare its target through that predicate's typed arguments. Reject an ambiguous or missing subject instead of choosing a nearby player. The exact full player-selection field catalog remains part of the capability vocabulary.

Start with typed checks for mechanic completion, objective satisfaction, player state, role and aura membership, spatial presence, relic state, NPC state, counters, and timers. The example accepts `completed: {mechanic: ...}` and `has_aura: aura_id` in a member check. A mechanic's completion and a live condition objective's satisfaction remain different state queries. [Q161](aura-periodic-and-queries.md#q161-aura-stack-and-remaining-time-conditions) accepts the structured `has_aura` form with stack and countdown comparisons and a `timed` check, while preserving concise presence checks. A complete predicate catalog will specify each remaining leaf's fields and supported subject types.

Numeric predicates use readable comparisons such as `equals`, `at_least`, `at_most`, `greater_than`, and `less_than`. Require exactly one comparator in an individual comparison; combine range checks with `and`. For example:

```yaml
counter:
  id: cycles
  scope: encounter
  at_least: 3
```

A `count` predicate counts a selected collection and compares the result, so absence is explicit:

```yaml
count:
  players:
    area: north
  equals: 0
```

Selections retain Q60's living, online, eligible-participant default. Authors must explicitly request the full roster or other states where needed. The accepted Q61 empty-selection rules remain: both `any` and `all` are false for an empty collection, while a zero-count check can be true. Empty `and` and `or` lists are invalid.

Condition evaluation has no side effects, does not advance a timer, and never rerolls an assignment. Each condition evaluation reads one coherent gameplay state for that evaluation. A later rule or action may see updated state under Q68. Nested checks are bounded by schema and execution limits. Conditions cannot invoke arbitrary functions, traverse arbitrary object properties, or contain expression strings.

## Q89: sequence and parallel mechanics

Accepted: add registered composition types `sequence` and `parallel`. Both contain a nonempty `steps` list of named mechanic occurrences using the same `type` and `use` forms as other mechanics. A composed mechanic can itself appear under `objectives`, under background `mechanics`, or inside a reusable definition.

For example:

```yaml
- id: plate_order
  type: sequence
  steps:
    - id: first
      type: capture
      area: north
      duration: 5s
    - id: second
      type: capture
      area: south
      duration: 5s
```

A sequence starts its first step and waits for its success before starting the next. Start the next step no earlier than the next simulation tick. The sequence succeeds when every step succeeds. An ordinary terminal step failure fails the sequence without starting the remaining steps. A mismatch or temporary loss of capture progress is not a terminal step failure unless its mechanic contract says so.

A parallel composition starts all its steps in declaration order during the same activation. `completion: all` is the default and requires every step to succeed; a terminal failure fails that composition. An explicit `completion: any` succeeds when a step succeeds and fails only when all steps have failed without a success. Aggregate same-tick child outcomes before deciding, so a successful alternative does not lose merely because another branch's failure was delivered first. An engine error always retains Q69's separate handling.

When a composition ends, cancel unfinished child activations and pending work. Cancellation is not a defeat, successful completion, or natural timer/aura expiry. Cleanup uses each child's existing ownership contract and cannot delete state owned by another scope. Explicit phase- or encounter-owned state retains its declared lifetime; ending a child does not perform an implicit rollback of other gameplay changes.

Child IDs and progress belong to this composed occurrence. Two uses of the same definition have separate child state. Preserve completed step results for the parent's current activation, then discard local results when that activation ends. Reused definitions expose documented public events and parameters instead of allowing outside rules to reach into another occurrence's private children. [Q166](reusable-mechanic-contracts.md#q166-explicitly-exported-public-events) accepts explicit public event forwarding through `export.events`, with typed payload mappings. Q165 also accepts `type: layers` for mechanics needing local objectives, background behavior, rules, counters, and timers.

## Q90: controlled repetition

Accepted: add `type: repeat` with one `body` mechanic occurrence. Require exactly one stopping mode: positive integer `count`, a typed `until` condition, or explicit `forever: true`. All repetitions remain bounded by their owning lifetime and the accepted runtime resource limits.

For example:

```yaml
- id: repeated_capture
  type: repeat
  count: 3
  delay: 2s
  body:
    id: capture_step
    type: capture
    area: north
    duration: 5s
```

A counted repeat succeeds after that many successful body activations. It runs one body at a time, with fresh mechanic progress and private state for each iteration. An ordinary terminal body failure fails the repeat; repetition is not an automatic retry of failed work. No next iteration starts before the prior iteration has finished.

`delay` is the gap after a successful iteration and before the next, not a start-to-start schedule. Its default is `0s`, meaning no authored pause, but the next iteration still starts no earlier than the next simulation tick. This is an explicit documented zero-duration meaning under Q87. Do not queue catch-up iterations or overlapping bodies. Authors needing a fixed cadence can use named timer events independently of a body's completion.

For `until`, check the condition before starting and during the repeat at simulation evaluation points. [Q247](simulation-stages-and-outcomes.md#q247-settle-child-results-before-their-parents-outcome) accepts re-evaluation during bounded end-of-tick settlement when ready reactions change its inputs. If already true, succeed without starting a body. When it becomes true later, complete the repeat and cancel any unfinished body or waiting gap. This deliberate stopping condition does not fabricate a completion or defeat for the cancelled child. If stopping and a normal body failure coincide, apply the encounter's accepted success/failure precedence to the repeat's competing outcomes; engine errors remain separate.

A forever repeat has no natural successful completion and ends through cancellation, owning-scope cleanup, or an authored terminal failure. Reject using it as an objective whose only success criterion is that repeat's natural completion. It can run as background while other objectives determine progression. Untimed phases may host it without introducing an implicit phase deadline.

Counters and assignments intentionally shared across iterations belong in the enclosing scope and are referenced explicitly. Completed iterations cannot leave scheduled callbacks that act on their replacement. Spawned world resources continue to follow their declared ownership, so repetition does not silently despawn deliberately persistent NPCs or recreate already delivered relics. The author's explicit body actions determine those changes.

## Q91: conditional phase routing

Accepted: extend an outcome route with an ordered `choose` list and a mandatory `otherwise` route. Each branch has an `if` condition and exactly one valid outcome operation. Evaluate the first matching branch, with no fall-through and no branch actions hidden in the routing expression.

For example:

```yaml
success:
  choose:
    - if:
        counter:
          id: cycles
          scope: encounter
          at_least: 3
      complete: true
  otherwise:
    next: ritual
```

Resolve success-versus-failure precedence first, then evaluate the selected route against the current state before phase cleanup. Freeze that chosen destination for the transition. Cleanup and later events cannot cause the same route to be evaluated again against a different state.

Branches in a success route may use `next` or `complete: true`; branches in a failure route may use `next` or `wipe: true`. Apply the same rules to `otherwise`. Reject empty branch lists, missing fallbacks, invalid phase IDs, and conflicting outcome keys. A direct route remains valid when no choice is needed. Avoid nested choices in v1; authors can combine typed conditions within the ordered branch list.

Returning to a previous phase still creates fresh phase-owned state under Q86. This contract does not introduce more than one active phase or bypass global party defeat, administrative stops, or engine-error handling.

## Q92: named counters and timers

Accepted: make counters and timers declared state with `id` and optional `name`. Use a `counters` list with integer `initial` values, defaulting to zero. A `timers` list supplies a positive `duration`, with `auto_start: true` by default when the owning scope activates. Authors can set `auto_start: false` and start it through a rule.

For example, within a phase or encounter:

```yaml
counters:
  - id: cycles
    initial: 0
timers:
  - id: pulse
    duration: 10s
```

Use `set_counter`, `add_counter`, and `reset_counter` actions. Reset returns to the declared initial value. Counter values are signed integers; any declared bounds are validated, and arithmetic overflow or an out-of-bounds required mutation follows the documented error contract rather than silently wrapping or clamping. Counter mutations run in the accepted server action order.

Use `start_timer`, `restart_timer`, `pause_timer`, `resume_timer`, and `stop_timer`. Start begins an idle or expired timer and is a no-op for one already running or paused. Restart explicitly begins a fresh full-duration activation. Pause and resume preserve remaining time and are no-ops outside their applicable states. Stop cancels future expiry and returns the timer to idle without emitting an expiry event. Q275 defines the initial `expired` payload and explicitly defers other timer state-change notifications.

A named timer uses simulation time and emits `expired` once for that activation. Expiry has no automatic success, failure, damage, or wipe effect. A rule decides the response. Phase `duration` and `deadline` retain their existing success and failure meanings and are not silently converted to passive timers.

Phase declarations reset with phase re-entry; encounter declarations persist for the attempt. Private state inside a reusable mechanic is independent for each occurrence, and repeat-local state is fresh per iteration. Scoped references follow Q81. Stopping or restarting a timer invalidates its superseded scheduled expiry, while already admitted events preserve their original identity under Q84.

An explicitly authored timer-expiry rule can restart the timer for a regular pulse. The timer must have a positive duration, and it never advances while simulation time is stopped. This supplies regular pulses through the same typed rule contract.

[Q164](event-conditions.md) accepts typed `event_value` comparisons and optional-field presence checks in event-rule guards. Unknown fields fail validation; comparisons inspect immutable event values without arbitrary event-object traversal.

[Q169-Q170](roles-and-player-state.md) accept `has_role` for current membership and `player_state` for online/life-state checks. Both use the existing implicit-player or explicit typed-target convention. Role membership and current action eligibility remain separate.

[Q275](event-catalog-conventions.md) accepts concrete named-timer expiry fields and an explicit initial catalog limited to `expired`; additional timer state notifications remain optional future work.
