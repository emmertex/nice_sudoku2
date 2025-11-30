import kotlinx.browser.window
import org.w3c.dom.Navigator
import kotlin.js.Date
import kotlin.js.Promise

/**
 * Get current time in milliseconds (JS implementation)
 */
fun currentTimeMillis(): Long = Date.now().toLong()

/**
 * Clipboard utilities for import/export
 */
object ClipboardUtils {
    
    /**
     * Copy text to clipboard
     */
    fun copyToClipboard(text: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        try {
            val clipboard = window.navigator.asDynamic().clipboard
            if (clipboard != null) {
                val promise = clipboard.writeText(text) as Promise<Unit>
                promise.then { 
                    onSuccess()
                }.catch { error ->
                    onError(error.toString())
                }
            } else {
                // Fallback for older browsers
                fallbackCopyToClipboard(text, onSuccess, onError)
            }
        } catch (e: Exception) {
            onError(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Read text from clipboard
     */
    fun readFromClipboard(onSuccess: (String) -> Unit, onError: (String) -> Unit = {}) {
        try {
            val clipboard = window.navigator.asDynamic().clipboard
            if (clipboard != null) {
                val promise = clipboard.readText() as Promise<String>
                promise.then { text ->
                    onSuccess(text)
                }.catch { error ->
                    onError(error.toString())
                }
            } else {
                onError("Clipboard API not supported")
            }
        } catch (e: Exception) {
            onError(e.message ?: "Unknown error")
        }
    }
    
    private fun fallbackCopyToClipboard(text: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        try {
            val textArea = kotlinx.browser.document.createElement("textarea") as org.w3c.dom.HTMLTextAreaElement
            textArea.value = text
            textArea.style.position = "fixed"
            textArea.style.left = "-9999px"
            kotlinx.browser.document.body?.appendChild(textArea)
            textArea.select()
            val success = kotlinx.browser.document.execCommand("copy")
            kotlinx.browser.document.body?.removeChild(textArea)
            if (success) {
                onSuccess()
            } else {
                onError("Copy command failed")
            }
        } catch (e: Exception) {
            onError(e.message ?: "Unknown error")
        }
    }
}

/**
 * Parse puzzle string (81 or 810 chars)
 */
object PuzzleStringParser {
    
    /**
     * Parse an 81-char puzzle string into values
     * Returns null if invalid
     */
    fun parsePuzzleString(input: String): IntArray? {
        val cleaned = input.trim().replace("\\s+".toRegex(), "")
        
        if (cleaned.length < 81) return null
        
        val values = IntArray(81)
        for (i in 0 until 81) {
            val c = cleaned[i]
            values[i] = when {
                c == '.' || c == '0' -> 0
                c in '1'..'9' -> c.digitToInt()
                else -> return null
            }
        }
        return values
    }
    
    /**
     * Parse an 810-char state string into values and notes
     * Returns null if invalid
     */
    fun parseStateString(input: String): Pair<IntArray, Array<Set<Int>>>? {
        val cleaned = input.trim().replace("\\s+".toRegex(), "")
        
        if (cleaned.length < 81) return null
        
        val values = IntArray(81)
        val notes = Array<Set<Int>>(81) { emptySet() }
        
        // Parse values
        for (i in 0 until 81) {
            val c = cleaned[i]
            values[i] = when {
                c == '.' || c == '0' -> 0
                c in '1'..'9' -> c.digitToInt()
                else -> return null
            }
        }
        
        // Parse notes if present (729 more chars)
        if (cleaned.length >= 810) {
            for (cell in 0 until 81) {
                val noteSet = mutableSetOf<Int>()
                for (n in 0 until 9) {
                    val idx = 81 + cell * 9 + n
                    if (cleaned[idx] == '1') {
                        noteSet.add(n + 1)
                    }
                }
                notes[cell] = noteSet
            }
        }
        
        return Pair(values, notes)
    }
    
    /**
     * Create an 81-char puzzle string from values
     */
    fun createPuzzleString(values: IntArray): String {
        return values.joinToString("") { if (it == 0) "." else it.toString() }
    }
    
    /**
     * Create an 810-char state string from values and notes
     */
    fun createStateString(values: IntArray, notes: Array<Set<Int>>): String {
        val sb = StringBuilder(810)
        
        // First 81 chars: values
        for (v in values) {
            sb.append(if (v == 0) '0' else v.digitToChar())
        }
        
        // Next 729 chars: notes (9 per cell)
        for (cell in 0 until 81) {
            for (n in 1..9) {
                sb.append(if (n in notes[cell]) '1' else '0')
            }
        }
        
        return sb.toString()
    }
    
    /**
     * Validate a puzzle string format (81 or 810 chars)
     */
    fun isValidPuzzleString(input: String): Boolean {
        val cleaned = input.trim().replace("\\s+".toRegex(), "")
        
        if (cleaned.length != 81 && cleaned.length != 810) return false
        
        // Check first 81 chars are valid digits or .
        for (i in 0 until 81) {
            val c = cleaned[i]
            if (c !in '0'..'9' && c != '.') return false
        }
        
        // Check remaining 729 chars are 0 or 1 (if present)
        if (cleaned.length == 810) {
            for (i in 81 until 810) {
                if (cleaned[i] != '0' && cleaned[i] != '1') return false
            }
        }
        
        return true
    }
}

