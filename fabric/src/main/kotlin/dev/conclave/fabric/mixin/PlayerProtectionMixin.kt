package dev.conclave.fabric.mixin

import dev.conclave.fabric.*
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(ServerPlayer::class)
abstract class PlayerProtectionMixin : ConclavePlayerProtection {
    @Unique private var conclaveProtected: RevivalProtection? = null

    override fun conclaveProtection() = conclaveProtected

    override fun conclaveProtection(value: RevivalProtection?) {
        conclaveProtected = value
    }

    @Inject(method = ["hurtServer"], at = [At("HEAD")], cancellable = true)
    private fun conclaveDamage(
        level: ServerLevel,
        source: DamageSource,
        amount: Float,
        result: CallbackInfoReturnable<Boolean>,
    ) {
        if (RevivalProtectionHooks.prevent(this as Any as ServerPlayer)) result.returnValue = false
    }

    @Inject(method = ["addAdditionalSaveData"], at = [At("TAIL")])
    private fun conclaveWrite(output: ValueOutput, callback: CallbackInfo) {
        conclaveProtected?.let {
            val child = output.child("conclave:revival_protection")
            child.putInt("version", 1)
            child.putLong("began", it.began)
            child.putLong("until", it.until)
        }
    }

    @Inject(method = ["readAdditionalSaveData"], at = [At("TAIL")])
    private fun conclaveRead(input: ValueInput, callback: CallbackInfo) {
        conclaveProtected =
            input.child("conclave:revival_protection").orElse(null)?.let {
                check(it.getIntOr("version", -1) == 1)
                RevivalProtection(it.getLongOr("began", -1), it.getLongOr("until", -1))
            }
    }

    @Inject(method = ["restoreFrom"], at = [At("TAIL")])
    private fun conclaveCopy(previous: ServerPlayer, alive: Boolean, callback: CallbackInfo) {
        conclaveProtected = (previous as ConclavePlayerProtection).conclaveProtection()
    }
}
