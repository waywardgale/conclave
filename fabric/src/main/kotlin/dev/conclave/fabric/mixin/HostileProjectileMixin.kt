package dev.conclave.fabric.mixin

import dev.conclave.fabric.RevivalProtectionHooks
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ProjectileWeaponItem
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ProjectileWeaponItem::class)
abstract class HostileProjectileMixin {
    @Inject(method = ["shoot"], at = [At("HEAD")])
    private fun conclaveShoot(
        level: ServerLevel,
        shooter: LivingEntity,
        hand: InteractionHand,
        weapon: ItemStack,
        projectiles: List<ItemStack>,
        velocity: Float,
        inaccuracy: Float,
        critical: Boolean,
        target: LivingEntity?,
        callback: CallbackInfo,
    ) {
        if (projectiles.any { !it.isEmpty }) RevivalProtectionHooks.hostile(shooter)
    }
}
