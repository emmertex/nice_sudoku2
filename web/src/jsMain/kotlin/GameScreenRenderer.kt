import adapter.CandidateLocationDto
import adapter.ExplanationStepDto
import adapter.GroupDto
import adapter.LineDto
import adapter.TechniqueMatchInfo
import domain.*
import domain.getLocalizedDisplayName
import i18n.LanguageConfig
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import view.*
import kotlin.js.Date
import kotlin.math.abs

internal fun SudokuApp.renderGameScreen() {
    val grid = gameEngine.getCurrentGrid()
    val game = currentGame
    val isSolved = grid.isComplete && grid.isValid

    // Show completion modal when puzzle is solved and we haven't shown it for this puzzle yet
    if (isSolved && game != null && completionShownForPuzzle != game.puzzleId) {
        // Save the state if not already marked complete
        if (!game.isCompleted) {
            saveCurrentState()
        }
        completionShownForPuzzle = game.puzzleId
        showCompletionModal = true
    }

    val currentElapsed = if (trackPlayTime) {
        if (isPaused) pausedTime + (pauseStartTime - gameStartTime)
        else pausedTime + (currentTimeMillis() - gameStartTime)
    } else 0L

    val puzzleTitleTrimmed = game?.title?.trim().orEmpty()
    document.title = when {
        game == null -> "Nice Sudoku"
        puzzleTitleTrimmed.isEmpty() -> "Nice Sudoku"
        else -> "Nice Sudoku - $puzzleTitleTrimmed"
    }

    // Compute highlights
    val primaryCells = getPrimaryHighlightCells(grid)
    val secondaryCells = emptySet<Int>()

    appRoot.append {
        div("sudoku-container-wrapper") {
            val containerClass = if (showHints && isLandscape) "sudoku-container hints-expanded" else "sudoku-container"
            div(containerClass) {
            // Header with nav
            div("header") {
                div("nav-row") {
                    // Single menu button that opens settings
                    button(classes = "nav-btn menu-btn") {
                        +"☰ Menu"
                        onClickFunction = {
                            prepareLeaveGameScreen()
                            currentScreen = AppScreen.SETTINGS
                            render()
                        }
                    }
                    div("header-brand") {
                        h1 {
                            +LanguageConfig.getString("ui.gameScreen.title")
                        }
                        if (game != null && puzzleTitleTrimmed.isNotEmpty()) {
                            span("game-header-puzzle-title") {
                                +puzzleTitleTrimmed
                                onClickFunction = {
                                    puzzleInfoTarget = game
                                    showPuzzleInfoModal = true
                                    render()
                                }
                            }
                        }
                        div("powered-by") {
                            a(
                                href = "https://github.com/strmckr",
                                target = "_blank",
                                classes = "powered-by-link"
                            ) {
                                attributes["rel"] = "noopener noreferrer"
                                +LanguageConfig.getString("ui.gameScreen.poweredBy")
                            }
                        }
                    }
                    // Show current mode indicators and selected numbers
                    div("mode-indicators") {
                        // Help button - opens button help modal
                        button(classes = "help-btn") {
                            +"?"
                            onClickFunction = {
                                showButtonHelpModal = true
                                render()
                            }
                        }
                        // Selection info inline with mode badges
                        if (selectedNumbers1.isNotEmpty()) {
                            span("selected-num primary") { +selectedNumbers1.sorted().joinToString(",") }
                        }
                    }
                }
                if (game != null) {
                    div("game-info") {
                        span("category ${game.category.name.lowercase()}") {
                            +game.category.getLocalizedDisplayName()
                        }
                        span("difficulty") { +"★ ${game.difficulty}" }
                        span("timer-container") {
                            span("timer") { +"⏱ ${formatTime(currentElapsed)}" }
                            if (trackPlayTime) {
                                button(classes = "pause-btn") {
                                    +(if (isPaused) "▶" else "⏸")
                                    onClickFunction = {
                                        togglePause()
                                    }
                                }
                            }
                        }
                        span("mistakes") { +"❌ ${game.mistakeCount}" }
                        span("hints") { +"💡 ${game.hintCount}" }
                    }
                }
            }

            // Main content wrapper - flex row in landscape, column in portrait
            val hints = if (showHints) gameEngine.getHints() else emptyList()
            currentHintList = hints  // Cache for event delegation
            val selectedHint = hints.getOrNull(selectedHintIndex)

            // Get current explanation step if showing explanation OR if in portrait mode with hints
            // In portrait mode, hints always show explanations inline
            val currentExplanationStep = if (selectedHint != null && (showExplanation || (showHints && !isLandscape))) {
                selectedHint.explanationSteps.getOrNull(explanationStepIndex)
            } else null

            div("main-content ${if (showHints && isLandscape) "landscape-hints" else ""}") {
            // Game area
            div("game-area") {
                // Sudoku grid container with SVG overlay
                div("sudoku-grid-container${if (isPaused && trackPlayTime) " paused" else ""}${if (isNotesMode) " notes-mode" else ""}") {
                    // Sudoku grid
        div("sudoku-grid") {
                        for (row in 0..8) {
                            div("sudoku-row") {
                                for (col in 0..8) {
                                    val cellIndex = row * 9 + col
                                    val cell = grid.getCell(cellIndex)
                                    val isPrimary = cellIndex in primaryCells
                                    val isSecondary = cellIndex in secondaryCells
                        renderCell(this@renderGameScreen, cellIndex, cell, isPrimary, isSecondary, grid, selectedHint, currentExplanationStep)
                                }
                            }
                        }
                    }

                    // SVG overlay for chain lines (when hint with lines is selected)
                    if (selectedHint != null && selectedHint.lines.isNotEmpty()) {
                        renderChainLinesSvg(this@renderGameScreen, selectedHint, currentExplanationStep)
                    }

                    // Pause overlay (blur effect when paused)
                    if (isPaused && trackPlayTime) {
                        div("pause-overlay") {
                            onClickFunction = {
                                resumeGame()
                            }
                            div("pause-message") {
                                div("pause-icon") { +"⏸" }
                                div("pause-text") { +"PAUSED" }
                                div("pause-subtext") { +"Click to resume" }
                                div("pause-notification") {
                                    +LanguageConfig.getString("ui.gameScreen.pauseNotification")
                                }
                            }
                        }
                    }
                }

                // Controls row - Notes, Multi-select Notes, Undo, Hint, and actions
                div("controls") {
                    // Notes toggle (pencil marks) - only relevant in Place mode
                        button(classes = "toggle-btn ${if (isNotesMode) "active" else ""}") {
                            +"✏️"
                            attributes["title"] = "Toggle pencil mark mode"
                            onClickFunction = {
                                if (isNotesMode) {
                                    isNotesMode = false
                                } else {
                                    isNotesMode = true
                                    if (playMode == PlayMode.MULTI_SELECT_NOTES) {
                                        playMode = PlayMode.PLACE
                                        GameStateManager.setPlayMode(playMode)
                                        selectedNumbers1.clear()
                                        selectedCell = null
                                    }
                                }
                                render()
                            }
                        }


                    // Multi-select notes mode toggle button (2 pencil marks)
                    button(classes = "toggle-btn multi-notes-btn ${if (playMode == PlayMode.MULTI_SELECT_NOTES) "active" else ""}") {
                        +"✏️✏️"
                        attributes["title"] = "Multi-select notes mode"
                        onClickFunction = {
                            playMode = if (playMode == PlayMode.MULTI_SELECT_NOTES) PlayMode.PLACE else PlayMode.MULTI_SELECT_NOTES
                            GameStateManager.setPlayMode(playMode)
                            isNotesMode = false
                            selectedNumbers1.clear()
                            selectedCell = null
                            if (highlightMode == HighlightMode.RCB_SELECTED) {
                                highlightMode = HighlightMode.PENCIL
                                GameStateManager.setHighlightMode(HighlightMode.PENCIL)
                            }
                            render()
                        }
                    }

                    button(classes = "undo-btn ${if (!gameEngine.canUndo()) "disabled" else ""}") {
                        +"↶"
                        attributes["title"] = "Undo last action"
                        onClickFunction = {
                            if (gameEngine.canUndo()) {
                                gameEngine.undoLastAction()
                                saveCurrentState()
                                render()
                            }
                        }
                    }

                    button(classes = "hint-btn ${if (!isBackendAvailable || candidateMode == CandidateMode.MANUAL) "disabled" else ""} ${if (showHints) "active" else ""} ${if (isLoadingHints) "loading" else ""}") {
                        if (isLoadingHints) +"🔄" else +"💡"
                        if (candidateMode == CandidateMode.MANUAL) {
                            attributes["title"] = "Hints are disabled in Manual mode"
                        } else if (!isBackendAvailable) {
                            attributes["title"] = "Hint system unavailable - backend not connected"
                        } else if (isLoadingHints) {
                            attributes["title"] = "Loading hints..."
                        }
                        onClickFunction = {
                            if (isBackendAvailable && !isLoadingHints && candidateMode == CandidateMode.AUTO) {
                                showHints = !showHints
                                if (showHints) {
                                    selectedHintIndex = 0
                                    explanationStepIndex = 0
                                    showExplanation = false
                                    expandedHintIndex = null
                                    selectedCell = null
                                    selectedNumbers1.clear()
                                    gameEngine.findAllTechniques()
                                    saveCurrentState(newHint = true)
                                } else {
                                    expandedHintIndex = null
                                }
                                render()
                            }
                        }
                    }

                    // Multi-select notes action buttons
                    if (playMode == PlayMode.MULTI_SELECT_NOTES) {
                        button(classes = "action-btn clr-sel-btn ${if (multiSelectAction == MultiSelectAction.CLEAR_SELECTED) "active" else ""}") {
                            +"Clr Sel"
                            attributes["title"] = "Clear selected numbers' pencil marks from clicked cell"
                            onClickFunction = {
                                multiSelectAction = MultiSelectAction.CLEAR_SELECTED
                                render()
                            }
                        }
                        button(classes = "action-btn clr-oth-btn ${if (multiSelectAction == MultiSelectAction.CLEAR_OTHER) "active" else ""}") {
                            +"Clr Oth"
                            attributes["title"] = "Clear all pencil marks except selected numbers from clicked cell"
                            onClickFunction = {
                                multiSelectAction = MultiSelectAction.CLEAR_OTHER
                                render()
                            }
                        }
                    }

                    // Clear selection button
                    if (selectedNumbers1.isNotEmpty()) {
                        button(classes = "action-btn clear-btn") {
                            +"✕"
                            onClickFunction = {
                                selectedNumbers1.clear()
                                render()
                            }
                        }
                    }
                }

                // Number pad
                val numberCounts = IntArray(10) { 0 }
                for (cell in grid.cells) {
                    if (cell.isSolved && cell.value != null) {
                        numberCounts[cell.value!!]++
                    }
                }

                div("number-pad") {
                    for (num in 1..9) {
                        val isPrimaryNum = num in selectedNumbers1
                        val isCompleted = numberCounts[num] >= 9
                        val numClass = when {
                            isCompleted -> "num-btn completed"
                            isPrimaryNum -> "num-btn primary"
                            else -> "num-btn"
                        }
                        button(classes = numClass) {
                            if (!isCompleted) {
                                +"$num"
                                onClickFunction = {
                                    handleNumberClick(num, grid)
                                }
                            }
                            if (isCompleted) {
                                attributes["disabled"] = "true"
                            }
                        }
                    }
                }

                // Portrait hint navigation (below number pad)
                    if (showHints && !isLandscape && hints.isNotEmpty()) {
                        renderPortraitHintCard(this@renderGameScreen, hints, selectedHint)
                    }
            }

            // Landscape hint sidebar (right of game area)
                if (showHints && isLandscape) {
                    renderLandscapeHintSidebar(this@renderGameScreen, hints, selectedHint)
                }
            } // Close main-content div

            // Toast
            if (toastMessage != null) {
                div("toast") { +toastMessage!! }
            }
            } // Close sudoku-container
        } // Close sudoku-container-wrapper
    }
}

