# Pattern progress and rule-driven submission

Status: Q193-Q194 are accepted. Q193 specifies the already accepted shared/per-player progress choice and its completion rules. Q194 adds an authored submission path to the accepted matcher. These decisions are independent of one another. No implementation exists.

## Q193: progress ownership, required players, and completion

Accepted: add `progress`, accepting `shared` by default or explicit `per_player`. Shared mode keeps one progress record that eligible participants can advance together. Completing its expected pattern completes the mechanic once. It does not capture a separate required set of solvers, and an empty current input selection cannot complete it.

For `per_player`, capture the configured mechanic-level `players` selection once during initialization and create one progress record for each selected participant. Use the accepted living, online, eligible participant defaults when fields are omitted. Authors can explicitly include tracked dead, offline, or non-active identities with the life, connection, and Q238 participation filters, such as `participation: any` when needed. Require the selected set to belong to this attempt; an outsider cannot receive a private progress record merely because a world-player selector included them.

```yaml
progress: per_player
players:
  role: runner
completion: all
pattern_per_player: true
```

Capture the set and initialize its answers before the mechanic's `started` event. If a role must first be assigned, set it in the enclosing scope's startup before activating this child. Do not create an empty frozen set and then recruit whoever happens to become a runner later. This capture belongs to the matcher; it does not change the general rule that a `players` parameter contains a query configuration.

Continue checking the configured player query for input eligibility after capture, along with the input source's own requirements. New matches cannot join this captured set, and a selected member who temporarily stops matching is not removed from it. Per-input filters restrict particular controls but do not define or shrink the required set. Captured identity and current permission to submit remain separate.

### Completion requirements

In per-player mode, allow `completion: all`, the default, `completion: any`, or `completion: {count: N}` with positive integer `N`. Count distinct captured players whose own pattern is complete. `all` needs every captured member, `any` needs one, and `count` needs at least the authored number. All forms require a nonempty set. Reject these fields in shared mode, where there is only one answer-progress record to complete.

An empty initial set or a count greater than its size is an `insufficient_players` initialization failure under the existing required/recoverable policy. Do not silently lower the requested count, wait indefinitely for new members to join the frozen set, or award empty-set success. Static invalid values fail validation before activation.

Personal completion stays latched. Further submissions to that completed record are rejected without a mismatch, another completion, or a new cooldown. Other required players can continue until the enclosing matcher completes. When its requirement is met, latch the overall result once and stop unfinished input sessions. Unfinished personal records do not become completed merely because `any` or the requested count ended the mechanic.

### Shared or independent expected answers

Add `pattern_per_player`, default false. False resolves one expected pattern and shares those immutable values across progress records. True is valid only with `progress: per_player` and resolves the accepted `pattern` constructor separately for each captured participant during initialization.

Independent random draws may coincide; the setting does not promise unique answers. A fixed literal pattern naturally gives everyone the same values even when resolved separately. Each result remains stable for that mechanic activation and private until explicitly disclosed. Initialize the complete set within the engine's existing work and size budgets, without leaking partial answers from failed initialization.

### Death, disconnects, and role changes

Retain admitted partial progress, personal completion, and expected answers through death, disconnection, and changes to roles, auras, or location. Unfinished physical holds still reset under Q192. Reconnecting within grace can resume only the existing record, subject to current eligibility; it cannot create a fresh answer or reopen a completed record.

Do not shrink the required set when a player dies, loses a role, or exceeds reconnect grace. Expired grace still prevents ordinary gameplay re-entry. Preserving a progress record does not restore that permission or grant revival. If an author wants an encounter consequence for a missing solver, use the accepted lifecycle events, deadlines, failure conditions, or a deliberately smaller completion requirement.

A new mechanic activation creates fresh progress and a new captured set. Cancellation discards unfinished state according to its owner, while an overall completed result retains the existing enclosing-scope completion semantics. No automatic progress migration, late-join enrollment, or per-player reset action is introduced here. [Q195-Q196](pattern-feedback-and-controls.md) accept personal/aggregate event payloads and explicit progress controls.

## Q194: token submission from authored rules

Accepted: add `submit_token` with required `mechanic` and `token`, plus optional typed `player`. The target must be an accessible `match_pattern` occurrence. `token` is a literal ID from that matcher's accepted vocabulary; this initial form does not accept arbitrary text, object paths, or an expression that computes a token.

```yaml
submit_token:
  mechanic: symbol_lock
  token: sun
  player:
    event: player
```

This fragment can appear in a rule whose event actually provides a typed player. A rule can react to a supported NPC defeat, resolved damage event, delivery, aura event, or other registered event and submit a declared token. The action does not invent an event that its source lacks. References such as `{event: attacker}` are valid only where the source schema supplies the required player type; an optional or non-player value retains the receiving action's validation requirements.

