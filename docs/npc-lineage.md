# NPC conversion and descendants

Status: Q206-Q207 are accepted. They address two separate native lifecycle cases before choosing the initial NPC adapter coverage. No lifecycle adapter or transfer code exists.

These contracts extend capability-based support, ordinary Minecraft behavior by default, explicit spawn-group membership, actual-death defeat credit, and cleanup of owned entities to native replacement and child creation.

## Q206: native conversion preserves the encounter member

Accepted: a supported native conversion, such as an owned zombie becoming a drowned, continues the same logical encounter NPC and spawn-group member. Follow the verified replacement operation even when Minecraft creates a new entity with a different UUID. Do not count the conversion as defeat, reinforcement, a second successful Conclave `spawn`, or a new encounter activation.

Preserve the owning attempt/scope and that member's defeat requirement. The group now resolves its living member to the replacement. A closed group stays closed because conversion does not add a member. Repeated supported conversions cannot increase or complete the objective's count. Actual death of the current member still supplies its one defeat.

Distinguish logical membership from a captured native entity reference. An event about the original body remains historical; a previously captured action target must not silently retarget the replacement. A later group selection can select the current body. This preserves the accepted stale-target and damage-attribution rules while allowing the group to track native conversion.

The adapter must establish the old-to-new relation at the conversion operation and record ownership before the replacement becomes an unmanaged entity. Matching a nearby mob, copying an entity tag, or recognizing the same display name is insufficient. Conversion does not grant permission to adopt an unrelated world entity.

Classify a single replacement separately from splitting on death. A platform callback used for both cases does not make several split children continuations of one group member. Failed replacement insertion cannot commit a successful identity handoff.

Capability validation must account for supported destination types and the configured controls. A known unsupported source/destination combination fails validation with an explanation. Unexpected integration failure uses Q69's existing error policy; it must not award defeat, lose cleanup ownership, or silently drop an authored control. This is a requirement for declaring adapter support, not a claim that every installed mod's conversion can be intercepted.

This decision settles identity and completion only. [Q208](npc-conversion-state.md#q208-preserving-state-through-native-conversion) accepts transfer of health, authored stats, equipment, auras, runtime controls, and model presentation. [Q210](npc-conversion-events-and-descendant-definitions.md#q210-a-converted-event-for-npc-groups) accepts conversion-event fields. No general authored `transform` action is introduced.

## Q207: native children inherit cleanup ownership, not defeat membership

Accepted: living entities created directly by a supported owned NPC, such as an evoker's vex or a slime's split children, inherit the spawning NPC's attempt and owning scope for cleanup. Record their verified origin so later descendants can retain that ownership. They do not automatically join their parent's named spawn group or increase its required defeat count.

A large slime's actual death can satisfy its own group-member requirement. Its smaller children remain in the world while their scope remains active and disappear during that scope's cleanup. An evoker's summons can continue their native lives after the evoker dies, subject to their own native lifetime and the same scope cleanup. Killing an ungrouped descendant does not create another defeat for the parent's group.

This keeps the accepted fixed-group contract predictable. Authors who want every reinforcement to count should create it through YAML `spawn` with explicit group membership, including `into` for an open group. The initial descendant policy does not silently add an adoption action, reopen a closed group, or expose unnamed native children as a new authored group.

Adopt only a new living entity whose creation relationship the adapter can establish. Proximity, a native attack target, damage attribution, or an unrelated entity being changed by combat does not prove ownership. Ambiguous origin cannot transfer an ordinary world NPC into an attempt's cleanup set. Supported lineage must remain available for cleanup and interrupted-attempt recovery within the existing bounded ownership policy.

Cleanup removes descendants without manufacturing deaths or rewards. [Q209](npc-conversion-state.md#q209-native-descendant-settings-and-rewards) accepts their initial configuration and ordinary death-reward defaults. This ownership contract does not copy all parent settings onto a different entity type.

An ending scope cannot leak new descendants through cleanup callbacks. Supported adapters must suppress cleanup-triggered creation or contain and remove verified children under bounded cleanup without awarding defeat or rewards. A notification that runs before native insertion is not by itself proof that this is handled.

Projectiles, area-effect entities, blocks, dropped ordinary items, native effects, and other existing resource kinds keep their accepted lifetime contracts. This question covers newly created living NPCs. It does not expand cleanup to every consequence of ordinary Minecraft combat.

## Related contracts and next branch

These contracts refine [NPC support and defeat groups](npcs-and-spawning.md), [explicit reinforcement and AI modes](npc-and-boundary-policies.md), [typed combat references](npc-stats-and-combat.md), and [owned cleanup and recovery](cleanup-and-restart.md). They preserve the user's ordinary-world gameplay requirement.

[Minecraft 26.2 lineage research](npc-combat-research.md#native-conversion-descendants-and-special-boss-lifecycles) records the inspected native paths and Fabric hook limitations separately from these accepted policies.

[Q208-Q209](npc-conversion-state.md) accept state transfer and descendant defaults. [Q210-Q211](npc-conversion-events-and-descendant-definitions.md) accept conversion notifications and explicit native-child configuration. [Q212-Q213](npc-spawn-events.md) accept creation notifications. Use the settled requirements when choosing initial supported NPC adapters and their in-game capability descriptions. Special boss lifecycles and custom model layers need verification in their own adapters; a common base class is not enough evidence of support.
