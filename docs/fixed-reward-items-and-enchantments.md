# Fixed reward items and enchantments

Status: Q256-Q257 are accepted. Q248-Q255 already accept completion rewards, qualification, private delivery, generation, durable success, and storage policy. Q144 already accepts configured native equipment with counts and enchantments, while Q231 accepts `item` and `item_model`. They define fixed reward fields and the shared explicit-enchantment format. No item or XP adapter is implemented.

## Q256: fixed items and experience in a reward entry

Accepted: add `items` as a list of native item descriptions and `experience` as a nonnegative integer number of XP points. Reuse Q144's concise item ID and configured item map, with required `item`, optional positive integer `count` defaulting to one, and supported typed properties such as Q231's `item_model`.

```yaml
rewards:
  - id: clear
    name: Completion rewards
    items:
      - item: minecraft:diamond
        count: 96
      - minecraft:golden_apple
    experience: 250
```

This is an encounter-body fragment, not shipped encounter content. Each qualified recipient earns 96 diamonds, one golden apple, and 250 XP points. The entry's `name` labels the reward in the Rewards view; it does not rename any native item. Q250's full-roster recipient defaults apply when `recipients` is omitted.

### Quantities and native stacks

In a reward item entry, `count` is the total number of that configured item owed to each qualified recipient. Split it into legal native stacks using the resolved item's actual maximum stack size. Never represent a large quantity by creating an oversized native stack or by changing the item's stack-size component. Each stack carries the same captured supported properties.

For the example, ordinary diamonds can be represented as a stack of 64 and a stack of 32. Delivery can merge into matching partial inventory stacks under Q251; it need not preserve those initial stack boundaries. Full inventory leaves the exact remaining quantity pending. Native component equality decides whether items merge, including enchantments and captured item-model identity under Q233.

The shared configured item form keeps the same meaning of `count` as quantity across consumers, but each consumer enforces its capacity. An equipment slot holds one legal stack, so equipment cannot use a reward's multi-stack expansion to equip several stacks in one slot. Reject an equipment count exceeding that resolved stack's maximum. Managed relic appearances and item icons retain their own accepted display fields; this contract does not turn them into real inventory contents.

Use bounded authored constants. Reject zero, negative, fractional, Boolean, percentage, range, expression, or overflowing counts, unknown item IDs, and a non-item such as air used as a reward item. Repeating an item in separate list entries adds those quantities; it is not a random choice, replacement, or duplicate ID. Preserve source locations for inspection even when identical contents can be consolidated for storage and delivery.

### Experience and combined contents

`experience` means points, matching the accepted NPC field name. It never means levels, a multiplier, an XP orb, or the native NPC reward calculation. Default it to zero. Reject negative, fractional, Boolean, percentage, expression, and `vanilla` values in this context because completion has no implicit native XP source.

An entry may combine `items`, `experience`, and the already accepted `loot: {table, at}`. Add fixed items to the generated table contents and award the fixed XP once per qualified recipient. Loot-table output is not modified by the fixed item's property map. Keep one immutable allocation decision per entry/recipient and the accepted independent table roll.

Omitted `items` means no fixed items; an explicit empty list has the same meaning. An entry whose fixed contents are empty, whose experience is zero, and which has no table is a valid no-op with an authoring warning. A supported table may also legitimately produce nothing. Neither case creates a payable empty reward or changes success. The editor distinguishes a known empty declaration from a random table that may be empty.

Validate declared XP against documented adapter and operational limits before admission. Native integer method signatures do not establish a safe grant maximum. Delivery must check the current player's numeric capacity, arithmetic safety, and bounded processing cost before mutation. Apply only a verified exact transferable amount, retaining any proven unapplied remainder as pending with a clear reason. Do not silently clamp away earned points, wrap arithmetic, substitute levels, or mark an unverified mutation paid. Q253 still handles an uncertain actual transfer.

### Validation and inspection

Validate the complete item/property combination on the server, not merely the existence of its registry ID. Native setters that clamp, ignore unsupported properties, or return an empty item do not satisfy the declared reward. Unknown fields and unsupported properties fail with the ordinary file/field diagnostic. Do not add arbitrary component maps, raw NBT, item command strings, formulas, or command execution. ASVS 2.1.1, 2.1.2, 2.1.3, 2.2.1, and 2.2.2.

Include worst-case expanded stack counts, serialized item properties, and XP bookkeeping in Q255 admission. Resolve against the captured supported definitions and preserve external-reload constraints. Archive every custom appearance before real stacks can reference it, including real-payout Test. Later claims use recorded contents, not current YAML. ASVS 2.3.1 and 2.3.3.

