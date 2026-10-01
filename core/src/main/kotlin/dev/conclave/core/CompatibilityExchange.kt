package dev.conclave.core

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.HexFormat

/**
 * Development build identity. A version claim is compatibility information, never authorization.
 */
data class BuildIdentity(val version: String, val fingerprint: String) {
    init {
        require(version.matches(Regex("[A-Za-z0-9._+\\-]{1,128}")))
        require(fingerprint.matches(Regex("[0-9a-f]{64}")))
    }
}

/** Small, bounded login message shared by the native client and server adapters. */
object CompatibilityExchange {
    private const val PROTOCOL = 1
    const val MAX_BYTES = 4 + 1 + 128 + 32
    private val hex = HexFormat.of()

    fun encode(identity: BuildIdentity): ByteArray {
        val version = identity.version.toByteArray(StandardCharsets.US_ASCII)
        return ByteBuffer.allocate(4 + 1 + version.size + 32)
            .putInt(PROTOCOL)
            .put(version.size.toByte())
            .put(version)
            .put(hex.parseHex(identity.fingerprint))
            .array()
    }

    fun decode(bytes: ByteArray): BuildIdentity? {
        if (bytes.size !in 38..MAX_BYTES) return null
        val input = ByteBuffer.wrap(bytes)
        if (input.int != PROTOCOL) return null
        val length = input.get().toInt() and 0xff
        if (length !in 1..128 || input.remaining() != length + 32) return null
        val version = ByteArray(length).also(input::get)
        if (version.any { it < 0 }) return null
        val fingerprint = ByteArray(32).also(input::get)
        return try {
            BuildIdentity(String(version, StandardCharsets.US_ASCII), hex.formatHex(fingerprint))
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
