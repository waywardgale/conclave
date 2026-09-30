# Individual NPC controls and hit reactions

Status: Q227-Q228 are accepted. Q227 extends existing controls to one identified NPC. Q228 independently adds notifications for supported blocking and Conclave damage protection. No implementation exists.

## Q227: use the same NPC controls on one typed target

Accepted: give `set_ai`, `set_vulnerable`, `set_health_floor`, `clear_health_floor`, `set_stats`, and `reset_stats` exactly one recipient form: the existing `group`, or `target` identifying one supported living NPC through a typed event reference. Keep their existing argument names, units, and effects.

```yaml
id: disable_new_vex
on:
  source:
    group: summoners
  event: descendant_spawned
if:
  event_value:
    field: type
    equals: minecraft:vex
do:
  - set_ai:
      target: {event: target}
      value: disabled
```

This illustrative rule changes the new vex, even though native descendants do not join their ancestor's group. It does not change the evoker, siblings, or future descendants. Authors can also use typed NPC targets from supported damage, conversion, creation, and explicitly exported events.

Reject missing or simultaneous recipient forms. A field's declared type must permit a supported NPC identity; a known player-only, location, item, or scalar field is incompatible. When a supported entity field can represent several native kinds, validate the actual target before applying the action. An optional identity is still optional; selecting it does not strengthen the event schema. Missing, dead, ended, replaced, or otherwise unavailable required targets retain the existing validation and Q69 required/recoverable error policy. Do not silently reinterpret them as an empty group.

### Identity and authority

Resolve an individual target once to the exact body identified by the event. Conversion does not retarget an old identity. The `converted` event can provide the new body's target, and a later group selection can resolve the continuing member. A `defeated` event's ended target cannot receive a living-NPC control or act as a resurrection request.

Require an NPC owned by the current attempt and a permitted typed reference. Ordinary combat attribution does not grant control over an unrelated world mob or another attempt's NPC. An explicit export may expose an allowed individual identity without exposing the private group handle or sibling identities. Existing lexical references, ownership, and export boundaries remain in force. These controls add no UUID query, proximity adoption, general world selector, or player-control variant.

Validate support and requested values before mutation. Group actions retain their once-captured living selection and full preflight; an existing empty group remains a no-op. Individual actions validate their one recipient. Neither form silently skips an incompatible NPC. Unexpected failures retain the existing technical-error policy rather than promising rollback of arbitrary native or mod callbacks.

### Persistence and reset

Preserve each action's accepted behavior. AI changes clear autonomous paths and targets as already specified; repeated equal modes do not restart navigation. Vulnerability changes do not change health or native immunity. A health floor preserves its one-notification-per-setting identity. Stat setters replace supported base values, and changing maximum health does not refill health. Clearing a missing floor remains harmless.

The settings persist with that logical NPC until changed or its owning lifetime ends, including the accepted supported conversion transfer. Ending the reacting rule's scope does not undo a base control. Future group reinforcements and newly born descendants retain their own initialization rules, without inheriting this action implicitly.

`reset_stats` uses that NPC's original initialization baseline, never its parent's or a newly converted native type's default. Capture a baseline for every owned NPC after native creation and explicit initial configuration, including an unconfigured native child. Resetting selected fields restores those captured supported base values; it does not undo independent aura modifiers, native effects, or equipment changes, and does not heal. Preserve that baseline across a continuing NPC's supported conversion under Q208.

All actions use the attempt's captured revision. Publishing an edited manifest still affects future attempts only. The editor offers the same control arguments for a group and an eligible event target, and explains unavailable or incompatible selections using the existing validation contract.

## Q228: actual blocks and Conclave damage prevention

Accepted: add `damage_blocked` for a positive native item-blocking result, and `damage_prevented` for damage actually rejected or capped by Conclave's NPC vulnerability lock or health floor. Preserve the existing `damaged` event's positive health-or-absorption-loss meaning. These are notifications after resolution, not cancellation hooks.

### Blocking

