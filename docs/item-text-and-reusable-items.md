# Item text and reusable configured items

Status: Q258-Q259 are accepted and define native names/lore and a reusable item definition. They extend Q144/Q231/Q256/Q257 configured real items, native item models, reward quantities, and explicit enchantments. No item construction or text adapter is implemented.

## Q258: native item names and lore with captured formatting

Accepted: add optional `name` and `lore` to configured real items used by NPC equipment and fixed completion rewards. Accept a literal string for the name and a list of literal strings for lore. Each nonempty text value may instead use `{text, style}` to reference an existing Q133 `text_style` that this native text consumer supports.

```yaml
item: minecraft:iron_sword
name: Ritual blade
lore:
  - Recovered from the sealed vault.
  - text: A faint pulse answers your touch.
    style: conclave:whisper
```

This configured-item fragment adds native tooltip text to an ordinary sword. It can appear in an equipment slot or a reward's `items` list. It is illustrative authoring, not bundled encounter content. A reward entry's own `name` still labels that reward in the Rewards view; only the configured item's `name` changes its native display name.

### Native behavior and defaults

Write authored `name` to the native custom-name component. Keep ordinary anvil renaming and removal: a player's later rename replaces the authored name, and removing it reveals the item's remaining native item name or base name. Do not continually restore the original text or add an unrenameable-name field in this first form.

Omitting `name` preserves the base item's supported initial naming data. A nonempty value sets the custom name. Explicit `name: ""` removes the initial custom-name component, revealing the native underlying name; it does not create a blank visible name. Use this concise empty-string form for removal. A styled mapping requires nonempty `text`, so it cannot attach meaningless formatting to a removed name. Reject whitespace-only names and line breaks in a name.

Omitting `lore` preserves supported base lore. A present list replaces the initial lore lines, and `lore: []` clears them. Each entry is one authored line; an empty literal line is allowed for spacing. Reject embedded line breaks in one entry and bound line count, individual text length, and total serialized content. The native tooltip may wrap a long line according to its supported renderer and client layout.

Plain strings retain ordinary native custom-name and lore presentation defaults. An explicit `style` resolves the self-contained Q133 template and writes its supported visual fields into that line, overriding inherited native defaults where specified. This can give a custom name an explicit `italic: false` instead of native inherited italics. Do not use a chat renderer, place a second floating label over the item, or change item behavior through tooltip text.

### Supported styles and literal text

Support native color, bold, italic, underline, and strikethrough from the existing style definitions at normal text scale. Q261 extends the original default-font-only form with supported archived custom fonts and an ordinary-font fallback; non-unit scale remains invalid for this consumer. Q260 extends literal text with captured locale alternatives. Do not silently discard unsupported formatting. Q133's broader capabilities for dialogue remain unchanged.

Resolve the style from the attempt's captured content when constructing the item data and copy the supported values into its native text components. Preserve the literal text and resolved formatting in a pending allocation. Existing items and pending rewards therefore keep their original text after a style edit or publication. Claims do not look up the latest style, and unrelated hotfixes do not add a new identity component to otherwise identical items.

Treat `text` as literal text rather than JSON, a translation key, an expression, a selector, or an interpolated string. Construct native literal components using supported APIs; do not concatenate user text into serialized component syntax. Reject legacy formatting control sequences as an unsupported formatting input and direct the author to `style`. This first form does not accept rich spans, click/hover actions, command links, dynamic scoreboard/NBT/entity content, or hidden runtime substitutions. ASVS 1.1.1, 1.1.2, 1.3.2, 1.3.5, 2.1.1, and 2.2.1.

Names and lore are ordinary item content visible wherever Minecraft exposes that stack. They do not inherit private encounter audiences, become viewer-specific clues, or authorize retrieval of private encounter state. Custom names, enchantments, and lore remain independent properties; setting one does not erase the others.

### Persistence and scope

Store the resolved native text as part of the item data, independently of editable manifests. Literal text and the supported inline formatting require no new permanent resource archive entry. Existing `item_model` dependencies still require Q232 archival. Q260-Q261 require translated and custom-font forms to retain their referenced resources in the archive; stored references alone do not contain them.

Keep native stacking, copying, splitting, transfer, repair, and later item mutation under Q233. Different names or lore can make stacks unequal under native component comparison; Conclave must not ignore that difference to merge them. The item carries no per-copy Conclave ID or automatic link that rewrites it when the source manifest changes.

Validate these fields and total payload bounds on the server before publication/admission and again on actual supported generated data. Native loot functions are not automatically permitted to emit arbitrary dynamic text just because fixed item text is supported; Q252's verified generation profile remains authoritative. Inspection uses the same resolved text without materializing a real grant. ASVS 2.1.2, 2.1.3, and 2.2.2.

Q258 selects initial native item text while leaving ordinary later Minecraft changes intact. Q260-Q261 subsequently add localization and custom-font distribution. These contracts add no item-text mutation action, player-inventory scan, or tooltip layout controls.

## Q259: reusable item definitions with explicit use sites

