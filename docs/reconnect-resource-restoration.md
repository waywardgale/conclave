# Restoring resources for a reconnect

Status: Q239-Q240 are accepted. Q237 defines successful reconnect admission; Q238 separates participation from connection and life state. These contracts choose when original resources are restored and how incomplete restoration is handled. They do not change the captured attempt revision, original grace deadline, or ordinary asset-update policy. No implementation exists.

## Q239: automatic restoration before world entry

Accepted: automatically prepare the resources required by the player's existing attempt during bounded connection configuration, under their existing server-resource consent. Reuse a compatible applied set when it can be freshly verified for this connection. Otherwise, offer the required captured graph, stage verified missing content, and apply it before native world entry where the supported integration permits.

This is a specific exception to Q235's manual ordinary Apply workflow. It restores the attempt's original resources. A newer published catalog, discovered item appearance, or pending hotfix cannot be added opportunistically. Continue to include resources needed by other retained consumers, such as persistent auras, without replacing their captured versions. Prepare the full required attempt graph, including later phases and reachable definitions. The resource set may contain compatible retained content, but readiness is checked against the exact required graph and completed resource-set generation.

The server finds the attempt from its established player identity and retained opportunity. A client cannot select another attempt or supply a revision as authority. Bind preparation, offers, application, and acknowledgement to the actual connection, player, attempt, original opportunity, and requested resource-set generation. Retained cached files alone prove neither application nor permission.

### Consent and progress

Honor approval or refusal already recorded for this server. If a decision is still needed, present it in Minecraft before staging or applying under that permission. Connecting is not permission to override a refusal. Do not use native required-pack refusal behavior to disconnect a player solely for declining Conclave resources.

Use a built-in progress view that does not require the missing custom assets. Show that the original encounter resources are being restored, the current preparation stage, and the original grace remaining. Preserve file-count, size, expansion, transfer, concurrent-work, and peak-storage limits. Give a specific reason when consent, missing content, capacity, or application prevents readiness. Do not repeatedly reopen an unchanged consent prompt.

The existing native configuration stage is the intended integration point. Prepare before native player creation/placement, rather than adding a special in-world waiting state. This workflow creates no frozen, invulnerable, or spectator avatar for asset loading. Handle server state on its proper thread and advance the configuration task at most once. Native resource-task termination is not proof of successful application.

### Deadline and admission

Bound Conclave's preparation wait by its configured preparation limit and the remaining original grace, whichever ends first. The original deadline continues during consent, transfer, reload, and native connection setup. A successful resource acknowledgement is still followed by Q237's successful native world placement and committed attempt admission. A generic early play callback cannot establish that placement.

Only begin restoration for a still-valid opportunity. Recheck the attempt, consent, captured graph, generation, connection, and opportunity before committing admission. A superseding connection invalidates the old operation's authority. Serialize resource application and admission so that a changing or uncertain applied set never briefly becomes ready.

If the attempt finishes, stop preparing a return to it and follow its existing owed-recovery policy. If grace expires, follow the existing observer policy. Neither path starts a replacement attempt, answers a ready check, renews grace, revives the player, or applies a newer catalog. Pending client work may require reconciliation even after its original admission purpose has ended. Q240 specifies fallback and explicit retry when preparation does not complete.

## Q240: ordinary entry after failure and explicit retry

Accepted: when required restoration is declined, cancelled, fails, or exceeds its bounded preparation wait, release Conclave's connection task and allow normal world entry to continue. If the player is already online, keep ordinary world play available. The original reconnect opportunity remains valid only until its original deadline, and participation stays `reconnecting` until Q237 admission succeeds.

This fallback applies regardless of whether preparation began automatically or through an explicit user action. Do not kick solely for Conclave asset failure, reset grace, silently forfeit the remaining opportunity, or make the player a new roster member. While grace remains, native movement, combat, interaction, and death retain their ordinary rules. Physical encounter input still requires active participation under Q238.

Releasing Conclave's task cannot force another mod, native setup, or an in-flight resource reload to complete immediately. Native connection rejection, network failure, or placement failure can still prevent world entry. The product should report its own state accurately rather than promise immediate entry or universal recovery from a broken client renderer.

### Retry after entering the world

