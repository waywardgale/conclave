package dev.conclave.fabric.client

import dev.conclave.fabric.*
import java.util.UUID
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.DeathScreen
import net.minecraft.resources.Identifier
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.Interaction
import net.minecraft.world.phys.EntityHitResult

/** Presents only the viewer's own state. The server independently validates every input. */
internal object GraveClient {
    var state: GraveStatePayload? = null
        private set

    private val markers = mutableMapOf<UUID, Int>()

    private data class Hold(val grave: UUID, val sequence: Long)

    private var hold: Hold? = null
    private var sequence = 0L
    private var ticks = 0L
    private var releaseRequired = false
    private var camera: Interaction? = null
    private var previousCamera: Entity? = null
    private var previousType: CameraType? = null
    var help: GraveHelpPayload? = null
        private set

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(GraveHelpPayload.TYPE) { payload, _ ->
            help = payload.takeIf { it.grave != null }
        }
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.CROSSHAIR,
            Identifier.fromNamespaceAndPath("conclave", "revival_help"),
        ) { graphics, _ ->
            val client = Minecraft.getInstance()
            val prompt = help
            if (
                state?.status == dev.conclave.core.GraveStatus.PASSED_OUT &&
                    SpectatorClient.state?.free == true &&
                    client.gui.screen() == null
            )
                graphics.centeredText(
                    client.font,
                    "Passed out · ${SpectatorClient.controls.translatedKeyMessage.string}: spectator controls",
                    graphics.guiWidth() / 2,
                    18,
                    0xffffffff.toInt(),
                )
            if (
                prompt != null &&
                    client.gui.screen() == null &&
                    (client.hitResult as? EntityHitResult)?.entity?.uuid == prompt.grave
            ) {
                val key = client.options.keyUse.translatedKeyMessage.string
                val label =
                    when {
                        prompt.holding -> "Reviving ${prompt.name}"
                        prompt.required == 0L -> "$key: revive ${prompt.name}"
                        else -> "Hold $key to revive ${prompt.name}"
                    }
                val center = graphics.guiWidth() / 2
                val y = graphics.guiHeight() / 2 + 26
                graphics.centeredText(client.font, label, center, y, 0xffffffff.toInt())
                if (prompt.holding && prompt.required > 0) {
                    graphics.fill(center - 60, y + 14, center + 60, y + 18, 0xaa1a232d.toInt())
                    val width =
                        (120.0 * prompt.progress.toDouble() / prompt.required.toDouble())
                            .toInt()
                            .coerceIn(0, 120)
                    graphics.fill(
                        center - 60,
                        y + 14,
                        center - 60 + width,
                        y + 18,
                        0xff86d7ad.toInt(),
                    )
                }
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(GraveStatePayload.TYPE) { payload, context ->
            state = payload.takeIf { it.grave != null }
            if (state == null) restore(context.client())
            (context.client().gui.screen() as? GraveScreen)?.refresh()
        }
        ClientPlayNetworking.registerGlobalReceiver(GraveMarkerPayload.TYPE) { payload, _ ->
            if (payload.visible) {
                if (markers.size < 128 || payload.grave in markers)
                    markers[payload.grave] = payload.entity
            } else markers.remove(payload.grave)
        }
        UseEntityCallback.EVENT.register { _, level, _, entity, _ ->
            if (!level.isClientSide || entity.uuid !in markers) InteractionResult.PASS
            else {
                val client = Minecraft.getInstance()
                if (
                    hold == null &&
                        !releaseRequired &&
                        client.isWindowActive &&
                        client.gui.screen() == null
                ) {
                    val next = Hold(entity.uuid, ++sequence)
                    hold = next
                    send(next, GraveInputAction.PRESS)
                }
                InteractionResult.FAIL
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            ticks++
            val use = client.options.keyUse.isDown
            if (!use) releaseRequired = false
            val active = hold
            if (
                active == null &&
                    ticks % 4L == 0L &&
                    client.gui.screen() == null &&
                    client.isWindowActive
            ) {
                (client.hitResult as? EntityHitResult)
                    ?.entity
                    ?.uuid
                    ?.takeIf { it in markers }
                    ?.let { send(Hold(it, ++sequence), GraveInputAction.LOOK) }
            }
            if (active != null) {
                val targeted = (client.hitResult as? EntityHitResult)?.entity?.uuid == active.grave
                if (!use || !client.isWindowActive || client.gui.screen() != null || !targeted) {
                    send(active, GraveInputAction.RELEASE)
                    hold = null
                    releaseRequired = use
                } else if (ticks % 4L == 0L) send(active, GraveInputAction.CONTINUE)
            }
            if (state != null && client.level != null && client.player != null) {
                val free =
                    state?.status == dev.conclave.core.GraveStatus.PASSED_OUT &&
                        SpectatorClient.state?.free == true
                if (free) {
                    restoreCamera(client)
                    SpectatorClient.attach(client)
                    if (client.gui.screen() is GraveScreen || client.gui.screen() is DeathScreen)
                        client.gui.setScreen(null)
                } else {
                    if (client.gui.screen() == null || client.gui.screen() is DeathScreen)
                        client.gui.setScreen(GraveScreen())
                    if (
                        state?.status == dev.conclave.core.GraveStatus.PASSED_OUT &&
                            SpectatorClient.state?.target != null
                    ) {
                        restoreCamera(client)
                        if (!SpectatorClient.attach(client)) updateCamera(client)
                    } else {
                        SpectatorClient.attach(client)
                        updateCamera(client)
                    }
                }
            } else if (camera != null) restore(client)
        }
        ClientPlayConnectionEvents.DISCONNECT.register { handler, client ->
            client.execute {
                if (client.connection != null && client.connection !== handler) return@execute
                restore(client)
                state = null
                markers.clear()
                hold = null
                sequence = 0
                releaseRequired = false
                help = null
            }
        }
    }

    fun revive() {
        val grave = state?.grave ?: return
        send(Hold(grave, ++sequence), GraveInputAction.SELF)
    }

    fun cameraReady(client: Minecraft) =
        camera != null && client.cameraEntity === camera ||
            SpectatorClient.state?.target?.let { client.cameraEntity?.uuid == it } == true

    private fun send(hold: Hold, action: GraveInputAction) {
        if (ClientPlayNetworking.canSend(GraveInputPayload.TYPE))
            ClientPlayNetworking.send(GraveInputPayload(hold.grave, hold.sequence, action))
    }

    private fun updateCamera(client: Minecraft) {
        val grave = state?.grave ?: return
        val level = client.level ?: return
        val marker = markers[grave]?.let { level.getEntity(it) } ?: return
        var pivot = camera
        if (pivot == null || pivot.level() !== level) {
            if (pivot != null) restoreCamera(client)
            previousCamera = client.cameraEntity
            previousType = client.options.cameraType
            pivot = Interaction(EntityTypes.INTERACTION, level)
            pivot.yRot = client.player?.yRot ?: 0f
            pivot.xRot = 15f
            camera = pivot
        }
        pivot.snapTo(marker.position().add(0.0, 0.5, 0.0))
        pivot.setOldPosAndRot()
        // The native third-person camera performs terrain clipping. No spectator mode or player
        // camera is used during the revival opportunity.
        client.cameraEntity = pivot
        client.options.cameraType = CameraType.THIRD_PERSON_BACK
    }

    fun turn(x: Double, y: Double) {
        camera?.let {
            it.yRot += (x * 0.4).toFloat()
            it.xRot = (it.xRot + y * 0.4f).toFloat().coerceIn(-80f, 80f)
            it.setOldPosAndRot()
        }
    }

    private fun restoreCamera(client: Minecraft) {
        if (client.cameraEntity === camera)
            client.cameraEntity =
                previousCamera?.takeIf { it.level() === client.level && !it.isRemoved }
                    ?: client.player
        if (camera != null && client.options.cameraType == CameraType.THIRD_PERSON_BACK)
            previousType?.let { client.options.cameraType = it }
        camera = null
        previousCamera = null
        previousType = null
    }

    private fun restore(client: Minecraft) {
        restoreCamera(client)
        if (client.gui.screen() is GraveScreen) client.gui.setScreen(null)
    }
}
