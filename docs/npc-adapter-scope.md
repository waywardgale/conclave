# Initial NPC adapters and compatibility inspection

Status: Q214-Q215 are accepted. Q214 selects implementation targets for the initial built-in NPC adapters. Q215 makes installed capability support inspectable in Minecraft and applies independently of the chosen roster. Nothing here claims that an adapter has been implemented or passed gameplay tests.

## Q214: initial built-in NPC coverage

Accepted: target the following native types for the initial release, sharing adapter code where their verified behavior permits it. Cover ground combat, climbing, ranged attacks, flying, swimming, native conversion, splitting, summoning, and passive interaction without starting with the native world bosses.

| Family | Initial native types |
| --- | --- |
| Zombies and conversion | `minecraft:zombie`, `minecraft:husk`, `minecraft:drowned`, `minecraft:zombie_villager`, `minecraft:villager`, `minecraft:witch` |
| Skeletons and conversion | `minecraft:skeleton`, `minecraft:stray` |
| Other ground mobs | `minecraft:spider`, `minecraft:creeper`, `minecraft:enderman`, `minecraft:iron_golem` |
| Splitting mobs | `minecraft:slime`, `minecraft:magma_cube` |
| Summoning and flying | `minecraft:evoker`, `minecraft:vex`, `minecraft:blaze` |
| Aquatic combat | `minecraft:guardian` |

This is a concrete coverage target, not a shipped encounter roster or a claim of universal controls. A compatible entity's native behavior remains its default. Authors can configure supported controls and custom appearance while retaining the selected native entity's gameplay. A passive type does not gain combat behavior merely because a manifest sets melee damage or equips a weapon.

Keep the Ender Dragon and Wither outside the initial built-in roster. They require dedicated adapters for their combat, AI, native boss bars, and lifecycle/world effects. Other native types and installed-mod types can become supported through additional verified adapters. The public YAML continues to name the native type with `npc`; authors do not select Kotlin classes or generic fallback adapters.

### Admission and capability requirements

Every supported type needs verified creation, ownership, current-body tracking, actual-death accounting, default reward suppression, and cleanup/recovery under the accepted contracts. The basic integration must also account for that type's supported native creation and removal paths. Merely being registered, extending `Mob`, rendering successfully, or having a health attribute is insufficient.

Advertise configuration and actions per capability. Health/stats, movement, `wander`/`disabled`, targeting, vulnerability, health floors, equipment, physical scaling, custom models, and special rendering layers each require their own compatible behavior. An adapter can support a native NPC while rejecting a particular optional control, as already accepted in Q63. Native behavior with an omitted field is not proof that Conclave can override that behavior safely.

The coverage target includes the known integrations exercised by these families: conversion destinations, native reinforcements, riders created during spawning, split children, summons, equipment-dependent attack goals, and attacks outside ordinary melee. Apply Q206-Q213's ownership, definition selection, transfer, and notification rules only through verified relationships. A transformation of an unrelated world victim does not make that victim an owned descendant.

Preserve ordinary Minecraft world interactions under Q103. Accepted ownership does not silently suppress an explosion, enderman block interaction, villager trade, or damage to an outsider. Conclave retains its explicit cleanup of owned NPCs and separately supported world-change resources. The adapter must report applicable native side effects to authors without inventing blanket arena protection or arbitrary world rollback.

Treat this roster as work required before declaring initial support, with compatibility results recorded against the actual installed version. If implementation reveals a required contract cannot be met for a targeted type, report the limitation and revisit that scope decision. Do not silently remove it from the promised target list or enable it with known broken ownership behavior.

This decision does not settle authored age, profession, native size/variant, taming, mounts, or breeding controls. Ordinary supported native initialization retains its existing contract, including any native-created children that the adapter must track. Explicit selectors for such state need typed fields and validation before authors can configure them. [Q216-Q218](npc-lifetimes-and-variants.md) accept self-destruction outcomes, ordinary despawn prevention, and initial variant fields. Q216 classifies a verified committed creeper self-destruction as defeat; selecting a type still requires verifying its adapter integration. This decision adds neither player avatars nor arbitrary collision meshes or new native entity types.

## Q215: inspect compatibility in the in-game NPC editor

Accepted: add an NPC compatibility view to the existing authoring screen and native-type picker. It uses the server's installed adapter catalog and the selected draft definition. Authors can see why a type or field is usable without leaving Minecraft or trying an invalid encounter.

Show supported native types by default, searchable by readable name and registry ID. Provide an option to include registered unsupported types, with a reason such as no compatible adapter or unavailable integration. A type absent from the server's native registry is a missing identifier, which differs from a registered type without Conclave support. Preserve unsupported references in editable drafts and point to their fields; do not replace them automatically.

For a selected type, show its supported capabilities and relevant bounds, units, defaults, and limitations. Separate native behavior from configurable controls. For example, native ranged attacks do not imply that `stats.melee_damage` scales every projectile. Equipment gameplay support remains distinct from whether the selected custom model renders that equipment.

Evaluate the actual definition and its reachable requirements, including requested AI modes, stat actions, auras, equipment, model/attachment features, native conversions, descendant mappings, and applicable arena bindings. Distinguish three results: supported as configured, incompatible as configured with a precise reason, and incomplete draft awaiting required information. Incomplete or incompatible content cannot publish merely because its base type appears in the supported list.

The result should identify the affected field and explain a concrete resolution, such as selecting a supported slot or changing a model binding. Show the integration's name/version in expandable technical details when useful for diagnosis. The normal authoring flow remains native IDs, YAML fields, readable descriptions, and compatible choices. No new capability IDs or adapter-selection field are required in manifests.

Use the same authoritative schemas and validators as publication and Test draft. Refresh the report when the draft changes. Preserve its draft/version context so an old report is not presented as validation of a later edit. Missing assets or incomplete arena placement remain their own diagnostics, rather than changing the base entity's compatibility claim.

Compatible previews remain harmless under the existing preview contracts. Opening this view never spawns a live NPC or starts a gameplay test. Authors use the existing explicit Test draft operation for real behavior. A successful test attempt does not automatically register a new adapter, certify every capability, or install client/server code.

Keep the view permission-filtered for authorized authors and provide its ordinary human-readable and machine-readable capability information through the existing schema/documentation facilities. Do not disclose private encounter runtime state or add an external CLI, separate service, or unsupported override switch. Final screen layout follows the overall editor design.

## Related contracts

These contracts refine [capability-based NPC support](npcs-and-spawning.md), [AI controls](npc-and-boundary-policies.md), [stats and equipment](npc-stats-and-combat.md), [custom models](npc-model-presentation.md), [lineage](npc-lineage.md), and [in-game authoring](in-game-authoring.md). [NPC source research](npc-combat-research.md) records engine evidence separately from unimplemented Conclave support.
