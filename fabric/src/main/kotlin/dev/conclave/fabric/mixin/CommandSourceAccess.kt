package dev.conclave.fabric.mixin

import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(CommandSourceStack::class)
interface CommandSourceAccess {
    @Accessor("source") fun conclaveOriginalSource(): CommandSource
}
