package dev.conclave.fabric.client

import dev.conclave.fabric.LoginCompatibility
import java.util.concurrent.CompletableFuture
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking

object ConclaveClient : ClientModInitializer {
    override fun onInitializeClient() {
        AuthoringClient.register()
        GraveClient.register()
        InteractionClient.register()
        SpectatorClient.register()
        ProtectionClient.register()
        ClientLoginNetworking.registerGlobalReceiver(LoginCompatibility.channel) { _, _, query, _ ->
            // Never echo the server's identity: the response describes this client's installed
            // code.
            val response =
                if (LoginCompatibility.read(query) != null) LoginCompatibility.payload() else null
            CompletableFuture.completedFuture(response)
        }
    }
}
