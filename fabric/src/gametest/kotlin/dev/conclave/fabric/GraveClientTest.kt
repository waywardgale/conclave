package dev.conclave.fabric

import dev.conclave.core.*
import dev.conclave.fabric.client.GraveClient
import dev.conclave.fabric.client.GraveScreen
import dev.conclave.fabric.client.ProtectionClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.CameraType
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

class GraveClientTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            val id =
                world.server.computeOnServer<java.util.UUID, RuntimeException> {
                    world.connection.serverPlayer.uuid
                }
            world.server.runOnServer<RuntimeException> { server ->
                val session = checkNotNull(ServerSession.get(server))
                val catalog =
                    (CatalogCompiler()
                            .compile(
                                listOf(
                                    SourceDocument(
                                        "settings.yaml",
                                        "schema: 1\nsettings:\n  revival: {self_revival: true, health: 50%, damage_protection: 20s}",
                                    )
                                )
                            ) as Validation.Valid)
                        .value
                session.installPublished(catalog)
                val player = checkNotNull(server.playerList.getPlayer(id))
                player.setGameMode(GameType.SURVIVAL)
                val feet = player.blockPosition().above(5)
                for (x in -2..2) for (z in -2..2) {
                    player
                        .level()
                        .setBlockAndUpdate(feet.offset(x, -1, z), Blocks.STONE.defaultBlockState())
                    for (y in 0..3) player
                        .level()
                        .setBlockAndUpdate(feet.offset(x, y, z), Blocks.AIR.defaultBlockState())
                }
                player.teleportTo(feet.x + 0.5, feet.y.toDouble(), feet.z + 0.5)
            }
            world.connection.waitForChunksRender()
            world.server.waitFor { server ->
                val player = checkNotNull(server.playerList.getPlayer(id))
                player.connection.hasClientLoaded() && !player.isChangingDimension
            }
            world.server.runOnServer<RuntimeException> { server ->
                val session = checkNotNull(ServerSession.get(server))
                val player = checkNotNull(server.playerList.getPlayer(id))
                player.hurtServer(
                    player.level(),
                    player.damageSources().genericKill(),
                    Float.MAX_VALUE,
                )
                check(!player.isAlive)
                // Changing the currently published policy cannot rewrite this outside death.
                session.installPublished(
                    (CatalogCompiler()
                            .compile(
                                listOf(
                                    SourceDocument(
                                        "settings.yaml",
                                        "schema: 1\nsettings:\n  revival: {self_revival: false, health: 100%}",
                                    )
                                )
                            ) as Validation.Valid)
                        .value
                )
            }
            context.waitForScreen(GraveScreen::class.java)
            context.waitFor { client ->
                GraveClient.state?.message == "" && client.cameraEntity !== client.player
            }
            context.runOnClient<RuntimeException> { client ->
                check(client.options.cameraType == CameraType.THIRD_PERSON_BACK)
                check(client.player?.isSpectator == false)
                check(GraveClient.state?.selfAllowed == true)
                GraveClient.turn(80.0, 25.0)
            }
            context.takeScreenshot("conclave-grave-camera")
            context.clickScreenButton("Revive here")
            context.waitFor { client ->
                GraveClient.state == null &&
                    client.player?.isAlive == true &&
                    client.gui.screen() !is GraveScreen &&
                    client.cameraEntity === client.player
            }
            world.server.runOnServer<RuntimeException> { server ->
                val player = checkNotNull(server.playerList.getPlayer(id))
                check(player.health == player.maxHealth / 2)
                check(NativeRevivalProtection.remaining(player) in 1..400) {
                    "The captured positive protection was not granted"
                }
            }
            world.connection.waitForChunksRender()
            context.waitFor { client ->
                ProtectionClient.remaining > 0 && client.gui.screen() == null
            }
            context.takeScreenshot("conclave-revival-protection")
            world.server.waitFor { server ->
                checkNotNull(server.playerList.getPlayer(id)).connection.hasClientLoaded()
            }
            val targetId = java.util.UUID.randomUUID()
            val aim =
                world.server.computeOnServer<Vec3, RuntimeException> { server ->
                    val session = checkNotNull(ServerSession.get(server))
                    session.installPublished(
                        (CatalogCompiler()
                                .compile(
                                    listOf(
                                        SourceDocument(
                                            "settings.yaml",
                                            "schema: 1\nsettings:\n  revival: {help_time: 2s}",
                                        )
                                    )
                                ) as Validation.Valid)
                            .value
                    )
                    val helper = checkNotNull(server.playerList.getPlayer(id))
                    val position = helper.position().add(2.0, 0.0, 0.0)
                    val profile = com.mojang.authlib.GameProfile(targetId, "RevivalTest")
                    val cookie =
                        net.minecraft.server.network.CommonListenerCookie.createInitial(
                            profile,
                            false,
                        )
                    val target =
                        net.minecraft.server.level.ServerPlayer(
                            server,
                            helper.level(),
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
                    target.teleportTo(position.x, position.y, position.z)
                    target.connection.handleAcceptPlayerLoad(
                        net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket()
                    )
                    target.hurtServer(
                        target.level(),
                        target.damageSources().genericKill(),
                        Float.MAX_VALUE,
                    )
                    check(!target.isAlive)
                    position.add(0.0, 0.45, 0.0)
                }
            val rotation =
                context.computeOnClient<Pair<Float, Float>, RuntimeException> { client ->
                    val delta = aim.subtract(checkNotNull(client.player).eyePosition)
                    Math.toDegrees(kotlin.math.atan2(-delta.x, delta.z)).toFloat() to
                        -Math.toDegrees(
                                kotlin.math.atan2(delta.y, kotlin.math.hypot(delta.x, delta.z))
                            )
                            .toFloat()
                }
            context.input.lookAt(rotation.first, rotation.second)
            context.waitFor { GraveClient.help?.name == "RevivalTest" }
            context.takeScreenshot("conclave-help-prompt")
            context.input.holdKey { it.keyUse }
            context.waitFor {
                GraveClient.help?.holding == true && checkNotNull(GraveClient.help).progress >= 5
            }
            context.takeScreenshot("conclave-help-progress")
            // Releasing after partial progress must interrupt instead of finishing in the
            // background.
            context.input.releaseKey { it.keyUse }
            context.waitFor { GraveClient.help?.holding == false }
            world.server.runOnServer<RuntimeException> { server ->
                check(!checkNotNull(server.playerList.getPlayer(targetId)).isAlive)
            }
            context.input.holdKey { it.keyUse }
            world.server.waitFor { server ->
                server.playerList.getPlayer(targetId)?.isAlive == true
            }
            context.input.releaseKey { it.keyUse }
            world.server.runOnServer<RuntimeException> { server ->
                check(checkNotNull(ServerSession.get(server)).graves.view(targetId) == null)
                server.playerList.remove(checkNotNull(server.playerList.getPlayer(targetId)))
                val player = checkNotNull(server.playerList.getPlayer(id))
                check(NativeRevivalProtection.remaining(player) > 0) {
                    "Helping someone revive ended protection"
                }
                server.commands.performPrefixedCommand(
                    server.createCommandSourceStack(),
                    "kill $id",
                )
                check(!player.isAlive)
            }
            context.waitForScreen(GraveScreen::class.java)
            context.runOnClient<RuntimeException> { client ->
                check(
                    (client.gui.screen() as GraveScreen)
                        .children()
                        .filterIsInstance<net.minecraft.client.gui.components.Button>()
                        .none { it.visible && it.message.string == "Revive here" }
                )
            }
            context.clickScreenButton("Respawn")
            context.waitFor { client ->
                GraveClient.state == null &&
                    client.player?.isAlive == true &&
                    client.gui.screen() !is GraveScreen
            }
        }
    }
}
