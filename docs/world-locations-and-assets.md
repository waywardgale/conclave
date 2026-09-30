# World locations and client assets

Status: Q123-Q124 and Q127-Q140 are accepted. The user explicitly expanded Q128 to custom models, sounds, dialogue lines, boss taunts, and ability sounds as part of a general asset contract. The presentation documents define actions, audiences, styles, scheduling, translations, playback controls, and model mappings. Primary-source research supports the client facilities discussed below; the Conclave transfer, staging, and publication integration has not been implemented or tested live. Exact dependency pins, supported consumers, and detailed client integration still require verification.

## Q123: Location anchors outside an arena

Accepted: allow world-owned named locations and optional Location anchor blocks outside any arena. They provide recovery destinations for the server-wide revival module, including a safe fallback when an outside-attempt grave position cannot be used. Use the same in-game editor, labels, YAML draft, validation, and publication workflow as arena anchors.

Store an explicit dimension, coordinates, facing, and normal display identity. Give world locations namespaced identities so two authored content sets can both define a location called `hub`. Global recovery settings can explicitly reference them. Merely placing or loading a Location anchor does not change everyone's spawn, start a mechanic, or spawn entities. Ordinary respawn continues to use its existing policy unless an applicable setting explicitly selects another supported destination.

Keep world-owned locations distinct from arena-local spatial IDs. An arena's `entrance` remains its own binding, with its existing containment and reference checks. Do not silently resolve a missing arena-local name to a similarly named world location. Q127 defines the accepted global definition wrapper and explicit reference form.

An outside-attempt death captures the selected recovery definition and resolved destination with its applicable policy. Moving the anchor or publishing new YAML cannot move an existing grave or change that unresolved death's fallback. Revalidate physical safety when using the destination and follow the existing nearby search, explicit fallbacks, and safe waiting policy. Retain old definitions while an unresolved recovery still needs them.

This extends where a location can be owned; it does not add automatic teleportation at an arena boundary or a second copy of recovery rules. Existing area-authoring capabilities remain available in their established arena context. Global area ownership and general ambient world rules are separate decisions if needed; a world recovery location does not silently introduce those systems.

## Q124: client assets through the Minecraft workflow

Accepted: use Minecraft resource packs for authored assets. The Conclave mod supplies the encounter engine, supported client behavior, authoring interface, and pack delivery integration. YAML configures that installed engine; resource packs supply textures, icons, sounds, fonts, and supported model data that its client can display. These parts serve different purposes and are used together.

A resource pack can change an aura's icon or a supported NPC's appearance. It cannot implement aura stacking, a new revival camera, a new custom screen, or a new Kotlin mechanic. Conversely, a mod can bundle default assets, but requiring authors to put every custom asset into a rebuilt mod would make ordinary art changes require mod redistribution and a relaunch. Prefer resource packs for authored art and sound, with mod updates reserved for new code capabilities. Changing an existing mechanic's supported YAML parameters needs neither new code nor a new resource pack.

Pack format and delivery are separate choices. Minecraft's ordinary server-pack URL workflow still uses a resource pack. Conclave's accepted transfer over the existing Minecraft connection also ends in a resource pack, with custom transport and staging to satisfy the accepted in-game workflow. This does not replace Minecraft's asset system with a bespoke renderer or make resource packs capable of executing mod code. Default Conclave assets may ship with the mod; authored assets remain publishable independently.

Accepted: support importing custom asset files or a supported pack through the in-game authoring interface, with validation and publication alongside their referencing YAML. Existing Minecraft assets remain usable without an extra authored pack. Limit asset kinds to what the installed Conclave client and registered integrations can interpret, such as HUD icons, sounds, fonts, and supported model data. A new Kotlin behavior, renderer, or native registry entry still requires a mod update.

Transfer the asset content over the authenticated Minecraft connection using bounded custom payloads, as required by Q75. Do not require an HTTP server, an extra listening port, SSH, an external upload step, or a CLI. Verify and stage the immutable content on each receiving client, and report transfer and application progress through Minecraft. A finished upload or download is not proof that the client has successfully applied the resources.

Respect the player's resource-pack consent and existing client permission state. If assets are declined, invalid, or fail to apply, explain that the affected Conclave content is unavailable on that client. Do not silently substitute unrelated visuals, override the choice, kick the player solely for this Conclave operation, or change ordinary world interaction. A new attempt requiring those assets can start only when its selected clients report the compatible applied content, under the accepted engine readiness checks. [Q163](npc-model-presentation.md#q163-visible-native-fallback-when-a-custom-model-is-unavailable) explicitly permits the same NPC's native appearance for a viewer lacking its captured custom model; this fallback never satisfies the readiness requirement or forces resource activation.

