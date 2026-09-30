# Dialogue formatting and delivery

Status: Q133-Q136 are accepted. The user's Q134 correction nests audience overrides under `text` and `sound`, with a shared `audience` supplying defaults to all delivered parts. Built-in and author-created formatting templates, global and filtered audiences, dialogue scheduling, and translated presentation are now specified. No renderer or playback system has been implemented.

## Q133: reusable text styles

Accepted: represent a formatting template as a named `text_style` definition. A dialogue references it through `style`. Ship `conclave:normal`, `conclave:shout`, and `conclave:whisper`; omitted style uses `conclave:normal`. Shout emphasizes the text with bold formatting, while whisper uses quieter visual emphasis such as italic text. Their names do not silently change who receives a line, its sound volume, or its audible range.

```yaml
schema: 1
namespace: raid_tools
text_style:
  id: ominous
  color: "#b792ff"
  italic: true
```

```yaml
dialogue:
  id: defiant_warning
  text: "You will go no further."
  style: conclave:shout
  sound: raid_tools:voice/defiant_warning
```

The second example is a definition fragment. A complete file uses the accepted schema and namespace wrapper. A custom reference such as `style: raid_tools:ominous` follows ordinary namespace rules. `shout` alone means the current namespace's style named shout, not an implicit search of framework definitions. The editor lists built-in styles by readable names and writes the qualified reference.

Expose typed formatting fields for color, bold, italic, underline, strikethrough, a supported font asset, and bounded text scale. Omitted fields use documented neutral values. The exact rendering limits are part of the UI capability schema. Retain a readable speaker label and accessible text layout; a style does not force a full-screen UI, obscure controls, or override the player's supported accessibility preferences. It does not rewrite the authored text into uppercase or alter its translation.

Authors can copy a built-in template in the editor, give it their own ID, edit it, preview it, and publish it through the normal workflow. Keep each style self-contained in v1 instead of introducing inheritance chains, cascading stylesheet rules, or a second template expression language. A reference to a custom font must pass the accepted asset validation and version-retention checks.

Use the same formatting-template type wherever a supported Conclave text capability accepts a style. A style controls presentation only. It does not contain click commands, embedded scripts, arbitrary object access, audience queries, gameplay effects, or audio playback policy. Rich text spans and typed dynamic text values remain a separate schema branch if required; this contract covers reusable line-level formatting.

## Q134: audience filters and per-medium overrides

Accepted with the user's correction: use a shared `audience` on `speak` as the default for all delivered parts. Nest a specific audience under `text.audience` or `sound.audience` to replace that shared default for the corresponding medium. All audience mappings use the same accepted player-selector grammar. An override can intentionally narrow or broaden delivery while respecting actual viewing permissions and supported private-information rules. It is never inferred from a text style.

```yaml
do:
  - speak:
      dialogue: raid_tools:defiant_warning
      speaker: {group: boss}
      audience: {from: participants}
      text:
        audience:
          from: participants
          role: reader
      sound:
        audience:
          from: online_players
          radius: 24
```

This invocation displays the line to readers and plays its voice for online players within 24 blocks of the speaker, subject to the configured positional audio rules. Removing either nested `audience` makes that medium use the shared participants selection. In this action, `text` and `sound` contain delivery options; the referenced dialogue supplies the authored text, style, and recording. The accepted scalar text and sound references in dialogue definitions and translations remain valid.

Audience replacement is whole-selector replacement, not a field-by-field merge. A nested audience does not inherit the shared audience's radius, area, role, or condition. Merely providing a `text` or `sound` options block without an `audience` does not override the shared audience. An explicitly empty selector follows the normal presentation collection default and never means merge with the parent; `null` is invalid. Prefer explicit `from` in examples and editor output.

Every delivered part must resolve an explicit audience through either its own nested mapping or the shared mapping. The shared field may be omitted when every delivered part provides its own audience; otherwise validation reports the missing audience. A text-only line needs no sound audience. Here shared or global audience means the invocation-level default, not an automatic broadcast to every player on the server.

Use `from: online_players` for global recipients, including GMs, and `from: online_raiders` when the author deliberately excludes GMs. `from: participants` uses the current captured roster. Existing `where` predicates can select specific identities, roles, auras, and supported player states. Identity predicate details remain in the selector catalog; no command selector or arbitrary expression syntax is added.

Add an optional positive finite `radius`, measured in blocks from the current dialogue speaker. It selects players in the same dimension whose authoritative position is inside or on that sphere. Use a defeated speaker's permitted captured position when applicable. A standalone presentation action may use its explicitly declared spatial origin instead; a radius with no valid speaker or origin fails validation instead of using the issuing GM or an unrelated camera position.

Keep `area` for a named arena-bound region. Combining `area`, `radius`, other concise filters, and `where` requires all of them. Authors express alternatives through the existing condition grammar. For example, this audience contains online raiders who are both in the altar area and within 24 blocks of the speaker, with the marked aura:

```yaml
audience:
  from: online_raiders
  area: altar
  radius: 24
  where:
    has_aura: marked
```

Radius is a recipient filter. `mode: positional` still applies sound attenuation, dimension, and configured audible-range rules; `mode: direct` delivers to the explicitly selected recipients without an additional distance limit. To broadcast a sound globally, use `audience: {from: online_players}` and `mode: direct`. An omitted radius never secretly limits a direct global message to the speaker's arena or dimension. A radius filter remains meaningful with direct audio, such as a clear warning heard only by players near its speaker.

