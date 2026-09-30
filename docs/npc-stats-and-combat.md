# NPC stats and combat actions

Status: Q141-Q146 are accepted. User corrections name the base-type field `npc`, keep Minecraft's native equipment slots, and increase the supported health ceiling to 1,000,000 points through native health integration. No NPC adapter, damage hook, or gameplay test has been implemented.

## Q141: base entity and readable stats

Accepted with the user's confirmed rename: name the registered base NPC type with `npc` and group supported base-stat configuration under `stats`. Keep `id`, optional `name`, `ai`, and `appearance` in their established roles. The base NPC type supplies ordinary behavior; a model does not select that behavior.

The outer `npc` key identifies the manifest kind. Inside that definition, `npc: minecraft:zombie` selects a registered Minecraft or installed-mod NPC type. A spawn action's `npc` reference still identifies a Conclave NPC definition. These are distinct typed contexts; a base-type reference never recursively resolves another Conclave definition.

```yaml
schema: 1
namespace: raid_tools
npc:
  id: vault_guardian
  npc: minecraft:zombie
  name: Vault guardian
  stats:
    health: 200
    melee_damage: 8
    speed: 120%
    armor: 10
    knockback_resistance: 50%
```

`health` sets native maximum health and starts a newly spawned NPC at its final configured maximum. Health and damage use health points, with two points corresponding to one ordinary heart. `melee_damage` sets the supported base melee attack attribute; weapon enchantments, equipment modifiers, difficulty rules, and mitigation still participate where the entity's normal attack uses them. It does not promise to control ranged projectiles, explosions, or every special attack.

`speed` is an explicit percentage of the selected adapter's base movement attribute for that entity type, with 100% meaning unchanged base speed. It is not a guaranteed blocks-per-second value. Navigation, terrain, status effects, and other normal modifiers can affect actual movement. Walking, flying, and swimming adapters must state which movement attributes they control; reject an unsupported control instead of silently using a walking attribute on an unrelated movement system.

`armor` uses native armor points. `knockback_resistance` uses an explicit percentage mapped to the native resistance attribute. Preserve documented native bounds, including supported negative resistance that increases knockback, and report an out-of-range value instead of silently clamping it. Omitted stats inherit the selected entity's supported defaults. These are base values, not an instruction to erase every later ordinary modifier.

Unmodified Minecraft 26.2 limits its native maximum-health attribute to 1 through 1,024 points. Q146 accepts increasing the supported authored ceiling to 1,000,000 points through native attribute integration while retaining ordinary health and combat processing. A large YAML number or a new unused custom attribute alone cannot bypass the clamp.

Validate property support and numeric bounds before publication. Apply the complete supported spawn configuration before exposing the NPC as a successfully created group member. Existing full-batch spawn and cleanup behavior still applies. Exact native facts and limitations are recorded in [NPC combat research](npc-combat-research.md).

## Q142: damage and healing actions

Accepted: add typed `damage` and `heal` actions that reuse the explicit `players`, `group`, or event `target` recipient forms. Each action captures its recipients once. Require an `amount` in positive finite health points. A `damage` action may identify an attributable NPC or player through a typed `source`; omitting it means an environmental source rather than inventing a killer.

```yaml
do:
  - damage:
      players:
        area: blast
      amount: 8
      source: {group: boss}
```

An NPC-group source must identify exactly one applicable NPC. A typed event source can name a particular player or NPC when the event exposes that identity. Invalid or stale required references follow the existing validation and required-operation policy; they do not silently transfer attribution to a replacement entity.

Damage goes through the supported ordinary Minecraft damage pipeline. The declared amount is incoming damage before applicable armor, effects, shields, immunity, and other native rules; it is not a promise that health decreases by exactly that amount. A source identifies attribution but does not turn the action into a complete native melee attack with automatic weapon calculations, attack animation, navigation, or reach checking. Those behaviors must be supplied by their supported capabilities.

