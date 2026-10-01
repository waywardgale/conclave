package dev.conclave.fabric.mixin

import dev.conclave.fabric.ConclaveRespawnTarget
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.portal.TeleportTransition
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(ServerPlayer::class)
abstract class RespawnDestinationMixin : ConclaveRespawnTarget {
    @Unique private var conclaveDestination: TeleportTransition? = null

    override fun conclaveRespawnTarget(target: TeleportTransition?) {
        check(target == null || conclaveDestination == null) { "Nested Conclave respawn" }
        conclaveDestination = target
    }

    @Inject(method = ["findRespawnPositionAndUseSpawnBlock"], at = [At("HEAD")], cancellable = true)
    private fun conclaveDestination(
        consumeAnchor: Boolean,
        after: TeleportTransition.PostTeleportTransition,
        callback: CallbackInfoReturnable<TeleportTransition>,
    ) {
        conclaveDestination?.let { callback.returnValue = it }
    }
}
