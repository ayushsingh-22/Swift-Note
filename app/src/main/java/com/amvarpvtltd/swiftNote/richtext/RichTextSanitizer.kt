package com.amvarpvtltd.swiftNote.richtext

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Sanitizes arbitrary HTML (from clipboard, share intents, web pages) down to
 * the strict subset of tags supported by SwiftNote's rich text editor.
 *
 * Supported tags: b, i, u, h1, h2, p, br, ul, ol, li, a, code, pre, input
 * Supported attributes: a[href], input[type,checked]
 *
 * All other tags are unwrapped (contents preserved, tag removed).
 * All other attributes are stripped.
 * Semantic equivalents are normalized: strong→b, em→i, h3–h6→h2.
 */
object RichTextSanitizer {

    private val ALLOWED_TAGS = setOf(
        "b", "strong", "i", "em", "u", "h1", "h2", "p", "br",
        "ul", "ol", "li", "a", "code", "pre", "input"
    )

    private val ALLOWED_ATTRS = mapOf(
        "a" to setOf("href"),
        "input" to setOf("type", "checked")
    )

    /**
     * Sanitize arbitrary HTML to SwiftNote's supported subset.
     *
     * @param html Raw HTML string (from clipboard, share intent, etc.)
     * @return Cleaned HTML containing only allowed tags and attributes.
     */
    fun sanitize(html: String): String {
        if (html.isBlank()) return ""

        val doc = Jsoup.parseBodyFragment(html)
        val body = doc.body()

        // Normalize semantic equivalents
        body.select("strong").forEach { it.tagName("b") }
        body.select("em").forEach { it.tagName("i") }

        // Downgrade h3–h6 to h2 (we only support h1/h2)
        body.select("h3, h4, h5, h6").forEach { it.tagName("h2") }

        // Remove script, style, and other dangerous/useless elements entirely (not unwrap)
        body.select("script, style, noscript, iframe, object, embed, applet, form").remove()

        // Convert <span style="font-weight:bold"> patterns to <b>
        body.select("span").forEach { span ->
            val style = span.attr("style").lowercase()
            when {
                "font-weight" in style && ("bold" in style || "700" in style || "800" in style || "900" in style) -> {
                    span.tagName("b")
                    span.removeAttr("style")
                }
                "font-style" in style && "italic" in style -> {
                    span.tagName("i")
                    span.removeAttr("style")
                }
                "text-decoration" in style && "underline" in style -> {
                    span.tagName("u")
                    span.removeAttr("style")
                }
                else -> {
                    // Unknown span — unwrap (keep contents)
                    span.unwrap()
                }
            }
        }

        // Convert <div> to <p> (block-level normalization)
        body.select("div").forEach { it.tagName("p") }

        // Now strip all disallowed tags (unwrap preserves text content)
        stripDisallowedTags(body)

        // Strip disallowed attributes from remaining allowed tags
        stripDisallowedAttributes(body)

        // Clean up empty paragraphs and excessive whitespace
        body.select("p").forEach { p ->
            if (p.text().isBlank() && p.children().isEmpty()) {
                p.remove()
            }
        }

        return body.html()
            .trim()
            .replace(Regex("\\n{3,}"), "\n\n") // Collapse excessive newlines
    }

    /**
     * Quick check: does the clipboard text contain any HTML at all?
     * Useful to decide whether to offer "Paste with formatting" vs regular paste.
     */
    fun containsHtml(text: String): Boolean {
        if (text.isBlank() || '<' !in text) return false
        return Regex(
            "<(/?)(b|strong|i|em|u|h[1-6]|p|br|div|span|ul|ol|li|a|code|pre|input|table|tr|td|th|img)\\b[^>]*>",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)
    }

    private fun stripDisallowedTags(element: Element) {
        // Process children in reverse to avoid index issues during modification
        val children = element.children().toList()
        for (child in children) {
            // Recurse first (depth-first)
            stripDisallowedTags(child)

            val tag = child.tagName().lowercase()
            if (tag !in ALLOWED_TAGS && tag != "body") {
                child.unwrap()
            }
        }
    }

    private fun stripDisallowedAttributes(element: Element) {
        for (child in element.allElements) {
            val tag = child.tagName().lowercase()
            val allowed = ALLOWED_ATTRS[tag] ?: emptySet()

            child.attributes().toList().forEach { attr ->
                if (attr.key.lowercase() !in allowed) {
                    child.removeAttr(attr.key)
                }
            }
        }
    }
}



