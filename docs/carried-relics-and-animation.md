# Carried relic visuals and animation actions

Status: Q189-Q190 are accepted. These contracts define optional carried presentation and explicit animation targeting using the accepted relic identity, appearance, model, and playback rules. No implementation exists.

## Q189: optional carried visuals without occupying equipment slots

Accepted: add optional definition-level `carried_appearance`. Omission keeps Q188's holder HUD without drawing a carried world model. Providing the block draws the same captured item or model used by the relic's `appearance`, including its explicit item fallback when applicable.

```yaml
carried_appearance:
  attachment: back
  scale: 0.75
  offset:
    right: 0
    up: 0.2
    forward: 0
```

Require `attachment`, initially `back` or `above_head`. Back follows the center of the player's native upper back and its body pose. Above-head stays above the current native head position, remains upright, and follows body facing rather than camera pitch. The adapter supplies documented neutral attachment positions and previews them on supported player poses. Neither choice reserves or replaces an equipment slot, changes the player's pose, or turns the relic into an inventory item.

Allow optional positive finite `scale`. It inherits `appearance.scale` when omitted and replaces that visual scale when supplied; the two values do not multiply. Allow fixed `offset` components `right`, `up`, and `forward`, in blocks relative to the selected attachment frame. Omitted components default to zero. Validate finite values and the adapter's supported bounds. These are authored constants, not formulas or arbitrary model-property paths. The preview shows the axes and the resulting position.

Start with the same appearance and model revision in both unheld and carried views. Do not add another item/model declaration or separate fallback under `carried_appearance`. Reusable models remain shareable, while this block controls where one relic is drawn. Native item visuals use the `fixed` display context for this attachment, separate from the unheld `ground` and HUD `gui` contexts. Custom model geometry uses the supported attachment transform without acquiring native player equipment layers.

Render every held instance whose definition enables a carried visual, independently of HUD selection. Multiple objects may overlap if authored at the same attachment and offset; do not silently hide one, add automatic orbiting, or move the selected relic into a hand. The editor can preview several instances. Each visual disappears when that exact carry ends, and stale updates cannot reattach a previous holder's copy.

### Viewers and private information

Enabling this block deliberately makes the carried object's appearance ordinary visible world information, including to nearby outsiders who can see its holder. Preserve normal occlusion, render distance, and the holder's visibility rules. Do not draw through walls or reveal an invisible holder through an independent relic layer. This does not disclose private aura lists, role assignments, pickup filters, or expected pattern answers.

The holder can see the attachment in third person. Hide a camera subject's own attachment in first person, including when spectating that subject, so it cannot cover the view or replace the ordinary hand renderer. Other entitled world objects remain visible. This setting never grants a watched player's private HUD; the accepted spectator policy still controls that information separately.

Keep the mandatory holder HUD whether or not the attachment can be drawn. Respect the accepted model fallback for a viewer lacking captured assets. Unsupported player-model replacements or rendering failures use the documented cosmetic/required error policy and diagnostics; do not replace another mod's whole player renderer merely to force an attachment. Native player variants and ordinary poses need explicit integration verification.

There is no separate audience selector on this initial definition-level attachment. Shared relic definitions cannot implicitly capture an encounter's private roles or area bindings. Authors who need private clues use the accepted audience-controlled presentation capabilities. A future instance-bound private attachment would need its own explicit binding contract. Hand-replacing relic weapons and player-pose overrides are also separate capabilities.

## Q190: explicit animation targets and logical playback

Accepted: give `play_animation` exactly one target form: `group` for current living members of a bound NPC group, `target` for a typed event reference to one living supported NPC, or `relic` for one created runtime relic instance. Require `animation`, the exact clip name in each target's captured model, and an explicit `audience` using the existing presentation selector.

```yaml
play_animation:
  id: orb_charge
  relic: north_orb
  animation: animation.orb.charge
  loop: true
  audience:
    from: participants
```

The clip belongs to the target's model definition, not a globally looked-up resource with a coincidentally matching name. This action does not swap models. A native-item-only appearance cannot play a GeckoLib clip, and a native fallback cannot stand in for that animation. Reject unsupported target kinds, missing clips, or incompatible model capabilities before publication where known, and validate the resolved live targets before changing their current playback.

