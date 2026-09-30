# Reward generation and uncertain delivery

Status: Q252-Q253 are accepted. They define loot generation and review of uncertain delivery alongside Q248-Q251 reward declarations, Test policy, qualification, and private delivery. No generation, storage, or recovery adapter is implemented.

## Q252: resolve a separate reward for each recipient at success

Accepted: generate each qualified recipient's complete item and XP allocation during successful-completion preparation, using the same decision state used for Q250 qualification. Fix the resulting contents before making the allocation claimable. Use a separate server-controlled random roll for each reward entry and recipient, rather than one shared roll copied to everyone.

Fixed authored items and XP retain their declared amounts for each qualified recipient. A loot table generates that recipient's item portion. Entries remain independent; this adds no shared prize pool, competitive winner, pity counter, or cross-attempt lockout. An empty table result is a valid result, not a reason to roll again.

Prepare against the attempt's captured definitions and verified external dependencies. Claims, reconnects, changes to inventory space, publication, and resource reloads do not change the allocation or trigger generation. A new attempt gets a new generation decision. Test inspection uses the same generation rules, with an independent Test draw; viewing a Test result does not predict or reserve a later ordinary attempt's draw.

### A defined completion context

Initially support loot tables compatible with the native `minecraft:chest` context and the restricted inputs below. This names the native loot context; Conclave does not create a physical chest.

```yaml
rewards:
  - id: clear
    loot:
      table: raid_tools:vault_completion
      at: reward_origin
```

This encounter-body fragment shows the accepted completion reward's `loot` block. `table` names a supported registered loot table, and `at` is a required typed encounter location resolved through the current arena binding. It supplies the loot origin and dimension. Q250's recipient defaults apply. The example is an authoring illustration, not bundled encounter content.

Use that captured named location as the origin for every recipient of the entry. Do not infer an arena-wide coordinate origin, which Q100 explicitly excludes, or use each player's current position. The location must be valid within the accepted arena placement and simulation contract.

Supply neutral luck of zero and no player entity, victim, killer, damage source, tool, block entity, or other invented gameplay subject. Player gear, luck attributes, life state, connection state, and claim location do not implicitly change the loot roll. An author can select recipients using supported conditions and give different entries different tables; a richer loot context needs a separate supported capability.

Validate the table and its complete known dependency graph against the inputs actually provided. A compatible context label alone is insufficient. Initially permit only verified generation operations that return supported item data without changing the world, player state, or unrelated saved data. Reject dependencies requiring unavailable entities or fields, and reject unverified custom conditions/functions. Native operations such as exploration-map generation need a separate side-effect and recovery adapter before being allowed here. Validation belongs on the server as well as in the editor. ASVS 2.1.1, 2.1.2, 2.2.1, and 2.2.2.

Supported world-dependent conditions read the retained arena's current simulation state at generation, such as a verified time or weather condition. Their result is captured by generating now; claims never reevaluate that world state. An offline recipient is a retained identity receiving an allocation, not a fabricated live player used to satisfy a table parameter.

### Randomness, dependencies, and required failures

Use an isolated server-owned random stream for each attempt/reward-entry/recipient allocation. Do not advance the level's general random stream or a table's shared native `random_sequence` to generate these allocations. The adapter must supply a real explicit random source or a valid overriding seed under the verified native API; a sentinel value that means no override is not a private stream. Keep generation state out of ordinary client requests and public inspection.

Retain the final generated item properties and XP amount. A stored seed alone cannot reproduce world-dependent conditions or changed external definitions and is not a substitute for the contents. Processing another recipient, changing declaration traversal, opening a menu, or retrying delivery must not consume another allocation's random stream.

Q148's external-dependency protection remains active through generation. Once an allocation has verified final contents, fulfilling it must not require rerunning its former loot table. Retain the item data and resources still needed to fulfill it, including Q232's appearance archive. Every custom appearance referenced by a real reward stack must be archived before that stack can reference it, whether its presentation is optional or required. Unsupported or unbounded dynamic appearance dependencies cannot evade that prerequisite through a loot function.

Generation is required work before immutable success commitment, not a terminal presentation action after success. Unsupported output, invalid item data, missing required dependencies, or an unhandled generation error follows Q69 and cannot become a successful empty award. Generation grants no native inventory items or XP. Successful qualification and generation become actual payable allocations only through Q254's accepted durable completion contract; interrupted preparation cannot be presented as an earned reward.

[Q254-Q255](durable-completion-and-reward-storage.md) accept joining confirmed success with its qualified recipients and final contents, recovery at each interruption point, and bounded storage admission. This contract does not choose a database or claim that a native inventory mutation and a Conclave record already share an atomic save.

## Q253: hold uncertain transfers for operator review

Accepted: if recovery cannot establish whether a recorded completion-reward transfer reached native inventory or XP, put that affected transfer in **Needs review**. Preserve its evidence and recorded contents. Do not automatically repeat it, erase the outstanding record, or treat uncertainty as proof of payment.

