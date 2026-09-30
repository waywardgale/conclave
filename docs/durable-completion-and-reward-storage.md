# Durable completion and reward storage

Status: Q254-Q255 are accepted and define the durable success boundary and storage admission/retention. They extend Q248-Q253 completion rewards, Test behavior, qualification, private delivery, generation, and review of uncertain transfers. No storage or native delivery implementation exists.

## Q254: save success and its rewards before announcing victory

Accepted: freeze the successful gameplay decision, then durably commit that success and every payable allocation together in Conclave storage before announcing victory, emitting attempt `completed`, or granting completion items or XP. A successful attempt with no payable rewards uses the same outcome boundary with an empty allocation set.

### Freeze the gameplay decision

First finish Q247's same-tick arbitration, including ready child-result reactions, eligible revival, remaining grave expirations, and the encounter's success-versus-failure precedence. A phase selecting `complete` alone is not yet attempt success. Required qualification and generation under Q250/Q252 must succeed against the final decision state before submitting the completion record.

Freeze the gameplay end time, qualified recipients, final generated contents, attempt and revision identities, and captured Test payout mode. Later inventory changes, disconnections, native deaths, publication, or world time must not change that prepared decision or reroll its contents. No allocation is claimable yet. Required generation failure remains Q69's technical interruption, not an empty successful award.

This refines Q245's successful attempt `elapsed` to end at the frozen gameplay decision, excluding subsequent storage wait; actual activation, startup waits, and phase transitions still count. Q250's qualification snapshot is frozen at that same decision and becomes earned only on durable commitment. Phase/mechanic elapsed values and ordinary failure timing remain unchanged.

Enter a bounded built-in **Finishing encounter** state while saving. This is operational state, not another authored phase. Close ordinary attempt reactions, input, schedules, and pending gameplay continuations; do not run more Conclave gameplay or reopen the outcome. Continue ordinary Minecraft simulation and input. There is no server-wide pause, temporary immunity, player confinement, or change to native physics.

### One Conclave commitment

Write success and all qualified allocations with their exact final item properties and XP amounts as one durable Conclave transaction. Persist sufficient versioned identity and provenance to inspect and fulfill the records without rereading mutable YAML or regenerating loot. Do not expose a successful outcome with only some recipients recorded, or expose claimable allocations without the corresponding successful outcome. Give this operation a stable identity so retries resolve the same decision rather than create another completion. ASVS 2.3.1, 2.3.3, 2.3.4, 15.4.1, and 15.4.2.

The transaction covers Conclave's own outcome and allocation records. Native inventory, XP, player saves, world saves, and appearance archival remain separate operations with their accepted ordering and recovery requirements. Q253 still governs an uncertain native transfer. A receipt for allocation creation is not proof that Minecraft received an item.

Ordinary Test records its output as nonpayable inspection data, with no pending production reward. Real-payout Test uses ordinary payable allocation rules. A restart, publication, or change to the next Test's mode cannot convert inspection data into payment.

### A bounded live wait

Do storage I/O without blocking the server tick thread. Retain only the bounded context needed to finish the operation and, on prompt success, run Q245's optional final presentation. Storage latency does not advance the closed attempt's gameplay clock. Its ordinary world cleanup and participant recovery remain required.

Once the write is confirmed, make the outcome and allocations visible together, run eligible attempt final presentation once if its live context remains available, then finish teardown and recovery. Private automatic delivery follows Q251 and waits for the recipient's recovery and eligibility. Phase history remains committed independently; an already-dispatched phase final cue is not replayed or undone by an attempt-save failure.

Use a bounded operational timeout, with its numeric default established alongside the measured runtime limits. While waiting, retain existing arena/chunk ownership only for the still-required finish and cleanup work. Do not keep the arena, entities, cameras, or players waiting indefinitely for storage. On timeout or server interruption, discard undispatched authored final presentation and perform safe cleanup/recovery. Release exclusive resources when that work finishes under Q101/Q107. Pending record reconciliation retains data, not a permanently occupied arena.

### Failure and recovery

