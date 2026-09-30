# Durable storage upgrades

Status: Q277 is accepted. Q272 separates content conversion from durable storage; Q273 governs unsafe startup; Q276 accepts a complete stopped-world backup and restore boundary. No storage engine, migrator or upgrade coordinator exists.

## Q277: approve a specific storage upgrade before replacing code

Accepted: version durable storage independently of authored manifests and the public Kotlin API. Offer Prepare storage upgrade in Minecraft for supported release transitions. Review a concrete target plan under the working release, close and back up the source set, then let the installed target release convert staged data before admitting the world. Preserve the original set and refuse automatic downgrades.

### A concrete plan under the working release

Distribute a bounded declarative upgrade plan with a release that changes storage. Import it through Minecraft's administration interface. The plan names the target Conclave release/build and participating provider identities, supported source/target storage versions, platform requirements, affected stores, preservation guarantees and expected capacity needs. It contains no executable code, arbitrary file paths or authored migration instructions. Its format is a small operational contract readable by the preparing release, not another encounter manifest authors must maintain.

The old release can inspect this metadata without loading future code or pretending it knows how to run the future converter. The new installed release must independently verify that its trusted converter registrations, code identities and actual source versions match the approved plan. A misleading, incomplete or stale plan cannot authorize a different conversion. If the old release cannot understand the preparation contract, require a documented compatible intermediate release instead of approving an arbitrary future migration.

The operator reviews the target, included stores, required downtime, backup, and rollback limits in Minecraft. Prepare storage upgrade requires operator authority, records one stable operation identity, and follows Q276's admission hold, wait-for-attempts, authority recheck, save-error detection and exclusive closure. It creates and verifies the complete source backup before marking the preparation ready. Preserve recorded pending/Needs review rewards and offline lifecycles without requiring them to settle into a different gameplay state.

Bind the durable authorization to that target plan, world identity and closed source set. Later changes to participating data or relevant authority invalidate an unstarted job and require fresh preparation. Retain current operator/GM authority and administrative audit separately from gameplay rollback. Any required representation conversion of that administrative state must preserve its current meaning; a world snapshot cannot reintroduce revoked privileges.

After preparation, the server stops. Install the target executable code and matching required client code through the normal mod-installation workflow, then start the server normally. Conclave neither downloads/loads a replacement JAR nor adds a companion CLI. Code updates that keep the same supported storage representation do not require this migration operation; their existing installation and restart rules remain.

### Convert before ordinary world admission

The target release reads the minimal version/maintenance metadata before opening mutable gameplay state. Verify the original preparation, backup integrity, current authority, supported version path, required providers and actual capacity. No player enters and no native world/player conversion or payout begins before this gate succeeds. Directly installing incompatible code without preparation cannot open an unsafe world just to display a confirmation screen. Preserve diagnostics and use Q273's documented host recovery or a compatible preparing release.

Run only trusted installed converters on staged copies under exclusive access. Each converter declares a supported source/target contract and bounded work. Missing providers, unsupported intermediate versions, unknown record kinds or an inability to preserve required state stop the upgrade. Never skip the failing store and run a partial combination. Validate the complete converted set and its cross-store references before selecting it for use. No open-ended promise of reading every historical format is made.

Preserve durable meaning: original operation/allocation/transfer identities, exact reward contents and paid/pending/uncertain amounts, Test nonpayability, retained definitions and policy, grave time remaining, aura sources and lifetimes, ownership/recovery obligations, and permanent resource identities. Conversion does not publish a hotfix, refresh a timer, reapply an aura, reroll loot, grant payment, invent a success, reset uncertainty or replay terminal presentation. Immutable authored history remains its original history; Q272 handles editable content upgrades separately.

Use an external maintenance journal and verifiable staged progress. Retrying the same interrupted upgrade resumes or rebuilds its staging from the same closed source rather than running gameplay effects again. Installing several files/stores is not assumed atomic. If interruption makes selection uncertain, block world admission until the journal establishes one complete supported set. Do not discard the originals, backup or failed staging needed to diagnose and recover the operation.

### Recovery and rollback

Before any new gameplay/native save mutation, a failed conversion can retain the intact source and leave the server stopped for the compatible source release. An interrupted installation follows its recorded recovery protocol. Do not silently boot the old format under the new release or delete unreadable records to make startup succeed.

Once the selected converted set or native world/player state has accepted post-migration mutations, returning to an older release requires Q276's explicit complete compatible snapshot restoration, with its loss of later progress and preserved current administrative authority. This includes startup cleanup, participant recovery or reconciliation before any player enters, not only ordinary gameplay. If the absence of such writes is uncertain, do not assume that selecting the original store alone is safe. Never restore only an old Conclave database beside newer inventories, merge receipts, or run an automatic down-converter. The required installed code must support both the restored gameplay format and current administrative state.

The initial contract covers supported Conclave/provider storage transitions on the supported Minecraft platform. A Minecraft version change may also convert native saves and requires its own verified compatibility path; this operation does not certify arbitrary native downgrades or unrelated mod databases. Exact storage choice, converter APIs, supported transition matrix and failure-injection tests remain implementation work. Any transition that cannot preserve this contract needs an explicit new decision before it is offered.

Related contracts: [content and startup recovery](content-upgrades-and-startup-recovery.md), [coordinated backup](coordinated-backup-and-restore.md), [code compatibility](code-compatibility.md), [durable completion](durable-completion-and-reward-storage.md), and [interrupted attempts](cleanup-and-restart.md).
