# NPC defeat events and requirements

Status: Q219-Q220 are accepted. They define terminal notifications, fatal-source information, and cause-filtered completion over retained group records. Completion does not depend on subscribing to the notification. No implementation exists.

## Q219: one defeated notification per group member

Accepted: add `defeated` to a named NPC-group source. Emit it once when an actual member commits a qualifying terminal combat outcome under Q216. Use a required `cause` to distinguish `death` from `self_destruct`. Keep mechanic `completed` separate from an individual member's defeat.

```yaml
on:
  source: {group: guardians}
  event: defeated
if:
  event_value:
    field: cause
    equals: death
```

This rule fragment observes native deaths in the group. It needs an ordinary rule ID and actions to become a complete rule. Omitting the guard also observes supported self-destruction. There is no second group `died` alias for the same operation.

### Outcome fields

| Field | Meaning |
| --- | --- |
| `target` | Required typed historical NPC reference to the body that ended. |
| `npc` | Required captured Conclave NPC-definition reference for this member. Conversion preserves that definition. |
| `type` | Required native entity-type ID at the terminal outcome. |
| `cause` | Required `death` or `self_destruct`. |
| `damage_type` | Optional registered damage-type ID supplied by the fatal native operation. Absent for self-destruction. |
| `attacker` | Optional typed identity of the fatal source's causing entity. |
| `direct_source` | Optional typed identity of the fatal source's direct entity, such as an arrow. |

Use `damage_type` here because `type` identifies the NPC's native type, as it does in `spawned` and `converted`. The existing `damaged.type` still identifies its damage type; this does not rename that accepted field. Document the distinction in the event picker.

Capture fatal-source fields from the committed native operation. A normal arrow can identify its owner as `attacker` and the arrow as `direct_source`. An environmental death may have neither. Preserve a non-player causing entity as itself; a wolf's owner, earlier attacker, igniting player, or nearest raider must not replace it. Do not infer these fields from Minecraft's displayed death message or remembered kill credit. Q153's `damaged` attribution uses the same direct-source distinction for its observed operation.

A typed source identity does not promise a living NPC or player. Preserve its registered reference kind, validate every consumer against the subject kinds it supports, and never coerce a projectile into an NPC or a player. Presence checks can inspect a historical identity even when no supported gameplay action can use it. These fields grant no authority over an unrelated entity and expose no arbitrary entity state. Broader external-entity selectors and any new subject kinds need their own registered contracts.

