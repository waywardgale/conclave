# Encounter start rules and ready checks

Status: Q104 rejects the proposed leave command. Q105-Q106 replace the proposed invitation lobby and fixed player-count field with author-controlled activation conditions, plus a ready-check command. Q108-Q111 are accepted with the user's field rename, online-raider default, HUD popup, and author responsibility for retriggering. Q112-Q116 are accepted for player-selection sources, raider identity, effect filters, popup controls, and conflicting-state handling. No command, parser, or encounter runtime has been implemented.

## Q104: no leave command

Rejected by the user: do not add `/conclave leave`. Remove the proposed forfeit workflow and do not reintroduce it through a renamed command or a mandatory Leave attempt screen. Ordinary movement continues under Q103. Participation bookkeeping after movement is a separate engine concern, accepted in Q109, rather than a player command that changes ordinary Minecraft behavior.

## Q105: author-controlled encounter starts

Accepted from the user's revision: add a ready-check command. Authors define when an encounter starts through YAML, including a count of players in an area, a particular player entering an area, an interaction with a configured target, or custom combinations of typed conditions. Continue using the accepted term `area` for the user's zone examples.

The invitation screen, preparation-group leader, mandatory invitation acceptance, and compulsory readiness before every attempt were not accepted and are removed from the design. A ready check is a separate coordination capability whose role in activation must be explicit. Start conditions reuse registered event sources and the existing typed condition system instead of adding command execution or embedded expressions.

The existing `start` field names the initial phase. Q108 keeps that meaning and names the encounter-start field `start_encounter`. GM start, stop, restart, and test operations retain their existing administrative scope. Q274 accepts bypassing authored start/readiness guards for manual start while retaining engine admission and the declared participant selector.

## Q106: counts are authored start conditions

Revised by the user through Q105: express player-count requirements with the existing `count` condition and comparisons. Do not add the proposed special `players: 6` or `players: {min, max}` admission field, automatic invitations, or count-based difficulty scaling. Authors can combine lower and upper bounds with `and`, or use `equals` for an exact count.

A count condition identifies which players and area it checks. Other conditions can restrict identity, readiness, or other supported pre-start state. Starting an attempt still needs valid engine resources and well-defined participation for revival and recovery; it does not grant an exclusive physical world to those players. Later occupancy changes affect only the conditions and mechanics that the author actually declares.

## Q108: encounter-start rules and condition composition

Accepted with the user's rename: use a `start_encounter` list of named start rules. The spelling normalizes the user's `start_enocounter` typo to `start_encounter`. Each rule has `id`, optional `on` using the existing typed source/event shape, and optional `when` using the existing condition tree. Require at least `on` or `when`. All predicates within a rule must hold for that rule, and separate rules provide alternative ways to start the same encounter. The engine still commits only one start when several rules qualify together.

```yaml
start: ritual
start_encounter:
  - id: group_gathered
    when:
      count:
        players:
          area: entrance
        at_least: 6
```

This is an encounter-body fragment. It describes a count-triggered start while preserving `start: ritual` as the first phase. Exact counts use `equals`, ranges combine comparisons with `and`, and alternative checks use `or`. Existing `not`, `any`, and `all` semantics remain unchanged.

Without `on`, observe the condition while this encounter/arena pairing is idle and available. With `on`, evaluate `when` only for the matching event. A particular player's entry uses an area `entered` event plus a typed check on that event's player. A button, block, or supported entity interaction uses its registered interaction event and optional conditions. An identity can be selected in game and resolved to a stable player identity; the exact identity selector fields remain part of the vocabulary catalog.

A false event guard discards that start request. It does not store an old interaction to trigger hours later when another condition becomes true. Stacking conditions means combining current checks. Remembering several separate historical interactions would require an explicit pre-start state capability, which is not silently inferred from `and`.

