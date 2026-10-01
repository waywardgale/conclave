package dev.conclave.fabric.mixin

import net.minecraft.world.entity.Interaction
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Invoker

@Mixin(Interaction::class)
interface GraveInteractionAccess {
    @Invoker("setWidth") fun conclaveWidth(value: Float)

    @Invoker("setHeight") fun conclaveHeight(value: Float)
}
