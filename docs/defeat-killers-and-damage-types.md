# Defeat requirements for killers and damage types

Status: Q221-Q222 are accepted. They define killer qualification and fatal damage-type filtering over retained defeat records. Neither changes ordinary Minecraft combat. No implementation exists.

## Q221: killer selection at the fatal operation

Accepted: add optional `killer` to `defeat`, with exactly one `players` selector or named NPC `group`. Match the causing entity supplied by the fatal source under Q219. Omitting `killer` retains the accepted unrestricted attribution policy.

```yaml
objectives:
  - id: empowered_finish
    type: defeat
    group: guardians
    killer:
      players:
        where:
          has_aura: empowered
```

This fragment requires every guardian to be killed by a qualifying player who has the aura at the fatal operation. The usual `completion: all` default applies. The existing `causes` and completion fields remain available independently.

For a requirement that an allied NPC finish the targets, use the alternative:

```yaml
killer:
  group: executioners
```

The killer group and defeated group are separate references. The former identifies eligible causing NPCs; it does not change the latter's membership. This initial killer block has no direct-entity UUID strings, generic native-entity query, arbitrary predicate target, or implicit pet-owner lookup. Further source-selection forms need registered contracts.

### Attribution and selector meaning

An arrow credited to a player by the fatal source can match `killer.players`. A wolf remains a wolf and cannot qualify as its owner's player kill. A supported Conclave damage action with an explicit source uses that operation's actual attribution. A prior attacker, damage contribution, death-message name, or igniting player is not a substitute for a missing causing entity. Supported periodic aura damage remains unattributed under Q159.

Self-destruction has no fatal attacker under Q219 and therefore cannot pass a killer requirement. Reject a configuration that combines only `causes: [self_destruct]` with `killer`. When `causes` is omitted, its ordinary two-cause default remains visible, but only outcomes with a qualifying actual killer can pass this additional filter. The editor must explain that consequence.

Reuse the existing `players` collection and filter names. `killer: {players: {}}` means living, online participants with active admission in this attempt at the observation boundary. Authors may explicitly select `online_players`, including GMs, or `online_raiders`, and use supported online, life-state, and Q238 participation overrides. Explicit world-player collections keep their broad participation behavior unless restricted. These are eligibility checks for credit; an outsider's nonqualifying kill still happens in the world.

For player qualification, initially support collection membership, connection/life/participation state, the existing `area` and `role` filters, and `where` trees using `has_role`, `has_aura`, `has_effect`, and `player_state` against that same implicit player. Keep the existing supported aura stack, remaining-time, and timed checks. Permit `and`, `or`, and `not`; concise filters and `where` combine with AND. Do not add new predicate spellings for the historical context.

Validate this context explicitly. Its initial `where` cannot read arbitrary counters, timers, other mechanics, nested player collections, another explicit target, or an event field. Those would require a broader history contract. Ordinary live selectors and rule guards retain their existing wider capabilities. A typed `players` parameter is accepted only when its concretely bound contents satisfy these restrictions; its declared type alone is not proof of compatibility.

For `killer.group`, match the causing NPC's logical membership in that exact group activation immediately before the operation. Preserve conversion continuity, but do not retroactively transfer old body references or adopt an ungrouped descendant. A same-named later group is a different group. A declared pending killer group can qualify only outcomes after it actually exists and includes the attacker.

### Timing and retained qualification

Capture the needed eligibility and filter observations immediately before the fatal native operation starts. Commit the qualification only if that operation produces a real death. This means impact time for a projectile, not launch time, and the state before damage/death callbacks or queued rule reactions change it. A rescued target contributes no defeat record.

For example, a player launches an arrow without `empowered`, gains it before impact, and delivers the fatal hit. That can qualify. Gaining the aura only in a later `defeated` handler cannot qualify the earlier hit. Losing the aura after a qualifying hit cannot remove the credit.

Retain the qualification with the group's terminal record so an objective starting later can reach the same result. Never rerun its killer selector against today's player state. Normal `if` guards observing `defeated` still read live state under Q164; this historical requirement does not silently turn all guards into snapshot queries.

Bound qualification uses the captured revision, concrete parameter values, namespace/arena bindings, and actual role/group activation identities. An encounter-owned role can support an objective that starts later in that encounter. A role created only in a later phase or repeat iteration cannot establish a match for an earlier kill. A new role with the same name cannot borrow the prior role's membership, including through a negated role check. A branch relying on a role or group must find that same permitted binding at observation time; an independent known-true alternative can still qualify under the condition rules below.

Prepare the finite qualification configurations from validated encounter uses so observations begin when the referenced group can produce outcomes, even if a consuming objective has not started. This does not instantiate future phases or roles. Retain bounded match results and binding provenance needed by those configured consumers, rather than exposing arbitrary player-state history. Identical configurations may share observations only when all relevant bindings match; an authored objective ID alone is insufficient.

