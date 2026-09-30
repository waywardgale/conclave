# Area fields and membership

Status: Q266-Q267 are accepted. Q53-Q59 already select the initial shapes, relative placement, visualization, membership behaviors, and observation semantics. These contracts settle their YAML fields and geometric interpretation. No schema or geometry implementation exists.

## Q266: one geometry format for arena boundaries and named areas

Accepted: use `type: box`, `cylinder`, `sphere`, or `composite`, with readable dimensions and explicit placement. Store named areas in the arena's `areas` list, using the existing `id` and optional `name` convention. Use the same geometry format for `boundary` and composition children, without adding a separate top-level area manifest.

### Shapes and placement

| Field | Meaning |
| --- | --- |
| `type` | `box`, `cylinder`, `sphere`, or `composite`. Required; no inferred type. |
| `position` | Explicit world `{x, y, z}` for a primitive shape. Uses the arena's dimension. |
| `location` | An arena-local location ID providing relative placement instead of `position`. |
| `offset` | Local `{x, y, z}` displacement from that location, defaulting to zero. Valid only with `location`. |
| `rotation` | Horizontal rotation in degrees for a box, defaulting to zero. Relative to the referenced location's yaw when attached. |
| `width`, `depth`, `height` | Positive finite box dimensions in blocks. |
| `radius`, `height` | Positive finite cylinder dimensions in blocks. |
| `radius` | Positive finite sphere radius in blocks. |

A primitive supplies exactly one of `position` and `location`. Coordinate mappings contain all three finite numeric components; omitted `offset` means the complete zero vector. Do not accept coordinate strings, an implied current player position, or world-location fallback for an arena-local reference. Directly placed shapes do not require a Location anchor block.

The position is the horizontal center of the base for a box or cylinder, and the center for a sphere. A box's local width follows X and depth follows Z before rotation; its height extends upward. Use the same yaw direction as the existing location `facing.yaw`, with zero aligned to the world axes. A referenced location's yaw rotates the horizontal offset and establishes the box's base orientation; the box's own `rotation` adds to it. Pitch never tips these upright shapes. A cylinder or sphere can have a location-relative offset but has no `rotation` field because rotation does not change its shape. Reject irrelevant shape fields.

Use arena `locations` as named list entries with the same `id`, optional `name`, `position`, and optional `facing` conventions as Q127, inheriting the arena dimension. Their existing optional anchor/spawn capabilities remain separate from area geometry. Both kinds retain independent local identities and typed references. The editor can move a location and preview every attached area without changing their IDs.

The following is an arena fragment illustrating these fields, not a bundled encounter or a complete launchable definition:

```yaml
arena:
  id: hall
  dimension: minecraft:overworld
  boundary:
    type: box
    position: {x: 0, y: 64, z: 0}
    width: 40
    depth: 40
    height: 16
  locations:
    - id: plate_origin
      position: {x: 0, y: 64, z: 0}
      facing: {yaw: 0, pitch: 0}
  areas:
    - id: plate
      type: cylinder
      location: plate_origin
      radius: 5
      height: 3
```

The complete file keeps Q70's `schema: 1` header and its single `arena` wrapper. All coordinates and dimensions are ordinary authored data. No generated ID, native block presence, or separate coordinate language is required.

### Composition

Use `type: composite`, a required nonempty `include` list, and an optional `exclude` list. Each list entry is either an arena-local area ID or an inline geometry mapping. Inline geometry has no `id`, display `name`, or membership policy. References reuse the referenced area's geometry, not the behavior of its consumers.

```yaml
areas:
  - id: usable_floor
    type: composite
    include: [north_floor, south_floor]
    exclude:
      - type: cylinder
        position: {x: 0, y: 64, z: 0}
        radius: 2
        height: 4
```

This second fragment assumes `north_floor` and `south_floor` are declared in the same arena. The resulting volume is the union of included volumes with the union of excluded volumes removed. Order does not affect membership, and overlapping includes do not count a player twice. Exclusion wins where the sets overlap. An excluded shape includes its own boundary, so touching that cutout is outside the resulting area. This makes the subtraction edge explicit alongside Q56's included outer-boundary rule.

