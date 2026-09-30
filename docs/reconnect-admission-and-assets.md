# Reconnect admission and captured resources

Status: Q237-Q238 are accepted. Q35 and Q67 already define a retained reconnect opportunity and observation after expiry; Q172 says a qualifying return cancels that opportunity. Q235 leaves restoration of the current attempt's resources separate from ordinary asset updates. These contracts define the admission boundary and author-visible eligibility. Q239-Q240 accept the dependent restoration workflow. No implementation exists.

## Q237: what completes a return within grace

Accepted: a return qualifies when the server commits admission to the existing attempt after both successful native world placement and confirmation that the client has applied that attempt's required captured resources. Authentication, opening a connection, downloading files, and an early play-network callback are insufficient individually.

Keep the original real-time deadline throughout preparation. The default remains 60 seconds from the qualifying disconnection, with the accepted encounter override. Retries, consent dialogs, downloads, and opening another connection cannot extend it. Serialize admission and expiry on the server: admission committed at or before the stored deadline succeeds; after that deadline it cannot succeed. Once expiry has committed, no late packet or client timestamp can reverse it. Use server elapsed time, and cancel the opportunity immediately after successful admission as Q172 requires.

If a permitted restoration workflow allows world entry before resource preparation completes, that player is online without having regained encounter admission. Completing permitted restoration before the same deadline can still qualify. Declining or cancelling one restoration attempt does not forfeit the remaining opportunity. Disconnecting again before admission preserves the same deadline, rather than opening a fresh window. A later genuine disconnection after successful readmission receives the normal new opportunity.

Emit `reconnected` once for an actual completed return to play while the tracked attempt exists, even when encounter admission is unavailable. Do not emit it for authentication alone or repeat it when an already-online player later becomes resource-ready. A failed placement is not a successful return. Preserve the existing distinction between network presence, life state, and encounter eligibility.

### State that continues while preparation runs

The attempt continues. Its phase, deadlines, aura ages, grave window, and other simulation timers do not pause or reset. Retain authoritative life state, role assignments, mechanic history, and the fixed roster. Do not revive the player, restore a former relic, remove them from a fixed matcher cohort, or reassign a role just because they returned.

Grace expiry follows Q67 once, with its ordinary observation and eventual recovery policy. A return does not reopen that opportunity. If the attempt ends during preparation, release its admission claim and follow Q66 recovery; do not attach the old return to a replacement attempt. Native connection/admission failures remain possible independently of Conclave's resource policy.

## Q238: participation independent of online and life state

Accepted: expose a read-only `participation` field for the current attempt, distinct from `online` and `state`.

| Value | Meaning |
| --- | --- |
| `active` | Admitted to this attempt. The player can still be dead; life state and each capability determine what they can do. |
| `reconnecting` | Retained on the roster with a still-valid return opportunity, but not currently admitted. They may be offline, preparing their connection, or online awaiting required resources. |
| `observer` | Retained on the roster after ordinary return eligibility has expired. Their history and recovery rights remain. |
| `any` | A selector value imposing no restriction on this axis, not a stored participation state. |

These values describe admission, not camera or native game mode. A dead admitted participant watching a teammate under the accepted passed-out policy can still be `active`. An outsider has no participation state in this attempt. Do not derive their value from another attempt, silently enroll them, or expose a generic action that sets this field.

Allow `participation` in `from: participants` selectors and current-attempt `player_state` conditions, using the existing implicit or explicit typed player subject. Require an attempt context. An explicit participation filter on a world-player collection restricts it to members of this attempt with that state; `any` removes the restriction and does not invent an offline world-player directory. Outside-attempt/pre-start use is invalid.

### Defaults and independent capability checks

For ordinary gameplay selectors whose collection is `participants`, default to `participation: active`, alongside the accepted `online: true` and `state: alive`. Presentation audiences and participant lifecycle sources default to `participation: any`, retaining their existing online/life defaults and information restrictions. Explicit `online_players` and `online_raiders` collections keep their accepted broad behavior unless the author supplies a participation condition. This does not narrow world queries or native combat to the roster.

