package dev.conclave.fabric.mixin

import dev.conclave.fabric.InteractionNativeHooks
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(LevelChunk::class)
abstract class InteractionBlockMixin {
    @Inject(method = ["setBlockState"], at = [At("RETURN")])
    private fun conclaveReplaced(
        position: BlockPos,
        state: BlockState,
        flags: Int,
        callback: CallbackInfoReturnable<BlockState?>,
    ) {
        val chunk = this as Any as LevelChunk
        val level = chunk.level as? ServerLevel ?: return
        val previous = callback.returnValue ?: return
        if (previous.block !== state.block)
            InteractionNativeHooks.blockChanged(level, position, previous, state)
    }
}
