# Pattern state and progress display

Status: Q199-Q200 are accepted. The development build implements Q199 server conditions, including explicit event players, implicit `any`/`all` members, exact percentages and retained successful summaries. Q200 authored HUD output remains pending. See [implementation status](implementation-status.md).

## Q199: current pattern progress and personal completion

Accepted: add `pattern_state` with a required `mechanic` reference. Inspect one progress record using `progress`, a nested numeric comparison, and/or `completed`, a boolean. Supplied checks use AND. Require at least one check; an empty mapping does not mean that the matcher exists or has started.

```yaml
pattern_state:
  mechanic: symbol_lock
  progress:
    at_least: 2
```

The shared form reads the one shared record. For a per-player matcher, require a typed `player` or an unambiguous implicit player from an enclosing member condition. A normal event rule does not implicitly supply its triggering player to this predicate; reference the documented field explicitly.

```yaml
pattern_state:
  mechanic: personal_symbols
  player:
    event: player
  completed: true
```

An explicit player takes precedence over an enclosing implicit member. Reject an explicit player on a shared matcher, incompatible reference types, or a per-player record query without either subject. A valid runtime player with no captured record yields false. The query does not enroll that player or silently read another person's progress.

This capability deliberately uses `player` for the owner of a personal pattern record, consistent with the matcher actions. It does not change the existing `target` field on predicates that inspect general player or NPC state.

### Progress values and completion

Use exactly one of `equals`, `at_least`, `at_most`, `greater_than`, or `less_than` inside `progress`. A nonnegative integer compares the number of correctly admitted tokens. Ordered progress is the current correct prefix length; unordered progress counts accepted occurrences, including repeats. It is neither the number of all historical button presses nor a lifetime score.

An explicit percentage from `0%` through `100%` compares that count against this record's positive expected-pattern length. This extends the existing explicit-unit convention to pattern progress. Do not average participants or use the longest possible randomized answer as the denominator. Evaluate the exact ratio against the authored value, without rounding to a displayed integer percentage or adding an equality tolerance. Use integer counts for exact token thresholds.

Reject fractional token counts, negative values, non-finite values, implicit percentage coercion, multiple comparators in one node, and unsupported units. Thresholds remain bounded by the capability's numeric limits. A threshold larger than a particular answer may simply remain false; it does not change the answer length. Express ranges through ordinary `and` conditions.

`completed` compares this record's latched completion with the supplied boolean. For shared mode, it corresponds to the matcher's overall successful completion. For per-player mode, it refers only to the selected solver. A selected unfinished record stays incomplete even if another player satisfies `completion: any` and finishes the enclosing matcher.

Keep `completed: {mechanic: ...}` as the existing general predicate for overall mechanic completion. Do not add an implicit player mode to that predicate. An author requiring the whole matcher should use that existing form; an author checking one solver uses `pattern_state`.

Live progress can fall after a mismatch or explicit reset. Its condition is not automatically latched. Condition objectives retain their explicit `latch` option, while personal and overall mechanic completion retain their accepted completion guarantees.

### Counting finished solvers

As an alternative to a record query, allow `completed_players` on a per-player matcher:

```yaml
pattern_state:
  mechanic: personal_symbols
  completed_players:
    at_least: 3
```

This counts completed records in the captured solver set, using exactly one ordinary numeric comparator and a bounded nonnegative integer. It has no percentage form. The count includes recorded finishes by players who later die, disconnect, lose a role, or stop matching an input filter. Never recalculate the cohort from the current living roster or lower the required completion goal.

Reject `completed_players` on shared progress and reject combining it with `player`, `progress`, or record-level `completed`. Combine distinct aggregate and personal checks using `and` when needed. An enclosing implicit player does not restrict this aggregate query. No new player collection, arbitrary list traversal, or stored public-output handle is introduced.

### Unavailable and retained records

Read one coherent authoritative state per evaluation. An initialized active matcher supplies current measurements, including retained personal records for explicitly selected dead or offline players. Querying retained progress does not require a live body or current permission to submit input.

A declared pending matcher, a missing optional player value, or an identity without a captured record makes the predicate false, including comparisons against zero and `completed: false`. Missing state is not an empty initialized record. Unknown declarations, unknown event fields, wrong reference kinds, and unsupported capabilities still fail validation. `not` keeps its ordinary Boolean meaning; negating a false missing-record query can therefore be true.

After successful overall completion, retain a compact immutable summary with the existing enclosing-scope completion record. It contains each relevant record's final progress count, answer length, and completion flag, plus the captured cohort and completed-player count where applicable. This makes the final successful state queryable without reopening input. In an `any` or count-based completion, unfinished personal records retain their actual final count and false completion flag.

The summary does not retain a queryable answer array or submitted-token history. It authorizes neither post-completion `reveal_pattern` nor a reset of a terminal result. It expires with the completion record's accepted owner; it is not a history archive across repeat iterations or discarded phases. Existing work and retained-state budgets apply.

A failed or cancelled matcher supplies no progress view through this predicate. Its unfinished records are unavailable rather than successful zero-progress records. This does not manufacture completion or change a previously recorded result; the general completion contract remains authoritative. Diagnostics may keep permitted historical information under their separate policy.

Use existing scoped and captured activation references. An old query context cannot retarget a replacement occurrence. Direct reuse of a `match_pattern` definition exposes this capability at its occurrence. A `layers`, sequence, parallel, or repeat wrapper exposes its own public result and events, not a private child's `pattern_state`. Conditions send no puzzle data to clients and perform no reset, reveal, random draw, or input operation.

