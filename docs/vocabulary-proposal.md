# Conclave vocabulary proposal

Status: the naming decisions and role/aura distinction below are accepted. The YAML schema and detailed behavior remain under discussion. This document does not define the final schema or the full implementation of every example.

## Accepted foundation

The user accepted a small standard library of reusable gameplay capabilities. Encounter, arena, area, location, NPC, relic, role, aura, attempt, phase, mechanic, objective, rule, and participant have meanings in [the glossary](../CONTEXT.md).

## Naming and identity

Accepted.

Use lowercase `snake_case` for YAML keys and authored identifiers. A configured mechanic's `id` names that particular mechanic, its `type` selects the behavior, and an optional `name` supplies a player-facing label.

Illustrative mechanic fragment; the enclosing manifest uses the accepted `schema: 1` and kind wrapper:

```yaml
id: north_capture
type: capture
name: North plate
area: north
```

Here `capture` is a reusable behavior type; `north_capture` is an authored identity. Keep one canonical name per capability. Namespaced definitions, `type` versus `use`, parameters, arena bindings, and scoped-state references are accepted in [manifest references and reuse](manifest-references.md).

## Accepted starter mechanic names

| Type | Intended meaning |
|---|---|
| `capture` | Track progress toward capturing an area by meeting an occupancy requirement. |
| `deliver` | Track a carried relic through delivery to a destination. |
| `interact` | Track required player interactions with designated objects. |
| `defeat` | Track defeat of designated combatants. |
| `match_pattern` | Track whether submitted values match a required pattern. |

`capture` describes a mechanic with progress and completion. Instantaneous occupancy can also be exposed as a condition. Incomplete capture progress resets when occupancy no longer qualifies by default; authors may choose pause or decay instead. Completed capture remains complete until restarted.

`deliver` names the intended completion event. Merely carrying a relic is a state that conditions can inspect; pickup and drop are events. A relic has one holder, with interaction-based delivery by default and explicit automatic area-entry delivery available. Holder-death behavior is configurable as drop, immediate return to the initial position, or disappearance. Q180 accepts the separate opt-in respawn settings for causes, delay, and return location; cleanup cancels pending returns. Q181-Q183 accept the concrete pickup/drop controls, delivery syntax, and explicit lifecycle actions.

`match_pattern` is not restricted by its name to symbols. Ordered matching is the default, with an explicit unordered alternative that preserves counts. Shared and per-player progress and private clues are defined in [selection and patterns](selection-and-patterns.md). [Q191-Q192](pattern-definitions-and-inputs.md) accept the concrete token vocabulary, expected-answer forms, and direct interaction bindings.

This is a first naming group. NPC spawning, counters, participant assignments, timing, and presentation are also needed, but not every capability must be a mechanic type. Some belong in the action, condition, state, or presentation vocabulary. [Q89-Q92](conditions-and-composition.md) add `sequence`, `parallel`, and `repeat`, plus named counters and timers with their control actions.

## Accepted names for referenced objects

| Name | Meaning |
|---|---|
| `area` | A named region with volume, used for occupancy and spatial rules. |
| `location` | A named position, used for a destination, spawn, or marker. |
| `npc` | A named non-player entity participating in an encounter. |
| `relic` | An encounter-managed object players can carry and deliver. |

