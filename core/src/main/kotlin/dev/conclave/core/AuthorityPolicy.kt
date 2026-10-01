package dev.conclave.core

import java.util.UUID

sealed interface Principal {
    data object Console : Principal

    data class Player(val id: UUID, val operator: Boolean) : Principal

    data object Unsupported : Principal
}

object AuthorityPolicy {
    fun operator(principal: Principal): Boolean =
        principal == Principal.Console || principal is Principal.Player && principal.operator

    fun gameMaster(principal: Principal, members: Set<UUID>): Boolean =
        operator(principal) || principal is Principal.Player && principal.id in members

    fun auditId(principal: Principal): String =
        when (principal) {
            Principal.Console -> "console"
            Principal.Unsupported -> "unsupported_source"
            is Principal.Player -> principal.id.toString()
        }
}
