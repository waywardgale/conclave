package dev.conclave.fabric.mixin

import net.minecraft.server.network.ServerGamePacketListenerImpl
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Invoker

@Mixin(ServerGamePacketListenerImpl::class)
interface RespawnConnectionAccess {
    @Invoker("restartClientLoadTimerAfterRespawn") fun conclaveRestartClientLoadTimer()
}
