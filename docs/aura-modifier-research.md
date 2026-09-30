# Aura modifier research

Bounded source inspection for Minecraft Java 26.2 / Fabric, checked 2026-09-17. These are native facts and implementation candidates, not accepted aura policy or a tested Conclave implementation. No gameplay code was changed.

The native evidence below comes from Mojang's immutable [26.2 artifact](https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar), identified by the official [26.2 metadata](https://piston-meta.mojang.com/v1/packages/987b91a95ae93b3bb78cc14d6e0bbd31bad08d59/26.2.json). Named methods were inspected with `javap`; integration and compatibility tests remain outstanding.

## Native attribute arithmetic

`AttributeInstance.calculateValue` applies these stages in order:

| Operation | Native meaning |
| --- | --- |
| `ADD_VALUE` / `add_value` | Add all flat amounts to the base value, producing `X`. |
| `ADD_MULTIPLIED_BASE` / `add_multiplied_base` | Add `X * amount` for each modifier. These contributions add together and use the value **after flat additions**, not the untouched base. |
| `ADD_MULTIPLIED_TOTAL` / `add_multiplied_total` | Multiply the running result by `1 + amount` for each modifier. These factors compound. |

Ignoring rounding, the result is `(base + sum(flat)) * (1 + sum(baseFractions)) * product(1 + totalFraction)`, followed by the attribute's `sanitizeValue`. For a ranged attribute this clamps to its allowed range; NaN becomes the minimum. Two `+0.2` multiplied-base modifiers contribute a `1.4` factor, while two `+0.2` multiplied-total modifiers contribute `1.44`. A native fractional amount of `0.2` means an additional 20%, not a final multiplier of `0.2`. Source: `AttributeModifier.Operation`, `AttributeInstance.calculateValue`, and `RangedAttribute.sanitizeValue` in the official artifact.

