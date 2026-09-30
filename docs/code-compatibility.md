# Client code and extension compatibility

Status: Q268-Q269 are accepted. Q263 already selects typed startup registration, a fixed session catalog, trusted installed addons, and restart for code changes. These contracts settle the client connection requirement and the public extension compatibility promise. No handshake, published Kotlin API, or compatibility test suite exists.

## Q268: require matching core code before world entry

Accepted: require the same supported Conclave release on the server and every connecting player, including players who will remain outside an encounter. Verify that requirement before world entry. Keep addon requirements specific to their registered client behavior, and preserve the separate accepted treatment of asset readiness and refusal.

This explicitly extends ADR-0002's participating-client requirement to the whole Conclave server. Global revival, grave cameras, spectating, native health integration, and administrative screens cannot rely on a player deciding to enter an encounter before compatible core code exists. Do not invent an unmodded-player death path or silently switch a player to vanilla respawn because their client cannot present the accepted lifecycle.

For the initial supported release, require an exact core release match, including the build identity for development builds. The supported Minecraft target remains 26.2; compatible Fabric, Kotlin and other dependencies come from the tested distribution's requirements. Do not promise mixed Conclave patch versions merely because their version numbers look close, or claim that an identical base release makes every optional mod compatible. A later mixed-version protocol would need an explicit compatibility contract and evidence.

Connection preparation reports a missing or incompatible core before gameplay begins, with the installed and required Conclave release and a readable explanation. A bounded failed or missing compatibility exchange refuses world admission; it does not create a participant, grave, or reconnect success. Authentication and a client version claim alone never authorize gameplay actions. Server-side permissions, input validation and current-state checks still apply. Exact payloads and the native configuration integration are implementation work.

### Addons and required client behavior

The server does not require identical server/client mod directories. A server-only addon using supported generic synchronization and presentation needs no matching client addon solely because it contributes server behavior. An addon supplying a new renderer, camera, client payload, or native client integration must declare the corresponding installed client capability and compatible versions.

Distinguish connection-wide requirements from content-specific ones in the installed capability description. A capability needed by global lifecycle behavior must be compatible before entry. A capability used only by a selected encounter, preview, or editor operation is checked before that operation's admission, under its existing required-capability rules. A missing optional client addon can therefore leave that operation unavailable without refusing otherwise compatible ordinary play. If the installed platform or another mod independently requires a client dependency at connection, Conclave does not override that requirement.

Derive the requirements from validated capabilities and the applicable captured configuration. Do not trust a manifest to label executable support optional when its declared behavior requires it. Do not download code, attempt runtime registration, or treat resource-pack data as the missing implementation. The existing in-game compatibility view shows installed, required, missing, and incompatible capabilities with actionable names and supported versions. Installation of mod code remains the prerequisite already inherent in a required Fabric mod; no companion authoring CLI is introduced.

### Keep code compatibility separate from asset readiness

A compatible client that declines a resource pack, cancels an asset transfer, or fails to apply an asset retains Q124's ordinary-world policy. Those cases are not core-code mismatch and cannot trigger a connection refusal solely through this new check. This includes Q240's ordinary entry after required captured assets fail restoration. Required captured assets still gate the relevant attempt/operation; optional outputs use their accepted skip or fallback contracts. Never convert asset refusal into a false report of incompatible code.

Q237-Q240 still define successful reconnect admission. Passing the code check does not complete world placement, apply captured assets, extend reconnect grace, or restore participation by itself. Returning players keep the attempt's original data and resources. A server code update requires restart under Q263, and Q120 then handles unfinished attempts as interrupted rather than migrating them into the new code.

## Q269: version the small public extension API separately from content

Accepted: publish a deliberately small versioned Kotlin extension API, with documented compatibility within a supported platform line. Keep internal implementation outside that promise, and make incompatible code or configuration fail explicitly before it can become active.

### Public API stability

Label development interfaces experimental until the first complete framework release. Changes during that period can require addon updates, with migration notes; a development version is not an unannounced stable API promise. The first complete release establishes public API major version 1 for its documented registration, typed capability, lifecycle, and supported native-adapter interfaces.

