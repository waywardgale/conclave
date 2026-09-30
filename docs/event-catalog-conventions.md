# Completing the typed event catalog

Status: Q275 is accepted. Existing event meanings, simulation order, typed references, privacy and exports remain accepted. This contract supplies the remaining common conventions and fields for existing capabilities. No event implementation or generated schema exists.

## Q275: expose gameplay fields and retain engine provenance separately

Accepted: keep each event a small, documented, flat payload. Show its fields, units, optionality, triggering-player contract and valid contexts in the same capability catalog that drives editor help and validation. Complete the existing area, interaction, aura, timer and health-floor contracts without adding a general event scripting system.

### Shared rules

Use the accepted whole-field references such as `{event: player}` and `event_value.field`. Event measurements describe the admitted change. A typed subject reference identifies the original subject, whose live state or availability may subsequently change. Never retarget an old event to a replacement native body or repeated mechanic with the same authored name.

Keep internal attempt, activation, operation, generation and ordering provenance for validation and GM diagnostics. Do not add a mandatory public envelope to every event or require an attempt identity on a pre-start event. Existing domain-specific identity fields remain available under their schemas. In particular, aura source/activation information promised by Q118 remains typed information where the subscriber is allowed to observe it.

Do not reserve `type` for the event kind. Existing combat and NPC events already use it for damage type or native NPC type. The subscription's `event` selects the event kind. Requiredness, scalar identity comparisons and supported typed-reference consumers come from that event's registered schema.

Absent optional fields remain absent. Ordinary comparisons return false; `present` checks can distinguish absence. No string UUID conversion, fabricated zero, arbitrary object traversal, automatic field copying or implicit client broadcast is added. Use Q223's `on.player` to select and narrow an actual identity field when a subscription needs a player. Presence checks alone do not narrow types.

Exports retain Q166/Q223's explicit `data` mapping and triggering-player rules. Private engine provenance remains available for runtime checks without becoming exportable payload. Source-owned cleanup, stale-reference rejection and current action authority still apply even if a subscriber possesses a typed identity.

### Area entry and exit

Use a logical area source and the existing event names:

```yaml
on:
  source: {area: entrance}
  event: entered
```

`entered` and `exited` supply required `player` and `area`. `player` is the observed player's typed identity and the default triggering player. `area` is the typed bound area identity under the observation's captured definition. It is not a geometry map or a writable Location anchor.

Permit the structured source form `area: {id: entrance, players: {...}}` to select the observed player population through the existing player selector. Omission uses the established context defaults: online raiders with `state: any` before an attempt, living online active participants for ordinary attempt gameplay. An explicit selector may use the other accepted domains and filters without an added implicit participant restriction. Pre-start selectors cannot reference an absent participant roster or private attempt state.

Observe physical membership independently of eligibility. Test selector eligibility in the coherent observation state for that event. Joining the eligible population while already inside initializes membership without an entry; losing eligibility does not emit an exit. An author who needs an eligibility transition uses the relevant lifecycle event or a current occupancy condition. Preserve Q59/Q264's endpoint movement, same-dimension checks and absence of synthetic events on initialization, death, disconnection or cleanup.

### Pre-start interaction

For the initial bound-block source, use:

```yaml
on:
  source:
    interaction:
      block: entrance_switch
  event: used
```

Resolve `block` through the encounter/arena location binding, using the same actual block cell, floor-coordinate rule, native reach and line-of-sight checks as accepted block interaction targets. Carry required `player` and `location` typed references; `player` supplies the default triggering player. The source identifies the actual validated input target, not the nearest location. A missing/air target cannot create a use.

This observation represents one validated fresh interaction press. It does not acquire an interact mechanic's accumulated uses, hold progress, completion event or private activation. Native block behavior continues; the observation does not consume or cancel it. Author a mechanic when held interaction or accumulated progress is needed after start. Native input duplication must not create duplicate uses.

Supported entity interaction sources must register their typed target contract and explicitly support the pre-start context. An uncreated attempt group remains invalid. This does not introduce arbitrary entity lookup, persistent world NPC definitions or a free-form target selector. Conditions may filter the actual event player through the already accepted start-rule vocabulary.

