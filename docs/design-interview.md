# Conclave design interview

Status: shared understanding confirmed by the user on 2026-09-30. The interview is closed, the product decision frontier is empty, and implementation is authorized in the accepted development stages.

## Confirmed brief

- Conclave is a Minecraft mod for Fabric, implemented in Kotlin.
- Authors describe encounters declaratively in YAML manifests.
- The framework should cover a broad range of raid-style encounters, with most designs requiring no code changes.
- Developers should be able to add mechanics without complicated changes to the framework.
- Manifests must remain readable for humans and AI agents and must not expose generic command execution.

The selected Minecraft Java target is 26.2. Dependency compatibility must be verified for Fabric on that version.

The supplied YAML illustrates desired capabilities. It does not yet settle the schema, defaults, or runtime behavior. The examples place `phases` at different nesting levels and use both `objectives` and `mechanic`. Q70 and Q83 later settle the framework's layout and distinction between objectives and background mechanics.

## Product boundary

Conclave ships the framework. The user authors encounters separately, and later encounter designs can motivate extensions to Conclave. No complete sample encounter is required as a shipped feature or as the implementation target for this design session. Hypothetical gameplay scenarios may clarify framework semantics without becoming bundled content.

## Consolidated design for confirmation

Conclave is a Fabric/Kotlin encounter framework targeting Minecraft Java 26.2. The server owns gameplay, and every connecting client needs the matching Conclave build for its global revival, camera, HUD and authoring behavior. YAML and supported resources describe encounters; trusted Kotlin extensions add capabilities when the existing vocabulary cannot express a design. A finite initial catalog does not promise every possible mechanic without code.

The five core mechanics are `capture`, `deliver`, `interact`, `defeat` and `match_pattern`. Authors combine them with typed conditions, actions, events, counters, timers, roles, auras and relics. Reusable parameterized mechanics support sequence, parallel work, repetition and `layers`; explicit `export` exposes public results. An encounter has multiple sequential or looping phases with one active phase, and each phase can run several mechanics. Untimed phases are valid. Duration, deadline and explicit outcomes retain their separate accepted meanings.

Encounter definitions are separate from arena placements and live attempts. Authors bind named areas and locations in an existing world and configure them through YAML or Location anchors. Arena boundaries organize geometry and ownership. Players retain ordinary Minecraft movement, interaction, combat and inventory rules. The initial roster is fixed and defaults to all online raiders, with author-defined filters available; ready checks and authored start conditions are available. Concurrent attempts may use separate arenas while ownership and participant conflicts remain validated.

Authors can edit YAML, configure anchors, inspect errors, Test drafts, manage assets and publish through Minecraft. Operators publish a complete validated revision for future attempts. Active attempts retain their definitions, policy and resources. Supported code-free hotfixes do not require a server restart. Existing persistent lifecycles retain their captured definitions, and incompatible changes receive explicit diagnostics. Code installation, asset application and durable storage conversion have separate contracts.

Conclave owns revival and spectating. Defaults are assisted instant revival, no self-revival, a 15-second combat revive window, full health and no damage protection. Global policy controls methods, timing, recovery and viewing; encounters may prohibit self-revival when globally permitted. Eligible dead players use a grave-bound camera, then the configured teammate/free viewing after passing out. Private information is withheld unless specifically authorized. Optional positive revival protection and shared aura/world visuals follow Q278-Q279.

The complete release includes the accepted NPC coverage and controls, persistent aura contributions, managed relic lifecycles, native items/equipment, models and animation, dialogue, sound, text styles, localization, fonts, HUD and world effects. Resource packs supply supported authored assets; new renderers or gameplay capabilities require installed code. Presentation audiences control disclosure, and cosmetics do not decide gameplay outcomes. Native items retain their required published appearance resources independently of recent content history.

Operator-granted GMs can inspect, prepare content, run Tests and use the accepted attempt/recovery operations. Publication, global settings, GM delegation, uncertain reward repair and stopped-world maintenance retain their specific operator authority. Completion rewards are recorded with durable success before victory announcement and paid through the accepted native delivery/review workflow. Cleanup preserves ownership, restart ends unfinished attempts without replaying rewards, and backup/restore handles native and Conclave data together. Storage upgrades use an explicitly prepared target transition and staged conversion before world admission.

All accepted capabilities remain the target for the first complete release. Development proceeds through the accepted runtime, authoring, gameplay, presentation and durability stages; intermediate builds disclose incomplete support. Developers use internal fixtures and disposable worlds to verify behavior. No playable encounter bundle, external authoring CLI, executable YAML, automatic inventory rollback or unfinished-attempt resumption is added. Arena copying remains v2. Installing executable updates and starting the server use its normal host lifecycle.

| Review area | Detailed accepted contracts |
| --- | --- |
| YAML, binding and rules | [Manifests/publication](manifests-and-publishing.md), [references](manifest-references.md), [phases/rules](phases-and-rules.md), [composition](conditions-and-composition.md) |
| Authoring and spatial tools | [Minecraft editor](in-game-authoring.md), [Location anchors](location-anchors.md), [area geometry](area-fields-and-membership.md), [start rules](encounter-activation.md) |
| Core mechanics and state | [Mechanics](mechanics-and-participants.md), [pattern matching](match-pattern.md), [reusable contracts](reusable-mechanic-contracts.md), [aura lifecycle](auras-and-world-lifetimes.md), [relic lifecycle](relic-identity-and-lifecycle.md) |
| Native gameplay | [NPC support](npc-adapter-scope.md), [combat](combat-events-and-controls.md), [player/NPC travel](movement-and-simulation.md) |
| Revival and viewing | [Revival](death-and-revival.md), [global settings/cameras](settings-and-spectator-controls.md), [optional protection](revival-protection.md), [reconnect admission](reconnect-admission-and-assets.md) |
| Presentation and resources | [Assets](world-locations-and-assets.md), [dialogue](presentation-and-dialogue.md), [aura/world visuals](aura-and-world-visuals.md), [retained item appearance](durable-item-appearances.md) |
| Administration and recovery | [GM operations](administrative-operation-contracts.md), [restart cleanup](cleanup-and-restart.md), [startup/content upgrades](content-upgrades-and-startup-recovery.md) |
| Durable rewards and maintenance | [Durable completion](durable-completion-and-reward-storage.md), [transfer review](reward-generation-and-review.md), [backup/restore](coordinated-backup-and-restore.md), [storage upgrades](durable-storage-upgrades.md) |
| Engineering and delivery | [Stages/extensions](framework-delivery-and-extensions.md), [code compatibility](code-compatibility.md), [event conventions](event-catalog-conventions.md), [domain glossary](../CONTEXT.md) |

These links are an entry point, not a replacement for the full accepted decision record. Some early sections below preserve the interview chronology; subsequent accepted refinements and the linked current contracts govern the final behavior. Shared-understanding confirmation accepts this combined target. Exact schemas, dependencies, storage and native adapters still need implementation and verification; a material conflict discovered there must be brought back for a decision.

## Accepted decisions

### Authoring boundary

YAML configures and combines existing mechanics into named definitions with parameters. Composition supports sequencing, simultaneous requirements, branching, and repetition. A gameplay capability not already provided by the framework requires Kotlin. Generic commands, embedded scripts, and unrestricted expressions are excluded.

See [the authoring decision](adr/0001-declarative-encounter-authoring.md).

### Client requirements

Conclave requires the client mod. Gameplay decisions remain on the server; the client provides encounter presentation.

See [the client decision](adr/0002-required-client-with-server-owned-gameplay.md).

### Encounter placement and concurrency

Separate encounter definitions, arena placements, and active attempts. One arena permits one active attempt, and separate arenas can concurrently use the same definition. YAML names an arena and defines its location and boundaries within an existing build. Arena copying is deferred to v2. Location anchors and an in-game editor are accepted below. Q266-Q267 settle the coordinate, geometry and membership fields.

See [the arena decision](adr/0003-separated-encounter-definitions-and-arenas.md).

### Phase orchestration

An encounter supports multiple sequential phases, with one phase active at a time; the user explicitly reaffirmed this after Q151. Routes can advance, branch, or return to an earlier phase. Mechanics provide concurrency within that progression. Phase-started mechanics and their temporary state end with that phase by default; explicit encounter scope permits persistence across phases. Completed phases use explicit transitions or explicit encounter completion, never implicit file order. Phases can be untimed, with no implicit time limit.

See [the phase decision](adr/0004-single-active-phase.md).

### Declarative behavior vocabulary

YAML can react to documented mechanic events, check typed conditions, invoke supported actions, and use limited named state such as counters and player assignments. A mechanic describes active gameplay behavior; an objective names a completion requirement; a rule connects an event and optional conditions to supported actions. A mechanic can run without blocking phase completion. Reusable definitions can encapsulate rules, so ordinary encounter files can reference them without repeating their internals.

The user accepted these distinctions with the expectation of broad encounter coverage. This is a composition model, not a guarantee that a finite built-in library provides every possible gameplay capability. Missing capabilities still require Kotlin extensions under the accepted authoring boundary. Later accepted contracts define the initial behaviors and extension responsibilities; the complete executable schema/catalog remains implementation work.

### Attempt cleanup and world ownership

Conclave cleans up attempt-owned entities, timers, and temporary effects on normal completion or wipe, and tracks its own block edits for restoration. Q103 supersedes automatic arena building/mining protection and other blanket world restrictions. Ordinary Minecraft interactions continue. Q119-Q120 settle restoration conflicts and interrupted-attempt recovery; Q273/Q276 settle startup and coordinated backup/restore. See [ADR-0013](adr/0013-encounter-state-preserved-minecraft-rules.md).

See [the cleanup decision](adr/0005-attempt-owned-cleanup.md).

### Participant policy

Conclave tracks a fixed initial participant roster for gameplay state, with no matchmaking or new-player late joining in the initial target. Q103 makes clear that this is not a world-access or combat permission system. Q105-Q106 replace the proposed invitation lobby with authored activation conditions; Q109 accepts all online raiders as the default tracked set, with author-configurable filters. Death uses the globally configured grave-and-revival system in [death and revival](death-and-revival.md). Encounters may prohibit self-revival only as permitted by global policy; they do not define separate copies of the revive timer. Disconnect grace remains 60 seconds of real elapsed time by default and retains participant identity and existing state. Detailed disconnect behavior is recorded in [mechanics and participants](mechanics-and-participants.md).

### Live hotfix requirement

The author can edit and publish YAML while players are in gameplay. Publishing activates changes for future attempts without restarting the server. Every current attempt keeps all the manifest definitions it started with; even balance changes never apply to an active attempt.

See [the content revision decision](adr/0006-pinned-content-per-attempt.md).

### Publishing YAML changes

Automatic reload is available during development, and one explicit publish action sends a complete revision to a live server. Validation covers YAML and its references before activation; invalid revisions leave the previous valid revision available for new attempts. Publishing must be easy and intuitive for both humans and AI agents. Q71 now requires the full workflow inside Minecraft, with no external CLI tools. The accepted editor and in-game transfer contracts are linked above. Development reload follows the same rule that running attempts retain their original definitions.

### Initial framework capabilities

The first version includes a standard library of reusable gameplay capabilities alongside the framework. These support separately authored encounter content. The five accepted core mechanic names are `capture`, `deliver`, `interact`, `defeat`, and `match_pattern`; their accepted behavior and defaults are recorded in the runtime and mechanic contracts. Executable schemas and native verification remain implementation work.

### Naming conventions and identity

Use `snake_case` for identifiers and YAML keys, `id` for authored identity, `type` for the selected behavior, and optional `name` for display text. Q70 defines the enclosing manifest structure; Q78-Q81 define namespaced definitions, reusable mechanics, arena bindings, and scoped references.

### Names for referenced encounter objects

Use `area`, `location`, and `npc`, replacing the proposed zone, point, and actor terminology. Retain `relic` for a carried encounter object, `role` for a participant assignment, and `aura` for a gameplay state attached to a player or NPC. Q92 defines named integer counters and timers; other authored state forms remain part of the capability catalog.

### Event and action naming

Actions use verbs such as `spawn`, while events describe what happened, such as `spawned`; an explicit source identifies which mechanic or NPC emitted an event. Q84 defines the accepted rule shape and typed event-field references. The complete event/action catalog remains open.

### Participant assignments and auras

`role` is an optional assignment such as runner or reader. `aura` is a buff, debuff, or encounter mark currently attached to a player or NPC. A relic is a carried object. A participant can have all three independently, and aura use never requires assigning a role.

### Visible auras

Declare an aura with an optional duration and display information, then apply it through a rule. By default its icon and name appear on the holder's HUD, along with remaining time for a timed aura. Visibility to other players is configurable, while particles and glowing rings are optional visuals. Expiry behavior is defined by rules, never inferred from display text. The concepts and presentation defaults are accepted; the sample YAML schema remains illustrative.

See [the vocabulary document](vocabulary-proposal.md) for the accepted names and illustrative aura example.

### Q20: phase completion

Require all objectives by default, with explicit composition for alternatives and sequences. The user also requires `any`, `or`, and `and` conditions. Q25 and Q88 define the accepted logical grammar. A phase without objectives needs an explicit completion criterion. Successful completion follows a declared transition or declared encounter completion.

### Q21: simultaneous success and failure

Resolve competing terminal outcomes in the same gameplay tick together. Success wins by default, with an overridable encounter setting to prefer failure. The result must not depend on which listener ran first.

### Q22: gameplay lifetimes

Phase-owned mechanics and state are cleaned up on exit, while explicitly encounter-owned mechanics and state can persist across phases. Everything temporary owned by the attempt is cleaned up at attempt end. Cleanup is distinguishable from natural aura expiry and must not trigger expiry-only consequences.

### Q23: aura reapplication

Refresh the existing aura's duration by default and keep one HUD entry. Explicit configuration can ignore reapplication or enable capped stacks. Q29 defines source ownership and Q65 defines stack durations, caps, death removal, and timer display.

### Q24: gameplay time

Use one server-owned simulation clock for capture progress, phase timers, and aura durations. A stalled simulation does not consume gameplay time. Operational timeouts, such as publishing and network grace periods, remain separate decisions. See [the clock decision](adr/0007-gameplay-simulation-clock.md).

See [runtime semantics](runtime-semantics.md) for the accepted Q20-Q24 behavior.

### Q25: condition grammar

`and`/`or` combine checks, `any`/`all` test collections, and `not` negates a check. Support typed, nested condition trees without embedded scripts. For three selected runners, `any` needs at least one to satisfy a check, while `all` needs all three. Q88 defines the accepted condition-tree syntax; Q61 defines empty collection behavior.

### Q26: timed completion and deadlines

`duration` completes a simple timed phase successfully, while optional `deadline` fails an unfinished objective-driven phase. The user's requested `deadline` name replaces the previously proposed `timeout`. Combined time/objective success logic must be explicit. Both fields are optional; untimed phases progress through their declared gameplay conditions, and may still contain timed mechanics or auras.

### Q27: capture progress

Incomplete capture progress resets when qualifying occupancy is lost by default, with explicit pause or decay options. Completed capture remains complete until restarted; continued occupancy can be required separately.

### Q28: relic delivery

Interaction at the destination completes delivery by default, with optional automatic area entry delivery. Delivery removes the carried copy and records completion. Authors can choose whether holder death drops the relic, resets it to its initial position, or makes it disappear with support for delayed respawn. Drop remains the default. Invalid drops or escape from the arena return it home. Respawn configuration is discussed below.

### Q29: aura sources

Refresh repeated applications from the same source, retain independent contributions from different sources, and clean up only the ended source's contribution. Keep one HUD entry and avoid duplicate non-stacking effects. This is the accepted refinement of the refresh default.

Q25-Q29 are recorded with the user's revisions in [runtime semantics](runtime-semantics.md).

### Q30: relic respawn configuration

Separate the immediate holder-death response from opt-in respawn configuration with explicit eligible causes, delay, and destination. Successful delivery and gameplay cleanup never implicitly schedule respawn; cleanup cancels any pending respawn owned by that scope. Exact field names remain illustrative.

### Q31: interaction completion

A single valid use of its target by an eligible participant completes `interact` by default. Authors can explicitly require repeated uses, distinct participants, or a held interaction. Reject out-of-range or ineligible interaction requests and avoid counting one input twice.

### Q32: defeat completion

Actual defeat of every designated target completes `defeat` by default; explicit count requirements allow alternatives. Target disappearance or cleanup does not count as defeat. Required targets are explicit, and unrelated later spawns do not silently expand the requirement. A specific killer is not required unless configured.

### Q33: pattern matching

`match_pattern` matches an ordered sequence of author-defined tokens by default, with explicitly unordered matching preserving counts. Wrong input resets progress and emits a mismatch event, while any punishment requires an authored rule. Fixed patterns and patterns sampled at activation from a declared pool are supported; sampled patterns remain stable during that activation.

### Q34: participant death and revival

Revised by the user: death creates a grave with a constrained third-person view and only the player's own information. Assisted revival is instant by default, self-revival is off by default, and combat has a global 15-second revive window. Outside combat that window does not exist. After passing out, revival ends until attempt-end respawn; global settings choose player viewing or unrestricted spectator mode and whether first-person viewing shares private information. All-revival, method availability, eligibility delay, helper interaction time, and permission for encounter self-revive bans are globally configurable. See [the replacement death decision](adr/0009-grave-revival-before-spectating.md).

### Q35: disconnect and reconnect

Reserve a disconnected participant's place for a configurable 60 seconds of real elapsed time, retain their attempt identity, and exclude them from active gameplay checks. Gameplay and aura timers continue while the attempt runs. Relic disconnect behavior has a separate setting with drop as its default. With no online survivors, a still-living participant's reconnect grace can keep the attempt open until grace expires, though gameplay failure conditions can end it sooner.

Q31-Q33 and disconnect policy are accepted in [mechanics and participants](mechanics-and-participants.md). The original immediate-spectating flow is superseded.

### Q36: spectator camera

The earlier immediate teammate-camera proposal was not accepted. During revival opportunity, the dead player stays in a grave-bound third-person view. Only after passing out does the globally configured teammate-view or unrestricted-spectator policy apply.

### Q37: spectator information

The player's own information is retained during grave viewing. Private watched-player information remains unavailable by default after passing out, but the user requires a global option to enable it. The earlier unconfigurable restriction is superseded.

### Game master administration

Conclave provides server administrative commands. Operators or the trusted server console manage GM membership through designated grant, revoke, and list commands; command blocks, gameplay roles, and YAML cannot grant authority. GMs can inspect and control attempts and perform explicit audited recovery. UUID membership persists and revocation applies immediately; content activation remains operator-only initially. See [game master commands](game-master-commands.md) and [the authority decision](adr/0010-operator-granted-game-masters.md).

### Owned revival and spectating modules

The user selected Conclave-owned revival and spectating modules after research found no complete fit through the supported APIs of the investigated Fabric 26.2 mods. Revival owns eligibility and recovery; spectating owns camera behavior and information access. Both retain the effective gameplay policy of the current attempt. No external revival or spectating dependency is selected. See [ADR-0011](adr/0011-owned-revival-and-spectating.md) and the [research evidence](revival-dependency-research.md).

### Q38: combat and scope

