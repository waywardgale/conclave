package dev.conclave.fabric.client

import dev.conclave.fabric.*
import dev.conclave.storage.*
import java.security.MessageDigest
import java.util.UUID
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

internal class AuthoringScreen : Screen(Component.literal("Conclave")) {
    private enum class Page {
        DRAFTS,
        EDITOR,
        HISTORY,
        PUBLISH,
        CONFLICT,
    }

    private class Buffer(
        var text: String,
        var saved: String?,
        var token: UUID?,
        var conflict: Boolean = false,
        var server: DraftFile? = null,
    )

    private var page = Page.DRAFTS
    private var drafts: List<DraftSummary> = emptyList()
    private var snapshot: DraftSnapshot? = null
    private val buffers = linkedMapOf<String, Buffer>()
    private var selected: String? = null
    private var editor: YamlEditor? = null
    private var filename: EditBox? = null
    private var draftName: EditBox? = null
    private var search: EditBox? = null
    private var status = "Loading Conclave…"
    private var operator = false
    private var allowed = true
    private var history: List<String> = emptyList()
    private var current: String? = null
    private var offset = 0
    private var ticks = 0
    private var recoveryLoading = false
    private var recoveryBlocked = false
    private var importLoading = false
    private var localGeneration = 0L
    private val localLoading
        get() = recoveryLoading || importLoading

    private var recovered: UUID? = null
    private var recoveryDirectory: java.nio.file.Path? = null
    private var diagnostics = emptyList<dev.conclave.core.Diagnostic>()

    override fun isPauseScreen() = false

    fun message(value: String) {
        status = value
    }

    private fun ask(request: AuthorRequest) {
        if (localLoading) return
        if (!allowed) {
            status = "Editing authority is unavailable. You can copy or export your local text."
            return
        }
        if (AuthoringClient.request(request)) status = "Waiting for server…"
        else status = "An authoring operation is still in progress."
    }

    fun receive(reply: AuthorReply, request: AuthorRequest?) {
        operator = reply.operator
        status = reply.message
        diagnostics = reply.diagnostics
        if (reply.result == AuthorResult.DENIED) {
            allowed = false
            saveRecovery()
            rebuildWidgets()
            return
        }
        allowed = true
        if (
            request == AuthorRequest.ListDrafts ||
                request == null && reply.draft == null && reply.history.isEmpty()
        ) {
            saveRecovery()
            drafts = reply.drafts
            page = Page.DRAFTS
            offset = 0
        }
        if (request == AuthorRequest.History) {
            history = reply.history
            current = reply.current
            page = Page.HISTORY
            offset = 0
        }
        reply.draft?.let { saved ->
            val same = snapshot?.summary?.id == saved.summary.id
            if (!same) {
                saveRecovery()
                localGeneration++
                recoveryLoading = false
                recoveryBlocked = false
                importLoading = false
                buffers.clear()
                selected = null
                recovered = null
            }
            val server = saved.files.associateBy { it.file }
            for (file in (buffers.keys + server.keys).toList()) {
                val actual = server[file]
                val buffer = buffers[file]
                if (buffer == null && actual != null)
                    buffers[file] = Buffer(actual.text, actual.text, actual.token)
                else if (buffer != null) {
                    val submitted =
                        (request as? AuthorRequest.Save)?.changes?.find { it.file == file }
                    if (
                        submitted != null &&
                            reply.result == AuthorResult.OK &&
                            submitted.text == actual?.text
                    ) {
                        buffer.saved = actual?.text
                        buffer.token = actual?.token
                        buffer.conflict = false
                        buffer.server = null
                        if (actual == null && buffer.text == submitted.text) buffers.remove(file)
                    } else if (buffer.text == buffer.saved && !buffer.conflict) {
                        if (actual == null) buffers.remove(file)
                        else {
                            buffer.text = actual.text
                            buffer.saved = actual.text
                            buffer.token = actual.token
                        }
                    } else if (buffer.token != actual?.token) {
                        buffer.conflict = true
                        buffer.server = actual
                    }
                }
            }
            snapshot = saved
            if (selected !in buffers) selected = buffers.keys.firstOrNull()
            restoreRecovery()
            page = Page.EDITOR
        }
        if (reply.current != null) current = reply.current
        rebuildWidgets()
    }

