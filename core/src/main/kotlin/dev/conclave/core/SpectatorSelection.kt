package dev.conclave.core

import java.util.UUID

data class SpectatorView(
    val active: Boolean,
    val target: UUID?,
    val eligible: List<UUID>,
    val mirrorPrivate: Boolean,
)

/** Stable roster selection and disclosure permission, independent of native camera transport. */
class SpectatorSelection(
    val viewer: UUID,
    roster: List<UUID>,
    private val sharePrivate: Boolean,
    private val requireTarget: Boolean = true,
) {
    private val thread = Thread.currentThread()
    private val roster = java.util.List.copyOf(roster)
    private var requested = false
    private var required = false
    private var allowed = false
    private var closed = false
    private var target: UUID? = null
    private var eligible: List<UUID> = emptyList()

    init {
        require(viewer in roster && roster.size <= 1024 && roster.distinct().size == roster.size)
    }

    /**
     * Availability means the native adapter can supply this camera without new unbounded claims.
     */
    fun refresh(players: PlayerFrame, available: Set<UUID>): SpectatorView {
        checkThread()
        val own = players.byId[viewer]
        required = !closed && own?.online == true && own.life == LifeState.PASSED_OUT
        allowed =
            !closed &&
                own?.online == true &&
                (required ||
                    own.life == LifeState.ALIVE && own.participation == Participation.OBSERVER)
        val next =
            if (!allowed) emptyList()
            else
                roster.filter { id ->
                    val player = players.byId[id]
                    id != viewer &&
                        id in available &&
                        player?.online == true &&
                        player.life == LifeState.ALIVE &&
                        player.participation == Participation.ACTIVE
                }
        val old = target
        eligible = java.util.List.copyOf(next)
        if (!allowed) {
            requested = false
            target = null
        } else if (required || requested) {
            if (old == null || old !in eligible) target = successor(old, 1)
            if (!required && requireTarget && target == null) requested = false
        }
        return view()
    }

    fun watch(): Boolean {
        checkThread()
        if (!allowed || requireTarget && eligible.isEmpty()) return false
        requested = true
        if (target == null || target !in eligible) target = eligible.firstOrNull()
        return true
    }

    fun leave(): Boolean {
        checkThread()
        if (required || !allowed) return false
        requested = false
        target = null
        return true
    }

    fun select(player: UUID): Boolean {
        checkThread()
        if (!allowed || player !in eligible) return false
        requested = true
        target = player
        return true
    }

    fun cycle(direction: Int): Boolean {
        checkThread()
        require(direction == 1 || direction == -1)
        if (!allowed || eligible.isEmpty()) return false
        target = successor(target, direction)
        requested = true
        return true
    }

    /**
     * Native free-view attachment grants private data only for an independently eligible target.
     */
    fun permitsPrivate(player: UUID): Boolean {
        checkThread()
        return sharePrivate && allowed && (required || requested) && player in eligible
    }

    fun view(): SpectatorView {
        checkThread()
        val active = allowed && (required || requested)
        return SpectatorView(
            active,
            target.takeIf { active },
            eligible,
            active && target?.let(::permitsPrivate) == true,
        )
    }

    fun close() {
        checkThread()
        closed = true
        allowed = false
        required = false
        requested = false
        target = null
        eligible = emptyList()
    }

    private fun successor(previous: UUID?, direction: Int): UUID? {
        if (eligible.isEmpty()) return null
        if (previous == null) return if (direction > 0) eligible.first() else eligible.last()
        val position = roster.indexOf(previous)
        for (offset in 1..roster.size) {
            val candidate = roster[(position + offset * direction).mod(roster.size)]
            if (candidate in eligible) return candidate
        }
        return null
    }

    private fun checkThread() = check(Thread.currentThread() === thread)
}