### Attribution and admission

In shared mode, omitting `player` means an explicitly unattributed server-authored submission. This permits a sequence driven by world events or timers without inventing a triggering participant. The enclosing rule's source, guards, and invocation limits determine when it happens. Omission does not select a nearby player, a GM, the last interactor, or every participant.

When a player is supplied, require that identity to belong to the target attempt and match the matcher's current player selection. In per-player mode, also require membership in the already captured solver set and an unfinished personal record. `player` is mandatory for per-player progress; an unattributed action cannot advance everybody's records. These requirements use the existing progress concepts under Q193.

This is an authored state change, not a simulated physical use. It can credit a tracked dead or offline participant when the matcher explicitly permits that life, connection, and participation state, because it does not require a live body to touch a target. Ordinary defaults still exclude those states. It never restores the credited player's physical interaction rights, reconnect eligibility, camera access, or revival opportunity.

The action checks current state. Admission of an earlier death or disconnect event does not preserve the player's former action eligibility. An author using that event for credited progress must configure the intended current-state filters. Per-input filters belong to those physical controls; a rule-driven action instead uses the matcher's global selection and its own authored source/guards.

A known player outside the current selection or captured set causes an ordinary rejected submission, with no progress change, mismatch, or technical stop. A completed personal record behaves the same way. A typed field that cannot provide a player, an unknown token, or an unsupported target capability is a contract error, not a mistaken token attributed to someone.

### Physical controls and authored actions

Reuse the same authoritative matching operation and server ordering after admission. Direct physical bindings retain Q192's holds, reach, fresh-input checks, input consumption, and `use_cooldown`. `submit_token` does not perform or bypass a physical interaction; those physical-input settings do not apply to it. Its rule can use ordinary `cooldown`, guards, and invocation limits when authored events need throttling.

A rule's player-specific invocation limits still use its source event's unambiguous triggering player. The action's optional attribution does not retroactively give a timer event a triggering player or change the rule's cooldown bucket. Detailed matcher event payloads must accommodate unattributed submissions without manufacturing a player.

Retrying the same committed action operation cannot submit twice. Distinct authored actions remain distinct, including two actions reacting to the same event. Q192's one-token-per-gesture guarantee still applies to the direct binding; an additional explicit rule action is a separate authored contribution. Do not deduplicate all actions merely because they share an originating event. Authors should use one route for an intended single contribution unless they deliberately want several, and diagnostics retain each operation's provenance.

Several rule-driven inputs can therefore advance, mismatch, or complete the same matcher in the existing stable server order. Matching and resulting notifications commit through the existing event queue, without recursive dispatch inside an unfinished action list. Engine execution limits still bound feedback rules and repeated actions.

### Lifetime and reusable boundaries

Require the matcher to be initialized and active when an action executes. A declared future matcher is not an input buffer: an unavailable required target follows the existing required/recoverable policy rather than starting the child or saving a token for later. An already completed or cancelled occurrence rejects further submissions as a no-op and retains its terminal result. An unknown declaration remains a validation error.

Use the existing permitted mechanic-reference scopes and captured activation identity. A delayed operation cannot retarget another activation just because it has the same authored ID. Direct use of a reusable `match_pattern` definition exposes that matcher's supported capability at its public occurrence. A `layers`, sequence, or other wrapper does not expose a private child matcher through dotted paths or a mutable exported handle. Wrapper input ports require a separate registered interface; `export.events` remains a notification contract.

Permit a matcher to omit `inputs` when registered `submit_token` routes provide its input. A supplied direct `inputs` list must still be nonempty. Validate token coverage across both direct bindings and statically declared actions targeting the matcher. A route with a conditional guard is a possible route, not proof that the encounter will eventually make progress. Do not claim reachability or solve the author's conditions at publication.

Reject nonzero `use_cooldown` on a matcher with no direct inputs, where it would have no effect; use a rule cooldown for an action-only design. Client packets cannot invoke `submit_token` with arbitrary values. Only the validated server-owned rule/action path supplies these contributions. This adds a typed gameplay operation without command execution, chat parsing, arbitrary scripts, or a new administration command.

## Related contracts

These contracts build on [pattern definitions and input bindings](pattern-definitions-and-inputs.md), [shared progress and private clues](selection-and-patterns.md), [player selection and lifecycle state](roles-and-player-state.md), [event ordering and error handling](execution-and-errors.md), and [private reusable interfaces](reusable-mechanic-contracts.md). [Q195-Q196](pattern-feedback-and-controls.md) accept matcher events and progress resets while preserving expected answers. [Q197-Q198](pattern-presentation-and-clues.md) accept token presentation and clue revelation. [Q199-Q200](pattern-state-and-progress-display.md) accept current-state predicates and progress displays. [Q201-Q202](pattern-parameters.md) accept typed pattern parameters.
