# NPC endings, despawn prevention, and native variants

Status: Q216-Q218 are accepted. They refine the initial NPC coverage with native outcome classification, distance-despawn prevention, and explicit initial variants. No implementation exists.

## Q216: committed native self-destruction counts as defeat

Accepted: count a verified, successfully completed native combat self-destruction as one defeat of that group member. The initial example is a creeper completing its explosion. This explicitly refines Q32/Q64's actual-defeat contract for a native combat ending that removes the NPC without ordinary lethal-damage processing.

Require adapter evidence that the self-destructive action committed and permanently ended that NPC. Merely starting a fuse, playing an explosion effect, receiving a removal callback, or losing an entity reference is insufficient. If ordinary lethal damage defeats the same NPC first, credit that actual death once; a later removal callback cannot add another defeat.

Keep ordinary death and supported self-destruction distinct in the internal outcome record. A `defeat` objective counts either by default under its existing all-members or explicit-count requirement. Attribution-specific requirements still need a real qualifying identity; self-destruction does not invent a killer or award credit to the nearest player. [Q219-Q220](npc-defeat-events-and-requirements.md) accept author-facing outcome events and cause filters.

| Ending or change | Defeat result |
| --- | --- |
| Actual native death | Existing one-member defeat semantics. |
| Verified native combat self-destruction | One member defeated after commitment. |
| Native conversion | Same logical member continues under Q206; no defeat. |
| Ordinary despawn, expected departure, administrative removal, or cleanup | No defeat. |
| Unknown loss or required simulation failure | Existing technical/recoverable error handling; no inferred defeat. |

This does not run a fake native death, set health to zero, manufacture a `damaged` event, or generate item loot, experience, or equipment drops that the self-destruction path does not normally produce. Q97's authored NPC death rewards remain attached to actual native death. Collateral explosion damage and ordinary world drops retain their own native behavior and accepted ownership rules.

For Conclave holder state, the self-destroyed NPC is terminal. Apply the existing aura death policy once: remove by default, or retain explicitly death-persistent contributions with their remaining duration and ownership. Retention does not keep a living body, enable periodic damage/healing, accumulate missed pulses, or retarget a later NPC. Owning-scope cleanup still removes its own records. Conversion continues to use its separate nonterminal transfer contract.

Stop body-dependent interactions and presentation under their existing terminal/source-removal rules. Required group accounting can complete while the resulting native explosion has independently affected other entities; resolve same-tick encounter outcomes under the already accepted success/failure precedence.

`vulnerable: false` and `health_floor` protect against supported damage-driven health loss. They do not promise to stop a native self-removal. An adapter advertising `wander` or `disabled` must separately meet its existing promise to stop autonomous attacks and queued AI attacks, including relevant fuse behavior. Manual/native interaction and any future authored ignition control retain their own contracts.

## Q217: owned NPCs resist ordinary distance despawn

Accepted: prevent ordinary distance-based and random despawning for Conclave-owned NPCs during their active ownership. Apply this to explicitly spawned members and verified native descendants, and preserve it across supported conversion. It is a framework lifecycle default and needs no extra YAML field.

An author should not lose a boss or required wave member merely because every participant moved away. This changes only the ordinary distance/random despawn behavior of owned NPCs. Their native AI, damage, movement, targeting, and authored cleanup scope retain their existing meanings. Unrelated world mobs retain normal rules.

Keep native limited-life behavior, actual death, committed self-destruction, peaceful-difficulty removal where applicable, explicit administrative removal, and scope cleanup effective. Persistence does not cancel a vex's native lifetime, freeze age, grant invulnerability, or turn an ended NPC into a permanent world entity. The native vex timer begins recurring starvation damage rather than guaranteeing immediate disappearance. Supported damage protection can affect that damage; do not add a hidden forced-removal timer to compensate.

Use the selected adapter's verified persistence integration before accepting creation or conversion. A copied flag alone is not proof that every entity-specific despawn path respects it. The compatibility view must identify known limitations; an adapter cannot advertise this baseline while leaving ordinary distance/random loss active.

Persistence does not expand arena chunk retention, force every region containing a wandering NPC to remain loaded, or add confinement. Required simulation loss and an unexpectedly removed required member still follow Q69. A known removal reason, including a server-policy change, does not become defeat just because it was intentional. Existing documented recoverable handling may apply; otherwise stop the affected attempt rather than silently shrinking its requirement or waiting forever for a permanently missing member.

Native descendants outside named groups retain Q209's expected lifetime behavior. An ordinary supported expiry is not a missing required group member. If a native limited-life NPC was explicitly spawned into a group and actually dies through its supported native damage/death path, it is an ordinary death with that group's existing attribution and defeat rules.

