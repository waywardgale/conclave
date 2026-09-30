# Mechanics and participants

Status: Q31-Q33 and disconnect policy are accepted. The original Q34 death flow and Q36-Q37 camera proposals were replaced by [death and revival](death-and-revival.md). Accepted capture, delivery, and relic respawn behavior is recorded in [runtime semantics](runtime-semantics.md).

## Q31: interact

One deliberate, valid use of a designated target by an eligible participant completes the mechanic. Authors can require multiple uses, distinct participants, or a held interaction explicitly.

The server validates participant eligibility and interaction range. Multiple notifications for the same physical input must not count as separate uses. Held interactions end when the participant releases the control or stops qualifying. Q175-Q177 define accepted hold progress and input settings.

These are generic framework capabilities, independent of any particular button, altar, NPC, or encounter.

[Q174](capture-and-interaction-targets.md#q174-typed-interaction-targets) accepts concrete block-location and NPC-group targets. [Q175-Q177](interaction-input-and-progress.md) accept progress and input settings, including repeated uses, distinct participants, individual holds, and explicit native-interaction consumption.

## Q32: defeat

Actual defeat of every designated target completes the mechanic by default. Authors can explicitly require a smaller count. Target selection is explicit; unrelated targets spawned later do not silently expand the requirement.

[Q216](npc-lifetimes-and-variants.md#q216-committed-native-self-destruction-counts-as-defeat) accepts actual native death and verified committed native combat self-destruction as distinct defeat outcomes. Neither requires an invented killer, and self-destruction does not generate ordinary death rewards.

[Q219-Q220](npc-defeat-events-and-requirements.md) accept per-member notifications, optional cause filters, `completion: all|any|{count: N}`, and ordinary mechanic failure when a requirement becomes permanently impossible. Completion reads retained records and does not require listening to a live event.

Despawn, cleanup, or a target becoming unavailable is not defeat. An unknown target definition is a validation error, while a target known to the definition but not yet present cannot count as defeated. Defeat credit does not require a specific killer unless the author adds that condition.

[Q64](npcs-and-spawning.md#q64-named-spawn-groups-and-defeat-completion) defines accepted binding to named spawn-group activations and tracking of ongoing waves. [Q81](manifest-references.md#q81-references-to-active-gameplay-state) defines phase-local and explicit encounter-scoped references. [Q93](npc-and-boundary-policies.md#q93-spawning-and-reinforcing-groups) accepts pending binding to a declared future producer, retained defeat records, and creation of each public group ID once per owning scope activation.

## Q33: match_pattern

Compare input against an ordered sequence of author-defined tokens. Explicit unordered matching compares token counts, so repeated tokens retain their meaning.

The expected pattern can be authored directly or sampled when the mechanic starts from a declared pool. It remains stable during that activation. A wrong input resets partial progress and emits a mismatch event. Failure, damage, or other punishment requires an authored rule, allowing the same matcher to serve forgiving and punitive puzzles.

Accepted [Q62](selection-and-patterns.md#q62-shared-pattern-progress-and-private-clues) uses shared pattern and input progress by default, with explicit per-player alternatives and audience-specific clue disclosure. Authors separately choose input eligibility and who sees the full pattern or selected clues. Secret answers are not transmitted to all clients by default.

[Q191-Q192](pattern-definitions-and-inputs.md) accept concrete token/answer fields and repeatable interaction inputs. These contracts preserve the accepted matching behavior without treating the finite-use `interact` mechanic as an unlimited input button. [Q193-Q194](pattern-progress-and-submission.md) accept concrete progress ownership, completion, and authored token submission.

## Q34: death and revival

The current design creates a grave and gives the dead player a grave-bound third-person view with only their own information. Assisted revival is instant by default, self-revival is disabled by default, and combat uses a global 15-second revive window. Once that opportunity ends, the player waits for attempt-end respawn and uses globally configured post-window viewing. All-revival, method availability, timing, and permission for encounter self-revive bans are globally configurable.

The user explicitly permits unrestricted spectator mode as a global option after passing out. The earlier blanket no-noclip spectator restriction is superseded. During the grave revival opportunity the player does not enter spectator mode or another player's camera.

Dead, passed-out, and disconnected participants do not contribute to occupancy or interactions. Reconnection preserves current state without granting a revival. Accepted Q40 keeps an otherwise defeated attempt open while an allowed self-revival or a living participant's reconnect opportunity remains; other encounter outcomes still apply. See [death and revival](death-and-revival.md) for the full contract and open questions.

Vanilla gamerules govern ordinary inventory and XP on death. Conclave adds no competing retention or rollback policy; encounter relics retain their separate lifecycle. Revival defaults to 100% maximum health and zero damage protection, with author configuration. [Location anchors](location-anchors.md) provide accepted recovery and entity-spawn authoring. [Q66](death-and-revival.md#q66-recovery-after-an-attempt-ends) defines accepted player recovery on exit.

## Q35: disconnect and reconnect

A disconnected participant retains their reserved place and attempt identity for 60 seconds of real elapsed time by default, configurable by the encounter. They do not count toward active occupancy or interactions. Q237 qualifying admission within grace restores access to the existing attempt and its current state; it does not restore an old copy of a carried relic or revive a dead participant.

[Q237-Q238](reconnect-admission-and-assets.md) require successful native world entry and verified original required resources before admission cancels grace. Incomplete returns preserve the same deadline. Participation remains independent of connection, life, and camera state.

An active attempt continues its gameplay clock while a participant is disconnected. Their timed aura contributions continue to age with that clock. Disconnect grace uses real elapsed time, separately from the accepted simulation clock for gameplay.

Relic behavior on disconnect is independently configurable, with dropping as its default. It does not automatically inherit the holder-death setting.

[Q180](relic-identity-and-lifecycle.md#q180-death-disconnect-and-respawn-fields) accepts the exact disconnect and respawn fields, including independent immediate policies and one pending return per disappearance.

If no actively admitted online survivors remain but a still-living participant is within reconnect grace, the attempt can remain open for that grace period. Normal gameplay failure conditions, including deadlines, remain active and can end it sooner. The default party-defeat rule ends the attempt only when no living actively admitted participants, eligible allowed self-revivals, or other supported recovery opportunities remain, including eligible reconnecting survivors. Living observers do not keep the attempt open.

[Q67](death-and-revival.md#q67-returning-after-reconnect-grace-expires) defines accepted behavior after grace expires. [Q120](cleanup-and-restart.md#q120-interrupted-attempts-after-a-server-restart) and [Q273](content-upgrades-and-startup-recovery.md#q273-keep-content-repair-available-and-block-only-unsafe-operations) settle interrupted-attempt and startup recovery. New-player late joining remains an optional expansion outside the initial target; [Q274](administrative-operation-contracts.md) accepts explicit GM operations without adding readmission or roster editing.

## Q36: restricted spectator camera

Superseded before acceptance. The current contract uses a grave-bound third-person view while revival is possible, followed by globally selectable player viewing or unrestricted spectator mode after passing out. Accepted Q42 makes teammate viewing the default and retains the grave camera when reconnect grace keeps an attempt open without an available teammate to watch.

## Q37: spectator information

Replaced by the user's global policy: grave viewing retains only the dead player's own information. After passing out, first-person viewing does not grant the watched player's private information by default, but the author can explicitly enable it globally. Recipient filtering still belongs on the server and must reflect that configured policy.

## Spectating feasibility notes

These facts informed the earlier camera proposal. The current design intentionally offers ordinary unrestricted spectator mode only in the globally configured post-window mode, while the grave view is a separate constrained camera.

Mojang's [Java release notes](https://www.minecraft.net/en-us/article/nether-update-java) describe spectator flight through blocks, and Fabric-maintained [entity visibility mappings](https://maven.fabricmc.net/docs/yarn-1.21.4%2Bbuild.8/net/minecraft/entity/Entity.html#isInvisibleTo(net.minecraft.entity.player.PlayerEntity)) document spectator exceptions to ordinary invisibility. Inference: assigning spectator game mode alone does not meet Conclave's restriction.

The [spectate command](https://feedback.minecraft.net/hc/en-us/articles/360034618512-Minecraft-Java-Edition-Snapshot-19W41A) can select a camera entity. First-person perspective, rotation following, and prevention of detachment still need separate implementation and verification. The cited versions establish capabilities, not a selected Conclave Minecraft target.

Camera presentation, [networked world and entity data](https://docs.fabricmc.net/develop/networking), and private Conclave HUD payloads are distinct concerns. A camera lock does not prove that a modified client cannot inspect world data already delivered to it. The design must restrict legitimate spectator controls and server-distributed Conclave secrets without claiming a general anti-cheat guarantee.
