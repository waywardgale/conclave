# Archived appearance delivery and client cache

Status: Q234-Q236 are accepted. Q232 accepts a permanent server appearance archive; Q124 already requires consent, bounded transfer, verified application, and no resource activation during the receiving player's active attempt. These contracts settle demand selection, the application gesture, and local cache retention. No implementation exists.

## Q234: request only appearances the player can currently need

Accepted: automatically offer and stage missing archived item appearances as their references become available to that player through supported ordinary item synchronization, subject to the player's server-resource consent. Transfer each requested appearance's complete supported dependency graph, not the entire archive or current catalog.

Include the player's own synchronized inventory and equipment, the contents of a container menu they can actually open, and equipment or dropped items already tracked for that client. Include supported nested item references only when that client actually receives their containing data. Do not search unloaded chests or other players' private inventories to preload their art. A nearby position alone grants no right to inspect an item or receive its metadata.

The server determines which immutable appearance identities it can offer from its authoritative synchronization and consumer state. A client may request missing content from those offers; it cannot submit an arbitrary archive ID, path, URL, or another player's identity and obtain files. Scope offers and pending operations to the actual connection and intended consumer or preparation. Unknown or corrupt archive identities use the existing diagnostic and fallback contracts, without searching the internet, selecting the latest logical model, or revealing the whole archive catalog.

### Required consumers and bounded requests

This demand path supplements the existing resource requirements of active or preparing attempts, persistent auras, authorized previews, ongoing presentation, and unresolved recovery. Account for those consumers when building a candidate resource set. An older item does not replace a retained aura's texture, and a current revision does not overwrite an old required model.

Preflight every declared required asset for a selected next attempt, including later phases and reachable definitions, before that attempt starts. Do not defer required encounter content until the player happens to see it. Ordinary item demand can tolerate its accepted temporary base-model view; a fallback cannot satisfy required presentation or readiness.

Coalesce duplicate offers, transfer requests, and shared dependencies by their immutable identities. Use bounded queues, aggregate sizes, file counts, expansion, bandwidth, and concurrent-transfer limits. Reuse verified cached content when it matches the offered graph; possession of bytes does not establish current consent, permission, or successful application. Transfer only supported asset data and necessary resolution metadata, not unrelated manifests, server paths, inventory contents, or private encounter state.

Respect the existing server-resource consent choice. A refusal prevents staging under that choice and leaves ordinary item use available with the accepted base-model rendering. Show one coherent assets status rather than another popup for every encountered item. If a decision is needed, the player can review the pending offer in Minecraft. A server cannot silently convert refusal into approval merely because an item was equipped or a ready check opened.

Verified downloads become staged content. Until the exact graph is applied, keep the actual stack unchanged and use Q231's temporary base-model fallback when necessary. In-flight or failed downloads do not make the player ready for a dependent attempt. A newer offer cannot replace another generation's expected bytes or acknowledgement.

An interrupted transfer may reuse verified complete content on retry; corrupt or unverified partial content cannot be applied. Keep attempts and backoff bounded, expose Retry in the same status view, and avoid retrying an unchanged refusal or failure on every item update. Disconnect ends the connection's offers and pending authority; reconnect establishes current offers anew. Cached art is not a reason to replay an item grant, sound, animation, or expired presentation.

## Q235: one explicit action to apply staged assets

Accepted: stage permitted content in the background and expose one coalesced pending-assets indicator with an `Apply assets` action. The player chooses when to perform an eligible resource reload. Q242 also permits ordinary Apply for an observer retained on a running attempt's roster. Do not automatically reload at each newly observed item or immediately when an attempt ends.

Show permission needed or declined, downloading, ready to apply, waiting for encounter, applying, ready, or failure with its relevant reason. Reuse the existing consent decision; the Apply gesture controls when staged resources become active and is not a substitute for consent. After a successful application, clear only the status satisfied by that exact resource-set generation. Later offers join a new pending generation.

A start or ready-check flow that needs newer assets links to this same action and explains why preparation is waiting. Applying resources does not count as answering Yes to a ready check or auto-starting an encounter. Keep the accepted bounded preparation and fresh readiness checks; a timed-out preparation releases its claims instead of waiting indefinitely for a click.

### Safe activation and retained presentation

Before beginning the native reload, recheck that the receiving player is outside an attempt or has `participation: observer` under Q242, and that the candidate is complete, verified, permitted, and within capacity. Active participants cannot use ordinary Apply; reconnecting participants use Q239-Q240's original-resource restoration path. Coordinate application admission with server start admission. Once an apply operation is admitted, do not start or rejoin an active attempt for that player until its resource state is known. Conversely, an attempt that starts first prevents that pending apply operation from beginning. Checking the client's screen or relying on an earlier idle observation is insufficient.

Do not carry an Apply request forward as an automatic reload after an active attempt finishes. Keep it pending for a fresh Apply gesture at a permitted time. For an eligible ordinary Apply, a short wait for an already-finishing Conclave sound or dialogue line may complete within the admitted operation. Native resource reload resets the sound engine, so let those finite cues finish instead of promising that they will survive the reload. A conflicting preview must be stopped or closed through its existing controls when its playback cannot be preserved. Do not discard an unsaved draft.

