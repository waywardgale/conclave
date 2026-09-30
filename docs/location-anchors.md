# Location anchors

Status: Q48-Q52 are accepted, with the user naming the block Location anchor. The user also requires anchors to configure named areas referenced by YAML, with toggleable area visualization in Creative mode. Initial area shapes, anchor relationships, and visualization defaults are accepted in [areas](areas.md); initial geometry editing and spatial events are also accepted there. No block, screen, or runtime has been implemented.

## Accepted capability

Authors can place an invisible block to identify a player respawn location or configure entity spawning at a desired location. They can refer to it from YAML and edit its configuration through a human-readable in-game interface. Its name appears above it. The user cited a vanilla barrier as an example of an invisible block; they did not require changing vanilla barrier behavior.

The existing vocabulary uses `location` for a named position and `npc` for a non-player encounter entity. `id` is a stable authored reference and optional `name` is display text. The accepted block name is Location anchor. Keep `area` as the existing YAML term for the regions the user calls zones; no change to that earlier naming decision was requested. The current grave-and-revival flow remains accepted; adding placeable respawn locations does not itself change when a player may revive or respawn.

## Q48: one location anchor block

Accepted: provide one custom Location anchor block for player recovery, entity spawning, and area configuration. It is invisible and has no collision during ordinary gameplay. In edit mode, an authorized author sees its outline, facing indicator, and overhead display name. The block occupies an authoring position without introducing an invisible obstacle for players.

