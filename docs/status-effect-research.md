# Native status effects and Conclave auras

Research date: 2026-09-17. Target: Minecraft Java 26.2 with Fabric. This records native behavior and implementation implications. It does not select aura-to-effect mapping, dispel policy, or an implementation.

## Findings that constrain the design

1. Vanilla retains one merged entry per status-effect type, with an optional hidden-effect chain. It does not retain independent applications or their owners. Removing a type removes the whole entry.
2. Native effect timers pause while a player is offline. Q117 requires player-owned Conclave auras to keep aging while the server runs, so the native effect timer cannot be their authoritative timer.
3. Milk clears the native effect map, including hidden effects. It does not know about Conclave aura contributions stored elsewhere.
4. Instant effects are executable behavior. Treating one as a continuously maintained effect can repeat healing or damage rather than represent a removable buff.

These findings come from the exact [official 26.2 client artifact](https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar). The method-level evidence and its limits follow below.

## Evidence and scope

The cached client jar's SHA-1 was verified as `2dc72797acbc1b63fc16a11c4ac393605f453754`, matching the [official version metadata](https://piston-meta.mojang.com/v1/packages/987b91a95ae93b3bb78cc14d6e0bbd31bad08d59/26.2.json). Its common and server classes were inspected with `javap`; the game and mod code were not executed. Native statements below concern this artifact. No claim is made that every other mod preserves these paths.