Expose a typed damage-kind reference only for supported registered damage semantics. [Q147](combat-rules-and-health.md#q147-damage-types-and-the-physical-default) accepts `damage.type` with the default `conclave:physical`, correcting the earlier generic default because Minecraft's `generic` type bypasses armor and shields. A profile that bypasses armor or other defenses must be explicitly selected and documented by its adapter; do not introduce a universal `ignore_everything` switch or implement damage by writing health directly. [Q148](combat-rules-and-health.md#q148-external-gameplay-data-reloads) accepts the external-reload boundary separately.

Ordinary defenses preventing damage are gameplay outcomes, not technical errors. Actual lethal damage enters the normal death, grave, defeat-attribution, and configured loot paths once. A declared gameplay wipe remains the accepted attempt outcome and recovery operation; it does not secretly invoke lethal damage on every participant.

Healing restores living targets up to their current maximum health. It does not resurrect a grave-bound or passed-out player, reset a revive window, add absorption, refill hunger, or remove auras. Those are separate supported operations. A recipient who becomes dead before the operation can apply receives no healing; the action cannot bypass the revival module. Full health, ordinary damage immunity, and valid empty selections are harmless no-change outcomes. Unsupported required targets remain errors.

[Q151](combat-rules-and-health.md#q151-explicit-percentage-amounts) accepts explicit percentage amounts and a missing-health healing basis. [Q152-Q155](combat-events-and-controls.md) accept health floors, resolved combat events, live base-stat mutations, and explicit damage origins. This action family does not introduce arbitrary numeric expressions or a health setter that bypasses gameplay rules.

## Q143: physical size versus appearance scale

Accepted: expose an optional `size` percentage on supported NPC definitions for native physical scaling, with 100% as the NPC's normal scale. Keep the accepted `appearance.scale` as an additional visual multiplier. Authors can therefore intentionally enlarge the physical NPC, enlarge only its appearance, or configure both while seeing the difference in preview.

```yaml
size: 150%
appearance:
  model: raid_tools:vault_guardian
  scale: 1
```

Use the native scale behavior through a verified entity adapter. The inspected 26.2 attribute permits multipliers from 0.0625 to 16, corresponding to 6.25% through 1,600%; an individual adapter may support a narrower range. Validate the actual entity dimensions, pose changes, and safe placement at the configured size. Scale the custom model consistently with that physical size before applying the additional appearance multiplier, so the example does not accidentally apply 150% twice.

Changing native size follows the supported native size-related behavior; it is not a health or damage multiplier. Keep health and attack configuration explicit. The same preview shows the rendered model and actual collision dimensions. A large visual-only model is permitted when authored deliberately, but its bones do not automatically become collision or weak-point hitboxes.

Do not expose arbitrary independent collision meshes, multipart boss weak points, or root-motion physics through this initial field. Those require explicit supported gameplay capabilities. Omitted size keeps the base entity's native behavior, with no automatic change based on model bounds or an encounter being active. Runtime resizing and obstruction recovery need a separate contract before becoming an action.

## Q144: typed equipment

Accepted with the user's correction: expose an `equipment` map using Minecraft's native equipment slots and their native serialized names. Do not introduce Conclave-specific slots or replace native names with aliases. The selected NPC must support the requested slot. A registered item ID is the concise form. A configured item form may add a valid count, enchantments, and explicitly supported typed item properties; arbitrary raw NBT or executable components are not authoring inputs.

Minecraft 26.2's native names are `mainhand`, `offhand`, `feet`, `legs`, `chest`, `head`, `body`, and `saddle`. `body` is distinct from `chest` and is used by supported animal equipment. Native item metadata and NPC-specific slot rules determine applicability. Do not treat inventory paths or command selectors such as `weapon.mainhand` as equipment-map keys. These names were verified against `EquipmentSlot`, `EntityEquipment.CODEC`, `SlotRanges`, and `Equippable` in Mojang's [official 26.2 artifact](https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar).

```yaml
equipment:
  mainhand: minecraft:iron_sword
  head: minecraft:iron_helmet
```

If the equipment section is omitted, retain the entity type's normal initial equipment behavior. If it is present, treat it as the complete authored initial loadout: unspecified supported slots are empty, and `equipment: {}` clears the initial loadout. This avoids random spawn equipment appearing in slots the author expected to control. Validate unsupported slots, item references, counts, and enchantments before spawn.

These are real equipped items with their supported native gameplay effects, including weapon and armor modifiers. [Q162](npc-model-presentation.md#q162-explicit-equipment-attachments-on-custom-npc-models) accepts explicit model bone bindings for `mainhand` and `offhand`, with separate verified support required for armor and special layers. Equipping an item does not prove every custom model knows where to draw it. Equipment configuration does not itself grant a passive NPC combat AI or make a ranged attack obey the melee-damage attribute.

Q97's no-equipment-drops default remains in force unless explicitly configured otherwise. Initial equipment configuration does not snapshot and restore later inventory, repeatedly re-equip removed items, or silently change ordinary item pickup behavior. A later pickup policy or equipment-changing action must be explicitly supported. Cleanup removes the owned NPC without manufacturing death rewards.

## Q145: boss-bar configuration and audiences

Accepted: an optional `boss_bar` section displays one bar for that NPC's actual health divided by its current maximum health. Use the NPC's name by default, with typed supported title, color, and segmentation options. Require an explicit `audience` using the accepted presentation selector, so whole-team, area, radius, or role-limited bars remain author-controlled.

```yaml
boss_bar:
  audience: {from: participants}
```

The bar follows authoritative health and maximum-health changes. It is visible only to currently eligible connected recipients while its NPC and owning lifecycle remain valid. Reconnecting receives current state, not a history of old health values. A dead or cleaned-up NPC removes its bar; the accepted finite final dialogue can still finish independently.

Each spawned copy has its own bar. Do not silently pool a group's health, choose a random group member, or grant a boss bar invulnerability and encounter progression effects. Shared-health bosses and a bar bound to an authored counter or objective need their own declared capability rather than an ambiguous aggregation default.

If the base entity has a native boss bar, a supporting adapter must avoid showing both native and Conclave bars when this override is configured, and must respect the selected audience. An unsupported override fails validation. Omitting the section adds no Conclave bar and preserves ordinary base-entity behavior. The bar is presentation; it does not restrict who may approach, attack, or assist.

## Q146: increased native health ceiling

Accepted: support authored NPC health up to 1,000,000 points by increasing the native `minecraft:max_health` ceiling through a narrow Conclave integration. Keep the native minimum, default values, modifiers, current-health storage, and combat processing. Implement the compatible ceiling on both server and required clients. Keep one native health pool, with damage, healing, death, persistence, and boss bars using that pool. See [ADR-0017](adr/0017-raised-native-health-ceiling.md).

The higher maximum is a shared Minecraft attribute ceiling. It is not scoped exclusively to Conclave NPCs: other entities or mods can also use the larger permitted range. Raising the ceiling does not heal them or change ordinary vanilla health defaults. Existing base values or modifiers previously clipped at 1,024 can produce a higher effective maximum. Authors still choose each Conclave NPC's health explicitly. Do not raise unrelated attribute limits as part of this change.

Prefer a small internal integration for this one attribute rather than requiring another general attribute-configuration mod or its external configuration workflow. AttributeFix's Minecraft 26.2 source provides evidence that the native-ceiling approach is practical, not proof that Conclave integration already works. Exact hook selection and compatibility behavior need an implementation check. If another mod supplies a higher compatible ceiling, do not lower it; Conclave's authored health limit remains 1,000,000. Detect an incompatible effective ceiling rather than accepting content that will be silently clamped.

Install the engine ceiling at startup, before NPC state is loaded and before attributes are evaluated on clients. Do not change this shared engine limit through manifest hotfixes. An authored `stats.health` change within the supported range remains ordinary revisioned content and affects only future attempts. Initial NPC health still starts at the final configured native maximum under Q141.

Use a finite supported range rather than an unlimited numeric value. Native current health is a 32-bit floating-point value; at 1,000,000 points its representable step is 0.0625 points. Very small damage or healing can therefore round away. Raising the ceiling much further loses more precision. This contract does not promise arbitrary-precision raid health or add hidden damage scaling to compensate.

Before declaring support, verify a live NPC above 1,024 points through spawn, native damage and healing, client attribute synchronization, boss-bar updates, save/load and interrupted-attempt recovery, and lethal damage/defeat/loot processing. Verify that ordinary NPC and player defaults remain unchanged and test any supported modded-NPC adapter. Preserve the accepted technical-error policy when a required integration fails. The evidence and unverified boundaries are recorded in [NPC combat research](npc-combat-research.md).

[Q257](fixed-reward-items-and-enchantments.md#q257-one-explicit-enchantment-map-with-normal-compatibility) accepts the explicit-enchantment map, normal compatibility checks, and stored-book handling for Q144 configured real items. Q256 accepts count defaulting to one and completion-reward quantities that may span multiple legal stacks; equipment still requires one legal stack per native slot.
