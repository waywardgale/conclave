# Interaction input and progress

Status: Q175-Q177 are accepted. These contracts define completed uses, individual holds, physical eligibility, and coexistence with native interaction, building on Q174's block-position and NPC-group targets. The development build implements and tests the block-target adapter. NPC targets remain pending; see [implementation status](implementation-status.md).

## Q175: completed uses and individual holds

Accepted: add the following fields to `interact`:

| Field | Meaning and default |
| --- | --- |
| `uses` | Positive integer number of completed uses required across the mechanic. Default one. |
| `distinct_players` | Each player may contribute at most one completed use during this activation. Default false. |
| `hold` | Simulation duration of one use. Default `0s`, explicitly meaning instant. Otherwise positive. |
| `use_cooldown` | Simulation time before the same player may begin another use of this mechanic after a credited use. Default `0s`, meaning no additional delay. |

```yaml
- id: authorize_console
  type: interact
  targets:
    - block: console
  uses: 3
  distinct_players: true
  hold: 2s
```

This requires three different eligible players to complete a two-second hold. They can do so at different times or concurrently. Each player advances their own hold at the ordinary simulation rate. Nobody adds time to somebody else's hold, inherits their partial progress, or accelerates a shared meter. The mechanic's completed-use count is shared across its listed targets.

### Input and counting

One fresh deliberate press can begin at most one use per player in this mechanic. A held key cannot repeatedly contribute instant uses or automatically begin another hold. Require release and a new press after a completed or interrupted use. Repeated native callbacks, both-hand attempts, retransmitted client input, and overlapping target entries cannot duplicate that credit. Holding a key before the mechanic becomes available does not automatically activate it.

Each player holds against one actual target at a time. Interrupt and discard their unfinished progress when they release, change targets, lose physical or participant eligibility, disconnect, die, or lose gameplay input focus. A server-observed block replacement also interrupts the current hold, even though a new press may target the replacement under Q174's position binding. A moving NPC remains the same target while it stays eligible and reachable. A replacement NPC never receives the earlier NPC's hold.

Initially, interrupted holds reset to zero; there is no pooled progress, pause, or decay field for `interact`. Capture retains its separately accepted `on_interrupt` options. The author can combine already-completed uses across players, but an individual hold requires continuous valid input. A fresh activation starts with no completed uses or held progress.

When a use completes, commit one credit. With `distinct_players: true`, remember that player's identity for this activation. Later death, disconnection, revival, or changes to their role do not erase a committed use or let the same player count twice. With false, the same player may contribute again after a fresh press and any `use_cooldown`. Changing targets does not bypass either the distinct-player rule or the cooldown.

Start the cooldown only on a credited use, not a rejected press or an interrupted hold. Input during cooldown is rejected without being saved for later. Reject a nonzero `use_cooldown` combined with `distinct_players: true`, because that player cannot contribute a second use in this activation. This avoids a setting that appears effective but never changes behavior.

### Progress events and completion

Expose `used` once for each credited use, with typed `player` and integer `uses_before`, `uses_after`, and `uses_required`. Queue `completed` after the final `used`, once for the mechanic activation, with the same final player and count snapshot. These events support `per_player` limits using the player who completed that use. They do not imply that a native button, chest, or NPC interaction succeeded.

Commit final progress and completion before delivering the notifications. A rule cannot undo an already completed interaction by reacting to `used`. Resolve competing completed holds in the accepted stable server operation order; stop crediting once the required count is reached, and cancel other unfinished holds without fabricating another use. Completed progress remains latched under the existing mechanic lifetime contract. Cancellation or technical failure is not a final credited use.

Unknown fields and invalid values fail validation. The editor explains that `uses: 3` with distinct players requires three identities, but a temporarily insufficient team is ordinary unfinished gameplay. Do not silently lower the requested count, change difficulty, or choose replacement players. Keep numeric upper bounds within the eventual engine budgets.

## Q176: physical eligibility and interruption

Accepted: require the player to aim at the actual target, remain in the same dimension, and have an unobstructed line of sight under a supported server-verified target test. Use the player's current native reach for that target kind by default. Optional positive `reach`, measured in blocks, adds a stricter maximum; the effective reach is the smaller of that limit and native reach. It does not extend native attributes or create remote-use authority.

Do not reuse the grave-revival module's separate three-block global default. Block and entity targeting retain their appropriate native geometry and reach checks. A held interaction rechecks its actual target, current eligibility, reach, and line of sight as simulation advances. Merely being somewhere in a matching NPC group or near the block's location is insufficient. The adapter must document and verify the geometric tests; these are intended guarantees, not proof that an existing Fabric callback implements them all.

Add `interrupt_on_damage`, defaulting to false. When true, a positive supported `damaged` outcome under Q153 interrupts the player's unfinished hold, including absorption-only damage. A wholly blocked or prevented hit does not. Death always ends the hold independently of this field. Damage never subtracts uses already credited. Resolve interruption and completion using the existing stable operation order; there is no special damage-versus-hold tie override. Grave revival keeps its own separately accepted final-tick rule.

