# NPCs and spawning

Status: `npc` is the accepted term for non-player encounter entities, including bosses. Explicit spawning through phases or rules, Location anchor placement, actual-defeat tracking, and attempt-owned cleanup are accepted. Q63-Q64 below are accepted as the initial NPC authoring contract. No spawning or AI implementation exists. Q93-Q99 define [spawn actions, targeting, confinement, boundaries, loot, and AI modes](npc-and-boundary-policies.md). Q141-Q146 accept [stats, combat actions, physical size, native equipment slots, boss bars, and a 1,000,000-point authored health ceiling](npc-stats-and-combat.md). The base-type field is `npc`.

## Q63: NPC definitions and behavior

Accepted: define an NPC using an existing Minecraft or installed-mod entity type. Expose readable, typed properties for supported attributes such as health, damage, speed, equipment, display name, drops, and an optional boss bar. Q141-Q145 settle the initial fields and units; additional supported properties remain part of the capability catalog. Raw entity NBT is not an alternative authoring interface.

Keep the entity type's existing AI by default. Offer supported controls for enabling or disabling AI, selecting eligible targets, movement, and vulnerability. The user explicitly requires wandering and complete AI disablement in Q94. [Q98-Q99](npc-and-boundary-policies.md#q98-ai-modes-and-their-scope) accept the mode names, semantics, and changes through authored rules. YAML can compose encounter attacks from registered actions, events, areas, and auras. A fundamentally new AI or attack capability requires a Kotlin extension, after which manifests can configure and reuse it.

Entity support is capability-based: validate whether the selected type supports each configured property and requested control. Reject unsupported combinations with a specific error instead of silently ignoring fields or promising that every installed mod's special behavior is controllable. [Q214-Q215](npc-adapter-scope.md) accept the initial native-type coverage targets and an in-game compatibility view. Every advertised adapter still needs implementation verification.

The user's Q128 extension explicitly requires custom model assets, sounds, boss dialogue and taunts, and ability cues. [Q129-Q132](presentation-and-dialogue.md) accept named dialogue, typed presentation actions, audiences, and a GeckoLib model adapter direction. The exact dependency pin and adapter mappings still need verification; custom appearance does not change the server hitbox or AI implicitly.

An NPC definition describes what to create; spawn actions specify when, how many, and at which location. The attempt owns the resulting entities and their cleanup under the accepted phase or encounter scope. Q103 supersedes automatic combat isolation and confinement in Q94-Q96; authors can configure supported targeting and confinement explicitly. Q97 retains opt-in NPC death rewards. Q144 accepts native equipment slots and initial-loadout behavior. Further item properties, loot fields, external resource pinning, and physical interaction with unrelated entities remain part of the NPC capability contract.

## Q64: named spawn groups and defeat completion

Accepted: a spawn operation creates a named group of the NPCs created for that operation. A `defeat` objective binds to the selected group activation and records real defeats of its members. Reusing an authored group name during a later activation does not retarget an existing objective or erase the earlier group's history.

Groups have fixed membership by default. A requested spawn count must be fulfilled before the group can be considered successfully created; partial spawning is a reported failure, not a smaller completed group. Later reinforcements belong to a new group unless the author explicitly adds them to an open group.

For ongoing waves, an author can explicitly keep a group open while adding NPCs, then close it when all intended members have been added. An all-members `defeat` objective succeeds only when its group is closed and all its required members were actually defeated. An empty or not-yet-created group cannot satisfy that default objective. Explicit count objectives keep their authored count requirement.

Despawn, cleanup, disconnection, or loss of an entity reference is not a defeat. Unexpected disappearance reports a missing member rather than completing the objective. [Q69](execution-and-errors.md#q69-failure-categories-and-bounded-work) defines the accepted technical-error stop and explicit handling of documented recoverable failures through a fallback or bounded retry. [Q93](npc-and-boundary-policies.md#q93-spawning-and-reinforcing-groups) accepts `spawn`, explicit reinforcement through `into`, and `close_group`, plus pending producer binding and single creation of a group ID per owning activation.

[Q216-Q217](npc-lifetimes-and-variants.md) accept verified committed native combat self-destruction as defeat, alongside actual native death, and prevent ordinary distance/random despawn of owned NPCs. Self-destruction does not manufacture a killer or native death rewards. Known noncombat removal still gives no defeat credit, and a permanently missing required member follows the error policy.

[Q219-Q220](npc-defeat-events-and-requirements.md) accept per-member `defeated` notifications and cause-filtered completion over retained records. A disallowed combat outcome stays a real defeat even when it makes an authored objective impossible.

Group membership is per attempt and activation. An author-facing group is a set of encounter NPCs, not a Minecraft scoreboard team or a grant of control over unrelated world entities.

[Q206-Q207](npc-lineage.md) accept explicit ownership and membership rules for native conversions, split children, and summons. Conversions preserve logical group membership; native descendants receive cleanup ownership without automatic group membership. [Q208-Q209](npc-conversion-state.md) accept their state and configuration policies. [Q210-Q211](npc-conversion-events-and-descendant-definitions.md) accept conversion notifications and explicit native-child configuration. [Q212-Q213](npc-spawn-events.md) accept notifications for ordinary member creation and native descendants.
