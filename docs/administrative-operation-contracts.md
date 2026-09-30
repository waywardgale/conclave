# Administrative operation contracts

Status: Q274 is accepted. Q41 already grants GM/operator attempt control and explicit recovery, Q117/Q122 grant persistent-aura administration, and Q273 accepts retrying supported recovery. This contract fixes their targets and effects without adding roster editing or forced outcomes. No command implementation exists.

## Q274: explicit targets and ordinary validation for GM operations

Accepted: expose the accepted operations through the existing Minecraft administration screen and the command forms below. Keep privileged revival separate from encounter admission. Require current server authority, display the exact affected attempt/player/resource, and record administrative mutations without adding a mandatory confirmation exchange to every routine command.

| Command | Target and effect |
| --- | --- |
| `/conclave start <arena> <encounter>` | Start one supported pairing through administrative admission. |
| `/conclave stop <attempt>` | Administratively stop that current attempt and run owned cleanup/recovery. |
| `/conclave restart <attempt>` | Stop and clean up that attempt, then prepare a fresh attempt of the same pairing. |
| `/conclave revive <attempt> <player>` | Force revival of that online roster member's current death in that live attempt. |
| `/conclave relic reset <attempt> <relic-instance>` | Reset one existing owned logical relic through its accepted lifecycle operation. |
| `/conclave recovery retry <record>` | Retry the supported operation represented by an existing unresolved recovery record. |
| `/conclave aura apply <player> <aura>` | Apply a player-owned mark from current published content while the player is outside an active attempt. |
| `/conclave aura remove <player> <aura>` | Remove that player's explicitly selected player-owned contributions; default to all player-owned contributions of that aura. |
| `/conclave aura replace <player> <aura>` | Deliberately replace an independent aura with the current published definition outside active attempts. |

The screen supplies choices and command completion supplies permission-filtered identifiers. Show readable authored names, ownership scope and location beside runtime targets; authors do not construct a runtime UUID or guess a repeated mechanic's activation. A generated selection token must still bind the actual attempt/activation/resource. Text commands resolve their explicit current target at execution; screen requests also carry the selected state/version needed to reject stale interactions.

All listed operations require GM or operator authority, or the trusted local server console acting with operator authority. GM delegation, publication, global settings and reward-review privileges remain separately operator-only. Reject command blocks and remote/unknown command sources for Conclave privileged operations in the initial release; RCON and other remote integrations remain outside this supported workflow. Ordinary player commands such as ready checks and personal reward claims keep their own accepted permissions.

### Start, stop, and restart

Manual start deliberately bypasses authored `start_encounter` event/condition triggers and an authored ready-check guard. It does not fabricate the triggering interaction or player, mark a ready check successful, or bypass engine admission. Resolve the declared participant selector normally. Require at least one participant, valid published content and arena binding, compatible client/resources, usable world state, supported budgets/storage, and uncontested ownership. Report missing event context if a selected capability actually requires it; never supply the GM as an invented event subject. Do not silently remove conflicting players or revive selected dead players.

The screen labels manual start as an administrative start and shows the pairing and selected roster. Explicit command invocation supplies the administrative intent; no extra typed confirmation token is required. A start operation and an automatic trigger still acquire the same reservation, so only one can win.

Stop closes ordinary gameplay with the administrative result and performs the already accepted cleanup/recovery. It produces no gameplay wipe punishment, completion reward, fake NPC defeat or duplicate death. Repeating a stop for the same completed operation cannot repeat its effects. During Q254's frozen completion commit, stop may request independently safe cleanup but cannot replace the pending original result or discard a committed success. Report that distinction explicitly.

Restart is stop followed by new admission, not a rewind or active-attempt hotfix. Resolve the new attempt against the selected current published revision after cleanup, and reselect participants through the normal policy. If the definition/pairing disappeared or new admission fails, leave the old attempt ended and show the reason; do not resurrect its state. Restart test follows Q77's newly validated draft snapshot and resets the optional real-payout switch to off. Publication/selection races use the existing admission/version checks, with the actual selected revision visible in the result.

### Forced revival

Require the explicit live attempt and an online member of its retained roster who has a current dead/passed-out lifecycle. Reject an unrelated player, an offline target or a target whose attempt has already ended. Q254's Finishing encounter state has already closed gameplay and cannot accept this revival. An already living target is an explained no-op, not a heal. Do not queue a privileged revival for some future death or reconnect.