For self-destruction, omit the fatal-source fields. Damage dealt by the resulting explosion to other entities belongs to their own combat events. There is no synthetic killing blow against the exploding NPC. [Q221](defeat-killers-and-damage-types.md#q221-killer-selection-at-the-fatal-operation) accepts killer selection and historical qualification. Player-only event fields and assist credit remain separate decisions.

The base event has no required triggering player. Without explicit Q223 subscription-level player selection, `per_player: true` is invalid, including when an `if` guard checks that `attacker` is present. A retained identity is not a player lifecycle, a live entity handle, or a reason to revive the target. Current-state predicates still read live state at dispatch; the outcome fields remain historical snapshots.

[Q223](event-players-and-mechanic-results.md#q223-an-explicit-player-field-for-a-subscription) accepts explicit subscription-level narrowing with `on.player`, while keeping this base event contract.

### Commitment, ordering, and scope

Commit the member's terminal record and update already-bound defeat requirements before dispatching reactions. A later guard cannot cancel the death or change its classification. For one lethal operation, queue any resolved `damaged` and threshold notifications before `defeated`, then resulting mechanic completion or failure notifications. A supported death-split child's `descendant_spawned` follows its parent's committed defeat notification. Do not use a callback's incidental order as proof that these boundaries already hold in Fabric.

Each logical member can contribute one terminal record. Native conversion continues the member and does not emit `defeated`; subsequent death identifies the replacement body and current native type. A duplicate callback, later removal, or native death-effect notification cannot emit it again. Do not emit it for rejected death followed by native rescue, ordinary despawn, administrative removal, technical cleanup, failed creation, missing references, or unknown loss. An actual native administrative kill still follows Q149's native death path and counts as death; removal and cleanup remain distinct. Existing error handling applies to a required member lost without a qualifying defeat.

Group sources observe actual members only. Native descendants outside the group do not bubble their deaths into it. A group's notification is independent of a particular objective's cause filter, so an outcome can emit `defeated` while failing to satisfy that objective.

Keep the original attempt, group activation, and listening-scope identities. Subscriptions may await a declared producer, but subscribing after a recorded defeat does not replay its notification. Later-started objectives still inspect the retained group records under Q93. Publication, reconnect, client tracking, restart reconciliation, and later reuse of an authored group name do not replay terminal events.

An enclosing active rule may observe a terminal child result under the existing dispatch boundary; ending that listening scope cancels its remaining work. Forward private-group notifications only through a declared `export.events` mapping, preserving typed identities and ownership. No notification itself sends information to players, grants a reward, or bypasses explicit presentation audiences.

## Q220: cause filters and reachable completion

Accepted: give `defeat` an optional nonempty `causes` list containing `death`, `self_destruct`, or both. Omission accepts both under Q216. Add `completion: all`, `any`, or `{count: N}`, using the same readable completion forms already accepted for personal pattern progress. Default to `all`; require a positive integer count.

```yaml
objectives:
  - id: stop_guardians
    type: defeat
    group: guardians
    causes: [death]
    completion: all
```

This fragment requires every member of the captured group to die through a qualifying native death. A creeper that completes self-destruction has still been defeated, but that outcome does not meet this requirement. Leaving out `causes` counts either accepted outcome. Use `completion: {count: 2}` for two qualifying member defeats, or `any` for one.

Reject unknown or duplicate cause values, an empty list, invalid completion forms, and statically provable count requirements above a fixed closed group's total membership. A cause describes the terminal outcome, not a damage type, weapon, player, or native removal reason. A health floor, conversion, or ordinary noncombat removal is not a third selectable cause. [Q221-Q222](defeat-killers-and-damage-types.md) accept separate killer and damage-type qualification.

### Membership and retained records

Bind once to the declared group's actual activation. Count each logical member at most once, preserve the captured group's history, and do not change membership when applying the cause filter. A previously defeated member can qualify for an objective that starts later. Reading that record neither re-emits `defeated` nor reruns rewards. A new phase or mechanic occurrence does not change an old cause.

For `all`, require a nonempty closed group and a qualifying defeat for every member. Closing an open group still supplies the membership boundary; adding reinforcements cannot repair a required member's earlier nonqualifying outcome. For `any` or a count, complete as soon as the required number of qualifying records exists, even if the group remains open. Successful completion stays latched. Later members or nonqualifying outcomes do not revoke it.

A declared producer that has not successfully created its group remains pending, never an empty success. Descendants do not increase counts, conversion preserves one member, and unrelated future groups cannot satisfy this requirement. No cause filter changes native combat, shields an NPC from an outsider, or suppresses native death effects or rewards.

### Permanently impossible requirements

An active requirement terminates with ordinary mechanic failure when its retained membership and terminal records prove it cannot succeed. Do not label a real but nonqualifying combat outcome as an engine error or leave the objective waiting forever.

| Requirement | Result after a nonqualifying defeat |
| --- | --- |
| `all` | Fail immediately. That required member cannot produce a different terminal record, even in an open group. |
| `any` or count, group still open | Keep waiting while reinforcements remain possible. Existing deadlines and authored outcomes still apply. |
| `any` or count, group closed | Fail when qualifying defeats plus remaining nonterminal members are fewer than the required count. Otherwise keep waiting. |

Evaluate these rules when binding to existing records, after each committed member outcome, and when the group closes. Current health, distance, a temporary vulnerability lock, or speculation about future AI does not prove impossibility. A nonterminal NPC that currently seems unlikely to die can still qualify through later supported play.

This failure follows the existing required-objective and phase failure rules. A background mechanic reports its failure without automatically failing the phase. Normal same-tick success/failure precedence remains in force, and this contract adds no automatic wipe punishment or respawn. [Q224](event-players-and-mechanic-results.md#q224-common-mechanic-completion-and-failure-fields) accepts common failure fields, including `reason: unreachable`.

Keep technical failures separate. Unfulfilled spawning, an unexpectedly missing member, or required simulation loss still follows Q69. Do not reduce the required count to hide those failures. If the group is closed at runtime with too little membership to meet an otherwise valid count, its actual completion is unreachable under this rule. Reject the same situation before play whenever the pinned configuration proves it statically.

## Related contracts

These contracts extend [group creation and retained defeats](npc-and-boundary-policies.md), [accepted native outcomes](npc-lifetimes-and-variants.md), [combat events](combat-events-and-controls.md), [event-value conditions](event-conditions.md), [phase outcomes](phases-and-rules.md), and [queued execution](execution-and-errors.md). [Fatal-source research](npc-combat-research.md#fatal-sources-and-native-kill-credit) records the native facts separately from the accepted authoring policy.
