# Client asset delivery and reload boundaries

Checked 2026-09-26 against the SHA-1-verified [official Minecraft 26.2 artifact](https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar), digest `2dc72797acbc1b63fc16a11c4ac393605f453754`. These findings support the accepted Q234-Q236 contracts. Static source inspection establishes native paths, not an implemented Conclave transfer, consent, cache, reload, or playback adapter.

## Item references already synchronized to a client

The native container synchronizer in `ServerPlayer$1` sends initial contents, changed slots, and the cursor stack. `ClientPacketListener.handleContainerContent`, `handleContainerSetSlot`, and `handleSetPlayerInventory` update the corresponding client state. The item codecs include component patches, so the already verified network-synchronized `ITEM_MODEL` identifier travels with a supported stack.

`ServerEntity.sendPairingData` sends initial tracked metadata and nonempty equipment. `ClientboundSetEquipmentPacket` serializes stacks, and `handleSetEquipment` applies them to the target client entity. Dropped items use `ItemEntity.DATA_ITEM` with `EntityDataSerializers.ITEM_STACK` and `ItemStack.OPTIONAL_STREAM_CODEC`. Later dirty tracked metadata passes through `ServerEntity.sendDirtyEntityData` and `handleSetEntityData`.

These paths expose actual synchronized item references, not every item in the world or every other player's inventory. The server knows the stack state and intended recipients at these boundaries. Fabric supports custom payload delivery to a selected server player and server-side validation of incoming requests. See [Fabric networking](https://docs.fabricmc.net/develop/networking#sending-a-packet-to-the-client).

A bounded server offer for an archived appearance is therefore feasible as a Conclave integration inference. Native item synchronization does not fetch missing assets, provide archive authorization, or grant access to a client-supplied ID. Conclave must preserve the recipient and reference boundary deliberately.

## Reload affects the client resource set

`DownloadedPackSource.startReload` configures the pack source and calls `Minecraft.reloadResourcePacks`. That operation reloads the repository, opens all selected packs, and invokes the shared `ReloadableResourceManager.createReload`. The manager replaces its combined resource view and runs its registered listeners. This is a client-wide operation, not an isolated reload for one item stack.

Native feedback distinguishes accepted, downloaded, successfully loaded, download failure, and reload failure. The completed success path reaches `DownloadedPackSource.onReloadSuccess`; `ServerPackManager$1.onSuccess` marks participating packs active and reports application. Failures follow activation-failure and recovery/rollback paths. Finishing a download or scheduling a reload cannot establish successful application, and the existence of native recovery does not prove atomic rollback of every mod's reload listener.

## Consent must be preserved by custom delivery

`ServerData.ServerPackStatus` exposes `ENABLED`, `DISABLED`, and `PROMPT`. Native `ClientCommonPacketListenerImpl.handleResourcePackPush` consults that setting; a required pack with the disabled setting can still open a prompt. `ServerPackManager.pushPack` rejects a pack while its prompt state is declined.

Those checks describe the native server-pack path. They do not automatically protect a custom Conclave transfer or locally supplied pack. The accepted consent, no-kick, and no-forced-activation rules therefore require explicit integration. A resource request or an observed item is not itself permission to bypass the player's choice.

## Reload interrupts native audio

`Minecraft` registers `SoundManager` as a resource-reload listener. `SoundManager.apply` calls `SoundEngine.reload`, which calls `destroy` and then `loadLibrary`. When loaded, `destroy` calls `stopAll`, clears sound buffers, and cleans up the sound library.

Consequently, native finite sounds and voice lines do not automatically continue through a resource reload. Keeping a Conclave logical playback record is not sufficient to preserve the native audio position. Q235 accepts waiting for finishing finite Conclave audio before an admitted update; implementation must not advertise uninterrupted native sound playback across Apply without a separately verified integration.

## Configuration before world admission

Additional inspection on 2026-09-26 used the same official Minecraft artifact and Fabric source at commit `0ec1d58582bd816012a8f9f1617eb4dfdeda1e93`. These facts support the accepted reconnect boundary in Q237-Q238 and support the accepted Q239-Q240 restoration workflow; they do not establish a working Conclave adapter.

### Identity and a pre-play task

Native `ServerLoginPacketListenerImpl.handleLoginAcknowledgement` passes its established profile through `CommonListenerCookie` to the configuration listener. `ServerConfigurationPacketListenerImpl.getOwner()` exposes that profile and UUID before native player creation. Identity assurance follows the server's own authentication policy. Later, `handleConfigurationFinished` performs admission checks and calls `PrepareSpawnTask.spawnPlayer`; `PrepareSpawnTask.Ready.spawn` creates the `ServerPlayer` and calls `PlayerList.placeNewPlayer`.

