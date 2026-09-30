# Coordinated backup and restore

Status: Q276 is accepted. Consistent recovery is required by the accepted durable outcome, native payout, ownership and permanent item-resource contracts. This accepts a supported in-game maintenance workflow; it does not claim that a backup coordinator exists.

## Q276: make complete backups after closing the world

Accepted: provide operator-only Back up and stop and Restore backup and stop actions in Minecraft administration. Stop the world for a consistent backup or replacement, then require the normal server startup. Keep content Publish and Upgrade draft available without this downtime; those operations do not replace native world state.

[Native source research](backup-recovery-research.md) found separate player, world and mod-store save boundaries. Save/flush, tick freeze, save-off and the native ZIP method do not establish a coordinated live snapshot. A closed world with completed writes and verified exclusive access provides a boundary that can be tested. The initial workflow therefore promises a stopped-world backup rather than uninterrupted play or an in-process dedicated-server restart.

### Request and quiesce

The operator selects the operation and, for restore, an existing server-managed backup. Show the world, creation time, recorded versions, included data, required capacity and expected stop/restart. Restore also shows that the selected snapshot will replace later world progress, player inventories/XP and Conclave records. Require an explicit confirmation bound to that selection and current authority. GM status alone grants neither operation.

After an accepted maintenance request, block new attempt admission, including Test, and show the queued operation. Let existing attempts finish by default; the operator may use already accepted administrative stop separately. Do not wipe, force success or restart an attempt as part of backup. A request waiting for attempts can be canceled before shutdown starts. Recheck current operator authority immediately before committing to shutdown; revocation cancels an uncommitted request and releases its admission hold. Once replacement has begun, finish its recorded safety/recovery protocol even if the requesting identity later loses authority. If an unresolved operation cannot reach a supported safe persistence boundary, report the blocker rather than bypassing it.

Settled operations here means safely persisted and quiescent, not that all gameplay lifecycles have ended or every reward has been paid. A complete set can preserve offline graves, player-owned auras, pending rewards and already-recorded Needs review transfers. Do not resolve those records merely to take a backup. An outstanding write whose result cannot be established or safely retained still requires the accepted recovery boundary.

Once attempts and required operations are settled, disconnect players through normal shutdown, stop new writes and drain/close the participating stores and world. Obtain and retain verified exclusive access throughout copying or replacement, including the native world-lock handoff. A normal automatic server restart must wait or fail safely while maintenance holds that access. A lifecycle callback alone does not establish the lock or prove all writes completed.

Detect required save/close failures, including native paths that log rather than propagate failures. Do not mark a backup complete after such a failure. A restart can show the failed operation and retained evidence if startup remains safe. The implementation must prove these boundaries through interruption and write-failure tests before advertising the operation as supported.

### Included data and completion

Capture one coordinated set containing the complete selected Minecraft world and player data plus all Conclave data needed to interpret it:

- Drafts, published content, global gameplay settings, revision identities and required captured definitions.
- Owned world changes, interrupted-operation/recovery records, graves, retained participant recovery and player-owned auras.
- Durable outcomes, exact reward allocations, pending/uncertain transfers, receipts and allocation-review evidence.
- Retained real-item appearances, translations, fonts and their dependencies, including resources stored outside the native world folder.

The coordinator uses an explicit registered inventory of required stores and writers. Copying only the native world ZIP or only a Conclave database is insufficient. Installed integrations with required Conclave-owned storage must supply a supported maintenance contract; otherwise refuse to label the result a complete Conclave backup. Arbitrary external mod databases and hosting services are outside this guarantee and must be identified as excluded where known.

Record snapshot identity, world identity, creation time, installed compatibility requirements, storage/content formats, included paths and integrity digests in backup metadata. Use server-owned bounded paths and staging. An incomplete or unverifiable copy remains incomplete and cannot be selected for normal restore. Verify the final set before making it available. Do not silently delete older backups to make space; expose capacity and explicit operator deletion in the same Minecraft screen, protecting any backup needed by a pending operation.

Executable mod jars are not installed or rolled back by this workflow. Record the required versions so incompatibility can be diagnosed. Retain the current server's operator identities, GM grants, credentials, access controls and local audit history independently of world replacement. Restoring a gameplay backup must not restore revoked authority. Preserve allocation-review evidence inside its accounting snapshot and append the restore operation to the current administrative audit.

### Restore a whole matching set

Preflight the selected backup's integrity, world identity, completeness, installed code/storage compatibility and replacement capacity before shutdown or mutation. Refuse an unsupported version rather than discarding unknown records or trying an automatic downgrade. A valid older snapshot can be restored only as a complete compatible set. Content-only rollback remains the separate accepted operation for future attempts.

Before replacement, create and verify a complete backup of the current safely closed set. If current state cannot be read or closed consistently, refuse the ordinary in-game restore path and retain it for explicit host recovery. Do not pretend that an arbitrary forensic copy is a verified pre-restore backup.

Stage replacement data before touching the originals. Keep a durable maintenance journal outside the replaced set and exclusive access through replacement. A multi-file restore is not assumed atomic. An interrupted restore must be detected before ordinary world admission and either finish a verifiable original operation or remain blocked with both source sets preserved. Never start a world assembled from whichever old/new files happen to exist.

On successful replacement, normal startup validates and reconciles the restored set using Q273. Q120 still ends interrupted attempts rather than resuming them. Stored pending and uncertain obligations retain their original identities and review state. Do not reroll rewards, reconstruct payments from inventory contents, merge current player files with old receipts, replay terminal presentation or invent compensation for progress removed by the explicit world restore.

### Operational limits

All ordinary selection, confirmation, capacity inspection, backup listing and deletion are available inside Minecraft. No companion CLI is required. The actual dedicated-server process must still be started through its normal host/server lifecycle after maintenance; Conclave does not manage its own executable installation or promise in-process world restart. An integrated server can use the normal return-to-menu and open-world flow.

If required code or unknown global ownership prevents the world from opening at all, an in-game screen cannot repair that unavailable server. Q273's exceptional host recovery boundary remains honest: retain files and diagnostics for restoration of a verified set under exclusive access. This contract adds no force-start button, arbitrary filesystem browser, cloud-backup service, automatic schedule or cross-server replication system.

Q277 accepts a separately reviewed storage-upgrade workflow built on this consistent backup boundary, with staged conversion and an explicit rollback policy. Content draft upgrades under Q272 do not migrate durable stores.

Related contracts: [startup repair and recovery](content-upgrades-and-startup-recovery.md), [interrupted attempt cleanup](cleanup-and-restart.md), [durable outcomes and rewards](durable-completion-and-reward-storage.md), [uncertain native transfer review](reward-generation-and-review.md), [retained item resources](durable-item-appearances.md), and [client/API compatibility](code-compatibility.md).

[Q277](durable-storage-upgrades.md) accepts the dependent storage-upgrade workflow, including a concrete transition plan reviewed before installing code, staged conversion and complete-set rollback after post-migration state changes, including startup recovery.
