package dev.conclave.fabric

import dev.conclave.core.CatalogCompiler
import dev.conclave.core.CompiledCatalog
import dev.conclave.core.Diagnostic
import dev.conclave.core.SourceLocation
import dev.conclave.core.Validation
import dev.conclave.storage.AttemptStore
import dev.conclave.storage.AuthorityStore
import dev.conclave.storage.ContentStore
import dev.conclave.storage.Database
import dev.conclave.storage.DraftStore
import dev.conclave.storage.RewardLedger
import dev.conclave.storage.StoredAttempt
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import org.slf4j.LoggerFactory

/**
 * Per-world state. No static gameplay catalog or GM membership can leak between integrated worlds.
 */
internal class ServerSession
private constructor(val server: MinecraftServer, private val database: Database) : AutoCloseable {
    val content = ContentStore(database)
    val authority = AuthorityStore(database)
    val rewards = RewardLedger(database)
    val attempts = AttemptStore(database, rewards = rewards)
    val drafts = DraftStore(database)
    val authoring = AuthoringServer(this)
    val chunks = NativeChunkClaims(server)
    val playerSaves = NativePlayerSave(server)
    val recovery = NativeRecovery(this)
    val runtime = NativeAttempts(this)
    val interactions = NativeInteractions(this)
    val graves = NativeGraves(this)
    val spectating = NativeSpectating(this)
    val protection = NativeRevivalProtection(this)
    var interruptedAttempts: List<StoredAttempt> = emptyList()
        private set

    val gameMasters = mutableSetOf<UUID>()
    private val membershipVersions = mutableMapOf<UUID, Long>()
    var catalog: CompiledCatalog? = null
        private set

    var failure: String? = null
        private set

    var contentDiagnostics: List<Diagnostic> = emptyList()
        private set

    @Volatile private var acceptingCallbacks = true

    private fun prepare() {
        gameMasters += authority.members().join()
        rewards.recoverTransfers().join()
        interruptedAttempts = attempts.interrupted().join()
        val saved = content.current().join() ?: return
        when (val validation = CatalogCompiler().compile(saved.sources)) {
            is Validation.Valid -> {
                if (validation.value.revision == saved.id) catalog = validation.value
                else
                    contentDiagnostics =
                        listOf(
                            Diagnostic(
                                "compiled_identity_changed",
                                "Published content needs compatibility review before encounters can start",
                                SourceLocation("<publication>"),
                            )
                        )
            }
            is Validation.Invalid -> contentDiagnostics = validation.diagnostics
        }
    }

    fun setGameMaster(actor: String, player: UUID, grant: Boolean, done: (Boolean) -> Unit) {
        check(server.isSameThread)
        val version =
            Math.incrementExact(membershipVersions[player] ?: 0).also {
                membershipVersions[player] = it
            }
        // Revocation closes access immediately; a delayed grant completion cannot reopen a newer
        // revocation.
        if (!grant) gameMasters.remove(player)
        complete(authority.setGameMaster(actor, player, grant)) { _, error ->
            if (error != null) {
                failure = "Administrative storage is unavailable. Privileged changes are disabled."
                gameMasters.clear()
                done(false)
            } else {
                if (membershipVersions[player] == version && grant) gameMasters += player
                done(true)
            }
        }
    }

    fun <T> complete(operation: CompletableFuture<T>, callback: (T?, Throwable?) -> Unit) {
        operation.whenComplete { result, error ->
            if (acceptingCallbacks)
                server.execute {
                    // A stopped native event loop may execute a submitted task on its caller
                    // thread.
                    // Durable records survive shutdown; native mutations must await the next
                    // session.
                    if (acceptingCallbacks && server.isSameThread && sessions[server] === this)
                        callback(result, error)
                }
        }
    }

    fun installPublished(value: CompiledCatalog) {
        check(server.isSameThread)
        catalog = value
        contentDiagnostics = emptyList()
    }

    override fun close() {
        interactions.close()
        graves.close()
        runtime.close()
        recovery.close()
        authoring.close()
        playerSaves.close()
        database.close()
    }

    companion object {
        private val sessions = ConcurrentHashMap<MinecraftServer, ServerSession>()
        private val logger = LoggerFactory.getLogger("Conclave")

        fun get(server: MinecraftServer): ServerSession? = sessions[server]

        fun register() {
            ServerLifecycleEvents.SERVER_STARTING.register { server ->
                try {
                    val session =
                        ServerSession(
                            server,
                            Database.open(
                                server
                                    .getWorldPath(LevelResource.ROOT)
                                    .resolve("conclave/conclave.db")
                            ),
                        )
                    try {
                        session.prepare()
                        sessions[server] = session
                    } catch (failure: Throwable) {
                        session.close()
                        throw failure
                    }
                } catch (failure: Exception) {
                    logger.error(
                        "Conclave storage preparation failed; native ownership cannot be recovered",
                        failure,
                    )
                    throw IllegalStateException(
                        "Conclave ownership storage is unavailable; server startup must stop before players enter",
                        failure,
                    )
                }
            }
            ServerLifecycleEvents.SERVER_STARTED.register { server ->
                sessions[server]?.runtime?.initialize()
            }
            net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register {
                previous,
                current,
                _ ->
                sessions[current.level().server]?.let {
                    it.interactions.disconnected(previous)
                    it.runtime.replaced(previous, current)
                    it.graves.replaced(previous, current)
                }
            }
            net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register {
                handler,
                server ->
                sessions[server]?.let {
                    it.interactions.disconnected(handler.player)
                    it.runtime.disconnected(handler.player)
                    it.graves.disconnected(handler.player)
                }
            }
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register {
                server ->
                sessions[server]?.interactions?.tick()
                sessions[server]?.runtime?.tick()
                sessions[server]?.recovery?.tick()
                sessions[server]?.graves?.tick()
                sessions[server]?.spectating?.tickObservers()
                sessions[server]?.protection?.tick()
            }
            ServerLifecycleEvents.SERVER_STOPPING.register { server ->
                sessions[server]?.let {
                    it.acceptingCallbacks = false
                    it.runtime.shutdown()
                    it.chunks.close()
                }
            }
            ServerLifecycleEvents.SERVER_STOPPED.register { server ->
                sessions.remove(server)?.close()
            }
        }
    }
}
