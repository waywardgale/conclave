package dev.conclave.fabric

import io.netty.buffer.Unpooled
import java.util.UUID
import kotlin.test.*
import net.minecraft.core.BlockPos
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import org.junit.jupiter.api.Test

class InteractionProtocolTest {
    @Test
    fun `native input carries only a bounded position sequence and opaque offer`() {
        val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        try {
            val id = UUID.randomUUID()
            for (action in InteractionAction.entries) {
                val value =
                    InteractionInputPayload(
                        action,
                        BlockPos(-17, 68, 31),
                        12,
                        if (action == InteractionAction.LOOK) null else id,
                    )
                InteractionInputPayload.CODEC.encode(buffer, value)
                assertEquals(value, InteractionInputPayload.CODEC.decode(buffer))
            }
            for (value in
                listOf(
                    InteractionOfferPayload(null),
                    InteractionOfferPayload(id, BlockPos(2, 3, 4), true, true),
                )) {
                InteractionOfferPayload.CODEC.encode(buffer, value)
                assertEquals(value, InteractionOfferPayload.CODEC.decode(buffer))
            }
            assertEquals(0, buffer.readableBytes())
            val progress = InteractionProgressPayload(id, true, 4, 40)
            InteractionProgressPayload.CODEC.encode(buffer, progress)
            assertEquals(progress, InteractionProgressPayload.CODEC.decode(buffer))
            assertFailsWith<IllegalArgumentException> {
                InteractionProgressPayload(id, true, 41, 40)
            }
            assertFailsWith<IllegalArgumentException> {
                InteractionProgressPayload(id, false, 0, 40)
            }
            assertFailsWith<IllegalArgumentException> {
                InteractionInputPayload(InteractionAction.PRESS, BlockPos.ZERO, 1)
            }
            assertFailsWith<IllegalArgumentException> {
                InteractionInputPayload(InteractionAction.LOOK, BlockPos.ZERO, 1, id)
            }
            buffer.writeByte(255)
            assertFailsWith<IllegalStateException> { InteractionInputPayload.CODEC.decode(buffer) }
            buffer.clear()
            buffer.writeByte(InteractionAction.LOOK.ordinal)
            buffer.writeBlockPos(BlockPos.ZERO)
            buffer.writeVarLong(0)
            assertFailsWith<IllegalArgumentException> {
                InteractionInputPayload.CODEC.decode(buffer)
            }
        } finally {
            buffer.release()
        }
    }
}
