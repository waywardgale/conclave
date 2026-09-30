# Revival dependency research

Research date: 2026-09-13. Target: Minecraft Java 26.2 with Fabric. No dependency was installed or selected, and no runtime compatibility test was performed.

Decision after this investigation: the user selected Conclave-owned revival and spectating modules. See [ADR-0011](adr/0011-owned-revival-and-spectating.md). The recommendations below preserve the research as presented before that selection; further dependency adoption experiments are not planned work.

## Recommendation

None of the three verified Fabric 26.2 candidates supplies Conclave's agreed lifecycle through configuration and supported APIs alone. Better Revive is the closest gameplay match and has a useful documented addon API. Its downed timer, ordinary respawn path, and use of spectator mode conflict with the current requirements. Hardcore Revival offers a clearer way for Conclave to own timing, but leaves the grave and most client behavior to Conclave. Down But Not Out needs more internal adaptation than its small feature set initially suggests.

Keep revival ownership in Conclave's design until a bounded integration experiment proves a dependency removes more code than it adds. For Better Revive, first seek supported hooks for downed entry, per-player deadlines, expiry, respawn veto, and camera policy. Using its existing public revive and grave services is a plausible partial integration. Replacing its internal lifecycle with mixins would need a separate maintainability decision.

This recommendation is an engineering inference from the evidence below. Release availability establishes a matching artifact, not robustness with Conclave or a particular modpack.

## Fit criteria

The requirements come from [death and revival](death-and-revival.md). Grave inventory storage remains undecided. A mod's inventory recovery system therefore cannot be counted as a required benefit yet.

| Required behavior | Dependency implication |
|---|---|
| Grave at lethal-damage location; constrained third-person view; no spectator mode during the revive opportunity | Need control of downed representation, camera, movement, and exposed information. |
| Assisted revival on and instant by default; self-revival off; configurable permissions and delays | Need separate method eligibility and interaction timing. A revival minigame duration is a different control. |
| Global 15-second combat window; no window outside combat | Need combat-aware state or permission for Conclave to own expiry. |
| Window expiry eliminates the player until attempt end; selectable viewing policy after elimination | Need to prevent ordinary respawn and distinguish revival from attempt-finished recovery. |
| Gameplay changes apply only to new attempts, including effective global defaults | Simultaneous attempts must retain different policies. Reloading one mutable global config cannot establish that behavior. |

## Ranked candidates

Rank reflects fit for further investigation, not a selection.

