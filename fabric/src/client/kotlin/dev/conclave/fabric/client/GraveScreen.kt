package dev.conclave.fabric.client

import dev.conclave.core.GraveStatus
import dev.conclave.fabric.WatchAction
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket
import org.lwjgl.glfw.GLFW

internal class GraveScreen : Screen(Component.literal("Your grave")) {
    private var self: Button? = null
    private var respawn: Button? = null
    private var previous: Button? = null
    private var next: Button? = null
    private var choose: Button? = null

    override fun isPauseScreen() = false

    override fun shouldCloseOnEsc() = false

    override fun keyPressed(event: KeyEvent): Boolean {
        if (SpectatorClient.controls.matches(event) && SpectatorClient.state != null) {
            SpectatorClient.open()
            return true
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            minecraft.gui.setScreen(PauseScreen(true))
            return true
        }
        return super.keyPressed(event)
    }

    override fun init() {
        previous =
            addRenderableWidget(
                Button.builder(Component.literal("Previous")) {
                        SpectatorClient.choose(WatchAction.PREVIOUS)
                    }
                    .bounds(width / 2 - 155, height - 52, 95, 20)
                    .build()
            )
        choose =
            addRenderableWidget(
                Button.builder(Component.literal("Choose teammate")) { SpectatorClient.open() }
                    .bounds(width / 2 - 56, height - 52, 112, 20)
                    .build()
            )
        next =
            addRenderableWidget(
                Button.builder(Component.literal("Next")) {
                        SpectatorClient.choose(WatchAction.NEXT)
                    }
                    .bounds(width / 2 + 60, height - 52, 95, 20)
                    .build()
            )
        self =
            addRenderableWidget(
                Button.builder(Component.literal("Revive here")) { GraveClient.revive() }
                    .bounds(width / 2 - 155, height - 52, 150, 20)
                    .build()
            )
        respawn =
            addRenderableWidget(
                Button.builder(Component.literal("Respawn")) {
                        minecraft.connection?.send(
                            ServerboundClientCommandPacket(
                                ServerboundClientCommandPacket.Action.PERFORM_RESPAWN
                            )
                        )
                    }
                    .bounds(width / 2 + 5, height - 52, 150, 20)
                    .build()
            )
        refresh()
    }

    fun refresh() {
        val state = GraveClient.state
        val watching = state?.status == GraveStatus.PASSED_OUT && SpectatorClient.state != null
        for (button in listOf(previous, next, choose)) {
            button?.visible = watching
            button?.active = SpectatorClient.state?.choices?.isNotEmpty() == true
        }
        self?.visible = state?.selfAllowed == true && state.status == GraveStatus.OPPORTUNITY
        respawn?.visible = state != null && !state.inAttempt
        self?.x = if (state?.inAttempt == true) width / 2 - 75 else width / 2 - 155
        respawn?.x = if (state?.selfAllowed == true) width / 2 + 5 else width / 2 - 75
        self?.active =
            state != null &&
                state.selfAllowed &&
                state.status == GraveStatus.OPPORTUNITY &&
                state.delay == 0L &&
                state.message.isEmpty()
        respawn?.active = state != null && !state.inAttempt
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
        val state = GraveClient.state ?: return
        if (!GraveClient.cameraReady(minecraft))
            graphics.fill(0, 0, width, height, 0xff10141a.toInt())
        graphics.fill(0, height - 124, width, height, 0xb010141a.toInt())
        val watch = SpectatorClient.state
        val watched =
            watch?.target?.let { id -> watch.choices.firstOrNull { it.player == id }?.name }
        val heading =
            if (watched != null) "Watching $watched"
            else if (state.status == GraveStatus.PASSED_OUT) "Revival window ended"
            else "Waiting for revival"
        graphics.centeredText(font, heading, width / 2, height - 112, 0xffffffff.toInt())
        val detail =
            when {
                state.status == GraveStatus.PASSED_OUT ->
                    watch?.message?.takeIf { it.isNotEmpty() }
                        ?: "You will return when the attempt finishes."
                state.delay > 0 -> "Revival available in ${seconds(state.delay)}s"
                state.remaining < 0 ->
                    "A teammate can help you. No revival time limit outside combat."
                else ->
                    "${seconds(state.remaining)}s remaining" +
                        if (state.combat) "" else " · Paused outside combat"
            }
        graphics.centeredText(font, detail, width / 2, height - 95, 0xffd1d8e0.toInt())
        if (state.message.isNotEmpty())
            graphics.centeredText(font, state.message, width / 2, height - 80, 0xffffd084.toInt())
        graphics.centeredText(
            font,
            if (watched == null) "Drag the world view to look around your grave"
            else "${SpectatorClient.controls.translatedKeyMessage.string}: spectator controls",
            width / 2,
            height - 17,
            0xffb5c0ce.toInt(),
        )
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }

    override fun mouseDragged(event: MouseButtonEvent, x: Double, y: Double): Boolean {
        if (SpectatorClient.state?.target == null && event.y() < height - 124) {
            GraveClient.turn(x, y)
            return true
        }
        return super.mouseDragged(event, x, y)
    }

    private fun seconds(ticks: Long) = (if (ticks == 0L) 0 else 1 + (ticks - 1) / 20).toString()
}
