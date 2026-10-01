package dev.conclave.fabric.mixin

import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.players.PlayerList
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Invoker

@Mixin(PlayerList::class)
interface PlayerListSaveAccess {
    @Invoker("save") fun conclaveSavePlayer(player: ServerPlayer)
}
