# Travel, remote graves, and NPC simulation

Status: Q264-Q265 are accepted. They finish the ordinary-movement boundary of the accepted roster, revival, NPC ownership, and bounded arena-retention contracts. No movement adapter, grave implementation, ticket manager, or runtime verification exists.

## Q264: player travel preserves participation and uses dimension-aware spatial checks

Accepted: keep an admitted player's existing attempt identity and gameplay state when they travel, including through a native dimension change. Apply the same revival policy at a usable remote death location, and keep the already accepted attempt-end regrouping. Travel alone introduces no departure penalty, new participation state, or reconnect grace.

### Continuing membership and gameplay

Q109 already keeps the captured roster until attempt end, and Q116 already permits selection of a living player in another dimension. Extend that same meaning consistently through later travel. Distance and dimension changes do not remove or readmit a participant, reset progress, clear roles/counters/auras, turn combat off, or open a second attempt slot. The prohibition on simultaneous attempt membership still applies. Death, disconnect, grace expiry, an explicit privileged operation, and normal attempt completion keep their own accepted transitions.

Encounter/phase combat remains the classifier for that participant even far from the arena. Simulation-time work follows its declared owner; it does not pause because the player left the arena's retained chunks. A player-owned aura retains its separate lifetime. Periodic aura effects still require an eligible living holder available to the supported simulation and never accumulate missed pulses. This policy does not turn unavailable native operations into successful remote effects.

A living, actively admitted participant elsewhere still counts as a survivor under Q40. Leaving the fight therefore does not automatically cause party defeat, even if everyone remaining in the arena has died. Authors can deliberately express spatial requirements, deadlines, or failure rules with the accepted vocabulary. Do not introduce an implicit presence requirement to compensate for a design that leaves no way to finish. Completion reward qualification keeps its captured-roster default and explicit authored filters.

### Spatial behavior

Every resolved position includes its dimension. Membership in an area requires that dimension to match before testing geometry; equal coordinates in another dimension are outside. Distance/radius checks compare positions only in the same dimension. A cross-dimension move compares actual departure and destination membership under Q59, with no intermediate path or coordinate scaling. A tracked participant leaving an observed area through a portal can therefore produce its ordinary `exited` event.

Nonspatial participant selection adds no arena or dimension filter. Direct participant HUD/text/audio cues can use their accepted audience rules across dimensions. Positional sounds, interactions, assisted revival, and native actions retain their own same-dimension, reach, visibility, target-availability, and adapter requirements. Being in the roster alone cannot satisfy them. Existing target-binding and stale-target rules continue to apply when a move changes an operation's physical preconditions.

Reconcile a supported player transfer as one identity change before classifying a temporary native removal/reference change as loss. A portal transition is not a disconnect, death, revival, or new admission. An actual death/respawn accompanying a transfer still uses the proper lifecycle path once. Track logical player identity independently of whether a particular native operation retains or replaces its Java object. Do not invent a new grace timer or replay completed player events to cover an integration failure.

The managed relic policy remains independent: a relic leaving its permitted arena returns to its captured home with the accepted `left_arena` reason, releases only its own carry effects, and neither counts delivery nor schedules a second respawn. The holder can continue traveling.

### Graves outside the arena

Use the actual death position and dimension when usable, including outside the arena. This clarifies Q46's existing preference for the death location and makes a runtime grave an exception to Q100's containment rule for authored encounter locations. It does not create an author-bindable location or a second arena. Record the original death position even when safe placement needs a fallback.

If that position is unusable, retain Q46's attempt fallback order: the most recent usable standing location in the captured arena, then its authored recovery location. Do not invent a corresponding coordinate in another dimension or search unlimited terrain. Outside attempts, retain the existing normal-respawn fallback. Validate the chosen destination again at revival; if all permitted destinations are unavailable or unsafe, preserve the recovery obligation and expose the accepted actionable error instead of duplicating a grave or forcing an unsafe revival.

An ordinary helper still needs to be alive, online, actively admitted to the same attempt, and physically in the grave's dimension within the configured reach and line of sight. An outsider near a remote grave does not become an eligible helper. The dead participant keeps the constrained grave camera and captured revival availability, timing, health, and protection. Leaving the arena does not grant voluntary normal respawn during an attempt or expose another player's private information.

The grave's logical record and remaining timer do not depend on a decorative native entity remaining loaded. Keep enough owned state to present the same grave when the relevant world and client are available. A disconnect does not require retaining its remote terrain forever; the combat window still spends simulation time under the existing policy. A returning eligible player uses the original grave and remaining window, subject to safe placement and the accepted reconnect rules.

Any additional chunk claims needed for an online grave camera, actual revival, or recovery must fit a bounded stationary footprint and the shared resource accounting. Include any required per-participant allowance in attempt admission; reserve cleanup/recovery capacity. Release claims when that operation no longer needs them. Do not grow the arena footprint along a player's route, keep every visited region loaded, or silently offer unbounded spectator-driven tickets. Exact claims and readiness hooks require verification of the chosen player/camera implementation. A confirmed inability to perform required work follows Q69, with recovery records retained; it does not create an authored wipe or extra death.

After the attempt ends, Q66 still regroups all participants at the captured recovery Location anchor, including living participants who traveled away or changed dimension. This existing recovery operation is separate from an automatic return on crossing a boundary. Apply the established safe-destination, offline-recovery, and occupancy-conflict checks, restore only Conclave-changed camera/game mode and state, and never repeat death consequences.

## Q265: follow owned NPC identity while simulation is available