Fabric's [26.2 effect documentation](https://docs.fabricmc.net/develop/entities/effects) confirms the ordinary `LivingEntity.addEffect(MobEffectInstance)` integration, tick-based durations, and zero-based amplifier. Fabric's [26.2 data-attachment documentation](https://docs.fabricmc.net/develop/serialization/data-attachments) provides a separate mechanism for persisted entity data and selective client synchronization. Neither document establishes automatic source ownership for vanilla effects.

The applicable Conclave contract is in [auras and world lifetimes](auras-and-world-lifetimes.md). Its contribution identities, independent stack durations, captured definitions, and disconnected-player timing are distinct from the native representation described here.

## Native merging and hidden effects

`LivingEntity.activeEffects` is a map keyed by `Holder<MobEffect>`. `addEffect` inserts a new type or calls `MobEffectInstance.update` on the existing instance of that type. Repeated applications do not create a native stack count or add their amplifiers together. [26.2 artifact, `LivingEntity.addEffect` and `MobEffectInstance.update`](https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar)

| Incoming application | Native result for strength and duration |
|---|---|
| Higher amplifier | Replaces the active amplifier and duration. If it ends sooner than the previous active effect, the previous effect becomes hidden. |
| Same amplifier, longer duration | Extends the active duration to the incoming duration. Durations are not added. |
| Same amplifier, equal or shorter duration | Does not extend or shorten the active duration. |
| Lower amplifier, longer duration | Can become a hidden effect, or merge into the existing hidden chain. |
| Lower amplifier that does not outlast the active effect | Does not create a separately retained strength/duration contribution. |

The incoming particle/icon flags can still change presentation when strength and duration do not change. Ambient handling also has merge rules. These flags are shared properties of the resulting instance, not independent per-source display preferences. [26.2 artifact, `MobEffectInstance.update`](https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar)

`tickDownDuration` also decrements hidden durations. Hidden effects do not wait with paused timers. When the active duration reaches zero, `downgradeToHiddenEffect` promotes the hidden entry with its remaining duration. Thus a 100-tick weaker effect hidden by a 40-tick stronger effect has 60 ticks left when it returns, assuming uninterrupted entity ticking. This example follows the inspected code; it was not tested in a running world. [26.2 artifact, `MobEffectInstance.tickServer`, `tickDownDuration`, and `downgradeToHiddenEffect`](https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar)

Duration `-1` means infinite and does not count down. The constructor clamps amplifier to 0 through 255, and the persisted amplifier codec is an unsigned byte. Amplifier 0 corresponds to level I. These are native representation facts, not proposed authoring limits for Conclave. [26.2 artifact, `MobEffectInstance` constructor, `isInfiniteDuration`, and `Details` codec](https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar), [Fabric effect parameters](https://docs.fabricmc.net/develop/entities/effects#applying-the-effect)

## Ownership and removal

`MobEffectInstance` stores the effect type, duration, amplifier, ambient/particle/icon flags, hidden effect, and visual blend state. There is no application ID, source owner, attempt ID, or original potion record. Its persisted details include `amplifier`, `duration`, `ambient`, `show_particles`, `show_icon`, and recursive `hidden_effect` data. [26.2 artifact, `MobEffectInstance` fields and `MobEffectInstance.Details` codec](https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar)

The overload `addEffect(instance, sourceEntity)` passes the source to add/update callbacks. It does not add that source to the stored instance. Instant effects may separately use source entities for damage attribution. Neither mechanism supplies persistent ownership of a merged status-effect contribution. [26.2 artifact, `LivingEntity.addEffect` and `HealOrHarmMobEffect.applyInstantaneousEffect`](https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar)

`removeEffect(type)` removes the map entry for that type and runs removal handling. Its hidden chain leaves the map with it; removal does not reveal the hidden potion effect. `removeAllEffects` clears the map. `/effect clear` uses those methods for one type or all types. Milk's `Consumables.MILK_BUCKET` contains `ClearAllStatusEffectsConsumeEffect`, whose application calls `removeAllEffects`. [26.2 artifact, `LivingEntity`, `EffectCommands`, `Consumables`, and `ClearAllStatusEffectsConsumeEffect`](https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar)

**Implementation inference:** removing only Conclave's contribution after arbitrary overlap with an independent potion cannot be guaranteed by reading the resulting vanilla entry and calling stock removal methods. Different histories produce the same entry, and some incoming effects are discarded during merging. Matching amplifier, duration, object identity, or display flags is not proof that the entry belongs exclusively to Conclave.

**Implementation inference:** saving a pre-aura snapshot and restoring it later is insufficient. Another potion, milk, a command, or another mod may change the effect while the aura is active. Restoring the snapshot can erase newer state or resurrect a deliberately removed effect. Short refreshes can reduce how long a grant remains after refresh stops, but do not establish ownership or immediate selective removal.

## Entity applicability and instant effects

Native application targets `LivingEntity`. `addEffect` first asks `canBeAffected`. The base 26.2 checks reject poison and regeneration for the `ignores_poison_and_regen` tag, infested for `immune_to_infested`, and oozing for `immune_to_oozing`. The shipped tags include undead, silverfish, and slime respectively. Subclasses and effect implementations can impose additional behavior. [26.2 artifact, `LivingEntity.canBeAffected` and the named `data/minecraft/tags/entity_type` files](https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar)

Acceptance does not mean every effect has useful behavior on every living entity. For example, saturation's effect logic changes food only for players. Heal/harm checks the recipient's inverted-healing behavior. A status-effect category does not establish universal applicability. [26.2 artifact, `SaturationMobEffect.applyEffectTick` and `HealOrHarmMobEffect`](https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar)

Potion consumption distinguishes `MobEffect.isInstantaneous()`. `PotionContents.applyToLivingEntity` calls `applyInstantaneousEffect` for instant effects and `addEffect` for sustained effects. However, an instant effect can also be represented as a positive-duration `MobEffectInstance`. `InstantaneousMobEffect.shouldApplyEffectTickThisTick` returns true while its duration is at least one, so that path can execute repeatedly. `/effect give` itself handles instant-effect duration differently from ordinary seconds-to-ticks conversion. [26.2 artifact, `PotionContents`, `InstantaneousMobEffect`, and `EffectCommands.giveEffect`](https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar)

**Implementation inference:** a generic sustained aura cannot safely treat every registered effect as an interchangeable timed buff. Instant effects need an explicit application meaning. Removing the aura cannot undo health already healed, damage already dealt, or other completed side effects.

## Persistence, offline time, and death

`LivingEntity` saves `active_effects` through `MobEffectInstance.CODEC`, including remaining duration and the hidden chain. Loading reconstructs these entries without subtracting an offline timestamp. `PlayerList.remove` saves the disconnecting player before removing it from the active player list. Duration decreases through effect ticking, not wall-clock elapsed time. [26.2 artifact, `LivingEntity.addAdditionalSaveData`, `readAdditionalSaveData`, `tickEffects`, `MobEffectInstance.tickDownDuration`, and `PlayerList.remove`](https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar)

**Implementation inference:** ordinary logout/login and shutdown/restart preserve remaining native effect time. Time spent offline or with the server stopped is not consumed. An entity that is not being ticked likewise does not run this duration decrement. This differs from Q117, where a disconnected player's Conclave aura continues aging on the running server's simulation clock.

Death respawn is a separate path. `ServerGamePacketListenerImpl` invokes player respawn with the boolean false for death. `ServerPlayer.restoreFrom` copies active effects only in its true branch, used for the inspected non-death return path. Native save persistence therefore does not establish retention across ordinary player death. [26.2 artifact, `ServerGamePacketListenerImpl.handleClientCommand`, `PlayerList.respawn`, and `ServerPlayer.restoreFrom`](https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar)

Fabric attachments can persist separate aura records and optionally copy them across death. Their synchronization can target the owning player. Those facilities store Conclave's data; they do not change native effect merging, create source attribution, or provide an offline timer automatically. [Fabric data attachments](https://docs.fabricmc.net/develop/serialization/data-attachments)

## Implications left for the design

The existing source-owned aura contract requires separate contribution records. Native effect state cannot substitute for those records, their captured revisions, or their lifecycle events.

If an aura grants a native effect that also comes from ordinary Minecraft, the design still needs to specify what owned removal means after merging. Guaranteeing selective subtraction would require additional ownership-aware integration that observes relevant applications and removals before information is lost, or a different supported mapping. Its complexity and compatibility have not been established here.

Milk clearing a native grant does not automatically remove a separately stored aura. Conversely, an aura that keeps reapplying its grant can make the native effect return after milk. Neither behavior is selected by this report. The same separation applies to native death cleanup and Conclave's configured retain-on-death behavior.

No code, schema, or gameplay policy was added. The only created artifact is this research note.
