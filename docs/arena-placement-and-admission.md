# Arena placement and admission

Status: Q100-Q101 and Q107 are accepted. Q102 rejected invisible walls; Q103 further replaces automatic arena isolation with ordinary Minecraft behavior. Arena-owned spatial IDs, reusable encounter bindings, one attempt per arena, and pinned placement remain accepted. Earlier automatic confinement and player-return defaults are superseded by Q103; authors may configure explicit encounter behavior. These contracts describe required behavior; they do not claim that every external mod's movement or damage code has been integrated.

## Q100: dimensions, coordinates, and containment

Accepted: each arena belongs to exactly one explicitly named dimension and has one finite boundary built from the accepted area shapes or their include/subtract composition. Multi-room and disconnected regions in that dimension are possible. Multi-dimension authored arena layouts remain outside the initial scope. Q264-Q265 preserve tracking and ownership when ordinary players or NPCs travel to another dimension.

Use explicit world coordinates for standalone named locations and Location anchors. Preserve the accepted anchor-relative offsets and horizontal rotation for attached areas. Directly placed areas use world coordinates for their base or center. Do not add a second arena-wide origin or coordinate transform in v1. Authors can use in-game placement and corner selection without calculating coordinates, and arena copying remains deferred to v2.

Require all authored gameplay areas, resolved gameplay locations, recovery destinations, NPC confinement regions, and permitted spawn fallback positions to lie within the boundary. Q264 permits a runtime grave at a usable actual death location outside this boundary; that grave is not an authored encounter binding. Containment covers the full authored area, including composed geometry, rather than merely checking its center. A boundary point counts as inside under Q56. Placement safety and actual entity dimensions remain separate checks at use time. A search radius is clipped to valid positions within the arena, not a license to place someone outside it.

Validate the relationship using the complete candidate revision before publication and revalidate world availability before attempt start. An error identifies the arena and the offending spatial definition. Physical terrain can change after validation, so placement still needs the accepted runtime safety check. Limits on geometry size and complexity follow the bounded-work contract.

An explicitly configured outside access or return position is administrative placement rather than an encounter-bindable gameplay location. It may be outside the boundary by explicit purpose. Q102 removes the proposed requirement for such a position to enforce outsider entry restrictions; an arena does not need one merely because outsiders can walk through it. Q123/Q127 accept world-owned locations and their typed references. [Q264-Q265](movement-and-simulation.md) accept cross-dimension travel and remote graves while retaining one-dimension authored arena layouts.

## Q101: overlapping arenas and occupancy

Accepted: permit arena definitions to overlap for alternative uses of the same build, but refuse to start an attempt if its resolved boundary intersects a currently occupied arena in the same dimension. Shared boundary points count as intersection, consistently with the accepted inside test. Authors should leave space between arenas intended to run concurrently. Overlapping mechanic areas within a single arena remain valid.

Check against each existing attempt's pinned geometry, not just the latest published arena coordinates. Also reserve the stable arena identity, so publishing a moved arena does not allow two attempts for the same arena. Concurrent start requests must acquire their reservations as one server decision before spawning or moving participants. The losing request explains which occupied arena conflicts.

The reservation includes preparation and world cleanup or participant recovery that still needs exclusive use by one Conclave attempt. This reserves engine ownership; it does not exclude ordinary players or change world physics. Release it when those operations have finished. A failed start releases its reservation after cleaning up anything it created. An abandoned draft or mere arena definition holds no reservation. Draft test attempts use these same rules.

Pending recovery for an offline player does not reserve the arena indefinitely. When that player returns, their retained recovery operation must revalidate against current occupancy and wait for a safe permitted destination if necessary. It cannot teleport an unrelated former participant into a new active attempt or silently use a newly published recovery location. This follows the existing safe-recovery waiting policy.

These are spatial occupancy rules only. Q107 accepts chunk availability. Neighboring block effects and interactions that cross a boundary need their own world-change contracts.

## Q102: outsiders entering an occupied arena

Revised by the user: do not add invisible walls. Outsiders can cross the arena boundary normally. Remove automatic movement cancellation, pushback, forced return, and the mandatory outside return destination. Q103 extends this decision to ordinary Minecraft interactions, including collision and combat. The earlier start refusal because unrelated players were inside is also superseded; start conditions now belong to the author under Q105-Q106.

## Q103: ordinary Minecraft behavior

Revised by the user: players should play under ordinary Minecraft rules. An encounter being active or inactive is system state, not a permission boundary or a different set of world physics. Player position, encounter membership, and active arena ownership alone must not change movement, collision, pushing, projectile interception, PvP, ordinary combat, assistance, building, mining, or ordinary item use. Existing server settings, gamerules, and other mods retain their normal authority.

