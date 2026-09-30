# Pattern clues placed in the world

Status: Q203 is accepted. It extends the accepted `reveal_pattern` action with a second visual consumer. HUD remains the default, with world output explicitly selected. Platform facilities have been inspected, but no Conclave world-clue adapter exists.

## Q203: world-space clues at named locations

Accepted: add `display: hud|world` to `reveal_pattern`, with `hud` as the existing default. World display requires an explicit arena-bound `location`. Show the same selected answer pieces at that location instead of on the recipient's HUD. All existing answer-selection, audience, timing, and publication rules still apply.

```yaml
reveal_pattern:
  id: north_wall_clue
  mechanic: symbol_lock
  positions: [2]
  display: world
  location: north_symbol
  audience:
    from: participants
    role: reader
  until_stopped: true
```

This fragment reveals only the second answer piece as a world clue for the captured readers. A named location may have an optional Location anchor, but placing an anchor is not required for valid YAML or for the clue to appear. The anchor's editor label remains privileged authoring information; it is not the gameplay clue.

### Position, facing, and scale

Use the captured location as the visual panel's center and its captured facing as the outward front direction. Show that front direction in the authoring preview so a wall-mounted panel does not depend on guessing rotation numbers. Moving an anchor or publishing changed coordinates affects future attempts only.

Default to a fixed, front-facing panel. Add optional `billboard: true` to turn the panel toward the permitted viewer's current camera while preserving its center. A fixed panel is readable from its front; it does not mirror text onto its back. Authors can place a second, oppositely facing display if they need independent presentation on both sides. Billboarding changes orientation only, not audience or permission to receive private data.

Add positive finite `scale`, default one, within the supported rendering limits. At scale one, use half-block token cells in a centered horizontal row, with one-eighth-block gaps. Fit each supported icon within its cell and its readable label in the cell's bounded label area. Apply scale to the row, labels, and spacing together. The authoring preview must show the complete rendered bounds and clipped/wrapped label behavior; the detailed font and label layout follows the client UI design.

Only selected answer pieces occupy cells. Partial revelation creates no blank placeholders for undisclosed positions. Preserve Q198's original position labels for ordered matching and its unnumbered unordered presentation. Never create a row whose hidden length reveals the rest of the answer. To distribute pieces across different walls or rooms, use separate named invocations with explicit positions and locations.

All placement is visual. The location does not need player footing or empty collision space, and a panel may deliberately float. Terrain can obscure it, including a clue positioned inside a solid block. Show such placement in the editor preview rather than moving the clue to an arbitrary nearby point. The required location remains within the attempt's permitted arena; this initial consumer does not add world-scoped clue placement, moving entity attachments, or raw coordinate arguments.

### World visibility and interaction

Render every component with ordinary world depth testing. Opaque terrain can obscure the icon and text; use normal transparent/translucent material behavior where supported. Do not add a through-wall outline, x-ray text, offscreen arrow, or HUD fallback that reveals a hidden panel. A billboard obeys the same visibility policy.

Add positive finite `view_distance`, default 32 blocks, within the renderer's supported limit. Treat it as a world-rendering cap measured from the current viewing camera to the panel center. It does not override the client's available world data or make distant chunks visible. Rendering also requires the panel's dimension and relevant client world region to be available.

The authored audience remains the authority for receiving clue data. A radius in that query still measures from the action's location using the existing authoritative player-position rules. It is a recipient filter; `view_distance` is a rendering limit. Do not replace a private player query with whoever happens to look at the wall, nor use camera movement to recruit recipients.

A selected client may receive its permitted clue data before the panel is visually unobstructed or within camera range. Depth testing controls ordinary rendering, not what an authorized modified client can inspect after receiving data. Keep the existing server-side private-information boundary: nonrecipients must not receive the selected tokens at all.

