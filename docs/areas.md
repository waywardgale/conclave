# Areas

Status: Q53-Q59 are accepted. Location anchors configure named areas, Creative mode supports toggleable area visualization, and the initial spatial editing and event rules are settled below. No geometry engine, visualization, or editing interface has been implemented.

## Accepted requirements and terminology

An `area` is a named region used by encounter mechanics and spatial rules. The user previously chose `area` over `zone`; the latest request adds zone creation and viewing without explicitly renaming the YAML concept. Continue using `area` in the schema and editor. Location anchor is the accepted name of the authoring block.

Location anchors support area configuration in their human-readable in-game editor. Authors can refer to those areas from YAML. Areas can be viewed in Creative mode, with a toggle controlling that visualization. The initial shapes, anchor relationship, membership defaults, and viewing policy are accepted below. Detailed geometry editing and event semantics follow in Q58-Q59.

Area definitions use the same YAML draft and publication workflow as [Location anchors](location-anchors.md). Publishing includes resolved geometry in the complete revision. Existing attempts keep their starting areas, including areas needed later in an attempt. Visualization settings affect what an author sees, not which geometry the server uses for gameplay.

## Q53: initial shapes and composition

Accepted: support a box, vertical cylinder, and sphere initially. The editor exposes width, depth, and height for boxes, radius and height for cylinders, and radius for spheres. Boxes suit rooms and corridors, cylinders suit capture plates and circular hazards, and spheres suit volumes around an object.

For irregular spaces, allow a named area to combine included regions and subtract excluded regions. This provides rooms with cutouts and disconnected platforms through reusable shapes. A combined area with no included region is invalid. Reference cycles are validation errors, and area evaluation has bounded composition depth and size. Q266 accepts `type: composite` with `include` and `exclude`; numeric bounds still require implementation evidence.

Q58 defines coordinate origins, rotation support, and corner selection. Every cylinder has a finite height. An arbitrary-polygon editor is not part of the accepted shape catalog.

## Q54: anchors and area identity

Accepted: one Location anchor may define several areas, each with its own `id` and optional display `name`. For example, `north_anchor` can locate both `north_plate` and `north_approach`. A location reference names a position, while an area reference names the selected region; the anchor does not make these interchangeable.

Areas attached to an anchor store their placement relative to it. Moving the anchor moves those areas in the next published revision, with an editor preview of the affected regions. Their IDs remain stable. Q58 defines how anchor facing and area rotation interact.

Authors can also define areas directly in YAML without placing an anchor. A physical block is an optional editing handle and never a runtime prerequisite for a valid published area definition. The editor should show which areas an anchor controls before moving or deleting it; exact rename and removal behavior belongs with reference editing.

## Q55: Creative visualization and permissions

Accepted: provide a per-player Show areas toggle, off by default, available through a rebindable key and the editing interface. Creative players may view area boundaries through this toggle; operators and GMs may also view them in edit mode without changing game mode. Creative mode grants area viewing only, while editing drafts and publication retain their existing permissions.

Render outlines, optional translucent fill, and area names. Distinguish a selected area without depending on color alone. Limit the normal overlay to relevant nearby areas and let the editor preview the selected area explicitly, so a large map does not display every volume at once. Exact viewing distance and filters remain presentation details.

Ordinary area viewing displays the current attempt's geometry when an attempt is active, otherwise the published geometry. Authorized editors can choose Preview draft; the overlay then visibly identifies the draft. The toggle does not change gameplay and does not publish anything. A regular Creative viewer receives area geometry and display metadata, not spawn configuration, draft changes, private mechanic state, or the full manifest.

This is an accepted exception for Creative area viewing to the previous editor-only authoring-label policy. Location anchor labels and configuration still require the accepted editor access. Leaving an eligible viewing mode disables its overlay and stops further privileged updates; permission revocation remains immediate.

## Q56: what counts as being inside

Accepted: a player's feet position determines membership by default. Other entities use their base position under the same geometric convention. Authors can explicitly request any body overlap or the whole body being inside when those better fit a mechanic. Q267 places `membership: position | overlap | contained` on the named area, with `position` as the default for all its consumers.