Fabric exposes `addTask` and `completeTask` during configuration. Completion advances the connection without requiring a successful resource result. Conclave could therefore record refusal, failure, or expiry and release its task without deliberately disconnecting the client. Completion must identify the active task; duplicate or late completion can throw. Conclave must separately cancel its work and reject stale results. See the [task API](https://github.com/FabricMC/fabric/blob/0ec1d58582bd816012a8f9f1617eb4dfdeda1e93/fabric-networking-api-v1/src/main/java/net/fabricmc/fabric/api/networking/v1/FabricServerConfigurationPacketListenerImpl.java#L27-L52) and [task implementation](https://github.com/FabricMC/fabric/blob/0ec1d58582bd816012a8f9f1617eb4dfdeda1e93/fabric-networking-api-v1/src/main/java/net/fabricmc/fabric/mixin/networking/ServerConfigurationPacketListenerImplMixin.java#L157-L176).

Configuration has registered payload channels in both directions. Its codecs use `FriendlyByteBuf`; the play-specific registry-aware buffer is unavailable there. The inspected Fabric source also supplies bounded large-payload registration with splitting. This provides transport, not file authorization, consent, installation, or readiness. Configuration callbacks run on network event loops, so Conclave game-state work must move to the server thread. See [configuration networking](https://github.com/FabricMC/fabric/blob/0ec1d58582bd816012a8f9f1617eb4dfdeda1e93/fabric-networking-api-v1/src/main/java/net/fabricmc/fabric/api/networking/v1/ServerConfigurationNetworking.java), [payload registration](https://github.com/FabricMC/fabric/blob/0ec1d58582bd816012a8f9f1617eb4dfdeda1e93/fabric-networking-api-v1/src/main/java/net/fabricmc/fabric/api/networking/v1/PayloadTypeRegistry.java#L48-L98), and [configuration callbacks](https://github.com/FabricMC/fabric/blob/0ec1d58582bd816012a8f9f1617eb4dfdeda1e93/fabric-networking-api-v1/src/main/java/net/fabricmc/fabric/api/networking/v1/ServerConfigurationConnectionEvents.java#L29-L63).

### Completion is not successful resource application

Native `ServerboundResourcePackPacket.Action.isTerminal()` includes failure and decline, excluding only accepted and downloaded intermediate states. The configuration resource-pack task can therefore finish without successful loading. `ServerCommonPacketListenerImpl.handleResourcePackResponse` disconnects on decline when the server requires its native resource pack. Directly relying on that required-pack path would conflict with Conclave's accepted no-kick policy; a custom transfer also needs explicit consent integration.

`ConfigurationTask.tick()` supplies no default automatic deadline. Normal keepalive and connection failures continue. Releasing one custom task does not cancel an in-flight client reload, finish other setup, guarantee native admission, or guarantee successful player placement. The accepted reconnect grace must be enforced by Conclave independently.

### The play JOIN callback is earlier than placement completion

Fabric documents `ServerPlayConnectionEvents.JOIN` as the point at which packets can be sent. Its pinned mixin runs inside `PlayerList.placeNewPlayer` at creation of the abilities packet. Native insertion into the player list/map and `ServerLevel.addNewPlayer` occurs later. An early JOIN callback or asset acknowledgement cannot alone prove completed world placement. See the [JOIN contract](https://github.com/FabricMC/fabric/blob/0ec1d58582bd816012a8f9f1617eb4dfdeda1e93/fabric-networking-api-v1/src/main/java/net/fabricmc/fabric/api/networking/v1/ServerPlayConnectionEvents.java#L41-L49) and [injection point](https://github.com/FabricMC/fabric/blob/0ec1d58582bd816012a8f9f1617eb4dfdeda1e93/fabric-networking-api-v1/src/main/java/net/fabricmc/fabric/mixin/networking/PlayerListMixin.java#L31-L36).

Inference: a bounded Conclave readiness task can prepare captured resources before creating an in-world player. Successful later placement, current resource verification, and server-owned admission still need to be integrated deliberately. No frozen or invulnerable avatar is required merely to perform this preparation. The exact adapter and failure paths have not been tested in a running client/server.

## Remaining implementation limits

These findings establish no resource-size budget, per-player pack assembler, universal mod-compatibility claim, complete private-information barrier, or automatic replay/recovery policy. Demand selection, cache ownership, application admission, exact acknowledgements, restored-client readiness, and the handling of an incompatible reload must be implemented and verified under their accepted Conclave contracts.