This output is a cosmetic panel, not a block, NPC, relic, or interaction target. It has no collision, hitbox, AI, inventory, health, persistence as a world object, or item drops. Looking at it, clicking it, or attacking through its visual area does not submit a token, consume native input, or block ordinary Minecraft use. If a clue labels a real control, the control's actual block/group binding and accepted reach/eligibility checks remain separate.

Use the token's registered item GUI visual or texture icon and its name/style for the panel. Rendering an item icon does not spawn an item or grant its properties. This first world consumer does not add a custom 3D model for each token; supported item visuals may themselves use their registered native appearance.

### Per-viewer information

Preserve the distinction between answer ownership and audience. A typed `player` can select one solver's answer for a different reader. With per-player `each_recipient: true`, two selected solvers may see different clues at the same location. Their camera positions or physical proximity do not merge those private views.

Snapshot eligible recipient identities once, as accepted for the HUD action. Continue enforcing the original audience query and current viewing permissions. An original recipient who temporarily stops qualifying receives no continuing output; an original recipient who qualifies again can resume while the same playback is active. Newly matching identities do not join automatically. Entering rendering range is not a new audience selection.

Apply the accepted spectator policy. Viewing another player's camera does not grant their clue by default. Explicit global permission to share watched-player private information remains a separate viewing rule, not a way for clients to request arbitrary answers. Creative area overlays and anchor labels grant no additional clue access.

Use server-filtered presentation payloads and client-local visual state. Do not place the private answer in a normally broadcast display entity, block entity, shared resource file, or public metadata and then rely on the receiving client to hide it. This does not alter normal public NPC/relic rendering or hide physical world objects from outsiders.

### Lifetime and resource handling

Reuse Q198's five-second simulation-time default, positive `duration`, or named `until_stopped: true`. Reuse phase/encounter ownership, named replacement, and `stop_presentation`. A reconnect restores only still-active, currently permitted presentation with its original remaining lifetime. Leaving render range or temporarily lacking the client region does not refresh that lifetime or redraw the expected answer.

Clear the panel when its matcher ends, its presentation owner ends, its duration expires, or an explicit stop removes it. Remove client-local state when the relevant world/session is discarded; still-active state can be resynchronized on return only under the same audience and lifetime rules. A stale update cannot recreate an ended panel or replace a newer playback using the same name.

The same playback ID names the whole output. Switching an active named clue from HUD to world or changing its position through a new invocation replaces the old presentation under Q138. Use distinct IDs when intentionally showing both a HUD clue and a world clue at once.

For `display: hud`, preserve Q198's existing meaning of optional `location` as an audience-radius origin only. Reject the world-only fields `billboard`, `scale`, and `view_distance` there, rather than silently ignoring them. `display: world` uses `location` for both placement and any radius origin. Unknown display modes fail validation.

Validate locations, assets, styles, scale, distance, and supported consumer fields before publication where possible. Use the attempt's captured asset revisions and Q137's normal cosmetic/required dispatch policy. `required: true` cannot guarantee that a player turns toward a clue, approaches it, removes an obstruction, or reads it. Being outside camera range is not an asset delivery error.

World clues do not request arbitrary distant chunk loads or change player render-distance settings. Use the existing arena readiness and bounded presentation work. Count instances, cells, and updates against renderer/engine budgets; exact hard limits require implementation measurement. Editor previews remain local authoring previews and never publish the answer or start a live encounter.

## Evidence and remaining implementation work

[Presentation asset research](presentation-assets-research.md#private-world-space-pattern-clues) records verified 26.2 text/item/custom-geometry submission, fixed and camera-facing transforms, depth-aware rendering, and targeted custom payloads. These facilities support the accepted adapter direction, but do not implement its privacy, cleanup, sizing, or revision behavior. Rendering around transparent blocks, the chosen camera modes, resource changes, and reconnection still require integration tests.

This contract adds world output only to `reveal_pattern`. It does not silently extend `show_pattern_progress`, give the broad `show_effect` action a pattern-answer input, or label direct interaction bindings automatically. [Q204-Q205](token-labels-and-world-progress.md) accept explicit fixed-token labels and a separate world progress consumer.
