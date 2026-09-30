# Relic events, state checks, and carry auras

Status: Q184-Q186 are accepted. These contracts define relic lifecycle events, current-state checks, and optional carry-owned auras using the accepted identity, rule, condition, and contribution-ownership rules. No implementation exists.

## Q184: relic lifecycle events

Accepted: let a rule subscribe to one declared runtime relic instance through `source.relic`. Use the existing local or explicit encounter-scoped reference. A subscription can wait for initial creation and then follows that logical instance through drops, resets, and automatic returns. It never retargets a new activation that reuses the name.

```yaml
on:
  source:
    relic: north_orb
  event: picked_up
```

Expose events for committed changes, with the following public payload fields. All retain the existing attempt, activation, and operation provenance internally.

| Event | When it occurs | Public fields |
| --- | --- | --- |
| `spawned` | Initial creation successfully places the instance. | None beyond the event source. |
| `picked_up` | An available instance acquires a holder. | Required typed `player`, the new holder. |
| `released` | A held instance loses its holder for any supported reason. | Required typed `player`, the former holder, and `reason`. |
| `dropped` | A holder release successfully places the instance at a permitted nearby drop position. | Required typed `player` and `reason`. |
| `delivered` | A delivery successfully consumes the held instance. | Required typed `player` and `trigger`, either `interact` or `enter`. |
| `returned` | A return policy or placement fallback puts the instance at its captured initial location. | `reason` and optional typed `previous_holder`. |
| `despawned` | A present or held instance becomes absent through a disappearance operation. | `reason` and optional typed `previous_holder`. |
| `reset` | An explicit reset restores the initial location and starts a fresh availability generation. | Optional typed `previous_holder`. |
| `respawned` | A scheduled automatic return successfully places the instance. | `reason`, the cause that scheduled this return. |

`released` lets an author react to any loss of possession without subscribing to every outcome separately. It also occurs for delivery, reset, holder death or disconnect policies, and owned cleanup when those operations actually remove a holder. An already unheld instance cannot release a player again. `previous_holder` is present only when that same operation removed a holder; it is not a remembered player from an earlier carry.

Use a bounded `reason` vocabulary, checked against the selected event:

| Event | Allowed reasons |
| --- | --- |
| `released` | `player_drop`, `holder_death`, `holder_disconnect`, `delivery`, `invalid_drop`, `left_arena`, `forced_drop`, `reset`, `despawn`, `cleanup` |
| `dropped` | `player_drop`, `holder_death`, `holder_disconnect`, `forced_drop` |
| `returned` | `holder_death`, `holder_disconnect`, `invalid_drop`, `left_arena` |
| `despawned` | `holder_death`, `holder_disconnect`, `despawn`, `cleanup` |
| `respawned` | `holder_death`, `holder_disconnect` |

`forced_drop` means the explicit `drop_relic` action. A failed nearby placement that successfully falls back home emits `returned` with `invalid_drop`, rather than claiming an ordinary drop occurred. Preserve the original requested operation and its cause in diagnostics. Authorized administrative use of these same operations keeps the same transition semantics and separate administrative audit provenance; it does not pretend a GM was the holder.

Commit the entire operation before dispatching its notifications. Queue `released` before its specific outcome event. Queue the relic's `delivered` event before the committing delivery mechanic's `completed` notification, with both the relic and mechanic already committed. Event payloads remain historical; subsequent guards read the coherent current state under Q68. A delivery listener that resets the relic cannot undo the already committed mechanic completion.

