# Framework delivery and Kotlin extensions

Status: Q262-Q263 are accepted. The repository contains design documents and research, with no Kotlin implementation, Fabric initialization, executable schema, or tests. The accepted decisions and the user's corrections form the design target, including how to complete and deliver that target and how extensions share the engine's behavior.

## Q262: keep the accepted target and build it in complete stages

Accepted: treat the accepted capability set as the target for the first complete framework release, implement it in dependency-ordered development stages, and finish the unresolved details of those capabilities before adding unrelated new feature families. An incomplete development build is not the completed v1 framework, and a stage is not permission to silently drop an accepted feature.

The goal remains an authorable framework, with encounters supplied separately by the user. Preserve the five core mechanics, composition and rules, phases, arenas and Location anchors, ordinary Minecraft behavior, owned revival/spectating, NPC compatibility targets, presentation and resources, in-game editing/Test/publication, future-attempt hotfixes, and durable completion rewards. This summary does not replace the individual accepted contracts or narrow less-visible features such as aura contributions, NPC lineage, item retention, or reconnect admission.

Q214's 18 initial native NPC types remain coverage commitments. Unsupported optional controls must remain distinguishable from a fully supported adapter. If implementation evidence shows an accepted contract cannot be met on the selected platform, document the concrete conflict and return to that decision instead of quietly advertising reduced or broken support.

### Development stages

| Stage | Complete behavior to demonstrate |
| --- | --- |
| Runtime foundation | Typed manifest validation, immutable definitions, ordered execution, phase/composition lifetimes, core mechanic state, deterministic decision rules, and cleanup through real supported Minecraft operations. |
| Authoring and operation | The required client connection, in-game YAML editing and diagnostics, spatial tools, GM controls, draft Test, readiness, publication and rollback, with current attempts staying on their original content. |
| Gameplay coverage | The accepted mechanics, actions/events, NPC targets and controls, auras, relics, revival, spectating, reconnect handling, and ordinary-world interactions through their shared lifetimes. |
| Presentation and retained assets | The accepted HUD/world presentation, models, audio, styles, translations, native item appearance, permanent resource retention, consent, staging, Apply, and fallback behavior. |
| Durable completion and release verification | Reward generation, qualification, durable success and claims, uncertain-transfer review, restart cleanup, storage limits, compatibility checks, documentation, and evidence that the complete supported set meets its contracts. |

These stages organize integration work, not isolated implementations that will later be joined without tests. Implement foundational networking, ownership records, persistence, asset identity, and recovery before a dependent capability can expose real effects that need them. The final stage verifies the complete durability paths; it does not postpone recovery design until after unsafe real-item creation. Work can overlap when its prerequisites are ready.

Every development stage must report its actual supported capability set. Incomplete registered features cannot become publishable merely because their YAML parses. Validate through the intended interface, use focused internal fixtures and disposable test worlds, and include live native integration checks where pure Kotlin tests cannot establish the required behavior. Internal test fixtures are not a bundled playable encounter, campaign, or required encounter design from the user.

The author/operator workflow remains entirely inside Minecraft. Build tools and automated tests used by framework developers do not become a companion CLI requirement for encounter authors. No implementation begins merely because this delivery structure is approved; the interview's shared-understanding confirmation still applies.

### Finish existing promises first

The accepted Q277 storage-upgrade workflow builds on these accepted capabilities:

- Administrative operation/recovery cases, accepted in [Q274](administrative-operation-contracts.md), without reopening accepted camera/privacy policy.
- Typed event conventions, accepted in [Q275](event-catalog-conventions.md). Q268-Q269 already settle client requirements and public extension compatibility.
- Consistent backup/restore, accepted in [Q276](coordinated-backup-and-restore.md), followed by the dependent durable storage-upgrade policy.

[Q264-Q265](movement-and-simulation.md) accept player travel, remote graves, and owned-NPC simulation beyond the retained arena. Their native adapters still require implementation and verification.

[Q266-Q267](area-fields-and-membership.md) accept the remaining concrete area fields and membership policy placement. [Q268-Q269](code-compatibility.md) accept core client admission and public extension stability. [Q270-Q271](settings-and-spectator-controls.md) accept settings/viewing details, and [Q272-Q273](content-upgrades-and-startup-recovery.md) accept content-upgrade and startup/recovery handling.

