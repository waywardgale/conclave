# Relic pickup, delivery, and explicit controls

Status: Q181-Q183 are accepted. These contracts define player input, the `deliver` mechanic, and direct lifecycle actions, building on Q178-Q180's managed instances and return policies. [Q187-Q188](relic-placement-and-appearance.md) accept appearance and physical placement. No implementation exists.

[Q184-Q186](relic-events-and-carry-auras.md) accept lifecycle events, current-state predicates, and optional carry-owned aura applications.

[Q187-Q188](relic-placement-and-appearance.md) accept world placement, interaction dimensions, and appearance, including holder feedback.

## Q181: deliberate pickup and selected-relic dropping

Accepted: pick up an available world relic through a fresh press of Minecraft's configured Use control while aiming at it. Do not collect it merely by touching it. Successful pickup has one holder and consumes that admitted gesture, including both-hand attempts, so the held inventory item cannot also act through the relic. Holding the control cannot pick up a replacement or immediately deliver the newly acquired object.

Allow a `pickup` block on `spawn_relic` with optional `players`, `hold`, `reach`, and `interrupt_on_damage`. These describe this placed instance's input and eligibility; the reusable definition retains appearance and holder policies. This placement keeps role and area references in the caller's scope instead of making a shared definition implicitly search for its caller's roles.

```yaml
spawn_relic:
  id: north_orb
  relic: raid_tools:void_orb
  location: north_spawn
  pickup:
    players:
      role: runner
    hold: 1s
```

Omission permits ordinary living, online, eligible participants in that attempt and instant pickup. `hold` defaults to `0s`; positive holds use simulation time and reset on interruption. `reach` can narrow native entity-interaction reach, and line of sight is required. `interrupt_on_damage` defaults to false, with true following Q176's positive-damage rule. There is no repeated-use count, distinct-player setting, or use cooldown on a single pickup.

Apply the accepted fresh-input and target-validation contract to the managed relic's supported world representation. Revalidate capacity, current holder, current availability generation, participant eligibility, and physical reach before commitment. Several players can attempt a hold; the first valid commitment wins in the accepted server operation order. Losing that race is an ordinary rejected pickup, not a technical error or permission to take the winner's relic. No other hold retains a claim on it.

Pickup filters remain live while a hold is underway. After pickup, losing the role or aura that qualified the player does not automatically drop the relic. Authors can react explicitly through their rules. A query's captured references must remain valid for the relic's lifetime; reject a placement that binds a longer-lived relic's pickup policy to shorter-lived private state. Undefined references and unsupported fields are validation errors.

### Voluntary drop and selection

Provide a dedicated, rebindable Drop relic action and an accessible HUD drop control. Preserve Minecraft's ordinary item-drop control. With one relic held, it is the selected relic. With several, the HUD lets the player explicitly select one; the action never drops all relics or silently chooses a different instance. Detailed key defaults and the selector layout belong to the client controls specification.

Keep an existing selection when another relic is picked up. If the selected relic becomes unavailable, clear that selection; select automatically only when exactly one carried relic remains. The HUD must show which relic is selected. Requests always identify the specific current instance, and stale selections cannot target a replacement or a relic now held by somebody else.

Add `allow_drop` to the relic definition, defaulting to true, for voluntary player dropping. False disables that player action without changing death, disconnect, invalid-drop, boundary-return, cleanup, or explicit authored/administrative lifecycle operations. A valid drop releases the holder and places the relic at a permitted nearby position, using Q180's home fallback if no drop is valid.

The ordinary first form passes a relic by dropping it for another eligible player to pick up. It does not introduce throwing, remote giving, or an implicit handoff to a nearby person. Ground placement and physical motion need their own supported contract. Dropping never awards delivery credit and does not produce a normal inventory item.

## Q182: explicit delivery destinations and triggers

Accepted: a `deliver` occurrence names one live `relic` instance and one typed `destination`. Add `trigger`, defaulting to `interact`, with explicit `enter` for automatic area-entry delivery.

```yaml
- id: deliver_orb
  type: deliver
  relic: north_orb
  destination:
    block: altar
  trigger: interact
```

`relic` is a runtime instance reference under Q179, not its reusable definition ID. An encounter-owned instance uses the existing explicit scope form. A declared future producer may remain pending until created, then the mechanic binds to that actual logical instance. It cannot retarget a new logical instance just because a later activation reuses its name.

### Interaction delivery

With `trigger: interact`, the destination contains exactly one `block` location or `group` reference using Q174's targeting semantics. A group permits the actual living member the carrier uses, including permitted reinforcements to that bound group. No nearby NPC is inferred from a group name. Use separate mechanics when distinct delivery destinations need separate outcomes.

Permit optional `players` to filter eligible carriers, plus `hold`, `reach`, `interrupt_on_damage`, and `consume_interaction` with their accepted interaction meanings. Defaults are instant, ordinary reach, no damage interruption, and native pass-through. One completed use delivers this one relic; `uses`, `distinct_players`, and `use_cooldown` do not belong on `deliver`.

