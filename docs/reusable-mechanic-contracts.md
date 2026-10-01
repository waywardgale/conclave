# Reusable mechanic bodies and public events

Status: Q165-Q166 are accepted with the user's naming changes: the mechanic type is `layers`, and the public interface block is `export`. Typed parameters, `type` versus `use`, independent occurrences, sequence/parallel/repeat composition, private internal names, scoped cleanup, and typed event rules are accepted. The development compiler and runtime implement these composition types and explicit event forwarding. See [implementation status](implementation-status.md) for supported value kinds, native adapters and remaining release work.

## Q165: a layers mechanic with local rules and state

Accepted with the user's rename: add `type: layers` for a mechanic assembled from objectives, background mechanics, rules, counters, and timers. Use the same section names and condition grammar as phases. Support it inline or as the body of a reusable `mechanic` definition under Q79, so authors can package behavior without introducing Kotlin when existing capabilities suffice.

```yaml
schema: 1
mechanic:
  id: hold_two_plates
  type: layers
  deadline: 30s
  objectives:
    - id: north
      type: capture
      area: north
      duration: 5s
    - id: south
      type: capture
      area: south
      duration: 5s
```

This illustrative definition succeeds when both captures have completed, or fails if its deadline expires first. Capture completion remains latched under Q27; the fragment does not require both areas to stay occupied simultaneously after capture. A caller can place the definition under `objectives` with `use: hold_two_plates`, or run it as background under `mechanics`. It is not a shipped encounter or a new preset promised in the framework bundle.

### Completion and failure

With a nonempty `objectives` list, all objectives are required by default. Optional `complete_when` replaces that default completion expression. `fail_when` adds a failure condition, while a terminal failure of a required child remains a failure under the existing objective contract. Background child mechanics do not become completion requirements merely because they run inside the layers mechanic.

Support optional `duration` for timed success and `deadline` for timed failure, using the simulation clock and the same explicit-composition rules as Q26 and Q83. Do not guess an AND/OR policy when the author supplies competing shorthand success criteria. Untimed layers mechanics are valid. With no objectives, `complete_when`, or success duration, a layers mechanic stays running until cancellation or failure; it may be background but cannot be an objective that depends only on its impossible natural completion. An empty objectives list never produces immediate success.

Resolve same-tick success and failure together under the enclosing encounter's accepted precedence policy. Report `completed` or `failed` once for that activation. The caller decides what that result means through the existing objective, composition, or background rules. A layers mechanic cannot select an encounter's next phase, declare encounter success, or perform a gameplay wipe through a private phase route. Its body has no `phases`, `start`, `success.next`, or `failure.wipe` fields. Technical errors retain Q69 and cannot be downgraded into an ordinary child failure.

### Independent state and bounded lifetime

Each occurrence has separate private children, counters, timers, rule limits, and completion records. Two uses of this definition can both have a child named `north` without sharing progress. Local references resolve inside that occurrence, with the existing explicit scope rules for supported enclosing state; they never search another occurrence for a matching name. Typed configuration parameters bind through the existing `{parameter: ...}` form.

