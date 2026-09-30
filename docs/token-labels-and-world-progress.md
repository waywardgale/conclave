# Token labels and world progress

Status: Q204-Q205 are accepted. They reuse accepted HUD and world presentation. Q204 displays one declared token without reading an answer. Q205 gives the existing progress counter a world-space consumer. No implementation exists.

## Q204: explicit fixed-token labels

Accepted: add `show_token` with required `mechanic`, `token`, and explicit `audience`. The mechanic identifies an accessible active matcher's captured vocabulary and display metadata. The token identifies one declared value to show, regardless of whether it occurs in the chosen answer.

```yaml
show_token:
  id: sun_button_label
  mechanic: symbol_lock
  token: sun
  display: world
  location: sun_label
  audience:
    from: participants
  until_stopped: true
```

This label shows the matcher's Sun name and icon at the named location. It does not assert that Sun is correct, currently required, or usable by any particular player. A token that is absent from the chosen answer can still label a deliberate decoy.

### Token identity and rendering

Resolve a literal token ID against the fully bound matcher vocabulary. An existing compatible configuration parameter may supply the whole value under Q201, but it must resolve to a declared token before activation. Reject unknown tokens, arbitrary text, dynamic event references, random selection, and expressions in this initial action form. As with Q194's initial submission form, reacting differently to known token values can use explicit `event_value` guards and authored literal actions.

Use the token's captured name, icon, style, and translated name under Q197. Missing authored metadata uses its accepted ID-label and text-only defaults. Showing a label does not change or replace the vocabulary or create a new token definition. The token's display settings supply formatting; this action does not add another inline formatting language.

Render exactly one token without an answer-position number. Default `display` to `hud`; explicit `world` uses Q203's named location, facing, optional billboard, scale, view distance, and ordinary depth testing. A world label occupies one standard token cell. The HUD form may still use `location` as the origin for an audience radius, but rejects world-only rendering fields.

Keep answer selection out of this action. Reject `positions`, `player`, `each_recipient`, `summary`, and `show_total`. Every selected recipient receives the same declared token, localized where supported. To show different labels to different groups, author separate actions with explicit audiences. To reveal a solver's actual expected answer, use `reveal_pattern` with its separate answer-ownership contract.

### A label is separate from a control

Do not inspect the expected answer, current progress, remaining required values, or input eligibility when deciding whether the declared label can appear. Hiding a token because it was not drawn would disclose answer membership through presentation behavior. Unknown vocabulary IDs remain authoring errors, but valid decoy IDs remain displayable.

Do not automatically associate the label with the nearest block, group member, or input binding. Its location is visual placement only. A label may intentionally be elsewhere, and its token need not have a direct input route when the rest of the matcher's required coverage is valid. The author controls the relationship between a labelled object and an actual input route.

The panel has no interaction target, collision, native-input consumption, or token-submission behavior. A button or NPC still needs its own declared Q192 binding. Nothing changes Minecraft block state, a block's ordinary appearance, the group's model, or another player's ability to use the real object under ordinary rules.

No automatic label is added to every declared input. This keeps hidden controls, deliberately misleading decoration, and separate reader/solver information possible without code changes. Labels also do not indicate that a pending NPC target has spawned or that a filtered control is currently enabled.

### Availability, privacy, and lifetime

Require an initialized active matcher, matching the other pattern presentation actions. A pending declared target follows the existing required/recoverable unavailable-target policy. A known terminal target produces no new label; an unknown or inaccessible private target fails validation. This initial action does not read a reusable definition as though it were a live occurrence or display labels before the associated matcher starts.

An enclosing rule can subscribe to the matcher's `started` event to create its labels once its configuration is initialized. It should not try to label a pending child during the parent's earlier `started` stage. Direct reuse of a `match_pattern` definition exposes its public matcher capability; a wrapper does not expose private children through dotted paths.

Require explicit recipients even when a label is intended to be public. `audience: {from: online_players}` deliberately includes currently selected online outsiders and GMs; it does not become an unrestricted persistent world object or recruit future arrivals automatically. Preserve the accepted captured-recipient, continued-eligibility, spectator, and reconnect rules. The existence of possible token artwork in a resource pack is not permission to broadcast a private label's placement or selected token.

Reuse the five-second simulation-time default, positive `duration`, or `until_stopped: true` with a required ID. Reuse phase/encounter ownership, `stop_presentation`, and the common named-playback namespace. Reusing an active clue or counter ID replaces its previous output, so use distinct IDs to keep them together. Clearing progress or mismatching changes neither the fixed label nor its remaining time.

Remove the label at matcher completion/failure/cancellation, presentation-owner end, expiry, explicit stop, or replacement. Reconnection restores only a still-active permitted label with its existing remaining time. Old packets cannot recreate it after its owner ends or overwrite a replacement. Permanent signs and labels outside an active matcher remain a separate world-content use.

