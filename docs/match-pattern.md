# Match pattern reference

Status: this reference consolidates accepted Q33, Q62, and Q191-Q205. It adds no decisions. The linked contracts retain the detailed validation, lifecycle, and privacy rules. No matcher implementation exists.

## Configure a matcher

This illustrative objective uses two arena-bound block locations. It is not a shipped encounter or a complete manifest.

```yaml
- id: symbol_lock
  type: match_pattern
  tokens: [sun, moon]
  pattern: [sun, moon, sun]
  inputs:
    - token: sun
      targets:
        - block: sun_button
    - token: moon
      targets:
        - block: moon_button
```

By default, eligible participants contribute to one ordered answer. Each successful physical use submits one token. A wrong declared token clears partial progress, consumes that submission, and keeps the same answer. Completing the answer completes the mechanic. Mismatch has no automatic punishment.

| Authoring choice | Accepted behavior | Contract |
| --- | --- | --- |
| `tokens`, `pattern`, `ordered` | Unique named vocabulary; a fixed answer, sampled tokens, or a chosen complete answer. Ordered matching is the default; unordered matching counts repeated values. | [Definitions, Q191](pattern-definitions-and-inputs.md#q191-declared-tokens-and-stable-expected-patterns) |
| `inputs`, `players`, `use_cooldown` | Repeatable typed block/group interactions with optional filters and holds. Server validation admits one token per fresh gesture per matcher. | [Direct inputs, Q192](pattern-definitions-and-inputs.md#q192-repeatable-interaction-bindings-for-pattern-input) |
| `progress`, `completion`, `pattern_per_player` | Shared progress by default. Per-player progress captures solvers once and completes for all, any, or a positive count. Independent answers are optional. | [Progress, Q193](pattern-progress-and-submission.md#q193-progress-ownership-required-players-and-completion) |
| `token_display` | Optional readable names, icons, text styles, and translated names for declared tokens. | [Presentation, Q197](pattern-presentation-and-clues.md) |
| `params` and `with` | Reuse typed `pattern_tokens`, `pattern`, `token_display`, and `pattern_inputs` configuration through whole-value binding. | [Parameters, Q201-Q202](pattern-parameters.md) |

Resolve the answer during initialization, before the matcher's `started` event. Random answers stay fixed through mistakes, resets, and reconnects. Passing the same constructor to separate matchers does not share their draws. A new activation can draw again.

Per-player completion stays recorded through death, disconnects, and role changes. The captured solver set does not shrink or recruit newcomers. Input still checks current permission. Retaining progress grants no physical input rights or permission to rejoin after grace expires.

## Submit, inspect, and react

| Capability | Purpose | Contract |
| --- | --- | --- |
| `submit_token` | Submit a declared literal token from an authored rule. Shared progress permits unattributed input; per-player progress requires its captured solver. | [Q194](pattern-progress-and-submission.md#q194-token-submission-from-authored-rules) |
| `matched`, `mismatched`, `player_completed`, `completed` | React to committed progress and completion using typed event data. No event exposes the expected next token or whole answer. | [Q195](pattern-feedback-and-controls.md) |
| `reset_pattern`, `progress_reset` | Clear unfinished progress and affected holds. Preserve answers, completed records, cooldowns, and captured solvers. | [Q196](pattern-feedback-and-controls.md) |
| `pattern_state` | Check a record's current count/completion or the completed-player count. Successful terminal summaries follow the owning scope's lifetime. | [Q199](pattern-state-and-progress-display.md) |

Direct inputs and authored submissions may coexist. Every possible answer token needs a declared route, but validation cannot prove that the author's guards will make the puzzle solvable. Action-only matchers omit `inputs`; an explicitly empty list is invalid.

A pending matcher cannot receive buffered input. A known terminal matcher accepts no new progress. Reusable wrappers keep child state private; `export.events` does not expose a mutable matcher handle.

## Choose what players see

All three presentation actions require an active matcher and explicit audience. They default to HUD output and support `display: world` at a captured named arena location.

| Action | Information disclosed | Contract |
| --- | --- | --- |
| `show_token` | One declared literal token, including an intentional decoy. It does not inspect the chosen answer. | [Q204](token-labels-and-world-progress.md#q204-explicit-fixed-token-labels) |
| `reveal_pattern` | The expected answer or explicitly selected positions. Per-player mode selects one solver or each recipient's own answer. | [Q198](pattern-presentation-and-clues.md), [world output, Q203](world-pattern-clues.md) |
| `show_pattern_progress` | Current matched count or completed-solver summary. `show_total: false` hides the denominator without sending a hidden fraction. | [Q200](pattern-state-and-progress-display.md), [world output, Q205](token-labels-and-world-progress.md#q205-pattern-progress-placed-in-the-world) |

Output lasts five simulation seconds by default, a positive authored duration, or until explicitly stopped with a named playback. Matcher end always clears it. Recipients are captured once and must remain entitled; reconnect restores only still-current output with its existing remaining time.

World output uses ordinary occlusion and has no collision or interaction behavior. It does not automatically label a nearby button, change a block's appearance, or submit a token. Selected viewers may see different private answers or counts at the same location. Render distance does not grant audience permission or force chunk loading.

## Implementation boundaries

The contracts require authoritative server state, typed validation, bounded work, captured content revisions, and private client delivery. Concrete engine limits, final HUD layout, client synchronization, and integration testing remain outstanding. These implementation tasks do not reopen the accepted matcher authoring vocabulary.
