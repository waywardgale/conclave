package dev.conclave.fabric.mixin

import dev.conclave.fabric.RevivalProtectionHooks
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.EggItem
import net.minecraft.world.item.SnowballItem
import net.minecraft.world.item.ThrowablePotionItem
import net.minecraft.world.item.WindChargeItem
import net.minecraft.world.level.Level
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(SnowballItem::class, EggItem::class, WindChargeItem::class, ThrowablePotionItem::class)
abstract class HostileThrowMixin {
    @Inject(method = ["use"], at = [At("HEAD")])
    private fun conclaveThrow(
        level: Level,
        player: Player,
        hand: InteractionHand,
        callback: CallbackInfoReturnable<InteractionResult>,
    ) {
        val stack = player.getItemInHand(hand)
        if (stack.isEmpty) return
        if (
            stack.item is ThrowablePotionItem &&
                stack
                    .get(net.minecraft.core.component.DataComponents.POTION_CONTENTS)
                    ?.allEffects
                    ?.none {
                        it.effect.value().category ==
                            net.minecraft.world.effect.MobEffectCategory.HARMFUL
                    } != false
        )
            return
        RevivalProtectionHooks.hostile(player)
    }
}
