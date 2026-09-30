# Required resource loss and observer assets

Status: Q241-Q242 are accepted. Q239-Q240 settle reconnect preparation and retry while the original return opportunity remains. These contracts address an already-admitted client losing required resources and ordinary asset application after the player becomes an observer. No implementation exists.

## Q241: confirmed loss of required resources after admission

Accepted: treat confirmed loss of an admitted participant's required captured resources as an immediate technical interruption of the affected attempt under Q69. Do not wait for a later phase or cue to discover the same known incompatibility. Cancel future attempt work, clean up owned state, and perform the existing recovery policy. Preserve the distinction from a gameplay failure or wipe punishment; unrelated attempts continue.

Use the existing classification of resources required by the captured attempt and its supported capabilities. This adds no blanket requirement that every cosmetic output must work. A missing optional sound, unavailable optional output, or cosmetic renderer failure retains its documented warning, skip, or supported fallback. A fallback that cannot satisfy required readiness cannot be counted as successful required delivery. Do not silently downgrade a required consumer after failure.

### Evidence and an uncertain resource state

Bind resource state and failure reports to the actual client connection, applied generation, expected captured graph, and supported operation. Reconcile the supported client's actual reload outcome. A stale response from another generation or a client-supplied attempt ID is not authority to stop an unrelated attempt. The protocol verifies its own state transitions; it does not prove what a human sees or prevent a modified client from lying about rendering.

A normal reload starting with the same resources is not by itself confirmed loss. While an actual reload makes the applied state uncertain, invalidate the affected readiness acknowledgement until the completed set is verified again. Do not claim compatibility from cached files or an earlier generation while resources are changing. If the same required graph is successfully verified, restore its readiness without replaying presentation or changing participation.

This uncertainty is not a new pause or asset-repair grace period. Gameplay and timers continue under their existing rules. A required action invoked while compatible delivery cannot be established still follows Q137's already-accepted error contract. Neither this policy nor a reload adds an implicit wait, shrinks the required audience, or overrides a capability's explicitly documented recoverable result. A completed reload failure or confirmed missing/incompatible required graph follows the immediate interruption policy above.

### Participation and recovery

Do not synthesize a disconnection, death, `active` to `reconnecting` transition, or fresh reconnect opportunity to repair an active client's assets. A real disconnection retains Q237-Q240, including its original opportunity and captured resources. A resource failure observed only after that genuine disconnection is handled in the reconnect preparation context, rather than retroactively treating the disconnected player as admitted.

No automatic in-world repair or ordinary hotfix application is introduced for an active participant. Q239-Q240 remain the narrow original-resource restoration path for an actual reconnect opportunity. After a technical interruption, normal asset repair uses the explicit Apply workflow at a permitted time. An already-ended attempt is not reopened or retroactively changed because a later report arrives.

Give the affected player a short explanation that required encounter resources became unavailable. Authorized diagnostics identify the affected connection/generation, captured revision, resource or capability, and cause without exposing private puzzle state. Required presentation that fails late keeps its documented error path; no earlier gameplay action, item grant, or external native effect is rolled back by this rule.

## Q242: ordinary Apply for an observation-only participant

Accepted: allow a player whose current attempt participation is `observer` to use the existing explicit `Apply assets` action while that attempt continues. Apply the ordinary consent, capacity, consumer, serialization, and recovery rules. This clarifies Q235's eligibility boundary; retained roster membership alone does not prohibit an observer from applying assets.

This is presentation preparation only. Keep `participation: observer`, the expired return opportunity, and the existing spectator and private-information policy. Never emit another `reconnected`, reset timers, revive the player, enroll them in another attempt, or restore active participation merely because resource application succeeds. Q240's removed `Retry restoration` action stays removed; ordinary Apply cannot act as a renamed late-join action.

### Which resources the observer receives

Treat authorized observation as a current resource consumer. Resolve the observed attempt's models, sounds, textures, and other supported presentation from its captured revision. Include their supported dependency graphs and all other still-required retained consumers in the candidate set. Apply Q234's authorization and bounded-transfer rules; do not expose unrelated manifests, another player's private gameplay data, or the entire server archive just because this player is observing.

Ordinary eligible pending assets may coexist in the same applied set under Q235, but cannot replace the observed attempt's captured resources. An already-applied resource whose consumer remains active stays protected. Successful loading changes the client's resource availability, not the encounter's definition or current gameplay state. Asset possession does not grant access to private clues or watched-player information.

Show in the existing assets view that application improves the observer's permitted view and does not allow rejoining this attempt. Keep application explicit; expiry, becoming an observer, and an attempt ending do not trigger a reload automatically. Respect an existing consent refusal and never require the player to accept resources merely to continue ordinary server play.

### Application and presentation boundaries

Use Q235's single admitted application operation. Wait for finite Conclave audio already finishing within the bounded preparation interval; resolve conflicting previews through their existing controls. Preserve required resources, cache/recovery data, current logical visual state, and unsaved drafts. Applying does not pause the encounter or grant native invulnerability. Reconcile the actual outcome and keep a failed or uncertain set unready for dependent work.

Recheck actual participation when admitting Apply. A passed-out participant watching a teammate can still be `active` under Q238 and does not qualify through their camera mode. A player still `reconnecting` uses Q240's bounded original-resource retry. Once a resource operation has begun, coordinate any privileged attempt admission with the same known-resource-state requirement. No administrative state change can make an uncertain resource set ready.

Observation continues using compatible loaded resources and existing supported fallbacks while assets are unavailable or being applied. Cosmetic output can be skipped under Q137. Loading later may restore only still-current authorized visual state under its existing lifetime contract, never an expired clue or old sound.

Presentation audiences still default to `participation: any`. Consequently, an online observer explicitly selected by `required: true` remains subject to Q137 if their resources are unavailable. The Apply flow does not waive that contract or make the action wait. Authors who intend a required cue only for admitted participants must select `participation: active`; Conclave must not silently change the audience for them.

If the observed attempt ends while Apply is pending, re-evaluate whether its resources are still needed under ordinary consumer and recovery rules. A native reload already underway may finish; reconcile its applied state without restoring the ended attempt. Finishing one resource operation never automatically starts another pending update or replays a gameplay result.

## Related contracts

These contracts extend [Q239-Q240 reconnect restoration](reconnect-resource-restoration.md), [Q235 explicit application](archived-appearance-delivery.md#q235-one-explicit-action-to-apply-staged-assets), and [Q238 participation](reconnect-admission-and-assets.md#q238-participation-independent-of-online-and-life-state). They preserve [Q137 required versus cosmetic presentation](presentation-controls-and-models.md#q137-selected-recipients-without-the-required-assets), [Q69 technical errors](execution-and-errors.md#q69-failure-categories-and-bounded-work), and [current clue restoration](pattern-presentation-and-clues.md).

Q241 selects the immediate policy for confirmed required-resource loss after admission. Q242 independently permits the existing manual application workflow for observers whose ordinary rejoin opportunity has ended. Neither changes reconnect grace or authorizes applying a new definition to an active attempt.
