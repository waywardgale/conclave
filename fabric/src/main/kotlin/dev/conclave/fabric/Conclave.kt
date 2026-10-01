package dev.conclave.fabric

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import org.slf4j.LoggerFactory

object Conclave : ModInitializer {
    override fun onInitialize() {
        NativeChunkClaims.register()
        LoginCompatibility.registerServer()
        AuthoringServer.register()
        NativeGraves.register()
        NativeInteractions.register()
        NativeSpectating.register()
        NativeRevivalProtection.register()
        ServerSession.register()
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            ConclaveCommands.register(dispatcher)
        }
        LoggerFactory.getLogger("Conclave")
            .info(
                "Conclave {} initialized. V1 implementation is in progress.",
                BuildDetails.describe(),
            )
    }
}
