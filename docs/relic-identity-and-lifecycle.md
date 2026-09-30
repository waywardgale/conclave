# Relic identity, carrying, and return policies

Status: Q178-Q180 are accepted. These contracts settle the managed carry model, creation and runtime identity, and concrete return-policy fields. [Q181-Q183](relic-interaction-and-delivery.md) accept pickup/drop controls, delivery fields, and direct lifecycle actions. [Q184-Q186](relic-events-and-carry-auras.md) accept events, conditions, and carry-owned auras. [Q187-Q188](relic-placement-and-appearance.md) accept world placement and appearance. No implementation exists.

[Q189-Q190](carried-relics-and-animation.md) accept optional carried visuals and explicit animation control.

## Q178: managed relics and carrying capacity

Accepted: represent a relic as an encounter-managed world object with authoritative holder state separate from the player's ordinary inventory. A carried relic has a clear indicator on its holder's HUD. Its world appearance can use supported item/model assets; the exact appearance fields and carried-world rendering follow the selected representation.

Do not reserve or overwrite a hotbar, offhand, armor, or storage slot, move the player's existing items, or create a normal collectible item as the ownership record. A full inventory does not prevent carrying a relic. Inventory sorting, containers, crafting, and native item drops do not transfer or reproduce it. This keeps the relic's accepted lifecycle separate from ordinary rewards and vanilla gamerules. The implementation still needs to reconcile owned world visuals and holder records after interrupted cleanup; this model is not a claim of crash-atomic Minecraft saves.

The player retains ordinary movement, combat, and item use while carrying. Authors can use explicit supported auras and other gameplay capabilities for burdens or advantages. Carrying does not itself grant an aura, a role, new attacks, or a replacement weapon. Specialized relic abilities and hand-replacing equipment are separate capabilities rather than hidden behavior of every pickup.

Add encounter-level `carry_limit`, a positive integer defaulting to one, as the maximum number of distinct relic instances a participant may hold at once. This is independent of the already accepted one-holder-per-relic invariant. The limit belongs to the captured encounter revision and cannot change during the attempt. A different relic definition does not receive a separate allowance; all held instances count. Increasing the configured limit permits multiple carried relics without changing code, within the eventual engine budgets.

At capacity, a new pickup cannot silently discard a currently held relic or displace an ordinary inventory item. Report the unavailable pickup without changing either relic. The server checks capacity and holder ownership together before committing a transfer. Per-item counts, stack merging, and simultaneous possession of the same instance are not part of this representation. Each carried instance has its own identity and HUD entry.

Relic lifetime remains phase- or encounter-owned under the accepted scope rules, never silently player-owned. The owning lifecycle can remove it from a carrier at cleanup. Reconnection receives current authoritative state and cannot restore an earlier carried copy. Carrying several relics applies each one's configured death/disconnect policy once without converting any into an ordinary death drop.

This decision favors encounter-state ownership over full participation in Minecraft's inventory and item-transfer systems. A native inventory-item representation would require a different integration contract for slot operations and transfer paths. [ADR-0020](adr/0020-managed-relic-ownership.md) records that tradeoff.

## Q179: explicit creation and live instance identity

Accepted: keep reusable `relic` definitions under the accepted named-manifest convention, and add one explicit `spawn_relic` action. It requires an instance `id`, a `relic` definition reference, and a bound `location`, following the location name used by NPC spawning.

```yaml
spawn_relic:
  id: north_orb
  relic: raid_tools:void_orb
  location: north_spawn
```

The definition describes the reusable relic configuration. The action creates one live instance named `north_orb` in its owning activation. Another spawn can use the same definition with another ID and location. Omit a `count` option initially so each physical relic has an explicit identity rather than an automatically generated suffix. Authors can use multiple declarations or fresh private mechanic activations for repeated creation.

Loading or publishing the definition does not spawn it. An ordinary rule, including accepted startup rules, invokes the action. Use the existing creation ownership convention: phase ownership by default where applicable, explicit encounter ownership when it must survive phase changes, and private identity inside a reusable occurrence. Preserve the accepted resource lifetime even when a short-lived producing mechanic finishes. Neither publication nor passing a reference changes ownership.

The producer is statically declared by the action so supported consumers can validate references before the world object exists. A consumer that permits pending binding can wait for that producer. A live mutation cannot operate on an uncreated relic just because the declaration is valid. Unknown producer IDs are validation errors; a temporary pending producer is not a delivered relic.

### Runtime references and repeated creation

