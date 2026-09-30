# Death and revival

Status: the death flow, Q38-Q47 policies with the user's Q45-Q46 revisions, Conclave-owned revival and spectating modules, and Q66-Q67 recovery policies are accepted. The [Location anchor](location-anchors.md) authoring model is accepted in Q48-Q52. Anchors also configure areas; initial geometry, visualization, spatial editing, and spatial events are accepted. Minecraft Java 26.2 with Fabric is the selected target; no implementation exists yet.

## Accepted death flow

Lethal damage creates a grave at the player's death location. The dead player remains at that grave with a constrained third-person camera and can look around it. They retain only information available to themselves, do not enter spectator mode, and do not watch other players' cameras during this revival opportunity.

Assisted revival is available and instant by default. Self-revival is disabled by default. A globally configured 15-second revive window spends time only in combat. A death outside combat has no running window; entering combat starts it, leaving combat pauses its remainder, and returning resumes that remainder. After it expires, the player can no longer revive and respawns only after the current attempt finishes.

After the player passes out, global configuration selects watching other players or unrestricted spectator game mode. First-person spectating does not grant the watched player's private information by default, but a global option can permit it. This is an explicit revision of the earlier blanket restriction against unrestricted spectating.

Revival is distinct from respawning after an attempt. Combat is explicitly declared as described in Q38. Q43 permits voluntary normal respawn outside attempts. Vanilla gamerules govern ordinary inventory and XP behavior under the user's Q45 revision.

## Accepted global controls

| Control | Requirement or default |
|---|---|
| Revival availability | Can disable revival entirely; self and assisted methods are separately configurable. Self is off and assisted is on by default. |
| Combat window | 15 seconds by default, globally configurable; no such limit outside combat. |
| Eligibility and interaction timing | Globally configurable eligibility delay and helping interaction time both default to zero. |
| Encounter self-revival restriction | Globally permitted by default; encounters may prohibit self-revival but cannot enable a globally disabled method. |
| After passing out | Teammate viewing by default, with unrestricted spectator mode available globally; private watched-player information is off by default and globally configurable. |

