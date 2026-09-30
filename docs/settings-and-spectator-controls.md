# Global settings and spectator controls

Status: Q270-Q271 are accepted. The underlying revival, camera/privacy, recovery, publication, and operator permissions are accepted. This round selects the settings fields and the remaining player controls. No settings schema or camera implementation exists.

## Q270: group global gameplay settings by revival, spectating, and recovery

Accepted: use the singleton `settings` manifest with `revival`, `spectating`, and `recovery` blocks. Expose the accepted defaults directly, materialize omitted defaults in the captured policy, and keep operational settings and vanilla gamerules separate.

```yaml
schema: 1
settings:
  revival:
    enabled: true
    self_revival: false
    assisted_revival: true
    encounters_may_disable_self_revival: true
    combat_window: 15s
    delay: 0s
    help_time: 0s
    help_reach: 3
    health: 100%
    damage_protection: 0s
  spectating:
    mode: teammates
    share_private_info: false
  recovery:
    health: 100%
    hunger: 20
    search_radius: 3
    outside_attempt_fallbacks: []
```

This is a complete settings example, not encounter content. All fields are optional with the displayed defaults. An omitted settings document supplies the same built-in defaults for a newly validated revision; it does not replace policy already captured by an attempt or grave. The singleton has no `id` or `namespace`, and duplicate settings documents remain invalid.

### Revival fields and precedence

`enabled` disables all ordinary revival when false. The two method switches remain independently configurable and retain their authored values while the master switch is off. Neither method can bypass it. `combat_window` must be a positive duration and spends time only during declared combat. `delay`, `help_time`, and `damage_protection` permit zero and require duration units. `help_reach` is a positive finite distance in blocks, with line of sight still mandatory.

`delay` applies to both methods, measuring simulation time since death regardless of combat. `help_time` is the continuous eligible assistance time from one helper; self-revival is an explicit instant action after the delay when permitted. The helper interruption/reset rules remain those of Q47. Show a warning when configured timing cannot fit within continuous combat, rather than rejecting a configuration that can intentionally allow revival during noncombat. Preserve the valid-final-tick revival rule.

`health` is a percentage greater than zero and at most `100%` of the player's effective maximum health at the successful revival. `damage_protection` uses the accepted early end on hostile action and cannot block an explicit attempt result. These fields do not add a hunger refill to ordinary revival or override vanilla item/XP consequences. Supported native damage handling and measured numeric maxima remain adapter/schema verification work.

Use optional `encounter.revival.prohibit_self_revival: true` to forbid self-revival for that encounter. It defaults to false. A true value requires `settings.revival.encounters_may_disable_self_revival: true`; otherwise validation rejects the prohibited override. False or omission imposes no encounter restriction and cannot enable a globally disabled method. This is the only encounter-level revival override in this block; encounters cannot redefine the master switch, window, helping method, or global return values.

If the effective master/method policy leaves no ordinary revival method available, an in-attempt death goes directly to passed-out viewing after its one actual death and grave bookkeeping. Queue `died` before `passed_out` under Q172; do not wait through an unusable window or create an extra death. A still-possible method behind a temporary delay is different. Outside an attempt, the already accepted immediate normal Respawn choice applies when revival is disabled. Party-defeat arbitration and administrative recovery retain their separate contracts.

### Spectating and recovery fields

`spectating.mode` accepts `teammates` or `free`, with the latter selecting the already accepted unrestricted native spectator mode after passing out. It never changes the constrained camera during a valid grave opportunity. `share_private_info` is a global permission for supported watched-player Conclave presentation; it defaults to false. Q271 selects the controls and disclosure details independently of these field names.

`recovery.health` uses the same positive percentage format and defaults to full effective maximum health. `recovery.hunger` is an integer native food level from 0 through 20, defaulting to 20. This changes food level only; saturation and exhaustion retain their native values subject to native consistency constraints. Do not silently add a separate saturation refill or expose competing inventory/XP retention settings. The in-game form labels the unit and shows the normal food-bar equivalent.

`recovery.search_radius` is a finite nonnegative distance in blocks, defaulting to three; zero tries the exact destination only. It is independent of helper reach. Arena destinations and their ordered fallbacks remain selected by the encounter and must satisfy arena containment. Global settings do not supply another arena's anchor as a fallback.

`recovery.outside_attempt_fallbacks` is an ordered list of explicit world-location references:

```yaml
outside_attempt_fallbacks:
  - location: {id: raid_tools:hub, scope: world}
```

For unsafe outside-attempt grave revival, try the accepted usable death/recent-standing positions first, then this authored list, then the normal respawn fallback. Validate each destination and bounded nearby search before use. If none is usable, preserve the recovery obligation and show the existing GM diagnostic. Capture the resolved list with that death. The list does not change voluntary normal Respawn, affect attempt-end regrouping, or make a world location valid in an arena binding.

Operators edit these fields in the existing Server settings form or YAML editor. GMs do not gain global-policy permissions. Explain units and show the effective settings before publication. Preserve existing attempt/death snapshots, and make defaults part of the resolved policy so a later software/default change cannot rewrite them. Reconnect grace remains the accepted encounter setting; GM membership, storage/transfer budgets, history retention, and vanilla gamerules remain outside this gameplay manifest.

