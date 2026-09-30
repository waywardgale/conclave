# Content upgrades and startup recovery

Status: Q272-Q273 are accepted. They follow the accepted separation of installed code, authored content, captured lifecycle policy, and durable recovery. No migration service or startup recovery coordinator exists.

## Q272: upgrade content through a separate reviewed draft

Accepted: provide an in-game Upgrade draft operation for supported manifest or addon-configuration changes. Produce a separate draft with a readable difference and validation results. Keep publication explicit and preserve the source draft and all immutable records.

The editor identifies unsupported schema versions and changed capability contracts without deleting their original text. A supported converter belongs to the installed trusted framework or addon, with a declared source and destination contract. A manifest cannot supply executable conversion code. Run the same bounded structural/reference/native-capability validation on the converted candidate as on manually authored content. A successful text conversion alone is not proof of compatible gameplay.

Show affected files, field changes, defaults being made explicit, and any documented behavior differences. Preserve comments and unrelated text where supported; show limitations and retain the original when a transformation cannot safely preserve them. If no supported converter exists, keep the file editable with precise guidance and mark the remaining work. Never guess how an unknown mechanic should behave, silently drop unsupported fields, or label partial conversion complete.

Make conversion reproducible from the selected source snapshot and installed converter identity. Repeated previews cannot stack changes onto an earlier converted buffer or acquire a new published identity by accident. Bound file counts, expansion, work and diagnostics through the existing authoring limits. Installed converters remain trusted code under Q263; bounds are no claim that arbitrary JVM code can be forcibly preempted.

### Review, permissions, and publication

The result is a new named draft with its own version tokens and the active revision baseline at preparation time. The source stays available. An ordinary GM can prepare encounter-content upgrades but cannot write operator-only global policy; a candidate involving settings requires the existing operator authority for those changes. Check permissions and source versions again before saving, and publication conflicts still require reconciliation.

An upgrade does not trigger Auto-publish my saves. The new draft begins with automatic publication off. The first activation uses the ordinary explicit Publish operation after review, or remains a draft for Test/manual repair. Canceling discards only the candidate as requested; it does not reverse a published revision or overwrite the source. Subsequent ordinary editing can use the existing operator-controlled auto-publish workflow.

Validate the complete candidate catalog before publication. Test draft keeps its existing arena, consent, reward, and global-settings authority rules. Publication makes the new revision available for future attempts and new applicable lifecycles. Active attempts, existing graves, retained player-owned auras and their assets remain on their captured versions. Do not run a migration against a live attempt or overwrite a historical revision to make rollback appear compatible.

Supported source schema/configuration versions are explicit in the installed catalog and upgrade view. The first format remains `schema: 1`; this decision adds no new version now and promises no indefinite reader for every historical addon. A future breaking release must supply the migration guidance required by Q269 and accurately describe any converter it ships. An older retained revision still needs successful revalidation for rollback. Code rollback and world-state rollback are outside this operation.

Durable reward allocations, completion commitments, ownership records, retained item resources and native player/world saves are not editable content drafts. Their storage-format migration must preserve identities, obligations, supported recovery, and the actual storage transaction guarantees. It cannot regenerate loot, clear an unknown commitment, or be justified by the user's approval of a YAML diff. Storage upgrades and consistent backup/restore need their separate verified workflow.

## Q273: keep content repair available and block only unsafe operations

Accepted: distinguish invalid authored content from unavailable executable support and uncertain durable state. Keep ordinary Minecraft and the in-game repair workflow available when core/global lifecycle behavior is safe. Use the narrowest proven block for unresolved records; refuse unsafe startup when the framework cannot establish the necessary global guarantees.

| Startup finding | Response |
| --- | --- |
| Core or required global executable integration cannot initialize, or installed registrations conflict | Refuse unsafe world startup/admission with a precise diagnostic. Do not silently skip a conflicting installed provider. |
| The current published catalog fails validation, while core recovery and the committed global policy remain usable | Enter content repair mode. Keep the in-game editor and ordinary play available; block published encounter starts until an operator publishes or rolls back to a complete compatible revision. |
| A readable recovery record identifies one blocked arena, player, resource or operation | Preserve it, perform independently safe cleanup, and block only conflicting work. Unaffected attempts/operations may continue when their own admission checks pass. |
| Ownership/global lifecycle data is unreadable and the affected set cannot be bounded safely | Refuse ordinary world startup/admission until supported recovery restores trustworthy state. Do not invent an empty ownership store or new default policy. |
| Outcome/reward accounting is unavailable while world/lifecycle ownership is independently sound | Hold dependent claims, payout, and every new attempt needing the unavailable recording path, including success with an empty reward allocation set. Continue independently safe world cleanup, recovery and unrelated operation. |
| An individual archived item resource is missing | Keep the accepted scoped block on new dependent creation, preserve existing items and supported visual fallback, and report the missing resource. |

