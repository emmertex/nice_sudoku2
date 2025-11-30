import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.onInputFunction
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import adapter.GameEngine
import adapter.TechniqueMatchInfo
import domain.*

enum class AppScreen {
    GAME,
    PUZZLE_BROWSER,
    IMPORT_EXPORT,
    SETTINGS
}

enum class HighlightMode {
    CELL,        // Cells with matching numbers
    RCB_SELECTED, // Rows, Columns, Boxes of selected cell
    RCB_ALL,     // Rows, Columns, Boxes of all matching numbers
    PENCIL       // Matching pencil marks
}

enum class PlayMode {
    FAST,        // Click number then cell to fill
    ADVANCED     // Click number to highlight, then choose action
}

class SudokuApp {
    private val gameEngine = GameEngine()
    private var selectedCell: Int? = null
    private var isNotesMode = false
    
    // Current game state
    private var currentGame: SavedGameState? = null
    private var solution: String? = null  // Background solved solution for mistake detection
    private var gameStartTime: Long = 0L
    private var pausedTime: Long = 0L
    
    // UI state
    private var currentScreen = AppScreen.GAME
    private var selectedCategory: DifficultyCategory = DifficultyCategory.EASY
    private var toastMessage: String? = null
    private var showSettingsMenu = false
    
    // Highlight and Play mode state (loaded from preferences)
    private var highlightMode = GameStateManager.getHighlightMode()
    private var playMode = GameStateManager.getPlayMode()
    private var selectedNumber1: Int? = null  // Primary selected number (light blue)
    private var selectedNumber2: Int? = null  // Secondary selected number (light red)
    
    // Hint system state
    private var showHints = false  // Whether hint panel is visible
    private var selectedHintIndex: Int = 0  // Currently selected hint in the list
    private var isLandscape = false  // Responsive layout detection
    private var isBackendAvailable = false  // Whether hint system can be used
    
    private val appRoot: Element get() = document.getElementById("app")!!
    
    fun start() {
        // Set up keyboard listener
        document.addEventListener("keydown", { event ->
            val keyEvent = event.asDynamic()
            val key = keyEvent.key as String
            // Don't handle if focus is in an input/textarea
            val target = keyEvent.target
            val tagName = (target?.tagName as? String)?.lowercase() ?: ""
            if (tagName != "input" && tagName != "textarea") {
                val grid = gameEngine.getCurrentGrid()
                handleKeyPress(key, grid)
            }
        })
        
        // Fix for Firefox touch support - convert touchend to click
        document.addEventListener("touchend", { event ->
            val touchEvent = event.asDynamic()
            val target = touchEvent.target as? HTMLElement
            if (target != null) {
                // Check if target or parent is a button or cell
                val clickable = target.closest("button, .cell") as? HTMLElement
                if (clickable != null) {
                    event.preventDefault()
                    clickable.click()
                }
            }
        }, js("{ passive: false }"))
        
        // Set up orientation/aspect ratio detection for responsive hint layout
        val mediaQuery = window.matchMedia("(min-aspect-ratio: 4/3)")
        isLandscape = mediaQuery.matches
        mediaQuery.addEventListener("change", { event ->
            val mql = event.asDynamic()
            isLandscape = mql.matches as Boolean
            if (showHints) render()  // Re-render if hints are showing
        })
        
        // Check backend availability for hint system
        gameEngine.checkBackendAvailable { available ->
            isBackendAvailable = available
            render()  // Re-render to update hint button state
        }
        
        // Set up callback for when hints are ready
        gameEngine.onHintsReady = {
            render()  // Re-render to show hints
        }
        
        // Try to resume last game
        val lastGameId = GameStateManager.getCurrentGameId()
        if (lastGameId != null) {
            val saved = GameStateManager.loadGame(lastGameId)
            if (saved != null && !saved.isCompleted) {
                resumeGame(saved)
                return
            }
        }
        
        // Otherwise start fresh with a random easy puzzle
        val puzzle = PuzzleLibrary.getRandomPuzzle(DifficultyCategory.EASY)
        if (puzzle != null) {
            startNewGame(puzzle)
        }
    }
    
    private fun startNewGame(puzzle: PuzzleDefinition) {
        gameEngine.loadPuzzle(puzzle.puzzleString)
        
        // Start with null solution, will be populated async
        solution = null
        
        // Create new saved game (solution will be updated when available)
        currentGame = GameStateManager.createNewGame(puzzle, null)
        currentGame?.let { 
            GameStateManager.saveGame(it)
            GameStateManager.setCurrentGameId(it.puzzleId)
        }
        
        gameStartTime = currentTimeMillis()
        pausedTime = 0L
        selectedCell = null
        currentScreen = AppScreen.GAME
        render()
        
        // Solve in background for mistake detection
        val solverEngine = GameEngine()
        solverEngine.loadPuzzle(puzzle.puzzleString)
        solverEngine.getSolutionString(
            onStatus = { status -> 
                showToast("⏳ $status")
            },
            onComplete = { solutionStr ->
                if (solutionStr != null) {
                    solution = solutionStr
                    // Update saved game with solution
                    currentGame = currentGame?.copy(solution = solutionStr)
                    currentGame?.let { GameStateManager.saveGame(it) }
                    showToast("✓ Ready for mistake checking")
                    println("Solution loaded: ${solutionStr.take(20)}...")
                } else {
                    showToast("⚠️ Could not verify solution")
                    println("Failed to get solution for puzzle")
                }
            }
        )
    }
    
    private fun resumeGame(saved: SavedGameState) {
        // Parse the state
        val (values, notes) = SavedGameState.parseStateString(saved.currentState)
        
        // Load into engine
        gameEngine.loadPuzzle(saved.puzzleString)
        
        // Apply saved values and notes
        for (i in 0 until 81) {
            val originalValue = saved.puzzleString[i].digitToIntOrNull() ?: 0
            if (values[i] != 0 && values[i] != originalValue) {
                // User-entered value - use setCellValue to properly update engine
                gameEngine.setCellValue(i, values[i])
            }
            if (notes[i].isNotEmpty()) {
                // For notes, we need to toggle each candidate
                gameEngine.toggleCandidate(i, notes[i])
            }
        }
        
        solution = saved.solution
        currentGame = saved
        GameStateManager.setCurrentGameId(saved.puzzleId)
        
        gameStartTime = currentTimeMillis()
        pausedTime = saved.elapsedTimeMs
        selectedCell = null
        currentScreen = AppScreen.GAME
        render()
        
        // If no solution saved, solve in background
        if (saved.solution == null) {
            val solverEngine = GameEngine()
            solverEngine.loadPuzzle(saved.puzzleString)
            solverEngine.getSolutionString(
                onStatus = { status -> showToast("⏳ $status") },
                onComplete = { solutionStr ->
                    if (solutionStr != null) {
                        solution = solutionStr
                        currentGame = currentGame?.copy(solution = solutionStr)
                        currentGame?.let { GameStateManager.saveGame(it) }
                        showToast("✓ Ready for mistake checking")
                    }
                }
            )
        }
    }
    
    private fun saveCurrentState() {
        val game = currentGame ?: return
        val grid = gameEngine.getCurrentGrid()
        val elapsedSinceStart = currentTimeMillis() - gameStartTime
        
        val updated = GameStateManager.updateGameState(
            currentGame = game,
            grid = grid,
            additionalTimeMs = elapsedSinceStart
        )
        currentGame = updated
        GameStateManager.saveGame(updated)
        
        // Reset timer
        gameStartTime = currentTimeMillis()
        pausedTime = updated.elapsedTimeMs
    }
    
    private fun checkMistake(cellIndex: Int, value: Int): Boolean {
        val isMistake = GameStateManager.isMistake(solution, cellIndex, value)
        if (isMistake) {
            currentGame = currentGame?.copy(mistakeCount = (currentGame?.mistakeCount ?: 0) + 1)
            currentGame?.let { GameStateManager.saveGame(it) }
        }
        return isMistake
    }
    
