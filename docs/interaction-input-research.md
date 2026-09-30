# Fabric interaction input research

Reviewed on 2026-09-22 for Minecraft Java 26.2. This is primary-source inspection, not a Conclave integration test. The accepted authoring contracts are separate in [interaction input and progress](interaction-input-and-progress.md).

## Source identity

Fabric commit `0ec1d58582bd816012a8f9f1617eb4dfdeda1e93` declares Minecraft `26.2`, Fabric API `0.160.0`, and interaction module `5.2.8`. This identifies the inspected source; it does not select the eventual dependency pin. [Version declaration](https://github.com/FabricMC/fabric/blob/0ec1d58582bd816012a8f9f1617eb4dfdeda1e93/gradle.properties).

## Callbacks observe attempted native use

The block and entity use event dispatchers stop at the first listener returning a result other than `PASS`. An earlier cancellation can prevent a later listener from running. Returning `PASS` continues dispatch and ordinary processing; it does not prove the eventual native operation succeeded. [Block callback](https://github.com/FabricMC/fabric/blob/0ec1d58582bd816012a8f9f1617eb4dfdeda1e93/fabric-events-interaction-v0/src/main/java/net/fabricmc/fabric/api/event/player/UseBlockCallback.java), [entity callback](https://github.com/FabricMC/fabric/blob/0ec1d58582bd816012a8f9f1617eb4dfdeda1e93/fabric-events-interaction-v0/src/main/java/net/fabricmc/fabric/api/event/player/UseEntityCallback.java).

The server block hook runs at the beginning of `ServerPlayerGameMode.useItemOn`. The entity hook runs before the native interaction within `handleInteract`. Non-`PASS` results stop the intercepted processing. These are interception points, not universal after-success notifications for placement, trading, or opening an interface. [Server block hook](https://github.com/FabricMC/fabric/blob/0ec1d58582bd816012a8f9f1617eb4dfdeda1e93/fabric-events-interaction-v0/src/main/java/net/fabricmc/fabric/mixin/event/interaction/ServerPlayerGameModeMixin.java), [server entity hook](https://github.com/FabricMC/fabric/blob/0ec1d58582bd816012a8f9f1617eb4dfdeda1e93/fabric-events-interaction-v0/src/main/java/net/fabricmc/fabric/mixin/event/interaction/ServerGamePacketListenerImplMixin.java).

Inference for Q177: Conclave must route its own matching observers before issuing its native cancellation decision. A standalone listener that immediately cancels would make later matching listeners depend on registration order. This does not solve cancellation or side effects introduced by unrelated mods.

## Logical sides and spectator checks

On the client, consuming results can stop ordinary handling while sending an interaction packet. Failure can stop handling without sending that packet. A client-only failure result therefore cannot report an authored use to the server by itself. The pinned block client hook checks `consumesAction()` when deciding whether to send. The entity contract distinguishes success with a hand swing from consumption without that swing. [Client block hook](https://github.com/FabricMC/fabric/blob/0ec1d58582bd816012a8f9f1617eb4dfdeda1e93/fabric-events-interaction-v0/src/client/java/net/fabricmc/fabric/mixin/event/interaction/client/MultiPlayerGameModeMixin.java), [client entity hook](https://github.com/FabricMC/fabric/blob/0ec1d58582bd816012a8f9f1617eb4dfdeda1e93/fabric-events-interaction-v0/src/client/java/net/fabricmc/fabric/mixin/event/interaction/client/MinecraftMixin.java), [entity callback contract](https://github.com/FabricMC/fabric/blob/0ec1d58582bd816012a8f9f1617eb4dfdeda1e93/fabric-events-interaction-v0/src/main/java/net/fabricmc/fabric/api/event/player/UseEntityCallback.java).

The broad block callback documentation describes notification before spectator checks, but the inspected client mixin explicitly excludes spectators before invoking it. The server hook remains at method entry. Callback arrival alone is therefore insufficient evidence of Conclave eligibility. The implementation must validate its own participant and lifecycle requirements. [Client block hook](https://github.com/FabricMC/fabric/blob/0ec1d58582bd816012a8f9f1617eb4dfdeda1e93/fabric-events-interaction-v0/src/client/java/net/fabricmc/fabric/mixin/event/interaction/client/MultiPlayerGameModeMixin.java).

## Fresh input and held intent

The use callbacks expose the player, world, hand, and hit target. Their signatures do not supply a unique physical press, release notification, or continuous held state. Fabric's key-mapping documentation places key mappings on the client and warns that holding a mapping can repeat `consumeClick()` handling. Callback arrival and consumed-click handling are therefore not a complete one-press identity contract. No exact native repeat interval or number of hand attempts was established by this audit. [Callback signatures](https://github.com/FabricMC/fabric/blob/0ec1d58582bd816012a8f9f1617eb4dfdeda1e93/fabric-events-interaction-v0/src/main/java/net/fabricmc/fabric/api/event/player/UseEntityCallback.java), [key mappings](https://docs.fabricmc.net/develop/key-mappings).

Inference for Q175-Q176: the required Conclave client can report start, continuation, and release intent through custom payloads, while the server owns target validation, simulation time, and credit. Fabric supports client-to-server payloads. This is an implementation direction for arbitrary configured block/NPC holds, not proof of physical key state or a claim that custom packets are the only possible architecture. Native sustained-use lifecycles for particular items may offer narrower alternatives. [Networking](https://docs.fabricmc.net/develop/networking).

## Narrower replacement hooks

Fabric also exposes `BlockEvents.USE_ITEM_ON`, `BlockEvents.USE_WITHOUT_ITEM`, `ItemEvents.USE_ON`, and `ItemEvents.USE` to replace particular native calls while retaining surrounding logic. Their pass-through result is `null`; a non-null result replaces the intercepted call. That differs from the broad use callbacks' `PASS` convention. These hooks may support a focused adapter, but do not establish universal post-success observation. [Fabric explanation](https://www.fabricmc.net/2026/03/14/261.html), [pinned block-event contracts](https://github.com/FabricMC/fabric/blob/0ec1d58582bd816012a8f9f1617eb4dfdeda1e93/fabric-events-interaction-v0/src/main/java/net/fabricmc/fabric/api/event/player/BlockEvents.java).

## Remaining verification

Implementation must verify server geometry and reach tests, both-hand correlation, client prediction, focus/release handling, disconnects, stale input, target replacement, and packet latency. Consuming a gesture must not leave native repeats active or suppress unrelated future interactions. A successful native interaction remains distinct from an admitted Conclave gesture. No live interaction adapter, hook coverage test, or compatibility guarantee was produced during this research.
