# NPC and arena boundary policies

Status: Q93 and Q97-Q99 are accepted. Q103 revises Q94-Q96 to preserve ordinary Minecraft behavior by default; targeting, confinement, and boundary responses remain explicit authorable capabilities. Wandering, completely disabled NPC AI, and changes through authored rules remain accepted. NPC definitions, spawn groups, actual-defeat tracking, scoped references, safe placement, and attempt cleanup are already accepted. These are behavioral requirements for later implementation and adapter verification, not claims that Fabric hooks or all installed entity types already satisfy them.

## Q93: spawning and reinforcing groups

Accepted: expose one `spawn` action with a named NPC definition, a location, a positive count defaulting to one, and a group ID. Resolve the definition and location from the attempt's pinned revision.

```yaml
do:
  - spawn:
      npc: raid_tools:shielded_guard
      location: north_spawn
      count: 3
      group: north_guards
```

An alternative form uses `anchor` plus `group` to activate a Location anchor's saved NPC, count, facing, and placement configuration. It requires that anchor to have valid spawn configuration. Use exactly one configuration form: explicit `npc`/`location` fields or a configured `anchor`. Do not silently combine conflicting settings from both. The anchor still does not spawn anything merely by being placed or loaded.

New groups are closed to reinforcements by default. `open: true` explicitly leaves a new group open. Further `spawn` actions use `into` instead of `group` to add NPCs to that existing open group. `close_group` closes it, and closing an already closed group is harmless. Adding to a closed or missing group is an error.

For example:

```yaml
do:
  - spawn:
      npc: raid_tools:shielded_guard
      location: north_spawn
      count: 2
      into: north_guards
  - close_group:
      group: north_guards
```

A public group ID is created once in its owning scope activation. Calling `spawn` with `group` again in that same scope does not replace it, even if its earlier members are dead. Use `into`, a distinct group ID, or a fresh enclosing mechanic/phase activation. Private groups in separate repeat iterations belong to separate activations. This refines the accepted later-ID-reuse rule without retargeting an existing objective.

An objective referencing a declared future group remains pending until that producer successfully creates it. It binds once, and completion records stay associated with that group. Undefined producers fail validation. An already created group's recorded defeats remain available for an objective bound to it; waiting for a new wave requires that wave's own group activation.

Check capacity and safe placement for the full requested batch. Announce group creation or reinforcement only after the batch succeeds. If creation fails partway, remove the partially created NPCs without defeat credit or loot, and leave an existing open group unchanged. Follow Q69 for explicit recoverable failures or a technical-error stop. This is a Conclave consistency contract, not a claim that arbitrary other mods cannot observe intermediate entity callbacks.

## Q94: targets and combat interaction

Revised by Q103: keep the selected entity's supported normal targeting, movement, and attack behavior unless the author configures another supported policy. Do not automatically restrict attacks, projectiles, damage, or assistance to one attempt's roster, and do not impose a Conclave friendly-fire default. Ordinary Minecraft and server policy govern those interactions. Encounter state is not automatic PvP protection or NPC immunity against outsiders.

Authors may explicitly select eligible targets or a supported targeting policy, such as retaining an eligible target and otherwise selecting the nearest matching player within range. Target selection changes this NPC's authored behavior; it does not create a global immunity rule for other players. Passive types do not gain a new combat AI merely from target filtering. Validate requested properties and controls against the entity adapter.

Q98-Q99 retain the accepted `default`, `wander`, and `disabled` AI modes and `set_ai`. Explicit aura, attack, mechanic, and vulnerability capabilities keep their typed target and ownership contracts. Their default selectors are not a blanket filter on unrelated Minecraft interactions. Graves and passed-out views continue to follow the separately requested revival and spectator modules.

## Q95: explicitly configured NPC confinement

Revised by Q103: an active arena does not automatically confine or return NPCs. An author may explicitly configure confinement to the arena or a smaller named area using the previously accepted capability. Normal roaming and movement otherwise follow the selected NPC behavior.

When confinement is explicitly configured and a living NPC escapes, return it to its resolved home spawn location using safe placement. Preserve health, auras, group identity, and mechanic progress, clear stale movement and invalid targets, and emit the attributable `escaped` event. This is neither a heal nor a respawn, and it never resurrects a defeated member.

Configured confinement regions and fallback positions must fit inside the pinned arena. Validate the authored geometry and actual placement at use time. An unusable required return follows Q69. Boundary detection and damage ordering still need verification for each supported movement kind.

A `wander` mode's roaming destinations can be constrained by its configured region without silently adding a teleport-on-escape policy. Q107 accepts bounded chunk retention. [Q265](movement-and-simulation.md#q265-follow-owned-npc-identity-while-simulation-is-available) accepts how to follow owned NPCs beyond it, including supported dimension transfers and confirmed loss of required simulation.

## Q96: explicitly authored player boundary responses

Revised by Q103: walking out of the arena does not automatically return, kill, wipe, forfeit, or otherwise punish a player. Remove the earlier default immediate return. Merely crossing a boundary does not clear player state or reset timers. Q104 rejects the proposed leave command and forfeit workflow.

An author may explicitly react to area entry, exit, occupancy, or a condition through supported actions. Previously discussed warning countdowns, safe returns, ordinary lethal damage, and wipe behavior are available only when deliberately authored. Such actions retain their normal lifetime, safety, revival, and outcome contracts. An authored countdown uses simulation time and can be cancelled on re-entry.

