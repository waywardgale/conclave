# Periodic auras, removal events, and state queries

Status: Q159-Q161 are accepted. Aura contributions, lifecycle events, retained lifetimes, modifiers, and native status-effect actions are accepted. These contracts extend the aura vocabulary; no runtime implementation exists. Examples are fragments inside their stated definitions or conditions.

## Q159: periodic damage and healing

Accepted: allow an optional `periodic` list in an aura definition. Each entry has a positive `every` interval and exactly one `damage` or `heal` operation. The aura holder is the implicit recipient. Reuse the accepted numeric or percentage `amount` forms and supported damage `type`, defaulting to `conclave:physical`. Do not accept an arbitrary action list, recipient selector, script, or world-rule callback inside this field.

```yaml
periodic:
  - every: 1s
    damage:
      amount: 4
    per_stack: true
  - every: 2s
    heal:
      amount:
        percent: 5%
        of: max_health
```

This fragment demonstrates both operations; it is not a proposed bundled aura. Each entry runs once per holder at its own cadence. Independent contributions of the same compatible aura do not create additional schedules. `per_stack` defaults to false and is valid only for a stacking aura. When true, multiply that entry's calculated point amount by the current stack count once. A percentage is the per-stack authored amount and keeps Q151's positive, at-most-100% operand bound; the resulting combined request can exceed that percentage before normal defenses or healing clamping.

Read the holder's amount basis and stack count at each pulse. Do not freeze them when the aura is applied. Earlier entries can change the state seen by later entries. Normal native defenses, hurt cooldowns, health floors, death, healing limits, and the accepted aura combat multipliers apply. A rapid periodic interval does not bypass Minecraft's damage cooldown. Healing cannot revive a dead holder.

Periodic effects initially carry no attacker or healer attribution. Aura contribution ownership records who owns a lifecycle; it is not proof of a damaging player or NPC. Do not guess an attacker from whichever contribution is oldest or retain an ended NPC group to credit future damage. This version accepts no periodic `source` or `origin` override. An attributed attack can use the ordinary explicit damage action from a valid live rule. A future attributed periodic capability would need a separate contribution and expiry contract.

### Cadence and expiration

Use simulation time and Q87's duration quantization. The first pulse occurs after one full interval. Refreshing a contribution's duration, adding another compatible source or stack, or removing one while others remain does not restart the schedule or cause an immediate pulse. Removing the holder's entire aura cancels its schedules; a later application creates new schedules.

Run a pulse due exactly at a contribution's natural expiry before that contribution expires. A continuously eligible holder with one 10-second application and a one-second interval therefore receives ten pulses, at seconds one through ten. A stack due to expire on that pulse still contributes, then expires. Explicit removal, death, or owning-scope cleanup that has already occurred cancels the affected future work. If a pulse itself kills the holder, resolve normal death and aura death policy before any next entry; do not invent a second expiry or continue healing a dead target.

Within one aura, process simultaneously due entries in declaration order, then reconcile its scheduled natural expirations. Queue resulting combat and aura events through the accepted event system rather than recursively running handlers inside the pulse. A callback into an ended activation remains invalid. [Q246](simulation-stages-and-outcomes.md#q246-ordinary-work-before-timed-work) accepts the tick stages and stable ordering of independent due aura operations, preserving this pulse-versus-natural-expiry relationship. Exact native hooks remain implementation verification work.

### Dead, offline, and retained holders

Run damage and healing only while the holder is alive and available to the supported simulation. For retained auras, duration and cadence continue during death or disconnection on the running server, but skipped pulses are discarded. Never accumulate damage or healing to replay on revival or login. Resume at the first scheduled deadline strictly after the holder becomes available, without a free immediate pulse or a reset of the remaining aura duration.

Keep player-owned periodic state with the accepted durable aura record. Server downtime pauses its simulation clock. Reconnection and restoration do not emit application events or replay a previously performed pulse. Store sufficient cadence state to continue its remaining interval, subject to the separately required crash-reconciliation contract with Minecraft saves. Do not claim exactly-once native health changes across an unclean crash before that integration is established.

Required NPC simulation loss still follows the existing technical-error policy. It is not an option to unload a required NPC and silently skip gameplay. Player-owned effects do not retain ended attempts, arena chunk claims, or callbacks. Their supported external dependencies follow Q148's retained-consumer reload checks. Bound periodic work under the existing lifecycle and server limits; do not turn one persistent aura into a general scheduler.

## Q160: events for contributions removed before expiry

Accepted: add `contribution_removed` and `stack_removed` alongside the accepted natural-expiry events. They report actual removals caused by explicit cleansing, holder death, owning-scope cleanup, or exceptional recovery. Natural timeout keeps `contribution_expired` and `stack_expired`; it does not also emit the new removal events.

For each removed contribution, queue `contribution_removed`, followed by `stack_removed` when it was a stack. After resolving that aura's contribution changes for the update, emit holder-level `removed` only if the final effective end was a removal under Q118. Surviving contributions keep the aura present and prevent a holder-level terminal event. Remove only the contributions permitted by the action or automatic cleanup's ownership contract.

Use the existing typed holder, aura, contribution, source, activation, and reason information. Include previous and resulting aggregate stack counts where relevant. A removal request that finds nothing emits nothing. Refreshing does not remove and recreate a contribution. When natural expiry and explicit removal compete, the committed lifecycle change decides the reason once; a stale second operation cannot report the same contribution ending again.

Removed subscriptions cannot receive events after their owning activation ends, including on restart recovery. Surviving authorized subscribers can observe valid removals, but notification does not permit expired scopes to run punishment or rewards. Private contribution information retains its existing visibility rules. These additions complete lifecycle notifications without introducing a new event-delivery system.

## Q161: aura stack and remaining-time conditions

Accepted: retain concise `has_aura: charged` for presence and add a structured form for optional stack and time checks. Reuse the accepted comparators. Fields combine with `and`; use ordinary `or` or `not` nodes for alternatives and absence.

```yaml
has_aura:
  aura: charged
  stacks:
    at_least: 3
  remaining:
    at_most: 5s
```

The fragment uses the selected member as its implicit subject, as in `any` or `all`. Outside such a context, permit one explicit typed `target` or unambiguous single-NPC `group`, following Q150's reference rules. Reject ambiguous subjects and arbitrary property paths. Use these conditions in existing `where` filters without creating a separate selector language.

`stacks` reads the current aggregate count across compatible contributions. It requires a stacking aura and exactly one integer comparator. `remaining` reads the same countdown the aura HUD represents: the next stack expiration for a stacking aura, or the final contribution expiration for a non-stacking aura. Compare durations with units, allowing zero as a threshold. This is a current-state query, not the original authored duration or a promise that cleanup cannot remove the aura sooner.

An absent aura makes the entire predicate false, including `stacks: {equals: 0}`. Use `not: {has_aura: charged}` for absence. An aura with no finite displayed countdown makes a `remaining` comparison false rather than treating infinity as an ordinary duration. Optional `timed: true` or `timed: false` tests whether the present aura has that finite countdown. Contradictory `timed: false` together with `remaining` is a validation error.

Read one coherent current state for the condition. The query can observe a retained aura on a dead or offline holder when an explicit selection includes that holder; default gameplay selection remains living online participants. Read the holder's retained definition under Q122 rather than interpreting an old aura through newly published settings. A current query is distinct from an immutable event payload describing an earlier stack or timer change. Never send private aura state to an unauthorized client merely because a server-side condition inspected it.
