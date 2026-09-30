# Reward recipients and delivery

Status: Q250-Q251 are accepted. They define recipient qualification and private delivery for Q248 completion rewards, with Q249 Test payout behavior. Q252-Q255 accept generation, uncertain-transfer review, durable completion, and storage policy; native reconciliation still needs implementation verification. No reward implementation exists.

## Q250: qualify the captured roster at success

Accepted: default each completion-reward entry to every member of the successful attempt's captured participant roster. Include dead, passed-out, disconnected, reconnecting, and observer members. Add an optional per-entry `recipients` selector so authors can narrow that set using supported conditions.

Reward qualification gets its own contextual defaults:

```yaml
recipients:
  from: participants
  online: any
  state: any
  participation: any
```

This selector fragment makes the default explicit. It is not a complete reward entry. Omitting `recipients` or supplying an empty selector has the same full-roster meaning. Explicit fields replace the corresponding reward default; concise filters and `where` combine with AND under the existing selection rules. For example, `recipients: {participation: active}` excludes reconnecting and observer members but still includes dead actively admitted members.

Restrict this initial recipient collection to the current attempt's `participants`. Reject other `from` collections rather than silently intersecting an unrelated world query with the roster. A late arrival, nearby player, fatal attacker, or GM inspecting the attempt is not automatically a completion-reward recipient. Granting or revoking GM status after admission does not rewrite the captured roster or silently reapply the original raider filter. An explicitly included GM remains a member under the same authored qualification.

### What the default means

Membership is the default qualification, not a claim that Conclave measured effort. Do not invent damage thresholds, attendance percentages, minimum time alive, last-hit credit, or automatic penalties for leaving the arena. The accepted framework has no general contribution-score or reward-lockout system. Authors needing stricter qualification can use supported encounter-owned roles, auras, counters, and conditions; any new attendance or contribution tracker needs an explicit capability.

