package dev.conclave.fabric

import dev.conclave.storage.*
import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test

class AuthorProtocolTest {
    @Test
    fun `multipart-sized raw Unicode YAML survives protocol roundtrip`() {
        val draft = UUID.randomUUID()
        val text = "# comments\nname: Привет\n".repeat(2000)
        val request =
            AuthorRequest.Save(
                draft,
                listOf(DraftChange("folder/file.yaml", UUID.randomUUID(), text)),
                12,
            )
        assertEquals(request, AuthorProtocol.request(AuthorProtocol.encode(request)))
        val snapshot =
            DraftSnapshot(
                DraftSummary(draft, UUID.randomUUID(), "Named draft", true, 12, null),
                listOf(DraftFile("folder/file.yaml", UUID.randomUUID(), text)),
            )
        val reply =
            AuthorProtocol.reply(
                AuthorProtocol.encode(
                    AuthorReply(AuthorResult.OK, "Opened", true, draft = snapshot)
                )
            )
        assertEquals(snapshot.summary, reply.draft?.summary)
        assertEquals(snapshot.files, reply.draft?.files)
    }

    @Test
    fun `trailing bytes unsupported versions and oversized content are rejected`() {
        val bytes = AuthorProtocol.encode(AuthorRequest.ListDrafts)
        assertFailsWith<IllegalArgumentException> { AuthorProtocol.request(bytes + byteArrayOf(0)) }
        assertFailsWith<IllegalArgumentException> {
            AuthorProtocol.request(bytes.copyOf().also { it[3] = 2 })
        }
        assertFailsWith<IllegalArgumentException> {
            AuthorProtocol.encode(
                AuthorRequest.Save(
                    UUID.randomUUID(),
                    listOf(DraftChange("a.yaml", null, "x".repeat(262145))),
                )
            )
        }
    }
}