With only the shared audience, keep Q131's matching text/audio eligibility for positional speech. An explicit `text.audience` can deliberately show text beyond the sound's range or privately to a role; an explicit `sound.audience` can deliberately send the audio to another collection. The unchanged medium retains its base policy. Do not widen text recipients merely because the sound audience was widened, or route private sound through an ordinary world-wide broadcast path.

Resolve each specified recipient query once for the invocation and preserve those identities. Moving the speaker later does not recruit new recipients into an already issued one-shot line, though positional audio may continue following the speaker for recipients who received it. Future utterances evaluate fresh positions. Permission revocation, scope cancellation, and the accepted spectator information policy still apply. The receiving player cannot request another audience's text or audio by changing client-side UI settings.

Global selection identifies intended recipients; it does not bypass Q124's resource readiness or force an asset reload during another active attempt. [Q137](presentation-controls-and-models.md#q137-selected-recipients-without-the-required-assets) accepts skipping unavailable cosmetic components with diagnostics and explicit required-delivery handling. Do not equate an audience match with proof that every selected client has applied the required resources.

## Q135: overlapping dialogue, queues, and lifetime

Accepted: give dialogue `priority: flavor`, `normal`, or `urgent`, defaulting to `normal`. Each player hears at most one active Conclave dialogue voice at a time, with its associated subtitle instance. A higher-priority incoming line interrupts a lower-priority one for that recipient. Equal- or lower-priority incoming dialogue is skipped by default. This does not serialize ordinary ability sound effects, music, or unrelated vanilla sounds as dialogue.

Authors may opt into `queue: true` with a required positive `max_wait`. A queued line is discarded if it cannot start before that wait expires; it never arrives as an old warning after an indefinite backlog. Queues have an operational hard bound, and full queues drop the newly queued cosmetic line with bounded diagnostics. Capture the selected variant, audience, revision, and owner when the invocation is issued. A queued line does not reroll its taunt or move to a new audience when it starts.

Evaluate availability per recipient. A busy player can skip a taunt while another selected player receives that same invocation. When a higher-priority line interrupts, stop that recipient's old voice and subtitle together, and do not automatically resume the obsolete sentence. Permission loss or an ended owning activation cancels pending delivery. This is a cosmetic scheduling rule and never delays an action list, phase transition, or server timer.

For text-only dialogue, use a five-second default display duration with a positive authored `duration` override. For voiced dialogue, derive the default presentation duration from the imported recording. An authored duration may keep the subtitle visible longer; reject a shorter duration unless an explicit audio-clipping capability is added later. Sound definitions with several possible recordings need a validated bounded duration covering the selected playback. Dialogue presentation time and `max_wait` use real elapsed time, because they follow media playback and reading time; this does not change the accepted simulation clock for gameplay.

Already started finite speech may finish at an ordinary phase or successful attempt end under Q130. [Q245](ending-scope-presentation.md) also permits finite cues actually dispatched by its final presentation window to finish after ordinary gameplay failure. That exception does not retain old queued speech, loops, or gameplay continuations. Future queued lines and loops are cancelled with their owner. A technical stop, explicit administrative stop, or a newly started attempt for the same participant cancels obsolete dialogue from that participant's ended attempt. It does not cancel another still-active attempt's public line merely because its speaker is elsewhere. Keep the resources of a permitted finishing line until it ends or is interrupted, then release them normally.

Clients acknowledge presentation state only for playback management and diagnostics. A muted client or an audio-completion acknowledgement cannot claim objective success, decide damage, or advance server gameplay. [Q138](presentation-controls-and-models.md#q138-named-playback-and-explicit-stopping) accepts named playback and `stop_presentation`. The exact network handles and queue capacity follow the client protocol and operational-limit design.

## Q136: translated text and voice

Accepted: retain required default `text` and optional default `sound` on a line, and allow a `translations` map keyed by supported locale ID. Each locale entry may supply translated text and an optional corresponding voice asset. The same form is available on each dialogue variant, so the server chooses a variant once while clients can receive its appropriate localized presentation.

```yaml
dialogue:
  id: defiant_warning
  text: "You will go no further."
  sound: raid_tools:voice/defiant_warning
  translations:
    ru_ru:
      text: "Дальше вы не пройдёте."
      sound: raid_tools:voice/ru_ru/defiant_warning
```

If translated text is absent, use the default text. If a localized voice is absent, use the default recording; if no recording exists, show text only. Translated text may therefore accompany the default-language recording. Show this fallback in the editor's locale preview. Missing required published assets are validation errors, not a request to synthesize or download a translation.

Resolve the recipient's supported locale when its line begins and keep that presentation until it finishes. Changing client language affects later lines without changing the shared variant choice, audience membership, or gameplay. Different recording lengths affect only that recipient's presentation lane. A style uses the chosen text without changing its meaning or forcing a case transformation.

The publisher may compile these authored translations into its versioned resource representation. Retain the captured text, voice references, and style for existing consumers. Native resource-pack language files remain available for other supported assets; this dialogue map is a readable authoring form, not a requirement to duplicate every translation manually in a second file. Automatic translation and speech generation are not implied.

[Q258](item-text-and-reusable-items.md#q258-native-item-names-and-lore-with-captured-formatting) accepts a restricted native item-text consumer of these styles. Q260-Q261 subsequently extend that consumer with captured translations and archived custom fonts. Native item text remains at normal scale; these item contracts do not change dialogue playback or localization behavior.