Only `picked_up`, `released`, `dropped`, and `delivered` have a required, unambiguous `player` for default `per_player` invocation limits. An optional `previous_holder` alone does not grant that feature. Explicit [Q223 `on.player`](event-players-and-mechanic-results.md#q223-an-explicit-player-field-for-a-subscription) can select a documented player-valued field for one subscription, skipping events where it is absent. Actions targeting either identity still check whether that player is available for the requested operation. No public mutable relic handle or implicit event-to-action retargeting is introduced; lifecycle actions continue to name a permitted runtime instance explicitly.

Initial creation, automatic respawn, reset, and return are distinct events. Replacing a world representation during pickup or delivery does not invent `despawned`, and subsequent placement does not repeat `spawned`. An explicit new reset emits `reset` even if the relic was already at home, because Q183 deliberately starts a fresh generation. An idempotent retry of the same reset emits nothing again.

Failed operations, rejected pickups, losing delivery requests, and no-op actions emit no successful-change event. Cancelling a pending respawn while the instance is already absent does not emit another `despawned`. A timer becoming due is insufficient for `respawned`; placement must succeed. Ended gameplay subscriptions remain cancelled during cleanup, and restart, reconnection, or publication cannot replay these events. Resource cleanup must not depend on a gameplay listener remaining alive.

## Q185: current carrying and relic-state conditions

Accepted: add `carrying` as a player predicate, plus `relic_state` for the state of a declared instance. Reuse existing condition composition and typed subject rules.

The concise form checks whether the implicit selected player currently holds that exact instance:

```yaml
carrying: north_orb
```

The structured form accepts optional `target` and at most one of `relic` or `definition`:

```yaml
carrying:
  relic: north_orb
  target:
    event: player
```

`relic` identifies a runtime instance. `definition` identifies a reusable relic definition and matches any held instance using that definition within the permitted current attempt. With neither filter, the predicate checks for any held relic in that attempt. Thus `carrying: {}` checks the implicit player, and a target-only structure checks the explicit player. Reject both filters together and unsupported boolean shortcuts. Authors use `not` for exclusions.

An implicit subject must be unambiguous. The typed `target` form accepts a player identity, never an arbitrary property path or an NPC masquerading as a player. A valid player with no matching held instance returns false. Pending creation, a dropped object, and an absent or delivered relic do not count as carrying. Unknown references remain validation errors. Surrounding selectors retain their own online and life-state rules; the predicate itself reads authoritative holder state and grants no action permission.

For instance state, require `relic` and at least one of `state` or `respawning`. Supplied checks are combined with AND:

```yaml
relic_state:
  relic: north_orb
  state: absent
  respawning: true
```

| State | Meaning |
| --- | --- |
| `pending` | The producer is declared in the permitted activation but has not created the instance yet. |
| `available` | The instance has a world representation and no holder. Individual pickup eligibility still applies. |
| `held` | The instance has an authoritative holder. |
| `absent` | The created instance has neither a holder nor a present representation and is not currently delivered. |
| `delivered` | The instance records a committed delivery and has not subsequently been reset. |

`respawning` is a boolean check for a current scheduled automatic return, including an admitted bounded retry awaiting placement. An absent instance may have either value. Cancelling or successfully completing the return clears it. `pending` is distinct from `absent`; an unknown declaration is neither. Reject a literal combination of `respawning: true` with a state other than `absent` as contradictory.

These predicates observe the current activation and never cause creation, retry, pickup, or delivery. An explicit reset clears current delivered state but leaves historical diagnostic records and completed mechanics intact. There is no initial remaining-respawn-time field, because a due timer does not guarantee successful placement at that instant. Neither predicate reads another attempt's private relics or gives a client additional private information. The existing content-definition parameter type named `relic` keeps its accepted meaning.

## Q186: auras owned by carrying a relic

Accepted: add optional `carry_auras` to a reusable relic definition. It is a list of distinct Conclave aura references, applied to the holder when pickup commits. Omission applies no aura.

```yaml
relic:
  id: void_orb
  name: Void orb
  carry_auras:
    - charged
```

This fragment illustrates configuration only. The referenced aura must be defined separately; Conclave ships no encounter or aura inferred from this example. Duration, stack policy, cap, death behavior, modifiers, periodic effects, and display all come from that aura definition. Each entry performs one application, requesting one stack for a stacking aura. Reject duplicate resolved aura references and per-entry duration, stack-count, or scope overrides in this initial form.

Each continuous period of carrying has its own source identity. Conclave records the exact aura contributions admitted by those applications. Any holder release directly removes only the surviving contributions owned by that period of carrying. This includes dropping, delivery, return, reset, despawn, death/disconnect policies, and resource cleanup. Removal is independent of rule subscriptions and does not use broad `remove_aura` selection.

Two carried relics can therefore contribute the same compatible aura independently. Releasing one removes its contribution while the other can keep the aura present. Carry cleanup itself leaves unrelated mechanic or player-owned contributions alone. Ordinary aura death policy still applies to each affected contribution, and an already expired or removed contribution cannot be removed or emit a removal event twice.

Use the accepted aura semantics without forcing continuous presence. Timed contributions can expire while the relic is held. Explicit cleanses can remove them. A full stack cap may admit no contribution. None of these cases automatically reapplies or refreshes the aura, and pickup does not fail merely because the cap admitted no stack. An untimed aura is appropriate when the intended burden lasts through carrying unless explicitly removed. A later fresh pickup starts another carry source and applies its entries again.

Carry ownership ends with possession or the relic's owning lifetime, whichever ends first. It cannot become player-persistent, survive release through a retain-on-death setting, or be extended by refreshing duration. An encounter-owned relic's carry aura can survive a phase change with that relic. The definition's effects remain subject to the supported aura APIs; native status-effect handoffs have their separate Minecraft ownership and are not accepted as entries in this list.

Preflight required aura references, target support, compatible definitions, and supported modifier values before pickup commitment. Do not claim a successful, fully configured pickup if required application validation failed. Existing required/recoverable failure handling remains in force; ignored stacks at a cap are ordinary successful evaluation. Unexpected mutation failures retain Q69's technical-stop and owned-cleanup policy rather than promising rollback of arbitrary mod callbacks.

Commit holder state and its owned aura changes before dispatching gameplay notifications. Reuse the existing aura events and event queue; listeners cannot run partway through the carry mutation. No new relic event from Q184 is required for this ownership mechanism. Attempt revisions stay pinned, so publishing changed `carry_auras` or aura definitions affects only future attempts and never replaces a current carry's captured contributions.

## Relationship to accepted contracts

These contracts extend [managed relic identity and lifecycle](relic-identity-and-lifecycle.md), [pickup, delivery, and explicit controls](relic-interaction-and-delivery.md), [typed event rules](phases-and-rules.md), [event-value conditions](event-conditions.md), and [aura actions and contribution ownership](aura-actions.md). [Q187-Q188](relic-placement-and-appearance.md) accept placement and appearance.
