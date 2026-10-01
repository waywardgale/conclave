package dev.conclave.fabric

import dev.conclave.core.*
import java.math.BigDecimal
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.phys.Vec3

class InteractionNativeTest {
    @GameTest
    fun block_targets_require_native_aim_reach_and_unobstructed_loaded_terrain(
        helper: GameTestHelper
    ) {
        val block = helper.absolutePos(BlockPos(1, 2, 3))
        val feet = helper.absolutePos(BlockPos(1, 1, 1))
        val player = helper.makeMockServerPlayerInLevel()
        player.snapTo(feet.x + 0.5, feet.y.toDouble(), feet.z + 0.5)
        helper.level.setBlockAndUpdate(block, Blocks.CHEST.defaultBlockState())
        fun aim() {
            val delta = Vec3.atCenterOf(block).subtract(player.eyePosition)
            player.yRot = Math.toDegrees(kotlin.math.atan2(-delta.x, delta.z)).toFloat()
            player.xRot =
                -Math.toDegrees(kotlin.math.atan2(delta.y, kotlin.math.hypot(delta.x, delta.z)))
                    .toFloat()
        }
        aim()
        val id = DefinitionId("local", "geometry_test")
        val compiled =
            (BuiltinMechanics.registry()
                    .compile(
                        DefinitionId("conclave", "interact"),
                        (YamlDocumentReader()
                                .read(SourceDocument("test.yaml", "targets: [{block: console}]"))
                                as Validation.Valid)
                            .value,
                    ) as Validation.Valid)
                .value
        val definition =
            EncounterDefinition(
                id,
                null,
                "test",
                listOf(
                    PhaseDefinition(
                        "test",
                        null,
                        null,
                        PhaseRoute.Complete,
                        content =
                            ScopeDefinition(
                                mechanics = listOf(MechanicOccurrence("use", null, compiled))
                            ),
                    )
                ),
            )
        val arena =
            ArenaDefinition(
                id,
                null,
                "minecraft:overworld",
                Geometry.Box(
                    Position(block.x.toDouble(), block.y.toDouble(), block.z.toDouble()),
                    BigDecimal.TEN,
                    BigDecimal.TEN,
                    BigDecimal.TEN,
                ),
                areas = emptyList(),
                locations =
                    listOf(
                        NamedLocation(
                            "actual",
                            null,
                            LocationPlacement(
                                Position(
                                    BigDecimal(block.x + 1).subtract(BigDecimal("1e-30")),
                                    BigDecimal(block.y + 1).subtract(BigDecimal("1e-30")),
                                    BigDecimal(block.z + 1).subtract(BigDecimal("1e-30")),
                                )
                            ),
                        )
                    ),
                encounters = listOf(ArenaEncounter(id, emptyMap(), mapOf("console" to "actual"))),
            )
        val targets = NativeBlockTargets(arena, definition)
        val target = targets.handles(helper.level, block).single()
        check(target.reference.id == "console")
        check(targets.valid(player, target, null)) { "A reachable native outline was rejected" }
        check(!targets.valid(player, target, 1.0)) {
            "Authored reach did not restrict native reach"
        }
        player.yRot += 180f
        check(!targets.valid(player, target, null)) { "Looking away still counted as aiming" }
        aim()
        val wall = helper.absolutePos(BlockPos(1, 2, 2))
        helper.level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState())
        check(!targets.valid(player, target, null)) { "A wall did not block interaction" }
        helper.level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState())
        check(targets.valid(player, target, null))
        helper.level.setBlockAndUpdate(
            block,
            Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH),
        )
        check(targets.handles(helper.level, block).single() == target) {
            "A property change replaced the physical target"
        }
        player.snapTo(feet.x + 0.5, feet.y.toDouble(), feet.z - 7.0)
        aim()
        check(!targets.valid(player, target, 100.0)) { "Authored reach extended Minecraft reach" }
        helper.level.setBlockAndUpdate(block, Blocks.AIR.defaultBlockState())
        check(targets.handles(helper.level, block).isEmpty())
        helper.level.server.playerList.remove(player)
        helper.succeed()
    }
}