Use captured assets, bounded work, and Q137's cosmetic/required dispatch behavior. Do not replace a missing icon with another token, widen the audience, or attach arbitrary sounds. Missing assets follow the existing component policy. Client display acknowledgements have no gameplay authority.

## Q205: pattern progress placed in the world

Accepted: add `display: hud|world` to `show_pattern_progress`, defaulting to the accepted HUD consumer. World mode requires an arena-bound named `location` and places the live counter there.

```yaml
show_pattern_progress:
  id: ritual_progress
  mechanic: personal_symbols
  summary: true
  display: world
  location: ritual_meter
  audience:
    from: participants
  until_stopped: true
```

This displays the captured solvers' finished count against the required completion goal at `ritual_meter`. It is an extension of the accepted counter, not a new objective or a scoreboard that calculates progress independently.

### Preserve the measurement and disclosure contract

Keep Q200's exact source selection. Shared mode shows the shared record; per-player mode requires one typed `player`, `each_recipient: true`, or `summary: true`. A player's current life state or role does not change which retained record the explicit selector identifies. Summary count and required goal never shrink after a death or disconnect.

Keep `show_total: true` as the default. A record view discloses its actual expected-answer length; a summary view discloses the required completion goal. False omits the denominator from both the visible counter and its client payload. Do not add a hidden percentage, progress fraction, bar, slots, or total-derived panel width.

World placement adds no expected token values, submitted-token history, next-token hints, roster list, or per-player detail to a summary. Preserve Q200's distinction between a labelled selected player's record, the recipient's own record, and an aggregate summary. Personal completion may remain visible while the matcher is still waiting for others. Overall completion ends the output under the existing lifecycle policy.

Two solvers using `each_recipient: true` may see different counts at the same location. Server-selected data and private viewing permissions remain authoritative. Nearby players cannot obtain another person's count merely by looking at the same panel or watching their camera.

### World layout and visibility

Use a centered counter panel two blocks wide at scale one, with bounded wrapping for its readable label and count. This is a counter-specific base layout; the half-block token-cell width from Q203 does not apply to a numeric progress panel. An optional positive `scale` multiplies the complete panel. Final font layout and supported dimensions remain part of the client UI and engine-limit work.

Retain the existing mechanic-name/ID label, selected-player identification, standard localized wording, and optional `style`. Do not add arbitrary layout definitions, text expressions, automatic narration, or model references. Showing a number in the world does not require a token or an item icon.

Reuse Q203's captured location/facing, fixed front-facing default, optional billboarding, default 32-block `view_distance`, ordinary occlusion, and available-client-world requirements. A fixed panel can be obscured by terrain. It has no hitbox, collision, interaction handling, or authority to load distant chunks. Audience radius and camera rendering distance keep their distinct meanings.

Reject `billboard`, `scale`, and `view_distance` on `display: hud`. In HUD mode, an optional location remains only a radius origin; in world mode it supplies both placement and any radius origin. Named locations can have optional Location anchors, and moving an anchor later does not move an active attempt's panel.

### Live state and cleanup

Reuse current-state updates from Q200, including initial snapshots, committed count changes, coalescing obsolete updates, and restoring current state after a permitted reconnect. Do not reconstruct the counter by replaying gameplay events or trust a client's reported number. An obscured or out-of-range display does not stop the matcher or pause the presentation timer.

Keep the existing explicit audience, recipient capture, continuing eligibility checks, five-second simulation default, duration/persistent alternatives, ownership, named replacement, explicit stop, and matcher-end cleanup. Required dispatch still means supported delivery, not proof that a player approached or read the panel. A retained successful server summary is not permission to keep the display running after the matcher ends.

Use client-local, audience-filtered presentation state rather than public entity metadata for private counters. Changing source, location, or display mode through a newly admitted invocation replaces the addressed playback; delayed work cannot modify its replacement. Existing asset and execution limits remain in force.

## Relationship to the matcher contract

The resulting presentation vocabulary has three explicit purposes: `show_token` displays authored vocabulary, `reveal_pattern` discloses an expected answer or selected pieces, and `show_pattern_progress` displays current counts. These operations never submit input, change an answer, or imply permission to use a physical control.

These contracts use [token display and clue selection](pattern-presentation-and-clues.md), [world clue rendering](world-pattern-clues.md), [progress counters](pattern-state-and-progress-display.md), [typed parameter binding](pattern-parameters.md), [input validation](pattern-definitions-and-inputs.md), and [named playback](presentation-controls-and-models.md). The [matcher reference](match-pattern.md) indexes the complete accepted contract. Detailed UI limits and client synchronization remain implementation-design work. These contracts do not add moving clue attachments, arbitrary text templates, new input sources, or a shared runtime-answer object.
