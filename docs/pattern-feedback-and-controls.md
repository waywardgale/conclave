# Pattern feedback and progress controls

Status: Q195-Q196 are accepted. They refine the accepted matcher, progress ownership, typed event, and action contracts. These decisions are independent: ordinary submission events do not require a reset action, and the reset action defines its own change notification. No implementation exists.

## Q195: matcher events and private feedback

Accepted: expose `matched` and `mismatched` for admitted token outcomes, `player_completed` for a personal finish, and the existing overall `completed`. Subscribe through the ordinary `source: {mechanic: symbol_lock}` form. Keep the accepted `started` event after initialization; it has no triggering player or expected-answer payload.

```yaml
on:
  source:
    mechanic: symbol_lock
  event: mismatched
```

This fragment selects the source of a rule. Its authored actions can provide feedback or impose a gameplay consequence. The event itself performs no damage, failure, sound, text, or clue reveal.

| Event | When it occurs | Public fields |
| --- | --- | --- |
| `matched` | An admitted token advances its progress record, including the final correct token. | `token`, `origin`, `progress_before`, `progress_after`, `pattern_length`, `record_completed`, and attributed `player` as specified below. |
| `mismatched` | An admitted declared token is wrong for the current position or exceeds the remaining unordered count. | `token`, `origin`, `progress_before`, `progress_after`, `pattern_length`, and attributed `player` as specified below. |
| `player_completed` | One captured player's personal record first finishes in per-player mode. | Required typed `player` and `pattern_length`. |
| `completed` | The shared record or the per-player aggregate requirement first finishes the mechanic. | Common `elapsed` under Q224, with no player or answer payload. |

`token` is the submitted local string ID from this matcher's vocabulary. It is not the expected next token. `origin` is `interaction` for a direct Q192 binding or `action` for Q194's `submit_token`, regardless of what originally triggered the rule. Keep more detailed action provenance in authorized diagnostics rather than adding arbitrary target objects to the payload.

`progress_before` and `progress_after` are nonnegative integer counts of correctly admitted tokens in the applicable record immediately before and after this operation. An ordered count is a prefix length; an unordered count includes repeated required values. `pattern_length` is the positive length of that record's answer. A match adds one, and a mismatch sets progress to zero even if it was already zero. `record_completed` is a required boolean on `matched`, true exactly when this token finished that record. It does not claim the whole per-player requirement has finished.

These fields support ordinary `event_value` comparisons. For example, `field: record_completed` with `equals: true` identifies a final correct submission without comparing two dynamic event fields. No event supplies the expected next token, complete answer, other players' answers, or a list of earlier inputs. Additional list-valued payloads require a separate registered contract.

### Player attribution

For a shared matcher, `matched.player` and `mismatched.player` are optional. Direct input always supplies its player; an authored submission may be unattributed. Keep the static shared-event schema optional even when a particular manifest currently uses only direct bindings. A later authored route must not change the meaning of a public event schema.

For a per-player matcher, those fields are required and identify the captured solver whose record changed. `player_completed.player` is always required and has the same meaning. `player_completed` is unavailable on a shared matcher. Overall `completed` never selects the last interactor, all contributors, or an arbitrary player as a winner. Authors use the personal event or an explicit recipient selection for rewards.

Permit `per_player: true` rule limits only on these events with a required player in the source schema. Without explicit Q223 player selection, reject it on shared `matched`/`mismatched`; overall `completed` has no player field to select. A presence guard can inspect an optional field but does not strengthen its schema for invocation limits. Forwarding through `export.events` preserves requiredness and privacy. Actions receiving an optional player retain their own documented missing-target behavior; there is no implicit fallback player or general type-narrowing language.

[Q223-Q224](event-players-and-mechanic-results.md) accept explicit subscription-level player selection and a common `elapsed` field on mechanic completion. They retain the base matcher's optional/required player distinction and supply no player or answer on overall completion.

### Commitment, ordering, and rejected input

Commit the progress change, any personal completion, and any resulting overall completion as one matching operation before exposing its notifications. Queue the token outcome first, then `player_completed` when applicable, then overall `completed` when applicable. A final correct token can therefore produce all three notifications in per-player mode. A mismatch produces only `mismatched`, with the reset already committed.

Deliver the admitted final outcome notifications to eligible enclosing subscriptions under the existing terminal-event rules. Ending the matcher must not erase its own committed completion notifications or revive subscriptions whose owners have already ended. A rule responding to the final `matched` cannot undo completion that has already committed. Later events from other operations may change current state before a listener executes; the recorded payload stays historical.

In shared mode, a mismatch resets the shared record; in per-player mode, it resets only the attributed player's unfinished record. Preserve Q192's rule that an automatic mismatch does not cancel other otherwise valid holds. Reaching personal or overall completion cancels input that can no longer be admitted.

Rejected input, cooldown blocks, interrupted holds, unknown tokens, unavailable targets, submissions to completed records, and duplicate reports of a committed operation emit none of these successful gameplay notifications. An undeclared token remains a validation or contract problem rather than a player mistake. Distinct admitted wrong submissions each emit a mismatch, even when the count was already zero. Rules can deliberately limit repeated consequences through their accepted invocation settings.

