package dev.conclave.fabric

import dev.conclave.core.*
import dev.conclave.fabric.client.InteractionClient
import java.util.UUID
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

class InteractionClientTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            val id =
                world.server.computeOnServer<UUID, RuntimeException> {
                    world.connection.serverPlayer.uuid
                }
            val block =
                world.server.computeOnServer<BlockPos, RuntimeException> { server ->
                    val player = checkNotNull(server.playerList.getPlayer(id))
                    player.setGameMode(GameType.SURVIVAL)
                    val feet = player.blockPosition().above(5)
                    for (x in -3..3) for (z in -3..4) {
                        player
                            .level()
                            .setBlockAndUpdate(
                                feet.offset(x, -1, z),
                                Blocks.STONE.defaultBlockState(),
                            )
                        for (y in 0..4) player
                            .level()
                            .setBlockAndUpdate(feet.offset(x, y, z), Blocks.AIR.defaultBlockState())
                    }
                    player.teleportTo(feet.x + 0.5, feet.y.toDouble(), feet.z + 0.5)
                    val block = feet.offset(0, 1, 2)
                    player.level().setBlockAndUpdate(block, Blocks.CHEST.defaultBlockState())
                    val session = checkNotNull(ServerSession.get(server))
                    val sources =
                        listOf(
                            SourceDocument(
                                "encounter.yaml",
                                """
                                schema: 1
                                encounter:
                                  id: use_test
                                  participants: {area: entry}
                                  recovery: {location: entrance}
                                  start: together
                                  phases:
                                    - id: together
                                      objectives:
                                        - {id: first, type: interact, targets: [{block: console}], consume_interaction: true}
                                        - {id: second, type: interact, targets: [{block: alias}]}
                                      success: {next: holding}
                                    - id: holding
                                      objectives:
                                        - id: held
                                          type: interact
                                          targets: [{block: console}]
                                          hold: 2s
                                          interrupt_on_damage: true
                                          consume_interaction: true
                                      success: {next: ordinary}
                                    - id: ordinary
                                      objectives:
                                        - {id: open, type: interact, targets: [{block: console}]}
                                      success: {next: waiting}
                                    - id: waiting
                                      success: {complete: true}
                                      objectives:
                                        - {id: background, type: interact, targets: [{block: console}], hold: 2s}
                                """
                                    .trimIndent(),
                            ),
                            SourceDocument(
                                "arena.yaml",
                                """
                        schema: 1
                        arena:
                          id: use_test
                          dimension: minecraft:overworld
                          boundary: {type: box, position: {x: ${feet.x + 0.5}, y: ${feet.y}, z: ${feet.z + 0.5}}, width: 8, depth: 10, height: 6}
                          areas:
                            - {id: entry, type: box, position: {x: ${feet.x + 0.5}, y: ${feet.y}, z: ${feet.z + 0.5}}, width: 4, depth: 6, height: 5}
                          locations:
                            - {id: entrance, position: {x: ${feet.x + 0.5}, y: ${feet.y}, z: ${feet.z + 0.5}}}
                            - {id: actual_console, position: {x: ${block.x + 0.5}, y: ${block.y + 0.5}, z: ${block.z + 0.5}}}
                          encounters:
                            - encounter: use_test
                              bindings:
                                locations: {console: actual_console, alias: actual_console}
                        """
                                    .trimIndent(),
                            ),
                        )
                    val validation = CatalogCompiler().compile(sources)
                    check(validation is Validation.Valid) { validation.toString() }
                    session.installPublished(validation.value)
                    block
                }
            world.connection.waitForChunksRender()
            world.server.waitFor { server ->
                val player = checkNotNull(server.playerList.getPlayer(id))
                player.connection.hasClientLoaded() && !player.isChangingDimension
            }
            val attempt =
                world.server.computeOnServer<UUID, RuntimeException> { server ->
                    checkNotNull(
                        checkNotNull(ServerSession.get(server)).runtime.start(
                            DefinitionId("local", "use_test"),
                            DefinitionId("local", "use_test"),
                            "client test",
                            { true },
                        ) { success, message ->
                            check(success) { message }
                        }
                    )
                }
            fun phase(server: net.minecraft.server.MinecraftServer): String? =
                (ServerSession.get(server)
                        ?.runtime
                        ?.interaction(checkNotNull(server.playerList.getPlayer(id)))
                        ?.engine
                        ?.state as? ProgressionState.Running)
                    ?.activation
                    ?.phase
            fun waitTicks(count: Int) {
                val until =
                    world.server.computeOnServer<Int, RuntimeException> { it.tickCount + count }
                world.server.waitFor { it.tickCount >= until }
            }
            context.waitFor { client ->
                client.player
                    ?.position()
                    ?.distanceToSqr(Vec3(block.x + 0.5, block.y - 1.0, block.z - 1.5))
                    ?.let { it < 0.001 } == true
            }
            val rotation =
                context.computeOnClient<Pair<Float, Float>, RuntimeException> { client ->
                    val delta =
                        Vec3.atCenterOf(block).subtract(checkNotNull(client.player).eyePosition)
                    Math.toDegrees(kotlin.math.atan2(-delta.x, delta.z)).toFloat() to
                        -Math.toDegrees(
                                kotlin.math.atan2(delta.y, kotlin.math.hypot(delta.x, delta.z))
                            )
                            .toFloat()
                }
            context.input.lookAt(rotation.first, rotation.second)
            waitTicks(6)
            context.runOnClient<RuntimeException> { client ->
                check((client.hitResult as? BlockHitResult)?.blockPos == block) {
                    "Client aim missed: ${(client.hitResult as? BlockHitResult)?.blockPos}, player ${client.player?.position()}, rotation ${client.player?.yRot}, ${client.player?.xRot}, expected $block"
                }
            }
            world.server.runOnServer<RuntimeException> { server ->
                val player = checkNotNull(server.playerList.getPlayer(id))
                val runtime =
                    checkNotNull(ServerSession.get(server)?.runtime?.interaction(player)) {
                        "No running attempt"
                    }
                check(NativeBlockTargets.aimed(player, block)) {
                    "Server aim missed at ${player.position()}, rotation ${player.yRot}, ${player.xRot}, target $block"
                }
                val targets = runtime.world.blocks.handles(player.level(), block)
                check(runtime.engine.interactionClaims(id, UUID.randomUUID(), targets).size == 2) {
                    "Initial matching recipients are absent: $targets, ${runtime.world.players().byId[id]}, ${runtime.engine.state}"
                }
            }
            context.waitFor {
                InteractionClient.offer?.position == block &&
                    InteractionClient.offer?.consume == true
            }
            val firstOffer =
                context.computeOnClient<InteractionOfferPayload, RuntimeException> {
                    checkNotNull(InteractionClient.offer)
                }
            // A valid custom press by itself cannot bypass cancellation in the actual native use
            // path.
            context.runOnClient<RuntimeException> {
                ClientPlayNetworking.send(
                    InteractionInputPayload(InteractionAction.PRESS, block, 1, firstOffer.offer)
                )
            }
            waitTicks(8)
            world.server.runOnServer<RuntimeException> { check(phase(it) == "together") }
            context.runOnClient<RuntimeException> {
                ClientPlayNetworking.send(
                    InteractionInputPayload(InteractionAction.RELEASE, block, 1, firstOffer.offer)
                )
            }
            context.waitFor {
                InteractionClient.offer?.offer != null &&
                    InteractionClient.offer?.offer != firstOffer.offer
            }
            // Beginning Use before aiming at a mechanic cannot later become a Conclave press.
            context.input.lookAt(rotation.first + 180f, rotation.second)
            context.input.holdKey { it.keyUse }
            waitTicks(5)
            context.input.lookAt(rotation.first, rotation.second)
            waitTicks(8)
            world.server.runOnServer<RuntimeException> { check(phase(it) == "together") }
            context.input.releaseKey { it.keyUse }
            context.runOnClient<RuntimeException> { checkNotNull(it.player).closeContainer() }
            context.waitTicks(2)
            context.waitFor { InteractionClient.offer?.consume == true }
            context.input.holdKey { it.keyUse }
            world.server.waitFor { phase(it) == "holding" }
            waitTicks(45)
            world.server.runOnServer<RuntimeException> { server ->
                check(phase(server) == "holding") { "Held input adopted the new phase" }
                val player = checkNotNull(server.playerList.getPlayer(id))
                val result =
                    player.gameMode.useItemOn(
                        player,
                        player.level(),
                        player.offhandItem,
                        InteractionHand.OFF_HAND,
                        BlockHitResult(Vec3.atCenterOf(block), Direction.NORTH, block, false),
                    )
                check(result.consumesAction()) { "Offhand repeat leaked through consumption" }
                check(player.containerMenu === player.inventoryMenu) {
                    "Consumed input opened the chest"
                }
                player.setItemInHand(
                    InteractionHand.OFF_HAND,
                    net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE),
                )
                val itemResult =
                    player.gameMode.useItem(
                        player,
                        player.level(),
                        player.offhandItem,
                        InteractionHand.OFF_HAND,
                    )
                check(itemResult.consumesAction() && !player.isUsingItem) {
                    "Consumed gesture leaked a held-item use"
                }
                player.setItemInHand(
                    InteractionHand.OFF_HAND,
                    net.minecraft.world.item.ItemStack.EMPTY,
                )
            }
            context.input.releaseKey { it.keyUse }
            context.waitFor { InteractionClient.offer?.hold == true }
            context.takeScreenshot("conclave-block-interaction")

            // Release loses all partial progress.
            context.input.holdKey { it.keyUse }
            context.waitFor {
                InteractionClient.progress?.holding == true &&
                    checkNotNull(InteractionClient.progress).progress >= 4
            }
            context.takeScreenshot("conclave-block-hold-progress")
            context.input.releaseKey { it.keyUse }
            waitTicks(45)
            world.server.runOnServer<RuntimeException> { check(phase(it) == "holding") }
            context.waitFor { InteractionClient.offer?.hold == true }

            // Replacing and restoring the same block inside one server turn changes its generation.
            context.input.holdKey { it.keyUse }
            context.waitFor {
                InteractionClient.progress?.holding == true &&
                    checkNotNull(InteractionClient.progress).progress >= 4
            }
            world.server.runOnServer<RuntimeException> { server ->
                val level = checkNotNull(server.playerList.getPlayer(id)).level()
                level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState())
                level.setBlockAndUpdate(block, Blocks.CHEST.defaultBlockState())
            }
            waitTicks(45)
            world.server.runOnServer<RuntimeException> {
                check(phase(it) == "holding") { "A hold survived block replacement" }
            }
            context.input.releaseKey { it.keyUse }
            context.waitFor { InteractionClient.offer?.hold == true }

            context.input.holdKey { it.keyUse }
            context.waitFor {
                InteractionClient.progress?.holding == true &&
                    checkNotNull(InteractionClient.progress).progress >= 4
            }
            world.server.runOnServer<RuntimeException> { server ->
                val player = checkNotNull(server.playerList.getPlayer(id))
                check(player.hurtServer(player.level(), player.damageSources().generic(), 2f))
            }
            waitTicks(45)
            world.server.runOnServer<RuntimeException> {
                check(phase(it) == "holding") { "Damage failed to interrupt the hold" }
            }
            context.input.releaseKey { it.keyUse }
            context.waitFor { InteractionClient.offer?.hold == true }

            context.input.holdKey { it.keyUse }
            world.server.waitFor { phase(it) == "ordinary" }
            waitTicks(10)
            world.server.runOnServer<RuntimeException> { server ->
                check(phase(server) == "ordinary")
                val player = checkNotNull(server.playerList.getPlayer(id))
                check(player.containerMenu === player.inventoryMenu)
            }
            context.input.releaseKey { it.keyUse }
            context.waitFor {
                InteractionClient.offer?.consume == false && InteractionClient.offer?.hold == false
            }
            context.input.holdKey { it.keyUse }
            context.waitForScreen(ContainerScreen::class.java)
            world.server.waitFor { phase(it) == "waiting" }
            context.input.releaseKey { it.keyUse }
            context.runOnClient<RuntimeException> { checkNotNull(it.player).closeContainer() }
            context.waitFor {
                InteractionClient.offer?.hold == true && InteractionClient.offer?.consume == false
            }
            context.input.holdKey { it.keyUse }
            context.waitForScreen(ContainerScreen::class.java)
            context.input.releaseKey { it.keyUse }
            waitTicks(45)
            world.server.runOnServer<RuntimeException> {
                check(phase(it) == "waiting") {
                    "The chest screen did not interrupt pass-through holding"
                }
            }
            context.runOnClient<RuntimeException> { checkNotNull(it.player).closeContainer() }
            world.server.runOnServer<RuntimeException> {
                check(checkNotNull(ServerSession.get(it)).runtime.stop(attempt))
            }
            world.server.waitFor {
                ServerSession.get(it)?.runtime?.views()?.none { view -> view.id == attempt } == true
            }
        }
    }
}
