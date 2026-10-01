package dev.conclave.fabric.mixin;

import dev.conclave.fabric.RevivalProtectionHooks;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.KillCommand;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A static native boundary. The policy and state remain in Kotlin. */
@Mixin(KillCommand.class)
abstract class KillProtectionMixin {
    @Inject(method = "kill", at = @At("HEAD"))
    private static void conclaveAdministrativeKill(
            CommandSourceStack source, Collection<Entity> entities,
            CallbackInfoReturnable<Integer> callback) {
        RevivalProtectionHooks.administrativeKill(source, entities);
    }
}
