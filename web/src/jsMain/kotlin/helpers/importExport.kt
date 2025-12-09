package helpers.importExport

import kotlinx.browser.window
import kotlinx.html.*

// External declarations for JavaScript global functions
external fun decodeURIComponent(encodedURI: String): String
external fun encodeURIComponent(uriComponent: String): String


fun buildShareUrl(stateString: String): String {
    val baseUrl = "${window.location.origin}${window.location.pathname}${window.location.search}"
    val encodedState = encodeURIComponent(stateString)
    return "$baseUrl#/import/$encodedState"
}



fun handleSharedGameLinkFromUrl(showToast: (String) -> Unit, importFunction: (String, Boolean) -> Boolean): Boolean {
    val hash = window.location.hash ?: ""
    val prefix = "#/import/"
    if (!hash.startsWith(prefix)) return false

    val encodedState = hash.removePrefix(prefix)
    val stateString = try {
        decodeURIComponent(encodedState)
    } catch (e: Exception) {
        null
    }

    if (stateString.isNullOrBlank()) {
        showToast("❌ Invalid shared game link")
        return false
    }

    val success = importFunction(stateString, true)
    
    // Clear the URL hash after successful import to prevent re-importing on refresh
    if (success) {
        window.history.replaceState(null, "", window.location.pathname + window.location.search)
    }
    
    return success
}



