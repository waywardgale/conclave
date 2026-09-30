# Presentation and dialogue

Status: Q128-Q140 are accepted. [Dialogue formatting and delivery](dialogue-formatting-and-delivery.md) defines reusable styles, shared audiences with nested text/sound overrides, dialogue scheduling, and translations. [Presentation controls and model configuration](presentation-controls-and-models.md) defines resource-readiness, playback-control, and model authoring contracts. No presentation implementation or integration test exists.

## Q129: named dialogue with recorded speech

Accepted: add a reusable named `dialogue` definition with readable `text` and an optional `sound` asset for its recorded voice. Use a `speak` action to reference it and identify the speaker and audience. A voiced line displays the matching text as subtitles; a text-only line still works without a recording. The text is authored content, not a command or an instruction to generate speech at runtime.

The user additionally requires reusable formatting templates, including preexisting shout and whisper styles and author-created styles. They are available through the same YAML and in-game authoring workflow. Q133 accepts the `text_style` definition kind, `style` reference, typed formatting properties, and separation from audio delivery.

```yaml
schema: 1
namespace: raid_tools
dialogue:
  id: defiant_warning
  text: "You will go no further."
  sound: raid_tools:voice/defiant_warning
```

The speaker belongs to the action, so different NPCs may use the same dialogue. A `speaker: {group: boss}` reference must resolve to exactly one available NPC in that captured group activation; an ambiguous group is an error rather than an accidental chorus. A typed event reference can identify a single NPC when the event supplies one. A defeat event may expose its defeated NPC's captured name and last position for a final line, even after the live entity becomes unavailable. This presentation snapshot cannot target it for gameplay, revive it, or retarget a replacement. Announcements without an NPC speaker remain a distinct presentation use rather than a fake world entity.

```yaml
do:
  - speak:
      dialogue: raid_tools:defiant_warning
      speaker: {group: boss}
      audience: {from: participants}
```

Allow either one text/sound pair or a nonempty `variants` list of such pairs, never both. Variants provide repeated taunts without duplicated rules. Give each variant a stable ID; optional positive weights choose relative frequency. The server chooses once per invocation and sends the same choice to that invocation's audience. Avoid immediately repeating the same variant for the same speaker and dialogue during an attempt when another variant is available. A single available variant remains usable. Choosing a variant never rerolls a gameplay condition or changes encounter outcome.

Every variant needs readable text even when it has voice audio. Respect supported client volume and subtitle controls; muting sound does not change gameplay. Q136 defines the accepted locale-specific text and voice fields and their fallbacks. Recorded voice files and text are published and pinned with their definitions. Import and preview occur through the existing Minecraft editor.

Q135 accepts overlapping-line, priority, queue, timing, and interruption rules, and Q136 accepts translated text and voice fields. Do not introduce unlimited dialogue queues, a mandatory global narrator, automatic periodic taunts, or live text-to-speech as part of this definition. Authors activate dialogue through explicit rules or supported mechanic configuration.

## Q130: presentation attached to gameplay events

Accepted: expose typed `play_sound`, `play_animation`, and `show_effect` actions alongside `speak`. Authors place them in the existing ordered `do` list for a mechanic's declared event, such as a capability's warning, impact, or completion event. A capability must actually expose the selected event; the engine does not invent universal cast stages for every mechanic. This keeps ability presentation in the same rule and ownership system as gameplay.

Each action has a typed resource reference, an appropriate target or origin, and an explicit audience where recipients can vary. Validate that the installed capability supports the chosen sound, model animation, or visual effect. `play_animation` addresses a supported model's named animation and a specific NPC or group; an animation clip cannot stand in for a gameplay attack. Detailed fields and playback handles follow the selected presentation capabilities.

