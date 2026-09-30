# Pattern definitions and interaction inputs

Status: Q191-Q192 are accepted. These contracts define token vocabulary, expected-answer construction, and direct interaction bindings. Ordered/unordered matching, stable answers, shared progress by default, optional per-player progress, and private clues retain their accepted meanings. No implementation exists.

[Q193-Q194](pattern-progress-and-submission.md) accept concrete progress/completion fields and rule-driven submission. The [matcher reference](match-pattern.md) indexes the complete accepted contract.

## Q191: declared tokens and stable expected patterns

Accepted: give each `match_pattern` occurrence a nonempty `tokens` list of unique local `snake_case` string IDs. These identify logical values such as `sun`, `moon`, or `left`, independently of their eventual names, icons, sounds, or world representations. Tokens are local to the mechanic's configured vocabulary, not global content definitions or arbitrary Minecraft item IDs.

Use `pattern` for the expected answer and `ordered`, default true, for matching order:

```yaml
- id: symbol_lock
  type: match_pattern
  tokens: [sun, moon, star]
  pattern: [sun, moon, sun]
  ordered: true
```

This fragment defines the answer only; input bindings are configured separately. Require a nonempty pattern whose values all reference declared tokens. Repeated values are meaningful and allowed. Display text cannot become a token implicitly, and changing a later display name does not rename its logical identity. Reject implicit numeric or boolean tokens and unknown IDs rather than coercing them to strings. [Q197-Q198](pattern-presentation-and-clues.md) accept symbol presentation and clue-reveal fields as separate decisions.

### Fixed, sampled, or chosen answers

Accept exactly one of three `pattern` forms. A literal list fixes the answer. A `sample` mapping builds an answer from token choices. A `choose` mapping selects one complete authored answer. Never combine these forms or infer a draw merely because a list contains several values.

Apply the engine's pattern-length and bounded-work limits to all forms, including vocabulary size, pool size, and the number and length of complete alternatives. No form bypasses the existing publication and runtime budgets by nesting a larger list.

```yaml
pattern:
  sample:
    from: [sun, moon, star]
    length: 3
    repeats: false
```

`sample.length` is a positive integer within the engine's pattern-length limit. Optional `from` is a nonempty unique subset of the declared tokens and defaults to the complete vocabulary. `repeats` defaults to false. Without repeats, sample a uniform ordered sequence without replacement and require enough distinct pool members. With repeats, each position is an independent uniform draw from the pool. Duplicate pool entries cannot act as hidden weights.

```yaml
pattern:
  choose:
    - [sun, moon, star]
    - [star, sun, moon]
```

`choose` is a nonempty list of complete, nonempty token sequences. Select an alternative uniformly once. Alternatives may have different lengths or contain repeated tokens. Reject duplicate equivalent alternatives instead of treating repetition as an implicit weight. With `ordered: false`, equivalence compares token counts, so differently ordered copies of the same multiset are duplicates. Explicit weighting is outside this initial shape; neither form silently borrows the unrelated random-player assignment contract.

Resolve and retain the expected answer on the server as part of mechanic initialization, before its `started` rules can reveal or otherwise observe it. Checking a condition, reconnecting, submitting a wrong token, hiding a clue, or receiving a client retry does not draw again. A new mechanic activation can draw a new answer. Keep chosen values and any diagnostic random state within the existing authorized inspection boundary; no public seed or client-supplied random choice is required.