Accepted: revival applies server-wide. Encounter YAML explicitly declares combat; phases inherit that state unless they override it. Outside an attempt, default to noncombat behavior. Damage alone does not classify combat. Q43 permits normal respawn outside attempts, and Q44 defines transitions affecting existing graves.

### Q39: global and encounter precedence

Accepted: global settings cap available methods and permit encounter self-revive bans by default. Encounters cannot re-enable globally disabled methods or change the global timer. Eligibility delay defaults to zero. Active attempts retain their starting effective gameplay settings, including for deaths after a hotfix publication.

### Q40: no living helper

Accepted: an otherwise defeated attempt ends only when no allowed revival or supported recovery opportunity remains. Preserve allowed self-revival and living participants' reconnect opportunities until they expire. Other encounter outcomes still apply. With default self-revival disabled and no living helper or eligible reconnecting survivor, do not wait through an unusable window.

### Q41: initial GM command scope

Accepted: `/conclave gm grant`, `revoke`, and `list` require operator authority or the trusted server console and reject command blocks. GMs can inspect encounters, start/stop/restart attempts, force a revival, and restore a relic. Recovery explicitly overrides normal gameplay restrictions and records the administrator responsible. Membership persists by UUID without granting Minecraft OP; revocation is immediate. Content activation stays operator-only initially. Exact syntax beyond membership, storage, and RCON policy remain open.

### Q42: post-window viewing default

Accepted: teammate viewing is the global default after passing out. Unrestricted spectator mode and sharing the watched player's private information are separate global options, with private sharing off by default. Retain a grave-bound waiting view if no teammate is available while reconnect grace keeps the attempt open. During a revival opportunity, always use the grave camera.

### Q43: normal respawning outside attempts

Accepted: outside an attempt, allow indefinite waiting for help or a voluntary normal respawn away from the grave. If no revival method is available, offer normal respawning immediately. During an attempt, ordinary respawn continues to wait for attempt end.

### Q44: combat changes and expiry ordering

Accepted: consume the revive window only during combat simulation time. A noncombat death receives its full window on entering combat; leaving combat pauses the remainder and re-entering resumes it. Expiry remains final for that attempt. Valid revival completion on the final permitted tick wins over expiry. Outside attempts, capture effective Conclave global settings at death so publication cannot rewrite an existing grave's rules.

### Q45: inventory and grave contents

Revised by the user: vanilla gamerules govern ordinary inventory and XP behavior. Conclave adds no separate keep-inventory setting or grave inventory system. Preserve vanilla death consequences once without duplicating loss or restoration during subsequent recovery. Encounter relics retain their separate accepted death policies. Vanilla gamerule administration remains outside Conclave publication.

### Q46: revival settings and placeable recovery locations

Revised by the user: revival restores 100% maximum health and grants zero seconds of damage protection by default, with author configuration under the existing global policy. Normal revival uses the grave, with safe-location recovery for unusable death positions. Add placeable invisible blocks that act as respawn locations, can be referenced in YAML, and can configure entity spawning at desired locations. Each needs an overhead name label and an intuitive human-readable in-game configuration interface. Q48-Q57 settle Location anchor authoring and the initial area contract; Q58-Q59 refine spatial editing and events.

### Q47: helper eligibility and interrupted assistance

Accepted: only living, online teammates can help within an attempt; outside attempts, living players who are not in another attempt may help. The target must be online. Require line of sight and a global default reach of three blocks. If helping takes time, one helper performs it; release, lost reach or sight, damage, death, or disconnection resets unfinished progress. Helpers cannot pool progress. A disconnected dead player's combat timer continues and reconnect never resets it.

Q43-Q47 are recorded in [death and revival](death-and-revival.md). The user's invisible-block and editor request is part of the framework scope, not a bundled encounter design.

### Q48: Location anchor naming and physical behavior

Accepted with the user's name: one custom Location anchor block for recovery, entity spawning, and area configuration. It is invisible and has no collision during gameplay; edit mode shows its outline, facing, and name. Each has `id` and optional `name`; YAML keeps the existing location vocabulary and refers to the ID. Q57 and Q80 define arena ownership and encounter bindings.

### Q49: editor and YAML publication

Accepted: the in-game editor writes the same YAML draft data used by file authors. Save draft is separate from publishing. A published revision includes resolved anchor positions and spawn settings, and attempts retain their starting values. Physical anchor changes in occupied arenas wait until those arenas are idle, with draft previews available to editors. Current attempts never reread a moved block to resolve a later spawn. The same policy applies to area geometry. See [ADR-0012](adr/0012-shared-yaml-authoring-for-world-editors.md).

### Q50: label visibility and editor access

Accepted: operators and GMs can enter edit mode, see Location anchor labels, and prepare drafts; ordinary players receive no anchor authoring labels or configuration. GMs gain draft-editing authority while publication remains operator-only. Right-click configuration provides name and ID, use, position/facing, recovery or entity-spawn settings, a preview, and Save draft. The latest request extends configuration to areas and requires toggleable area viewing in Creative; the detailed viewing policy is accepted in Q55.

### Q51: anchor activation and recovery uses

Accepted: assisted revival normally stays at the grave; recovery anchors handle unusable grave locations and attempt-end recovery. Encounter phases or rules explicitly select recovery locations and activate configured entity spawns. Placing or loading an anchor does not activate spawning. Attempt ownership controls entity cleanup, and YAML authors can still use ordinary named coordinates without placing blocks.

### Q52: safe destination selection

Accepted: validate anchor destinations at use time. For player groups, allocate distinct valid nearby positions in stable order within a global default search radius of three blocks. Try an explicit ordered fallback list if necessary, retaining arena boundaries. If all destinations are unusable, keep affected players waiting and report the problem to GMs. Entity spawns follow the placement needs of the selected entity.

### Area authoring and Creative visualization

The user requires Location anchors to configure regions that YAML manifests can reference, plus toggleable viewing of those regions in Creative mode. Keep `area` as the earlier accepted YAML name for zones. Q53-Q57 accept the initial shapes, anchor relationships, viewing defaults, membership checks, and arena ownership below.

### Q53: initial area shapes

Accepted: box, vertical cylinder, and sphere, with readable dimensions in the editor. Support irregular areas by combining included regions and subtracting exclusions. Q58 defines coordinate origins, rotation, and geometry editing; exact YAML field names remain part of schema design.

### Q54: one anchor and multiple areas

Accepted: an anchor can define multiple areas with independent IDs and names. Attached areas use relative placement, so moving the anchor moves them in the next published revision. Standalone area definitions in YAML remain supported. A position reference and an area reference have distinct meanings.

### Q55: Creative viewing toggle

Accepted: a per-player Show areas toggle, off by default, available through a keybind and editor control. Creative players may view areas; operators and GMs may also view them in edit mode. Editing and publication keep their existing authority requirements. View current-attempt geometry when active and published geometry otherwise; authorized draft previews are visibly labelled. Show boundaries, names, and optional translucent fill without sending unrelated encounter state.

### Q56: inside-area checks

Accepted: use the player's feet position by default, with explicit alternatives for any body overlap or the whole body being inside. Included outer boundaries count as inside; Q266 explicitly removes excluded shapes and their boundaries. Overlapping areas are allowed, and the server uses the attempt's resolved geometry. Q267 names the per-area choices `position`, `overlap`, and `contained`. Gameplay eligibility remains separate from geometric presence.

### Q57: arena-owned references

Accepted: each arena owns its locations, anchors, and areas. Encounters refer to logical IDs, allowing several arenas to supply different placements for the same encounter. IDs are unique within each kind in an arena. Validate references before publication and attempt start. Q123/Q127 also accept separately owned world locations and explicit typed references for outside-attempt recovery.

Q53-Q57 are recorded in [areas](areas.md). The [Location anchor contract](location-anchors.md) records accepted Q48-Q52.

### Q58: geometry editing and rotation

Accepted: boxes and cylinders extend upward from a base position, while spheres use a center. Offer two-corner box selection and readable numeric controls. Support horizontal rotation for boxes and attached offsets; turning an anchor turns its attached areas. Keep cylinders upright. Q266 accepts the concrete field names and horizontal-center base convention.

### Q59: area initialization and movement events

Accepted: initialize a mechanic's occupancy immediately, so players already inside count without moving. `entered` and `exited` report subsequent spatial changes. Teleports compare origin and destination rather than crossing intermediate areas. Cleanup does not emit gameplay exit events; death and disconnect affect eligibility separately from geometric movement.

### Q60: selecting players

Accepted: typed filters for area, role, aura, and participant state. Gameplay selections default to living, online participants in the current attempt, with an explicit full-roster choice. Each action resolves its recipients once. Random choices occur only when explicitly requested and stay fixed for their assignment or mechanic until replaced.

### Q61: empty selections

Accepted: both `any` and `all` are false for an empty selected collection. Use an explicit zero-count check when absence is intended. Empty `and` or `or` condition lists are validation errors. Recorded defeat completion and explicit phase completion retain their existing semantics.

### Q62: pattern progress and clue disclosure

Accepted: shared pattern and input progress per mechanic activation by default, with per-player progress available explicitly. Authors separately declare eligible input interactions and who sees the full answer or selected clues. Server validation and targeted disclosure keep private clues separate from shared progress. Wrong input resets the applicable progress under the accepted mismatch policy.

Q58-Q59 are recorded in [areas](areas.md); Q60-Q62 are recorded in [selection and patterns](selection-and-patterns.md).

### Q63: NPC definitions and supported behavior

Accepted: start from existing Minecraft or installed-mod entity types and their existing AI. Offer typed, supported settings for attributes, equipment, name, drops, and optional boss bars, plus supported targeting, movement, AI, and vulnerability controls. YAML composes existing gameplay capabilities; fundamentally new capabilities require Kotlin extensions. Validate unsupported entity/property combinations explicitly.

### Q64: spawn groups and defeat tracking

Accepted: bind `defeat` to a named spawn-group activation with fixed membership by default. Later reinforcements form a new group. Authors can explicitly keep a group open for ongoing waves and close it when finished spawning; all-member defeat then requires closure and actual defeats of all required members. Disappearance and cleanup do not count as defeat, and partial spawn failures do not silently reduce requirements.

### Q65: aura death and stack behavior

Accepted: remove Conclave auras on death by default, with an explicit retain-on-death option. Removal is not natural expiry; retained timers continue. For stacking, use independent per-stack durations, one cap across sources, and ignore extra applications at the cap. One HUD entry shows stack count and next expiry; non-stacking aura time reflects the final remaining contribution. Existing same-source refresh and source-owned cleanup remain in force.

### Q66: attempt-end recovery

Accepted: regroup participants at the selected recovery anchor after success, wipe, or stop. Clear owned state, restore camera and game mode changed by Conclave, and refill health and hunger by globally configurable defaults. Inventory and XP continue to follow vanilla gamerules. Offline participants receive pending recovery when they return.

### Q67: reconnecting after grace

Accepted: after grace expires, ordinary rejoining of that attempt is unavailable. Retain roster history for recovery, allow observation on return under the spectator policy, and exclude the player from active gameplay. Reconnection does not reset grace. A timeout is not another death. After attempt end, complete any pending recovery on return.

Q63-Q64 are recorded in [NPCs and spawning](npcs-and-spawning.md), Q65 in [runtime semantics](runtime-semantics.md), and Q66-Q67 in [death and revival](death-and-revival.md).

### Q68: event order and phase transitions

Accepted: process events through a server-owned queue, matching rules in declaration order and actions in their written order. Later actions see earlier changes; an action's recipient selection stays fixed for that action. Generated events join the queue instead of recursively invoking rules. Collect success and failure requests for resolution at the end of the tick under the accepted precedence policy. Commit at most one phase transition per attempt per tick, with the new phase starting on the next tick. Stale events cannot act on a replacement activation.

### Q69: execution errors and limits

Accepted: distinguish normal gameplay outcomes from engine errors. A required spawn or action that cannot complete stops only its affected attempt with a technical-error result, cleans up owned state, and recovers participants. It does not run an authored wipe punishment. Authors may handle documented recoverable failures with an explicit fallback or bounded retry. Reject invalid manifests before activation and bound events, repeats, timers, and owned entities so a rule loop cannot consume the server indefinitely. Report the source file, authored ID, and cause to administrators.

### Q70: manifest layout

Accepted: use one named definition per YAML file, beginning with `schema: 1` and one enclosing kind such as `encounter`, `arena`, `npc`, `aura`, `relic`, or `mechanic`. Keep phases inside `encounter`. Definitions refer to stable IDs, independent of filenames. Permit inline one-off configuration and reusable definitions through explicit references and parameters. Use ordinary folders and reject unknown fields, duplicate keys, and unsupported schema versions with line-specific diagnostics. Q78-Q81 define the accepted reference syntax.

### Q71: all operations inside Minecraft

Revised by the user: the full authoring, validation, publication, history, rollback, and administration workflow must be available inside Minecraft. Remove the companion CLI and external SSH/SFTP workflow. Portable YAML and optional external file editing remain supported, but no external CLI is required or planned. Keep server validation, complete atomic revisions, operator-only publication, and application only to future attempts. Q73-Q77 accept the editor, connected-session transfer, draft collaboration, development publishing, and test attempts.

### Q72: rollback, retention, and conflicting publications

Accepted: give each complete revision a content-derived ID, optional label, and publication history. Allow in-game revision history and the accepted `/conclave rollback <revision>` shortcut to activate a retained compatible revision for future attempts. Retain the ten most recently activated distinct revisions by default, plus every revision still needed by an attempt or pending lifecycle operation. Reject a publication based on an outdated active revision so concurrent authors cannot silently overwrite each other's published work. Rollback affects Conclave content and gameplay policy, not permissions, mod binaries, vanilla gamerules, or world edits already made.

Q68-Q69 are recorded in [execution and errors](execution-and-errors.md). Q70-Q72, including the user's replacement of the CLI workflow, are recorded in [manifests and publishing](manifests-and-publishing.md).

### Q73: authoring screen and commands

Accepted: `/conclave` opens an authoring and administration screen, with in-game command shortcuts for editing, validation, publishing, history, and rollback. Include a complete YAML editor with highlighting, completion, templates, and clickable errors. Keep visual controls for anchors and areas. GMs prepare encounter-content drafts; operators publish and manage global gameplay settings. New capabilities contribute schema and help without requiring a bespoke visual form.

### Q74: saved drafts and optional local files

Accepted: saved drafts live as YAML on the connected server, and authoring requests use the existing Minecraft connection. No extra credentials or server port are required for this workflow. Include optional in-game Import YAML and Export YAML for local or AI-edited files. Import saves a reviewed draft without publishing. Preserve unsaved text across connection loss, and let authors resume server-saved work after reconnecting.

### Q75: conflicting saves and authored text

Accepted: check file and draft versions on save. When another author edited the same file, preserve both versions and show the difference instead of overwriting. Raw YAML saves preserve the author's text; form edits preserve comments and unrelated fields or explicitly show any preservation limitation. Use named drafts and explicit conflict resolution, without real-time collaborative cursors in v1.

### Q76: automatic development publishing

Accepted: provide an operator-only Auto-publish my saves toggle, off by default and scoped to one selected draft and connected session. Publish complete valid revisions after explicit saves or accepted imports. Stop on conflicting edits, an unrelated publication, permission loss, or disconnect. Every attempt still keeps the revision it started with. Ordinary Save draft remains separate from Publish when the toggle is off.

### Q77: testing unpublished content

Accepted: provide Test draft in a selected idle arena. Validate and pin the selected draft to a clearly marked test attempt without activating it for other attempts. Changes require restarting the test to take effect. Use the normal cleanup and recovery policies in the real arena; no copied arena or inventory rollback is implied. GMs can test encounter drafts, while testing changes to global gameplay settings requires an operator.

Q73-Q77 are recorded in [in-game authoring](in-game-authoring.md). The editor, transport, collaboration, development publishing, and draft testing are accepted design requirements, not implemented features.

### Q78: namespaced definitions

Accepted: retain a simple `id` and add optional document-level `namespace`, defaulting to `local`. Use short references within the same namespace and `namespace:id` across namespaces. Resolve references by their declared kind, never by filename or a search through all namespaces. Reserve `conclave` for framework definitions and registered capabilities. Namespaces group names without creating permissions or separate publication boundaries.

### Q79: reusable mechanics and typed parameters

Accepted: use `type` for a registered capability and `use` plus `with` for an authored reusable definition. Require exactly one of `type` and `use` for each mechanic occurrence. Definitions declare typed parameters and optional defaults; the body binds them through `{parameter: name}`. Reject missing or unknown arguments and recursive reuse. Each use has independent runtime state, and customization uses declared parameters rather than inheritance or deep overrides.

### Q80: arena bindings

Accepted: each arena declares supported encounters. Their logical area and location references match arena IDs by default, with optional typed mappings when names differ. Derive requirements from the configured encounter and validate each pairing before publication and again before start. Bindings stay within the selected arena, and all resolved placements are pinned for the attempt.

### Q81: runtime references and scope

Accepted: distinguish references to reusable definitions from references to an attempt's active groups, mechanics, counters, and timers. A short scoped-state reference resolves within the current phase; an explicit `{id: guards, scope: encounter}` selects encounter-owned state. Never silently fall back between scopes or target another attempt. Reusable mechanics keep internal IDs private, and later activations do not retarget existing bound references.

### Q82: global settings manifest

Accepted: provide one operator-controlled Server settings page backed by a singleton `settings` manifest with `schema: 1`, without an authored ID or namespace. Keep global revival, spectating, and recovery gameplay policies there. Include them in the published revision under the existing attempt and outside-death snapshot rules. Vanilla gamerules and administrative state such as GM membership remain outside that manifest.

Q78-Q82 are recorded in [manifest references and reuse](manifest-references.md). The glossary now includes namespace, parameter, and arena binding.

### Q83: objectives and background mechanics

Accepted: write required `type` or `use` mechanic occurrences directly under `objectives`, and non-blocking behavior under `mechanics`. Declare each occurrence once. Also support a named objective with a typed `condition`, live by default with explicit `latch: true` for a recorded milestone. A phase requires all objectives by default; `complete_when` can explicitly replace that success condition, while `fail_when` adds an authored failure condition. A phase without objectives still needs an explicit completion criterion.

### Q84: rule shape

Accepted: use `id`, `on`, optional `if`, and ordered `do`. The `on` block contains a typed `source` and an `event`; actions use registered verb names with typed arguments. Permit typed event-field values through `{event: player}` only when that source/event supplies the required field. A source subscription follows valid events from its declared producer while preserving each event's activation identity; it does not retarget previously bound objectives or delayed actions.

### Q85: rule frequency

Accepted: run once for each eligible matching event by default. Add optional `once: true` and simulation-time `cooldown`. Limits apply per owning scope activation, with `per_player: true` available only for events with a triggering player. Reserve the invocation before actions run. False conditions consume no invocation, and later condition changes do not execute a rule without another matching event.

### Q86: initial phase and outcomes

Accepted: require encounter `start`, and explicit `success: {next: phase_id}` or `success: {complete: true}` on every phase. Default phase failure to `wipe: true`, with an explicit `failure: {next: recovery}` route available for authored phase failures. A wipe ends the failed attempt and performs recovery without creating an extra death. Returning to an earlier phase starts fresh phase-owned state while preserving encounter-owned state. Existing outcome precedence and transition timing remain in force.

### Q87: duration and numeric syntax

