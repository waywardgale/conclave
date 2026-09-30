# Auras and world lifetimes

Status: Q117-Q118 and Q121-Q122 are accepted, including the user's additions of `applied`, `stack_gained`, `stack_refreshed`, and `contribution_refreshed`. Aura definitions, source-owned contributions, same-source refresh, death behavior, stack limits, simulation time, and `has_aura` versus `has_effect` are already accepted. These contracts address lifetimes outside an attempt and distinguish holder-level changes from individual contribution and stack changes.

## Q117: auras that outlive an attempt

Accepted: support explicitly player-owned aura applications using `scope: player`. This lets an author grant an attunement or another gameplay mark that remains useful as a later encounter's start filter. The ordinary phase or encounter scope remains the default for an aura applied by an encounter. A definition's mere existence does not apply it to anybody.

An explicit `apply_aura` action may create a player-owned contribution, and explicit administrative apply/remove operations in Minecraft may do the same outside an attempt. Those operations require the accepted GM or operator authority. Registered integrations may use a typed API under the same ownership rules. Q125-Q126 define action targeting and Q274 defines administrative command arguments; no free-form command execution is added to YAML.

The application becomes owned by that player's aura lifecycle, with its source identity and provenance retained. Ending its originating phase or attempt does not remove it or reapply it as a fresh aura. Reapplication by the same source follows the accepted refresh policy, and independent sources retain their own contributions. Do not silently convert existing phase-owned contributions into player-owned ones or let removal by one source erase another source's contribution.

Keep the aura's accepted duration and remove-on-death default, including explicit retention through death when configured. Timed player-owned auras continue to age on the server simulation clock while the player is disconnected, and expire without waiting for login. Untimed applications require explicit removal or their configured death behavior; they must be inspectable and removable through the authorized in-game interface. Server downtime does not consume simulation time. Accepted [Q159 periodic effects](aura-periodic-and-queries.md#q159-periodic-damage-and-healing) retain their cadence but discard damage and healing pulses while the holder is dead or offline; revival or login never replays that skipped work.

Persist player-owned applications independently of an active attempt so reconnect or restart cannot duplicate or silently reset them. Retain the definition needed by an existing application instead of rewriting its balance or presentation when YAML is published. New applications use their caller's applicable revision: an active attempt uses its pinned definitions, while an outside-attempt administrative application uses the currently published revision. Q122 defines the accepted compatibility policy when revisions provide different definitions of the same aura on one holder. Their behavior and display must not be merged without an explicit contract.

A player-owned application must not retain a callback into an ended phase, a dead mechanic activation, or a cleaned-up NPC group. Its permitted independent effects and removal behavior need valid player/world targets. Existing authored attempt rules can observe it while they are active; ending those rules does not leave them waiting for a future expiry. A general world-rule scheduler, arbitrary pre-start counters, and promotion of other attempt resources to permanent state are not introduced by this proposal.

Starting a new encounter does not itself transfer, consume, or remove the player's aura. Authors can explicitly configure such behavior. Cleanup and exceptional recovery can remove an invalid application with a reason, but never fabricate a natural expiry or replay gameplay rewards.

## Q118: aura lifecycle events

Accepted: distinguish the end of one source contribution from the holder losing the aura completely. Use `contribution_expired` for a timed contribution ending naturally. For a stacking aura, each expired stack also emits `stack_expired` with its stack identity. Use `expired` once when the final effective contribution disappears through natural expiry, and `removed` once when the final contribution disappears through explicit removal, death, or cleanup.

A remaining contribution keeps the aura present and prevents an aura-wide `expired` event. A non-stacking refresh does not manufacture another application or expiry. For a stacking aura, intermediate expiry updates the stack count without implying that the holder has lost the whole aura. The existing cap and ignore-at-cap policy remain unchanged.

Carry holder, aura identity, source identity, removal reason, and relevant activation identity in typed event payloads. Private sources expose only the fields permitted by their owning scope. A rule subscribing to the aura's `expired` event gets the holder-level result; a rule that needs its own source's timer can explicitly subscribe to `contribution_expired`. Event names identify different outcomes and are never aliases that fire the same handler twice.

When several contributions end in one simulation update, apply their scheduled changes in the accepted deterministic order and decide the final holder-level state after that aura's contribution changes are resolved. If the holder still has a contribution, emit no holder-level end event. If none remains, emit one terminal event based on the last effective removal: natural timeout yields `expired`; death, cleanup, or explicit removal yields `removed`. Retain individual reasons in contribution events for authors who need them.

New application from a later event handler is a new observable change; it does not rewrite an already emitted event. Cleanup cannot trigger expiry-only punishment. Stale source activations do not deliver events into replacement mechanics. Cancelling the originating attempt removes its subscriptions, including any subscription to a player-owned aura that later expires.

The user additionally requires `applied` when an aura is applied and `stack_gained` when a stack is added. Q121 defines their accepted first-application and reapplication semantics, with the user's subsequent addition of `stack_refreshed` and `contribution_refreshed`.

