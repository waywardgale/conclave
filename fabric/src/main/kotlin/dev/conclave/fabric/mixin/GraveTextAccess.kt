package dev.conclave.fabric.mixin

import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Display
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Invoker

@Mixin(Display.TextDisplay::class)
interface GraveTextAccess {
    @Invoker("setText") fun conclaveText(value: Component)
}
