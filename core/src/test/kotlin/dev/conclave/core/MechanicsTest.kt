package dev.conclave.core

import java.util.Random
import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test

private class MechanicWorld : MechanicContext {
    override val identity = MechanicIdentity(UUID.randomUUID(), 1, "test")
    override var tick = 0L
    val first = UUID.randomUUID()
    val second = UUID.randomUUID()
    var frame = PlayerFrame(listOf(player(first), player(second)), setOf(first, second))
    val events = mutableListOf<MechanicNotice>()
    var targetValid = true
    var group: GroupObservation? = null
    var relic: RelicObservation? = null
    var selected: UUID? = null
    var deliveries = 0
    val statesAtEvent = mutableListOf<MechanicState>()
    var mechanic: MechanicInstance? = null

    override fun players() = frame

    override fun notice(value: MechanicNotice) {
        events += value
        mechanic?.let { statesAtEvent += it.state }
    }

    override fun validTarget(player: UUID, target: TargetHandle, maximumReach: Double?) =
        targetValid

    override fun group(id: String) = group

    override fun random(player: UUID?) = Random(player?.leastSignificantBits ?: 1)

    override fun relic(id: String) = relic

    override fun selectedRelic(player: UUID) = selected

    override fun deliver(relic: UUID, generation: Long, holder: UUID): Boolean {
        val current = this.relic ?: return false
        if (
            current.identity != relic ||
                current.generation != generation ||
                current.holder != holder
        )
            return false
        this.relic = current.copy(holder = null)
        deliveries++
        return true
    }

    fun advance(instance: MechanicInstance, steps: Int = 1) {
        repeat(steps) {
            tick++
            instance.tick()
        }
    }

    fun player(
        id: UUID,
        area: Boolean = true,
        online: Boolean = true,
        life: LifeState = LifeState.ALIVE,
    ) =
        PlayerObservation(
            id,
            online,
            life,
            Participation.ACTIVE,
            false,
            areas = if (area) setOf("plate") else emptySet(),
        )
}

class MechanicsTest {
    @Test
    fun `pattern snapshots are immutable and terminal views retain counts without answers`() {
        val world = MechanicWorld()
        val pattern =
            PatternMechanic(
                PatternConfiguration(
                    setOf("sun"),
                    PatternAnswer.Fixed(listOf("sun", "sun")),
                    perPlayer = true,
                    completion = CompletionRequirement.Any,
                ),
                world,
            )
        assertNull(pattern.patternState())
        pattern.start()
        val empty = assertNotNull(pattern.patternState())
        assertNull(empty.record())
        pattern.input(MechanicInput.Token("sun", world.second, UUID.randomUUID()))
        pattern.input(MechanicInput.Token("sun", world.first, UUID.randomUUID()))
        val partial = assertNotNull(pattern.patternState())
        pattern.input(MechanicInput.Token("sun", world.first, UUID.randomUUID()))
        val terminal = assertNotNull(pattern.patternState())
        assertEquals(0, empty.record(world.first)?.progress)
        assertEquals(1, partial.record(world.first)?.progress)
        assertEquals(PatternRecordState(2, 2, true), terminal.record(world.first))
        assertEquals(PatternRecordState(1, 2, false), terminal.record(world.second))
        assertEquals(1, terminal.completedPlayers)
        assertNull(pattern.expected(world.first))
        assertNull(pattern.expected(world.second))
        world.frame = PlayerFrame(emptyList(), emptySet())
        pattern.cancel()
        assertSame(terminal, pattern.patternState())
    }

    @Test
    fun `cancelled pattern discards its live progress view and private answer`() {
        val world = MechanicWorld()
        val pattern =
            PatternMechanic(
                PatternConfiguration(setOf("sun"), PatternAnswer.Fixed(listOf("sun", "sun"))),
                world,
            )
        pattern.start()
        pattern.input(MechanicInput.Token("sun", null, UUID.randomUUID()))
        val before = assertNotNull(pattern.patternState())
        pattern.cancel()
        assertEquals(1, before.shared?.progress)
        assertNull(pattern.patternState())
        assertNull(pattern.expected())
    }

