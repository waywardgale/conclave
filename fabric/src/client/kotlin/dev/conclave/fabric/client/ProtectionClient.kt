package dev.conclave.fabric.client

import dev.conclave.fabric.ProtectionPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier

internal object ProtectionClient {
    var remaining = 0L
        private set

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(ProtectionPayload.TYPE) { payload, _ ->
            remaining = payload.remaining
        }
        ClientPlayConnectionEvents.DISCONNECT.register { handler, client ->
            client.execute {
                if (client.connection == null || client.connection === handler) remaining = 0
            }
        }
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.CROSSHAIR,
            Identifier.fromNamespaceAndPath("conclave", "revival_protection"),
        ) { graphics, _ ->
            val client = Minecraft.getInstance()
            if (remaining > 0 && client.gui.screen() == null)
                graphics.centeredText(
                    client.font,
                    "Revival protection · ${1 + (remaining - 1) / 20}s",
                    graphics.guiWidth() / 2,
                    32,
                    0xff86d7ad.toInt(),
                )
        }
    }
}
