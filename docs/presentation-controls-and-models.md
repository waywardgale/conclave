# Presentation controls and model configuration

Status: Q137-Q140 are accepted. They follow the accepted dialogue, nested audiences, resource-pack publication, playback lifetime, and GeckoLib adapter decisions. The examples describe framework authoring and are not bundled encounters or working implementations.

## Q137: selected recipients without the required assets

Accepted: treat ordinary presentation as best-effort cosmetic output. If a selected outside recipient does not have compatible applied resources, skip only the unavailable output and report a bounded diagnostic to authorized administrators. Text can still appear if that player belongs to its resolved audience and its own required resources are available. Do not substitute a different sound, widen another medium's audience as a fallback, force a resource reload during an active attempt, or replay an obsolete cue after a later download.

The selected audience remains the authored audience. Delivery status separately records who could receive each component. This matters for global speech after an asset hotfix: another player's active attempt can still pin an older resource set, even though the new attempt's own participants passed readiness checks. Global selection is not an exception to Q124's next-attempt and resource activation rules.

Allow an explicit `required: true` on a presentation action for content whose delivery is part of the authored gameplay contract. Preflight known required resources for the attempt's selected participants before start. At invocation, require the currently selected recipients to have compatible applied resources; an unsatisfied required delivery follows Q69's technical-error policy. It never waits indefinitely for a pack download, silently shrinks the selected set, or converts an error into a gameplay wipe punishment.

[Q241-Q242](client-resource-failures-and-observers.md) accept proactive handling of required-resource loss and observer asset application. They preserve this required-dispatch policy, including online observers selected by the authored audience.

Required delivery means that the server can dispatch the supported presentation to a compatible connected client. It cannot require a human to hear audio or disable their volume, subtitle, or accessibility controls. A client's playback-completion message never decides a gameplay outcome. Late client presentation failures use the capability's documented diagnostic/error path and cannot roll back gameplay actions already committed.