Required client input reports fresh-press and release intent, with bounded continuation for a held gesture. The server owns start time, elapsed progress, target validation, and credit. It never accepts a client-reported completed duration, retroactively credits a hold, or multiplies progress by packet frequency. Expired continuation, disconnection, or an invalid session ends the hold. Server simulation pauses do not advance progress; operational input freshness and reconnect rules retain their separate clocks.

Bind input to the current attempt, mechanic activation, player connection, and actual target. A stale packet cannot start work in a replacement activation or carry progress across reconnect. Focus loss ends the gesture, and returning to gameplay requires a fresh press. The exact packet format, continuation interval, tolerances, and execution budgets remain implementation work, with no claim that the server can verify a human's physical key state independently of client intent.

## Q177: ordinary use and explicit input consumption

Accepted: use Minecraft's configured Use control for eligible block and NPC interactions. Add `consume_interaction`, defaulting to false. With false, Conclave observes the deliberate use while the ordinary block/entity/item interaction continues. A console implemented as a button can therefore both contribute a Conclave use and operate normally.

With `consume_interaction: true`, an active mechanic can admit an eligible gesture and consume its native interaction before the block, NPC, or held item reacts. This allows a custom console on a chest or guide on a villager without opening the native interface. Consumption begins when the gesture is admitted, including at the start of a hold; waiting until hold completion would be too late to prevent the initial interface from opening.

```yaml
hold: 2s
consume_interaction: true
```

This fragment configures an existing interact occurrence; it does not define a target. With pass-through left enabled, an ordinary interface that takes input focus interrupts an unfinished hold. Show that behavior in author help and preview so authors can deliberately choose consumption for such targets. Do not automatically change it based on the block type or duration.

### Scope of consumption

Consume only an admitted gesture from a qualifying player to a valid active target. A nonparticipant, a player outside reach, a completed mechanic, a player who already contributed under the distinct-player rule, or a press rejected by cooldown does not gain an invisible global interaction restriction. Ordinary mining, attacks, movement, and unrelated targets retain their Minecraft behavior.

Consumption covers both hands and native repeat attempts belonging to that same admitted gesture until release or input-session termination. A held gesture must not leak a second native action immediately when its mechanic completes or another phase begins. This short-lived gesture record is input deduplication, not continued gameplay progress or a claim of ownership over the block. Focus loss, disconnect, and expired input continuation clear the session safely; a fresh press is required before another Conclave use.

One gesture can match several active interaction mechanics. Determine eligible recipients from the same pre-dispatch input snapshot, let each match observe the gesture at most once, and consume native use if any admitted match requests consumption. Route Conclave observers before returning a single native cancellation decision. This avoids callback registration order silently choosing which matching mechanic exists. Subsequent guards, actions, and state changes follow the usual event queue and limits.

### Native outcomes and compatibility

A valid Conclave `used` event reports the author's interaction gesture and progress, not success of the native interaction. Passing input onward does not prove that a door opened, an item was placed, or trading began. Observing those outcomes would need a separate supported event. Conversely, explicit consumption must not trigger the native behavior later as deferred work or repeat a held item effect through the other hand.

The adapter must coordinate client prediction, server admission, both-hand paths, and native repeats. [Fabric 26.2 source research](interaction-input-research.md) confirms that existing callbacks expose attempted native use and do not supply a complete fresh-press/hold lifecycle. Earlier cancellation by another mod, unsupported interaction paths, and client/server disagreement need explicit compatibility handling; do not promise cancellation of arbitrary side effects already performed elsewhere. Unsupported required integration follows the existing validation/error policy rather than quietly crediting an interaction the server could not verify.

## Current block adapter

Arena location bindings select the containing block cell by flooring each coordinate. The server uses Minecraft's outline ray and native block reach, reduced by an authored `reach` when present. It checks that terrain along the ray is already entity-ticking. A different block identity at the cell invalidates held progress, including a break and rebuild between simulation ticks. Property changes on the same block, such as its powered state, preserve the generation.

The client uses ordinary Use and shows prompts only for server-eligible recipients. A press binds an opaque offer to the current attempt, mechanic activations, target generation and connection. A custom press alone earns nothing: admission occurs in the actual Fabric server block-use callback. All matching Conclave mechanics observe the gesture before queued rules run. Both-hand repeats and held-item fallback share the consumption decision. Native callbacks canceled earlier by another mod cannot be observed by this adapter; side effects already performed by other mods cannot be undone.

Continuation arrives every four client ticks. The server expires an unrefreshed gesture after one real-time second and releases unfinished holds before advancing the simulation. Release, input focus loss, physical ineligibility and replacement discard progress. A held key cannot acquire a replacement activation. The holder receives only their own server-reported progress; when several holds share a gesture, the bar shows the hold with the longest remaining duration. A completed or interrupted gesture asks for release before another use.

Input limits are currently 16 messages per player and 1,024 total per server tick, with at most 128 recipients per gesture. Geometry checks bound work to 128 blocks and 64 horizontal chunks and never load terrain. These are development capacity limits; broader load and other-mod compatibility measurements remain release work. Native NPC-group input and the complete author-defined presentation vocabulary are still pending.
