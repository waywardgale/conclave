# Manifest references and reuse

Status: Q78-Q82 are accepted. The accepted file shape uses `schema: 1` and one kind wrapper, with stable authored IDs, typed capabilities, and explicit reusable definitions. The fragments illustrate the accepted reference syntax; they are not complete executable specifications or bundled encounters.

## Q78: namespaced definitions

Accepted: keep each definition's `id` in `snake_case`. Add an optional document-level `namespace`, also in `snake_case`, defaulting to `local`. The full identity consists of kind, namespace, and ID. A namespace groups names; it neither grants permissions nor creates a separate publication unit.

An NPC manifest could begin:

```yaml
schema: 1
namespace: raid_tools
npc:
  id: shielded_guard
```

Within `raid_tools`, an NPC definition reference can use `shielded_guard`. Another namespace uses `raid_tools:shielded_guard`. An unqualified authored-definition reference searches only the referring definition's namespace. Never scan all namespaces or choose the first matching filename. Duplicate full identities fail validation, while different kinds may share the same namespace and ID because their reference fields are typed.

Reserve `conclave` for framework-provided definitions and capabilities. Authors can copy a provided definition into their own namespace and modify that copy. Imported definitions cannot replace framework-owned definitions. A registered behavior's `type` has a separate registry: the existing short form `type: capture` names the built-in capability, while extensions use their qualified registered names. Minecraft entity and asset identifiers keep their own registry rules.

Folders and filenames remain organizational choices. Moving a file does not rename its definition. Editor completion shows both display names and full identities when needed. Import reports collisions before replacing draft definitions, and unresolved external references fail validation; namespace references never download content or load arbitrary files.

## Q79: reusable mechanics and parameters

Accepted: use `type` to configure a registered capability directly. Use `use` to instantiate an authored reusable mechanic definition, with `with` supplying its declared parameters. A mechanic occurrence must specify exactly one of `type` and `use`.

Direct configuration inside an appropriate mechanic list:

```yaml
- id: north_capture
  type: capture
  area: north
  duration: 10s
```

The equivalent authoring shape for a reusable definition:

```yaml
- id: north_capture
  use: raid_tools:capture_plate
  with:
    area: north
    duration: 10s
```

`north_capture` identifies this occurrence; `capture_plate` identifies the reusable definition. Each occurrence has separate gameplay state and retains the existing phase-owned lifetime by default. This syntax does not introduce a new completion policy or imply that every mechanic is an objective.

A reusable mechanic declares parameters with types and optional defaults and constraints. The body uses a typed parameter reference, not string interpolation. A definition's opening and body might include:

```yaml
schema: 1
namespace: raid_tools
mechanic:
  id: capture_plate
  parameters:
    area:
      type: area
    duration:
      type: duration
      default: 10s
  type: capture
  area: {parameter: area}
  duration: {parameter: duration}
```

Parameters without defaults are required. Reject unknown arguments, incorrect types, and invalid constraints before an attempt starts. Bind configuration parameters once for an activation; changing runtime state uses declared gameplay actions rather than mutating configuration values. Parameter names do not make author text executable.

Definition-internal references resolve in the definition's namespace. Caller-supplied definition references resolve in the caller's namespace before being passed in; area and location parameters retain their typed arena binding. This prevents importing a mechanic from silently retargeting its internal references.

Permit composition and nested reuse with bounded expansion, but reject recursive definition cycles. Configure declared parameters instead of supporting inheritance, arbitrary deep overrides, filesystem includes, or expression strings. This contract specifies parameterized reuse for mechanics; ordinary references to NPCs, auras, and relic definitions retain their own typed fields. [Q89-Q90](conditions-and-composition.md) define sequence, parallel, and repeat composition. [Q165-Q166](reusable-mechanic-contracts.md) accept `type: layers` for local rules and state, plus `export.events` for explicit public event forwarding. The user selected both names. Each occurrence retains independent private state and the existing resource ownership rules.

