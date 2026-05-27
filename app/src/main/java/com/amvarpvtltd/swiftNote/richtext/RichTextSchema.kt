package com.amvarpvtltd.swiftNote.richtext

/**
 * Defines the canonical set of HTML tags and attributes that SwiftNote supports
 * as its rich-text storage format.
 *
 * Used by [RichTextRenderer] to guide parsing and by the Phase 3 [RichTextSanitizer]
 * to strip disallowed markup from pasted content.
 */
object RichTextSchema {

    /**
     * Tags that may appear in stored note content.
     * Everything else is stripped on sanitize.
     */
    val ALLOWED_TAGS = setOf(
        "b", "strong",          // bold
        "i", "em",              // italic
        "u",                    // underline
        "s", "strike",          // strikethrough
        "h1", "h2",             // headings
        "p", "br",              // paragraphs / line-breaks
        "ul", "ol", "li",       // lists
        "a",                    // hyperlinks
        "code", "pre",          // inline code / code blocks
        "input"                 // checkboxes  (<input type="checkbox">)
    )

    /**
     * Per-tag whitelist of allowed HTML attributes.
     * Any attribute not listed here is stripped on sanitize.
     */
    val ALLOWED_ATTRS: Map<String, Set<String>> = mapOf(
        "a"     to setOf("href"),
        "input" to setOf("type", "checked")
    )
}

