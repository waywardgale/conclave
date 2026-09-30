# Final presentation from ending scopes

Status: Q245 is accepted. It adds a bounded final presentation window and the encounter-attempt gameplay result schema to Q243 phase events and Q244 ordinary reaction order, without changing required-work error handling. No implementation exists.

## Q245: final presentation after a committed gameplay result

Accepted: allow an encounter, phase, or `layers` body to react to its own committed `completed` or `failed` event through the existing `on.source: self` form. Give those rules one bounded opportunity to issue immediate, best-effort presentation before the scope's presentation context is released. Cleanup always proceeds, and no final rule can revise the result or frozen phase route.

```yaml
rules:
  - id: victory_sound
    on:
      source: self
      event: completed
    do:
      - play_sound:
          sound: raid_tools:victory
          mode: direct
          audience: {from: participants}
```

In an encounter body this fragment reacts to that attempt's success. In a phase or layers body, `self` means that body's own result instead. The sound is an illustrative authored resource, not bundled encounter content. Use the ordinary rule ID, guard, event-value, audience, and source-location conventions; add no second `on_end` language or general cleanup script.

### Allowed actions

Initially permit terminal-compatible, finite forms of `speak`, `play_sound`, `play_animation`, and `show_effect`, plus `stop_presentation`. Text-only dialogue uses `speak`. Each capability must explicitly support this context and validate its concrete configuration, including referenced dialogue and asset definitions. A new action or integration is unavailable here until its terminal presentation behavior is declared and verified.

Require best-effort presentation with immediate dispatch eligibility. Reject `required: true`, loops, queued dialogue, delayed starts, retries, and operations that need a pending gameplay continuation. A finite recording or subtitle may take time to finish; that media lifetime is separate from waiting to start the action. Ordinary dialogue priority and per-recipient availability still apply, so a busy recipient can skip a line instead of queueing it past cleanup.

