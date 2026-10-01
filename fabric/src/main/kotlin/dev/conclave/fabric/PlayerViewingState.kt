package dev.conclave.fabric

import dev.conclave.core.SpectatorMode
import java.util.UUID
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

/** Stored atomically with the native mode. A durable PLAYER_STATE intent must already exist. */
data class PlayerViewingState(
    val attempt: UUID,
    val originalMode: GameType,
    val mode: SpectatorMode,
    val dimension: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val living: Boolean = false,
) {
    init {
        require(
            dimension.length <= 256 &&
                Identifier.tryParse(dimension) != null &&
                listOf(x, y, z).all { it.isFinite() && kotlin.math.abs(it) <= 60_000_000 } &&
                yaw.isFinite() &&
                pitch.isFinite() &&
                pitch in -90f..90f
        )
    }

    fun write(output: ValueOutput) {
        output.putInt("version", 1)
        output.putString("attempt", attempt.toString())
        output.putInt("original_mode", originalMode.id)
        output.putString("mode", mode.name)
        output.putString("dimension", dimension)
        output.putDouble("x", x)
        output.putDouble("y", y)
        output.putDouble("z", z)
        output.putFloat("yaw", yaw)
        output.putFloat("pitch", pitch)
        output.putBoolean("living", living)
    }

    companion object {
        const val TAG = "conclave:viewing"

        fun read(input: ValueInput): PlayerViewingState {
            check(input.getIntOr("version", -1) == 1)
            val original = GameType.entries.single { it.id == input.getIntOr("original_mode", -1) }
            return PlayerViewingState(
                UUID.fromString(input.getString("attempt").orElseThrow()),
                original,
                SpectatorMode.valueOf(input.getString("mode").orElseThrow()),
                input.getString("dimension").orElseThrow(),
                input.getDoubleOr("x", Double.NaN),
                input.getDoubleOr("y", Double.NaN),
                input.getDoubleOr("z", Double.NaN),
                input.getFloatOr("yaw", Float.NaN),
                input.getFloatOr("pitch", Float.NaN),
                input.getBooleanOr("living", false),
            )
        }

        fun capture(
            player: ServerPlayer,
            attempt: UUID,
            mode: SpectatorMode,
            living: Boolean = false,
        ) =
            PlayerViewingState(
                attempt,
                player.gameMode.gameModeForPlayer,
                mode,
                player.level().dimension().identifier().toString(),
                player.x,
                player.y,
                player.z,
                player.yRot,
                player.xRot.coerceIn(-90f, 90f),
                living,
            )

        internal fun destination(
            server: net.minecraft.server.MinecraftServer,
            state: PlayerViewingState,
        ): NativeDestination? {
            val level =
                server.getLevel(
                    net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        Identifier.parse(state.dimension),
                    )
                ) ?: return null
            return NativeDestination(
                level,
                net.minecraft.world.phys.Vec3(state.x, state.y, state.z),
                state.yaw,
                state.pitch,
            )
        }

        /** Restore before native save confirmation; preserve later external mode changes. */
        fun restore(player: ServerPlayer, attempt: UUID) {
            val access = player as ConclavePlayerViewing
            val state = access.conclaveViewing() ?: return
            check(state.attempt == attempt) {
                "Native viewing ownership disagrees with the recovery attempt"
            }
            access.conclaveViewing(null)
            player.setCamera(player)
            if (player.gameMode.gameModeForPlayer == GameType.SPECTATOR)
                player.setGameMode(state.originalMode)
            if (
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(
                    player,
                    WatchStatePayload.TYPE,
                )
            )
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    player,
                    WatchStatePayload(null),
                )
        }
    }
}

interface ConclavePlayerViewing {
    fun conclaveViewing(): PlayerViewingState?

    fun conclaveViewing(state: PlayerViewingState?)
}