The accepted shared answer remains the default. [Q193](pattern-progress-and-submission.md#q193-progress-ownership-required-players-and-completion) accepts the concrete configuration for separately sampled per-player answers with progress and completion. This decision supplies answer constructors without silently deciding who gets a distinct answer or which participants must finish.

### Matching and mismatch

With `ordered: true`, compare each admitted token with the next expected position. With false, accept a token only while its submitted count is below the expected count for that value. Thus an unordered answer `[sun, sun, moon]` still needs two suns and one moon. A token absent from the chosen answer, or an excess copy, produces an immediate mismatch; do not wait for a fixed-length batch to discover the extra value.

A mismatch clears only the applicable partial progress under Q62 and emits the accepted mismatch notification. Consume the mismatching submission without treating it as the first token of another run. The next admitted submission starts from empty progress against the same expected answer. For example, against `[sun, moon]`, the second `sun` in `sun, sun, moon` resets progress; the following `moon` then mismatches an empty sequence.

A declared token that is wrong for this answer is ordinary gameplay input. An undeclared token or invalid source is a configuration or input-validation problem, not a fabricated mistake by a player. Mismatch alone does not fail the mechanic, damage anyone, or change a phase. Q193 accepts completion requirements, and Q194 accepts authored submission. [Q195-Q196](pattern-feedback-and-controls.md) accept event payloads and explicit progress resets while preserving the stable answer.

The server retains the complete expected answer. Neither the vocabulary declaration nor the existence of matching progress automatically sends it to clients. New catalog/list parameter types are not implied by the existing scalar `string` or `enum` parameter families. [Q201-Q202](pattern-parameters.md) accept registered contracts for whole-pattern and input-list reuse.

## Q192: repeatable interaction bindings for pattern input

Accepted: give the direct interaction form of `match_pattern` a nonempty `inputs` list. Each entry names one `token` and a nonempty list of typed `targets`, using the accepted `{block: location}` and `{group: spawn_group}` forms. A completed valid use of any target in that entry submits its configured token once.

```yaml
inputs:
  - token: sun
    targets:
      - block: sun_button
  - token: moon
    targets:
      - block: moon_button
```

These bindings stay active while the relevant pattern progress can still receive input. They do not instantiate an `interact` objective that finishes after one use, and they do not need an artificially large `uses` count. They reuse the accepted input-validation behavior, including fresh presses, server-owned holds, physical reach, line of sight, and stale-session rejection. Hidden child objectives and their completion events are not part of this authoring form.

Allow optional mechanic-level `players` to select eligible input participants. Its default is the existing living, online, eligible participant set. Each input entry may add its own `players` filter; the participant must satisfy both selectors. Input permission grants no access to the expected answer or to another player's private clue.

Explicit selectors cannot make a dead, offline, or otherwise ineligible observer able to submit a physical interaction. The input capability still checks its intrinsic availability requirements.

Each input entry may specify `hold`, `reach`, `interrupt_on_damage`, and `consume_interaction` with Q175-Q177's meanings and defaults: instant `0s`, native reach unless narrowed, no damage interruption, and native pass-through. Hold progress belongs to that player and physical target. Releasing the control, changing target, losing eligibility, death, disconnection, or other accepted interruption resets the unfinished hold. Players never pool a hold, and holding continuously cannot submit repeated tokens.

Add optional mechanic-level `use_cooldown`, default `0s`, measured in simulation time separately for each player across all of that matcher's input bindings. Start it after an admitted token submission, including a mismatch. A rejected or interrupted use starts no cooldown. Attempts during cooldown do not queue for later; another fresh press is required. Do not add `uses` or `distinct_players` to pattern input bindings, since they would confuse input admission with completion of the answer.

### Target identity and competing inputs

One physical target must identify one input entry within a matcher. Separate nonoverlapping entries may submit the same token with different holds or eligibility. Reject duplicate targets and provable overlapping bindings before publication, including bound location aliases that name the same block cell. Do not resolve two token meanings by whichever YAML entry happens to be first.

Use the actual physical target identity from the validated interaction, not a nearest-NPC guess. Group bindings retain their accepted activation identity and can include later members of that same group. Declared future targets may wait until available; their absence never submits a token or shrinks the random-answer pool. Unexpected runtime ambiguity is a contract error under Q69, not a mismatch credited to the player.

Admit at most one token per fresh gesture for this matcher. Both-hand reports, network retries, and repeated observations cannot duplicate it. The server derives the token from its binding; the client cannot replace it with an arbitrary answer. Different explicitly authored mechanics may still observe the same physical gesture once each under Q177. Any qualifying input that consumes the gesture suppresses native interaction through the existing shared input path.

When a hold begins, retain its input binding and current progress owner, but evaluate correctness only when the submission commits. Other eligible players may have advanced shared progress in the meantime. Concurrent completed inputs use Q68's stable admitted server order. A mismatch does not automatically cancel other valid holds; their later submissions evaluate the reset progress. Cancellation or completion of the relevant matcher state cancels input that can no longer be admitted.

With native pass-through, a credited token does not prove the underlying button, chest, or NPC operation succeeded. A native interface taking focus can interrupt an unfinished hold. Once the matcher no longer admits input, it does not continue consuming unrelated Minecraft interactions. Invalid or ineligible input cannot be converted into a punitive mismatch.

### Authoring coverage and limits of this input form

Validate that every token that can appear in an expected answer has a declared input route. Tokens outside the chosen answer can have routes as deliberate decoys. A future declared group or an explicitly gated player filter is a configured route even if it is not currently usable; validation does not prove that the author's encounter will eventually make every target reachable.

Input binding and answer selection are independent. The same bindings work with a fixed answer, a sampled token sequence, or a chosen complete pattern. This first input form does not itself add a general `submit_token` action, damage-triggered submission, chat parsing, or a HUD answer picker. [Q194](pattern-progress-and-submission.md#q194-token-submission-from-authored-rules) accepts a separate rule-driven action with explicit provenance and admission rules. Other sources still need their own supported contracts.

[Q193-Q194](pattern-progress-and-submission.md) accept shared/per-player progress fields, required-player selection, personal completion, progress retention through lifecycle changes, and authored input. [Q195-Q196](pattern-feedback-and-controls.md) accept detailed events and progress reset controls. [Q197-Q198](pattern-presentation-and-clues.md) accept clue revelation. Completed-use retention from `interact` does not silently settle those pattern-specific choices.

## Related contracts

These contracts refine [the accepted matcher behavior](mechanics-and-participants.md#q33-match_pattern), [shared progress and private clues](selection-and-patterns.md#q62-shared-pattern-progress-and-private-clues), [typed interaction targets](capture-and-interaction-targets.md#q174-typed-interaction-targets), and [interaction input validation](interaction-input-and-progress.md). They preserve the existing event queue, simulation clock, publication isolation, and required/recoverable error handling.