These are global gameplay settings, not per-encounter copies of the timer. Q39 defines precedence, and active attempts retain their starting effective settings. [Q82](manifest-references.md#q82-global-gameplay-settings) assigns them to the operator-controlled singleton `settings` manifest and Server settings page.

[Q270-Q271](settings-and-spectator-controls.md) accept the concrete settings fields, immediate passed-out transition when all methods are disabled, camera controls, watch-target eligibility, and private presentation mirroring. The existing defaults and those remaining choices are accepted.

Revival restores 100% of maximum health and grants zero seconds of damage protection by default. Authors can configure these values through the global revival settings. An authored positive protection duration ends early if the revived player attacks and cannot prevent an explicit encounter wipe.

## Relationship to existing decisions

The existing per-attempt revision policy remains in force: applying a new global gameplay configuration must not rewrite an active attempt's effective rules. Both owned modules must use the appropriate attempt policy, including for a death that occurs after a newer revision was published. Updating one mutable process-wide timeout is not sufficient.

Disconnected or dead players still cannot contribute to capture or interaction. Existing relic death and disconnect policies remain separate from inventory and grave presentation. Reconnecting must not reopen an expired revive window, recreate a carried relic, or restore an obsolete copy of the participant's state.

Q40 refines party defeat to preserve valid self-revival and reconnect opportunities.

## Q38: combat and system scope

Accepted: the globally configured revival system works server-wide, including outside Conclave attempts. Encounter YAML explicitly declares combat, and phases inherit that setting unless they override it. Outside an active attempt, use noncombat behavior by default. Recent damage alone does not classify a puzzle or environmental hazard as combat.

Q43 defines normal respawning outside attempts, and Q44 defines how changing combat state affects an existing grave. Integration with an external combat provider remains undecided.

## Q39: global and encounter precedence

Accepted: global configuration sets the maximum available revival methods. Encounter self-revival restrictions are permitted globally by default. An encounter cannot enable a method globally disabled, change the global revive-window length, or bypass the global all-revival switch. Ordinary revive actions in YAML enforce the same policy as player interactions.

The eligibility delay defaults to zero. Validation must reject negative or unbounded numeric inputs. Exact upper bounds and diagnostics for combinations that cannot fit in a combat revive window remain part of schema design; no new validation limits are implied by accepting the default.

Q41 authorizes explicit administrative recovery commands to override ordinary revival restrictions. They use a distinct privileged operation and an audit record.

## Q40: when no living helper remains

Accepted: if no living actively admitted participant remains and nobody has an eligible allowed self-revival or another explicitly supported recovery path, the attempt ends without waiting for unusable revive windows. If allowed self-revival can still occur, keep the attempt running until that chance expires or another encounter outcome resolves it. Preserve the existing opportunity for a living participant with `participation: reconnecting`, only within its original grace. A living observer is neither an active survivor nor a recovery opportunity. Administrative intervention is not an ordinary recovery opportunity that keeps an attempt alive.

The server resolves competing revival, expiry, and attempt-end transitions once. Q44 accepts valid revival completion on the final permitted tick before expiry; an attempt that has already ended cannot accept a later revival. Q43 and Q47 define ordinary outside-attempt and disconnected-grave behavior.

## Q42: default after passing out

Accepted: teammate viewing is the global default after the revive window, with unrestricted spectator mode available explicitly. Private watched-player information remains off by default as requested. If no teammate can be watched while reconnect grace remains, retain a grave-bound waiting view rather than silently switching modes.

Reviving through ordinary gameplay remains unavailable after passing out even if the player is watching someone. An accepted administrative override uses a distinct privileged operation.

## Implementation direction

Accepted: implement revival and spectating as Conclave-owned modules. Revival owns graves, eligibility, timers, and return to active play. Spectating owns the grave camera, later viewing modes, and disclosure of Conclave information. The server decides state and permissions; the required client presents the selected view.

Both modules use the same authoritative player lifecycle and effective gameplay policy. No external revival or spectating mod is selected. This decision follows the [Fabric 26.2 dependency evaluation](revival-dependency-research.md) and is recorded in [ADR-0011](adr/0011-owned-revival-and-spectating.md). The interview continues before implementation; packaging, public interfaces, persistence, and compatibility with other death-handling mods still require design.

## Q43: normal respawning outside attempts

Accepted: outside an active attempt, a dead player can wait indefinitely for help or choose Respawn to return to the server's normal respawn location. Respawn abandons the revival opportunity and cannot be used to revive at the grave. If no revival method is enabled, offer normal respawning immediately. This avoids permanently trapping a lone player while preserving the default prohibition on self-revival.

Inside an active attempt, voluntary normal respawn remains unavailable; ordinary recovery after passing out waits for attempt end. A completed respawn closes the old revival opportunity exactly once. Inventory treatment follows Q45.

## Q44: combat changes and expiry ordering

Accepted: only combat simulation time consumes the revive window. A death outside combat has no running window; entering combat starts the full global window. Leaving combat pauses any remaining time, and returning to combat resumes that remainder instead of granting a fresh window. An already expired window stays expired for the rest of the attempt.

The initial eligibility delay measures simulation time since death, independent of combat. A held help interaction also uses simulation time. A valid revival completing on the final permitted server tick wins over expiry; the server commits one terminal outcome. An attempt already ended cannot accept a later revival. [Q247](simulation-stages-and-outcomes.md#revival-before-the-final-expiry-boundary) accepts settling ready eligible result reactions, including ordinary YAML revival, before remaining grave expirations and final attempt arbitration. Reactions made possible only by a committed expiry cannot reopen that expired grave.

Outside an attempt, capture effective global Conclave death settings when that death occurs, so publishing does not rewrite an existing grave's revival rules. New deaths use the newly active global settings. Q116 rejects admission when a selected participant is still dead; starting an attempt does not implicitly revive them.

## Q45: inventory and grave contents

Revised by the user: vanilla gamerules control ordinary inventory and XP retention or loss. Conclave adds no competing keep-inventory setting, inventory rollback, or grave-loot storage system. Encounter relics continue to follow their separately accepted death and respawn policies and must not acquire a second ordinary dropped copy.

The implementation must preserve vanilla death consequences once for each death, then avoid applying them again during revival, window expiry, or attempt-end recovery. Merely cancelling lethal damage does not establish that contract. Vanilla gamerules remain server-owned settings and follow vanilla administration; they are not copied into Conclave's published gameplay revision. The exact integration with the selected server death path will require implementation verification.

## Q46: safe revival and return health

Accepted with an addition: use the death location for the grave and revival when it is usable. For a void death or a position without usable footing, use the most recent valid standing location in the same arena, with an authored recovery location as fallback. Outside attempts, fall back to the normal respawn location if no recent safe location is usable. Record the actual death position separately for diagnostics.

Authors can place invisible Location anchors that act as respawn locations, reference them in YAML, and configure entity spawning at desired positions. Each has a name label above it and an intuitive, human-readable in-game configuration interface. Naming, visibility, placement behavior, and synchronization with YAML are accepted in [Location anchors](location-anchors.md). Area configuration is a further accepted capability with remaining spatial choices in [areas](areas.md).

The user changed revival defaults to 100% of maximum health and zero seconds of damage protection, with author overrides supported under the existing global policy. If protection is configured, a hostile action by the revived player ends it early. Validate the destination again when reviving; if it has become unusable, use the fallback policy. Q65 in [runtime semantics](runtime-semantics.md#q65-aura-death-behavior-stacks-and-display) defines aura retention. Q278 accepts ordinary combat/environmental damage coverage, the deliberate-hostile-input boundary and the administrative/explicit-result exceptions.

[Q264](movement-and-simulation.md#q264-player-travel-preserves-participation-and-uses-dimension-aware-spatial-checks) accepts using a usable actual death location outside the arena or in another dimension, with this existing fallback order and bounded grave resources. It preserves captured revival rules and Q66's regrouping after the attempt; the remote-location refinement is part of the accepted revival policy.

## Q47: helper eligibility and interrupted assistance

Accepted: during an attempt, an ordinary helper must be a living, online participant with `participation: active` in that same attempt. Outside attempts, any living, online player who is not participating in another attempt may help. The target must be online. Q238 permits a reconnecting target whose grave and revival window remain valid; revival changes life state without granting admission or resetting grace. Observer revival cannot reopen admission or prolong the attempt as a recovery opportunity. Require line of sight and a globally configurable reach of three blocks by default.

If the configured interaction takes time, one helper performs it at a time. Releasing the interaction, losing reach or line of sight, taking damage, dying, losing helper admission, or disconnecting resets that helper's unfinished progress. Multiple helpers cannot pool progress or accelerate it. Instant assistance remains the default and only one valid request can commit a revival.

A dead participant's combat window continues on the attempt's simulation clock while they are disconnected; reconnecting does not reset it. The existing real-time reconnect grace remains separate. Q67 defines accepted behavior after grace expires. [Q120](cleanup-and-restart.md#q120-interrupted-attempts-after-a-server-restart) accepts interrupted-attempt cleanup and durable recovery instead of mid-attempt resume.

## Validation notes

Validate participant identity, current state, grave identity, method availability, helper eligibility, range, and timers on the server before applying a revival. Process the state change once, so two helpers or repeated requests cannot duplicate a revival, grave, or inventory transfer. ASVS 2.1.1, 2.2.1, 2.2.2, 2.3.1; stronger atomicity guidance from ASVS 2.3.3 and 15.4.2 also applies.

The global helper reach defaults to three blocks and the eligibility rules are accepted in Q47. Numeric upper bounds remain part of schema validation design. No implemented verification is claimed.

Fabric's [26.2 death events](https://github.com/FabricMC/fabric-api/blob/26.2/fabric-entity-events-v1/src/main/java/net/fabricmc/fabric/api/entity/event/v1/ServerLivingEntityEvents.java) distinguish cancelling fatal damage from observing an actual death. Inference: an integration that cancels death needs additional work to preserve vanilla consequences; choosing that event alone cannot prove gamerule compatibility. Mojang's [26.2 pre-release notes](https://feedback.minecraft.net/hc/en-us/articles/46153634280333-Minecraft-Java-Edition-26-2-Pre-release-1) also document a fix involving items entering dead players' inventories with `keep_inventory` disabled, so that boundary belongs in the later verification.

## Q66: recovery after an attempt ends

Accepted: after success, wipe, or administrative stop, clear attempt-owned gameplay state and regroup the participants at the selected recovery Location anchor. Restore the camera and game mode that Conclave temporarily changed. Refill health and hunger by default, with global configuration for recovery values. Ordinary item and XP consequences continue to follow vanilla gamerules; consumed items and an earlier inventory snapshot are not restored.

Clear Conclave-owned effects and temporary state without deleting unrelated effects or world state. Recovery after an attempt is a distinct operation from helping someone at a grave, so the normal revive-window restriction does not prevent it. It must not invoke a second death or duplicate drops.

For an offline participant, record recovery still owed and complete it when they return. Retain the resolved recovery destination and applicable cleanup context from their attempt, rather than silently using another encounter's current settings. The accepted safe-destination policy applies, including waiting with a GM-visible error if every permitted destination is unusable. [Q120](cleanup-and-restart.md#q120-interrupted-attempts-after-a-server-restart) accepts the interruption policy. Exact durable record format and reconciliation remain implementation design work.

## Q67: returning after reconnect grace expires

Accepted: expiration of reconnect grace removes the player's eligibility to rejoin that attempt through ordinary gameplay and commits `participation: observer` under Q238. Keep their roster identity and history for recovery and diagnostics, but exclude them from active gameplay. If they return while the attempt continues, they may observe under the configured spectator policy. Reconnecting does not grant another grace window or bypass the prohibition on late joining.

A disconnect timeout is not another death and does not create additional vanilla drops or XP loss. Once no valid active or reconnecting survivor or permitted self-revival remains, the accepted party-defeat rule can finish the attempt.

[Q172](role-and-player-events.md#q172-participant-lifecycle-events) accepts rule notifications for committed death, revival, passing out, and connection transitions. These report the existing lifecycle policies without changing them.

If the attempt has already ended, complete the pending recovery under Q66 on return. Administrative overrides remain explicit privileged operations; this policy does not silently broaden an ordinary revive interaction into roster admission. Detailed override command names and optional late-joining behavior remain part of the administrative and admission contracts.

[Q278](revival-protection.md) accepts the coverage and hostile-action boundary for positive revival damage protection. The accepted default remains `0s`; the contract adds no roster-based immunity.
