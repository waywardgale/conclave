package dev.conclave.fabric.mixin

import net.minecraft.world.entity.Display
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Invoker

@Mixin(Display::class)
interface GraveDisplayAccess {
    @Invoker("setBillboardConstraints") fun conclaveBillboard(value: Display.BillboardConstraints)
}
