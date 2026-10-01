package dev.conclave.fabric

import io.netty.buffer.Unpooled
import kotlin.test.*
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import org.junit.jupiter.api.Test

class ProtectionProtocolTest {
    @Test
    fun `own remaining protection is bounded to a nonnegative native duration`() {
        val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        try {
            for (remaining in listOf(0L, 1L, 400L, Long.MAX_VALUE)) {
                ProtectionPayload.CODEC.encode(buffer, ProtectionPayload(remaining))
                assertEquals(remaining, ProtectionPayload.CODEC.decode(buffer).remaining)
            }
            buffer.writeVarLong(-1)
            assertFailsWith<IllegalArgumentException> { ProtectionPayload.CODEC.decode(buffer) }
        } finally {
            buffer.release()
        }
    }
}
