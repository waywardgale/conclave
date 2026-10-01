package dev.conclave.core

import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test

class AuthorityPolicyTest {
    @Test
    fun `GM membership permits operation but never delegation and revocation applies to the next check`() {
        val id = UUID.randomUUID()
        val gm = Principal.Player(id, false)
        assertTrue(AuthorityPolicy.gameMaster(gm, setOf(id)))
        assertFalse(AuthorityPolicy.operator(gm))
        assertFalse(AuthorityPolicy.gameMaster(gm, emptySet()))
    }

    @Test
    fun `trusted console and current operator are privileged but unsupported sources are never elevated`() {
        assertTrue(AuthorityPolicy.operator(Principal.Console))
        assertTrue(AuthorityPolicy.operator(Principal.Player(UUID.randomUUID(), true)))
        assertFalse(AuthorityPolicy.operator(Principal.Unsupported))
        assertFalse(AuthorityPolicy.gameMaster(Principal.Unsupported, setOf(UUID.randomUUID())))
    }
}
