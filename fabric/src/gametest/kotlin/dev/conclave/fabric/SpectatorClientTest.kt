package dev.conclave.fabric

import dev.conclave.core.*
import dev.conclave.fabric.client.*
import dev.conclave.storage.SavedPublication
import java.util.UUID
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.CameraType
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

class SpectatorClientTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        for (mode in SpectatorMode.entries) context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            context.runOnClient<RuntimeException> { it.options.pauseOnLostFocus = false }
            val viewer =
                world.server.computeOnServer<UUID, RuntimeException> {
                    world.connection.serverPlayer.uuid
                }
            val teammate = UUID.randomUUID()
            var previousDeath: NativeGraves.AdministrativeTarget? = null
            val definition = DefinitionId("local", "watch_fixture")
            val attempt =
                world.server.computeOnServer<UUID, RuntimeException> { server ->
                    val session = checkNotNull(ServerSession.get(server))
                    val player = checkNotNull(server.playerList.getPlayer(viewer))
                    player.setGameMode(GameType.SURVIVAL)
                    val feet = player.blockPosition().above(5)
                    for (x in -3..5) for (z in -3..3) {
                        player
                            .level()
                            .setBlockAndUpdate(
                                feet.offset(x, -1, z),
                                Blocks.STONE.defaultBlockState(),
                            )
                        for (y in 0..3) player
                            .level()
                            .setBlockAndUpdate(feet.offset(x, y, z), Blocks.AIR.defaultBlockState())
                    }
                    val origin = Vec3.atBottomCenterOf(feet)
                    player.teleportTo(origin.x, origin.y, origin.z)
                    val profile = com.mojang.authlib.GameProfile(teammate, "TeammateTest")
                    val cookie =
                        net.minecraft.server.network.CommonListenerCookie.createInitial(
                            profile,
                            false,
                        )
                    val target =
                        net.minecraft.server.level.ServerPlayer(
                            server,
                            player.level(),
                            profile,
                            cookie.clientInformation(),
                        )
                    val connection =
                        net.minecraft.network.Connection(
                            net.minecraft.network.protocol.PacketFlow.SERVERBOUND
                        )
                    io.netty.channel.embedded.EmbeddedChannel(connection)
                    server.playerList.placeNewPlayer(connection, target, cookie)
                    target.setGameMode(GameType.SURVIVAL)
                    target.teleportTo(origin.x + 2, origin.y, origin.z)
                    target.connection.handleAcceptPlayerLoad(
                        net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket()
                    )
                    val sources =
                        listOf(
                            SourceDocument(
                                "settings.yaml",
                                "schema: 1\nsettings:\n  revival: {combat_window: 1s}\n  spectating: {mode: ${mode.name.lowercase()}}",
                            ),
                            SourceDocument(
                                "encounter.yaml",
                                """
                                schema: 1
                                encounter:
                                  id: watch_fixture
                                  combat: true
                                  reconnect_grace: 10s
                                  participants: {area: entry}
                                  start: waiting
                                  recovery: {location: entrance}
                                  phases:
                                    - id: waiting
                                      objectives:
                                        - id: never_ready
                                          type: capture
                                          area: origin
                                          players_required: 3
                                          duration: 1s
                                      success: {complete: true}
                                """
                                    .trimIndent(),
                            ),
                            SourceDocument(
                                "arena.yaml",
                                """
                        schema: 1
                        arena:
                          id: watch_fixture
                          dimension: minecraft:overworld
                          boundary: {type: box, position: {x: ${origin.x}, y: ${origin.y}, z: ${origin.z}}, width: 12, depth: 8, height: 6}
                          areas:
                            - {id: entry, type: box, position: {x: ${origin.x}, y: ${origin.y}, z: ${origin.z}}, width: 10, depth: 6, height: 4}
                            - {id: origin, type: box, position: {x: ${origin.x}, y: ${origin.y}, z: ${origin.z}}, width: 1, depth: 1, height: 4}
                          locations:
                            - {id: entrance, position: {x: ${origin.x}, y: ${origin.y}, z: ${origin.z}}}
                          encounters: [{encounter: watch_fixture}]
                    """
                                    .trimIndent(),
                            ),
                        )
                    val validation = CatalogCompiler().compile(sources)
                    check(validation is Validation.Valid) { "Invalid camera fixture: $validation" }
                    check(
                        session.content
                            .publish(UUID.randomUUID(), "camera_fixture", null, validation.value)
                            .join() is SavedPublication.Published
                    )
                    session.installPublished(validation.value)
                    checkNotNull(
                        session.runtime.start(definition, definition, "camera_fixture", { true }) {
                            success,
                            message ->
                            check(success) { message }
                        }
                    )
                }
            world.server.waitFor { server ->
                checkNotNull(ServerSession.get(server)).runtime.views().any {
                    it.id == attempt && it.state == "running"
                } && checkNotNull(server.playerList.getPlayer(viewer)).connection.hasClientLoaded()
            }
            world.server.runOnServer<RuntimeException> { server ->
                val player = checkNotNull(server.playerList.getPlayer(viewer))
                player.hurtServer(
                    player.level(),
                    player.damageSources().genericKill(),
                    Float.MAX_VALUE,
                )
                check(!player.isAlive)
            }
            context.waitTicks(30)
            world.server.runOnServer<RuntimeException> { server ->
                val session = checkNotNull(ServerSession.get(server))
                val player = checkNotNull(server.playerList.getPlayer(viewer))
                check(session.graves.view(viewer)?.status == GraveStatus.PASSED_OUT) {
                    "Native grave did not expire: ${session.graves.view(viewer)}; ${session.runtime.views()}"
                }
                check(
                    if (mode == SpectatorMode.TEAMMATES) player.camera.uuid == teammate
                    else player.camera === player
                ) {
                    "Native camera did not select teammate: ${player.camera.uuid}; ${session.runtime.deathContext(viewer)?.world?.players()?.byId}"
                }
            }
            context.runOnClient<RuntimeException> { client ->
                check(
                    if (mode == SpectatorMode.TEAMMATES) SpectatorClient.state?.target == teammate
                    else SpectatorClient.state?.free == true
                ) {
                    "Missing client watch state: ${SpectatorClient.state}; grave ${GraveClient.state}"
                }
                check(
                    if (mode == SpectatorMode.TEAMMATES) client.cameraEntity?.uuid == teammate
                    else client.cameraEntity === client.player
                ) {
                    "Client camera mismatch: ${client.cameraEntity?.uuid}; tracked ${client.level?.players()?.map { it.uuid }}; grave ${GraveClient.state}"
                }
            }
            context.runOnClient<RuntimeException> { client ->
                check(client.options.cameraType == CameraType.FIRST_PERSON)
                check(
                    GraveClient.state?.status == GraveStatus.PASSED_OUT &&
                        client.player?.isSpectator == true
                )
            }
            context.takeScreenshot("conclave-${mode.name.lowercase()}-view")
            if (mode == SpectatorMode.FREE) {
                val before =
                    world.server.computeOnServer<Vec3, RuntimeException> { server ->
                        checkNotNull(server.playerList.getPlayer(viewer)).position()
                    }
                context.input.holdKeyFor({ it.keyUp }, 6)
                world.server.waitFor { server ->
                    checkNotNull(server.playerList.getPlayer(viewer))
                        .position()
                        .distanceToSqr(before) > 0.01
                }
                context.input.pressKey { SpectatorClient.controls }
                context.waitTicks(2)
                context.runOnClient<RuntimeException> { client ->
                    check(client.gui.screen() is SpectatorScreen) {
                        "Spectator key did not open picker: screen=${client.gui.screen()}; state=${SpectatorClient.state}; grave=${GraveClient.state}; focus=${client.isWindowActive}"
                    }
                }
            } else context.clickScreenButton("Choose teammate")
            context.waitForScreen(SpectatorScreen::class.java)
            context.clickScreenButton("TeammateTest")
            world.server.waitFor { server ->
                server.playerList.getPlayer(viewer)?.camera?.uuid == teammate
            }
            world.server.runOnServer<RuntimeException> { server ->
                val session = checkNotNull(ServerSession.get(server))
                val player = checkNotNull(server.playerList.getPlayer(viewer))
                val target = checkNotNull(server.playerList.getPlayer(teammate))
                check(
                    player.camera === target &&
                        player.isSpectator &&
                        session.graves.life(viewer) == LifeState.PASSED_OUT
                )
                if (mode == SpectatorMode.TEAMMATES) {
                    player.setCamera(player)
                    check(player.camera === target) {
                        "Native spectator input bypassed the constrained teammate camera"
                    }
                }
                session.spectating.accept(
                    player,
                    WatchInputPayload(attempt, WatchAction.SELECT, UUID.randomUUID()),
                )
                check(player.camera === target)
                target.teleportTo(target.x + 2, target.y, target.z)
            }
            world.server.waitFor { server ->
                val player = checkNotNull(server.playerList.getPlayer(viewer))
                val target = checkNotNull(server.playerList.getPlayer(teammate))
                player.position().distanceToSqr(target.position()) < 0.1
            }
            world.server.runOnServer<RuntimeException> { server ->
                val session = checkNotNull(ServerSession.get(server))
                val frame = checkNotNull(session.runtime.deathContext(viewer)).world.observe()
                check("origin" in checkNotNull(frame.byId[viewer]).areas) {
                    "Camera transport changed gameplay area membership"
                }
                check(checkNotNull(frame.byId[viewer]).life == LifeState.PASSED_OUT)
                val player = checkNotNull(server.playerList.getPlayer(viewer))
                val other = checkNotNull(server.playerList.getPlayer(teammate))
                // A player who lacks GM authority cannot use the native command.
                server.commands.performPrefixedCommand(
                    other.createCommandSourceStack(),
                    "conclave revive $attempt $viewer",
                )
                check(session.graves.life(viewer) == LifeState.PASSED_OUT)
                check(session.graves.administrativeTarget(UUID.randomUUID(), player) == null)
                session.setGameMaster("client_test", teammate, true) { granted ->
                    check(granted)
                    server.commands.performPrefixedCommand(
                        other.createCommandSourceStack(),
                        "conclave revive $attempt $viewer",
                    )
                    session.setGameMaster("client_test", teammate, false) { revoked ->
                        check(revoked)
                    }
                }
            }
            world.server.waitFor { server ->
                checkNotNull(ServerSession.get(server)).authority.audit().join().any {
                    it.operation == "player_revive" && it.result == "authority_changed"
                }
            }
            world.server.runOnServer<RuntimeException> { server ->
                val session = checkNotNull(ServerSession.get(server))
                val player = checkNotNull(server.playerList.getPlayer(viewer))
                check(session.graves.life(viewer) == LifeState.PASSED_OUT) {
                    "Revoked GM authority still revived the target"
                }
                previousDeath = checkNotNull(session.graves.administrativeTarget(attempt, player))
                server.commands.performPrefixedCommand(
                    server.createCommandSourceStack(),
                    "conclave revive $attempt $viewer",
                )
            }
            context.waitFor { client ->
                SpectatorClient.state == null &&
                    GraveClient.state == null &&
                    client.player?.isAlive == true &&
                    client.cameraEntity === client.player
            }
            world.server.runOnServer<RuntimeException> { server ->
                val session = checkNotNull(ServerSession.get(server))
                val player = checkNotNull(server.playerList.getPlayer(viewer))
                val observed =
                    checkNotNull(session.runtime.deathContext(viewer))
                        .world
                        .observe()
                        .byId
                        .getValue(viewer)
                check(session.runtime.views().any { it.id == attempt && it.state == "running" })
                check(
                    observed.life == LifeState.ALIVE &&
                        observed.participation == Participation.ACTIVE
                )
                check("origin" in observed.areas && player.health == player.maxHealth)
                check(player.gameMode.gameModeForPlayer == GameType.SURVIVAL)
                check((player as ConclavePlayerViewing).conclaveViewing() == null)
                check(session.graves.administrativeTarget(attempt, player) == null)
                check(!session.graves.reviveAdministrative(checkNotNull(previousDeath)).revived)
                check(session.runtime.stop(attempt))
            }
            world.server.waitFor { server ->
                checkNotNull(ServerSession.get(server)).runtime.views().none { it.id == attempt }
            }
            world.server.waitFor { server ->
                val recovery = checkNotNull(ServerSession.get(server)).recovery
                !recovery.blocksRespawn(viewer) && !recovery.blocksRespawn(teammate)
            }
            world.server.runOnServer<RuntimeException> { server ->
                val player = checkNotNull(server.playerList.getPlayer(viewer))
                check(
                    player.gameMode.gameModeForPlayer == GameType.SURVIVAL &&
                        (player as ConclavePlayerViewing).conclaveViewing() == null
                )
            }
            val observerAttempt =
                world.server.computeOnServer<UUID, RuntimeException> { server ->
                    checkNotNull(
                        checkNotNull(ServerSession.get(server)).runtime.start(
                            definition,
                            definition,
                            "observer_fixture",
                            { true },
                        ) { success, message ->
                            check(success) { message }
                        }
                    )
                }
            world.server.waitFor { server ->
                checkNotNull(ServerSession.get(server)).runtime.views().any {
                    it.id == observerAttempt && it.state == "running"
                }
            }
            val origin =
                world.server.computeOnServer<Vec3, RuntimeException> { server ->
                    val session = checkNotNull(ServerSession.get(server))
                    val old = checkNotNull(server.playerList.getPlayer(teammate))
                    val attemptWorld = checkNotNull(session.runtime.deathContext(teammate)).world
                    // A real native removal, saved player file, fresh connection and placement.
                    attemptWorld.disconnected(old, System.nanoTime() - 11_000_000_000L)
                    server.playerList.remove(old)
                    attemptWorld.observe()
                    val cookie =
                        net.minecraft.server.network.CommonListenerCookie.createInitial(
                            old.gameProfile,
                            false,
                        )
                    val returned =
                        net.minecraft.server.level.ServerPlayer(
                            server,
                            old.level(),
                            old.gameProfile,
                            cookie.clientInformation(),
                        )
                    returned.load(
                        net.minecraft.world.level.storage.TagValueInput.create(
                            net.minecraft.util.ProblemReporter.DISCARDING,
                            server.registryAccess(),
                            server.playerList
                                .loadPlayerData(
                                    net.minecraft.server.players.NameAndId(old.gameProfile)
                                )
                                .orElseThrow(),
                        )
                    )
                    val connection =
                        net.minecraft.network.Connection(
                            net.minecraft.network.protocol.PacketFlow.SERVERBOUND
                        )
                    io.netty.channel.embedded.EmbeddedChannel(connection)
                    server.playerList.placeNewPlayer(connection, returned, cookie)
                    returned.connection.handleAcceptPlayerLoad(
                        net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket()
                    )
                    check(
                        returned.isAlive && returned.gameMode.gameModeForPlayer == GameType.SURVIVAL
                    )
                    val frame = attemptWorld.observe().byId.getValue(teammate)
                    check(
                        frame.life == LifeState.ALIVE &&
                            frame.participation == Participation.OBSERVER
                    )
                    returned.inventory.setItem(
                        0,
                        net.minecraft.world.item.ItemStack(
                            net.minecraft.world.item.Items.DIAMOND,
                            7,
                        ),
                    )
                    returned.health = 9f
                    val position = returned.position()
                    session.spectating.tickObservers()
                    check(!returned.isSpectator && session.graves.view(teammate) == null)
                    session.spectating.accept(
                        returned,
                        WatchInputPayload(observerAttempt, WatchAction.WATCH),
                    )
                    check(returned.isSpectator && session.graves.life(teammate) == LifeState.ALIVE)
                    if (mode == SpectatorMode.TEAMMATES) check(returned.camera.uuid == viewer)
                    else {
                        check(returned.camera === returned)
                        session.spectating.accept(
                            returned,
                            WatchInputPayload(observerAttempt, WatchAction.SELECT, viewer),
                        )
                    }
                    check(returned.camera.uuid == viewer)
                    check(
                        attemptWorld.observe().byId.getValue(teammate).participation ==
                            Participation.OBSERVER
                    )
                    session.spectating.accept(
                        returned,
                        WatchInputPayload(observerAttempt, WatchAction.LEAVE),
                    )
                    position
                }
            world.server.waitFor { server ->
                (server.playerList.getPlayer(teammate) as? ConclavePlayerViewing)
                    ?.conclaveViewing() == null
            }
            world.server.runOnServer<RuntimeException> { server ->
                val session = checkNotNull(ServerSession.get(server))
                val observer = checkNotNull(server.playerList.getPlayer(teammate))
                check(
                    observer.gameMode.gameModeForPlayer == GameType.SURVIVAL &&
                        observer.camera === observer
                )
                check(
                    observer.position().distanceToSqr(origin) < 0.01 &&
                        observer.health == 9f &&
                        observer.inventory.getItem(0).count == 7
                )
                check(
                    session.graves.view(teammate) == null &&
                        checkNotNull(session.runtime.deathContext(teammate))
                            .world
                            .observe()
                            .byId
                            .getValue(teammate)
                            .participation == Participation.OBSERVER
                )
                check(session.runtime.stop(observerAttempt))
            }
            world.server.waitFor { server ->
                checkNotNull(ServerSession.get(server)).runtime.views().none {
                    it.id == observerAttempt
                }
            }
        }
    }
}
