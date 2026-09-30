# Completion rewards and Test payouts

Status: Q248-Q249 are accepted. They add completion-reward declarations and a Test payout policy alongside Q97/Q225 native NPC death rewards. Q250-Q251 accept recipient qualification and private delivery. Q252-Q253 accept generation, the initial loot context, and uncertain-transfer review. Q254-Q255 accept durable completion, storage admission, and retention. Native reconciliation still requires a verified implementation. No reward implementation exists.

## Q248: declarative rewards for encounter success

Accepted: add an optional encounter-level `rewards` list for successful completion. Give each entry a stable `id` and optional display `name`, following the established authoring conventions. Let the engine process these declarations through a dedicated reward capability when the attempt completes successfully. Keep Q245's terminal presentation rules free of reward delivery and other gameplay mutations.

Omitting `rewards` grants nothing for completion. Reward entries belong to the attempt's captured revision. A later hotfix cannot change an earlier attempt's declared rewards. Completing an intermediate phase, failing an attempt, administrative stop/restart, technical interruption, and recovery of an unfinished attempt do not create completion rewards. A genuinely successful Test attempt follows its separately selected payout policy under Q249.

### Initial contents

Support these bounded native reward contents:

| Content | Meaning |
| --- | --- |
| Items | Explicit registered items with positive counts and supported typed item properties. Reuse the item vocabulary already needed for NPC equipment without requiring an equipment slot. |
| Experience | A nonnegative integer number of experience points, with zero granting none. |
| Loot tables | Results from a supported registered table evaluated with an explicitly documented completion context. Validate the table and its dependencies against that context. |

An entry can combine these contents, such as fixed experience plus items chosen by a table. Q250-Q251 separately accept recipient qualification and private delivery. Q252 accepts the initial `loot: {table, at}` form and context; Q256-Q257 accept fixed item/XP fields and explicit enchantments. Do not silently reuse an entity-death context or invent a killed boss, killer, damage source, or winner for an encounter-completion reward. Native NPC death drops retain Q225's separate eligibility and attribution.

Use the existing supported item-property validation, content dependency checks, and appearance archive requirements. Reward declarations do not accept raw NBT, arbitrary executable item data, scripts, or server commands. Economy balances, permissions, arbitrary achievements, and other third-party side effects require explicit future capabilities with their own contracts.

Define each reward entry for one processing decision per successful attempt. Ordinary repeat clears create new attempts; this initial capability adds no daily lockout, account progression, shared raid chest, or cross-server economy. Such systems cannot be inferred from an entry ID. Reconnect, later inspection, or a publication change does not create a new completion or a second reward decision.

### Responsibility and dependent choices

Keep completion rewards outside ordinary event action lists. Authors should not need to keep an ended encounter alive, enqueue a late gameplay continuation, or write a `give_item` loop to describe victory rewards. This contract adds no general grant action for arbitrary phases or mechanic events.

Q250-Q251 accept recipient qualification at success and private delivery with pending rewards for offline or capacity-limited recipients. Q252 defines generation and the initial loot-table context, while Q253 defines operator review of uncertain transfers. Q254-Q255 accept durable completion, storage admission, and retention; verified reconciliation with native player and world saves remains implementation work. No lossless or exactly-once native delivery, general retry policy, or reward-reissue command is implied before those contracts are settled and verified.

Those delivery decisions must preserve Q120's prohibition on inventing or replaying a successful encounter during restart recovery. They must also distinguish a recorded reward still owed from an already-delivered item. The appearance archive retains art, not proof that an item was owed or delivered, and must never recreate an item by itself.

## Q249: inspect rewards by default during Test

Accepted: Test attempts suppress actual delivery through Conclave's supported reward channels by default. Show authorized authors what those channels would produce and why, while retaining the same supported eligibility, content validation, and captured definitions. This policy applies independently to the already-accepted NPC death rewards and the accepted completion-reward capability.

Cover Q225's configured NPC loot, equipment death drops, and XP, plus Q248 completion rewards. Record the supported output produced by that Test operation rather than repeatedly rolling a random table when an author opens inspection. Actual native administrative kills inside the Test follow the same Test payout policy. Ordinary cleanup, conversion, unsupported non-death removals, and failed spawns still produce no invented reward operation.

Test inspection grants no items, XP, or outstanding production reward balance. It does not schedule an automatic payment when the Test finishes, when the author publishes the draft, or when someone later enables real rewards. Inspecting a prior result cannot reroll or pay it. Keep participant-private reward information under the existing inspection and disclosure permissions.

### Testing actual delivery

Provide an operator-only **Deliver real rewards** switch when launching a Test. Show the selected mode in the launch view and the resulting Test status. Capture it for that Test, and default it off for every new Test or Restart test. A GM without operator authority can inspect reward behavior but cannot enable this payout mode through a draft or client request.

With real delivery enabled, supported reward channels use their normal payout and recovery contracts. Test items and XP delivered this way are real, with ordinary native consequences. The switch permits testing reward delivery; it does not enable unsupported native adapters, bypass required resource checks, or turn an interrupted Test into a success.

This switch is administrative Test state, not an encounter YAML setting or a global change to Minecraft gamerules. It requires the existing server-side authority check and audit record for privileged operations. Exact command syntax and screen placement can follow the established in-game authoring workflow.

### Boundary of the default

Test still runs in a real arena under Q77. Suppressing supported reward payouts does not restore inventories, prevent unrelated world drops, intercept every native trade or equipment transfer, or refund consumed items. NPC equipment remains real. Q232 requires appearance archival before any real custom-model equipment stack is materialized, including Test equipment with death drops disabled; archival is not conditional on proving that the item can leave its NPC.

The native adapter must intercept and inspect each supported reward channel before actual payout. It cannot deliver the reward first and then delete nearby items or subtract player XP to simulate suppression. If a required adapter cannot honor this mode, report that incompatibility under the existing validation/error policy. Do not quietly deliver a reward while labelling the Test as inspection-only.

## Related contracts and remaining work

Q248 chooses the authoring entry point and initial completion-reward contents. Q249 independently chooses payout behavior for Test attempts, including native NPC death rewards that already exist in the design.

These contracts extend [Q225 NPC death rewards](npc-death-rewards-and-descendant-events.md#q225-loot-equipment-drops-and-experience), [Q77 Test draft](in-game-authoring.md#q77-trying-a-draft-before-publication), and [Q245 final presentation](ending-scope-presentation.md). They retain [typed equipment](npc-stats-and-combat.md#q144-typed-equipment), [retained appearances](durable-item-appearances.md), [Q120 restart recovery](cleanup-and-restart.md#q120-interrupted-attempts-after-a-server-restart), and [administrative authority](game-master-commands.md#authority).

Q254-Q255 accept durable completion, storage admission, and retention. Native reconciliation, numeric capacity limits, and additional typed item properties remain dependent work for the accepted completion-reward capability. No item grant, reward store, Test suppression adapter, or native delivery guarantee is implemented by this design round.

[Q250-Q251](reward-recipients-and-delivery.md) accept recipient qualification and private delivery with pending rewards.

[Q252-Q253](reward-generation-and-review.md) accept reward generation and uncertain-transfer review, with the supported loot context grounded in [native research](completion-loot-research.md).

[Q254-Q255](durable-completion-and-reward-storage.md) accept the durable-success boundary and reward storage admission/retention.

[Q256-Q257](fixed-reward-items-and-enchantments.md) accept fixed item/XP fields and a shared explicit-enchantment format.