The in-game editor shows quantities per qualified player, native stack requirements, XP points, and applicable validation limits. Test uses the same generation and validation but follows its captured payout mode. A preview cannot grant items, alter real equipment, reroll recorded Test results, or convert them into pending rewards.

## Q257: one explicit enchantment map with normal compatibility

Accepted: use `enchantments` as a map from registered enchantment IDs to positive integer levels in configured real-item descriptions. Enforce each enchantment's defined maximum, item suitability, and pairwise compatibility. Use the same contract for existing NPC equipment and fixed completion items, independently of Q256's reward list and XP field choices.

```yaml
equipment:
  mainhand:
    item: minecraft:iron_sword
    enchantments:
      minecraft:sharpness: 3
      minecraft:unbreaking: 2
```

This NPC-body fragment configures an ordinary compatible enchanted sword. It does not declare a new item definition, change the base item globally, or bypass Q225's equipment-drop policy. The enclosing equipment map still uses native slots and its accepted complete-loadout semantics.

Resolve enchantment IDs through the supported native registry and captured external dependency checks. Validate levels against the definition's maximum and the native representable range. A changed supported definition may have a different maximum; do not hardcode every vanilla enchantment's ordinary maximum into the YAML language. Report unsupported IDs, non-integer or nonpositive levels, excess levels, incompatible pairs, or unsuitable items before publication. Do not rely on native helpers that silently remove, clamp, or ignore an invalid request. ASVS 2.1.1, 2.1.2, 2.2.1, and 2.2.2.

An omitted map adds no explicit enchantment customization and preserves the selected item's supported base defaults. A present map is the complete authored initial enchantment set for the selected native channel; an empty map clears that channel. Apply this once when materializing the configured item. Native enchanting, repairs, transfers, wear, and replacement afterward retain ordinary Minecraft behavior, without automatic reapplication.

For `minecraft:enchanted_book`, use the same readable `enchantments` field to populate native stored enchantments. A book stores an enchantment for later use, so do not require that enchantment to apply directly to a book as a weapon or tool. Still enforce supported levels and pairwise compatibility. For ordinary equipment, use native applied enchantments and require item suitability. A plain `minecraft:book` does not silently change type; explain that stored enchantments need an enchanted book. Other modded storage items require a verified adapter rather than an assumed component layout.

Preserve unrelated supported base-item properties. Initially reject a real-item adapter whose applied-versus-stored enchantment channel or base defaults cannot be handled unambiguously. A stored-enchantment book does not gain the enchantment's combat effect merely because it is equipped in a hand.

Do not add an `unsafe`, force-compatibility, or over-level switch in this initial form. Explicitly supported future item capabilities can extend authoring with their own validation and native behavior. This restriction applies to Conclave-authored item construction, not player inventories generally; existing unusual Minecraft items are not scanned, stripped, or repaired. Native NPC `loot: vanilla` keeps its separate accepted behavior. Completion table functions and outputs retain Q252's verified profile and cannot gain unrestricted component access by naming a native table.

Q258 subsequently accepts initial native item names/lore, and Q259 accepts reusable item definitions. Dynamic text, custom attributes, damage values, potion contents, and other item-property fields still require explicit typed capabilities. These contracts add no item-grant action.

## Related contracts and remaining work

[Native item and XP research](completion-loot-research.md#item-payload-and-experience-bounds) distinguishes native serialization ranges, strict stack validation, enchantment helpers, and XP arithmetic. It supplies integration facts, not proof that these adapters or delivery bounds already work.

These contracts extend [Q144 typed equipment](npc-stats-and-combat.md#q144-typed-equipment), [Q231 native item models](model-lighting-bounds-and-item-appearance.md#q231-typed-native-item-model-selection), [Q248-Q249 rewards and Test policy](completion-rewards-and-test-policy.md), [Q250-Q251 qualification and delivery](reward-recipients-and-delivery.md), [Q252-Q253 generation and review](reward-generation-and-review.md), and [Q254-Q255 durable completion and storage](durable-completion-and-reward-storage.md). They preserve [ordinary item identity and retained appearances](durable-item-appearances.md).

The selected ASVS references describe accepted validation and operation requirements, not an achieved conformance level. Exact numeric quantity/XP budgets, verified modded-item support, additional typed properties, native delivery reconciliation, and storage implementation remain later work.

[Q258-Q259](item-text-and-reusable-items.md) accept native item names/lore and reusable configured item definitions, extending the item properties accepted here.
