package dev.conclave.fabric.mixin

import dev.conclave.fabric.RevivalProtectionHooks
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TridentItem
import net.minecraft.world.level.Level
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(TridentItem::class)
abstract class HostileTridentMixin {
    @Inject(
        method = ["releaseUsing"],
        at =
            [
                At(
                    value = "INVOKE",
                    target =
                        "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;pickHighestLevel(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/component/DataComponentType;)Ljava/util/Optional;",
                )
            ],
    )
    private fun conclaveRelease(
        stack: ItemStack,
        level: Level,
        shooter: LivingEntity,
        remaining: Int,
        callback: CallbackInfoReturnable<Boolean>,
    ) {
        RevivalProtectionHooks.hostile(shooter)
    }
}
