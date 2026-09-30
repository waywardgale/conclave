# Model lighting, visual bounds, and item appearance

Status: Q229-Q231 are accepted. They extend the accepted model and item consumers with explicit visual controls. The contracts are independent: lighting and bounds concern reusable GeckoLib models, while `item_model` selects a native item definition. No implementation exists.

## Q229: world lighting, full brightness, and emissive regions

Accepted: add `lighting: world|fullbright` to a reusable `model` definition, defaulting to `world`, and an optional `emissive` texture ID. Use these fields for supported NPC and managed-relic model consumers, including a carried relic's existing world visual.

```yaml
model:
  id: vault_guardian
  type: geckolib
  geometry: my_raid:vault_guardian
  texture: my_raid:entity/vault_guardian
  lighting: world
  emissive: my_raid:entity/vault_guardian_glow
```

`world` uses the consumer's normal supported environment lighting. `fullbright` supplies maximum block and sky light for the model's base surface. It does not promise removal of every directional-shading effect, introduce a custom shader, or make a model emit light into the surrounding world.

`emissive` supplies a separate texture overlay for luminous regions, such as eyes, runes, or seams. It uses the same geometry and UV layout; transparent pixels contribute no overlay. The supported adapter renders that layer without ordinary darkness from environment lighting. The author names the texture explicitly; no filename convention silently activates it. Validate texture dimensions, format, UV compatibility requirements, and required resources under the supported import profile. Omitting it draws no additional emissive layer. It can coexist with either base lighting mode.

Apply the chosen lighting and overlay to the model's own geometry. Real hand items and other separately submitted native equipment retain their own rendering behavior. The model cannot change a sword's item definition or restore an unsupported native armor layer through its lighting field. A native NPC fallback or a relic's item fallback keeps that consumer's own supported lighting; do not overlay custom-model emissive pixels on unrelated fallback geometry.

Preserve depth testing, ordinary occlusion, invisibility, and the consumer's audience and visibility permissions. Full brightness is distinct from Minecraft's glowing outline, bloom, or dynamic world lighting; none is implied by these fields. Visible luminosity does not reveal a private clue to an unauthorized viewer. Verify the relevant entity and managed-object paths separately, with disclosed platform limitations where rendering differs.

Capture these fields and texture dependencies with the model revision. The in-game preview offers a dark scene and ordinary lighting to make the result visible to an author. Changes apply through the existing publication policy; no runtime `set_lighting` action, arbitrary material map, or animation-driven gameplay is added.

## Q230: visual bounds separate from gameplay size

Accepted: calculate default visual bounds from the supported model's rest-pose geometry, and allow an explicit `bounds` block when animations or attachments extend farther. Keep these visual bounds separate from physical collision, pickup geometry, reach, and spawn placement.

```yaml
bounds:
  width: 4
  height: 6
  depth: 4
  offset: {x: 0, y: 3, z: 0}
```

The block belongs to the reusable `model` definition. Require all three dimensions as positive finite numbers in model-local block units, before physical size and appearance scaling. Width is the X extent, height the Y extent, and depth the Z extent. Y points up. The optional offset identifies the box's center relative to the consumer adapter's model origin shown in the preview, with all three coordinates required when supplied. Omitted offset is zero. The example covers six blocks upward from that origin. Negative offsets are valid within the supported finite bounds.

Derive the default box using the actual supported geometry, including the bone hierarchy, initial bone/cube rotations, cube inflation, and the adapter's coordinate conversion. Do not mistake raw cube coordinates for world coordinates. This is Conclave adapter work; GeckoLib's parsed `visible_bounds_*` metadata is not itself a verified culling implementation. Do not silently reinterpret that imported metadata as this authored block. If a supported geometry cannot yield a valid finite default, require an explicit compatible box or reject the unsupported import with a clear error.

An explicit box replaces the geometry-derived envelope for the custom model. For an NPC, conservatively include its current native rendering box as well. A fallback uses its own consumer's appropriate bounds. Transform the custom envelope with the same position, orientation, physical scale, appearance scale, and carried attachment transform as the visual, applying each scale once. Use a conservative world-aligned enclosure for the camera test. Do not recalculate the configured envelope only from whichever pose happened to be rendered most recently.

Bounds decide whether the renderer should consider drawing a consumer near the camera's edges. They are not a mesh-cropping region, an invisible wall, a damage volume, or a new interaction target. Enlarging them does not extend server tracking, simulation, the client's render distance, visibility permissions, or through-wall visibility. Both entity and managed-object consumers require an explicit supported culling path.

Rest-pose bounds do not certify every animation or equipped-item pose. Authors can enlarge the box to cover the clips and attachments they use. Show the box, model, and actual gameplay dimensions together in the in-game preview. Allow previewing selected clips and equipped items; warn when an observed pose exceeds the configured box without claiming that a few preview samples prove all possible poses fit.