Apart from the accepted [Q163 native NPC appearance fallback](npc-model-presentation.md#q163-visible-native-fallback-when-a-custom-model-is-unavailable), fallbacks must be explicitly authored and supported. Translated-text fallback and native NPC appearance fallback are declared contracts, not permission to substitute unrelated assets. A stale cached resource with the same logical ID is not a compatible substitute for a different pinned revision. Empty valid audiences retain their normal no-op meaning unless the author explicitly requires a count through an existing condition.

## Q138: named playback and explicit stopping

Accepted: allow presentation actions to declare an optional `id` for their playback. Require an ID for looping or otherwise explicitly stoppable output. Use one typed `stop_presentation` action to stop an addressed sound, dialogue, animation, or effect instance without guessing its kind from an asset filename.

```yaml
do:
  - play_sound:
      id: ritual_music
      sound: raid_tools:music/ritual
      loop: true
      audience: {from: participants}
  - stop_presentation:
      id: ritual_music
```

This fragment illustrates the two action forms; placed consecutively, they would start and immediately stop playback. Ordinary authored rules place the stop at the desired later event. The sound's delivery mode and origin must follow its supported action schema. An unnamed one-shot receives an internal identity for cleanup and diagnostics without requiring the author to name every effect.

Playback IDs belong to the owning phase or encounter scope under Q81. Creating a new playback under an already active ID explicitly replaces that named playback, including its old recipients and queued components. Each replacement has a new internal activation identity. A previously captured reference or delayed cancellation remains bound to the old activation and cannot stop its replacement. An explicit stop that resolves the logical name when it executes targets the current playback in that named scope.

Allow the usual explicit encounter-scoped reference when a phase stops encounter-owned music. Do not allow player-owned presentation lifetimes merely because auras support `scope: player`. An undefined name is a validation error; a declared playback that has not started or has already ended is a harmless stop no-op. Explicit stopping removes pending dialogue as well as any active components of that playback.

Stopping a dialogue stops its text and voice together for the affected recipients, including media with different nested audiences. Stopping an animation relinquishes its authored override so the model can return to its current automatic state. Normal cleanup stops owned loops and pending work under the existing rules, while permitted finite one-shots can finish. A stop never cancels another attempt's playback, another source's native sound, or all instances of a resource-pack sound just because their asset IDs match.

The exact network handle and cancellation protocol are internal. Authored names identify playback rather than media: two independent uses of the same sound can coexist when they have different IDs or are unnamed one-shots. Scope cleanup, restart, and accepted interruption rules keep those lifetimes bounded.

## Q139: reusable model definitions

Accepted: add a named `model` definition that groups a supported geometry asset, texture, and optional animation resource. A `type` selects the registered model adapter, initially `geckolib` for custom animated NPC appearances. Keep stable logical resource references so authors do not write revision hashes or client cache paths.

```yaml
schema: 1
namespace: raid_tools
model:
  id: vault_guardian
  type: geckolib
  geometry: raid_tools:vault_guardian
  texture: raid_tools:entity/vault_guardian
  animations: raid_tools:vault_guardian
```

These are typed logical asset IDs. Geometry and animation references can share an ID because their resource kinds differ; the importer and adapter resolve the supported exported file paths. A static appearance may omit the animation resource if the adapter supports that form. The in-game picker shows only compatible validated resources.

An NPC selects the reusable definition through an `appearance` section:

```yaml
appearance:
  model: raid_tools:vault_guardian
  scale: 1
```

`scale` is a positive finite visual multiplier, defaulting to one, subject to the renderer's supported limits. The base entity type still supplies its separately configured server gameplay. Several NPC definitions can share a model while differing in health, AI, equipment, or other supported gameplay properties. A visual scale change does not silently resize collision, reach, or safe-spawn checks.

Validate the whole geometry, texture, animation, and font/material dependency graph that the chosen adapter supports. Report unsupported consumers and missing clips before publication where statically knowable. The authoring preview shows the appearance alongside the entity's actual physical dimensions. An unsupported model consumer requires an installed adapter; a new valid model in an existing format does not require another renderer or native entity registration.

All references resolve within the captured revision. A newer definition under the same model ID affects future consumers under the accepted publication policy. Exact supported model consumers beyond the initial NPC adapter remain part of the v1 capability catalog; this schema does not promise that every entity, block, item, and particle shares one renderer.

## Q140: automatic and authored animation

Accepted: let a model optionally map supported entity states to animation clips, beginning with idle and movement. Adapters may expose additional verified states such as hurt, attack, and death. These mappings react to the entity's actual supported state; an arbitrary entity type does not acquire a new AI signal just because a YAML key names it.

```yaml
states:
  idle: animation.guardian.idle
  move: animation.guardian.walk
```

The fragment belongs to the accepted model definition. Clip names reference the imported animation resource's exact declared names. Missing clips or unsupported state bindings fail validation. A model without automatic mappings can remain in its rest pose until an explicit animation action runs.

Authors use `play_animation` through ordinary rules for a cast, taunt, attack cue, or other explicit visual. Select a supported NPC or group, a clip, and the intended playback behavior. A one-shot is the default; a loop must be named and owned so it can be stopped. Keep one explicit full-body animation override per NPC in the initial adapter. A newer authored override replaces the previous one. Multiple independent bone layers and unrestricted animation-controller scripts are outside this initial contract.

While an explicit override runs, it takes precedence over the automatic idle/movement state. When it finishes, is stopped, or loses its owning scope, return to the entity's current supported automatic state rather than a pose captured before the animation began. A mandatory adapter death/removal state terminates incompatible ordinary overrides. An ended clip cannot retain a callback that acts on a replaced or cleaned-up NPC.

Animation playback changes presentation only. It does not move the server entity through root motion, change vulnerability or hitboxes, apply damage at an asset marker, or delay phase transitions. Authors coordinate those gameplay operations with declared actions, events, and simulation timers. An imported animation's incidental sound/effect markers must not bypass Conclave's explicit audience and owned playback actions; the supported import profile must handle or reject them explicitly.

The public schema supplies named states and clips rather than executable YAML expressions. The adapter's supported data format and bounded visual calculations are validated through the selected library integration. Exact clip-rate, transition, and stop-action options follow the capability schema, with the accepted required-operation and cosmetic-diagnostic policies kept explicit.

[Q162-Q163](npc-model-presentation.md) accept explicit equipment attachments and a native-renderer fallback for viewers without a usable captured model. This fallback preserves the same NPC and never satisfies required asset readiness or substitutes a newer custom-model revision.

[Q188](relic-placement-and-appearance.md#q188-item-or-custom-model-appearance-with-readable-holder-feedback) accepts a managed relic model consumer and authored item fallback. [Q189-Q190](carried-relics-and-animation.md) accept a carried consumer and concrete animation-action fields with logical relic playback. These additional adapters still require implementation and verification.

[Q229-Q230](model-lighting-bounds-and-item-appearance.md) accept model lighting, emissive regions, and explicit visual bounds.
