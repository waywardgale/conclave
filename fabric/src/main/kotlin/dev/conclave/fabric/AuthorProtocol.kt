package dev.conclave.fabric

import dev.conclave.core.*
import dev.conclave.storage.*
import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/** Internal versioned protocol. The build handshake precedes these bounded play payloads. */
data class AuthorPayload(val chunk: TransferChunk) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE =
            CustomPacketPayload.Type<AuthorPayload>(
                Identifier.fromNamespaceAndPath("conclave", "authoring")
            )
        val CODEC =
            object : StreamCodec<RegistryFriendlyByteBuf, AuthorPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): AuthorPayload {
                    val id = buffer.readUUID()
                    val index = buffer.readVarInt()
                    val total = buffer.readVarInt()
                    val hash = ByteArray(32)
                    buffer.readBytes(hash)
                    return AuthorPayload(
                        TransferChunk(
                            id,
                            index,
                            total,
                            hash,
                            buffer.readByteArray(TransferChunk.CHUNK_BYTES),
                        )
                    )
                }

                override fun encode(buffer: RegistryFriendlyByteBuf, value: AuthorPayload) {
                    val chunk = value.chunk
                    buffer.writeUUID(chunk.transfer)
                    buffer.writeVarInt(chunk.index)
                    buffer.writeVarInt(chunk.totalBytes)
                    buffer.writeBytes(chunk.hash)
                    buffer.writeByteArray(chunk.bytes)
                }
            }
    }
}

sealed interface AuthorRequest {
    data object ListDrafts : AuthorRequest

    data class Create(val id: UUID, val name: String, val shared: Boolean) : AuthorRequest

    data class Open(val draft: UUID) : AuthorRequest

    data class Save(val draft: UUID, val changes: List<DraftChange>, val version: Long? = null) :
        AuthorRequest

    data class Validate(val draft: UUID, val version: Long) : AuthorRequest

    data class Publish(val draft: UUID, val version: Long) : AuthorRequest

    data object History : AuthorRequest

    data class Rollback(val revision: String, val baseline: String?) : AuthorRequest

    data class Share(val draft: UUID, val version: Long, val shared: Boolean) : AuthorRequest
}

enum class AuthorResult {
    OK,
    CONFLICT,
    INVALID,
    ERROR,
    DENIED,
}

data class AuthorReply(
    val result: AuthorResult,
    val message: String,
    val operator: Boolean = false,
    val drafts: List<DraftSummary> = emptyList(),
    val draft: DraftSnapshot? = null,
    val diagnostics: List<Diagnostic> = emptyList(),
    val history: List<String> = emptyList(),
    val current: String? = null,
)

object AuthorProtocol {
    fun encode(request: AuthorRequest): ByteArray = write {
        when (request) {
            AuthorRequest.ListDrafts -> writeByte(0)
            is AuthorRequest.Create -> {
                writeByte(1)
                uuid(request.id)
                text(request.name, 64)
                writeBoolean(request.shared)
            }
            is AuthorRequest.Open -> {
                writeByte(2)
                uuid(request.draft)
            }
            is AuthorRequest.Save -> {
                writeByte(3)
                uuid(request.draft)
                nullableLong(request.version)
                count(request.changes.size, 256)
                request.changes.forEach {
                    text(it.file, 256)
                    nullableUuid(it.expected)
                    nullableText(it.text, 262144)
                }
            }
            is AuthorRequest.Validate -> {
                writeByte(4)
                uuid(request.draft)
                writeLong(request.version)
            }
            is AuthorRequest.Publish -> {
                writeByte(5)
                uuid(request.draft)
                writeLong(request.version)
            }
            AuthorRequest.History -> writeByte(6)
            is AuthorRequest.Rollback -> {
                writeByte(7)
                text(request.revision, 64)
                nullableText(request.baseline, 64)
            }
            is AuthorRequest.Share -> {
                writeByte(8)
                uuid(request.draft)
                writeLong(request.version)
                writeBoolean(request.shared)
            }
        }
    }

    fun request(bytes: ByteArray): AuthorRequest =
        read(bytes) {
            when (readUnsignedByte()) {
                0 -> AuthorRequest.ListDrafts
                1 -> AuthorRequest.Create(uuid(), text(64), readBoolean())
                2 -> AuthorRequest.Open(uuid())
                3 -> {
                    val draft = uuid()
                    val version = nullableLong()
                    AuthorRequest.Save(
                        draft,
                        List(count(256)) {
                            DraftChange(text(256), nullableUuid(), nullableText(262144))
                        },
                        version,
                    )
                }
                4 -> AuthorRequest.Validate(uuid(), readLong())
                5 -> AuthorRequest.Publish(uuid(), readLong())
                6 -> AuthorRequest.History
                7 -> AuthorRequest.Rollback(text(64), nullableText(64))
                8 -> AuthorRequest.Share(uuid(), readLong(), readBoolean())
                else -> error("Unknown author request")
            }
        }

