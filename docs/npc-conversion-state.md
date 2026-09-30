# NPC conversion state and descendant defaults

Status: Q208-Q209 are accepted. They follow accepted Q206-Q207 ownership and membership. Q208 concerns the same logical NPC receiving a replacement body. Q209 concerns separate native children. No implementation exists.

## Q208: preserving state through native conversion

Accepted: transfer the continuing NPC's current Conclave state to a supported replacement, without replaying initial spawn configuration. Preserve its captured definitions, current authored controls, original stat-reset baseline, aura records, and reward policy. A native conversion does not heal the NPC, restore its original equipment, refresh an aura, or restart an encounter mechanic.

### Health and base stats

Keep current health in health points, clamped only when the replacement's final supported maximum is lower. Do not fill a newly constructed body's health or preserve health percentage by increasing current health. Conversion adjustment is not `heal`, damage, death, or defeat and emits none of those notifications. Preserve current absorption within the replacement's supported bounds without granting a new absorption pool merely because effects or modifiers are attached again.

For each stat explicitly configured in the NPC definition or subsequently controlled by `set_stats` or `reset_stats`, transfer its current resolved native base value. Preserve the original spawn-captured reset baseline. Attributes not controlled by Conclave use the destination's native conversion/default behavior. Do not copy an arbitrary attribute map, duplicate native modifiers, or turn all of the source type's defaults into authored overrides.

An already configured speed percentage has produced a concrete base movement value. Transfer that value without multiplying by the percentage again or silently recomputing it against the destination's base. For example, if configured speed resolved to a base value of 0.3 before conversion, it remains 0.3 on a compatible destination. A later explicit `set_stats.speed` uses the current type's supported base under Q141; `reset_stats.speed` restores the original spawn-captured numeric base under Q154. The editor must distinguish those operations.

This requires compatible attribute meaning and ranges. A walking speed must not silently become a flying speed. Validate destination support, current authored values, reset baselines reachable through authored actions, and active constraints. Unknown or incompatible transfer is an adapter error under Q69; it is not permission to clamp an authored base stat or drop it silently. External modifiers retain their supported native transfer behavior, while Conclave moves only its own aura modifiers once.

Retain the current `health_floor` setting and its already-reached flag. Percentage floors evaluate against the final current maximum under the existing rule. Conversion does not reinstall or rearm a reached floor. If an unreached setting becomes satisfied by a supported downward health clamp, record its first threshold crossing and queue the normal single `health_floor_reached` notification. A maximum-health change incompatible with an absolute floor follows the existing constraint/error contract; it cannot silently remove the floor.

### Current equipment, auras, and controls

| State | Accepted transfer |
| --- | --- |
| Equipment | Move current real equipped items into compatible native slots, preserving their current contents and properties. Never recreate the original loadout, restore lost items, or duplicate transferred items as drops. |
| Conclave auras | Retain holder-level applications, contribution/source identities, stacks, remaining durations, periodic cadence, and ownership. Move owned modifiers once. No apply, refresh, removal, expiry, or extra periodic event is caused merely by conversion. |
| Runtime controls | Retain the current authored AI mode, targeting policy, vulnerability, explicit confinement/home, and supported physical-size override. Clear obsolete native paths and pending attacks as required to apply those controls on the new body. |
| Definition and rewards | Keep the original pinned Conclave NPC definition, authored name, loot/experience/equipment-drop policy, and once-only death accounting. The current native type changes; the definition is not rebound to whichever manifest happens to name the destination type. |
| Omitted native properties | Let the supported native conversion determine ordinary state not controlled by Conclave, including native status-effect transfer. Do not snapshot and restore every field or assume arbitrary mod state can be copied. |

The aura's logical holder continues with the NPC; the producer identity and cleanup owner do not become the new body's UUID. Source references in historical events and already captured native targets retain Q206's stale-body behavior. Moving the aura does not retroactively give old damage sources the new body's aura multipliers. A continuing contribution is not a new contribution, and its identity cannot be reused for an unrelated holder.

Native status effects remain separate from Conclave auras under Q158. Do not implement aura transfer by repeatedly applying native potions or replaying their instant effects. A destination that cannot support an existing required aura effect is incompatible with that transfer.

### Presentation and physical interactions

