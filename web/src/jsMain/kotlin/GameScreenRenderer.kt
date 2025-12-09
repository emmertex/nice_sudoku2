import adapter.CandidateLocationDto
import adapter.ExplanationStepDto
import adapter.GroupDto
import adapter.LineDto
import adapter.TechniqueMatchInfo
import domain.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
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
    
    val currentElapsed = pausedTime + (currentTimeMillis() - gameStartTime)
    
    // Compute highlights
    val primaryCells = getPrimaryHighlightCells(grid)
    val secondaryCells = getSecondaryHighlightCells(grid)
    
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
                            saveCurrentState()
                            currentScreen = AppScreen.SETTINGS
                            render()
                        }
                    }
                    div {
                        h1 { +"Nice Sudoku" }
                        div("powered-by") { +"Powered by StormDoku" }
                    }
                    // Show current mode indicators and selected numbers
                    div("mode-indicators") {
                        span("mode-badge highlight-mode") { 
                            +when (highlightMode) {
                                HighlightMode.CELL -> "Cell"
                                HighlightMode.RCB_SELECTED -> "RCB"
                                HighlightMode.RCB_ALL -> "RCB+"
                                HighlightMode.PENCIL -> "✏️"
                            }
                        }
                        span("mode-badge play-mode clickable ${if (playMode == PlayMode.FAST) "fast" else "advanced"}") {
                            +if (playMode == PlayMode.FAST) "Fast" else "Adv"
                            onClickFunction = {
                                if (playMode == PlayMode.FAST) {
                                    playMode = PlayMode.ADVANCED
                                    GameStateManager.setPlayMode(PlayMode.ADVANCED)
                                } else {
                                    playMode = PlayMode.FAST
                                    GameStateManager.setPlayMode(PlayMode.FAST)
                                    // Clear all state when switching to FAST
                                    selectedNumbers1.clear()
                                    selectedNumbers2.clear()
                                    selectedCell = null
                                }
                                render()
                            }
                        }
                        // Selection info inline with mode badges
                        if (selectedNumbers1.isNotEmpty()) {
                            span("selected-num primary") { +selectedNumbers1.sorted().joinToString(",") }
                        }
                        if (selectedNumbers2.isNotEmpty()) {
                            span("selected-num secondary") { +selectedNumbers2.sorted().joinToString(",") }
                        }
                    }
                }
                if (game != null) {
                    div("game-info") {
                        span("category ${game.category.name.lowercase()}") { 
                            +game.category.displayName 
                        }
                        span("difficulty") { +"★ ${game.difficulty}" }
                        span("timer-container") {
                            span("timer") { +"⏱ ${formatTime(currentElapsed)}" }
                            button(classes = "pause-btn") {
                                +(if (isPaused) "▶" else "⏸")
                                onClickFunction = {
                                    togglePause()
                                }
                            }
                        }
                        span("mistakes") { +"❌ ${game.mistakeCount}" }
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
                div("sudoku-grid-container ${if (isPaused) "paused" else ""}") {
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
                    if (isPaused) {
                        div("pause-overlay") {
                            onClickFunction = {
                                resumeGame()
                            }
                            div("pause-message") {
                                div("pause-icon") { +"⏸" }
                                div("pause-text") { +"PAUSED" }
                                div("pause-subtext") { +"Click to resume" }
                            }
                        }
                    }
                }
                
                // Controls row - Notes, Erase, Hint, and Advanced actions
                div("controls") {
                    button(classes = "toggle-btn ${if (isNotesMode) "active" else ""}") {
                        +if (isNotesMode) "✏️ ON" else "✏️"
                        onClickFunction = {
                            isNotesMode = !isNotesMode
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
                    
                    button(classes = "hint-btn ${if (!isBackendAvailable) "disabled" else ""} ${if (showHints) "active" else ""} ${if (isLoadingHints) "loading" else ""}") {
                        if (isLoadingHints) {
                            +"🔄"
                        } else {
                            +"💡"
                        }
                        if (!isBackendAvailable) {
                            attributes["title"] = "Hint system unavailable - backend not connected"
                        } else if (isLoadingHints) {
                            attributes["title"] = "Loading hints..."
                        }
                        onClickFunction = {
                            if (isBackendAvailable && !isLoadingHints) {
                                showHints = !showHints
                                if (showHints) {
                                    selectedHintIndex = 0
                                    explanationStepIndex = 0
                                    showExplanation = false
                                    expandedHintIndex = null
                                    selectedCell = null
                                    selectedNumbers1.clear()
                                    selectedNumbers2.clear()
                                    gameEngine.findAllTechniques()
                                } else {
                                    // Reset expansion state when closing
                                    expandedHintIndex = null
                                }
                                render()
                            }
                        }
                    }
                    
                    // Advanced mode action buttons
                    if (playMode == PlayMode.ADVANCED && selectedCell != null) {
                        val cell = grid.getCell(selectedCell!!)
                        
                        // Deselect button (to hide action buttons and show number bars again)
                        button(classes = "action-btn deselect-btn") {
                            +"Deselect"
                            onClickFunction = {
                                selectedCell = null
                                render()
                            }
                        }
                        
                        // Set buttons for candidates that match selected numbers
                        // Show Set button for each candidate in the cell that is in either selection
                        if (!cell.isGiven && !cell.isSolved) {
                            val allSelected = selectedNumbers1 + selectedNumbers2
                            val settableCandidates = cell.displayCandidates.filter { it in allSelected }.sorted()
                            
                            for (num in settableCandidates) {
                                val btnClass = when {
                                    num in selectedNumbers1 && num in selectedNumbers2 -> "action-btn set-btn both"
                                    num in selectedNumbers1 -> "action-btn set-btn primary"
                                    else -> "action-btn set-btn secondary"
                                }
                                button(classes = btnClass) {
                                    +"Set $num"
                                    onClickFunction = {
                                        gameEngine.recordAction(gameEngine.createPlacementAction(selectedCell!!, num))
                                        gameEngine.setCellValue(selectedCell!!, num)
                                        saveCurrentState()
                                        render()
                                    }
                                }
                            }
                        }
                        
                        // Clear pencil mark buttons for all selected numbers
                        val primaryInCandidates = selectedNumbers1.filter { it in cell.displayCandidates }
                        val secondaryInCandidates = selectedNumbers2.filter { it in cell.displayCandidates }
                        
                        if (primaryInCandidates.isNotEmpty() && !cell.isGiven) {
                            button(classes = "action-btn clr-btn primary") {
                                +"Clr ${primaryInCandidates.sorted().joinToString(",")}"
                                onClickFunction = {
                                    var hadMistake = false
                                    primaryInCandidates.forEach { num ->
                                        // All candidates in primaryInCandidates are present (being removed)
                                        if (checkCandidateRemovalMistake(selectedCell!!, num, true)) {
                                            hadMistake = true
                                        }
                                        gameEngine.recordAction(gameEngine.createEliminationAction(selectedCell!!, num))
                                        gameEngine.toggleCandidate(selectedCell!!, num)
                                    }
                                    if (hadMistake) showToast("❌ Wrong candidate removed!")
                                    saveCurrentState()
                                    render()
                                }
                            }
                        }
                        if (secondaryInCandidates.isNotEmpty() && !cell.isGiven) {
                            button(classes = "action-btn clr-btn secondary") {
                                +"Clr ${secondaryInCandidates.sorted().joinToString(",")}"
                                onClickFunction = {
                                    var hadMistake = false
                                    secondaryInCandidates.forEach { num ->
                                        // All candidates in secondaryInCandidates are present (being removed)
                                        if (checkCandidateRemovalMistake(selectedCell!!, num, true)) {
                                            hadMistake = true
                                        }
                                        gameEngine.recordAction(gameEngine.createEliminationAction(selectedCell!!, num))
                                        gameEngine.toggleCandidate(selectedCell!!, num)
                                    }
                                    if (hadMistake) showToast("❌ Wrong candidate removed!")
                                    saveCurrentState()
                                    render()
                                }
                            }
                        }
                        
                        // Clear all OTHER pencil marks (keep only highlighted numbers)
                        val keepNumbers = selectedNumbers1 + selectedNumbers2
                        val candidatesToRemove = cell.displayCandidates - keepNumbers
                        if (!cell.isGiven && !cell.isSolved && candidatesToRemove.isNotEmpty()) {
                            button(classes = "action-btn clr-btn other") {
                                +"Clr ✕"
                                attributes["title"] = "Clear all pencil marks except ${keepNumbers.sorted().joinToString(", ")}"
                                onClickFunction = {
                                    var hadMistake = false
                                    candidatesToRemove.forEach { candidate ->
                                        // All candidatesToRemove are present (being removed)
                                        if (checkCandidateRemovalMistake(selectedCell!!, candidate, true)) {
                                            hadMistake = true
                                        }
                                        gameEngine.recordAction(gameEngine.createEliminationAction(selectedCell!!, candidate))
                                        gameEngine.toggleCandidate(selectedCell!!, candidate)
                                    }
                                    if (hadMistake) showToast("❌ Wrong candidate removed!")
                                    saveCurrentState()
                                    render()
                                }
                            }
                        }
                    }
                    
                    // Clear selection button
                    if (selectedNumbers1.isNotEmpty() || selectedNumbers2.isNotEmpty()) {
                        button(classes = "action-btn clear-btn") {
                            +"✕"
                            onClickFunction = {
                                selectedNumbers1.clear()
                                selectedNumbers2.clear()
                                render()
                            }
                        }
                    }
                }
                
                // Number pad with selection state
                // Count how many of each number are placed (solved cells)
                val numberCounts = IntArray(10) { 0 }
                for (cell in grid.cells) {
                    if (cell.isSolved && cell.value != null) {
                        numberCounts[cell.value!!]++
                    }
                }
                
                // In Advanced mode with cell selected, hide number pads (show action buttons instead)
                val hideNumberPads = playMode == PlayMode.ADVANCED && selectedCell != null
                
                if (!hideNumberPads) {
                    if (playMode == PlayMode.FAST) {
                        // FAST mode: single number pad
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
                                    }
                                    if (!isCompleted) {
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
                    } else {
                        // ADVANCED mode: two number pads (primary and secondary)
                        // Primary number pad (blue)
                        div("number-pad primary") {
                            for (num in 1..9) {
                                val isSelected = num in selectedNumbers1
                                val isCompleted = numberCounts[num] >= 9
                                val numClass = when {
                                    isCompleted -> "num-btn completed"
                                    isSelected -> "num-btn primary"
                                    else -> "num-btn"
                                }
                                button(classes = numClass) {
                                    if (!isCompleted) {
                                        +"$num"
                                    }
                                    if (!isCompleted) {
                                        onClickFunction = {
                                            togglePrimaryNumber(num)
                                        }
                                    }
                                    if (isCompleted) {
                                        attributes["disabled"] = "true"
                                    }
                                }
                            }
                        }
                        // Secondary number pad (red)
                        div("number-pad secondary") {
                            for (num in 1..9) {
                                val isSelected = num in selectedNumbers2
                                val isCompleted = numberCounts[num] >= 9
                                val numClass = when {
                                    isCompleted -> "num-btn completed"
                                    isSelected -> "num-btn secondary"
                                    else -> "num-btn"
                                }
                                button(classes = numClass) {
                                    if (!isCompleted) {
                                        +"$num"
                                    }
                                    if (!isCompleted) {
                                        onClickFunction = {
                                            toggleSecondaryNumber(num)
                                        }
                                    }
                                    if (isCompleted) {
                                        attributes["disabled"] = "true"
                                    }
                                }
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
    
    // Apply scaling after DOM is updated
    window.setTimeout({
        matchHintSidebarHeight()
        if (isLandscape == false) {
            applyContainerScaling()
        }
    }, 0)
}

/**
 * Calculate and apply scale transform to fit container within viewport.
 * This prevents scrolling by scaling down the game container if it exceeds viewport boundaries.
 */
internal fun SudokuApp.applyContainerScaling() {
    // Only scale on game screen
    if (currentScreen != AppScreen.GAME) return
    
    val wrapper = document.querySelector(".sudoku-container-wrapper") as? HTMLElement
    val container = document.querySelector(".sudoku-container") as? HTMLElement
    
    if (wrapper == null || container == null) return
    
    // Store original styles to restore later
    val originalMaxHeight = container.style.maxHeight
    val originalHeight = container.style.height
    val originalTransform = container.style.transform
    val originalWrapperHeight = wrapper.style.height
    
    // Temporarily remove height constraints to allow container to expand to natural size
    // This is crucial for accurate measurement when content changes (e.g., second number pad appears)
    container.style.maxHeight = "none"
    container.style.height = "auto"
    container.style.transform = "scale(1)"
    wrapper.style.height = "auto"  // Allow wrapper to expand during measurement
    
    // Force a reflow to ensure accurate measurements
    val _x = container.offsetHeight
    
    // Get the natural (unscaled) dimensions of the container
    // Use scrollWidth/scrollHeight to get the full content size
    val containerWidth = container.scrollWidth.toDouble()
    val containerHeight = container.scrollHeight.toDouble()
    
    // Restore original height constraints
    container.style.maxHeight = originalMaxHeight
    container.style.height = originalHeight
    wrapper.style.height = originalWrapperHeight
    
    // Get available viewport space
    // Use wrapper's parent (app element) to get actual available space
    val appElement = document.getElementById("app") as? HTMLElement
    val availableWidth = (appElement?.clientWidth ?: window.innerWidth).toDouble()
    val availableHeight = (appElement?.clientHeight ?: window.innerHeight).toDouble()
    
    // Calculate scale factors for both dimensions
    val scaleX = availableWidth / containerWidth
    val scaleY = availableHeight / containerHeight
    
    // Use the smaller scale to ensure it fits in both dimensions
    // Also ensure we don't scale up (min scale is 1.0)
    val scale = minOf(scaleX, scaleY, 1.0)
    
    // Apply the scale transform
    container.style.transform = "scale($scale)"
    
    // Ensure wrapper is properly sized
    wrapper.style.width = "100%"
    wrapper.style.height = "100%"
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
    
    val boxBorderClasses = buildString {
        if (col % 3 == 0 && col > 0) append(" box-left")
        if (row % 3 == 0 && row > 0) append(" box-top")
    }
    
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
    
    // Check if this cell has a colored cell highlight from the explanation step
    val coloredCellType = currentExplanationStep?.coloredCells?.find { it.cellIndex == cellIndex }?.colorType
    
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
        coloredCellType == "warning" -> " hint-cell-warning"  // Warning highlight (yellow/orange)
        coloredCellType == "target" -> " hint-cell-target"    // Target highlight (green)
        coloredCellType == "primary" -> " hint-cell-primary"  // Primary highlight
        isStepHighlighted -> " hint-step-highlight"  // Current explanation step highlight
        isInHighlightedRegion -> " hint-region-highlight"  // Cell is in a highlighted region
        isHintSolved -> " hint-solved-cell"
        isInCoverArea -> " hint-cover-area"
        else -> ""
    }
    
    // Get solved digit for this cell from the hint
    val hintSolvedDigit = selectedHint?.solvedCells?.find { it.cell == cellIndex }?.digit
    
    // Check if ALL candidates in this cell are covered by selected numbers (from either color)
    val allSelectedNumbers = app.selectedNumbers1 + app.selectedNumbers2
    val allCandidatesCovered = !cell.isSolved && 
        cell.displayCandidates.isNotEmpty() && 
        allSelectedNumbers.isNotEmpty() &&
        cell.displayCandidates.all { it in allSelectedNumbers }
    val coveredClass = if (allCandidatesCovered) " all-candidates-covered" else ""
    
    val solvedClass = if (cell.isSolved && !cell.isGiven) " solved" else ""
    div("cell${if (isSelected) " selected" else ""}${if (cell.isGiven) " given" else ""}$solvedClass${if (hasMistake) " mistake" else ""}$highlightClass$hintClass$coveredClass$boxBorderClasses") {
        if (cell.isSolved) {
            span("cell-value") { +"${cell.value}" }
        } else if (cell.displayCandidates.isNotEmpty()) {
            div("candidates") {
                for (n in 1..9) {
                    // Highlight pencil marks that match selected numbers (with color-coded classes)
                    val inPrimary = n in app.selectedNumbers1
                    val inSecondary = n in app.selectedNumbers2
                    // Always highlight selected numbers when viewing explanations, otherwise respect highlightMode
                    val pencilHighlightClass = if (app.highlightMode == HighlightMode.PENCIL || app.showExplanation) {
                        when {
                            inPrimary && inSecondary -> " pencil-highlight-both"
                            inPrimary -> " pencil-highlight-primary"
                            inSecondary -> " pencil-highlight-secondary"
                            else -> ""
                        }
                    } else ""
                    
                    // Hint-specific pencil mark highlighting
                    val isElimination = n in eliminationDigitsForThisCell
                    val isMatchingButNotEliminated = n in matchingButNotEliminatedDigits
                    val isSolvedHint = n == hintSolvedDigit
                    
                    // Check for colored candidate from explanation step
                    val coloredCandidate = currentExplanationStep?.coloredCandidates?.find { 
                        it.row == row && it.col == col && it.candidate == n 
                    }
                    val coloredCandidateType = coloredCandidate?.colorType
                    
                    val candidateClasses = buildString {
                        append("candidate")
                        if (n !in cell.displayCandidates) append(" hidden")
                        append(pencilHighlightClass)
                        // Colored candidates from explanation step take priority
                        when (coloredCandidateType) {
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