Expose `damage_blocked` on selected players, named NPC groups, and Q226's explicit descendant source where the target adapter supports native item blocking. Emit once when a supported blocking item actually removes a positive amount from the incoming request, including a partial block. Holding a shield, swinging at nothing, a nominal zero-damage request, or merely observing no health loss does not qualify.

Provide required typed `target`, registered damage `type`, and positive `amount`, plus optional actual `attacker` and `direct_source` identities. `amount` is the native quantity removed by item blocking before armor, effects, absorption, and the later native hurt-cooldown decision. It is not lost health or an estimate of health saved. A positive block can notify even when the remaining hit is subsequently rejected by cooldown. A partial block may also produce `damaged` when the same operation consumes health or absorption.

For a player source, the affected required player `target` is the default triggering player for invocation limits. Do not invent a second payload alias named `player`. Group and descendant sources have no default triggering player. Q223's explicit `on.player` can instead choose a supported actual player identity field, with its existing matching and export rules.

### Conclave protection

Expose `damage_prevented` on named NPC groups and the explicit descendant source. Provide required `target`, damage `type`, and registered `reason`, plus optional actual `attacker` and `direct_source`. Initial reasons are `vulnerability` and `health_floor`:

- `vulnerability` means Conclave's own damage lock actually rejected a positive supported gameplay damage request. A lock being enabled is not proof that it rejected a hit; an earlier native check or external veto may have ended the operation first.
- `health_floor` means Conclave's own floor actually reduced positive health-directed damage after native mitigation and absorption. It can accompany positive absorption or health consumption and therefore an existing `damaged` event. Emit for each actual cap, independently of the existing once-per-setting `health_floor_reached` notification.

Do not supply a general `amount` or claimed health-saved value on `damage_prevented`. A vulnerability rejection never resolves later defenses, and floor input can include overkill. The event reports the verified intervention; `damaged` reports actual consumption. Authors can combine the reason with existing typed guards and cooldowns.

```yaml
on:
  source:
    group: boss
  event: damage_prevented
if:
  event_value:
    field: reason
    equals: vulnerability
```

This illustrative fragment can select reactions such as an immunity cue. A complete rule still needs its ID and actions. The cue does not retroactively change the rejected operation.

### Observation and ordering

Match source eligibility against coherent pre-operation state, including Q226's lineage/type filters and the accepted player-selection rules. Capture attribution from the actual operation, without remembered attacker, pet-owner, or nearby-player substitution. Keep body identities historical and mutation authority separate. A source not supporting an event fails validation rather than silently producing nothing.

Queue after the entire observed operation resolves. Within that operation, notify actual blocking before any `damaged`, then any floor prevention before the corresponding threshold and terminal notifications. A vulnerability rejection normally supplies only its prevention notification. Preserve native causal nesting and other operations' established order; never run these reactions inside the damage callback to revise a half-finished hit. Suppress duplicates across native and Conclave instrumentation.

Administrative bypass, cleanup, direct health assignments, and initial configuration do not manufacture these events. Native immunity, hurt cooldown, ordinary armor/effect mitigation, absorption consumption, external vetoes, totem rescue, and misses are outside the initial `damage_prevented` reasons. Their absence from this event does not change their gameplay. In particular, a totem does not erase a real `damaged` event.

No generic attack-attempt, universal rejection-reason, or other-mod cancellation API is included in this initial addition. Validate supported native paths and Conclave's own branches explicitly. Fabric's existing callbacks alone do not supply this contract; [native hit research](npc-combat-research.md#native-item-blocking-and-damage-rejections) records the observed boundaries. Registered event schemas, queued dispatch, normal invocation limits, and execution budgets apply as for other combat events. All adapters remain implementation requirements.

## Related contracts

These contracts extend [AI controls](npc-and-boundary-policies.md#q99-changing-ai-during-an-attempt), [vulnerability](combat-rules-and-health.md#q149-explicit-npc-vulnerability), [floors and stat controls](combat-events-and-controls.md), [conversion state](npc-conversion-state.md), [descendant observations](npc-death-rewards-and-descendant-events.md#q226-explicit-subscriptions-to-native-descendants), and [typed event conditions](event-conditions.md).
