# Simulation stages and outcome reactions

Status: Q246-Q247 are accepted. They fill the ordering gaps left by Q68, Q244, and Q245. Existing simulation clocks, gameplay precedence, startup barriers, and next-tick progression remain accepted constraints. Initial queue and phase helpers exist, but this complete stage coordinator and native tick integration remain unimplemented. See [implementation status](implementation-status.md).

## Q246: ordinary work before timed work

Accepted: give each running simulation step a documented order. Process already-admitted ordinary inputs and world observations before that step's due Conclave work. Use a stable order for independent scheduled operations, and drain ready event reactions between operations. Preserve the specific revival and aura expiry exceptions.

| Stage | Work |
| --- | --- |
| Start | Admit eligible phase, sequence, and repeat activations scheduled by an earlier tick. Initialize each scope and process `started` before its dependent children under Q167. |
| Ordinary observations | Admit supported native gameplay observations, player input, and ready asynchronous continuations in server observation order. Process their ready reactions through Q244. |
| Spatial observations | Observe area membership, emit valid changes, and process their ready reactions. Activation still initializes occupancy without fake entry events. |
| Due work | Run eligible Conclave operations whose simulation time is due, including progress advancement, held-use completion, timers, aura processing, and supported delayed work. Process each operation's queued reactions before the next independent operation. |
| Outcomes and closure | Settle ready outcome reactions, honor valid last-tick revival before grave expiry, and arbitrate final attempt outcomes. Apply frozen routing, final presentation, and cleanup. Q247 defines this coordination. Schedule replacement phases, sequence steps, and repeat iterations no earlier than the next tick. |

The ordinary observation cutoff determines which external input and asynchronous results belong to this step. Work arriving after that cutoff is admitted on a later step, without client timestamps backdating it. Synchronous consequences of Conclave's own operations still enter the current queue; they do not reopen external intake or recursively invoke rules inside an unfinished operation. Real-time reconnect expiry keeps Q237's separate admission deadline and gains no extra grace from these simulation stages.

This is a Conclave scheduling contract. Native combat and other immediate Minecraft operations retain their supported native commit points. Do not defer a native damage decision until a later stage or claim to reorder vanilla AI, every mod callback, or network arrivals. The adapter must verify the hooks needed to observe native outcomes and enforce Conclave input eligibility. Diagnostics must distinguish native commitment, event admission, and later authored reactions.

### Independent due operations

Order independent due operations by their simulation due time, then by a stable order assigned when the corresponding Conclave work activation is first admitted. Never use map iteration, authored ID sorting, or callback registration order as the tie-breaker. This producer order is separate from Q244's listener order.

Keep a work activation's order while it continues. A new activation, including an explicitly restarted named timer, receives a new order; stale scheduled entries cannot affect its replacement. Refreshing an aura contribution preserves the aura's existing periodic cadence and does not move it to a new logical activation merely to change scheduling priority. A newly created positive-duration timer or hold cannot consume elapsed time from before its own start.

For simultaneously due work inside one aura, treat its declared periodic entries followed by its natural expirations as one ordered operation. Run the entries in declaration order, including contributions expiring at that instant, then reconcile natural expiry under Q159. Queue authored reactions until that operation finishes. Native death and aura death policy still take effect before the next periodic entry. Independent aura operations use the stable order above; add no aura priority field.

Capture and held progress advance at most once for the simulation interval they actually experienced. Recheck the current supported eligibility and target state when their operation runs. An earlier death or departure can prevent unfinished progress; completed credit remains latched. Refresh spatial membership when an admitted supported state change requires it, without awarding a second interval of capture progress or manufacturing repeated entry events. Packet frequency and repeated condition evaluation cannot accelerate progress.

The grave rule is an explicit ordering constraint: a still-valid revival completing on the last permitted tick precedes that grave's expiry. Do not commit grave expiry merely because ordinary due work has finished; eligible result reactions can also perform ordinary YAML revival under Q39. Q247 defines the closing-stage coordination. This gives no general immunity to earlier damage or interruption. Ordinary interaction keeps Q176's stable operation order, with no special completion-versus-damage tie override.

Recheck ownership, activation, cancellation, and current timer state before a due operation commits. An earlier reaction can cancel work that has not happened yet. Once expiry or completion has committed and its event has been admitted, later actions cannot retract that history. For example, an input rule stopping a timer before the due-work stage prevents its expiry; stopping it after its `expired` event does not erase that event.

The event queue and ready reactions remain bounded by Q69. A documented pending action yields its continuation and does not stall the stage or advance the simulation clock by waiting. Required startup work still gates dependent startup. No new zero-delay scheduling, catch-up pulses, retry policy, or priority knob is introduced. Numeric limits and exact native hook placement require implementation measurement and verification.

## Q247: settle child results before their parent's outcome

Accepted: let ready reactions to child results affect a still-live parent's outcome in the same tick. Settle nested results from children toward their enclosing scopes, processing eligible queued reactions before committing an enclosing result. Re-evaluate affected live conditions until ready work settles, within the existing execution budget. Committed results and phase routes stay fixed.

An operation that already commits final progress keeps that result. For example, Q175's final `used` notification cannot undo the interaction that produced it. Preserve each capability's causal notifications before its terminal notification. Gather competing same-tick result requests under the relevant mechanic or composition's existing outcome policy; a child completing does not prematurely end its enclosing phase before that tick's other admitted gameplay is considered.

Collect sibling outcomes before deciding their composition. Resolve nested compositions from their children outward, using stable activation order for independent work at the same dependency level. Preserve `parallel` all/any semantics, layers success/failure precedence, and the distinction between an ordinary child failure and a required engine error.