| Rank | Verified Fabric 26.2 release | Published, UTC | Requirements and license |
|---|---|---|---|
| 1. Better Revive, MrStilu2k6 | [v2.2.0, vJxaO6H1](https://modrinth.com/mod/better-revive/version/vJxaO6H1) | 2026-08-10 21:24 | Java >=25, Fabric Loader >=0.19.3, Fabric API; client and server. Jar says All-Rights-Reserved; Modrinth project says MIT. |
| 2. Hardcore Revival, TwelveIterations | [26.2.0.6+fabric-26.2, NqBY7aOQ](https://modrinth.com/mod/hardcore-revival/version/NqBY7aOQ) | 2026-09-08 13:20 | Java >=25, Fabric Loader >=0.19.3, Fabric API, Balm Fabric >=26.2.0.1; client and server. All Rights Reserved. |
| 3. Down But Not Out, phasmoware | [0.5.2+26.2, 8DXufGeM](https://modrinth.com/mod/down-but-not-out/version/8DXufGeM) | 2026-08-29 01:52 | Java >=25, Fabric Loader >=0.19.3, Fabric API; dedicated server. MIT. |

Exact release metadata comes from the author-published [Better Revive version record](https://api.modrinth.com/v2/version/vJxaO6H1), [Hardcore Revival version record](https://api.modrinth.com/v2/version/NqBY7aOQ), and [Down But Not Out version record](https://api.modrinth.com/v2/version/8DXufGeM). Dependencies were checked against the published jar manifests or matching source. All three have recent 26.2 releases; ongoing future support is unknown.

### 1. Better Revive

The author documents graves that capture inventory and XP, a downed view around the grave, protected recovery, configurable revive locks, and persistence across restart. Ordinary assistance uses accepted potion offers or a Life Pulse minigame. Players can choose ordinary respawn while downed, and timeout makes death final. These defaults do not match Conclave's instant assistance and attempt-scoped elimination. [Author gameplay description](https://modrinth.com/mod/better-revive)

#### Supported integration

The [API 1.0 guide for v2.2.0](https://www.mrstilu2k6.de/docs/better-revive-api) defines `de.mrstilu2k6.better_revive.api` as the stable addon contract and excludes implementation classes from its compatibility promise. Fabric addons register during initialization; registration freezes before the first server starts. Mutations run on the logical server thread.

`ReviveExtension` can reject a revival, modify its outcome, and observe success. An addon can complete its own interaction with `BetterReviveApi.revives().revive(request)`. The service checks state, cooldown, grave ownership and priority, dimension, and a safe destination before restoring inventory and clearing the downed state. This supports a Conclave-owned assistance interaction. The guide does not establish a complete self-revival policy. [Revive extension contract](https://www.mrstilu2k6.de/docs/better-revive-api#revive-extension)

Grave services support creation, revision-checked edits, collection, removal, and lifecycle callbacks. Client extensions add administration pages or extra grave-overview lines. Grave appearance providers replace the head display model. [Grave and client API](https://www.mrstilu2k6.de/docs/better-revive-api#grave-service)

#### Verified limits in the exact artifact

I inspected the [published Fabric 26.2 jar](https://cdn.modrinth.com/data/kcGm0YBV/versions/vJxaO6H1/Better%20Revive%20-%202.2.0%20-%20Fabric%20-%2026.2.jar) in memory, including class signatures and selected lifecycle bytecode. Its SHA-1 is `1bb1627d2aa59c4bb6b31d6db0856be1267c1cfd`. The following are artifact observations, not runtime test results.

| Inspected class, under `de.mrstilu2k6.better_revive` | Finding |
|---|---|
| `api.ReviveService`, `api.DownedPlayerSnapshot` | Query, offer, and revive operations exist. The snapshot exposes `startedAtMs` and `expiresAtMs`. No public downed-entry, deadline setter, expiry veto, or respawn veto exists in this service. |
| `util.DownedPlayerManager.enterDowned` | Takes only a player. Reads global `ReviveConfig.graveDurationSeconds`, records a wall-clock deadline, and queues expiry. It sets spectator game mode and invokes grave camera positioning. |
| `config.ReviveConfig` | Declares grave-duration bounds of 30 to 604800 seconds. The requested 15-second and unbounded policies fall outside those declared bounds. |
| `util.DownedPlayerManager.tick`, `acceptNormalRespawn` | Expiry calls `finalizeDeath`. Ordinary respawn follows a separate path that clears downed state and returns the player to a spawn destination. |
| `api.GraveService`, `api.GraveEditor` | Can edit a grave's timer. This is separate from the manager's downed-state deadline and expiry queue. |

The public client API has no camera-policy or downed respawn-button veto hook. `ReviveExtension.beforeRevive` can reject ordinary revives, but it does not intercept ordinary respawn. Internal methods such as `enterDowned`, `finalizeDeath`, and `acceptNormalRespawn` are outside the documented compatibility contract. [API guide](https://www.mrstilu2k6.de/docs/better-revive-api), [exact artifact](https://modrinth.com/mod/better-revive/version/vJxaO6H1)

The timing distinction matters. Better Revive captures a deadline when the player becomes downed, not when the encounter attempt starts. After a global config change, a later death in an older active attempt could use the new duration. A grave timer edit does not supply the missing per-player downed-policy API. This is an inference from the inspected state-entry and timer code.

Conclave would also need to replace or extend the spectator-based downed view and final-death recovery. No verified supported hook supplies the required eliminated-until-attempt-end state. Optional private teammate information remains Conclave's responsibility.

#### License and distribution

The exact jar, [CurseForge page](https://www.curseforge.com/minecraft/mc-mods/better-revive), and [author site](https://www.mrstilu2k6.de/mods/better-revive) say All Rights Reserved. The [Modrinth project metadata](https://api.modrinth.com/v2/project/better-revive) says MIT. Do not assume the Modrinth label grants permission to copy, fork, or bundle this artifact. Resolve the discrepancy before planning those distribution choices. Ordinary dependency use through the author's documented API is a separate question; the metadata conflict does not establish that such integration is impossible.

### 2. Hardcore Revival

The matching [v26.2.0.6 source tag](https://github.com/TwelveIterations/HardcoreRevival/tree/v26.2.0.6) resolves to `1c7c49812dcd8df682fd891c021416f068aa7474`. Its [configuration](https://mods.twelveiterations.com/minecraft/hardcore-revival/config) includes `secondsUntilDeath`, default 120 and zero to disable expiry; `rescueActionTicks`, default 40; and `allowAcceptingFate`. Source control flow implies rescue duration zero completes on the next server tick after assistance starts. No runtime timing test was performed. [Rescue handler](https://github.com/TwelveIterations/HardcoreRevival/blob/v26.2.0.6/common/src/main/java/net/blay09/mods/hardcorerevival/handler/RescueHandler.java)

Its [public API](https://github.com/TwelveIterations/HardcoreRevival/blob/v26.2.0.6/common/src/main/java/net/blay09/mods/hardcorerevival/api/HardcoreRevivalAPI.java) exposes `knockout`, `wakeup`, `isKnockedOut`, and elapsed/remaining KO ticks. There is no public deadline setter. `PlayerAboutToKnockOutEvent` is cancellable, but cancellation lets ordinary death proceed. It is not a general death veto. Revived and rescued events report completed changes. [Death handler](https://github.com/TwelveIterations/HardcoreRevival/blob/v26.2.0.6/common/src/main/java/net/blay09/mods/hardcorerevival/handler/KnockoutHandler.java), [lifecycle manager](https://github.com/TwelveIterations/HardcoreRevival/blob/v26.2.0.6/common/src/main/java/net/blay09/mods/hardcorerevival/HardcoreRevivalManager.java)

A partial adapter can disable stock expiry globally, maintain Conclave deadlines from each attempt's pinned revision, and call `wakeup(player, false)` on an authorized transition. This avoids modifying the internal timer. Conclave would still have to own helper eligibility and interaction progress if timing differs across active attempts. Optional [Shogi rules](https://mods.twelveiterations.com/minecraft/hardcore-revival/rules) gate ordinary rescue; they are not a per-attempt timing API.

The remaining behavior differs substantially. KO retains a living player, locks actions, and uses a lying pose. Stock expiry applies fatal damage. The client opens a KO screen and renders an overlay with changed FOV. No verified config/API switch replaces the entire presentation with Conclave's grave camera. A normal death-triggered grave mod also will not automatically create a grave when Hardcore Revival cancels death. [KO handler](https://github.com/TwelveIterations/HardcoreRevival/blob/v26.2.0.6/common/src/main/java/net/blay09/mods/hardcorerevival/handler/KnockoutHandler.java), [client implementation](https://github.com/TwelveIterations/HardcoreRevival/blob/v26.2.0.6/common/src/main/java/net/blay09/mods/hardcorerevival/client/HardcoreRevivalClient.java)

There is no built-in player self-revive control matching Conclave's policy. A Conclave-owned authorized action could use `wakeup`; Conclave must also enforce elimination, attempt-finished recovery, and private viewing. Depending on how much of the stock presentation is retained, this leaves client mixins or upstream API additions alongside the supported server adapter.

The [license](https://github.com/TwelveIterations/HardcoreRevival/blob/v26.2.0.6/LICENSE) is All Rights Reserved. The author's [permissions](https://mods.twelveiterations.com/permissions) allow modpacks on supported platforms and prohibit public jar/fork rehosting. They separately describe limited private sharing of disclosed modifications. Do not treat a publicly distributed fork as an already permitted implementation option.

### 3. Down But Not Out

The inspected [source revision](https://github.com/phasmoware/DownButNotOutMod/tree/c5f7e029d3576658ca7a6394f45a0cf9a4746f5b) is `c5f7e029d3576658ca7a6394f45a0cf9a4746f5b`. Its [manifest](https://github.com/phasmoware/DownButNotOutMod/blob/c5f7e029d3576658ca7a6394f45a0cf9a4746f5b/src/main/resources/fabric.mod.json) declares physical environment `server`. That excludes an integrated singleplayer/LAN server, consistent with Fabric's [environment definition](https://docs.fabricmc.net/develop/loader/fabric-mod-json). It could still coexist with Conclave clients on a dedicated server.

Configuration supports instant assistance with `REVIVE_DURATION_TICKS=0`, and an unlimited bleedout timer with `BLEEDING_OUT_DURATION_TICKS=-1`. A 300-tick duration represents 15 seconds at the normal 20 ticks per second. These are mutable singleton settings, and revive progress reads the global threshold. The default repeat-revival penalty changes timing unless disabled. [Author README](https://github.com/phasmoware/DownButNotOutMod/blob/c5f7e029d3576658ca7a6394f45a0cf9a4746f5b/README.md), [revive timer](https://github.com/phasmoware/DownButNotOutMod/blob/c5f7e029d3576658ca7a6394f45a0cf9a4746f5b/src/main/java/com/phasmoware/down_but_not_out/timer/ReviveTimer.java)

The player mixin exposes a per-player bleedout timer through a duck interface, and that timer has a setter. This is an implementation hook, not a documented stable addon API. There is no custom expiry veto in the inspected timer. The class named `ModEvents` registers Fabric events for the mod itself; it does not provide Conclave lifecycle events. [Bleedout timer](https://github.com/phasmoware/DownButNotOutMod/blob/c5f7e029d3576658ca7a6394f45a0cf9a4746f5b/src/main/java/com/phasmoware/down_but_not_out/timer/BleedOutTimer.java), [event registration](https://github.com/phasmoware/DownButNotOutMod/blob/c5f7e029d3576658ca7a6394f45a0cf9a4746f5b/src/main/java/com/phasmoware/down_but_not_out/registry/ModEvents.java)

Downed players crawl through an invisible helper-entity mechanism. They can use a held vanilla totem, with no separate self-revive switch in the inspected config. Expiry damages the player through the ordinary death/totem path. Revive cleanup also changes base movement speed and status effects, which would need review alongside Conclave auras and role modifiers. It supplies neither a grave camera nor an attempt elimination state. [Event handler](https://github.com/phasmoware/DownButNotOutMod/blob/c5f7e029d3576658ca7a6394f45a0cf9a4746f5b/src/main/java/com/phasmoware/down_but_not_out/handler/EventCallbackHandler.java), [downed utility](https://github.com/phasmoware/DownButNotOutMod/blob/c5f7e029d3576658ca7a6394f45a0cf9a4746f5b/src/main/java/com/phasmoware/down_but_not_out/util/DownedUtility.java)

Its [MIT license](https://github.com/phasmoware/DownButNotOutMod/blob/c5f7e029d3576658ca7a6394f45a0cf9a4746f5b/LICENSE) permits more reuse than the other candidates' inspected labels. That does not make an invasive adapter or maintained fork simpler than implementing Conclave's smaller lifecycle directly.

## Named alternatives that do not qualify for this target

| Project | Why it was not ranked |
|---|---|
| CreativeMD's PlayerRevive | The author's releases target Forge/NeoForge. A 26.2 NeoForge build is not a Fabric dependency. [Author project](https://www.curseforge.com/minecraft/mc-mods/playerrevive) |
| PlayerReviveFabric, TNTNetta | Separate project. Published Fabric v1.0.0 targets only 1.20.1, released 2026-05-03. All Rights Reserved; no 26.2 artifact verified. [Author release metadata](https://api.modrinth.com/v2/version/gTKFSuk2) |
| You're in Grave Danger | Latest verified Fabric release 2.4.18 targets 1.21/1.21.1, released 2025-06-22. Its MIT grave implementation can inform design, but no 26.2 dependency was verified. [Author release](https://modrinth.com/mod/yigd/version/T3grMjgj), [source repository](https://github.com/B1n-ry/Youre-in-grave-danger) |

## Evidence required before adoption

An integration experiment should demonstrate these five outcomes on Fabric 26.2 before committing to a dependency:

1. Two active attempts retain different effective revival defaults after a gameplay reload, including deaths that occur later in the older attempt.
2. Assisted and self-revive requests use Conclave's eligibility and timing, while ordinary dependency interactions cannot bypass them.
3. Expiry prevents revival and ordinary respawn until attempt end, with one transition despite simultaneous helper requests or disconnect/reconnect.
4. The downed grave camera and later viewing policy match the chosen behavior; targeted private information stays separate from the watched player's HUD.
5. Grave creation, inventory treatment, cleanup, and recovery have one owner and survive interrupted transitions without duplicate restoration.

A camera lock alone cannot remove world data already delivered to a client. The privacy criterion concerns Conclave's own disclosure and supported viewing behavior; it is not a promise that a modified client cannot inspect received world data.

Public interfaces support some of these operations today. None of the inspected candidates establishes all five. The remaining choice is between an upstream API extension, a narrowly scoped partial dependency, and a minimal Conclave-owned lifecycle. No option has been implemented in this research.