Initialize declared local state and subscriptions before child activation can publish its first observable events. Start child occurrences in their declared order within each section, with objectives before background mechanics when both sections exist. Queue resulting events through Q68 rather than invoking rule handlers during partial initialization. Accepted [Q167](mechanic-start-and-parameters.md#q167-a-started-event-before-child-mechanics-begin) adds the standard `started` event and `source: self`, with startup rules processed after local initialization and before child activation. These remain ordinary typed rules; Q224/Q243-Q245 define the accepted lifecycle payloads and Q275 defines common catalog conventions.

After the layers mechanic finishes or is cancelled, stop its unfinished children, local timers, local subscriptions, and other private future work. Retain its terminal result in the enclosing activation for the existing completion checks. A later repetition or phase re-entry creates fresh private state; stale work cannot target that replacement. Sequence and repeat still advance no earlier than their accepted next-tick boundary.

Do not change the lifetime of world resources merely because the action that created them appeared in a layers mechanic. NPCs, aura contributions, native effects, presentation, and block edits keep their documented ownership and cleanup rules. Explicit phase- or encounter-owned state can outlive the private child that created it under Q89; native status effects retain Q158's handoff. Ending a layers mechanic is not an implicit inventory or health rollback. A reference into its discarded private state cannot remain usable just because one resource has a longer supported owner.

Use `sequence`, `parallel`, and `repeat` directly when their smaller shape expresses the behavior. `layers` adds local orchestration with existing actions and conditions, not arbitrary functions, recursive definitions, mutable configuration, or a second scripting language. Validate cycles and bound expanded definitions, active children, timers, and queued work under the existing execution limits.

## Q166: explicitly exported public events

Accepted with the user's rename: allow a reusable mechanic definition to declare `export.events`. Each named public event forwards one declared internal source/event through the familiar `on` shape, with optional `if` and an explicit `data` mapping. This also works for the already accepted sequence and parallel definitions; it does not require choosing `layers` as the body's type.

```yaml
export:
  events:
    north_captured:
      on:
        source: {mechanic: north}
        event: completed
      data:
        side: north
```

The fragment belongs to a reusable definition with a direct child named `north`. An enclosing rule addresses the occurrence it instantiated, such as `source: {mechanic: ritual}`, with `event: north_captured`. It does not need to know the internal child ID, capture implementation, or timers. This permits the definition to change its internals while retaining the same public event contract on future revisions.

Validate every export against its internal source's event schema. For a nested reusable child, only that child's public interface is visible; exports cannot use dotted paths to reach private grandchildren. The mapping is one source/event per exported name initially. An author who needs several differently named public outcomes declares those separately; event-name computation and an unrestricted global event bus are not introduced.

### Explicit payloads

`data` is optional and defaults to no author-defined fields. Copy no internal payload automatically. Allow documented typed event-value references, bound parameter values, and supported scalar literals as mapped values. A mapping such as `amount: {event: amount}` preserves that registered field's numeric type and units. The compiler derives the public schema from these typed values and displays it in the editor. It must not erase units into untyped strings or accept a missing source field.

Optional source fields stay optional in the public schema. An absent optional input produces an absent output field, not a fabricated zero, empty identity, or stale value. Do not infer requiredness from a guard as a substitute for a declared schema. Reject conflicting field definitions, unsupported value kinds, and attempts to overwrite reserved runtime provenance. Arbitrary property traversal, expressions, client code, whole-payload copying, and serialized native objects are not allowed.

[Q223](event-players-and-mechanic-results.md#q223-an-explicit-player-field-for-a-subscription) accepts an explicit `on.player` matching contract and rules for retaining an explicitly forwarded triggering-player identity. Ordinary guards still cannot strengthen optional fields.

Supported player/NPC identities and bound area/location values can be forwarded when their source contract permits it. They keep their original identity and lifetime; an export never promotes an object to a longer owner or grants access to another attempt. Do not export private counters, timers, subscriptions, or producer handles as mutable public state. Additional output kinds need a registered, documented type contract.

An exported event is server-side information for its enclosing mechanic or encounter. It does not send a hidden clue or private aura field to clients. Subsequent presentation must still choose an authorized audience. Renaming a field does not remove the source's privacy obligations.

### Delivery and outcomes

For each valid matching internal event, evaluate the export's guard under the ordinary event contract. If true, capture its mapped values and queue one public event under this reusable occurrence's public identity. Simultaneously matching export declarations use declaration order. Retain internal provenance for diagnostics and stale-activation checks without presenting the private child as the public source.

Exports are notifications. They do not themselves complete or fail the mechanic, reset it, or run actions. Reserve the body's built-in public lifecycle event names, including its completion and failure events, so an export cannot forge `completed` while the mechanic is still running. A false guard forwards nothing. Unknown exports and mismatched payload use fail validation.

An admitted terminal child event can be observed while the enclosing occurrence processes its result. Ended subscriptions cannot later forward new events, and an old queued event never changes its activation identity to match a new use of the same name. Keep ordinary event ordering, cancellation, and work limits; do not run subscribers recursively or replay exports on reconnect, restart, or publication. Consumers can use `event_value`, existing subject predicates, `once`, and cooldowns through the accepted rule interface.

Arbitrary authored `emit_event` actions, stored public output values, cross-attempt messaging, and mutation of another occurrence's private state are outside this accepted first export form. Further output capabilities should be added only with an explicit typed lifetime and delivery contract.

[Q167-Q168](mechanic-start-and-parameters.md) accept startup events and detailed parameter constraints. `started` runs once per real activation; parameter bindings preserve caller references and the distinction between a live player query and a captured group identity. The accepted names remain `layers` and `export`.

[Q244](phase-events-and-rule-order.md#q244-stable-reaction-order-across-rule-scopes) accepts reaction order across scopes. Ordinary rules precede exports within the same listening activation, so export guards see earlier committed local changes; the exported public event still joins the queue without recursive dispatch.

[Q245](ending-scope-presentation.md) accepts a bounded presentation-only exception for a scope's own terminal rules. It permits immediate best-effort finite cues after committed ordinary results, with no gameplay continuations or arbitrary cleanup callbacks.
