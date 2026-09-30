# Player identities in events and mechanic results

Status: Q223-Q224 are accepted. They define explicit player selection for subscriptions and common timing and reason fields for mechanic outcomes. Existing event meanings, committed outcomes, typed identities, and scope boundaries remain in force. No implementation exists.

## Q223: an explicit player field for a subscription

Accepted: allow optional `on.player` to name one documented identity field in the selected event. Admit that subscription only when the field is present and identifies a player. In that subscription's typed view, the same field becomes a required player reference and supplies the identity for `per_player` limits.

```yaml
rules:
  - id: mark_finisher
    on:
      source: {group: guardians}
      event: defeated
      player: attacker
    once: true
    per_player: true
    do:
      - apply_aura:
          aura: finisher
          target: {event: attacker}
```

This fragment can mark each attributed player once during the rule's owning activation, provided that the aura application permits that target and scope. It uses the actual fatal attacker. A non-player attacker or missing attribution does not invoke this rule. Ordinary target authority and availability still apply; the choice is not an implicit participant filter or a grant of control over outsiders.

### Typed matching without changing the event

Resolve `on.player` against the source's registered event schema. Reject unknown fields, dotted paths, scalar fields, and identity kinds that cannot denote a player. A required player field is also a valid explicit choice. There is exactly one chosen field, not a player list, fallback sequence, selector, or expression.

Keep the original event, field names, values, and source identity unchanged. `player: attacker` makes `{event: attacker}` usable as a required player in that subscription; it does not add `{event: player}` or replace a field already named `player`. `{event: target}` still refers to the original affected target. Other subscribers see their own schema view.

When `on.player` is omitted, keep the source event's existing triggering-player contract. For example, participant `revived` defaults to its revived `player`; interaction `completed` defaults to its final interactor. A deliberate `on.player: helper` on `revived` instead admits assisted revivals with a helper and uses that helper for this rule's player-specific limits. The event's `player` still means the revived participant and `helper` still means the helper. This is an explicit override of invocation identity, not an implicit change to either payload field.

Do not change other implicit predicate subjects through this option. Authors can use an explicit typed `target` such as `{event: attacker}` when a condition or action should address the chosen identity. The editor shows the player field used for limits and the separately available payload fields.

The choice checks the identity carried by the admitted event, not whether it currently has a loaded entity, is alive, belongs to the roster, or carries an aura. Rules retain their ordinary live `if` guards and action requirements. Historical killer qualification under Q221 remains a separate objective contract. An attacker who lost an aura after the hit can still be the event's attacker even if a current `has_aura` guard is false.

A typed player identity must already be present in the source observation. This option cannot infer projectile owners, promote a pet to its owner, identify an earlier attacker, or manufacture a player for aggregate completion. Overall matcher `completed` has no player field to choose. Shared matcher `matched` and `mismatched` can explicitly choose their optional `player`, receiving only attributed submissions.

### Invocation and exports

Perform the identity match before evaluating the rule guard and reserving invocation limits. Missing or non-player values are nonmatches; they consume no `once` or cooldown. Existing false-guard behavior remains unchanged. After a match, `per_player` uses that chosen identity within the rule's existing owning activation. Reconnect does not reset its bucket, and a different action recipient does not replace it.

This creates no second emitted event and no replay. Multiple differently configured subscriptions can observe the same committed event under existing declaration order. An ordinary presence guard alone still does not narrow a field or enable `per_player`; the explicit subscription option is the schema guarantee. Base group `defeated` still has no required player when this option is absent.

Allow the same `on.player` form inside `export.events` source selection. Resolve it before the export's guard and explicit `data` mapping. A mapped chosen field is now a required player because the declared subscription guarantees it, rather than because a guard happened to test its presence. Other optional fields stay optional. An omitted `data` still copies no payload.

An export preserves a triggering-player contract only if its `data` directly forwards the selected source identity as a required typed field. This also applies to a source's default required triggering player when there is no explicit `on.player`. Renaming that field preserves its identity and requiredness. If the identity is omitted from `data`, the public event has no hidden triggering player. Forwarding a different player field does not silently select it.

If several aliases directly forward the same chosen identity, they still represent one triggering player, not several invocations. Additional player-valued data does not replace that choice. The public schema records the contract in the editor. A receiving rule may make its own explicit `on.player` choice among eligible public fields. Never infer a trigger just because an export happens to have a field named `player`.

No export can forward an undeclared private group, retain an ended scope, extend a player's native lifecycle, or publish private information to clients without an authorized presentation. This contract adds no untyped cast, arbitrary field mutation, general event transform, or automatic client message.

## Q224: common mechanic completion and failure fields

Accepted: keep the standard mechanic lifecycle notifications `started`, `completed`, and `failed`. Add required duration-valued `elapsed` to mechanic `completed` and `failed`, and required registered `reason` to mechanic `failed`. Preserve each mechanic's existing documented extra fields and triggering-player meaning.

| Notification | Common additional fields | Meaning |
| --- | --- | --- |
| `started` | None | The existing Q167 initialization boundary has been reached. |
| `completed` | `elapsed` | This mechanic's success has committed. |
| `failed` | `elapsed`, `reason` | This mechanic's ordinary gameplay failure has committed. |