## Q200: optional pattern progress on the HUD

Accepted: add `show_pattern_progress`, requiring `mechanic` and an explicit presentation `audience`. It creates a noninteractive HUD counter of current authoritative progress, independently of `reveal_pattern`. A clue does not automatically gain a progress meter, and declaring a matcher does not automatically display one.

```yaml
show_pattern_progress:
  id: symbol_progress
  mechanic: symbol_lock
  audience:
    from: participants
  show_total: false
  until_stopped: true
```

This shared example shows a counter such as "2 matched" until stopped or the matcher ends. It sends no expected values, submitted-token sequence, next-token hint, per-token correctness colors, or interaction target identities.

### Selecting the measurement

Shared progress uses the one shared record and rejects all personal/summary selectors. For a per-player matcher, require exactly one of typed `player`, `each_recipient: true`, or `summary: true`.

A typed player chooses whose record every selected recipient sees. `each_recipient: true` gives each selected solver only their own record. These two forms use Q198's record-ownership checks independently of audience selection. In particular, every selected recipient in the convenience form must have a captured solver record; preflight the mapping before dispatch instead of silently shrinking the audience. A specific player's record remains readable while that player is dead, offline, or currently ineligible for input.

`summary: true` shows the number of captured players who have completed their record against the matcher's required completion goal. The goal is the captured cohort size for `all`, one for `any`, or the authored count. Thus an encounter with six captured solvers and `completion: {count: 3}` can show "2 of 3 required players finished". It does not change its denominator to six or shrink it after a disconnect.

Summary output includes no player names, roster list, per-player counts, or clue values. Reject false selector flags, conflicting selectors, and a missing selector in per-player mode. This is not a general scoreboard or an arbitrary query-to-HUD binding.

### Disclosing counts and totals

Default `show_total` to true. A record view shows current matched tokens and that record's actual expected length, such as "2 of 5 matched". A summary view shows completed players and the required goal. This explicitly discloses the relevant denominator, including answer length when the record view is selected.

With `show_total: false`, display only the count, such as "2 matched" or "2 players finished". Send no denominator, percentage, bar fraction, or hidden full-length slot list. This prevents an omitted total from being merely hidden by the client. It does not promise that players cannot infer length through their own observations or other intentionally revealed cues.

A personal view may show that the selected record is complete while the matcher waits for others. This discloses that record's completion status, including when totals are hidden. It does not mark all viewers or all captured players complete. Overall matcher completion ends the display rather than keeping a post-completion scoreboard.

Use the mechanic's readable name, falling back to its ID, as the counter label. A single explicitly selected player's view also identifies that player, so a reader knows whose progress is shown; the own-record form identifies it as the recipient's progress. An optional `style` uses the accepted text-style contract and defaults to `conclave:normal`. Standard counter wording is client-localized text. Do not introduce a template expression, arbitrary layout language, automatic voice, or a new model consumer.

### Current state, recipients, and lifetime

Populate the display from one coherent current snapshot at dispatch, then update from committed state. This is a current counter, not a replay of `matched` events. Mismatch and reset immediately change the represented count; rejected input, held-use progress, and retries do not add tokens. Coalesce network updates when appropriate so a delayed client sees the latest count without a backlog of obsolete transitions. Client values and acknowledgements never decide completion.

Adopt Q198's explicit audience, captured recipient identities, continued eligibility checks, optional location origin for radius, spectator restrictions, and reconnect behavior. A reader can receive another player's count when explicitly selected; input permission or a watched camera alone does not grant it. Resume only still-active output to an original eligible recipient after reconnect, with current counts and the original remaining lifetime. Do not recruit newly matching identities or replay missed increments.

Reuse Q198's five-second simulation-time default, positive `duration`, or `until_stopped: true` with required `id`. Those timing forms remain mutually exclusive. Reuse phase/encounter ownership, `stop_presentation`, and named replacement. IDs share the presentation namespace, so deliberately reusing a clue's active ID replaces that clue according to Q138; use different IDs to keep both outputs.

An initialized active matcher is required. Pending targets follow the existing required/recoverable unavailable-target policy. A known terminal occurrence creates no new progress display. Clear an existing display at matcher completion, failure, cancellation, explicit stop, expiry, or owner cleanup. A successful retained server summary under Q199 is not permission to keep this HUD alive or to query private child state through a wrapper.

Use the attempt's captured configuration, normal resource-readiness checks, and Q137's cosmetic or explicitly required dispatch handling. Empty valid audiences remain no-ops. Keep display instances and update work within the supported UI and engine budgets. Final layout and numeric capacity remain part of the client and operational-limit design.

## Related contracts

These contracts extend [progress ownership](pattern-progress-and-submission.md), [feedback and reset](pattern-feedback-and-controls.md), [clue presentation](pattern-presentation-and-clues.md), [condition composition](conditions-and-composition.md), [runtime references](manifest-references.md), and [named playback](presentation-controls-and-models.md). [Q201-Q202](pattern-parameters.md) accept typed pattern reuse. [Q203](world-pattern-clues.md) accepts world-space clue output as a separate capability from the progress HUD. [Q205](token-labels-and-world-progress.md#q205-pattern-progress-placed-in-the-world) accepts a world consumer for this counter.
