# NPC conversion events and descendant definitions

Status: Q210-Q211 are accepted. They follow accepted NPC lineage and state-transfer contracts. Q210 exposes a committed conversion to authored rules. Q211 configures a newly created native child. No implementation exists.

## Q210: a converted event for NPC groups

Accepted: add `converted` to the existing named NPC-group event source. Emit one notification for each member's successfully committed native single-conversion operation, after the replacement is inserted, required state is transferred, and the group resolves that member to its current body.

```yaml
on:
  source:
    group: guardians
  event: converted
if:
  event_value:
    field: type
    equals: minecraft:drowned
do:
  - apply_aura:
      aura: waterborne
      target: {event: target}
```

This rule fragment assumes a declared `guardians` group and compatible `waterborne` aura. It reacts to a conversion that already happened. The guard reads the admitted destination-type value; the action still requires its captured target to be available when executed.

### Typed payload

| Field | Meaning |
| --- | --- |
| `target` | Required typed NPC reference to the new body created by this conversion. |
| `previous_type` | Required registered native NPC type ID before conversion. |
| `type` | Required registered native NPC type ID after conversion. This event's `type` is not a damage-type ID or a Conclave NPC-definition reference. |
| `health_before`, `health_after` | Current health points immediately before conversion and after the committed transfer, respectively. |
| `max_health_before`, `max_health_after` | Corresponding supported effective maximum-health values, after applying the relevant modifiers at each snapshot. |

These are immutable event measurements. Use ordinary `event_value` comparisons with native registry IDs or health-point amounts as appropriate. Current conditions on `target` read current state instead. Do not infer a triggering player, damage attacker, optional cause string, old-body action target, or arbitrary native entity data. Consequently, this event does not support player-specific rule limits in its initial form.

If the NPC converts again or dies before a rule runs, the first notification keeps its original payload and target body. It cannot retarget the later body or resurrect the dead one. A subsequent action that deliberately selects the named group's current members uses that action's accepted current-selection semantics.

### Commitment and order

Admit the event through the ordinary server queue only after the complete supported conversion is committed. Queue `converted` before any `health_floor_reached` notification caused by its clamp, while both handlers observe coherent committed state. Native insertion callbacks alone are not success. A failed handoff produces the existing technical/recoverable error outcome and no successful conversion event.

Conversion emits neither an NPC death/defeat nor a new group creation, reinforcement, or scope `started` notification. The initial event covers verified single replacement even if a supported operation happens to retain the same native type. Splitting on death and creating an additional summon are separate operations under Q207 and do not emit this event for their parent.

Use the bound group activation, one committed operation identity, and ordinary declaration-order dispatch. Retried observation, client reconnection, model refresh, and restart cleanup cannot replay the event. An authored action in response is a new operation, not part of the original conversion transaction.

Ungrouped native descendants do not bubble conversions through an ancestor's group. This source observes actual members only. [Q213](npc-spawn-events.md#q213-notifications-for-native-descendants) accepts separate child-creation notifications. [Q226](npc-death-rewards-and-descendant-events.md#q226-explicit-subscriptions-to-native-descendants) accepts a separate descendant source and payload contract. No new cancellation hook or YAML `transform` action is introduced.

## Q211: configuring native children with NPC definitions

Accepted: add an optional `descendants` map to an NPC definition. Its keys are native child NPC type IDs and its values are ordinary Conclave NPC-definition references. Apply a matching definition once when that NPC directly creates a supported native living child.

```yaml
schema: 1
namespace: raid_tools
npc:
  id: summoner
  npc: minecraft:evoker
  descendants:
    minecraft:vex: lesser_vex
```

The referenced definition might be:

```yaml
schema: 1
namespace: raid_tools
npc:
  id: lesser_vex
  npc: minecraft:vex
  name: Vault wisp
  stats:
    health: 18
    melee_damage: 4
```

These are illustrative definitions, not bundled encounter content. `lesser_vex` can also be used by ordinary YAML `spawn`. The descendant use configures the actual native child without creating a replacement or an additional entity.

### Exact selection and typed reuse

Require the referenced definition's base `npc` to match the map key exactly. This is configuration of a vex that native behavior created, not an instruction to turn every summon into another species. Reject unknown native types, missing definitions, duplicate keys, and unsupported parent-to-child creation routes. Do not add wildcards, first-match rules, expressions, or arbitrary nested override maps.

