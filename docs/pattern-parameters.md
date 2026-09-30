# Reusable pattern parameters

Status: Q201-Q202 are accepted. They independently extend the accepted typed-parameter catalog with configuration shapes already supported by the matcher. Q201 covers logical tokens, answer constructors, and appearance; Q202 covers direct input bindings. Neither requires a new runtime mechanic or generic YAML override system. No implementation exists.

## Q201: reusable token sets, answer constructors, and appearance

Accepted: register three parameter types with their existing matcher meanings. Use the ordinary `parameters`, `with`, and whole-value `{parameter: ...}` forms.

| Parameter type | Value | Supported receiving field |
| --- | --- | --- |
| `pattern_tokens` | A nonempty unique list of local token IDs. | A matcher's `tokens` or the unique pool in `pattern.sample.from`. |
| `pattern` | One accepted literal sequence, `sample` constructor, or `choose` constructor. | A matcher's `pattern`. |
| `token_display` | A mapping of token IDs to the accepted optional name, icon, style, and translation settings. | A matcher's `token_display`. |

```yaml
parameters:
  symbols:
    type: pattern_tokens
  answer:
    type: pattern
  artwork:
    type: token_display
    default: {}

type: match_pattern
tokens: {parameter: symbols}
pattern: {parameter: answer}
token_display: {parameter: artwork}
```

This is a reusable-body fragment. Its valid direct bindings or authored submission routes must still be declared. It does not supply an executable encounter by itself. A reusable definition can expose only the settings authors need to vary; fixed literal tokens or fixed appearance remain valid without corresponding parameters.

These are specific registered configuration types, not generic lists or maps. They do not create global token/answer definition kinds, public runtime answer handles, custom YAML schemas, arbitrary nested structures, or executable configuration. A `pattern` value describes how to construct an answer; it is not the answer already drawn for a running challenge.

### Constraints and defaults

Support optional positive integer `min_items` and `max_items` on `pattern_tokens`, constraining vocabulary size. The intrinsic nonempty, unique, valid-ID rules always apply. Constraints cannot permit duplicates or numeric/boolean token coercion.

Support optional positive integer `min_length` and `max_length` on `pattern`, constraining every possible resolved answer length. A fixed list must fit, `sample.length` must fit, and each `choose` alternative must fit. These bounds do not change the sampling algorithm, add weights, or silently trim a supplied answer. Existing limits still bound vocabulary, pools, alternatives, and expansion even when an author omits narrower constraints.

Allow nonnegative `min_items` and `max_items` on `token_display`, counting mapped entries. An empty mapping is valid and gives the existing ID-label defaults. Reject reversed bounds, unsupported constraint names for a type, and a default outside its type's declared limits.

Defaults remain concrete configuration in the defining file. They cannot refer to another parameter or event. Asset and style references inside a default resolve in the definition's namespace; caller-supplied references resolve in the caller's namespace before binding. Token IDs and token-display keys are local logical values, not automatically namespaced content references.

Validate standalone shape and declared constraints when the parameter is declared. Validate relationships to other fields after the complete matcher is bound. A pattern parameter cannot independently prove membership in a vocabulary supplied by another parameter. This is a validation dependency, not permission to accept an invalid instantiated definition.

### Whole values, scalar entries, and nested reuse

Replace the complete receiving value rather than merging a passed mapping with a default. Caller-supplied artwork replaces the artwork parameter's default map as a whole. There is no implicit concatenation of token lists, list splicing, key inheritance, or partial override of a sampled constructor.

Within an authored body, permit an existing compatible scalar or typed-reference parameter at a whole leaf value, including a scalar list entry. For example, an integer parameter may supply `sample.length`, an enum/string parameter may supply one declared token value, and an item or texture reference may supply its compatible icon field. Validate the fully substituted value under its receiving field's contract. A broad string type does not bypass token-ID or vocabulary checks.

This extends the whole-value substitution rule to those concrete list entries; it does not add index access, computed keys, interpolation, or extraction from a structured parameter. A parameterized mapping key is invalid. To vary all display keys together, pass a complete `token_display` value. To vary a whole answer or vocabulary, use the registered whole-value types instead of generating YAML through a string.

Permit whole-value forwarding through a nested reusable occurrence:

```yaml
with:
  answer: {parameter: outer_answer}
```

The outer body forwards its declared `pattern` parameter to a compatible child parameter. Preserve already resolved references and their original provenance through each level. Do not resolve a caller's style or asset again in an intermediate definition's namespace. Each forwarding step checks its parameter constraints, and the final receiving capability checks its own invariants. Bound expansion remains finite and recursive definitions remain invalid.

Structured supplied values cannot contain unresolved references to parameters private to another definition. A caller can construct a value using its own declared compatible parameters, or forward a whole already bound value. Defaults still cannot refer to parameters. Event-dependent values and runtime mutation do not become configuration just because they appear inside a structured parameter.

### Validation after binding

Validate the combined vocabulary, chosen constructor, ordering mode, display map, input routes, and any bound clue positions before the affected attempt starts. Preserve all existing requirements: token membership, positive answer lengths, unique sample pools, enough values for sampling without replacement, duplicate-alternative checks under the chosen ordering mode, and valid display keys and assets.

Check every referenced reveal position against the shortest possible bound answer. The framework must not discover after a random draw that a configured clue position does not exist. Check input coverage against every token that can occur, including all alternatives, rather than only the first or a preview draw. Unused decoy tokens and their appearance entries remain allowed.

