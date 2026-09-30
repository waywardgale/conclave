# Durable item localization and fonts

Status: Q260-Q261 are accepted. They independently extend Q258 native item text with localization and custom fonts, using Q232-Q236's retained-resource workflow. Q259 reusable item definitions can use these supported properties. No localization, font fallback, or archive adapter is implemented.

## Q260: item translations follow the viewer and retain the original text

Accepted: allow a `translations` map inside each configured name or lore text mapping. Keep required default `text`, optional shared `style`, and per-locale entries containing literal `text`, following Q136's locale-map convention. Resolve the viewer's exact supported locale against the item's captured translations, falling back to the authored default text.

```yaml
item: minecraft:iron_sword
name:
  text: Ritual blade
  translations:
    ru_ru:
      text: Ритуальный клинок
lore:
  - text: Recovered from the sealed vault.
    translations:
      ru_ru:
        text: Найден в запечатанном хранилище.
```

This configured-item fragment can be used directly or under a reusable item's `stack`. It adds no translations to the item's Conclave definition label or the enclosing reward label. Plain strings and Q258's nontranslated mappings remain valid; authors need neither translation keys nor separate language files for this form.

### Viewing and fallback

Each viewer sees their own language from the same authoritative stack. Changing the client's language changes subsequent rendering of the existing item when the native language change is applied. Do not freeze text in the original recipient's language, rewrite stack components on a language change, or create different reward allocations for different locales. There is no dialogue-style playback lifetime to preserve for a persistent tooltip.

Use an exact authored locale match; otherwise use the required default for that line. An omitted or empty translation map behaves like no translations. Reject an invalid locale, duplicate normalized locale, unknown translation field, or a missing `text` within a supplied entry. A translated item name must be nonempty; lore retains Q258's intentional blank-line support. `name: ""` still removes the custom name and cannot contain a translation map.

Keep style and the number/order of lore entries common across locales. A locale can translate a line but cannot insert extra lines, change the base item or model, supply its own style, select recipients, or substitute runtime state. A missing translation for one line does not erase the whole tooltip or force another line into a different language.

The fallback is the authored default even when an explicitly supplied `en_us` translation differs from it. Native language resources merge English with the selected language before ordinary component fallback, so an implementation cannot assume that a native fallback string alone enforces this rule. Require a verified exact-locale resolution path for Conclave-managed keys. Preserve arbitrary literal text, including percent signs, without interpreting native formatting placeholders or exposing author-written component syntax. ASVS 1.1.1, 1.1.2, 1.3.2, 1.3.5, 2.1.1, and 2.2.1.

### Durable translation resources

Extend the appearance archive to retain immutable item translation content before any real stack can reference it. Capture the default text and complete authored locale map, sufficient format metadata, and the necessary identity mapping. Use a stable content-derived managed identity shared by unchanged content, not a per-copy, whole-revision, encounter, or recipient-language stamp. Copied native formatting retains Q258's separate component semantics.

Store a native compatible translation reference with a literal-preserving default fallback in the item data. Preserve a rendering path that shows that default if the exact translation resource is unavailable, declined, staged but unapplied, or missing after an incomplete world transfer. Do not show a generated key, fetch the latest translation, or remove the actual component to manufacture a fallback. Exact encoding and native text hooks need implementation verification; raw generated language JSON is not presumed lossless.

Apply Q232's conservative retention and Q255's preparation bounds to the new resource type. Reserve storage and verify the bundle before real-item materialization, including real NPC equipment in Test. Pending rewards retain their exact contents and dependencies. Claims, later publication, history pruning, and item transfer cannot replace old translations. Pure authoring previews retain their existing temporary lifetime until a real item creates a durable dependency.

Changing any captured translation changes that content's identity; unchanged content remains shareable across unrelated publications and reusable definitions. Different translation identities may keep otherwise identical items from merging even when their current displayed language looks the same. Do not override native component equality to collapse them. The archive tracks retained text resources, not item copies or reward eligibility.

### Client delivery and ordinary item changes

Offer only translation resources justified by an item already legitimately synchronized to that client or another authorized consumer under Q234. Send the supported bundle and necessary metadata, not unrelated item definitions or private encounter data. All authored translations on an ordinarily disclosed item are inspectable content, not a private-clue mechanism. Arbitrary client keys do not authorize archive lookup. ASVS 8.2.2, 8.2.3, and 8.3.1.

Use the existing consent, bounded staging, explicit Apply, reconnect-restoration, and cache policies. A newly encountered translated item does not trigger a resource reload during an active attempt. Ordinary item use and private reward claims remain available with the default text. An ordinary local language change may refresh the display using the already applied captured content; it does not authorize applying a newer pending Conclave resource set. Do not promise uninterrupted native rendering during a user-initiated language reload.

A native anvil rename replaces the custom name with the player's new literal name under ordinary Minecraft behavior. Removing it reveals the underlying native name. Do not restore the translated name afterward, translate player-written text, or remove unrelated lore. Later native transformations keep their own component behavior; no inventory migration or replay is added.

Q260 needs no custom font and remains independent of Q261. This extends Q258's literal-only authoring form solely with captured literal alternatives; it does not introduce dynamic text, rich spans, translation services, or executable components.

## Q261: archive custom item fonts and provide an ordinary-font fallback

Accepted: allow the existing `text_style.font` reference on configured native item names and lore, using a supported immutable font resource. Archive its complete supported dependencies before a real stack references it. When that font is unavailable to a viewer, show the same text in the ordinary default font without changing the actual item.

