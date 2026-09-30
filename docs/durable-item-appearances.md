# Durable item appearances

Status: Q232-Q233 are accepted. Q231 accepts custom native item models on real NPC equipment as well as managed visuals. Q232 addresses durable resources after real items can escape attempt ownership. Q233 independently preserves ordinary item identity and inventory behavior. No implementation exists.

## Q232: retain an appearance archive once real items can use it

Accepted: maintain a shared immutable appearance archive in the world's Conclave data. Before Conclave can create real equipment using a custom item model, durably retain that appearance's supported asset dependencies and the metadata needed to resolve it. Keep this archive independently of the configurable recent-content history. In the initial implementation, do not automatically retire an archived appearance on the assumption that its last item has disappeared.

A sword can move from an NPC into a player's inventory, an unloaded chest, a nested container, or an offline player's saved state. An ended attempt, a missing loaded item, or a zero count in an online-inventory scan does not establish that no copy remains. Native saves retain a model identifier rather than its asset bytes. Keeping the supported appearance graph supplies what an older real item still references after its original encounter catalog is pruned.

### What is retained

Archive the immutable model resource, the complete supported dependency graph, and sufficient versioned mapping and compatibility metadata to resolve it without reopening the original draft or restoring a retired encounter. Preserve authored names and origin information useful for inspection, without requiring the entire old gameplay catalog. Share unchanged appearance identities and deduplicate stored content where the supported asset representation permits it. Retention is per appearance graph, not per item copy.

Use the existing immutable resource identities and supported dependency rules from Q124/Q128. An ID alone, a pointer into an editable draft, or a reference to the latest file is insufficient. Built-in or external resources retain their documented compatibility boundaries; this archive does not freeze arbitrary unrelated client packs or executable renderer code. A graph that cannot be captured and resolved under the supported profile is incompatible with this consumer.

Deleting a source file, overwriting its logical ID, rolling back content, pruning the recent revision history, ending a test, or cleaning up an attempt does not release this archive entry. New equipment can select a newer appearance while existing items retain their captured reference. An unchanged appearance should not need another archive copy merely because an unrelated manifest changed.

Purely managed relic visuals, item icons, and harmless previews retain their existing lifecycle-based asset retention. They do not permanently archive every appearance they display. A Test draft attempt does create real equipment and therefore follows the same durable requirements as an ordinary attempt, including when equipment drops are disabled. Real equipment may still be moved or copied by supported world operations; a no-drop policy is not proof that every copy remains temporary.

### Admission and crash ordering

Preflight the reachable custom real-equipment appearances during attempt preparation, including supported descendant definitions and later phases. Validate capacity during publication and test validation where knowable; reserve and recheck it when preparing the actual attempt. Do not wait until an NPC dies to retain the assets. Any supported path that would materialize a previously unretained real stack must pass the same durable admission before exposing that stack to native state.

Commit the archive's verified asset data and required catalog metadata durably before a real equipment stack can reference it. Only then may the native creation operation proceed. An interrupted archive write creates no admitted stack; an interruption after archive commitment may leave a retained appearance with no surviving item. Keep that conservative extra entry. Do not infer that a failed spawn proves the archive is unneeded, recreate an item from an archive entry, or replay a death reward after restart.

This ordering protects the supported Conclave creation paths. It does not claim one atomic transaction spanning archive files, all Minecraft world saves, other mods, and external backups. Recovery reconciles archive metadata and verifies committed content before new dependent creation. Missing or corrupt committed data produces a bounded operator diagnostic and blocks new dependent creation. Existing ordinary items remain intact and use the already accepted base-model fallback where needed; missing assets are not a reason to delete inventories or interrupt unrelated attempts. A required presentation failure retains its existing error policy.

Store the archive with the world's persistent Conclave data and include its required data in a complete world backup. Restoring or moving item save data without the corresponding appearance data cannot guarantee its custom visual. Unknown archive identities do not authorize downloading code or resources from arbitrary locations, adopting a newer appearance, or silently reconstructing items.

### Capacity and in-game inspection

Charge unique stored content and metadata to a bounded operator-configured storage budget. Show used storage, newly required storage, known origin, and the reason an appearance is retained in the in-game asset/history interface. Do not report an exact number of surviving items when Conclave cannot establish it. Immutable entries can be shared by many items without incrementing an invented authoritative item counter.

Reserve capacity across concurrent preparation and staging so two individually valid requests cannot jointly overfill the archive. If admitting new unique assets exceeds the budget or available storage, reject that new admission with a readable explanation. Preserve existing items and archived content. Keep cleanup and recovery reserves under the accepted execution policy. Exact numeric defaults and file/transfer limits remain part of the common bounded-resource configuration.

