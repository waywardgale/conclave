# Role and player lifecycle events

Status: Q171-Q172 are accepted. Role assignments and explicit player-state filters are accepted in [Q169-Q170](roles-and-player-state.md). These contracts make membership and player lifecycle changes observable through existing rules. No runtime implementation exists.

## Q171: role membership events

Accepted: expose `assigned`, `unassigned`, and `changed` on a declared role source. Reuse the existing local reference and explicit encounter-scope form.

```yaml
on:
  source:
    role: runner
  event: assigned
```

`assigned` identifies one participant newly added to the role. `unassigned` identifies one participant removed. Both provide a typed `player` field, usable as `{event: player}`, and support that player's `per_player` invocation limits. They refer to an assignment change regardless of whether that member is currently alive or connected. Any action addressed to that identity still enforces its own availability requirements.

`changed` describes one complete membership mutation. Provide integer `count_before`, `count_after`, `added_count`, and `removed_count`. The per-member events also provide `count_before` and `count_after` for the complete mutation. A replacement can keep the same count and still change membership. The aggregate event has no single triggering player, so it cannot supply `event: player` or use `per_player: true`. Do not expose mutable membership objects or invent list-valued event operations through this contract.

### Commit and notification order

Resolve and validate the entire assignment under Q169, then commit the new membership before queuing notifications. Queue all removals, then all additions, then one `changed`. Use stable player-identity order within each batch for reproducible delivery, without giving a role a gameplay ranking. Recipients present in both old and new sets generate no per-member change event.

For example, replacing runners Alice and Bob with Bob and Cara produces `unassigned` for Alice, `assigned` for Cara, and one `changed` with both counts equal to two. Every event describes the same completed mutation. Ordinary guards and subsequent selectors read current membership, which may change again before that rule runs. Payload values remain the historical snapshot.

An identical assignment, an already-present addition, an absent removal, an empty clear, or a rejected random draw emits nothing. Choosing the same random set again is also a no-change outcome. No rule sees a partially replaced membership set. Use the accepted event queue rather than invoking listeners inside the assignment operation. A rule that deliberately changes roles can generate later events, subject to its guards, invocation limits, and execution limits.

These events describe explicit membership mutations. Death, passing out, or disconnection does not emit `unassigned`, because Q169 retains the assignment. Scope teardown disposes local role state without pretending the author removed every member. It cannot reactivate ended rules, reward cleanup, or refer to the next activation's role. Public forwarding uses Q166's existing typed player and scalar fields, with ordinary scope and privacy restrictions.

## Q172: participant lifecycle events

Accepted: add the following events to an explicit player source in active attempt rules. They report committed transitions in the existing lifecycle; subscribing does not request a transition.

| Event | Meaning |
| --- | --- |
| `died` | An actual player death committed, after supported native rescue was resolved. A successful totem rescue does not qualify. |
| `revived` | A grave revival completed and the player became living. A request, incomplete help interaction, ordinary healing, or attempt-end recovery does not qualify. |
| `passed_out` | The player entered the state in which ordinary grave revival is no longer available, according to the accepted global policy. |
| `disconnected` | A connected participant's server connection ended. This is not a death. |
| `reconnected` | A tracked participant completed native world entry while the attempt still existed. Connection restoration does not itself promise eligibility to rejoin. |
| `reconnect_grace_expired` | The current real-time reconnect opportunity expired without a qualifying return. Emit once for that opportunity. This is not another death or a grave-window expiry. |
| `participation_changed` | Q238: committed admission changed between `active`, `reconnecting`, and `observer`. Camera changes alone do not qualify. |

All provide the typed affected `player`, plus scalar `online_before`, `online_after`, `state_before`, and `state_after` snapshots using Q170's life-state vocabulary. Q238 also provides `participation_before` and `participation_after` on these events, including `participation_changed`. The affected player is the unambiguous subject for `per_player`, including when another person helped them. For `revived`, also provide `method: assisted|self|administrative` and optional typed `helper`, present only for an actual assisted revival. An administrative operation reports its actual recovery kind; it cannot call attempt-end recovery a grave revival.