    @Test
    fun `pattern physical input uses server holds resets and a single cooldown across bindings`() {
        val world = MechanicWorld()
        val sun = TargetHandle(TargetReference(TargetKind.BLOCK, "sun_button"), UUID.randomUUID())
        val moon = TargetHandle(TargetReference(TargetKind.BLOCK, "moon_button"), UUID.randomUUID())
        val pattern =
            PatternMechanic(
                PatternConfiguration(
                    setOf("sun", "moon"),
                    PatternAnswer.Fixed(listOf("sun", "moon")),
                    inputs =
                        listOf(
                            PatternInputConfiguration(
                                "sun",
                                listOf(sun.reference),
                                hold = SimulationDuration(2),
                                consume = true,
                            ),
                            PatternInputConfiguration("moon", listOf(moon.reference)),
                        ),
                    useCooldown = SimulationDuration(2),
                ),
                world,
            )
        pattern.start()
        val press = MechanicInput.Press(world.first, UUID.randomUUID(), sun)
        assertTrue(pattern.input(press))
        assertTrue(pattern.consumes(press))
        world.advance(pattern)
        pattern.input(MechanicInput.ResetPattern(operation = UUID.randomUUID()))
        world.advance(pattern, 2)
        assertEquals(0, pattern.progress())
        assertFalse(pattern.input(press))
        assertTrue(pattern.input(press.copy(gesture = UUID.randomUUID())))
        world.advance(pattern, 2)
        assertEquals(1, pattern.progress())
        assertFalse(pattern.input(MechanicInput.Press(world.first, UUID.randomUUID(), moon)))
        world.advance(pattern, 2)
        assertTrue(pattern.input(MechanicInput.Press(world.first, UUID.randomUUID(), moon)))
        assertEquals(MechanicState.SUCCEEDED, pattern.state)
    }

    @Test
    fun `entry delivery requires a real entry and only one competing mechanic can consume the relic`() {
        val world = MechanicWorld()
        world.relic = RelicObservation(UUID.randomUUID(), 1, world.first)
        val config = DeliverConfiguration("orb", DeliveryDestination.Area("plate"))
        val first = DeliverMechanic(config, world)
        val second = DeliverMechanic(config, world)
        first.start()
        second.start()
        world.advance(first, 10)
        assertEquals(0, world.deliveries)
        assertTrue(first.input(MechanicInput.AreaEntered(world.first, "plate")))
        assertFalse(second.input(MechanicInput.AreaEntered(world.first, "plate")))
        assertEquals(1, world.deliveries)
        assertEquals(MechanicState.SUCCEEDED, first.state)
        assertEquals(MechanicState.RUNNING, second.state)
    }

    @Test
    fun `delivery holds bind selection and generation while completed delivery stays latched`() {
        val world = MechanicWorld()
        val relic = RelicObservation(UUID.randomUUID(), 1, world.first)
        world.relic = relic
        world.selected = relic.identity
        val target = TargetHandle(TargetReference(TargetKind.BLOCK, "altar"), UUID.randomUUID())
        val deliver =
            DeliverMechanic(
                DeliverConfiguration(
                    "orb",
                    DeliveryDestination.Interaction(target.reference),
                    hold = SimulationDuration(2),
                ),
                world,
            )
        deliver.start()
        deliver.input(MechanicInput.Press(world.first, UUID.randomUUID(), target))
        world.relic = relic.copy(generation = 2)
        world.advance(deliver, 2)
        assertEquals(0, world.deliveries)
        deliver.input(MechanicInput.Press(world.first, UUID.randomUUID(), target))
        world.advance(deliver, 2)
        assertEquals(1, world.deliveries)
        world.relic = relic.copy(generation = 3)
        world.advance(deliver)
        assertEquals(MechanicState.SUCCEEDED, deliver.state)
    }