[Q277](durable-storage-upgrades.md) accepts the storage-upgrade preparation and rollback contract. [Q278](revival-protection.md) accepts positive revival protection's damage/input rules, and [Q279](aura-and-world-visuals.md) accepts shared aura display and initial world-effect placement. These resolve the final required behavior branches identified by the closure audit. Complete field schemas and adapter proofs remain engineering work; [shared-understanding confirmation](design-interview.md#shared-understanding-confirmation) is pending before implementation.

Native hooks, exact dependency versions, parser/storage libraries, numeric budgets, and crash/performance verification require implementation evidence. Do not ask the user to guess these facts or invent arbitrary limits to declare the design complete. Where evidence creates an actual product tradeoff, bring that choice back with a recommendation.

Keep optional additions outside this first release target unless an accepted capability requires them or the user adds a concrete authoring requirement. Examples include further item properties, additional combat systems, rich dynamic text, a generic world-edit action family, general ambient rules, new NPC coverage, and extra font providers. This does not remove existing native item behavior or the accepted cleanup policy for supported world changes. Behavior required when a player travels normally is still required even if a new authored teleport action is deferred.

Previously explicit exclusions remain: arena copying is v2; concurrent active phases, resuming unfinished attempts after restart, runtime scripts/commands, a visual node editor, automatic inventory rollback, and bundled encounter content are not silently added. Ender Dragon and Wither adapters remain outside initial native NPC coverage. Unpromised remote administration integrations stay outside the ordinary in-game workflow until explicitly selected.

## Q263: one typed extension interface with engine-owned lifetimes

Accepted: build registered capabilities behind small typed Kotlin interfaces and use the same registration and lifecycle rules for built-in and addon capabilities. Keep validation, revision capture, scheduling, cancellation, resource ownership, and diagnostics in shared engine modules so each new mechanic implements its own behavior rather than another copy of those systems.

### Register once and describe the capability

A capability registration supplies its qualified identity, typed configuration description, documented defaults and bounds, supported contexts, required references/dependencies, readable help, and implementation. Registered actions, predicates, mechanics, event sources, and native/resource adapters have interfaces suited to their behavior; do not force them through a universal string-to-object callback or untyped map.

Use that authoritative description to produce editor completion, machine-readable schema, capability discovery, and ordinary structural validation. Additional semantic validation can check cross-field and native adapter constraints through the same validation module and source diagnostics. Publication, Test, and the editor must agree about support; a second handwritten client schema cannot silently accept behavior the server rejects. ASVS 2.1.1, 2.1.2, 2.1.3, 2.2.1, and 2.2.2.

Descriptions must state relevant lifecycle and delivery limits, such as supported scopes, event payload types, public outputs, whether an action can remain pending, supported failure outcomes, client resources, and whether a presentation form is allowed in Q245's final pass. These fields apply where relevant; a pure condition does not need to pretend it is a long-running mechanic. Unknown or incomplete capability support remains invalid under the existing validation policy.

Client support is explicit. New server-only composition over already supported synchronized values should not require a custom screen. A genuinely new client renderer, payload, camera behavior, or visual adapter requires matching installed code and compatibility checks. A downloaded manifest or asset must never be presented as executable client support.

### Shared execution and ownership

Compile authored data into typed immutable configuration before activation. Give each activation its own state and an engine-owned context bound to its actual permitted lifecycle owner and applicable captured configuration, including attempt identity when it belongs to an attempt. Player-owned auras and global revival can operate outside an attempt under their already accepted lifetimes. A capability gets the permitted typed state, observations and supported operations it needs; it does not receive mutable access to another activation's private state or the latest editable YAML.

The engine supplies the established clocks, event queue, scheduling, invocation limits, cancellation, source diagnostics, resource accounting, and ownership tracking. Capabilities report typed observations/results and request supported operations through that interface. They do not directly assign the enclosing phase route, commit another scope's outcome, or bypass Q247 arbitration. Composition remains reusable engine behavior rather than logic copied into every mechanic.

Register owned schedules, subscriptions, entities and resources with the appropriate lifetime as they are created. Cleanup must retain enough engine-owned information to cancel or recover supported operations even if the capability's own callback fails. Repeated cleanup must not grant rewards, recreate entities, or treat removal as gameplay defeat. Native operations with partial or durable effects still need their specific adapters and recovery contracts; a generic cleanup handle is not a transaction over Minecraft.

