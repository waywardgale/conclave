package dev.conclave.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@JvmInline
value class RealtimeDuration(val nanos: Long) {
    init {
        require(nanos >= 0)
    }

    companion object {
        fun parse(text: String): RealtimeDuration {
            require(text.length <= 64)
            val match =
                requireNotNull(Regex("([0-9]+(?:\\.[0-9]+)?)(ms|s|m|h)").matchEntire(text)) {
                    "Use a real-time duration such as 500ms or 60s"
                }
            val factor =
                when (match.groupValues[2]) {
                    "ms" -> 1_000_000L
                    "s" -> 1_000_000_000L
                    "m" -> 60_000_000_000L
                    else -> 3_600_000_000_000L
                }
            return RealtimeDuration(
                match.groupValues[1]
                    .toBigDecimal()
                    .multiply(BigDecimal(factor))
                    .setScale(0, RoundingMode.CEILING)
                    .longValueExact()
            )
        }
    }
}

object RealtimeDurationSchema : ConfigSchema<RealtimeDuration> {
    override val description =
        SchemaDescription(
            "string",
            "Real elapsed time; independent of simulation ticks",
            maxLength = 64,
            pattern = "^[0-9]+(?:\\.[0-9]+)?(?:ms|s|m|h)$",
            format = "real_duration",
        )

    override fun decode(value: YamlValue, context: SchemaContext): RealtimeDuration =
        try {
            RealtimeDuration.parse(value.text())
        } catch (_: IllegalArgumentException) {
            invalid("real_duration", description.help, value.source)
        } catch (_: ArithmeticException) {
            invalid(
                "real_duration",
                "Real-time duration exceeds the supported clock range",
                value.source,
            )
        }

    override fun encode(value: RealtimeDuration) =
        jsonString(
            BigDecimal(value.nanos)
                .divide(BigDecimal(1_000_000_000))
                .stripTrailingZeros()
                .toPlainString() + "s"
        )
}

data class ParticipantSnapshot(
    val player: UUID,
    val online: Boolean,
    val life: LifeState,
    val participation: Participation,
)

enum class ParticipantTransition {
    DISCONNECTED,
    RECONNECTED,
    RECONNECT_GRACE_EXPIRED,
    PARTICIPATION_CHANGED,
    DIED,
    REVIVED,
    PASSED_OUT,
}

data class ParticipantEvent(
    val type: ParticipantTransition,
    val before: ParticipantSnapshot,
    val after: ParticipantSnapshot,
    val sequence: Long,
    val method: RevivalMethod? = null,
    val helper: UUID? = null,
) {
    init {
        require(before.player == after.player && sequence > 0)
        require((type == ParticipantTransition.REVIVED) == (method != null))
        require((method == RevivalMethod.ASSISTED) == (helper != null))
        require(helper == null || helper != after.player)
    }
}

/** Source filters use these retained facts; later rule guards read current gameplay state. */
data class ParticipantNotice(val event: ParticipantEvent, val before: PlayerObservation) {
    init {
        require(
            before.id == event.before.player &&
                before.online == event.before.online &&
                before.life == event.before.life &&
                before.participation == event.before.participation
        )
    }
}

