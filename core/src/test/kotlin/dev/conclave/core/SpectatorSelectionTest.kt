package dev.conclave.core

import java.util.UUID
import kotlin.test.*
import org.junit.jupiter.api.Test

class SpectatorSelectionTest {
    private val watcher = UUID.randomUUID()
    private val first = UUID.randomUUID()
    private val second = UUID.randomUUID()
    private val third = UUID.randomUUID()
    private val outsider = UUID.randomUUID()
    private val roster = listOf(watcher, first, second, third)

    private fun frame(
        viewerLife: LifeState = LifeState.PASSED_OUT,
        viewerParticipation: Participation = Participation.ACTIVE,
        missing: Set<UUID> = emptySet(),
        dead: Set<UUID> = emptySet(),
        observers: Set<UUID> = emptySet(),
    ) =
        PlayerFrame(
            (roster + outsider).map { id ->
                PlayerObservation(
                    id,
                    id !in missing,
                    if (id == watcher) viewerLife
                    else if (id in dead) LifeState.DEAD else LifeState.ALIVE,
                    if (id == watcher) viewerParticipation
                    else if (id in observers) Participation.OBSERVER else Participation.ACTIVE,
                    false,
                )
            },
            roster.toSet(),
        )

    @Test
    fun `selection follows roster order preserves a target and excludes foreign or ineligible players`() {
        val selection = SpectatorSelection(watcher, roster, false)
        val available = (roster + outsider).toSet()
        assertEquals(first, selection.refresh(frame(), available).target)
        assertFalse(selection.select(outsider))
        assertFalse(selection.select(watcher))
        assertTrue(selection.select(second))
        assertEquals(second, selection.refresh(frame(missing = setOf(first)), available).target)
        assertEquals(second, selection.refresh(frame(), available).target)
        assertEquals(third, selection.refresh(frame(dead = setOf(second)), available).target)
        assertEquals(
            first,
            selection
                .refresh(frame(dead = setOf(second), observers = setOf(third)), available)
                .target,
        )
        assertTrue(selection.cycle(-1))
        assertEquals(first, selection.view().target)
        assertFalse(selection.view().mirrorPrivate)
    }

    @Test
    fun `a grave opportunity never allows a teammate camera and unavailable targets retain a safe waiting view`() {
        val selection = SpectatorSelection(watcher, roster, true)
        assertFalse(selection.refresh(frame(viewerLife = LifeState.DEAD), roster.toSet()).active)
        assertFalse(selection.watch())
        assertFalse(selection.select(first))
        val waiting = selection.refresh(frame(), emptySet())
        assertTrue(waiting.active)
        assertNull(waiting.target)
        assertFalse(waiting.mirrorPrivate)
        assertFalse(selection.leave())
        assertEquals(second, selection.refresh(frame(), setOf(second)).target)
    }

    @Test
    fun `living observers choose when to watch and lose viewing when active admission returns`() {
        val selection = SpectatorSelection(watcher, roster, true)
        val observer =
            frame(viewerLife = LifeState.ALIVE, viewerParticipation = Participation.OBSERVER)
        assertFalse(selection.refresh(observer, roster.toSet()).active)
        assertFalse(selection.permitsPrivate(first))
        assertTrue(selection.watch())
        assertTrue(selection.view().active)
        assertTrue(selection.leave())
        assertFalse(selection.view().active)
        assertTrue(selection.watch())
        assertFalse(selection.refresh(observer, emptySet()).active)
        assertFalse(selection.refresh(observer, roster.toSet()).active)
        assertTrue(selection.watch())
        assertFalse(selection.refresh(frame(viewerLife = LifeState.ALIVE), roster.toSet()).active)
    }

    @Test
    fun `free observers can watch without a target while private access still needs an eligible teammate`() {
        val selection = SpectatorSelection(watcher, roster, true, requireTarget = false)
        val observer =
            frame(viewerLife = LifeState.ALIVE, viewerParticipation = Participation.OBSERVER)
        selection.refresh(observer, emptySet())
        assertTrue(selection.watch())
        assertTrue(selection.view().active)
        assertNull(selection.view().target)
        assertFalse(selection.permitsPrivate(first))
        selection.refresh(observer, setOf(first))
        assertTrue(selection.permitsPrivate(first))
        assertTrue(selection.refresh(observer, emptySet()).active)
        assertFalse(selection.permitsPrivate(first))
        assertTrue(selection.leave())
        assertFalse(selection.view().active)
    }

    @Test
    fun `private mirroring is checked afresh for each attachment and cannot survive closure`() {
        val selection = SpectatorSelection(watcher, roster, true)
        selection.refresh(frame(), (roster + outsider).toSet())
        assertTrue(selection.view().mirrorPrivate)
        assertTrue(selection.permitsPrivate(first))
        assertFalse(selection.permitsPrivate(outsider))
        selection.refresh(frame(missing = setOf(first)), roster.toSet())
        assertFalse(selection.permitsPrivate(first))
        selection.close()
        assertFalse(selection.refresh(frame(), roster.toSet()).active)
        assertFalse(selection.select(second))
        assertFalse(selection.permitsPrivate(second))
    }
}
