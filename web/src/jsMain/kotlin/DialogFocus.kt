import kotlinx.browser.document
import org.w3c.dom.HTMLElement

private const val focusableSelector = "button:not([disabled]), a[href], input:not([disabled]), textarea:not([disabled]), select:not([disabled]), summary, [tabindex='0']"
private var returnFocusIndex: Int? = null
internal data class DialogFocusState(val pageIndex: Int, val dialogIndex: Int, val hadDialog: Boolean)

internal fun SudokuApp.hasOpenModal(): Boolean = showAboutModal || showHelpModal ||
    showButtonHelpModal || showCompletionModal || showVersionModal || showPuzzleInfoModal || showExplanation

private fun focusable(root: org.w3c.dom.Element): List<HTMLElement> {
    val nodes = root.querySelectorAll(focusableSelector)
    return (0 until nodes.length).mapNotNull { nodes.item(it) as? HTMLElement }
        .filter { it.asDynamic().getClientRects().length > 0 }
}

internal fun SudokuApp.captureDialogFocus(): DialogFocusState {
    val dialog = appRoot.querySelector("[role='dialog']")
    return DialogFocusState(focusable(appRoot).indexOf(document.activeElement),
        dialog?.let { focusable(it).indexOf(document.activeElement) } ?: -1, dialog != null)
}

internal fun SudokuApp.restoreDialogFocus(state: DialogFocusState) {
    val dialogs = appRoot.querySelectorAll(".modal-content")
    val dialog = dialogs.item(dialogs.length - 1) as? HTMLElement
    if (dialog != null) {
        dialog.setAttribute("role", "dialog")
        dialog.setAttribute("aria-modal", "true")
        dialog.setAttribute("aria-label", dialog.querySelector("h1, h2")?.textContent ?: "Dialog")
        dialog.tabIndex = -1
        if (!state.hadDialog) returnFocusIndex = state.pageIndex
        val targets = focusable(dialog)
        (targets.getOrNull(if (state.hadDialog) state.dialogIndex else 0) ?: targets.firstOrNull() ?: dialog).focus()
    } else if (state.hadDialog) {
        (focusable(appRoot).getOrNull(returnFocusIndex ?: -1) ?: focusable(appRoot).firstOrNull())?.focus()
        returnFocusIndex = null
    }
}

internal fun SudokuApp.closeVersionModal() {
    if (changelogContent.isNotBlank() && currentVersion.isNotBlank()) GameStateManager.setLastSeenVersion(currentVersion)
    showVersionModal = false
    render()
}

internal fun SudokuApp.handleDialogKey(key: String, shift: Boolean): Boolean {
    if (!hasOpenModal()) return false
    if (key == "Escape") {
        when {
            showPuzzleInfoModal -> showPuzzleInfoModal = false
            showVersionModal -> { closeVersionModal(); return true }
            showCompletionModal -> showCompletionModal = false
            showButtonHelpModal -> showButtonHelpModal = false
            showHelpModal -> showHelpModal = false
            showAboutModal -> showAboutModal = false
            showExplanation -> showExplanation = false
        }
        render()
        return true
    }
    if (key == "Tab") {
        val dialogs = appRoot.querySelectorAll("[role='dialog']")
        val dialog = dialogs.item(dialogs.length - 1) as? HTMLElement ?: return false
        val targets = focusable(dialog)
        if (targets.isEmpty()) { dialog.focus(); return true }
        val index = targets.indexOf(document.activeElement)
        if (index < 0 || (shift && index == 0) || (!shift && index == targets.lastIndex)) {
            (if (shift) targets.last() else targets.first()).focus()
            return true
        }
    }
    return false
}
