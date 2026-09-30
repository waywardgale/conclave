# Conclave

Conclave is a framework for designing Minecraft encounters through declarative manifests.

## Language

**Encounter**:
A designed gameplay challenge that players attempt, with rules for progression and outcomes.

**Encounter definition**:
The reusable description of an encounter, independent of its location or a particular group's attempt.

**Start condition**:
An authored requirement that determines when an encounter can begin.

**Ready check**:
A request asking selected players whether they are ready, with their responses recorded.

**Arena**:
A named, bounded place where a group attempts an encounter. Multiple arenas can host the same encounter definition.

**Arena binding**:
An association between an encounter's logical area or location name and its corresponding spatial definition in an arena.

**Area**:
A named region within an arena used by encounter mechanics and spatial rules.
_Avoid_: Zone

**Location**:
A named position used as a destination, spawn position, or marker placement.
_Avoid_: Point

**World location**:
A named location belonging to the world independently of any arena, such as a shared recovery destination.

**Location anchor**:
A named authoring block for defining locations and areas and configuring player recovery or entity spawning.
_Avoid_: Location marker

**NPC**:
A named non-player entity participating in an encounter, including a boss.
_Avoid_: Actor

**NPC definition**:
A reusable description of an NPC's entity type, properties, and supported behavior.

**NPC conversion**:
A native change of an NPC's entity type that continues the same encounter NPC.

**NPC descendant**:
A separate NPC created by another NPC's native behavior, such as a summon or split child.

**NPC defeat**:
A terminal combat outcome in which an NPC dies or completes a supported native self-destruction.

**Attacker**:
The entity attributed as causing a resolved damage operation. It can differ from the entity that directly delivered the damage.

**Killer**:
An attacker whose attributed fatal operation caused an NPC's death.

**Direct source**:
The entity that directly delivered a damage operation, such as an arrow.

**Health floor**:
An authored limit on how far damage can reduce an NPC's health.

**Model definition**:
A reusable description of an appearance's geometry, textures, animations, and supported state mappings.

**Item definition**:
A reusable description of an ordinary item's initial properties.

**Item model**:
A visual definition used to render an item without changing the item's gameplay properties.

**Appearance archive**:
The collection of retained visual and text resources that existing items can continue to use after their originating encounter ends.

**Staged assets**:
Verified presentation files available locally but not yet applied for use by the client.

**Applied resource set**:
The combined presentation resources the client has successfully loaded for its current consumers.

**Visual bounds**:
The volume enclosing a model's intended appearance, used to decide whether it is in view.

**AI mode**:
An NPC's choice of autonomous behavior: its normal behavior, wandering, or no autonomous decisions.

**Spawn group**:
A named group of NPCs created together or explicitly gathered across waves, whose defeats can be tracked as one requirement.

**Confinement**:
The restriction that keeps an NPC within its permitted arena or area.

**Relic**:
An encounter-managed object that participants can carry and deliver.

**Role**:
An optional named assignment for participants, such as runner or reader.

**Aura**:
A named gameplay state attached to a player or NPC, such as a buff, debuff, or temporary encounter mark. An aura may have a duration.

**Status effect**:
An effect provided by Minecraft or another mod, such as Strength, distinct from a Conclave aura.

**Visual effect**:
A cosmetic presentation such as particles or a glowing ring, shown at a location or attached to a holder. It is distinct from a gameplay aura or Minecraft status effect.

**Attempt**:
One group's active playthrough of an encounter in an arena.
_Avoid_: Instance, run

**Test attempt**:
An attempt using a selected draft in an arena without publishing that draft for ordinary attempts.

**Completion reward**:
Items or experience awarded for successfully finishing an encounter.

**Pending reward**:
An earned completion reward with contents still awaiting delivery.

**Phase**:
A named stage within an encounter, such as a ritual or a period when a boss can be damaged. An attempt has one active phase at a time.

**Combat**:
An explicitly declared gameplay state in which the combat revival limit applies. Taking damage alone does not define this state.

**Mechanic**:
A gameplay behavior that runs during an encounter, such as capturing an area, delivering a relic, or matching a pattern. A mechanic need not be a completion requirement.

**Pattern token**:
A named logical value that a pattern-matching challenge accepts, independent of how that value is displayed.

**Expected pattern**:
The sequence of tokens a pattern-matching challenge requires, with an authored choice of whether their order matters.

**Pattern clue**:
An intentionally revealed part or all of an expected pattern, shown to a selected audience.

**Layers mechanic**:
A mechanic that combines objectives, background behavior, and local rules into one unit with its own progress and outcome.

**Objective**:
A named completion requirement within an encounter, such as capturing all required areas.

**Condition**:
A check of current gameplay state that is either satisfied or unsatisfied.

**Event**:
A reported occurrence from an identified gameplay source.

**Dialogue**:
A named line or set of alternative lines spoken or displayed by an identified speaker, optionally with recorded voice audio.

**Text style**:
A reusable formatting template for dialogue or other authored text.

**Audience**:
The players selected to receive an authored message, sound, or visual cue.

**Playback**:
One active or queued presentation of a sound, dialogue, animation, or visual effect.

**Action**:
A supported operation that changes gameplay state or presents information.

**Rule**:
A declared reaction to an encounter event, optionally restricted by conditions, that performs supported gameplay actions.

**Counter**:
A named integer value used to track gameplay progress or other authored state.

**Timer**:
A named countdown whose expiry can trigger a gameplay rule.

**Mechanic definition**:
A reusable description of a mechanic with parameters that encounter authors can configure.

**Parameter**:
A named configuration input an author supplies to a reusable mechanic definition.

**Raider**:
A player who is not a Conclave game master. Being a raider does not by itself mean participating in a particular attempt.

**Participant**:
A player on an attempt's roster. Reconnecting does not create a new participant identity.

**Participation**:
A participant's admission status in an attempt, independent of being online, alive, or using a particular camera.

**Reconnect grace**:
The time available after connection loss to return to the same attempt with its required resources ready.

**Observer**:
A retained participant whose ordinary opportunity to rejoin the current attempt has expired.

**Outsider**:
A player who is not on a particular attempt's roster, regardless of their physical position.

**Grave**:
A marker at a player's death location where another player can help revive them.
_Avoid_: Graveyard when referring to one player's marker

**Revival**:
A return to life after death without starting a new attempt. Revival does not by itself restore encounter admission.

**Respawn**:
A return after death at a designated recovery location, outside the opportunity for revival at the grave.

**Revive window**:
The limited amount of combat time available for revival after a player's death.

**Revival protection**:
Optional temporary damage protection granted after a successful revival, distinct from the opportunity to revive during the revive window.

**Spectator**:
A participant using the permitted observation mode. Global policy determines their camera and access to another player's private information, independently of their admission status.

**Game master**:
A player granted Conclave administrative privileges through the designated server-operator command.
_Avoid_: Role when referring to administrative permission

**Draft**:
An editable set of manifest definitions prepared for validation and publication.

**Content revision**:
A fixed version of Conclave definitions and gameplay settings that an attempt can use.

**Namespace**:
A named grouping that distinguishes definitions with the same local ID.

**Manifest**:
An authored description of an encounter, mechanic, or arena and its configuration.