Do not allow damage, healing, revival, aura/native-effect changes, counter/role mutations, spawning, relic movement, world edits, timer creation, mechanic activation, or reward delivery in this final window. Those operations have gameplay or persistence consequences. Perform necessary gameplay work while its owning gameplay scope is still active, before it can be declared complete. [Q248](completion-rewards-and-test-policy.md#q248-declarative-rewards-for-encounter-success) accepts separate encounter-level reward declarations; Q250-Q251 define their recipient and delivery policy, with generation, durable commitment, and retention accepted in Q252-Q255 and native-save integration still unimplemented.

This restriction belongs to rules observing their own terminal event. An encounter that remains active after a phase transition can still use its ordinary named-phase subscription and normal action capabilities. For example, an encounter-owned `source: {phase: ritual}` rule can change an encounter counter after a ritual that routes to another phase. Q243 still prevents that change from altering the route already chosen.

Validate the final rule's action profile before publication. Do not silently drop an authored gameplay action or downgrade required presentation into optional output. The editor must explain why an action belongs in an earlier live rule, objective, or surviving encounter reaction instead of a final presentation rule.

### Encounter-attempt result events

Expose `completed` and `failed` on encounter `source: self`, alongside the accepted `started` event. They report a committed ordinary gameplay result once for that attempt. Both terminal events provide nonnegative simulation-time `elapsed`, including supported startup waits and phase transitions. Under Q254, success measures actual activation to the frozen final gameplay decision and excludes the subsequent durable-storage wait; failure measures activation to ordinary failure commitment. They provide no implicit player, killer, or contributor, so `per_player` and `on.player` are unavailable.

Encounter `failed` also provides registered `reason`:

| Reason | Meaning |
| --- | --- |
| `phase_failed` | A phase's committed failure selected its `wipe` route and ended the attempt. |
| `party_defeated` | The accepted party-defeat policy ended the attempt after eligible survival and recovery opportunities were exhausted. |

These codes describe attempt outcomes. A phase's deadline or failed objective remains its own Q243 reason; it is not silently copied into the attempt reason. A phase failure that routes to recovery produces no attempt failure. Reconnect expiry or a grave window ending may make party defeat decisive, but is not independently another attempt result or player death.

When several ordinary failure requests establish the same attempt failure, retain the first applicable cause in stable admitted order and report additional causes in diagnostics. Preserve the encounter's existing success-versus-failure precedence. The event does not add attempt-level `deadline`, `fail_when`, phase-jump, force-success, or force-failure capabilities.

Phase self-terminal rules use Q243's `elapsed`, frozen `route`, optional `next_phase`, and failure reasons. Layers self-terminal rules use their existing Q224 mechanic result schema. There is no extra copy of the result, new triggering player, or private-child payload simply because the listener is `self`.

### The final window and listener lifetime

Commit an ordinary gameplay result before admitting its final presentation. Mark that scope as ending and stop its ordinary subscriptions, new gameplay input, timers, and pending invocations according to their lifetimes. Preserve only the declared self-terminal subscriptions and the read context needed for their guards, permitted targets, and audiences during this bounded pass. Do not revive a previously ended scope.

Reactions to one event retain Q244's scope-activation order, local YAML rule order, live guards, immutable event payloads, and invocation reservations. A self-terminal rule can inspect state still retained for this pass, but its permitted actions cannot mutate gameplay. A normal enclosing listener still runs before its younger child when that enclosing activation remains active.

If a phase ends the whole attempt, the encounter's ordinary named-phase listeners are ending too. They do not receive a new gameplay-reaction opportunity through the final window. Use the phase's own final presentation for that phase, or encounter `self.completed`/`self.failed` for the attempt result. This explicitly resolves Q243's previously deferred final-phase delivery case. An ordinary pending reaction already belonging to an ending scope cannot resume after closure.

Where a phase actually committed an ordinary result that then ends the attempt, report that phase result and process its eligible final presentation before the attempt's terminal presentation. This order concerns two distinct causal events; it does not reverse Q244's listener order within one event. Party defeat, an administrative stop, or a technical interruption that merely cancels an unfinished phase does not invent a phase result or phase final rule.

Once the final pass finishes, discard its remaining listener state and continue normal teardown. Required recovery, resource release, and cleanup do not depend on a final rule being present, its guard passing, or a client receiving output. The pass does not add another simulation tick, delay the next phase, keep an NPC alive for an animation, or retain gameplay callbacks until media playback ends.

### Commitment and errors

Unhandled required-work failure before attempt-result commitment keeps Q69's technical-error result. Q254 separately distinguishes an uncertain submitted completion write from confirmed failure: a storage timeout or read error cannot establish technical interruption while that original write may have committed. Reconcile it under Q254 while bounded cleanup proceeds. Do not convert a previously committed phase success into attempt success after a separate required integration error. This contract allows only documented cosmetic operations after the result commits, so it does not create a post-success exception for required gameplay actions or required presentation.

Unavailable optional output follows its supported cosmetic diagnostic or fallback path. The attempt result stays fixed and cleanup continues. Bound final presentation separately and reserve cleanup capacity; if optional final-dispatch capacity is unavailable, omit the undispatched output with a diagnostic. Never treat that best-effort budget as a catch-all that hides gameplay contract violations or grants unlimited work. The numeric ceiling remains part of the common measured runtime budget.

A failure reported after ordinary gameplay was already committed cannot roll back that gameplay. Diagnostic and cleanup-repair obligations retain their own authority; this is not a claim that arbitrary engine bugs, external mods, or native world saves are transactional. The final window introduces no retry, compensation, inventory rollback, or durable gameplay side effect.

### Finishing media and interrupted attempts

Permit finite presentation actually dispatched by this final window to finish after ordinary gameplay success or failure, retaining only the resources and recipient permissions needed for playback. This explicitly permits a final failure message after a gameplay wipe. Apply existing audience privacy, interruption, and finite-duration bounds. It holds no gameplay continuation and cannot decide any outcome through a client completion message.

Target-bound animation and effects still end when their supported target disappears. Cleanup does not manufacture a defeated-speaker snapshot or retain a world entity for a final cue. Authors needing a cue to survive entity cleanup must use an already-supported suitable origin or presentation form. Queued future cues and loops receive no new lifetime, and obsolete lines still stop when a new attempt starts for the same participant under Q135.

Administrative stop/restart, technical interruption, and server shutdown/crash do not emit these gameplay `completed`/`failed` events and do not run final presentation rules. They use the existing built-in interruption message, diagnostics, cleanup, and recovery. Do not add a generic YAML `cancelled`, `errored`, or `finally` callback in this first contract.

Reconnect, asset reload, later subscription, and restart recovery never replay the final pass. A crash can prevent a cosmetic cue from being seen; recovery does not replay it to simulate exactly-once delivery. Accepted native NPC death loot remains independent of attempt completion, and this interface supplies no reward grant or receipt.

## Related contracts

This contract extends [Q243-Q244 phase events and rule order](phase-events-and-rule-order.md), [Q167 startup and self](mechanic-start-and-parameters.md#q167-a-started-event-before-child-mechanics-begin), and [Q224 mechanic results](event-players-and-mechanic-results.md#q224-common-mechanic-completion-and-failure-fields). It preserves [Q69 required-work errors](execution-and-errors.md#q69-failure-categories-and-bounded-work), [Q137 presentation requirements](presentation-controls-and-models.md#q137-selected-recipients-without-the-required-assets), [Q135 media lifetime](dialogue-formatting-and-delivery.md#q135-overlapping-dialogue-queues-and-lifetime), and [Q120 recovery without replay](cleanup-and-restart.md#q120-interrupted-attempts-after-a-server-restart).

The final window, restricted action profile, attempt result schema, and documented cosmetic failures form one accepted contract. [Q246-Q247](simulation-stages-and-outcomes.md) accept the simulation-stage order and outcome settlement. Q248 separately accepts completion-reward declarations; Q250-Q251 define qualification and private delivery, with generation, durable commitment, and retention accepted in Q252-Q255 and native-save integration still unimplemented.

[Q248-Q249](completion-rewards-and-test-policy.md) accept encounter-level completion rewards and a separate Test payout policy. Test suppresses supported reward delivery by default and offers an operator-only real-payout option at launch. Terminal presentation remains presentation-only; no general grant action is added.

[Q254](durable-completion-and-reward-storage.md#q254-save-success-and-its-rewards-before-announcing-victory) accepts a separate bounded storage wait before attempt success and final presentation, with successful attempt `elapsed` frozen at the gameplay decision. The final dispatch pass itself adds no simulation tick; the preceding storage operation may finish later. Timeout/interruption discards undispatched authored final presentation while cleanup proceeds and the original write is reconciled. Phase/mechanic elapsed and the presentation-only action restriction remain unchanged.