    @Test
    fun `capture requires a continuous full interval and does not accelerate for extra occupants`() {
        val world = MechanicWorld()
        val capture =
            CaptureMechanic(
                CaptureConfiguration("plate", SimulationDuration(3), required = 1),
                world,
            )
        capture.start()
        world.advance(capture, 2)
        assertEquals(MechanicState.RUNNING, capture.state)
        world.advance(capture)
        assertEquals(MechanicState.SUCCEEDED, capture.state)
        world.frame = PlayerFrame(emptyList(), setOf(world.first, world.second))
        world.advance(capture, 10)
        assertEquals(1, world.events.filterIsInstance<MechanicNotice.Result>().size)
    }

    @Test
    fun `capture reset pause and exact fractional decay preserve their distinct contracts`() {
        for ((interruption, expected) in
            listOf(
                CaptureInterruption.Reset to 0.0,
                CaptureInterruption.Pause to 3.0,
                CaptureInterruption.Decay(SimulationDuration(4)) to 0.5,
            )) {
            val world = MechanicWorld()
            val capture =
                CaptureMechanic(
                    CaptureConfiguration(
                        "plate",
                        SimulationDuration(10),
                        interruption = interruption,
                    ),
                    world,
                )
            capture.start()
            world.advance(capture, 3)
            world.frame =
                PlayerFrame(listOf(world.player(world.first, area = false)), setOf(world.first))
            world.advance(capture)
            assertEquals(expected, capture.progressTicks)
            world.frame = PlayerFrame(listOf(world.player(world.first)), setOf(world.first))
            world.advance(capture)
            assertEquals(
                expected,
                capture.progressTicks,
                "Reentry does not invent an elapsed interval",
            )
        }
    }

    @Test
    fun `capture never admits outside dead or offline contributors through a broad selection`() {
        val world = MechanicWorld()
        world.frame =
            PlayerFrame(
                listOf(
                    world.player(world.first, life = LifeState.DEAD),
                    world.player(world.second),
                ),
                setOf(world.first),
            )
        val capture =
            CaptureMechanic(
                CaptureConfiguration(
                    "plate",
                    SimulationDuration(1),
                    players =
                        PlayerSelection(PlayerCollection.ONLINE_PLAYERS, life = LifeFilter.ANY),
                ),
                world,
            )
        capture.start()
        world.advance(capture, 10)
        assertEquals(0.0, capture.progressTicks)
    }

    @Test
    fun `interaction credits each fresh gesture once and retains distinct identities`() {
        val world = MechanicWorld()
        val target = TargetHandle(TargetReference(TargetKind.BLOCK, "console"), UUID.randomUUID())
        val interact =
            InteractMechanic(
                InteractConfiguration(listOf(target.reference), uses = 2, distinctPlayers = true),
                world,
            )
        world.mechanic = interact
        interact.start()
        val press = MechanicInput.Press(world.first, UUID.randomUUID(), target)
        assertTrue(interact.input(press))
        assertFalse(interact.input(press))
        assertFalse(interact.input(press.copy(gesture = UUID.randomUUID())))
        assertTrue(interact.input(press.copy(player = world.second, gesture = UUID.randomUUID())))
        assertEquals(2L, interact.uses)
        assertEquals(
            listOf(MechanicState.SUCCEEDED, MechanicState.SUCCEEDED),
            world.statesAtEvent.takeLast(2),
        )
        assertIs<MechanicNotice.Used>(world.events[world.events.lastIndex - 1])
        assertIs<MechanicNotice.Result>(world.events.last())
    }

