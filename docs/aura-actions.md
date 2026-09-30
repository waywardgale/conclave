# Aura actions

Status: Q125-Q126 are accepted. Player and NPC holders, source contributions, player-owned lifetimes, independent stack timers, lifecycle events, and compatible-definition checks are also accepted. These contracts define author-facing operations; no runtime implementation exists. Examples are schema fragments, not shipped encounters.

[Q156-Q158](aura-effects.md) accept owned temporary modifiers and separate native status-effect actions. Conclave aura modifiers follow the ownership rules below; native status-effect actions explicitly hand their result to Minecraft and do not receive automatic source cleanup.

## Q125: applying, refreshing, and removing auras

Accepted: expose `apply_aura`, `refresh_aura`, and `remove_aura`, with a required `aura` reference and exactly one explicit recipient form: `players` for the accepted player selector, `group` for an attempt-owned NPC group, or `target` for a typed event reference to one holder. Reuse existing selector, scope, and event-reference rules. Do not create another targeting language or assume that an omitted recipient means everybody.

```yaml
do:
  - apply_aura:
      aura: charged
      players:
        role: runner
```

An explicitly empty `players: {}` uses the established living, online participant default inside an attempt. Explicit `from` and filters keep their accepted meaning. Resolve recipients once per action. A valid empty selection changes nothing, while an unknown required group or invalid event field follows the existing validation and required-operation error policy. `target: {event: player}` is valid only when that event provides a player; a holder field may target a supported NPC when its declared type permits it.

`apply_aura` creates or reapplies the named aura according to its definition. A stacking aura gains one stack by default; an optional positive `stacks` count requests several. Reject `stacks` on non-stacking definitions. Admit only the stacks that fit the cap, in stable order, and ignore the remainder under Q65. Non-stacking application follows the same-source refresh or explicit ignore policy. Validate the compatible definition and supported target before committing a holder's change.

Keep duration, stacking policy, maximum stacks, death behavior, gameplay effects, and presentation in the referenced aura definition. These actions do not introduce ad hoc duration or effect overrides that would make two applications of one definition mean different things. Authors can create a separately named definition when they need a distinct configured aura. The accepted `scope` on application controls phase, encounter, or explicit player ownership, not the aura's properties. Player scope is valid only for players. Source identities are stable for the originating producer activation, so repeated invocations do not create a fresh non-stacking source every time; Q275 names the typed source, activation and contribution fields.

`refresh_aura` renews existing timed contributions to the duration captured by each contribution's compatible definition. Under [Q159](aura-periodic-and-queries.md#q159-periodic-damage-and-healing), renewing duration does not reset a periodic effect's cadence or grant an immediate pulse. It never creates an absent aura, adds stacks, changes ownership, or extends a lifetime beyond its cleanup owner. An untimed contribution has nothing to renew. `remove_aura` explicitly removes the selected contributions; it does not treat removal as natural expiry. Q126 defines which contributions these two actions select.

Do not offer a generic `set_stacks` in this initial family: replacing a count would leave ambiguous which source owns the new or surviving timers. Applying, refreshing, and removing actual contributions keeps identity and events meaningful. A later capability may add a specific stack transformation if its ownership contract is explicit.

## Q126: deliberate removal and contribution selection

Accepted: distinguish an explicit authored cleanse from automatic cleanup. `remove_aura` defaults to clearing all contributions of that named aura on each selected holder that belong to the current attempt or are explicitly player-owned. `refresh_aura` defaults to renewing the same set of existing timed contributions. The explicit action is the author's request to affect that whole named aura, including another mechanic's contribution; phase cleanup still removes only its own resources.

Allow an optional `contributions` filter with `all`, `attempt`, or `player`, defaulting to `all`. Here `all` means the union of the current attempt's contributions and the selected player's independently owned contributions. `attempt` leaves independent player marks alone, and `player` affects only those independent applications. It never means every active attempt on the server. The editor explains this distinction next to the field.

```yaml
do:
  - remove_aura:
      aura: charged
      players:
        role: runner
      contributions: attempt
```

An ordinary encounter action cannot mutate another active attempt's aura contributions. If a whole-aura operation would otherwise include such a contribution, report an ownership conflict for that holder before changing any of its contributions; do not claim the holder lost the aura after silently skipping a foreign source. An explicit narrow filter may affect only the current attempt's permitted contributions while the foreign contribution keeps the holder-level aura present. Administrative operations retain their separately checked authority. This boundary governs managed state and does not change ordinary Minecraft combat or movement.

For rules reacting to one stack or contribution, allow `contribution: {event: contribution}` instead of the broad filter. Only source events that expose that typed identity can use it. A stacking contribution identifies exactly one stack, so a rule can refresh or consume that stack without selecting all stacks or guessing which expires first. Validate the referenced holder, aura, ownership, and activation; a stale reference never retargets a replacement contribution. Exact event registry field names must consistently expose the documented `contribution` value on the relevant lifecycle events.

Removing an absent aura or refreshing one with no eligible timed contributions is a no-op. A typed contribution that already ended also causes no successful-change event. A reference to an unrelated holder or unauthorized active owner is an error. Changed contributions emit the accepted removal or refresh events in deterministic order; each affected holder gets only the applicable aggregate event. An action cannot bypass Q122 to reapply a changed definition as a refresh.

Explicit removal uses the removal cause, including when it clears the final contribution. It does not emit `expired`, `contribution_expired`, or `stack_expired`. Accepted [Q160](aura-periodic-and-queries.md#q160-events-for-contributions-removed-before-expiry) adds `contribution_removed` and `stack_removed` for the contributions actually removed, with holder-level `removed` only when the aura ends under its aggregate lifecycle rules.
