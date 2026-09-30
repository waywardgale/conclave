# Aura display and world visual effects

Status: Q279 is accepted. Holder HUD auras, configurable visibility to others, optional particles/rings, typed `show_effect`, explicit audiences and captured assets are accepted. This contract completes their initial presentation behavior without adding another asset-delivery system. No renderer or effect schema exists.

## Q279: private holder HUD, explicit shared aura display and simple world effects

Accepted: keep player aura information on the holder's HUD by default. When the author shares it, show permitted other viewers a compact aura strip above the actual holder. Support NPC aura strips through the same explicit audience. Begin world effects with configurable particle emission and a glowing ring, usable by an aura or the existing `show_effect` action.

### One disclosed aura, appropriate placement for each viewer

Use the aura's `display` block for its accepted icon and authored description, plus `audience`. Default `audience` to the context shortcut `holder`, meaning only the affected player. An NPC holder yields no player recipient under this default. An explicit audience uses the existing player selector, including supported role, aura, area and holder-radius filters. Replacing the default with a selector does not implicitly add the holder back; include that player when intended.

An eligible holder sees their normal HUD entry with name, icon, stack count where relevant and Q65's countdown. Eligible other viewers see that aura's icon, count and countdown in a compact strip attached above the holder, with its name/description available when inspecting the visible strip. Do not place someone else's aura in the viewer's personal buff list or infer a team-wide boss bar. NPC aura information uses this same world strip for its selected viewers. Empty audiences produce no display.

The aura remains gameplay state even when its display is hidden. Omitted display uses the accepted default holder HUD, the aura's display name or ID, and a bundled generic icon if no explicit icon was authored. A hidden-display option suppresses the presentation without removing the aura or changing selection by `has_aura`. Exact sizing, accessibility controls and field schemas follow this behavior through the shared editor/catalog.

World strips follow the currently valid native holder while that holder is available to ordinary client entity tracking. They obey dimension, render-distance and depth/occlusion rules; they add no through-wall outline, remote camera, chunk loading or hidden-entity reveal. A model replacement does not create another aura. Death, unload or unavailable native body removes the world attachment; a retained aura may display again when its holder is valid, without a synthetic `applied` event.

Aura display uses continuous audience selection, not a fixed list captured when the aura was applied. A newly eligible viewer can see its current state after entering the selected radius, gaining the required role or reconnecting; an ineligible viewer loses it. Restore current counts/countdown and any current attached visual without replaying application cues or elapsed emissions. Entity tracking and compatible resources still gate world delivery. Player-owned auras can use only audience dependencies valid for their independent lifetime; reject a persistent display that retains a callback into an ended attempt or private role/group. Q271's separately authorized teammate mirroring can show the selected teammate's private HUD. Deduplicate direct/mirrored output and clear it on permission or target change. No client receives the private underlying aura merely because it tracks the same physical entity.

### Initial visual-effect profiles

Provide reusable typed visual-effect profiles with stable logical IDs, supported parameters, and resource dependencies under the existing captured-asset/capability system. The initial profiles are:

- Particle emission using a supported registered particle representation, bounded count/rate, spread and finite particle lifetime. Typed options can reference supported particle textures/material inputs; arbitrary native data or scripts are invalid.
- A horizontal glowing ring with authored radius, thickness, color/opacity and supported optional texture. It is a depth-tested cosmetic visual with no collision, illumination-based gameplay, membership test or damage.

Keep these profiles distinct from Minecraft status effects and Conclave gameplay auras. Authors can publish another supported profile or asset without compiling code. New rendering behavior requires a registered adapter. The exact resource container, particle subtype coverage and measured numeric limits are engineering work; the framework must document and validate its supported set rather than claim every native/mod particle format works.

`show_effect` selects one profile and exactly one supported typed origin: a bound location, a player/NPC, or a managed relic instance. Require the existing explicit audience. A one-shot takes the origin's current position; following a live target is an explicit option. Following ends when that original target is unavailable and never retargets a same-name replacement. A ring uses a positive authored visible duration; particle output uses its bounded emitter/particle lifetimes. Repetition and loops follow Q138's named playback, stop and owning-scope rules.

An action playback preserves its invocation's selected recipient identities. Moving its origin or changing a filter does not recruit new recipients into that playback. Continuous private delivery still stops when a selected viewer loses permission. A still-active loop may restore its current output to an originally selected, currently eligible viewer after reconnect/tracking recovery, with the current playback age and no old burst replay. A later explicit action evaluates a new audience. This action rule is separate from the continuous selection of an existing aura display.

An optional aura world visual uses these same profiles, follows the holder, and remains bounded by that aura's display lifetime. It defaults to the aura's display audience; an explicit nested audience replaces that audience for the world visual only, mirroring the accepted text/sound override convention. Aura-owned display ends with the aura or recipient permission. It does not promote arbitrary `show_effect` playbacks to player-owned lifetimes or keep an ended phase callback alive. Offline holders do not accumulate emission or replay it on reconnect.

Existing Q137 required/best-effort delivery, explicit resource consent, revision retention, cleanup and finite terminal-presentation rules still apply. Neither particles nor a ring decides whether a player was hit or stood in an area. An author must declare the matching gameplay geometry and timing separately. Client detail settings may reduce cosmetics; server outcomes do not depend on rendered frames.

Related contracts: [original aura display](vocabulary-proposal.md), [assets](world-locations-and-assets.md), [presentation actions and audiences](presentation-and-dialogue.md), [playback controls](presentation-controls-and-models.md), [aura lifetimes](auras-and-world-lifetimes.md), and [spectator privacy](settings-and-spectator-controls.md).
