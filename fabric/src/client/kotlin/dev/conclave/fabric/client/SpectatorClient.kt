package dev.conclave.fabric.client

import dev.conclave.fabric.*
import java.util.UUID
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.CameraType
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

internal object SpectatorClient {
    var state: WatchStatePayload? = null
        private set

    lateinit var controls: KeyMapping
        private set

    private var attached: UUID? = null
    private var previousType: CameraType? = null

    fun register() {
        controls =
            KeyMappingHelper.registerKeyMapping(
                KeyMapping(
                    "key.conclave.spectator_controls",
                    GLFW.GLFW_KEY_V,
                    KeyMapping.Category.SPECTATOR,
                )
            )
        ClientPlayNetworking.registerGlobalReceiver(WatchStatePayload.TYPE) { payload, context ->
            val old = state?.target
            state = payload.takeIf { it.attempt != null }
            if (state == null || old != state?.target) restore(context.client())
            (context.client().gui.screen() as? SpectatorScreen)?.refresh()
            (context.client().gui.screen() as? GraveScreen)?.refresh()
        }
        ClientPlayConnectionEvents.DISCONNECT.register { handler, client ->
            client.execute {
                if (client.connection != null && client.connection !== handler) return@execute
                state = null
                restore(client)
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (controls.consumeClick()) if (state != null && client.gui.screen() == null) open()
            val view = state
            if (view?.living == true && client.level != null) {
                if (view.active && !view.free) {
                    if (client.gui.screen() == null) client.gui.setScreen(ObserverViewScreen())
                    attach(client)
                } else {
                    restore(client)
                    if (client.gui.screen() is ObserverViewScreen) client.gui.setScreen(null)
                }
            } else if (
                view != null &&
                    GraveClient.state == null &&
                    client.level != null &&
                    (client.gui.screen() == null ||
                        client.gui.screen() is GraveScreen ||
                        client.gui.screen() is ObserverViewScreen)
            )
                client.gui.setScreen(ViewRecoveryScreen())
        }
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
            net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CROSSHAIR,
            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                "conclave",
                "observer_controls",
            ),
        ) { graphics, _ ->
            val client = Minecraft.getInstance()
            if (state?.living == true && client.gui.screen() == null) {
                val message = if (state?.active == true) "Observing" else "Encounter observer"
                graphics.centeredText(
                    client.font,
                    "$message · ${controls.translatedKeyMessage.string}: viewing controls",
                    graphics.guiWidth() / 2,
                    18,
                    0xffffffff.toInt(),
                )
            }
        }
    }

    fun open() {
        if (state != null) Minecraft.getInstance().gui.setScreen(SpectatorScreen())
    }

    fun choose(action: WatchAction, target: UUID? = null) {
        val attempt = state?.attempt ?: return
        if (ClientPlayNetworking.canSend(WatchInputPayload.TYPE))
            ClientPlayNetworking.send(WatchInputPayload(attempt, action, target))
    }

    fun attach(client: Minecraft): Boolean {
        if (state?.active == false) {
            restore(client)
            return false
        }
        if (state?.free == true) return true
        val target =
            state?.target
                ?: run {
                    restore(client)
                    return false
                }
        val player =
            client.level?.getPlayerByUUID(target)
                ?: run {
                    restore(client)
                    return false
                }
        if (previousType == null) previousType = client.options.cameraType
        attached = target
        client.cameraEntity = player
        client.options.cameraType = CameraType.FIRST_PERSON
        return true
    }

    private fun restore(client: Minecraft) {
        if (attached != null && client.cameraEntity?.uuid == attached)
            client.cameraEntity = client.player
        if (previousType != null && client.options.cameraType == CameraType.FIRST_PERSON)
            client.options.cameraType = checkNotNull(previousType)
        attached = null
        previousType = null
        if (
            state == null &&
                (client.gui.screen() is SpectatorScreen ||
                    client.gui.screen() is ViewRecoveryScreen ||
                    client.gui.screen() is ObserverViewScreen)
        )
            client.gui.setScreen(null)
    }
}

private class ViewRecoveryScreen : Screen(Component.literal("Returning after the attempt")) {
    override fun isPauseScreen() = false

    override fun shouldCloseOnEsc() = false

