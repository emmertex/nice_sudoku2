import kotlinx.browser.window
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import org.w3c.fetch.Response
import kotlin.js.Promise

/**
 * Utility extension functions for SudokuApp.
 */

internal fun SudokuApp.formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        "${hours}h ${minutes}m ${seconds}s"
    } else if (minutes > 0) {
        "${minutes}m ${seconds}s"
    } else {
        "${seconds}s"
    }
}

internal fun SudokuApp.showToast(message: String) {
    toastMessage = message
    render()
    window.setTimeout({
        toastMessage = null
        render()
    }, 2000)
}

internal const val APP_VERSION = "v1.3.1"

internal fun SudokuApp.loadChangelog() {
    changelogLoading = true
    changelogError = null
    currentVersion = APP_VERSION
    val promise = window.fetch("/CHANGELOG.md?v=$APP_VERSION")
    promise.then { response ->
        if (!response.ok) throw IllegalStateException("Release notes unavailable (${response.status})")
        response.text()
    }.then { text ->
        if (!text.startsWith("# $APP_VERSION - ")) throw IllegalStateException("Release notes do not match this version")
        changelogContent = text
        changelogLoading = false
        if (GameStateManager.getLastSeenVersion() != APP_VERSION) showVersionModal = true
        render()
    }.catch {
        changelogLoading = false
        changelogError = "Could not load release notes. Please try again."
        render()
    }
}

internal fun SudokuApp.renderVersionIndicator() {
    appRoot.append {
        div("version-indicator") {
            +currentVersion
            onClickFunction = {
                showVersionModal = true
                render()
            }
        }
    }
}

