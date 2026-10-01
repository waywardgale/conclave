package dev.conclave.fabric.client

import dev.conclave.core.ChunkAssembler
import dev.conclave.core.TransferChunk
import dev.conclave.fabric.*
import java.util.UUID
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

internal object AuthoringClient {
    private val incoming = ChunkAssembler(TransferChunk.MAX_TOTAL)
    private val connection = UUID.randomUUID()
    private val outgoing = ArrayDeque<TransferChunk>()
    private var pending: Pair<UUID, AuthorRequest>? = null
    private var ticks = 0L
    private var began = 0L
    val busy
        get() = pending != null

    fun request(request: AuthorRequest): Boolean {
        if (busy || !ClientPlayNetworking.canSend(AuthorPayload.TYPE)) return false
        val id = UUID.randomUUID()
        pending = id to request
        began = ticks
        outgoing.addAll(TransferChunk.split(id, AuthorProtocol.encode(request)))
        return true
    }

    fun register() {
        ClientLifecycleEvents.CLIENT_STOPPING.register { client ->
            (client.gui.screen() as? AuthoringScreen)?.saveRecovery()
            LocalAuthorFiles.shared.close()
        }
        ClientPlayNetworking.registerGlobalReceiver(AuthorPayload.TYPE) { payload, context ->
            try {
                val bytes =
                    incoming.accept(connection, payload.chunk, ticks)
                        ?: return@registerGlobalReceiver
                val reply = AuthorProtocol.reply(bytes)
                val request = pending?.takeIf { it.first == payload.chunk.transfer }?.second
                if (request != null) {
                    pending = null
                    outgoing.clear()
                }
                val client = context.client()
                var screen = client.gui.screen() as? AuthoringScreen
                if (screen == null && request == null && reply.result != AuthorResult.DENIED) {
                    screen = AuthoringScreen()
                    client.gui.setScreen(screen)
                }
                screen?.receive(reply, request)
            } catch (failure: Exception) {
                incoming.discard(connection)
                pending = null
                outgoing.clear()
                (context.client().gui.screen() as? AuthoringScreen)?.message(
                    "Authoring response was incomplete. Reopen the saved draft; local buffers remain available."
                )
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            ticks++
            incoming.expire(ticks)
            if (client.connection != null)
                repeat(minOf(4, outgoing.size)) {
                    ClientPlayNetworking.send(AuthorPayload(outgoing.removeFirst()))
                }
            if (pending != null && ticks - began > 1200) {
                pending = null
                outgoing.clear()
                (client.gui.screen() as? AuthoringScreen)?.message(
                    "The response timed out. Refresh before retrying a change."
                )
            }
        }
        ClientPlayConnectionEvents.DISCONNECT.register { handler, client ->
            client.execute {
                if (client.connection != null && client.connection !== handler) return@execute
                (client.gui.screen() as? AuthoringScreen)?.saveRecovery()
                pending = null
                outgoing.clear()
                incoming.discard(connection)
            }
        }
    }
}