Accepted: use explicit duration strings such as `500ms`, `10s`, `2m`, and `1h`, with no bare duration numbers or arithmetic strings. Omit optional phase timing for untimed phases. Permit zero only where documented, such as instant revival assistance. Use integer counts, documented distance units in blocks, explicit percentages such as `100%`, and field-specific finite ranges. Show units and validation directly in editor controls.

Q83-Q87 are recorded in [phase and rule authoring](phases-and-rules.md). The glossary now distinguishes conditions, events, and actions.

### Q88: condition trees

Accepted: use one typed logical operator or predicate per node. `and` and `or` contain condition lists, `not` contains one condition, and `any`/`all` combine a typed collection selection with `satisfy`. Use named comparisons such as `equals`, `at_least`, and `at_most`; provide explicit collection-count checks. Preserve the accepted empty-selection rules and require side-effect-free evaluation with no random reassignment.

### Q89: sequence and parallel composition

Accepted: add `sequence` and `parallel` mechanic types with a nonempty `steps` list of ordinary `type` or `use` occurrences. A sequence starts its next step after the previous succeeds, no earlier than the next tick. Parallel starts all steps and defaults to `completion: all`, with `completion: any` for alternatives. Cancel unfinished children when the composition ends and retain existing ownership and engine-error rules.

### Q90: repetition

Accepted: add `repeat` with one body and exactly one stopping mode: a positive count, a typed `until`, or explicit `forever: true`. Run one iteration at a time with fresh local state. An optional `delay` separates successful iterations, and no next iteration starts in the same tick. `until` can stop an active body; ordinary body failure fails the repeat. Forever repetition serves background behavior and remains bounded by scope cleanup and execution limits.

### Q91: conditional routes

Accepted: use an ordered `choose` list with `if` conditions and a required `otherwise` destination. Select the first matching branch after resolving success/failure precedence and before cleanup. Each branch contains one route operation valid for that outcome. Freeze the selected destination; reject missing fallbacks and invalid targets. Direct routes remain the concise default when no branching is needed.

### Q92: counters and timers

Accepted: declare named integer counters and positive-duration timers in their owning scope. Counters default to zero and support set, add, and reset actions. Timers start with their scope by default, with `auto_start: false` available, and support start, restart, pause, resume, and stop actions. Timer expiry emits one event; authored rules decide its effect. Phase duration and deadline keep their existing outcome meanings.

Q88-Q92 are recorded in [conditions and composition](conditions-and-composition.md). The glossary now includes counters and timers.

### Q93: spawning and reinforcement

Accepted: `spawn` names an NPC, location, positive count, and group, or uses an anchor's complete saved spawn configuration. New groups are closed by default; `open: true` allows later `spawn` operations with `into` to add members, and `close_group` finishes the waves. A group ID is created once in its owning scope activation. Partial batch failure cleans up the new NPCs without loot or defeat credit and follows the accepted error policy.

### Q94: NPC targets and combat interaction

Revised by Q103: preserve normal entity targeting and ordinary Minecraft combat unless the author configures a supported NPC policy. Remove automatic cross-roster immunity, same-attempt damage filtering, and the Conclave friendly-fire default. Explicit target filters, movement, vulnerability, wandering, and disabled AI remain authorable capabilities. Q98-Q99 define the accepted AI modes and runtime action.

### Q95: explicit NPC confinement

Revised by Q103: remove default confinement and automatic NPC return merely because an encounter is active. Authors may explicitly configure a permitted area and safe return behavior. Such a return preserves health, identity, and progress, emits its event, and never counts as defeat or resurrection. Required placement failures retain the technical-error policy.

### Q96: authored player boundary responses

Revised by Q103: players can walk out under ordinary world rules without an automatic return, punishment, or forfeit. Authors may explicitly react to area events or conditions through supported actions. Existing revival, relic ownership, and selected-mechanic state remain separate; crossing the boundary alone does not reset them. Q104 rejects a leave command.

### Q97: NPC loot and experience

Accepted: disable NPC loot, experience, and automatic equipment drops by default. Allow explicit vanilla or supported loot-table rewards, equipment-drop settings, and experience amounts. Only actual NPC death produces those rewards once; cleanup and return do not. Ordinary rewards persist as normal items, while relics retain their separate ownership. External loot-resource pinning and broader victory-reward handling require explicit follow-up contracts.

Q93-Q97 are recorded in [NPC and arena boundary policies](npc-and-boundary-policies.md). The added wandering and disabled-AI capabilities and their detailed semantics in Q98-Q99 are accepted.

### Q98: NPC AI modes

Accepted: use `ai: default`, `ai: wander`, and `ai: disabled`. Default retains supported normal AI, with filters and confinement only when explicitly configured under Q103. Wander roams without autonomous combat. Disabled stops autonomous movement, looking, targeting, and attacks, while physics, damage, auras, and authored actions retain their own behavior. Validate adapter support explicitly; vulnerability remains separate.

### Q99: changing AI through encounter rules

Accepted: add `set_ai` for currently living members of a named group. Preserve identity, health, auras, equipment, membership, and progress. Clear autonomous paths and targets on a mode change; repeated assignment of the same mode is harmless. Future reinforcements still use their own definition. Existing projectiles and separately authored work retain their lifetime. Encounter-scoped NPCs retain their mode across phases. Only authored rules from the pinned revision affect an active attempt; publishing a YAML change still applies only to future attempts.

### Q100: arena coordinates and containment

Accepted: one dimension and a finite boundary per authored arena layout, using the accepted shapes and composition. Use world coordinates for standalone locations and anchors, plus the accepted relative offsets for attached areas. No additional arena-wide transform in v1. Validate complete containment of authored gameplay areas, locations, recovery, and spawn fallbacks. Explicit outside access positions are separate administrative placements, not encounter bindings. Q102 removes the proposed mandatory outside return point for outsider entry enforcement. Q264-Q265 preserve ordinary cross-dimension tracking and permit a runtime grave at a usable actual death location outside the authored arena. Keep runtime placement safety checks.

### Q101: overlapping arena definitions

Accepted: allow overlapping definitions, but prevent concurrent attempts whose pinned boundaries intersect, including shared boundary points. Reserve the stable arena identity as well as its resolved space through preparation, the attempt, and required world cleanup. Report the conflicting occupied arena. Publishing new coordinates does not move an active reservation. Offline pending recovery does not hold the arena forever, but must revalidate placement when the player returns.

### Q102: outsider admission

Revised by the user: do not add invisible walls, automatic pushback, or forced outsider return. Q103 extends this to ordinary Minecraft collision, combat, movement, and world interaction, and removes start refusal merely because other players are inside. Private presentation, explicit modules, authored mechanics, and administrative permissions retain their own contracts.

