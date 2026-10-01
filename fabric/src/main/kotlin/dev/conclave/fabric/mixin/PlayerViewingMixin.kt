package dev.conclave.fabric.mixin

import dev.conclave.fabric.*
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ServerPlayer::class)
abstract class PlayerViewingMixin : ConclavePlayerViewing {
    @Unique private var conclaveView: PlayerViewingState? = null

    override fun conclaveViewing() = conclaveView

    override fun conclaveViewing(state: PlayerViewingState?) {
        conclaveView = state
    }

    @Inject(method = ["addAdditionalSaveData"], at = [At("TAIL")])
    private fun conclaveWrite(output: ValueOutput, callback: CallbackInfo) {
        conclaveView?.write(output.child(PlayerViewingState.TAG))
    }

    @Inject(method = ["readAdditionalSaveData"], at = [At("TAIL")])
    private fun conclaveRead(input: ValueInput, callback: CallbackInfo) {
        conclaveView =
            input.child(PlayerViewingState.TAG).orElse(null)?.let(PlayerViewingState::read)
    }

    @Inject(method = ["restoreFrom"], at = [At("TAIL")])
    private fun conclaveCopy(previous: ServerPlayer, alive: Boolean, callback: CallbackInfo) {
        conclaveView = (previous as ConclavePlayerViewing).conclaveViewing()
    }

    @Inject(method = ["setCamera"], at = [At("HEAD")], cancellable = true)
    private fun conclaveCamera(target: Entity?, callback: CallbackInfo) {
        if (SpectatorNativeHooks.blockCamera(this as Any as ServerPlayer)) callback.cancel()
    }
}
