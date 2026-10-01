package dev.conclave.fabric.client

import dev.conclave.fabric.*
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.minecraft.world.InteractionResult
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

/** Uses the configured vanilla Use control. A held control cannot adopt a newly offered target. */
internal object InteractionClient {
    var offer: InteractionOfferPayload? = null
        private set

    var progress: InteractionProgressPayload? = null
        private set

    private var offeredAt = 0L
    private var sequence = 0L
    private var ticks = 0L
    private var releaseRequired = false

    private data class Hold(val offer: InteractionOfferPayload, val sequence: Long)

    private var held: Hold? = null

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(InteractionOfferPayload.TYPE) { payload, _ ->
            if (held == null) {
                offer = payload.takeIf { it.offer != null }
                offeredAt = System.nanoTime()
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(InteractionProgressPayload.TYPE) { payload, _ ->
            if (payload.offer == held?.offer?.offer) progress = payload
        }
        UseBlockCallback.EVENT.register { _, level, _, hit ->
            if (!level.isClientSide) return@register InteractionResult.PASS
            val client = Minecraft.getInstance()
            val active = held
            if (active != null)
                return@register if (active.offer.consume && active.offer.position == hit.blockPos)
                    InteractionResult.CONSUME
                else InteractionResult.PASS
            if (releaseRequired || !client.isWindowActive || client.gui.screen() != null)
                return@register InteractionResult.PASS
            releaseRequired = true
            val prompt =
                currentOffer()?.takeIf { it.position == hit.blockPos }
                    ?: return@register InteractionResult.PASS
            val next = Hold(prompt, ++sequence)
            held = next
            offer = null
            progress = null
            send(next, InteractionAction.PRESS)
            // Fabric sends the native use packet for CONSUME; FAIL would suppress server admission.
            if (prompt.consume) InteractionResult.CONSUME else InteractionResult.PASS
        }
        UseItemCallback.EVENT.register { _, level, _ ->
            if (!level.isClientSide) return@register InteractionResult.PASS
            val active = held
            val hit = Minecraft.getInstance().hitResult as? BlockHitResult
            if (
                level.isClientSide &&
                    active?.offer?.consume == true &&
                    hit?.type == HitResult.Type.BLOCK &&
                    hit.blockPos == active.offer.position
            )
                InteractionResult.CONSUME
            else InteractionResult.PASS
        }
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            ticks++
            val use = client.options.keyUse.isDown
            val focused = client.isWindowActive && client.gui.screen() == null
            val hit =
                (client.hitResult as? BlockHitResult)?.takeIf { it.type == HitResult.Type.BLOCK }
            if (!use) releaseRequired = false
            val active = held
            if (active != null) {
                if (!use || !focused || hit?.blockPos != active.offer.position) {
                    send(active, InteractionAction.RELEASE)
                    held = null
                    progress = null
                    releaseRequired = use
                    offer = null
                } else if (ticks % 4L == 0L) send(active, InteractionAction.CONTINUE)
            } else if (
                focused &&
                    hit != null &&
                    ticks % 4L == 0L &&
                    ClientPlayNetworking.canSend(InteractionInputPayload.TYPE)
            ) {
                ClientPlayNetworking.send(
                    InteractionInputPayload(InteractionAction.LOOK, hit.blockPos, ++sequence)
                )
            }
            // A hold begun over air or before a mechanic exists is not a fresh later press.
            if (use) releaseRequired = true
        }
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.CROSSHAIR,
            Identifier.fromNamespaceAndPath("conclave", "interaction_help"),
        ) { graphics, _ ->
            val client = Minecraft.getInstance()
            val prompt = held?.offer ?: currentOffer()
            val hit = client.hitResult as? BlockHitResult
            if (
                prompt != null &&
                    client.gui.screen() == null &&
                    hit?.type == HitResult.Type.BLOCK &&
                    hit.blockPos == prompt.position
            ) {
                val key = client.options.keyUse.translatedKeyMessage.string
                val state = progress
                val label =
                    when {
                        held != null && state?.holding == false -> "Release $key to interact again"
                        state?.holding == true -> "Interacting"
                        prompt.hold -> "Hold $key to interact"
                        else -> "$key: interact"
                    }
                graphics.centeredText(
                    client.font,
                    label,
                    graphics.guiWidth() / 2,
                    graphics.guiHeight() / 2 + 26,
                    0xffffffff.toInt(),
                )
                if (state?.holding == true && state.required > 0) {
                    val center = graphics.guiWidth() / 2
                    val y = graphics.guiHeight() / 2 + 40
                    val width = (120.0 * state.progress / state.required).toInt().coerceIn(0, 120)
                    graphics.fill(center - 60, y, center + 60, y + 4, 0xaa1a232d.toInt())
                    graphics.fill(center - 60, y, center - 60 + width, y + 4, 0xff86d7ad.toInt())
                }
            }
        }
        ClientPlayConnectionEvents.DISCONNECT.register { handler, client ->
            client.execute {
                if (client.connection != null && client.connection !== handler) return@execute
                offer = null
                held = null
                progress = null
                sequence = 0
                releaseRequired = false
            }
        }
    }

    private fun currentOffer() = offer?.takeIf { System.nanoTime() - offeredAt < 1_000_000_000L }

    private fun send(hold: Hold, action: InteractionAction) {
        if (ClientPlayNetworking.canSend(InteractionInputPayload.TYPE))
            ClientPlayNetworking.send(
                InteractionInputPayload(
                    action,
                    hold.offer.position,
                    hold.sequence,
                    checkNotNull(hold.offer.offer),
                )
            )
    }
}