| Established storage outcome | Behavior |
| --- | --- |
| Success and allocations durably committed | Keep success and those exact allocations, even if the acknowledgment or cosmetic victory cue was lost. Resume permitted delivery opportunities after recovery; do not replay gameplay or final presentation. |
| Completion definitively not committed | End as a technical interruption with no completion award. Clean up and recover participants; do not resume the old encounter or reroll its preparation. |
| Commit outcome cannot yet be established | Show a built-in result-pending diagnostic and reconcile the original operation. Expose no new claimable reward, emit no authored terminal rule, and do not guess success or failure. |

A timeout, canceled waiter, unavailable store, or read error is not proof that a submitted write failed. Resolve the original operation using authoritative recovery information. An absent record is definitive only after the storage protocol establishes that no original or late write can still commit. Do not submit a second distinct completion, contradict a later valid commitment, or interpret a preparation record alone as proof of earned rewards.

An administrative stop or shutdown during this wait stops live work and triggers cleanup; it cannot revoke an already committed or still-uncertain successful write. After restart, recover a confirmed committed outcome and existing allocations. Preparation that never committed remains an interrupted attempt under Q120. Recovery does not generate missing rewards, resume the fight, or run success/failure presentation. A concise built-in status update can report newly established storage state without impersonating the authored final pass.

Once success is durable, a later claim error, full inventory, uncertain native transfer, or cleanup conflict cannot retroactively turn it into a wipe. Surface operational failures through restricted diagnostics and retain recoverable evidence. ASVS 16.2.1, 16.2.2, 16.2.5, 16.5.1, 16.5.2, and 16.5.3.

## Q255: reserve reward storage before starting and preserve unpaid rewards

Accepted: reserve bounded storage for every potentially qualified participant before activating an attempt. Refuse new starts that cannot be accommodated. Preserve pending and Needs review rewards without age-based expiry; compact settled history separately.

This admission and retention policy applies to the already accepted pending-reward capability independently of the exact success-commit mechanism accepted in Q254. It does not choose a database, journal format, or disk-space guarantee.

### Admission and limits

Validate a defensible worst-case item count, XP range, serialized payload size, and bookkeeping cost for each supported reward definition. Include supported nested tables, functions, item components, retained display metadata, and protocol overhead. A finite table name or context label alone does not establish a bounded output. Reject a dependency whose relevant bounds cannot be established under the supported profile; do not promise unlimited arbitrary native loot functions. ASVS 2.1.1, 2.1.2, 2.1.3, and 2.2.1.

Publication and Test validation calculate bounds but do not reserve space for every published encounter. Actual start preparation reserves against the captured roster, all reward entries that could apply, and their verified maximum outputs. Conditions may change during play, so current recipient matches cannot justify reserving only for today's matches. Reserve before `started` and release failed preparation safely.

Apply server-wide, per-player outstanding-record, and per-attempt payload budgets. Include the outcome/allocation records and the bounded receipt, partial-delivery, and review information required to finish their supported lifecycle. Coordinate concurrent starts and storage updates so they cannot each consume the same remaining capacity. Preserve independently reserved cleanup, recovery, and safe record-maintenance capacity. ASVS 2.3.4, 15.4.1, 15.4.2, and 15.4.3.

Recheck actual generated output against its admitted bound. An adapter exceeding that bound is a required-work error before success, not permission to truncate the award, reroll it, drop it into the world, or discard another player's records. Capacity reservation prevents known logical overcommitment; disk failure or corruption still follows the explicit storage-error contract.

Transfer the reservation to the resulting retained records when appropriate. Release unused space only after the originating operation's outcome is authoritative. An unresolved possible commitment keeps enough accounting for its result even after world cleanup has released the arena. Claims and review resolution must be able to use reserved bookkeeping capacity to reduce existing obligations when new admissions are blocked.

Expose used and reserved capacity, pending and held records, compacted history, and the reason for a refused start in operator administration inside Minecraft. Operators can adjust operational budgets within actual available capacity. Lowering a limit does not erase records or invalidate an existing reservation; it prevents new admission until usage permits it. Numeric capacity defaults require measurement rather than arbitrary author-facing YAML settings.