Stored reusable definitions may have required arguments that are supplied only by an actual use, as under Q168. Every concrete use and declared arena pairing must pass the complete bound validation. Diagnostics identify the caller argument, consuming field, and both source locations when they differ. The in-game editor uses the registered structures for appropriate controls rather than accepting an untyped blob.

### Configuration and runtime independence

Binding a constructor does not run it. Each matcher activation resolves its own expected answer at initialization under Q191 and Q193. Two children receiving the same sampled configuration do not share a draw, progress record, or random seed. Per-player independent answers remain governed only by `pattern_per_player`.

Publishing a new caller argument, default, texture, or reusable body affects future attempts under the captured-revision policy. No argument mutates a current answer or live parameter binding.

These parameter types do not become supported event-output values automatically. `export.events` still accepts only its documented payload types. Do not forward a constructor, display map, or a running private answer as an unregistered list/map payload. Sharing one resolved random answer between independent matcher activations would require an explicit future runtime-data contract; passing the same configuration does not provide that behavior.

## Q202: reusable direct interaction bindings

Accepted: register `pattern_inputs` for the accepted nonempty list of direct matcher input entries. It is compatible with the matcher's `inputs` field and a compatible forwarded parameter, without adding a generic action/rule-list parameter.

```yaml
parameters:
  controls:
    type: pattern_inputs

type: match_pattern
tokens: [sun, moon]
pattern: [sun, moon]
inputs: {parameter: controls}
```

An occurrence can configure the physical controls through ordinary arguments:

```yaml
id: symbol_lock
use: raid_tools:symbol_pair
with:
  controls:
    - token: sun
      targets:
        - block: sun_button
    - token: moon
      targets:
        - block: moon_button
```

These are illustrative definition and occurrence fragments. The referenced definition and locations must exist under the normal manifest and arena contracts. The parameter does not install buttons or invent their locations.

### Supported shape and bounds

Each entry retains Q192's declared `token`, nonempty typed `targets`, optional player filter, and supported hold/reach/interruption/native-consumption settings. Accept block-location and spawn-group targets with their existing meanings. Reject unknown fields, raw coordinates in a reference field, action lists, rules, scripts, command strings, and arbitrary native data.

Support optional positive `min_items` and `max_items`, counting input entries. A literal default must be a nonempty valid list within those bounds. Nested target counts, query complexity, and total expanded work remain bounded by their existing schemas and engine limits. A broader parameter constraint never loosens a consuming field's intrinsic limit.

Use whole-list replacement and whole-value forwarding. An authored body may use an existing compatible scalar or reference parameter as a complete leaf, such as a block's location, an entry's token, or its hold duration. It cannot splice another list into `targets`, index into a supplied input map, or compute a target key. This independently applies the typed whole-value rule to the input configuration.

An action-only matcher continues to omit `inputs` under Q194. It does not pass `[]`, `null`, or a false flag through this type to mean omission. A reusable definition exposing a required direct-input list remains a direct-input definition; an authored action-only definition can omit that field without adding another runtime capability.

### Caller references and activation identity

Resolve references authored in a supplied list in the caller's namespace and permitted scope before binding. This includes block locations, spawn groups, roles, auras, and area references nested in supported player filters. Preserve each typed reference through nested whole-value forwarding. Defaults and definition-internal literal references belong to their defining context.

Arena locations retain their typed logical binding until the concrete arena pairing resolves them. A runtime group retains its permitted producer and owning activation. Supplying a future declared group is valid only because the receiving input capability permits pending targets; it does not spawn that group or turn another action into a pending-target consumer.

A player query remains a configuration evaluated at the input capability's existing eligibility checks. Passing it does not capture recipients early, replace the matcher's separate per-player solver set, or allow offline/dead physical input. Its filter remains an additional restriction combined with the mechanic-level query.

Forwarded references cannot access another attempt, promote resource ownership, or expose private child state through a wrapper. The list contains physical target bindings, not a mutable handle for submitting into an arbitrary matcher. Rule-driven submission to a public matcher continues to use `submit_token` and its separate reference boundary.

### Coverage, ambiguity, and independent use

After arguments and arena bindings are known, validate all tokens against the consuming vocabulary and check input coverage across direct bindings and declared `submit_token` routes. Preserve Q194's distinction between a possible configured route and a proof that authored conditions will eventually enable it.

Detect duplicate or provably overlapping target bindings after substitution, including two location arguments that resolve to the same block cell. Preserve Q192's runtime ambiguity policy for overlaps that become apparent only at runtime. Caller configuration cannot rely on list order to choose between competing token meanings.

Each consuming matcher gets separate holds, progress, admission state, and per-player cooldowns even when it receives the same input-list value as another occurrence. The configuration value is immutable and carries no input session. If two independently authored matchers observe the same physical gesture, the accepted per-matcher admission and shared native-consumption rules still apply; parameter reuse does not create a cross-matcher single-owner lock.

Apply the same diagnostics, editor validation, bounded expansion, and captured-revision rules as Q168. All configuration references must be valid before use; publication never modifies an active list or retargets an old input session.

## Related contracts

These contracts extend [parameter declarations](mechanic-start-and-parameters.md), [reuse and reference resolution](manifest-references.md), [tokens and direct input](pattern-definitions-and-inputs.md), [authored input and solver ownership](pattern-progress-and-submission.md), [token display and clue positions](pattern-presentation-and-clues.md), and [private reusable interfaces](reusable-mechanic-contracts.md). [Q203](world-pattern-clues.md) accepts a world-space clue consumer. [Q204-Q205](token-labels-and-world-progress.md) accept fixed-token labels and world progress counters.