Before committing an enclosing result, process its already-ready child-result reactions and exports. Re-evaluate live condition objectives, `complete_when`, `fail_when`, and `repeat.until` against the resulting coherent current state. Read live conditions again if subsequent ready actions change their inputs before commitment; do not latch them merely because an earlier check was true. Explicitly latched objectives retain their accepted meaning. Check `repeat.until` before starting its body and during this settlement, with its existing success/failure precedence.

For example, a child-completed rule can increment its living parent's counter to three, allowing that parent's counter-based completion condition to succeed in the same tick. The counter does not need a hidden extra tick to become visible. This grants no additional simulation-time progress, no replay of already-committed operations, and no reversal of an earlier result.

Ready means executable now. A documented pending action does not hold an ordinary enclosing outcome open merely because the action may eventually finish. Its continuation remains bound to its owner and is cancelled if that owner ends. Authors who need work to determine progression must express that requirement through a supported objective or other accepted barrier. Required startup work retains its explicit startup barrier.

### Phase and attempt commitment

When the phase is ready to end, resolve its outcome and freeze its route while considering the attempt-wide result requests already applicable at that boundary. Keep final attempt commitment pending until the same-tick revival and expiry boundary below has settled. A phase route of `next` does not itself request attempt success. A phase route of `complete` does, and competes with party defeat using the encounter's accepted success-versus-failure setting. An unhandled required technical error keeps Q69's separate authority.

Determine which enclosing scopes must end before admitting terminal notifications. A frozen `complete` or `wipe` route closes the encounter to ordinary gameplay reactions even while the final attempt outcome awaits arbitration; it does not emit a provisional attempt result. This preserves Q245: when the attempt is ending, its ordinary named-phase listeners are no longer eligible. A phase that actually committed a result gets its permitted final presentation before the attempt's final presentation. Cancelling an unfinished phase still invents no phase result.

When the encounter survives a phase's `next` route, its ordinary named-phase reactions can change encounter state. Those changes can affect later gameplay but cannot reroute the phase that just ended. If such a reaction causes a new attempt-wide outcome or an unhandled technical error, settle it before the tick closes and cancel any now-invalid next-phase startup. Do not retroactively undo the committed phase history.

Apply the same lifetime checks to notifications caused by cleanup: only surviving eligible observers may react, and cleanup cannot resurrect its owner or reopen its result. Ready reactions and newly affected live outcomes may settle within this stage, but each activation commits at most one terminal result. One phase transition per attempt per tick and next-tick replacement activation remain in force.

### Revival before the final expiry boundary

First settle ready gameplay and result reactions from scopes still eligible to run them, including ordinary YAML revival. Keep a grave whose window is due this step available for a valid final-tick revival during that work. This includes a child-result rule in a live enclosing scope and an ordinary named-phase rule when the encounter survives that phase's frozen route. It does not grant ordinary reactions to an encounter already closing through `complete`, `wipe`, or another established ending cause.

After that ready work settles, commit the remaining due grave expirations, then process their lifecycle notifications and evaluate party defeat. Only then settle the final gameplay decision, with success and failure considered under the accepted precedence. Q254 requires successful qualification and generation against that decision state, then freezes it while success and allocations are durably committed. The storage wait cannot reopen arbitration or advance the closed attempt's gameplay clock; ordinary failure commitment is unchanged. Thus a successful final phase and loss of the last recovery opportunity still compete in the same tick. A phase's `next` route can remain historical even if subsequent expiry ends the attempt before the next phase starts.

Work that becomes possible only because an expiry has already committed cannot revive that expired grave retroactively. This is the same distinction used by timer cancellation: an already-ready valid completion wins the boundary, but a reaction to the committed expiry observes the resulting state. Pending asynchronous work and input admitted on a later tick do not reserve a revival opportunity. Administrative recovery keeps its separate explicit authority.

### Bounds and inspection

Do not allow a reaction cycle to run without limit while waiting for conditions to stabilize. Exhausting the ordinary work budget before commitment follows Q69's technical-error policy. Final presentation uses Q245's separate best-effort bound and cannot mutate gameplay to start another outcome cycle. Required cleanup retains reserved capacity.

Authorized in-game inspection should show the operation order, observed event, committed changes, chosen outcome, and frozen route without exposing private values to players. No new YAML evaluation stages, numeric priorities, arbitrary expressions, or scheduler scripts are needed.

## Related contracts and remaining work

Q246 selects ordinary producer scheduling. Q247 independently selects same-tick propagation from child results into still-live parent outcomes; it can follow any ordinary scheduling order that preserves the already-accepted timing constraints.

These contracts extend [Q68-Q69 execution](execution-and-errors.md), [Q244 listener order](phase-events-and-rule-order.md#q244-stable-reaction-order-across-rule-scopes), and [Q245 final presentation](ending-scope-presentation.md). They preserve [Q44 revival](death-and-revival.md#q44-combat-changes-and-expiry-ordering), [Q159 aura cadence](aura-periodic-and-queries.md#q159-periodic-damage-and-healing), [Q89-Q92 composition and timers](conditions-and-composition.md), [Q167 startup](mechanic-start-and-parameters.md#q167-a-started-event-before-child-mechanics-begin), and [Q175-Q176 held interactions](interaction-input-and-progress.md).

Exact native integration points, measured execution limits, synchronization payloads, and persistence reconciliation remain implementation design work. This document makes no claim that Fabric already provides this complete scheduler.

[Q254](durable-completion-and-reward-storage.md#q254-save-success-and-its-rewards-before-announcing-victory) accepts a bounded asynchronous durable-success boundary after this gameplay settlement. The final presentation pass remains separate; delayed confirmation cannot replay gameplay, alter frozen recipients, or hold world cleanup indefinitely.
