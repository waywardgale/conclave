# Relic placement and appearance

Status: Q187-Q188 are accepted. These contracts define stationary relic placement, explicit interaction geometry, item or custom-model appearance, and holder feedback. No implementation exists.

[Q189-Q190](carried-relics-and-animation.md) accept optional carried visuals and explicit animation controls.

## Q187: stationary world relics and explicit interaction size

Accepted: make the initial managed relic stationary while unheld. It stays at its placed position until pickup, a lifecycle operation, or cleanup. It does not acquire native dropped-item gravity, water transport, pushing, merging, automatic collection, damage destruction, or age-based despawning. These rules apply to the managed object; ordinary players, NPCs, items, and terrain keep their accepted Minecraft behavior.

The relic does not block movement or projectiles and has no ordinary damage target. A separate server-validated interaction volume lets eligible players aim and Use under Q181. It must not steal ordinary attacks aimed through it. The adapter must verify this distinction; a native entity being noncolliding alone does not establish all of these behaviors.

Add optional definition-level `interaction_size`, with positive finite `width` and `height` in blocks. Omission uses 0.5 for both dimensions. If the block is supplied, require both fields. Width applies equally along the horizontal axes; the volume extends upward from the instance's location by its height. Start with an axis-aligned box and the engine's supported size bounds, without separate bones or mesh-derived targets.

```yaml
interaction_size:
  width: 0.5
  height: 0.5
```

This volume controls pickup targeting and placement clearance, not physical collision or visual scale. Q181's native entity reach, optional narrower reach, line of sight, fresh input, holder identity, and capacity checks still apply. Authors can scale a visual independently, and the editor previews the actual pickup volume alongside it. A large model cannot grant additional reach through an unverified visual mesh.

Initial spawn, explicit reset, direct return, and scheduled respawn use their captured destination exactly. That destination identifies the center of the volume's base. Require its complete interaction volume to fit within permitted arena and world bounds, the required loaded simulation, and unobstructed block space. A stationary relic does not need a supporting floor; authors can deliberately place it above a pedestal or in midair. Do not move an authored destination to an unrelated nearby position merely to make creation succeed.

### Nearby dropping

A player drop or `drop_relic` chooses a nearby supported resting position, rather than throwing the object or freezing it at arbitrary eye height. Search within three blocks of the holder's recorded feet position. Prefer a valid position immediately in front of the holder; otherwise choose the nearest candidate in a stable server order. Keep the candidate search bounded. Require a supporting solid collision surface, space for the whole interaction volume, permitted bounds and chunk readiness, and an unobstructed path for placement from the holder's recorded position. Do not place through a wall or closed floor.

Use the same rule for configured holder-death and holder-disconnect drops, using the captured departure position and facing. Other players and nonblocking relics do not themselves make a position physically occupied. Intersecting relic volumes do not merge objects or permit one gesture to collect several; deterministic aim selection and the existing fresh-input rule still select one instance at a time.

If no nearby resting position is valid, use the accepted return to the captured initial location. The operation then emits `returned` with `invalid_drop` under Q184. This includes an airborne drop with no permitted surface nearby. If home is also unusable, apply Q69's required/recoverable failure handling. Never create a hidden carried duplicate, erase possession without the owned failure path, or count the failure as delivery. Placement preparation and commitment retain Q183's existing contract.

The three-block search is an initial relic-placement rule, independent of player recovery radius, revival reach, and pickup reach. It grants no player teleport or confinement. Configurable throwing, falling physics, magnets, moving platforms, and projectile relics would need registered behavior with its own supported contract; they are not implied by a relic's item-shaped appearance.

Ordinary building or terrain changes after placement remain allowed. If a block later obstructs the volume, the relic remains at its position and may become temporarily unreachable. Do not break the block, push players away, repeatedly recreate the relic, or invoke a new hidden return policy. Removing the obstruction can make it usable again. An explicit reset still needs a valid destination. Required simulation loss and actual resource removal retain their existing error and cleanup behavior.

## Q188: item or custom-model appearance with readable holder feedback

