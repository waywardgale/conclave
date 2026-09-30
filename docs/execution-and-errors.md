# Execution and errors

Status: Q68-Q69 are accepted. Single active phases, explicit progression, outcome precedence, simulation time, and owned cleanup are already accepted in [runtime semantics](runtime-semantics.md). No engine implementation exists.

## Q68: event processing and transition boundaries

Accepted: the server gives each admitted gameplay event a stable sequence and processes events in queue order. Matching rules within one scope run in manifest declaration order; each rule's actions run in their written order. A reusable definition retains its internal rule order. [Q244](phase-events-and-rule-order.md#q244-stable-reaction-order-across-rule-scopes) accepts scope-activation creation order across listeners, local rules before local exports, and bounded continuations for documented pending actions. [Q246-Q247](simulation-stages-and-outcomes.md) accept the tick-stage order and same-tick parent-outcome settlement, including the final revival/expiry boundary. This is a deterministic handling contract once events are admitted, not a promise that network arrivals or vanilla AI are reproducible. [Q167](mechanic-start-and-parameters.md#q167-a-started-event-before-child-mechanics-begin) fixes the startup boundary: initialize state and subscriptions, process the activation's `started` event through this queue, then activate dependent children. Required pending work keeps those children pending without blocking the server thread.

Later conditions and actions see state changes from earlier actions. Each individual action retains its initially resolved recipient set, as accepted in [selection and patterns](selection-and-patterns.md). Events emitted by an action join the queue and run after the current action list. They do not recursively invoke listeners inside an unfinished action. Independent rules observing the same event each run unless their conditions no longer hold.

Keep the current phase active while collecting its tick's outcome requests. Resolve competing success and failure together at the end of the tick under the accepted encounter policy. Do not discard a same-tick boss defeat merely because the final participant's death was observed first. Engine errors follow their separate handling policy; they are not another authored success/failure tie.

Commit at most one phase transition for an attempt in a tick. Clean up the ended phase and start the next phase on the next simulation tick. Events and delayed work identify their owning attempt and activation; work belonging to an ended activation cannot affect a new use of the same authored ID. Encounter-owned work can survive phase changes under its existing lifetime policy.

Revival still honors [Q44](death-and-revival.md#q44-combat-changes-and-expiry-ordering): an eligible revival completing on the final allowed tick wins over window expiry. Event intake and expiry stages must enforce this deliberately. Generic FIFO ordering does not replace that accepted rule.

## Q69: failure categories and bounded work

Accepted: ordinary mechanic failure is part of gameplay. A wrong pattern input, unmet condition, or elapsed deadline follows the authored rules. Invalid client interactions are rejected and do not become engine errors that stop an attempt.

An engine error means required work cannot meet its declared contract, such as an NPC group that cannot reach its requested spawn count or an unexpected missing required member. Unless the action documents a recoverable outcome and the author handles it, stop the affected attempt with a distinct technical-error result. Cancel its future work, clean up everything it owns, and perform accepted participant recovery. Do not run gameplay wipe punishments or award encounter success for that result. Unrelated attempts continue.

Actions may document recoverable failures and support an explicit fallback or a retry with finite attempts and delay. There is no implicit endless retry. A retry must not duplicate effects already applied or leave a partially created NPC group behind. Contract violations and safety-limit overruns cannot be turned into ignored errors through an authored catch-all. Cosmetic output may report a warning without stopping gameplay when its capability explicitly permits that behavior.

Validate unknown fields, invalid references, unsupported capabilities, and statically invalid bounds before activation. Runtime ceilings cover work processed per attempt per tick, queued events, active timers, repetition, and owned entities. Exceeding an attempt's ceiling stops that attempt as an error; server-wide admission limits refuse new work before exceeding capacity. Numeric defaults require a measured implementation budget and remain open. Cleanup and participant recovery need reserved capacity so reaching a gameplay limit cannot prevent recovery.

[Q245](ending-scope-presentation.md) permits only bounded best-effort presentation after an ordinary result commits. A skipped final cue does not revise the result, and final presentation cannot perform required gameplay work or control cleanup. Unhandled required-work failure before attempt commitment still follows this technical-error policy.

[Q254](durable-completion-and-reward-storage.md#q254-save-success-and-its-rewards-before-announcing-victory) distinguishes confirmed completion-save failure from an uncertain submitted write after gameplay has closed. Confirmed noncommitment produces technical interruption; uncertainty requires reconciliation of that original operation and cannot yet be labelled success or failure. Safe cleanup and recovery proceed after the bounded live wait. A subsequently confirmed committed success retains its recorded allocations without replaying gameplay.

Diagnostics identify the revision, attempt, file and source location, authored rule or action ID, and failure cause. Players receive a short interruption message; authorized administrators receive details. Avoid exposing private puzzle values in ordinary logs. This diagnostic contract does not claim a complete test suite, runtime implementation, or measured performance.

[Q241](client-resource-failures-and-observers.md#q241-confirmed-loss-of-required-resources-after-admission) applies this technical-interruption policy to confirmed loss of an admitted participant's required captured resources. Ordinary cosmetic failures retain their documented skip or fallback behavior; real disconnections retain the accepted reconnect opportunity.
