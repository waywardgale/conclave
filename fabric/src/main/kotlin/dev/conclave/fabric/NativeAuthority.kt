package dev.conclave.fabric

import dev.conclave.core.Principal
import dev.conclave.fabric.mixin.CommandSourceAccess
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.players.NameAndId

internal object NativeAuthority {
    fun principal(stack: CommandSourceStack): Principal {
        val origin = (stack as CommandSourceAccess).conclaveOriginalSource()
        // Vanilla probes command requirements with a synthetic stack while building its command
        // tree.
        val nativeServer: net.minecraft.server.MinecraftServer? = stack.server
        val server = nativeServer ?: return Principal.Unsupported
        if (origin === server) return Principal.Console
        // ASVS 8.3.1, 8.3.3: /execute may replace an entity or permissions, never the initiating
        // identity.
        val player =
            server.playerList.players.firstOrNull {
                (it.createCommandSourceStack() as CommandSourceAccess).conclaveOriginalSource() ===
                    origin
            } ?: return Principal.Unsupported
        return Principal.Player(player.uuid, server.playerList.isOp(NameAndId(player.gameProfile)))
    }
}
