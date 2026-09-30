# Optional revival damage protection

Status: Q278 is accepted. Revival defaults to 100% health and `damage_protection: 0s`. Positive protection, early termination on hostile action and the explicit-wipe exception are already accepted. This contract fixes the remaining damage and input boundaries; no damage adapter exists.

## Q278: block ordinary damage until expiry or a deliberate attack

Accepted: when the captured revival policy grants positive protection, reject supported ordinary incoming damage before it consumes health or absorption. Include combat and environmental damage, including falls, fire, lava, drowning, suffocation and the void. End protection immediately before a supported deliberate hostile action by the revived player. Preserve authorized administrative removal and explicit encounter outcomes.

This is temporary damage protection only. It does not stop world time, extinguish fire, replenish air/food, remove harmful auras or native effects, change collision, prevent unrelated movement, or guarantee that the player will be safe when protection ends. Safe revival placement remains required. Direct health assignments and maximum-health changes retain their own accepted semantics; arbitrary other-mod mutations outside verified damage paths are not promised to be intercepted.

Count down the captured positive duration on the server simulation clock from successful revival, independent of combat state. Pausing combat pauses the revive window, not this protection. Server downtime does not consume simulation time; disconnection while the server runs does not pause the countdown. Persist any protection remaining when needed for consistent recovery. Another death or attempt-end recovery removes that revival's protection; reconnect and startup do not grant a fresh duration. A later successful revival can create its own new protection under its captured policy.

### Ending protection through player input

End protection before processing a server-accepted attack against an entity or a supported deliberate use/release that launches an offensive projectile or applies a harmful effect. A valid hostile action ends it even if a shield, vulnerability lock or other rule prevents damage. Merely holding a weapon, beginning and canceling an uncompleted charge, or swinging without an accepted attack target does not count.

Ordinary movement, mining/building, eating, harmless interactions and helping another player revive do not end protection. A projectile released before revival, an already scheduled periodic effect or passive retaliation is not new hostile input. Do not infer a fresh player action from an old damage attribution or a pet's behavior. Supported addon abilities must declare and report their hostile activation through the trusted capability contract; no YAML command or generic public attack-attempt event is added.

The player HUD shows remaining protection while it exists and removes it immediately on termination. The server makes the decision before hostile resolution; a delayed client animation or packet cannot extend the protected interval.

### Exceptions and event behavior

Preserve actual authorized operator `/kill`, supported administrative removal and explicit encounter stop/wipe outcomes. Choosing a particular damage-type ID is not administrative authority. Gameplay damage authored through a supported `damage` action is still ordinary damage and respects protection. An encounter that declares an explicit failure does not need to simulate lethal damage to defeat protected participants.

A fully prevented hit emits no `damaged` event and consumes no health or absorption. Q228's initial `damage_prevented` reasons remain the accepted NPC vulnerability/floor interventions; this contract does not silently add a participant protection event to that catalog. Ordinary unsupported or administrative operations keep their documented native consequences. Implementation must verify lethal/nonlethal paths, prevention before side effects, hostile input timing and the exceptions on the selected 26.2 adapters.

Related contracts: [revival defaults and safe placement](death-and-revival.md), [settings fields](settings-and-spectator-controls.md), [simulation time](runtime-semantics.md), and [combat observations](individual-npc-controls-and-hit-reactions.md).
