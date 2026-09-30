# Combat rules and health conditions

Status: Q147-Q151 are accepted. Q142 accepts native damage and healing actions, and Q146 accepts native NPC health up to 1,000,000 points. These contracts define their next author-facing controls. No runtime integration has been implemented.

## Q147: damage types and the physical default

Accepted: give `damage` an optional `type` containing a supported registered damage-type ID. Use a built-in `conclave:physical` type by default. Explicit native types such as `minecraft:magic` retain their documented native behavior. This corrects Q142's earlier generic default: research found that Minecraft 26.2's `minecraft:generic` bypasses armor and shields through its default damage-type tags.

```yaml
damage:
  players:
    area: blast
  amount: 8
  type: conclave:physical
  source: {group: boss}
```

The built-in physical type uses the native damage pipeline with ordinary armor, effects, absorption, hurt cooldowns, and death protection. Native shield blocking remains conditional on the damage source and its position; an unattributed environmental hit does not automatically have a blockable direction. It does not bypass an explicitly authored Conclave vulnerability lock. Use no automatic difficulty multiplier for this authored type, so the amount remains the configured incoming damage before defenses. Other explicitly selected types retain their own difficulty-scaling behavior.

The damage type and optional `source` are separate. A type does not fabricate an attacker, run an NPC's melee AI, calculate weapon damage, launch a projectile, or supply a direction when none exists. A fire damage type alone does not set a target on fire. Ignition, projectile creation, knockback, and other side effects need their own supported capabilities.

Ship the fixed physical damage definition with the mod and validate its required semantics. Do not expose a generic YAML damage-type builder or arbitrary tag editing as part of this field. An installed type is usable only when the adapter can honor its advertised source requirements and behavior. Missing or unsupported types fail validation. Administrative kill types such as `minecraft:generic_kill` are not offered as ordinary authored damage types. Native damage definitions and tags are external dependencies whose reload behavior is addressed by Q148.

## Q148: external gameplay-data reloads

Accepted: distinguish ordinary Conclave publication from Minecraft datapack reloads. Conclave YAML and asset publication retains the accepted next-attempt contract and does not invoke Minecraft's global datapack reload. Native damage tags and supported external loot resources require a separate maintenance boundary because holding an old resource ID or damage source does not freeze all their behavior. See [ADR-0018](adr/0018-gated-external-data-reloads.md).

Treat a global datapack reload as maintenance. Reject its application while any attempt is preparing, active, or performing gameplay-sensitive cleanup. Do not automatically stop an attempt to make a reload succeed. Also reject changes that would alter a retained Conclave consumer's required external dependencies, such as a supported persistent effect whose native dependency must remain stable. Report the blocking attempts or consumers so an operator can finish them or use already authorized explicit cleanup operations before retrying. Do not invent an automatic retry that could apply later without the operator knowing.

Prepare and validate a candidate reload without mutating active data, then make the final eligibility check on the server thread before the first live resource or tag mutation. A failed preparation or rejected application leaves active attempts and their resource environment unchanged. Successful application when eligible invalidates readiness results tied to affected data and requires future attempts to resolve against the new compatible environment. Conclave publication itself remains available during gameplay and does not use this maintenance operation.

This requires a verified version-specific integration before the native resource swap and pending-tag application. Fabric's start notification occurs too early to know whether preparation succeeded, and its end notification occurs after application; neither alone provides the required gate. Implementation must also dispose of a rejected candidate correctly and avoid reporting a successful reload. If the gate cannot be implemented reliably for a supported reload path, this contract must be revisited before shipping, rather than silently changing it to a post-reload stop.

The gate protects supported platform reload paths, not arbitrary mods that mutate live registries or tags out of band. Detect an unsupported environment change where possible and fail affected operations explicitly; do not claim an old holder freezes its tags. Native damage-type definitions loaded at startup, mod-binary changes, and server restarts retain their separate compatibility and interrupted-attempt recovery requirements. Detailed external-dependency fingerprints, retained-effect compatibility, and startup migration rules belong to their integration contracts.

## Q149: explicit NPC vulnerability

Accepted: add `vulnerable` to NPC definitions, defaulting to `true`, and complete the accepted `set_vulnerable` action using `group` and boolean `value`. A false value installs a Conclave damage lock on those NPCs. A true value removes that lock and lets normal native defenses, immunities, and special NPC states decide whether a hit succeeds. It never forces a fire-immune NPC to take fire damage or changes armor.

```yaml
set_vulnerable:
  group: boss
  value: false
```

The lock rejects ordinary gameplay damage through all supported native damage paths, including ordinarily armor-bypassing types and creative-player attacks. Do not implement that promise using only Minecraft's invulnerable flag, which has native exceptions. Preserve explicit operator `/kill` and authorized GM removal or cleanup as administrative operations. Native `/kill` on a living NPC itself uses a damage path, so the adapter must deliberately distinguish that authorized operation rather than assume it bypasses every damage gate. Do not expose that bypass as an ordinary authored `damage` type or infer administrative authority from an arbitrary caller choosing a damage-type ID.