These are responses to established missing support or uncertainty. The table is not permission to add speculative restrictions to ordinary play, claim every failed disk read corrupts the whole world, or shut down a running server merely because one reward needs review. Runtime failures keep Q69, Q241 and Q254's existing behavior; this question settles startup and retained recovery.

### Content repair mode

Preserve the invalid current catalog's identity, files, activation history, and diagnostics. Do not call an incomplete subset the active revision, automatically choose a different historical catalog, or publish framework defaults over an operator's content. Let an authorized operator inspect the fault, prepare an upgrade/repair draft, explicitly publish a complete valid revision, or select a compatible retained revision through existing rollback.

Existing global revival/recovery outside encounters uses the separately validated captured global policy from the last committed publication. This is continuation of that policy for its existing purpose, not activation of the broken catalog as playable encounter content. Do not combine settings from an arbitrary older revision with definitions from the failed one. Existing graves and persistent lifecycles retain their own captured policies. If the required global policy or retained lifecycles cannot be interpreted safely, use the narrower recovery block where possible, or the unsafe-startup rule when their scope is unknown.

A valid draft Test can still run when its complete candidate, effective global policy, resources, ownership reservations and storage requirements pass their normal checks. It does not repair or activate the published catalog. Preserve the default nonpaying Test policy, but do not treat disabled payout as exemption from outcome, ownership, or other required storage. Q254 records success even when its allocation set is empty. If a Test needs an unavailable recording path, it cannot start. Automatic published start rules stay disabled while there is no valid current catalog; repair publication then uses the accepted start-rule and rearm semantics.

Show Content needs repair in the existing Conclave administration interface with the offending revision and concrete errors. Ordinary players need a short explanation only when an attempted Conclave operation is unavailable. Missing addon installation still requires the already accepted code update and restart; the content editor cannot download or replace executable code.

### Recover known ownership without inventing state

Load enough durable ownership/accounting information before admitting conflicting work. An interrupted attempt remains interrupted. Cancel or remove resources through supported engine-owned records even if an addon callback failed, whenever those records and adapters actually establish how to do so safely. Missing executable code is not equivalent to a failed callback: if cleanup needs an unavailable adapter, preserve the record and its ownership claim instead of guessing.

A known blocked arena prevents overlapping conflicting starts; a known blocked player cannot join a replacement attempt before its required recovery. Offline recovery does not hold a whole arena indefinitely. A known stale entity record must be reconciled before that entity can resume ended-scope gameplay on later load. Known unresolvable block edits preserve later world changes. Use existing bounded recovery work and diagnostics rather than permanent whole-world tickets.

Where supported, offer Retry recovery after the missing dependency, world position, or transient service becomes available. A retry uses the original stable record and operation identity. It cannot create another grave, replay death consequences, retarget recovery to the latest edited anchor, rerun completion generation, or clear accounting merely to make the screen look healthy. Exact additional GM repair commands remain their own privilege and state-transition decisions.

Unknown completion commitment retains Q254's original unresolved operation. A read failure is not proof that no success was committed; do not expose another claim or permit a GM to guess a new outcome. Q253's Record as delivered and Return to pending options apply only to its known allocation with an uncertain native transfer, using the existing operator review and audit. They do not repair unreadable global ledgers or settle an unknown completion write.

If an unreadable ownership index prevents identifying affected resources, ordinary world simulation cannot be assumed safe while stale owned entities or player states may resume. Preserve the original data and report what support is missing. Refusing unsafe startup is an exceptional integrity boundary, not a replacement for the in-game workflow on a functioning server. Do not add a force-start, forget-all, or payment-reset escape hatch that discards the missing proof. Restoring unavailable executable support or a consistent verified backup may be necessary; Q276 accepts the supported complete-set backup/restore boundary.

## Related contracts

These contracts extend [code compatibility](code-compatibility.md), [in-game authoring](in-game-authoring.md), [complete publication and rollback](manifests-and-publishing.md), [interrupted-attempt cleanup](cleanup-and-restart.md), [durable completion](durable-completion-and-reward-storage.md), [uncertain native payout review](reward-generation-and-review.md), and [permanent item resources](durable-item-appearances.md). Q272 selects the authored-content upgrade workflow; Q273 selects the response when startup or recovery cannot use existing content/state. They do not select a storage library, promise an atomic Minecraft/store backup, or authorize implementation.

[Q276](coordinated-backup-and-restore.md) accepts a coordinated stopped-world backup/restore workflow, with native data and Conclave records restored as one matching set. Q277 accepts the dependent durable storage-upgrade workflow.

[Q277](durable-storage-upgrades.md) accepts the remaining durable storage-upgrade contract. It keeps Q272 draft conversion separate and preserves Q273 refusal when new code cannot safely open global state.