| Established state | Behavior |
| --- | --- |
| Confirmed not delivered | Keep that amount pending under Q251's ordinary delivery opportunities. |
| Confirmed delivered | Preserve the receipt and exclude that amount from future claims. |
| Delivery uncertain | Hold the affected amount for operator review; do not attempt another native mutation automatically. |

This policy applies to delivery from an existing confirmed payable allocation. It cannot reconstruct a missing award from the latest YAML, infer encounter success after a crash, reroll lost contents, convert an ordinary Test preview to a payable record, or manufacture rewards without a confirmed allocation. Q254 defines durable outcome and allocation recovery separately from native-transfer review.

Track the smallest supported transfer whose outcome is uncertain. An item remainder or XP transfer can be held independently of amounts already confirmed delivered or still known to be pending. If evidence cannot separate the amounts safely, hold the affected transfer as a whole. Other proven records may continue normally. A held transfer does not retroactively change a committed encounter result. Claim all skips held transfers and shows the reason; it does not bypass the hold.

### In-game resolution

Expose the affected recipient, encounter/reward identity, recorded item or XP amount, known transfer state, and available evidence in authorized reward inspection. The player sees a concise Needs review message without private server internals. A GM can use existing inspection authority to diagnose the record, but only server-operator authority may resolve delivery uncertainty. ASVS 8.2.1, 8.2.2, 8.2.3, 8.3.1, and 16.5.1.

Provide two explicit resolutions for an existing uncertain transfer:

- **Record as delivered** settles the held amount without adding or removing native items or XP.
- **Return to pending** makes that exact recorded amount claimable again through normal delivery. Show that choosing this can duplicate items or XP if the earlier transfer actually succeeded. It does not immediately perform another native grant.

Require a reason and show the exact affected amount before applying either resolution. Do not permit editing the recipient, changing the contents, increasing the amount, reviving a known-paid record, or resolving a Test preview into payment. This is repair of uncertain accounting, not a general grant or compensation command.

Check current authority and record state on the server when committing the resolution. An old inspection screen, repeated request, concurrent claim, or second operator must not resolve the same held transfer twice or act on superseded state. Apply revocation immediately. ASVS 2.3.1, 2.3.4, 8.3.2, 15.4.1, and 15.4.2.

Record the operator, UTC time, reason, record identity, previous state, and resulting state in the existing privileged-operation audit. Keep detailed reward evidence restricted to authorized inspection instead of broadcasting it to participants. ASVS 16.2.1, 16.2.2, 16.2.5, 16.3.2, and 16.3.3.

### Limits of recovery

Do not scan nearby dropped items or count matching stacks to prove delivery. Items can have moved, merged, been used, or been lost, and XP balances can change independently. Do not restore a saved inventory snapshot, reclaim another player's items, or undo normal Minecraft gameplay to make the records agree. Q233's ordinary stack identity and Q120's recovery constraints remain in force.

Needs review is the fallback when verified reconciliation cannot decide. It is not a reason to abandon recoverable evidence or mark every ordinary restart uncertain. The implementation still needs a tested protocol for native saves, transfer receipts, partial progress, and interrupted record writes. Only confirmed evidence may clear the hold automatically. ASVS 16.5.2 and 16.5.3.

These selected ASVS requirements describe the accepted validation, authority, and concurrency behavior. They do not claim a complete ASVS conformance level or an implemented cross-save transaction. Q254-Q255 accept Conclave commitment ordering, admission, and record retention. Numeric budgets, the verified storage/native-save protocol, and independent-backup reconciliation remain open.

## Related contracts and remaining work

Q252 chooses generation time, independent recipient rolls, and the initial loot context. Q253 independently chooses how to handle an uncertain native transfer and who may resolve it, regardless of whether its items were fixed or generated by a table.

These contracts extend [Q248-Q249 reward declarations and Test policy](completion-rewards-and-test-policy.md), [Q250-Q251 qualification and delivery](reward-recipients-and-delivery.md), [Q69 required-work errors](execution-and-errors.md#q69-failure-categories-and-bounded-work), [Q120 interrupted recovery](cleanup-and-restart.md#q120-interrupted-attempts-after-a-server-restart), [Q148 external data reloads](combat-rules-and-health.md#q148-external-gameplay-data-reloads), and [retained appearance and stack identity](durable-item-appearances.md).

[Native loot and save research](completion-loot-research.md) establishes the available context inputs, caller-owned randomness, validation limits, and separate native player saves. It does not prove a completed Conclave adapter or crash-safe transaction. Q254-Q255 accept durable success/allocation commitment, capacity admission, and retention. Q256-Q257 accept fixed item/XP authoring and explicit enchantments. Additional typed item properties, numeric limits, storage implementation, and verified native delivery/save reconciliation remain dependent work. No grant, automatic replay, repair command, or live test is implemented here.

[Q254-Q255](durable-completion-and-reward-storage.md) define the accepted durable-completion and storage contracts.
