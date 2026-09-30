# Cleanup and restart recovery

Status: Q119-Q120 are accepted. Attempt-owned cleanup, ordinary Minecraft world behavior, pinned definitions, safe recovery, and bounded chunk ownership are already accepted. These contracts define what to do when normal world changes or an interrupted server prevent a simple cleanup; no persistence layer or recovery implementation exists.

## Q119: restoring blocks after ordinary world changes

Accepted: restore a temporary Conclave block edit only while Conclave still owns the current change. Record the original supported block state and the state written by the action. If a player or unrelated system subsequently changes that position, preserve the later change and report a cleanup conflict to the GM. Do not overwrite it merely because it lies within the arena or once belonged to an attempt.

Check both expected state and ownership history when available. A position changing away from and back to the same block value does not prove Conclave still owns the latest edit. An observed unrelated write invalidates the old restoration claim, even if the resulting block value happens to match. An unverified ownership history after interruption must not be treated as proof of ownership.

For successive Conclave edits to the same position, keep ordered ownership so cleanup cannot resurrect a change belonging to an already-ended scope or undo another still-active scope's change. Once an unrelated world edit interrupts that chain, older cleanup cannot cross it. A later explicit Conclave edit captures its own new baseline from the then-current world.

Preserve ordinary drops, inventory changes, and other consequences of normal Minecraft interactions. Restoration is not an inventory snapshot, refund, replay of drops, or whole-arena rollback. Do not duplicate block contents by restoring a saved container into a changed world. Initially support only block edits whose complete restoration semantics are verified; inventory-bearing blocks, block entities, and multi-block structures need explicit adapters before being accepted by the editor or schema.

Bound edited positions and cleanup work. Show unresolved positions and their reasons in the in-game diagnostics, with explicit GM repair choices. A cleanup conflict does not retroactively change a completed encounter into a gameplay wipe or apply punishment. Whether a particular unresolved position prevents a later start follows the encounter's explicit required-world checks and safe-placement checks; ordinary player presence is not an obstruction policy.

This preserves the user's Q103 decision that people can mine, build, and play normally. It does not reintroduce build protection or claim that arbitrary mod writes already expose enough ownership information. Required hooks, supported block adapters, and interruption reconciliation need implementation verification.

## Q120: interrupted attempts after a server restart

Accepted: do not resume an in-progress encounter in v1 after the server restarts or crashes. Treat it as interrupted, perform recoverable cleanup, and finish participant recovery. Do not replay success, wipe punishment, a boss death, item drops, or XP as part of that recovery. A future attempt starts fresh on its selected complete revision.

Persist the minimum recovery information needed to identify the attempt, its pinned revision, owned entities and effects, supported block edits, participant camera/game-mode changes, and recovery still owed. Retain relevant definitions while recovery still needs them. Use stable identities and explicit operation state so repeated recovery does not create duplicate graves, entities, relics, or rewards. This is an idempotent recovery requirement, not a claim of an atomic transaction with all Minecraft world saves.

On clean shutdown, stop and clean up attempts while the server can still perform those operations. On an unclean interruption, reconcile retained Conclave records with loaded world and player state before allowing a conflicting new attempt. Remove only resources whose Conclave ownership is established. Do not delete unrelated entities based on position or type, and do not infer that an unavailable required NPC was defeated. Native status effects handed to Minecraft under [Q158](aura-effects.md#q158-native-status-effects-as-explicit-actions) have no removable Conclave source ownership; recovery must not clear them or replay their application. Reconcile owned aura modifiers from their authoritative lifecycles separately.

Restore Conclave-changed camera and game mode, clear attempt-owned temporary state, and complete the already accepted safe recovery. Offline participants keep a pending recovery record and receive it once when returning. If required world state is unavailable or conflicting, preserve it and show an actionable diagnostic; prevent only starts that conflict with unresolved engine ownership. Never restore an old inventory or repeat vanilla death consequences to make a record look complete.

Outside-attempt graves retain their captured revival policy and remaining simulation-time state. Downtime does not consume that timer, reset it to a fresh window, or grant an extra death or revival. Ready checks are discarded at restart so stale confirmations cannot authorize a new start. Player-owned aura persistence under Q117 follows its separate lifetime; interrupted attempt cleanup does not remove another owner's contribution.

Retain fired/rearm state for unchanged start rules sufficiently to avoid restarting an interrupted encounter merely because the server restarted. Initialize occupancy without fake entry events. Wait for world cleanup and recovery work that still requires exclusive arena ownership before allowing a fresh qualifying trigger for the affected arena. Offline pending recovery does not hold that reservation indefinitely, consistent with Q101; its player-specific obligation is checked when the player returns. Exact durable storage, ordering against world saves, detection of uncertain writes, and restart-time start-rule reconciliation remain implementation design and verification work.

This choice favors a predictable interrupted-attempt result over serializing and migrating every mechanic, coroutine, AI state, and scheduled callback. Full mid-attempt resume can be considered later as a separate capability; v1 must not silently claim partial resumption.

[Q254-Q255](durable-completion-and-reward-storage.md) accept recovery of an already committed successful outcome and its recorded completion allocations, distinct from resuming an unfinished attempt. An uncertain original completion write must be reconciled without replaying gameplay or generation; a definitively uncommitted preparation remains interrupted. Safe world cleanup and participant recovery proceed independently of unresolved reward storage, while retained accounting preserves existing obligations.

[Q273](content-upgrades-and-startup-recovery.md#q273-keep-content-repair-available-and-block-only-unsafe-operations) accepts startup behavior for invalid content, missing code, isolated retained recovery faults, and unreadable global state. Known faults remain scoped where possible; its exceptional unsafe-startup rule does not convert an individual uncertain reward transfer into global corruption.

[Q274](administrative-operation-contracts.md) accepts exact supported recovery-retry behavior. [Q276](coordinated-backup-and-restore.md) accepts complete coordinated backup and restore, preserving the interrupted-attempt policy and original accounting identities.