Within a public API major version and its explicitly supported Minecraft/runtime line, preserve source and binary compatibility for supported addon use of those public interfaces. Additive minor releases must not require every existing addon to implement a new abstract method or rebuild against an internal type. Correctness fixes can change faulty behavior, but must document observable changes. Intentional breaking changes require a new API major version and migration guidance. Announce planned removals in at least one supported minor release before that major change; do not remove deprecated public interfaces in an ordinary patch.

Minecraft, Fabric, Kotlin/JVM, and native adapter dependencies still have their own supported ranges. API-major equality alone does not certify cross-Minecraft compatibility or make an addon using unsupported internals safe. Publish the tested platform matrix and require addons to declare their supported Conclave/API and platform ranges through installed-code metadata. An addon that reaches into internals or another mod's undocumented behavior assumes those additional compatibility constraints. The public API must avoid exposing native details where Q263 assigns them to a supported adapter.

Keep the core network release check in Q268 distinct from this addon API promise. Stable server-side registration can permit an existing supported addon across several Conclave releases even when the core client and server must match for a session. If Q268 changes, the same public API contract remains usable; it does not itself prescribe a network negotiation scheme.

### Capability and configuration compatibility

Each installed capability declares its qualified identity, supported configuration contract and contexts, implementation/provider identity, and applicable client requirements. The catalog reports those facts. Reject duplicate identities, unsupported API versions, and incompatible registrations before the catalog freezes. A matching ID is not enough to replace an unsupported mechanic with a different implementation or silently ignore configuration fields.

Keep three separate compatibility questions visible: whether the addon can run on this installed platform, whether the authored configuration is supported, and whether a selected client can perform the required presentation or operation. The existing `schema: 1` remains the manifest format marker. It is not the Kotlin API version, network release, or a promise that every addon configuration exists on every server. Authors need no version numbers copied into each mechanic invocation; validation derives dependencies from referenced capabilities.

Publication, draft Test, preview and rollback validate against the actual installed catalog. New code can change available support only after the required installed update and restart. A retained revision whose required capability is absent or incompatible fails revalidation; keep its authored data and diagnostics intact. Do not delete unknown addon fields on opening the editor, publish an incomplete subset of a catalog as though it were the same revision, or reinterpret an old field under incompatible semantics.

An addon changing the meaning or accepted shape of a public configuration contract must identify the breaking contract change and provide migration guidance. Additive optional fields with preserved defaults can remain compatible. Code/API compatibility does not authorize rewriting existing YAML, captured settings, retained reward allocations, or pending recovery records in place. [Q272-Q273](content-upgrades-and-startup-recovery.md) accept in-game content/schema upgrade review and the failure scope for an invalid startup catalog or unavailable retained-state adapter; Q276-Q277 accept the separate consistent backup/restore and durable storage-upgrade contracts.

### Evidence required for a compatibility claim

Verify supported compiled addon fixtures against subsequent compatible releases, exercise registration and lifecycle cancellation through the public interfaces, and run native/client integration checks for advertised adapters. Document actual tested ranges rather than treating version metadata as proof. Runtime contract errors continue to use Q69, and trusted JVM addons retain Q263's explicit lack of sandboxing or forced preemption.

Exact Kotlin signatures, packaging, dependency coordinates, network payload versions, and configuration-contract metadata follow this policy and implementation evidence. The framework remains documentation-only. These contracts create no claim that published releases or an operational upgrade path already exist.

## Related contracts

These contracts extend [shared typed extensions](framework-delivery-and-extensions.md), [required client and server authority](adr/0002-required-client-with-server-owned-gameplay.md), [global revival](death-and-revival.md), [publication and rollback](manifests-and-publishing.md), and [capability discovery](npc-adapter-scope.md). They preserve [asset consent](world-locations-and-assets.md), [reconnect admission](reconnect-admission-and-assets.md), and [restart cleanup](cleanup-and-restart.md). Q268 selects connection admission; Q269 selects the developer-facing compatibility promise. Neither authorizes implementation before shared understanding is confirmed.

[Q276](coordinated-backup-and-restore.md) accepts compatibility checks for complete backup restoration, retaining installed code and current administrative authority independently of restored gameplay state. Durable storage upgrades remain separate from Q272 content conversion.

[Q277](durable-storage-upgrades.md) accepts a supported transition-plan bridge between the working release and installed target converters, without runtime JAR loading or automatic downgrades.