Retain the configured custom model, visual scale, attachments, and boss-bar configuration only through a compatible destination adapter. The health bar follows the current body with the same audience policy and no duplicate native/Conclave bars. A viewer missing the captured model uses the current destination type's native fallback under Q163. An unsupported model consumer is not an asset-missing fallback case.

Explicit animation playback bound to the old body ends for that target. It does not restart on or silently retarget the replacement, and an override shared across several NPC targets ends only for the replaced target. The new body's automatic model state follows its current supported state. An author may start another explicit animation through a later supported event/rule; animation completion still has no gameplay authority.

Other presentation bound to a concrete old body keeps its existing source-removal semantics. Permitted finite output can finish from its captured historical origin; body-bound loops and queued work cannot attach themselves to the replacement. Independent location-based or direct presentation keeps its own lifetime. None of this widens an audience or rewrites an old speaker snapshot.

Cancel unfinished physical holds against the old body. Completed interaction credits and mechanic progress remain committed. Later group-based input can use the new body only through a fresh validated interaction. Delayed packets and callbacks must not resurrect an old hold or mutate the replacement.

### Adapter boundary

Transfer belongs to the verified single-conversion operation, not to a broad rescan of nearby NPCs. Validate knowable source/destination compatibility before publication and complete required runtime checks before accepting the handoff. Retain ownership of any verified replacement during failure cleanup. Do not claim rollback of arbitrary native/mod callbacks or a successful transfer merely because a pre-insertion notification fired.

Do not add an author-facing transfer mask, raw entity field copy, or alternate health pool for this initial contract. [Q210](npc-conversion-events-and-descendant-definitions.md#q210-a-converted-event-for-npc-groups) accepts conversion notifications and their exact typed payloads. This contract does not introduce an authored `transform` action.

## Q209: native descendant settings and rewards

Accepted: new native living descendants use their native creation setup. Conclave adds verified lineage and scope ownership, applies its no-death-rewards default, and does not automatically clone the parent's authored encounter configuration.

This means the native creation path determines the child's species, size, health, AI, equipment, native effects, and other supported initial state. Native code may already copy some parent state, such as a no-AI flag during splitting. Leave such supported native behavior intact rather than forcibly resetting every child to generic defaults. Conclave must not accidentally inherit extra settings merely because native code copied tags or another internal marker.

Do not automatically copy the parent's Conclave base-stat overrides, `reset_stats` history, aura contributions, custom model, boss bar, authored targeting/confinement policy, health floor, vulnerability lock, or runtime `set_ai` record. These belong to the parent. A separately created child is not a converted continuation under Q208. A native copied flag can preserve a visible effect of a parent's control, such as no-AI, without adopting the parent's ongoing Conclave control record. Later parent/group changes do not select the ungrouped child. The adapter must distinguish native fields from copied Conclave bookkeeping.

Disable child item loot, experience, and equipment drops by default, including when the parent opted into rewards. Do not multiply a boss's reward table across split children or summons. Killing the child remains an actual native death but gives no defeat credit to the parent's group under Q207. Cleanup produces neither reward nor defeat. Ordinary items already legitimately dropped before cleanup retain their accepted world lifetime.

Apply the same defaults recursively to supported descendants, within the existing entity/work budgets. They keep their owner's captured revision and cleanup boundary even if the original parent dies. Native expiry does not become a missing required group member, since the descendant never joined that group.

These defaults add no required YAML fields. [Q211](npc-conversion-events-and-descendant-definitions.md#q211-configuring-native-children-with-npc-definitions) accepts explicit descendant configuration; nothing here makes parent reward opt-in such an option. Authors can already use explicit YAML spawning when they need a named, fully configured NPC and deliberate group membership. The initial adapter list must disclose which native child-creation paths it can own and clean up.

## Related contracts

These contracts extend [NPC lineage](npc-lineage.md), [base stats and equipment](npc-stats-and-combat.md), [stat changes and health floors](combat-events-and-controls.md), [aura contribution ownership](aura-actions.md), [aura modifiers and native effects](aura-effects.md), [physical input identity](interaction-input-and-progress.md), [model fallback](npc-model-presentation.md), and [presentation lifetime](presentation-controls-and-models.md). [Source research](npc-combat-research.md#native-conversion-descendants-and-special-boss-lifecycles) establishes native paths and hook limitations, not a completed implementation.
