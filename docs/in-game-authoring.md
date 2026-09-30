# In-game authoring

Status: the user requires the entire Conclave workflow inside Minecraft and excludes external CLI tools. Q73-Q77 below are accepted. The existing required client, server authority, YAML format, permissions, and immutable attempts remain accepted. No editor, command, transfer protocol, or test mode has been implemented.

## Q73: an editor and discoverable commands

Accepted: `/conclave` opens a permission-filtered Conclave screen with access to manifests, world editing, validation, publication history, and attempt controls. Provide command shortcuts for repeatable operations, including `/conclave edit`, `/conclave validate`, `/conclave publish`, `/conclave history`, and `/conclave rollback <revision>`. When several drafts or attempts exist, require an explicit selection instead of guessing the target. The screen and commands call the same server operations.

Include a built-in YAML editor with syntax highlighting, indentation, line numbers, undo, copy and paste, search, schema-driven completion, and diagnostics that jump to the affected field. Add small templates for document kinds and registered capabilities. Templates are authoring aids, not shipped playable encounters. Authors can create and edit every supported manifest in this editor without opening another application.

Use human-readable controls for Location anchors, area geometry, and common operations. Keep YAML available for the full behavior vocabulary so a new registered capability can appear through its schema and help without requiring a bespoke visual editor. Scope v1 around usable text editing and spatial tools; a visual node graph is not proposed.

GMs can prepare encounter-content drafts, extending their accepted Location anchor editing access. Operators publish or roll back and edit global server gameplay policy. Permission changes apply to every request, and a client screen must close privileged access when it is revoked. Server operational settings and administrative membership cannot be smuggled through ordinary encounter YAML.

## Q74: draft storage and transport

Accepted: store saved drafts as YAML on the connected server. An author can start a draft from the current published revision, create definitions, save incomplete work, close Minecraft, and reopen the saved draft later. Each draft records its identity and publication baseline. Publishing always builds the full resolved catalog, even when the author changed only one file. The precise directory layout is internal to the server and must not be needed for normal authoring.

Keep unsaved editor text separate from acknowledged server saves. Preserve a local recovery copy of unsaved text if the connection drops, and identify it as unsaved when the author returns. Opening a file is not permission to send the full manifest catalog to ordinary players; authoring data is restricted to authorized editors.

Offer optional Import YAML and Export YAML inside Minecraft, including a client-side authoring folder for files edited by a human or AI agent. Import shows additions, replacements, and deletions before saving them into a draft. Import alone does not publish, and an absent local file does not silently delete a server definition. Clipboard paste into the built-in editor remains another route. Explicit user selection confines any local file access to the chosen authoring content.

Send authorized draft content and requests over the existing Minecraft connection using Conclave payloads. Require no SSH credentials, extra port, or external command. The server validates permissions, target draft, file identifiers, content size, and expected versions. Bounded multipart uploads remain staged until complete; never place arbitrary client-supplied paths on the server filesystem. Recheck current authority before saving or activating, including after a reconnect or permission change.

Fabric's [26.2 networking guide](https://docs.fabricmc.net/develop/networking) documents client-to-server payloads and server validation. Its [custom screen guide](https://docs.fabricmc.net/develop/rendering/gui/custom-screens) documents screens and widgets. Using these for Conclave's editor and draft transfer is a design inference. These APIs do not provide a ready-made YAML editor or prove that this workflow has been implemented.

## Q75: simultaneous editing and YAML preservation

Accepted: give saved drafts and their files revision tokens. A save states which version the author opened. If the file changed meanwhile, retain both the server version and the author's buffer, show the difference, and require reconciliation. Never let the last arriving save silently overwrite an intervening edit. Publication also checks its whole-draft snapshot and active-content baseline under accepted Q72.

Authors can use separate named drafts; collaboration on the same draft follows the same conflict checks. Do not build real-time shared cursors or automatic conflict resolution in v1. A Location anchor form and a YAML editor modifying the same underlying definition must participate in the same checks.

Save raw YAML text as written. Structured form edits should preserve comments and unrelated fields and show the proposed change before saving. If an edit cannot preserve them safely, show that limitation and keep the original text available; do not silently discard authored notes. Avoid rewriting every manifest when only one field changed. Exact parsing and source-editing library choice requires later implementation research.

[Q272](content-upgrades-and-startup-recovery.md#q272-upgrade-content-through-a-separate-reviewed-draft) accepts a separate Upgrade draft workflow with source preservation, an affected-file review, and explicit first publication. It does not authorize rewriting immutable revisions or durable gameplay records.

## Q76: development auto-publishing

Accepted: provide an operator-only Auto-publish my saves toggle in Minecraft, off by default. It applies to one selected draft during that operator's current connected session. Publish after an explicit save or accepted import completes, never on every keystroke. Save all can group related edits into one draft change. Validation still covers the complete revision, and all current attempts stay pinned.

Before enabling, show which draft and complete change set the operator is authorizing. The toggle advances through that operator's own saved changes. If another author changes the draft, another publication changes its baseline, or the operator loses authority, stop automatic publication and show the reason. Re-enabling requires the operator to review the new state. This avoids treating any GM's later save as blanket publication authority.

Invalid saves can remain drafts but cannot become active content. Show the validation problem in the editor and keep the last valid revision. Closing the session or disconnecting turns the toggle off; reconnecting does not silently re-arm it. The operator can always use explicit Publish after reviewing a draft.

## Q77: trying a draft before publication

Accepted: add Test draft for authorized GMs and operators. It validates a selected draft and pins that exact version to one clearly marked test attempt in a chosen idle arena, without changing the published revision used by ordinary new attempts. A later edit does not alter the running test; Restart test starts a new attempt from a newly validated snapshot.

Reserve the selected arena just like an ordinary attempt. Other arenas continue to use the published content. Participants join the explicitly labelled test knowingly. Show the draft identity, test status, validation results, and GM stop/restart controls inside Minecraft. A GM test uses the published global gameplay policy; testing draft changes to operator-only global settings requires an operator.

Testing uses a real, existing arena. It does not copy a world or promise to restore unrelated construction, consumed items, or vanilla death losses. Attempt-owned cleanup, participant recovery, and accepted vanilla gamerules still apply. This preserves arena copying as v2 work. Explicit start and stop keep a preview from accidentally spawning live combat entities.

Spatial Preview remains a separate harmless overlay under the Location anchor contract. Full gameplay simulation without a world, interactive breakpoints, and record/replay are not proposed for v1. Tests can expose the ordinary authored IDs and status needed to diagnose an encounter without changing the established private-information policy for participants.

[Q248-Q249](completion-rewards-and-test-policy.md) accept encounter-level completion rewards and a separate Test payout policy. Test suppresses supported reward delivery by default and offers an operator-only real-payout option at launch. Terminal presentation remains presentation-only; no general grant action is added.

[Q251](reward-recipients-and-delivery.md#q251-private-delivery-with-pending-rewards) accepts a personal Rewards view and `/conclave rewards` for ordinary players, with individual claims and Claim all. Players can access only their own allocations; privileged inspection does not permit claiming another player's reward or reissuing a paid reward.

[Q262-Q263](framework-delivery-and-extensions.md) accept staged delivery of the accepted framework target and shared capability descriptions for validation, editor help, and installed support discovery. All author/operator workflows remain inside Minecraft; development staging is not permission to omit accepted release capabilities.