The implementation may retain compact observations where necessary to resolve an authorized later consumer, but it must preserve this same result and scope boundary. Do not preserve discarded private role contents as a new queryable archive. Losing required data that the adapter promised to capture is an integration failure, not permission to inspect current state or invent a match. Normal nonexistence of a future binding is an unavailable observation, handled below. Release observations with their group's owning lifetime, and include observation work and storage in the existing runtime budgets.

### Availability, completion, and privacy

A dead, offline, or non-active participant selected through the required explicit life/connection/participation overrides can qualify only when the fatal source actually supplies that identity and enough authoritative state is available to establish a match. This field cannot recover a projectile owner the platform no longer attributes, infer a current location from an offline cache, or reuse a replacement player lifecycle. Retained role, lifecycle, participation, and aura state can support the predicates whose existing contracts permit it.

Distinguish an observed false predicate from an unavailable observation. An observed absent aura makes `has_aura` false, so its ordinary negation is true. Unavailable native state or a nonexistent historical binding supplies neither truth nor falsity. For this historical qualifier, `not` preserves unavailable; `and` is false if any child is known false, true if all are known true, and otherwise unavailable; `or` is true if any child is known true, false if all are known false, and otherwise unavailable. Only a known-true overall selector qualifies. Apply the same AND behavior to collection and concise filters.

Thus an offline participant included by explicit connection and participation overrides who is known to hold a retained `runner` role can pass an OR between that role and a native-effect check whose state is unavailable. A negated effect check alone cannot pass merely because native state is missing. This availability handling belongs to the historical killer context and does not change existing live guards or `event_value` absent-field comparisons.

Preserve group activation and player identity across normal reconnects without allowing another attempt, later admission, or respawn to rewrite the past result. Reusing an encounter-owned group in a later objective intentionally reuses its eligible historical defeats; authors who need new kills create a new group activation.

Apply Q220's ordinary failure rules to nonqualifying defeats. A required all-members objective fails when one member permanently lacks an eligible killer. A count objective can still use later qualifying members while its group is open. Successful completion stays latched, and a killer filter cannot erase the underlying `defeated` notification or change native rewards.

The retained result is server-side gameplay state. It does not publish private aura/role contents, expose another attempt's internals, or grant a client a new event payload. A killer restriction does not make every group `defeated` event carry a required player, so it does not enable `per_player` on that event. [Q223](event-players-and-mechanic-results.md#q223-an-explicit-player-field-for-a-subscription) accepts an explicit subscription-level player choice. Assist scoring and damage-contribution rewards remain separate capabilities.

## Q222: explicit fatal damage-type requirements

Accepted: add optional `damage_types` to `defeat`, containing a nonempty unique list of exact registered damage-type IDs. Require the committed fatal operation's `damage_type` to match one listed ID. Omission imposes no damage-type restriction.

```yaml
objectives:
  - id: burn_guardians
    type: defeat
    group: guardians
    damage_types:
      - minecraft:on_fire
      - minecraft:lava
```

This fragment accepts either listed fatal type. Earlier fire damage followed by a differently typed fatal hit does not qualify. The field compares Q219's recorded `damage_type`, never the NPC's native `type`, a weapon name, an animation, or the name of an ability.

Reuse Q220's retained records and completion rules. A later-started objective reads the original fatal type, and a nonmatching terminal outcome can make its requirement impossible. The list is an OR within this field; `causes` and every other configured requirement combine with it using AND. Each member still contributes at most one defeat.

Missing fatal type does not match. Self-destruction supplies none, so reject `causes: [self_destruct]` together with `damage_types`. An omitted cause filter still has its accepted default, with the additional type restriction admitting only matching deaths. Do not manufacture a creeper's own death type from the explosion it caused.

Validate IDs and capture their registry dependencies with the attempt's existing external-data contract. Reject unknown IDs, duplicates, empty lists, tags, globs, and aliases such as `fire` or `physical`. This field does not add new damage types or an arbitrary tag language. Reuse registered typed parameter substitution only where it can validate the complete supplied value or individual supported scalar entries; no untyped list parameter is introduced.

Reading a type is distinct from authoring damage with it. A real native administrative death can have a type that is prohibited in an ordinary `damage` action, and matching that recorded type grants no bypass or ability to create such an attack. Native defenses, death protection, vulnerability, and reward behavior retain their existing contracts.

Damage-type matching is independent of killer selection. It can accept an unattributed environmental death when its type matches. When both fields are authored, the same terminal record must satisfy both.

## Related contracts

These contracts refine [accepted defeat records and completion](npc-defeat-events-and-requirements.md), [player collections](encounter-activation.md#q112-selector-fields-and-collection-defaults), [role and lifecycle state](roles-and-player-state.md), [aura queries](aura-periodic-and-queries.md#q161-aura-stack-and-remaining-time-conditions), and [typed parameter bindings](mechanic-start-and-parameters.md#q168-readable-typed-parameters-and-constraints). [Native fatal-source research](npc-combat-research.md#fatal-sources-and-native-kill-credit) supports the attribution distinction; it does not implement these history requirements.
