package dev.conclave.core

import kotlin.test.*
import org.junit.jupiter.api.Test

class CompatibilityExchangeTest {
    private val installed = BuildIdentity("0.1.0-dev.1", "a".repeat(64))

    @Test
    fun `same release with different development code is incompatible`() {
        assertEquals(
            installed,
            CompatibilityExchange.decode(CompatibilityExchange.encode(installed)),
        )
        assertNotEquals(
            installed,
            CompatibilityExchange.decode(
                CompatibilityExchange.encode(installed.copy(fingerprint = "b".repeat(64)))
            ),
        )
        assertNotEquals(
            installed,
            CompatibilityExchange.decode(
                CompatibilityExchange.encode(installed.copy(version = "0.1.1"))
            ),
        )
    }

    @Test
    fun `truncated trailing oversized or unknown protocol messages fail closed`() {
        val valid = CompatibilityExchange.encode(installed)
        for (length in 0 until valid.size) assertNull(
            CompatibilityExchange.decode(valid.copyOf(length))
        )
        assertNull(CompatibilityExchange.decode(valid + 0.toByte()))
        assertNull(CompatibilityExchange.decode(ByteArray(CompatibilityExchange.MAX_BYTES + 1)))
        assertNull(CompatibilityExchange.decode(valid.copyOf().also { it[3] = 2 }))
        assertNull(CompatibilityExchange.decode(valid.copyOf().also { it[4] = 127 }))
    }

    @Test
    fun `remote version text cannot inject control characters into diagnostics`() {
        val valid = CompatibilityExchange.encode(installed)
        assertNull(CompatibilityExchange.decode(valid.copyOf().also { it[5] = '\n'.code.toByte() }))
        assertNull(CompatibilityExchange.decode(valid.copyOf().also { it[5] = 0xff.toByte() }))
    }
}
