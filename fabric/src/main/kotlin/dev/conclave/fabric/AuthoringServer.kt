package dev.conclave.fabric

import dev.conclave.core.*
import dev.conclave.storage.*
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory

internal class AuthoringServer(private val session: ServerSession) : AutoCloseable {
    private val drafts = session.drafts
    private val content = session.content
    private val uploads = ChunkAssembler()

    private data class Download(
        val player: ServerPlayer,
        val frames: ArrayDeque<TransferChunk>,
        val total: Int,
        val denied: Boolean,
    )

    private val downloads = linkedMapOf<UUID, Download>()
    private var downloadBytes = 0
    private var tick = 0L
    private val busy = mutableSetOf<UUID>()
    private val inputBytes = mutableMapOf<UUID, Int>()
    private val inputFrames = mutableMapOf<UUID, Int>()
    private var totalFrames = 0
    private val compiler =
        ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(16),
            { action -> Thread(action, "conclave-compiler").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy(),
        )

    private fun operator(player: ServerPlayer) =
        AuthorityPolicy.operator(NativeAuthority.principal(player.createCommandSourceStack()))

    private fun allowed(player: ServerPlayer, publication: Boolean = false) =
        session.failure == null &&
            session.server.playerList.getPlayer(player.uuid) === player &&
            if (publication) operator(player)
            else
                AuthorityPolicy.gameMaster(
                    NativeAuthority.principal(player.createCommandSourceStack()),
                    session.gameMasters,
                )

    fun accept(player: ServerPlayer, chunk: TransferChunk) {
        check(session.server.isSameThread)
        val frames = (inputFrames[player.uuid] ?: 0) + 1
        inputFrames[player.uuid] = frames
        if (++totalFrames > 512 || frames > 32) {
            uploads.discard(player.uuid)
            return
        }
        if (!allowed(player)) {
            discard(player.uuid)
            send(
                player,
                chunk.transfer,
                AuthorReply(AuthorResult.DENIED, "Conclave editing access is unavailable."),
            )
            return
        }
        val received = (inputBytes[player.uuid] ?: 0) + chunk.bytes.size
        inputBytes[player.uuid] = received
        if (received > 393_216) {
            uploads.discard(player.uuid)
            return
        }
        try {
            val bytes = uploads.accept(player.uuid, chunk, tick) ?: return
            perform(player, chunk.transfer, AuthorProtocol.request(bytes)) {
                send(player, chunk.transfer, it)
            }
        } catch (failure: Exception) {
            uploads.discard(player.uuid)
            send(
                player,
                chunk.transfer,
                AuthorReply(
                    AuthorResult.ERROR,
                    "Invalid or incomplete authoring request. Reopen the draft and retry.",
                ),
            )
        }
    }

    fun open(player: ServerPlayer) {
        val operation = UUID.randomUUID()
        perform(player, operation, AuthorRequest.ListDrafts) { send(player, operation, it) }
    }