Operators can inspect usage, raise a configured limit when capacity permits, reuse an archived appearance, or remove the new asset requirement from the draft. Ordinary eligible draft/history/cache cleanup can release its own unneeded data; it cannot evict a permanent appearance dependency. Do not add a first-version force-purge command or a loaded-world scan advertised as safe proof of deletion. World-wide item migration or certified retirement would need a separately approved contract and is outside this initial archive.

The tradeoff is conservative disk retention. Even if all items using an appearance were destroyed, its archived data remains. This keeps ordinary item behavior and supports unloaded or copied items without an item-tracking system that changes inventory semantics. Capacity limits and shared content keep this cost explicit rather than disguising it as an automatically reclaimable cache.

### Existing client rules still apply

A permanent server archive does not require every client to hold or activate the entire archive. Delivery continues through the authenticated Minecraft connection, with consent, bounded staging, integrity checks, application acknowledgement, and activation outside that player's active attempt. The Q231 base-model fallback preserves normal item use when the exact appearance is unavailable. It never satisfies a new attempt's required asset readiness or mutates the actual stack.

[Q234-Q236](archived-appearance-delivery.md) accept selection of old item assets for a client's next resource set, explicit application, and client cache eviction/capacity. This contract defines the durable server obligation, not an unbounded download or a new network endpoint. Retaining an asset privately on the server is not permission to transmit unrelated manifests or private gameplay state with it.

## Q233: preserve ordinary stack identity and item transformations

Accepted: store the captured native item-model reference without adding an extra per-copy identifier to the stack for appearance tracking. Do not stamp its originating NPC or attempt identity onto the stack solely to retain its art. Existing native entity UUIDs and Conclave ownership/recovery identities keep their established purposes. Let ordinary item identity, component comparison, maximum stack size, movement, and transformation rules continue to decide inventory behavior.

Reuse the same immutable appearance identifier when the selected supported model and its resolved dependencies are unchanged. Do not put the whole encounter revision or the creation attempt into the stack merely to identify its art. An unrelated balance hotfix therefore does not make otherwise identical items different. A materially changed captured model or dependency graph receives its own immutable appearance identity.

Two items with the same base item, model reference, and other relevant components can stack when ordinary native limits allow it. Different captured model references remain distinct components, so ordinary merging must not discard one appearance to combine them. A sword whose native maximum stack size is one remains non-stackable even if another sword has identical art. Do not add a custom merge override to collapse different appearances or ignore durability, enchantments, names, and other native differences.

Copying or splitting a stack preserves its model reference under the supported native item path. Moving it between compatible inventories, entities, or containers does not create an encounter-owned relic or an obligation to restore its old location. Another native operation may legitimately create or modify a resulting stack. Crafting, repair, equipment replacement, destruction, despawn, and authorized administration follow their actual item/component behavior; Conclave does not automatically transfer a consumed ingredient's appearance to its result or continuously reapply an earlier component after it changed.

A retained archive entry owns asset data only. It grants no ownership over the item, permission to reclaim it at cleanup, kill credit, reward eligibility, or a way to replay a previous grant. Ordinary world-item persistence remains Minecraft's responsibility. Appearance identity is not a globally unique item identifier or a durable reward receipt. [Q250-Q251](reward-recipients-and-delivery.md) accept private completion delivery with pending rewards. The remaining generation and durable-reconciliation contracts must establish their own duplicate-prevention evidence independently of the appearance archive.

The model's supported contextual visuals may still respond to the real stack's current native state. Pinning its definition does not freeze durability, enchantment glint, counts, or future user actions. The temporary base-model fallback changes rendering only, preserving the authoritative current stack under Q231.

## Related contracts

These contracts extend [typed item-model selection](model-lighting-bounds-and-item-appearance.md#q231-typed-native-item-model-selection), [client asset delivery](world-locations-and-assets.md#q124-client-assets-through-the-minecraft-workflow), [resource identity](world-locations-and-assets.md#q128-stable-resource-references-in-manifests), [revision history](manifests-and-publishing.md#q72-history-rollback-and-publication-conflicts), [real-world test attempts](in-game-authoring.md#q77-trying-a-draft-before-publication), [death rewards](npc-death-rewards-and-descendant-events.md), and [interrupted-attempt recovery](cleanup-and-restart.md). They do not change the future-attempt-only hotfix policy.

[Native item research](item-appearance-persistence-research.md) records the verified persistence, copy, component comparison, and live-scan limits separately from these accepted archive requirements.

[Q260-Q261](durable-item-localization-and-fonts.md) extend this archive to durable item translations and custom-font graphs, with the same conservative retention and pre-materialization admission. Each text-resource adapter and its fallback still requires implementation and verification.
