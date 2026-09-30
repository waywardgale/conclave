# Pattern presentation and clues

Status: Q197-Q198 are accepted. They build on accepted logical tokens, stable expected patterns, explicit audiences, and named presentation. Appearance and disclosure are independent choices: a clue can display existing token IDs without custom appearance metadata. No implementation exists.

## Q197: readable token names and icons

Accepted: keep the accepted `tokens` list of local string IDs. Add optional `token_display`, a mapping from those IDs to presentation settings. An omitted entry uses the exact token ID as its readable label and no icon.

```yaml
tokens: [sun, moon, star]
token_display:
  sun:
    name: Sun
    icon:
      texture: raid_tools:symbols/sun
    style: conclave:shout
    translations:
      ru_ru:
        name: Солнце
```

Each entry may contain `name`, `icon`, `style`, and `translations`. `name` is nonempty literal text, not an expression or executable component. `icon` uses exactly one registered `item` or captured `texture` under Q128. An item uses its ordinary GUI visual, without granting an item, accepting raw components, or changing inventory. A token with no icon remains a valid text-only clue.

`style` references the accepted `text_style` type and defaults to `conclave:normal`. It formats the label, including supported font and bounded scale. It does not select an audience, add audio, change matching, or allow arbitrary HUD placement. Keep a readable text label alongside an icon so the clue does not depend solely on a custom image or color.

Use the accepted locale-map convention for `translations`, with `name` as the translated field. If the recipient has no translated name, use the default name, then the exact token ID when `name` is omitted. Choose the locale when that recipient's presentation first begins and retain it for that playback. This extends the locale convention to a token label without introducing dynamic text expressions, translated identities, or automatic translation. Icons and styles are common across locales in this initial form.

Validate keys against the declared token vocabulary. Reject an unknown display key rather than silently adding a token. Duplicate keys, empty authored names, unsupported fields, incompatible assets, or invalid locale/style references fail publication. Display entries can cover some or all tokens, including deliberate decoys. Omitting the whole mapping keeps the Q191 string-list form unchanged.

Token identity remains independent from appearance. Two tokens may deliberately look alike; the editor can point out the ambiguity without merging them or rejecting an authored puzzle. Changing a name or texture in a new revision changes only future consumers. Existing attempts retain their captured labels, translations, styles, and assets.

