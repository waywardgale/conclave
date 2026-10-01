package dev.conclave.fabric.mixin

import dev.conclave.fabric.SpectatorNativeHooks
import net.minecraft.server.network.ServerGamePacketListenerImpl
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ServerGamePacketListenerImpl::class)
abstract class SpectatorInputMixin {
    @Inject(
        method =
            [
                "handleMovePlayer",
                "handleTeleportToEntityPacket",
                "handleInteract",
                "handleAttack",
                "handleUseItemOn",
                "handleUseItem",
                "handlePlayerAction",
            ],
        at = [At("HEAD")],
        cancellable = true,
    )
    private fun conclaveViewingInput(callback: CallbackInfo) {
        if (SpectatorNativeHooks.constrained((this as Any as ServerGamePacketListenerImpl).player))
            callback.cancel()
    }
}
