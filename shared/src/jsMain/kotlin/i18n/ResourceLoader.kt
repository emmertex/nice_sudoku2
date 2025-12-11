package i18n

import kotlinx.browser.window
import org.w3c.fetch.Response
import kotlin.js.Promise
import org.w3c.xhr.XMLHttpRequest

/**
 * JS platform implementation: Load resources via fetch
 * Note: This uses synchronous XMLHttpRequest which works for bundled resources
 * For production, consider pre-loading language files or using async loading
 */
internal actual object ResourceLoader {
    actual fun loadResource(path: String): String? {
        // Use synchronous XMLHttpRequest for bundled resources
        // This works because resources are bundled with the app
        return try {
            val xhr = XMLHttpRequest()
            xhr.open("GET", path, false) // Synchronous - OK for bundled resources
            xhr.send()
            if (xhr.status == 200.toShort() || xhr.status == 0.toShort()) {
                xhr.responseText
            } else {
                null
            }
        } catch (e: Throwable) {
            null
        }
    }
}