    @Test
    fun `holds are individual and reset on damage release or invalid target`() {
        val world = MechanicWorld()
        val target = TargetHandle(TargetReference(TargetKind.BLOCK, "console"), UUID.randomUUID())
        val interact =
            InteractMechanic(
                InteractConfiguration(
                    listOf(target.reference),
                    hold = SimulationDuration(3),
                    interruptOnDamage = true,
                ),
                world,
            )
        interact.start()
        val press = MechanicInput.Press(world.first, UUID.randomUUID(), target)
        interact.input(press)
        world.advance(interact, 2)
        interact.input(MechanicInput.Damaged(world.first))
        world.advance(interact)
        assertEquals(0L, interact.uses)
        assertFalse(interact.input(press))
        interact.input(press.copy(gesture = UUID.randomUUID()))
        world.targetValid = false
        world.advance(interact, 3)
        world.targetValid = true
        assertEquals(0L, interact.uses)
        interact.input(press.copy(gesture = UUID.randomUUID()))
        world.advance(interact, 3)
        assertEquals(MechanicState.SUCCEEDED, interact.state)
    }

    @Test
    fun `defeat reads retained history and distinguishes open groups from unreachable outcomes`() {
        val world = MechanicWorld()
        val group = UUID.randomUUID()
        val defeated =
            GroupMember(UUID.randomUUID(), MemberOutcome.DEFEATED, cause = DefeatCause.DEATH)
        world.group = GroupObservation(group, false, listOf(defeated))
        val all = DefeatMechanic(DefeatConfiguration("guards"), world)
        all.start()
        assertEquals(MechanicState.RUNNING, all.state)
        world.group = GroupObservation(group, true, listOf(defeated))
        world.advance(all)
        assertEquals(MechanicState.SUCCEEDED, all.state)
        world.group =
            GroupObservation(
                UUID.randomUUID(),
                false,
                listOf(
                    GroupMember(
                        UUID.randomUUID(),
                        MemberOutcome.DEFEATED,
                        cause = DefeatCause.SELF_DESTRUCT,
                    )
                ),
            )
        val deathOnly =
            DefeatMechanic(DefeatConfiguration("guards", causes = setOf(DefeatCause.DEATH)), world)
        deathOnly.start()
        assertEquals(MechanicState.FAILED, deathOnly.state)
    }

    @Test
    fun `pattern mismatches consume the input and preserve the hidden expected answer`() {
        val world = MechanicWorld()
        val pattern =
            PatternMechanic(
                PatternConfiguration(
                    setOf("sun", "moon"),
                    PatternAnswer.Fixed(listOf("sun", "moon")),
                ),
                world,
            )
        pattern.start()
        fun submit(token: String) =
            pattern.input(MechanicInput.Token(token, world.first, UUID.randomUUID()))
        submit("sun")
        submit("sun")
        assertEquals(0, pattern.progress())
        submit("moon")
        assertEquals(0, pattern.progress())
        assertEquals(listOf("sun", "moon"), pattern.expected())
        submit("sun")
        submit("moon")
        assertEquals(MechanicState.SUCCEEDED, pattern.state)
        assertNull(pattern.expected())
        assertNull(world.events.filterIsInstance<MechanicNotice.Result>().single().result.player)
    }

    @Test
    fun `per-player pattern freezes its solvers retains progress and completes once`() {
        val world = MechanicWorld()
        val pattern =
            PatternMechanic(
                PatternConfiguration(
                    setOf("sun"),
                    PatternAnswer.Fixed(listOf("sun")),
                    perPlayer = true,
                ),
                world,
            )
        world.mechanic = pattern
        pattern.start()
        val first = MechanicInput.Token("sun", world.first, UUID.randomUUID())
        assertTrue(pattern.input(first))
        assertFalse(pattern.input(first))
        val outsider = UUID.randomUUID()
        world.frame =
            PlayerFrame(
                listOf(
                    world.player(world.first, online = false),
                    world.player(world.second),
                    world.player(outsider),
                ),
                setOf(world.first, world.second, outsider),
            )
        assertFalse(pattern.input(MechanicInput.Token("sun", outsider, UUID.randomUUID())))
        assertTrue(pattern.completed(world.first))
        assertTrue(pattern.input(MechanicInput.Token("sun", world.second, UUID.randomUUID())))
        assertEquals(MechanicState.SUCCEEDED, pattern.state)
        assertEquals(
            listOf(MechanicState.SUCCEEDED, MechanicState.SUCCEEDED, MechanicState.SUCCEEDED),
            world.statesAtEvent.takeLast(3),
        )
    }