[Q160](aura-periodic-and-queries.md#q160-events-for-contributions-removed-before-expiry) also accepts `contribution_removed` and `stack_removed` for actual removal before natural expiry. These retain source and reason information, do not duplicate natural-expiry events, and cannot reactivate an ended subscriber. Q275 accepts the typed lifecycle fields, reasons and count/refresh snapshots; their registered schema remains implementation work. This contract defines the observable distinctions and names without introducing arbitrary object traversal or a second event-processing model.

## Q121: first application, stack gain, and refresh

Accepted: emit holder-level `applied` when an absent aura becomes present. Emit `stack_gained` for each accepted new stack, including the first stack of a stacking aura. The first stack therefore emits both events, with different meanings. An aura that is already present does not emit another holder-level `applied` merely because a different source adds a contribution.

Add `contribution_applied` for each newly created source contribution, symmetric with the accepted `contribution_expired`. For a stacking aura, each distinct stack is a contribution with its own identity and lifetime. Emit `contribution_refreshed` when an existing contribution's expiry is actually renewed under the configured reapplication policy. If that contribution is a stack, also emit `stack_refreshed` with the stack identity. Emit holder-level `refreshed` once for that committed renewal operation on the holder's aura. A same-source non-stacking refresh therefore emits `contribution_refreshed` and `refreshed`, without `applied` or `stack_gained`.

The refresh events observe a supported renewal; they do not introduce a new refresh-all policy, turn a new stack into a refresh, or change the accepted ignore-at-cap default. If a supported operation renews several existing stacks, each changed contribution and stack emits its corresponding event, followed by one holder-level `refreshed`. Renewing one contribution can emit these events even when another contribution still determines the holder's displayed final expiry.

An application ignored by policy or rejected at the stack cap emits none of these successful-change events. A reapplication that changes nothing, such as the same untimed contribution already present under a no-change policy, emits no artificial refresh. Authors react to a confirmed state change rather than an input request that did not change the aura.

For one committed application, queue `contribution_applied`, then `stack_gained` when relevant, then holder-level `applied` when relevant. Include the holder, aura identity, source/contribution identity, previous and new stack counts where meaningful, and the originating activation. These typed fields describe that committed change even if a later action changes the live aura before the event handler runs. Multiple accepted stacks use stable allocation order.

For a committed renewal, queue each `contribution_refreshed` followed by its `stack_refreshed` when relevant, then the single holder-level `refreshed`. Retain the previous and new expiry values and the affected identities in the applicable typed payloads. Specific and holder-level events describe different levels of the same change; authors choose which level their rule needs.

The events remain part of Q68's ordinary server queue and execution limits. A handler that adds a stack can produce another `stack_gained` event, so authors must use the existing guards and invocation limits for intentional repeated effects. Do not add a hidden rule that suppresses all events caused by another event, and do not invoke handlers recursively while mutating the aura.

Removing the aura completely and later applying it creates a new holder-level application and emits `applied` again. Reconnecting, rendering the HUD again, loading a persisted aura, or reconciling a restart does not apply or refresh an aura and must not replay any of these gain or refresh events. Restoration of saved state remains distinct from a new gameplay application.

## Q122: different definitions of the same aura on one holder

Accepted: allow source contributions to merge under one aura only when their resolved aura definitions are identical. Compare the actual resolved definition rather than the whole content-revision ID, so an unrelated YAML hotfix does not make an unchanged aura incompatible.

Keep an existing application on its captured definition. If a new application of the same namespaced aura ID has a different resolved definition, report a version conflict instead of silently changing the old contribution, selecting a hidden winner, or applying both sets of gameplay effects. Apply the accepted required-operation error policy or a capability's explicit recoverable handling. Where the conflict is discoverable before start, report it before spawning or beginning the attempt.

An author can let the old aura expire, remove it explicitly, or use an explicit GM operation to replace it while the player is outside an active attempt. Replacement follows deliberate removal and application semantics rather than presenting a definition migration as a duration refresh. Pin the replacement to its selected published definition. Q274 defines the administrative syntax and replacement checks.

`has_aura` still checks the stable aura identity. A new encounter that merely uses a persistent attunement as a presence condition does not require that mark to be reapplied or migrated. Stack limits, display aggregation, and effect behavior remain those of the holder's actual compatible contributions. Meaningful gameplay changes can use a different authored aura ID when the author wants both states to coexist intentionally.

This gives up automatic mixing of old and new aura behavior to preserve the accepted revision and source-ownership guarantees. Publication itself does not remove existing auras. Removing a definition from the current catalog also does not discard an older definition still needed by a retained application or recovery record.

[Q125-Q126](aura-actions.md) accept the explicit aura action family, recipient forms, and deliberate removal or refresh across contributions. The accepted cleanup and source-lifetime rules above also govern [Q156-Q157 aura modifiers](aura-effects.md). Q158 native status-effect actions are separate: after handoff, Minecraft controls their lifetime, including offline timing. They are not contributions that aura cleanup can subtract.

[Q274](administrative-operation-contracts.md) accepts concrete administrative aura operations. [Q275](event-catalog-conventions.md) accepts the remaining typed lifecycle fields, count snapshots, reasons and duration-based refresh measurements. These contracts preserve the previously accepted lifecycle meanings.