Do not use player proximity as ownership proof or restore a removed NPC by guessing its old type and location. Cleanup still removes only verified owned resources and produces neither defeat credit nor death rewards.

[Q265](movement-and-simulation.md#q265-follow-owned-npc-identity-while-simulation-is-available) accepts the remaining travel boundary: continue verified owned identity while supported simulation is available, interrupt on required loss, and retain optional unloaded descendants for scope-aware cleanup. It adds no moving retention footprint or automatic confinement.

## Q218: a typed block for initial native variants

Accepted: add an optional `variant` block to an NPC definition for supported initial native state, with keys validated for the selected base type. Keep the existing top-level percentage `size` for physical scaling and `appearance.scale` for additional visual scaling.

Start with these typed fields:

| Field | Initial supported holders | Meaning |
| --- | --- | --- |
| `baby` | Supported zombie-family types and villager | Boolean initial baby/adult state. |
| `slime_size` | Slime and magma cube | Integer native size, 1 through 127 subject to narrower verified adapter and placement/work limits. |
| `charged` | Creeper | Boolean initial charged state. |

```yaml
npc:
  id: large_slime
  npc: minecraft:slime
  variant:
    slime_size: 4
  stats:
    health: 120
```

This fragment creates a native size-four slime with an authored maximum of 120 health points. Native slime size influences its ordinary attributes and splitting behavior; it is not an alias for appearance scale. The existing top-level `size: 150%` would additionally apply supported physical scaling to that native form. Validate the final dimensions and placement, including combined scaling, before admitting a configured spawn.

Reject unknown keys, unsupported type/field combinations, non-integer native sizes, and out-of-range values. Do not silently clamp or coerce a native-size integer into a percentage. An omitted field retains supported native initialization. An empty block adds no variant override. This is a registered per-type schema, not an arbitrary NBT/component map or a second base-type selector.

### Initial configuration order

For an explicit Conclave `spawn`, including an anchor spawn or `into` reinforcement, complete supported native initialization, apply explicit initial variant fields, then resolve supported authored base stats, equipment, physical scale, and presentation before successful admission. Authored stats take precedence over native variant-derived base defaults. A percentage speed uses the selected native form's supported base movement value before the authored override; it does not multiply the final value repeatedly.

Capture `reset_stats` baselines after this complete initial configuration. Changing the native variant during initialization is not a runtime stat-change or healing event. The spawned NPC begins at the accepted final configured maximum health. Adapters must account for native methods that also write attributes or health so they do not overwrite the authored result afterward.

These are initial values, not permanent locks. Native aging, lightning charging, conversion, and other supported gameplay may change them later. A baby villager follows native growth; a baby zombie does not acquire a new Conclave growth timer. Do not repeatedly restore a baby's age or erase a later charge because the starting manifest differs. No runtime `set_variant` action or age-freezing setting is added in this initial block.

Setting `charged` must change only the supported initial charged state. Do not simulate a lightning strike to obtain it or introduce damage, sounds, or other lightning side effects during configuration. The adapter may need a narrow native integration where the state has no suitable public setter; that is not an authoring escape hatch for arbitrary entity data.

### Conversion and descendant reuse

The `variant` block configures explicit Conclave spawning only. Native single-conversion outcomes keep their supported native form changes while Q208 transfers continuing Conclave state. Do not reapply the original initial variant to the new body or turn a naturally aged NPC back into a baby.

Native children keep the creation state selected by their native path under Q211. A definition selected through `descendants` may have an initial `variant` for its independent explicit-spawn uses, but that block is not reapplied to native children. Other accepted child configuration, including explicit stats and equipment, keeps its existing behavior. This permits a slime definition to select itself for descendants while its children continue halving their native size instead of returning to the parent's authored starting size.

Make this context distinction visible in compatibility inspection and preview: initial variant for explicit spawning, native-created form for descendant use. Keep the field in the YAML rather than silently deleting it, and never claim a child's starting form was overridden by a field that does not apply in that context.

Keep villager profession, biome appearance, trading level/offers, taming, authored riders/mounts, and other species-specific properties outside this initial variant vocabulary. Their ordinary native behavior remains available. Additional controls need typed capability contracts rather than exposing all native fields through this block.

## Related contracts

These contracts refine [actual defeat and group membership](npcs-and-spawning.md), [NPC rewards and AI](npc-and-boundary-policies.md), [lineage](npc-lineage.md), [native-child configuration](npc-conversion-events-and-descendant-definitions.md), [aura death policy](runtime-semantics.md#q65-aura-death-behavior-stacks-and-display), and [initial adapter coverage](npc-adapter-scope.md). Source evidence belongs in [NPC research](npc-combat-research.md), separate from these unimplemented policies.
