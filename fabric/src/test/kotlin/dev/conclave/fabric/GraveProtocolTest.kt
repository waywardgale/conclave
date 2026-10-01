package dev.conclave.fabric

import dev.conclave.core.GraveStatus
import io.netty.buffer.Unpooled
import java.util.UUID
import kotlin.test.*
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import org.junit.jupiter.api.Test

class GraveProtocolTest {
    private fun buffer() = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)

    @Test
    fun `grave input and bounded personal status survive native buffer transport`() {
        val id = UUID.randomUUID()
        val buffer = buffer()
        try {
            for (action in GraveInputAction.entries) {
                val input = GraveInputPayload(id, 42, action)
                GraveInputPayload.CODEC.encode(buffer, input)
                assertEquals(input, GraveInputPayload.CODEC.decode(buffer))
            }
            for (state in
                listOf(
                    GraveStatePayload(null),
                    GraveStatePayload(
                        id,
                        GraveStatus.OPPORTUNITY,
                        true,
                        true,
                        300,
                        10,
                        false,
                        "Waiting",
                    ),
                )) {
                GraveStatePayload.CODEC.encode(buffer, state)
                assertEquals(state, GraveStatePayload.CODEC.decode(buffer))
            }
            val help = GraveHelpPayload(id, "Helper", 10, 40, true)
            GraveHelpPayload.CODEC.encode(buffer, help)
            assertEquals(help, GraveHelpPayload.CODEC.decode(buffer))
            assertEquals(0, buffer.readableBytes())
        } finally {
            buffer.release()
        }
    }

    @Test
    fun `invalid input counters actions and impossible help progress are rejected`() {
        val buffer = buffer()
        try {
            buffer.writeUUID(UUID.randomUUID())
            buffer.writeVarLong(0)
            buffer.writeByte(0)
            assertFailsWith<IllegalArgumentException> { GraveInputPayload.CODEC.decode(buffer) }
            buffer.clear()
            buffer.writeUUID(UUID.randomUUID())
            buffer.writeVarLong(1)
            buffer.writeByte(255)
            assertFailsWith<IllegalStateException> { GraveInputPayload.CODEC.decode(buffer) }
            buffer.clear()
            buffer.writeBoolean(true)
            buffer.writeUUID(UUID.randomUUID())
            buffer.writeUtf("Helper", 64)
            buffer.writeVarLong(41)
            buffer.writeVarLong(40)
            buffer.writeBoolean(true)
            assertFailsWith<IllegalArgumentException> { GraveHelpPayload.CODEC.decode(buffer) }
        } finally {
            buffer.release()
        }
    }
}
