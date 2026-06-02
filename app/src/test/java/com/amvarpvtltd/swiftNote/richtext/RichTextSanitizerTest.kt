package com.amvarpvtltd.swiftNote.richtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RichTextSanitizer].
 *
 * Tests cover:
 * - Tag normalization (strong→b, em→i, h3–h6→h2)
 * - Stripping disallowed tags while preserving content
 * - Attribute stripping (only href on <a>, type/checked on <input>)
 * - Span-to-semantic conversion (inline styles → proper tags)
 * - Edge cases (empty input, plain text, deeply nested)
 * - Real-world paste sources (Wikipedia, Gmail, Google Docs)
 */
class RichTextSanitizerTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Basic tag preservation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `preserves bold italic underline tags`() {
        val input = "<b>bold</b> <i>italic</i> <u>underline</u>"
        val result = RichTextSanitizer.sanitize(input)
        assertTrue(result.contains("<b>bold</b>"))
        assertTrue(result.contains("<i>italic</i>"))
        assertTrue(result.contains("<u>underline</u>"))
    }

    @Test
    fun `preserves heading tags h1 and h2`() {
        val input = "<h1>Title</h1><h2>Subtitle</h2>"
        val result = RichTextSanitizer.sanitize(input)
        assertTrue(result.contains("<h1>Title</h1>"))
        assertTrue(result.contains("<h2>Subtitle</h2>"))
    }

    @Test
    fun `preserves lists`() {
        val input = "<ul><li>item 1</li><li>item 2</li></ul>"
        val result = RichTextSanitizer.sanitize(input)
        assertTrue(result.contains("<ul>"))
        assertTrue(result.contains("<li>item 1</li>"))
        assertTrue(result.contains("<li>item 2</li>"))
        assertTrue(result.contains("</ul>"))
    }

    @Test
    fun `preserves ordered lists`() {
        val input = "<ol><li>first</li><li>second</li></ol>"
        val result = RichTextSanitizer.sanitize(input)
        assertTrue(result.contains("<ol>"))
        assertTrue(result.contains("<li>first</li>"))
        assertTrue(result.contains("</ol>"))
    }

    @Test
    fun `preserves links with href only`() {
        val input = """<a href="https://example.com" class="link" target="_blank">Example</a>"""
        val result = RichTextSanitizer.sanitize(input)
        assertTrue(result.contains("""<a href="https://example.com">Example</a>"""))
        assertFalse(result.contains("class"))
        assertFalse(result.contains("target"))
    }

    @Test
    fun `preserves code and pre tags`() {
        val input = "<p>Use <code>println()</code> to print.</p><pre>val x = 1</pre>"
        val result = RichTextSanitizer.sanitize(input)
        assertTrue(result.contains("<code>println()</code>"))
        assertTrue(result.contains("<pre>val x = 1</pre>"))
    }

    @Test
    fun `preserves br and p tags`() {
        val input = "<p>Line one</p><p>Line two<br>continued</p>"
        val result = RichTextSanitizer.sanitize(input)
        assertTrue(result.contains("<p>Line one</p>"))
        assertTrue(result.contains("<br>"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tag normalization
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `converts strong to b`() {
        val input = "<strong>important</strong>"
        val result = RichTextSanitizer.sanitize(input)
        assertTrue(result.contains("<b>important</b>"))
        assertFalse(result.contains("<strong>"))
    }

    @Test
    fun `converts em to i`() {
        val input = "<em>emphasis</em>"
        val result = RichTextSanitizer.sanitize(input)
        assertTrue(result.contains("<i>emphasis</i>"))
        assertFalse(result.contains("<em>"))
    }

    @Test
    fun `downgrades h3 through h6 to h2`() {
        val input = "<h3>H3</h3><h4>H4</h4><h5>H5</h5><h6>H6</h6>"
        val result = RichTextSanitizer.sanitize(input)
        assertTrue(result.contains("<h2>H3</h2>"))
        assertTrue(result.contains("<h2>H4</h2>"))
        assertTrue(result.contains("<h2>H5</h2>"))
        assertTrue(result.contains("<h2>H6</h2>"))
        assertFalse(result.contains("<h3>"))
        assertFalse(result.contains("<h4>"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tag stripping (unwrap disallowed, preserve content)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `strips script tags completely`() {
        val input = "<p>Hello</p><script>alert('xss')</script><p>World</p>"
        val result = RichTextSanitizer.sanitize(input)
        assertFalse(result.contains("<script>"))
        assertFalse(result.contains("alert"))
        assertTrue(result.contains("Hello"))
        assertTrue(result.contains("World"))
    }

    @Test
    fun `strips style tags`() {
        val input = "<style>.foo{color:red}</style><p>Content</p>"
        val result = RichTextSanitizer.sanitize(input)
        assertFalse(result.contains("<style>"))
        assertFalse(result.contains(".foo"))
        assertTrue(result.contains("Content"))
    }

    @Test
    fun `strips table structure but preserves text`() {
        val input = "<table><tr><td>Cell 1</td><td>Cell 2</td></tr></table>"
        val result = RichTextSanitizer.sanitize(input)
        assertFalse(result.contains("<table>"))
        assertFalse(result.contains("<tr>"))
        assertFalse(result.contains("<td>"))
        assertTrue(result.contains("Cell 1"))
        assertTrue(result.contains("Cell 2"))
    }

    @Test
    fun `strips img tags`() {
        val input = """<p>Text <img src="pic.jpg" alt="photo"> more text</p>"""
        val result = RichTextSanitizer.sanitize(input)
        assertFalse(result.contains("<img"))
        assertTrue(result.contains("Text"))
        assertTrue(result.contains("more text"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Span-to-semantic conversion
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `converts span with bold style to b tag`() {
        val input = """<span style="font-weight: bold">Bold text</span>"""
        val result = RichTextSanitizer.sanitize(input)
        assertTrue(result.contains("<b>Bold text</b>"))
    }

    @Test
    fun `converts span with font-weight 700 to b tag`() {
        val input = """<span style="font-weight: 700">Bold text</span>"""
        val result = RichTextSanitizer.sanitize(input)
        assertTrue(result.contains("<b>Bold text</b>"))
    }

    @Test
    fun `converts span with italic style to i tag`() {
        val input = """<span style="font-style: italic">Italic text</span>"""
        val result = RichTextSanitizer.sanitize(input)
        assertTrue(result.contains("<i>Italic text</i>"))
    }

    @Test
    fun `converts span with underline style to u tag`() {
        val input = """<span style="text-decoration: underline">Underlined</span>"""
        val result = RichTextSanitizer.sanitize(input)
        assertTrue(result.contains("<u>Underlined</u>"))
    }

    @Test
    fun `unwraps span with unknown styles`() {
        val input = """<span style="color: red; font-size: 14px">Colored text</span>"""
        val result = RichTextSanitizer.sanitize(input)
        assertFalse(result.contains("<span"))
        assertTrue(result.contains("Colored text"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Attribute stripping
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `strips class and id attributes from allowed tags`() {
        val input = """<p class="intro" id="p1">Text</p>"""
        val result = RichTextSanitizer.sanitize(input)
        assertTrue(result.contains("<p>Text</p>"))
        assertFalse(result.contains("class"))
        assertFalse(result.contains("id"))
    }

    @Test
    fun `strips style attributes from all tags`() {
        val input = """<b style="color: blue">Bold</b>"""
        val result = RichTextSanitizer.sanitize(input)
        assertTrue(result.contains("<b>Bold</b>"))
        assertFalse(result.contains("style"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Div-to-p conversion
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `converts div to p`() {
        val input = "<div>Block content</div>"
        val result = RichTextSanitizer.sanitize(input)
        assertTrue(result.contains("<p>Block content</p>"))
        assertFalse(result.contains("<div>"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Edge cases
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `handles empty string`() {
        assertEquals("", RichTextSanitizer.sanitize(""))
    }

    @Test
    fun `handles blank string`() {
        assertEquals("", RichTextSanitizer.sanitize("   "))
    }

    @Test
    fun `handles plain text without tags`() {
        val input = "Just plain text with no HTML"
        val result = RichTextSanitizer.sanitize(input)
        assertTrue(result.contains("Just plain text with no HTML"))
    }

    @Test
    fun `handles deeply nested disallowed tags`() {
        val input = "<div><section><article><b>Nested bold</b></article></section></div>"
        val result = RichTextSanitizer.sanitize(input)
        assertTrue(result.contains("<b>Nested bold</b>"))
        assertFalse(result.contains("<section>"))
        assertFalse(result.contains("<article>"))
    }

    @Test
    fun `removes empty paragraphs`() {
        val input = "<p></p><p>  </p><p>Content</p>"
        val result = RichTextSanitizer.sanitize(input)
        assertTrue(result.contains("<p>Content</p>"))
        // Empty paragraphs should be removed
        assertFalse(result.contains("<p></p>"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Real-world paste sources
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `wikipedia paragraph with links and bold`() {
        val input = """
            <p><b>Kotlin</b> is a <a href="https://en.wikipedia.org/wiki/Cross-platform" class="mw-redirect" title="Cross-platform">cross-platform</a>, 
            <a href="https://en.wikipedia.org/wiki/Static_typing" title="Static typing">statically typed</a> language.</p>
        """.trimIndent()
        val result = RichTextSanitizer.sanitize(input)
        assertTrue(result.contains("<b>Kotlin</b>"))
        assertTrue(result.contains("""<a href="https://en.wikipedia.org/wiki/Cross-platform">cross-platform</a>"""))
        assertFalse(result.contains("class="))
        assertFalse(result.contains("title="))
    }

    @Test
    fun `google docs heading paste`() {
        val input = """<h1 style="font-size:26px;color:#000">My Document Title</h1>"""
        val result = RichTextSanitizer.sanitize(input)
        assertTrue(result.contains("<h1>My Document Title</h1>"))
        assertFalse(result.contains("style"))
    }

    @Test
    fun `gmail signature with font spans`() {
        val input = """
            <div dir="ltr">
                <span style="font-size:12.8px">Best regards,</span><br>
                <span style="font-size:12.8px"><b>John Doe</b></span><br>
                <span style="font-size:12.8px;color:rgb(102,102,102)">Software Engineer</span>
            </div>
        """.trimIndent()
        val result = RichTextSanitizer.sanitize(input)
        assertTrue(result.contains("Best regards,"))
        assertTrue(result.contains("<b>John Doe</b>"))
        assertTrue(result.contains("Software Engineer"))
        assertFalse(result.contains("<span"))
        assertFalse(result.contains("<div"))
    }

    @Test
    fun `vscode code copy preserves as plain text`() {
        // VSCode typically wraps code in lots of spans with color classes
        val input = """
            <div style="color: #d4d4d4;background-color: #1e1e1e;">
                <span style="color: #569cd6;">val</span> <span style="color: #9cdcfe;">x</span> = <span style="color: #b5cea8;">42</span>
            </div>
        """.trimIndent()
        val result = RichTextSanitizer.sanitize(input)
        // All spans and divs should be unwrapped, leaving just the text
        assertTrue(result.contains("val"))
        assertTrue(result.contains("x"))
        assertTrue(result.contains("42"))
        assertFalse(result.contains("<span"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // containsHtml detection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `containsHtml returns true for html content`() {
        assertTrue(RichTextSanitizer.containsHtml("<b>bold</b>"))
        assertTrue(RichTextSanitizer.containsHtml("<p>paragraph</p>"))
        assertTrue(RichTextSanitizer.containsHtml("<a href=\"x\">link</a>"))
        assertTrue(RichTextSanitizer.containsHtml("<div>block</div>"))
    }

    @Test
    fun `containsHtml returns false for plain text`() {
        assertFalse(RichTextSanitizer.containsHtml("Hello world"))
        assertFalse(RichTextSanitizer.containsHtml("5 < 10 and 10 > 5"))
        assertFalse(RichTextSanitizer.containsHtml(""))
        assertFalse(RichTextSanitizer.containsHtml("   "))
    }

    @Test
    fun `containsHtml returns false for non-html angle brackets`() {
        assertFalse(RichTextSanitizer.containsHtml("x < y"))
        assertFalse(RichTextSanitizer.containsHtml("<3"))
        assertFalse(RichTextSanitizer.containsHtml("C++ templates use <>"))
    }
}