/**
 * Calculate and apply scale transform to fit container within #app.
 * Wrapper is sized to the *visual* bounds (layout × scale) so flex layout matches what you see
 * and wide-aspect / landscape + hint sidebars still shrink when content is taller than the viewport.
 */
internal fun SudokuApp.applyContainerScaling() {
    if (currentScreen != AppScreen.GAME) return

    val wrapper = document.querySelector(".sudoku-container-wrapper") as? HTMLElement
    val container = document.querySelector(".sudoku-container") as? HTMLElement

    if (wrapper == null || container == null) return

    val originalMaxHeight = container.style.maxHeight
    val originalHeight = container.style.height
    val originalWrapperHeight = wrapper.style.height
    val originalWrapperWidth = wrapper.style.width

    container.style.maxHeight = "none"
    container.style.height = "auto"
    container.style.transform = "scale(1)"
    container.style.transformOrigin = ""
    wrapper.style.height = "auto"
    wrapper.style.width = "auto"

    val _reflow = container.offsetHeight

    val containerWidth = container.scrollWidth.toDouble()
    val containerHeight = container.scrollHeight.toDouble()

    container.style.maxHeight = originalMaxHeight
    container.style.height = originalHeight
    wrapper.style.height = originalWrapperHeight
    wrapper.style.width = originalWrapperWidth

    if (containerWidth < 1.0 || containerHeight < 1.0) return

    val appElement = document.getElementById("app") as? HTMLElement
    val availableWidth = (appElement?.clientWidth ?: window.innerWidth).toDouble()
    val availableHeight = (appElement?.clientHeight ?: window.innerHeight).toDouble()

    val scaleX = availableWidth / containerWidth
    val scaleY = ((availableHeight*2 + containerHeight) / 3) / containerHeight
    val scale = minOf(scaleX, scaleY, 1.0)

    val layoutEpsilon = 1e-6
    if (scale < 1.0 - layoutEpsilon) {
        val visualWidth = containerWidth * scale
        val visualHeight = containerHeight * scale
        wrapper.style.width = "${visualWidth}px"
        wrapper.style.height = "${visualHeight}px"
        wrapper.style.setProperty("flex-shrink", "0")
        wrapper.style.setProperty("overflow", "hidden")
        wrapper.style.display = "flex"
        wrapper.style.justifyContent = "flex-start"
        wrapper.style.alignItems = "flex-start"
        container.style.transform = "scale($scale)"
        container.style.transformOrigin = "top"
    } else {
        container.style.transform = ""
        container.style.transformOrigin = ""
        wrapper.style.width = "100%"
        wrapper.style.height = "100%"
        wrapper.style.removeProperty("flex-shrink")
        wrapper.style.removeProperty("overflow")
        wrapper.style.display = ""
        wrapper.style.justifyContent = ""
        wrapper.style.alignItems = ""
    }
}