Resolve unqualified definition references in the parent definition's namespace. Keep the child's internal references in its own namespace and resolve any spatial requirements through the attempt's existing arena binding. Pin the entire dependency graph with the attempt, including conditional descendant paths and compatible assets. Publishing a new child definition affects future attempts only.

This map accepts a definition reference, not mechanic `use`/`with` syntax. It does not add parameters to NPC definitions or override their typed-reference rules. A descendant definition can declare its own `descendants` map for children it later creates.

### Apply configuration without replaying native creation

Let the native operation determine whether creation occurs, the number of children, their actual base type, position, native parent relationship, and native lifespan. Preserve species-specific creation state, such as a split child's internal size. Do not run the ordinary entity factory or native spawn initialization again.

Apply the selected definition's explicitly authored compatible fields once before admitting the child as successfully configured. Supported stats, name, AI, vulnerability, size scaling, equipment, appearance, boss bar, and reward configuration use their existing contracts. Omitted native gameplay fields preserve this child's actual native creation state under Q209; Conclave defaults such as no death rewards remain in force. Explicit `ai: default` remains an authored mode selection, whereas omission does not erase a native copied no-AI flag.

An authored `stats.health` initializes the newly created child's current health at its final configured maximum, as an initial configuration rather than a conversion or healing action. Without authored health, preserve native initial health subject to supported native clamping. Capture this child's own reset baseline after initialization; it never borrows the parent's baseline. Equipment omission preserves native initial equipment, a present map is the complete authored loadout, and `{}` clears that initial loadout. Do not restore it again later.

The selected definition can explicitly opt into supported ordinary death rewards under Q97. Parent reward opt-in still has no effect on the child's rewards. [Q225](npc-death-rewards-and-descendant-events.md#q225-loot-equipment-drops-and-experience) accepts exact loot, equipment-drop, and experience fields; this mapping does not bypass external-resource validation.

Do not copy the parent's aura contributions, current combat history, or explicit presentation playback. A child may use its own configured model and boss bar. Its configured entity-specific capabilities and known native conversion destinations must satisfy the same compatibility checks as an ordinarily spawned NPC. Unexpected native creation/configuration failures retain Q69 and verified cleanup ownership; partial native batches do not fabricate group creation or defeat events.

### Ownership, generations, and bounds

The child remains outside named spawn groups under Q207. It inherits the parent's attempt and owning scope for cleanup. Its selected definition becomes its own captured configuration, including the policy used for any later children. Changes directed at the parent's group do not reach it. This map adds neither `into` nor an automatic group name.

Lookup uses the direct parent's captured definition. If a type has no matching entry, create the native child using Q209 defaults. Do not search ancestors for a fallback mapping. An unconfigured child does not acquire a descendant policy merely because some catalog definition happens to use the same native type. A converted parent retains its captured definition and its map under Q208.

Allow references back to the same NPC definition or another already visited NPC definition. These are runtime selection references and do not recursively instantiate children during compilation or activation. A slime definition can therefore select itself for successive native generations without copying a different definition for every size. This does not relax the prohibition on recursive mechanic expansion. Traverse each reachable definition once for dependency validation, and keep normal entity, event, and creation-work budgets at runtime.

Declaring a mapping never causes spawning by itself and cannot reset native split size or lifespan to manufacture a new generation. A scope that is ending still suppresses or contains cleanup-generated descendants. Selected definitions cannot change cleanup ownership, adopt unrelated world entities, or bypass parent/child provenance checks.

## Related contracts and remaining scope

These contracts use [group events and committed state](combat-events-and-controls.md), [event-value comparisons](event-conditions.md), [namespaced references](manifest-references.md), [NPC lineage](npc-lineage.md), and [conversion state and child defaults](npc-conversion-state.md). [Q212-Q213](npc-spawn-events.md) accept ordinary NPC/child creation event contracts. [Q214-Q215](npc-adapter-scope.md) accept the initial native adapter targets and compatibility inspection. [Q225](npc-death-rewards-and-descendant-events.md#q225-loot-equipment-drops-and-experience) accepts reward-field schemas. Native integration and runtime verification remain unimplemented.