/** A retained roster identity. World entry and resource admission are independent operations. */
class ParticipantLifecycle(
    val player: UUID,
    connection: UUID,
    val grace: RealtimeDuration = RealtimeDuration(60_000_000_000),
) {
    private val owner = Thread.currentThread()
    private var connection: UUID? = connection
    private var lostAt: Long? = null
    private var open = true
    private val events = ArrayDeque<ParticipantEvent>()
    private var sequence = 0L
    var snapshot = ParticipantSnapshot(player, true, LifeState.ALIVE, Participation.ACTIVE)
        private set

    fun disconnected(connection: UUID, now: Long): Boolean {
        checkThread()
        if (!open || this.connection != connection || !snapshot.online) return false
        this.connection = null
        val before = snapshot
        if (before.participation == Participation.ACTIVE) lostAt = now
        val participation =
            if (before.participation == Participation.ACTIVE) Participation.RECONNECTING
            else before.participation
        snapshot = before.copy(online = false, participation = participation)
        changed(ParticipantTransition.DISCONNECTED, before)
        return true
    }

    /** Only successful native placement constitutes an actual completed return to play. */
    fun placed(connection: UUID): Boolean {
        checkThread()
        if (!open || snapshot.online || this.connection != null) return false
        this.connection = connection
        val before = snapshot
        snapshot = before.copy(online = true)
        changed(ParticipantTransition.RECONNECTED, before)
        return true
    }

    fun admit(connection: UUID, now: Long, resourcesApplied: Boolean): Boolean {
        checkThread()
        if (
            !open ||
                this.connection != connection ||
                !snapshot.online ||
                !resourcesApplied ||
                snapshot.participation != Participation.RECONNECTING
        )
            return false
        val began = checkNotNull(lostAt)
        // Subtraction permits the native monotonic clock to wrap without extending the window.
        val elapsed = now - began
        check(elapsed >= 0) { "Reconnect clock moved backwards" }
        if (elapsed > grace.nanos) return false
        val before = snapshot
        lostAt = null
        snapshot = before.copy(participation = Participation.ACTIVE)
        changed(null, before)
        return true
    }

    /** Run after ready admission decisions; exact-deadline admission can win this boundary. */
    fun expire(now: Long): Boolean {
        checkThread()
        if (!open || snapshot.participation != Participation.RECONNECTING) return false
        val elapsed = now - checkNotNull(lostAt)
        check(elapsed >= 0) { "Reconnect clock moved backwards" }
        if (elapsed < grace.nanos) return false
        val before = snapshot
        snapshot = before.copy(participation = Participation.OBSERVER)
        lostAt = null
        changed(ParticipantTransition.RECONNECT_GRACE_EXPIRED, before)
        return true
    }

    /** Native death/grave commitment supplies the life transition; it never grants admission. */
    fun life(next: LifeState, method: RevivalMethod? = null, helper: UUID? = null): Boolean {
        checkThread()
        if (!open || snapshot.life == next) return false
        val before = snapshot
        val type =
            when {
                before.life == LifeState.ALIVE && next == LifeState.DEAD ->
                    ParticipantTransition.DIED
                before.life == LifeState.DEAD && next == LifeState.PASSED_OUT ->
                    ParticipantTransition.PASSED_OUT
                before.life != LifeState.ALIVE && next == LifeState.ALIVE ->
                    ParticipantTransition.REVIVED
                else -> error("Life transitions must preserve actual death before passing out")
            }
        require((type == ParticipantTransition.REVIVED) == (method != null))
        require((method == RevivalMethod.ASSISTED) == (helper != null))
        require(helper == null || helper != player)
        snapshot = before.copy(life = next)
        changed(type, before, method, helper)
        return true
    }

    fun survivor(): Boolean {
        checkThread()
        return open &&
            snapshot.life == LifeState.ALIVE &&
            snapshot.participation != Participation.OBSERVER
    }

    fun end() {
        checkThread()
        open = false
        connection = null
        lostAt = null
        events.clear()
    }

    fun drainEvents(): List<ParticipantEvent> {
        checkThread()
        return events.toList().also { events.clear() }
    }

    private fun changed(
        type: ParticipantTransition?,
        before: ParticipantSnapshot,
        method: RevivalMethod? = null,
        helper: UUID? = null,
    ) {
        check(events.size < 1022) { "Undelivered participant lifecycle capacity exceeded" }
        if (type != null)
            events += ParticipantEvent(type, before, snapshot, nextSequence(), method, helper)
        if (before.participation != snapshot.participation)
            events +=
                ParticipantEvent(
                    ParticipantTransition.PARTICIPATION_CHANGED,
                    before,
                    snapshot,
                    nextSequence(),
                )
    }

    private fun nextSequence() = Math.incrementExact(sequence).also { sequence = it }

    private fun checkThread() = check(Thread.currentThread() === owner)
}