An NPC group resolves once at invocation. Later reinforcements do not join an old playback. Each selected member must support the requested clip; a heterogeneous group does not silently skip a member whose definition cannot animate. A valid created group with no living members is an empty selection and causes no playback. An uncreated required group or relic remains unavailable under the existing required/recoverable action policy. A relic must currently be available or held; delivered or absent instances do not accept a new animation.

Allow `loop`, default false, and positive finite `speed`, default one, within supported playback bounds. The action's loop option controls repetition, even when the imported clip declares another default. Require a positive finite clip duration. A one-shot plays one pass and relinquishes its override; it does not hold the last frame indefinitely. Looping requires an explicit playback `id`, as under Q138. Omitted IDs on one-shots still receive internal ownership for cleanup.

Use the existing phase or explicit encounter playback ownership and `stop_presentation`. Do not introduce a separate `stop_animation`, player-persistent playback, or executable animation controller. Additional blending and bone-layer controls are outside this initial action shape; an author can supply supported animation data and choose its playback speed without changing code.

### Replacement and audiences

Keep one explicit full-body animation override per target. A newer admitted invocation replaces that target's older override, regardless of whether their audiences differ. Recipients of a removed override return to the target's automatic state if they are not selected for the new override. Ordinary appearance remains visible under its own world-visibility contract; the explicit clip is sent only to its permitted audience.

When only one member of an older group playback is replaced, its other members continue. Stopping the older playback affects only targets still owned by that playback. Reusing the same named playback ID retains Q138's stronger replacement rule: stop that ID's old playback and replace its complete target set. Old delayed stops never cancel a newer playback generation.

Resolve audience identities once at invocation, retaining permission and spectator checks. A radius filter on this action uses each actual target's position, including the current holder position for a held relic; evaluate it separately for each group member. Changing a role or walking into range later does not recruit new recipients into an existing invocation. Losing permission can stop delivery. The action's audience grants no through-wall view, entity tracking outside normal visibility, or private watched-player information.

Viewers lacking a required model follow Q137's cosmetic skip or explicit `required: true` error contract. An item fallback remains visible but cannot claim the custom animation was shown. A held relic with no configured carried world visual can retain ordinary cosmetic playback for a later drop, but cannot satisfy a newly requested required world-animation delivery while it has no drawable world consumer. Required dispatch never guarantees that a person was looking at the object or watched an entire clip.

### Pickup, dropping, and elapsed time

Bind relic playback to its logical instance and captured model, not a disposable world-rendering object or a former holder. Pickup and a normal nearby drop preserve the current playback's elapsed progress. If no world visual is currently drawn, the presentation clock still advances. A compatible visual that becomes visible again shows the current point in a still-active clip; an already finished one-shot does not replay.

This contract applies equally to Q188's HUD-only carry, where a world animation can be hidden between pickup and drop, and Q189's optional carried consumer. A carried consumer uses the same logical playback rather than starting another independent animation.

Use elapsed server simulation time multiplied by `speed` to determine clip progress. Pausing simulation pauses that progression. Asset readiness, camera changes, and receipt of a delayed client update cannot reset the start time or grant gameplay progress. Cosmetic interpolation remains a client detail; this is not a claim of frame-perfect agreement between clients.

Explicit reset, a return home, delivery, despawn, or the end of playback/target ownership stops that relic's current explicit animation. A later reset or automatic respawn presents the model's current automatic state, normally idle, without resuming an old override. Normal drop preserves progress only when it actually drops nearby; an invalid-drop fallback is a return and stops it. A completed one-shot or explicit stop also returns to the current automatic state, never to a stale pose captured before pickup.

Animation markers, clip completion, client acknowledgements, and visibility changes have no gameplay authority. They cannot move the interaction volume, grant delivery, apply damage, or decide a phase transition. Gameplay timing remains authored through the accepted timers and rules. Named identity, target cleanup, revision retention, and presentation failure behavior follow the existing playback contracts.

## Related contracts

These contracts extend [relic placement and appearance](relic-placement-and-appearance.md), [relic identity and lifecycle](relic-identity-and-lifecycle.md), [presentation actions and audiences](presentation-and-dialogue.md), [playback ownership and model configuration](presentation-controls-and-models.md), and [audience selection](dialogue-formatting-and-delivery.md). The player attachment adapter, animation synchronization, and compatibility behavior still require implementation and runtime verification.