Bind each queued continuation to its actual owning activation and generation. A canceled or replaced owner makes it ineligible before it can mutate gameplay, even if its future or callback completes later. Ownership is not necessarily the scope of the action that originally created a longer-lived resource: an explicitly player-owned aura keeps its accepted lifecycle when the applying phase ends, while a pending phase-owned invocation cannot follow it into that lifecycle. Use the accepted startup barrier only for documented required startup work. Do not let arbitrary pending extension work hold ordinary outcome settlement open.

Run authoritative gameplay changes through the server's ordered simulation path. Native adapters retain the immediate commit points required by their actual hooks. Restrict background work to supported preparation/I/O or computation over immutable inputs; admit its result through a checked continuation before affecting live state. Do not expose an unscoped coroutine launcher that can keep changing the world after its attempt ends.

Budget checks and operation reservations are shared engine behavior. This is not a claim that Conclave can forcibly preempt arbitrary blocking JVM code, undo every effect from a buggy addon, or sandbox an installed mod. Addons are trusted installed code; the interfaces define supported behavior and make it testable. Client requests and authored manifests still receive authoritative validation and permission checks regardless of which capability handles them. ASVS 2.3.1, 2.3.4, 8.3.1, and 15.4.1.

### Native adapters and tests

Keep Minecraft-specific state and operations behind adapters at interfaces where behavior actually varies, such as supported NPC behavior, player lifecycle operations, item creation/delivery, native presentation, and resource formats. Keep the encounter state machine and declarative composition usable through typed observations/results without importing a native entity into every mechanic. An adapter owns the native compatibility knowledge that its callers should not need to repeat.

Use the same interface for built-in capabilities, addon capabilities, and focused behavioral tests. Test time, randomness, observations and supported operation results through their real interfaces; verify native adapters with integration tests and crash cases where required. Avoid a mock for every internal method or a nominal interface around a single pass-through wrapper. The distinct core mechanics already provide real variation at the mechanic interface.

Do not expose every internal helper as a public extension promise. Keep implementation details within their modules and grow public interfaces only where an accepted capability needs them. Exact Kotlin types, package/Gradle layout, serialization format, coroutine strategy, and storage implementation follow this contract and implementation evidence rather than being fixed as speculative class diagrams here.

### Installed code and hotfixes

Register extensions from trusted installed Fabric addon mods during startup, before loading and validating encounter content. Reject duplicate capability identities and incompatible registrations; the reserved `conclave` namespace cannot be replaced by a later addon. Freeze the installed capability catalog for that server session.

Do not add a separate runtime JAR/plugin loader, code upload through manifests, or code replacement during an active server session. Adding or changing capability implementation code requires updating the installed mod set and restarting the affected server/clients. The accepted in-game hotfix workflow continues to publish YAML and supported resource data for future attempts without changing installed code or active-attempt state. Code compatibility is separate from content rollback.

The extension compatibility/version policy and public interface stability contract are accepted in [Q268-Q269](code-compatibility.md). Startup registration here does not silently promise binary compatibility across arbitrary Conclave or Minecraft versions. Missing or incompatible code must remain visible in the installed capability catalog and diagnostics rather than degrading an accepted definition into a different mechanic.

## Related contracts and remaining work

Q262 selects the delivery target and scope discipline independently of a particular Kotlin interface. Q263 selects shared extension responsibilities independently of how implementation work is staged. Neither authorizes beginning implementation before the interview is complete.

These contracts preserve the [framework brief and accepted decisions](design-interview.md), [Q70-Q72 manifests and publication](manifests-and-publishing.md), [Q73-Q77 in-game authoring](in-game-authoring.md), [Q78-Q82 typed references](manifest-references.md), [Q68-Q69 execution](execution-and-errors.md), [Q167-Q168 startup and typed parameters](mechanic-start-and-parameters.md), [Q214-Q215 native NPC targets and discovery](npc-adapter-scope.md), and [Q246-Q247 ordered outcomes](simulation-stages-and-outcomes.md).

The selected ASVS references state design requirements, not an achieved conformance level. Neither module design nor a successful schema check proves native behavior, recovery, client compatibility, or measured limits. The product frontier is empty; final confirmation, executable schemas and implementation evidence remain distinct from accepted design decisions.