    @Test
    fun `unordered patterns preserve multiplicity and explicit reset never reopens completed records`() {
        val world = MechanicWorld()
        val pattern =
            PatternMechanic(
                PatternConfiguration(
                    setOf("sun", "moon"),
                    PatternAnswer.Fixed(listOf("sun", "sun", "moon")),
                    ordered = false,
                ),
                world,
            )
        pattern.start()
        pattern.input(MechanicInput.Token("moon", null, UUID.randomUUID()))
        pattern.input(MechanicInput.Token("moon", null, UUID.randomUUID()))
        assertEquals(0, pattern.progress())
        pattern.input(MechanicInput.Token("sun", null, UUID.randomUUID()))
        pattern.input(MechanicInput.ResetPattern(null, UUID.randomUUID()))
        assertEquals(0, pattern.progress())
        listOf("moon", "sun", "sun").forEach {
            pattern.input(MechanicInput.Token(it, null, UUID.randomUUID()))
        }
        assertFalse(pattern.input(MechanicInput.ResetPattern(null, UUID.randomUUID())))
        assertTrue(pattern.completed())
    }

    @Test
    fun `a future defeat group may wait but a bound group cannot disappear`() {
        val world = MechanicWorld()
        val mechanic = DefeatMechanic(DefeatConfiguration("guards"), world)
        mechanic.start()
        assertEquals(MechanicState.RUNNING, mechanic.state)
        world.group =
            GroupObservation(
                UUID.randomUUID(),
                true,
                listOf(GroupMember(UUID.randomUUID(), MemberOutcome.ALIVE)),
            )
        world.advance(mechanic)
        world.group = null
        assertFailsWith<IllegalStateException> { world.advance(mechanic) }
        assertEquals(
            MechanicState.RUNNING,
            mechanic.state,
            "An observation fault is not an authored gameplay failure",
        )
    }

    @Test
    fun `multi-player reset commits all selected records before any reset notification`() {
        val world = MechanicWorld()
        lateinit var pattern: PatternMechanic
        val snapshots = mutableListOf<List<Int?>>()
        val context =
            object : MechanicContext by world {
                override fun notice(value: MechanicNotice) {
                    if (value is MechanicNotice.PatternReset)
                        snapshots +=
                            listOf(pattern.progress(world.first), pattern.progress(world.second))
                    world.notice(value)
                }
            }
        pattern =
            PatternMechanic(
                PatternConfiguration(
                    setOf("sun", "moon"),
                    PatternAnswer.Fixed(listOf("sun", "moon")),
                    perPlayer = true,
                ),
                context,
            )
        pattern.start()
        pattern.input(MechanicInput.Token("sun", world.first, UUID.randomUUID()))
        pattern.input(MechanicInput.Token("sun", world.second, UUID.randomUUID()))
        pattern.input(MechanicInput.ResetPattern(operation = UUID.randomUUID()))
        assertEquals(listOf(listOf<Int?>(0, 0), listOf<Int?>(0, 0)), snapshots)
    }

    @Test
    fun `all and any reject empty selections and raiders use current GM membership`() {
        assertFalse(allSelected(emptyList(), PlayerPredicate.Always))
        assertFalse(anySelected(emptyList(), PlayerPredicate.Always))
        val gm = UUID.randomUUID()
        val frame =
            PlayerFrame(
                listOf(PlayerObservation(gm, true, LifeState.ALIVE, Participation.ACTIVE, true)),
                setOf(gm),
            )
        assertTrue(PlayerSelection(PlayerCollection.ONLINE_RAIDERS).select(frame).isEmpty())
        assertEquals(1, PlayerSelection().select(frame).size)
    }
}