Offer one explicit `Retry restoration` action in the existing assets status while the original return opportunity remains. Explain that the encounter and world continue during the reload. The action restores only the original required graph and other still-required captured resources. It does not grant permission to apply unrelated pending updates. If consent was declined, the player must change that choice explicitly before a retry can stage or apply those resources.

Revalidate eligibility and the required resource graph when Retry is used. Coalesce repeated clicks into the current operation; do not run overlapping client reloads, loop failed attempts indefinitely, or automatically retry an unchanged failure on every server update. Reuse verified cached content under a fresh authorized offer. A failed or cancelled retry preserves the same deadline and ordinary fallback behavior.

An in-world retry follows Q235's admitted-application checks in addition to this restoration exception. Reserve one application operation, protect its required cached/recovery data, and retain all current consumers. Wait for already-finishing finite Conclave audio within the bounded preparation interval. A conflicting preview must be stopped or closed through its existing controls. Never discard an unsaved draft. If a blocking consumer cannot be resolved in time, return to the actionable failed/pending status without scheduling a surprise reload later.

Do not freeze the server, grant temporary immunity, move the player to spectator, or suspend normal timers during an online reload. A player can die while it runs; any later admission uses the resulting current life state. A valid grave revival can change life under Q238, but cannot complete the asset operation or renew its deadline. Starting a different attempt must remain blocked while the client's resource state is changing or uncertain, even if the former attempt ends during the operation.

### Completion, expiry, and actual client state

Keep the last usable resource-set description and the supported recovery data until the application outcome is known. Treat success as an acknowledgement of the completed exact generation, checked against the live operation and required graph. Do not equate a successful download, task advancement, generic reload notification, or an old connection's response with readiness.

When restoration and Q237 admission succeed, change participation to `active` and cancel the existing grace. An already-online return emits `participation_changed` without another `reconnected`. Failure keeps dependent encounter admission unavailable. Restore the previous compatible set only if that restoration actually succeeds; an unknown applied state cannot be advertised as ready.

At grace expiry, remove the Retry action for that attempt and follow its observation policy. At attempt end, follow owed recovery. Neither event authorizes a new attempt or an ordinary pending asset update. Stop cancellable transfer/preparation work and release obsolete offers and claims, while retaining anything still required by another consumer or by actual in-flight application recovery.

A native reload already running may finish after expiry or attempt end. Reconcile what the client actually applied without re-admitting the expired opportunity. Ignore the late result as authority for gameplay, but do not pretend the resource set stayed unchanged. Do not force another reload merely to erase newly cached or loaded old art. Subsequent ordinary changes use Q235's permitted Apply path and current required consumers.

### Current state after successful admission

Synchronize the current authorized encounter view under the existing gameplay protocol. Preserve life state, retained roles, current phase/progress, aura ages, and fixed personal objectives. Restore only still-active permitted clues and supported visual state with their remaining lifetime. A resource set containing an asset does not grant access to another player's private information.

Do not replay expired presentation, historical sounds, grants, rewards, lifecycle transitions, or physical input. Reconnecting is not another encounter start. These are existing state-restoration obligations, not new gameplay events or a promise to resume native audio across a reload.

## Evidence and related contracts

[Native configuration research](client-asset-delivery-research.md#configuration-before-world-admission) establishes the task, identity, transport, and placement boundaries. [Reload research](client-asset-delivery-research.md#reload-affects-the-client-resource-set) records why application affects the shared client resource set, and [audio research](client-asset-delivery-research.md#reload-interrupts-native-audio) records its interruption of native sounds. The restoration protocol and exact adapter remain unimplemented.

These contracts apply [Q237-Q238 admission and participation](reconnect-admission-and-assets.md), [Q234-Q236 delivery/application/cache](archived-appearance-delivery.md), [Q67 late-return observation](death-and-revival.md#q67-returning-after-reconnect-grace-expires), and [Q66 recovery](death-and-revival.md#q66-recovery-after-an-attempt-ends). Q239 chooses automatic pre-entry preparation; Q240 chooses fallback and an explicit online retry path whether initial preparation was automatic or manual.

[Q241-Q242](client-resource-failures-and-observers.md) accept the active-client resource-loss policy and ordinary Apply eligibility for observers after expiry. Those contracts extend this reconnect workflow without restoring expired admission.
