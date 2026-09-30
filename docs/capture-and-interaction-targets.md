# Capture configuration and interaction targets

Status: Q173-Q174 are accepted. These contracts define capture fields and interaction target binding; [Q175-Q177](interaction-input-and-progress.md) accept the detailed interaction controls. Examples are framework review fragments, not bundled encounter content. No implementation exists.

## Q173: capture configuration and interruption

Accepted: a `capture` occurrence requires one bound `area` and a positive `duration`. Add a positive integer `players_required`, defaulting to one, and an optional `players` selector for additional eligibility filters. Use the existing mechanic `id`, optional `name`, and scope rules.

```yaml
- id: north_capture
  type: capture
  area: north
  players_required: 3
  duration: 10s
  players:
    role: runner
  on_interrupt: reset
```

This requires three eligible runners inside the north area for ten seconds of capture progress. Omitting `players` uses the accepted ordinary gameplay selection. The area's occupancy and the player selector combine with AND. If the selector also names another area, both spatial requirements apply rather than one replacing the other.

### Occupancy and progress

Count distinct living, online participants who remain eligible to contribute to this attempt and satisfy the selector and area membership. An explicit broad selection cannot let dead, disconnected, or observation-only participants contribute. NPCs and nonparticipants do not add capture progress. This changes mechanic bookkeeping without blocking anyone's ordinary movement or interaction.

Use the referenced area's Q267 membership policy, defaulting to Q56's feet-position test, with its resolved geometry. The shared area policy also supplies the accepted overlap and containment modes; there is no capture-specific geometry format. Initialize occupancy from players already inside, then reevaluate at server simulation updates under Q59. Capture duration measures simulation time, with no progress awarded before that much qualifying time has elapsed.

[Q267](area-fields-and-membership.md#q267-name-the-three-membership-modes-and-apply-them-to-the-final-volume) accepts placing the mode on the named area, so capture and other consumers use the same configured geometry test. That field-placement choice is accepted.

At or above `players_required`, advance one second of progress per second of simulation time, regardless of extra occupants. Qualifying identities may change while the required count remains satisfied. The mechanic does not bind the original occupants, require them all to remain, or accelerate with a larger team. Authors can narrow contributors through explicit role or aura filters. Simultaneous occupancy decisions use the accepted coherent server observation; a client movement packet is not an independent capture tick.

### Interrupted progress

Give `on_interrupt` three typed forms:

| Value | While occupancy is insufficient |
| --- | --- |
| `reset` | Clear unfinished progress immediately. This is the default. |
| `pause` | Preserve progress until occupancy qualifies again. |
| `{decay: 5s}` | Lose progress at a constant rate that drains a full meter in five seconds of simulation time. |

For a ten-second capture with `{decay: 5s}`, one second of insufficient occupancy removes two seconds of earned progress. A half-full meter therefore drains in two and a half seconds. Decay starts when occupancy becomes insufficient, clamps at zero, and stops when qualification returns. The specified duration is positive; it is neither a delay before decay nor a percentage expression. Invalid values fail validation.

Temporary interruption is not a terminal mechanic failure. Once complete, capture remains complete and emits `completed` once for that activation. Losing occupancy afterward cannot erase that result. A continuously occupied requirement belongs in an existing count/condition check. A new activation starts fresh progress; general mechanic restart/reset action names remain a separate unfinished contract. Phase re-entry and accepted repeat composition already create fresh occurrences.

Require explicit positive duration, so authors never rely on an invented default or use zero as a hidden instantaneous condition. Reject zero players, fractional counts, missing areas, and incompatible bindings. A temporary lack of participants is normal gameplay; failure to load required simulation or resolve the area follows the technical-error contract. Do not add automatic capture visuals, walls, failure damage, a deadline, or variable team-size scaling through these fields.

## Q174: typed interaction targets

Accepted: an `interact` occurrence supplies a nonempty `targets` list. Each entry contains exactly one registered target kind. Begin with `block`, referencing a named location, and `group`, referencing a declared spawn group. Keep optional `players` as the ordinary eligibility selector.

```yaml
- id: use_console
  type: interact
  targets:
    - block: console
  players:
    role: reader
```

```yaml
targets:
  - block: west_console
  - block: east_console
  - group: guides
```

The second fragment permits a valid use of either block or a current living member of the guides group to contribute to the same mechanic. Under the already accepted default, one deliberate valid use completes it. A requirement to use every console uses separate occurrences under an `all` composition or objectives. A targets list does not imply an ordered sequence, one completion per target, or a use requirement multiplied by group size.

### Block positions

`block: console` resolves the encounter's logical location binding through the chosen arena. It identifies the block cell containing that position, using mathematical floor on each coordinate, including negative coordinates. The editor shows the resulting block cell and lets the author select it in-world. An existing Location anchor can supply the location; its administrative configuration interface is not implicitly opened for ordinary participants.

Keep the resolved dimension and block position for the captured attempt. The target refers to that cell, not to a particular old block-state object. If the block is removed, the cell has no valid block target until a supported block is present again. Replacing a button with another supported block at that same cell keeps the position binding. This position contract is visible to the author; it does not freeze or restore the world. Block-type restrictions, if added, need explicit validated fields.

The target list creates no block, collision volume, invisible wall, protection, or automatic HUD prompt. Interacting with air at the location does not count as using a block. The interaction adapter must validate that the actual input identified the supported block at the bound position.

### NPC groups

`group: guides` resolves a permitted group producer and owning activation under Q81/Q93. It permits an actual use of any current living member of that group. Multiple members are unambiguous because the interaction identifies which NPC was used. The target predicate does not choose the nearest member or turn a group into one pooled NPC.

A declared future producer can remain pending until its group exists. Once bound, preserve that group activation's identity. Explicit reinforcements into that same open group become available targets; a replacement activation under the same authored name does not. Dead or removed members cannot receive interactions, and absent targets cannot count as completed uses. Group creation, reinforcement, and unexpected loss keep their accepted validation and error contracts.

### Validation and later input settings

Resolve target declarations and supported encounter/arena pairings before publication and admission. Unknown locations or groups, wrong reference kinds, unsupported adapters, and empty target lists are errors. Normal pending spawn, a destroyed block, or ordinary NPC death may leave no usable target; that alone is neither successful interaction nor an automatic wipe. The author can provide another target or an explicit failure condition. Missing required simulation and unexpected entity loss still follow Q69.

Validate the actual player's identity, ordinary mechanic eligibility, target identity, and supported reach checks on the server. One physical input cannot be counted twice by one mechanic because several entries matched or both client hands reported it. These bindings grant no bypass of reach, no remote input authority, and no private information. A source reference cannot mutate another attempt's private group state.

The existing support for repeated uses, distinct participants, and holds is detailed in [Q175-Q177](interaction-input-and-progress.md). Those contracts accept individual hold progress, ordinary reach and line of sight, and optional native-interaction consumption. They remain distinct from the separate grave-revival interaction policy.
