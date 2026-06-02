
package com.amvarpvtltd.swiftNote.richtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MarkdownToHtmlConverter].
 */
class MarkdownToHtmlConverterTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Detection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `containsMarkdown detects bold`() {
        assertTrue(MarkdownToHtmlConverter.containsMarkdown("This is **bold** text"))
    }

    @Test
    fun `containsMarkdown detects italic`() {
        assertTrue(MarkdownToHtmlConverter.containsMarkdown("This is *italic* text"))
    }

    @Test
    fun `containsMarkdown detects headings`() {
        assertTrue(MarkdownToHtmlConverter.containsMarkdown("# Heading"))
        assertTrue(MarkdownToHtmlConverter.containsMarkdown("## Subheading"))
    }

    @Test
    fun `containsMarkdown detects ordered lists`() {
        assertTrue(MarkdownToHtmlConverter.containsMarkdown("1. First item\n2. Second item"))
    }

    @Test
    fun `containsMarkdown detects unordered lists`() {
        assertTrue(MarkdownToHtmlConverter.containsMarkdown("- Item one\n- Item two"))
        assertTrue(MarkdownToHtmlConverter.containsMarkdown("* Item one\n* Item two"))
    }

    @Test
    fun `containsMarkdown detects inline code`() {
        assertTrue(MarkdownToHtmlConverter.containsMarkdown("Use `println()` to print"))
    }

    @Test
    fun `containsMarkdown detects links`() {
        assertTrue(MarkdownToHtmlConverter.containsMarkdown("[Click here](https://example.com)"))
    }

    @Test
    fun `containsMarkdown returns false for plain text`() {
        assertFalse(MarkdownToHtmlConverter.containsMarkdown("Hello world"))
        assertFalse(MarkdownToHtmlConverter.containsMarkdown("Just some normal text"))
        assertFalse(MarkdownToHtmlConverter.containsMarkdown(""))
    }

    @Test
    fun `containsMarkdown returns false for single asterisk in math`() {
        // "5 * 3 = 15" should NOT be detected as markdown italic
        // This is handled by the regex requiring content between the asterisks
        assertFalse(MarkdownToHtmlConverter.containsMarkdown("5 * 3"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Conversion - Inline formatting
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `converts bold markdown to html`() {
        val result = MarkdownToHtmlConverter.convert("This is **bold** text")
        assertNotNull(result)
        assertTrue(result!!.contains("<b>bold</b>"))
    }

    @Test
    fun `converts underscore bold to html`() {
        val result = MarkdownToHtmlConverter.convert("This is __bold__ text")
        assertNotNull(result)
        assertTrue(result!!.contains("<b>bold</b>"))
    }

    @Test
    fun `converts italic markdown to html`() {
        val result = MarkdownToHtmlConverter.convert("This is *italic* text")
        assertNotNull(result)
        assertTrue(result!!.contains("<i>italic</i>"))
    }

    @Test
    fun `converts strikethrough to html`() {
        val result = MarkdownToHtmlConverter.convert("This is ~~deleted~~ text")
        assertNotNull(result)
        assertTrue(result!!.contains("<s>deleted</s>"))
    }

    @Test
    fun `converts inline code to html`() {
        val result = MarkdownToHtmlConverter.convert("Use `println()` function")
        assertNotNull(result)
        assertTrue(result!!.contains("<code>println()</code>"))
    }

    @Test
    fun `converts links to html`() {
        val result = MarkdownToHtmlConverter.convert("[Click here](https://example.com)")
        assertNotNull(result)
        assertTrue(result!!.contains("<a href=\"https://example.com\">Click here</a>"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Conversion - Block elements
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `converts h1 heading`() {
        val result = MarkdownToHtmlConverter.convert("# Main Title")
        assertNotNull(result)
        assertTrue(result!!.contains("<h1>Main Title</h1>"))
    }

    @Test
    fun `converts h2 heading`() {
        val result = MarkdownToHtmlConverter.convert("## Sub Title")
        assertNotNull(result)
        assertTrue(result!!.contains("<h2>Sub Title</h2>"))
    }

    @Test
    fun `converts ordered list`() {
        val input = "1. First\n2. Second\n3. Third"
        val result = MarkdownToHtmlConverter.convert(input)
        assertNotNull(result)
        assertTrue(result!!.contains("<ol>"))
        assertTrue(result.contains("<li>First</li>"))
        assertTrue(result.contains("<li>Second</li>"))
        assertTrue(result.contains("<li>Third</li>"))
        assertTrue(result.contains("</ol>"))
    }

    @Test
    fun `converts unordered list with dashes`() {
        val input = "- Apple\n- Banana\n- Cherry"
        val result = MarkdownToHtmlConverter.convert(input)
        assertNotNull(result)
        assertTrue(result!!.contains("<ul>"))
        assertTrue(result.contains("<li>Apple</li>"))
        assertTrue(result.contains("<li>Banana</li>"))
        assertTrue(result.contains("<li>Cherry</li>"))
        assertTrue(result.contains("</ul>"))
    }

    @Test
    fun `converts unordered list with asterisks`() {
        val input = "* Item A\n* Item B"
        val result = MarkdownToHtmlConverter.convert(input)
        assertNotNull(result)
        assertTrue(result!!.contains("<ul>"))
        assertTrue(result.contains("<li>Item A</li>"))
        assertTrue(result.contains("<li>Item B</li>"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Conversion - Mixed content
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `converts mixed heading and list`() {
        val input = "# Shopping List\n\n1. Milk\n2. Eggs\n3. Bread"
        val result = MarkdownToHtmlConverter.convert(input)
        assertNotNull(result)
        assertTrue(result!!.contains("<h1>Shopping List</h1>"))
        assertTrue(result.contains("<ol>"))
        assertTrue(result.contains("<li>Milk</li>"))
    }

    @Test
    fun `converts bold text within list items`() {
        val input = "- **Important** task\n- Regular task"
        val result = MarkdownToHtmlConverter.convert(input)
        assertNotNull(result)
        assertTrue(result!!.contains("<li><b>Important</b> task</li>"))
    }

    @Test
    fun `handles the user screenshot scenario`() {
        // This is the exact format shown in the user's screenshot
        val input = """**Five features in scope:**
1. **Today / Daily Review screen** — new home tab
2. **Rich text formatting** — bold, italic, underline, H1, H2, normal + lists, checkboxes, code blocks, links
3. **Paste-with-formatting** — smart paste that preserves HTML/Markdown structure
4. **Auto-titles** — auto-suggest title when user leaves it blank
5. **Enhanced Share-to-SwiftNote** — already exists, needs polish + auto-title integration

**Non-negotiable constraint:** zero regression in working features."""
        val result = MarkdownToHtmlConverter.convert(input)
        assertNotNull(result)
        assertTrue(result!!.contains("<b>Five features in scope:</b>"))
        assertTrue(result.contains("<ol>"))
        assertTrue(result.contains("<b>Today / Daily Review screen</b>"))
        assertTrue(result.contains("<b>Non-negotiable constraint:</b>"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Edge cases
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `handles single asterisk bold-italic with bullets`() {
        // Pattern from user's actual screenshot - single asterisks used for emphasis
        val input = """*1. Where are you starting from?*
Tell me quick so I can tailor this:
- *Fresh grad or <1 yr experience?*
- *Switching roles/companies?*
- *Returning after a break?*

*2. The 4-part job hunt playbook*"""
        val result = MarkdownToHtmlConverter.convert(input)
        assertNotNull(result)
        // Single asterisks should become italic
        assertTrue(result!!.contains("<i>"))
        // Bullet items should be in a list
        assertTrue(result.contains("<ul>"))
        assertTrue(result.contains("<li>"))
    }

    @Test
    fun `returns null for blank input`() {
        assertNull(MarkdownToHtmlConverter.convert(""))
        assertNull(MarkdownToHtmlConverter.convert("   "))
    }

    @Test
    fun `returns null for plain text without markdown`() {
        assertNull(MarkdownToHtmlConverter.convert("Hello world, nothing special here"))
    }

    @Test
    fun `handles multiple bold in same line`() {
        val result = MarkdownToHtmlConverter.convert("Both **first** and **second** are bold")
        assertNotNull(result)
        assertTrue(result!!.contains("<b>first</b>"))
        assertTrue(result.contains("<b>second</b>"))
    }

    @Test
    fun `handles bold and italic in same line`() {
        val result = MarkdownToHtmlConverter.convert("**Bold** and *italic* together")
        assertNotNull(result)
        assertTrue(result!!.contains("<b>Bold</b>"))
        assertTrue(result.contains("<i>italic</i>"))
    }

    @Test
    fun `closes list before heading`() {
        val input = "- Item 1\n- Item 2\n# New Section"
        val result = MarkdownToHtmlConverter.convert(input)
        assertNotNull(result)
        // The ul should be closed before the heading
        val ulCloseIdx = result!!.indexOf("</ul>")
        val h1Idx = result.indexOf("<h1>")
        assertTrue(ulCloseIdx < h1Idx)
    }
}