Q98-Q99 are recorded in [NPC and arena boundary policies](npc-and-boundary-policies.md#q98-ai-modes-and-their-scope). Q100-Q102 are recorded in [arena placement and admission](arena-placement-and-admission.md).

### Q103: ordinary Minecraft behavior

Revised by the user: active or inactive encounter status is system state. Keep ordinary Minecraft rules for movement, collision, pushing, projectiles, combat, assistance, building, mining, and ordinary items. Remove automatic outsider isolation, friendly-fire overrides, combat protection by roster, arena build protection, and default NPC/player boundary enforcement. Explicitly authored mechanics and the separately configured revival/spectator modules still operate. Participant tracking does not create a protected world. See [ADR-0013](adr/0013-encounter-state-preserved-minecraft-rules.md).

### Q104: no leave command

Rejected by the user: do not add `/conclave leave`. Remove the proposed command and its forfeit workflow rather than moving it into another mandatory interface. Ordinary movement continues; Q109 defines participation bookkeeping separately.

### Q105: author-controlled start conditions and ready check

Accepted from the user's revision: add a ready-check command and let authors define encounter start conditions in YAML. Support player counts in an area, a particular player entering an area, interaction with a configured target, and stacked custom conditions using the existing typed vocabulary. Remove the unaccepted invitation lobby, coordinating leader, and mandatory readiness flow. Keep `area` as the vocabulary. Q108 names the encounter-start rules `start_encounter`, separate from the initial-phase `start` field.

### Q106: counts through activation conditions

Revised through Q105: use typed `count` predicates and comparisons within authored start conditions. Do not introduce the proposed standalone `players: 6` or min/max admission field. Combine conditions when an encounter needs an exact count, range, identity, or readiness requirement. No implicit count-based difficulty scaling is introduced.

### Q107: arena chunk availability

Accepted: retain and tick the complete bounded arena footprint and required neighboring simulation throughout preparation, active play, and world cleanup. Verify readiness and capacity before starting; fail clearly rather than running a partially available encounter. Keep shared claims so one attempt's cleanup cannot unload another's required chunks. Release claims after cleanup and reacquire limited resources for later offline recovery. Unexpected loss of required readiness follows the technical-error policy. Exact limits and runtime integration remain implementation work.

Q103 and Q107 are recorded in [arena placement and admission](arena-placement-and-admission.md). Q104-Q106 are recorded in [encounter activation and ready checks](encounter-activation.md).

### Q108: encounter-start syntax

Accepted with the user's rename: use `start_encounter` for the named start-rule list, normalizing the spelling of `start_enocounter`. Rules have optional `on` for a registered event and optional `when` for the existing condition tree, requiring at least one. Condition-only rules observe idle world state; event rules evaluate their conditions for the matching event. Separate rules give alternative start paths and still create one attempt. Keep `start` as the initial phase. Reuse existing logical operators and count semantics.

### Q109: player selection and tracking

Accepted with the user's changes: provide filters for all online players, raiders excluding GMs, area, identity, applied auras, and other supported player state. Default to all online raiders across the server, with no implicit arena, distance, dimension, or living-state filter. Snapshot the selected identities at attempt start. This is gameplay bookkeeping, not a restriction on ordinary Minecraft interaction; walking away does not automatically forfeit or reset state. Q112-Q116 accept the selector fields and conflicting-state handling.

### Q110: ready-check command and HUD

Accepted with the user's HUD requirement: `/conclave readycheck <arena>` asks selected players to confirm Ready or Not ready in a popup HUD. Show pending responses and use a configurable 30-second real-time expiry. Readiness remains informational unless an author uses its result in a start condition. A passed result requires all selected recipients, is invalidated by recipient/relevant-revision changes, and is consumed at start. Q115 accepts popup input behavior without imposing a lobby.

### Q111: repeat activation and author responsibility

Accepted with the user's clarification: authors must design triggers and areas to avoid unintended restarts. A condition-only rule fires once when armed and satisfied, then needs a false-to-true change while idle to fire again after an attempt. Event-based starts require a fresh matching event. Deduplicate starts and do not fabricate entry events or queue active-attempt triggers for later. The engine does not add hidden cooldowns, movement changes, or extra conditions to repair authored logic.

Q108-Q111 are recorded in [encounter start rules and ready checks](encounter-activation.md).

### Q112: explicit selection collections and filters

Accepted: add `from: online_raiders`, `from: online_players`, and `from: participants`, with existing concise filters and a `where` condition tree. Initial participant selection and pre-start queries default to online raiders; active gameplay retains Q60's living online participant default. Explicit filters combine with AND, and `where` supports the existing logical operators. Selecting all online players includes GMs. Omitted area filters do not silently restrict the server-wide collection.

### Q113: raider identity and administrative changes

Accepted: a raider is a player outside Conclave's GM registry. No extra Raider permission or game-mode inference is needed, and operator status alone does not exclude a player. New live raider selections use the current GM registry, while an already captured participant roster remains intact. Administrative grants and revocations take effect immediately without clearing gameplay state or recovery obligations.

### Q114: applied aura and effect checks

Accepted: `has_aura` checks an existing Conclave aura; `has_effect` checks a vanilla or supported registered effect. Definitions alone do not count as applied state. Queries never create auras or assume an instance from an attempt that has not started. Preserve namespace, source ownership, lifetime, and private-state rules. Applying auras outside attempts requires a separate explicit supported application and cleanup contract.

### Q115: popup input behavior

Accepted: use a compact HUD popup naming the encounter/arena, with Ready, Not ready, and remaining time. Provide rebindable response keys and deliberate mouse focus without automatically taking the cursor or freezing movement. Dismissal leaves the response pending, and ordinary gameplay input never counts as consent. Show the answer and permit changes while the same check remains open and unused; the server rejects stale responses.

### Q116: conflicts in the selected player set

Accepted: select exactly what the author's filters request, then reject a start with a named reason if any selected player has an incompatible active attempt, unresolved death/recovery, or disconnects before commit. Never silently omit, revive, or teleport them. An author can narrow the set explicitly. Require a nonempty set and available server capacity. Being in another dimension alone does not make a living selected player invalid; the arena remains one-dimensional.

Q112-Q116 are recorded in [encounter start rules and ready checks](encounter-activation.md). The glossary distinguishes raiders, participants, Conclave auras, and status effects.

### Q117: player-owned aura lifetime

Accepted: permit explicit `scope: player` aura applications that survive phase and attempt cleanup. Apply them through a typed action or authorized in-game administration. Preserve source ownership, configured duration, death rules, and simulation-time behavior while disconnected. Persist them independently, and retain their applicable definitions rather than changing an existing application when YAML is published. Do not retain callbacks into an ended attempt. Ordinary encounter applications keep their existing scoped cleanup default.

### Q118: aura lifecycle events

Accepted: emit `contribution_expired` for a source's natural expiry and `stack_expired` for an individual stack. Emit aura-wide `expired` once when the holder loses the last contribution through natural expiry, or `removed` when the final loss is caused by death, cleanup, or explicit removal. A remaining source keeps the aura present. Use typed source and reason fields, stable ordering, and a single holder-level terminal event for a resolved change batch. The user additionally requires `applied` on aura application and `stack_gained` on stack gain. Q121 records their accepted first-application and reapplication semantics, including the subsequent `stack_refreshed` and `contribution_refreshed` additions.

### Q119: block restoration conflicts

Accepted: restore only edits whose current state and ownership still belong to Conclave. Preserve later player or unrelated-system changes and report the conflict. Track ordered ownership when multiple Conclave scopes edit a position, so cleanup cannot resurrect a finished scope's edit. Do not restore inventories or generate extra drops. Require verified adapters for complex blocks and expose unresolved cleanup through the in-game diagnostics, without reintroducing automatic build protection.

### Q120: server interruption recovery

Accepted: end interrupted attempts and recover them after restart rather than resume them mid-phase in v1. Retain minimal durable ownership and recovery records, clean up only confirmed Conclave resources, restore changed camera/game mode, and complete safe pending recovery without replaying rewards or death consequences. Preserve outside-attempt graves and their remaining simulation time; discard ready checks. Retain relevant start-rule rearm state, and release arena reservations once required world cleanup is finished without waiting indefinitely for offline players.

Q117-Q118 are recorded in [auras and world lifetimes](auras-and-world-lifetimes.md). Q119-Q120 are recorded in [cleanup and restart recovery](cleanup-and-restart.md), with the restart choice captured in [ADR-0014](adr/0014-recovered-interrupted-attempts-without-resuming.md).

### Q121: application and refresh event semantics

Accepted: emit `applied` when the holder goes from having no aura to having it, and `stack_gained` for each added stack, including the first. Add `contribution_applied` for a new source contribution and `refreshed` for an actual duration renewal. The user additionally requires `contribution_refreshed` for each renewed contribution and `stack_refreshed` when that renewed contribution is a stack. Emit the holder-level `refreshed` once per committed renewal operation on that aura. An ignored or unchanged application emits no successful-change event, and these events do not change the ignore-at-cap policy. Queue typed events in stable order, and never replay gain or refresh events when reconnecting or restoring persisted state.

### Q122: incompatible versions of an aura

Accepted: combine contributions only when their resolved aura definitions match. Keep existing applications unchanged; report a conflict when a new application of the same aura ID has a different definition. Detect conflicts before start where possible. The old aura can expire, be removed, or be deliberately replaced through authorized administration outside an active attempt. Presence filters still recognize the stable ID. Unrelated content-revision changes do not create a conflict when the aura definition itself is unchanged.

### Q123: world-owned Location anchors

Accepted: support named, namespaced locations and optional Location anchors outside arenas for explicitly configured global recovery uses. Use the same in-game authoring workflow. Keep arena-local bindings distinct, capture destinations for existing death/recovery lifecycles, and revalidate placement safety at use time. Placing an anchor does not automatically change ordinary spawn behavior or activate gameplay. Exact global reference syntax follows this ownership decision.

Q121-Q122 are recorded in [auras and world lifetimes](auras-and-world-lifetimes.md#q121-first-application-stack-gain-and-refresh). Q123 is recorded in [world locations and client assets](world-locations-and-assets.md#q123-location-anchors-outside-an-arena).

### Q124: custom client assets

Accepted after the resource-pack clarification: import and publish supported custom assets from Minecraft alongside YAML, transfer them over the existing authenticated game connection, and stage immutable local resource packs. Activate updates for each client only outside its active attempt and verify application before that client enters content requiring them. Retain version-specific asset IDs for older attempts and persistent lifecycles. Respect pack consent and report unavailable assets without changing ordinary play. New Kotlin behavior or native registrations require a mod update; no external asset-upload service or extra server port is required by this workflow.

Accepted clarification: use all three together. The mod supplies the installed engine and client capabilities, YAML configures supported encounter behavior, and resource packs supply the art and audio those capabilities use. A mod can bundle default resources, but authored assets remain publishable without rebuilding the mod. Pack format is separate from transport: custom transfer still installs resource-pack data and uses Minecraft's resource loading. The transport choice is recorded in [ADR-0015](adr/0015-resource-packs-published-through-minecraft.md).

### Q125: aura action family and recipients

Accepted: expose `apply_aura`, `refresh_aura`, and `remove_aura`, each naming an aura and exactly one explicit recipient form: `players`, `group`, or typed event `target`. Reuse existing selectors and group references. Keep duration, effects, stacking limits, and presentation in the aura definition; application may set the accepted ownership scope. Applying adds one stack by default, with an optional positive `stacks` count for a stacking aura. Refreshing affects existing timers only, and removal never counts as natural expiry. Do not add an ambiguous count-replacement action.

### Q126: deliberate cleanses and exact contributions

Accepted: explicit removal or refresh defaults to the named aura's current-attempt and player-owned contributions on the selected holder. Allow `contributions: attempt` or `contributions: player` to narrow this, or a typed `contribution` event reference to affect one exact contribution or stack. Preserve automatic owner-only cleanup. Never mutate another active attempt's contributions; report a conflict for a whole-aura operation rather than pretending it removed an aura still held by a foreign owner. Missing or unchanged eligible state produces no successful-change event.

Q125-Q126 are detailed in [aura actions](aura-actions.md).

### Q127: world-location definition and reference shape

Accepted: use a named `location` manifest with dimension, position, optional facing, and the usual identity fields. Reference it through `location: {id: raid_tools:hub, scope: world}` only where a world destination is supported. Keep ordinary encounter `location: entrance` arena-local. A placed anchor is optional, and neither spelling changes spawn behavior without an explicit setting or action.

### Q128: stable IDs across all presentation assets

Accepted: reference assets by stable namespaced IDs, with the publisher managing immutable versions internally. Use exactly one aura icon source, `item` or `texture`, such as `icon: {texture: raid_tools:auras/charged}`. Validate supported assets and dependencies before publication; reject ambiguous duplicate IDs. Old consumers retain old visuals, and referenced asset changes participate in aura-definition compatibility. Support pack data whose references can be validated and versioned rather than promising isolated arbitrary overrides.

The user explicitly expanded this to all supported asset families, including custom models, sounds, boss dialogue lines and taunts, and sounds attached to abilities. Apply the same reference, import, preview, publication, validation, and retention contracts to models and animation data, textures, sound effects and music, voice recordings, fonts, localization, and supported visual-effect data. Typed definitions and actions choose the presentation and recipients; the resource pack holds its media. Another supported model or spoken line must not require a bespoke code change.

Q127-Q128 are detailed in [world locations and client assets](world-locations-and-assets.md#q127-world-location-manifests-and-references).

### Q129: dialogue lines and taunt variants

Accepted: define named `dialogue` content with text and optional recorded voice, invoked by `speak` with a speaker and explicit audience. Show matching subtitles and support text-only lines. An optional list of weighted variants lets the server choose a taunt once for the whole invocation and avoid an immediate repeat when alternatives exist. New lines are authored content, without runtime speech generation or a code change for each line. The user additionally requires preexisting and author-created formatting templates, including shout and whisper. Q133 accepts their concrete style fields, and Q135-Q136 accept dialogue scheduling and translated presentation.

### Q130: ability sounds, animations, and effects

Accepted: attach `play_sound`, `play_animation`, `show_effect`, and `speak` actions to the existing rules for declared mechanic events. Validate their assets and targets; do not create a second ability event system. Server gameplay timing remains authoritative regardless of client sound or animation playback. One-shot cues may finish, while owned loops and future cues follow explicit cleanup and interruption controls.

### Q131: presentation audiences and positional audio

Accepted: use an explicit `audience` with existing player selectors, including whole-team, role-specific, or deliberately public selections. The user explicitly requires global delivery, particular filtered players, radius around the dialogue's owner or speaker, and an area, for both text and sound. Presentation selection can include entitled dead participants. Separate recipients from audio origin: positional sound comes from an NPC or location and respects dimension and range; direct sound reaches selected players without distance attenuation. NPC speech defaults to positional delivery. Keep private audio and subtitles private under the existing spectator policy. Q134 accepts exact radius syntax and nested text/sound audiences.

### Q132: reusable custom-model adapter

Accepted: provide one Conclave adapter for GeckoLib model and animation assets on compatible NPCs, with references selected by YAML. Primary-source research verified a Fabric 26.2 release and MIT licensing. Keep appearance separate from server entity type, AI, collision, and combat behavior. Models and animations remain publishable content; the dependency pin, supported consumers, reload integration, and simultaneous old/new resource retention need an implementation proof. The adapter direction is captured in [ADR-0016](adr/0016-geckolib-model-adapter.md).

Q129-Q132 are recorded in [presentation and dialogue](presentation-and-dialogue.md). [Presentation asset research](presentation-assets-research.md) records primary-source facts separately from the unimplemented Conclave integration.

### Q133: named text styles

Accepted: use named `text_style` definitions referenced by `style`, with built-in `conclave:normal`, `conclave:shout`, and `conclave:whisper`. Authors create self-contained styles controlling typed color, emphasis, supported font, and bounded text scale through YAML or the in-game editor. A style changes text appearance without implicitly changing sound volume, audience, or range. Omission uses normal style; do not add inheritance or an executable template language.

### Q134: radius filters and separate text/sound audiences

Accepted with the user's correction: the shared `audience` applies to all delivered parts, and overrides nest under `text.audience` and `sound.audience`. A nested audience replaces the entire shared selector for that medium instead of merging filters. A medium without an override inherits the shared audience; the shared field may be omitted when every delivered medium supplies its own audience. Global selection uses `from: online_players`; raiders and participants keep their existing explicit collections. Add positive `radius` around the speaker or explicitly declared presentation origin, in the same dimension. Combine radius, `area`, and other filters with AND. Direct global audio must be explicit; changing a style never broadens recipients.

### Q135: overlapping speech and presentation lifetime

Accepted: use `flavor`, `normal`, and `urgent` priorities with normal as default, one active dialogue voice and subtitle per player, and higher-priority interruption. Skip equal/lower-priority overlaps by default; allow an explicitly bounded queue with `max_wait`. Text-only display defaults to five seconds, while voiced lines derive duration from their recording, with longer authored display duration permitted. Presentation reading/playback time uses real elapsed time without affecting gameplay clocks. Cancel obsolete queued, stopped, or previous-attempt dialogue; finite started speech may finish at normal completion under Q130.

### Q136: per-language dialogue and voice

Accepted: retain default text and optional sound, plus an optional `translations` map with per-locale text and voice. Resolve the same selected variant for each recipient's locale. Fall back to default text or recording when a locale lacks one, and show text only when no recording exists. Keep locales and presentation duration independent of gameplay and publish all referenced content under the existing revision contract.

Q133-Q136 are recorded in [dialogue formatting and delivery](dialogue-formatting-and-delivery.md).

### Q137: asset readiness for outside recipients

Accepted: skip an unavailable cosmetic output for a selected client without compatible applied resources, with bounded administrative diagnostics and no forced reload or delayed stale replay. Other available components can still be delivered. Allow explicit `required: true` for supported presentation actions; preflight known resources and follow the existing technical-error policy if required delivery cannot be dispatched. This never requires a person to hear audio or makes gameplay wait for playback completion.

### Q138: named playback controls

Accepted: let presentation actions name their playback with `id`, required for loops, and add `stop_presentation` using that scoped ID. Reusing an active ID explicitly replaces that playback; captured old activation references cannot cancel the replacement. A stop clears its active and queued components without affecting unrelated uses of the same asset. Scope cleanup retains the accepted loop and one-shot lifetime rules.

### Q139: model definitions and NPC appearance

Accepted: add reusable `model` definitions with a registered adapter `type`, geometry, texture, and optional animation asset references. An NPC selects one through `appearance.model` and may set a positive visual `scale`, defaulting to one. Share models across different NPC gameplay definitions; preserve explicit server hitbox and AI settings. Validate dependencies and adapter compatibility before publication.

### Q140: automatic and authored animation

Accepted: map verified entity states such as idle and movement to named clips, and use `play_animation` for explicit cast, attack, or taunt presentation. Start with one authored full-body override per NPC, replaced by a later override, and return to the current automatic state when it ends. Animation does not determine damage, root movement, or phase timing. Reject unsupported bindings and prevent asset markers from bypassing the existing action/audience contracts.

Q137-Q140 are recorded in [presentation controls and model configuration](presentation-controls-and-models.md).

### Q141: base entity and readable stats

Accepted with the user's confirmed rename: use `npc` inside an NPC definition for the registered base NPC type and a `stats` section for `health`, `melee_damage`, `speed`, `armor`, and `knockback_resistance`. Health and damage use health points; speed and knockback resistance use explicit percentages. These configure supported base attributes while ordinary equipment and other modifiers still apply. Spawn at the final configured maximum health. Q146 resolves the user's health-ceiling follow-up by accepting native health up to 1,000,000 points.

### Q142: damage and healing actions

Accepted: add `damage` and `heal` with explicit player, group, or typed event targets and positive health-point amounts. Optional damage `source` supplies attribution. Damage uses supported native damage semantics and ordinary mitigation, blocking, immunity, and death processing; it does not silently execute a complete melee attack or set health directly. Healing clamps to the living recipient's current maximum and never revives. Defense-blocked damage, full health, and valid empty selections are harmless no-change outcomes. Exact damage-kind references and their revision behavior remain a later capability decision.

### Q143: physical size versus appearance scale

Accepted: use `size` as a percentage for supported native physical scaling, with 100% as normal. Retain `appearance.scale` as an additional visual multiplier. Verify entity-specific limits, dimensions, and safe placement, and show the visual model and actual collision dimensions in preview. Size does not implicitly multiply health or damage, and model bones do not create weak-point hitboxes.

### Q144: typed equipment

Accepted with the user's correction: use Minecraft's native equipment slots and serialized names in the `equipment` map, without custom slots or renamed aliases. Accept concise item IDs and a configured form for counts, enchantments, and supported item properties. Omission keeps ordinary initial equipment; a present section defines the complete initial loadout, leaving unspecified supported slots empty. Equipment has its ordinary gameplay effects. Retain the accepted default of no equipment drops, and do not silently change pickup behavior or repeatedly restore the initial gear.

### Q145: boss bars

Accepted: an optional `boss_bar` displays each NPC's current health against its maximum, using its name by default and typed title, color, and segmentation options. Require an explicit audience and keep recipients current. Reconnect receives current state; death or cleanup removes the bar. A supported adapter must replace a native bar without duplicates or audience leaks when configured. Omission retains ordinary entity behavior. Group health pooling and counter-based bars require separate capabilities.

Q141-Q145 are recorded in [NPC stats and combat actions](npc-stats-and-combat.md). [Primary-source research for Minecraft 26.2](npc-combat-research.md) records engine constraints separately from Conclave policy.

### Q146: increased native health ceiling

Accepted: support authored NPC health up to 1,000,000 points by raising only the native maximum-health attribute's ceiling through a small internal integration on both server and clients. Keep one native health pool and its normal damage, healing, death, saving, and boss-bar behavior. This shared ceiling also permits other entities and mods to use higher health. It does not heal them or change vanilla defaults, but previously clipped base values and modifiers can produce a higher effective maximum. Use a fixed startup integration, while YAML health changes remain revisioned and apply only to future attempts. Do not lower a higher compatible ceiling supplied by another mod. AttributeFix's 26.2 source supports feasibility; Conclave's live synchronization, persistence, combat, and compatibility still need verification. Native float precision motivates a finite authored limit rather than unlimited health.

The full accepted contract is in [Q146](npc-stats-and-combat.md#q146-increased-native-health-ceiling) and [ADR-0017](adr/0017-raised-native-health-ceiling.md).

### Q147: damage types and the physical default

Accepted: add optional `damage.type` with a supported registered damage-type ID, defaulting to a built-in `conclave:physical`. Use native armor, effects, absorption, cooldowns, and death protection, with shields subject to valid source positioning. Give this authored physical type no automatic difficulty multiplier. Explicit native types retain their documented semantics and source requirements. A type does not create an attacker, projectile, fire, or weapon calculation. Keep administrative kill types outside ordinary authored damage. This revises the earlier generic default rather than treating native generic damage as armor-respecting.

### Q148: external gameplay-data reloads

Accepted: keep normal Conclave publication independent of Minecraft datapack reloads. Reject a global datapack reload before application while an attempt is preparing, active, or performing gameplay-sensitive cleanup, or when changed external dependencies would invalidate retained Conclave consumers. Report what blocks maintenance; do not automatically stop attempts or retry the reload later. A verified server-thread gate must run before resource and tag mutation, leaving active data unchanged on rejection or failed preparation. Native holder references do not freeze reloaded tags. Exact hooks, dependency fingerprints, and retained-state compatibility still need implementation verification. See [ADR-0018](adr/0018-gated-external-data-reloads.md).

### Q149: explicit NPC vulnerability

Accepted: default NPC `vulnerable` to true and use the accepted `set_vulnerable` action for current living group members. False blocks supported gameplay damage, including armor-bypassing damage and creative-player attacks; true removes only Conclave's lock and preserves native defenses and immunities. Keep explicit operator `/kill` and authorized GM removal effective through verified administrative handling. Preserve health and other NPC state, treat repeat assignments as no-ops, and let future reinforcements use their own definitions. This does not make unrelated players immune or promise to intercept arbitrary direct health writes by other mods.

### Q150: health conditions

Accepted: add a `health` predicate using the existing comparators, with numbers meaning current health points and explicit percentages meaning a fraction of current maximum health. Support an unambiguous single-NPC group, typed event target, or implicit selected member. Exclude absorption, and treat a not-yet-spawned or no-longer-living target as false. Do not silently pool groups or treat death as a live zero-health NPC. These checks are live and do not block lethal damage; actual defeat and a future health-floor capability remain separate.

### Q151: explicit percentage amounts

Accepted: extend damage and healing amounts with `{percent: 10%, of: max_health}` or `current_health`, and allow `missing_health` for healing. Require an explicit basis and a positive percentage no greater than 100%. Snapshot each recipient's basis at action execution; apply normal defenses to calculated damage and clamp healing to maximum health. A zero basis is a no-op. Healing 100% of missing health fully heals an eligible living target with a neutral healing-received factor under Q157; percentage damage never promises exact final health loss or an unavoidable kill. No arbitrary arithmetic strings or direct health setter are introduced.

Q147-Q151 are recorded in [combat rules and health conditions](combat-rules-and-health.md). The user's follow-up confirmed [multiple sequential phases](phases-and-rules.md#multiple-sequential-phases), retaining one active phase and concurrent mechanics within it.

### Q152: optional NPC health floors

Accepted: add optional `health_floor` as health points or a percentage, plus `set_health_floor` and `clear_health_floor`. Limit supported damage-driven health loss after mitigation and absorption, before tracking and subtraction, so an oversized hit cannot skip a protected threshold. Emit `health_floor_reached` once per installed setting; changing phases does not automatically clear an encounter-owned NPC's floor. Installing a floor never heals, direct maximum-health clamps are separate, and authorized administrative handling remains effective. Align native threshold conversion with percentage health checks. Default floors are off, and adapter verification is required.

### Q153: damage and healing events

Accepted: expose `damaged` and `healed` for NPC groups and explicitly selected players. Damage qualifies when positive health or absorption was consumed, reports those amounts separately plus their sum, and distinguishes pre-rescue health removed from final health after totems or other rescue. Healing reports actual supported health restoration, excluding spawn, revive, and direct rescue assignments. Preserve typed target/source identities and before/after values, and queue notifications after the operation resolves in causal order. Empty or prevented damage does not fabricate a damage event; absorption-only damage does qualify. Native callbacks need additional instrumentation for this contract.

### Q154: authored NPC stat changes

Accepted: add `set_stats` for current living group members using the accepted stats and units, replacing only supplied base fields without cumulative application. Add `reset_stats` with an explicit field list to restore each NPC's initial configured base values. Preserve independent modifiers and identity. Maximum-health increases do not heal; decreases clamp current health as needed. Validate the complete change before mutation, keep future reinforcements on their own definitions, and retain changes across phases for encounter-owned NPCs. Temporary aura modifiers remain a separate capability.

### Q155: damage origin separate from attribution

Accepted: allow `damage.origin: {location: ...}` independently of its attributable `source`. Snapshot the named location to supply the incoming direction; an explicit origin requires same-dimension recipients. Do not invent a local direction for cross-dimension sources. The origin does not create an area, range or line-of-sight check, projectile, knockback, or presentation. Preserve server attribution and verify client behavior through a supported adapter, because the full native constructor is private and ordinary client reconstruction does not preserve both pieces of information.

Q152-Q155 are recorded in [combat events and runtime controls](combat-events-and-controls.md).

### Q156: temporary stat modifiers owned by an aura

Accepted: add aura `modifiers` entries with a supported `stat` and exactly one `add` or `multiply` operation. Remove only owned modifiers, preserving current base values and unrelated effects. Apply once per present aura by default; explicit `per_stack` scales additions and multiplier bonuses linearly, so a 120% modifier reaches 160% at three stacks. Different aura multipliers compound, and multiple sources of one non-stacking aura do not duplicate it. Maximum-health bonuses do not heal and removal can clamp current health. Safe persistence, modifier identity, and load ordering need verification.

### Q157: damage and healing multipliers

Accepted: add aura modifier targets `damage_dealt`, `damage_taken`, and `healing_received`, with multiplicative percentages and the same stack option. Apply outgoing and incoming damage factors once at the supported incoming stage before defenses, using current attributable-holder state, and apply healing received once before the normal maximum-health clamp. Cover supported native and authored calls without double application. Healing reduction can limit a 100%-of-missing-health request. Preserve admin handling, reject invalid aggregate arithmetic, and keep direct health writes and unsupported overrides outside the claimed hook coverage.

### Q158: native status effects as explicit actions

Accepted: add `apply_status_effect` and `remove_status_effect` with explicit targets, registered effect IDs, human-facing levels starting at one, and positive durations for sustained effects. Apply supported instant effects once without a duration. These operations hand effects to Minecraft's merge, expiry, milk, death, save, and offline-timing rules; they are not automatically cleaned up with an aura or phase. Removal deliberately clears the whole named native effect, including hidden potion contributions. Keep native effects separate from source-owned Conclave auras because the native store retains no removable owner identity. A source-aware native-effect projection remains a separate possible integration.

Q156-Q158 are recorded in [aura modifiers and native status effects](aura-effects.md). The deliberate native-effect cleanup exception is recorded in [ADR-0019](adr/0019-native-status-effect-handoff.md).

### Q159: periodic damage and healing

Accepted: add aura `periodic` entries with a positive `every` and exactly one holder-targeted `damage` or `heal`. Use ordinary amount and damage-type semantics, with one schedule per holder and entry rather than one per contributing source. Optional `per_stack` scales the request by the current stack count. First pulse follows the full interval; refreshes do not reset cadence. A due pulse runs before coinciding natural expiry, so a 10-second aura pulsing every second can pulse ten times. Retained auras continue aging while dead or offline but discard skipped pulses and resume only at a future scheduled deadline. Persist cadence without replaying effects. Initial periodic effects have no attacker attribution, source override, arbitrary action list, or callback into an ended attempt.

### Q160: contribution and stack removal events

Accepted: add `contribution_removed` and `stack_removed` for committed removals other than natural expiry. Include source identity and reason, emit contribution then stack notification, and retain holder-level `removed` only when the aura actually ends under Q118. Natural timeout retains its existing expiry events. No-op removal emits nothing, competing end operations cannot report a contribution twice, and cancelled subscribers never resume merely because cleanup or recovery produced a notification.

### Q161: aura stack and remaining-time conditions

Accepted: preserve concise `has_aura: charged` and add a structured form with `aura` and optional `stacks`, `remaining`, and `timed` checks. Reuse typed subjects, comparators, and existing condition filters. Stacks require a stacking aura; remaining time follows its HUD countdown, meaning next stack expiration or final non-stacking contribution expiration. Absence makes the predicate false; an untimed aura fails a remaining-time comparison and can be matched explicitly with `timed: false`. Evaluate the current retained aura state without exposing private information or confusing it with an earlier event payload.

Q159-Q161 are recorded in [periodic auras, removal events, and state queries](aura-periodic-and-queries.md).

### Q162: equipment attachments on custom NPC models

Accepted: add model `attachments` mapping supported native equipment slots to named geometry bones, starting with `mainhand` and `offhand`. Render the NPC's current real equipment without creating a second item or changing its gameplay. Omitted bindings draw no separate equipment item on the custom model. Preserve common name visibility and invisibility behavior, and require verified adapters for armor, saddles, and other special layers. The preview shows both native equipment effects and actual rendered equipment. Ordinary world NPCs of the same type retain native presentation.

### Q163: native model fallback

Accepted: show an NPC's native base-type appearance to a viewer whose captured custom model is unavailable, preserving the same server entity and current state. Do not borrow a newer model revision or force a pack reload during an active attempt. This specific fallback keeps ordinary world visibility but never satisfies required asset readiness, accepts invalid content, or claims a required cue was delivered. Report unexpected cosmetic failures to authorized administrators and keep required-presentation failures on their existing error path. Correct resources resume current presentation without replaying old cues.

### Q164: conditions on event values

Accepted: add `event_value` with a documented `field` and one typed comparison or `present` check. Reuse existing condition composition and numeric comparators; support exact scalar equality where appropriate. Compare immutable event values, such as actual damage `amount` or damage `type`, while existing subject predicates inspect current state. Absent optional fields fail ordinary comparisons, unknown fields are validation errors, and neither arbitrary property traversal nor expressions are permitted. The in-game editor exposes valid fields for the selected source/event.

Q162-Q163 are recorded in [NPC equipment visuals and model fallback](npc-model-presentation.md), and Q164 in [event-value conditions](event-conditions.md). Renderer source evidence remains separate from the unimplemented Conclave integration in [presentation asset research](presentation-assets-research.md#npc-renderer-and-attachment-follow-up).

### Q165: layers mechanics with local orchestration

Accepted with the user's rename: add `type: layers` with local objectives, background mechanics, rules, counters, and timers. Reuse existing completion/failure conditions and optional success duration or failure deadline. Each use owns independent private state and reports its result to its caller; it cannot route the encounter to another phase itself. Untimed background operation is valid, but an objective needs a possible success criterion. Resolve competing results with the accepted encounter policy, cancel private future work on termination, and preserve each created resource's existing lifetime rather than treating completion as a blanket rollback. Smaller sequence/parallel/repeat forms remain available.

### Q166: public events from reusable mechanics

Accepted with the user's rename: add `export.events` with a named public event, an internal `on` source/event, optional guard, and explicit typed `data` mapping. Forward only declared values and present the reusable occurrence as the public source. Infer the exposed schema from documented event fields, parameters, and supported literals; retain optionality, units, privacy, and original identities. Outside rules use public event names rather than private child paths. Reserve built-in lifecycle event names, use normal queued delivery and limits, and introduce no arbitrary emit action, global event bus, or mutable public state in this first form.

Q165-Q166 are recorded in [reusable mechanic bodies and public events](reusable-mechanic-contracts.md). The user explicitly selected the names `layers` and `export`; all other recommendations in that round were accepted.

### Q167: a started event before child activation

Accepted: emit `started` once for each new encounter, phase, and mechanic activation. In scopes supporting rules, `source: self` addresses the containing activation. Initialize configuration, state, and subscriptions first, process startup through ordinary ordered rules, then activate dependent children. Required startup failure or cancellation prevents those children from starting. Keep activation-based timers and the existing event queue, bounded retries, and one-active-phase policy. Reconnection and publication do not produce another startup event, and automatic startup does not invent a triggering player.

### Q168: typed parameter constraints and binding

Accepted: use a registered parameter type catalog for scalars, enum choices, spatial/content references, player selections, and spawn-group references. Support optional name/description, literal typed defaults, inclusive numeric/duration/percentage min/max, and string max_length. Validate values again against consuming fields after binding, with diagnostics at the caller and definition. Bind caller references in the caller's namespace and scope. A player-selection parameter keeps its query fixed but evaluates current recipients when used; a group parameter retains its permitted producer/activation identity. Keep arbitrary maps, action lists, expressions, and mutable configuration outside the parameter interface.

Q167-Q168 are recorded in [mechanic startup and parameter contracts](mechanic-start-and-parameters.md).

### Q169: role assignment and fixed random choices

Accepted: declare initially empty roles by ID with optional name in encounter, phase, or private layers state. Add `assign_role` to replace membership, `add_to_role`, `remove_from_role`, and `clear_role`, with explicit selected participants and atomic membership validation. Allow `choose: {random: 2}` on assignment to draw exactly that many distinct candidates once. A shortage leaves the old assignment intact and follows documented recoverable/required-error handling. No-count assignment uses all selected identities, including an empty set that clears membership. Roles may overlap and survive death or disconnection until explicitly changed or their scope ends. Add `has_role` and typed role-reference parameters; grant no aura, HUD, camera access, or administrative authority automatically.

### Q170: explicit online and life-state filters

Accepted: add `online: true|false|any` and `state: alive|dead|any` to player selectors, with a matching `player_state` condition. Dead includes the grave and passed-out states. Preserve contextual defaults: gameplay uses living online participants; initial selection remains all online raiders without a hidden life-state filter; presentation includes entitled dead players. `from: participants` with both filters set to `any` selects the full tracked roster. Offline queries use retained authoritative lifecycle state without creating a live entity or bypassing a capability's availability requirements. Collection membership, privacy, event eligibility snapshots, and admission rules remain explicit.

Q169-Q170 are recorded in [role assignments and player-state selection](roles-and-player-state.md).

### Q171: role membership events

Accepted: expose `assigned` and `unassigned` with the affected player, plus one aggregate `changed` carrying membership counts. Commit the whole mutation first, then queue removals, additions, and the aggregate notification. No-op assignments and failed random draws emit nothing. Death and disconnection retain roles; scope teardown does not pretend to be an authored membership change. Event payloads retain the committed snapshot while guards read current state, and only per-member events support player-specific invocation limits.

### Q172: participant lifecycle events

Accepted: expose `died`, `revived`, `passed_out`, `disconnected`, `reconnected`, and `reconnect_grace_expired` for tracked participants. Emit each once per committed transition, with the affected player and before/after connection and life state; revival also records the method and actual helper when present. Lifecycle source defaults include dead and offline participants, and explicit filters match the pre-transition snapshot. Ordinary action defaults remain living and online. Preserve final-tick revival precedence, expired-grace restrictions, and causal event order. Attempt-end recovery is separate from revival; ended rules, restart, and publication cannot replay past events.

Q171-Q172 are recorded in [role and player lifecycle events](role-and-player-events.md).

### Q173: capture configuration and interruption

Accepted: require an `area` and positive `duration`, with `players_required` defaulting to one and optional `players` filters. Count distinct living, online, eligible participants in that area. Extra players do not accelerate progress, and participants can replace one another while the count remains sufficient. Use `on_interrupt: reset` by default, explicit `pause`, or `{decay: 5s}` to drain a full meter over that simulation duration. Completed capture stays complete; temporary interruption is not a terminal failure. Reuse accepted geometry and scope contracts without creating walls or automatic visual effects.

### Q174: typed interaction targets

Accepted: give `interact` a nonempty `targets` list with typed `{block: location_id}` and `{group: spawn_group_id}` entries. A valid use of any listed target contributes to the same mechanic. A block target names the block cell containing a bound location; a group target permits current living members of the bound group activation, including explicit reinforcements to that group. Never retarget a replacement group. Future declared targets can wait, and ordinary target unavailability cannot count as completion. Separate mechanics express a requirement to interact with each target. Keep input timing, progress, and native-interaction consumption as the next decisions once target binding is settled.

Q173-Q174 are recorded in [capture configuration and interaction targets](capture-and-interaction-targets.md).

### Q175: completed uses and individual holds

Accepted: add positive `uses`, default one; `distinct_players`, default false; `hold`, default instant `0s`; and optional per-player `use_cooldown`, default `0s`. Each completed hold contributes one use to the mechanic's shared count. Players may hold concurrently without pooling or accelerating one another's progress. Interrupted holds reset, while completed uses and distinct-player credit persist for the activation. Require a fresh press for every use. Emit `used` for each credit and `completed` once after the final credit, with player and count snapshots. Stop crediting once the requested count is reached.

### Q176: physical eligibility and interruption

Accepted: require aim at the actual target, same dimension, line of sight, and the player's current native reach for the target kind. Optional `reach` can narrow that distance. Recheck a hold as simulation advances; release, target change, lost eligibility, focus loss, or disconnection resets it. Add `interrupt_on_damage: false` by default, with true interrupting on positive supported damage including absorption loss. The server owns elapsed progress and validates bounded client intent; packet frequency and client timestamps cannot grant extra credit. Do not import the separate revival-helper reach or timing policy.

### Q177: ordinary use and explicit input consumption

Accepted: use Minecraft's configured Use control and default `consume_interaction` to false, preserving ordinary block/entity/item use. True consumes only a qualifying admitted gesture before native behavior, including at the start of a hold. Prevent duplicate native actions from the other hand or repeats of that consumed gesture. Multiple matching Conclave mechanics each observe once, and any admitted request for consumption suppresses native use. Rejected inputs and unrelated players retain ordinary Minecraft behavior. A credited Conclave use is not proof of native action success; interfaces that take focus interrupt pass-through holds.

Q175-Q177 are recorded in [interaction input and progress](interaction-input-and-progress.md). [Fabric interaction research](interaction-input-research.md) separates verified callback behavior from the Conclave adapter that still needs implementation.

### Q178: managed relics and carrying capacity

Accepted: track relics as managed world objects with holder state outside ordinary inventory slots and a clear carried HUD indicator. Preserve normal movement, combat, and item use; add burdens through explicit supported gameplay effects. Add encounter-level `carry_limit`, a positive integer defaulting to one, captured for the attempt. Each relic still has one holder. Full capacity rejects another pickup without dropping an existing relic, and cleanup removes only the owned relic state. Detailed world and carried visuals follow the representation choice.

### Q179: explicit creation and live instance identity

Accepted: add `spawn_relic` with instance `id`, reusable `relic` definition, and bound `location`. Create one named instance per action, once per owning activation, with existing phase/encounter lifetime and private-scope rules. Runtime relic references address the instance; the existing `relic` parameter type remains a content-definition reference. Supported consumers may wait for a declared future producer. Drops and respawns retain logical identity while replacement world representations invalidate stale input. Recreating a delivered relic requires an explicit lifecycle operation, not a second spawn under the same ID.

### Q180: death, disconnect, and respawn fields

Accepted: define independent `on_holder_death` and `on_holder_disconnect` choices of `drop`, `return`, or `despawn`, both defaulting to drop. Keep automatic return in a separate opt-in `respawn` block with nonempty `causes`, positive `after`, and `at`, defaulting to `initial` or explicitly referencing a location. Initially allow the two holder causes only when their corresponding policy despawns. Commit one cause and one pending return per disappearance. Delivery and cleanup do not trigger respawn. Preserve the existing invalid-drop and arena-escape return for the relic while ordinary player movement continues.

Q178-Q180 are recorded in [relic identity, carrying, and return policies](relic-identity-and-lifecycle.md). [ADR-0020](adr/0020-managed-relic-ownership.md) records the choice to keep relic ownership separate from inventory items.

### Q181: deliberate pickup and selected-relic dropping

Accepted: pick up an available relic with a fresh Use press, instant by default, consuming that admitted gesture. Configure optional pickup players, hold, reach, and damage interruption under the spawning action's `pickup` block. Validate eligibility and capacity at commitment; concurrent pickup attempts produce one holder. Provide a dedicated rebindable Drop relic action and explicit HUD selection when several relics are held. Add definition-level `allow_drop`, default true, affecting voluntary input only. Preserve committed carrying when pickup eligibility later changes, and keep ordinary item-drop controls unchanged.

### Q182: explicit delivery destinations and triggers

Accepted: require one runtime `relic` and typed `destination` on `deliver`. Default `trigger: interact` accepts a block location or NPC group with the existing interaction controls; explicit `trigger: enter` accepts an area and requires a real entry while holding the relic. Do not deliver automatically on activation, pickup inside the area, or a filter change. Interaction delivery uses the selected carried instance. Commit one delivery per current cycle, release its holder, and complete the committing mechanic once; competing requests cannot consume it again. Old delivery records never auto-complete a new mechanic, and reset does not undo an already-completed objective.

### Q183: direct relic lifecycle actions

Accepted: add `drop_relic`, `reset_relic`, and `despawn_relic`, each naming one runtime instance. Drop releases a holder with the accepted placement fallback and otherwise does nothing. Reset cancels pending return, clears current delivered state, and restores the initial location with a fresh availability generation. Despawn removes the present object/holder and cancels return without fabricating a new respawn cause or erasing delivered history. Preserve logical identity and prior mechanic outcomes, validate required placement before mutation, and prevent stale input or retry from duplicating the operation.

Q181-Q183 are recorded in [relic pickup, delivery, and explicit controls](relic-interaction-and-delivery.md).

### Q184: relic lifecycle events

Accepted: subscribe through `source.relic` and expose `spawned`, `picked_up`, `released`, `dropped`, `delivered`, `returned`, `despawned`, `reset`, and `respawned`. `released` covers every actual loss of possession, with the former player and a documented reason. Distinguish successful world drops from home fallback, and scheduled placement from initial creation. Commit state first, queue release before its outcome and delivery before the mechanic's completion notification, and never fabricate events for failed or duplicate operations. Only events with a required player support player-specific invocation limits; cleanup never revives ended listeners.

### Q185: current carrying and relic-state conditions

Accepted: add concise `carrying: north_orb` and a structured player predicate that can identify an instance, match a reusable definition, or check for any carried relic in the permitted attempt. Add `relic_state` with `pending`, `available`, `held`, `absent`, or `delivered`, plus an optional `respawning` boolean. Keep pending creation distinct from a created but absent object. Conditions inspect current authoritative state, retain ordinary subject and privacy rules, and never create or deliver a relic. Historical mechanic completion remains unchanged after a reset.

### Q186: auras owned by carrying a relic

Accepted: add optional definition-level `carry_auras`, a list of distinct aura references. Apply each once per pickup using the existing aura definition and retain exact admitted contribution identities for that carry. Any holder release removes only those surviving contributions, even if gameplay listeners have ended. Timed expiry, cleansing, stack caps, and aura events retain their accepted semantics; there is no automatic reapplication while held. Omission adds no aura. Carry ownership never becomes player-persistent, and publication cannot change the current attempt's captured configuration.

Q184-Q186 are recorded in [relic events, state checks, and carry auras](relic-events-and-carry-auras.md).

### Q187: stationary world relics and explicit interaction size

Accepted: keep unheld relics at their placed position without native dropped-item physics, damage destruction, automatic collection, or age-based despawning. They block neither movement nor projectiles and do not intercept ordinary attacks. Add `interaction_size` with width and height, both defaulting to 0.5 blocks, separate from visual scale. Authored spawn and return destinations are exact and require clear permitted space; they can deliberately float. Drops search for a valid supported position within three blocks of the holder, then use the accepted home fallback. Later terrain changes can obstruct access without triggering block protection or automatic relocation.

### Q188: item or custom-model appearance with readable holder feedback

Accepted: require `appearance` with exactly one `item` or reusable `model`, plus optional visual `scale`. Add and verify a model consumer for static or idle-animated relics. A custom model requires an explicit `fallback.item` for viewers lacking its captured appearance; fallback never satisfies new-attempt asset readiness. On pickup, replace the unheld world visual with the holder's mandatory name/icon HUD entry. Allow `display.icon` and descriptive text, preserve selected-relic/drop controls, and do not infer effects or public trackers from presentation. Carried body attachments and explicit animation targeting follow as separate consumer decisions.

Q187-Q188 are recorded in [relic placement and appearance](relic-placement-and-appearance.md). [Presentation research](presentation-assets-research.md#managed-relic-presentation-and-interaction-facilities) records the native facilities and remaining integration work.

### Q189: optional carried visuals without occupying equipment slots

Accepted: add optional `carried_appearance` with required `attachment: back` or `above_head`, optional scale override, and fixed right/up/forward offsets. Reuse the relic's captured appearance and fallback. Render every enabled held instance without occupying a slot, changing the holder's pose, or moving the selected relic into a hand. These are public world visuals subject to ordinary visibility; preserve the holder HUD and private-information rules. Hide the camera subject's own attachment in first person. Validate native player poses and variants through a dedicated layer, without assuming compatibility with every replacement renderer.

### Q190: explicit animation targets and logical playback

Accepted: let `play_animation` name exactly one NPC `group`, typed NPC `target`, or runtime `relic`, plus the model's exact `animation` clip and explicit `audience`. Default to a one-shot at `speed: 1`; looping requires a playback ID and uses `stop_presentation`. Keep one explicit override per target and preserve ownership when only part of a group playback is replaced. Relic playback retains elapsed simulation time through pickup, ordinary dropping, and temporary lack of a visible consumer. Reset, return home, delivery, despawn, or scope cleanup stops it. Required delivery cannot claim a hidden or fallback-only consumer displayed a custom animation, and animation completion never controls gameplay.

Q189-Q190 are recorded in [carried relic visuals and animation actions](carried-relics-and-animation.md). [Player-layer research](presentation-assets-research.md#player-render-layers-for-carried-objects) records the rendering facilities and remaining integration work.

### Q191: declared tokens and stable expected patterns

Accepted: declare a nonempty unique `tokens` vocabulary of local string IDs and use `pattern` for a literal answer, `sample` of tokens, or uniform `choose` among complete authored answers. Add `ordered`, default true; false still preserves token counts. Sampling has a required length, optional pool, and explicit repeats, default false. Resolve the answer once during initialization and never reroll on mismatch or reconnection. An excess unordered value mismatches immediately, and a mismatching token resets progress without becoming the first token of another run. Token presentation, per-player answer distribution, and registered list parameters remain separate choices.

### Q192: repeatable interaction bindings for pattern input

Accepted: add a direct `inputs` list mapping each token to explicit block-location or NPC-group targets. Reuse fresh-input, hold, reach, interruption, and native-consumption behavior without creating a finite-use `interact` objective. Support mechanic-level player selection, narrower per-input eligibility, per-input interaction settings, and a per-player `use_cooldown` across this matcher's bindings. Derive tokens on the server and admit at most one per gesture per matcher. Require unambiguous target bindings, evaluate correctness at commitment, and preserve deterministic order for concurrent inputs. General rule-driven submissions, per-player completion, lifecycle retention, events, and clue display follow separately.

Q191-Q192 are recorded in [pattern definitions and interaction inputs](pattern-definitions-and-inputs.md).

### Q193: progress ownership, required players, and completion

Accepted: add `progress: shared|per_player`, default shared. In per-player mode, capture the configured `players` set at initialization and retain it through lifecycle or filter changes. Support `completion: all` by default, `any`, or `{count: N}`; empty or insufficient initial sets use the existing `insufficient_players` error policy. Add `pattern_per_player`, default false, for independent expected-answer construction without promising unique random results. Retain partial progress, completed records, and answers through death/disconnection, while unfinished holds reset and current eligibility still controls input. Do not silently shrink the required set or enroll later arrivals.

### Q194: token submission from authored rules

Accepted: add `submit_token` with a target `mechanic`, declared literal `token`, and optional typed `player`. Shared progress permits unattributed server-authored inputs; per-player progress requires its specific captured solver. Attributed inputs check current filters and can credit retained dead/offline identities only when explicitly permitted, without restoring their gameplay rights. Use ordinary rule throttling rather than physical hold/reach/cooldown settings, and deduplicate retries of an operation without suppressing distinct authored actions. Pending matchers cannot buffer tokens; terminal occurrences reject input without changing outcomes. Support action-only matchers with validated token routes, preserving reusable private-state boundaries.

Q193-Q194 are recorded in [pattern progress and rule-driven submission](pattern-progress-and-submission.md).

### Q195: matcher events and private feedback

Accepted: expose `matched`, `mismatched`, `player_completed`, and the standard overall `completed`. Submission outcomes carry the submitted token, input origin, before/after progress counts, pattern length, and a player when attributed. Per-player outcomes require their captured solver; shared outcomes permit an absent player. Personal completion has a required player, while overall completion names no single winner. Commit progress and all resulting completion before queuing notifications, in outcome/personal/overall order. Invalid input emits no gameplay mismatch. Keep expected values and all notifications server-side until an explicit authorized presentation.

### Q196: clearing unfinished pattern progress

Accepted: add `reset_pattern` with a required mechanic and, only for per-player progress, an explicit choice of one typed `player` or `all: true`. Clear only unfinished records and cancel their active holds, preserving expected answers, the captured solver set, completed records, and cooldowns. Resetting empty progress still cancels a selected unfinished hold, but emits no progress-change event. Emit `progress_reset` only when a nonzero record is cleared. Pending targets follow the existing unavailable-target policy; terminal targets are no-ops. Use a new activation through repetition or phase re-entry for a new answer or a full replay.

Q195-Q196 are recorded in [pattern feedback and progress controls](pattern-feedback-and-controls.md).

### Q197: readable token names and icons

Accepted: retain the string `tokens` vocabulary and add optional `token_display` entries keyed by those IDs. Support `name`, an `item` or `texture` icon, a reusable text `style`, and localized names. Omitted names fall back to the exact ID; omitted icons give text-only output. Display metadata does not change matching, label world input targets automatically, play media, or reveal a chosen answer. Capture and validate presentation dependencies with the attempt revision.

### Q198: explicit pattern clues on the HUD

Accepted: add `reveal_pattern` with a required matcher and explicit audience. Reveal the whole answer by default or an ascending list of one-based `positions`; send only those pieces. In per-player mode require either one typed `player` whose answer is shown or `each_recipient: true` for each selected solver to see their own. Keep recipients distinct from answer ownership. Default to a five-second simulation-time display; allow positive `duration` or named `until_stopped: true`, using `stop_presentation`. Clear output when the matcher ends. Snapshot recipients, recheck continued viewing eligibility, and restore only still-current display state after reconnect without extending its timer.

Q197-Q198 are recorded in [pattern presentation and clues](pattern-presentation-and-clues.md).

### Q199: current pattern progress and personal completion

Accepted: add `pattern_state` with a required mechanic. Inspect a shared or selected personal record through nested numeric `progress` comparisons and/or boolean `completed`. Whole numbers count matched tokens; explicit percentages compare against that record's answer length. Per-player record checks require a typed `player` or an unambiguous implicit player. Alternatively, `completed_players` compares the count of finished captured solvers, independent of current life state or filters. Keep generic `completed: {mechanic: ...}` for the overall result. Pending or missing records compare false, including against zero. Retain a compact final summary after successful overall completion until its enclosing scope ends; failure/cancellation exposes no unfinished progress.

### Q200: optional pattern progress on the HUD

Accepted: add `show_pattern_progress` with a required mechanic and explicit audience. Shared mode shows its matched-token count; per-player mode requires one typed `player`, `each_recipient: true`, or `summary: true` for completed solvers against the required goal. Default `show_total: true` displays count/total, with false displaying only the count and no denominator, percentage, or progress bar. Send no token values, next-token hints, or full hidden answer. Reuse Q198's timing, recipient eligibility, reconnect, named replacement, and stop behavior; update from current authoritative state and clear at matcher end.

Q199-Q200 are recorded in [pattern state and progress display](pattern-state-and-progress-display.md).

### Q201: reusable token sets, answer constructors, and appearance

Accepted: register `pattern_tokens`, `pattern`, and `token_display` parameter types for their existing configuration forms. Bind through ordinary whole-value `{parameter: ...}` references and validate the fully bound matcher, including vocabulary membership, sampling feasibility, display keys, and possible answer lengths. Permit applicable collection/length bounds without general maps, schema definitions, inheritance, or deep merging. A passed constructor remains fixed configuration; each activation independently resolves its expected answer. Preserve the namespace of caller-supplied asset references and keep these configuration types separate from runtime answers or event payloads.

### Q202: reusable direct interaction bindings

Accepted: register `pattern_inputs` for the existing nonempty typed input list. It carries declared tokens, block-location or group targets, supported filters, and interaction settings, with caller references resolved before binding and preserved through nested reuse. Validate token coverage and target uniqueness after all arguments and arena bindings are known. Passing a query does not freeze its recipients, passing a group does not create it, and reusing the list does not share holds or cooldowns. Use whole-list replacement; action-only definitions continue to omit `inputs` rather than passing an empty list.

Q201-Q202 are recorded in [pattern parameters](pattern-parameters.md).

### Q203: world-space clues at named locations

Accepted: add `display: hud|world` to `reveal_pattern`, default hud. World mode requires a captured arena location, uses its facing by default, and supports optional billboarding, bounded scale, and a 32-block default view distance. Render selected token icons and labels with ordinary world occlusion, without collision or input behavior. Keep private per-viewer answers and explicit audiences, using targeted client-local presentation rather than broadcast answer metadata. Reuse the accepted timing, stop, reconnect, and matcher-end cleanup rules; a view-distance limit does not replace audience permission or force chunk rendering.

Q203 is recorded in [world pattern clues](world-pattern-clues.md). [Rendering research](presentation-assets-research.md#private-world-space-pattern-clues) records the verified platform facilities and remaining integration work.

### Q204: explicit fixed-token labels

Accepted: add `show_token` with a required active matcher, declared literal token, and explicit audience. Use that matcher's captured token name/icon/style without consulting its expected answer, progress, or input availability. Support HUD by default and explicit world output under Q203. Permit decoys and tokens absent from the chosen answer; reject unknown tokens and irrelevant answer-selection fields. Reuse clue timing, recipient checks, named replacement, stop, and matcher-end cleanup. A label neither submits input nor automatically binds itself to a nearby control.

### Q205: pattern progress placed in the world

Accepted: add `display: hud|world` to `show_pattern_progress`, default hud. World mode requires a named location and uses the accepted facing, billboard, scale, view-distance, occlusion, and noninteraction rules. Keep all existing record/summary selection and `show_total` semantics, sending no answer tokens or hidden denominator. Use a bounded two-block-wide counter panel at scale one, with client-local per-viewer output and the existing live-update, reconnect, and cleanup rules.

Q204-Q205 are recorded in [token labels and world progress](token-labels-and-world-progress.md). The [matcher reference](match-pattern.md) consolidates the accepted authoring contract and links its detailed rules. No runtime implementation exists.

### Q206: native conversion preserves the encounter member

Accepted: follow a verified native conversion as the same logical encounter NPC and group member, even when the native entity UUID changes. Preserve ownership and the existing defeat requirement without counting conversion as death or reinforcement. New group selections resolve the current body; historical event references do not silently retarget. Require verified source/destination compatibility and operation-level provenance. Q208 accepts detailed state transfer.

### Q207: native children inherit cleanup ownership, not defeat membership

Accepted: give verified native living descendants their parent's attempt/scope cleanup ownership, without automatically adding them to a named group or its defeat count. A slime parent's real death counts once; split children and summons remain until their own native end or owning-scope cleanup. Authors who need counted reinforcements use explicit YAML spawning. Never infer ownership from proximity or damage attribution. Contain cleanup-triggered creation so descendants cannot escape scope cleanup. Q209 accepts descendant settings and ordinary death rewards.

Q206-Q207 are recorded in [NPC conversion and descendants](npc-lineage.md). Native-source evidence remains separate in [lineage research](npc-combat-research.md#native-conversion-descendants-and-special-boss-lifecycles).

### Q208: preserving state through native conversion

Accepted: keep current health points without healing, current resolved Conclave-controlled base stats, the original stat-reset baseline, current equipment, aura contributions/timers/cadence, authored runtime controls, captured appearance, and reward policy on compatible replacements. Do not replay spawn configuration, refresh auras, or rearm health floors. Cancel old-body holds and explicit animation playback, while keeping already committed mechanic progress. Preserve historical native-target references and existing finite presentation semantics. Require verified transfer compatibility rather than silently dropping state.

### Q209: native descendant settings and rewards

Accepted: use the native child's creation setup with inherited cleanup ownership and no item loot, experience, or equipment drops by default. Do not automatically clone the parent's Conclave stats, aura contributions, custom model, boss bar, control records, or reward opt-in. Preserve supported native inheritance separately from Conclave bookkeeping. Apply these defaults to further descendants within engine budgets; later parent/group actions do not select ungrouped children. Q211 accepts explicit descendant customization.

Q208-Q209 are recorded in [NPC conversion state and descendant defaults](npc-conversion-state.md). No runtime implementation exists.

### Q210: a converted event for NPC groups

Accepted: add `converted` to `on.source.group`, emitted once after successful replacement insertion, state transfer, and membership handoff. Include the new typed `target`, previous/current native type IDs, and before/after current and maximum health snapshots. Keep historical body references, use normal queued dispatch, and order conversion before any resulting floor notification. Do not fabricate a player, death, reinforcement, or spawn event; ungrouped descendants do not bubble through an ancestor's group.

### Q211: configuring native children with NPC definitions

Accepted: add an optional `descendants` map from native child type IDs to same-base-type Conclave NPC definitions. Configure the actual native child once, preserving native creation state for omitted fields and applying explicit authored settings with existing spawn contracts. Keep native count, creation timing, position, parent relationship, and lifespan, plus scope cleanup and no automatic group membership. Each child's selected definition governs its later children; unmatched types keep Q209 defaults without ancestor fallback. Pin dependencies and allow bounded runtime reference cycles without recursive instantiation.

Q210-Q211 are recorded in [NPC conversion events and descendant definitions](npc-conversion-events-and-descendant-definitions.md).

### Q212: spawned notifications for explicit group members

Accepted: add per-member `spawned` notifications after a complete explicit spawn batch succeeds, including configured anchors and `into` reinforcements. Provide typed `target`, authored `npc`, native `type`, batch index/size, and a reinforcement flag. Queue in batch order, emit nothing for partial failures or retries, and preserve the distinction between acting on one event target and selecting the whole current group. Conversion, native descendants, and state restoration do not emit this event.

### Q213: notifications for native descendants

Accepted: add a distinct group `descendant_spawned` notification for each successfully configured native living child in a verified member's lineage, including later generations. Provide the new typed target, historical direct parent, native types, optional child definition, and generation number. Preserve lineage after a root dies while its owner and subscriptions remain active. Never add children to the group, bubble unrelated descendant events, expose private groups, or notify for cleanup-generated creation. Retain original group activation and bounded causal dispatch.

Q212-Q213 are recorded in [NPC creation events](npc-spawn-events.md). No runtime implementation exists.

### Q214: initial built-in NPC coverage

Accepted: target zombie, husk, drowned, zombie villager, villager, witch, skeleton, stray, spider, creeper, enderman, iron golem, slime, magma cube, evoker, vex, blaze, and guardian for initial verified adapters. Share implementation where justified and advertise individual capabilities honestly. Keep Wither and Ender Dragon outside the initial built-in roster pending dedicated adapters. Additional native/mod types require verified support. Preserve ordinary native behavior, cover known conversions/children, and revisit explicitly if a target cannot meet its required contracts. Q216-Q218 accept native variants, despawn prevention, and terminal-outcome details.

### Q215: inspect compatibility in the in-game NPC editor

Accepted: show supported types by default with an option to inspect registered unsupported types and their reasons. Provide per-capability units, bounds, limitations, and definition-specific compatibility, distinguishing supported, incompatible, and incomplete configurations. Use the same server schemas/validators as publication, preserve draft versions, identify affected fields, and keep previews harmless. Show technical adapter details only when useful, retain author permissions, and add neither an adapter-selection YAML field nor an unsupported bypass.

Q214-Q215 are recorded in [initial NPC adapters and compatibility inspection](npc-adapter-scope.md). Coverage targets are implementation requirements, not existing support claims.

### Q216: committed native self-destruction counts as defeat

Accepted: count a verified completed native combat self-destruction, initially a creeper explosion, as one member defeat without simulating lethal damage, inventing a killer, replaying native death effects, or manufacturing death rewards. Retain its distinct outcome cause, ordinary aura death/retention policy, and same-tick outcome arbitration. Despawn, cleanup, administrative removal, and unknown loss still produce no defeat. Conversion remains a continuing member. Q219-Q220 accept terminal events and cause filters.

### Q217: owned NPCs resist ordinary distance despawn

Accepted: suppress ordinary distance/random despawn for owned members and verified descendants while ownership remains active, with no extra required YAML field. Preserve conversion continuity, native limited-life behavior, actual death, self-destruction, applicable Peaceful removal, administrative removal, and cleanup. This grants no invulnerability, confinement, or extra chunk retention. A permanently missing required member remains an error rather than an inferred defeat or a silently reduced requirement.

### Q218: a typed block for initial native variants

Accepted: add `variant` with supported initial `baby`, integer `slime_size`, and `charged` fields. Validate by native type and apply before final authored stats, physical scaling, health initialization, and reset-baseline capture. Preserve ordinary later native changes. Apply the block to explicit Conclave spawning; native conversion and descendant creation retain their own native form, so a reused slime definition does not reset split-child size. Keep village trading/profession and other species-specific controls outside this initial block.

Q216-Q218 are recorded in [NPC endings, despawn prevention, and native variants](npc-lifetimes-and-variants.md). [Native source research](npc-combat-research.md#native-endings-persistence-and-initial-variants) records the relevant behavior separately from these unimplemented contracts.

### Q219: one defeated notification per group member

Accepted: expose one group `defeated` notification for each committed member outcome, with historical target, captured NPC definition, native type, and `cause: death|self_destruct`. Provide fatal damage type, attributable entity, and direct source only when the native operation supplies them; do not substitute remembered attackers, pet owners, or an igniting player. Keep the event separate from mechanic completion, source identities separate from authority, and group membership separate from lineage. Update bookkeeping before queued reactions, preserve causal order, and do not replay events for later subscribers. Q221-Q222 accept killer selection, historical qualification, and fatal damage-type filtering.

### Q220: cause filters and reachable completion

Accepted: add optional `causes` to `defeat`, defaulting to both accepted outcomes, and use `completion: all|any|{count: N}`, defaulting to `all`. Evaluate retained records, including defeats before the objective started. All-members completion still requires a nonempty closed group; count/any may succeed while it remains open. Treat a permanently unreachable requirement as ordinary mechanic failure: a disallowed member outcome makes all-members completion impossible, while count requirements can still wait for open-group reinforcements. Reject statically impossible bounds, preserve technical-error handling for missing members, and never rewrite membership or replay rewards.

Q219-Q220 are recorded in [NPC defeat events and requirements](npc-defeat-events-and-requirements.md). [Fatal-source research](npc-combat-research.md#fatal-sources-and-native-kill-credit) distinguishes the native operation's attribution from remembered kill credit.

### Q221: killer selection at the fatal operation

Accepted: add optional `killer` with exactly one `players` selector or NPC `group`. Match the fatal source's causing identity, with no assist, pet-owner, or previous-attacker substitution. Player filters default to living online participants and initially support their own role, aura, native-effect, area, and lifecycle checks. Observe qualification just before the fatal operation, then retain bounded results with the exact revision and role/group bindings so later objectives cannot change history. Do not borrow state from later activations or invent unavailable offline observations. Apply the existing impossibility and completion rules without changing combat, rewards, event payloads, or permissions.

### Q222: explicit fatal damage-type requirements

Accepted: add optional `damage_types`, a nonempty unique list of registered IDs matched against the recorded fatal type. Values within the list are alternatives; authored requirements combine with AND. Preserve historical results and ordinary unreachable-objective failure. Missing type, self-destruction, and earlier nonfatal damage do not satisfy this field. Reject impossible self-destruction-only combinations, unknown IDs, and tag/glob shortcuts. Inspecting a native administrative death type grants no ability to author that damage or bypass protection.

Q221-Q222 are recorded in [defeat requirements for killers and damage types](defeat-killers-and-damage-types.md). Historical qualification retains the fatal operation's observations and actual scoped bindings.

### Q223: an explicit player field for a subscription

Accepted: add `on.player` to name one documented identity field. Match only events where that field identifies a player, make the same field required and player-typed for this subscription, and use it for player-specific invocation limits. Preserve every payload field's original meaning and existing defaults when the choice is omitted. Missing/non-player values consume no limits, and guards alone still cannot strengthen schemas. Support the same explicit choice in exports; preserve the triggering-player contract only when the selected identity is explicitly forwarded as required typed data. Keep ordinary scope, privacy, authority, and availability checks.

### Q224: common mechanic completion and failure fields

Accepted: add typed simulation-time `elapsed` to mechanic `completed` and `failed`, with registered `reason` on `failed`. Begin with `deadline`, `condition`, `unreachable`, and `child_failed` where the capability supports them. Preserve existing specific payloads, including interaction completion's player and matcher completion's lack of one. Emit one committed terminal outcome after normal arbitration and causal notifications. Keep cancellation and technical errors separate from gameplay failure, add no generic cancellation/error callbacks, and forward common fields through exports only when explicitly mapped.

Q223-Q224 are recorded in [player identities in events and mechanic results](event-players-and-mechanic-results.md). They preserve original event fields, explicit export payloads, and the distinction between gameplay failure and technical errors.

### Q225: loot, equipment drops, and experience

Accepted: add independent `loot`, `equipment_drops`, and `experience` fields, defaulting to `none`, `none`, and `0`. Support native loot or one registered replacement table, native equipment drops or per-slot native base percentages, and native XP or an explicit final eligible point total. Preserve native eligibility, applicable gamerules, context, and once-only accounting. Cover special death-item paths, prevent pickup from bypassing authored equipment policy, and keep conversion/descendant rules and captured external dependencies. Genuine drops become ordinary world items/orbs; cleanup, self-destruction without native death, and replay produce no rewards.

### Q226: explicit subscriptions to native descendants

Accepted: add `on.source.descendants` with a visible root `group` and optional exact native `type` and child-definition `npc` filters. Observe descendant damage, healing, conversion, floor, and defeat events across verified generations, excluding root group members and preserving all group counts. Match source filters before the operation, retain existing event meanings, and make descendant defeat's own definition optional. Use the existing group `descendant_spawned` for births. Preserve actual lineage and activation, private-group exports, listener lifetime, queued ordering, and target authority; add no general ownership wildcard or world-mob selector.

Q225-Q226 are recorded in [NPC death rewards and descendant events](npc-death-rewards-and-descendant-events.md). [Native reward research](npc-combat-research.md#native-death-reward-paths) records the separate native loot, equipment, special-drop, and XP paths.

### Q227: use the same NPC controls on one typed target

Accepted: let `set_ai`, `set_vulnerable`, `set_health_floor`, `clear_health_floor`, `set_stats`, and `reset_stats` accept exactly one existing `group` or typed NPC `target`. Preserve arguments, preflight, persistence, and baseline reset behavior. Operate on the exact living body within the current attempt's permitted ownership; do not follow conversion from an old reference, control outsiders, or select siblings and future members. Capture each owned child's own initialization baseline, including children without an explicit definition.

### Q228: actual blocks and Conclave damage prevention

Accepted: add `damage_blocked` for positive native item blocking, with the actual pre-armor blocked amount, and `damage_prevented` for Conclave's own `vulnerability` rejection or `health_floor` cap, without a guessed health-saved amount. Partial blocks and floor caps can coexist with `damaged`. Preserve pre-operation selection, actual attribution, queued causal order, and once-per-setting floor notifications. Native immunity, cooldown, external vetoes, misses, and administrative cleanup do not become invented prevention reasons. Keep this initial catalog narrow and require verified adapters.

Q227-Q228 are recorded in [individual NPC controls and hit reactions](individual-npc-controls-and-hit-reactions.md). [Native hit research](npc-combat-research.md#native-item-blocking-and-damage-rejections) records their supported observation boundaries.

### Q229: world lighting, full brightness, and emissive regions

Accepted: add model-level `lighting: world|fullbright`, default world, and an optional explicit `emissive` texture for luminous regions on the same geometry. Support the existing NPC and managed-relic consumers. Preserve ordinary visibility, occlusion, and captured resources; keep separate equipment and fallback rendering under their own contracts. Add neither world-light emission nor an outline, and do not infer a glow texture from its filename.

### Q230: visual bounds separate from gameplay size

Accepted: derive default bounds from supported rest-pose geometry and allow explicit positive `width`, `height`, `depth`, and optional center `offset` under model `bounds`. Use model-local block units and transform the box consistently with physical size, appearance scale, and attachments. Preview the box and warn about observed poses extending beyond it. Do not claim complete automatic animation coverage, change gameplay dimensions, extend render/tracking distance, or offer zero/infinite bounds to disable culling.

### Q231: typed native item-model selection

Accepted: support optional `item_model` beside a registered base `item` on configured NPC equipment, item relic appearances, and existing item-icon forms. Select a captured native client item definition without replacing the registered item globally or changing gameplay. Preserve display contexts and actual equipment state; use a base-model fallback without altering the real stack, and keep asset readiness strict. Real drops retain the property, so durable resource references and revision retirement become the next dependent decision before that consumer can ship.

Q229-Q231 are recorded in [model lighting, visual bounds, and item appearance](model-lighting-bounds-and-item-appearance.md). [Model and item research](presentation-assets-research.md#model-lighting-visual-bounds-and-native-item-models) records verified facilities separately from the required adapters.

### Q232: retain an appearance archive once real items can use it

Accepted: durably archive each supported custom equipment appearance and its dependencies before any real stack can reference it, including Test draft equipment. Share archived content independently of recent catalog history and retain it conservatively without loaded-world scan-based retirement. Enforce capacity before new admission, expose usage in Minecraft, and preserve existing entries when storage is full. Keep crash ordering explicit, accept harmless surplus retention, and never recreate items from archive records. Client consent, active-attempt activation deferral, and base-model fallback remain unchanged; selective client delivery is the next dependent decision.

### Q233: preserve ordinary stack identity and item transformations

Accepted: use the captured native item-model reference without adding a per-copy or originating NPC/attempt identifier to the stack for appearance retention. Preserve existing entity and recovery identities. Reuse unchanged appearance identities across unrelated hotfixes. Let normal component equality and capacity determine stacking; different model references remain different items for merging purposes. Preserve native copying, splitting, storage, and transformation behavior without automatic ingredient-model inheritance, inventory restoration, or reward replay. Appearance identity remains separate from item ownership and reward receipts.

Q232-Q233 are recorded in [durable item appearances](durable-item-appearances.md). [Native persistence research](item-appearance-persistence-research.md) distinguishes item references from their required art, and [ADR-0021](adr/0021-retained-item-appearance-archive.md) records the conservative retention tradeoff.

### Q234: request only appearances the player can currently need

Accepted: stage permitted missing appearances from server-authorized item references delivered through the player's inventory, accessible menus, or tracked entities, with supported nested references and complete dependency graphs. Coalesce requests and enforce transfer bounds; do not send the whole archive or accept arbitrary client archive queries. Include existing non-item consumers and preflight every required next-attempt asset. Keep declined or pending assets on their accepted fallback path, with bounded retry and no premature readiness.

### Q235: one explicit action to apply staged assets

Accepted: provide one pending-assets indicator and an explicit Apply assets action outside the player's active attempt. Integrate it with start/readiness flows without treating Apply as Ready. Coordinate apply/start admission, wait for finishing finite Conclave audio, bound preparation, preserve still-required old resources, and acknowledge only the completed exact resource-set generation. Leave failed or uncertain application unready with explicit recovery/retry; neither ordinary item discovery nor attempt completion automatically reloads resources.

### Q236: reclaim only unused local cache content

Accepted: use bounded client storage with least-recently-used eviction only for eligible inactive data. Protect applied sets, retained consumers, admitted pending work, transfers, and recovery data, including shared dependencies. Offer Clear unused downloads inside Minecraft, keep permanent server archival separate, and preflight peak storage. If space is insufficient, defer optional downloads or leave required future content unready without evicting live dependencies or changing actual items.

Q234-Q236 are recorded in [archived appearance delivery and client cache](archived-appearance-delivery.md). [Native client research](client-asset-delivery-research.md) records the supported transport, reload, consent, and audio boundaries.

### Q237: what completes a return within grace

Accepted: define a qualifying return as server-committed admission after successful native world placement and verified application of the existing attempt's required captured resources. Keep the original real-time deadline until that commitment, then cancel its expiry. Opening a connection or downloading assets is insufficient. Preserve a remaining opportunity across incomplete returns and retries without extending it; distinguish the actual `reconnected` event from later admission. Keep current life, roles, progress, and running timers, with ordinary post-expiry observation and attempt-end recovery.

### Q238: participation separate from connection and life state

Accepted: add read-only `participation: active|reconnecting|observer`, with `any` for unrestricted selection. Default participant gameplay selection to active admission; retain broad presentation/lifecycle and explicit world-collection behavior. Apply independent eligibility checks to physical mechanic input and revival helpers without restricting ordinary Minecraft or permitted authored state changes. Expose committed changes through `participation_changed` and coherent lifecycle snapshots. Do not confuse admission with camera mode, drop fixed objective members, or let an observer prolong the attempt as a survivor.

Q237-Q238 are recorded in [reconnect admission and captured resources](reconnect-admission-and-assets.md). [Native configuration research](client-asset-delivery-research.md#configuration-before-world-admission) establishes the relevant integration points without claiming an implemented restoration flow.

### Q239: automatic restoration before world entry

Accepted: automatically restore the current attempt's original required resources during bounded connection setup under the player's existing server-resource consent. Reuse a freshly verified compatible applied set or stage and apply the captured dependency graph, including retained consumers. Show built-in progress and remaining original grace. Keep ordinary newer assets pending, bind all work to the actual connection and opportunity, and require Q237 admission after successful native placement. This is a narrow restoration exception to ordinary manual Apply.

### Q240: ordinary entry after failure and explicit retry

Accepted: release Conclave's connection task after refusal, cancellation, failure, or bounded timeout and allow ordinary world entry. Preserve the remaining original opportunity without active mechanic input. Offer explicit Retry restoration for the original resources while grace remains, using serialized application, consent, capacity, consumer, and recovery checks. World gameplay continues during an online reload. Reconcile actual late resource results without restoring expired admission; sync only current authorized state after success, with no replay.

Q239-Q240 are recorded in [restoring resources for a reconnect](reconnect-resource-restoration.md). Q239 controls automatic initial preparation; Q240 controls failure and retry regardless of whether preparation began automatically or manually.

### Q241: confirmed loss of required resources after admission

Accepted: immediately apply Q69's affected-attempt technical interruption when an admitted client's required captured resources are confirmed unavailable. Preserve optional presentation fallbacks and existing documented recoverable outcomes. A routine reload starting is not itself proof of failure, but an uncertain applied set cannot satisfy new required delivery. Do not manufacture disconnection, reconnect grace, or automatic in-place repair. Preserve real disconnect handling, ordinary cleanup/recovery, and already committed gameplay.

### Q242: ordinary Apply for an observation-only participant

Accepted: let `participation: observer` use the existing explicit Apply assets workflow while the observed attempt continues. Include the original observation resources and all retained consumers without replacing the captured revision. Keep observer status and expired grace, existing camera/privacy rules, bounded application, and current-state-only restoration. Passed-out but actively admitted players do not qualify through camera mode. Required presentation still enforces its authored audience; authors can explicitly select active participants when that is their intent.

Q241-Q242 are recorded in [required resource loss and observer assets](client-resource-failures-and-observers.md). They settle two policies not specified by the accepted reconnect workflow: proactive required-resource failure and ordinary Apply eligibility after grace.

### Q243: observe a named phase from encounter rules

Accepted: add `on.source: {phase: phase_id}` to encounter-level rules, with `started`, `completed`, and `failed`. Preserve actual phase activation identity and distinguish phase failure from attempt failure. Terminal events carry simulation-time `elapsed`, frozen `route`, optional `next_phase`, and a registered failure reason for `failed`: `deadline`, `condition`, or `objective_failed`. React only after outcome and route commitment; preserve cancellation, technical errors, private state, queueing, and listener lifetimes. Q245 separately settles ending-scope self-reactions and the attempt-terminal interface.

### Q244: stable reaction order across rule scopes

Accepted: begin eligible reactions to one admitted event in scope-activation creation order, then ordinary YAML rule order within each scope, followed by that scope's export order. Preserve written action order, immutable event observations, live guards, and activation-bound candidate subscriptions. Newly emitted events stay queued. Documented pending actions resume through bounded later continuations without blocking other ready work. Preserve startup barriers, outcome precedence, and cleanup lifetimes; add no YAML priority field.

Q243-Q244 are recorded in [phase events and rule order](phase-events-and-rule-order.md). The named phase interface and ordinary cross-scope reaction order are independent choices; neither grants an ending scope permission to run final actions.

### Q245: final presentation after a committed gameplay result

Accepted: allow encounter, phase, and layers `source: self` rules for committed `completed` or `failed`, with one bounded presentation-only pass before teardown. Support immediate best-effort finite cues and stopping permitted presentation; reject required delivery, gameplay mutations, spawning, rewards, loops, queued starts, retries, and pending gameplay work. Add attempt terminal `elapsed` and failure reasons `phase_failed`/`party_defeated`. Keep normal phase reactions only in surviving encounter scopes, preserve frozen results/routes, and cancel ordinary listeners when their owner ends. Final cues may finish under bounded playback lifetimes after ordinary success/failure without retaining gameplay callbacks. Technical/admin/server interruptions do not run these gameplay final rules; recovery never replays them.

Q245 is recorded in [final presentation from ending scopes](ending-scope-presentation.md). [ADR-0022](adr/0022-presentation-only-terminal-rules.md) records why the final window excludes gameplay work.

### Q246: ordinary work before timed work

Accepted: process already-admitted native/input observations and ready continuations, then spatial observations, before due Conclave work. Order independent due operations by due time and stable work-activation admission order, draining ready reactions between operations. Recheck current eligibility and cancellation without retracting committed history. Preserve aura pulse-before-expiry, final-tick revival-before-grave-expiry, actual elapsed progress, startup barriers, and next-tick progression. Define an external intake cutoff without backdating packets or changing real-time reconnect grace. Native operations keep their supported commit points; exact hooks and budgets still require implementation verification.

### Q247: settle child results before their parent's outcome

Accepted: process ready child-result reactions before committing a still-live parent's outcome in the same tick. Settle nested compositions from children outward, collect sibling outcomes, and re-evaluate affected live conditions within the existing work budget. Do not advance time twice, wait for arbitrary pending actions, reopen committed results, or reroute an ended phase. Freeze phase routes and identify ending scopes before terminal delivery, respecting Q245. Settle ready revival reactions before remaining grave expirations, then arbitrate final attempt success and failure without reopening expired graves. Surviving encounter reactions and cleanup notifications can cause new outcomes before tick closure, while replacement phases and composition steps remain deferred until the next tick.

Q246-Q247 are recorded in [simulation stages and outcome reactions](simulation-stages-and-outcomes.md). Q246 orders ordinary producers; Q247 independently defines propagation from child outcomes into live parents.

### Q248: declarative rewards for encounter success

Accepted: add an optional encounter-level `rewards` list with stable entry IDs and optional names, processed through a dedicated successful-completion capability. Support bounded native items, experience points, and compatible loot-table results from the captured revision. Keep reward delivery outside terminal presentation rules, and do not infer success from a phase end, failure, stop, technical interruption, or restart recovery. Add no generic grant action, lockout system, or arbitrary external side effect. Q250-Q257 subsequently define qualification, delivery, generation, durable commitment, storage policy, and fixed payload fields; native delivery reconciliation still needs implementation verification.

### Q249: inspect rewards by default during Test

Accepted: suppress actual delivery through supported Conclave reward channels in Test by default, recording inspectable would-be output from native NPC loot, equipment drops, XP, and any accepted completion rewards. Add an operator-only Deliver real rewards switch at Test launch, captured per Test and off again for every new Test or Restart test. Do not create production debts or pay an old preview later. Preserve real-arena gameplay and ordinary inventory consequences outside these channels. Require adapters to suppress before payout, with normal compatibility/error handling and no deletion of nearby drops to imitate suppression.

Q248-Q249 are recorded in [completion rewards and Test payouts](completion-rewards-and-test-policy.md). Completion-reward authoring and the Test policy for existing reward channels are independent choices.

### Q250: qualify the captured roster at success

Accepted: default each completion-reward entry to the full captured participant roster, including dead, offline, reconnecting, and observer members. Add a per-entry `recipients` selector restricted to that attempt's participants, with reward-specific `online`, `state`, and `participation` defaults of `any`. Authors may narrow the set using supported filters. Freeze all entries' recipients coherently at the final successful gameplay decision before attempt teardown/recovery, using encounter-visible state and existing lifetime rules; Q254 makes entitlement effective only on durable commitment. Later claims never reevaluate qualification. Each qualified identity earns the entry's allocation; add no implicit effort score, last-hit rule, roster mutation, or recipient count division.

### Q251: private delivery with pending rewards

Accepted: deliver completion items to the recipient's ordinary inventory and XP as private native points. Try automatic delivery after recovery when the recipient is online and alive; retain offline and capacity-limited contents in a personal Rewards view with `/conclave rewards`, individual claims, and Claim all. Preserve partial progress and recorded contents, with no overflow world drops, repeated XP, or rerolls. Defer automatic delivery during another attempt but permit an explicit valid claim. Serialize claims, enforce personal access, preserve native item behavior and asset fallback, and keep Test inspection nonpayable. Do not silently expire pending rewards. Q252-Q257 subsequently settle generation, durable outcome policy, admission/retention, repair authority, and fixed payloads; numeric limits and verified native delivery remain implementation work.

Q250-Q251 are recorded in [reward recipients and delivery](reward-recipients-and-delivery.md). Qualification and delivery are independent choices.

### Q252: resolve a separate reward for each recipient at success

Accepted: generate each qualified recipient's fixed item/XP contents and independent loot result during success preparation, using the same decision state as qualification. Add completion `loot: {table, at}` with a required bound location, initially supporting a verified subset of native chest-context tables with zero luck and no player/combat subject. Use isolated server-controlled random streams, validate actual transitive dependencies and supported generation-only functions, retain final contents rather than relying on a seed, and preserve reload and appearance-archive requirements. Required generation failure precedes immutable success and follows Q69. Q254-Q255 subsequently accept durable success/allocation commitment and storage policy.

### Q253: hold uncertain transfers for operator review

Accepted: when verified recovery cannot determine whether a completion-reward transfer reached native inventory or XP, hold the affected recorded amount as Needs review. Preserve confirmed paid and pending amounts separately. Permit server-operator resolution of an existing uncertain transfer as Record as delivered or Return to pending, showing the exact amount and duplicate risk for reissue and requiring an audited reason. Keep GM diagnostic access separate from repair authority, serialize resolution with claims, and reject stale or repeated resolutions. Do not infer success, reroll contents, mint replacements, repay known-paid records, scan world items as proof, or restore old inventories. Exact native save/receipt integration remains unimplemented.

Q252-Q253 are recorded in [reward generation and uncertain delivery](reward-generation-and-review.md). Generation policy and uncertain-transfer resolution are independent choices. [Native source research](completion-loot-research.md) records the available loot contexts, randomness, dependency limits, and separate player saves.

### Q254: save success and its rewards before announcing victory

Accepted: after final gameplay arbitration, freeze the end time, recipient snapshot, and generated contents, then atomically save success and all payable allocations in Conclave storage before victory notification, attempt `completed`, or native payout. Explicitly exclude storage wait from successful attempt `elapsed`. Bound the live finishing state, close ordinary Conclave gameplay while Minecraft continues, and release world resources through cleanup even if storage remains uncertain. Recover only authoritative committed allocations; confirmed absence means technical interruption, while an uncertain write must be reconciled under its original identity without guessing, rerolling, or replaying final presentation. Native delivery remains a separate save boundary.

### Q255: reserve reward storage before starting and preserve unpaid rewards

Accepted: validate bounded reward output and reserve storage against every potentially qualified captured participant before activation, coordinating concurrent attempts and retaining cleanup/recovery capacity. Refuse new starts when capacity is insufficient, with in-game operator diagnostics and settings. Never expire pending or Needs review rewards to make room. Keep detailed fully paid history for 30 days after final settlement by default, then compact eligible data while retaining durable replay-prevention receipts and required audit evidence. Count retained receipts against budgets; preserve old content only as needed, with appearance archival independent. Numeric capacity defaults remain measured implementation work.

Q254-Q255 are recorded in [durable completion and reward storage](durable-completion-and-reward-storage.md). The durable outcome boundary and storage admission/retention are independent policy choices. Q254 refines Q245's attempt elapsed endpoint and Q250's qualification snapshot timing. [ADR-0023](adr/0023-durable-success-before-reward-delivery.md) records the separate Conclave and native-save boundaries.

### Q256: fixed items and experience in a reward entry

Accepted: use an `items` list with concise native item IDs or configured `{item, count, ...}` descriptions, and `experience` for nonnegative integer XP points. Default count to one and omitted contents to none. Count is the total per qualified recipient, split into legal native stacks without changing their stack limits. Add fixed and table-generated contents together, preserve exact pending remainders, validate bounded quantities and safe native XP delivery, and treat known empty declarations as no-ops with an authoring warning. Reuse existing supported item properties and the accepted Test, durable-allocation, and appearance policies.

### Q257: one explicit enchantment map with normal compatibility

Accepted: use `enchantments: {registered_id: level}` across configured real items, including existing NPC equipment. Enforce defined maximum levels, native representation, item suitability, and pairwise compatibility; reject rather than silently clamp or drop invalid input. An omitted map preserves supported item defaults; a present map replaces the selected initial enchantment channel, with an empty map clearing it. Use native stored enchantments for enchanted books and applied enchantments for supported ordinary items. Add no unsafe/over-level switch or automatic type conversion, and leave unrelated inventory items unchanged.

Q256-Q257 are recorded in [fixed reward items and enchantments](fixed-reward-items-and-enchantments.md). Fixed reward fields and the explicit enchantment format for already accepted configured real items are independent choices. [Native research](completion-loot-research.md#item-payload-and-experience-bounds) records the difference between total quantities, native stacks, enchantment validation, and safe XP delivery.

### Q258: native item names and lore with captured formatting

Accepted: add `name` and `lore` to configured real items, using literal strings or a `{text, style}` value for supported line formatting. Store the name as a native custom name with ordinary anvil rename/removal, and store lore as native lines. Omission preserves supported base defaults; empty name removes its custom-name component, and an empty lore list clears lore. Copy supported captured style values into the native text so publication does not rewrite existing items or pending rewards. Use normal native scale; Q260-Q261 subsequently add captured translations and archived custom fonts to the original default-font form. Keep literal text, bounded validation, native equality, and no executable or private dynamic content.

### Q259: reusable item definitions with explicit use sites

Accepted: add an `item` manifest with definition `id`, optional editor label `name`, and a quantity-free `stack` containing the configured native item properties. Equipment and fixed reward entries may select the definition using `use`, with optional count at the use site. Preserve native `item` references, typed namespace resolution, per-consumer stack limits, immutable captured contents, and ordinary native equality without a definition ID stamped on the stack. Reject inheritance, nested reuse, property overrides, and mechanic-style parameter arguments. Begin with equipment and completion rewards; other consumers remain explicit future work.

Q258-Q259 are recorded in [item text and reusable configured items](item-text-and-reusable-items.md). Native text fields and reuse of already accepted item configuration are independent choices. [Native item-text research](item-text-research.md) distinguishes stored literal components from font and translation dependencies.

### Q260: item translations follow the viewer and retain the original text

Accepted: add per-line `translations` maps under configured item `name` and lore text mappings, with required default `text` and shared style. Resolve the current viewer's exact supported locale or the authored default; language changes update rendering without changing the stack or reward. Archive immutable translation content before real-item exposure and retain a literal-preserving default fallback on the item. Preserve content-based identity, native equality, ordinary anvil changes, authorized demand delivery, consent, explicit Apply, and bounded cache. Native English merging and placeholder parsing require a verified adapter rather than assuming raw language files satisfy the contract.

### Q261: archive custom item fonts and provide an ordinary-font fallback

Accepted: permit `text_style.font` for native item text through verified TrueType/bitmap adapters and supported bounded spacing/reference dependencies. Archive the complete immutable font graph before real stacks reference it; preserve normal scale, copied non-font styles, glyph validation, native equality, and permanent retention. When the exact font is unavailable, use a temporary ordinary-font rendering of the same text without changing the item. Extend the existing authorized item-demand, consent, explicit Apply, and cache workflow. Fallback permits ordinary item use but does not satisfy separately required font readiness or presentation. Other provider formats require explicit supported adapters.

Q260-Q261 are recorded in [durable item localization and fonts](durable-item-localization-and-fonts.md). Each independently extends Q258 and the archive for its own resource type; either can be used without the other. [Native text research](item-text-research.md) records the relevant locale, formatting, font, synchronization, and anvil boundaries.

### Q262: keep the accepted target and build it in complete stages

Accepted: keep every accepted capability as the first complete framework release target, build in dependency-ordered development stages, and finish remaining behavior/schema details before adding unrelated feature families. Stages cover runtime, in-game authoring/operation, gameplay coverage, presentation/retained assets, and durable completion/release verification, with foundational recovery and resource contracts implemented before dependent effects. Preserve the initial 18-NPC coverage and all explicit exclusions. Internal fixtures and test worlds verify framework behavior without shipping an encounter. Incomplete development builds must advertise actual support; approval of staging does not authorize implementation before shared understanding.

### Q263: one typed extension interface with engine-owned lifetimes

Accepted: use typed Kotlin capability interfaces and authoritative descriptions for built-ins and addons, sharing schemas/help, validation, immutable configuration, scopes, scheduling, cancellation, ownership, and diagnostics. Keep capability behavior local and native compatibility behind adapters; gameplay results and background continuations reenter the accepted ordered execution path. Register trusted installed Fabric addons at startup and freeze the catalog for the session, with no runtime code upload or JAR loader. Data/assets retain the accepted future-attempt publishing workflow; code changes require installed updates and restart. Q269 defines public interface compatibility, and the interfaces do not claim to sandbox arbitrary JVM code.

Q262-Q263 are recorded in [framework delivery and Kotlin extensions](framework-delivery-and-extensions.md). Delivery scope and shared extension responsibilities are independent choices. A local audit confirmed that the repository is still documentation-only and that accepted lifecycle/validation requirements have no implemented Kotlin interface yet.

### Q264: player travel preserves participation and uses dimension-aware spatial checks

Accepted: preserve roster membership, combat classification, roles, counters, aura lifetimes, and ordinary survival/reward eligibility when players travel far away or change dimension. Spatial checks require matching dimensions; nonspatial selection adds no implicit proximity restriction. Use a usable actual remote death location for the grave, retain the captured revival policy and same-attempt nearby helper requirement, and use Q46's safe fallback when needed. Keep grave state independent of unloaded visuals, with bounded resources for actual presentation/revival and no moving retention footprint. Relics retain their own return-home policy. Q66's existing end-of-attempt regrouping still returns all participants to the captured recovery anchor. This permits native travel without adding multi-dimension authored arena layouts.

### Q265: follow owned NPC identity while simulation is available

Accepted: let owned NPCs roam and undergo supported native dimension transfers without automatic confinement or moving chunk tickets. Track the same logical member and its state through verified native replacement; do not fabricate spawn, conversion, defeat, or rewards. Continue while required simulation is available, including incidental availability outside retained chunks. Confirmed loss for a required member follows Q69 technical interruption; an ungrouped descendant with no required dependency may remain natively unloaded with ownership retained. Ended-scope cleanup must reconcile stale bodies before later gameplay, using bounded recovery and durable records. Preserve the one-dimension authored layout and fixed arena footprint.

Q264-Q265 are recorded in [travel, remote graves, and NPC simulation](movement-and-simulation.md). Player travel/revival and owned-NPC simulation are independent choices under Q103's ordinary-world policy. [Native movement research](entity-movement-research.md) records the relevant identity and ticking distinctions without claiming a working integration.

### Q266: one geometry format for arena boundaries and named areas

Accepted: use `type: box | cylinder | sphere | composite`, named `areas` and `locations` lists, explicit world `position` or arena-local `location` plus optional `offset`, and horizontal box `rotation`. Boxes/cylinders use the horizontal center of their base; spheres use their center. Compose geometry with nonempty `include` and optional `exclude`, accepting local area references or inline geometry. Exclusion wins, including its own boundary. Use the same geometry or a local area reference for `arena.boundary`; validate cycles, expansion bounds, and full authored containment. Preserve Q80's existing encounter binding maps, editor controls, and future-attempt publication.

### Q267: name the three membership modes and apply them to the final volume

Accepted: place `membership: position | overlap | contained` on a named area, defaulting to feet/base position. All consumers use that area's policy. Body tests use current native physical bounds against the final composed volume and require the same dimension. Keep eligibility separate, preserve spatial event ordering, and do not approximate whole-body containment with a corner-only check. Geometry references ignore the referenced area's membership, allowing a second named area to reuse the shape with another policy. Inline geometry and arena-boundary queries do not gain entity-membership settings.

Q266-Q267 are recorded in [area fields and membership](area-fields-and-membership.md). Geometry representation and placement of the already accepted membership choices are independent decisions. A local audit found no earlier accepted geometry field spellings or per-use membership override to replace.

### Q268: require matching core code before world entry

Accepted: require the same Conclave release/build on the server and every connecting client, extending the earlier participating-client requirement so global revival and camera behavior have compatible code. Refuse incompatible core code before world admission with a readable explanation. Server-only addons need no client counterpart; registered global client requirements gate entry, while content-specific requirements gate the applicable attempt/editor operation. Preserve ordinary entry/play when compatible clients decline or cannot apply assets, including required captured assets whose absence blocks encounter readmission. Code checks do not complete reconnect placement, resource readiness, or encounter admission.

### Q269: version the small public extension API separately from content

Accepted: keep development interfaces experimental, then establish public API major version 1 at the first complete release. Preserve supported source/binary compatibility within an API major and documented Minecraft/runtime line. Planned breaking changes require a new major, prior deprecation, and migration guidance. Addons declare supported platform/API ranges and client requirements; internal helpers carry no public compatibility promise. Validate configuration, installed code, and client support separately without adding version fields to every manifest use. Missing or incompatible support leaves authored data intact and fails activation/revalidation explicitly. Q272-Q273 define content upgrades and retained-state startup recovery; storage-format migration and backup/restore remain separate contracts.

Q268-Q269 are recorded in [client code and extension compatibility](code-compatibility.md). Connection admission and the public addon API promise are independent choices. Before this decision, the audit found no accepted blanket refusal for missing executable client code or public API stability promise. Accepted asset-refusal behavior remains separate.

### Q270: group global gameplay settings by revival, spectating, and recovery

Accepted: use `settings.revival`, `settings.spectating`, and `settings.recovery` with the accepted defaults, explicit duration units, percentage health, native food-level recovery, and separate helper reach/search radius. Name the encounter ban `revival.prohibit_self_revival`, valid only under the global permission. All-methods-disabled deaths pass directly to passed-out viewing inside attempts; outside attempts retain normal Respawn. Ordered world-location fallbacks apply only to unsafe outside-attempt grave revival. Materialize default settings in captured policy; preserve operator authority, future-attempt/death publication, and the separation from gamerules and operational controls.

### Q271: explicit grave controls and teammate selection after passing out

Accepted: keep a bounded terrain-clipped grave camera with only the player's own status and eligible revival controls. After passing out, teammate mode offers first-person viewing, Previous/Next and a compact picker of living online actively admitted teammates. Keep a valid target, advance in stable roster order when it becomes unavailable, and wait at the grave if none is available. Q67 returning living observers can explicitly enter/leave viewing without another death; with no target they return to their own camera instead of acquiring a grave. Support eligible remote teammates through bounded camera integration without fabricating gameplay movement. Private sharing remains off by default; when enabled, mirror only the authorized current teammate presentation and new cues, clear it on switching, and never replay expired output. Free mode preserves unrestricted native spectator movement while keeping private-data permissions separate.

Q270-Q271 are recorded in [global settings and spectator controls](settings-and-spectator-controls.md). Settings fields and the viewing controls are independent choices for already accepted capabilities. The document makes the disabled-method, hunger-unit, world-fallback, and private-mirroring choices explicit.

### Q272: upgrade content through a separate reviewed draft

Accepted: provide in-game Upgrade draft using supported installed converters, preserving the source text and producing a new candidate with affected-file differences, behavior notes, and complete validation. Unsupported conversions remain editable with guidance. Respect settings authority and draft/version conflicts. Upgrades do not trigger auto-publish; first activation is explicit Publish after review. Keep active lifecycles and immutable history untouched. Content conversion cannot rewrite durable rewards, ownership, native saves, or an uncertain completion commitment, and introduces no new manifest schema version by itself.

### Q273: keep content repair available and block only unsafe operations

Accepted: permit ordinary Minecraft and in-game repair when core/global lifecycle behavior remains sound but the current content catalog is invalid. Block published starts until an operator activates a complete compatible revision, without automatic rollback or partial-catalog activation. Continue separately validated captured global policy and safe existing recovery. Known isolated recovery faults block only conflicting work; unavailable accounting holds dependent payouts and every attempt needing its recording path, including empty-reward success, without stopping independently safe cleanup. Refuse unsafe startup/admission when executable initialization or unreadable unbounded ownership/global state prevents safe operation. Preserve records and stable operation identities, distinguish uncertain commitment from uncertain native payout, and add no force-forget/reset workaround.

Q272-Q273 are recorded in [content upgrades and startup recovery](content-upgrades-and-startup-recovery.md). They select the content-upgrade workflow and startup failure responses independently. They preserve the existing ordinary-play, complete-revision, scoped cleanup, Test, and reward-accounting contracts. Q276 subsequently accepts coordinated backup/restore; Q277 accepts the separate durable storage-upgrade workflow.

### Q274: explicit targets and ordinary validation for GM operations

Accepted: expose start, stop, restart, forced participant revival, existing relic reset, persistent-aura administration and original-record recovery retry through the Minecraft screen and explicit commands. Manual start bypasses authored triggers/readiness but preserves engine admission and the declared roster selector. Restart cleans up before selecting a fresh revision. Forced revival can override ordinary revival restrictions but cannot change participation or run after the outcome freezes. Preserve aura source ownership and captured definitions. Limit privileged sources to GM/operator players and trusted local console, keep operator-only powers separate, and audit mutations without mandatory confirmation for every routine command. Add no phase jump, forced result, roster editing or force-forget operation.

The complete command forms, state checks, aura operations and audit choices are accepted in [administrative operation contracts](administrative-operation-contracts.md).

### Q275: complete the existing typed event catalog

Accepted: keep flat documented gameplay fields, immutable event measurements and typed identities; retain general engine bookkeeping in diagnostics instead of adding a mandatory public envelope. Define area entry/exit and bound-block pre-start interaction fields, aura source/contribution/count/refresh fields, named timer expiry fields, and the resolved NPC health-floor threshold. Preserve explicit player matching, private scopes, captured definitions and original lifecycle identities. Use duration-valued remaining time instead of wall-clock expiry timestamps. Keep named timers' initial notification catalog at `expired`; additional timer state notifications remain optional future work. Do not add arbitrary event emission, field traversal or automatic exports/client broadcast.

The source forms, fields, optionality and reason values are accepted in [event catalog conventions](event-catalog-conventions.md). Already specified event families retain their existing contracts.

### Q276: complete backups and restoration through stopped-world maintenance

Accepted: add operator-only Back up and stop and Restore backup and stop in Minecraft, with normal server startup afterward. Queue behind existing attempts while blocking new admission, recheck authority before shutdown, close required writers and hold verified exclusive access. Back up matching world/player data, Conclave records and permanent resources together. Restore only a verified compatible complete set, preserve a verified pre-restore backup, and recover interrupted replacement through a durable journal before world admission. Preserve current administrative authority independently of restored gameplay data. Keep pending and uncertain obligations intact, with no receipt/inventory mixing or reward reconstruction. Downtime and normal host restart are explicit; no companion CLI, live-snapshot claim or in-process server restart is introduced.

The workflow, included data, restore consequences and exceptional host-recovery boundary are accepted in [coordinated backup and restore](coordinated-backup-and-restore.md). [Native research](backup-recovery-research.md) establishes why save/flush, freeze and background copying alone do not prove a consistent snapshot.

### Q277: approve a specific durable storage upgrade before replacing code

Accepted: version durable storage separately from YAML and API versions. Import a bounded declarative target-release upgrade plan into the working release and review it through operator-only Prepare storage upgrade. Bind one authorization to the concrete transition, participating providers and a verified closed source backup. After normal mod installation, trusted target-release converters verify that plan and migrate staged copies before any world admission or native gameplay write. Preserve original identities, captured policy, obligations, uncertainty and resources. Journal interrupted conversion/installation, refuse partial sets and automatic downgrades, and require complete compatible snapshot restore after any post-migration live-state mutation, including startup recovery. Unsupported preparation requires a compatible intermediate release or the documented host-recovery boundary, never an unsafe world opened just to ask for approval.

The source/target checks, preparation bridge and rollback boundary are accepted in [durable storage upgrades](durable-storage-upgrades.md). This selects product behavior without choosing a storage engine or claiming a verified migrator.

### Q278: define the optional revival protection boundary

Accepted: keep protection off by default. A configured positive duration blocks supported ordinary combat and environmental damage before health/absorption loss, but not authorized administrative removal or explicit encounter outcomes. Count server simulation time independently of combat. End protection before a supported deliberate attack or offensive item/ability activation, even when damage is blocked; ordinary movement, building, helping and passive/previously launched effects do not count as new hostile input. Show the remaining protection, retain elapsed time across lifecycle interruptions and never grant a fresh duration on reconnect. Add no general player immunity setting or new combat-event family.

The damage categories, lifetime and input distinctions are accepted in [revival protection](revival-protection.md). Q46/Q270 had accepted the configurable duration and early termination while leaving these gameplay boundaries unspecified.

### Q279: finish aura sharing and the initial world-effect behavior

Accepted: default aura display to the affected player's HUD. An explicit display audience can share a compact icon/count/countdown strip above a player or NPC, with name/description inspection. Continuously select eligible aura viewers and restore current state without replaying old cues. Keep ordinary entity tracking and occlusion. Begin reusable visual-effect profiles with bounded particles and a glowing ring, addressed through the existing `show_effect` action or an aura's optional attached visual. Action playbacks preserve their invocation recipients; one-shots capture an origin, while following a live original target is explicit. Aura visuals follow their holder under its independent lifetime, with a nested audience replacing the aura-display default. Preserve captured resources, required/best-effort delivery and existing playback cleanup; visuals never define collision, damage or gameplay geometry.

The default disclosure, other-viewer placement, initial effect profiles and attachment behavior are accepted in [aura and world visuals](aura-and-world-visuals.md). Exact schemas, renderer APIs, particle subtype coverage and numeric limits remain engineering work under that behavior.

## Current decision frontier

None. Q277-Q279 are accepted and shared understanding is confirmed. The closure audit found no further required product decision in the agreed scope.

## Later decisions

Q277-Q279 complete the storage-upgrade, revival-protection and aura/world-visual contracts. Product choices are settled for the agreed initial target. No new feature round is required solely to choose mechanical field spellings or discover implementation facts.

The closure audit traced older unresolved notes to later accepted decisions. It found no other required product branch in the reviewed gameplay, authoring, administration, NPC, combat, item, client and extension contracts. The consolidated design above is the reviewable target for final confirmation. World-owned locations, area syntax, ordinary travel, and code/API compatibility stay settled.

Implementation and verification still need exact native hooks, compatible dependency pins, complete capability schemas, external-dependency fingerprinting, client synchronization, storage and crash reconciliation, migrations/coordinated backup handling, and measured runtime/transfer/storage budgets. This includes the remaining typed action/predicate fields and recoverable-action handling syntax under Q69's existing policy. Detailed editor layout follows the accepted in-game capabilities. A schema or static source audit is not evidence that these adapters work. Material tradeoffs discovered while verifying an accepted promise must return for a decision; mechanical details can follow the accepted contract.

Further item properties, richer text, new combat systems, extra NPC/provider coverage, dynamic participant tracking, general ambient rules, and new world-change/action families are optional expansions. Q262 leaves them outside the initial target absent a concrete requirement. This does not defer behavior already required by ordinary player movement or remove accepted supported-world-change cleanup. Arena copying remains v2, active-attempt migration and restart resumption remain excluded, and specific encounter content remains outside the shipped framework.

## Shared-understanding confirmation

Confirmed by the user on 2026-09-30: "Shared understanding confirmed". The consolidated design and its accepted contracts are the implementation target. The interview is concluded and implementation may proceed through Q262's development stages. The interview produced design documents and research; implementation evidence is tracked separately.

## Hotfix feasibility notes

Fabric documents reloadable custom data and staged resource loading, with preparation separate from application on the engine thread. Conclave still needs to implement YAML handling, validation, publication, and retention of definitions used by active attempts. See [custom resources](https://wiki.fabricmc.net/tutorial:custom_resources) and [resource reload stages](https://wiki.fabricmc.net/tutorial:resource).

Existing client behavior can receive updated presentation data through [Fabric networking](https://docs.fabricmc.net/develop/networking). New code and new client assets have different delivery requirements; a server manifest reload alone does not distribute or install them. This is an inference from Fabric's separate [data and asset reloads](https://docs.fabricmc.net/develop/debugging) and its description of [class hotswapping as a debugging facility](https://docs.fabricmc.net/develop/getting-started/intellij-idea/launching-the-game#hotswapping-classes).