Accepted: allow ordinary owned-NPC travel beyond the arena boundary and through supported native dimension transfers, while keeping the arena's retained footprint fixed. Continue tracking an available NPC; interrupt an attempt when a required living member loses supported simulation. Preserve ownership of unloaded optional descendants for later cleanup without inventing a defeat or respawn.

### Boundary, retention, and availability

The arena boundary and the chunks receiving usable simulation are different sets. Crossing the authored boundary is not itself an engine error or automatic return. Without explicit confinement, an owned NPC may keep roaming and fighting outside it wherever Minecraft continues to supply the required simulation. An adjacent region or another player's normal chunk loading can make that possible; Conclave does not guarantee how long that incidental availability lasts.

Keep Q107's admitted finite chunk footprint. Do not acquire moving tickets to follow a mob, enlarge retained terrain without admission, or keep a destination dimension loaded indefinitely. Q217's distance/random-despawn prevention continues while ownership is active, but it does not itself keep chunks ticking. Explicit confinement retains its accepted safe return and `escaped` behavior; a `wander` region still does not silently become confinement.

An accessible entity reference is insufficient evidence of simulation. Determine readiness using the supported native integration at a stable observation point, after reconciling in-flight native transfers. Normal server lag, a paused simulation clock, or the intermediate steps of one verified transfer are not automatically loss. Conversely, do not wait for a disk-unload callback when a required body has already stopped receiving the necessary simulation.

For a living named-group member, or another NPC whose continued simulation is required by an active capability, confirmed loss invokes Q69's documented recoverable outcome if one exists; otherwise stop the affected attempt with a technical interruption and participant recovery. Do not silently suspend a phase, shrink a group, complete `defeat`, heal or recreate the NPC, or apply gameplay wipe punishment. Diagnostics identify the owned member, last verified position/dimension, and lost readiness. A mob outside guaranteed retention can be identified in GM inspection before an actual interruption.

An ungrouped native descendant with no required simulation dependency can follow ordinary native non-ticking/unloaded behavior without stopping the attempt. Retain its verified identity, ownership and scope; do not infer a terminal ending. Logical Conclave clocks keep their existing scope rules, while native body behavior follows actual native simulation. Resume only still-valid owned state if it becomes available before scope end, with no missed periodic-effect backlog or replayed creation events. Being ungrouped cannot exempt an entity that a supported capability actually requires. This introduces no author-facing `optional` switch for suppressing contract errors.

### Native dimension transfer

For supported transfers, follow the verified original-to-destination operation and bind the logical member, aura holder and cleanup ownership to the resulting body. Preserve its group membership, defeat accounting, captured definition, runtime controls, remaining timers and supported native state. A same-type dimension transfer is not the native type conversion described by Q206 and emits no fabricated `converted`, `spawned`, `defeated`, or descendant event. It must not rerun initial health, equipment, variant, or reward configuration.

Previously captured native-body references remain historical under the existing target-binding contract; they are not permission to retarget an unrelated entity. Future logical-member selections use the verified current body. Matching a UUID alone after losing the transfer relationship is not permission to adopt an unknown mob, even though the ordinary native path preserves UUID. Cleanup ownership must cover partial transfer failures and verified replacement bodies without deleting unrelated entities.

Once transfer finishes, apply the same simulation requirements in the destination. A supported, ticking destination permits continuation. An unsupported transfer or confirmed loss of required readiness follows the existing technical-error contract. Do not prevent portal use, place a barrier, or teleport the NPC home unless the author explicitly selected confinement. Ordinary native combat and actual death at an available destination retain the accepted outcome, attribution, and native loot rules.

### Cleanup and authored scope

When ownership ends, cancel owned work and remove verified accessible bodies through cleanup, producing no defeat or death rewards. For a body saved in unavailable terrain, retain durable cleanup intent and enough identity to recognize it later. Before a supported reload can resume its gameplay, reconcile that ended ownership and remove the stale body. Do not revive an ended attempt, rerun spawn configuration, or keep the entire world loaded to make an unavailable cleanup record disappear. Record unresolved work in the existing in-game diagnostics and apply bounded recovery capacity.

This extends Q120's restart cleanup requirement to ordinary unload/reload and native transfers. A committed encounter result cannot be rolled back by a later cleanup failure. Q254's unresolved completion-write handling remains separate from precommit gameplay errors. None of these paths justifies generating replacement loot or replaying rewards.

The authored arena layout remains in one dimension. Q264-Q265 clarify Q100's exclusion as excluding multi-dimension arena geometry, bindings and guaranteed retained layouts, while allowing ordinary travelers to retain their state. Existing manifests still cannot bind an area or gameplay location from a second dimension. Continuing to account for a traveling player or NPC does not promise a supported authored cross-dimension encounter layout.

## Evidence and related contracts

[Native movement research](entity-movement-research.md) distinguishes player identity, mob object replacement, removal, tracking, and actual entity ticking in 26.2. Public after-transfer callbacks and load/unload callbacks alone do not prove that the accepted ownership and readiness contracts work. Required integration checks include portals, player respawn, replacement failure, NPC ticking loss before unload, remote graves, final-tick death/transfer ordering, and cleanup after returning to an ended scope. No such runtime verification has been completed.

These contracts refine [activation and roster admission](encounter-activation.md), [ordinary movement and confinement](npc-and-boundary-policies.md), [single-dimension placement and bounded retention](arena-placement-and-admission.md), [areas](areas.md), [revival and recovery](death-and-revival.md), [NPC lineage](npc-lineage.md), [NPC endings and persistence](npc-lifetimes-and-variants.md), [execution errors](execution-and-errors.md), and [durable cleanup](cleanup-and-restart.md). Player travel/revival and owned-NPC simulation are independent choices under the already accepted ordinary-world policy.
