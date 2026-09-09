package view

/**
 * Simple changelog-specific Markdown parser.
 * Supports: headers, list items with 2-space indented sublists, inline bold/italic/code/strikethrough.
 */
fun parseMarkdownToHtml(markdown: String): String {
    val lines = markdown.lines()
    val html = StringBuilder()
    var i = 0
    var inList = false
    
    while (i < lines.size) {
        val line = lines[i]
        
        // H1 headers (version headers)
        if (line.startsWith("# ") && !line.startsWith("## ")) {
            if (inList) { html.append("</ul>"); inList = false }
            html.append("<h2 class='changelog-version'>${escapeHtml(line.drop(2))}</h2>")
        }
        // H2 headers
        else if (line.startsWith("## ") && !line.startsWith("### ")) {
            if (inList) { html.append("</ul>"); inList = false }
            html.append("<h2>${escapeHtml(line.drop(3))}</h2>")
        }
        // H3 headers (section headers like "Features", "Fixes")
        else if (line.startsWith("### ")) {
            if (inList) { html.append("</ul>"); inList = false }
            html.append("<h3 class='changelog-section'>${escapeHtml(line.drop(4))}</h3>")
        }
        // List items with 2-space indent (sub-items)
        else if (line.startsWith("  - ")) {
            if (!inList) { html.append("<ul>"); inList = true }
            val content = line.drop(4).trim()
            html.append("<li class='changelog-subitem'>${formatInlineMarkdown(content)}</li>")
        }
        // Regular list items
        else if (line.trimStart().startsWith("- ")) {
            if (!inList) { html.append("<ul>"); inList = true }
            val content = line.drop(2).trim()
            html.append("<li>${formatInlineMarkdown(content)}</li>")
        }
        // Empty lines
        else if (line.isBlank()) {
            if (inList) { html.append("</ul>"); inList = false }
        }
        // Regular paragraphs
        else {
            if (inList) { html.append("</ul>"); inList = false }
            html.append("<p>${formatInlineMarkdown(line)}</p>")
        }
        
        i++
    }
    
    // Close any remaining list
    if (inList) { html.append("</ul>") }
    
    return html.toString()
}

fun escapeHtml(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

fun formatInlineMarkdown(text: String): String {
    // Escape HTML first to prevent injection
    val escaped = escapeHtml(text)
    return escaped
        // Code `text` - process first so it's not affected by other formatting
        .replace(Regex("`(.+?)`"), "<code>$1</code>")
        // Bold text **text**
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
        // Italic text *text*
        .replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
        // Strikethrough ~~text~~
        .replace(Regex("~~(.+?)~~"), "<del>$1</del>")
}