[Q223](event-players-and-mechanic-results.md#q223-an-explicit-player-field-for-a-subscription) accepts allowing an explicit subscription to choose a different documented player field for its invocation limits. This preserves the meanings of both `player` and `helper`.

Events retain their attempt, player lifecycle, and transition identity. Duplicate client input, repeated native notifications, or repeated recovery processing cannot produce another notification for an already committed transition. A later genuine death or disconnection creates its own transition. These are observations after commitment, so rules cannot undo the event by rejecting it.

### Lifecycle source defaults

Lifecycle subscriptions use `from: participants`, `online: any`, `state: any`, and Q238's `participation: any` when those fields are omitted. This is an additional contextual default under Q170, specific to these events. It allows a dead runner's revive window to expire while offline and still notify a matching rule.

```yaml
on:
  source:
    players:
      role: runner
  event: disconnected
```

This fragment subscribes to disconnections of assigned runners, including those already dead. Ordinary action selectors inside the rule retain their accepted living, online, active-participation default when selecting `participants`. The event does not remove the role or choose a replacement automatically; an author can explicitly react with the accepted assignment actions.

Resolve source membership and explicit filters against a coherent snapshot immediately before the transition, then retain that match for queued delivery. An explicit `state: alive` matches a living player's death, while `state: dead` can match revival. An explicit `online: false` can match a disconnected participant's return when the collection is `participants`. Intrinsically online collections retain Q170's limits and will not include a participant who was offline before reconnecting. The editor must explain this snapshot rule beside lifecycle-source filters.

Roles and other supported retained state can be inspected in that snapshot. Offline position and health remain unavailable live measurements; an area filter cannot infer current occupancy from an old logout position. Unknown or unsupported fields fail validation. Later `if` guards use current state as usual, while `event_value` inspects the captured before/after fields. These defaults do not replace the separate pre-operation contract for `damaged` and `healed`.

### Lifecycle boundaries and cleanup

Queue causal notifications after their native or Conclave operation finishes. A lethal damage operation reports its accepted `damaged` event before `died`; a transition directly from death into passed-out state reports `died` before `passed_out`. Existing aura and relic cleanup keeps its own committed outcomes. Do not execute lifecycle listeners recursively inside death or recovery handling. Supported hooks and exact cross-module ordering still need implementation verification.

The accepted final-tick revival precedence remains authoritative. If revival commits, the competing window expiry does not also emit `passed_out`. Q237 qualifying admission within the original grace cancels that opportunity's expiry. A connection or `reconnected` event alone does not establish qualification. After grace has already expired, a later `reconnected` event cannot restore gameplay eligibility, revive a dead participant, reset the grace, or grant another assignment. The roster remains available for the accepted observation and eventual recovery policy.

[Q237-Q238](reconnect-admission-and-assets.md) accept the qualifying-return boundary and independent participation snapshots/events. Queue `disconnected`, `reconnected`, or `reconnect_grace_expired` before an associated `participation_changed`, using the same completed operation's snapshots. An already-online restoration that later qualifies emits only the participation change. Initial roster creation, teardown, or loading retained records emits no synthetic participation change.

Attempt-end recovery remains distinct from `revived`, and cancelling an attempt is neither `died` nor `passed_out`. Ended subscriptions cannot observe later recovery as though their attempt were still running. Loading retained state after restart, initializing a rule, or publishing manifests does not replay historical transitions. A new phase only subscribes to future eligible events.

The server-wide revival module can maintain its own lifecycle records outside attempts. This proposal adds no general world-rule scheduler, outside-player subscription, synthetic join event at attempt start, or respawn-driven encounter restart. Clients receive only the existing authorized presentation and information; lifecycle event payloads remain server-side gameplay data.