    /** Commands and screens share this operation path and its permission/version checks. */
    fun perform(
        player: ServerPlayer,
        operation: UUID,
        request: AuthorRequest,
        reply: (AuthorReply) -> Unit,
    ) {
        check(session.server.isSameThread)
        val publication = request is AuthorRequest.Publish || request is AuthorRequest.Rollback
        if (!allowed(player, publication)) {
            reply(
                AuthorReply(
                    AuthorResult.DENIED,
                    "This operation requires current ${if(publication)"operator" else "game master"} authority.",
                )
            )
            return
        }
        if (!busy.add(player.uuid)) {
            reply(AuthorReply(AuthorResult.ERROR, "An authoring operation is still in progress."))
            return
        }
        fun finish(value: AuthorReply) {
            busy.remove(player.uuid)
            if (allowed(player, publication)) reply(value.copy(operator = operator(player)))
            else
                reply(
                    AuthorReply(
                        AuthorResult.DENIED,
                        "Authoring access changed. Your unsaved text remains local.",
                    )
                )
        }
        fun failed(error: Throwable) {
            logger.warn(
                "Conclave authoring operation {} failed for {}",
                operation,
                player.uuid,
                error,
            )
            finish(
                AuthorReply(
                    AuthorResult.ERROR,
                    "The operation could not be completed. Saved drafts and the active revision remain available for inspection.",
                )
            )
        }
        fun <T> complete(future: CompletableFuture<T>, action: (T) -> Unit) {
            session.complete(future) { value, error ->
                if (!allowed(player, publication)) {
                    finish(AuthorReply(AuthorResult.DENIED, "Authoring access changed."))
                    return@complete
                }
                if (error != null) failed(error)
                else
                    try {
                        action(checkNotNull(value))
                    } catch (failure: Exception) {
                        failed(failure)
                    }
            }
        }
        fun openDraft(
            id: UUID,
            message: String = "Draft opened.",
            result: AuthorResult = AuthorResult.OK,
        ) {
            session.complete(drafts.read(id, player.uuid, operator(player))) { value, error ->
                if (error != null) failed(error)
                else if (value == null)
                    finish(AuthorReply(AuthorResult.DENIED, "Draft is unavailable."))
                else finish(AuthorReply(result, message, draft = value))
            }
        }
        fun compiled(snapshot: DraftSnapshot, publish: Boolean) {
            complete(
                CompletableFuture.supplyAsync(
                    { CatalogCompiler().compile(snapshot.sources()) },
                    compiler,
                )
            ) { validation ->
                when (validation) {
                    is Validation.Invalid ->
                        finish(
                            AuthorReply(
                                AuthorResult.INVALID,
                                "Draft has ${validation.diagnostics.size} validation problems.",
                                draft = snapshot,
                                diagnostics = validation.diagnostics.take(256),
                            )
                        )
                    is Validation.Valid ->
                        if (!publish)
                            finish(
                                AuthorReply(
                                    AuthorResult.OK,
                                    "Draft version ${snapshot.summary.version} is valid." +
                                        if (validation.value.warnings.isEmpty()) ""
                                        else " ${validation.value.warnings.size} warning(s).",
                                    draft = snapshot,
                                    diagnostics = validation.value.warnings.take(256),
                                )
                            )
                        else {
                            complete(
                                content.publish(
                                    operation,
                                    player.uuid.toString(),
                                    snapshot.summary.baseline,
                                    validation.value,
                                    DraftPublication(snapshot.summary.id, snapshot.summary.version),
                                )
                            ) { result ->
                                when (result) {
                                    is SavedPublication.Published ->
                                        session.complete(content.current()) { current, error ->
                                            if (error != null) failed(error)
                                            else {
                                                if (current?.id == validation.value.revision)
                                                    session.installPublished(validation.value)
                                                openDraft(
                                                    snapshot.summary.id,
                                                    "Published for future attempts. Current attempts keep their captured content.",
                                                )
                                            }
                                        }
                                    is SavedPublication.Conflict ->
                                        openDraft(
                                            snapshot.summary.id,
                                            "Another revision was published. Reconcile the draft's baseline before publishing.",
                                            AuthorResult.CONFLICT,
                                        )
                                    is SavedPublication.DraftConflict ->
                                        openDraft(
                                            snapshot.summary.id,
                                            "The draft changed during validation. Review its current version.",
                                            AuthorResult.CONFLICT,
                                        )
                                }
                            }
                        }
                }
            }
        }
        try {
            when (request) {
                AuthorRequest.ListDrafts ->
                    complete(drafts.list(player.uuid, operator(player))) {
                        finish(
                            AuthorReply(
                                AuthorResult.OK,
                                "Choose or create a draft.",
                                drafts = it,
                                current = session.catalog?.revision,
                            )
                        )
                    }
                is AuthorRequest.Create ->
                    complete(drafts.create(request.id, player.uuid, request.name, request.shared)) {
                        finish(
                            AuthorReply(
                                AuthorResult.OK,
                                "Draft created from the current publication.",
                                draft = it,
                            )
                        )
                    }
                is AuthorRequest.Open -> openDraft(request.draft)
                is AuthorRequest.Save ->
                    complete(
                        drafts.save(
                            request.draft,
                            player.uuid,
                            operator(player),
                            operation,
                            request.changes,
                            request.version,
                        )
                    ) { result ->
                        when (result) {
                            is DraftSave.Saved ->
                                openDraft(request.draft, "Saved draft version ${result.version}.")
                            is DraftSave.Conflict ->
                                finish(
                                    AuthorReply(
                                        AuthorResult.CONFLICT,
                                        "The server file changed. Your buffer has been retained; review both versions before saving.",
                                        draft = result.actual,
                                    )
                                )
                            DraftSave.Unavailable ->
                                finish(AuthorReply(AuthorResult.DENIED, "Draft is unavailable."))
                            DraftSave.OperatorRequired ->
                                finish(
                                    AuthorReply(
                                        AuthorResult.INVALID,
                                        "Only a server operator can change global gameplay settings.",
                                    )
                                )
                        }
                    }
                is AuthorRequest.Validate,
                is AuthorRequest.Publish -> {
                    val id =
                        if (request is AuthorRequest.Validate) request.draft
                        else (request as AuthorRequest.Publish).draft
                    val version =
                        if (request is AuthorRequest.Validate) request.version
                        else (request as AuthorRequest.Publish).version
                    session.complete(drafts.read(id, player.uuid, operator(player))) { value, error
                        ->
                        if (error != null) failed(error)
                        else if (!allowed(player, publication) || value == null)
                            finish(AuthorReply(AuthorResult.DENIED, "Draft is unavailable."))
                        else if (value.summary.version != version)
                            finish(
                                AuthorReply(
                                    AuthorResult.CONFLICT,
                                    "The draft changed. Review this version first.",
                                    draft = value,
                                )
                            )
                        else
                            try {
                                compiled(value, publication)
                            } catch (failure: Exception) {
                                failed(failure)
                            }
                    }
                }
                AuthorRequest.History ->
                    complete(content.history()) {
                        finish(
                            AuthorReply(
                                AuthorResult.OK,
                                "Retained whole-catalog revisions.",
                                history = it.take(256),
                                current = session.catalog?.revision,
                            )
                        )
                    }
                is AuthorRequest.Rollback ->
                    session.complete(content.revision(request.revision)) { saved, error ->
                        if (error != null) failed(error)
                        else if (saved == null)
                            finish(
                                AuthorReply(
                                    AuthorResult.ERROR,
                                    "That revision is no longer retained.",
                                )
                            )
                        else if (!allowed(player, true))
                            finish(AuthorReply(AuthorResult.DENIED, "Operator access changed."))
                        else
                            try {
                                complete(
                                    CompletableFuture.supplyAsync(
                                        { CatalogCompiler().compile(saved.sources) },
                                        compiler,
                                    )
                                ) { validation ->
                                    when (validation) {
                                        is Validation.Invalid ->
                                            finish(
                                                AuthorReply(
                                                    AuthorResult.INVALID,
                                                    "The retained revision is incompatible with this build.",
                                                    diagnostics = validation.diagnostics.take(256),
                                                )
                                            )
                                        is Validation.Valid ->
                                            if (validation.value.revision != saved.id)
                                                finish(
                                                    AuthorReply(
                                                        AuthorResult.INVALID,
                                                        "The revision requires a separate reviewed content upgrade.",
                                                    )
                                                )
                                            else
                                                complete(
                                                    content.publish(
                                                        operation,
                                                        player.uuid.toString(),
                                                        request.baseline,
                                                        validation.value,
                                                    )
                                                ) { result ->
                                                    if (result is SavedPublication.Published)
                                                        session.complete(content.current()) {
                                                            current,
                                                            failure ->
                                                            if (failure != null) failed(failure)
                                                            else {
                                                                if (current?.id == saved.id)
                                                                    session.installPublished(
                                                                        validation.value
                                                                    )
                                                                finish(
                                                                    AuthorReply(
                                                                        AuthorResult.OK,
                                                                        "Rollback published for future attempts.",
                                                                        current = current?.id,
                                                                    )
                                                                )
                                                            }
                                                        }
                                                    else
                                                        finish(
                                                            AuthorReply(
                                                                AuthorResult.CONFLICT,
                                                                "The active revision changed. Refresh history before rolling back.",
                                                            )
                                                        )
                                                }
                                    }
                                }
                            } catch (failure: Exception) {
                                failed(failure)
                            }
                    }
                is AuthorRequest.Share ->
                    complete(
                        drafts.share(
                            request.draft,
                            player.uuid,
                            operator(player),
                            request.version,
                            request.shared,
                        )
                    ) {
                        if (it) openDraft(request.draft, "Draft sharing updated.")
                        else
                            finish(
                                AuthorReply(
                                    AuthorResult.CONFLICT,
                                    "Draft sharing requires its owner or an operator and the current version.",
                                )
                            )
                    }
            }
        } catch (failure: Exception) {
            failed(failure)
        }
    }

