# Combat events and runtime controls

Status: Q152-Q155 are accepted. Multiple sequential phases, concurrent mechanics within each phase, native health, damage types, explicit vulnerability, health conditions, and percentage amounts are accepted. These contracts describe accepted authoring capabilities, not implemented hooks.

## Q152: optional NPC health floors

Accepted: add optional NPC `health_floor`, disabled when omitted. Accept a positive health-point amount or an explicit percentage of current maximum health. A floor limits supported damage-driven health loss so a large hit cannot kill an NPC or pass an authored health threshold before the encounter can react. It does not automatically select a phase or make all NPCs immortal.

```yaml
health_floor: 50%
```

Expose `set_health_floor` with `group` and `value`, plus `clear_health_floor` with `group`. Resolve current living group members once and validate support before mutation. Future reinforcements use their own definitions. The setting belongs to the NPC's lifetime and persists across phases for an encounter-owned NPC; changing phases does not silently release it. Repeat assignment of the same setting is a no-op, and clearing an absent floor is harmless. A dead NPC remains dead.

Apply the cap to health-directed damage after ordinary mitigation and absorption, before native combat tracking and health subtraction. Preserve source attribution, actual absorption expenditure, and the capped health-damage amount. Existing hurt feedback, cooldowns, item wear, and other native consequences can still occur. A floor is not the same as `vulnerable: false`, which rejects supported gameplay damage earlier. Authorized administrative kill/removal and cleanup keep Q149's explicit handling.

Emit `health_floor_reached` once per installed floor setting when its effective threshold is reached or the first hit is capped there. Provide the affected NPC and resolved threshold through typed event fields. Installing a floor at or above the NPC's existing health satisfies it immediately and queues that event without healing. Healing above a reached floor does not rearm the notification; clear and reinstall the floor, or install a different value, to create a new setting. Never manufacture a death or defeat at the floor. Existing health conditions can also inspect the resulting living NPC.

Resolve a percentage floor against current native maximum health. Use the same documented conversion to a representable native health threshold for percentage health comparisons and floors so the exact floor can satisfy the matching `at_most` check. This is numeric representation, not an arbitrary comparison tolerance. Validate positive finite thresholds and reject authored point floors above the configured maximum. If an external maximum-health change would put a retained point floor above that maximum, cap its effective threshold at the current maximum; this does not heal the NPC. A Conclave-authored conflicting configuration is rejected before mutation.

The floor never raises current health. If a supported external health change leaves the NPC alive below the threshold, later damage cannot lower it further, and the threshold notification becomes satisfied once. Direct health writes, native maximum-health clamping, removal, and unsupported custom damage methods are not intercepted simply by adding a damage hook. Require an adapter to state its support; detect incompatible required behavior through the existing error contract instead of claiming universal interception. These native integration limits are recorded in [NPC combat research](npc-combat-research.md).

## Q153: damage and healing events

Accepted: expose `damaged` and `healed` for named NPC groups and explicitly selected players through the existing rule-source forms. One event identifies the actual affected NPC or player. A group's event describes one member's operation, not a sum over the group.

```yaml
on:
  source:
    group: boss
  event: damaged
```

```yaml
on:
  source:
    players:
      role: runner
  event: healed
```

`damaged` means a resolved supported damage operation consumed positive native health or absorption. Report `health_damage` as health actually removed by that operation before death protection, excluding overkill; report `absorption_damage` separately. `amount` is their sum. Also expose `health_before`, `health_after`, the damage `type`, the affected typed target, and optional attributable attacker and direct-source identities when available. `health_after` is the resolved value after native rescue or death handling, so a totem can make net health change differ from `health_damage`.

