/**
 * Utility functions moved from Main.kt
 */

/**
 * Escape HTML special characters to prevent injection attacks
 */
fun htmlEscape(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#x27;")
}
