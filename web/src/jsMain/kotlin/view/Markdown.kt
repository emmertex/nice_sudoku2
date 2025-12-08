package view

fun parseMarkdownToHtml(markdown: String): String {
    return markdown.lines().joinToString("\n") { line ->
        when {
            // H1 headers
            line.startsWith("# ") -> "<h2 class='changelog-version'>${line.drop(2)}</h2>"
            // H3 headers (### Features, ### Fixes, etc.)
            line.startsWith("### ") -> "<h3 class='changelog-section'>${line.drop(4)}</h3>"
            // List items with double dash (sub-items)
            line.trimStart().startsWith("- - ") -> {
                val content = line.trimStart().drop(4)
                "<li class='changelog-subitem'>${formatInlineMarkdown(content)}</li>"
            }
            // Regular list items
            line.trimStart().startsWith("- ") -> {
                val content = line.trimStart().drop(2)
                "<li>${formatInlineMarkdown(content)}</li>"
            }
            // Empty lines
            line.isBlank() -> ""
            // Regular text
            else -> "<p>${formatInlineMarkdown(line)}</p>"
        }
    }.replace(Regex("<li>"), "<ul><li>")
        .replace(Regex("</li>(?!\\s*<li)"), "</li></ul>")
        .replace(Regex("</ul>\\s*<ul>"), "") // Clean up consecutive ul tags
}

fun formatInlineMarkdown(text: String): String {
    return text
        // Strikethrough ~~text~~
        .replace(Regex("~~(.+?)~~"), "<del>$1</del>")
        // Bold text **text**
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
        // Italic text *text*
        .replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
        // Code `text`
        .replace(Regex("`(.+?)`"), "<code>$1</code>")
}