    private fun send(player: ServerPlayer, operation: UUID, reply: AuthorReply) {
        if (
            session.server.playerList.getPlayer(player.uuid) !== player ||
                downloads.containsKey(player.uuid)
        )
            return
        val bytes = AuthorProtocol.encode(reply)
        if (downloadBytes + bytes.size > 33_554_432) return
        downloads[player.uuid] =
            Download(
                player,
                ArrayDeque(TransferChunk.split(operation, bytes)),
                bytes.size,
                reply.result == AuthorResult.DENIED,
            )
        downloadBytes += bytes.size
    }

    fun tick() {
        tick++
        inputBytes.clear()
        inputFrames.clear()
        totalFrames = 0
        uploads.expire(tick)
        uploads
            .connections()
            .filter { session.server.playerList.getPlayer(it)?.let(::allowed) != true }
            .forEach(uploads::discard)
        var remaining = 16
        for ((id, download) in downloads.toMap()) {
            if (session.server.playerList.getPlayer(id) !== download.player) {
                discard(id)
                continue
            }
            if (!allowed(download.player) && !download.denied) {
                discard(id)
                send(
                    download.player,
                    UUID.randomUUID(),
                    AuthorReply(AuthorResult.DENIED, "Editing access was revoked."),
                )
                continue
            }
            repeat(minOf(4, download.frames.size, remaining)) {
                ServerPlayNetworking.send(
                    download.player,
                    AuthorPayload(download.frames.removeFirst()),
                )
                remaining--
            }
            if (download.frames.isEmpty()) {
                downloads.remove(id)
                downloadBytes -= download.total
            }
            if (remaining == 0) break
        }
    }

    fun discard(player: UUID) {
        uploads.discard(player)
        downloads.remove(player)?.let { downloadBytes -= it.total }
        inputBytes.remove(player)
    }

    override fun close() {
        compiler.shutdownNow()
        downloads.clear()
        uploads.connections().forEach(uploads::discard)
    }

    companion object {
        private val logger = LoggerFactory.getLogger("Conclave")

        fun register() {
            PayloadTypeRegistry.serverboundPlay().register(AuthorPayload.TYPE, AuthorPayload.CODEC)
            PayloadTypeRegistry.clientboundPlay().register(AuthorPayload.TYPE, AuthorPayload.CODEC)
            ServerPlayNetworking.registerGlobalReceiver(AuthorPayload.TYPE) { payload, context ->
                ServerSession.get(context.server())
                    ?.authoring
                    ?.accept(context.player(), payload.chunk)
            }
            ServerTickEvents.END_SERVER_TICK.register { ServerSession.get(it)?.authoring?.tick() }
            ServerPlayConnectionEvents.DISCONNECT.register { handler, server ->
                ServerSession.get(server)?.authoring?.discard(handler.player.uuid)
            }
        }
    }
}
