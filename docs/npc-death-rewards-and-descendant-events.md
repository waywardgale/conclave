# NPC death rewards and descendant events

Status: Q225-Q226 are accepted. Q225 defines fields for the opt-in NPC death rewards accepted in Q97. Q226 independently adds explicit event observation for verified native descendants, without changing group membership or reward policy. No implementation exists.

## Q225: loot, equipment drops, and experience

Accepted: add three independent fields to an NPC definition: `loot`, `equipment_drops`, and `experience`. Preserve Q97's defaults of no death items, no equipment drops, and zero experience.

```yaml
npc:
  id: guardian
  npc: minecraft:zombie
  loot:
    table: my_raid:entities/guardian
  equipment_drops:
    mainhand: 25%
  experience: 20
```

This illustrative definition selects an existing supported loot table, configures the main-hand slot's native base drop chance, and replaces the native eligible XP amount with 20 points. Native reward eligibility still applies. It does not define the referenced loot table or ship an encounter.

| Field | Accepted forms | Default |
| --- | --- | --- |
| `loot` | `none`, `vanilla`, or a block with exactly one `table` containing a supported registered loot-table ID | `none` |
| `equipment_drops` | `none`, `vanilla`, or a map from supported native equipment slots to explicit percentages | `none` |
| `experience` | `vanilla` or a nonnegative integer number of experience points | `0` |

Each field controls its own channel. Opting into a table does not enable equipment or XP; opting into XP does not restore item drops. Reject unknown fields, ambiguous forms, unsupported slots, missing or incompatible resources, invalid percentages, fractional XP, and negative amounts. Per-slot percentages range from 0% through 100%; unlisted slots in a map have zero chance. An empty map disables equipment drops. Do not add NBT, arbitrary native components, expressions, or command execution.

### Item loot

`loot: none` suppresses the owned victim's supported non-equipment death-item rewards, including native special rewards outside its ordinary entity table. `loot: vanilla` enables the current native body's supported normal death loot, including applicable native special cases. A configured `table` replaces the ordinary victim reward table and suppresses additional native special death-item rewards; it does not append the replacement to another roll of the native loot.

Keep equipment separate even when its native implementation shares a death-loot method with other drops. Examples such as creeper music discs and charged-creeper heads can pass through different native paths. The adapter must cover those paths deliberately, rather than assuming a normal-table replacement controls every reward.

Apply this policy to the owned victim. An owned attacker killing an unrelated world mob does not transfer ownership of that victim or suppress its native rewards. Conversely, an unrelated attacker does not bypass an owned victim's configured death-item policy. Preserve unrelated combat callbacks, death effects, world drops, and non-death activities such as trading or shearing under their own native contracts.

Use the actual supported native death context for table evaluation, preserving final damage source, attacker/direct source, recent-player attribution, luck, and the table's own conditions where applicable. Do not insert a participant merely to make a player-kill predicate pass. Native loot attribution can differ from Q221's literal fatal-killer requirement, and the objective's qualification does not rewrite the native loot context.

Retain native eligibility and the `minecraft:mob_drops` gamerule where the native reward path applies them. Selecting a table does not bypass a mob's native loot gate or guarantee that the table produces items. Validate that the table can run with the supported entity-death context and that its referenced dependencies are supported. Global table replacement must not affect unrelated mobs.

Capture the selected policy and external loot dependencies through Q148's maintenance boundary. A resource ID alone is not a snapshot of functions, predicates, tags, nested tables, or mod callbacks. Resolve known conversion paths and native special reward dependencies before claiming compatibility. A reload or unsupported external change must follow the existing dependency/error contract; publication cannot mutate an active attempt's reward policy.

### Equipment drops

`equipment_drops: vanilla` uses the current body's native per-slot drop policy, including native preserved equipment where supported. A slot map instead supplies authored base chances for the current items occupying those slots, including items acquired after spawn. Reapply that policy at the supported death-drop boundary so native pickup cannot silently turn a configured zero or partial chance into a preserved drop.

Mapped percentages are native base chances. Ordinary recent-player eligibility, enchantment drop prevention and chance modification, native randomization, and the applicable mob-drop gate remain effective. Explain this in the editor: 25% is the base before native modifiers, and 100% is not the native preserved-item mode. No separate guaranteed-drop bypass is introduced.

