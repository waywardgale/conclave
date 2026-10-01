package dev.conclave.fabric

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import dev.conclave.core.AuthorityPolicy
import dev.conclave.core.DefinitionId
import dev.conclave.core.Principal
import java.util.UUID
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.GameProfileArgument
import net.minecraft.commands.arguments.IdentifierArgument
import net.minecraft.network.chat.Component

internal object ConclaveCommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val root =
            Commands.literal("conclave").executes { context ->
                val player = context.source.player
                if (player != null)
                    ServerSession.get(context.source.server)?.authoring?.open(player)
                else
                    context.source.sendSuccess(
                        {
                            Component.literal(
                                "Conclave ${BuildDetails.describe()}. Use the in-game interface to manage drafts."
                            )
                        },
                        false,
                    )
                1
            }
        root.then(
            Commands.literal("edit")
                .requires { source ->
                    val principal = NativeAuthority.principal(source)
                    principal != dev.conclave.core.Principal.Unsupported &&
                        ServerSession.get(source.server)?.let {
                            AuthorityPolicy.gameMaster(principal, it.gameMasters)
                        } == true
                }
                .executes { context ->
                    context.source.player?.let {
                        ServerSession.get(context.source.server)?.authoring?.open(it)
                    }
                    1
                }
        )
        val gm =
            Commands.literal("gm").requires {
                AuthorityPolicy.operator(NativeAuthority.principal(it))
            }
        for ((verb, grant) in listOf("grant" to true, "revoke" to false)) {
            gm.then(
                Commands.literal(verb)
                    .then(
                        Commands.argument("player", GameProfileArgument.gameProfile()).executes {
                            context ->
                            val source = context.source
                            val principal = NativeAuthority.principal(source)
                            if (!AuthorityPolicy.operator(principal)) return@executes 0
                            val session = ServerSession.get(source.server)
                            if (session == null || session.failure != null) {
                                source.sendFailure(
                                    Component.literal(
                                        "Conclave administrative storage is unavailable."
                                    )
                                )
                                return@executes 0
                            }
                            val targets = GameProfileArgument.getGameProfiles(context, "player")
                            if (targets.size != 1) {
                                source.sendFailure(Component.literal("Choose one player."))
                                return@executes 0
                            }
                            val player = targets.single()
                            session.setGameMaster(
                                AuthorityPolicy.auditId(principal),
                                player.id(),
                                grant,
                            ) { saved ->
                                if (!AuthorityPolicy.operator(NativeAuthority.principal(source)))
                                    return@setGameMaster
                                if (saved)
                                    source.sendSuccess(
                                        {
                                            Component.literal(
                                                "${player.name()}: game master ${if (grant) "granted" else "revoked"}."
                                            )
                                        },
                                        false,
                                    )
                                else
                                    source.sendFailure(
                                        Component.literal(
                                            "The membership change could not be saved. Conclave administrative access is disabled."
                                        )
                                    )
                            }
                            1
                        }
                    )
            )
        }
        gm.then(
            Commands.literal("list").executes { context ->
                val source = context.source
                val session = ServerSession.get(source.server)
                if (session == null) {
                    source.sendFailure(Component.literal("Conclave storage is unavailable."))
                    return@executes 0
                }
                val names =
                    session.gameMasters
                        .map { id ->
                            source.server.playerList.getPlayer(id)?.plainTextName ?: id.toString()
                        }
                        .sorted()
                source.sendSuccess(
                    {
                        Component.literal(
                            if (names.isEmpty()) "No game masters are assigned."
                            else "Game masters: ${names.joinToString()}"
                        )
                    },
                    false,
                )
                names.size
            }
        )
        root.then(gm)
        val start = Commands.literal("start").requires(::gameMaster)
        start.then(
            Commands.argument("arena", IdentifierArgument.id())
                .suggests { context, builder ->
                    ServerSession.get(context.source.server)?.catalog?.arenas?.keys?.forEach {
                        builder.suggest(it.toString())
                    }
                    builder.buildFuture()
                }
                .then(
                    Commands.argument("encounter", IdentifierArgument.id())
                        .suggests { context, builder ->
                            val arena = runCatching {
                                definition(context, "arena")
                            }
                                .getOrNull()
                            ServerSession.get(context.source.server)
                                ?.catalog
                                ?.arenas
                                ?.get(arena)
                                ?.encounters
                                ?.keys
                                ?.forEach { builder.suggest(it.toString()) }
                            builder.buildFuture()
                        }
                        .executes { context ->
                            val source = context.source
                            if (!gameMaster(source)) return@executes 0
                            val session = checkNotNull(ServerSession.get(source.server))
                            val arena = runCatching {
                                definition(context, "arena")
                            }
                                .getOrNull()
                            val encounter = runCatching {
                                definition(context, "encounter")
                            }
                                .getOrNull()
                            if (arena == null || encounter == null) {
                                source.sendFailure(
                                    Component.literal(
                                        "Use a published arena and encounter identifier."
                                    )
                                )
                                return@executes 0
                            }
                            val id =
                                session.runtime.start(
                                    arena,
                                    encounter,
                                    AuthorityPolicy.auditId(NativeAuthority.principal(source)),
                                    { gameMaster(source) },
                                ) { success, message ->
                                    if (success)
                                        source.sendSuccess({ Component.literal(message) }, false)
                                    else source.sendFailure(Component.literal(message))
                                }
                            if (id != null)
                                source.sendSuccess(
                                    { Component.literal("Preparing attempt $id.") },
                                    false,
                                )
                            if (id == null) 0 else 1
                        }
                )
        )
        root.then(start)
        RevivalCommand.register(root, ::gameMaster)
        root.then(
            Commands.literal("recovery").requires(::gameMaster).executes { context ->
                if (!gameMaster(context.source)) return@executes 0
                val messages =
                    checkNotNull(ServerSession.get(context.source.server)).recovery.messages()
                if (messages.isEmpty())
                    context.source.sendSuccess(
                        { Component.literal("No participant recovery is pending.") },
                        false,
                    )
                else
                    messages.forEach { (id, message) ->
                        context.source.sendSuccess({ Component.literal("$id: $message") }, false)
                    }
                1
            }
        )
        for (verb in listOf("stop", "restart")) root.then(
            Commands.literal(verb)
                .requires(::gameMaster)
                .then(
                    Commands.argument("attempt", StringArgumentType.word())
                        .suggests { context, builder ->
                            ServerSession.get(context.source.server)?.runtime?.views()?.forEach {
                                builder.suggest(
                                    it.id.toString(),
                                    Component.literal("${it.arena} / ${it.encounter}: ${it.state}"),
                                )
                            }
                            builder.buildFuture()
                        }
                        .executes { context ->
                            val source = context.source
                            if (!gameMaster(source)) return@executes 0
                            val session = checkNotNull(ServerSession.get(source.server))
                            val id = runCatching {
                                UUID.fromString(StringArgumentType.getString(context, "attempt"))
                            }
                                .getOrNull()
                            val actor = AuthorityPolicy.auditId(NativeAuthority.principal(source))
                            val found =
                                id != null &&
                                    if (verb == "stop") session.runtime.stop(id)
                                    else
                                        session.runtime.restart(
                                            id,
                                            actor,
                                            { gameMaster(source) },
                                        ) { success, message ->
                                            if (success)
                                                source.sendSuccess(
                                                    { Component.literal(message) },
                                                    false,
                                                )
                                            else source.sendFailure(Component.literal(message))
                                        }
                            if (!found)
                                source.sendFailure(
                                    Component.literal(
                                        "Choose a current attempt from command completion."
                                    )
                                )
                            else {
                                source.sendSuccess(
                                    {
                                        Component.literal(
                                            "${verb.replaceFirstChar { it.uppercase() }} requested for $id. A frozen completion keeps its original decision while cleanup proceeds."
                                        )
                                    },
                                    false,
                                )
                                session.complete(
                                    session.authority.record(
                                        actor,
                                        "attempt_$verb",
                                        id.toString(),
                                        "requested",
                                    )
                                ) { _, error ->
                                    if (error != null && gameMaster(source))
                                        source.sendFailure(
                                            Component.literal(
                                                "The operation audit requires storage recovery."
                                            )
                                        )
                                }
                            }
                            if (found) 1 else 0
                        }
                )
        )
        dispatcher.register(root)
    }

    private fun gameMaster(source: CommandSourceStack): Boolean {
        val principal = NativeAuthority.principal(source)
        if (principal == Principal.Unsupported) return false
        val session = ServerSession.get(source.server) ?: return false
        return session.failure == null && AuthorityPolicy.gameMaster(principal, session.gameMasters)
    }

    private fun definition(
        context: CommandContext<CommandSourceStack>,
        name: String,
    ): DefinitionId {
        // Use vanilla's namespaced-token parser and Conclave's local default namespace.
        val range = context.nodes.single { it.node.name == name }.range
        return DefinitionId.parse(context.input.substring(range.start, range.end))
    }
}
