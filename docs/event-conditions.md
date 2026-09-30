# Conditions on event values

Status: Q164 is accepted. Typed event payloads, `on` / `if` / `do` rules, readable comparisons, resolved combat events, and current aura-state queries are accepted. This contract adds one condition form for inspecting documented event fields; it is not an implemented schema.

## Q164: typed event-value comparisons

Accepted: add `event_value` as a registered predicate within an event rule's `if`. It names one documented event `field` and exactly one supported comparison or a `present` check. Reuse the existing numeric comparison names and each field's documented units. Permit exact `equals` for supported scalar identities, enums, strings, and booleans. Use `not` for inequality and ordinary condition trees for combinations.

```yaml
if:
  and:
    - event_value:
        field: amount
        at_least: 10
    - event_value:
        field: type
        equals: conclave:physical
```

This fragment is valid for a `damaged` rule. It checks the recorded health-plus-absorption consumption from one resolved hit and the recorded damage-type ID. It does not compare the target's current health or the incoming request before defenses. A `healed` rule also supplies an `amount`, with its accepted actual-healing meaning, but does not thereby acquire a damage `type` field.

Validate the selected field against that rule's source/event contract before publication. Reject unknown fields, incompatible literal types or units, unsupported comparisons, and use outside an event context. Field names are single documented keys, not dotted paths into an arbitrary object. Do not add expression strings, interpolation, regex matching, implicit numeric coercion, or arbitrary getters.

An optional field that is absent makes an ordinary comparison false. Allow `present: true` and `present: false` for any documented field, including a typed identity, so a rule can check whether an attacker is available. Unknown fields are validation errors, never an absent-value result. Require either a presence check or one comparison within each `event_value` node; authors can combine nodes with `and`.

```yaml
event_value:
  field: attacker
  present: true
```

For gameplay checks about an entity identified by an event, reuse that predicate's typed `target`, such as a structured `has_aura` with `target: {event: attacker}`. Entity identities are not arbitrary strings and cannot be queried by traversing `attacker.inventory` or similar paths. Each predicate retains its own supported subject and missing-target behavior. Checking presence does not make an ended NPC available for mutation or override an action's target validation.

Event comparisons read the immutable admitted payload. Current-state predicates in the same guard read the coherent live state at evaluation under Q68. This distinction lets a rule require both a large recorded hit and an aura that is still present now. No comparison retroactively changes the observed hit, revives a target, or changes the event's original activation identity.

Keep the existing event queue, invocation limits, and execution ceilings. A false guard consumes neither `once` nor cooldown. Matching an event caused by an authored action remains allowed; this predicate does not silently suppress feedback chains. The capability catalog must expose available fields and types in the in-game editor, including whether each field is optional and whether its value is a past event measurement or a reference to a subject whose state may have changed.
