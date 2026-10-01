package dev.conclave.fabric

import io.netty.buffer.Unpooled
import java.util.UUID
import kotlin.test.*
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import org.junit.jupiter.api.Test

class SpectatorProtocolTest {
    private fun buffer() = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)

    @Test
    fun `controls preserve attempt identity selection and optional observer state`() {
        val attempt = UUID.randomUUID()
        val target = UUID.randomUUID()
        val buffer = buffer()
        try {
            for (action in WatchAction.entries) {
                val value =
                    WatchInputPayload(
                        attempt,
                        action,
                        target.takeIf { action == WatchAction.SELECT },
                    )
                WatchInputPayload.CODEC.encode(buffer, value)
                assertEquals(value, WatchInputPayload.CODEC.decode(buffer))
            }
            val choices = listOf(WatchChoice(target, "Teammate"))
            for (state in
                listOf(
                    WatchStatePayload(null),
                    WatchStatePayload(attempt, target, choices),
                    WatchStatePayload(attempt, choices = choices, living = true, active = false),
                    WatchStatePayload(attempt, target, choices, free = true, living = true),
                    WatchStatePayload(attempt, message = "No teammate available"),
                )) {
                WatchStatePayload.CODEC.encode(buffer, state)
                assertEquals(state, WatchStatePayload.CODEC.decode(buffer))
            }
            assertEquals(0, buffer.readableBytes())
        } finally {
            buffer.release()
        }
    }

    @Test
    fun `invalid actions impossible selections duplicate identities and oversized choices are rejected`() {
        val attempt = UUID.randomUUID()
        val target = UUID.randomUUID()
        val buffer = buffer()
        try {
            buffer.writeUUID(attempt)
            buffer.writeByte(255)
            assertFailsWith<IllegalStateException> { WatchInputPayload.CODEC.decode(buffer) }
            buffer.clear()
            buffer.writeBoolean(true)
            buffer.writeUUID(attempt)
            buffer.writeBoolean(false)
            buffer.writeVarInt(1025)
            assertFailsWith<IllegalArgumentException> { WatchStatePayload.CODEC.decode(buffer) }
            assertFailsWith<IllegalArgumentException> {
                WatchInputPayload(attempt, WatchAction.SELECT)
            }
            assertFailsWith<IllegalArgumentException> {
                WatchInputPayload(attempt, WatchAction.LEAVE, target)
            }
            assertFailsWith<IllegalArgumentException> { WatchStatePayload(attempt, target) }
            assertFailsWith<IllegalArgumentException> {
                WatchStatePayload(attempt, choices = List(2) { WatchChoice(target, "Same") })
            }
            assertFailsWith<IllegalArgumentException> {
                WatchStatePayload(attempt, choices = listOf(WatchChoice(target, "x".repeat(65))))
            }
        } finally {
            buffer.release()
        }
    }
}