Included outer boundaries count as inside; Q266 explicitly removes excluded shapes and their boundaries from a composite. Areas can overlap, and membership in one does not exclude membership in another. The server evaluates membership against the attempt's resolved geometry; the editor overlay is not gameplay authority. Participant eligibility is separate: dead and disconnected players do not become valid capture contributors merely because their stored positions lie inside an area.

Q59 defines enter and exit initialization and teleport behavior. Any opt-in boundary tolerance remains a separate possible feature; the accepted position test adds no automatic hysteresis or invisible grace margin.

## Q57: arena ownership and reusable references

Accepted: an arena's spatial definitions own its locations, anchors, and areas. An encounter refers to logical IDs such as `north_plate` or `entrance`; each arena supplies its corresponding placement. This lets two arenas reuse one encounter without sharing world coordinates or copying encounter logic.

IDs are unique within their kind in an arena. Typed references distinguish a location from an area even if an author uses the same ID for both. Names are display labels and do not determine identity. Validate all required references before publishing or starting an attempt; report the owning arena and the missing or wrong-kind reference.

Arena-local ownership and typed IDs are accepted. Q123/Q127 also accept world-owned locations and their typed references for purposes outside encounter bindings. Q58 defines local area coordinates. [Q80](manifest-references.md#q80-encounter-to-arena-bindings) accepts supported encounter/arena pairings and rejects cross-arena bindings. [Q69](execution-and-errors.md#q69-failure-categories-and-bounded-work) defines engine-error handling. [Q100-Q103](arena-placement-and-admission.md) retain authored geometry and engine occupancy while preserving ordinary Minecraft world behavior. The boundary is not an automatic movement or interaction restriction.

## Q58: geometry origins, rotation, and editing

Accepted: boxes and cylinders extend upward from a base position, while spheres extend around a center position. Attached areas use the Location anchor plus an explicit offset for that position. Dimensions are in blocks, support fractional values, and must be positive and finite. The editor labels the base or center clearly and previews the resulting geometry.

Allow rotation around the vertical axis for boxes and attached offsets. Turning an anchor rotates its attached areas with it; an area can add its own horizontal rotation. Cylinders remain upright and spheres do not need a shape rotation. Full three-axis rotation is outside this accepted initial catalog. Q266 accepts the concrete `position`, `location`, `offset`, and box `rotation` fields.

Provide selection of two opposite corners for creating a box, plus editable dimension and rotation controls. The editor converts the selection into the area's local representation and shows the published result in preview; authors need not calculate offsets manually. Corner selection includes the selected blocks in the initial box. Direct numeric editing remains available for precise placement.

## Q59: initial occupancy and spatial events

Accepted: when a mechanic or rule starts observing an area, initialize its occupancy from the current positions immediately. A capture mechanic therefore counts players already inside without requiring them to step out and return. This initial population is a starting state, not a manufactured `entered` event.

After initialization, `entered` and `exited` describe changes in geometric membership at the server's simulation updates. A teleport compares the departure and destination membership; it does not traverse intermediate areas. Full pass-through between two observations is not an entry/exit pair under this model. A future crossing detector would be an explicit separate behavior if required.

Death and disconnection change gameplay eligibility, so capture contributions update even if the player's last position remains inside. They do not manufacture spatial exit events. Removing an observation on phase cleanup does not emit gameplay exit events or their punishments. Authors who need an effect immediately on activation can use the activation event and a current-area selection.

A mechanic's observation lifetime follows its accepted phase or encounter scope. [Q246](simulation-stages-and-outcomes.md#q246-ordinary-work-before-timed-work) accepts spatial observations after admitted ordinary observations and before due Conclave work, with valid state-change reconciliation and no duplicate progress. Event payload details and exact native observation hooks remain part of the event registry and implementation verification.

[Q264](movement-and-simulation.md#q264-player-travel-preserves-participation-and-uses-dimension-aware-spatial-checks) accepts explicit same-dimension geometry and distance checks, including ordinary endpoint-based area events for native dimension travel. This adds no intermediate crossing detector or implicit participant filter.

[Q266-Q267](area-fields-and-membership.md) accept the concrete geometry fields, include/exclude grammar, subtraction-edge treatment, and per-area `membership` setting. The accepted shape and membership capabilities above use those field and placement contracts.

[Q275](event-catalog-conventions.md) accepts the area event source forms, typed player/area fields and selector handling. The accepted physical membership and initialization rules remain unchanged.