    fun encode(reply: AuthorReply): ByteArray = write {
        writeByte(reply.result.ordinal)
        text(reply.message, 2048)
        writeBoolean(reply.operator)
        count(reply.drafts.size, 64)
        reply.drafts.forEach { summary(it) }
        writeBoolean(reply.draft != null)
        reply.draft?.let { snapshot ->
            summary(snapshot.summary)
            count(snapshot.files.size, 256)
            snapshot.files.forEach {
                text(it.file, 256)
                uuid(it.token)
                text(it.text, 262144)
            }
        }
        count(reply.diagnostics.size, 256)
        reply.diagnostics.take(256).forEach {
            text(it.code, 128)
            text(it.message, 2048)
            text(it.source.file, 512)
            writeInt(it.source.line)
            writeInt(it.source.column)
            text(it.source.path, 4096)
        }
        count(reply.history.size, 256)
        reply.history.forEach { text(it, 64) }
        nullableText(reply.current, 64)
    }

    fun reply(bytes: ByteArray): AuthorReply =
        read(bytes) {
            val result =
                AuthorResult.entries.getOrNull(readUnsignedByte()) ?: error("Invalid author result")
            val message = text(2048)
            val operator = readBoolean()
            val drafts = List(count(64)) { summary() }
            val draft =
                if (readBoolean()) {
                    val summary = summary()
                    DraftSnapshot(
                        summary,
                        List(count(256)) { DraftFile(text(256), uuid(), text(262144)) },
                    )
                } else null
            val diagnostics =
                List(count(256)) {
                    val code = text(128)
                    val description = text(2048)
                    Diagnostic(
                        code,
                        description,
                        SourceLocation(text(512), readInt(), readInt(), text(4096)),
                    )
                }
            AuthorReply(
                result,
                message,
                operator,
                drafts,
                draft,
                diagnostics,
                List(count(256)) { text(64) },
                nullableText(64),
            )
        }

    private fun DataOutputStream.summary(v: DraftSummary) {
        uuid(v.id)
        uuid(v.owner)
        text(v.name, 256)
        writeBoolean(v.shared)
        writeLong(v.version)
        nullableText(v.baseline, 64)
    }

    private fun DataInputStream.summary() =
        DraftSummary(uuid(), uuid(), text(256), readBoolean(), readLong(), nullableText(64))

    private fun write(action: DataOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream()
            .also { bytes ->
                DataOutputStream(bytes).use {
                    it.writeInt(1)
                    it.action()
                }
            }
            .toByteArray()
            .also { require(it.size <= TransferChunk.MAX_TOTAL) }

    private fun <T> read(bytes: ByteArray, action: DataInputStream.() -> T): T =
        DataInputStream(ByteArrayInputStream(bytes)).use {
            require(bytes.size <= TransferChunk.MAX_TOTAL && it.readInt() == 1)
            val result = it.action()
            require(it.available() == 0)
            result
        }

    private fun DataOutputStream.text(v: String, max: Int) {
        val bytes = v.toByteArray(Charsets.UTF_8)
        require(bytes.size <= max)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.text(max: Int): String {
        val length = readInt()
        require(length in 0..max && length <= available())
        val bytes = readNBytes(length)
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }

    private fun DataOutputStream.count(v: Int, max: Int) {
        require(v in 0..max)
        writeInt(v)
    }

    private fun DataInputStream.count(max: Int) = readInt().also { require(it in 0..max) }

    private fun DataOutputStream.uuid(v: UUID) {
        writeLong(v.mostSignificantBits)
        writeLong(v.leastSignificantBits)
    }

    private fun DataInputStream.uuid() = UUID(readLong(), readLong())

    private fun DataOutputStream.nullableUuid(v: UUID?) {
        writeBoolean(v != null)
        v?.let { uuid(it) }
    }

    private fun DataInputStream.nullableUuid() = if (readBoolean()) uuid() else null

    private fun DataOutputStream.nullableLong(v: Long?) {
        writeBoolean(v != null)
        v?.let { writeLong(it) }
    }

    private fun DataInputStream.nullableLong() = if (readBoolean()) readLong() else null

    private fun DataOutputStream.nullableText(v: String?, max: Int) {
        writeBoolean(v != null)
        v?.let { text(it, max) }
    }

    private fun DataInputStream.nullableText(max: Int) = if (readBoolean()) text(max) else null
}