### Disclosure

These are server-side rule notifications. Neither publication of the event schema nor observing an event in an enclosing reusable mechanic sends tokens, lengths, progress, or answers to clients. Presenting a result or clue still requires the authored audience and accepted spectator-information policy. Ordinary logs must not expose private puzzle values. [Q197-Q198](pattern-presentation-and-clues.md) accept token appearance and clue reveal. [Q199-Q200](pattern-state-and-progress-display.md) accept current progress queries and optional displays.

## Q196: clearing unfinished pattern progress

Accepted: add `reset_pattern` for an initialized active matcher. Require `mechanic`. In shared mode, reset its one unfinished progress record and reject player-selection fields. In per-player mode, require exactly one typed `player` or explicit `all: true`, meaning every captured unfinished record.

```yaml
reset_pattern:
  mechanic: symbol_lock
  player:
    event: player
```

This form addresses a per-player matcher from a source that supplies the required player. Use `reset_pattern: {mechanic: symbol_lock}` for shared progress. To clear all unfinished personal records, use `reset_pattern: {mechanic: symbol_lock, all: true}`. Reject `all: false`, both selectors together, and missing selectors in per-player mode. There is no implicit event-player selection or broad player query in this initial control.

### State that changes and state that stays

Clear the selected unfinished record's correctly admitted token history and count. Cancel its active direct-input holds, including when its count is already zero, so a player must begin a fresh use after an explicit reset. In shared mode, this cancels every current hold contributing to that shared record. In per-player mode, it cancels only holds for the selected unfinished records. Delayed packets from those cancelled holds cannot commit.

Keep the expected answer, captured solver set, personal completion, overall completion, direct-input cooldowns, rule invocation limits, and other mechanic state. Clearing progress cannot refund a cooldown or restart `started`. Completed personal records remain completed even while other players are unfinished. Selecting such a record is a no-op.

The action controls retained progress state, so the selected captured player need not currently be online, alive, or eligible to submit. It grants no gameplay permission or revival. A known identity without a record in this matcher is a no-op; an unknown reference or incompatible target type is a validation/contract error. `all: true` operates on the existing captured set and enrolls nobody.

A new expected answer or a complete replay requires a new mechanic activation, using accepted repetition or phase re-entry. Do not add in-place redraw, answer replacement, reopening completed records, or a generic restart action in this initial matcher contract. New activations capture fresh progress and participants under Q193, while old references remain bound to their original activation.

### Explicit reset notifications

The reset capability adds `progress_reset` on the target matcher only when an unfinished record with nonzero progress is cleared. Its fields are `progress_before`, `progress_after` fixed at zero, and `pattern_length`. For per-player progress it also has required typed `player`, the affected solver. For shared progress it has no player field, even when another player event triggered the reset action.

An automatic mismatch emits its own accepted mismatch notification and no separate `progress_reset`. This keeps an authored mistake handler from accidentally reacting twice to the same mistake. A reset does not emit `matched`, `mismatched`, `player_completed`, `completed`, or `started`.

For `all: true`, validate the operation and clear all affected records and holds before queuing notifications in the captured solver order. Emit one `progress_reset` per record whose nonzero count changed. Listeners see the committed operation, and no aggregate event with a list of player identities is needed. Per-player reset events have an unambiguous affected player for rule invocation limits; the shared event does not.

Resetting empty progress or cancelling only a hold emits no `progress_reset`, because no admitted pattern progress changed. The cancelled input session still receives any necessary ordinary private interaction-state update. No-op, failed, and deduplicated retry operations emit no progress-change event. A reset listener resetting the same still-empty record therefore produces no event loop. Authored actions that submit new tokens and reset repeatedly remain bounded by the engine's existing limits.

### Lifetime and operation identity

A declared pending matcher follows the existing required/recoverable unavailable-target policy. Do not initialize it, buffer a reset, or invent an empty progress record. A known completed, failed, or cancelled occurrence is a no-op and retains its terminal outcome. An unknown declaration fails validation.

Use the same permitted reference scopes and public capability boundary as `submit_token`. A wrapper cannot expose a private child through a dotted path or mutable exported handle. Idempotent retries of one reset operation cannot clear later progress a second time. Distinct authored reset actions are distinct operations and may clear progress admitted between them.

Respect authoritative operation order. Input committed before the reset contributes to the state being cleared; a valid subsequent authored submission starts against empty progress. Cancelling physical holds does not discard unrelated queued rule events or silently retarget them. Old work cannot reach a replacement activation.

## Related contracts

These contracts build on [pattern definitions and direct inputs](pattern-definitions-and-inputs.md), [progress ownership and authored submissions](pattern-progress-and-submission.md), [typed event comparisons](event-conditions.md), [event rules and limits](phases-and-rules.md), [execution and errors](execution-and-errors.md), and [reusable event boundaries](reusable-mechanic-contracts.md). [Q197-Q198](pattern-presentation-and-clues.md) accept token presentation and clue revelation. [Q199-Q200](pattern-state-and-progress-display.md) accept current-state predicates and progress displays. [Q201-Q202](pattern-parameters.md) accept typed pattern reuse.