The privileged operation may override ordinary method availability, an expired revival window, eligibility delay, and the encounter self-revival ban. It still requires safe placement and an identifiable recoverable death record. Use that death's captured grave/fallback, health and protection policy; restore the Conclave-changed camera/game mode and preserve ordinary inventory/XP consequences. It cannot repair an unreadable record by creating another death or switching to the latest recovery anchor.

A successful grave revival emits the existing `revived` with `method: administrative` and no assisted `helper`. Keep `participation` unchanged. An active member returns alive and can contribute under ordinary checks; a reconnecting member still needs valid admission within grace; an observer remains an observer. Show the unchanged admission state in the result. This command cannot override code/assets, reset grace, or add someone to fixed mechanic cohorts. There is no new readmission/late-join command in this initial scope.

If the attempt has ended, use its existing pending attempt-end recovery and the Retry recovery operation instead. That recovery never emits `revived` as though an active grave interaction occurred. Q273's safety and accounting limits remain effective even for an operator.

### Relics, persistent auras, and recovery retry

Relic reset uses Q183's same logical instance, captured home, released carrier, canceled pending return, and fresh availability generation. Only a live owning scope with that created instance can be targeted. It does not create an instance from a definition, reopen completed delivery objectives, or modify another activation with the same authored name. Emit the existing successful reset/release notifications and record the administrator separately. Failure to place safely uses the existing recovery/error policy.

Aura apply selects the current published definition and uses an administrative producer identity stable for repeated operations by that administrator on that holder/aura. A stacking aura defaults to one stack; expose its supported stack count through the screen and a corresponding optional command argument. Reject a stack count for a non-stacking aura. Require an online living target outside an active attempt. Repeat applications retain the accepted same-source refresh, cap, compatibility, and player-owned lifetime rules; they cannot create arbitrary new sources to evade them.

Aura removal can address existing player-owned records while the holder is offline or in an attempt. It is an explicit administrative gameplay change, never content migration. The screen can narrow to one contribution; the broad operation removes only the selected player's independently owned contributions of that aura. Leave phase/attempt-owned contributions intact and emit only actual existing lifecycle changes to still-valid listeners. Native modifiers for unavailable holders reconcile through the supported durable lifecycle, with no fake login or expiry.

Aura replacement requires the player to be online, alive and outside an active attempt, and no active attempt-owned contribution of that aura may be involved. Show the existing and replacement definitions before the explicit Replace action. Deliberately remove the selected old independent aura and apply the selected current definition once, preserving removal/application semantics and audit. Do not call it refresh, hot-migrate contributions, or erase an incompatible foreign owner. Preflight supported replacement work; partial native failure still needs the accepted recovery contract, not a claim that removal/application is a Minecraft transaction.

Retry recovery uses the original record identity and supported operation only. It may retry placement, verified owned cleanup, or reconciliation once a dependency is available. It cannot overwrite a later player block edit, guess entity ownership, discard an unreadable record, change an uncertain completion into a new result, or grant payment. If a world conflict prevents safe cleanup, the GM can inspect it and repair the physical world normally, then retry; there is no generic force-restore or forget option.

### Inspection, audit, and exclusions

Inspect shows current state, captured revision, ownership and recovery blockers. Private puzzle/participant values require an explicit authorized Show private state view; they do not enter ordinary player output, completion suggestions for unauthorized users, or default audit messages. Permission revocation immediately ends further privileged requests and disclosure. Inspection itself grants no collision, combat or camera exception.

Record grants/revocations and administrative mutations with UTC time, actor identity/source, stable target, operation, selected content identity where relevant, and result. Record denied privileged requests with bounded rate/size and no secret arguments. A reason is optional for these ordinary operations; Q253's existing reward-review actions still require their explicit reason and exact-allocation review. Do not log hidden answers, private chat, credentials, or arbitrary manifest bodies. ASVS 8.2.1, 8.3.1, 16.2.1, 16.2.2, and 16.3.3 express design requirements, not an implemented assurance claim.

Phase jumps, force-success/failure, arbitrary counters/stats editing, player roster replacement, and new-player late joining are not added. Existing native administrative actions keep their actual native consequences; a real administrative kill can still be a real NPC death under the accepted attribution policy. None of these commands becomes executable YAML.

This contract completes the existing [GM powers](game-master-commands.md), [start rules](encounter-activation.md), [revival/recovery](death-and-revival.md), [relic actions](relic-interaction-and-delivery.md), [persistent auras](auras-and-world-lifetimes.md), [admission distinction](reconnect-admission-and-assets.md), and [startup recovery limits](content-upgrades-and-startup-recovery.md). Exact Brigadier types, screen layout and persistence implementation follow the accepted behavior after the interview.