Accepted: add an `item` manifest kind containing a reusable configured native item under `stack`. Reference it using `{use: definition_id}` wherever NPC equipment or a fixed reward item already accepts a configured real item. Keep quantity at the use site, with `count` defaulting to one.

```yaml
schema: 1
namespace: raid_tools
item:
  id: guard_sword
  name: Guard sword preset
  stack:
    item: minecraft:iron_sword
    enchantments:
      minecraft:sharpness: 3
```

```yaml
equipment:
  mainhand:
    use: raid_tools:guard_sword
```

```yaml
rewards:
  - id: clear
    items:
      - use: raid_tools:guard_sword
        count: 2
```

These independent fragments show one definition used for equipment and a reward. The wrapper's `id` is its Conclave identity, and optional wrapper `name` is an editor/inspection label. Neither renames the real item. `stack` contains its native base `item` and supported configured properties. Q258 permits `stack.name` and `stack.lore` to describe native text explicitly; reuse itself does not require those fields.

This is a recipe for constructing existing registered Minecraft or installed-mod items, not registration of a new native item type. It supplies no custom attack code, item event handlers, unique-item ownership, persistent aura, or general item-grant command. In the reward example, two swords become two legal stacks under Q256 because the base sword's native stack maximum is one.

### Unambiguous references and quantities

Preserve bare native item IDs and `{item: native_id, ...}` for direct construction. A consumer mapping chooses exactly one of `item` or `use`; never search both native and Conclave registries for the same string. `use` resolves the Conclave `item` definition kind in this context. Its reuse spelling matches mechanics, but does not import mechanic parameters, lifecycle, or `with` arguments.

Use Q78's namespace rules: an unqualified authored `use` searches only the caller's namespace, while references written inside `stack` resolve under the definition's own rules and namespace. Native item and enchantment IDs keep their native registry semantics; asset references keep their existing typed rules. Report missing, duplicate, or wrong-kind definitions before publication.

Keep a definition's `stack` independent of quantity. Reject `count` inside `stack`; put it on the equipment or reward use site. A `use` mapping permits only `use` and the applicable `count`. The resolved quantity still obeys the consumer's existing rules: one legal equipment stack per slot, or a bounded total quantity across reward stacks.

Do not accept inline property overrides alongside `use`, arbitrary deep merges, inheritance, nested item `use` inside `stack`, or a second templating language. An author can copy the definition in the editor or write a direct configured item when they need different properties. This keeps validation and source inspection local to a single definition. Adding parameterized item variants later would be an explicit capability, not an accidental consequence of Q79 mechanics. ASVS 2.1.1, 2.1.2, 2.2.1, and 2.2.2.

### Captured contents and ordinary items

Resolve and validate all referenced item definitions with the complete candidate revision, including reward limits, enchantments, consumer capacity, and reachable appearance dependencies. Current attempts keep their captured definitions; pending rewards keep their already generated item data. Editing or deleting a source definition cannot rewrite an old stack or regenerate a pending award.

Materialize the resolved supported native properties. Do not add the Conclave definition ID, whole revision identity, or attempt ID to the actual stack merely because the author used a reusable definition. Record useful provenance in authorized diagnostics and reward records separately. A direct item and a reused item with identical resolved native components have the same native equality and stacking behavior.

The in-game editor can create, find, copy, inspect, and use these definitions through the accepted draft workflow. Show both its readable label and identity where needed, its resolved properties, quantity at the use site, and the consumers affected by an edit. Saving is not publishing; Test and publication retain their existing permissions and resource policies.

Start with the two already accepted real-item consumers, NPC equipment and fixed completion rewards. Managed relic visuals, icons, native loot tables, crafting, and arbitrary world item creation do not gain implicit `use` support. Each additional consumer needs an explicit compatible contract.

## Related contracts and remaining work

Q258 chooses native item text independently of whether authors repeat a direct configuration or reference an accepted reusable item definition. Q259 chooses reuse for the already accepted configured-item capabilities independently of adding text fields.

These contracts extend [Q144 equipment](npc-stats-and-combat.md#q144-typed-equipment), [Q256-Q257 fixed rewards and enchantments](fixed-reward-items-and-enchantments.md), [Q70 manifest layout](manifests-and-publishing.md#q70-one-consistent-file-shape), [Q78-Q79 namespaces and reuse](manifest-references.md), [Q133 styles and Q136 dialogue translations](dialogue-formatting-and-delivery.md), and [Q232-Q233 appearance retention and native item identity](durable-item-appearances.md).

[Native item-text research](item-text-research.md) records component, anvil, formatting, and resource-reference behavior. These are accepted authoring contracts; the selected ASVS references do not claim an implemented adapter or a complete conformance level. Q260-Q261 accept item localization and durable custom fonts. Rich text, other typed item properties, numeric limits, and native delivery/save integration remain later work.

[Q260-Q261](durable-item-localization-and-fonts.md) accept independent localization and custom-font extensions, with retained dependencies and readable fallbacks. They extend the original literal/default-font form while preserving the normal-scale restriction.
