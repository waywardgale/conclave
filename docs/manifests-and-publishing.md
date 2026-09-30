# Manifests and publishing

Status: Q70 and Q72 are accepted. The user revised Q71 to require the full workflow inside Minecraft, with no external CLI tools. Complete validated revisions, activation only for future attempts, pinned gameplay settings, and one explicit publishing action are already accepted. Q73-Q77 accept the in-game editor, command shortcuts, draft transport, collaboration, development publishing, and testing. None of these operations is implemented yet.

## Q70: one consistent file shape

Accepted: use one named top-level definition per YAML file. Begin with `schema: 1`, followed by a single kind wrapper such as `encounter`, `arena`, `npc`, `aura`, `relic`, or `mechanic`. The wrapper contains the definition's `id`, optional `name`, and kind-specific settings. The existing `type` convention continues to select a behavior within a definition; it does not also identify the document kind.

For example, the opening of an encounter manifest would be:

```yaml
schema: 1
encounter:
  id: my_encounter
```

This fragment illustrates nesting only and is not a complete runnable encounter. Its phases belong inside `encounter`. The original user examples did not constrain this accepted layout.

Keep related files in an ordinary authoring directory, optionally grouped in folders such as `encounters`, `arenas`, and `npcs`. A filename does not establish an object's identity or execution order. References use stable authored IDs. [Q78-Q82](manifest-references.md) define accepted namespaces, typed references, reusable-mechanic parameters, arena bindings, and a singleton global gameplay-settings document. Detailed settings fields and packaging of independently reusable libraries remain subsequent decisions.

Permit short inline configuration for one-off mechanics and explicit references with parameters for reused definitions. Avoid a second text-templating language, unrestricted expressions, and filesystem includes. Reject YAML custom object tags, duplicate keys, unknown fields, and unsupported schema versions. Prefer explicit reuse over YAML aliases and merge keys. Report errors with file, line, field path, and a useful suggestion where possible. Publish a machine-readable schema for editor completion and AI tooling alongside readable capability documentation.

## Q71: all operations inside Minecraft

Accepted with the user's revision: authors must be able to perform the complete Conclave authoring and administration workflow from Minecraft. No companion CLI is part of the design. The previously proposed external commands, SSH/SFTP profiles, and filesystem access requirement are superseded. YAML files remain portable and optional external text editing remains possible; it cannot be required to finish an authoring operation.

Minecraft must provide access to creating and editing manifests, managing Location anchors and areas, validation, publishing, development auto-publishing, revision history, rollback, attempt inspection and control, and GM administration. Preserve the accepted permission boundaries: operators manage GM membership and publication, and GMs have their granted editing and attempt-control powers. Ordinary Creative mode still grants only its accepted area viewing capability.

Saving a draft remains distinct from publishing. Publication snapshots the complete intended content, obtains authoritative server validation, activates the revision for future attempts, and returns a result to the author inside Minecraft. A sent request or completed upload is not proof of activation. Current attempts keep their original definitions and effective gameplay settings.

Development auto-publishing remains explicitly enabled and uses the same complete-revision validation and activation policy. Partly edited or invalid files leave the previous valid revision active. Its in-game controls and draft source are accepted in [Q76](in-game-authoring.md#q76-development-auto-publishing).

Keep incomplete transfers separate from usable drafts and published revisions. The server records the publication request and result so reconnecting can retrieve the outcome without applying it twice. Physical Location anchor reconciliation still defers changes in occupied arenas until idle. Exact request protocol, transfer bounds, and timeout policy remain implementation decisions.

The accepted [in-game authoring contract](in-game-authoring.md) specifies the editor and command shortcuts, draft ownership and transfer through the connected Minecraft session, collaboration, and testing. A separate web administration service is not required.

## Q72: history, rollback, and publication conflicts

Accepted: identify each complete revision by its canonical content, including effective Conclave gameplay policy. Store an optional human label and an activation history. A revision is the whole resolved catalog; selectively mixing files from old and new catalogs is not rollback.

Make rollback available in the in-game revision history. The accepted command shortcut is:

```text
/conclave rollback <revision>
```

Rollback revalidates a retained revision against the installed framework and capabilities, then makes it current for future attempts. Reject an incompatible rollback and keep the current revision. Active attempts keep their pinned revision. Mod code, client assets, vanilla gamerules, GM membership, and world changes already performed are outside content rollback.

Retain the ten most recently activated distinct revisions by default, with an operator-configurable count. Always preserve revisions needed by active attempts or unresolved lifecycle operations, including pending recovery that still references them. Only prune an otherwise eligible revision once its consumers release it. Durable recovery records may instead carry the complete data they need; storage layout remains part of persistence design.

Each publication declares the active revision it was prepared against. If another author publishes first, reject the stale publication with the actual current revision and a readable difference summary. Do not silently overwrite the newer publication. The author can reconcile and resubmit; opening or importing a draft records its baseline against the connected server. Saving a draft remains separate and never overwrites an immutable published revision. Q75 in the [editor contract](in-game-authoring.md#q75-simultaneous-editing-and-yaml-preservation) defines version checks and preservation of both conflicting edits.

Publication and rollback retain the existing administrative authorization policy. Revoking GM authority remains immediate and is never undone by a content rollback.