This is a deliberate common-field addition to Q195's otherwise payload-free overall matcher completion. It still supplies no player, expected answer, last interactor, or contributor list. Interaction completion retains its final `player` and use-count fields alongside `elapsed`. Do not remove those fields in an attempt to make every completion schema identical.

### Timing

Measure `elapsed` using the mechanic occurrence's existing simulation clock from its actual activation to its committed terminal outcome. Include waits after activation, such as waiting for a declared group or a supported startup operation. Do not include time before a future phase, sequence step, or repeat occurrence actually activates, or delay in dispatching its already committed notification.

The value can be zero when existing state satisfies the mechanic during its activation tick. It is a nonnegative typed duration with Q87's tick resolution. Use existing duration comparisons, including zero thresholds, rather than raw wall-clock timestamps or an untyped numeric seconds value. A paused server does not add gameplay time.

Each repeated occurrence starts its own measurement. A sequence, parallel, repeat, or layers parent measures its whole activation, including its own waiting gaps, rather than summing child times. This report does not add a new timeout, alter an existing deadline, or turn publication and disconnect-grace timing into simulation-time policies.

### Failure reasons

Start with the following common registered reasons where the selected mechanic supports them:

| Reason | When it applies |
| --- | --- |
| `deadline` | That mechanic's own authored gameplay deadline expired. |
| `condition` | Its own authored terminal `fail_when` condition became decisive. |
| `unreachable` | A defeat requirement became permanently impossible under Q220-Q222. |
| `child_failed` | A required child failure caused a composition to fail under its existing policy. |

These are descriptions of already supported outcomes, not new failure triggers for every mechanic. An elapsed capture duration is success, an interrupted interaction is unfinished progress, and a pattern mismatch remains recoverable input. An empty or temporarily insufficient selection does not become `unreachable` unless that capability's accepted completion contract establishes permanent impossibility.

For a composition failure, report `child_failed` at the parent. The child can report its own reason to listeners allowed to observe it. Do not automatically leak a private child's identifier, handle, nested state, or complete failure tree through the public parent result. Authorized diagnostics retain the causal detail, and an author can explicitly export permitted child data.

Each capability publishes its supported reasons in its schema. The common codes keep these meanings; future extension-specific reasons need registered definitions and typed documentation. Reject unknown codes in a `reason` comparison rather than accepting arbitrary strings. Payload schemas reserve these common field names and types.

```yaml
on:
  source: {mechanic: ritual}
  event: failed
if:
  event_value:
    field: reason
    equals: deadline
```

This rule fragment observes a known mechanic that supports a deadline. Its failure has already committed; any actions react to that outcome and cannot convert it into success. A complete rule still needs an ID and actions.

### Outcomes and delivery

Admit one terminal gameplay notification for a mechanic activation, choosing `completed` or `failed` only after its existing success/failure arbitration. If several failure requests determine the same failure, expose the first applicable one in stable admitted runtime order and retain other causes in diagnostics. Do not emit several failures or both terminal events for the same activation.

The existing causal notifications still come first: for example, final interaction `used`, final pattern outcome and personal completion, or the NPC `defeated` event that made an objective terminal. Do not wait for their authored reactions to decide a result already committed by that operation. Eligible enclosing subscriptions can receive terminal notifications under the accepted queue and scope rules; ended subscribers cannot be revived to receive them.

Cancellation does not masquerade as success or failure. Keep cancellation and technical-error details in authorized inspection and diagnostics; this initial common contract adds no generic gameplay `cancelled` or `errored` callback. Native player lifecycle events and their separate meanings remain unchanged. A phase ending cancels unfinished mechanics without giving each an invented `failed.reason`.

Technical spawn errors, missing required entities, failed required integration, and runtime limit overruns retain Q69's technical-error handling. Do not convert them to `unreachable`, run ordinary failure punishments, or award completion. A documented action fallback or retry retains its own result contract and does not automatically emit a mechanic failure.

The event source and ordinary provenance already identify the producer and activation. No mutable mechanic handle, hidden puzzle state, winner, elapsed record from a prior activation, or unrequested list is added. `export.events` copies these fields only when explicitly mapped. Reconnect, publication, restart reconciliation, or a late subscription does not replay lifecycle events.

This contract concerns mechanic result schemas. It does not add phase/attempt terminal event sources, self-terminal finalizers, new lifetime exceptions, or cancellation hooks. [Q243](phase-events-and-rule-order.md#q243-observe-a-named-phase-from-encounter-rules) accepts a named phase lifecycle source. [Q245](ending-scope-presentation.md) separately accepts attempt-terminal events and bounded presentation-only self-terminal reactions for encounter, phase, and layers bodies.

## Related contracts

These contracts refine [rules and invocation limits](phases-and-rules.md), [typed event comparisons](event-conditions.md), [public exports](reusable-mechanic-contracts.md), [mechanic startup](mechanic-start-and-parameters.md), [matcher payloads](pattern-feedback-and-controls.md), [interaction completion](interaction-input-and-progress.md), and [ordinary versus technical failure](execution-and-errors.md). The accepted [killer qualification](defeat-killers-and-damage-types.md) and [NPC defeat event](npc-defeat-events-and-requirements.md) retain their separate meanings.
