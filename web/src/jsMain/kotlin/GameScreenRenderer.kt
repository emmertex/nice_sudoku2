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
                        span("timer") { +"⏱ ${formatTime(currentElapsed)}" }
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
                div("sudoku-grid-container") {
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
                    
                    button(classes = "hint-btn ${if (!isBackendAvailable) "disabled" else ""} ${if (showHints) "active" else ""}") {
                        +"💡"
                        if (!isBackendAvailable) {
                            attributes["title"] = "Hint system unavailable - backend not connected"
                        }
                        onClickFunction = {
                            if (isBackendAvailable) {
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

private fun TagConsumer<HTMLElement>.renderLandscapeHintSidebar(app: SudokuApp, 
    hints: List<TechniqueMatchInfo>,
    selectedHint: TechniqueMatchInfo?
) {
    div("hint-sidebar") {
        // Show either the explanation view OR the list view (not both)
        if (app.showExplanation && selectedHint != null) {
            // Explanation view - replaces the entire list
            // Collapse any expanded hint when showing explanation
            app.expandedHintIndex = null
            renderExplanationView(app, selectedHint, hints.size)
        } else {
            // List view
            div("hint-sidebar-header") {
                h3 { +"Available Hints" }
                span("hint-count") { +"(${hints.size})" }
            }
            
            if (hints.isEmpty()) {
                div("hint-empty") {
                    p { +"Searching for hints..." }
                }
            } else {
                div("hint-list") {
                    hints.forEachIndexed { index, hint ->
                        val isSelected = index == app.selectedHintIndex
                        val isExpanded = app.expandedHintIndex == index
                        div("hint-item ${if (isSelected) "selected" else ""} ${if (isExpanded) "expanded" else ""}") {
                            // Header row (always visible) - title only
                            div("hint-item-header") {
                                onClickFunction = { e ->
                                    // Select this hint
                                    app.selectedHintIndex = index
                                    app.explanationStepIndex = 0
                                    // Toggle expansion: if clicking the same one, collapse it; otherwise expand this one
                                    app.expandedHintIndex = if (isExpanded) null else index
                                    app.render()
                                }
                                div("hint-item-content") {
                                    div("hint-technique") { +hint.techniqueName }
                                    // Description is hidden in landscape mode - only show when expanded
                                }
                            }
                            // Expanded content: eurekaNotation and Explain button
                            if (isExpanded) {
                                div("hint-item-expanded") {
                                    // Show eurekaNotation if available
                                    val eureka = hint.eurekaNotation
                                    if (eureka != null) {
                                        div("inline-eureka") {
                                            span("eureka-label") { +"Eureka: " }
                                            span("eureka-notation") { +eureka }
                                        }
                                    }
                                    // Explain button
                                    button(classes = "hint-explain-btn") {
                                        +"📖 Explain"
                                        onClickFunction = { e ->
                                            e.stopPropagation()
                                            app.showExplanation = true
                                            app.explanationStepIndex = 0
                                            app.render()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Close button
            button(classes = "hint-close-btn") {
                +"✕ Close"
                onClickFunction = {
                    app.showHints = false
                    app.expandedHintIndex = null
                    app.render()
                }
            }
        }
    }
}

private fun TagConsumer<HTMLElement>.renderPortraitHintCard(app: SudokuApp, 
    hints: List<TechniqueMatchInfo>,
    selectedHint: TechniqueMatchInfo?
) {
    div("hint-card") {
        // Compact header: [Prev] [Title or Position] [Next] [Collapse/Close]
        div("hint-card-header-compact") {
            button(classes = "hint-nav-btn-small ${if (app.selectedHintIndex <= 0) "disabled" else ""}") {
                +"◀ Prev"
                onClickFunction = {
                    if (app.selectedHintIndex > 0) {
                        app.selectedHintIndex--
                        app.explanationStepIndex = 0
                        app.render()
                    }
                }
            }
            
            // Title area: show technique name, or position if no hint selected
            div("hint-title-area") {
                if (selectedHint != null) {
                    span("hint-technique-compact") { +selectedHint.techniqueName }
                    span("hint-position-small") { +"${app.selectedHintIndex + 1}/${hints.size}" }
                } else {
                    span("hint-position") { +"${app.selectedHintIndex + 1} / ${hints.size}" }
                }
            }
            
            button(classes = "hint-nav-btn-small ${if (app.selectedHintIndex >= hints.size - 1) "disabled" else ""}") {
                +"Next ▶"
                onClickFunction = {
                    if (app.selectedHintIndex < hints.size - 1) {
                        app.selectedHintIndex++
                        app.explanationStepIndex = 0
                        app.render()
                    }
                }
            }
            
            // Close button
            
            button(classes = "hint-close-btn-small") {
                +"✕"
                onClickFunction = {
                    app.showHints = false
                    app.render()
                }
            }
        }
        
        if (selectedHint != null) {
            // Show explanation with step navigation
            renderInlineExplanationCompact(app, selectedHint)
        } else {
            div("hint-content hint-empty") {
                p { +"Searching for hints..." }
            }
        }
    }
}

/**
 * Render compact inline explanation (collapse button is in header)
 */
private fun TagConsumer<HTMLElement>.renderInlineExplanationCompact(app: SudokuApp, hint: TechniqueMatchInfo) {
    val steps = if (hint.explanationSteps.isNotEmpty()) {
        hint.explanationSteps
    } else {
        app.generateFallbackExplanationSteps(hint)
    }
    val currentStep = steps.getOrNull(app.explanationStepIndex)
    
    div("inline-explanation-compact") {
        // Eureka notation if available (for chains)
        val eureka = hint.eurekaNotation
        if (eureka != null) {
            div("inline-eureka") {
                span("eureka-label") { +"Eureka: " }
                span("eureka-notation") { +eureka }
            }
        }
        
        // Step content
        if (currentStep != null) {
            div("inline-step") {
                div("step-header-compact") {
                    // Step navigation buttons (only show if more than one step)
                    if (steps.size > 1) {
                        button(classes = "step-nav-btn-small ${if (app.explanationStepIndex <= 0) "disabled" else ""}") {
                            +"◀"
                            onClickFunction = { e ->
                                e.stopPropagation()
                                if (app.explanationStepIndex > 0) {
                                    app.explanationStepIndex--
                                    app.render()
                                }
                            }
                        }
                    }
                    
                    span("step-badge") { +"Step ${currentStep.stepNumber}" }
                    span("step-title") { +currentStep.title }
                    
                    // Step navigation buttons (only show if more than one step)
                    if (steps.size > 1) {
                        button(classes = "step-nav-btn-small ${if (app.explanationStepIndex >= steps.size - 1) "disabled" else ""}") {
                            +"▶"
                            onClickFunction = { e ->
                                e.stopPropagation()
                                if (app.explanationStepIndex < steps.size - 1) {
                                    app.explanationStepIndex++
                                    app.render()
                                }
                            }
                        }
                    }
                }
                div("step-description") {
                    renderInteractiveDescription(app, currentStep.description, hint)
                }
            }
            } else {
                div("inline-step") {
                    div("step-description") {
                        renderInteractiveDescription(app, hint.description, hint)
                    }
                }
            }
    }
}


/**
 * Render interactive hint description with clickable/hoverable elements
 * Parses patterns like:
 * - (3)R5C6 - cell/candidate reference (for chains)
 * - R5C6 or R1C8, R1C9 - simple cell references
 * - {5, 7, 8} or {7} - digit sets
 * - Row 5, Column 7, Box 3 - house references
 * - --[strong]--> or --[weak]--> - link indicators
 */
private fun TagConsumer<HTMLElement>.renderInteractiveDescription(app: SudokuApp, description: String, hint: TechniqueMatchInfo) {
    var linkIndex = 0
    val result = StringBuilder()
    
    // Find all matches and their positions
    data class Match(val start: Int, val end: Int, val type: String, val content: String, val data: Any?)
    val matches = mutableListOf<Match>()
    
    // Pattern for chain notation: (3)R5C6 or (3)R5C6,R5C7
    val chainCellPattern = Regex("""\((\d)\)([Rr])(\d)[Cc](\d)(?:,([Rr])(\d)[Cc](\d))*""")
    chainCellPattern.findAll(description).forEach { match ->
        val digit = match.groupValues[1].toInt()
        val cells = mutableListOf<Pair<Int, Int>>()
        val singleCellPattern = Regex("""[Rr](\d)[Cc](\d)""")
        singleCellPattern.findAll(match.value).forEach { cellMatch ->
            val row = cellMatch.groupValues[1].toInt() - 1
            val col = cellMatch.groupValues[2].toInt() - 1
            cells.add(row to col)
        }
        matches.add(Match(match.range.first, match.range.last + 1, "chain-cell", match.value, digit to cells))
    }
    
    // Pattern for simple cell references: R5C6 or R1C8, R1C9 (but not preceded by digit in parens)
    // Match cell lists like "R1C7, R3C7" or single cells like "R5C5"
    val simpleCellPattern = Regex("""(?<!\(\d\))([Rr]\d[Cc]\d(?:\s*,\s*[Rr]\d[Cc]\d)*)""")
    simpleCellPattern.findAll(description).forEach { match ->
        // Skip if this overlaps with a chain-cell match
        val overlaps = matches.any { m -> 
            m.type == "chain-cell" && 
            ((match.range.first >= m.start && match.range.first < m.end) ||
             (match.range.last >= m.start && match.range.last < m.end))
        }
        if (!overlaps) {
            val cells = mutableListOf<Pair<Int, Int>>()
            val singleCellPattern = Regex("""[Rr](\d)[Cc](\d)""")
            singleCellPattern.findAll(match.value).forEach { cellMatch ->
                val row = cellMatch.groupValues[1].toInt() - 1
                val col = cellMatch.groupValues[2].toInt() - 1
                cells.add(row to col)
            }
            if (cells.isNotEmpty()) {
                matches.add(Match(match.range.first, match.range.last + 1, "simple-cell", match.value, cells))
            }
        }
    }
    
    // Pattern for digit sets: {5, 7, 8} or {7}
    val digitSetPattern = Regex("""\{(\d(?:\s*,\s*\d)*)\}""")
    digitSetPattern.findAll(description).forEach { match ->
        val digits = match.groupValues[1].split(",").mapNotNull { it.trim().toIntOrNull() }
        if (digits.isNotEmpty()) {
            matches.add(Match(match.range.first, match.range.last + 1, "digit-set", match.value, digits))
        }
    }
    
    // Pattern for single digits in context (e.g., "eliminate 7 from" or "candidate 5 only")
    // Match digits after keywords like "candidate", "digit", "eliminate", or before certain words
    val digitContextPatterns = listOf(
        // Digit after keyword: "candidate 5", "digit 7", "eliminate 5"
        Regex("""(?:candidate|digit|eliminate|Eliminate)\s+(\d)(?!\d)""", RegexOption.IGNORE_CASE),
        // Digit before action word: "5 can", "7 from", "5 in", "5 must", "5 is", "5 only"
        Regex("""(?<=\s|^)(\d)(?=\s+(?:can|from|in|must|is|only|appears?|be)\b)""", RegexOption.IGNORE_CASE),
        // Digit at end of sentence or before comma
        Regex("""(?<=\s)(\d)(?=\s*[.,]|\s*$)""")
    )
    
    for (pattern in digitContextPatterns) {
        pattern.findAll(description).forEach { match ->
            val digit = match.groupValues[1].toIntOrNull()
            if (digit != null && digit in 1..9) {
                // Find the actual position of the digit within the match
                val digitIndex = match.value.indexOfFirst { it.isDigit() }
                val digitStart = match.range.first + digitIndex
                val digitEnd = digitStart + 1
                
                // Skip if this overlaps with other matches
                val overlaps = matches.any { m ->
                    (digitStart >= m.start && digitStart < m.end) ||
                    (digitEnd > m.start && digitEnd <= m.end)
                }
                if (!overlaps) {
                    matches.add(Match(digitStart, digitEnd, "single-digit", digit.toString(), digit))
                }
            }
        }
    }
    
    // Pattern for house references: Row 5, Column 7, Box 3
    val housePattern = Regex("""(Row|Column|Box)\s+(\d)""", RegexOption.IGNORE_CASE)
    housePattern.findAll(description).forEach { match ->
        val houseType = match.groupValues[1].lowercase()
        val houseIndex = match.groupValues[2].toInt() - 1  // Convert to 0-indexed
        if (houseIndex in 0..8) {
            matches.add(Match(match.range.first, match.range.last + 1, "house", match.value, houseType to houseIndex))
        }
    }
    
    // Pattern for link indicators: --[strong]--> or --[weak]-->
    val linkPattern = Regex("""--\[(strong|weak)\]-->""")
    linkPattern.findAll(description).forEach { match ->
        val linkType = match.groupValues[1]
        matches.add(Match(match.range.first, match.range.last + 1, "link", match.value, linkIndex++ to linkType))
    }
    
    // Sort matches by position and remove overlaps (keep longer/earlier matches)
    matches.sortBy { it.start }
    val filteredMatches = mutableListOf<Match>()
    for (match in matches) {
        val overlaps = filteredMatches.any { existing ->
            (match.start >= existing.start && match.start < existing.end) ||
            (match.end > existing.start && match.end <= existing.end)
        }
        if (!overlaps) {
            filteredMatches.add(match)
        }
    }
    
    // Build HTML with interactive spans
    var currentPos = 0
    filteredMatches.forEach { match ->
        // Add text before this match
        if (match.start > currentPos) {
            result.append("""<span class="desc-text">${htmlEscape(description.substring(currentPos, match.start))}</span>""")
        }
        
        when (match.type) {
            "chain-cell" -> {
                @Suppress("UNCHECKED_CAST")
                val data = match.data as Pair<Int, List<Pair<Int, Int>>>
                val digit = data.first
                val cells = data.second
                val cellIndices = cells.map { pair -> pair.first * 9 + pair.second }
                val dataAttr = cellIndices.joinToString(",")
                result.append("""<span class="chain-cell-ref interactive-ref" data-cells="$dataAttr" data-candidate="$digit">${match.content}</span>""")
            }
            "simple-cell" -> {
                @Suppress("UNCHECKED_CAST")
                val cells = match.data as List<Pair<Int, Int>>
                val cellIndices = cells.map { pair -> pair.first * 9 + pair.second }
                val dataAttr = cellIndices.joinToString(",")
                result.append("""<span class="cell-ref interactive-ref" data-cells="$dataAttr">${match.content}</span>""")
            }
            "digit-set" -> {
                @Suppress("UNCHECKED_CAST")
                val digits = match.data as List<Int>
                val dataAttr = digits.joinToString(",")
                result.append("""<span class="digit-ref interactive-ref" data-digits="$dataAttr">${match.content}</span>""")
            }
            "single-digit" -> {
                val digit = match.data as Int
                result.append("""<span class="digit-ref interactive-ref" data-digits="$digit">${match.content}</span>""")
            }
            "house" -> {
                @Suppress("UNCHECKED_CAST")
                val data = match.data as Pair<String, Int>
                val houseType = data.first
                val houseIndex = data.second
                result.append("""<span class="house-ref interactive-ref" data-house-type="$houseType" data-house-index="$houseIndex">${match.content}</span>""")
            }
            "link" -> {
                @Suppress("UNCHECKED_CAST")
                val data = match.data as Pair<Int, String>
                val idx = data.first
                val linkType = data.second
                result.append("""<span class="chain-link-ref interactive-ref chain-link-$linkType" data-link-index="$idx">[$linkType]</span>""")
            }
        }
        currentPos = match.end
    }
    
    // Add remaining text
    if (currentPos < description.length) {
        result.append("""<span class="desc-text">${htmlEscape(description.substring(currentPos))}</span>""")
    }
    
    // If no interactive elements found, just show plain text
    if (filteredMatches.isEmpty()) {
        span { +description }
    } else {
        // Generate unique ID first
        val containerId = "desc-${kotlin.js.Date().getTime().toLong()}"
        
        div("interactive-description") {
            id = containerId
            unsafe { +result.toString() }
        }
        
        // Use setTimeout to attach listeners after DOM is updated
        kotlinx.browser.window.setTimeout({
            app.attachDescriptionInteractionListeners(containerId, hint)
        }, 50)  // Small delay to ensure DOM is ready
    }
}

/**
 * Set up global event delegation for interactive hint description elements
 * This uses event delegation so we don't need to re-attach listeners on each render
 */
internal fun SudokuApp.setupChainInteractionDelegation() {
    // Mouseover delegation for all interactive elements
    document.addEventListener("mouseover", { event ->
        val target = (event.target as? HTMLElement) ?: return@addEventListener
        val interactiveRef = target.closest(".interactive-ref") as? HTMLElement
        if (interactiveRef == null || interactiveRef.classList.contains("ref-hovered")) {
            return@addEventListener
        }
        
        interactiveRef.classList.add("ref-hovered")
        
        when {
            // Chain cell reference: (3)R5C6
            interactiveRef.classList.contains("chain-cell-ref") -> {
                val cellsAttr = interactiveRef.getAttribute("data-cells") ?: return@addEventListener
                val candidateAttr = interactiveRef.getAttribute("data-candidate") ?: return@addEventListener
                val cells = cellsAttr.split(",").mapNotNull { it.toIntOrNull() }
                val candidate = candidateAttr.toIntOrNull() ?: return@addEventListener
                val hint = currentHintList.getOrNull(selectedHintIndex)
                if (hint != null) {
                    updateChainHighlights(cells, candidate, hint)
                }
            }
            // Simple cell reference: R5C6, R1C8
            interactiveRef.classList.contains("cell-ref") -> {
                val cellsAttr = interactiveRef.getAttribute("data-cells") ?: return@addEventListener
                val cells = cellsAttr.split(",").mapNotNull { it.toIntOrNull() }
                highlightCells(cells)
            }
            // Digit reference: {5, 7, 8} or single digit
            interactiveRef.classList.contains("digit-ref") -> {
                val digitsAttr = interactiveRef.getAttribute("data-digits") ?: return@addEventListener
                val digits = digitsAttr.split(",").mapNotNull { it.toIntOrNull() }
                highlightDigits(digits)
            }
            // House reference: Row 5, Column 7, Box 3
            interactiveRef.classList.contains("house-ref") -> {
                val houseType = interactiveRef.getAttribute("data-house-type") ?: return@addEventListener
                val houseIndex = interactiveRef.getAttribute("data-house-index")?.toIntOrNull() ?: return@addEventListener
                highlightHouse(houseType, houseIndex)
            }
            // Link reference: --[strong]-->
            interactiveRef.classList.contains("chain-link-ref") -> {
                val linkIdx = interactiveRef.getAttribute("data-link-index")?.toIntOrNull() ?: return@addEventListener
                updateLinkHighlight(linkIdx, true)
            }
        }
    })
    
    // Mouseout delegation
    document.addEventListener("mouseout", { event ->
        val target = (event.target as? HTMLElement) ?: return@addEventListener
        if (!target.classList.contains("interactive-ref") || !target.classList.contains("ref-hovered")) {
            return@addEventListener
        }
        
        target.classList.remove("ref-hovered")
        
        when {
            target.classList.contains("chain-cell-ref") -> {
                if (highlightedNodeCell == null) {
                    clearChainHighlights()
                }
            }
            target.classList.contains("cell-ref") -> {
                clearCellHighlights()
            }
            target.classList.contains("digit-ref") -> {
                clearDigitHighlights()
            }
            target.classList.contains("house-ref") -> {
                clearHouseHighlights()
            }
            target.classList.contains("chain-link-ref") -> {
                val linkIdx = target.getAttribute("data-link-index")?.toIntOrNull() ?: return@addEventListener
                if (highlightedLinkIndex == null) {
                    updateLinkHighlight(linkIdx, false)
                }
            }
        }
    })
    
    // Click delegation for toggle behavior
    document.addEventListener("click", { event ->
        val target = (event.target as? HTMLElement) ?: return@addEventListener
        val interactiveRef = target.closest(".interactive-ref") as? HTMLElement ?: return@addEventListener
        
        event.stopPropagation()
        
        when {
            interactiveRef.classList.contains("chain-cell-ref") -> {
                val cellsAttr = interactiveRef.getAttribute("data-cells") ?: return@addEventListener
                val candidateAttr = interactiveRef.getAttribute("data-candidate") ?: return@addEventListener
                val cells = cellsAttr.split(",").mapNotNull { it.toIntOrNull() }
                val candidate = candidateAttr.toIntOrNull() ?: return@addEventListener
                
                if (highlightedNodeCell == cells.firstOrNull() && highlightedNodeCandidate == candidate) {
                    highlightedNodeCell = null
                    highlightedNodeCandidate = null
                    clearChainHighlights()
                } else {
                    highlightedNodeCell = cells.firstOrNull()
                    highlightedNodeCandidate = candidate
                    val hint = currentHintList.getOrNull(selectedHintIndex)
                    if (hint != null) {
                        updateChainHighlights(cells, candidate, hint)
                    }
                }
            }
            interactiveRef.classList.contains("chain-link-ref") -> {
                val linkIdx = interactiveRef.getAttribute("data-link-index")?.toIntOrNull() ?: return@addEventListener
                if (highlightedLinkIndex == linkIdx) {
                    highlightedLinkIndex = null
                    updateLinkHighlight(linkIdx, false)
                } else {
                    highlightedLinkIndex = linkIdx
                    updateLinkHighlight(linkIdx, true)
                }
            }
        }
    })
}

/**
 * Highlight specific cells on hover
 */
private fun SudokuApp.highlightCells(cellIndices: List<Int>) {
    clearCellHighlights()
    val grid = document.querySelector(".sudoku-grid") ?: return
    val rows = grid.querySelectorAll(".sudoku-row")
    
    for (cellIndex in cellIndices) {
        val row = cellIndex / 9
        val col = cellIndex % 9
        val rowElement = rows.item(row) ?: continue
        val cellElement = rowElement.childNodes.item(col) as? HTMLElement ?: continue
        cellElement.classList.add("hover-highlight-cell")
    }
}

/**
 * Clear cell highlights
 */
private fun SudokuApp.clearCellHighlights() {
    document.querySelectorAll(".hover-highlight-cell").asList().forEach { element ->
        (element as? HTMLElement)?.classList?.remove("hover-highlight-cell")
    }
}

/**
 * Highlight digits across the grid
 */
private fun SudokuApp.highlightDigits(digits: List<Int>) {
    clearDigitHighlights()
    val grid = document.querySelector(".sudoku-grid") ?: return
    
    // Highlight all candidates and solved cells with these digits
    for (digit in digits) {
        // Highlight candidates
        grid.querySelectorAll(".candidate").asList().forEach { element ->
            val candidateElement = element as? HTMLElement ?: return@forEach
            val candidateText = candidateElement.textContent?.trim()?.toIntOrNull()
            if (candidateText == digit && !candidateElement.classList.contains("hidden")) {
                candidateElement.classList.add("hover-highlight-digit")
            }
        }
        // Highlight solved cells
        grid.querySelectorAll(".cell-value").asList().forEach { element ->
            val valueElement = element as? HTMLElement ?: return@forEach
            val value = valueElement.textContent?.trim()?.toIntOrNull()
            if (value == digit) {
                valueElement.parentElement?.classList?.add("hover-highlight-digit-cell")
            }
        }
    }
}

/**
 * Clear digit highlights
 */
private fun SudokuApp.clearDigitHighlights() {
    document.querySelectorAll(".hover-highlight-digit").asList().forEach { element ->
        (element as? HTMLElement)?.classList?.remove("hover-highlight-digit")
    }
    document.querySelectorAll(".hover-highlight-digit-cell").asList().forEach { element ->
        (element as? HTMLElement)?.classList?.remove("hover-highlight-digit-cell")
    }
}

/**
 * Highlight a house (row, column, or box)
 */
private fun SudokuApp.highlightHouse(houseType: String, houseIndex: Int) {
    clearHouseHighlights()
    val grid = document.querySelector(".sudoku-grid") ?: return
    val rows = grid.querySelectorAll(".sudoku-row")
    
    val cellIndices = when (houseType) {
        "row" -> (0..8).map { col -> houseIndex * 9 + col }
        "column" -> (0..8).map { row -> row * 9 + houseIndex }
        "box" -> {
            val boxRow = houseIndex / 3
            val boxCol = houseIndex % 3
            val startRow = boxRow * 3
            val startCol = boxCol * 3
            (0..2).flatMap { r -> (0..2).map { c -> (startRow + r) * 9 + (startCol + c) } }
        }
        else -> emptyList()
    }
    
    for (cellIndex in cellIndices) {
        val row = cellIndex / 9
        val col = cellIndex % 9
        val rowElement = rows.item(row) ?: continue
        val cellElement = rowElement.childNodes.item(col) as? HTMLElement ?: continue
        cellElement.classList.add("hover-highlight-house")
    }
}

/**
 * Clear house highlights
 */
private fun SudokuApp.clearHouseHighlights() {
    document.querySelectorAll(".hover-highlight-house").asList().forEach { element ->
        (element as? HTMLElement)?.classList?.remove("hover-highlight-house")
    }
}

/**
 * Attach listeners for interactive description elements (uses global delegation)
 */
private fun SudokuApp.attachDescriptionInteractionListeners(containerId: String, hint: TechniqueMatchInfo) {
    // Event delegation is handled globally in setupChainInteractionDelegation()
    // This function is kept for potential future use
}

/**
 * Legacy alias for attachDescriptionInteractionListeners
 */
private fun SudokuApp.attachChainInteractionListeners(containerId: String, hint: TechniqueMatchInfo) {
    attachDescriptionInteractionListeners(containerId, hint)
}

/**
 * Update visual highlights for cells and candidates
 */
private fun SudokuApp.updateChainHighlights(cells: List<Int>, candidate: Int, hint: TechniqueMatchInfo) {
    // First clear any existing highlights
    clearChainHighlights()
    
    // Highlight the cells - cells are in rows, so we need to find them properly
    cells.forEach { cellIndex ->
        val row = cellIndex / 9
        val col = cellIndex % 9
        // Selector: .sudoku-grid > .sudoku-row:nth-child(row+1) > .cell:nth-child(col+1)
        val cellElement = document.querySelector(
            ".sudoku-grid > .sudoku-row:nth-child(${row + 1}) > .cell:nth-child(${col + 1})"
        ) as? HTMLElement
        cellElement?.classList?.add("chain-node-highlight")
        
        // Highlight the specific candidate within the cell
        val candidateElement = cellElement?.querySelector(".candidate:nth-child($candidate)") as? HTMLElement
        candidateElement?.classList?.add("chain-candidate-highlight")
    }
    
    // Also highlight corresponding SVG circles if they exist
    val svgContainer = document.querySelector(".chain-lines-container svg") as? Element
    svgContainer?.querySelectorAll(".board-candidate-highlight")?.asList()?.forEach { circle ->
        val circleEl = circle as? Element ?: return@forEach
        val cx = circleEl.getAttribute("cx")?.toDoubleOrNull() ?: return@forEach
        val cy = circleEl.getAttribute("cy")?.toDoubleOrNull() ?: return@forEach
        
        // Check if this circle matches any of our cells and candidate
        cells.forEach { cellIndex ->
            val row = cellIndex / 9
            val col = cellIndex % 9
            val candCol = (candidate - 1) % 3
            val candRow = (candidate - 1) / 3
            val expectedCx = col * 100.0 + 20.0 + candCol * 30.0
            val expectedCy = row * 100.0 + 20.0 + candRow * 30.0
            
            if (kotlin.math.abs(cx - expectedCx) < 5 && kotlin.math.abs(cy - expectedCy) < 5) {
                circleEl.classList.add("svg-highlight")
            }
        }
    }
}

/**
 * Clear all chain highlights
 */
private fun SudokuApp.clearChainHighlights() {
    document.querySelectorAll(".chain-node-highlight").asList().forEach {
        (it as? Element)?.classList?.remove("chain-node-highlight")
    }
    document.querySelectorAll(".chain-candidate-highlight").asList().forEach {
        (it as? Element)?.classList?.remove("chain-candidate-highlight")
    }
    document.querySelectorAll(".svg-highlight").asList().forEach {
        (it as? Element)?.classList?.remove("svg-highlight")
    }
    document.querySelectorAll(".svg-line-highlight").asList().forEach {
        (it as? Element)?.classList?.remove("svg-line-highlight")
    }
}

/**
 * Update SVG line highlight
 */
private fun SudokuApp.updateLinkHighlight(linkIndex: Int, highlight: Boolean) {
    val svgContainer = document.querySelector(".chain-lines-container svg") as? Element ?: return
    val lines = svgContainer.querySelectorAll(".board-chain-line").asList()
    
    if (linkIndex < lines.size) {
        val line = lines[linkIndex] as? Element
        if (highlight) {
            line?.classList?.add("svg-line-highlight")
        } else {
            line?.classList?.remove("svg-line-highlight")
        }
    }
}

/**
 * Render full explanation view (replaces hint list in landscape sidebar)
 */
private fun TagConsumer<HTMLElement>.renderExplanationView(app: SudokuApp, hint: TechniqueMatchInfo, totalHints: Int) {
    // Use backend steps if available, otherwise generate fallback
    val steps = if (hint.explanationSteps.isNotEmpty()) {
        hint.explanationSteps
    } else {
        app.generateFallbackExplanationSteps(hint)
    }
    val currentStep = steps.getOrNull(app.explanationStepIndex)
    
    div("explanation-view") {
        // Header with back button
        div("explanation-view-header") {
            button(classes = "explanation-back-btn") {
                +"← Back to List"
                onClickFunction = {
                    app.showExplanation = false
                    app.explanationStepIndex = 0
                    app.render()
                }
            }
            span("hint-position-badge") { +"${app.selectedHintIndex + 1}/$totalHints" }
        }
        
        // Technique info
        div("explanation-technique-info") {
            div("explanation-technique-name") { +hint.techniqueName }
            div("explanation-technique-desc") { +hint.description }
        }
        
        // Eureka notation if available (for chains)
        val eureka = hint.eurekaNotation
        if (eureka != null) {
            div("explanation-eureka") {
                span("eureka-label") { +"Eureka: " }
                span("eureka-notation") { +eureka }
            }
        }
        
        // Step content
        div("explanation-step-content") {
            if (currentStep != null) {
                div("step-header") {
                    span("step-number") { +"Step ${currentStep.stepNumber}" }
                    span("step-title") { +currentStep.title }
                }
                div("step-description") {
                    renderInteractiveDescription(app, currentStep.description, hint)
                }
            } else {
                div("step-description") {
                    renderInteractiveDescription(app, hint.description, hint)
                }
            }
        }
        
        // Navigation (only show if more than one step)
            if (steps.size > 1) {
            div("explanation-nav") {
                    button(classes = "explanation-nav-btn ${if (app.explanationStepIndex <= 0) "disabled" else ""}") {
                    +"◀ Prev"
                    onClickFunction = {
                            if (app.explanationStepIndex > 0) {
                                app.explanationStepIndex--
                                app.render()
                        }
                    }
                }
                    span("step-indicator") { +"Step ${app.explanationStepIndex + 1} / ${steps.size}" }
                    button(classes = "explanation-nav-btn ${if (app.explanationStepIndex >= steps.size - 1) "disabled" else ""}") {
                    +"Next ▶"
                    onClickFunction = {
                            if (app.explanationStepIndex < steps.size - 1) {
                                app.explanationStepIndex++
                                app.render()
                        }
                    }
                }
            }
        }
        
        // Close button at bottom
        button(classes = "hint-close-btn") {
            +"✕ Close Hints"
            onClickFunction = {
                    app.showHints = false
                    app.showExplanation = false
                    app.explanationStepIndex = 0
                    app.render()
            }
        }
    }
}

/**
 * Render inline explanation content (used in both landscape sidebar and portrait card)
 */
private fun TagConsumer<HTMLElement>.renderInlineExplanation(app: SudokuApp, hint: TechniqueMatchInfo) {
    // Use backend steps if available, otherwise generate fallback
    val steps = if (hint.explanationSteps.isNotEmpty()) {
        hint.explanationSteps
    } else {
        app.generateFallbackExplanationSteps(hint)
    }
    val currentStep = steps.getOrNull(app.explanationStepIndex)
    
    div("inline-explanation") {
        // Collapse button
        div("explanation-collapse-row") {
            button(classes = "explanation-collapse-btn") {
                +"▲ Collapse"
                onClickFunction = { e ->
                    e.stopPropagation()
                    app.showExplanation = false
                    app.explanationStepIndex = 0
                    app.render()
                }
            }
        }
        
        // Eureka notation if available (for chains)
        val eureka = hint.eurekaNotation
        if (eureka != null) {
            div("inline-eureka") {
                span("eureka-label") { +"Eureka: " }
                span("eureka-notation") { +eureka }
            }
        }
        
        // Step content
        if (currentStep != null) {
            div("inline-step") {
                div("step-header") {
                    span("step-number") { +"Step ${currentStep.stepNumber}" }
                    span("step-title") { +currentStep.title }
                }
                div("step-description") {
                    renderInteractiveDescription(app, currentStep.description, hint)
                }
            }
        } else {
            // Fallback if no steps at all
            div("inline-step") {
                div("step-description") {
                    renderInteractiveDescription(app, hint.description, hint)
                }
            }
        }
        
        // Navigation (only show if more than one step)
        if (steps.size > 1) {
            div("inline-nav") {
                button(classes = "inline-nav-btn ${if (app.explanationStepIndex <= 0) "disabled" else ""}") {
                    +"◀ Prev"
                    onClickFunction = { e ->
                        e.stopPropagation()
                        if (app.explanationStepIndex > 0) {
                            app.explanationStepIndex--
                            app.render()
                        }
                    }
                }
                span("step-indicator") { +"${app.explanationStepIndex + 1} / ${steps.size}" }
                button(classes = "inline-nav-btn ${if (app.explanationStepIndex >= steps.size - 1) "disabled" else ""}") {
                    +"Next ▶"
                    onClickFunction = { e ->
                        e.stopPropagation()
                        if (app.explanationStepIndex < steps.size - 1) {
                            app.explanationStepIndex++
                            app.render()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Generate fallback explanation steps if backend didn't provide any
 */
private fun SudokuApp.generateFallbackExplanationSteps(hint: TechniqueMatchInfo): List<ExplanationStepDto> {
    val steps = mutableListOf<ExplanationStepDto>()
    
    // Step 1: Overview
    steps.add(ExplanationStepDto(
        stepNumber = 1,
        title = "Overview",
        description = hint.description,
        highlightCells = hint.highlightCells
    ))
    
    // Step 2: Eliminations (if any)
    if (hint.eliminations.isNotEmpty()) {
        val elimDesc = hint.eliminations.joinToString("; ") { elim ->
            val cells = elim.cells.map { "R${it/9 + 1}C${it%9 + 1}" }
            "Remove ${elim.digit} from ${cells.joinToString(", ")}"
        }
        steps.add(ExplanationStepDto(
            stepNumber = 2,
            title = "Eliminations",
            description = elimDesc,
            highlightCells = hint.eliminations.flatMap { it.cells }
        ))
    }
    
    // Step 3: Solutions (if any)
    if (hint.solvedCells.isNotEmpty()) {
        val solvedDesc = hint.solvedCells.joinToString("; ") { solved ->
            "R${solved.cell/9 + 1}C${solved.cell%9 + 1} = ${solved.digit}"
        }
        steps.add(ExplanationStepDto(
            stepNumber = steps.size + 1,
            title = "Solution",
            description = solvedDesc,
            highlightCells = hint.solvedCells.map { it.cell }
        ))
    }
    
    return steps
}


/**
 * Render SVG overlay for chain lines on the main game board
 */
private fun FlowContent.renderChainLinesSvg(app: SudokuApp, 
    hint: TechniqueMatchInfo,
    currentStep: ExplanationStepDto? = null
) {
    // Use step-specific lines/groups if available, otherwise use hint's full data
    val linesToDraw = currentStep?.lines?.takeIf { it.isNotEmpty() } ?: hint.lines
    val groupsToDraw = currentStep?.groups?.takeIf { it.isNotEmpty() } ?: hint.groups
    
    // SVG viewBox is set to match a 9x9 grid where each cell is 100 units
    // This allows us to position lines relative to cell/candidate positions
    div("chain-lines-container") {
        val svgContent = buildString {
            append("""<svg class="chain-lines-overlay" viewBox="0 0 900 900" preserveAspectRatio="xMidYMid meet">""")
            
            // Draw lines first (behind candidate highlights)
            linesToDraw.forEach { line ->
                // Calculate positions
                val fromCellX = line.from.col * 100
                val fromCellY = line.from.row * 100
                val fromCandCol = (line.from.candidate - 1) % 3
                val fromCandRow = (line.from.candidate - 1) / 3
                val fromX = fromCellX + 20 + fromCandCol * 30
                val fromY = fromCellY + 20 + fromCandRow * 30
                
                val toCellX = line.to.col * 100
                val toCellY = line.to.row * 100
                val toCandCol = (line.to.candidate - 1) % 3
                val toCandRow = (line.to.candidate - 1) / 3
                val toX = toCellX + 20 + toCandCol * 30
                val toY = toCellY + 20 + toCandRow * 30
                
                val strokeClass = if (line.isStrongLink) "strong-link" else "weak-link"
                
                val curveXVal = line.curveX
                val curveYVal = line.curveY
                if (curveXVal != null && curveYVal != null) {
                    // Curved line using quadratic bezier
                    val midX = (fromX + toX) / 2 + (curveXVal * 100).toInt()
                    val midY = (fromY + toY) / 2 + (curveYVal * 100).toInt()
                    append("""<path class="board-chain-line $strokeClass" d="M$fromX,$fromY Q$midX,$midY $toX,$toY" />""")
                } else {
                    // Straight line
                    append("""<line class="board-chain-line $strokeClass" x1="$fromX" y1="$fromY" x2="$toX" y2="$toY" />""")
                }
            }
            
            // Draw group highlights on top of lines
            groupsToDraw.forEach { group ->
                val colorClass = when (group.groupType) {
                    "chain-on" -> "group-on"
                    "chain-off" -> "group-off"
                    "als" -> "group-als"
                    else -> "group-default"
                }
                group.candidates.forEach { loc ->
                    // Calculate position within the cell
                    // Each cell is 100x100 units, candidates are in a 3x3 grid within
                    val cellX = loc.col * 100
                    val cellY = loc.row * 100
                    // Candidate position within cell (1-9 maps to 3x3 grid)
                    val candCol = (loc.candidate - 1) % 3
                    val candRow = (loc.candidate - 1) / 3
                    val cx = cellX + 20 + candCol * 30
                    val cy = cellY + 20 + candRow * 30
                    append("""<circle class="board-candidate-highlight $colorClass" cx="$cx" cy="$cy" r="12" />""")
                }
            }
            
            append("</svg>")
        }
        unsafe {
            +svgContent
        }
    }
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