Drop only the actual current stack through the supported native path and consume it once. Do not recreate the initial loadout, duplicate an item already transferred on conversion, or restore equipment lost during gameplay. Native wear processing can change a damageable item's dropped durability under its normal rules. An absent slot or prevented drop yields no item, not an error.

`none` suppresses supported equipment death rewards even for native picked-up/preserved items. It does not erase those items during life, change pickup behavior, or create an inventory rollback at cleanup. Validate every native slot against the selected adapter, retaining Q144's `mainhand`, `offhand`, `feet`, `legs`, `chest`, `head`, `body`, and `saddle` names where supported.

### Experience

`experience: vanilla` uses the complete supported native reward calculation, including applicable type, equipment, and enchantment adjustments. An explicit positive integer replaces the final eligible total, so `experience: 20` does not also receive the native base amount or extra equipment/enchantment bonuses. `0` suppresses XP.

Preserve native XP eligibility, consumption accounting, and applicable gamerule behavior. A positive amount changes the reward amount; it does not invent recent-player credit, enable an ineligible native death, or turn cleanup into a kill. The compatibility view must explain native eligibility restrictions that can prevent the configured amount from dropping.

Create ordinary world XP orbs at the native death-reward location, not an automatic grant to every raider or a private payment to the fatal attacker. Native orb collection behavior remains in force. Enforce supported numeric and resource budgets without silently truncating an authored amount. Native special XP paths need verified adapters before they can claim the same configuration support.

### Lifetime and once-only accounting

Rewards belong to an actual supported native death, including a genuine native administrative kill under Q149. Native conversion, self-destruction without a death path, scope cleanup, failed spawning, ordinary removal, and restart reconciliation generate none. Prevent the original reward pass and a replacement pass from both paying out. An observation hook that runs after drops exist is not sufficient to enforce the no-reward default.

Conversion preserves the authored reward policy. `vanilla` follows the resulting body's native behavior within the captured compatible environment; an explicit table or amount keeps its authored value. A selected descendant definition uses its own explicit policy. Unconfigured descendants keep the no-reward default, even when their parent opts in.

Legitimate drops become ordinary world items and XP, and survive later attempt cleanup under their normal world rules. A failing objective does not take back the victim's lawful drops. Do not rescan nearby items to infer provenance, reroll loot after an uncertain failure, or replay rewards on reconnect, publication, or restart. Q248 separately accepts completion-reward declarations. [Q250-Q251](reward-recipients-and-delivery.md) accept recipient qualification and private delivery with pending rewards. Generation and durable reconciliation retain their separate design branch; Q249 changes actual payout only for explicitly marked Test attempts.

## Q226: explicit subscriptions to native descendants

Accepted: add `on.source.descendants` with a required named `group` and optional exact `type` and `npc` filters. It observes verified native living descendants whose lineage originates at an actual member of that group, including later generations. It excludes the group's own members.

```yaml
on:
  source:
    descendants:
      group: summoners
      type: minecraft:vex
  event: defeated
```

This fragment can react to an evoker group's vex deaths. It does not add the vexes to `summoners`, change that group's defeat objective, or create a new spawn group. A complete rule still needs an ID and actions.

Use this explicit lineage source for the initial broader owned-NPC event capability. Do not introduce an attempt-wide ownership wildcard, a world-mob query, or implicit access to private groups. Authors select the root group they are permitted to observe, and reusable mechanics can forward chosen notifications through `export.events`.

### Supported observations and filters