In runtime-state fields, `relic: north_orb` references that live instance declaration, and the explicit form `{id: north_orb, scope: encounter}` references an encounter-owned instance. In `spawn_relic`, the `relic` field instead takes a content-definition reference, as declared by that action's schema. The editor must identify which reference kind a field accepts. Keep the existing `relic` parameter type as a content-definition reference; a future live-instance parameter must have its own registered type rather than silently changing Q168.

Create each public instance ID once in its owning activation. A second independent creation request under that ID does not replace an available, held, absent, or delivered instance. As with Q93's NPC groups, it is an invalid duplicate producer operation. Retries of the same committed operation cannot create another instance. A fresh phase or private repeat activation has fresh local identities under the existing scope rules.

Drop, return, and permitted respawn keep the logical instance identity. A replacement world representation has a fresh generation so an old pickup, holder update, or return callback cannot act on the replacement. An instance's initial location stays the location captured at creation, even if a later respawn deliberately uses a different permitted location. Neither respawn nor reusing a definition creates a second holder record.

Validate the definition, location, server resource capacity, and required placement before announcing creation. A failed creation leaves no usable partial copy and follows the existing recoverable or technical-error policy. A missing renderer is governed by the eventual required-presentation contract, not permission to invent a different gameplay object. No implicit item reward or native loot is produced by creation or failure cleanup.

Returning or resetting an already created relic requires its explicit lifecycle operation rather than rerunning `spawn_relic`. [Q181-Q183](relic-interaction-and-delivery.md) accept pickup/drop input, delivery bindings, and direct lifecycle actions. [Q184-Q186](relic-events-and-carry-auras.md) accept holder queries, lifecycle events, and carry-owned auras. A live-reference action cannot target another attempt's private instance, and a later activation cannot receive an old captured reference.

## Q180: death, disconnect, and respawn fields

Accepted: put independent `on_holder_death` and `on_holder_disconnect` settings on the relic definition. Each accepts `drop`, `return`, or `despawn`, defaulting independently to `drop`.

| Value | Immediate result |
| --- | --- |
| `drop` | Release the current holder and place the same relic instance at a valid nearby drop position. |
| `return` | Release the holder and return the relic to its initial location immediately, subject to valid placement. |
| `despawn` | Release the holder and remove its present world representation. It remains absent unless an explicit permitted return is scheduled or requested. |

Keep the existing opt-in `respawn` block separate from those immediate responses:

```yaml
on_holder_death: despawn
on_holder_disconnect: drop
respawn:
  causes: [holder_death]
  after: 10s
  at: initial
```

Require a nonempty unique `causes` list and a positive simulation duration in `after`. Initially support `holder_death` and `holder_disconnect` as causes. A listed cause requires its corresponding immediate policy to be `despawn`; reject contradictory combinations that could schedule another copy while the relic is dropped or already returned. Omitting `respawn` schedules nothing.

`at` defaults to `initial`. It also accepts an explicit `{location: recovery_pedestal}` reference bound through the existing arena-location contract. The typed form allows an actual location named `initial` to remain distinguishable from the special initial-position value. This destination does not rewrite the instance's recorded initial location. Validate the destination with the attempt's captured content, and recheck placement when the return becomes due.

### Competing causes and return failure

Process a cause only while the player is the current holder. If death already released the relic, a following disconnect cannot apply its holder policy again, change the cause, or restart the pending timer. Keep at most one pending return for that absent instance and generation. A reconnect does not reclaim a relic picked up by somebody else or reset a pending return's clock.

Only committed disappearance for a listed cause schedules automatic respawn. Successful delivery and phase/attempt cleanup never do. Manual despawn, arbitrary world damage, or other unlisted causes do not secretly match one of these labels. Any additional cause needs a named capability contract. Cleanup cancels pending callbacks before they can restore the object, and interrupted-attempt recovery does not replay them.

Retain the accepted Q28 rule: an invalid drop or a relic leaving its permitted arena returns it to its initial home. This affects the object; the player can keep moving under ordinary Minecraft rules. Crossing the boundary does not otherwise return the player, remove their role, or reset unrelated timers. These returns do not count as delivery or trigger a second automatic respawn. A future explicit boundary-policy override would need its own setting.

Use the established supported placement and error handling for a required home or delayed return. An unusable destination must not create the relic in an invalid position, keep a hidden carried copy, or grant delivery credit. An explicitly handled recoverable placement failure can retry within its bounds; an unhandled required failure uses Q69's technical stop and cleanup. Exact ground-placement search and physical behavior remain part of the selected representation's implementation.

[Q181-Q183](relic-interaction-and-delivery.md) accept pickup/drop input, concrete delivery destinations, and direct lifecycle actions. Resets keep logical identity, while a completed delivery mechanic keeps its recorded result.
