package dev.conclave.fabric

import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

class GraveNativeTest {
    @GameTest(maxTicks = 600)
    fun assistance_checks_aim_reach_visibility_and_closes_the_actual_death_once(
        helper: GameTestHelper
    ) {
        val session = checkNotNull(ServerSession.get(helper.level.server))
        val feet = helper.absolutePos(BlockPos(1, 1, 1))
        for (x in -2..5) for (z in -2..2) {
            helper.level.setBlockAndUpdate(feet.offset(x, -1, z), Blocks.STONE.defaultBlockState())
            for (y in 0..2) helper.level.setBlockAndUpdate(
                feet.offset(x, y, z),
                Blocks.AIR.defaultBlockState(),
            )
        }
        val position = Vec3.atBottomCenterOf(feet)
        val victim = helper.makeMockServerPlayerInLevel()
        val assistant = helper.makeMockServerPlayerInLevel()
        victim.setGameMode(GameType.SURVIVAL)
        assistant.setGameMode(GameType.SURVIVAL)
        victim.snapTo(position)
        assistant.snapTo(position.add(2.0, 0.0, 0.0))
        victim.connection.handleAcceptPlayerLoad(ServerboundPlayerLoadedPacket())
        assistant.connection.handleAcceptPlayerLoad(ServerboundPlayerLoadedPacket())
        fun aim() {
            val delta = position.add(0.0, 0.45, 0.0).subtract(assistant.eyePosition)
            assistant.yRot = Math.toDegrees(kotlin.math.atan2(-delta.x, delta.z)).toFloat()
            assistant.xRot =
                -Math.toDegrees(kotlin.math.atan2(delta.y, kotlin.math.hypot(delta.x, delta.z)))
                    .toFloat()
        }
        helper
            .startSequence()
            .thenWaitUntil {
                helper.assertTrue(
                    NativePlayerPlacement.usable(
                        victim,
                        NativeDestination(helper.level, position, 0f, 0f),
                    ),
                    "Waiting for safe native footing",
                )
            }
            .thenExecute {
                victim.hurtServer(
                    helper.level,
                    victim.damageSources().genericKill(),
                    Float.MAX_VALUE,
                )
                helper.assertFalse(victim.isAlive, "A real vanilla death must precede the grave")
            }
            .thenWaitUntil {
                helper.assertTrue(
                    session.graves.view(victim.uuid)?.message == "",
                    "Waiting for a usable grave",
                )
            }
            .thenExecute {
                val state = checkNotNull(session.graves.view(victim.uuid))
                val grave = checkNotNull(state.grave)
                helper.assertTrue(
                    state.remaining == -1L && !state.inAttempt && !state.selfAllowed,
                    "Outside graves retain unlimited time and default self-revival prohibition",
                )
                session.graves.accept(victim, GraveInputPayload(grave, 1, GraveInputAction.SELF))
                helper.assertFalse(
                    victim.isAlive,
                    "A forged self-revival request must respect global settings",
                )
                repeat(1100) {
                    session.graves.accept(
                        victim,
                        GraveInputPayload(grave, 1, GraveInputAction.SELF),
                    )
                }
                // The assistant's valid input below must still be admitted in this same tick.
                assistant.snapTo(position.add(5.0, 0.0, 0.0))
                aim()
                session.graves.accept(
                    assistant,
                    GraveInputPayload(grave, 1, GraveInputAction.PRESS),
                )
                helper.assertTrue(
                    session.server.playerList.getPlayer(victim.uuid) === victim,
                    "Out-of-reach assistance must not replace the body",
                )
                session.graves.accept(
                    assistant,
                    GraveInputPayload(grave, 1, GraveInputAction.RELEASE),
                )
                assistant.snapTo(position.add(2.0, 0.0, 0.0))
                aim()
                for (y in 0..1) helper.level.setBlockAndUpdate(
                    feet.offset(1, y, 0),
                    Blocks.STONE.defaultBlockState(),
                )
                session.graves.accept(
                    assistant,
                    GraveInputPayload(grave, 2, GraveInputAction.PRESS),
                )
                helper.assertTrue(
                    session.server.playerList.getPlayer(victim.uuid) === victim,
                    "A wall must prevent helping through it",
                )
                session.graves.accept(
                    assistant,
                    GraveInputPayload(grave, 2, GraveInputAction.RELEASE),
                )
                for (y in 0..1) helper.level.setBlockAndUpdate(
                    feet.offset(1, y, 0),
                    Blocks.AIR.defaultBlockState(),
                )
                assistant.yRot += 180
                session.graves.accept(
                    assistant,
                    GraveInputPayload(grave, 3, GraveInputAction.PRESS),
                )
                helper.assertTrue(
                    session.server.playerList.getPlayer(victim.uuid) === victim,
                    "Knowing a grave ID does not substitute for aiming at it",
                )
                session.graves.accept(
                    assistant,
                    GraveInputPayload(grave, 3, GraveInputAction.RELEASE),
                )
                aim()
                session.graves.accept(
                    assistant,
                    GraveInputPayload(grave, 4, GraveInputAction.PRESS),
                )
                val revived = checkNotNull(session.server.playerList.getPlayer(victim.uuid))
                helper.assertTrue(
                    revived !== victim && revived.isAlive && revived.health == revived.maxHealth,
                    "Eligible assistance must revive instantly with captured default health",
                )
                session.graves.accept(
                    assistant,
                    GraveInputPayload(grave, 4, GraveInputAction.PRESS),
                )
                helper.assertTrue(
                    session.server.playerList.getPlayer(victim.uuid) === revived &&
                        session.graves.view(victim.uuid) == null,
                    "Repeated input cannot revive the closed death again",
                )
                session.server.playerList.remove(revived)
                session.server.playerList.remove(assistant)
            }
            .thenSucceed()
    }
}