An explicit full-roster query is:

```yaml
players:
  from: participants
  participation: any
  online: any
  state: any
```

Concise filters and `where` continue to combine with AND. Existing historical selection contracts also capture this axis at their specified boundary: fatal killer qualification and player lifecycle sources use the pre-operation state, while later guards use current state. Preserve finite captured qualification profiles and actual scope bindings; a later return cannot rewrite a recorded defeat.

Independently require active admission for physical participant input to capture, interact, deliver, pattern matching, and ordinary revival assistance as a helper. Broad selectors cannot grant that permission. Keep each capability's additional life, connection, range, target, and input checks. An ineligible member does not make a fixed personal objective automatically complete or disappear from its required cohort.

A reconnecting target who is online can still receive a valid grave revival under the existing method, window, reach, and lifecycle rules. Revival changes life state only: it grants neither resource readiness nor encounter admission and does not reset reconnect grace. An observation-only return cannot regain ordinary participation or become a recovery opportunity that prolongs the attempt through revival. A living reconnecting participant remains a recovery opportunity only within the original grace; a living observer does not count as an active survivor. Administrative overrides remain explicit privileged operations.

This admission gate supplies no native immunity, movement restriction, invisible wall, or forced camera while resources are pending. Explicit world selections and permitted native actions retain their existing scope and behavior. Assigning a retained member a role or targeting them with an allowed aura does not admit them to mechanics. Authored `submit_token` retains its accepted ability to credit unavailable tracked participants when the matcher explicitly permits their selected states; it is not physical input and does not grant physical eligibility. Actual death and the accepted post-expiry spectator policy retain their own authority.

### Observing participation changes

Add `participation_changed` to the existing participant lifecycle source, with required typed `player` and `participation_before`/`participation_after` values. Use the same retained identity, coherent pre-transition source matching, queued delivery, per-player limits, and current-state guards as Q172. Include its existing online/life snapshots; add the participation snapshots to the other participant lifecycle events so a reconnect can report whether admission also succeeded.

Emit once for each actual committed admission-state change. Where one operation also produces `disconnected`, `reconnected`, or `reconnect_grace_expired`, queue that lifecycle event before its associated `participation_changed`, with snapshots of the same completed operation. An already-online successful restoration produces the participation event without a second `reconnected`. Initial roster construction, teardown, and loading retained records emit no synthetic change. These are server observations, not client-controlled commands or raw resource acknowledgement events.

## Accepted restoration workflow

[Q239-Q240](reconnect-resource-restoration.md) accept automatic restoration under existing consent before play, bounded fallback entry, and explicit retry after world entry. Initial connection setup and a reload after world entry need distinct admission handling. Q239 supplies the narrow automatic-restoration exception to the ordinary manual Apply policy; Q240 preserves explicit retry after world entry.

The feasible configuration integration is recorded in [native research](client-asset-delivery-research.md#configuration-before-world-admission). It can prepare resources before a player entity enters the world. It does not itself implement consent, successful application, admission, task expiry, or recovery. The inspected Fabric `JOIN` callback is earlier than completed native world placement and cannot alone prove Q237's condition.

Restoration must preserve the accepted captured revision, retained resource consumers, bounded capacity, privacy, and current-state synchronization. It cannot replay historical cues or gameplay, revive a player, reset timers, apply the latest hotfix to this attempt, or treat cached bytes as successful application.

## Related contracts

These contracts build on [disconnect and reconnect](mechanics-and-participants.md#q35-disconnect-and-reconnect), [late returns](death-and-revival.md#q67-returning-after-reconnect-grace-expires), [player-state selection](roles-and-player-state.md#q170-explicit-online-and-life-state-filters), [participant lifecycle events](role-and-player-events.md#q172-participant-lifecycle-events), and [ordinary asset application](archived-appearance-delivery.md#q235-one-explicit-action-to-apply-staged-assets). Q237 defines qualification; Q238 exposes existing eligibility distinctions consistently. Q239-Q240 define the consent and restoration workflow. None authorizes mid-attempt publication migration.