    override fun init() {
        editor = null
        filename = null
        draftName = null
        search = null
        if (localLoading) {
            button("Close", 8, 23, 80) { onClose() }
            return
        }
        val buttons =
            listOf(
                "Drafts" to
                    {
                        saveRecovery()
                        ask(AuthorRequest.ListDrafts)
                    },
                "Save" to { save(false) },
                "Save all" to { save(true) },
                "Validate" to { validate() },
                "Publish" to { reviewPublish() },
                "History" to { ask(AuthorRequest.History) },
                "Close" to { onClose() },
            )
        val columns = if (width >= 360) buttons.size else 4
        val cell = (width - 16) / columns
        buttons.forEachIndexed { index, (label, action) ->
            button(
                    label,
                    8 + (index % columns) * cell,
                    23 + (index / columns) * 22,
                    cell - 4,
                    action,
                )
                .active = label == "Close" || allowed && (label != "Publish" || operator)
        }
        val top = 29 + ((buttons.size + columns - 1) / columns) * 22
        when (page) {
            Page.DRAFTS -> {
                draftName =
                    addRenderableWidget(
                        EditBox(font, 12, top, width - 112, 20, Component.literal("New draft name"))
                            .apply {
                                setMaxLength(64)
                                setHint(Component.literal("Name a new draft"))
                            }
                    )
                button("Create", width - 94, top, 82) {
                    val name = draftName?.value.orEmpty()
                    if (name.isBlank()) status = "Enter a draft name."
                    else ask(AuthorRequest.Create(UUID.randomUUID(), name, false))
                }
                rows(drafts.size, top + 30) { index, y ->
                    val draft = drafts[index]
                    button(
                        "${draft.name}${if(draft.shared)" • Shared" else ""}",
                        12,
                        y,
                        width - 24,
                    ) {
                        saveRecovery()
                        ask(AuthorRequest.Open(draft.id))
                    }
                }
            }
            Page.EDITOR -> {
                val left = if (width >= 500) 142 else 100
                filename =
                    addRenderableWidget(
                        EditBox(
                                font,
                                left,
                                top,
                                maxOf(50, width - left - 78),
                                20,
                                Component.literal("File name"),
                            )
                            .apply {
                                setMaxLength(256)
                                value = selected.orEmpty()
                                setHint(Component.literal("folder/file.yaml"))
                            }
                    )
                button("New file", width - 72, top, 64) {
                    val name = filename?.value.orEmpty()
                    try {
                        DraftStore.validateFile(name)
                        if (name in buffers) selected = name
                        else {
                            buffers[name] = Buffer("schema: 1\n", null, null)
                            selected = name
                        }
                        rebuildWidgets()
                    } catch (_: IllegalArgumentException) {
                        status = "Use a relative .yaml filename such as arenas/hall.yaml."
                    }
                }
                val visible = maxOf(1, (height - top - 105) / 22)
                buffers.keys.toList().drop(offset).take(visible).forEachIndexed { index, file ->
                    val b = buffers.getValue(file)
                    button(
                            (if (b.conflict) "! " else if (b.text != b.saved) "* " else "") +
                                file.substringAfterLast('/'),
                            8,
                            top + index * 22,
                            left - 16,
                        ) {
                            selected = file
                            rebuildWidgets()
                        }
                        .setTooltip(Tooltip.create(Component.literal(file)))
                }
                button("↑", 8, height - 75, 28) {
                    offset = maxOf(0, offset - visible)
                    rebuildWidgets()
                }
                button("↓", 40, height - 75, 28) {
                    offset = minOf(maxOf(0, buffers.size - visible), offset + visible)
                    rebuildWidgets()
                }
                val buffer = selected?.let(buffers::get)
                if (buffer != null)
                    editor =
                        addRenderableWidget(
                            YamlEditor(
                                font,
                                left,
                                top + 25,
                                maxOf(40, width - left - 10),
                                maxOf(40, height - top - 78),
                                buffer.text,
                            ) {
                                buffer.text = it
                            }
                        )
                search =
                    addRenderableWidget(
                        EditBox(
                                font,
                                left,
                                height - 47,
                                maxOf(45, width - left - 143),
                                20,
                                Component.literal("Find text"),
                            )
                            .apply {
                                setMaxLength(256)
                                setHint(Component.literal("Find text"))
                            }
                    )
                button("Find", width - 138, height - 47, 42) {
                    if (editor?.find(search?.value.orEmpty()) != true)
                        status = "Text was not found."
                }
                button(
                    if (buffer?.conflict == true) "Conflict" else "Export",
                    width - 92,
                    height - 47,
                    80,
                ) {
                    if (buffer?.conflict == true) {
                        page = Page.CONFLICT
                        rebuildWidgets()
                    } else exportSelected()
                }
                val issue = diagnostics.firstOrNull { it.source.file == selected }
                if (issue != null)
                    button(
                        "Go to ${issue.source.line}:${issue.source.column}",
                        8,
                        height - 100,
                        left - 16,
                    ) {
                        editor?.goTo(issue.source.line, issue.source.column)
                        status = issue.message
                    }
            }
            Page.HISTORY ->
                rows(history.size, top) { index, y ->
                    val revision = history[index]
                    button(
                        "${revision.take(16)}${if(revision==current)" • Current" else ""}",
                        12,
                        y,
                        width - 24,
                    ) {
                        if (!operator) {
                            status = "Rollback requires operator authority."
                            return@button
                        }
                        minecraft.gui.setScreen(
                            ReviewScreen(
                                this,
                                "Roll back content?",
                                "Activate retained revision ${revision.take(16)} for future attempts? Current attempts keep their original content.",
                            ) {
                                ask(AuthorRequest.Rollback(revision, current))
                            }
                        )
                    }
                }
            Page.PUBLISH -> {
                val draft = snapshot ?: return
                addRenderableWidget(
                    YamlEditor(
                        font,
                        12,
                        top,
                        width - 24,
                        maxOf(45, height - top - 90),
                        buildString {
                            append(
                                "Publish ${draft.summary.name}\nDraft version ${draft.summary.version}\n\nComplete catalog, ${draft.files.size} files:\n"
                            )
                            draft.files.forEach {
                                append(it.file)
                                append('\n')
                            }
                            append(
                                "\nThe server validates all files together.\nThis revision applies to future attempts."
                            )
                        },
                        true,
                    )
                )
                button("Publish this version", 12, height - 75, 160) {
                    page = Page.EDITOR
                    ask(AuthorRequest.Publish(draft.summary.id, draft.summary.version))
                    rebuildWidgets()
                }
                button("Back", 180, height - 75, 65) {
                    page = Page.EDITOR
                    rebuildWidgets()
                }
            }
            Page.CONFLICT -> {
                val buffer = selected?.let(buffers::get) ?: return
                val half = (width - 30) / 2
                addRenderableWidget(
                    YamlEditor(font, 10, top, half, maxOf(45, height - top - 90), buffer.text, true)
                )
                addRenderableWidget(
                    YamlEditor(
                        font,
                        20 + half,
                        top,
                        half,
                        maxOf(45, height - top - 90),
                        buffer.server?.text ?: "# This file was deleted on the server.",
                        true,
                    )
                )
                button("Keep my buffer", 10, height - 75, 120) {
                    minecraft.gui.setScreen(
                        ReviewScreen(
                            this,
                            "Reconcile this file?",
                            "Keep your text and adopt the current server token? A later Save will replace that server file after checking for another change.",
                        ) {
                            buffer.saved = buffer.server?.text
                            buffer.token = buffer.server?.token
                            buffer.server = null
                            buffer.conflict = false
                            page = Page.EDITOR
                            rebuildWidgets()
                        }
                    )
                }
                button("Use server text", 140, height - 75, 120) {
                    buffer.text = buffer.server?.text.orEmpty()
                    buffer.saved = buffer.server?.text
                    buffer.token = buffer.server?.token
                    buffer.conflict = false
                    buffer.server = null
                    page = Page.EDITOR
                    rebuildWidgets()
                }
                button("Back", 270, height - 75, 55) {
                    page = Page.EDITOR
                    rebuildWidgets()
                }
            }
        }
    }