Real-payout Tests reserve the same storage as ordinary attempts. Inspection-only Test output uses its own bounded diagnostic retention and never reserves a claimable debt. Native public NPC drops remain under their own payout policy; this contract does not turn them into private completion records.

### Retention by record state

Pending and Needs review contents do not automatically expire. Offline absence, revision pruning, the age of an encounter, and pressure to admit another attempt cannot delete an earned outstanding allocation. Keep all evidence needed for delivery or review, including known partial progress and any relevant operator resolution.

For a fully settled allocation, retain detailed paid history for **30 days after final settlement by default**, configurable by the operator. After that interval, compact only data no longer needed by outstanding operations or review. Retain a minimal durable receipt carrying the allocation and transfer identities, settled states, and necessary resolution/audit references so stale claims or recovery cannot recreate a payment. Keep required privileged-operation audit evidence under its own retention contract; compaction is not an audit bypass. ASVS 14.1.1, 14.1.2, 14.2.4, 14.2.6, and 14.2.7.

Initially retain these compact replay-prevention receipts conservatively. Count them against storage budgets and show their growth. This is bounded admission with protected existing obligations, not unlimited storage. A later safe receipt-retirement protocol would need its own proven recovery boundary; nearby item scans, a current inventory count, or recent catalog history cannot supply it.

The personal Rewards view retains full outstanding information and the configured recent paid detail. Compacted history must not appear as a new claimable entry or imply that all historical item descriptions remain available forever. Restrict each player's records to that player and authorized administration under Q251/Q253. Storage settings and privileged changes retain server-side authority checks and audit. ASVS 8.2.1, 8.2.2, 8.2.3, 8.3.1, 8.3.2, and 16.3.3.

Keep sufficient self-contained item data and provenance rather than indefinitely retaining an entire old catalog merely because one reward remains pending. Retain old definitions only while a real consumer still needs them under Q72. Q232's permanent appearance archive is separate: settling a reward or compacting its receipt never proves the corresponding native item or its appearance is unused.

## Related contracts and remaining work

[ADR-0023](adr/0023-durable-success-before-reward-delivery.md) records why saved success precedes delivery while native player saves retain a separate recovery boundary.

These contracts extend [Q248-Q249 completion rewards and Test policy](completion-rewards-and-test-policy.md), [Q250-Q251 qualification and delivery](reward-recipients-and-delivery.md), and [Q252-Q253 generation and uncertain transfers](reward-generation-and-review.md). They reconcile those capabilities with [Q245 final presentation](ending-scope-presentation.md), [Q246-Q247 simulation and outcome order](simulation-stages-and-outcomes.md), [Q69 bounded required work](execution-and-errors.md#q69-failure-categories-and-bounded-work), [Q120 interrupted recovery](cleanup-and-restart.md#q120-interrupted-attempts-after-a-server-restart), [arena admission and release](arena-placement-and-admission.md), and [retained appearance admission](durable-item-appearances.md).

[Native source research](completion-loot-research.md) establishes that native inventory/XP mutations and player saves are separate from any Conclave store. It does not establish a shared transaction, a completed adapter, or crash-test results. The selected ASVS references describe accepted requirements, not an achieved conformance level.

Additional typed item properties, supported loot-operation bounds, numeric operational limits, storage implementation, native delivery reconciliation, migrations, and coordinated backup/restore remain later work. Restoring unrelated world/player/store backups independently is not made safe merely by retaining receipts. These contracts add no command execution, general grant action, forced inventory rollback, or requirement for an external CLI.

[Q256-Q257](fixed-reward-items-and-enchantments.md) accept fixed item/XP payloads and explicit enchantments; they retain these storage and recovery contracts.

[Q276](coordinated-backup-and-restore.md) accepts the consistent backup boundary and complete-set restore required to keep native player data and Conclave accounting matched. Recorded pending and uncertain obligations remain intact; storage upgrades are a subsequent decision.