[Q190](carried-relics-and-animation.md#q190-explicit-animation-targets-and-logical-playback) accepts the concrete animation-action fields and an additional runtime relic target.

Audio, subtitles, particles, and cosmetic animation report or accompany server-owned gameplay. A damage action, area check, deadline, or phase transition never waits for a client's audio device or animation-completion message to determine whether it happened. Authors use the accepted simulation timers and sequences to schedule the gameplay and its cues. There is no claim of frame-perfect synchronization across different clients or paused client audio.

For example, an authored warning event may play a sound and start an animation while an explicit server timer determines the later damage. Missing or muted audio never removes the damage. A harmless client presentation failure may use Q69's documented cosmetic-warning behavior, while a required model capability or asset dependency must already pass publication and readiness checks. Do not use missing assets as an implicit optional fallback.

An ordinary cue is a one-shot by default. Explicit looping presentation retains an owning scope and a handle so cleanup can stop it; queued future cues from an ended activation are discarded. An already issued finite sound or speech line may finish without retaining gameplay callbacks, including a boss's final line. Keep its resources until playback finishes. The next dialogue-control branch must bound this behavior and define explicit interruption so old speech cannot accumulate across retries.

The word ability describes gameplay built from supported mechanics and actions here; this decision does not introduce a separate `ability` programming language or a second event system. An author can package repeated behavior through the existing reusable mechanic definitions.

## Q131: audience and sound origin

Accepted: require an explicit `audience` using the existing player selector on transient speech, sound, and private visual cues. `audience: {from: participants}` selects connected members of the attempt's captured roster, including dead members entitled to team-wide information. This is a presentation query and does not inherit the implicit living-only default of ordinary gameplay actions. Authors can narrow it with the accepted role, area, aura, and player-state filters.

The user explicitly requires global delivery, particular filtered players, a radius around the dialogue's owner or speaker, and an area. Keep the accepted YAML term `area` for a zone. Apply these targeting capabilities to both text and sound, and permit combined filters. Global recipients use the existing online-player collection; no new audience membership permission is implied. Q134 accepts `radius` and a shared `audience`, with `text.audience` and `sound.audience` replacing that default for their respective media.

Keep sound origin separate from permission to receive it. Offer `mode: positional` for sound emitted from the selected NPC or location, and `mode: direct` for a cue delivered individually without distance attenuation. Speech with an NPC speaker defaults to positional mode; an author can choose direct delivery for a team-wide warning. Positional delivery also requires a recipient in the origin's dimension and within its configured audible range. Live NPC speech follows that NPC; a valid defeated-speaker snapshot uses its captured position. A direct team cue can reach selected participants elsewhere, consistent with the accepted roster model.

For positional speech with only a shared audience, use the same eligible recipients for text and audio. Q134 permits explicit nested audiences when the author deliberately wants different text and sound delivery. Selecting `from: online_players` can intentionally make a nearby sound public, including to outsiders and GMs. Choosing a private role limits transmission to that role's selected players; ordinary broadcast sound APIs must not leak the cue to everyone nearby. Existing vanilla world sounds keep their own behavior.

Send speech and private visual messages only to permitted recipients. A spectator does not gain another player's private line merely by watching their camera; use the accepted global private-information policy and recipient rights. Public NPC appearance stays ordinary visible world presentation. An `audience` filter for a private cue is not permission to hide physical players, NPC collisions, or the existence of a public entity from outsiders.

Recipient selection is captured once per one-shot invocation. Continuous private presentation must stop delivery when its recipient loses the relevant permission or supported viewing right. Reconnect must not replay an obsolete taunt. Exact resynchronization of current looping effects and voice interruption remains part of the client presentation protocol.

## Q132: custom models and animation adapters

Accepted: support resource-defined static and animated appearances for compatible NPCs and other declared visual consumers through registered model adapters. Authors choose a model by logical ID and configure supported animation bindings in YAML. Installing a supported adapter is a mod capability decision; publishing another model, texture, or animation in that adapter's format is content work.

Keep the server entity type, AI, health, collision, and damage rules explicit in gameplay configuration. Changing the model or its visual scale does not silently change the server hitbox, reach, or attack timing. A larger boss model can use separately supported physical settings when the author intends different gameplay dimensions. Visual animation cannot become an unrestricted executable script.

The framework supplies a documented model path for the requested custom boss appearances. Primary-source research found an official Fabric 26.2 GeckoLib release and its MIT license. The user accepted one Conclave adapter using GeckoLib's geometry and animation formats, with model, texture, and clip IDs selected from the attempt's captured content. Do not register a new native entity type or write a new renderer for each authored boss appearance. A static model or vanilla texture override does not satisfy the full animated-model requirement by itself.

Validate geometry, textures, animation references, and the selected consumer adapter before publication. Keep referenced model graphs versioned with the attempt just like sounds and textures. In-game preview should show the supported model, visual scale, available clips, and the separately configured physical dimensions. Unsupported formats must identify the missing adapter rather than silently rendering a different boss.

The exact dependency pin, animation-controller mappings, supported entity consumers, and safe reload/version retention require an implementation proof with the chosen release. The official library's existence does not establish that Conclave can already hot-publish arbitrary models or retain two versions concurrently. See [presentation asset research](presentation-assets-research.md) for verified facilities and remaining integration work. This decision settles the rendering and gameplay boundary without claiming an implemented custom renderer or universal support for arbitrary model files.

[Q279](aura-and-world-visuals.md) accepts the initial particle/ring profiles, exact origin-versus-follow behavior and aura attachment. It preserves the accepted audience, captured assets and gameplay/presentation separation.
