package dev.conclave.fabric

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import dev.conclave.core.AuthorityPolicy
import java.util.UUID
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/** Privileged revival names one current death; delayed audit acknowledgement cannot retarget it. */
internal object RevivalCommand {
    fun register(
        root: LiteralArgumentBuilder<CommandSourceStack>,
        authorized: (CommandSourceStack) -> Boolean,
    ) {
        root.then(
            Commands.literal("revive")
                .requires(authorized)
                .then(
                    Commands.argument("attempt", StringArgumentType.word())
                        .suggests { context, builder ->
                            if (authorized(context.source))
                                ServerSession.get(context.source.server)
                                    ?.runtime
                                    ?.views()
                                    ?.filter { it.state == "running" }
                                    ?.forEach {
                                        builder.suggest(
                                            it.id.toString(),
                                            Component.literal("${it.arena} / ${it.encounter}"),
                                        )
                                    }
                            builder.buildFuture()
                        }
                        .then(
                            Commands.argument("player", StringArgumentType.word())
                                .suggests { context, builder ->
                                    if (authorized(context.source)) {
                                        val session = ServerSession.get(context.source.server)
                                        val attempt = attempt(context)
                                        if (session != null && attempt != null)
                                            session.server.playerList.players.forEach { player ->
                                                if (
                                                    session.graves.administrativeTarget(
                                                        attempt,
                                                        player,
                                                    ) != null
                                                )
                                                    builder.suggest(
                                                        player.scoreboardName,
                                                        Component.literal(
                                                            "${player.uuid}: current grave"
                                                        ),
                                                    )
                                            }
                                    }
                                    builder.buildFuture()
                                }
                                .executes { context ->
                                    val source = context.source
                                    if (!authorized(source)) return@executes 0
                                    val session = checkNotNull(ServerSession.get(source.server))
                                    val id = attempt(context)
                                    val player = player(context)
                                    val target =
                                        if (id != null && player != null)
                                            session.graves.administrativeTarget(id, player)
                                        else null
                                    if (target == null) {
                                        source.sendFailure(
                                            Component.literal(
                                                "Choose an online roster member with a current grave in that live attempt. Living players are not healed."
                                            )
                                        )
                                        return@executes 0
                                    }
                                    val actor =
                                        AuthorityPolicy.auditId(NativeAuthority.principal(source))
                                    val identity =
                                        "${target.attempt}/${target.player.uuid}/${target.grave}"
                                    session.complete(
                                        session.authority.record(
                                            actor,
                                            "player_revive",
                                            identity,
                                            "requested",
                                        )
                                    ) { _, error ->
                                        if (!authorized(source)) {
                                            session.complete(
                                                session.authority.record(
                                                    actor,
                                                    "player_revive",
                                                    identity,
                                                    "authority_changed",
                                                )
                                            ) { _, _ ->
                                            }
                                            return@complete
                                        }
                                        if (error != null) {
                                            source.sendFailure(
                                                Component.literal(
                                                    "Revival was not attempted because its audit record could not be saved."
                                                )
                                            )
                                            return@complete
                                        }
                                        val result =
                                            try {
                                                session.graves.reviveAdministrative(target)
                                            } catch (_: Exception) {
                                                NativeGraves.AdministrativeResult(
                                                    false,
                                                    "Revival encountered a native failure. The attempt entered owned recovery.",
                                                )
                                            }
                                        if (result.revived)
                                            source.sendSuccess(
                                                { Component.literal(result.message) },
                                                false,
                                            )
                                        else source.sendFailure(Component.literal(result.message))
                                        session.complete(
                                            session.authority.record(
                                                actor,
                                                "player_revive",
                                                identity,
                                                if (result.revived) "revived" else "not_applied",
                                            )
                                        ) { _, auditError ->
                                            if (auditError != null && authorized(source))
                                                source.sendFailure(
                                                    Component.literal(
                                                        "The revival request is recorded, but saving its result requires storage recovery."
                                                    )
                                                )
                                        }
                                    }
                                    1
                                }
                        )
                )
        )
    }

    private fun attempt(context: CommandContext<CommandSourceStack>): UUID? = runCatching {
        UUID.fromString(StringArgumentType.getString(context, "attempt"))
    }
        .getOrNull()

    private fun player(context: CommandContext<CommandSourceStack>): ServerPlayer? {
        val name = StringArgumentType.getString(context, "player")
        return context.source.server.playerList.getPlayerByName(name)
            ?: runCatching { context.source.server.playerList.getPlayer(UUID.fromString(name)) }
                .getOrNull()
    }
}
