package dev.conclave.fabric.mixin

import dev.conclave.fabric.ConclavePlayerReceipt
import java.util.UUID
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ServerPlayer::class)
abstract class PlayerRecoveryReceiptMixin : ConclavePlayerReceipt {
    @Unique private var conclaveReceipt: UUID? = null

    override fun conclaveRecoveryReceipt() = conclaveReceipt

    override fun conclaveRecoveryReceipt(value: UUID?) {
        conclaveReceipt = value
    }

    @Inject(method = ["addAdditionalSaveData"], at = [At("TAIL")])
    private fun conclaveWriteReceipt(output: ValueOutput, callback: CallbackInfo) {
        conclaveReceipt?.let { output.putString("conclave:recovery_receipt", it.toString()) }
    }

    @Inject(method = ["readAdditionalSaveData"], at = [At("TAIL")])
    private fun conclaveReadReceipt(input: ValueInput, callback: CallbackInfo) {
        conclaveReceipt =
            input.getString("conclave:recovery_receipt").orElse(null)?.let(UUID::fromString)
    }

    @Inject(method = ["restoreFrom"], at = [At("TAIL")])
    private fun conclaveCopyReceipt(
        previous: ServerPlayer,
        alive: Boolean,
        callback: CallbackInfo,
    ) {
        conclaveReceipt = (previous as ConclavePlayerReceipt).conclaveRecoveryReceipt()
    }
}