A boss uses the NPC vocabulary. The accepted term for grouped spawns is `spawn group`; its membership and defeat semantics are in [NPCs and spawning](npcs-and-spawning.md). Arena-local reference binding is accepted in [Q80](manifest-references.md#q80-encounter-to-arena-bindings). World-owned locations and optional anchors are accepted in [Q123](world-locations-and-assets.md#q123-location-anchors-outside-an-arena), with their concrete reference syntax accepted in Q127. Roles and auras have accepted meanings below.

The accepted block name is **Location anchor**. It is a placeable invisible authoring block for player recovery, entity spawning, and named areas, with YAML references and an in-game editor. Keep `location` for position references and `area` for region references. The user also requires toggleable area visualization in Creative mode. See [Location anchors](location-anchors.md) and the [area contract](areas.md).

## Accepted event and action naming

Actions use verbs that request an operation, such as `spawn` and `despawn`. Events state what happened, such as `spawned`, `despawned`, and `completed`. A rule names the event's source explicitly so `completed` refers to a particular mechanic rather than an unscoped global event.

This convention does not mean every verb has a matching event or every event has an action. Each capability must document the exact actions, events, and conditions it supports. [Q84](phases-and-rules.md#q84-event-rules) defines the accepted `on` source/event, optional `if`, ordered `do`, and typed event-field references. Per-capability payload fields and the full action catalog remain open. The supported NPC vulnerability action is named `set_vulnerable`.

[Q141-Q146](npc-stats-and-combat.md) accept concrete NPC stat fields, `damage` and `heal`, physical `size`, equipment using native slots, a `boss_bar`, and a 1,000,000-point authored health ceiling. The user confirmed `npc` as the base-type field inside an NPC definition. Native engine facts are recorded separately in [NPC combat research](npc-combat-research.md).

[Q147-Q151](combat-rules-and-health.md) accept `damage.type`, the armor-respecting `conclave:physical` default, detailed vulnerability control, `health` conditions, and explicit percentage amounts.

[Q152-Q155](combat-events-and-controls.md) accept `health_floor`, its control actions and `health_floor_reached`, resolved `damaged` and `healed` events, `set_stats` and `reset_stats`, and a separate damage `origin`.

[Q156-Q158](aura-effects.md) accept aura `modifiers`, explicit per-stack scaling, `damage_dealt`, `damage_taken`, `healing_received`, and separate `apply_status_effect` / `remove_status_effect` actions. Native status effects follow Minecraft merging and lifetime rules after application; aura modifiers retain Conclave ownership.

[Q159-Q161](aura-periodic-and-queries.md) accept aura `periodic` damage and healing, `contribution_removed` and `stack_removed`, and structured `has_aura` checks for stacks, remaining time, and timed state.

[Q162-Q163](npc-model-presentation.md) accept model equipment `attachments` and a native-renderer fallback. [Q164](event-conditions.md) accepts `event_value` for typed event comparisons and optional-field presence checks.

[Q165-Q166](reusable-mechanic-contracts.md) accept `type: layers` with private local orchestration and `export.events` for reusable mechanics. These are the user's chosen names; the behavior otherwise follows the accepted recommendations.

[Q167-Q168](mechanic-start-and-parameters.md) accept `started`, the event source `self`, and readable parameter constraints. Startup uses ordinary rules, and configuration remains fixed for its captured activation.

[Q169-Q170](roles-and-player-state.md) accept declared `roles`, explicit assignment actions, `choose.random`, `has_role`, and online/life-state filtering with `player_state`.

[Q171-Q172](role-and-player-events.md) accept role membership and participant lifecycle events, with explicit snapshots and lifecycle-source defaults that include dead and offline participants.

[Q173-Q174](capture-and-interaction-targets.md) accept concrete capture fields and typed interaction targets.

[Q175-Q177](interaction-input-and-progress.md) accept counted uses, individual holds, physical eligibility, and explicit native-interaction consumption.

[Q178-Q180](relic-identity-and-lifecycle.md) accept a managed relic carry model, explicit creation and live identity, and concrete death/disconnect/respawn fields.

[Q181-Q183](relic-interaction-and-delivery.md) accept pickup/drop controls, `deliver` destinations and triggers, and explicit relic lifecycle actions.

[Q184-Q186](relic-events-and-carry-auras.md) accept relic lifecycle events, `carrying` and `relic_state` predicates, and optional `carry_auras` with exact contribution ownership.

[Q187-Q188](relic-placement-and-appearance.md) accept stationary relic placement, `interaction_size`, and `appearance` using an item or supported model with an explicit item fallback.

[Q189-Q190](carried-relics-and-animation.md) accept optional `carried_appearance` and concrete `play_animation` targets, clip selection, speed, and logical playback.

[Q191-Q192](pattern-definitions-and-inputs.md) accept `tokens`, fixed/sampled/chosen `pattern` forms, `ordered`, and repeatable interaction `inputs` for `match_pattern`.

[Q193-Q194](pattern-progress-and-submission.md) accept `progress`, per-player completion requirements, `pattern_per_player`, and `submit_token`.

[Q195-Q196](pattern-feedback-and-controls.md) accept matcher feedback events and `reset_pattern` for unfinished progress.

[Q197-Q198](pattern-presentation-and-clues.md) accept optional token presentation and an audience-controlled `reveal_pattern` HUD action.

[Q199-Q200](pattern-state-and-progress-display.md) accept `pattern_state` queries and an optional `show_pattern_progress` HUD action.

[Q201-Q202](pattern-parameters.md) accept typed pattern configuration and direct-input parameters for reusable mechanics.

[Q203](world-pattern-clues.md) accepts `display: world` for `reveal_pattern`, with named-location placement and private per-viewer presentation.

[Q204-Q205](token-labels-and-world-progress.md) accept explicit `show_token` labels and world placement for `show_pattern_progress`. The [matcher reference](match-pattern.md) indexes the accepted configuration, gameplay, and presentation contracts.

[Q206-Q207](npc-lineage.md) accept how native NPC conversions retain group membership and how native living descendants acquire cleanup ownership without automatic defeat membership.

[Q208-Q209](npc-conversion-state.md) accept conversion state transfer and native descendant settings/reward defaults.

[Q210-Q211](npc-conversion-events-and-descendant-definitions.md) accept the group `converted` event and explicit native-child configuration through a `descendants` map of NPC-definition references.

[Q212-Q213](npc-spawn-events.md) accept per-member `spawned` and native-lineage `descendant_spawned` notifications.

[Q214-Q215](npc-adapter-scope.md) accept initial native NPC adapter targets and in-game compatibility inspection.

[Q216-Q218](npc-lifetimes-and-variants.md) accept native self-destruction defeat credit, owned-NPC distance-despawn prevention, and a typed initial `variant` block.

[Q219-Q220](npc-defeat-events-and-requirements.md) accept group `defeated` notifications, explicit fatal-source attribution, and cause-filtered completion over retained defeats.

[Q221-Q222](defeat-killers-and-damage-types.md) accept killer qualification at the fatal operation and explicit fatal damage-type filters, preserving retained defeat history.

[Q223-Q224](event-players-and-mechanic-results.md) accept explicit `on.player` selection and common mechanic result fields.

[Q225-Q226](npc-death-rewards-and-descendant-events.md) accept explicit NPC death-reward fields and subscriptions to verified native descendants.

[Q227-Q228](individual-npc-controls-and-hit-reactions.md) accept individual NPC controls and notifications for actual blocking or Conclave damage protection.

[Q229-Q231](model-lighting-bounds-and-item-appearance.md) accept model lighting, explicit visual bounds, and typed native item-model selection.

[Q232-Q233](durable-item-appearances.md) accept conservative appearance archival and ordinary item stacking and transformation rules.

[Q234-Q236](archived-appearance-delivery.md) accept selective appearance delivery, explicit client resource application, and bounded local-cache eviction.

[Q237-Q238](reconnect-admission-and-assets.md) accept the qualifying reconnect boundary and an independent participation state with selection and lifecycle notifications.

[Q239-Q240](reconnect-resource-restoration.md) accept automatic original-resource restoration before world entry, ordinary entry after failure, and explicit retry within the original grace.

[Q241-Q242](client-resource-failures-and-observers.md) accept proactive required-resource failure handling and explicit asset application for observation-only participants.

[Q243-Q244](phase-events-and-rule-order.md) accept named phase lifecycle sources and stable reaction order across rule scopes.

[Q245](ending-scope-presentation.md) accepts bounded final presentation through self-terminal rules and the encounter-attempt gameplay result schema.

[Q246-Q247](simulation-stages-and-outcomes.md) accept simulation-stage ordering and same-tick propagation of child results into live parent outcomes.

[Q248-Q249](completion-rewards-and-test-policy.md) accept an encounter-level `rewards` list and a separate Test payout policy.

[Q250-Q251](reward-recipients-and-delivery.md) accept reward-specific roster qualification and private delivery with personal pending rewards.

[Q252-Q253](reward-generation-and-review.md) accept completion-time generation with independent recipient rolls and operator review of uncertain transfers.

[Q254-Q255](durable-completion-and-reward-storage.md) accept durable completion before victory notification and protected pending-reward storage with early capacity reservation. Finishing encounter is operational status, not an authored phase or new mechanic type.

[Q256-Q257](fixed-reward-items-and-enchantments.md) accept fixed item quantities and XP fields, plus one explicit-enchantment map shared by configured real items. Reward display names remain separate from native item names.

[Q258-Q259](item-text-and-reusable-items.md) accept native item names/lore and an item definition with separate metadata and native stack properties. The glossary records Item definition separately from Item model.

[Q260-Q261](durable-item-localization-and-fonts.md) accept durable item translations and custom fonts, with the authored default text and ordinary font as explicit fallback behavior. Native item text remains ordinary disclosed item content.

[Q262-Q263](framework-delivery-and-extensions.md) accept the delivery plan and common Kotlin extension responsibilities. They add no new YAML gameplay primitive or speculative encounter content.

[Q266-Q267](area-fields-and-membership.md) accept `type` values for geometry, `include`/`exclude` composition, and `membership: position | overlap | contained`. These accepted spellings preserve the accepted terms area, location, and Location anchor.

## Accepted distinction between roles and auras

`role` means an optional named assignment for participants, such as runner or reader. A declared role can be empty. Rules can select participants by role. An aura can apply to a participant without any assigned role. A role does not automatically create a gameplay effect or display an icon.

`aura` means a named gameplay state attached to a player or NPC, such as a buff, debuff, or temporary encounter mark. An aura may have a duration, and rules can inspect it and react to its application, removal, or expiry. Source ownership, capped stacks, death removal, retained durations, and timer display are accepted in [runtime semantics](runtime-semantics.md). [Q117-Q118 and Q121-Q122](auras-and-world-lifetimes.md) accept player-owned applications, lifecycle events, and definition compatibility. The user's event additions include `applied`, `stack_gained`, `stack_refreshed`, and `contribution_refreshed`.

A relic is the carried object, a role identifies a participant's assignment, and an aura describes a state currently affecting that participant. These can coexist independently: delivering a relic might be assigned to a runner and carrying that relic might apply a timed aura.

## Accepted aura presentation

An aura displays an icon and its name on the affected player's HUD by default, plus remaining time when timed. Its description is authored text. Q279 accepts configurable `display.audience`, private holder HUD by default, shared player/NPC aura strips, and optional attached particles or rings. A rule checks aura state on the server; the client receives the state it is allowed to display.

Illustrative definition using the accepted naming, icon and holder-audience conventions; the executable schema remains implementation work:

```yaml
aura:
  id: unstable_charge
  name: Unstable charge
  duration: 15s
  display:
    icon:
      item: minecraft:amethyst_shard
    description: Deliver the relic before time runs out.
    audience: holder
```

This definition alone does not apply the aura. A rule would use the accepted `apply_aura` action on a selected participant, for example after a relic pickup event. A separate rule determines what expiry does; description text has no executable meaning. The example uses an already available item icon and does not require a new texture to be distributed.

A generic Conclave aura display is the accepted implementation direction. Whether a particular aura also applies a Minecraft status effect is a separate gameplay capability, not an implication of its name or icon.

[Q124](world-locations-and-assets.md#q124-client-assets-through-the-minecraft-workflow) accepts resource packs for authored art and audio, with in-game publication and transfer through the Minecraft connection. Q128 accepts stable logical asset references and the concrete `item` or `texture` icon forms. The user expanded Q128 to all supported presentation assets, including custom models and animations, sounds, dialogue and boss taunts, and ability cues. [Q125-Q126](aura-actions.md) accept applying, refreshing, and removing aura contributions.

[Q129-Q132](presentation-and-dialogue.md) accept named `dialogue`, `speak`, typed ability cues, explicit audiences, and the GeckoLib adapter direction. The user also requires reusable formatting templates and global, filtered-player, speaker-radius, and area audiences for text and sound. Use `area` in YAML for the user's zone terminology. [Q133-Q136](dialogue-formatting-and-delivery.md) accept `text_style` definitions, shared `audience` with nested `text.audience` and `sound.audience` overrides, dialogue scheduling, and translated presentation.

Fabric documents [custom HUD rendering](https://docs.fabricmc.net/develop/rendering/hud) and [server-to-client networking](https://docs.fabricmc.net/develop/networking). A generic client HUD driven by synchronized aura state is a design inference from those capabilities. Fabric's [custom mob effects](https://docs.fabricmc.net/develop/entities/effects) use registered effect types; the proposed Conclave aura state does not require each YAML aura to become one of those types.

[Q279](aura-and-world-visuals.md) accepts the remaining shared aura display and NPC presentation, using `display.audience`, which supersedes the earlier illustrative `visible_to`. It also defines the initial particle/ring behavior for the already accepted visual-effect capability. These presentation choices are accepted.
