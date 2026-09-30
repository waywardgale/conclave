# Role assignments and player-state selection

Status: Q169-Q170 are accepted. Roles are optional participant assignments, distinct from auras and GM permissions. These contracts define concrete assignment actions and player-state fields, building on the accepted selectors, stable attempt membership, startup rules, and typed parameters. No runtime implementation exists.

## Q169: declared roles and explicit assignment

Accepted: declare roles in a `roles` list with `id` and optional `name`. A role begins empty. It belongs to its declaring encounter, phase, or private layers activation, like other declared state. Assignment does not implicitly create an undeclared role. Re-entering a phase or starting a new layers occurrence creates fresh local roles; encounter roles last through that attempt's phase changes.

```yaml
roles:
  - id: runner
    name: Runner
```

Use the existing short reference and explicit `{id: ..., scope: encounter}` convention. Within a reusable occurrence, short names resolve to its own declarations. A caller can pass a permitted role reference as a registered `role` parameter, retaining the caller's declaration and activation identity under Q168. This parameter refers to the role itself; a later selector sees its current membership. It does not expose other private declarations or turn a role into global server state.

### Assignment actions

Provide `assign_role`, `add_to_role`, `remove_from_role`, and `clear_role`. Each names one role. The first three require exactly one explicit `players` selector or typed player `target`. `clear_role` requires only the role reference and clears every member, including offline or dead members. Do not infer a recipient from an omitted selector.

`assign_role` replaces the role's membership with the selected set. `add_to_role` adds selected participants without removing current members; `remove_from_role` removes the selected participants. Existing additions, absent removals, clearing an empty role, and replacing a set with the identical set are no-change outcomes. Membership is an unordered set, not a ranking or an assignment of first and second places.

An assignment with no count requirement and a valid empty selection replaces membership with the empty set. This follows the replacement meaning rather than silently retaining an obsolete assignment. It differs from a per-recipient effect action that has nobody to affect. Authors can use `clear_role` to state intentional clearing directly.

Resolve the selected identities once and validate the whole mutation before committing it. Roles contain only participants in the current attempt. If an explicit selection includes somebody outside that roster, report the unsupported assignment before changing membership; do not enroll them, silently drop them, or affect another attempt. A role is not a Minecraft team or an administrative permission grant.

### Choosing a fixed random set

On `assign_role` with `players`, allow an optional `choose` block containing `random` and a positive integer count:

```yaml
assign_role:
  role: runner
  players: {}
  choose:
    random: 2
```

The explicit empty selector uses the accepted ordinary gameplay default of living, online participants with active admission. Choose uniformly without replacement from the validated candidate snapshot, then commit that chosen set as the new membership. A player cannot occupy two places in the same draw. Omitting `choose` assigns all selected participants. Initially, sampling belongs to this assignment action; a condition, ordinary player query, or `players` parameter never rerolls a random set.

A new explicit random assignment is a new draw and may choose the same players again. There is no hidden anti-repeat, balancing, weighting, or automatic rotation policy. An author can exclude an existing role or aura through ordinary conditions before drawing. `add_to_role` and `remove_from_role` initially operate on their explicit selected sets without their own sampling option.

Require the full requested count. Too few candidates produces a documented recoverable `insufficient_players` result and leaves the old membership unchanged. Do not assign a smaller group or clear the role as a side effect of that failure. The author can guard the operation with an existing count condition or handle its supported recoverable outcome under Q69. Unhandled required failure follows the existing technical-error policy; it is not an automatic gameplay wipe or endless retry. Statically invalid counts fail publication.

The server owns the draw and its committed result. Normal inspection and condition evaluation do not change it. A retry of the same operation must not repeat a committed mutation or secretly redraw after reporting success; a later authored invocation can deliberately request another draw. Record enough operation identity for diagnostics without broadcasting private role membership or random state to unrelated clients.

### Membership, eligibility, and visibility

A participant can hold several roles at once. Role membership survives death, passing out, and disconnection until explicit mutation or the declaring scope ends. Do not automatically replace an unavailable runner. Ordinary gameplay selectors still exclude unavailable members, while an explicit full-roster query can inspect the retained assignment. Reconnection restores access to that existing role, not a new assignment.

Keep the accepted concise selector filter `role`. Add `has_role` as its condition equivalent, with a concise role reference for an implicit selected player and a structured `role` plus typed `target` form when needed. It reads membership independently of whether that player is alive or online; the surrounding selector determines eligibility. Undefined roles are validation errors, and a valid empty role matches no player. Combine exclusions or overlaps through existing condition operators.