```yaml
schema: 1
namespace: raid_tools
text_style:
  id: relic_script
  font: raid_tools:relic_letters
  color: "#b792ff"
  italic: false
```

```yaml
item: minecraft:iron_sword
name:
  text: Ritual blade
  style: raid_tools:relic_script
```

These are a style definition and a configured-item fragment. The font is an imported supported asset selected in Minecraft, not a font URL, filesystem path, new item type, or renderer plugin. This contract works with Q258's literal text independently of Q260 item translations.

### Supported resources and validation

Initially support imported TrueType fonts and native bitmap font resources through verified adapters, including bounded native spacing and font-reference dependencies. The import UI may prepare a supported native font definition from a selected TrueType file. Other provider formats require explicit verified support before import accepts them; a resource filename or a registered third-party provider is not sufficient.

Resolve the complete supported dependency graph, reject missing resources and cycles, and preserve provider order and supported options that affect glyph selection. Validate files, decoded dimensions, glyph data, font metrics, reference expansion, and peak loading budgets under Q124/Q128 before admission. Do not follow external URLs, arbitrary local paths, executable providers, or unverified custom loaders. Built-in client resources keep their existing compatibility boundary; Conclave does not freeze every unrelated player resource pack. ASVS 2.1.1, 2.1.2, 2.1.3, 2.2.1, and 2.2.2.

Keep native item text at normal scale. Q133's non-unit scale still cannot be copied into a native text component, and this contract does not add tooltip layout or font-size controls. Retain supported color and emphasis independently of the font. The editor previews the captured font with the authored text and each authored translated line under Q260. Report known missing glyphs and unsupported metrics before publication; the supported profile must establish usable glyph coverage or an explicit supported fallback within the graph for those authored strings.

### Archival and fallback

Extend Q232's archive with the font's immutable definition, supported font files/textures and referenced dependencies, and sufficient mapping data to resolve them after catalog retirement. Reuse unchanged graph identities across item definitions and publications. Store the captured native font reference on the item and keep the resolved non-font style values under Q258. Do not add a per-copy identity or make an item look up the latest logical font at rendering time.

Archive before real native item exposure, account for peak and permanent storage during preparation, and retain conservatively even after no loaded item appears to use the font. The same requirement applies to Test equipment regardless of death-drop suppression and to private completion items. An unresolved font archival error cannot be bypassed by classifying the item's presentation as optional. Existing items remain intact when new admission is blocked.

For a known Conclave-managed font whose exact applied graph is missing or unusable, render a temporary text view with the default font while preserving the actual item data, selected literal text, and supported color/emphasis. Native missing-font behavior is not a proved readable fallback, so this requires a verified client integration. Do not replace the entire item's custom name with its base item name, erase lore, or mutate stack components to make fallback rendering work.

Resolve localization and font availability separately: an applied translation can use the ordinary-font fallback, and a missing translation uses the captured default text with whichever font is currently supported. Fallback follows the receiving client's ordinary glyph/accessibility settings; it does not promise a custom rune's meaning can be recovered from an unrelated glyph. Authors should use textual content that remains understandable in the default font when this fallback is needed.

### Delivery and readiness

Extend Q234's server-authorized item demand to captured font references in supported synchronized native text. Stage missing font graphs under the same consent and bounded transfer controls as item models. An ordinary item cannot force Apply during active gameplay, bypass cache limits, or make a client disclose unrelated archived resources. Protect applied and still-required dependencies under Q236; local cache eviction does not delete the server's permanent graph.

Ordinary native tooltip text introduces no new `required` flag. A cosmetic fallback allows ordinary item use and reward delivery; it does not satisfy a separately declared required presentation or future-attempt readiness dependency that needs the exact font. Such dependencies retain Q137 and Q241's existing checks. Successful download alone is not applied readiness, and required output cannot shrink its audience to avoid unavailable resources.

After an eligible Apply or permitted restoration successfully applies the exact graph, existing items may render with their captured font. Do not replay item grants, rename operations, or encounter presentation to refresh the view. The font graph remains retained independently of any translation bundle and any item-model graph, sharing dependencies where safe.

## Related contracts and remaining work

Q260 independently adds captured translations and readable default text. Q261 independently adds custom fonts and default-font rendering for the already accepted native text consumer. Each extends the archive and delivery workflow for its own resource type; either can be used without the other.

These contracts extend [Q258-Q259 native item text and reuse](item-text-and-reusable-items.md), [Q133 styles and Q136 locale maps](dialogue-formatting-and-delivery.md), [Q128 asset identity](world-locations-and-assets.md#q128-stable-resource-references-in-manifests), [Q232-Q233 permanent appearances and native equality](durable-item-appearances.md), and [Q234-Q236 demand, Apply, and cache](archived-appearance-delivery.md). They retain [Q253 uncertain delivery](reward-generation-and-review.md#q253-hold-uncertain-transfers-for-operator-review), [Q254-Q255 durable completion and capacity](durable-completion-and-reward-storage.md), and [required resource loss and observer rules](client-resource-failures-and-observers.md).

[Native text research](item-text-research.md) records locale merging, component formatting, font references, and missing-resource behavior. The selected ASVS references state accepted validation and authority requirements, not an achieved conformance level. Lossless text compilation, exact native integration hooks, font adapters and measured limits, resource-removal/reload tests, and independent-backup compatibility still require implementation and verification.
