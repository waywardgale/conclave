package dev.conclave.storage

import dev.conclave.core.SourceDocument
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.HexFormat

internal fun digest(bytes: ByteArray): String =
    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

/**
 * Explicit bounded records; never JVM object deserialization. All returned collections are
 * detached.
 */
internal object StoredData {
    private const val MAX_FILES = 256
    private const val MAX_BYTES = 4_194_304

    fun sources(documents: List<SourceDocument>): ByteArray {
        require(documents.size <= MAX_FILES)
        require(documents.map { it.file }.distinct().size == documents.size)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(1)
            data.writeInt(documents.size)
            for (document in documents.sortedBy { it.file }) {
                require(document.file.length <= 256 && document.file.none { it.isISOControl() })
                data.writeUTF(document.file)
                val text = document.text.toByteArray(Charsets.UTF_8)
                require(text.size <= 262_144)
                data.writeInt(text.size)
                data.write(text)
                require(output.size() <= MAX_BYTES)
            }
        }
        return output.toByteArray()
    }

    fun sources(bytes: ByteArray): List<SourceDocument> {
        require(bytes.size <= MAX_BYTES)
        return DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            check(data.readInt() == 1) { "Unsupported source record" }
            val size = data.readInt()
            require(size in 0..MAX_FILES)
            val result =
                List(size) {
                    val file = data.readUTF()
                    require(file.length <= 256 && file.none { it.isISOControl() })
                    val length = data.readInt()
                    require(length in 0..262_144 && length <= data.available())
                    val decoder =
                        Charsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                    SourceDocument(
                        file,
                        decoder.decode(ByteBuffer.wrap(data.readNBytes(length))).toString(),
                    )
                }
            check(data.available() == 0)
            check(result.map { it.file }.distinct().size == result.size)
            java.util.List.copyOf(result)
        }
    }
}