Pre-start sources and predicates must be valid before an attempt exists. Reject a reference to an uncreated phase mechanic, attempt timer, private aura contribution, or spawn group. Resolve logical areas and interaction locations through the encounter/arena binding. Evaluate activation using the available published candidate revision, then pin one complete revision when an attempt is actually created. A publication or eligibility change during preparation requires revalidation; never mix definitions from two revisions.

Omitting `start_encounter` leaves the encounter available for explicit GM start or test. A rule does not let YAML issue arbitrary commands or bypass engine reservations, resource budgets, or the global revival policy. Exact start-event source fields beyond the already accepted event shape remain part of the registered capability catalog.

## Q109: selecting and tracking participants

Accepted with the user's changes: use a `participants` player selection for the players whose encounter progress, revival, recovery, and private presentation the attempt tracks. Default to all online raiders. Provide filters for all online players, raiders excluding GMs, area, identity, applied auras, and other supported player conditions. The default is server-wide, with no implicit arena, distance, dimension, or living-state restriction. An author can explicitly narrow it. Eligibility conflicts are defined in Q116 rather than silently changing the selected set.

Before an attempt exists, player predicates query the chosen world-player collection and authored filters, not a nonexistent current-attempt roster. The player whose entry or interaction triggers a start need not be the whole tracked set. Snapshot the configured participant selection at start and assign the attempt identity once. Q112 defines concrete selector fields, and Q113 refines the meaning of raider and administrative changes.

Walking away does not silently forfeit, erase progress, clear auras, reset revival, or create a new participant identity on return. The initial tracked set remains associated with the attempt until it ends, subject to the accepted disconnect and revival policies. Authors can explicitly react to an empty area through conditions. No automatic boundary punishment or leave command is implied.

Late arrivals remain ordinary Minecraft players and retain normal movement, combat, collision, and interactions. They do not automatically receive attempt-specific private clues or revival/recovery bookkeeping. Existing mechanics retain their documented selection defaults; authors may explicitly configure broader supported eligibility for a particular mechanic. Dynamically adding players to the tracked set remains an opt-in capability with its own lifecycle contract.

Raider filtering excludes GMs when that filter applies. The exact treatment of operator-only players and a GM grant or revocation after the initial snapshot is defined in Q113. This is a selection policy, not a PvP team, invitation system, world-access permission, or entitlement to ordinary dropped items. Private Conclave state ownership does not suppress normal Minecraft interactions.

## Q110: ready-check command

Accepted: name the command `/conclave readycheck <arena>`, with the arena argument optional when the player's context identifies exactly one configured target. If several encounter bindings need different recipients, the in-game command response asks the caller to select the intended binding rather than guessing. Authors configure the recipient selection, defaulting to the candidate participant selection when one exists.

Show each selected online player a popup HUD asking them to confirm Ready or Not ready, as explicitly requested by the user. Show the caller who answered or is still pending. Q115 defines the popup interaction details. Every participant can answer only for themselves. Any player included in that selection can request a check for it; GMs can select a configured target administratively. Rate-limit requests and keep one active check for a target so the command cannot repeatedly interrupt everyone. It grants no authority to force a response, modify YAML, or start an encounter administratively.

Use a configurable timeout, default 30 seconds of real elapsed time from creation. A disconnected or nonresponding recipient never counts as ready. Expose a typed `ready_check` result with a `passed` check for authored activation conditions; all selected recipients must have answered Ready, the set must be nonempty, and the check must still be current. The check and its usable result expire at the deadline, even if everyone answered earlier.

Readiness is informational by default. It becomes a start requirement only when the author references it in an activation rule, and becomes a direct start trigger only if the author declares that behavior. Invalidate the result when its recipient set or relevant encounter/arena revision changes, and consume it when an attempt starts so a previous check cannot authorize a later attempt. Repeating a check does not preserve old answers.

The recipient set must match the set required by the configured readiness predicate; checking two people cannot imply readiness of an eventual six-player set. The command is a short in-game coordination tool, not a mandatory lobby or preparation screen.

## Q111: rearming automatic starts