## Q271: explicit grave controls and teammate selection after passing out

Accepted: keep the grave view fixed to the player's grave, then provide a first-person teammate picker after passing out in `teammates` mode. Also preserve Q67's observation route for returning observers who may be alive and have no grave. Offer only living, online, actively admitted members of that same attempt as normal watch targets. Keep free spectator mode and private-info sharing independent global choices.

### Grave opportunity

Allow ordinary mouse/controller look around a bounded third-person camera anchored to the grave. Clip the camera against terrain so it cannot pass through solid blocks. Do not provide translation, free flight, teammate switching, or another player's viewpoint during the opportunity. Choose the precise orbit bounds through native integration and usability verification, not a new author-configurable camera system.

The HUD shows the player's own death status, remaining combat window or its paused noncombat state, and any eligibility delay. Show a self-revive action only when the effective method is enabled, with its current availability explained. An eligible helper receives the normal grave interaction prompt and progress when a nonzero help time applies. The server validates grave identity and eligibility again at commitment. Outside attempts, retain the distinct normal Respawn choice.

### Teammate mode

Show the watched player's name and a compact list of eligible teammates, with Previous, Next, and a rebindable Open spectator controls action. Direct selection and cycling use the same server-validated set. Keep a valid current target rather than jumping whenever another participant becomes eligible. If it dies, disconnects, loses active admission, or becomes unavailable, move to the next eligible target in the captured roster's stable order. Never select a dead watcher and create a camera chain.

A returned living player with `participation: observer` can explicitly enter or leave the configured viewing mode through these controls. Observation does not require another death, create a grave, or restore active admission. When they leave, restore their own camera and any game mode Conclave temporarily changed. Ordinary world play remains available under Q103. A dead player still follows their grave/passed-out lifecycle and cannot use Leave view as a revival or voluntary in-attempt respawn.

A teammate elsewhere or in another dimension remains eligible under Q264 when the supported camera integration can make that view available. Viewing one target does not grant new gameplay participation, move a grave/recovery destination, or reset timers. Native transport needed only for viewing must not fabricate gameplay teleports, spatial events, interactions, or authored input from the dead viewer. A camera switch is not revival.

When no eligible target is available but the attempt continues, a dead viewer keeps the accepted grave-bound waiting view and sees that no teammate can currently be watched. Reevaluate on relevant state changes and attach when an eligible view becomes available. A living observer without a grave instead returns to their own position/camera and can choose Watch again when targets are available; do not create a grave or force a waiting death state. Neither case switches automatically to unrestricted spectator. The attempt still ends normally when its survival/outcome rules require it; administrative intervention does not keep an otherwise ended attempt alive.

No new moving Conclave chunk-ticket footprint follows the viewer. Use the supported bounded player/camera availability rules from Q264. Unexpected camera failure retains a safe view and reports the problem rather than granting extra visibility. Recovering the player or ending the attempt closes the viewing controls and restores only the camera/game-mode state Conclave changed.

### Private information and free spectator mode

With private sharing off, render only Conclave information the viewer is independently permitted to receive. A teammate camera cannot reveal the watched player's private clues, role/aura displays, private markers, or dialogue merely through attachment. Ordinary visible world content remains visible from the permitted camera; this setting does not claim to erase knowledge inferred from a teammate's actions.

With private sharing on, mirror the supported current Conclave presentation of the selected eligible teammate, including permitted ongoing HUD/clues/markers and new private cues while that view is active. Label that perspective. Replace corresponding personal mechanic panels with the watched perspective while keeping the viewer's own death/recovery status visible. Independently addressed viewer messages retain their normal delivery; deduplicate a cue the viewer receives through both routes. Do not expose another attempt, admin-only information, inventory inspection, private chat, or an unrelated mod's private UI through this switch.

Clear mirrored state immediately on a target change or lost viewing permission, then synchronize only the newly authorized current state. Do not replay expired clues, historical dialogue or sounds, or old gameplay events. The server controls the presentation permission and limits transmitted data; hiding already-sent secret values in the client UI is insufficient. Captured global policy governs the lifecycle, while target eligibility is checked continuously.

In `free` mode, retain the user's explicitly requested unrestricted native spectator movement and ordinary camera controls after passing out or when an eligible returned observer chooses to watch under Q67. The teammate-only picker is not a world-movement restriction in that mode. Native camera attachment to an outsider still grants no Conclave private data; private mirroring requires the same eligible teammate and global permission as above. Neither camera mode changes the retained roster or `participation` state.

## Related contracts

These contracts finish the fields and controls left open by [global settings](manifest-references.md#q82-global-gameplay-settings), [death and revival](death-and-revival.md), [Location anchors](location-anchors.md), [world recovery locations](world-locations-and-assets.md), [participant lifecycle events](role-and-player-events.md), [captured travel policy](movement-and-simulation.md), and [private clues](pattern-presentation-and-clues.md). Q270 chooses configuration and its disabled-method edge case; Q271 chooses the camera controls and disclosure behavior for the already accepted modes.

[Q278](revival-protection.md) accepts the gameplay boundary for positive `damage_protection`, preserving its accepted field, default and publication timing. [Q279](aura-and-world-visuals.md) accepts aura sharing/world visuals while retaining the accepted spectator-private-information controls.
