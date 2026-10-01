package dev.conclave.core

/** Called after ready revivals, remaining grave expirations and real-time reconnect expiry. */
object PartySurvival {
    fun hasOpportunity(
        attempt: java.util.UUID,
        players: PlayerFrame,
        graves: Collection<GraveLifecycle>,
    ): Boolean {
        val opportunities =
            graves
                .filter { it.attempt == attempt && it.selfRevivalOpportunity() }
                .mapTo(hashSetOf()) { it.player }
        return players.roster.any { id ->
            val player = players.byId[id] ?: return@any false
            (player.participation == Participation.ACTIVE ||
                player.participation == Participation.RECONNECTING) &&
                (player.life == LifeState.ALIVE || id in opportunities)
        }
    }
}