Support the existing `damaged`, `healed`, `converted`, `health_floor_reached`, and `defeated` event meanings for one affected descendant. [Q228](individual-npc-controls-and-hit-reactions.md#q228-actual-blocks-and-conclave-damage-prevention) additionally accepts `damage_blocked` and `damage_prevented` on supported descendant targets. Keep successful native creation on Q213's existing group `descendant_spawned` event; do not add a duplicate descendant-source `spawned` alias or pretend native births are explicit spawn batches.

`type` names one registered native NPC type. `npc` names the child's own captured Conclave definition when Q211 selected one. Neither filter falls back to an ancestor's definition. Omission imposes no restriction on that axis; supplied filters combine with AND. Validate references and reject unknown fields, unsupported events, and arbitrary property queries. A valid filter can match no current descendants without being an error.

Match lineage, definition, and native type against coherent state immediately before the observed operation changes it. A `type: minecraft:zombie` subscription can observe that descendant's conversion away from zombie; the `converted.type` payload describes the destination. To match the new form, use that payload in a guard. Later events about the new body use its new native type. Damage and death do not remove their own subject from a source match.

Filters select notifications; they do not change the entity's definition, ownership, AI, rewards, or native behavior. They do not capture a permanent list of existing children. Later native generations can match while the original lineage, group activation, and listener remain valid.

### Payload and causality

Retain each event's established fields, units, and meanings. In particular, `damaged.type` remains its damage-type ID, while `converted.type` and `defeated.type` remain native NPC types. A source filter's `type` is always a native NPC-type filter; it does not rename a payload field.

For descendant `defeated`, the child's `npc` definition field is optional because an unconfigured native child has none. Group-member `defeated.npc` remains required. Preserve the same `target`, terminal `type`, `cause`, and optional fatal-source fields. The compiler must expose these source-specific schemas honestly. Selecting a definition with a source filter does not silently strengthen optional payload fields.

Other event payloads retain their existing schemas. Do not attach an implicit root-group handle, list of relatives, or ancestor definition to every event. Q213's creation notification already supplies direct parent and generation information. A typed target identifies the affected body at that operation, not a live alias that follows later replacement.

Preserve existing committed operation order, including damage before terminal defeat and conversion before a resulting floor notification. The adapter's one event record can match several explicit subscriptions; it must not duplicate one operation within a subscription. Rules and exports keep their existing order and invocation limits. Q223's explicit `on.player` is available only for event fields that can actually contain a player, such as `attacker` on a descendant's defeat.

### Lineage, privacy, and ending scopes

Keep the root group's actual activation identity. A root's supported conversion continues its lineage; its actual death does not erase living descendants. A descendant's single conversion preserves its lineage position and selected definition under the existing transfer contract. Split children are new descendants, not replacement group members.

Subscriptions may await a declared root producer. Native children created during its pending spawn batch do not publish successful descendant events before that root batch is admitted. Before successful root admission, descendants are not active encounter event subjects; rejected initialization or failed creation emits no gameplay history later. Do not replay prior damage, conversions, or deaths when a listener is installed or when a group name is reused.

Access to the root group follows ordinary lexical visibility and typed group-parameter bindings. Cleanup ownership alone does not make a private root group visible to its caller. An explicit export can forward supported target identities without exposing that group's handle. Ending a private creator does not keep its subscriptions alive just because one of its resources has an outer cleanup owner.

If the permitted group record and listening scope still exist, a root can already be dead while its remaining owned descendants continue to notify. If either necessary scope ends, this source does not recover the discarded private declaration or silently adopt the descendants into another visible group. Designers needing continued observation keep the relevant producer and listener in a suitable enclosing lifetime.

Expected native expiry still has its own actual outcome. A vex that dies through supported starvation damage can emit descendant `defeated`; ordinary removal or cleanup cannot. The death has no parent-group defeat credit. Unsupported loss or required integration failure follows the existing ownership/error rules without fabricating a combat outcome.

No event selects unrelated world mobs by proximity, definition similarity, native owner guesses, or damage attribution. It grants no extra authority over its target and sends no private information to clients by itself. General world-NPC selectors and arbitrary target-bound subscriptions are outside this initial source.

## Related contracts

These contracts refine [NPC death reward policy](npc-and-boundary-policies.md#q97-npc-loot-equipment-and-experience), [native equipment](npc-stats-and-combat.md#q144-typed-equipment), [external data reloads](combat-rules-and-health.md#q148-external-gameplay-data-reloads), [native lineage](npc-lineage.md), [child creation events](npc-spawn-events.md), [defeat events](npc-defeat-events-and-requirements.md), and [player selection in subscriptions](event-players-and-mechanic-results.md). Native reward integration facts are recorded separately in [NPC research](npc-combat-research.md#native-death-reward-paths).

[Q248-Q249](completion-rewards-and-test-policy.md) accept encounter-level completion rewards and a separate Test payout policy. Test suppresses supported reward delivery by default and offers an operator-only real-payout option at launch. Terminal presentation remains presentation-only; no general grant action is added.