### Aura lifecycle fields

Use `source: {aura: aura_id}` for the accessible lifecycle of that aura. The source observes only holders and contributions permitted by its subscription's owning context; it is not permission to inspect another attempt's private state. Existing typed player/NPC targets and conditions filter the holder as needed. An aura event has no unconditional triggering player because a holder may be an NPC. Explicit `on.player: holder` gives the accepted player-only subscription view by narrowing `holder` itself; it creates no `player` alias.

| Field | Contract |
| --- | --- |
| `holder` | Required typed player or NPC identity. |
| `aura` | Required namespaced aura identity. The actual retained definition governs the event. |
| `source` | Typed producer identity when the event has one observable source; otherwise absent. It is not a player inferred from an NPC/projectile or an arbitrary string. |
| `activation` | Typed source activation when applicable and observable. Absence does not imply a new activation or permit access to a private owner. |
| `contribution` | Required exact contribution identity on contribution/stack events. A stack is that same contribution, so there is no second competing stack identity. Absent on aggregate holder events. |
| `contributions_before`, `contributions_after` | Required nonnegative counts for the affected holder's aura around the committed operation. |
| `stacks_before`, `stacks_after` | Nonnegative stack counts for a stacking aura; absent for a non-stacking aura. |
| `reason` | Required on expiry/removal events, with `expired`, `removed`, `death`, `cleanup` or `recovery` according to the actual cause. Absent on application/refresh events. |

For an operation affecting several contributions, individual events carry counts immediately around their committed change; the single holder event carries the aggregate before/after operation counts. An event with several relevant sources must not choose a fabricated representative. Keep individual source information in the contribution events and internal diagnostic provenance. Registered extensions must declare any additional reason values; arbitrary authored reason strings are not accepted.

Refresh events also carry `remaining_before`, `remaining_after`, `timed_before` and `timed_after`. Measure both previous and renewed expiry relative to the same simulation instant. Remaining values are duration-valued and present exactly when their corresponding `timed` value is true. Untimed contributions do not acquire an infinite number or wall-clock timestamp. Contribution/stack events describe that contribution; the holder event uses Q161's displayed countdown, next stack expiry for stacking auras and final contribution expiry for non-stacking auras. Its aggregate countdown may remain unchanged even though a contribution was renewed.

Preserve the accepted gain/refresh/removal event order, no-op behavior and natural-expiry distinction. Source cleanup and explicit administrative replacement are real removal/application operations where already supported; persistence restoration is neither. A terminal event may retain a contribution identity after it has ended, but using it in an action cannot recreate it or affect a newer contribution.

### Timers and health floors

For named timers, initially expose the accepted `expired` event with required typed `timer` and duration-valued `duration`, recording that timer activation's configured full duration. Expiry has no triggering player. Timer generation and scheduled identity remain internal; an admitted event never silently follows a later restart. The identity does not become an exported mutable handle into private state. Keep `start_timer`, `restart_timer`, `pause_timer`, `resume_timer` and `stop_timer` behavior. Defer additional timer state-change event names rather than introducing several notifications without an encounter-design requirement. This explicitly resolves Q92's previously unchosen additional event catalog.

`health_floor_reached` carries required `target`, the affected typed NPC identity, and `threshold`, the resolved floor in native health points. It has no implicit player or attacker. Installing a floor above current health can produce this event without a hit. Preserve Q152's once-per-installed-setting behavior and its existing damage/floor ordering; healing does not rearm it.

All other already specified event families retain their accepted fields and reasons. Their concrete registry/schema implementation must follow those contracts rather than reopening their semantics. No counter-change event, arbitrary emit action, generic canceled/error event or extra outcome alias is added by this contract.

Related contracts: [rules and event references](phases-and-rules.md), [event comparisons](event-conditions.md), [player identity matching](event-players-and-mechanic-results.md), [area observations](areas.md), [start rules](encounter-activation.md), [interaction targets](capture-and-interaction-targets.md), [aura lifecycles](auras-and-world-lifetimes.md), [aura actions](aura-actions.md), [aura countdowns](aura-periodic-and-queries.md), [timers](conditions-and-composition.md), and [combat controls](combat-events-and-controls.md).