Show any blocking consumer in the assets status. Bound the application preparation wait; if it cannot become eligible, return to a pending state with the reason. Do not force-stop a required cue, wait forever, or apply later after that preparation was cancelled or timed out. A continuously present aura is retained data to include, not an instruction to wait until the aura expires before any resource update.

Include all still-required captured resources in the replacement set, such as persistent aura assets and ongoing compatible presentation. A newly current catalog does not authorize dropping an older live consumer. Keep logical playback state and established elapsed-time rules where the supported renderer can resume current visual state; never replay old cues merely because resources reloaded. Native audio interruption is why finite finishing audio is an activation boundary. The operation does not promise uninterrupted unrelated Minecraft audio during a resource reload.

### Completion and failure

Retain the last usable resource-set description and the data needed by the supported recovery path while applying a candidate. Invalidate readiness while the client's applied state is changing or uncertain. Only a successful completed application of the exact requested generation can establish the new acknowledgement. Successful download, a sent apply request, and a generic resource-reload completion for another generation are not enough.

If application fails, keep a clear failed status and reconcile the actual client state. Restore and acknowledge the previous compatible set only if restoration actually succeeds; do not claim transactional rollback of every renderer or another mod's callbacks. Keep ordinary world play and supported fallback available, preserve the server archive and actual items, and block dependent Conclave readiness while the required resources are unavailable. Offer an explicit bounded retry after the cause is addressed. Applying assets neither publishes gameplay content nor migrates an active attempt.

## Q236: reclaim only unused local cache content

Accepted: use a bounded local asset cache with automatic least-recently-used eviction of eligible inactive content. Keep the permanent server archive separate. A player may clear unused local downloads in Minecraft, but that operation cannot delete server-retained appearances or remove resources from a live applied set.

Protect the current applied resource set, resources still needed by retained consumers, admitted preparation/application sets, in-progress transfers, and data required for an ongoing restoration. Track shared dependencies before deleting a blob: one retired graph does not release content still required by another. Items and presentation that are no longer relevant can release demand references, while the current applied pack remains protected until a permitted replacement has finished using it. Ending one attempt does not by itself prove all of its former assets are unused.

Cache retention and resource activation are distinct. Deleting an unused downloaded file does not trigger a resource reload. Removing a resource from an applied pack requires the ordinary permitted replacement path. Clearing local cache does not erase native item components or invalidate immutable server appearance identities. If a later consumer needs evicted art, stage it again from a newly authorized server offer and retain the ordinary base-model fallback until application.

Calculate peak local capacity for the candidate, its dependencies, transfer staging and expansion, the still-applied set, and required recovery data. Reserve capacity across concurrent work. Evict only eligible content before admitting a new transfer or application. Never break an active attempt or required consumer merely to make room for optional newly encountered art.

If the protected sets already occupy the budget or physical storage is insufficient, defer new optional appearance downloads and show the capacity reason. Required future content remains unready; explain the shortage rather than silently omitting it. The player can clear eligible unused data, raise their configured local budget when storage permits, or finish/release the relevant consumer. A server cannot silently raise the player's disk budget. Ordinary gameplay continues with the existing applicable fallback.

Expose used storage, protected storage, reclaimable storage, and pending required space in the assets screen. State sizes from the verified supported graph and actual cache accounting; do not equate compressed transfer size with peak reload cost. Use the common bounded-resource configuration for numeric defaults and hard limits. Do not promise that a source audit establishes safe memory or disk budgets before the adapters have been measured.

Keep cache data within Conclave's owned storage. Cache clearing affects only eligible Conclave content, not arbitrary resource packs, Minecraft saves, or another server's unrelated local files. A server learns what is necessary to complete its authorized offers, not an inventory of the player's other cached archives. Server archive retention and client cache eviction have different lifetimes and must be labelled clearly in the in-game interface.

## Related contracts

These contracts extend [durable item appearances](durable-item-appearances.md), [resource-pack delivery](world-locations-and-assets.md#q124-client-assets-through-the-minecraft-workflow), [required presentation](presentation-controls-and-models.md#q137-selected-recipients-without-the-required-assets), [start preparation](encounter-activation.md), and [presentation scheduling](presentation-and-dialogue.md). They preserve next-attempt-only publication, explicit readiness, ordinary item behavior, and the accepted fallback contracts.

[Native client research](client-asset-delivery-research.md) records item synchronization, consent boundaries, reload completion, and sound interruption separately from these accepted contracts. [Q237-Q238](reconnect-admission-and-assets.md) accept the qualifying reconnect boundary and participation state. [Q239-Q240](reconnect-resource-restoration.md) accept restoring the original required resources automatically during reconnect setup, with explicit retry after world entry. This narrow restoration exception does not authorize ordinary updates or changing the active attempt's captured content.

[Q242](client-resource-failures-and-observers.md#q242-ordinary-apply-for-an-observation-only-participant) accepts ordinary Apply for observers retained in a running attempt. Camera mode alone never establishes that eligibility, and successful application never restores admission.

[Q260-Q261](durable-item-localization-and-fonts.md) apply these same consent, authorized demand, Apply, and cache rules to durable item translation and font resources. Their accepted consumer fallbacks show the captured default text or ordinary font without mutating the actual item or satisfying separately required resources.