Use immutable version-specific asset identifiers and keep old resources while active attempts, persistent aura applications, previews, or unresolved recovery still need them. A pack UUID alone is not enough because two packs may replace the same texture or sound path. Conclave's presentation must resolve the asset IDs captured by the applicable definition; do not replace a fixed vanilla texture path and claim that old and new attempts are visually independent.

Stage updates during ordinary play, but defer resource-pack activation for a player currently participating in an active attempt until that attempt has ended. Resource activation can reload the client's resources globally and may briefly interrupt presentation. This preserves current-attempt presentation while new attempts wait for the appropriate assets. Publishing YAML remains a future-attempt change, and clients already running an attempt do not receive changed gameplay definitions. Existing player-owned auras continue to reference their captured assets when a newer pack is eventually applied.

Retain only validated supported asset data, with bounded transfer size, file counts, expansion, and cache ownership. Unreferenced old packs can be removed once their consumers no longer need them. [Q232](durable-item-appearances.md#q232-retain-an-appearance-archive-once-real-items-can-use-it) adds a conservatively retained archive for real item appearances; ending an attempt or seeing no loaded items does not release that server archive. Exact limits, download interruption handling, pack assembly, client permission UX, and cache/reload integration remain implementation design work. This is an asset-delivery capability; it does not install arbitrary client code from a YAML manifest.

### Verified 26.2 facilities and limits

[Fabric's networking guide](https://docs.fabricmc.net/develop/networking) documents custom-payload transport. Inspection of [Mojang's official 26.2 client binary](https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar) confirmed a local-pack insertion and removal path, pack feedback, and client resource reload facilities. Local insertion uses the resource-pack permission state, so a declined pack permission cannot be treated as successful activation. Fabric's [26.2 ResourceLoader contract](https://github.com/FabricMC/fabric-api/blob/26.2/fabric-resource-loader-v1/src/main/java/net/fabricmc/fabric/api/resource/v1/ResourceLoader.java) provides loader facilities, but its built-in-pack registration addresses assets packaged in the mod rather than arbitrary staged files. Connecting these facilities to a Conclave transfer and readiness protocol is an implementation inference, not a completed integration.

The ordinary server-pack protocol downloads from HTTP/HTTPS URLs, which does not satisfy Q75's connection-only transport by itself. Mojang documents multiple pack handling and distinct pack-status responses in its [server resource-pack protocol notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-20-3); the inspected 26.2 client retains separate accepted, downloaded, successfully loaded, declined, and failure states. A custom Conclave workflow must produce its own trustworthy application acknowledgement rather than assuming the URL protocol handles custom transfers automatically.

The [26.2 release notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2) identify resource-pack format 88.0. Fabric documents asset-backed [item models](https://docs.fabricmc.net/develop/items/item-models) and [sounds](https://docs.fabricmc.net/develop/sounds/custom), while [entity creation](https://docs.fabricmc.net/develop/entities/first-entity) and [status-effect registration](https://docs.fabricmc.net/develop/entities/effects) require programmatic registration. This supports the distinction between replacing supported data/assets and introducing new client capabilities. No live reload or transport test has been performed for Conclave.

## Q127: world-location manifests and references

Accepted: use a named `location` manifest for an arena-independent world location, with the usual `schema`, optional `namespace`, `id`, and optional `name`. Require `dimension` and a `position` containing finite `x`, `y`, and `z` coordinates. An optional `facing` contains numeric `yaw` and `pitch` in degrees, defaulting to zero when omitted. The in-game editor writes these same fields and offers Use my position.

```yaml
schema: 1
namespace: raid_tools
location:
  id: hub
  name: Hub
  dimension: minecraft:overworld
  position: {x: 100.5, y: 64, z: 200.5}
  facing: {yaw: 0, pitch: 0}
```

This location is valid without a placed block. A Location anchor is its optional in-world authoring representation under the existing placement and draft rules; its presence is not another source of truth. The manifest does not automatically install a global respawn rule or activate saved spawn configuration.

Where a capability accepts an outside-arena world destination, use an explicit reference with `scope: world` and a fully namespaced ID. This follows the existing explicit-scope reference shape while identifying spatial ownership rather than creating a new resource lifetime.

```yaml
location:
  id: raid_tools:hub
  scope: world
```

The surrounding global recovery setting remains part of the settings schema; this fragment only settles the typed destination reference. An ordinary `location: entrance` inside an encounter keeps its arena-local meaning. Never resolve it against world locations as a fallback, and do not allow `scope: world` in an action that requires an arena-contained destination. The new spelling does not bypass Q80's arena binding rules. Global references validate dimension and identity, then use the existing captured-destination and safe-placement policy.

## Q128: stable resource references in manifests

Accepted: authors reference resources by stable, namespaced logical IDs. They never write generated revision hashes, cache paths, download URLs, or pack UUIDs in encounter YAML. The publisher resolves these IDs to the immutable resources captured by the applicable definition and rewrites supported internal asset references as needed during pack assembly.

The user's scope extension applies this contract to all supported presentation asset families, not only aura icons. It includes custom models and their animation data, textures and other supported materials, sounds and music, voice recordings, UI images and fonts, localization resources, and supported particle or visual-effect data. NPCs, relics, props, auras, and authored abilities can reference the kinds their installed capabilities support. Exact formats and consumers are validated through those capabilities rather than inferred from a filename.

Dialogue lines and boss taunts are authored content that can pair readable text with a voice recording. Their text, chosen speaker, recipients, and activation belong to typed definitions and actions; the recording and other media belong to the resource pack. Abilities can attach presentation to their declared gameplay events. No new mod build is required for another asset or spoken line in an already supported format. New renderers, codecs, or gameplay capabilities still follow Q124's mod-extension boundary.

For aura icons, use exactly one typed source, `item` for an existing registered item's icon or `texture` for an image asset:

```yaml
display:
  icon:
    texture: raid_tools:auras/charged
```

For comparison, `icon: {item: minecraft:amethyst_shard}` reuses the item's icon. For the accepted `texture` field, `raid_tools:auras/charged` names `assets/raid_tools/textures/auras/charged.png` in the authored resource pack. This field addresses a logical asset; the actual client reference may be versioned internally. The asset picker previews each supported choice and writes the same YAML reference a human or agent could write.

Publication validates asset kind, presence, supported format, and dependencies before activation. Report duplicate authored IDs instead of silently choosing a pack by import order. A filename is not a code capability, an arbitrary filesystem reference, or a URL to fetch. Existing Minecraft resource identifiers and other registered kinds retain their own typed rules; a texture cannot stand in for an item, sound, or renderer.

Replacing a custom asset at the same logical ID publishes a new immutable version. Existing attempts and persistent applications keep their captured references; new definitions can use the replacement after client readiness checks. Referenced published asset content and dependencies participate in the resolved definition identity, so an authored icon change to a persistent aura follows Q122's same-ID compatibility rule. This does not mutate old contributions or treat the pack's latest file as their presentation. This versioning covers Conclave-published resources; it does not claim control over unrelated client pack changes.

In v1, validate the supported authored resource subset whose dependencies Conclave can resolve and version. Do not claim arbitrary third-party pack overrides or fixed vanilla paths provide per-attempt visual isolation. Explain unsupported pack entries in import diagnostics. A resource pack supplies data the installed client understands; a new unsupported model renderer still needs code under Q124.

Apply the same publication, dependency validation, retention, consent, and readiness rules to models, animations, sounds, voice recordings, and other supported assets. A new model that depends on an unsupported adapter fails validation with the missing capability identified. Replacing a model or sound does not mutate an active attempt, just as replacing an icon does not. Asset previews belong in the existing in-game authoring workflow, with model and animation previews, audio playback, and dialogue previews as appropriate.

[Q129-Q132](presentation-and-dialogue.md) accept dialogue definitions, ability cues, audience and sound-origin rules, and the custom model adapter direction. The user also requires reusable formatting templates and global, filtered, radius, and area targeting for text and sound. [Presentation asset research](presentation-assets-research.md) records verified 26.2 facilities and dependency compatibility. These contracts do not claim that the runtime has been implemented.

[Q234-Q236](archived-appearance-delivery.md) accept selective authorized demand delivery, an explicit Apply assets action, and bounded client-cache retention. These contracts preserve consent and forbid ordinary asset-update activation for actively admitted participants. Q242 explicitly permits ordinary Apply for observers while retaining captured encounter resources.

[Q239-Q240](reconnect-resource-restoration.md) accept restoration of the original required resources during a valid reconnect opportunity. [Q241-Q242](client-resource-failures-and-observers.md) accept the response to required-resource loss and ordinary explicit Apply for observers. These exceptions preserve the attempt's captured resources and never migrate its gameplay definition.

[Q279](aura-and-world-visuals.md) accepts the initial supported particle/ring presentation behavior and shared aura display under this existing asset contract. It adds no separate transfer or consent system.