Accepted: require one `appearance` section on a reusable relic definition. Choose exactly one primary source: `item` for the visual of an existing registered item, or `model` for a reusable supported model definition. Permit the accepted positive finite visual `scale`, defaulting to one. Reuse logical resource IDs and captured asset revisions.

```yaml
appearance:
  item: minecraft:amethyst_shard
  scale: 1
```

The item choice renders that item's ordinary model using the native `ground` display context. It creates no inventory stack, adopts no item abilities, and grants no native collectible-item lifecycle. Do not accept arbitrary item-component maps or raw NBT through the appearance field. Omitting an item-model override uses the registered item's default visual configuration. [Q231](model-lighting-bounds-and-item-appearance.md#q231-typed-native-item-model-selection) accepts the optional explicit `item_model` field and its supported consumers.

The model choice reuses Q139's named `model` definition. Extend the installed GeckoLib model adapter to this managed world consumer, with static geometry or an optional `idle` state mapping. Validate support for the actual consumer rather than assuming an NPC renderer can be installed unchanged. NPC equipment attachments and movement, attack, hurt, or death state mappings do not automatically acquire meaning for a relic.

```yaml
appearance:
  model: raid_tools:void_orb
  fallback:
    item: minecraft:amethyst_shard
  scale: 1
```

For a custom model, require the explicit `fallback.item`. A viewer who cannot use that captured model sees this recognizable item visual at the same logical object's current position. This is the authored fallback, not an arbitrary substitute or a newer model under the same ID. The scale applies to either visual. An item-primary appearance rejects a redundant fallback in the initial schema.

Fallback does not satisfy required asset readiness for joining a new attempt. Invalid models, unsupported consumers, and missing dependencies still fail publication. Unexpected presentation failures keep their existing required-versus-cosmetic error handling and bounded diagnostics. An outside viewer with an older active resource set must not receive a forced resource reload to draw this relic. The fallback preserves a visible object when its registered item appearance is available; it does not promise control over an unrelated client pack that deliberately hides or breaks that item.

Use the existing geometry, texture, animation, and dependency validation for supported custom models. Imported idle animation can change the visual pose but cannot move the server interaction target, cause damage, play undeclared sounds, or decide timing. [Q189-Q190](carried-relics-and-animation.md) accept explicit animation actions and carried body attachments as separate consumer contracts. Model support requires implementation and verification; this contract does not claim a tested Conclave renderer.

### Holder feedback

When picked up, remove the unheld world visual and show the holder's existing carried-relic HUD entry. Display the definition's `name`, falling back to its `id`, and a recognizable icon. Default the icon to the registered item's GUI visual or the model's explicit fallback item rendered in that context. Allow optional `display.icon` with the already accepted `{item: ...}` or `{texture: ...}` form, plus optional authored `display.description` text.

```yaml
display:
  icon:
    texture: raid_tools:relics/void_orb
  description: Bring this relic to the altar.
```

Description text has no executable meaning. Preserve the mandatory indication of possession, the selected-relic marker, and accessible drop controls under Q181. The client must not infer delivery rules or reveal hidden conditions from that description. Existing aura indicators remain separate even when `carry_auras` is configured.

Show possession on the holder's own HUD by default. This does not create a whole-team tracker, reveal another player's relic list through spectating, or automatically attach the model to a hand, head, or body. Q189 accepts an explicit carried-appearance setting that preserves native equipment slots and ordinary item use. Authors can use already supported, explicitly targeted presentation to communicate information to the team.

Aiming at a world relic can show its readable name and pickup progress to a player currently eligible for that pickup. Do not send private pickup-filter details or hidden encounter state to explain eligibility. World rendering obeys ordinary visibility and occlusion; this contract adds no through-wall nameplates or outlines. Detailed HUD placement, client input, and optional public labels follow the accepted client design work.

## Related contracts and verification

These contracts build on [managed relic ownership](relic-identity-and-lifecycle.md), [pickup and explicit lifecycle controls](relic-interaction-and-delivery.md), [accepted relic events and carry auras](relic-events-and-carry-auras.md), [model configuration](presentation-controls-and-models.md), and [asset publication](world-locations-and-assets.md). The existing [presentation research](presentation-assets-research.md) and [interaction research](interaction-input-research.md) separate native facilities from the adapter work Conclave still requires.