An inline child may itself be composite. Resolve references with cycle detection, bounded depth, and bounded total expanded complexity. Reference reuse must not evade work limits. Reject missing or wrong-kind references and an empty `include`; an empty `exclude` means no subtraction. A composite has no additional `position`, `location`, `offset`, or `rotation`. Place its primitive leaves, which can share one location when they need to move together.

`arena.boundary` accepts an inline geometry mapping or a local named-area reference. Resolve geometry first, then validate the existing containment rules for every named gameplay area and location. Inline boundary components are construction geometry, not additional bindable gameplay areas. This permits a boundary with a cutout without requiring an intermediate named area that extends beyond that boundary. A referenced named area remains subject to ordinary containment. A boundary needs a usable nonempty finite volume; the shape engine must verify supported compositions within its limits before advertising support.

The in-game editor exposes the four types with shape controls, a searchable include/exclude picker, and a preview of the final volume. Two-corner box selection writes the same centered-base representation and includes the selected blocks under Q58. Draft round-tripping and publication retain the accepted YAML-preservation and immutable-attempt rules. Exact geometric algorithms, tolerances for numeric computation, and measured limits are implementation work; they cannot introduce visible padding, approximate away a cutout, or accept a shape the runtime cannot evaluate correctly.

## Q267: name the three membership modes and apply them to the final volume

Accepted: add optional `membership: position | overlap | contained` to a named area, defaulting to `position`. Every consumer of that area uses the same test. Keep geometry reuse separate so authors can give the same shape different named membership policies when necessary.

| Value | Entity is inside when |
| --- | --- |
| `position` | Its feet/base position lies in the area. This is the accepted default. |
| `overlap` | Any part of its current native body bounds intersects the area. |
| `contained` | Its entire current native body bounds lie in the area. |

Use native physical bounds, including supported physical size and pose changes, rather than custom model visuals, particles, equipment art, or the camera position. Each query requires the same dimension under Q264. Missing or unavailable bodies do not acquire an invented position or zero-sized box. Their capability's eligibility and availability policy still decides how to handle them.

Evaluate the selected mode against the final composed volume. For `contained`, an entity can span adjacent included pieces if their union contains its whole body, but it cannot span an excluded hole. Merely testing body corners is not sufficient for general composites, including cutouts or disconnected unions. For `overlap`, intersecting an included piece only inside an excluded region is insufficient. Touching an included outer boundary counts as intersection; points removed by exclusion do not count. Geometry libraries and tests must establish these results before the capability is supported.

An area's membership policy applies consistently to occupancy, capture eligibility, spatial selectors, and enter/exit observations that refer to that area. Life state, admission, visibility, and interaction reach remain separate capability checks. Native movement or a body-size/pose change can change geometric membership at the ordinary spatial observation stage; death, disconnect, initialization, and observation teardown keep Q59's explicit event rules.

Membership belongs to the named area in the initial vocabulary. Do not introduce an implicit per-mechanic override or change a referenced area's mode when another consumer starts. If two behaviors need the same geometry with different tests, define a second area with one included reference and its own mode:

```yaml
areas:
  - id: plate_touch
    type: composite
    include: [plate]
    membership: overlap
```

The fragment references the earlier `plate` geometry. The original area's default remains `position`. The membership field is optional and invalid on inline geometry or an arena boundary mapping. When a boundary references a named area, it uses the geometry only. Authored containment, safe spawn/recovery placement, and retained-chunk calculations use their own accepted geometry and physical-safety checks; an area's mode cannot relax them.

Changing either geometry or membership publishes a new future-attempt revision. Existing attempts retain both. The overlay identifies the selected area's test in readable terms, such as Feet/base position, Any body overlap, or Whole body inside. It still displays the same geometric region, without secretly growing it for the chosen test.

## Related contracts

These contracts complete fields left open by [areas](areas.md), [Location anchors](location-anchors.md), [arena placement](arena-placement-and-admission.md), and [typed arena bindings](manifest-references.md). They reuse [world-location coordinate conventions](world-locations-and-assets.md#q127-world-location-manifests-and-references) and [accepted travel semantics](movement-and-simulation.md). Q266 selects geometry fields; Q267 selects the location and names of an already accepted membership choice. Either decision can be changed independently without removing the other capability.