Supported entity adapters must verify their special damage paths; reject unsupported capability combinations. Scoped cleanup and another mod directly changing health or removing an NPC are separate operations, not damage that this lock promises to intercept. A real native administrative kill still follows native death processing; a Conclave stop or cleanup remains removal without death rewards or defeat credit.

Changing vulnerability preserves health, AI, targeting, auras, equipment, position, and group membership. The lock does not add invisible walls or restrict who can approach. Ordinary attacks and physics still follow the supported entity behavior, except for consequences suppressed by rejecting the damage itself. Do not manufacture a damage or defeat event for a rejected hit.

Resolve the current living group members once and validate support before applying the action. Repeating the current value is a no-op. Dead members remain dead; an existing group without living members is a harmless empty selection, while an unknown required group is an error. Future reinforcements use their own NPC definition, just as with `set_ai`. The value persists for each NPC's owning lifetime, including across phases for an encounter-owned NPC, until another authored action changes it.

This is explicit NPC configuration and does not create participant-only damage immunity or change ordinary player combat. A separate player-protection capability would need its own contract. Source-owned aura protection and overlapping temporary protection effects also remain separate decisions; this field is the NPC's single authored vulnerability state.

## Q150: health conditions

Accepted: add a `health` predicate with exactly one of the existing numeric comparators. A number compares current native health points. A value with `%` compares current health against the target's current maximum health, excluding absorption. Use explicit `group` for a single-NPC group, a typed event `target`, or the implicit member selected by `any` or `all`.

```yaml
complete_when:
  health:
    group: boss
    at_most: 50%
```

```yaml
any:
  players: {}
  satisfy:
    health:
      less_than: 6
```

The first fragment completes the phase when its one living boss is at or below half of its current maximum health. The second tests whether any selected player is below six health points, or three ordinary hearts. Percentage comparisons accept 0% through 100%; point comparisons accept finite nonnegative values within the supported numeric limits. Do not add a hidden tolerance to `equals`; threshold comparisons are normally clearer for floating-point health. Combine ranges using `and`.

An explicit group must resolve unambiguously to one NPC identity. A multi-member group is an error rather than an implicit sum, minimum, average, or arbitrary member. Group-selection predicates for `any` and `all` can be added through the typed collection catalog; this example does not silently invent their syntax.

A declared group that has not spawned, or a target that has died or ceased to be eligible, makes this live health predicate false. Missing declarations still fail validation. A dead NPC is not treated as a live zero-health boss. Use actual defeat tracking for kill requirements. The predicate never revives, heals, latches automatically, or blocks lethal damage; a large hit can kill the NPC before a health-based phase transition. Authors can use the accepted condition-objective `latch` when they deliberately want remembered satisfaction.

Read one coherent current state per evaluation. Changing maximum health can change a percentage result without any damage. This is a live condition, not an event that retrospectively reports every threshold crossed. [Q152-Q153](combat-events-and-controls.md) accept optional health floors, the threshold notification, and resolved combat events, with native integration still requiring verification.

## Q151: explicit percentage amounts

Accepted: extend `damage.amount` and `heal.amount` beyond positive health-point numbers with an explicit percentage form that names its basis. Keep exactly one amount form and resolve it independently for each recipient at action execution.

```yaml
damage:
  group: boss
  amount:
    percent: 10%
    of: max_health
```

```yaml
heal:
  players:
    role: runner
  amount:
    percent: 100%
    of: missing_health
```

Allow `max_health` and `current_health` for damage, and those bases plus `missing_health` for healing. `missing_health` is the nonnegative difference between current maximum and current native health. It excludes absorption. Require a positive percentage at most 100% and an explicit basis; do not accept a bare percentage whose denominator must be guessed. A valid basis of zero gives a no-op rather than an invalid authored amount.

Percentage damage computes incoming damage before the selected type's defenses and scaling. Ten percent of maximum health therefore does not promise a ten-percent health loss, and 100% does not guarantee a kill. Healing still clamps to the maximum and never revives. The missing-health example fully heals an eligible living recipient through the normal supported heal operation when its healing-received factor is neutral. The accepted [Q157](aura-effects.md#q157-damage-and-healing-multipliers) modifier also affects this request, so a healing reduction can leave health missing.

Capture the recipient set and each recipient's basis from one action snapshot before applying that action's results. Later actions see the resulting state under Q68. Keep native float behavior, including tiny amounts rounding away, and validate overflow and non-finite calculations. This is bounded typed arithmetic, not an expression language or a direct health setter. It neither introduces shared group health nor chooses one recipient's maximum for everyone.

[Q227](individual-npc-controls-and-hit-reactions.md#q227-use-the-same-npc-controls-on-one-typed-target) also accepts a typed individual NPC `target` for these controls, preserving their effects and ownership requirements.