The player's position still affects mechanics whose authored eligibility depends on an area. Encounter roster tracking does not impose physical confinement. Global grave and spectator behavior remains the separately configured module contract, including optional unrestricted spectating. Relics retain their explicitly defined object lifecycle; that is not a movement restriction on their holder.

Q109 accepts retaining the captured roster and its state after movement until attempt end. [Q264](movement-and-simulation.md#q264-player-travel-preserves-participation-and-uses-dimension-aware-spatial-checks) accepts the remaining cross-dimension and remote-grave details, with no implicit movement barrier.

## Q97: NPC loot, equipment, and experience

Accepted: Conclave NPCs produce no item loot or experience by default, and their configured equipment does not drop automatically. Authors can opt into vanilla entity loot, select a supported registered loot table, configure equipment drops, and set an explicit experience amount. Q225 defines the field shapes and supported reward requirements; their native adapters remain unimplemented.

[Q225](npc-death-rewards-and-descendant-events.md#q225-loot-equipment-drops-and-experience) accepts independent `loot`, `equipment_drops`, and `experience` fields and their native eligibility behavior.

Generate configured death rewards once for an actual NPC death under the selected attribution policy. Cleanup, a failed spawn, confinement return, administrative removal, and group cancellation do not produce death rewards or defeat credit. A later attempt does not replay an earlier NPC's reward event.

Conclave loot choices and amounts are captured with the attempt. Applicable vanilla gamerules retain their vanilla administration, including the user's accepted ordinary player inventory and XP policy. Referenced external loot resources need a documented resolution and revision contract before being added to the supported catalog; a Conclave ID snapshot alone does not prove that an arbitrary external resource's behavior is pinned.

Legitimate item rewards become ordinary world items or player possessions according to the chosen delivery operation. They are not temporary relics and must not be deleted merely because the attempt's NPCs are cleaned up. Normal item behavior and gamerules still apply. [Q248-Q255](completion-rewards-and-test-policy.md) accept separate private completion rewards, including offline allocations and durable delivery/review; those contracts do not turn NPC death drops into private allocations.

## Q98: AI modes and their scope

Accepted: give an NPC definition one `ai` choice: `default`, `wander`, or `disabled`. Omission means `default`. This is the accepted Conclave behavior contract for wandering and fully disabled AI. It is not a claim that every entity's built-in AI exposes these modes unchanged.

```yaml
ai: wander
```

This is an NPC configuration fragment, not a complete manifest. `ai` and its three values are accepted.

`default` retains the entity adapter's supported normal behavior, with target filters or confinement applied only when explicitly configured under Q103. `wander` chooses reachable destinations in its configured roaming region and roams without acquiring combat targets, attacking, or retaliating. Navigation follows the supported walking, flying, or swimming movement kind. Ordinary supported idle looking may continue. A wanderer that cannot currently find a reachable destination idles and tries again within bounded work; it does not teleport merely to simulate wandering.

`disabled` stops autonomous navigation, looking, target selection, attacks, and other AI decisions. Remove or suspend existing autonomous paths and queued AI attacks when entering this mode. It does not stop the entire entity lifecycle. Health, damage, gravity, collisions, knockback, auras, animations, explicitly configured confinement, and authored encounter actions retain their own behavior. Use the accepted vulnerability controls to make an NPC immune to damage. This mode alone does not promise a physically immovable decoration or pause the encounter.

Validate each requested mode against the selected entity adapter. An adapter that cannot suppress a type's autonomous combat must not advertise `wander` or `disabled` as supported. Conclave-owned explicit actions can still operate on an AI-disabled NPC through supported capabilities. Fundamental new navigation or attacks continue to require a registered Kotlin capability.

## Q99: changing AI during an attempt

Accepted: add `set_ai` to authored rules, using the same modes that an NPC definition supports. This permits a phase to awaken a dormant boss or make a fighting group wander without replacing its entities. Use the accepted `default`, `wander`, and `disabled` values from Q98.

```yaml
do:
  - set_ai:
      group: guardians
      value: disabled
```

Resolve the group using the accepted scoped-reference rules and select its currently living members once for this action. Validate support for the requested mode across the selected members before changing them. Dead members stay dead. An already-created group with no living members is a harmless empty selection; an unknown or not-yet-created required group follows the required-action error policy. Changing an open group's current members does not implicitly override the NPC definition of future reinforcements.

Preserve entity identity, current health, auras, equipment, group membership, confinement home, and objective progress. Clear autonomous paths and combat targets when changing modes. Entering the same mode again is a no-op, so repeated rules do not continually restart navigation. Returning to `default` lets the supported AI acquire a currently eligible target normally. An adapter applies its advertised control before the next autonomous decision; inability to do so is an integration failure rather than a silent delay.

Switching modes does not undo damage already dealt, remove an already launched projectile, cancel separately authored timers or mechanics, or reset an NPC's health. Those resources keep their existing ownership and lifetime policies. Normal phase cleanup still removes phase-owned NPCs; an encounter-scoped NPC retains its current mode across phases until another action changes it.

Only rules in the attempt's pinned revision can cause these ordinary gameplay changes. Editing or publishing YAML still affects future attempts exclusively. This is authored runtime behavior, not permission for a manifest hotfix to mutate an active attempt.

[Q227](individual-npc-controls-and-hit-reactions.md#q227-use-the-same-npc-controls-on-one-typed-target) also accepts a typed individual NPC `target` for these controls, preserving their effects and ownership requirements.