The player must be the current living, online, eligible holder. With several relics held, interaction delivery applies to their explicitly selected relic. Do not silently deliver a different carried instance because it happens to fit the target. Switching selection interrupts an unfinished delivery hold. Admission and commitment both validate the relic's availability generation and holder, along with the actual destination and physical eligibility.

The destination is not inferred from the relic's initial or return location. A blocked, destroyed, dead, pending, or otherwise unusable interaction target cannot accept delivery. Default native pass-through may still open an ordinary interface; as under Q177, taking input focus interrupts a hold. Authors can explicitly consume the gesture when using a native object as a custom altar.

### Automatic entry delivery

```yaml
- id: deliver_orb
  type: deliver
  relic: north_orb
  destination:
    area: altar
  trigger: enter
```

`enter` requires an `area` destination and uses the accepted geometric `entered` transition under Q59. The participant must hold this relic and satisfy the delivery eligibility when the entry is observed, and still be its eligible holder when delivery commits. It does not require that relic to be the HUD selection. Separate qualifying relic instances can each have their own explicitly authored entry-delivery mechanic.

Do not synthesize entry when the mechanic activates with the holder already inside, when someone picks up a relic inside the area, or when a filter becomes true without movement. They must actually leave and re-enter. Teleports use the accepted departure/destination membership comparison; they do not traverse intermediate areas. This is an entry trigger, not a continuously evaluated inside condition.

`players` can narrow the eligible carriers in either mode. Reject interaction-only fields such as hold, reach, damage interruption, or input consumption on `enter`; it has no use gesture to configure. Reject an area destination with the default interaction trigger, or a block/group destination with `enter`, rather than guessing what the author meant.

### Consumption, competing requests, and completion

Commit a delivery by releasing the holder, removing the carried representation and HUD entry, and recording the instance as delivered. This is one operation and frees one place under `carry_limit`. It does not create a dropped copy, implicitly respawn the relic, or replay vanilla death/item consequences.

Only one competing delivery request can commit for the current available cycle of that relic. Use the accepted stable server order; once committed, later requests cannot consume it again or grant another delivery completion. An individual gesture can therefore deliver its selected instance once, even if several active delivery mechanics matched that input. Other candidates remain unfinished until cancelled or until a later explicit reset makes another valid delivery possible. Authors can combine alternative destinations with `parallel` using `completion: any`.

The mechanic that commits delivery emits `completed` once with the actual carrier as typed `player`, supporting that player's invocation limits. Its completion remains latched through later relic resets. A newly activated delivery mechanic waits for a new delivery through that mechanic; an old delivered record does not automatically complete it. A pending mechanic stays bound to the same logical instance through a permitted reset, but old held-input requests cannot act on the replacement generation.

Scope termination cancels unfinished delivery holds and pending observation. Pickup and delivery are distinct gestures, and cleanup or a technical error cannot manufacture delivery success. [Q184](relic-events-and-carry-auras.md#q184-relic-lifecycle-events) defines the accepted relic-level lifecycle events.

## Q183: direct relic lifecycle actions

Accepted: add `drop_relic`, `reset_relic`, and `despawn_relic`. Each requires one explicit runtime `relic` reference. Use the existing local or encounter-scoped form; do not infer an object from the nearest player, the caller's HUD selection, or a definition shared by several instances.

```yaml
reset_relic:
  relic: north_orb
```

| Action | Effect on an already-created instance |
| --- | --- |
| `drop_relic` | Release its current holder at a valid nearby drop position, with the accepted home fallback. A relic without a holder is a no-op. |
| `reset_relic` | Cancel its pending return, release any holder, clear current delivered state, and place it at its captured initial location with a fresh availability generation. |
| `despawn_relic` | Release any holder, remove the present representation, and cancel a pending return. Keep the logical instance record without automatically scheduling respawn. |

These are explicit authored state changes. `allow_drop: false` limits voluntary player input, not these actions, configured holder policies, or authorized GM recovery. An authored action remains limited to its permitted attempt and scope; privileged administrative recovery keeps its separate target and audit requirements.

`reset_relic` intentionally invalidates old pickup/delivery holds even when the object was already at home. Each new authored reset begins a fresh availability generation, while an idempotent retry of the same committed operation does not do it twice. Its initial location and logical instance ID do not change. Historical diagnostic records and already-completed mechanics retain their outcomes; clearing current delivered state cannot undo a previous phase's success.

`despawn_relic` on an absent instance still cancels a pending return. With no representation, holder, or pending return, it is a no-op. A delivered instance stays delivered until explicitly reset; manual despawn does not erase that state. A manual despawn is not one of Q180's automatic respawn causes. A drop request on an instance already delivered or absent has no holder and therefore does nothing.

Validate required placement before changing a holder or replacing the instance. A recoverable preparation failure leaves existing state intact. Unexpected failures during world mutation retain Q69's technical stop and owned cleanup rather than a promise to roll back arbitrary mod callbacks. Resolve state-changing operations in the accepted server order and invalidate superseded timers and inputs at commitment.

A declared but not-yet-created relic is not a live instance for these actions. Follow the documented required/recoverable action-error contract rather than treating a misspelled or pending target as a harmless empty selection. Only `spawn_relic` performs initial creation; these actions never create a second logical instance or substitute one from another activation.