Remove the proposed outsider isolation, automatic friendly-fire override, combat immunity across rosters, automatic arena building/mining protection, default NPC return on escape, and default living-player return on departure. A nonparticipant's ordinary attack can damage an encounter NPC if ordinary gameplay permits it. An actual designated NPC defeat remains a defeat under Q32, irrespective of killer unless the author explicitly requires particular attribution. Normal entity targeting is not automatically filtered by encounter membership.

Conclave still implements authored mechanics, selected targets, configured NPC AI and vulnerability, encounter-owned entities and effects, and the explicitly requested global revival and spectator modules. Authors may explicitly configure a boundary response, confinement area, eligibility filter, or other supported effect when their encounter needs one. Those behaviors must come from declared capabilities, never a hidden rule activated merely by occupying an arena.

A participant roster may still identify players for progress, revival, recovery, mechanic defaults, and authored audiences. It does not grant exclusive access to Minecraft combat or create a protected physical world. Authored interaction eligibility applies to the particular Conclave mechanic, not all uses of a block or entity. Private clue delivery and administrative permissions retain their independent accepted contracts. In particular, ordinary Minecraft behavior does not mean sending every player all private Conclave data.

Do not refuse to start merely because other players are present. The author's start predicates and participant selection decide which nearby players matter. Keep engine checks for one active attempt per arena, reservation conflicts, resource limits, and valid configuration. Q105-Q106 move player-count and entry rules into authored activation conditions.

GM inspection remains an explicit administrative observation capability rather than a blanket physical exception for GMs. Specific inspection controls still need design. Existing grave-camera and passed-out spectator policies remain explicitly configured module behavior.

Cleanup continues to track Conclave-owned resources. It must not treat ordinary player edits, items, or unrelated entities as encounter-owned just because they are in the arena. [Q119](cleanup-and-restart.md#q119-restoring-blocks-after-ordinary-world-changes) accepts preserving later world changes during restoration; the removed build protection cannot be assumed to prevent those conflicts.

## Q107: keeping an active arena available

Accepted: retain the finite set of chunks needed by an arena, its supported encounter behavior, and required neighboring simulation while an attempt is preparing, active, or completing world cleanup. Require the appropriate loaded and ticking readiness before mechanics begin. The team spreading out, disconnecting within grace, or temporarily lacking an online survivor must not unload part of the fight, lose a required NPC, or silently pause its simulation.

Calculate the full resource footprint and check configurable per-arena and server-wide limits before starting. Show an actionable error when the arena is too large, necessary terrain is unavailable, or loading does not complete within the bounded preparation timeout. Do not silently omit distant chunks or begin with only part of the arena ready. Server limits are operational admission controls; changing them does not silently rewrite an existing attempt's gameplay revision.

Own these claims through Conclave bookkeeping. Different non-overlapping arenas can still need the same chunk or neighboring simulation. Release an attempt's claims after its world cleanup, keeping a shared claim while another attempt still needs it and leaving unrelated systems' tickets untouched. Offline pending player recovery must not retain an entire arena forever; reacquire only the resources needed for safe recovery when that player returns.

If required chunk readiness is unexpectedly lost during an attempt, use the accepted technical-error and recovery policy instead of counting unavailable NPCs as defeated or quietly suspending a phase. This does not promise uninterrupted simulation during a paused or stopped server, sufficient CPU capacity, or recovery from arbitrary third-party corruption. Gameplay durations retain the accepted server simulation clock.

### Verified 26.2 facilities and remaining implementation work

Research inspected the server binary linked by [Mojang's official 26.2 metadata](https://piston-meta.mojang.com/v1/packages/987b91a95ae93b3bb78cc14d6e0bbd31bad08d59/26.2.json). Its ticket facilities distinguish loading, simulation, and keeping a dimension active, and permit acquisition and removal. Ticket matching uses type and level at a chunk, so repeated matching acquisitions are not independent per-attempt ownership claims. Conclave needs its own shared-ownership accounting rather than assuming one remove operation releases only one attempt's claim. These facilities make bounded encounter retention plausible without a global gamerule change; that conclusion is an implementation inference, not a completed encounter test.

Fabric's [26.2 chunk lifecycle event contract](https://github.com/FabricMC/fabric-api/blob/26.2/fabric-lifecycle-events-v1/src/main/java/net/fabricmc/fabric/api/event/lifecycle/v1/ServerChunkEvents.java) distinguishes full chunk status changes from load and unload events. It warns that the status callback may occur before entities are accessible and before the corresponding status future has completed. A load notification alone is therefore insufficient as the start-readiness test.

The exact ticket setup, readiness predicate, neighboring footprint, numeric resource limits, preparation timeout, and cleanup/recovery integration still need implementation design and runtime verification. No code or integration test has been completed by this design research.
