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

internal fun SudokuApp.loadChangelog() {
    val fetchPromise = window.asDynamic().fetch("CHANGELOG.md") as Promise<Response>
    fetchPromise.then { response ->
        if (response.ok) {
            response.text().then { text ->
                try {
                    changelogContent = text as String
                    
                    // Extract version from first line (format: "# v0.0.2 - 2025-12-01")
                    val firstLine = changelogContent.lines().firstOrNull() ?: ""
                    val versionMatch = Regex("""#\s*(v[\d.]+)""").find(firstLine)
                    currentVersion = versionMatch?.groupValues?.getOrNull(1) ?: ""
                    
                    // Check if this is a new version
                    val lastSeenVersion = GameStateManager.getLastSeenVersion()
                    if (currentVersion.isNotEmpty() && currentVersion != lastSeenVersion) {
                        // New version detected - show the changelog modal
                        // But not if greeting modal is already showing (first launch)
                        if (!showGreetingModal) {
                            showVersionModal = true
                        }
                        // Always mark as seen so it doesn't show again
                        GameStateManager.setLastSeenVersion(currentVersion)
                        render()
                    } else {
                        // Just re-render to show the version number
                        render()
                    }
                } catch (e: Exception) {
                    // Silently handle parsing errors - changelog is not critical
                    println("Error parsing changelog: ${e.message}")
                    render()
                }
            }.catch { error: dynamic ->
                // Silently handle text parsing errors
                println("Error reading changelog text: $error")
                render()
            }
        } else {
            // Response not OK - silently continue, changelog is not critical
            render()
        }
    }.catch { error: dynamic ->
        // Silently handle fetch errors (network issues, 404, etc.)
        // This prevents unhandled promise rejections on first launch
        println("Error loading changelog: $error")
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