Accepted: allow a condition-only rule to fire once when first armed and satisfied. After an attempt finishes, that rule must observe its condition become false while idle before another true result can start a new attempt. That rule does not repeat merely because its condition stayed true. The user explicitly places responsibility on authors to design triggers and areas that do not unintentionally start the encounter again. A different rule or a genuine new qualifying transition may still start it; the engine does not invent additional exclusions, cooldowns, or world changes to correct authored logic.

Event-based rules require a fresh matching entry or interaction after the encounter becomes idle again. Events during an active attempt are not queued as future start requests. Initialize area occupancy from current positions without synthesizing `entered`, consistent with Q59. If an author wants occupancy rather than actual movement across a boundary, use a condition-only rule.

Deduplicate simultaneous qualifying rules into one pending start for an arena. Recheck condition guards, participant selection, content revision, safe placement, and resource readiness before activation. A failed preparation reports its cause and does not spin indefinitely while the same condition remains true; it needs a new eligible trigger or an explicit administrative retry. Bound all preparation work and release resources acquired by an aborted preparation.

Publishing does not manufacture entry or interaction events or reset a previously fired rule merely to bypass rearming. Newly authored condition rules can be armed against current world state; unchanged rules retain their relevant rearm state. Persistence of idle activation state across a server restart remains part of crash/restart design. Deliberate automatic replay may be added through an explicit policy later, never as the default effect of a still-true start condition.

## Q112: selector fields and collection defaults

Accepted: add `from` to the existing player selector to name its source collection: `online_raiders`, `online_players`, or `participants`. The first two query connected world players; `participants` queries an already running attempt's tracked set. Reject `from: participants` where no attempt exists or where it would recursively define that same participant set.

Use `online_raiders` by default for the initial `participants` selection and pre-start player queries. Preserve Q60's living, online participant default for ordinary gameplay selectors inside an active attempt, with Q238's active-participation default for the `participants` collection. An explicit source chooses a different collection; it does not secretly apply the previous source's spatial or administrative filters. The collection names do not themselves change a player's game mode, permissions, health, or world interaction.

Keep accepted concise filters such as `area` and `role` where meaningful. Add `where` for the existing typed condition tree evaluated against each selected player. Concise filters and `where` combine with AND; alternatives or exclusions go inside `or` and `not`. Reject filters whose state does not exist in the current context, such as an unassigned attempt role before start.

```yaml
participants:
  from: online_raiders
  area: entrance
  where:
    and:
      - has_aura: attuned
      - not:
          has_aura: exhausted
```

