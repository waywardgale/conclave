package dev.conclave.fabric.mixin

import dev.conclave.fabric.RevivalProtectionHooks
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(Player::class)
abstract class HostileMeleeMixin {
    @Inject(
        method = ["attack"],
        at =
            [
                At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;onAttack()V",
                )
            ],
    )
    private fun conclaveAttack(target: Entity, callback: CallbackInfo) {
        val player = this as Any
        if (player is ServerPlayer && !player.isAutoSpinAttack)
            RevivalProtectionHooks.hostile(player)
    }

    @Inject(
        method = ["stabAttack"],
        at =
            [
                At(
                    value = "INVOKE",
                    target =
                        "Lnet/minecraft/world/entity/player/Player;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;",
                )
            ],
    )
    private fun conclaveStab(
        slot: EquipmentSlot,
        target: Entity,
        damage: Float,
        hurts: Boolean,
        knockback: Boolean,
        dismount: Boolean,
        callback: CallbackInfoReturnable<Boolean>,
    ) {
        val player = this as Any
        if (player is ServerPlayer) RevivalProtectionHooks.stab(player)
    }
}