An author's flat-versus-multiplier vocabulary therefore needs an explicit translation to these operations. Native arithmetic alone does not choose aura stacking rules. Attributes also need to exist on the target's attribute map; declaring a modifier does not make an unsupported attribute functional. Source: [Fabric 26.2 attribute guide](https://docs.fabricmc.net/develop/entities/attributes).

## IDs, replacement, removal, and saving

Modifier identity is an `Identifier`, unique within one entity's instance of one attribute. Ordinary `addTransientModifier` and `addPermanentModifier` throw if that ID is already present. `removeModifier(id)` removes the matching operation entry and permanent entry, then marks the value dirty. It does not reset the base or remove unrelated IDs. Sources: `AttributeModifier` and `AttributeInstance` in the official artifact.

There is a replacement caveat in the inspected version: `addOrUpdateTransientModifier` overwrites the ID map and the new operation's bucket, but does not remove a previous bucket if that ID changes operation, or clear a pre-existing permanent entry. A fixed-operation, transient-only ID can use the updater; a general replacement needs explicit removal followed by addition. `addOrReplacePermanentModifier` already removes first. Reusing another owner's ID can consequently overwrite or remove that owner's contribution. Conclave ownership-scoped IDs and cleanup bookkeeping are implementation work, not protection supplied by the native API.

`AttributeInstance.pack` saves the base value and **only permanent modifiers**. Transient modifiers participate in live calculations but are omitted from that attribute save. They are not necessarily client-invisible: `ClientboundUpdateAttributesPacket` sends all current modifiers for synchronized attributes. An aura runtime must reconstruct any intended transient state after loading; removing a transient modifier does not itself persist the aura's lifecycle. Sources: `AttributeInstance.pack`, `AttributeMap.onAttributeModified`, and `ClientboundUpdateAttributesPacket` in the official artifact.

## Interaction with other attribute changes

`setBaseValue`, modifier addition, and removal dirty the calculated value. Removing an aura modifier therefore exposes the **current** base and remaining equipment/effect/mod contributions, including changes made while the aura was active. Restoring a captured old base would instead overwrite those intervening changes. Equipment uses transient attribute modifiers; native `MobEffect.addAttributeModifiers` uses permanent ones and removes them by ID when appropriate. “Temporary gameplay effect” and “transient attribute modifier” are therefore different native concepts. Sources: `AttributeInstance`, `LivingEntity.collectEquipmentChanges`, and `MobEffect.addAttributeModifiers` / `removeAttributeModifiers` in the official artifact.

When dirty maximum-health changes are processed, `LivingEntity.onAttributeUpdated` reduces current health if it exceeds the new maximum. Raising maximum health does not heal. Removing a positive maximum-health aura can thus clamp health downward; restoring the aura later does not restore that lost health. This setter-based clamp also lies outside the proposed damage-floor hook. Attribute recalculation and the subsequent dirty-attribute side effects need an explicit runtime ordering; neither percentage preservation nor floor compatibility follows automatically. Source: `LivingEntity.refreshDirtyAttributes`, `onAttributeUpdated`, and `setHealth` in the official artifact.

## Candidate damage and healing multiplier hooks

Vanilla's `Attributes` contains `ATTACK_DAMAGE`, but no general `damage_dealt`, `damage_taken`, or `healing_received` attribute. These typed Conclave quantities require installed logic that consumes their computed values; registering an attribute alone would not apply them. This does not require arbitrary scripts.

For supported living targets, a single candidate application point is the common `LivingEntity.hurtServer` path before it saves the original amount and calls `applyItemBlocking`. It is before blocking, hurt-cooldown comparison, armor/effect mitigation, absorption, and health loss. Native early immunity checks occur earlier, and `Player.hurtServer` performs difficulty scaling before delegating to this common path. Thus this point is **not before every native adjustment**. Multiplying the incoming amount once by the selected causing entity's dealt factor and the target's taken factor could cover native melee and authored damage that reach this path. Attribution, absent causes, supported overrides, event measurement boundaries, and exact injection ordering remain implementation decisions. Sources: `LivingEntity.hurtServer`, `Player.hurtServer`, and `DamageSource.getEntity` in the official artifact.

`Mob.doHurtTarget` already reads the fully modified `ATTACK_DAMAGE`, applies weapon/enchantment adjustments, then calls the victim's damage method. A generalized dealt multiplier applied at the incoming hook must not also be installed as an `ATTACK_DAMAGE` modifier or pre-applied by an authored damage helper: that would apply the same contribution twice to melee. A separately authored melee-attribute modifier and general dealt modifier would naturally both affect a melee hit, so their advertised meanings must remain distinct. Source: `Mob.doHurtTarget` in the official artifact.

`LivingEntity.heal(float)` is a narrow candidate for positive ordinary healing: scale the positive requested amount once before its addition to current health, retain the alive check, and let `setHealth` enforce the maximum. This covers supported callers of that method, not direct health assignment, maximum-health changes, totem restoration, or overrides that bypass it. An authored heal helper must delegate without pre-applying the same factor. Source: `LivingEntity.heal` and `setHealth` in the official artifact.

Fabric's existing living-entity damage events do not expose an amount-changing return value, and that event family has no ordinary-heal amount hook. The candidate transformations therefore require narrow custom hooks or verified entity overrides. Validate aggregate arithmetic as well as authored inputs: finite factors can overflow when multiplied, and native damage handling converts non-finite amounts to `Float.MAX_VALUE`. Testing must cover once-only application, native/player paths, modifier replacement, save/reload reconstruction, and maximum-health removal. Sources: [pinned Fabric event declarations](https://github.com/FabricMC/fabric/blob/0ec1d58582bd816012a8f9f1617eb4dfdeda1e93/fabric-entity-events-v1/src/main/java/net/fabricmc/fabric/api/entity/event/v1/ServerLivingEntityEvents.java), [pinned hook locations](https://github.com/FabricMC/fabric/blob/0ec1d58582bd816012a8f9f1617eb4dfdeda1e93/fabric-entity-events-v1/src/main/java/net/fabricmc/fabric/mixin/entity/event/LivingEntityMixin.java), and the native methods above. None of these Conclave integration tests has run.
