package view

/** Restricted, escaped Markdown for release notes; raw HTML is always text. */
fun parseMarkdownToHtml(markdown: String): String {
    val releaseHeader = Regex("^# (v[0-9.]+) - (\\d{4}-\\d{2}-\\d{2})$")
    val lines = markdown.lines()
    val starts = lines.indices.filter { releaseHeader.matches(lines[it].trim()) }
    if (starts.isEmpty()) return renderBlocks(lines)
    return starts.mapIndexed { index, start ->
        val match = releaseHeader.matchEntire(lines[start].trim())!!
        val version = escapeHtml(match.groupValues[1])
        val date = escapeHtml(match.groupValues[2])
        val heading = "<span class='release-version'>$version</span><time datetime='$date'>$date</time>"
        val body = renderBlocks(lines.subList(start + 1, starts.getOrNull(index + 1) ?: lines.size))
        if (index == 0) "<article class='release-card latest-release'><header>$heading<span class='release-badge'>Latest</span></header><div class='release-body'>$body</div></article>"
        else "<details class='release-card'><summary>$heading</summary><div class='release-body'>$body</div></details>"
    }.joinToString("")
}

private data class Bullet(val indent: Int, val text: String)
private fun bullet(line: String): Bullet? {
    val trimmed = line.trimStart()
    if (!trimmed.startsWith("- ")) return null
    val legacy = trimmed.startsWith("- - ")
    return Bullet(line.length - trimmed.length + if (legacy) 2 else 0,
        trimmed.drop(if (legacy) 4 else 2))
}

private fun renderBlocks(lines: List<String>): String {
    var index = 0
    fun list(indent: Int): String = buildString {
        append("<ul>")
        while (index < lines.size) {
            val item = bullet(lines[index]) ?: break
            if (item.indent < indent) break
            if (item.indent > indent) break
            append("<li>").append(formatInlineMarkdown(item.text))
            index++
            while (index < lines.size) {
                val child = bullet(lines[index]) ?: break
                if (child.indent <= indent) break
                append(list(child.indent))
            }
            append("</li>")
        }
        append("</ul>")
    }
    return buildString {
        while (index < lines.size) {
            val line = lines[index]
            val item = bullet(line)
            if (item != null) { append(list(item.indent)); continue }
            index++
            when {
                line.isBlank() -> Unit
                line.startsWith("### ") -> append("<h3>").append(formatInlineMarkdown(line.drop(4))).append("</h3>")
                line.startsWith("## ") -> append("<h2>").append(formatInlineMarkdown(line.drop(3))).append("</h2>")
                line.startsWith("# ") -> append("<h2>").append(formatInlineMarkdown(line.drop(2))).append("</h2>")
                else -> append("<p>").append(formatInlineMarkdown(line)).append("</p>")
            }
        }
    }
}

fun escapeHtml(text: String): String = text.replace("&", "&amp;")
    .replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

private val inlineToken = Regex("`[^`]+`|\\*\\*[^*]+\\*\\*|~~[^~]+~~|\\*[^*]+\\*|\\[[^\\]]+\\]\\(https?://[^\\s)]+\\)|https?://[^\\s<>]+")
fun formatInlineMarkdown(text: String): String = buildString {
    var offset = 0
    for (match in inlineToken.findAll(text)) {
        append(escapeHtml(text.substring(offset, match.range.first)))
        val token = match.value
        when {
            token.startsWith("`") -> append("<code>${escapeHtml(token.drop(1).dropLast(1))}</code>")
            token.startsWith("**") -> append("<strong>${formatInlineMarkdown(token.drop(2).dropLast(2))}</strong>")
            token.startsWith("~~") -> append("<del>${formatInlineMarkdown(token.drop(2).dropLast(2))}</del>")
            token.startsWith("*") -> append("<em>${escapeHtml(token.drop(1).dropLast(1))}</em>")
            token.startsWith("[") -> {
                val split = token.indexOf("](")
                append("<a href='${escapeHtml(token.substring(split + 2).dropLast(1))}' rel='noopener noreferrer'>${escapeHtml(token.substring(1, split))}</a>")
            }
            else -> {
                val url = token.trimEnd('.', ',', '!', '?', ')', ';')
                append("<a href='${escapeHtml(url)}' rel='noopener noreferrer'>${escapeHtml(url)}</a>")
                append(escapeHtml(token.drop(url.length)))
            }
        }
        offset = match.range.last + 1
    }
    append(escapeHtml(text.substring(offset)))
}
