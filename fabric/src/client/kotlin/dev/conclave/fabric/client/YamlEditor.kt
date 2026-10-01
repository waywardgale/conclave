package dev.conclave.fabric.client

import dev.conclave.core.BuiltinMechanics
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractScrollArea
import net.minecraft.client.gui.components.AbstractTextAreaWidget
import net.minecraft.client.gui.components.MultilineTextField
import net.minecraft.client.gui.components.Whence
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/** Native text selection and clipboard behavior, with YAML-specific editing and bounded undo. */
internal class YamlEditor(
    private val font: Font,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    initial: String,
    val readOnly: Boolean = false,
    private val changed: (String) -> Unit = {},
) :
    AbstractTextAreaWidget(
        x,
        y,
        width,
        height,
        Component.literal("YAML source"),
        AbstractScrollArea.defaultSettings(4),
    ) {
    private val gutter = 34
    private val field = EditorTextField(font, maxOf(32, width - gutter - 12))

    private data class Edit(val text: String, val cursor: Int)

    private val undo = ArrayDeque<Edit>()
    private val redo = ArrayDeque<Edit>()
    private var previous = initial
    private var changing = false
    private var lineStarts = intArrayOf(0)
    val value
        get() = field.value()

    init {
        field.setCharacterLimit(262144)
        field.setValue(initial)
        rebuildLines(initial)
        field.setValueListener { value ->
            if (!changing) {
                undo.addLast(Edit(previous, field.cursor().coerceAtMost(previous.length)))
                redo.clear()
                while (
                    undo.size > 100 || undo.sumOf { it.text.length.toLong() * 2 } > 4_194_304
                ) undo.removeFirst()
            }
            previous = value
            rebuildLines(value)
            changed(value)
        }
        field.setCursorListener { revealCursor() }
    }

    fun replace(value: String) {
        field.setValue(value)
    }

    private fun rebuildLines(value: String) {
        lineStarts =
            (listOf(0) + value.indices.filter { value[it] == '\n' }.map { it + 1 }).toIntArray()
    }

    private fun revealCursor() {
        val row = field.lineAtCursor * font.lineHeight
        val visible = height - 8
        if (row < scrollAmount()) setScrollAmount(row.toDouble())
        else if (row + font.lineHeight > scrollAmount() + visible)
            setScrollAmount((row + font.lineHeight - visible).toDouble())
    }

    fun goTo(line: Int, column: Int = 1) {
        val start = lineStarts[(line - 1).coerceIn(0, lineStarts.lastIndex)]
        field.setSelecting(false)
        field.seekCursor(Whence.ABSOLUTE, (start + column - 1).coerceIn(0, value.length))
        isFocused = true
    }

    fun find(query: String): Boolean {
        if (query.isEmpty()) return false
        val at = value.indexOf(query, field.cursor() + 1).takeIf { it >= 0 } ?: value.indexOf(query)
        if (at < 0) return false
        field.setSelecting(false)
        field.seekCursor(Whence.ABSOLUTE, at)
        field.setSelecting(true)
        field.seekCursor(Whence.ABSOLUTE, at + query.length)
        field.setSelecting(false)
        isFocused = true
        return true
    }

    fun complete(): Boolean {
        if (readOnly) return false
        val before = value.substring(0, field.cursor())
        val prefix = before.takeLastWhile { it.isLetterOrDigit() || it == '_' }
        if (prefix.isEmpty()) return false
        val suggestion =
            completion.firstOrNull { it.startsWith(prefix) && it != prefix } ?: return false
        field.insertText(suggestion.removePrefix(prefix) + ": ")
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (!isFocused) return false
        if (
            readOnly &&
                !(event.isCopy ||
                    event.isSelectAll ||
                    event.isLeft ||
                    event.isRight ||
                    event.isUp ||
                    event.isDown ||
                    event.key() == GLFW.GLFW_KEY_HOME ||
                    event.key() == GLFW.GLFW_KEY_END)
        )
            return false
        if (
            !readOnly &&
                event.hasControlDownWithQuirk() &&
                event.key() in setOf(GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_Y)
        ) {
            val reverse = event.key() == GLFW.GLFW_KEY_Y || event.hasShiftDown()
            val source = if (reverse) redo else undo
            val target = if (reverse) undo else redo
            if (source.isNotEmpty()) {
                val edit = source.removeLast()
                target.addLast(Edit(value, field.cursor()))
                changing = true
                try {
                    field.setValue(edit.text)
                    field.seekCursor(Whence.ABSOLUTE, edit.cursor)
                } finally {
                    changing = false
                }
            }
            return true
        }
        if (!readOnly && event.hasControlDownWithQuirk() && event.key() == GLFW.GLFW_KEY_SPACE)
            return complete()
        if (!readOnly && event.key() == GLFW.GLFW_KEY_TAB) {
            field.insertText("  ")
            return true
        }
        if (!readOnly && event.key() == GLFW.GLFW_KEY_ENTER) {
            val before = value.substring(0, field.cursor())
            val line = before.substringAfterLast('\n')
            val indent =
                line.takeWhile { it == ' ' } + if (line.trimEnd().endsWith(':')) "  " else ""
            field.insertText("\n$indent")
            return true
        }
        return field.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (!isFocused || readOnly || !event.isAllowedChatCharacter) return false
        field.insertText(event.codepointAsString())
        return true
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        field.setSelecting(event.hasShiftDown())
        seek(event)
        if (doubleClick) field.selectWordAtCursor()
    }

    override fun onDrag(event: MouseButtonEvent, dx: Double, dy: Double) {
        field.setSelecting(true)
        seek(event)
        field.setSelecting(false)
    }

    private fun seek(event: MouseButtonEvent) {
        field.seekCursorToPoint(
            event.x() - innerLeft - gutter,
            event.y() - innerTop + scrollAmount(),
        )
    }

    override fun getInnerHeight() = field.lineCount * font.lineHeight

    override fun scrollRate() = font.lineHeight.toDouble() * 3

    override fun extractContents(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        val first = (scrollAmount() / font.lineHeight).toInt().coerceAtLeast(0)
        val last = minOf(field.lineCount, first + height / font.lineHeight + 2)
        val selected = field.selection()
        for (row in first until last) {
            val view = field.line(row)
            val line = value.substring(view.beginIndex(), view.endIndex())
            val yy = innerTop + row * font.lineHeight
            val at = lineStarts.binarySearch(view.beginIndex())
            val lineNumber = if (at >= 0) at else -at - 2
            if (at >= 0)
                graphics.text(
                    font,
                    (lineNumber + 1).toString(),
                    innerLeft,
                    yy,
                    0xff7f8a98.toInt(),
                    false,
                )
            val from = maxOf(view.beginIndex(), selected.beginIndex())
            val to = minOf(view.endIndex(), selected.endIndex())
            if (from < to)
                graphics.fill(
                    innerLeft + gutter + font.width(value.substring(view.beginIndex(), from)),
                    yy,
                    innerLeft + gutter + font.width(value.substring(view.beginIndex(), to)),
                    yy + font.lineHeight,
                    0xff344b69.toInt(),
                )
            val comment = line.indexOf('#')
            val colon = line.indexOf(':')
            val keyEnd = if (colon >= 0 && (comment < 0 || colon < comment)) colon else -1
            var xx = innerLeft + gutter
            for ((part, color) in
                when {
                    comment == line.indexOfFirst { !it.isWhitespace() } && comment >= 0 ->
                        listOf(line to 0xff7f9b7c.toInt())
                    keyEnd >= 0 ->
                        listOf(
                            line.take(keyEnd) to 0xff91c6e8.toInt(),
                            line.drop(keyEnd) to 0xffe0d5b7.toInt(),
                        )
                    else -> listOf(line to 0xffdddddd.toInt())
                }) {
                graphics.text(font, part, xx, yy, color, false)
                xx += font.width(part)
            }
            if (
                isFocused &&
                    field.cursor() in view.beginIndex()..view.endIndex() &&
                    row == field.lineAtCursor
            ) {
                val cx =
                    innerLeft +
                        gutter +
                        font.width(value.substring(view.beginIndex(), field.cursor()))
                graphics.fill(cx, yy, cx + 1, yy + font.lineHeight, 0xffeeeeee.toInt())
            }
        }
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(
            NarratedElementType.TITLE,
            Component.literal(
                "YAML editor, line ${field.lineAtCursor+1}. ${if(readOnly)"Read only." else "Control Z to undo, Control F to search, Control Space to complete."}"
            ),
        )
    }

    companion object {
        private val completion by lazy {
            val fields =
                mutableSetOf(
                    "schema",
                    "namespace",
                    "encounter",
                    "arena",
                    "location",
                    "mechanic",
                    "id",
                    "name",
                    "type",
                    "use",
                    "with",
                    "parameters",
                    "parameter",
                    "default",
                    "description",
                    "values",
                    "min",
                    "max",
                    "min_length",
                    "max_length",
                    "min_items",
                    "max_items",
                    "phases",
                    "start",
                    "success",
                    "failure",
                    "next",
                    "complete",
                    "wipe",
                    "objectives",
                    "mechanics",
                    "rules",
                    "on",
                    "source",
                    "event",
                    "if",
                    "do",
                    "counters",
                    "timers",
                    "boundary",
                    "areas",
                    "locations",
                    "dimension",
                    "position",
                    "facing",
                    "width",
                    "depth",
                    "height",
                    "radius",
                    "include",
                    "exclude",
                    "membership",
                )
            fun collect(schema: dev.conclave.core.SchemaDescription) {
                fields += schema.properties.keys
                schema.properties.values.forEach(::collect)
                schema.items?.let(::collect)
                schema.alternatives.forEach(::collect)
                schema.definitions.values.forEach(::collect)
            }
            BuiltinMechanics.registry().descriptions().values.forEach(::collect)
            fields.sorted()
        }
    }
}

private data class TextRange(private val begin: Int, private val end: Int) {
    fun beginIndex() = begin

    fun endIndex() = end
}

private class EditorTextField(font: Font, width: Int) : MultilineTextField(font, width) {
    fun line(index: Int): TextRange {
        val view = getLineView(index)
        return TextRange(view.beginIndex(), view.endIndex())
    }

    fun selection(): TextRange {
        val view = getSelected()
        return TextRange(view.beginIndex(), view.endIndex())
    }
}