This selector explicitly narrows the user's server-wide default to raiders in one area with the required aura. `from: online_players` includes GMs unless another explicit filter excludes them. Omit `area` to keep the server-wide collection; do not silently restrict it to the arena's dimension. Source membership, typed identity filters, aura checks, and explicit player-state checks can be combined. [Q170](roles-and-player-state.md#q170-explicit-online-and-life-state-filters) accepts the concrete player-state fields and contextual defaults. Exact identity predicate fields remain part of the capability catalog.

Selections used by one action remain snapshots for that action under Q60. Queries used by ongoing conditions evaluate current state. The initial participant selection is captured once under Q109. Publishing does not rewrite an active attempt's captured participant identities.

## Q113: raiders and GM status

Accepted: a raider is a player who is not in Conclave's GM registry. Do not add a second Raider permission grant or infer raider status from Survival, Creative, or an authored role. Operator authority alone does not make someone a GM; an operator who wants exclusion from raider selections can use the existing GM membership command.

Each new live `online_raiders` selection uses the current GM registry. Granting or revoking GM privileges still takes effect immediately for administration under Q41. A roster captured before that administrative change remains the same roster until the attempt ends, so granting GM authority cannot erase an active participant's death, progress, or recovery obligation. Conversely, revoking GM authority does not automatically enroll someone into a running attempt.

Where authors explicitly query `online_raiders` during active play, the live selection reflects the current registry. Where they query the captured `participants`, it reflects that roster and its explicit filters. Changing GM status is not an implicit aura removal, role reset, phase transition, or escape from ordinary Minecraft rules.

## Q114: aura and vanilla-effect filters

Accepted: `has_aura` checks a presently applied Conclave aura, while `has_effect` checks a vanilla or supported registered status effect, such as `minecraft:strength`. A definition existing in YAML is not an applied aura. Do not automatically translate every Conclave aura into a vanilla effect or create an aura merely because a selector mentions it.

Match Conclave aura IDs using the existing namespace rules and registered effect IDs through their own registry. A filter reads the selected player's currently applicable state; expired or removed contributions do not match, and an offline cache is not a currently online player. For a stacked aura, presence means at least one active contribution; more detailed stack comparisons use an explicit predicate rather than changing `has_aura`.

Pre-start filters can inspect an aura that already exists with a valid owner and lifetime. Reject a reference that specifically requires an instance created only after the very attempt being started exists. Keep these presence checks server-side; evaluating a filter does not broadcast a player's auras to other clients. Do not expose another attempt's private counters, answer tokens, or aura-source internals through generic property traversal. References to private aura instances still need a valid scope under Q81. A player's public named aura membership and a private source's internal state are different contracts.

Applying Conclave auras outside attempts still needs an explicit supported owner, application path, lifetime, and cleanup contract. This question accepts readable filtering of existing state, not an implicit global aura scheduler or permanent lifetime. [Q117](auras-and-world-lifetimes.md#q117-auras-that-outlive-an-attempt) accepts player-owned applications outside an attempt's lifetime.

## Q115: ready-check popup interaction

Accepted: show a compact HUD popup with the encounter/arena name, Ready and Not ready choices, and the remaining response time. Provide explicit rebindable response keys and a way to focus the popup for mouse interaction. The popup does not pause the server, change game mode, freeze movement, or automatically take the cursor away during ordinary play.

Never treat the next attack, jump, movement key, or existing held input as consent. Dismissing the popup leaves the answer pending; it does not silently answer Ready. Once answered, show the selected response and permit changing it while the same check is open and unused. Expiry closes the prompt and leaves nonresponses unready. A disconnected client cannot submit an old response to a replacement check.

Use visible text and symbols as well as color. Show only this check's coordination information, not unrelated private mechanics or clues. Responding confirms readiness only for the identified check and recipient set. The server owns the check identity, deadline, and response state; stale or duplicate input cannot confirm a different check. This specifies intended UI behavior, not a tested Fabric screen implementation.

## Q116: selected players with conflicting state

Accepted: selection and start eligibility remain separate. Select exactly the players requested by the author's filters, then report a start conflict if a selected player has an incompatible active attempt, an unresolved death/recovery state, or disconnects before the start commits. Do not silently skip them, revive them, teleport them, or start with a smaller set.

Require at least one selected player and respect server resource limits. An author who wants a narrower set can add an area, aura, identity, or explicit state filter. This matters for the accepted default: all online raiders can include players in another dimension or occupied by another encounter. Location in another dimension alone is not a reason to reject a selected living player or force them into the arena; the arena itself still has one dimension under Q100.

A ready answer does not override eligibility or a death state. Revalidate the selected identities, conditions, current content revision, and operational capacity before committing one attempt. A change that invalidates the ready-check recipient set requires a new check when readiness is an authored requirement. Once started, the accepted roster snapshot and disconnect policies govern subsequent changes.

Report the conflicting player and reason in the relevant start diagnostics without broadcasting private encounter state. This is an engine consistency check, not a world movement or combat restriction. Dead-player admission or simultaneous membership in multiple attempts would require an explicit future lifecycle contract rather than a silent exception.

[Q274](administrative-operation-contracts.md) accepts explicit manual-start behavior. [Q275](event-catalog-conventions.md) accepts typed pre-start area and bound-block interaction observations while preserving context validity and ordinary admission.