No positive health or absorption consumption means no `damaged` event. A blocked, immune, rejected, or fully floor-prevented hit is not silently reported as positive damage. Absorption-only damage still qualifies and can be distinguished through its fields. [Q228](individual-npc-controls-and-hit-reactions.md#q228-actual-blocks-and-conclave-damage-prevention) accepts separate notifications for actual item blocking and Conclave damage prevention. Attack attempts, other rejection reasons, and arbitrary health-value changes remain outside those events.

`healed` means a supported healing operation restored positive health. Its `amount` is the actual increase after the maximum-health clamp, with before/after health and target identity. Do not label initial spawn health, respawn, revival, a totem's direct rescue assignment, or a maximum-health clamp as healing merely because a health value changed. Do not invent a healer for native regeneration or another source without reliable attribution. Supported native calls and Conclave actions share one event adapter so one operation cannot be counted twice.

Match player subscription eligibility against the player state captured before that combat operation, so its lethal result does not erase the recipient from the event source. The accepted default player collection remains living, online participants unless the author selects otherwise. Capture the selected subscription match once; later guards follow the normal rule contract and can observe newer state. Typed target identities and event amounts remain snapshots and never transfer to a replacement NPC or player lifecycle.

Queue notifications only after the observed operation resolves. Within one operation, queue its damage outcome before its resulting threshold and terminal death/defeat notifications, without publishing a half-finished native state. Preserve each event's causal order and identity through the accepted server event queue. Reactions cannot retroactively cancel or change the hit that already resolved, and they cannot revive a target through ordinary `heal`. Technical cleanup does not manufacture these gameplay events.

Fabric's existing callbacks do not directly supply these final amounts, and lethal damage follows a different callback path. Additional instrumentation and supported-entity verification are required. Damage caused by a rule is still observable; authors use existing guards, `once`, and cooldowns to prevent unintended feedback. Engine execution limits remain the final bound on recursive rule chains. [Q164](event-conditions.md) accepts exact event-field comparisons and typed attacker predicates; [Q223](event-players-and-mechanic-results.md#q223-an-explicit-player-field-for-a-subscription) accepts explicit player identity selection. [Q228](individual-npc-controls-and-hit-reactions.md#q228-actual-blocks-and-conclave-damage-prevention) accepts separate actual-block and Conclave-protection events.

## Q154: authored NPC stat changes

Accepted: add `set_stats` for a named group's current living NPCs, using the accepted `stats` fields and units. Change only supplied fields. This supports an authored enrage or weakening without replacing the NPC or changing the definition used by other attempts.

```yaml
set_stats:
  group: boss
  stats:
    speed: 140%
    melee_damage: 12
```

Values replace supported base stats; they are not increments multiplied onto the current result. Repeating the same values does not accumulate bonuses. `speed` remains relative to the NPC type's supported base movement value under Q141. Equipment, auras, native effects, and other independent modifiers retain their own behavior. Normal native bounds and the accepted authored health ceiling apply.

Changing `health` in this action changes maximum health. Increasing it does not heal; decreasing it clamps current health to the new maximum when needed. Keep healing explicit through `heal`, including the accepted missing-health form. Do not preserve the old health percentage unless a later separately named operation explicitly offers that behavior. Validate the complete requested change against active supported constraints, including any configured health floor, before applying it.

Expose `reset_stats` with the same group target and an explicit nonempty `stats` list of fields to restore. Restore those fields' initial configured base values captured for each NPC at its spawn. This does not erase independent modifiers, restore an old inventory, refill health, or change unspecified stats.

```yaml
reset_stats:
  group: boss
  stats: [speed, melee_damage]
```

Capture living recipients once and validate all selected adapters and requested values before mutation. Expected invalid configuration must not leave half of a group updated. Unexpected runtime failures retain Q69's technical-error handling, without claiming transactional rollback of arbitrary other mods' callbacks. Empty existing living selections are no-ops; unknown required groups are errors. Future reinforcements still use their own definitions.

These mutations persist with each NPC, including across phases for encounter-owned NPCs, until explicitly changed or reset. A new attempt uses its selected revision and fresh NPCs. YAML publication still does not mutate an active attempt. [Q156-Q157](aura-effects.md) accept temporary aura-owned modifiers; this base-stat action should not pretend to stack and later undo independently owned changes. Size, equipment, appearance, AI, and vulnerability retain their own controls.

## Q155: damage origin separate from attribution

Accepted: add optional `origin` to `damage`, using an explicit named-location reference. Keep `source` as the attributable NPC or player. A boss can therefore receive attribution for an attack that originates at a floor trap or another Location anchor.

```yaml
damage:
  players:
    area: blast
  amount: 8
  source: {group: boss}
  origin: {location: blast_center}
```

Resolve the location from the pinned attempt context and snapshot its position once for the action. It supplies the incoming direction for supported native shield and directional feedback behavior. If `origin` is omitted, use the attributable source's valid native position when available; an unattributed operation has no invented origin. A private or public damage cue remains a separate presentation action with its own audience.

An explicit location origin requires recipients in that location's dimension. Validate this before applying the action; do not silently shrink an authored recipient set or interpret unrelated coordinates across dimensions as an attack direction. Without an explicit origin, a cross-dimension attributable source must not supply a misleading local position; any supported remote damage path retains attribution without pretending to have a local direction. Exact world-location support follows the capability's declared reference kinds rather than falling back from a missing arena location.

An origin does not create an area selection, range limit, line-of-sight check, projectile, explosion, knockback, sound, or visual effect. Authors select targets and combine supported capabilities explicitly. Attribution and position remain distinct even when they refer to the same NPC.

The native source can represent attribution plus an explicit position, but its full constructor is private, and the ordinary client packet reconstruction loses attribution when an explicit position is present. A small supported source/client adapter is therefore required where both are needed. Preserve authoritative server attribution and verify the required client presentation rather than claiming one public native constructor already implements this contract. The implementation limits are recorded in [NPC combat research](npc-combat-research.md).

[Q227](individual-npc-controls-and-hit-reactions.md#q227-use-the-same-npc-controls-on-one-typed-target) accepts an individual typed NPC target alongside the existing group form, preserving the accepted control effects.
