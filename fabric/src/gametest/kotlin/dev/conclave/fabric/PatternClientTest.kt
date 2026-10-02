package dev.conclave.fabric

import dev.conclave.core.*
import dev.conclave.fabric.client.InteractionClient
import java.util.UUID
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

class PatternClientTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            val id =
                world.server.computeOnServer<UUID, RuntimeException> {
                    world.connection.serverPlayer.uuid
                }
            val feet =
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
                    player
                        .level()
                        .setBlockAndUpdate(feet.offset(-1, 1, 2), Blocks.CHEST.defaultBlockState())
                    player
                        .level()
                        .setBlockAndUpdate(feet.offset(1, 1, 2), Blocks.CHEST.defaultBlockState())
                    val sources =
                        listOf(
                            SourceDocument(
                                "encounter.yaml",
                                """
                                schema: 1
                                encounter:
                                  id: pattern_test
                                  participants: {area: entry}
                                  recovery: {location: entrance}
                                  start: pattern
                                  phases:
                                    - id: pattern
                                      counters: [{id: mistakes}]
                                      objectives:
                                        - id: puzzle
                                          type: match_pattern
                                          tokens: [sun, moon]
                                          pattern: [sun, moon]
                                          inputs:
                                            - {token: sun, targets: [{block: left}], hold: 1s, consume_interaction: true}
                                            - {token: moon, targets: [{block: right}], consume_interaction: true}
                                      rules:
                                        - id: mistake
                                          on: {source: {mechanic: puzzle}, event: mismatched}
                                          do: [{add_counter: {counter: mistakes, value: 1}}]
                                      success:
                                        choose:
                                          - if:
                                              and:
                                                - counter: {id: mistakes, equals: 3}
                                                - pattern_state: {mechanic: puzzle, progress: {equals: 100%}, completed: true}
                                            next: authored
                                        otherwise: {next: unexpected}
                                    - id: authored
                                      objectives:
                                        - {id: puzzle, type: match_pattern, tokens: [sun], pattern: [sun]}
                                      mechanics:
                                        - {id: button, type: interact, targets: [{block: right}], consume_interaction: true}
                                      rules:
                                        - id: supply
                                          on: {source: {mechanic: button}, event: used}
                                          do:
                                            - {submit_token: {mechanic: puzzle, token: sun, player: {event: player}}}
                                            - {reset_pattern: {mechanic: puzzle}}
                                      success: {next: ordinary}
                                    - id: ordinary
                                      objectives:
                                        - id: puzzle
                                          type: match_pattern
                                          tokens: [moon]
                                          pattern: [moon]
                                          inputs: [{token: moon, targets: [{block: right}]}]
                                      success: {next: waiting}
                                    - id: waiting
                                      objectives:
                                        - {id: exit, type: interact, targets: [{block: left}]}
                                      success: {complete: true}
                                    - id: unexpected
                                      objectives:
                                        - {id: exit, type: interact, targets: [{block: left}]}
                                      success: {complete: true}
                                """
                                    .trimIndent(),
                            ),
                            SourceDocument(
                                "arena.yaml",
                                """
                        schema: 1
                        arena:
                          id: pattern_test
                          dimension: minecraft:overworld
                          boundary: {type: box, position: {x: ${feet.x + 0.5}, y: ${feet.y}, z: ${feet.z + 0.5}}, width: 8, depth: 10, height: 6}
                          areas:
                            - {id: entry, type: box, position: {x: ${feet.x + 0.5}, y: ${feet.y}, z: ${feet.z + 0.5}}, width: 4, depth: 6, height: 5}
                          locations:
                            - {id: entrance, position: {x: ${feet.x + 0.5}, y: ${feet.y}, z: ${feet.z + 0.5}}}
                            - {id: left, position: {x: ${feet.x - 0.5}, y: ${feet.y + 1.5}, z: ${feet.z + 2.5}}}
                            - {id: right, position: {x: ${feet.x + 1.5}, y: ${feet.y + 1.5}, z: ${feet.z + 2.5}}}
                          encounters: [{encounter: pattern_test}]
                    """
                                    .trimIndent(),
                            ),
                        )
                    val validation = CatalogCompiler().compile(sources)
                    check(validation is Validation.Valid) { validation.toString() }
                    checkNotNull(ServerSession.get(server)).installPublished(validation.value)
                    feet
                }
            world.connection.waitForChunksRender()
            context.waitFor { client ->
                client.player
                    ?.position()
                    ?.distanceToSqr(Vec3(feet.x + 0.5, feet.y.toDouble(), feet.z + 0.5))
                    ?.let { it < 0.001 } == true
            }
            world.server.waitFor { server ->
                val player = checkNotNull(server.playerList.getPlayer(id))
                player.connection.hasClientLoaded() && !player.isChangingDimension
            }
            val attempt =
                world.server.computeOnServer<UUID, RuntimeException> { server ->
                    checkNotNull(
                        checkNotNull(ServerSession.get(server)).runtime.start(
                            DefinitionId("local", "pattern_test"),
                            DefinitionId("local", "pattern_test"),
                            "client test",
                            { true },
                        ) { success, message ->
                            check(success) { message }
                        }
                    )
                }
            fun phase(server: MinecraftServer): String? =
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
            fun aim(block: BlockPos, hold: Boolean) {
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
                context.waitFor {
                    InteractionClient.offer?.position == block &&
                        InteractionClient.offer?.hold == hold
                }
            }
            fun release() {
                context.input.releaseKey { it.keyUse }
                context.waitFor { InteractionClient.offer != null }
            }
            fun submitSun() {
                aim(feet.offset(-1, 1, 2), true)
                context.input.holdKey { it.keyUse }
                context.waitFor { InteractionClient.progress?.holding == true }
                context.waitFor { InteractionClient.progress?.holding == false }
                release()
            }
            fun submitMoon() {
                aim(feet.offset(1, 1, 2), false)
                context.input.holdKey { it.keyUse }
                waitTicks(4)
                release()
            }

            // An interrupted hold cannot supply its binding's token.
            aim(feet.offset(-1, 1, 2), true)
            context.input.holdKey { it.keyUse }
            context.waitFor {
                InteractionClient.progress?.holding == true &&
                    checkNotNull(InteractionClient.progress).progress >= 4
            }
            context.takeScreenshot("conclave-pattern-hold")
            release()
            waitTicks(25)
            submitMoon() // mismatch 1; an incorrectly retained sun would complete the phase.
            world.server.runOnServer<RuntimeException> { check(phase(it) == "pattern") }
            submitSun()
            submitSun() // mismatch 2, without reusing it as the first token of a fresh answer.
            submitMoon() // mismatch 3.
            world.server.runOnServer<RuntimeException> { check(phase(it) == "pattern") }
            submitSun()
            aim(feet.offset(1, 1, 2), false)
            context.input.holdKey { it.keyUse }
            world.server.waitFor { phase(it) != "pattern" }
            waitTicks(25)
            world.server.runOnServer<RuntimeException> { server ->
                check(phase(server) == "authored") {
                    "Wrong mismatch count or held input adopted the next phase: ${phase(server)}"
                }
                val player = checkNotNull(server.playerList.getPlayer(id))
                check(player.containerMenu === player.inventoryMenu) {
                    "Consumed pattern input opened a chest"
                }
            }
            release()
            context.input.holdKey { it.keyUse }
            world.server.waitFor { phase(it) == "ordinary" }
            release()
            context.waitFor { InteractionClient.offer?.consume == false }
            context.input.holdKey { it.keyUse }
            context.waitForScreen(ContainerScreen::class.java)
            world.server.waitFor { phase(it) == "waiting" }
            context.input.releaseKey { it.keyUse }
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