Each anchor has an `id` and optional `name`; the label falls back to the ID. YAML refers to its ID using the existing location vocabulary. Changing display text does not change references. An anchor can support recovery, entity spawning, and area configuration without separate block types. Q57 assigns arena-local IDs, unique within each kind. [Q80](manifest-references.md#q80-encounter-to-arena-bindings) defines same-name binding and optional typed mappings for an encounter/arena pairing. Detailed placement fields remain part of manifest design.

## Area configuration and Creative visualization

The same Location anchor must let authors configure named areas and reference them from YAML. Areas can be visualized in Creative mode, and that visualization can be toggled. The accepted block, label, and editor capabilities extend to area authoring; one anchor may define multiple areas with separate IDs and relative placement. The accepted shapes and Creative viewing policy are in [areas](areas.md), with the accepted initial geometry editing controls.

Area configuration follows the accepted publication policy. Each attempt retains the resolved area geometry it started with, including geometry used by later phases. Saving a draft or changing an editor overlay does not change gameplay boundaries in an active attempt.

## Q49: shared YAML data and publication

Accepted: YAML is the portable authoritative authoring format. The in-game editor saves to the same draft model and serializer used for YAML authoring. Anchor blocks are in-world editing handles for that data; their current block data is not a second independently authoritative definition.

The complete published revision contains resolved anchor locations, facing, spawn configuration, and referenced area geometry. An attempt captures those values at its start, including locations needed for future phases or attempt-end recovery. It does not resolve a later spawn by rereading a moved live block. Existing attempts keep their starting anchor values even after publication.

Saving is distinct from publishing. On a live server, Save draft updates authoring data; the existing operator-only publish operation validates and activates the complete revision for future attempts. Development auto-reload uses the same validation and attempt isolation. Concurrent edits must detect stale drafts instead of silently overwriting another author's saved changes.

In an occupied arena, anchor moves, placements, and removals appear as editor previews until the arena is idle. Physical block changes wait for that point, since invisibility and lack of collision alone do not prove a world edit cannot affect gameplay. Apply published placements before the next attempt starts, and report occupied or invalid target positions instead of replacing unrelated build blocks. Q70 accepts one named definition per YAML file, and the user requires all operations inside Minecraft in [Q71](manifests-and-publishing.md#q71-all-operations-inside-minecraft). Connected-session transfer, draft collaboration, and preservation of hand-written YAML formatting are accepted in [in-game authoring](in-game-authoring.md).

## Q50: visibility, permissions, and configuration interface

Accepted: operators and GMs can explicitly enter edit mode, see anchor labels and outlines, and create or edit drafts. Ordinary participants do not see those authoring labels or receive anchor configuration. The Creative-mode area visualization requirement has a separate accepted viewing policy in Q55; it does not grant draft editing or publication authority. This extends the initial GM permissions to preparing anchor content; publishing remains operator-only as already accepted. Permission revocation immediately closes privileged editing access.

The accepted interaction is:

1. Enter edit mode and select the Location anchor item from the editing tools.
2. Place or select an anchor and right-click it to open its configuration panel.
3. Edit its name and reference ID, use, position, and facing. Offer Use my position and a facing preview; keep precise offsets available without requiring coordinate arithmetic for ordinary placement.
4. For an entity spawn, choose an existing NPC or entity definition and count. For player recovery, select its recovery use. The user also requires area configuration; its initial shape controls are specified in the area contract. Show readable descriptions and validation beside the affected field.
5. Preview the placement and Save draft. An operator can publish through the shared publication workflow.

Previews show placement and facing without creating live combat entities. The panel uses ordinary controls and searchable choices; arbitrary commands, scripts, and raw entity NBT are not authoring controls. A legitimate client screen does not establish authority: the server checks the editor's current permission, target identity, and draft revision on every mutation. Anchor metadata is sent only to eligible editors, rather than through unrestricted block-entity update data.

The full layout and accessibility details remain to be designed. No UI implementation or runtime usability result is claimed.

[Q123](world-locations-and-assets.md#q123-location-anchors-outside-an-arena) accepts world-owned Location anchors for recovery outside arenas while retaining the same editor and publication workflow.

## Q51: recovery uses and entity-spawn activation

Accepted: assisted revival normally returns the player to their grave. A configured recovery anchor supplies the fallback for an unusable grave position and the destination for attempt-end recovery. The encounter selects the intended anchor explicitly; a later phase can select another recovery location through the supported declarative behavior. Selecting a location never restores a player whose revival opportunity has expired.

An entity anchor can hold a reusable spawn configuration, such as an NPC definition, count, and facing. An encounter phase or authored rule activates it. The anchor supplies configuration and location while the attempt owns spawned entities and cleanup. Placing, editing, or loading the anchor is not an activation trigger. Ambient spawning outside attempts remains a separate possible capability, not an implicit consequence of placing the block.

Explicitly authored spawn actions can use ordinary named locations without a placed anchor. The in-game authoring tool must not become a requirement for writing valid YAML. [Q93](npc-and-boundary-policies.md#q93-spawning-and-reinforcing-groups) accepts explicit `spawn` configuration or an anchor's complete saved configuration. [Q214-Q215](npc-adapter-scope.md) accept the initial 18 native NPC targets and capability inspection; their adapters and the accepted typed NPC controls remain unimplemented.

## Q52: occupied and unsafe destinations

Accepted: an anchor defines a feet position and facing, with its block position supplying the default and the editor allowing offsets. Validate player space, footing, world bounds, and arena bounds at use time. For a group, choose distinct valid nearby positions in a stable order instead of placing every player in the same position.

Use a globally configurable search radius, three blocks by default, around the selected recovery anchor. This is separate from the helper's interaction reach. If it has no valid positions, try an explicit ordered fallback list. Never select another arena's anchor merely because it is nearby. If every permitted destination is unusable, keep affected players in recovery waiting and report the specific problem to GMs; do not force them into a damaging or obstructed position.

Entity spawning must use placement rules appropriate to the selected entity, rather than imposing player footing requirements on flying or aquatic entities. [Q93](npc-and-boundary-policies.md#q93-spawning-and-reinforcing-groups) requires the complete requested spawn batch and removes partial creations on failure. Q69 governs explicit recoverable fallbacks or retries and technical-error stops. Exact search limits and the failure-event catalog remain open.

[Q266](area-fields-and-membership.md#q266-one-geometry-format-for-arena-boundaries-and-named-areas) accepts arena location/area lists and concrete relative placement fields. It preserves optional physical anchors, captured placements, and the existing editor workflow.

## Fabric 26.2 feasibility notes

Fabric documents [block entities](https://docs.fabricmc.net/develop/blocks/block-entities) for per-block data, persistence, interaction, and synchronization. Its [block entity renderer guide](https://docs.fabricmc.net/develop/blocks/block-entity-renderer) demonstrates custom text rendering at a block. These support the proposed per-anchor data and overhead-label design; the editor-only overlay is Conclave behavior to implement.

Fabric's [custom screen guide](https://docs.fabricmc.net/develop/rendering/gui/custom-screens) supports configuration panels and widgets. Such screens are client UI; they do not by themselves supply server authorization or draft persistence. The [networking guide](https://docs.fabricmc.net/develop/networking) explicitly requires server validation of client payloads. Using a screen with authorized requests to the server is an implementation inference from those capabilities.

The [26.2 death events](https://github.com/FabricMC/fabric-api/blob/26.2/fabric-entity-events-v1/src/main/java/net/fabricmc/fabric/api/entity/event/v1/ServerLivingEntityEvents.java) distinguish fatal-damage cancellation from actual death. Conclave still needs to preserve the vanilla gamerule behavior required by Q45 while controlling recovery and camera state. No source inspection here proves the complete death or editor workflow at runtime.
