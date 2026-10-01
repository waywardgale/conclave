package dev.conclave.fabric

import dev.conclave.core.CompatibilityExchange
import io.netty.buffer.Unpooled
import kotlin.test.*
import net.minecraft.network.FriendlyByteBuf
import org.junit.jupiter.api.Test

/**
 * Native buffer/resource checks. A live client/server admission test is still a separate
 * requirement.
 */
class LoginCompatibilityTest {
    @Test
    fun `native payload carries the generated installed identity`() {
        val payload = LoginCompatibility.payload()
        try {
            assertEquals(BuildDetails.identity, LoginCompatibility.read(payload))
            assertEquals(0, payload.readableBytes())
            assertEquals(64, BuildDetails.identity.fingerprint.length)
        } finally {
            payload.release()
        }
    }

    @Test
    fun `native decoder rejects excessive data without reading it`() {
        val payload =
            FriendlyByteBuf(Unpooled.wrappedBuffer(ByteArray(CompatibilityExchange.MAX_BYTES + 1)))
        try {
            assertNull(LoginCompatibility.read(payload))
            assertEquals(0, payload.readerIndex())
        } finally {
            payload.release()
        }
    }

    @Test
    fun `empty native response is invalid`() {
        val payload = FriendlyByteBuf(Unpooled.buffer())
        try {
            assertNull(LoginCompatibility.read(payload))
        } finally {
            payload.release()
        }
    }
}
