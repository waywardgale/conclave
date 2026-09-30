# Game master commands

Status: Q41 accepted the membership commands, initial GM powers, persistent UUID membership, immediate revocation, trusted-console access, and exclusion of command blocks. Q274 accepts the concrete operation/recovery contracts, initial remote-source exclusion and diagnostic/audit policy. Storage representation remains implementation work. No commands have been implemented.

## Authority

Only operator authority can grant GM status. Game masters are separate from gameplay roles, and ordinary encounter YAML cannot grant administrative authority. The accepted grant, revoke, and list commands all require the same operator authority or trusted server console.

Check the initiating player's operator identity on the server for delegation commands. A generic command-permission threshold alone may admit command blocks, so it is not the complete policy. Permit the trusted server console and reject command blocks. Q274 excludes RCON and remote/unknown privileged command sources in the initial release. ASVS 8.1.1, 8.2.1, 8.3.1.

GMs cannot grant GM status merely because they are GMs, and granting GM status does not grant Minecraft OP. Persistent membership uses the player's UUID, with names as display labels. Membership is administrative server state rather than authorable encounter content. Storage format is not yet chosen.

Check permissions on every operation and apply revocation immediately, independently of the immutable gameplay revision held by an attempt. ASVS 8.3.1 and 8.3.2. The latter is a higher-level requirement deliberately selected for administrative revocation; this is not a claim of full ASVS Level 3 compliance.

## Accepted command groups

| Group | Examples | Authority |
|---|---|---|
| GM membership | `/conclave gm grant <player>`, `/conclave gm revoke <player>`, `/conclave gm list` | Operator or trusted server console only |
| Inspection | List encounters, arenas, attempts, and attempt status | GM or operator |
| Attempt control | Start, stop, or restart a specified attempt | GM or operator |
| Recovery | Explicitly force a participant revival or restore a relic | GM or operator |
| Content administration | Publish a complete revision, inspect history, or roll back for future attempts, all inside Minecraft | Publication and rollback require operator authority; the accepted interface is in [in-game authoring](in-game-authoring.md) |

[Q103](arena-placement-and-admission.md#q103-ordinary-minecraft-behavior) preserves ordinary world rules. Explicit GM inspection provides observation and diagnostics; it is not a blanket collision or combat exception for GMs. Q274 accepts explicit authorized private-state inspection, permission-filtered targets and immediate revocation.

Restart means stopping and cleaning up the old attempt and creating a new attempt. It does not replace the content revision of a running attempt. Gameplay recovery commands alter state through explicit authorized operations; they do not silently change the attempt's manifest definitions.

GM recovery may explicitly override ordinary player revival restrictions and must record the initiating administrator and result. Q274 accepts explicit live-attempt revival without readmission, excludes frozen Finishing encounter gameplay, and uses original-record recovery retry after an attempt ends.

Accepted [Q50](location-anchors.md#q50-visibility-permissions-and-configuration-interface) allows operators and GMs to enter edit mode, view Location anchor labels, and prepare drafts while retaining operator-only publication. The Creative-mode area visualization requirement has an accepted viewing policy in [Q55](areas.md#q55-creative-visualization-and-permissions); it does not grant ordinary Creative players editing or publication authority.

Accepted Q73 adds the `/conclave` screen and `/conclave edit`, `/conclave validate`, `/conclave publish`, `/conclave history`, and `/conclave rollback <revision>` shortcuts. GMs can prepare encounter-content drafts and run clearly labelled tests, while operators publish, roll back, and change global gameplay settings. See [in-game authoring](in-game-authoring.md).

Commands need discoverable help, suggestions for known identifiers, and errors naming the invalid argument or incompatible state. Permission-filtered suggestions must not reveal private encounter state to ordinary participants.

## Player coordination commands

Q105 requires a ready-check command alongside author-defined encounter activation. [Q110](encounter-activation.md#q110-ready-check-command) accepts `/conclave readycheck`, recipient handling, a player confirmation HUD popup, and optional use of the result in start conditions. Q115 accepts popup controls; Q113 accepts how GM changes affect raider selections and existing participant snapshots. This command does not grant GM authority. Q104 rejects `/conclave leave`; no equivalent forfeit menu is planned.

## Operation checks and audit

Validate the command's exact target, bounds, and current state in the trusted server layer. A command selecting an attempt must act only on that attempt; check explicit authority before accessing participant-private debug data. ASVS 2.2.1, 2.2.2, 8.2.1, 8.2.2; apply field-level restrictions from ASVS 8.2.3 when exposing private debug fields.

Q274 accepts recording grants, revocations, administrative mutations, and bounded denied privileged requests with UTC time, initiating identity, target, operation, and result. Do not copy secret pattern values or credentials into the audit record. ASVS 16.2.1, 16.2.2, 16.2.5, 16.3.2, 16.3.3. These logging requirements are design requirements, not a request for an unrelated logging platform or a claim of implemented assurance.

## Verified 26.2 infrastructure

Fabric's [command guide](https://docs.fabricmc.net/develop/commands/basics) documents server command registration and arbitrary Brigadier permission predicates. Its moderator-permission example explicitly includes command blocks, which is why operator delegation requires source checks as well as permission checks.

Fabric API 26.2 includes [permission predicates](https://github.com/FabricMC/fabric-api/blob/26.2/fabric-permission-api-v1/src/main/java/net/fabricmc/fabric/api/permission/v1/PermissionPredicates.java) and [permission context](https://github.com/FabricMC/fabric-api/blob/26.2/fabric-permission-api-v1/src/main/java/net/fabricmc/fabric/api/permission/v1/PermissionContext.java). No external permissions plugin is necessary for the proposed small GM registry. Exact operator and console checks must be verified against the selected 26.2 server sources during implementation.

[Q253](reward-generation-and-review.md#q253-hold-uncertain-transfers-for-operator-review) accepts an operator-only in-game resolution workflow for existing uncertain completion-reward transfers. Operators may Record as delivered or Return to pending for the recorded amount with an audited reason. GMs retain inspection only. This adds no general grant or known-paid reward-reissue authority.

[Q274](administrative-operation-contracts.md) accepts the command forms, manual-start override, forced-revival limits, aura administration, recovery retry, remote-source exclusion and audit requirements.