Role membership does not automatically apply an aura, display a HUD icon, change camera visibility, or grant GM authority. Authors use explicit presentation or aura actions for those effects. Gameplay rules and authorized inspection can read the assignment; clients receive only the information their presentation and privacy contracts permit. [Q171](role-and-player-events.md#q171-role-membership-events) accepts role-change events with committed membership snapshots.

## Q170: explicit online and life-state filters

Accepted: add independent `online` and `state` fields to player selectors. Accept `online: true`, `online: false`, or `online: any`; accept `state: alive`, `state: dead`, or `state: any`. Keep connection state separate from life state. A dead player can be connected, and a disconnected participant can retain a living lifecycle state.

[Q238](reconnect-admission-and-assets.md#q238-participation-independent-of-online-and-life-state) accepts the independent `participation: active|reconnecting|observer|any` filter. `any` means no restriction on this axis. Participation describes attempt admission, independently of camera mode, life, and connection. The example and defaults below incorporate that decision.

```yaml
players:
  from: participants
  participation: any
  online: any
  state: any
  role: runner
```

This selects every participant still assigned as a runner, including dead and disconnected members. Removing the `role` filter gives the full tracked roster. It does not include players outside that attempt, change eligibility for capture or interaction, or restore a disconnected native entity.

### Defaults by use

| Use | Default connection filter | Default life-state filter | Default participation filter |
| --- | --- | --- | --- |
| Ordinary gameplay using `participants` | `true` | `alive` | `active` |
| Ordinary gameplay using explicit world-player collections | `true` | `alive` | Unrestricted |
| Initial participant selection and pre-start world queries | `true` | `any` | Not available before an attempt exists |
| Presentation audiences | `true` | `any` | `any`, with existing audience/privacy restrictions |
| Participant lifecycle event sources | `any` | `any` | `any`, with coherent pre-transition matching |
| Completion-reward recipients | `any` | `any` | `any`, restricted to the captured participant roster under Q250 |

These defaults preserve the earlier contracts. The initial participant selection remains all online raiders when no narrower filter is authored, including players whose death state will cause admission to reject the selected set. Presentation can reach entitled dead participants. Ordinary participant gameplay selection uses living, online, actively admitted members unless explicitly overridden. An explicit `from` chooses the collection; these documented contextual defaults govern omitted eligibility fields, without adding an arena, dimension, or GM-status filter beyond that collection.

Explicit fields replace the corresponding default; concise filters and `where` still combine with AND. A collection named `online_players` or `online_raiders` intrinsically contains connected players. `online: any` does not expand it to offline accounts, and `online: false` with either collection is a contradictory request that fails validation. To query disconnected members, use the captured `participants` collection. This does not introduce a server-wide offline-player directory.

### Meaning of life state

`alive` means the player's current authoritative Conclave/native lifecycle is living. `dead` includes both the grave revival period and the passed-out state awaiting recovery. Those are distinct revival states but share the selector's dead category. A request for recovery is not itself a completed revival or respawn. An unavailable required lifecycle record must not be replaced by a guessed alive/dead value.

For a disconnected participant, use the retained authoritative lifecycle record. Do not simulate a loaded player solely to answer a state query or claim that offline native position and health are live measurements. Position, health, interaction, and mutation capabilities retain their own availability requirements. Selecting a dead or offline identity does not make ordinary healing revive them, let them contribute occupancy, or allow a live-entity action to operate on a missing entity.

Offer a `player_state` condition with the same fields, each optional but requiring at least one. Q238 adds `participation` when an attempt context exists; it is invalid in outside-attempt and pre-start queries. Explicit participation filters on world-player collections restrict them to this attempt's matching members, while `any` leaves the collection unrestricted. They combine with AND. Use the implicit player inside `where`, `any`, or `all`, or an explicit typed player `target` outside that context. Reject NPC subjects and ambiguous references. A missing subject makes the condition false; an unknown field is a validation error. As elsewhere, explicit `any` values mean no restriction on that axis, not a random choice.

Evaluate current state coherently under the existing condition contract. Player subscriptions such as `damaged` retain their documented pre-operation eligibility snapshot; this new filter does not erase a lethal event after the target dies. Aura retention, role membership, reconnect grace, grave cameras, spectator permissions, and attempt recovery keep their existing rules. The filter grants no additional private information and cannot change a captured attempt roster.

[Q172](role-and-player-events.md#q172-participant-lifecycle-events) accepts the lifecycle-source default shown above, including dead and offline participants so their transitions remain observable. Ordinary action and audience defaults keep their separate meanings.

[Q250](reward-recipients-and-delivery.md#q250-qualify-the-captured-roster-at-success) accepts the separate full-roster default for completion-reward recipients shown above. Qualification is captured at success; it does not change ordinary gameplay selection or grant physical mechanic eligibility.