[Q168](mechanic-start-and-parameters.md#q168-readable-typed-parameters-and-constraints) accepts the initial parameter type families, typed defaults, applicable bounds, and detailed selection/group binding behavior. Bound player filters evaluate current recipients when used, while spawn-group references retain their permitted producer and activation identity.

## Q80: encounter-to-arena bindings

Accepted: each arena declares its supported encounters. Each encounter refers to logical area and location IDs, and a supported pairing supplies the required placements. By default, `area: north` uses that arena's area named `north`. An optional per-encounter binding maps the logical ID to a differently named spatial definition in that same arena.

An arena attachment fragment could use:

```yaml
encounters:
  - encounter: training_encounter
    bindings:
      areas:
        north: north_plate
      locations:
        entrance: entry_anchor
```

Bindings have typed sections, so an area cannot be supplied by a location. Same-name matches need no explicit entry. The validator derives required logical references from the encounter and its configured reusable definitions, including conditional paths. Authors do not maintain a second handwritten list of every referenced area.

Validate every declared encounter/arena pairing before publication, and validate the selected pairing again before an attempt starts. A definition can be stored before it has an arena, but cannot start without a valid pairing. The Test draft operation validates the selected pairing using the draft's definitions. Resolve and pin all placements before starting, including those used by later phases and recovery.

Spatial references inside an attempt stay within its own arena. Reject another arena's location or area as a binding. This reference restriction does not impose physical confinement or combat isolation. Q103 preserves ordinary world interactions; authors can explicitly configure supported targeting or boundary behavior. [Q123](world-locations-and-assets.md#q123-location-anchors-outside-an-arena) accepts separate world-owned locations for outside-attempt recovery. Q127 defines their accepted manifest and reference syntax without turning them into arena bindings.

## Q81: references to active gameplay state

Accepted: distinguish static definitions from the objects and state created while an attempt runs. `use` references a reusable mechanic definition. A `group` reference identifies a spawn group belonging to the current attempt. A typed `area` or `location` reference identifies that attempt's bound spatial definition.

For state that can be phase-owned or encounter-owned, a short reference names the current phase's declaration. Use an explicit reference object for encounter scope. For example, a phase-local group reference is:

```yaml
group: guards
```

An encounter-owned group reference is:

```yaml
group:
  id: guards
  scope: encounter
```

Do not silently fall back from a missing phase-local name to an encounter-owned one. IDs must be unique within their kind and owning scope. Phase IDs themselves are unique within an encounter. Counter, timer, mechanic, and other scoped-state reference fields should use the same short-form and explicit-scope convention where the capability supports those lifetimes.

Lifetime and identity remain separate: `scope` on a creation determines ownership, while `scope` in a reference selects an existing declaration. [Q117](auras-and-world-lifetimes.md#q117-auras-that-outlive-an-attempt) adds explicit `scope: player` for aura applications only. It does not make player scope valid for arbitrary NPCs, timers, counters, or mechanic instances. A reference does not promote phase-owned state to encounter scope. Private IDs inside a reusable mechanic belong to that occurrence; outside rules use its documented public events and parameters rather than reaching into another occurrence's internals.

The engine binds references to the actual attempt and activation. A later activation that reuses the authored ID does not retarget an earlier objective or delayed event. Accepted Q64 still governs fixed membership, explicit open spawn groups, and the distinction between real defeat and disappearance. A declared future spawn is a pending target rather than an unknown name; Q93 defines pending producer binding and Q167 defines initialization and startup ordering.

An ordinary YAML reference cannot target another attempt or read a completed phase's discarded state. Encounter-owned counters or documented completion records can carry intentional history. Privileged administrative commands retain their explicit attempt target and separate authorization.

## Q82: global gameplay settings

Accepted: provide one Server settings page backed by a singleton `settings` manifest. It uses `schema: 1` and a `settings` root. Unlike named content definitions, this singleton has no authored `id` or `namespace`. Reject duplicate settings manifests instead of merging them by file order. Its filename is organizational; `settings.yaml` is the suggested name shown by the editor.

This manifest configures Conclave's global gameplay policy, including revival availability and timing, spectating and private-information defaults, and recovery health and hunger. It uses the already accepted defaults. [Q270](settings-and-spectator-controls.md#q270-group-global-gameplay-settings-by-revival-spectating-and-recovery) accepts the exact field names and grouping; Q82 settles the document boundary and source of authority.

Operators can edit these settings in Minecraft. GMs can prepare encounter content without gaining permission to modify global policy. Ordinary reusable content cannot embed settings that re-enable prohibited recovery methods or replace server policy. An import that includes global changes identifies them separately and requires operator authority before saving them.

Effective global gameplay settings are part of the complete published revision. Active attempts keep their starting policy. Outside-attempt deaths keep the settings captured for that death under Q44. New attempts and newly created outside-attempt death lifecycles use the latest applicable policy. Explicit encounter overrides remain limited to the already accepted global permissions.

Vanilla gamerules remain under vanilla administration. GM membership, publication history, retention policy, storage settings, and transfer limits are administrative or operational state outside this gameplay manifest and outside content rollback. Those controls must still be accessible through appropriate Minecraft administration screens or commands. Their individual change-timing policies remain separate decisions.

[Q259](item-text-and-reusable-items.md#q259-reusable-item-definitions-with-explicit-use-sites) accepts an item definition kind with a configured native `stack` and a typed `use` form for equipment/rewards. Quantity stays at the use site. This is separate from Q79 mechanic parameters and permits neither item inheritance nor inline property overrides.

[Q263](framework-delivery-and-extensions.md#q263-one-typed-extension-interface-with-engine-owned-lifetimes) accepts startup registration of typed Kotlin capabilities, with shared description/validation and a catalog fixed for the server session. This remains separate from authored definition namespaces and data publication.

[Q266-Q267](area-fields-and-membership.md) accept concrete arena spatial definitions and membership fields, preserving Q80's binding maps. [Q268-Q269](code-compatibility.md) accept installed-code compatibility without treating `schema: 1` as an addon API version or silently migrating authored data.
