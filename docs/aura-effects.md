# Aura modifiers and native status effects

Status: Q156-Q158 are accepted. Source-owned aura contributions, stacking, retained player lifetimes, native stats, combat actions, and resolved combat events are accepted. These contracts specify effect behavior; no runtime hooks have been implemented.

## Q156: temporary stat modifiers owned by an aura

Accepted: add a `modifiers` list to aura definitions. Each entry identifies a supported `stat` and exactly one operation, `add` or `multiply`. An explicit multiplier of `120%` means the resulting factor is 1.2. A flat addition uses that stat's documented units. Keep modifier configuration in the aura definition, not as an override on `apply_aura`.

```yaml
modifiers:
  - stat: speed
    multiply: 120%
    per_stack: true
  - stat: armor
    add: 4
```

Start with the accepted `health`, `melee_damage`, `speed`, `armor`, and `knockback_resistance` vocabulary on supported holders. Here `health` modifies maximum health. Permit numeric point additions for health, melee damage, and armor; permit percentage-point additions for knockback resistance, where `add: 10%` adds 0.1 to native resistance. Offer speed multiplication without a flat-speed addition whose unit would be unclear. Permit dimensionless multiplication for each supported stat. Other stats require a documented adapter capability instead of raw attribute mutation or arbitrary property access.

Modifiers work on supported players and NPCs without rewriting their base values. Removing an aura removes only its owned modifiers, exposing the current base and other equipment, status-effect, and mod contributions. A base change through `set_stats` remains after an aura expires. Never restore a saved old stat value as a substitute for removing the modifier.

Evaluate a modifier once while the aura is present by default, even when the aura has several contributions or stacks. Optional `per_stack: true` requires a stacking aura and uses its currently accepted stack count. Additions scale linearly. Multiplier bonuses and penalties also scale linearly within that aura: an authored `120%` yields 120%, 140%, and 160% at one, two, and three stacks. An `80%` factor yields 80%, 60%, and 40%. Validate the declared stack cap so it cannot produce a negative factor; zero is allowed where the supported stat permits it.

Different aura modifiers remain independent. Flat additions combine at the native addition stage; multiplier entries use native total multiplication after native flat and base-multiplier stages. Two separate aura entries at `120%` therefore produce a combined 144% multiplier. The editor shows the authored modifier, active stack scaling, and effective stat. Ordinary native attribute bounds still apply to the calculated result; invalid authored operands are rejected rather than silently rewritten.

A non-stacking aura with multiple independent sources still contributes its gameplay modifier only once. Removing one source must leave that modifier in place while another compatible contribution remains. Refreshing a timer does not remove and re-add a modifier or trigger intermediate maximum-health clamping. When the effective stack count changes, reconcile its aggregate once for that committed change before delivering the corresponding aura events.

Raising maximum health does not heal. Removing a health bonus can clamp current health to the resulting maximum; this is not damage and does not emit `damaged`. A retained health floor follows its accepted native maximum-health behavior. No hidden health restoration occurs when a modifier is re-applied.

Use Conclave-owned modifier identities that cannot collide with equipment or another source. Rebuild persistent aura effects from authoritative saved applications without replaying application events or creating duplicate modifiers. Reconcile expired offline applications before they can affect gameplay on login. Restoration must establish valid maximum-health modifiers before saved current health is incorrectly clamped; the exact load integration requires verification. Modifier storage alone is not the authoritative aura lifecycle. [Native modifier research](aura-modifier-research.md) records arithmetic, save behavior, and identity-update hazards.

## Q157: damage and healing multipliers

Accepted: allow `damage_dealt`, `damage_taken`, and `healing_received` as additional aura modifier targets, using `multiply` and the same explicit stack-scaling choice. These targets are supported combat controls rather than native attribute aliases. They do not become additional fields in NPC `set_stats` merely by appearing here.

```yaml
modifiers:
  - stat: damage_taken
    multiply: 80%
  - stat: healing_received
    multiply: 150%
```

At a supported damage operation, multiply once by the attributable causing player or NPC's current `damage_dealt` factor and the target's current `damage_taken` factor. Resolve a projectile's causing entity when the native source supplies it; do not treat the projectile itself as its shooter. An absent or no-longer-valid attributable holder supplies a neutral dealt factor. Resolve modifiers when damage reaches the target, so a projectile does not silently freeze these general aura bonuses at launch.

Apply these factors at the shared incoming-damage stage before ordinary blocking, hurt-cooldown comparison, armor, absorption, and health floors. Native difficulty scaling and some early immunity checks have already occurred on the inspected player path. Keep the accepted physical type's no-difficulty-scaling contract. An explicit authored `damage` action and a native attack that reaches the same supported path both receive the factors exactly once.

