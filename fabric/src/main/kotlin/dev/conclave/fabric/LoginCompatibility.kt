package dev.conclave.fabric

import dev.conclave.core.BuildIdentity
import dev.conclave.core.CompatibilityExchange
import io.netty.buffer.Unpooled
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

/** Shared between Loom's separately compiled main/client sources; not a supported addon API. */
object LoginCompatibility {
    val channel: Identifier = Identifier.fromNamespaceAndPath("conclave", "core_compatibility")

    fun payload(): FriendlyByteBuf =
        FriendlyByteBuf(Unpooled.wrappedBuffer(CompatibilityExchange.encode(BuildDetails.identity)))

    fun read(buffer: FriendlyByteBuf): BuildIdentity? {
        if (buffer.readableBytes() > CompatibilityExchange.MAX_BYTES) return null
        val bytes = ByteArray(buffer.readableBytes())
        buffer.readBytes(bytes)
        return CompatibilityExchange.decode(bytes)
    }

    fun registerServer() {
        ServerLoginNetworking.registerGlobalReceiver(channel) {
            _,
            listener,
            understood,
            buffer,
            _,
            _ ->
            val installed = if (understood) read(buffer) else null
            if (installed != BuildDetails.identity) {
                val present =
                    installed?.let(BuildDetails::describe)
                        ?: if (understood) "an invalid compatibility response"
                        else "no compatible Conclave client"
                listener.disconnect(
                    Component.literal(
                        "Conclave ${BuildDetails.describe()} is required by this server. Your client reported $present. Install the matching Conclave build before joining."
                    )
                )
            }
        }
        // Fabric keeps login pending until the response arrives; vanilla's login timeout bounds
        // silence.
        ServerLoginConnectionEvents.QUERY_START.register { _, _, sender, _ ->
            sender.sendPacket(channel, payload())
        }
    }
}