Metadata alone does not reveal the chosen answer, label or replace world blocks/NPCs, attach symbols to their models, play a sound, or change interaction eligibility. Those need an explicit supported presentation or gameplay capability. The display metadata supports HUD and Q203 world clues; it does not register a new native item or global token-definition kind. [Q204](token-labels-and-world-progress.md#q204-explicit-fixed-token-labels) accepts an action for showing a declared token independently of the answer.

Use Q137's delivery policy. A missing cosmetic icon may be skipped while a compatible text label still appears. If the presentation is explicitly required, every authored component must meet its required-delivery contract; a visible label does not automatically satisfy a required missing icon or font. Never substitute a newer asset revision under the same logical ID.

## Q198: explicit pattern clues on the HUD

Accepted: add `reveal_pattern` as an audience-controlled presentation action. Require `mechanic` and an explicit `audience` using the existing presentation selector. Its default consumer is a noninteractive HUD clue panel that leaves ordinary movement and input available. [Q203](world-pattern-clues.md) accepts an explicitly selected world-space consumer. Show the complete expected answer by default, or an explicit list of answer positions.

```yaml
reveal_pattern:
  id: reader_clue
  mechanic: symbol_lock
  positions: [1, 3]
  audience:
    from: participants
    role: reader
  duration: 8s
```

This example reveals positions one and three of a shared answer to the selected readers. It gives the readers no automatic right to submit input, and input eligibility gives no automatic right to receive this clue. It illustrates a capability rather than bundled encounter content.

### Selecting the answer independently from the audience

A shared-progress matcher has one expected answer and needs no additional answer selector. Reject `player` and `each_recipient` there.

For per-player progress, require exactly one of typed `player` or `each_recipient: true`, even when `pattern_per_player: false` gives the captured players identical answer values. A typed player identifies whose retained answer is presented to the selected audience. It does not select that player as the audience, and the answer owner need not currently be alive or online. They must have an existing captured record in this matcher.

```yaml
reveal_pattern:
  mechanic: personal_symbols
  each_recipient: true
  audience:
    from: participants
    role: runner
  duration: 8s
```

Here each selected runner receives only their own expected answer. Snapshot the audience first and resolve every recipient's own record before dispatch. Every selected recipient must have a captured solver record; report a missing record through the existing contract-error policy rather than selecting another player's answer, fabricating an answer, or silently narrowing the requested audience. A valid empty audience remains a no-op. Explicit count conditions are available when the author requires recipients.

Reject both answer selectors together, `each_recipient: false`, and an omitted selector in per-player mode. The convenience form is specific to this presentation action; it does not introduce a general `recipient` expression or permit arbitrary object traversal. A specific player's clue can deliberately go to a different reader through the typed `player` form.

### Whole and partial disclosure

Omitting `positions` reveals the complete selected answer. When supplied, require a nonempty, strictly ascending list of unique positive one-based integers. Validate each position against every possible answer length from the bound constructor, including the shortest `choose` alternative. Do not allow a random draw to cause an out-of-range reveal at runtime. Zero, duplicates, descending order, relative positions, and expressions are invalid.

Resolve the requested pieces once at invocation against the captured answer. Send only those tokens and their permitted display metadata. Do not send unrevealed tokens, a complete answer hidden by the client, blank slots for undisclosed positions, or a separate total-length field for a partial reveal. An explicitly selected position still communicates that position exists. A complete reveal necessarily discloses the complete length.

For an ordered matcher, retain the original position numbers in the visible clue so separately revealed pieces remain interpretable. For unordered matching, positions select from the stable constructor result solely to choose pieces; omit ordinal labels and identify the clue as unordered. Repeated values remain separate occurrences in either form. The stored draw/list order does not impose a new submission order on an unordered matcher.

Display the selected names and supported icons without automatically adding current progress, remaining-answer hints, correct/incorrect coloring, or another player's completion status. Showing a clue does not submit a token, reroll an answer, change progress, or refresh a timer. A mismatch or `reset_pattern` leaves the same revealed values intact for their remaining presentation lifetime.

Possible token artwork may already be distributed as part of the captured asset set. That does not authorize distributing the resolved answer or the mapping of private answers to players. Keep resolved patterns out of shared resource files and unfiltered client state. As with other presentation, removing a display cannot erase information a player has already seen.

### Audience eligibility and reconnection

Use the accepted presentation collection default: selected connected participants may include dead participants entitled to the cue. Support explicit online-player/raider collections and the existing filters. A globally addressed clue can deliberately reveal a shared answer to outsiders. The per-recipient answer form still requires a solver record for every selected recipient.

Capture recipient identities once at invocation. For this continuing private HUD display, recheck each original recipient against the original audience query and current viewing permissions while output remains active. Suppress their display while they stop qualifying. Do not recruit identities that first match after invocation. A formerly selected recipient can resume only while the same playback is active and they qualify again; its deadline never moves.

Reconnection resynchronizes only a still-active clue to an original recipient who currently qualifies and has compatible resources. It restores the remaining lifetime, not a fresh reading window or an obsolete expired clue. Initial offline identities do not join the captured presentation audience later. Presentation access does not restore gameplay re-entry after reconnect grace.

Watching a solver's camera does not grant their clue. Apply the accepted global spectator/private-information setting deliberately. The default keeps private watched-player output unavailable. Supported globally enabled sharing remains an explicit viewing permission, not a client request for arbitrary solver records. [Q271](settings-and-spectator-controls.md#q271-explicit-grave-controls-and-teammate-selection-after-passing-out) accepts exact spectator mirroring and target-switching controls.

For a radius filter, require an explicit `location` on this action and measure from that captured location in its dimension. In the default HUD mode it is only the audience origin; the accepted Q203 world mode also uses it for placement. An `area` filter needs no extra origin. Reject radius without a location rather than guessing a GM, solver, or camera position. Moving recipients may lose and regain eligibility under the continuing-display rule; no new identity joins automatically.

### Time, ownership, and explicit stopping

Default to a five-second display, with optional positive `duration`. Use the server's simulation clock because this clue window is part of an encounter challenge. Paused simulation does not consume the reading window. This intentionally differs from Q135's real-time spoken-media duration and does not change dialogue behavior.

Allow `until_stopped: true` instead of `duration`, with a required playback `id`. Reject both timing fields together and reject `until_stopped: false`; omission uses the finite default. Even an explicitly persistent clue ends with its source matcher or owning presentation scope. It is not a player-persistent record.

Reuse phase or explicit encounter playback ownership, named replacement, and `stop_presentation`. A finite unnamed clue gets an internal cleanup identity. Reusing an active ID replaces that whole clue playback and its former recipients under Q138. Distinct IDs can coexist within the supported HUD and work budgets; they do not silently overwrite each other. The precise panel layout and capacity belong to the client UI and operational limits.

Clear this private clue when its source matcher completes, fails, or is cancelled, even when a broader presentation owner is still active. This capability opts out of the allowance for some finite speech to finish after its source challenge has ended. Stop timers and release captured resources when no remaining consumer needs them. A captured old stop or network update cannot remove or refresh a replacement clue.

The matcher must be initialized and active at dispatch. A declared pending matcher uses the existing required/recoverable unavailable-target policy. A known terminal occurrence yields no new clue; post-completion answer review is not introduced by this action. Unknown targets and private wrapper-child access remain invalid. Personal completion does not itself end a still-active per-player matcher, so a completed player's retained answer can still be shown while others finish.

Use the captured revision and Q137's cosmetic/required delivery behavior. `required: true` means supported dispatch to compatible intended clients; it cannot prove that a human read or remembered a clue. Client acknowledgement never advances gameplay. Normal death, disconnect, or permission changes after dispatch do not become retroactive delivery failures.

## Related contracts

These contracts extend [pattern definitions](pattern-definitions-and-inputs.md), [progress ownership](pattern-progress-and-submission.md), [matcher events and reset](pattern-feedback-and-controls.md), [shared progress and private clues](selection-and-patterns.md), [presentation audiences](presentation-and-dialogue.md), [styles and translations](dialogue-formatting-and-delivery.md), and [playback controls](presentation-controls-and-models.md). [Q199-Q200](pattern-state-and-progress-display.md) accept current-state predicates and optional progress displays. [Q201-Q202](pattern-parameters.md) accept typed pattern reuse. [Q203](world-pattern-clues.md) accepts an explicit world-space clue consumer while preserving these HUD defaults.