    private fun button(text: String, x: Int, y: Int, width: Int, action: () -> Unit) =
        addRenderableWidget(
            Button.builder(Component.literal(text)) { action() }.bounds(x, y, width, 20).build()
        )

    private fun rows(size: Int, top: Int, row: (Int, Int) -> Unit) {
        val visible = maxOf(1, (height - top - 75) / 24)
        for (index in offset until minOf(size, offset + visible)) row(
            index,
            top + (index - offset) * 24,
        )
        button("Previous", 12, height - 75, 82) {
            offset = maxOf(0, offset - visible)
            rebuildWidgets()
        }
        button("Next", 100, height - 75, 65) {
            offset = minOf(maxOf(0, size - visible), offset + visible)
            rebuildWidgets()
        }
    }

    private fun save(all: Boolean) {
        if (localLoading) return
        val draft =
            snapshot
                ?: run {
                    status = "Open a draft first."
                    return
                }
        val candidates =
            if (all) buffers.entries.toList() else buffers.entries.filter { it.key == selected }
        if (candidates.any { it.value.conflict }) {
            status = "Resolve conflicting files before saving."
            return
        }
        val changes =
            candidates
                .filter { it.value.text != it.value.saved }
                .map { DraftChange(it.key, it.value.token, it.value.text) }
        if (changes.isEmpty()) {
            status = "There are no unsaved changes."
            return
        }
        saveRecovery()
        ask(AuthorRequest.Save(draft.summary.id, changes, if (all) draft.summary.version else null))
    }

