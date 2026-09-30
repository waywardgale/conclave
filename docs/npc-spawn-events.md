# NPC creation events

Status: Q212-Q213 are accepted. Both use the accepted group event-source syntax and typed rule actions. Q212 observes explicit group-member creation. Q213 observes native descendants without changing membership. No implementation exists.

## Q212: spawned notifications for explicit group members

Accepted: add `spawned` to the named NPC-group event source. Emit one event per newly created member after an entire explicit `spawn` batch succeeds, including configured-anchor spawning and reinforcement through `into`.

```yaml
on:
  source: {group: guardians}
  event: spawned
do:
  - apply_aura:
      aura: charged
      target: {event: target}
```

This illustrative rule applies an aura to each new member, including later explicit reinforcements. The source group must be declared in an accessible scope. The aura must support the target type. This is not an NPC-definition-local rule or a new spawn action.

### Payload and batch meaning

| Field | Required value |
| --- | --- |
| `target` | Typed NPC reference to this newly created member's body at commitment. |
| `npc` | Captured Conclave NPC-definition reference used to configure this member. |
| `type` | Native NPC type ID at commitment. |
| `batch_index` | One-based index of this member within this successful spawn operation. |
| `batch_size` | Number of new members created by that operation, excluding older group members. |
| `reinforcement` | True for `into`; false when the operation creates its named group. |

Keep the group's activation and the operation's provenance in the existing runtime metadata. An index is meaningful only within that operation, not a reusable NPC identity. Do not expose a mutable group handle, an arbitrary entity object, or a list of every target. `npc` compares as an authored-definition reference; `type` compares as a native registry ID under `event_value`.

Every member's configuration, insertion, and membership must succeed before admitting any of the batch's `spawned` events. Queue them in batch creation order. A partial failure uses Q93 cleanup/error handling and emits no successful member-creation notifications for that failed batch. Retried observation of the same committed operation does not notify again; a separate successful reinforcement operation has its own events and index sequence.

The complete batch is visible to current group selection by the time a handler runs. Targeting `{event: target}` acts on one new member. Targeting `group: guardians` instead selects all currently applicable members, including older members; doing that in every per-member callback is an explicit repeated action, not automatic deduplication. Use `batch_index: 1` through `event_value` if a rule intentionally runs once per batch. `once: true` retains its whole rule-scope meaning rather than becoming once per batch.

### Lifetime and causality

These are queued notifications after creation, not a cancellable or blocking initialization hook. They run after the current action list under Q68. A rule cannot assume its event target still exists or has not converted when the handler executes. Immutable fields keep the committed measurements, and the native target never silently retargets another body. Put required initial properties in the NPC definition where that capability supports them.

Conversion uses `converted`, not another `spawned`. Native children use the separate Q213 contract. Reconnection, client tracking, model replacement, publication, restart cleanup, or merely subscribing to an existing group do not replay creation. The event has no triggering player, so player-specific invocation limits are invalid.

Subscriptions may be installed before the declared group is created. Preserve the accepted startup order, activation identity, private-group references, and cancellation of ended scopes. No successful event can revive an ended listener or promote an NPC beyond its cleanup owner.

## Q213: notifications for native descendants

Accepted: add the separate `descendant_spawned` event to a named NPC-group source. It observes a successfully created native living descendant whose verified lineage begins with an actual member of that group. Include later native generations while their original group activation and ownership remain valid.

```yaml
on:
  source: {group: summoners}
  event: descendant_spawned
if:
  event_value:
    field: type
    equals: minecraft:vex
do:
  - apply_aura:
      aura: unstable
      target: {event: target}
```

This fragment applies a compatible aura to each new vex in the group's native lineage. It works with Q209 native defaults or a Q211 selected child definition. It does not add the vex to `summoners`, change the group's defeat count, or make later `group: summoners` actions select it.

### Payload and lineage

| Field | Meaning |
| --- | --- |
| `target` | Required typed NPC reference to the new child's body. |
| `parent` | Required typed historical NPC identity of the direct native creator. It may already be dying or removed when the event is handled. |
| `type`, `parent_type` | Required native type IDs of the child and its direct parent at creation. |
| `npc` | Optional captured Conclave NPC-definition reference selected for this child by `descendants`. Absent for an unconfigured native child. |
| `generation` | Positive integer, with 1 meaning a direct child of the group's member, 2 a child of that child, and so on. |

Use exact scalar comparisons and optional-field presence checks through `event_value`. A present `npc` identifies the child's own selected definition, never an ancestor's definition. The event does not provide a triggering player, promise a live parent target, expose a private group handle, or provide arbitrary entity state.

Bind lineage to the actual root member and group activation at native creation. A root conversion preserves that logical membership; an old historical body reference still cannot retarget its replacement. A dead root member's valid lineage can continue to produce events while its owned descendants, group record, and listening scope remain active. Ending their owning scope still ends the descendants and notifications.

Do not infer ancestry from location, entity tags, scoreboard teams, damage attribution, or matching definition IDs. Separate author-driven `spawn` operations do not become descendants merely because a rule was triggered by an NPC. Each native child has one verified originating group lineage under the current contracts and emits one notification there. Reusing an authored group name in another activation cannot inherit that lineage.

### Creation, failure, and privacy

Admit the event only after native insertion, verified ownership, and any selected child-definition configuration succeed. A native birth is not a Conclave reinforcement batch. Do not add `batch_index`, `batch_size`, or `reinforcement` fields or claim one successful child proves every child from a native summon operation succeeded. Failure retains the existing adapter/error and cleanup contracts.

If native child creation occurs while an explicit root group's batch is still pending, retain its verified ownership but defer its notification until that root batch commits. Failure of the root batch emits no successful descendant-creation notification. This uses Q93's batch-commit contract independently of Q212's ordinary member notification. A native child created by a successful death split follows the parent's committed death/defeat notification in causal order; it does not fabricate a second parent death or defeat.

No event is emitted for conversion, rejected or failed creation, client tracking, state restoration, or children suppressed/removed as part of cleanup. Runtime entity, event, and work budgets still apply. Declaring a descendant map or subscribing to this event does not spawn anything.

Keep ordinary scope visibility. A caller cannot subscribe directly to a private group inside a reusable mechanic. Its author can explicitly forward this notification through `export.events`, including supported typed fields, without exposing the group handle or lengthening ownership. The forwarded target retains the same lifetime and the receiving action still checks authority and availability. Notifications stay server-side until an authored presentation action selects an audience.

This event describes lineage creation only. It does not route descendant `damaged`, `healed`, `converted`, or death events through the ancestor group. [Q226](npc-death-rewards-and-descendant-events.md#q226-explicit-subscriptions-to-native-descendants) accepts a separate explicit descendant source for those observations, without changing membership.

## Related contracts

These contracts extend [group spawning and reinforcement](npc-and-boundary-policies.md), [NPC lineage](npc-lineage.md), [native descendant definitions](npc-conversion-events-and-descendant-definitions.md), [event values](event-conditions.md), [queued execution](execution-and-errors.md), and [private reusable interfaces](reusable-mechanic-contracts.md). Exact native adapter coverage and broader owned-NPC event selection remain separate from these notifications.
