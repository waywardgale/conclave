package dev.conclave.core

import java.util.concurrent.atomic.AtomicBoolean

sealed interface PublicationResult {
    data class Published(val revision: String, val changed: Boolean) : PublicationResult

    data class Conflict(val current: String?) : PublicationResult

    data class UnknownRevision(val revision: String) : PublicationResult
}

/**
 * In-memory revision/pin semantics. The production host must durably commit before announcing
 * publication.
 */
class RevisionHistory(private val retained: Int = 10) {
    private data class Entry(val catalog: CompiledCatalog, var pins: Int = 0)

    private val entries = linkedMapOf<String, Entry>()
    private val activated = mutableListOf<String>()
    private var active: String? = null

    init {
        require(retained > 0)
    }

    @Synchronized fun current(): String? = active

    // ASVS 15.4.1, 15.4.2: baseline comparison, activation, pin accounting and pruning share one
    // lock.
    @Synchronized
    fun publish(catalog: CompiledCatalog, expected: String?): PublicationResult {
        if (active != expected) return PublicationResult.Conflict(active)
        val changed = active != catalog.revision
        entries.putIfAbsent(catalog.revision, Entry(catalog))
        activate(catalog.revision)
        return PublicationResult.Published(catalog.revision, changed)
    }

    /**
     * Caller supplies a freshly revalidated catalog for rollback; historical bytes alone are
     * insufficient.
     */
    @Synchronized
    fun rollback(revalidated: CompiledCatalog, expected: String?): PublicationResult {
        if (active != expected) return PublicationResult.Conflict(active)
        if (revalidated.revision !in entries)
            return PublicationResult.UnknownRevision(revalidated.revision)
        return publish(revalidated, expected)
    }

    @Synchronized
    fun pin(revision: String? = null): RevisionPin? {
        // Select current inside the same lock as retention; a default argument would read it too
        // early.
        val entry = entries[revision ?: active] ?: return null
        entry.pins = Math.incrementExact(entry.pins)
        return RevisionPin(entry.catalog) { release(entry.catalog.revision) }
    }

    @Synchronized fun revisions(): List<String> = java.util.List.copyOf(activated)

    private fun activate(revision: String) {
        active = revision
        activated.remove(revision)
        activated.add(0, revision)
        prune()
    }

    @Synchronized
    private fun release(revision: String) {
        val entry = entries.getValue(revision)
        check(entry.pins > 0)
        entry.pins--
        prune()
    }

    private fun prune() {
        val keep = activated.take(retained).toSet()
        val remove = entries.filter { (id, entry) -> id !in keep && entry.pins == 0 }.keys
        remove.forEach {
            entries.remove(it)
            activated.remove(it)
        }
    }
}

class RevisionPin
internal constructor(val catalog: CompiledCatalog, private val release: () -> Unit) :
    AutoCloseable {
    private val closed = AtomicBoolean()

    override fun close() {
        if (closed.compareAndSet(false, true)) release()
    }
}