    override fun keyPressed(event: net.minecraft.client.input.KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            minecraft.gui.setScreen(net.minecraft.client.gui.screens.PauseScreen(true))
            return true
        }
        return super.keyPressed(event)
    }

    override fun extractRenderState(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        graphics.fill(0, 0, width, height, 0xff10141a.toInt())
        graphics.centeredText(
            font,
            "Returning after the attempt",
            width / 2,
            height / 2,
            0xffffffff.toInt(),
        )
    }
}

internal class SpectatorScreen : Screen(Component.literal("Watch a teammate")) {
    private var offset = 0

    override fun isPauseScreen() = false

    fun refresh() {
        rebuildWidgets()
    }

    override fun init() {
        val choices = SpectatorClient.state?.choices.orEmpty()
        val view = SpectatorClient.state
        val visible = maxOf(1, (height - 126) / 24)
        offset = offset.coerceIn(0, maxOf(0, choices.size - visible))
        choices.drop(offset).take(visible).forEachIndexed { index, choice ->
            addRenderableWidget(
                Button.builder(Component.literal(choice.name)) {
                        SpectatorClient.choose(WatchAction.SELECT, choice.player)
                        onClose()
                    }
                    .bounds(width / 2 - 110, 40 + index * 24, 220, 20)
                    .build()
            )
        }
        addRenderableWidget(
                Button.builder(Component.literal("Previous page")) {
                        offset = maxOf(0, offset - visible)
                        refresh()
                    }
                    .bounds(width / 2 - 155, height - 48, 150, 20)
                    .build()
            )
            .active = offset > 0
        addRenderableWidget(
                Button.builder(Component.literal("Next page")) {
                        offset += visible
                        refresh()
                    }
                    .bounds(width / 2 + 5, height - 48, 150, 20)
                    .build()
            )
            .active = offset + visible < choices.size
        addRenderableWidget(
            Button.builder(Component.literal("Back")) { onClose() }
                .bounds(width / 2 - 75, height - 24, 150, 20)
                .build()
        )
        if (view?.living == true) {
            addRenderableWidget(
                    Button.builder(Component.literal(if (view.active) "Leave view" else "Watch")) {
                            SpectatorClient.choose(
                                if (view.active) WatchAction.LEAVE else WatchAction.WATCH
                            )
                            onClose()
                        }
                        .bounds(width / 2 - 75, height - 74, 150, 20)
                        .build()
                )
                .active = view.active || view.free || choices.isNotEmpty()
        }
    }

    override fun onClose() {
        val view = SpectatorClient.state
        minecraft.gui.setScreen(
            when {
                view == null || view.free || !view.active -> null
                view.living -> ObserverViewScreen()
                else -> GraveScreen()
            }
        )
    }

    override fun extractRenderState(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        graphics.centeredText(font, title, width / 2, 16, 0xffffffff.toInt())
    }
}

private class ObserverViewScreen : Screen(Component.literal("Watching the encounter")) {
    override fun isPauseScreen() = false

    override fun shouldCloseOnEsc() = false

    override fun keyPressed(event: net.minecraft.client.input.KeyEvent): Boolean {
        if (SpectatorClient.controls.matches(event)) {
            SpectatorClient.open()
            return true
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            minecraft.gui.setScreen(net.minecraft.client.gui.screens.PauseScreen(true))
            return true
        }
        return super.keyPressed(event)
    }

    override fun init() {
        addRenderableWidget(
            Button.builder(Component.literal("Choose teammate")) { SpectatorClient.open() }
                .bounds(width / 2 - 155, height - 42, 150, 20)
                .build()
        )
        addRenderableWidget(
            Button.builder(Component.literal("Leave view")) {
                    SpectatorClient.choose(WatchAction.LEAVE)
                }
                .bounds(width / 2 + 5, height - 42, 150, 20)
                .build()
        )
    }

    override fun extractBackground(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {}

    override fun extractRenderState(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        val view = SpectatorClient.state ?: return
        if (view.target == null || minecraft.cameraEntity?.uuid != view.target)
            graphics.fill(0, 0, width, height, 0xff10141a.toInt())
        graphics.fill(0, height - 76, width, height, 0xb010141a.toInt())
        val name = view.choices.firstOrNull { it.player == view.target }?.name
        graphics.centeredText(
            font,
            name?.let { "Watching $it" }
                ?: view.message.ifEmpty { "Waiting for the teammate view" },
            width / 2,
            height - 66,
            0xffffffff.toInt(),
        )
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }
}
