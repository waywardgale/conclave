package dev.conclave.fabric.mixin

import dev.conclave.fabric.GraveNativeHooks
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket
import net.minecraft.server.network.ServerGamePacketListenerImpl
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ServerGamePacketListenerImpl::class)
abstract class GraveRespawnMixin {
    @Inject(method = ["handleClientCommand"], at = [At("HEAD")], cancellable = true)
    private fun conclaveRespawn(packet: ServerboundClientCommandPacket, callback: CallbackInfo) {
        val listener = this as Any as ServerGamePacketListenerImpl
        if (
            packet.action == ServerboundClientCommandPacket.Action.PERFORM_RESPAWN &&
                GraveNativeHooks.blockRespawn(listener.player)
        )
            callback.cancel()
    }
}