Do not also install a general `damage_dealt` contribution as a melee-damage attribute bonus or pre-scale it in the authored action helper. If an author deliberately configures both `melee_damage` and `damage_dealt`, they have chosen separate effects and both can influence a melee hit. A general combat multiplier does not create an attacker, increase projectile range, or change the audience of presentation cues.

Scale positive supported healing once by the target's current `healing_received` factor, then clamp to current maximum health. Native calls through the supported heal path and Conclave `heal` share this behavior. Maximum-health changes, spawn initialization, revival, and totem rescue assignments remain separate. A healing reduction also reduces an authored request for 100% of missing health; the Q151 full-heal example describes the neutral-factor result rather than a defense-bypassing health setter.

Different aura factors multiply. `80%` taken means 20% less incoming damage at this stage; combining it with a separate `150%` taken factor yields 120% before native defenses. Permit a zero factor and reject negative or non-finite factors. Zero damage is not identical to the earlier vulnerability gate: ordinary native hit handling can have different consequences even when no health is removed. Explicit authorized administrative kill/removal keeps its accepted handling.

Validate aggregate arithmetic and conversion to native numeric types, not just individual YAML values. Reject an aura application whose resulting owned aggregate is invalid before committing it. Never let an overflow become native non-finite damage that is interpreted as an enormous hit. Unexpected runtime incompatibility follows the existing required-operation policy and produces no fabricated damage/healing event.

These effects follow their aura's accepted ownership, expiry, stack, death, revision, and persistence rules. Required entity adapters must verify the hooks and source attribution. Direct health writes and unsupported overrides are not covered by an ordinary damage/heal hook. Initial multipliers apply to all supported damage or healing for the holder; damage-type-specific filters, healing-dealt bonuses, outgoing proc attribution, and additional mitigation stages remain later capabilities. [Native research](aura-modifier-research.md#candidate-damage-and-healing-multiplier-hooks) establishes candidate hook points, not a tested integration.

## Q158: native status effects as explicit actions

Accepted: expose separate `apply_status_effect` and `remove_status_effect` actions, using a registered `effect` ID and the accepted explicit player, group, or typed event target forms. Keep Conclave aura ownership separate from native effect merging. Minecraft stores no reliable source ownership for merged status effects, so ordinary native APIs cannot remove only a Conclave contribution while preserving an overlapping potion.

```yaml
apply_status_effect:
  players:
    role: runner
  effect: minecraft:speed
  level: 2
  duration: 10s
```

Use human-facing levels starting at one, with a default of one. Translate them to native amplifier values internally. Validate supported effect/holder combinations and useful level bounds rather than exposing arbitrary NBT or assuming every representable native amplifier is suitable. Sustained effects require an explicit positive finite duration. Native immunity or native merge rules rejecting a weaker application are normal no-change outcomes.

For a supported instant effect, apply it once through its native instant-effect path and reject a `duration` field. Do not wrap an instant effect in a ticking duration that repeats it every tick. Keep native instant-heal/harm differences for supported target types and use only reliable attribution available to the operation. An instant effect is not a lasting aura contribution.

This action deliberately hands the effect to Minecraft. Native rules control merging, stronger and hidden effects, milk, death, saving, and duration afterward. Conclave does not repeatedly reapply it, automatically remove it when an aura or phase ends, or restore an old potion snapshot. A future attempt can encounter a still-active native effect in the ordinary world, just as it can encounter an item or potion obtained normally. This is an explicit exception to owned temporary-effect cleanup. Conclave still cleans up its owned aura modifiers. See [ADR-0019](adr/0019-native-status-effect-handoff.md).

Native effect duration normally pauses while the player is offline. Conclave's player-owned aura timers continue to age on the running server under Q117. Preserve that difference instead of pretending one timer implements both systems. Milk clears applicable native effects and does not automatically remove Conclave auras. Explicit aura-removal mechanics remain available when an author wants them.

`remove_status_effect` requires an explicit effect ID and removes the whole native effect of that type on the selected targets, including hidden or independently granted potion contributions. An absent effect is a no-op. Do not invoke it automatically as source cleanup or add an implicit clear-all behavior. Its editor description must make the whole-effect consequence visible. Native cleansing does not remove a Conclave aura with a similar name or a native attribute bonus from another mechanism.

These actions use the native effect's presentation and behavior; they do not automatically create a Conclave aura icon or imply phase ownership. An author needing exact aura-lifetime control should use supported owned aura modifiers or other explicit Conclave capabilities. A fully source-aware projection of arbitrary native effects would require a separate integration and coexistence contract, so this contract does not promise one. [Status-effect research](status-effect-research.md) records the ownership, timing, merge, and instant-effect limits.

[Q159-Q161](aura-periodic-and-queries.md) accept periodic holder damage and healing, contribution and stack removal notifications, and detailed aura-state conditions. Periodic schedules follow the aura lifecycle, retain their cadence across refreshes, and discard pulses skipped while the holder is dead or offline.