/**
 * Match the hint sidebar height to the game area height in landscape mode.
 * This prevents the sidebar from causing the parent container to expand vertically.
 */
internal fun SudokuApp.matchHintSidebarHeight() {
    // Only apply when hints are shown in landscape mode
    val mainContent = document.querySelector(".main-content.landscape-hints") as? HTMLElement
    if (mainContent == null) return

    val gameArea = document.querySelector(".main-content.landscape-hints .game-area") as? HTMLElement
    val hintSidebar = document.querySelector(".hint-sidebar") as? HTMLElement

    if (gameArea == null || hintSidebar == null) return

    // Get the game area's height
    val gameAreaHeight = gameArea.offsetHeight

    // Set the sidebar to match the game area height
    hintSidebar.style.height = "${gameAreaHeight}px"
}

private fun FlowContent.renderCell(
    app: SudokuApp,
    cellIndex: Int,
    cell: SudokuCell,
    isPrimaryHighlight: Boolean,
    isSecondaryHighlight: Boolean,
    grid: SudokuGrid,
    selectedHint: TechniqueMatchInfo? = null,
    currentExplanationStep: ExplanationStepDto? = null
) {
    val row = cellIndex / 9
    val col = cellIndex % 9
    val isSelected = app.selectedCell == cellIndex

    // Check if this cell has a mistake (wrong value vs solution)
    val hasMistake = if (cell.isSolved && !cell.isGiven && app.solution != null) {
        val correctValue = app.solution!![cellIndex].digitToIntOrNull() ?: 0
        cell.value != correctValue
    } else false

    // Check if cell is highlighted by current explanation step
    val isStepHighlighted = currentExplanationStep?.highlightCells?.contains(cellIndex) == true

    // Check if cell is in a highlighted region from the current explanation step
    val isInHighlightedRegion = currentExplanationStep?.regions?.any { region ->
        when (region.type) {
            "row" -> row == region.index
            "column" -> col == region.index
            "box" -> {
                val boxRow = row / 3
                val boxCol = col / 3
                val boxIndex = boxRow * 3 + boxCol
                boxIndex == region.index
            }
            else -> false
        }
    } == true

    // Check if this cell has a coloured cell highlight from the explanation step
    val colouredCellType = currentExplanationStep?.colouredCells?.find { it.cellIndex == cellIndex }?.colourType

    // Build highlight class
    val highlightClass = when {
        isPrimaryHighlight && isSecondaryHighlight -> " highlight-both"
        isPrimaryHighlight -> " highlight-primary"
        isSecondaryHighlight -> " highlight-secondary"
        else -> ""
    }

    // Hint highlighting - new system
    val isInCoverArea = selectedHint != null && cellIndex in selectedHint.highlightCells
    val isHintSolved = selectedHint?.solvedCells?.any { it.cell == cellIndex } == true

    // Get all digits that are being eliminated (across all eliminations)
    val allEliminationDigits = selectedHint?.eliminations?.map { it.digit }?.toSet() ?: emptySet()

    // Get elimination digits specifically for this cell
    val eliminationDigitsForThisCell = selectedHint?.eliminations
        ?.filter { cellIndex in it.cells }
        ?.map { it.digit }
        ?.toSet() ?: emptySet()

    // Get digits that match elimination digits but are NOT being eliminated from this cell
    // (i.e., in cover area, has the candidate, but not in elimination list for this cell)
    val matchingButNotEliminatedDigits = if (isInCoverArea) {
        allEliminationDigits.filter { digit ->
            digit in cell.displayCandidates && digit !in eliminationDigitsForThisCell
        }.toSet()
    } else emptySet()

    // Hint class for cell background (blue for cover area)
    val hintClass = when {
        colouredCellType == "warning" -> " hint-cell-warning"  // Warning highlight (yellow/orange)
        colouredCellType == "target" -> " hint-cell-target"    // Target highlight (green)
        colouredCellType == "primary" -> " hint-cell-primary"  // Primary highlight
        isStepHighlighted -> " hint-step-highlight"  // Current explanation step highlight
        isInHighlightedRegion -> " hint-region-highlight"  // Cell is in a highlighted region
        isHintSolved -> " hint-solved-cell"
        isInCoverArea -> " hint-cover-area"
        else -> ""
    }

    // Get solved digit for this cell from the hint
    val hintSolvedDigit = selectedHint?.solvedCells?.find { it.cell == cellIndex }?.digit

    // Check if ALL candidates in this cell are covered by selected numbers
    val allCandidatesCovered = !cell.isSolved &&
        cell.displayCandidates.isNotEmpty() &&
        app.selectedNumbers1.isNotEmpty() &&
        cell.displayCandidates.all { it in app.selectedNumbers1 }
    val coveredClass = if (allCandidatesCovered) " all-candidates-covered" else ""

    // 3×3 block boundaries (pairs with .box-left / .box-top in Styles.kt for gutters + optional lines)
    val boxSepClass = buildString {
        if (col > 0 && col % 3 == 0) append(" box-left")
        if (row > 0 && row % 3 == 0) append(" box-top")
    }

    val solvedClass = if (cell.isSolved && !cell.isGiven) " solved" else ""
    div("cell${if (isSelected) " selected" else ""}${if (cell.isGiven) " given" else ""}$solvedClass${if (hasMistake) " mistake" else ""}$highlightClass$hintClass$coveredClass$boxSepClass") {
        if (cell.isSolved) {
            span("cell-value") { +"${cell.value}" }
        } else if (cell.displayCandidates.isNotEmpty()) {
            div("candidates") {
                for (n in 1..9) {
                    val inPrimary = n in app.selectedNumbers1
                    val pencilHighlightClass = if ((app.highlightMode == HighlightMode.PENCIL || app.showExplanation) && inPrimary) {
                        " pencil-highlight-primary"
                    } else ""

                    val pencilRemovalClass = when {
                        // Notes mode: slash on candidates that would be removed (selected and exist in cell)
                        app.playMode == PlayMode.PLACE && app.isNotesMode && inPrimary && n in cell.displayCandidates -> " pencil-removal"
                        // Multiselect CLEAR_SELECTED: slash on selected candidates that exist
                        app.playMode == PlayMode.MULTI_SELECT_NOTES && app.multiSelectAction == MultiSelectAction.CLEAR_SELECTED && inPrimary && n in cell.displayCandidates -> " pencil-removal"
                        // Multiselect CLEAR_OTHER: slash on non-selected candidates that exist (when selection is non-empty)
                        app.playMode == PlayMode.MULTI_SELECT_NOTES && app.multiSelectAction == MultiSelectAction.CLEAR_OTHER && app.selectedNumbers1.isNotEmpty() && !inPrimary && n in cell.displayCandidates -> " pencil-removal"
                        else -> ""
                    }

                    // Hint-specific pencil mark highlighting
                    val isElimination = n in eliminationDigitsForThisCell
                    val isMatchingButNotEliminated = n in matchingButNotEliminatedDigits
                    val isSolvedHint = n == hintSolvedDigit

                    // Check for coloured candidate from explanation step
                    val colouredCandidate = currentExplanationStep?.colouredCandidates?.find {
                        it.row == row && it.col == col && it.candidate == n
                    }
                    val colouredCandidateType = colouredCandidate?.colourType

                    val candidateClasses = buildString {
                        append("candidate")
                        if (n !in cell.displayCandidates) append(" hidden")
                        append(pencilHighlightClass)
                        append(pencilRemovalClass)
                        // coloured candidates from explanation step take priority
                        when (colouredCandidateType) {
                            "target" -> append(" hint-candidate-target")  // Green
                            "elimination" -> append(" hint-candidate-elimination")  // Red with strikethrough
                            "highlight" -> append(" hint-candidate-highlight")  // Yellow/amber
                            "info" -> append(" hint-candidate-info")  // Blue
                            else -> {
                                // Fall back to existing hint highlighting
                                if (isElimination) append(" hint-elimination")
                                if (isMatchingButNotEliminated) append(" hint-matching-not-eliminated")
                                if (isSolvedHint) append(" hint-solved")
                            }
                        }
                    }
                    span(candidateClasses) {
                        +"$n"
                    }
                }
            }
        }

        onClickFunction = {
            app.handleCellClick(cellIndex, grid)
        }
    }
}