Each qualified identity receives its own declared reward allocation. An entry is not divided among its recipients and does not select a winner implicitly. A valid empty recipient set awards that entry to nobody without changing encounter success or expanding the query. Different reward entries qualify independently; a player matching several entries earns each of them. [Q252](reward-generation-and-review.md#q252-resolve-a-separate-reward-for-each-recipient-at-success) accepts the initial loot context and independent recipient rolls. Q256-Q257 accept fixed item/XP fields and explicit enchantments.

### Qualification boundary and accessible state

Under Q254, resolve all reward recipient selectors against one coherent encounter-level state at the final successful gameplay decision, before attempt teardown and participant recovery. Freeze the selected stable player identities for each entry and make the allocations earned only when success and their complete contents commit durably together. Storage wait, result presentation, teleporting to recovery, removing attempt-owned auras, later reconnect, and later claim time cannot change those recipients. Failure, confirmed uncommitted interruption, and an uncommitted potential success create no payable allocation.

Use the encounter's lexical scope and ordinary typed reference rules. Do not retain or reach into a completed phase's discarded private state. Already-committed phase results, phase cleanup, and other state changes keep Q247's order and lifetime meaning. If an author needs a fact to qualify players at attempt success, keep it in supported encounter-owned state or an accepted completion record before its producer ends.

Offline roster and lifecycle records remain queryable where the selected condition supports them. Do not manufacture a loaded entity or treat a saved offline position, health value, or status effect as a current native observation. Conditions requiring a current available subject keep their documented availability semantics. The editor should make this visible when an author combines an all-roster selection with a spatial or live-entity requirement.

Selection does not deliver items or XP, revive a recipient, restore participation, or prolong the attempt. The Test inspection mode uses the same qualification rules but creates no actual payable reward under Q249.

## Q251: private delivery with pending rewards

Accepted: give each qualified player their completion rewards privately. Attempt automatic delivery after that player's ordinary attempt-end recovery finishes, when they are online and alive. Keep undelivered contents in a personal pending-rewards view, accessible through `/conclave` and `/conclave rewards`, with a Claim all action and individual reward claims.

Q251 chooses delivery for whatever identities the qualification policy selects. It does not require Q250's full-roster default. Native NPC death drops remain public world items and XP under Q225; this contract changes only completion-reward delivery.

### Items, experience, and partial delivery

Insert reward items through a supported native player-inventory operation. Honor current component equality, stack limits, inventory capacity, and actual slot availability. Fill available ordinary inventory capacity without replacing existing items, auto-equipping gear, moving unrelated possessions, or spilling overflow into the world. Keep every undelivered remainder pending.

Credit experience as native experience points to that recipient, not levels or public XP orbs. Track its delivered amount independently of item capacity: a full inventory need not block XP, and a later item claim must not grant that XP again. Unsupported numeric totals or native delivery failures follow the eventual explicit delivery contract; do not silently clamp or drop a remainder.

An entry can be partly delivered. Show what arrived and what remains. An ordinary full inventory is a pending-delivery state, not an encounter error, a new loot roll, or a reason to lose the reward. Once contents are resolved, claim requests, partial transfers, and retries use those same contents. They cannot reevaluate eligibility, select the latest published definition, or roll a new item to replace an inconvenient result.

Claims are requests to the server. The server owns the recipient identity, recorded contents, remaining amounts, and current delivery eligibility. Serialize overlapping automatic and manual claims for the same pending work, including repeated clicks, so two requests cannot consume the same remaining allocation. No client-supplied item list or claimed quantity is authoritative.

### Offline players, recovery, and later attempts

An offline or dead recipient keeps their pending rewards. After login, first finish any required participant recovery. When the player is online, alive, and available for ordinary delivery, offer the same recorded rewards. Do not edit offline native inventory data or simulate a login just to insert items. An unresolved recovery obligation keeps automatic payout pending without changing the earned allocation.

Make one automatic attempt at the ordinary post-recovery opportunity, or the corresponding later eligible return. After a capacity-limited attempt, leave the remainder for explicit claims rather than repeatedly modifying inventory whenever a slot becomes free. Show a nonblocking notification and the remaining count. Do not force open a menu or interrupt gameplay with a confirmation popup.

If a player has already entered another attempt, defer automatic payout so it does not unexpectedly fill inventory during that attempt. A living player can still explicitly claim an older earned reward while participating in another attempt, subject to ordinary inventory availability and completed recovery obligations. This adds no blanket restriction on receiving, trading, or using native items during encounters. Claiming an old allocation is not migration of the new attempt's captured content.

Newly delivered items are ordinary native items. They can be used, transferred, stored, dropped, or lost under normal Minecraft rules. Conclave does not reclaim or restore them after a later failure or death. Item appearances obey Q232's archival requirement and the accepted optional asset fallback/Apply workflow; a claim does not force a resource reload or reinterpret an old item through a new appearance definition.

### The personal view

Show encounter and reward display names, when the reward was earned, delivered contents, remaining contents, and a readable reason when a claim cannot proceed. Offer Claim all for the player's available pending rewards as well as a single-entry claim. Claim all is a bounded request; partial progress remains visible and never becomes a single all-or-nothing inventory rollback.

Ordinary players may view and claim only their own records. Existing authorized GM/operator inspection can explain reward and Test results, but it does not grant a new permission to claim another player's allocation, mint replacements, or reissue a paid reward. [Q253](reward-generation-and-review.md#q253-hold-uncertain-transfers-for-operator-review) accepts operator-only resolution of existing uncertain transfers, separate from inspection and without general grant authority.

Do not expire pending rewards merely because the originating encounter ended, its revision left recent history, or the recipient stayed offline. Retain the content actually needed to fulfill them. Q253 requires uncertain transfers to be held for review. Q255 accepts bounded storage reservation before activation and paid-history compaction with retained receipts. Verified native crash reconciliation remains implementation work; this is not a promise of unlimited storage or an implemented exactly-once transaction with Minecraft saves.

Q249's ordinary Test mode records inspectable output only. It creates no claimable entry and cannot be converted into a later payment by publishing the draft or changing the next Test's mode. An operator-authorized real-payout Test uses the ordinary qualification and delivery rules.

## Related contracts and remaining work

Q250 chooses who qualifies and when. Q251 independently chooses how qualified recipients receive completion rewards. These contracts extend [Q248-Q249 rewards and Test policy](completion-rewards-and-test-policy.md), [Q170 selector contexts](roles-and-player-state.md#q170-explicit-online-and-life-state-filters), [Q238 participation](reconnect-admission-and-assets.md#q238-participation-independent-of-online-and-life-state), [Q247 outcome settlement](simulation-stages-and-outcomes.md#q247-settle-child-results-before-their-parents-outcome), and [Q66 participant recovery](death-and-revival.md#q66-recovery-after-an-attempt-ends).

Q252-Q253 accept generation, the initial loot context, and uncertain-transfer review. Q254-Q255 accept durable outcome/allocation commitment, capacity admission, and retention. Q256-Q257 accept fixed item/XP payloads and explicit enchantments. Additional typed item properties, numeric storage budgets, and verified native delivery reconciliation remain dependent work. No reward store, claim interface, inventory adapter, or XP adapter is implemented.

[Q252-Q253](reward-generation-and-review.md) accept generation timing and loot context, plus operator resolution of uncertain native transfers. Inspection permission alone does not authorize repair.

[Q254-Q255](durable-completion-and-reward-storage.md) accept durable outcome/allocation commitment, early storage reservation, and retention. Q254 refines Q250 by freezing qualification at the final gameplay decision, with entitlement established only by subsequent durable commitment.