    private fun validate() {
        val draft = snapshot ?: return
        if (buffers.any { it.value.text != it.value.saved }) {
            status = "Save local edits before validating the complete server draft."
            return
        }
        ask(AuthorRequest.Validate(draft.summary.id, draft.summary.version))
    }

    private fun reviewPublish() {
        if (!operator) {
            status = "Publication requires operator authority."
            return
        }
        if (snapshot == null) return
        if (buffers.any { it.value.text != it.value.saved || it.value.conflict }) {
            status = "Save and reconcile local edits before publication."
            return
        }
        page = Page.PUBLISH
        rebuildWidgets()
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.hasControlDownWithQuirk() && event.key() == GLFW.GLFW_KEY_S) {
            save(event.hasShiftDown())
            return true
        }
        if (event.hasControlDownWithQuirk() && event.key() == GLFW.GLFW_KEY_F) {
            search?.let { setFocused(it) }
            return true
        }
        return super.keyPressed(event)
    }

    override fun extractRenderState(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        graphics.fill(0, 0, width, height, 0xf0151920.toInt())
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        graphics.text(
            font,
            "Conclave${snapshot?.takeIf{page!=Page.DRAFTS}?.let{" / ${it.summary.name}"}.orEmpty()}",
            10,
            9,
            0xffeeeeee.toInt(),
            false,
        )
        graphics.textWithWordWrap(
            font,
            Component.literal(status),
            10,
            height - 23,
            width - 20,
            0xffbbc8d6.toInt(),
            false,
        )
    }

    override fun tick() {
        if (++ticks % 100 == 0) saveRecovery()
    }

    override fun removed() {
        saveRecovery()
    }

    private fun recoveryPath(): java.nio.file.Path? {
        val draft = snapshot ?: return null
        val directory =
            recoveryDirectory
                ?: run {
                    val server =
                        minecraft.currentServer?.ip
                            ?: minecraft.singleplayerServer
                                ?.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                                ?.toString()
                            ?: return null
                    val key =
                        MessageDigest.getInstance("SHA-256")
                            .digest(server.toByteArray())
                            .joinToString("") { "%02x".format(it) }
                    minecraft.gameDirectory.toPath().resolve("conclave/unsaved/$key").also {
                        recoveryDirectory = it
                    }
                }
        return directory.resolve("${draft.summary.id}.bin")
    }

    fun saveRecovery() {
        // A pending or unreadable recovery must survive closing this screen.
        if (recoveryLoading || recoveryBlocked) return
        val draft = snapshot?.summary?.id ?: return
        val path = recoveryPath() ?: return
        val generation = localGeneration
        val changes =
            buffers
                .filter { it.value.text != it.value.saved }
                .map { DraftChange(it.key, it.value.token, it.value.text) }
        LocalAuthorFiles.shared.saveRecovery(path, draft, changes).whenComplete { _, failure ->
            if (failure != null)
                minecraft.execute {
                    if (generation == localGeneration)
                        status =
                            "Local recovery could not be saved. Copy or export your unsaved text before closing."
                }
        }
    }

    private fun restoreRecovery() {
        val draft = snapshot ?: return
        if (recovered == draft.summary.id) return
        recovered = draft.summary.id
        val path = recoveryPath() ?: return
        val generation = localGeneration
        recoveryLoading = true
        status = "Loading local recovery…"
        LocalAuthorFiles.shared.readRecovery(path, draft.summary.id).whenComplete {
            recovery,
            failure ->
            minecraft.execute {
                if (generation != localGeneration || snapshot?.summary?.id != draft.summary.id)
                    return@execute
                recoveryLoading = false
                if (failure != null) {
                    recoveryBlocked = true
                    status =
                        "Local recovery could not be read and has been preserved. Export new edits before closing."
                } else if (recovery != null) {
                    // Server replies may have arrived while the worker was reading.
                    val serverFiles = checkNotNull(snapshot).files.associateBy { it.file }
                    recovery.changes.forEach { change ->
                        val server = serverFiles[change.file]
                        if (change.text != null && change.text != server?.text)
                            buffers[change.file] =
                                Buffer(
                                    checkNotNull(change.text),
                                    server?.text,
                                    change.expected,
                                    change.expected != server?.token,
                                    server,
                                )
                    }
                    if (selected == null) selected = buffers.keys.firstOrNull()
                    status = "Recovered local unsaved text. Review it before saving."
                } else status = "Draft opened."
                if (minecraft.gui.screen() === this) rebuildWidgets()
            }
        }
    }

    private fun exportSelected() {
        val file = selected ?: return
        val buffer = buffers[file] ?: return
        val generation = localGeneration
        val directory = minecraft.gameDirectory.toPath().resolve("conclave/authoring")
        status = "Exporting YAML…"
        LocalAuthorFiles.shared.export(directory, file, buffer.text).whenComplete { target, failure
            ->
            minecraft.execute {
                if (generation != localGeneration) return@execute
                status =
                    if (failure == null)
                        "Exported ${target.fileName} to the Minecraft conclave/authoring folder."
                    else "The YAML export could not be written."
            }
        }
    }

    override fun onFilesDrop(paths: List<java.nio.file.Path>) {
        val draft = snapshot?.summary?.id
        if (draft == null) {
            status = "Open a draft before importing YAML."
            return
        }
        if (localLoading) return
        val generation = localGeneration
        importLoading = true
        status = "Reading selected YAML files…"
        rebuildWidgets()
        LocalAuthorFiles.shared.importFiles(paths).whenComplete { imports, failure ->
            minecraft.execute {
                if (generation != localGeneration || snapshot?.summary?.id != draft) return@execute
                importLoading = false
                if (minecraft.gui.screen() !== this) return@execute
                if (failure != null) {
                    status =
                        "Import requires selected UTF-8 YAML files within the draft size limits."
                    rebuildWidgets()
                    return@execute
                }
                status = "Review the selected files before importing."
                minecraft.gui.setScreen(
                    ReviewScreen(
                        this,
                        "Import YAML?",
                        imports.joinToString("\n") { (name, _) ->
                            "${if(name in buffers)"Replace" else "Add"} $name"
                        } +
                            "\n\nImport changes local buffers. Save explicitly to update the draft.",
                    ) {
                        if (generation == localGeneration && snapshot?.summary?.id == draft) {
                            imports.forEach { (file, text) ->
                                val previous = buffers[file]
                                if (previous == null) buffers[file] = Buffer(text, null, null)
                                else previous.text = text
                            }
                            selected = imports.first().file
                            page = Page.EDITOR
                            status = "Imported into local buffers. Save to update the server draft."
                            saveRecovery()
                            rebuildWidgets()
                        }
                    }
                )
            }
        }
    }
}

internal class ReviewScreen(
    private val parent: Screen,
    title: String,
    private val detail: String,
    private val confirmed: () -> Unit,
) : Screen(Component.literal(title)) {
    override fun isPauseScreen() = false

    override fun init() {
        addRenderableWidget(
            YamlEditor(font, 12, 35, width - 24, maxOf(50, height - 80), detail, true)
        )
        addRenderableWidget(
            Button.builder(Component.literal("Confirm")) {
                    minecraft.gui.setScreen(parent)
                    confirmed()
                }
                .bounds(12, height - 32, 100, 20)
                .build()
        )
        addRenderableWidget(
            Button.builder(Component.literal("Cancel")) { onClose() }
                .bounds(120, height - 32, 100, 20)
                .build()
        )
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, x: Int, y: Int, delta: Float) {
        graphics.fill(0, 0, width, height, 0xff151920.toInt())
        super.extractRenderState(graphics, x, y, delta)
        graphics.text(font, title, 12, 12, 0xffffffff.toInt(), false)
    }

    override fun onClose() {
        minecraft.gui.setScreen(parent)
    }
}
