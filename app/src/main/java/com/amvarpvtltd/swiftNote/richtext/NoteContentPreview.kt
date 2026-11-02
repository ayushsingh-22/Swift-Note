package com.amvarpvtltd.swiftNote.richtext

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.amvarpvtltd.swiftNote.checklist.ChecklistParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

data class ChecklistPreviewLine(val text: AnnotatedString, val checked: Boolean)

data class NoteContentPreview(
    val body: AnnotatedString,
    val checklist: Boolean = false,
    val lines: List<ChecklistPreviewLine> = emptyList(),
    val checked: Int = 0,
    val total: Int = 0
) {
    val plainText: String get() = if (checklist) {
        lines.joinToString("\n") { "${if (it.checked) "☑" else "☐"} ${it.text.text}" }
            .ifBlank { body.text }
    } else body.text
}

/** Small, read-only preview projection. Checklist JSON is decoded once, never rendered as note text. */
object NotePreviewFormatter {
    fun create(content: String): NoteContentPreview {
        if (ChecklistParser.isChecklistContent(content)) {
            val items = ChecklistParser.parseItems(content)
            return NoteContentPreview(
                body = AnnotatedString(if (items.isEmpty()) "Checklist unavailable" else ""),
                checklist = true,
                lines = items.take(3).map {
                    ChecklistPreviewLine(RichTextBridge.toPreviewAnnotatedString(it.text, 120), it.isChecked)
                },
                checked = items.count { it.isChecked },
                total = items.size
            )
        }
        return NoteContentPreview(RichTextBridge.toPreviewAnnotatedString(content))
    }
}

/** Keeps inline styles and paragraph/list boundaries without constructing a rich-editor state per card. */
internal fun buildRichTextPreview(content: String, maxChars: Int): AnnotatedString {
    require(maxChars > 0)
    if (content.isBlank()) return AnnotatedString("")
    val html = if (!RichTextBridge.containsHtml(content) && MarkdownToHtmlConverter.containsMarkdown(content)) {
        MarkdownToHtmlConverter.convert(content) ?: content
    } else content
    if (!RichTextBridge.containsHtml(html)) return AnnotatedString(content.take(maxChars))

    return try {
        val result = AnnotatedString.Builder()
        var lastChar: Char? = null
        fun append(value: String) {
            val available = maxChars - result.length
            if (available > 0) {
                val chunk = value.take(available)
                result.append(chunk)
                if (chunk.isNotEmpty()) lastChar = chunk.last()
            }
        }
        fun newline() {
            if (result.length > 0 && lastChar != '\n') append("\n")
        }
        fun render(node: Node) {
            if (result.length >= maxChars) return
            when (node) {
                is TextNode -> append(node.text())
                is Element -> {
                    val tag = node.normalName()
                    if (tag in listOf("script", "style")) return
                    val block = tag in listOf("p", "div", "h1", "h2", "h3", "pre", "li", "ul", "ol")
                    if (block) newline()
                    if (tag == "br") { append("\n"); return }
                    if (tag == "input") {
                        append(if (node.hasAttr("checked")) "☑ " else "☐ ")
                        return
                    }
                    if (tag == "li") {
                        if (node.parent()?.normalName() == "ol") {
                            val index = node.elementSiblingIndex() + 1
                            append("$index. ")
                        } else append("• ")
                    }
                    val style = when (tag) {
                        "b", "strong", "h1", "h2", "h3" -> SpanStyle(fontWeight = FontWeight.Bold)
                        "i", "em" -> SpanStyle(fontStyle = FontStyle.Italic)
                        "u", "a" -> SpanStyle(textDecoration = TextDecoration.Underline)
                        "s", "del", "strike" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                        "code", "pre" -> SpanStyle(fontFamily = FontFamily.Monospace)
                        else -> null
                    }
                    if (style != null) result.pushStyle(style)
                    node.childNodes().forEach { render(it) }
                    if (style != null) result.pop()
                    if (block) newline()
                }
            }
        }
        render(Jsoup.parseBodyFragment(html).body())
        val annotated = result.toAnnotatedString()
        annotated.subSequence(0, annotated.text.trimEnd().length)
    } catch (_: Exception) {
        AnnotatedString(RichTextBridge.stripHtmlToPlainText(content).take(maxChars))
    }
}