    private fun formatTime(ms: Long): String {
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
    
    private fun showToast(message: String) {
        toastMessage = message
        render()
        window.setTimeout({
            toastMessage = null
            render()
        }, 2000)
    }
    
    // Compute which cells should have primary highlight (light blue)
    private fun getPrimaryHighlightCells(grid: SudokuGrid): Set<Int> {
        val number = selectedNumber1 ?: return emptySet()
        return getHighlightCellsForNumber(grid, number)
    }
    
    // Compute which cells should have secondary highlight (light red)
    private fun getSecondaryHighlightCells(grid: SudokuGrid): Set<Int> {
        val number = selectedNumber2 ?: return emptySet()
        return getHighlightCellsForNumber(grid, number)
    }
    
    private fun getHighlightCellsForNumber(grid: SudokuGrid, number: Int): Set<Int> {
        return when (highlightMode) {
            HighlightMode.CELL -> {
                // Highlight cells with matching solved values
                grid.cells.filter { it.value == number }.map { it.index }.toSet()
            }
            HighlightMode.RCB_SELECTED -> {
                // Highlight row, column, box of selected cell where number is relevant
                val cell = selectedCell ?: return emptySet()
                val selectedCellData = grid.getCell(cell)
                val relatedCells = getRelatedCellIndices(selectedCellData.row, selectedCellData.col, selectedCellData.box)
                relatedCells.filter { idx ->
                    val c = grid.getCell(idx)
                    c.value == number || number in c.candidates
                }.toSet()
            }
            HighlightMode.RCB_ALL -> {
                // For each cell with the number, highlight its row, column, box
                val result = mutableSetOf<Int>()
                grid.cells.filter { it.value == number }.forEach { cell ->
                    result.addAll(getRelatedCellIndices(cell.row, cell.col, cell.box))
                    result.add(cell.index)
                }
                result
            }
            HighlightMode.PENCIL -> {
                // Highlight cells with matching pencil marks
                grid.cells.filter { number in it.candidates }.map { it.index }.toSet()
            }
        }
    }
    
    private fun getRelatedCellIndices(row: Int, col: Int, box: Int): Set<Int> {
        val result = mutableSetOf<Int>()
        // Row cells
        for (c in 0 until 9) result.add(row * 9 + c)
        // Column cells
        for (r in 0 until 9) result.add(r * 9 + col)
        // Box cells
        val boxStartRow = (box / 3) * 3
        val boxStartCol = (box % 3) * 3
        for (r in boxStartRow until boxStartRow + 3) {
            for (c in boxStartCol until boxStartCol + 3) {
                result.add(r * 9 + c)
            }
        }
        return result
    }
    
    private fun handleNumberClick(num: Int, grid: SudokuGrid) {
        when (playMode) {
            PlayMode.FAST -> {
                // Select number for highlighting
                if (selectedNumber1 == num) {
                    // Double click clears selection
                    selectedNumber1 = null
                } else {
                    selectedNumber1 = num
                }
                
                // If cell is selected, apply the number
                selectedCell?.let { cellIndex ->
                    val cell = grid.getCell(cellIndex)
                    if (!cell.isGiven && !cell.isSolved) {
                        if (isNotesMode) {
                            gameEngine.toggleCandidate(cellIndex, num)
                        } else {
                            val wasMistake = checkMistake(cellIndex, num)
                            if (wasMistake) showToast("❌ Wrong number!")
                            gameEngine.setCellValue(cellIndex, num)
                        }
                        saveCurrentState()
                    }
                }
            }
            PlayMode.ADVANCED -> {
                // In advanced mode, clicking numbers only sets highlight
                if (selectedNumber1 == num) {
                    selectedNumber1 = null
                } else if (selectedNumber1 != null && selectedNumber2 == null) {
                    // Set secondary number
                    selectedNumber2 = num
                } else if (selectedNumber2 == num) {
                    selectedNumber2 = null
                } else {
                    selectedNumber1 = num
                    selectedNumber2 = null
                }
            }
        }
        render()
    }
    
    private fun handleCellClick(cellIndex: Int, grid: SudokuGrid) {
        val cell = grid.getCell(cellIndex)
        
        // In advanced mode, clicking a solved cell with different number activates secondary highlight
        if (playMode == PlayMode.ADVANCED && cell.isSolved && 
            selectedNumber1 != null && cell.value != selectedNumber1) {
            selectedNumber2 = cell.value
            selectedCell = cellIndex
            render()
            return
        }
        
        // In FAST mode with a number selected, apply it to the cell
        if (playMode == PlayMode.FAST && selectedNumber1 != null && !cell.isGiven) {
            if (isNotesMode) {
                // Toggle pencil mark
                if (!cell.isSolved) {
                    gameEngine.toggleCandidate(cellIndex, selectedNumber1!!)
                    saveCurrentState()
                    selectedCell = null
                }
                selectedCell = null
            } else if (!cell.isSolved) {
                val wasMistake = checkMistake(cellIndex, selectedNumber1!!)
                if (wasMistake) showToast("❌ Wrong number!")
                gameEngine.setCellValue(cellIndex, selectedNumber1!!)
                saveCurrentState()
                // Auto-deselect cell after setting value in FAST mode
                selectedCell = null
            } else {
                selectedCell = cellIndex
            }
            render()
            return
        }
        
        selectedCell = cellIndex
        render()
    }
    
    private fun handleKeyPress(key: String, grid: SudokuGrid) {
        // Handle number keys 1-9
        val num = key.toIntOrNull()
        if (num != null && num in 1..9) {
            handleNumberClick(num, grid)
            return
        }
        
        // Handle other keys
        when (key.lowercase()) {
            "backspace", "delete" -> {
                selectedCell?.let { cellIndex ->
                    val cell = grid.getCell(cellIndex)
                    if (!cell.isGiven) {
                        gameEngine.setCellValue(cellIndex, null)
                        saveCurrentState()
                        render()
                    }
                }
            }
            "escape" -> {
                selectedNumber1 = null
                selectedNumber2 = null
                selectedCell = null
                render()
            }
            "n" -> {
                isNotesMode = !isNotesMode
                render()
            }
        }
    }
    
    private fun render() {
        appRoot.innerHTML = ""
        
        when (currentScreen) {
            AppScreen.GAME -> renderGameScreen()
            AppScreen.PUZZLE_BROWSER -> renderPuzzleBrowser()
            AppScreen.IMPORT_EXPORT -> renderImportExport()
            AppScreen.SETTINGS -> renderSettings()
        }
    }
    
    private fun renderGameScreen() {
        val grid = gameEngine.getCurrentGrid()
        val game = currentGame
        val isSolved = grid.isComplete && grid.isValid
        
        // Mark as completed if solved
        if (isSolved && game != null && !game.isCompleted) {
            saveCurrentState()
        }
        
        val currentElapsed = pausedTime + (currentTimeMillis() - gameStartTime)
        
        // Compute highlights
        val primaryCells = getPrimaryHighlightCells(grid)
        val secondaryCells = getSecondaryHighlightCells(grid)
        
        appRoot.append {
            div("sudoku-container") {
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
                            small { +"Powered by StormDoku" }
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
                            span("mode-badge play-mode ${if (playMode == PlayMode.FAST) "fast" else "advanced"}") {
                                +if (playMode == PlayMode.FAST) "Fast" else "Adv"
                            }
                            // Selection info inline with mode badges
                            if (selectedNumber1 != null) {
                                span("selected-num primary") { +"$selectedNumber1" }
                            }
                            if (selectedNumber2 != null) {
                                span("selected-num secondary") { +"$selectedNumber2" }
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
                val selectedHint = hints.getOrNull(selectedHintIndex)
                
                div("main-content ${if (showHints && isLandscape) "landscape-hints" else ""}") {
                // Game area
                div("game-area") {
                    // Sudoku grid
                    div("sudoku-grid") {
                        for (row in 0..8) {
                            div("sudoku-row") {
                                for (col in 0..8) {
                                    val cellIndex = row * 9 + col
                                    val cell = grid.getCell(cellIndex)
                                    val isPrimary = cellIndex in primaryCells
                                    val isSecondary = cellIndex in secondaryCells
                                    renderCell(cellIndex, cell, isPrimary, isSecondary, grid, selectedHint)
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
                        
                        button(classes = "erase-btn") {
                            +"⌫"
                            onClickFunction = {
                                selectedCell?.let { cellIndex ->
                                    val cell = grid.getCell(cellIndex)
                                    if (!cell.isGiven) {
                                        gameEngine.setCellValue(cellIndex, null)
                                        saveCurrentState()
                                        render()
                                    }
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
                                        gameEngine.findAllTechniques()
                                    }
                                    render()
                                }
                            }
                        }
                        
                        // Advanced mode action buttons
                        if (playMode == PlayMode.ADVANCED && selectedCell != null) {
                            val cell = grid.getCell(selectedCell!!)
                            
                            // Set buttons for selected numbers
                            if (selectedNumber1 != null && !cell.isGiven && !cell.isSolved) {
                                button(classes = "action-btn set-btn primary") {
                                    +"Set $selectedNumber1"
                                    onClickFunction = {
                                        gameEngine.setCellValue(selectedCell!!, selectedNumber1!!)
                                        saveCurrentState()
                                        render()
                                    }
                                }
                            }
                            if (selectedNumber2 != null && !cell.isGiven && !cell.isSolved) {
                                button(classes = "action-btn set-btn secondary") {
                                    +"Set $selectedNumber2"
                                    onClickFunction = {
                                        gameEngine.setCellValue(selectedCell!!, selectedNumber2!!)
                                        saveCurrentState()
                                        render()
                                    }
                                }
                            }
                            
                            // Clear pencil mark buttons
                            if (selectedNumber1 != null && !cell.isGiven && selectedNumber1 in cell.candidates) {
                                button(classes = "action-btn clr-btn primary") {
                                    +"Clr $selectedNumber1"
                                    onClickFunction = {
                                        gameEngine.toggleCandidate(selectedCell!!, selectedNumber1!!)
                                        saveCurrentState()
                                        render()
                                    }
                                }
                            }
                            if (selectedNumber2 != null && !cell.isGiven && selectedNumber2 in cell.candidates) {
                                button(classes = "action-btn clr-btn secondary") {
                                    +"Clr $selectedNumber2"
                                    onClickFunction = {
                                        gameEngine.toggleCandidate(selectedCell!!, selectedNumber2!!)
                                        saveCurrentState()
                                        render()
                                    }
                                }
                            }
                            
                            // Clear all OTHER pencil marks (keep only highlighted numbers)
                            val keepNumbers = setOfNotNull(selectedNumber1, selectedNumber2)
                            val candidatesToRemove = cell.candidates - keepNumbers
                            if (!cell.isGiven && !cell.isSolved && candidatesToRemove.isNotEmpty()) {
                                button(classes = "action-btn clr-btn other") {
                                    +"Clr ✕"
                                    attributes["title"] = "Clear all pencil marks except ${keepNumbers.joinToString(", ")}"
                                    onClickFunction = {
                                        candidatesToRemove.forEach { candidate ->
                                            gameEngine.toggleCandidate(selectedCell!!, candidate)
                                        }
                                        saveCurrentState()
                                        render()
                                    }
                                }
                            }
                        }
                        
                        // Clear selection button
                        if (selectedNumber1 != null || selectedNumber2 != null) {
                            button(classes = "action-btn clear-btn") {
                                +"✕"
                                onClickFunction = {
                                    selectedNumber1 = null
                                    selectedNumber2 = null
                                    render()
                                }
                            }
                        }
                    }
                    
                    // Number pad with selection state
                    div("number-pad") {
                        for (num in 1..9) {
                            val isPrimaryNum = selectedNumber1 == num
                            val isSecondaryNum = selectedNumber2 == num
                            val numClass = when {
                                isPrimaryNum && isSecondaryNum -> "num-btn both"
                                isPrimaryNum -> "num-btn primary"
                                isSecondaryNum -> "num-btn secondary"
                                else -> "num-btn"
                            }
                            button(classes = numClass) {
                                +"$num"
                                onClickFunction = {
                                    handleNumberClick(num, grid)
                                }
                            }
                        }
                    }
                    
                    // Portrait hint navigation (below number pad)
                    if (showHints && !isLandscape && hints.isNotEmpty()) {
                        renderPortraitHintCard(hints, selectedHint)
                    }
                }
                
                // Landscape hint sidebar (right of game area)
                if (showHints && isLandscape) {
                    renderLandscapeHintSidebar(hints, selectedHint)
                }
                } // Close main-content div
                
                // Toast
                if (toastMessage != null) {
                    div("toast") { +toastMessage!! }
                }
            }
        }
    }
    
    private fun TagConsumer<HTMLElement>.renderLandscapeHintSidebar(
        hints: List<TechniqueMatchInfo>,
        selectedHint: TechniqueMatchInfo?
    ) {
        div("hint-sidebar") {
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
                        val isSelected = index == selectedHintIndex
                        div("hint-item ${if (isSelected) "selected" else ""}") {
                            onClickFunction = {
                                selectedHintIndex = index
                                render()
                            }
                            div("hint-technique") { +hint.techniqueName }
                            div("hint-description") { +hint.description }
                        }
                    }
                }
            }
            
            // Close button
            button(classes = "hint-close-btn") {
                +"✕ Close"
                onClickFunction = {
                    showHints = false
                    render()
                }
            }
        }
    }
    
    private fun TagConsumer<HTMLElement>.renderPortraitHintCard(
        hints: List<TechniqueMatchInfo>,
        selectedHint: TechniqueMatchInfo?
    ) {
        div("hint-card") {
            div("hint-card-header") {
                button(classes = "hint-nav-btn ${if (selectedHintIndex <= 0) "disabled" else ""}") {
                    +"◀ Prev"
                    onClickFunction = {
                        if (selectedHintIndex > 0) {
                            selectedHintIndex--
                            render()
                        }
                    }
                }
                span("hint-position") { +"${selectedHintIndex + 1} / ${hints.size}" }
                button(classes = "hint-nav-btn ${if (selectedHintIndex >= hints.size - 1) "disabled" else ""}") {
                    +"Next ▶"
                    onClickFunction = {
                        if (selectedHintIndex < hints.size - 1) {
                            selectedHintIndex++
                            render()
                        }
                    }
                }
                button(classes = "hint-close-btn-small") {
                    +"✕"
                    onClickFunction = {
                        showHints = false
                        render()
                    }
                }
            }
            
            if (selectedHint != null) {
                div("hint-content") {
                    div("hint-technique") { +selectedHint.techniqueName }
                    div("hint-description") { +selectedHint.description }
                }
            } else {
                div("hint-content hint-empty") {
                    p { +"Searching for hints..." }
                }
            }
        }
    }
    
    private fun TagConsumer<HTMLElement>.renderCell(
        cellIndex: Int, 
        cell: SudokuCell, 
        isPrimaryHighlight: Boolean,
        isSecondaryHighlight: Boolean,
        grid: SudokuGrid,
        selectedHint: TechniqueMatchInfo? = null
    ) {
        val row = cellIndex / 9
        val col = cellIndex % 9
        val isSelected = selectedCell == cellIndex
        
        val boxBorderClasses = buildString {
            if (col % 3 == 0 && col > 0) append(" box-left")
            if (row % 3 == 0 && row > 0) append(" box-top")
        }
        
        // Check if this cell has a mistake (wrong value vs solution)
        val hasMistake = if (cell.isSolved && !cell.isGiven && solution != null) {
            val correctValue = solution!![cellIndex].digitToIntOrNull() ?: 0
            cell.value != correctValue
        } else false
        
        // Build highlight class
        val highlightClass = when {
            isPrimaryHighlight && isSecondaryHighlight -> " highlight-both"
            isPrimaryHighlight -> " highlight-primary"
            isSecondaryHighlight -> " highlight-secondary"
            else -> ""
        }
        
        // Hint highlighting - check if this cell is involved in the selected hint
        val isHintHighlight = selectedHint != null && cellIndex in selectedHint.highlightCells
        val isHintSolved = selectedHint?.solvedCells?.any { it.cell == cellIndex } == true
        val hintClass = when {
            isHintSolved -> " hint-solved-cell"
            isHintHighlight -> " hint-highlight"
            else -> ""
        }
        
        // Get elimination digits for this cell from the selected hint
        val eliminationDigits = selectedHint?.eliminations
            ?.filter { cellIndex in it.cells }
            ?.map { it.digit }
            ?.toSet() ?: emptySet()
        
        // Get solved digit for this cell from the hint
        val hintSolvedDigit = selectedHint?.solvedCells?.find { it.cell == cellIndex }?.digit
        
        val solvedClass = if (cell.isSolved && !cell.isGiven) " solved" else ""
        div("cell${if (isSelected) " selected" else ""}${if (cell.isGiven) " given" else ""}$solvedClass${if (hasMistake) " mistake" else ""}$highlightClass$hintClass$boxBorderClasses") {
            if (cell.isSolved) {
                span("cell-value") { +"${cell.value}" }
            } else if (cell.candidates.isNotEmpty()) {
                div("candidates") {
                    for (n in 1..9) {
                        // Highlight pencil marks that match selected numbers
                        val isPencilHighlight = (n == selectedNumber1 || n == selectedNumber2) && 
                                                highlightMode == HighlightMode.PENCIL
                        // Hint-specific pencil mark highlighting
                        val isElimination = n in eliminationDigits
                        val isSolvedHint = n == hintSolvedDigit
                        
                        val candidateClasses = buildString {
                            append("candidate")
                            if (n !in cell.candidates) append(" hidden")
                            if (isPencilHighlight) append(" pencil-highlight")
                            if (isElimination) append(" hint-elimination")
                            if (isSolvedHint) append(" hint-solved")
                        }
                        span(candidateClasses) {
                            +"$n"
                        }
                    }
                }
            }
            
            onClickFunction = {
                handleCellClick(cellIndex, grid)
            }
        }
    }
    
    private fun renderPuzzleBrowser() {
        val summaries = GameStateManager.getGameSummaries()
        val incompleteSummaries = summaries.filter { !it.isCompleted }
        
        appRoot.append {
            div("sudoku-container browser") {
                div("header") {
                    button(classes = "back-btn") {
                        +"← Back"
                        onClickFunction = {
                            currentScreen = AppScreen.GAME
                            render()
                        }
                    }
                    h1 { +"Puzzle Browser" }
                }
                
                // Resume incomplete games
                if (incompleteSummaries.isNotEmpty()) {
                    div("section") {
                        h2 { +"⏸ Resume Game" }
                        div("game-list") {
                            for (summary in incompleteSummaries.take(5)) {
                                div("game-item") {
                                    span("category ${summary.category.name.lowercase()}") { 
                                        +summary.category.displayName 
                                    }
                                    span("progress") { +"${summary.progressPercent}%" }
                                    span("time") { +formatTime(summary.elapsedTimeMs) }
                                    span("mistakes") { +"❌${summary.mistakeCount}" }
                                    button(classes = "resume-btn") {
                                        +"Resume"
                                        onClickFunction = {
                                            val saved = GameStateManager.loadGame(summary.puzzleId)
                                            if (saved != null) {
                                                resumeGame(saved)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Category selector
                div("section") {
                    h2 { +"🎯 New Puzzle" }
                    div("category-tabs") {
                        for (cat in DifficultyCategory.entries) {
                            button(classes = "tab-btn ${if (selectedCategory == cat) "active" else ""}") {
                                +cat.displayName
                                onClickFunction = {
                                    selectedCategory = cat
                                    render()
                                }
                            }
                        }
                    }
                    
                    // Puzzle list
                    div("puzzle-list") {
                        val puzzles = PuzzleLibrary.puzzles[selectedCategory] ?: emptyList()
                        for ((index, puzzle) in puzzles.withIndex()) {
                            val existingGame = summaries.find { it.puzzleId == puzzle.id }
                            div("puzzle-item ${if (existingGame?.isCompleted == true) "completed" else ""}") {
                                span("puzzle-num") { +"#${index + 1}" }
                                span("difficulty") { +"★ ${puzzle.difficulty}" }
                                if (existingGame != null) {
                                    if (existingGame.isCompleted) {
                                        span("status completed") { +"✓ Completed" }
                                    } else {
                                        span("status progress") { +"${existingGame.progressPercent}%" }
                                    }
                                }
                                button(classes = "play-btn") {
                                    +if (existingGame?.isCompleted == true) "Replay" else "Play"
                                    onClickFunction = {
                                        startNewGame(puzzle)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun renderImportExport() {
        val grid = gameEngine.getCurrentGrid()
        val game = currentGame
        
        // Create export strings
        val puzzleString = game?.puzzleString ?: ""
        val currentValues = grid.cells.joinToString("") { 
            (it.value ?: 0).toString() 
        }
        val stateString = SavedGameState.createStateString(grid)
        
        appRoot.append {
            div("sudoku-container import-export") {
                div("header") {
                    button(classes = "back-btn") {
                        +"← Back"
                        onClickFunction = {
                            currentScreen = AppScreen.GAME
                            render()
                        }
                    }
                    h1 { +"Import / Export" }
                }
                
                // Export section
                div("section") {
                    h2 { +"📤 Export" }
                    
                    div("export-option") {
                        label { +"Original Puzzle (81 chars)" }
                        div("export-row") {
                            input(InputType.text, classes = "export-field") {
                                value = puzzleString
                                readonly = true
                            }
                            button(classes = "copy-btn") {
                                +"Copy"
                                onClickFunction = {
                                    ClipboardUtils.copyToClipboard(puzzleString,
                                        onSuccess = { showToast("✓ Copied puzzle!") },
                                        onError = { showToast("Failed to copy") }
                                    )
                                }
                            }
                        }
                    }
                    
                    div("export-option") {
                        label { +"Current State (81 chars)" }
                        div("export-row") {
                            input(InputType.text, classes = "export-field") {
                                value = currentValues
                                readonly = true
                            }
                            button(classes = "copy-btn") {
                                +"Copy"
                                onClickFunction = {
                                    ClipboardUtils.copyToClipboard(currentValues,
                                        onSuccess = { showToast("✓ Copied state!") },
                                        onError = { showToast("Failed to copy") }
                                    )
                                }
                            }
                        }
                    }
                    
                    div("export-option") {
                        label { +"Full State with Notes (810 chars)" }
                        div("export-row") {
                            input(InputType.text, classes = "export-field") {
                                value = stateString
                                readonly = true
                            }
                            button(classes = "copy-btn") {
                                +"Copy"
                                onClickFunction = {
                                    ClipboardUtils.copyToClipboard(stateString,
                                        onSuccess = { showToast("✓ Copied full state!") },
                                        onError = { showToast("Failed to copy") }
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Import section
                div("section") {
                    h2 { +"📥 Import" }
                    p("hint") { +"Paste an 81-char puzzle or 810-char state string" }
                    
                    textArea(classes = "import-field") {
                        id = "import-input"
                        placeholder = "Paste puzzle string here..."
                    }
                    
                    div("import-actions") {
                        button(classes = "paste-btn") {
                            +"📋 Paste from Clipboard"
                            onClickFunction = {
                                ClipboardUtils.readFromClipboard(
                                    onSuccess = { text ->
                                        val input = document.getElementById("import-input") as? HTMLTextAreaElement
                                        if (input != null) {
                                            input.value = text
                                        }
                                        render()
                                    },
                                    onError = { showToast("Failed to read clipboard") }
                                )
                            }
                        }
                        
                        button(classes = "load-btn") {
                            +"Load Puzzle"
                            onClickFunction = {
                                val input = document.getElementById("import-input") as? HTMLTextAreaElement
                                val text = input?.value?.trim() ?: ""
                                
                                if (PuzzleStringParser.isValidPuzzleString(text)) {
                                    val puzzleStr = text.take(81).map { 
                                        if (it == '.') '0' else it 
                                    }.joinToString("")
                                    
                                    val puzzle = PuzzleDefinition(
                                        id = "imported_${currentTimeMillis()}",
                                        puzzleString = puzzleStr,
                                        difficulty = 0f,
                                        category = DifficultyCategory.MEDIUM
                                    )
                                    startNewGame(puzzle)
                                    showToast("✓ Puzzle loaded!")
                                } else {
                                    showToast("Invalid puzzle string")
                                }
                            }
                        }
                    }
                }
                
                // Toast
                if (toastMessage != null) {
                    div("toast") { +toastMessage!! }
                }
            }
        }
    }
    
    private fun renderSettings() {
        appRoot.append {
            div("sudoku-container settings") {
                div("header") {
                    button(classes = "back-btn") {
                        +"← Back"
                        onClickFunction = {
                            currentScreen = AppScreen.GAME
                            render()
                        }
                    }
                    h1 { +"Settings" }
                }
                
                // Navigation section
                div("section") {
                    h2 { +"📁 Navigation" }
                    div("nav-grid") {
                        button(classes = "settings-nav-btn") {
                            +"📚 Puzzles"
                            onClickFunction = {
                                currentScreen = AppScreen.PUZZLE_BROWSER
                                render()
                            }
                        }
                        button(classes = "settings-nav-btn") {
                            +"📋 Import/Export"
                            onClickFunction = {
                                currentScreen = AppScreen.IMPORT_EXPORT
                                render()
                            }
                        }
                    }
                }
                
                // Highlight Mode section
                div("section") {
                    h2 { +"🎨 Highlight Mode" }
                    p("setting-desc") { +"Choose how numbers are highlighted when selected" }
                    
                    div("mode-options") {
                        for (mode in HighlightMode.entries) {
                            val isActive = highlightMode == mode
                            button(classes = "mode-btn ${if (isActive) "active" else ""}") {
                                +when (mode) {
                                    HighlightMode.CELL -> "Cell"
                                    HighlightMode.RCB_SELECTED -> "RCB Selected"
                                    HighlightMode.RCB_ALL -> "RCB All"
                                    HighlightMode.PENCIL -> "Pencil"
                                }
                                onClickFunction = {
                                    highlightMode = mode
                                    GameStateManager.setHighlightMode(mode)
                                    render()
                                }
                            }
                        }
                    }
                    
                    div("mode-explanation") {
                        +when (highlightMode) {
                            HighlightMode.CELL -> "Highlights cells with matching solved numbers"
                            HighlightMode.RCB_SELECTED -> "Highlights Row, Column, Box of selected cell"
                            HighlightMode.RCB_ALL -> "Highlights all Row/Column/Box containing the number"
                            HighlightMode.PENCIL -> "Highlights cells with matching pencil marks"
                        }
                    }
                }
                
                // Play Mode section
                div("section") {
                    h2 { +"🎮 Play Mode" }
                    p("setting-desc") { +"Choose your input style" }
                    
                    div("mode-options play-modes") {
                        button(classes = "mode-btn fast ${if (playMode == PlayMode.FAST) "active" else ""}") {
                            +"⚡ Fast"
                            onClickFunction = {
                                playMode = PlayMode.FAST
                                GameStateManager.setPlayMode(PlayMode.FAST)
                                render()
                            }
                        }
                        button(classes = "mode-btn advanced ${if (playMode == PlayMode.ADVANCED) "active" else ""}") {
                            +"🎯 Advanced"
                            onClickFunction = {
                                playMode = PlayMode.ADVANCED
                                GameStateManager.setPlayMode(PlayMode.ADVANCED)
                                selectedNumber2 = null // Clear secondary when switching to fast
                                render()
                            }
                        }
                    }
                    
                    div("mode-explanation") {
                        +when (playMode) {
                            PlayMode.FAST -> "Click number, then click cell to fill. Quick and simple."
                            PlayMode.ADVANCED -> "Click numbers to highlight (2 colors). Click solved cells to activate secondary highlight. Use action buttons to fill."
                        }
                    }
                }
                
                // Two-number highlight info
                div("section highlight-info") {
                    h2 { +"🔵🔴 Two-Number Highlighting" }
                    div("color-legend") {
                        div("legend-item") {
                            span("color-box primary") {}
                            span { +"Primary (1st number)" }
                        }
                        div("legend-item") {
                            span("color-box secondary") {}
                            span { +"Secondary (2nd number)" }
                        }
                        div("legend-item") {
                            span("color-box both") {}
                            span { +"Intersection (both)" }
                        }
                    }
                    p("setting-desc") {
                        +"In Advanced mode, select a 2nd number by clicking it, or click a solved cell with a different value."
                    }
                }
            }
        }
    }
}

fun main() {
    window.onload = {
        // Add styles
        val style = document.createElement("style")
        style.textContent = CSS_STYLES
        document.head?.appendChild(style)
        
        // Start the app
        val app = SudokuApp()
        app.start()
    }
}

private val CSS_STYLES = """
    * {
        box-sizing: border-box;
        margin: 0;
        padding: 0;
    }
    
    /* Touch support fixes for Firefox and other browsers */
    html {
        touch-action: manipulation;
        -webkit-tap-highlight-color: transparent;
    }
    
    button, .cell {
        touch-action: manipulation;
        -webkit-tap-highlight-color: transparent;
        user-select: none;
        -webkit-user-select: none;
    }
    
    :root {
        /* Base size calculations - scales with viewport */
        --grid-size: min(90vw, 90vh - 200px, 600px);
        --cell-size: calc(var(--grid-size) / 9.5);
        --font-scale: min(1, var(--grid-size) / 400);
    }
    
    html, body {
        height: 100%;
        overflow: hidden;
    }
    
    body {
        font-family: 'SF Pro Display', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
        background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
        min-height: 100vh;
        min-height: 100dvh; /* Dynamic viewport height for mobile */
        display: flex;
        justify-content: center;
        align-items: center;
        padding: clamp(8px, 2vmin, 20px);
    }
    
    #app {
        width: 100%;
        height: 100%;
        display: flex;
        justify-content: center;
        align-items: center;
    }
    
    .sudoku-container {
        background: rgba(255, 255, 255, 0.05);
        backdrop-filter: blur(10px);
        border-radius: clamp(12px, 3vmin, 24px);
        padding: clamp(12px, 3vmin, 24px);
        box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
        width: min(100%, calc(var(--grid-size) + 48px));
        max-height: 100%;
        display: flex;
        flex-direction: column;
        overflow-y: auto;
    }
    
    .header {
        text-align: center;
        margin-bottom: clamp(8px, 2vmin, 20px);
        flex-shrink: 0;
    }
    
    .nav-row {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: clamp(6px, 1.5vmin, 12px);
    }
    
    .nav-btn, .back-btn {
        padding: clamp(6px, 1.5vmin, 12px) clamp(8px, 2vmin, 16px);
        border: none;
        border-radius: clamp(4px, 1vmin, 8px);
        font-size: clamp(0.65rem, calc(0.6rem + 0.5vmin), 0.9rem);
        font-weight: 600;
        cursor: pointer;
        background: rgba(255, 255, 255, 0.1);
        color: rgba(255, 255, 255, 0.8);
        transition: all 0.15s ease;
    }
    
    .nav-btn:hover, .back-btn:hover {
        background: rgba(255, 255, 255, 0.2);
    }
    
    .header h1 {
        font-size: clamp(1.1rem, calc(1rem + 2vmin), 2rem);
        font-weight: 700;
        color:rgb(69, 170, 233);
        letter-spacing: -0.02em;
    }
    
    .game-info {
        display: flex;
        justify-content: center;
        gap: clamp(6px, 1.5vmin, 12px);
        flex-wrap: wrap;
        margin-top: clamp(4px, 1vmin, 8px);
    }
    
    .game-info span {
        font-size: clamp(0.6rem, calc(0.55rem + 0.5vmin), 0.8rem);
        padding: clamp(2px, 0.5vmin, 4px) clamp(4px, 1vmin, 8px);
        border-radius: 4px;
        background: rgba(255, 255, 255, 0.1);
        color: rgba(255, 255, 255, 0.7);
    }
    
    .category {
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.05em;
    }
    
    .category.easy { background: rgba(76, 175, 80, 0.3); color: #81c784; }
    .category.medium { background: rgba(255, 193, 7, 0.3); color: #ffd54f; }
    .category.hard { background: rgba(255, 152, 0, 0.3); color: #ffb74d; }
    .category.diabolical { background: rgba(233, 69, 96, 0.3); color: #e94560; }
    
    .game-area {
        display: flex;
        flex-direction: column;
        gap: clamp(8px, 2vmin, 16px);
        flex: 1;
        min-height: 0;
    }
    
    .sudoku-grid {
        background: rgba(255, 255, 255, 0.1);
        border-radius: clamp(6px, 1.5vmin, 12px);
        padding: clamp(3px, 0.75vmin, 6px);
        display: flex;
        flex-direction: column;
        gap: clamp(1px, 0.25vmin, 2px);
        width: var(--grid-size);
        max-width: 100%;
        margin: 0 auto;
        aspect-ratio: 1;
    }
    
    .sudoku-row {
        display: flex;
        gap: clamp(1px, 0.25vmin, 2px);
        flex: 1;
    }
    
    .cell {
        aspect-ratio: 1;
        flex: 1;
        background: rgba(255, 255, 255, 0.08);
        border-radius: clamp(2px, 0.5vmin, 4px);
        display: flex;
        justify-content: center;
        align-items: center;
        cursor: pointer;
        transition: all 0.15s ease;
        position: relative;
    }
    
    .cell:hover { background: rgba(255, 255, 255, 0.15); }
    .cell.selected { background: rgba(233, 69, 96, 0.3); box-shadow: inset 0 0 0 2px #e94560; }
    .cell.given { background: rgba(255, 255, 255, 0.12); }
    .cell.solved { background: rgba(255, 255, 255, 0.08); }
    .cell.mistake { background: rgba(255, 82, 82, 0.3); }
    .cell.box-left { margin-left: clamp(2px, 0.5vmin, 4px); }
    .cell.box-top { margin-top: clamp(2px, 0.5vmin, 4px); }
    
    .cell-value {
        font-size: clamp(0.9rem, calc(var(--grid-size) / 18), 2rem);
        font-weight: 600;
        color: #fff;
    }
    
    .cell.given .cell-value { color: rgba(255, 255, 255, 0.95); }
    .cell.solved .cell-value { color: rgba(255, 255, 255, 0.85); }
    .cell:not(.given):not(.solved) .cell-value { color: #e94560; }
    .cell.mistake .cell-value { color: #ff5252; }
    
    .candidates {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        width: 100%;
        height: 100%;
        padding: clamp(1px, 0.25vmin, 2px);
    }
    
    .candidate {
        font-size: clamp(0.35rem, calc(var(--grid-size) / 60), 0.6rem);
        color: rgba(255, 255, 255, 0.5);
        display: flex;
        justify-content: center;
        align-items: center;
    }
    
    .candidate.hidden { visibility: hidden; }
    
    .controls {
        display: flex;
        gap: clamp(4px, 1vmin, 8px);
        justify-content: center;
        flex-wrap: wrap;
        flex-shrink: 0;
    }
    
    .toggle-btn, .erase-btn, .hint-btn, .solve-btn {
        padding: clamp(6px, 1.5vmin, 12px) clamp(10px, 2.5vmin, 20px);
        border: none;
        border-radius: clamp(6px, 1.5vmin, 12px);
        font-size: clamp(0.65rem, calc(0.6rem + 0.5vmin), 0.9rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
        background: rgba(255, 255, 255, 0.1);
        color: rgba(255, 255, 255, 0.8);
    }
    
    .toggle-btn:hover, .erase-btn:hover, .hint-btn:hover, .solve-btn:hover {
        background: rgba(255, 255, 255, 0.2);
        transform: translateY(-1px);
    }
    
    .toggle-btn.active { background: #e94560; color: white; }
    .erase-btn { background: rgba(255, 82, 82, 0.2); color: #ff5252; }
    .hint-btn { background: rgba(255, 193, 7, 0.2); color: #ffc107; }
    .hint-btn.active { background: #ffc107; color: #1a1a2e; }
    .hint-btn.disabled { 
        opacity: 0.4; 
        cursor: not-allowed; 
        pointer-events: none;
    }
    
    /* Hint System Styles */
    .main-content {
        display: flex;
        flex-direction: column;
        flex: 1;
        min-height: 0;
    }
    
    .main-content.landscape-hints {
        flex-direction: row;
        gap: clamp(10px, 2vmin, 20px);
    }
    
    .main-content.landscape-hints .game-area {
        flex: 0 0 auto;
        max-width: 60%;
    }
    
    /* Landscape Hint Sidebar */
    .hint-sidebar {
        flex: 1;
        min-width: 200px;
        max-width: 320px;
        background: rgba(255, 255, 255, 0.05);
        border-radius: clamp(8px, 2vmin, 16px);
        padding: clamp(10px, 2vmin, 20px);
        display: flex;
        flex-direction: column;
        overflow: hidden;
    }
    
    .hint-sidebar-header {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: clamp(8px, 1.5vmin, 16px);
        flex-shrink: 0;
    }
    
    .hint-sidebar-header h3 {
        margin: 0;
        font-size: clamp(0.9rem, calc(0.8rem + 0.5vmin), 1.1rem);
        color: #ffc107;
    }
    
    .hint-count {
        color: rgba(255, 255, 255, 0.5);
        font-size: clamp(0.7rem, calc(0.65rem + 0.3vmin), 0.9rem);
    }
    
    .hint-list {
        flex: 1;
        overflow-y: auto;
        display: flex;
        flex-direction: column;
        gap: clamp(6px, 1vmin, 12px);
    }
    
    .hint-item {
        background: rgba(255, 255, 255, 0.08);
        border-radius: clamp(6px, 1vmin, 10px);
        padding: clamp(8px, 1.5vmin, 14px);
        cursor: pointer;
        transition: all 0.15s ease;
        border: 2px solid transparent;
    }
    
    .hint-item:hover {
        background: rgba(255, 255, 255, 0.12);
    }
    
    .hint-item.selected {
        background: rgba(255, 193, 7, 0.15);
        border-color: #ffc107;
    }
    
    .hint-technique {
        font-weight: 600;
        color: #4ecdc4;
        font-size: clamp(0.75rem, calc(0.7rem + 0.4vmin), 0.95rem);
        margin-bottom: 4px;
    }
    
    .hint-description {
        color: rgba(255, 255, 255, 0.7);
        font-size: clamp(0.65rem, calc(0.6rem + 0.3vmin), 0.85rem);
        line-height: 1.3;
        word-break: break-word;
    }
    
    .hint-empty {
        text-align: center;
        color: rgba(255, 255, 255, 0.5);
        padding: clamp(16px, 3vmin, 32px);
    }
    
    .hint-close-btn {
        margin-top: clamp(8px, 1.5vmin, 16px);
        padding: clamp(8px, 1.5vmin, 14px);
        border: none;
        border-radius: clamp(6px, 1vmin, 10px);
        background: rgba(255, 82, 82, 0.2);
        color: #ff5252;
        font-size: clamp(0.7rem, calc(0.65rem + 0.4vmin), 0.9rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
        flex-shrink: 0;
    }
    
    .hint-close-btn:hover {
        background: rgba(255, 82, 82, 0.3);
    }
    
    /* Portrait Hint Card */
    .hint-card {
        background: rgba(255, 255, 255, 0.05);
        border-radius: clamp(8px, 2vmin, 16px);
        padding: clamp(10px, 2vmin, 20px);
        margin-top: clamp(8px, 1.5vmin, 16px);
    }
    
    .hint-card-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: clamp(6px, 1vmin, 12px);
        margin-bottom: clamp(8px, 1.5vmin, 16px);
    }
    
    .hint-nav-btn {
        padding: clamp(6px, 1vmin, 10px) clamp(10px, 2vmin, 16px);
        border: none;
        border-radius: clamp(4px, 0.8vmin, 8px);
        background: rgba(255, 193, 7, 0.2);
        color: #ffc107;
        font-size: clamp(0.65rem, calc(0.6rem + 0.35vmin), 0.85rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
    }
    
    .hint-nav-btn:hover:not(.disabled) {
        background: rgba(255, 193, 7, 0.3);
    }
    
    .hint-nav-btn.disabled {
        opacity: 0.4;
        cursor: not-allowed;
    }
    
    .hint-position {
        color: rgba(255, 255, 255, 0.6);
        font-size: clamp(0.7rem, calc(0.65rem + 0.3vmin), 0.9rem);
    }
    
    .hint-close-btn-small {
        padding: clamp(4px, 0.8vmin, 8px) clamp(8px, 1.5vmin, 14px);
        border: none;
        border-radius: clamp(4px, 0.8vmin, 8px);
        background: rgba(255, 82, 82, 0.2);
        color: #ff5252;
        font-size: clamp(0.7rem, calc(0.65rem + 0.4vmin), 0.9rem);
        font-weight: bold;
        cursor: pointer;
        transition: all 0.15s ease;
    }
    
    .hint-close-btn-small:hover {
        background: rgba(255, 82, 82, 0.3);
    }
    
    .hint-content {
        padding: clamp(8px, 1.5vmin, 16px);
        background: rgba(255, 255, 255, 0.03);
        border-radius: clamp(6px, 1vmin, 10px);
    }
    
    .hint-content .hint-technique {
        margin-bottom: clamp(6px, 1vmin, 12px);
    }
    
    .hint-content.hint-empty {
        text-align: center;
        color: rgba(255, 255, 255, 0.5);
    }
    
    .number-pad {
        display: grid;
        grid-template-columns: repeat(9, 1fr);
        gap: clamp(3px, 0.75vmin, 6px);
        flex-shrink: 0;
    }
    
    .num-btn {
        aspect-ratio: 1;
        border: none;
        border-radius: clamp(6px, 1.5vmin, 12px);
        font-size: clamp(0.9rem, calc(0.8rem + 1vmin), 1.5rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
        background: rgba(255, 255, 255, 0.1);
        color: white;
    }
    
    .num-btn:hover { background: rgba(233, 69, 96, 0.4); transform: scale(1.05); }
    .num-btn:active { transform: scale(0.95); }
    
    .status {
        text-align: center;
        padding: clamp(6px, 1.5vmin, 12px);
        color: rgba(255, 255, 255, 0.6);
        font-size: clamp(0.7rem, calc(0.65rem + 0.4vmin), 0.9rem);
        flex-shrink: 0;
    }
    
    .toast {
        position: fixed;
        bottom: clamp(60px, 15vh, 120px);
        left: 50%;
        transform: translateX(-50%);
        background: rgba(0, 0, 0, 0.8);
        color: white;
        padding: clamp(8px, 2vmin, 16px) clamp(16px, 4vmin, 32px);
        border-radius: clamp(4px, 1vmin, 8px);
        font-size: clamp(0.75rem, calc(0.7rem + 0.5vmin), 1rem);
        animation: fadeIn 0.2s ease;
        z-index: 100;
    }
    
    @keyframes fadeIn {
        from { opacity: 0; transform: translateX(-50%) translateY(10px); }
        to { opacity: 1; transform: translateX(-50%) translateY(0); }
    }
    
    /* Browser styles */
    .browser, .import-export, .settings {
        max-height: 100%;
        overflow-y: auto;
    }
    
    .browser .section, .import-export .section {
        background: rgba(255, 255, 255, 0.05);
        border-radius: clamp(6px, 1.5vmin, 12px);
        padding: clamp(10px, 2.5vmin, 20px);
        margin-bottom: clamp(10px, 2.5vmin, 20px);
    }
    
    .section h2 {
        color: #e94560;
        font-size: clamp(0.85rem, calc(0.8rem + 0.5vmin), 1.1rem);
        margin-bottom: clamp(8px, 2vmin, 16px);
    }
    
    .category-tabs {
        display: flex;
        gap: clamp(4px, 1vmin, 8px);
        margin-bottom: clamp(8px, 2vmin, 16px);
        flex-wrap: wrap;
    }
    
    .tab-btn {
        padding: clamp(6px, 1.5vmin, 12px) clamp(10px, 2.5vmin, 20px);
        border: none;
        border-radius: clamp(4px, 1vmin, 8px);
        font-size: clamp(0.65rem, calc(0.6rem + 0.5vmin), 0.9rem);
        font-weight: 600;
        cursor: pointer;
        background: rgba(255, 255, 255, 0.1);
        color: rgba(255, 255, 255, 0.7);
        transition: all 0.15s ease;
    }
    
    .tab-btn:hover { background: rgba(255, 255, 255, 0.2); }
    .tab-btn.active { background: #e94560; color: white; }
    
    .puzzle-list, .game-list {
        display: flex;
        flex-direction: column;
        gap: 8px;
        max-height: 300px;
        overflow-y: auto;
    }
    
    .puzzle-item, .game-item {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 10px 12px;
        background: rgba(255, 255, 255, 0.05);
        border-radius: 8px;
        transition: all 0.15s ease;
    }
    
    .puzzle-item:hover, .game-item:hover {
        background: rgba(255, 255, 255, 0.1);
    }
    
    .puzzle-item.completed { opacity: 0.6; }
    
    .puzzle-num {
        color: rgba(255, 255, 255, 0.5);
        font-size: 0.8rem;
        width: 30px;
    }
    
    .difficulty {
        color: #ffc107;
        font-size: 0.8rem;
    }
    
    .status {
        font-size: 0.75rem;
        padding: 2px 6px;
        border-radius: 4px;
    }
    
    .status.completed { background: rgba(76, 175, 80, 0.3); color: #81c784; }
    .status.progress { background: rgba(255, 193, 7, 0.3); color: #ffd54f; }
    
    .play-btn, .resume-btn {
        margin-left: auto;
        padding: 6px 12px;
        border: none;
        border-radius: 6px;
        font-size: 0.75rem;
        font-weight: 600;
        cursor: pointer;
        background: #e94560;
        color: white;
        transition: all 0.15s ease;
    }
    
    .play-btn:hover, .resume-btn:hover {
        background: #c73e54;
        transform: translateY(-1px);
    }
    
    /* Import/Export styles */
    .export-option {
        margin-bottom: 12px;
    }
    
    .export-option label {
        display: block;
        color: rgba(255, 255, 255, 0.7);
        font-size: 0.8rem;
        margin-bottom: 4px;
    }
    
    .export-row {
        display: flex;
        gap: 8px;
    }
    
    .export-field {
        flex: 1;
        padding: 8px 12px;
        border: 1px solid rgba(255, 255, 255, 0.2);
        border-radius: 6px;
        background: rgba(0, 0, 0, 0.3);
        color: rgba(255, 255, 255, 0.8);
        font-family: monospace;
        font-size: 0.75rem;
    }
    
    .copy-btn, .paste-btn, .load-btn {
        padding: 8px 12px;
        border: none;
        border-radius: 6px;
        font-size: 0.75rem;
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
    }
    
    .copy-btn {
        background: rgba(255, 255, 255, 0.1);
        color: rgba(255, 255, 255, 0.8);
    }
    
    .copy-btn:hover { background: rgba(255, 255, 255, 0.2); }
    
    .hint {
        color: rgba(255, 255, 255, 0.5);
        font-size: 0.75rem;
        margin-bottom: 8px;
    }
    
    .import-field {
        width: 100%;
        height: 80px;
        padding: 12px;
        border: 1px solid rgba(255, 255, 255, 0.2);
        border-radius: 8px;
        background: rgba(0, 0, 0, 0.3);
        color: rgba(255, 255, 255, 0.8);
        font-family: monospace;
        font-size: 0.8rem;
        resize: none;
        margin-bottom: 12px;
    }
    
    .import-actions {
        display: flex;
        gap: 8px;
    }
    
    .paste-btn {
        background: rgba(255, 193, 7, 0.2);
        color: #ffc107;
    }
    
    .load-btn {
        background: #e94560;
        color: white;
    }
    
    .load-btn:hover { background: #c73e54; }
    
    /* Highlight styles */
    .cell.highlight-primary { background: rgba(100, 181, 246, 0.4); } /* Light blue */
    .cell.highlight-secondary { background: rgba(239, 154, 154, 0.4); } /* Light red */
    .cell.highlight-both { background: rgba(206, 147, 216, 0.4); } /* Light purple */
    
    .cell.highlight-primary:hover { background: rgba(100, 181, 246, 0.5); }
    .cell.highlight-secondary:hover { background: rgba(239, 154, 154, 0.5); }
    .cell.highlight-both:hover { background: rgba(206, 147, 216, 0.5); }
    
    /* Hint cell highlighting */
    .cell.hint-highlight { 
        background: rgba(129, 199, 132, 0.35); /* Light green */
        box-shadow: inset 0 0 0 2px rgba(76, 175, 80, 0.5);
    }
    .cell.hint-solved-cell { 
        background: rgba(76, 175, 80, 0.45); /* Stronger green for solution cell */
        box-shadow: inset 0 0 0 2px rgba(76, 175, 80, 0.8);
    }
    .cell.hint-highlight:hover { background: rgba(129, 199, 132, 0.45); }
    .cell.hint-solved-cell:hover { background: rgba(76, 175, 80, 0.55); }
    
    /* Number button selection states */
    .num-btn.primary { 
        background: rgba(100, 181, 246, 0.5); 
        box-shadow: inset 0 0 0 2px #64b5f6;
    }
    .num-btn.secondary { 
        background: rgba(239, 154, 154, 0.5); 
        box-shadow: inset 0 0 0 2px #ef9a9a;
    }
    .num-btn.both { 
        background: rgba(206, 147, 216, 0.5); 
        box-shadow: inset 0 0 0 2px #ce93d8;
    }
    
    /* Pencil mark highlighting */
    .candidate.pencil-highlight {
        color: #64b5f6;
        font-weight: bold;
        background: rgba(100, 181, 246, 0.3);
        border-radius: 2px;
    }
    
    /* Hint candidate highlighting */
    .candidate.hint-elimination {
        color: #ef5350;
        text-decoration: line-through;
        font-weight: bold;
        background: rgba(244, 67, 54, 0.25);
        border-radius: 2px;
    }
    
    .candidate.hint-solved {
        color: #66bb6a;
        font-weight: bold;
        background: rgba(76, 175, 80, 0.35);
        border-radius: 2px;
        box-shadow: 0 0 0 1px rgba(76, 175, 80, 0.5);
    }
    
    /* Mode indicators in header */
    .mode-indicators {
        display: flex;
        gap: 6px;
    }
    
    .mode-badge {
        font-size: clamp(0.55rem, calc(0.5rem + 0.4vmin), 0.7rem);
        padding: clamp(2px, 0.5vmin, 4px) clamp(4px, 1vmin, 8px);
        border-radius: clamp(2px, 0.5vmin, 4px);
        background: rgba(255, 255, 255, 0.15);
        color: rgba(255, 255, 255, 0.7);
        font-weight: 600;
    }
    
    .mode-badge.highlight-mode { background: rgba(100, 181, 246, 0.3); color: #90caf9; }
    .mode-badge.play-mode.fast { background: rgba(76, 175, 80, 0.3); color: #81c784; }
    .mode-badge.play-mode.advanced { background: rgba(255, 152, 0, 0.3); color: #ffb74d; }
    
    /* Selected number badges - inline with mode indicators */
    .selected-num {
        font-size: clamp(0.55rem, calc(0.5rem + 0.4vmin), 0.7rem);
        font-weight: 700;
        padding: clamp(2px, 0.5vmin, 4px) clamp(4px, 1vmin, 8px);
        border-radius: clamp(2px, 0.5vmin, 4px);
        line-height: 1.2;
    }
    
    .selected-num.primary { background: rgba(100, 181, 246, 0.4); color: #64b5f6; }
    .selected-num.secondary { background: rgba(239, 154, 154, 0.4); color: #ef9a9a; }
    
    /* Advanced mode action buttons - compact */
    .advanced-actions {
        display: flex;
        gap: clamp(4px, 1vmin, 8px);
        justify-content: center;
        flex-shrink: 0;
    }
    
    .action-btn {
        padding: clamp(4px, 1vmin, 8px) clamp(8px, 2vmin, 14px);
        border: none;
        border-radius: clamp(4px, 1vmin, 8px);
        font-size: clamp(0.6rem, calc(0.55rem + 0.4vmin), 0.8rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
    }
    
    /* Set buttons - primary (blue) and secondary (red) */
    .action-btn.set-btn.primary {
        background: rgba(100, 181, 246, 0.3);
        color: #64b5f6;
    }
    .action-btn.set-btn.primary:hover {
        background: rgba(100, 181, 246, 0.5);
    }
    
    .action-btn.set-btn.secondary {
        background: rgba(239, 154, 154, 0.3);
        color: #ef9a9a;
    }
    .action-btn.set-btn.secondary:hover {
        background: rgba(239, 154, 154, 0.5);
    }
    
    /* Clear pencil mark buttons - primary (blue) and secondary (red) */
    .action-btn.clr-btn.primary {
        background: rgba(100, 181, 246, 0.15);
        color: #90caf9;
        border: 1px solid rgba(100, 181, 246, 0.3);
    }
    .action-btn.clr-btn.primary:hover {
        background: rgba(100, 181, 246, 0.3);
    }
    
    .action-btn.clr-btn.secondary {
        background: rgba(239, 154, 154, 0.15);
        color: #ef9a9a;
        border: 1px solid rgba(239, 154, 154, 0.3);
    }
    .action-btn.clr-btn.secondary:hover {
        background: rgba(239, 154, 154, 0.3);
    }
    
    .action-btn.clr-btn.other {
        background: rgba(255, 193, 7, 0.15);
        color: #ffc107;
        border: 1px solid rgba(255, 193, 7, 0.3);
    }
    .action-btn.clr-btn.other:hover {
        background: rgba(255, 193, 7, 0.3);
    }
    
    /* Clear selection button (X) */
    .action-btn.clear-btn {
        background: rgba(255, 255, 255, 0.1);
        color: rgba(255, 255, 255, 0.7);
    }
    .action-btn.clear-btn:hover {
        background: rgba(255, 255, 255, 0.2);
    }
    
    /* Settings screen styles */
    .settings .section {
        background: rgba(255, 255, 255, 0.05);
        border-radius: 12px;
        padding: 16px;
        margin-bottom: 16px;
    }
    
    .settings .section h2 {
        color: #e94560;
        font-size: 1rem;
        margin-bottom: 8px;
    }
    
    .setting-desc {
        color: rgba(255, 255, 255, 0.5);
        font-size: 0.75rem;
        margin-bottom: 12px;
    }
    
    .nav-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: clamp(6px, 1.5vmin, 12px);
    }
    
    .settings-nav-btn {
        padding: clamp(10px, 2.5vmin, 20px);
        border: none;
        border-radius: clamp(6px, 1.5vmin, 12px);
        font-size: clamp(0.75rem, calc(0.7rem + 0.5vmin), 1rem);
        font-weight: 600;
        cursor: pointer;
        background: rgba(233, 69, 96, 0.2);
        color: #e94560;
        transition: all 0.15s ease;
    }
    
    .settings-nav-btn:hover {
        background: rgba(233, 69, 96, 0.4);
        transform: translateY(-2px);
    }
    
    .mode-options {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: clamp(4px, 1vmin, 8px);
        margin-bottom: clamp(8px, 2vmin, 16px);
    }
    
    .mode-btn {
        padding: clamp(8px, 2vmin, 16px) clamp(10px, 2.5vmin, 20px);
        border: none;
        border-radius: clamp(4px, 1vmin, 8px);
        font-size: 0.8rem;
        font-weight: 600;
        cursor: pointer;
        background: rgba(255, 255, 255, 0.1);
        color: rgba(255, 255, 255, 0.7);
        transition: all 0.15s ease;
    }
    
    .mode-btn:hover {
        background: rgba(255, 255, 255, 0.2);
    }
    
    .mode-btn.active {
        background: rgba(100, 181, 246, 0.4);
        color: #90caf9;
        box-shadow: inset 0 0 0 2px #64b5f6;
    }
    
    .play-modes .mode-btn.fast.active {
        background: rgba(76, 175, 80, 0.4);
        color: #81c784;
        box-shadow: inset 0 0 0 2px #4caf50;
    }
    
    .play-modes .mode-btn.advanced.active {
        background: rgba(255, 152, 0, 0.4);
        color: #ffb74d;
        box-shadow: inset 0 0 0 2px #ff9800;
    }
    
    .mode-explanation {
        background: rgba(0, 0, 0, 0.2);
        border-radius: 6px;
        padding: 10px 12px;
        font-size: 0.75rem;
        color: rgba(255, 255, 255, 0.6);
        line-height: 1.4;
    }
    
    .highlight-info .color-legend {
        display: flex;
        flex-direction: column;
        gap: 8px;
        margin-bottom: 12px;
    }
    
    .legend-item {
        display: flex;
        align-items: center;
        gap: 10px;
        font-size: 0.8rem;
        color: rgba(255, 255, 255, 0.7);
    }
    
    .color-box {
        width: 24px;
        height: 24px;
        border-radius: 4px;
    }
    
    .color-box.primary { background: rgba(100, 181, 246, 0.5); }
    .color-box.secondary { background: rgba(239, 154, 154, 0.5); }
    .color-box.both { background: rgba(206, 147, 216, 0.5); }
    
    /* Responsive adjustments - with clamp() above, these are mostly for edge cases */
    @media (max-width: 400px) {
        :root {
            --grid-size: min(95vw, 85vh - 180px);
        }
        .mode-indicators { display: none; }
        .controls { gap: 4px; }
        .toggle-btn, .erase-btn, .hint-btn { padding: 6px 10px; }
    }
    
    @media (max-height: 600px) {
        :root {
            --grid-size: min(90vw, 75vh - 120px);
        }
        .game-info { margin-top: 4px; }
        .game-area { gap: 8px; }
    }
    
    /* Landscape orientation on mobile */
    @media (max-height: 500px) and (orientation: landscape) {
        :root {
            --grid-size: min(50vw, 70vh);
        }
        .sudoku-container {
            flex-direction: row;
            flex-wrap: wrap;
            max-width: 100%;
            width: auto;
        }
        .header { width: 100%; }
        .sudoku-grid { margin: 0; }
    }
    
    /* Large screens */
    @media (min-width: 1200px) and (min-height: 900px) {
        :root {
            --grid-size: min(70vh, 700px);
        }
    }
""".trimIndent()