Validate configured and transformed values against advertised renderer limits. Reject zero/infinite dimensions and a request to disable culling through special values. Keep the default automatic for ordinary static geometry, while the explicit form covers deliberate oversized animation. Capture the choice with the model revision; no runtime bounds-mutation action is added.

## Q231: typed native item-model selection

Accepted: add an optional `item_model` resource ID beside a registered base `item`. Support it on configured NPC equipment, item-based relic appearances, and the existing item-icon forms. Keep bare item IDs and existing `{item: ...}` forms valid.

```yaml
equipment:
  mainhand:
    item: minecraft:iron_sword
    item_model: my_raid:ritual_blade
```

```yaml
appearance:
  item: minecraft:amethyst_shard
  item_model: my_raid:void_orb
```

```yaml
icon:
  item: minecraft:amethyst_shard
  item_model: my_raid:void_orb
```

These are independent illustrative fragments, not a bundled encounter. On equipment, `item` is required in the configured map and existing supported count, enchantments, and typed properties retain their separate contracts. On a relic or icon, use the existing item branch; reject `item_model` alongside a GeckoLib `model` or texture-only icon. A raw string cannot carry sibling options, so the editor expands it to the configured item form when needed.

The resource is a native client item definition, resolved from `assets/<namespace>/items/<path>.json`. It is a different resource kind from a Conclave reusable `model`, a geometry file, or a texture. The picker shows compatible item definitions and their normal display contexts. Do not introduce custom-model numbers, arbitrary native component maps, scripts, or a requirement to register a new item for each appearance.

### Behavior and supported consumers

The base item still supplies native gameplay. Choosing an iron sword with another model does not change damage, durability, reach, enchantments, or equipment-slot behavior. Apply the property to the actual initial equipment stack. Later pickup and equipment replacement follow their existing native rules; do not continually overwrite a newly acquired stack's model. Q225's reward policy still decides whether an equipped item drops.

For managed relics and icons, use a visual stack only, with no inventory object or extra world drop. Keep the accepted consumer contexts: ground for an unheld item relic, fixed for its carried visual, and GUI for an icon. NPC hand attachments use their documented native hand contexts and actual current stacks. Native item definitions can deliberately vary by context and supported state, so preview the selected consumer rather than promising a single static appearance everywhere.

Changing `item_model` does not replace a worn armor or animal-equipment mesh, which uses its separate native equipment asset. It does not imply GeckoLib armor fitting or change which native slots exist. Those consumers require their own explicitly verified support.

### Resources, fallback, and persistence

Capture and validate the complete supported client-item dependency graph under the existing revision and publication policy. A model ID alone does not freeze nested item definitions, geometry, textures, or contextual renderer dependencies. Diagnose unsupported item-definition types and consumers before publication rather than displaying a native missing-model placeholder as success. Authors keep logical IDs; revision-specific internal resource naming belongs to asset integration.

When a viewer cannot use the captured custom item model, render that registered base item's default model selection through an explicit Conclave fallback. For real equipment, preserve the current stack's other properties on a temporary rendering view; do not mutate the real inventory or remove enchantments. A managed visual retains its own supported visual configuration. This is a presentation fallback, not permission to use a newer custom revision under the same ID.

A fallback never satisfies required presentation or new-attempt asset readiness, forces a resource reload, or proves the viewer saw an authored symbol. Preserve visibility and privacy rules. A registered base item may itself rely on installed resources; this fallback is not a guarantee against unrelated broken or deliberately altered client packs. Retain the existing plain `fallback.item` for a GeckoLib relic, without adding another custom item-model dependency to that fallback in this first shape.

A legitimate equipment drop retains its item-model property and ordinary item behavior. Do not delete, strip, or restyle that real item merely because its attempt ends or a new revision is published. Such items can outlive their NPC, move into storage, and survive a restart. [Q232-Q233](durable-item-appearances.md) accept durable appearance archival and ordinary stack identity. Their persistence integration must be implemented and verified before this consumer can ship. This contract does not treat the active-attempt reference count as sufficient or claim the persistence integration is implemented.

## Related contracts

These contracts extend [reusable model definitions](presentation-controls-and-models.md), [physical size and native equipment](npc-stats-and-combat.md), [NPC model attachments and fallback](npc-model-presentation.md), [relic appearance](relic-placement-and-appearance.md), [carried relics](carried-relics-and-animation.md), and [death rewards](npc-death-rewards-and-descendant-events.md). [Primary-source research](presentation-assets-research.md#model-lighting-visual-bounds-and-native-item-models) distinguishes available native/library facilities from the Conclave adapters still required.
