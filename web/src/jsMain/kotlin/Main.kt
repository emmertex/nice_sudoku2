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
import org.w3c.dom.asList
import org.w3c.fetch.Response
import kotlin.js.Promise
import adapter.GameEngine
import adapter.TechniqueMatchInfo
import adapter.LineDto
import adapter.GroupDto
import adapter.ExplanationStepDto
import adapter.CandidateLocationDto
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

enum class Theme {
    DARK,        // Pure dark theme
    BLUE,        // Current blue gradient theme
    LIGHT,       // Light theme
    EPAPER       // High contrast ePaper theme
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
    private var currentTheme = GameStateManager.getTheme()
    private var selectedNumbers1: MutableSet<Int> = mutableSetOf()  // Primary selected numbers (light blue)
    private var selectedNumbers2: MutableSet<Int> = mutableSetOf()  // Secondary selected numbers (light red)
    
    // Hint system state
    private var showHints = false  // Whether hint panel is visible
    private var selectedHintIndex: Int = 0  // Currently selected hint in the list
    private var isLandscape = false  // Responsive layout detection
    private var isBackendAvailable = false  // Whether hint system can be used
    
    // Explanation overlay state
    private var showExplanation = false  // Whether explanation overlay is visible
    private var explanationStepIndex: Int = 0  // Current step in explanation
    
    // Interactive chain highlighting state
    private var highlightedLinkIndex: Int? = null  // Index of link being highlighted (for SVG line)
    private var highlightedNodeCell: Int? = null  // Cell index being highlighted from notation
    private var highlightedNodeCandidate: Int? = null  // Candidate being highlighted from notation
    
    // Cached hint list for event delegation
    private var currentHintList: List<TechniqueMatchInfo> = emptyList()
    
    // Modal state
    private var showAboutModal = false
    private var showHelpModal = false
    private var showGreetingModal = false
    private var showCompletionModal = false
    private var completionShownForPuzzle: String? = null  // Track which puzzle we've shown completion for
    private var showVersionModal = false
    
    // Version info (loaded from CHANGELOG.md)
    private var currentVersion: String = ""
    private var changelogContent: String = ""
    
    // Puzzle browser state
    private var hideCompletedPuzzles = GameStateManager.getHideCompleted()
    
    private val appRoot: Element get() = document.getElementById("app")!!
    
    fun start() {
        // Apply the current theme
        applyTheme(currentTheme)

        // Set up keyboard listener
        document.addEventListener("keydown", { event ->
            val keyEvent = event.asDynamic()
            val key = keyEvent.key as String
            val ctrlKey = (keyEvent.ctrlKey as? Boolean) ?: false
            val shiftKey = (keyEvent.shiftKey as? Boolean) ?: false
            val altKey = (keyEvent.altKey as? Boolean) ?: false
            val metaKey = (keyEvent.metaKey as? Boolean) ?: false
            
            // Don't handle if focus is in an input/textarea
            val target = keyEvent.target
            val tagName = (target?.tagName as? String)?.lowercase() ?: ""
            if (tagName != "input" && tagName != "textarea") {
                val grid = gameEngine.getCurrentGrid()
                val handled = handleKeyPress(key, ctrlKey, shiftKey, altKey, metaKey, grid, event)
                if (handled) {
                    event.preventDefault()
                    event.stopPropagation()
                }
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
        
        // Global event delegation for chain notation interactions
        setupChainInteractionDelegation()
        
        // Set up orientation/aspect ratio detection for responsive hint layout
        val mediaQuery = window.matchMedia("(min-aspect-ratio: 4/3)")
        isLandscape = mediaQuery.matches
        mediaQuery.addEventListener("change", { event ->
            val mql = event.asDynamic()
            isLandscape = mql.matches as Boolean
            if (showHints) render()  // Re-render if hints are showing
        })
        
        // Set up window resize listener for container scaling
        var resizeTimeout: Int? = null
        window.addEventListener("resize", {
            // Debounce resize events
            resizeTimeout?.let { window.clearTimeout(it) }
            resizeTimeout = window.setTimeout({
                if (currentScreen == AppScreen.GAME) {
                    matchHintSidebarHeight()
                    if (isLandscape == false) {
                        applyContainerScaling()
                    }
                }
            }, 100)
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
        
        // Check if greeting should be shown on first load
        if (!GameStateManager.hasGreetingBeenShown()) {
            showGreetingModal = true
            GameStateManager.markGreetingAsShown()
        }
        
        // Load changelog and check for new version
        loadChangelog()
        
        // Try to resume last game
        val lastGameId = GameStateManager.getCurrentGameId()
        if (lastGameId != null) {
            val saved = GameStateManager.loadGame(lastGameId)
            if (saved != null && !saved.isCompleted) {
                resumeGame(saved)
                render()
                return
            }
        }
        
        // Otherwise start fresh with a random easy puzzle
        val puzzle = PuzzleLibrary.getRandomPuzzle(DifficultyCategory.EASY)
        if (puzzle != null) {
            startNewGame(puzzle)
        } else {
            render()
        }
    }
    
    private fun loadChangelog() {
        val fetchPromise = window.asDynamic().fetch("CHANGELOG.md") as Promise<Response>
        fetchPromise.then { response ->
            if (response.ok) {
                response.text().then { text ->
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
                }
            }
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
        completionShownForPuzzle = null  // Reset completion modal tracking for new game
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
        // Parse the state - returns user eliminations (1 = eliminated by user)
        val (values, userEliminations) = SavedGameState.parseStateString(saved.currentState)
        
        // Load into engine - this calculates auto-candidates
        gameEngine.loadPuzzle(saved.puzzleString)
        
        // Apply saved values and user eliminations
        for (i in 0 until 81) {
            val originalValue = saved.puzzleString[i].digitToIntOrNull() ?: 0
            if (values[i] != 0 && values[i] != originalValue) {
                // User-entered value - use setCellValue to properly update engine
                gameEngine.setCellValue(i, values[i])
            }
            if (userEliminations[i].isNotEmpty()) {
                // Set user eliminations for this cell
                gameEngine.setUserEliminations(i, userEliminations[i])
            }
        }
        
        solution = saved.solution
        currentGame = saved
        GameStateManager.setCurrentGameId(saved.puzzleId)
        
        gameStartTime = currentTimeMillis()
        pausedTime = saved.elapsedTimeMs
        selectedCell = null
        // If already completed, mark as shown so we don't re-show modal when resuming
        completionShownForPuzzle = if (saved.isCompleted) saved.puzzleId else null
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
    
    /**
     * Save the current game state to persistent storage.
     * This includes all cell values AND all user eliminations.
     * Must be called after any manual modification to candidates or cell values.
     * 
     * User eliminations are stored separately from auto-calculated candidates.
     * This ensures user eliminations are never lost when candidates are recalculated.
     */
    private fun saveCurrentState() {
        val game = currentGame ?: return
        val grid = gameEngine.getCurrentGrid()
        val elapsedSinceStart = currentTimeMillis() - gameStartTime
        
        // updateGameState uses createStateString which saves user eliminations
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
    // Uses AND logic - only cells containing ALL selected numbers are highlighted
    private fun getPrimaryHighlightCells(grid: SudokuGrid): Set<Int> {
        if (selectedNumbers1.isEmpty()) return emptySet()
        // Get cells for each number, then intersect (AND logic)
        return selectedNumbers1.map { getHighlightCellsForNumber(grid, it) }
            .reduce { acc, set -> acc.intersect(set) }
    }
    
    // Compute which cells should have secondary highlight (light red)
    // Uses AND logic - only cells containing ALL selected numbers are highlighted
    private fun getSecondaryHighlightCells(grid: SudokuGrid): Set<Int> {
        if (selectedNumbers2.isEmpty()) return emptySet()
        // Get cells for each number, then intersect (AND logic)
        return selectedNumbers2.map { getHighlightCellsForNumber(grid, it) }
            .reduce { acc, set -> acc.intersect(set) }
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
                    c.value == number || number in c.displayCandidates
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
                grid.cells.filter { number in it.displayCandidates }.map { it.index }.toSet()
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
                // Select number for highlighting (single selection)
                if (num in selectedNumbers1) {
                    // Double click clears selection
                    selectedNumbers1.clear()
                } else {
                    selectedNumbers1.clear()
                    selectedNumbers1.add(num)
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
                // In advanced mode, this is only used for keyboard input
                // The dual number pads handle clicking directly
                // For keyboard: toggle in primary set
                if (num in selectedNumbers1) {
                    selectedNumbers1.remove(num)
                } else {
                    selectedNumbers1.add(num)
                }
            }
        }
        render()
    }
    
    // Toggle number in primary selection (for advanced mode primary number bar)
    private fun togglePrimaryNumber(num: Int) {
        if (num in selectedNumbers1) {
            selectedNumbers1.remove(num)
        } else {
            selectedNumbers1.add(num)
        }
        render()
    }
    
    // Toggle number in secondary selection (for advanced mode secondary number bar)
    private fun toggleSecondaryNumber(num: Int) {
        if (num in selectedNumbers2) {
            selectedNumbers2.remove(num)
        } else {
            selectedNumbers2.add(num)
        }
        render()
    }
    
    private fun handleCellClick(cellIndex: Int, grid: SudokuGrid) {
        val cell = grid.getCell(cellIndex)
        
        // In FAST mode with a number selected, apply it to the cell
        val selectedNum = selectedNumbers1.singleOrNull()
        if (playMode == PlayMode.FAST && selectedNum != null && !cell.isGiven) {
            if (isNotesMode) {
                // Toggle pencil mark
                if (!cell.isSolved) {
                    gameEngine.toggleCandidate(cellIndex, selectedNum)
                    saveCurrentState()
                    selectedCell = null
                }
                selectedCell = null
            } else if (!cell.isSolved) {
                val wasMistake = checkMistake(cellIndex, selectedNum)
                if (wasMistake) showToast("❌ Wrong number!")
                gameEngine.setCellValue(cellIndex, selectedNum)
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
    
    private fun handleKeyPress(key: String, ctrlKey: Boolean, shiftKey: Boolean, altKey: Boolean, metaKey: Boolean, grid: SudokuGrid, event: dynamic): Boolean {
        // Handle Escape - always works regardless of screen
        if (key.lowercase() == "escape") {
            if (showExplanation) {
                showExplanation = false
                render()
                return true
            }
            if (showAboutModal) {
                showAboutModal = false
                render()
                return true
            }
            if (showHelpModal) {
                showHelpModal = false
                render()
                return true
            }
            if (showGreetingModal) {
                showGreetingModal = false
                render()
                return true
            }
            if (showVersionModal) {
                showVersionModal = false
                render()
                return true
            }
            if (showHints) {
                showHints = false
                render()
                return true
            }
            if (currentScreen != AppScreen.GAME) {
                currentScreen = AppScreen.GAME
                render()
                return true
            }
            // Clear selections in game screen
            selectedNumbers1.clear()
            selectedNumbers2.clear()
            selectedCell = null
            render()
            return true
        }
        
        // Handle screen-specific shortcuts
        when (currentScreen) {
            AppScreen.GAME -> {
                return handleGameScreenKeys(key, ctrlKey, shiftKey, altKey, metaKey, grid, event)
            }
            AppScreen.PUZZLE_BROWSER -> {
                return handlePuzzleBrowserKeys(key, ctrlKey, shiftKey, altKey, metaKey, grid, event)
            }
            AppScreen.IMPORT_EXPORT -> {
                return handleImportExportKeys(key, ctrlKey, shiftKey, altKey, metaKey, grid, event)
            }
            AppScreen.SETTINGS -> {
                return handleSettingsKeys(key, ctrlKey, shiftKey, altKey, metaKey, grid, event)
            }
        }
        return false
    }
    
    private fun handleGameScreenKeys(key: String, ctrlKey: Boolean, shiftKey: Boolean, altKey: Boolean, metaKey: Boolean, grid: SudokuGrid, event: dynamic): Boolean {
        // Hint navigation takes priority when hints are shown
        if (showHints && (key == "ArrowUp" || key == "ArrowDown" || key == "PageUp" || key == "PageDown")) {
            when (key) {
                "ArrowUp" -> {
                    if (selectedHintIndex > 0) {
                        selectedHintIndex--
                        render()
                        return true
                    }
                }
                "ArrowDown" -> {
                    val hints = gameEngine.getHints()
                    if (selectedHintIndex < hints.size - 1) {
                        selectedHintIndex++
                        render()
                        return true
                    }
                }
                "PageUp" -> {
                    selectedHintIndex = 0
                    render()
                    return true
                }
                "PageDown" -> {
                    val hints = gameEngine.getHints()
                    selectedHintIndex = hints.size - 1
                    render()
                    return true
                }
            }
        }
        
        // Arrow key navigation for cells
        when (key) {
            "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight" -> {
                return handleArrowNavigation(key, ctrlKey, shiftKey, grid)
            }
            "Home" -> {
                selectedCell?.let { cellIndex ->
                    val row = cellIndex / 9
                    selectedCell = row * 9  // Move to first column of current row
                    render()
                    return true
                }
            }
            "End" -> {
                selectedCell?.let { cellIndex ->
                    val row = cellIndex / 9
                    selectedCell = row * 9 + 8  // Move to last column of current row
                    render()
                    return true
                }
            }
        }
        
        // Ctrl+Home/End for row navigation
        if (ctrlKey && (key == "Home" || key == "End")) {
            if (key == "Home") {
                selectedCell = 0  // Top-left
                render()
                return true
            } else {
                selectedCell = 80  // Bottom-right
                render()
                return true
            }
        }
        
        // Handle F1-F9 for filter digits (similar to HoDoKu)
        if (key.startsWith("F") && key.length == 2) {
            val fNum = key.substring(1).toIntOrNull()
            if (fNum != null && fNum in 1..9) {
                if (shiftKey) {
                    // Shift+F1-F9: Set filter digit and toggle filter mode
                    // For now, just set the number as selected (could add filter mode toggle later)
                    if (fNum in selectedNumbers1) {
                        selectedNumbers1.remove(fNum)
                    } else {
                        selectedNumbers1.clear()
                        selectedNumbers1.add(fNum)
                    }
                    selectedNumbers2.clear()
                    render()
                    return true
                } else {
                    // F1-F9: Set/change the filtered digit
                    if (fNum in selectedNumbers1) {
                        selectedNumbers1.remove(fNum)
                    } else {
                        selectedNumbers1.clear()
                        selectedNumbers1.add(fNum)
                    }
                    selectedNumbers2.clear()
                    render()
                    return true
                }
            }
        }
        
        // Handle number keys 1-9
        val num = key.toIntOrNull()
        if (num != null && num in 1..9) {
            if (ctrlKey) {
                // Ctrl+number: Toggle candidate (pencil mark)
                selectedCell?.let { cellIndex ->
                    val cell = grid.getCell(cellIndex)
                    if (!cell.isGiven && !cell.isSolved) {
                        gameEngine.toggleCandidate(cellIndex, num)
                        saveCurrentState()
                        render()
                        return true
                    }
                }
            } else {
                // Regular number: Handle based on play mode
                handleNumberClick(num, grid)
                return true
            }
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
                        return true
                    }
                }
            }
            "n" -> {
                if (!ctrlKey && !shiftKey && !altKey && !metaKey) {
                    isNotesMode = !isNotesMode
                    render()
                    return true
                }
            }
            "h" -> {
                if (!ctrlKey && !shiftKey && !altKey && !metaKey) {
                    // Toggle hints
                    if (isBackendAvailable) {
                        showHints = !showHints
                        if (showHints) {
                            selectedHintIndex = 0
                            gameEngine.findAllTechniques()
                        }
                        render()
                        return true
                    }
                }
            }
            " " -> {
                // Space: If a filter (selected number) is set, toggle the candidate
                selectedCell?.let { cellIndex ->
                    val num = selectedNumbers1.singleOrNull()
                    if (num != null) {
                        val cell = grid.getCell(cellIndex)
                        if (!cell.isGiven && !cell.isSolved) {
                            gameEngine.toggleCandidate(cellIndex, num)
                            saveCurrentState()
                            render()
                            return true
                        }
                    }
                }
            }
        }
        
        // Advanced mode: Set primary number with keyboard (only works if exactly one number selected)
        if (playMode == PlayMode.ADVANCED && selectedCell != null) {
            val cell = grid.getCell(selectedCell!!)
            if (!cell.isGiven && !cell.isSolved) {
                val singleNum = selectedNumbers1.singleOrNull()
                when (key.lowercase()) {
                    "enter", "return" -> {
                        // Enter: Set primary number if exactly one selected
                        if (singleNum != null) {
                            gameEngine.setCellValue(selectedCell!!, singleNum)
                            saveCurrentState()
                            render()
                            return true
                        }
                    }
                    "s" -> {
                        if (!ctrlKey && !shiftKey && !altKey && !metaKey) {
                            // S: Set primary number if exactly one selected
                            if (singleNum != null) {
                                gameEngine.setCellValue(selectedCell!!, singleNum)
                                saveCurrentState()
                                render()
                                return true
                            }
                        }
                    }
                }
            }
        }
        
        // Screen navigation shortcuts
        when (key.lowercase()) {
            "m" -> {
                if (!ctrlKey && !shiftKey && !altKey && !metaKey) {
                    // M: Open menu/settings
                    saveCurrentState()
                    currentScreen = AppScreen.SETTINGS
                    render()
                    return true
                }
            }
            "b" -> {
                if (!ctrlKey && !shiftKey && !altKey && !metaKey) {
                    // B: Open puzzle browser
                    saveCurrentState()
                    currentScreen = AppScreen.PUZZLE_BROWSER
                    render()
                    return true
                }
            }
            "i" -> {
                if (!ctrlKey && !shiftKey && !altKey && !metaKey) {
                    // I: Open import/export
                    saveCurrentState()
                    currentScreen = AppScreen.IMPORT_EXPORT
                    render()
                    return true
                }
            }
        }
        
        return false
    }
    
    private fun handleArrowNavigation(key: String, ctrlKey: Boolean, shiftKey: Boolean, grid: SudokuGrid): Boolean {
        val current = selectedCell ?: 0
        var row = current / 9
        var col = current % 9
        
        when (key) {
            "ArrowUp" -> {
                if (ctrlKey) {
                    // Ctrl+Up: Jump to next unsolved cell above
                    val result = findNextUnsolvedCell(row, col, -1, 0, grid)
                    selectedCell = result
                    render()
                    return true
                } else {
                    row = (row - 1 + 9) % 9
                }
            }
            "ArrowDown" -> {
                if (ctrlKey) {
                    // Ctrl+Down: Jump to next unsolved cell below
                    val result = findNextUnsolvedCell(row, col, 1, 0, grid)
                    selectedCell = result
                    render()
                    return true
                } else {
                    row = (row + 1) % 9
                }
            }
            "ArrowLeft" -> {
                if (ctrlKey) {
                    // Ctrl+Left: Jump to next unsolved cell to the left
                    val result = findNextUnsolvedCell(row, col, 0, -1, grid)
                    selectedCell = result
                    render()
                    return true
                } else {
                    col = (col - 1 + 9) % 9
                }
            }
            "ArrowRight" -> {
                if (ctrlKey) {
                    // Ctrl+Right: Jump to next unsolved cell to the right
                    val result = findNextUnsolvedCell(row, col, 0, 1, grid)
                    selectedCell = result
                    render()
                    return true
                } else {
                    col = (col + 1) % 9
                }
            }
        }
        
        selectedCell = row * 9 + col
        render()
        return true
    }
    
    private fun findNextUnsolvedCell(startRow: Int, startCol: Int, rowDelta: Int, colDelta: Int, grid: SudokuGrid): Int {
        var row = startRow
        var col = startCol
        var attempts = 0
        
        while (attempts < 81) {
            row = (row + rowDelta + 9) % 9
            col = (col + colDelta + 9) % 9
            val cellIndex = row * 9 + col
            val cell = grid.getCell(cellIndex)
            
            if (!cell.isGiven && !cell.isSolved) {
                return cellIndex
            }
            
            attempts++
            // If we've wrapped around to the starting position, stop
            if (row == startRow && col == startCol) {
                break
            }
        }
        
        // If no unsolved found, return current position
        return startRow * 9 + startCol
    }
    
    private fun handlePuzzleBrowserKeys(key: String, ctrlKey: Boolean, shiftKey: Boolean, altKey: Boolean, metaKey: Boolean, grid: SudokuGrid, event: dynamic): Boolean {
        // Arrow keys for navigation in puzzle list
        when (key) {
            "ArrowUp", "ArrowDown" -> {
                // Could be used for puzzle list navigation in future
                return false
            }
        }
        
        // Escape already handled at top level
        return false
    }
    
    private fun handleImportExportKeys(key: String, ctrlKey: Boolean, shiftKey: Boolean, altKey: Boolean, metaKey: Boolean, grid: SudokuGrid, event: dynamic): Boolean {
        // Escape already handled at top level
        return false
    }
    
    private fun handleSettingsKeys(key: String, ctrlKey: Boolean, shiftKey: Boolean, altKey: Boolean, metaKey: Boolean, grid: SudokuGrid, event: dynamic): Boolean {
        // Escape already handled at top level
        return false
    }
    
    private fun render() {
        appRoot.innerHTML = ""
        
        when (currentScreen) {
            AppScreen.GAME -> renderGameScreen()
            AppScreen.PUZZLE_BROWSER -> renderPuzzleBrowser()
            AppScreen.IMPORT_EXPORT -> renderImportExport()
            AppScreen.SETTINGS -> renderSettings()
        }
        
        // About modal (can appear over any screen)
        if (showAboutModal) {
            renderAboutModal()
        }
        
        // Help modal (can appear over any screen)
        if (showHelpModal) {
            renderHelpModal()
        }
        
        // Greeting modal (can appear over any screen, shown on first load)
        if (showGreetingModal) {
            renderGreetingModal()
        }
        
        // Completion modal (shown when puzzle is solved)
        if (showCompletionModal) {
            renderCompletionModal()
        }
        
        // Version modal (shown on new version)
        if (showVersionModal) {
            renderVersionModal()
        }
        
        // Explanation overlay (shown when user clicks Explain on a hint)
        // Always show version number in bottom left corner (if loaded)
        if (currentVersion.isNotEmpty()) {
            renderVersionIndicator()
        }
    }
    
    private fun renderAboutModal() {
        appRoot.append {
            div("modal-overlay") {
                onClickFunction = { event ->
                    // Close when clicking overlay (not the modal content)
                    if ((event.target as? Element)?.classList?.contains("modal-overlay") == true) {
                        showAboutModal = false
                        render()
                    }
                }
                div("modal-content about-modal") {
                    button(classes = "modal-close") {
                        +"✕"
                        onClickFunction = {
                            showAboutModal = false
                            render()
                        }
                    }
                    
                    h1 { +"Nice Sudoku" }
                    
                    p("about-tagline") {
                        +"A FOSS Sudoku game for all platforms by Andrew Frahn"
                    }
                    
                    p("about-description") {
                        +"Intended to be the easy to pick up, while also suitable for the most complex puzzles by enthusiasts."
                    }
                    
                    div("about-section") {
                        h3 { +"💬 Feedback" }
                        p {
                            +"Please report bugs, features and ideas on GitHub: "
                            a(href = "https://github.com/emmertex/nice_sudoku2", target = "_blank") {
                                +"github.com/emmertex/nice_sudoku2"
                            }
                        }
                    }
                    
                    div("about-section") {
                        h3 { +"🧠 Solvers" }
                        p {
                            +"All solvers by StrmCkr via "
                            strong { +"StormDoku" }
                        }
                        p {
                            a(href = "https://www.reddit.com/user/strmckr/", target = "_blank") {
                                +"reddit.com/user/strmckr"
                            }
                        }
                        p {
                            +"Reddit Wiki: "
                            a(href = "https://www.reddit.com/r/sudoku/wiki/index/", target = "_blank") {
                                +"r/sudoku wiki"
                            }
                        }
                        p {
                            +"StrmCkr's GitHub: "
                            a(href = "https://github.com/strmckr", target = "_blank") {
                                +"github.com/strmckr"
                            }
                        }
                    }
                    
                    div("about-section") {
                        h3 { +"🎨 UI/UX" }
                        p {
                            +"Designed by Andrew Frahn: "
                            a(href = "https://github.com/emmertex", target = "_blank") {
                                +"github.com/emmertex"
                            }
                        }
                    }
                    
                    div("about-section") {
                        h3 { +"Puzzles" }
                        p {
                            +"Puzzles sourced from: "
                            a(href = "https://github.com/grantm/sudoku-exchange-puzzle-bank", target = "_blank") {
                                +"Sudoku Exchange Puzzle Bank"
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun renderHelpModal() {
        appRoot.append {
            div("modal-overlay") {
                onClickFunction = { event ->
                    // Close when clicking overlay (not the modal content)
                    if ((event.target as? Element)?.classList?.contains("modal-overlay") == true) {
                        showHelpModal = false
                        render()
                    }
                }
                div("modal-content help-modal") {
                    button(classes = "modal-close") {
                        +"✕"
                        onClickFunction = {
                            showHelpModal = false
                            render()
                        }
                    }
                    
                    h1 { +"Help" }
                    
                    div("help-section") {
                        h2 { +"Welcome" }
                        div("greeting-content") {
                            p {
                                +"Thank you for testing Nice Sudoku."
                            }
                            
                            p {
                                +"In short, I, Andrew, wanted a good Android Sudoku app."
                            }
                            
                            p {
                                +"I am not even good at Sudoku, I crap out after X Wings. So I wanted a way to genuinely learn."
                            }
                            
                            p {
                                +"Well, I tried almost all the apps, and they were all crap, Ad ridden nonsense, or alike."
                            }
                            
                            p {
                                +"I also wanted to learn godot, so wrote a sudoku app. I got all basic solvers working, but then StrmCkr seen my work, and things got hard and complex. After getting a few intermediate solvers working nice, and a world of UI issues, I threw it all away."
                            }
                            
                            p {
                                +"This is the second version, written in Kotlin, with the intent to be native on all platforms. Using not just the knowledge of StrmCkr, but his years of knowledge making solvers as a backend, and a new frontend, hooking into it as an API."
                            }
                            
                            p {
                                +"Currently this means it must be online, but over time I intend to make it all offline."
                            }
                            
                            p {
                                +"I am asking nothing more than community support for me and StrmCkr, and I will endeavour to make this app something I want to use."
                            }
                            
                            p {
                                +"This isn't a first version, it is way too early for that, it is a feedback gathering exercise."
                            }
                            
                            p {
                                +"If you try it, please, offer feedback. The earlier in the development process I get feedback, the better the chance I can make it happen."
                            }
                            
                            p("greeting-signature") {
                                +"Thanks for testing,"
                                br
                                +"Andrew"
                            }
                        }
                    }
                    
                    div("help-section") {
                        h2 { +"Keyboard Shortcuts" }
                    }
                    
                    div("help-section") {
                        h2 { +"Navigation" }
                        
                        h3 { +"Cell Selection" }
                        ul {
                            li {
                                strong { +"Arrow Keys (↑ ← ↓ →)" }
                                +": Move the cursor between cells"
                            }
                            li {
                                strong { +"Ctrl + Arrow Keys" }
                                +": Jump to the next unsolved cell in that direction"
                            }
                            li {
                                strong { +"Home" }
                                +": Move to the first column of the current row"
                            }
                            li {
                                strong { +"End" }
                                +": Move to the last column of the current row"
                            }
                            li {
                                strong { +"Ctrl + Home" }
                                +": Move to the top-left cell (cell 0)"
                            }
                            li {
                                strong { +"Ctrl + End" }
                                +": Move to the bottom-right cell (cell 80)"
                            }
                        }
                    }
                    
                    div("help-section") {
                        h2 { +"Number Entry" }
                        
                        h3 { +"Basic Entry" }
                        ul {
                            li {
                                strong { +"1-9" }
                                +": Enter numbers based on play mode:"
                                ul {
                                    li {
                                        strong { +"Fast Mode" }
                                        +": Selects the number for highlighting. If a cell is selected, applies the number to that cell"
                                    }
                                    li {
                                        strong { +"Advanced Mode" }
                                        +": Toggles the number in the primary selection. Use the two number bars for clicking (primary=blue, secondary=red)"
                                    }
                                }
                            }
                        }
                        
                        h3 { +"Candidate Entry (Pencil Marks)" }
                        ul {
                            li {
                                strong { +"Ctrl + 1-9" }
                                +": Toggle pencil mark candidate in the selected cell"
                            }
                            li {
                                strong { +"Space" }
                                +": If a number is selected (filter), toggle its candidate in the selected cell"
                            }
                            li {
                                strong { +"N" }
                                +": Toggle notes/pencil mode on/off"
                            }
                        }
                    }
                    
                    div("help-section") {
                        h2 { +"Editing" }
                        ul {
                            li {
                                strong { +"Delete" }
                                +" or "
                                strong { +"Backspace" }
                                +": Clear the value in the selected cell (cannot clear given cells)"
                            }
                            li {
                                strong { +"Escape" }
                                +": Clear all selections (selected numbers and cell)"
                            }
                        }
                    }
                    
                    div("help-section") {
                        h2 { +"Filters and Highlighting" }
                        ul {
                            li {
                                strong { +"F1-F9" }
                                +": Set/change the filtered (selected) digit for highlighting"
                            }
                            li {
                                strong { +"Shift + F1-F9" }
                                +": Set/change the filtered digit (future: toggle filter mode)"
                            }
                        }
                    }
                    
                    div("help-section") {
                        h2 { +"Game Actions" }
                        
                        h3 { +"Hint System" }
                        ul {
                            li {
                                strong { +"H" }
                                +": Toggle hint panel (show/hide available solving techniques)"
                                ul {
                                    li { +"Requires backend connection to be available" }
                                    li {
                                        +"When hints are visible:"
                                        ul {
                                            li {
                                                strong { +"Arrow Up/Down" }
                                                +": Navigate through available hints"
                                            }
                                            li {
                                                strong { +"Page Up" }
                                                +": Jump to first hint"
                                            }
                                            li {
                                                strong { +"Page Down" }
                                                +": Jump to last hint"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        h3 { +"Advanced Mode Actions" }
                        ul {
                            li {
                                strong { +"Enter" }
                                +" or "
                                strong { +"S" }
                                +": Set the value in the selected cell (only works when exactly one number is selected in primary)"
                            }
                            li {
                                strong { +"Deselect button" }
                                +": Click to deselect the cell and show number bars again"
                            }
                        }
                    }
                    
                    div("help-section") {
                        h2 { +"Screen Navigation" }
                        ul {
                            li {
                                strong { +"M" }
                                +": Open Menu/Settings screen"
                            }
                            li {
                                strong { +"B" }
                                +": Open Puzzle Browser screen"
                            }
                            li {
                                strong { +"I" }
                                +": Open Import/Export screen"
                            }
                            li {
                                strong { +"Escape" }
                                +":"
                                ul {
                                    li { +"Close modals (About, etc.)" }
                                    li { +"Close hint panel" }
                                    li { +"Return to Game screen from any other screen" }
                                    li { +"Clear selections in Game screen" }
                                }
                            }
                        }
                    }
                    
                    div("help-section") {
                        h2 { +"Mode Switching" }
                        ul {
                            li {
                                strong { +"N" }
                                +": Toggle Notes/Pencil mode"
                                ul {
                                    li { +"When enabled, number entry adds/removes pencil marks instead of values" }
                                }
                            }
                        }
                    }
                    
                    div("help-section") {
                        h2 { +"Notes" }
                        ol {
                            li { +"All shortcuts are disabled when typing in input fields or text areas to prevent conflicts" }
                            li { +"Keyboard shortcuts follow HoDoKu conventions for consistency with standard Sudoku software" }
                            li { +"The game is fully playable using only keyboard input" }
                            li { +"Some shortcuts may vary slightly in behavior between Fast and Advanced play modes" }
                            li {
                                +"F1-F9 keys: Some browsers use F-keys for developer tools (e.g., F12) or other functions. If a browser shortcut conflicts, you may need to disable the browser's shortcut or use number keys 1-9 instead for filtering"
                            }
                        }
                    }
                    
                    div("help-section") {
                        h2 { +"Play Mode Differences" }
                        
                        h3 { +"Fast Mode" }
                        ul {
                            li { +"Number keys immediately apply to selected cells when appropriate" }
                            li { +"Quick, streamlined input for faster solving" }
                            li { +"Single number selection for highlighting" }
                        }
                        
                        h3 { +"Advanced Mode" }
                        ul {
                            li { +"Two number bars: primary (blue) and secondary (red)" }
                            li { +"Toggle multiple numbers in each bar - cells must contain ALL selected numbers to highlight" }
                            li { +"When a cell is selected, number bars hide and action buttons appear" }
                            li { +"Use Deselect button to show number bars again" }
                            li { +"Use Set/Clr buttons to modify cells, or Enter/S for single-number selections" }
                            li { +"Click Fast/Adv badge in header to quickly toggle modes" }
                            li { +"Supports two-number highlighting for complex solving techniques" }
                        }
                    }
                    
                    div("help-section") {
                        h2 { +"Tips" }
                        ul {
                            li {
                                +"Use "
                                strong { +"Ctrl + Arrow Keys" }
                                +" to quickly jump between unsolved cells"
                            }
                            li {
                                +"Use "
                                strong { +"F1-F9" }
                                +" for quick number filtering and highlighting"
                            }
                            li {
                                +"Use "
                                strong { +"H" }
                                +" to access hints and learn new solving techniques"
                            }
                            li {
                                strong { +"Escape" }
                                +" is your universal \"go back\" key - use it to return to the game from any screen or modal"
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun renderGreetingModal() {
        appRoot.append {
            div("modal-overlay") {
                onClickFunction = { event ->
                    // Close when clicking overlay (not the modal content)
                    if ((event.target as? Element)?.classList?.contains("modal-overlay") == true) {
                        showGreetingModal = false
                        render()
                    }
                }
                div("modal-content greeting-modal") {
                    button(classes = "modal-close") {
                        +"✕"
                        onClickFunction = {
                            showGreetingModal = false
                            render()
                        }
                    }
                    
                    h1 { +"Welcome to Nice Sudoku" }
                    
                    div("greeting-content") {
                        p {
                            +"Thank you for testing Nice Sudoku."
                        }
                        
                        p {
                            +"In short, I, Andrew, wanted a good Android Sudoku app."
                        }
                        
                        p {
                            +"I am not even good at Sudoku, I crap out after X Wings. So I wanted a way to genuinely learn."
                        }
                        
                        p {
                            +"Well, I tried almost all the apps, and they were all crap, Ad ridden nonsense, or alike."
                        }
                        
                        p {
                            +"I also wanted to learn godot, so wrote a sudoku app. I got all basic solvers working, but then StrmCkr seen my work, and things got hard and complex. After getting a few intermediate solvers working nice, and a world of UI issues, I threw it all away."
                        }
                        
                        p {
                            +"This is the second version, written in Kotlin, with the intent to be native on all platforms. Using not just the knowledge of StrmCkr, but his years of knowledge making solvers as a backend, and a new frontend, hooking into it as an API."
                        }
                        
                        p {
                            +"Currently this means it must be online, but over time I intend to make it all offline."
                        }
                        
                        p {
                            +"I am asking nothing more than community support for me and StrmCkr, and I will endeavour to make this app something I want to use."
                        }
                        
                        p {
                            +"This isn't a first version, it is way too early for that, it is a feedback gathering exercise."
                        }
                        
                        p {
                            +"If you try it, please, offer feedback. The earlier in the development process I get feedback, the better the chance I can make it happen."
                        }
                        
                        p("greeting-signature") {
                            +"Thanks for testing,"
                            br
                            +"Andrew"
                        }
                    }
                }
            }
        }
    }
    
    private fun renderCompletionModal() {
        val game = currentGame ?: return
        
        appRoot.append {
            div("modal-overlay") {
                onClickFunction = { event ->
                    // Close when clicking overlay (not the modal content)
                    if ((event.target as? Element)?.classList?.contains("modal-overlay") == true) {
                        showCompletionModal = false
                        render()
                    }
                }
                div("modal-content completion-modal") {
                    button(classes = "modal-close") {
                        +"✕"
                        onClickFunction = {
                            showCompletionModal = false
                            render()
                        }
                    }
                    
                    div("completion-icon") { +"🎉" }
                    h1 { +"Congratulations!" }
                    
                    div("completion-content") {
                        p { +"You've completed the puzzle!" }
                        
                        div("completion-stats") {
                            div("stat") {
                                span("stat-icon") { +"⏱️" }
                                span("stat-label") { +"Time" }
                                span("stat-value") { +formatTime(game.elapsedTimeMs) }
                            }
                            div("stat") {
                                span("stat-icon") { +"❌" }
                                span("stat-label") { +"Mistakes" }
                                span("stat-value") { +"${game.mistakeCount}" }
                            }
                            div("stat") {
                                span("stat-icon") { +"📊" }
                                span("stat-label") { +"Difficulty" }
                                span("stat-value") { +game.category.displayName }
                            }
                        }
                    }
                    
                    div("completion-actions") {
                        button(classes = "close-btn") {
                            +"Close"
                            onClickFunction = {
                                showCompletionModal = false
                                render()
                            }
                        }
                        button(classes = "next-btn") {
                            +"Next Game"
                            onClickFunction = {
                                showCompletionModal = false
                                loadNextUncompletedGame(game.category)
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun renderVersionIndicator() {
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
    
    private fun renderVersionModal() {
        appRoot.append {
            div("modal-overlay") {
                onClickFunction = { event ->
                    // Close when clicking overlay (not the modal content)
                    if ((event.target as? Element)?.classList?.contains("modal-overlay") == true) {
                        showVersionModal = false
                        render()
                    }
                }
                div("modal-content version-modal") {
                    button(classes = "modal-close") {
                        +"✕"
                        onClickFunction = {
                            showVersionModal = false
                            render()
                        }
                    }
                    
                    h1 { +"What's New" }
                    
                    // Render changelog content as formatted HTML
                    div("changelog-content") {
                        unsafe {
                            +parseMarkdownToHtml(changelogContent)
                        }
                    }
                    
                    div("version-actions") {
                        button(classes = "close-btn") {
                            +"Got it!"
                            onClickFunction = {
                                showVersionModal = false
                                render()
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun parseMarkdownToHtml(markdown: String): String {
        return markdown.lines().joinToString("\n") { line ->
            when {
                // H1 headers
                line.startsWith("# ") -> "<h2 class='changelog-version'>${line.drop(2)}</h2>"
                // H3 headers (### Features, ### Fixes, etc.)
                line.startsWith("### ") -> "<h3 class='changelog-section'>${line.drop(4)}</h3>"
                // List items with double dash (sub-items)
                line.trimStart().startsWith("- - ") -> {
                    val content = line.trimStart().drop(4)
                    "<li class='changelog-subitem'>${formatInlineMarkdown(content)}</li>"
                }
                // Regular list items
                line.trimStart().startsWith("- ") -> {
                    val content = line.trimStart().drop(2)
                    "<li>${formatInlineMarkdown(content)}</li>"
                }
                // Empty lines
                line.isBlank() -> ""
                // Regular text
                else -> "<p>${formatInlineMarkdown(line)}</p>"
            }
        }.replace(Regex("<li>"), "<ul><li>")
            .replace(Regex("</li>(?!\\s*<li)"), "</li></ul>")
            .replace(Regex("</ul>\\s*<ul>"), "") // Clean up consecutive ul tags
    }
    
    private fun formatInlineMarkdown(text: String): String {
        return text
            // Strikethrough ~~text~~
            .replace(Regex("~~(.+?)~~"), "<del>$1</del>")
            // Bold text **text**
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
            // Italic text *text*
            .replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
            // Code `text`
            .replace(Regex("`(.+?)`"), "<code>$1</code>")
    }
    
    private fun loadNextUncompletedGame(category: DifficultyCategory) {
        val summaries = GameStateManager.getGameSummaries()
        val puzzles = PuzzleLibrary.getPuzzlesForCategory(category)
        
        // Find the first puzzle that has NOT been started (no saved game exists)
        val nextPuzzle = puzzles.firstOrNull { puzzle ->
            val existingGame = summaries.find { it.puzzleId == puzzle.id }
            existingGame == null
        }
        
        if (nextPuzzle != null) {
            startNewGame(nextPuzzle)
        } else {
            // All puzzles in this category have been started
            showToast("All ${category.displayName} puzzles started! 🎉")
            render()
        }
    }
    
    private fun renderGameScreen() {
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
                
                // Get current explanation step if showing explanation
                val currentExplanationStep = if (showExplanation && selectedHint != null) {
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
                                        renderCell(cellIndex, cell, isPrimary, isSecondary, grid, selectedHint, currentExplanationStep)
                                    }
                                }
                            }
                        }
                        
                        // SVG overlay for chain lines (when hint with lines is selected)
                        if (selectedHint != null && selectedHint.lines.isNotEmpty()) {
                            renderChainLinesSvg(selectedHint, currentExplanationStep)
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
                                        primaryInCandidates.forEach { num ->
                                            gameEngine.toggleCandidate(selectedCell!!, num)
                                        }
                                        saveCurrentState()
                                        render()
                                    }
                                }
                            }
                            if (secondaryInCandidates.isNotEmpty() && !cell.isGiven) {
                                button(classes = "action-btn clr-btn secondary") {
                                    +"Clr ${secondaryInCandidates.sorted().joinToString(",")}"
                                    onClickFunction = {
                                        secondaryInCandidates.forEach { num ->
                                            gameEngine.toggleCandidate(selectedCell!!, num)
                                        }
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
    private fun applyContainerScaling() {
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
    private fun matchHintSidebarHeight() {
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
    
    private fun TagConsumer<HTMLElement>.renderLandscapeHintSidebar(
        hints: List<TechniqueMatchInfo>,
        selectedHint: TechniqueMatchInfo?
    ) {
        div("hint-sidebar") {
            // Show either the explanation view OR the list view (not both)
            if (showExplanation && selectedHint != null) {
                // Explanation view - replaces the entire list
                renderExplanationView(selectedHint, hints.size)
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
                            val isSelected = index == selectedHintIndex
                            div("hint-item ${if (isSelected) "selected" else ""}") {
                                // Header row (always visible)
                                div("hint-item-header") {
                                    onClickFunction = { e ->
                                        // Select this hint
                                        selectedHintIndex = index
                                        explanationStepIndex = 0
                                        render()
                                    }
                                    div("hint-item-content") {
                                        div("hint-technique") { +hint.techniqueName }
                                        div("hint-description") { +hint.description }
                                    }
                                    if (isSelected) {
                                        button(classes = "hint-explain-btn") {
                                            +"📖 Explain"
                                            onClickFunction = { e ->
                                                e.stopPropagation()
                                                showExplanation = true
                                                explanationStepIndex = 0
                                                render()
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
                        showHints = false
                        render()
                    }
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
                if (showExplanation) {
                    // Show inline explanation
                    renderInlineExplanation(selectedHint)
                } else {
                    // Show hint content with explain button
                    div("hint-content") {
                        div("hint-technique") { +selectedHint.techniqueName }
                        div("hint-description") { +selectedHint.description }
                        button(classes = "hint-explain-btn") {
                            +"📖 Explain"
                            onClickFunction = {
                                showExplanation = true
                                explanationStepIndex = 0
                                render()
                            }
                        }
                    }
                }
            } else {
                div("hint-content hint-empty") {
                    p { +"Searching for hints..." }
                }
            }
        }
    }
    
    /**
     * Render interactive chain description with clickable/hoverable elements
     * Parses patterns like:
     * - (3)R5C6 - cell/candidate reference
     * - --[strong]--> or --[weak]--> - link indicators
     */
    private fun TagConsumer<HTMLElement>.renderInteractiveDescription(description: String, hint: TechniqueMatchInfo) {
        // Regex patterns for chain notation
        val cellPattern = Regex("""\((\d)\)([Rr])(\d)[Cc](\d)(?:,([Rr])(\d)[Cc](\d))*""")
        val linkPattern = Regex("""--\[(strong|weak)\]-->""")
        
        var lastIndex = 0
        var linkIndex = 0
        val result = StringBuilder()
        
        // Find all matches and their positions
        data class Match(val start: Int, val end: Int, val type: String, val content: String, val data: Any?)
        val matches = mutableListOf<Match>()
        
        // Find cell references like (3)R5C6 or (3)R5C6,R5C7
        cellPattern.findAll(description).forEach { match ->
            val digit = match.groupValues[1].toInt()
            // Parse all cells in the match (handles multi-cell nodes)
            val cells = mutableListOf<Pair<Int, Int>>()  // row, col pairs
            val fullMatch = match.value
            val singleCellPattern = Regex("""[Rr](\d)[Cc](\d)""")
            singleCellPattern.findAll(fullMatch).forEach { cellMatch ->
                val row = cellMatch.groupValues[1].toInt() - 1
                val col = cellMatch.groupValues[2].toInt() - 1
                cells.add(row to col)
            }
            matches.add(Match(match.range.first, match.range.last + 1, "cell", match.value, digit to cells))
        }
        
        // Find link indicators like --[strong]-->
        linkPattern.findAll(description).forEach { match ->
            val linkType = match.groupValues[1]
            matches.add(Match(match.range.first, match.range.last + 1, "link", match.value, linkIndex++ to linkType))
        }
        
        // Sort matches by position
        matches.sortBy { it.start }
        
        // Build HTML with interactive spans
        var currentPos = 0
        matches.forEach { match ->
            // Add text before this match
            if (match.start > currentPos) {
                result.append("""<span class="chain-text">${description.substring(currentPos, match.start)}</span>""")
            }
            
            when (match.type) {
                "cell" -> {
                    @Suppress("UNCHECKED_CAST")
                    val data = match.data as Pair<Int, List<Pair<Int, Int>>>
                    val digit = data.first
                    val cells = data.second
                    val cellIndices = cells.map { pair -> pair.first * 9 + pair.second }
                    val dataAttr = cellIndices.joinToString(",")
                    result.append("""<span class="chain-cell-ref" data-cells="$dataAttr" data-candidate="$digit">${match.content}</span>""")
                }
                "link" -> {
                    @Suppress("UNCHECKED_CAST")
                    val data = match.data as Pair<Int, String>
                    val idx = data.first
                    val linkType = data.second
                    result.append("""<span class="chain-link-ref chain-link-$linkType" data-link-index="$idx">[$linkType]</span>""")
                }
            }
            currentPos = match.end
        }
        
        // Add remaining text
        if (currentPos < description.length) {
            result.append("""<span class="chain-text">${description.substring(currentPos)}</span>""")
        }
        
        // If no interactive elements found, just show plain text
        if (matches.isEmpty()) {
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
                attachChainInteractionListeners(containerId, hint)
            }, 50)  // Small delay to ensure DOM is ready
        }
    }
    
    /**
     * Set up global event delegation for chain notation interactions
     * This uses event delegation so we don't need to re-attach listeners on each render
     */
    private fun setupChainInteractionDelegation() {
        // Click delegation
        document.addEventListener("click", { event ->
            val target = (event.target as? HTMLElement) ?: return@addEventListener
            
            // Handle cell reference clicks
            val cellRef = target.closest(".chain-cell-ref") as? HTMLElement
            if (cellRef != null) {
                val cellsAttr = cellRef.getAttribute("data-cells") ?: return@addEventListener
                val candidateAttr = cellRef.getAttribute("data-candidate") ?: return@addEventListener
                val cells = cellsAttr.split(",").mapNotNull { it.toIntOrNull() }
                val candidate = candidateAttr.toIntOrNull() ?: return@addEventListener
                
                event.stopPropagation()
                
                // Toggle persistent highlight
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
                return@addEventListener
            }
            
            // Handle link reference clicks
            val linkRef = target.closest(".chain-link-ref") as? HTMLElement
            if (linkRef != null) {
                val linkIndexAttr = linkRef.getAttribute("data-link-index") ?: return@addEventListener
                val linkIdx = linkIndexAttr.toIntOrNull() ?: return@addEventListener
                
                event.stopPropagation()
                
                // Toggle persistent highlight
                if (highlightedLinkIndex == linkIdx) {
                    highlightedLinkIndex = null
                    updateLinkHighlight(linkIdx, false)
                } else {
                    highlightedLinkIndex = linkIdx
                    updateLinkHighlight(linkIdx, true)
                }
                return@addEventListener
            }
        })
        
        // Mouseenter delegation (uses mouseover with check)
        document.addEventListener("mouseover", { event ->
            val target = (event.target as? HTMLElement) ?: return@addEventListener
            
            // Handle cell reference hover
            val cellRef = target.closest(".chain-cell-ref") as? HTMLElement
            if (cellRef != null && !cellRef.classList.contains("chain-hovered")) {
                cellRef.classList.add("chain-hovered")
                val cellsAttr = cellRef.getAttribute("data-cells") ?: return@addEventListener
                val candidateAttr = cellRef.getAttribute("data-candidate") ?: return@addEventListener
                val cells = cellsAttr.split(",").mapNotNull { it.toIntOrNull() }
                val candidate = candidateAttr.toIntOrNull() ?: return@addEventListener
                
                val hint = currentHintList.getOrNull(selectedHintIndex)
                if (hint != null) {
                    updateChainHighlights(cells, candidate, hint)
                }
                return@addEventListener
            }
            
            // Handle link reference hover
            val linkRef = target.closest(".chain-link-ref") as? HTMLElement
            if (linkRef != null && !linkRef.classList.contains("chain-hovered")) {
                linkRef.classList.add("chain-hovered")
                val linkIndexAttr = linkRef.getAttribute("data-link-index") ?: return@addEventListener
                val linkIdx = linkIndexAttr.toIntOrNull() ?: return@addEventListener
                
                updateLinkHighlight(linkIdx, true)
                return@addEventListener
            }
        })
        
        // Mouseleave delegation (uses mouseout with check)
        document.addEventListener("mouseout", { event ->
            val target = (event.target as? HTMLElement) ?: return@addEventListener
            
            // Handle cell reference leave
            if (target.classList.contains("chain-cell-ref") && target.classList.contains("chain-hovered")) {
                target.classList.remove("chain-hovered")
                // Only clear if not persistently highlighted
                if (highlightedNodeCell == null) {
                    clearChainHighlights()
                }
                return@addEventListener
            }
            
            // Handle link reference leave
            if (target.classList.contains("chain-link-ref") && target.classList.contains("chain-hovered")) {
                target.classList.remove("chain-hovered")
                val linkIndexAttr = target.getAttribute("data-link-index") ?: return@addEventListener
                val linkIdx = linkIndexAttr.toIntOrNull() ?: return@addEventListener
                // Only clear if not persistently highlighted
                if (highlightedLinkIndex == null) {
                    updateLinkHighlight(linkIdx, false)
                }
                return@addEventListener
            }
        })
    }
    
    /**
     * Attach mouse/click listeners for interactive chain elements (legacy - now using delegation)
     */
    private fun attachChainInteractionListeners(containerId: String, hint: TechniqueMatchInfo) {
        // Event delegation is now handled globally in setupChainInteractionDelegation()
        // This function is kept for compatibility but does nothing
    }
    
    /**
     * Update visual highlights for cells and candidates
     */
    private fun updateChainHighlights(cells: List<Int>, candidate: Int, hint: TechniqueMatchInfo) {
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
    private fun clearChainHighlights() {
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
    private fun updateLinkHighlight(linkIndex: Int, highlight: Boolean) {
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
    private fun TagConsumer<HTMLElement>.renderExplanationView(hint: TechniqueMatchInfo, totalHints: Int) {
        // Use backend steps if available, otherwise generate fallback
        val steps = if (hint.explanationSteps.isNotEmpty()) {
            hint.explanationSteps
        } else {
            generateFallbackExplanationSteps(hint)
        }
        val currentStep = steps.getOrNull(explanationStepIndex)
        
        div("explanation-view") {
            // Header with back button
            div("explanation-view-header") {
                button(classes = "explanation-back-btn") {
                    +"← Back to List"
                    onClickFunction = {
                        showExplanation = false
                        render()
                    }
                }
                span("hint-position-badge") { +"${selectedHintIndex + 1}/$totalHints" }
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
                        renderInteractiveDescription(currentStep.description, hint)
                    }
                } else {
                    div("step-description") {
                        renderInteractiveDescription(hint.description, hint)
                    }
                }
            }
            
            // Navigation (only show if more than one step)
            if (steps.size > 1) {
                div("explanation-nav") {
                    button(classes = "explanation-nav-btn ${if (explanationStepIndex <= 0) "disabled" else ""}") {
                        +"◀ Prev"
                        onClickFunction = {
                            if (explanationStepIndex > 0) {
                                explanationStepIndex--
                                render()
                            }
                        }
                    }
                    span("step-indicator") { +"Step ${explanationStepIndex + 1} / ${steps.size}" }
                    button(classes = "explanation-nav-btn ${if (explanationStepIndex >= steps.size - 1) "disabled" else ""}") {
                        +"Next ▶"
                        onClickFunction = {
                            if (explanationStepIndex < steps.size - 1) {
                                explanationStepIndex++
                                render()
                            }
                        }
                    }
                }
            }
            
            // Close button at bottom
            button(classes = "hint-close-btn") {
                +"✕ Close Hints"
                onClickFunction = {
                    showHints = false
                    showExplanation = false
                    render()
                }
            }
        }
    }
    
    /**
     * Render inline explanation content (used in both landscape sidebar and portrait card)
     */
    private fun TagConsumer<HTMLElement>.renderInlineExplanation(hint: TechniqueMatchInfo) {
        // Use backend steps if available, otherwise generate fallback
        val steps = if (hint.explanationSteps.isNotEmpty()) {
            hint.explanationSteps
        } else {
            generateFallbackExplanationSteps(hint)
        }
        val currentStep = steps.getOrNull(explanationStepIndex)
        
        div("inline-explanation") {
            // Collapse button
            div("explanation-collapse-row") {
                button(classes = "explanation-collapse-btn") {
                    +"▲ Collapse"
                    onClickFunction = { e ->
                        e.stopPropagation()
                        showExplanation = false
                        render()
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
                        +currentStep.description
                    }
                }
            } else {
                // Fallback if no steps at all
                div("inline-step") {
                    div("step-description") {
                        +hint.description
                    }
                }
            }
            
            // Navigation (only show if more than one step)
            if (steps.size > 1) {
                div("inline-nav") {
                    button(classes = "inline-nav-btn ${if (explanationStepIndex <= 0) "disabled" else ""}") {
                        +"◀ Prev"
                        onClickFunction = { e ->
                            e.stopPropagation()
                            if (explanationStepIndex > 0) {
                                explanationStepIndex--
                                render()
                            }
                        }
                    }
                    span("step-indicator") { +"${explanationStepIndex + 1} / ${steps.size}" }
                    button(classes = "inline-nav-btn ${if (explanationStepIndex >= steps.size - 1) "disabled" else ""}") {
                        +"Next ▶"
                        onClickFunction = { e ->
                            e.stopPropagation()
                            if (explanationStepIndex < steps.size - 1) {
                                explanationStepIndex++
                                render()
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
    private fun generateFallbackExplanationSteps(hint: TechniqueMatchInfo): List<ExplanationStepDto> {
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
    private fun TagConsumer<HTMLElement>.renderChainLinesSvg(
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
                
                // Draw group highlights first (behind lines)
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
                
                // Draw lines
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
                
                append("</svg>")
            }
            unsafe {
                +svgContent
            }
        }
    }
    
    private fun TagConsumer<HTMLElement>.renderCell(
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
        
        // Check if cell is highlighted by current explanation step
        val isStepHighlighted = currentExplanationStep?.highlightCells?.contains(cellIndex) == true
        
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
            isStepHighlighted -> " hint-step-highlight"  // Current explanation step highlight
            isHintSolved -> " hint-solved-cell"
            isInCoverArea -> " hint-cover-area"
            else -> ""
        }
        
        // Get solved digit for this cell from the hint
        val hintSolvedDigit = selectedHint?.solvedCells?.find { it.cell == cellIndex }?.digit
        
        // Check if ALL candidates in this cell are covered by selected numbers (from either color)
        val allSelectedNumbers = selectedNumbers1 + selectedNumbers2
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
                        val inPrimary = n in selectedNumbers1
                        val inSecondary = n in selectedNumbers2
                        val pencilHighlightClass = if (highlightMode == HighlightMode.PENCIL) {
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
                        
                        val candidateClasses = buildString {
                            append("candidate")
                            if (n !in cell.displayCandidates) append(" hidden")
                            append(pencilHighlightClass)
                            if (isElimination) append(" hint-elimination")
                            if (isMatchingButNotEliminated) append(" hint-matching-not-eliminated")
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
                                    button(classes = "delete-btn") {
                                        +"🗑️"
                                        attributes["title"] = "Delete saved game"
                                        onClickFunction = {
                                            GameStateManager.deleteGame(summary.puzzleId)
                                            showToast("Game deleted")
                                            render()
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
                    div("category-header") {
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
                        // Hide completed toggle
                        label(classes = "toggle-label") {
                            input(InputType.checkBox, classes = "toggle-checkbox") {
                                checked = hideCompletedPuzzles
                                onInputFunction = {
                                    hideCompletedPuzzles = !hideCompletedPuzzles
                                    GameStateManager.setHideCompleted(hideCompletedPuzzles)
                                    render()
                                }
                            }
                            span { +"Hide Completed" }
                        }
                    }
                    
                    // Puzzle list
                    div("puzzle-list") {
                        val puzzles = PuzzleLibrary.getPuzzlesForCategory(selectedCategory)
                        if (puzzles.isEmpty() && selectedCategory == DifficultyCategory.CUSTOM) {
                            div("empty-message") {
                                +"No custom puzzles yet. Import a puzzle from the Import/Export page to add it here."
                            }
                        }
                        for ((index, puzzle) in puzzles.withIndex()) {
                            val existingGame = summaries.find { it.puzzleId == puzzle.id }
                            val isCompleted = existingGame?.isCompleted == true
                            
                            // Skip completed puzzles if hide completed is enabled
                            if (hideCompletedPuzzles && isCompleted) continue
                            
                            div("puzzle-item ${if (isCompleted) "completed" else ""}") {
                                span("puzzle-num") { +"#${index + 1}" }
                                if (puzzle.difficulty > 0) {
                                    span("difficulty") { +"★ ${puzzle.difficulty}" }
                                }
                                if (existingGame != null) {
                                    if (isCompleted) {
                                        span("status completed") { +"✓ Completed" }
                                        span("completion-stats") { 
                                            +"${formatTime(existingGame.elapsedTimeMs)} · ❌${existingGame.mistakeCount}"
                                        }
                                    } else {
                                        span("status progress") { +"${existingGame.progressPercent}%" }
                                    }
                                }
                                button(classes = "play-btn") {
                                    +if (isCompleted) "Replay" else "Play"
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
        // Use export format (notes shown: 1 = visible) for sharing compatibility with other apps
        val stateString = SavedGameState.createStateStringForExport(grid)
        
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
                                        id = "custom_${currentTimeMillis()}",
                                        puzzleString = puzzleStr,
                                        difficulty = 0f,
                                        category = DifficultyCategory.CUSTOM
                                    )
                                    
                                    // Save to custom puzzles library
                                    GameStateManager.saveCustomPuzzle(puzzle)
                                    
                                    // Check if we have a full state string (810 chars) with notes
                                    if (text.length >= 810) {
                                        // Import includes notes - parse and convert to eliminations
                                        // The import format uses notes (1 = shown), which we invert to eliminations
                                        val (values, userEliminations) = SavedGameState.parseStateStringFromNotesFormat(text)
                                        
                                        // Start new game with puzzle
                                        gameEngine.loadPuzzle(puzzleStr)
                                        
                                        // Apply values and eliminations
                                        for (i in 0 until 81) {
                                            val originalValue = puzzleStr[i].digitToIntOrNull() ?: 0
                                            if (values[i] != 0 && values[i] != originalValue) {
                                                gameEngine.setCellValue(i, values[i])
                                            }
                                            if (userEliminations[i].isNotEmpty()) {
                                                gameEngine.setUserEliminations(i, userEliminations[i])
                                            }
                                        }
                                        
                                        // Create saved game with elimination format
                                        val grid = gameEngine.getCurrentGrid()
                                        val stateWithEliminations = SavedGameState.createStateString(grid)
                                        currentGame = SavedGameState(
                                            puzzleId = puzzle.id,
                                            puzzleString = puzzleStr,
                                            currentState = stateWithEliminations,
                                            solution = null,
                                            category = DifficultyCategory.CUSTOM,
                                            difficulty = 0f,
                                            elapsedTimeMs = 0L,
                                            mistakeCount = 0,
                                            isCompleted = false,
                                            lastPlayedTimestamp = currentTimeMillis()
                                        )
                                        currentGame?.let { 
                                            GameStateManager.saveGame(it)
                                            GameStateManager.setCurrentGameId(it.puzzleId)
                                        }
                                        
                                        gameStartTime = currentTimeMillis()
                                        pausedTime = 0L
                                        selectedCell = null
                                        currentScreen = AppScreen.GAME
                                        render()
                                        showToast("✓ Full state imported with notes!")
                                    } else {
                                        // Just a puzzle string, start fresh
                                        startNewGame(puzzle)
                                        showToast("✓ Puzzle loaded and saved to Custom!")
                                    }
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
                        button(classes = "settings-nav-btn") {
                            +"ℹ️ About"
                            onClickFunction = {
                                showAboutModal = true
                                render()
                            }
                        }
                        button(classes = "settings-nav-btn") {
                            +"❓ Help"
                            onClickFunction = {
                                showHelpModal = true
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
                
                // Theme section
                div("section") {
                    h2 { +"🎨 Theme" }
                    p("setting-desc") { +"Choose your visual theme" }

                    div("mode-options theme-options") {
                        for (theme in Theme.entries) {
                            val isActive = currentTheme == theme
                            button(classes = "mode-btn theme-btn ${theme.name.lowercase()} ${if (isActive) "active" else ""}") {
                                +when (theme) {
                                    Theme.DARK -> "🌙 Dark"
                                    Theme.BLUE -> "🔵 Blue"
                                    Theme.LIGHT -> "☀️ Light"
                                    Theme.EPAPER -> "📖 ePaper"
                                }
                                onClickFunction = {
                                    currentTheme = theme
                                    GameStateManager.setTheme(theme)
                                    applyTheme(theme)
                                    render()
                                }
                            }
                        }
                    }

                    div("mode-explanation") {
                        +when (currentTheme) {
                            Theme.DARK -> "Pure dark theme with high contrast"
                            Theme.BLUE -> "Classic blue gradient theme"
                            Theme.LIGHT -> "Clean light theme"
                            Theme.EPAPER -> "High contrast for ePaper Devices"
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
                                // Clear all state when switching to FAST mode
                                selectedNumbers1.clear()
                                selectedNumbers2.clear()
                                selectedCell = null
                                render()
                            }
                        }
                        button(classes = "mode-btn advanced ${if (playMode == PlayMode.ADVANCED) "active" else ""}") {
                            +"🎯 Advanced"
                            onClickFunction = {
                                playMode = PlayMode.ADVANCED
                                GameStateManager.setPlayMode(PlayMode.ADVANCED)
                                render()
                            }
                        }
                    }
                    
                    div("mode-explanation") {
                        +when (playMode) {
                            PlayMode.FAST -> "Click number, then click cell to fill. Quick and simple."
                            PlayMode.ADVANCED -> "Two number rows for highlighting. Select multiple numbers per color. Cells with ALL selected numbers highlight. Click cell for action buttons."
                        }
                    }
                }
                
                // Two-number highlight info
                div("section highlight-info") {
                    h2 { +"🔵🔴 Multi-Number Highlighting" }
                    div("color-legend") {
                        div("legend-item") {
                            span("color-box primary") {}
                            span { +"Primary row (blue)" }
                        }
                        div("legend-item") {
                            span("color-box secondary") {}
                            span { +"Secondary row (red)" }
                        }
                        div("legend-item") {
                            span("color-box both") {}
                            span { +"Intersection (both colors)" }
                        }
                    }
                    p("setting-desc") {
                        +"In Advanced mode, toggle numbers in each row. Only cells containing ALL selected numbers in a row will highlight in that color."
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

data class ThemeColors(
    // Core backgrounds
    val bgPrimary: Triple<Int, Int, Int>,      // Main background
    val bgSecondary: Triple<Int, Int, Int>,    // Secondary background (cards, modals)
    val bgTertiary: Triple<Int, Int, Int>,     // Tertiary background (buttons, highlights)
    val desat: Triple<Int, Int, Int>,

    // Core accents
    val accentPrimary: Triple<Int, Int, Int>,  // Primary accent (blue)
    val accentSecondary: Triple<Int, Int, Int>,// Secondary accent (red)
    val accentTertiary: Triple<Int, Int, Int>, // Tertiary accent (yellow)

    // Core text
    val textPrimary: Triple<Int, Int, Int>,    // Primary text
    val textSecondary: Triple<Int, Int, Int>,  // Secondary text (muted)
    val textTertiary: Triple<Int, Int, Int>,   // Tertiary text (very muted)

    // Grid status colors
    val gridYes: Triple<Int, Int, Int>,        // Success/green for grid
    val gridNeutral: Triple<Int, Int, Int>,    // Neutral/grey for grid
    val gridNo: Triple<Int, Int, Int>,          // Error/red for grid
    
    val btnOpacity: Double,
    val btnHoverOpacity: Double
)

private val THEME_COLORS = mapOf(
    Theme.DARK to ThemeColors(
        bgPrimary = Triple(0, 0, 0),
        bgSecondary = Triple(26, 26, 26),
        bgTertiary = Triple(100, 100, 100),
        accentPrimary = Triple(100, 181, 246),
        accentSecondary = Triple(255, 82, 82),
        accentTertiary = Triple(255, 193, 7),
        textPrimary = Triple(255, 255, 255),
        textSecondary = Triple(204, 204, 204),
        textTertiary = Triple(136, 136, 136),
        gridYes = Triple(76, 175, 80),
        gridNeutral = Triple(158, 158, 158),
        gridNo = Triple(244, 67, 54),
        desat = Triple(255, 255, 255),
        btnOpacity = 0.05,
        btnHoverOpacity = 0.1
    ),
    Theme.BLUE to ThemeColors(
        bgPrimary = Triple(26, 26, 46),
        bgSecondary = Triple(40, 50, 90),
        bgTertiary = Triple(100, 100, 150),
        accentPrimary = Triple(80, 190, 246),
        accentSecondary = Triple(80, 240, 82),
        accentTertiary = Triple(255, 255, 0),
        textPrimary = Triple(255, 255, 255),
        textSecondary = Triple(204, 204, 204),
        textTertiary = Triple(136, 136, 136),
        gridYes = Triple(100, 255, 100),
        gridNeutral = Triple(158, 158, 158),
        gridNo = Triple(255, 100, 100),
        desat = Triple(255, 255, 255),
        btnOpacity = 0.15,
        btnHoverOpacity = 0.25
    ),
    Theme.LIGHT to ThemeColors(
        bgPrimary = Triple(255, 255, 255),
        bgSecondary = Triple(200, 200, 220),
        bgTertiary = Triple(150, 150, 200),
        accentPrimary = Triple(0, 123, 255),
        accentSecondary = Triple(220, 53, 69),
        accentTertiary = Triple(255, 193, 7),
        textPrimary = Triple(10, 10, 10),
        textSecondary = Triple(80, 80, 100),
        textTertiary = Triple(120, 120, 140),
        gridYes = Triple(40, 167, 69),
        gridNeutral = Triple(158, 158, 158),
        gridNo = Triple(220, 53, 69),
        desat = Triple(0, 0, 0),
        btnOpacity = 0.1,
        btnHoverOpacity = 0.15
    ),
    Theme.EPAPER to ThemeColors(
        bgPrimary = Triple(255, 255, 255),
        bgSecondary = Triple(200, 200, 200),
        bgTertiary = Triple(150, 150, 150),
        accentPrimary = Triple(0, 102, 204),
        accentSecondary = Triple(204, 0, 0),
        accentTertiary = Triple(255, 153, 0),
        textPrimary = Triple(0, 0, 0),
        textSecondary = Triple(51, 51, 51),
        textTertiary = Triple(102, 102, 102),
        gridYes = Triple(0, 153, 0),
        gridNeutral = Triple(128, 128, 128),
        gridNo = Triple(204, 0, 0),
        desat = Triple(0, 0, 0),
        btnOpacity = 0.1,
        btnHoverOpacity = 0.2
    )
)

private fun rgbToString(rgb: Triple<Int, Int, Int>): String {
    return "${rgb.first}, ${rgb.second}, ${rgb.third}"
}

private fun applyTheme(theme: Theme) {
    val colors = THEME_COLORS[theme] ?: THEME_COLORS[Theme.BLUE]!!
    val root = document.documentElement.asDynamic()
    val style = root.style

    // Core color variables
    style.setProperty("--color-bg-primary", rgbToString(colors.bgPrimary))
    style.setProperty("--color-bg-secondary", rgbToString(colors.bgSecondary))
    style.setProperty("--color-bg-tertiary", rgbToString(colors.bgTertiary))

    style.setProperty("--color-accent-primary", rgbToString(colors.accentPrimary))
    style.setProperty("--color-accent-secondary", rgbToString(colors.accentSecondary))
    style.setProperty("--color-accent-tertiary", rgbToString(colors.accentTertiary))
    style.setProperty("--color-btn-opacity", colors.btnOpacity.toString())
    style.setProperty("--color-btn-hover-opacity", colors.btnHoverOpacity.toString())

    style.setProperty("--color-text-primary", rgbToString(colors.textPrimary))
    style.setProperty("--color-text-secondary", rgbToString(colors.textSecondary))
    style.setProperty("--color-text-tertiary", rgbToString(colors.textTertiary))

    style.setProperty("--color-grid-yes", rgbToString(colors.gridYes))
    style.setProperty("--color-grid-neutral", rgbToString(colors.gridNeutral))
    style.setProperty("--color-grid-no", rgbToString(colors.gridNo))

    // Derived color variables (for compatibility with existing CSS)
    style.setProperty("--color-accent-success", rgbToString(colors.gridYes))
    style.setProperty("--color-accent-info", rgbToString(colors.accentPrimary))
    style.setProperty("--color-accent-warning", rgbToString(colors.accentTertiary))
    style.setProperty("--color-accent-error", rgbToString(colors.gridNo))
    style.setProperty("--color-accent-desat", rgbToString(colors.desat))

    // Border and shadow (derived from backgrounds and black/white)
    style.setProperty("--color-border", rgbToString(colors.bgSecondary))
    style.setProperty("--color-shadow", "0, 0, 0")  // Black for shadows

    // Gradient background - need to wrap RGB values in rgb() for valid CSS
    style.setProperty("--gradient-bg", "linear-gradient(135deg, rgb(${rgbToString(colors.bgPrimary)}) 0%, rgb(${rgbToString(colors.bgSecondary)}) 100%")
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

        /* Theme colors - these are set dynamically by JavaScript */
        --color-bg-primary: 26, 26, 46;
        --color-bg-secondary: 22, 33, 62;
        --color-bg-tertiary: 15, 52, 96;
        --color-accent-primary: 100, 181, 246;
        --color-accent-secondary: 255, 82, 82;
        --color-accent-tertiary: 255, 193, 7;
        --color-text-primary: 255, 255, 255;
        --color-text-secondary: 204, 204, 204;
        --color-text-tertiary: 136, 136, 136;
        --color-grid-yes: 76, 175, 80;
        --color-grid-neutral: 158, 158, 158;
        --color-grid-no: 244, 67, 54;
        /* Derived colors */
        --color-accent-success: 76, 175, 80;
        --color-accent-info: 100, 181, 246;
        --color-accent-warning: 255, 193, 7;
        --color-accent-error: 244, 67, 54;
        --color-border: 22, 33, 62;
        --color-shadow: 0, 0, 0;
        --color-accent-desat: 255, 255, 255;
        --gradient-bg: linear-gradient(135deg, rgb(26,26,46) 0%, rgb(22,33,62) 50%, rgb(15,52,96) 100%);
    }
    
    html, body {
        height: 100%;
        overflow: hidden;
    }
    
    body {
        font-family: 'SF Pro Display', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
        background: var(--gradient-bg);
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
    
    .sudoku-container-wrapper {
        width: 100%;
        height: 100%;
        display: flex;
        justify-content: center;
        align-items: center;
        overflow: hidden;
    }
    
    .sudoku-container {
        background: rgba(var(--color-accent-desat), 0.05);
        backdrop-filter: blur(10px);
        border-radius: clamp(12px, 3vmin, 24px);
        padding: clamp(12px, 3vmin, 24px);
        box-shadow: 0 25px 50px -12px rgba(var(--color-shadow), 0.5);
        width: min(100%, calc(var(--grid-size) + 48px));
        display: flex;
        flex-direction: column;
        overflow: hidden;
        transition: width 0.2s ease, transform 0.2s ease;
        transform-origin: center center;
    }
    
    /* Expand container when hints sidebar is shown in landscape */
    .sudoku-container.hints-expanded {
        width: min(100%, calc(var(--grid-size) + 380px));
    }
    
    .header {
        text-align: center;
        margin-bottom: clamp(8px, 2vmin, 20px);
        flex-shrink: 0;
        position: relative;
    }
    
    /* Position back button to the left */
    .header .back-btn {
        position: absolute;
        left: 0;
        top: 50%;
        transform: translateY(-50%);
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
        background: rgba(var(--color-accent-desat), var(--color-btn-opacity));
        color: rgba(var(--color-text-primary), 0.8);
        transition: all 0.15s ease;
    }

    .nav-btn:hover, .back-btn:hover {
        background: rgba(var(--color-accent-desat), var(--color-btn-hover-opacity));
    }
    
    .header h1 {
        font-size: clamp(1.1rem, calc(1rem + 2vmin), 2rem);
        font-weight: 700;
        color: rgb(var(--color-accent-primary));
        letter-spacing: -0.02em;
    }

    .powered-by {
        color: rgb(var(--color-text-primary));
        display: block;
    }
    
    .game-info {
        display: flex;
        justify-content: center;
        gap: clamp(6px, 1.5vmin, 12px);
        flex-wrap: wrap;
        margin-top: clamp(4px, 1vmin, 8px);
        margin-bottom: clamp(2px, 1vmin, 4px);
    }
    
    .game-info span {
        font-size: clamp(0.6rem, calc(0.55rem + 0.5vmin), 0.8rem);
        padding: clamp(2px, 0.5vmin, 4px) clamp(4px, 1vmin, 4px);
        border-radius: 4px;
        background: rgba(var(--color-bg-tertiary), 0.2);
        color: rgba(var(--color-text-primary), 0.7);
    }
    
    .category {
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.05em;
    }
    
    .category.easy { background: rgba(var(--color-accent-success), 0.3); color: rgb(var(--color-accent-success)); }
    .category.medium { background: rgba(var(--color-accent-warning), 0.3); color: rgb(var(--color-accent-warning)); }
    .category.hard { background: rgba(var(--color-accent-warning), 0.3); color: rgb(var(--color-accent-warning)); }
    .category.diabolical { background: rgba(var(--color-accent-error), 0.3); color: rgb(var(--color-accent-error)); }
    
    .game-area {
        display: flex;
        flex-direction: column;
        gap: clamp(8px, 2vmin, 16px);
        flex: 1;
        min-height: 0;
    }
    
    /* Grid container for SVG overlay positioning */
    .sudoku-grid-container {
        position: relative;
        width: var(--grid-size);
        max-width: 100%;
        margin: 0 auto;
    }
    
    /* SVG overlay container for chain lines */
    .chain-lines-container {
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        pointer-events: none;
        z-index: 10;
    }
    
    /* SVG overlay for chain lines */
    .chain-lines-overlay {
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        pointer-events: none;
        z-index: 10;
    }
    
    .board-chain-line {
        fill: none;
        stroke-width: 3;
        stroke-linecap: round;
        filter: drop-shadow(0 0 2px rgba(0, 0, 0, 0.5));
    }
    
    .board-chain-line.strong-link {
        stroke: rgb(var(--color-accent-success));
        stroke-width: 4;
    }
    
    .board-chain-line.weak-link {
        stroke: rgb(var(--color-accent-warning));
        stroke-dasharray: 8 4;
    }
    
    .board-candidate-highlight {
        opacity: 0.5;
        filter: drop-shadow(0 0 3px rgba(0, 0, 0, 0.3));
    }
    
    .board-candidate-highlight.group-on {
        fill: rgba(193, 155, 249, 0.8);
    }
    
    .board-candidate-highlight.group-off {
        fill: rgba(123, 249, 155, 0.8);
    }
    
    .board-candidate-highlight.group-als {
        fill: rgba(249, 200, 123, 0.8);
    }
    
    .board-candidate-highlight.group-default {
        fill: rgba(var(--color-accent-info), 0.6);
    }
    
    /* SVG highlight states for interactive chain notation */
    .board-chain-line.svg-line-highlight {
        stroke-width: 8 !important;
        filter: drop-shadow(0 0 6px rgba(255, 255, 255, 0.8)) !important;
    }
    
    .board-candidate-highlight.svg-highlight {
        r: 18;
        opacity: 1;
        filter: drop-shadow(0 0 8px rgba(255, 255, 255, 0.9)) !important;
    }
    
    /* Interactive chain notation styles */
    .interactive-description {
        line-height: 1.6;
    }
    
    .chain-cell-ref {
        background: rgba(var(--color-accent-info), 0.2);
        padding: 2px 4px;
        border-radius: 4px;
        cursor: pointer;
        transition: all 0.15s ease;
        font-family: 'JetBrains Mono', 'Fira Code', monospace;
        font-size: 0.9em;
    }
    
    .chain-cell-ref:hover {
        background: rgba(var(--color-accent-info), 0.5);
        box-shadow: 0 0 8px rgba(var(--color-accent-info), 0.5);
    }
    
    .chain-link-ref {
        padding: 2px 6px;
        border-radius: 4px;
        cursor: pointer;
        transition: all 0.15s ease;
        font-weight: 600;
        font-size: 0.85em;
    }
    
    .chain-link-strong {
        background: rgba(var(--color-accent-success), 0.2);
        color: rgb(var(--color-accent-success));
    }
    
    .chain-link-strong:hover {
        background: rgba(var(--color-accent-success), 0.5);
        box-shadow: 0 0 8px rgba(var(--color-accent-success), 0.5);
    }
    
    .chain-link-weak {
        background: rgba(var(--color-accent-warning), 0.2);
        color: rgb(var(--color-accent-warning));
    }
    
    .chain-link-weak:hover {
        background: rgba(var(--color-accent-warning), 0.5);
        box-shadow: 0 0 8px rgba(var(--color-accent-warning), 0.5);
    }
    
    .chain-text {
        /* Normal text styling */
    }
    
    /* Cell highlight from chain interaction */
    .cell.chain-node-highlight {
        box-shadow: inset 0 0 0 3px rgba(var(--color-accent-info), 0.8), 0 0 12px rgba(var(--color-accent-info), 0.6) !important;
    }
    
    .candidate.chain-candidate-highlight {
        background: rgba(var(--color-accent-info), 0.7) !important;
        color: white !important;
        border-radius: 50%;
        font-weight: bold;
    }
    
    .sudoku-grid {
        background: rgba(var(--color-bg-primary), 0.6);
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
        background: rgba(var(--color-bg-tertiary), 0.15);
        border-radius: clamp(2px, 0.5vmin, 4px);
        display: flex;
        justify-content: center;
        align-items: center;
        cursor: pointer;
        transition: all 0.15s ease;
        position: relative;
    }

    .cell:hover { background: rgba(var(--color-bg-tertiary), 0.3); }
    .cell.selected { background: rgba(var(--color-text-primary), 0.1); box-shadow: inset 0 0 0 2px rgba(var(--color-text-primary), 0.7); }
    .cell.given { background: rgba(var(--color-bg-tertiary), 0.25); }
    .cell.solved { background: rgba(var(--color-bg-tertiary), 0.15); }
    .cell.mistake { background: rgba(var(--color-accent-error), 0.5); }
    .cell.box-left { margin-left: clamp(2px, 0.5vmin, 4px); }
    .cell.box-top { margin-top: clamp(2px, 0.5vmin, 4px); }
    
    .cell-value {
        font-size: clamp(0.9rem, calc(var(--grid-size) / 18), 2rem);
        font-weight: 600;
        color: rgb(var(--color-text-primary));
    }

    .cell.given .cell-value { color: rgba(var(--color-text-primary), 0.95); }
    .cell.solved .cell-value { color: rgba(var(--color-text-primary), 0.8); }
    .cell:not(.given):not(.solved) .cell-value { color: rgb(var(--color-accent-secondary)); }
    .cell.mistake .cell-value { color: rgb(var(--color-accent-error)); }
    
    .candidates {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        width: 100%;
        height: 100%;
        padding: clamp(1px, 0.25vmin, 2px);
    }
    
    .candidate {
        font-size: clamp(0.35rem, calc(var(--grid-size) / 60), 0.6rem);
        color: rgba(var(--color-text-primary), 0.85);
        display: flex;
        justify-content: center;
        align-items: center;
    }
    
    .candidate.hidden { visibility: hidden; }
    
    /* Hint highlighting for cells */
    .cell.hint-cover-area {
        background: rgba(var(--color-accent-info), 0.2);
    }
    
    .cell.hint-solved-cell {
        background: rgba(var(--color-accent-success), 0.3);
    }
    
    .cell.hint-step-highlight {
        background: rgba(var(--color-accent-warning), 0.3);
        box-shadow: inset 0 0 0 2px rgba(var(--color-accent-warning), 0.6);
    }
    
    /* Hint highlighting for candidates */
    .candidate.hint-elimination {
        color: rgb(var(--color-accent-error));
        font-weight: bold;
        text-decoration: line-through;
    }
    
    .candidate.hint-matching-not-eliminated {
        color: rgb(var(--color-accent-info));
        font-weight: bold;
    }
    
    .candidate.hint-solved {
        color: rgb(var(--color-accent-success));
        font-weight: bold;
        background: rgba(var(--color-accent-success), 0.3);
        border-radius: 2px;
    }
    
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
        background: rgba(var(--color-accent-desat), 0.2);
        color: rgba(var(--color-text-primary), 0.8);
    }

    .toggle-btn:hover, .erase-btn:hover, .hint-btn:hover, .solve-btn:hover {
        background: rgba(var(--color-accent-desat), 0.3);
        transform: translateY(-1px);
    }

    .toggle-btn.active { background: rgb(var(--color-accent-secondary)); color: rgb(var(--color-text-primary)); }
    .erase-btn { background: rgba(var(--color-accent-error), 0.4); color: rgb(var(--color-accent-error)); }
    .hint-btn { background: rgba(var(--color-accent-warning), 0.4); color: rgb(var(--color-accent-warning)); }
    .hint-btn.active { background: rgb(var(--color-accent-warning)); color: rgb(var(--color-bg-primary)); }
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
        align-items: flex-start;
        min-height: 0;
        overflow: hidden;
    }
    
    .main-content.landscape-hints .game-area {
        flex: 0 0 auto;
        /* Don't shrink the game area - let the container expand instead */
    }
    
    /* Landscape Hint Sidebar */
    .hint-sidebar {
        flex: 1;
        min-width: 200px;
        max-width: 320px;
        align-self: stretch;
        background: rgba(var(--color-bg-tertiary), 0.1);
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
        color: rgb(var(--color-accent-warning));
    }
    
    .hint-count {
        color: rgba(var(--color-text-primary), 0.5);
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
        background: rgba(var(--color-bg-tertiary), 0.15);
        border-radius: clamp(6px, 1vmin, 10px);
        padding: 0;
        cursor: pointer;
        transition: all 0.15s ease;
        border: 2px solid transparent;
    }

    .hint-item:hover {
        background: rgba(var(--color-bg-tertiary), 0.25);
    }

    .hint-item.selected {
        background: rgba(var(--color-accent-warning), 0.3);
        border-color: rgb(var(--color-accent-warning));
        overflow: visible;
    }
    
    .hint-item.expanded {
        background: rgba(var(--color-accent-warning), 0.15);
        border-color: rgb(var(--color-accent-warning));
        overflow: visible;
    }
    
    .hint-item-header {
        padding: clamp(8px, 1.5vmin, 14px);
        display: flex;
        flex-direction: column;
        gap: clamp(4px, 0.8vmin, 8px);
    }
    
    .hint-item-content {
        flex: 1;
    }

    .hint-technique {
        font-weight: 600;
        color: rgb(var(--color-accent-info));
        font-size: clamp(0.75rem, calc(0.7rem + 0.4vmin), 0.95rem);
        margin-bottom: 4px;
    }

    .hint-description {
        color: rgba(var(--color-text-primary), 0.7);
        font-size: clamp(0.65rem, calc(0.6rem + 0.3vmin), 0.85rem);
        line-height: 1.3;
        word-break: break-word;
    }
    
    /* Inline Explanation Styles */
    .inline-explanation {
        padding: clamp(8px, 1.5vmin, 14px);
        padding-top: 0;
        border-top: 1px solid rgba(var(--color-text-primary), 0.1);
        margin-top: clamp(4px, 0.8vmin, 8px);
    }
    
    .explanation-collapse-row {
        display: flex;
        justify-content: flex-end;
        margin-bottom: clamp(6px, 1vmin, 10px);
    }
    
    .explanation-collapse-btn {
        padding: clamp(4px, 0.6vmin, 6px) clamp(8px, 1.2vmin, 12px);
        border: none;
        border-radius: clamp(4px, 0.6vmin, 6px);
        background: rgba(var(--color-accent-warning), 0.2);
        color: rgb(var(--color-accent-warning));
        font-size: clamp(0.6rem, calc(0.55rem + 0.3vmin), 0.75rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
    }
    
    .explanation-collapse-btn:hover {
        background: rgba(var(--color-accent-warning), 0.3);
    }
    
    .inline-eureka {
        background: rgba(var(--color-bg-tertiary), 0.3);
        border-radius: clamp(4px, 0.6vmin, 6px);
        padding: clamp(6px, 1vmin, 10px);
        margin-bottom: clamp(8px, 1.2vmin, 12px);
        font-family: 'JetBrains Mono', 'Fira Code', monospace;
        font-size: clamp(0.6rem, calc(0.55rem + 0.3vmin), 0.75rem);
        overflow-x: auto;
    }
    
    .eureka-label {
        color: rgba(var(--color-text-primary), 0.6);
    }
    
    .eureka-notation {
        color: rgb(var(--color-accent-warning));
    }
    
    .inline-step {
        background: rgba(var(--color-bg-tertiary), 0.2);
        border-radius: clamp(6px, 1vmin, 10px);
        padding: clamp(8px, 1.2vmin, 12px);
        margin-bottom: clamp(8px, 1.2vmin, 12px);
    }
    
    .step-header {
        display: flex;
        align-items: center;
        gap: clamp(6px, 1vmin, 10px);
        margin-bottom: clamp(6px, 1vmin, 10px);
    }
    
    .step-number {
        background: rgba(var(--color-accent-info), 0.3);
        color: rgb(var(--color-accent-info));
        padding: clamp(2px, 0.4vmin, 4px) clamp(6px, 0.8vmin, 10px);
        border-radius: clamp(3px, 0.5vmin, 5px);
        font-size: clamp(0.55rem, calc(0.5rem + 0.25vmin), 0.7rem);
        font-weight: 600;
    }
    
    .step-title {
        font-size: clamp(0.7rem, calc(0.65rem + 0.35vmin), 0.9rem);
        font-weight: 600;
        color: rgb(var(--color-text-primary));
    }
    
    .step-description {
        color: rgba(var(--color-text-primary), 0.8);
        font-size: clamp(0.6rem, calc(0.55rem + 0.3vmin), 0.8rem);
        line-height: 1.4;
    }
    
    .inline-nav {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: clamp(6px, 1vmin, 10px);
    }
    
    .inline-nav-btn {
        padding: clamp(4px, 0.8vmin, 8px) clamp(10px, 1.5vmin, 14px);
        border: none;
        border-radius: clamp(4px, 0.6vmin, 6px);
        background: rgba(var(--color-accent-info), 0.2);
        color: rgb(var(--color-accent-info));
        font-size: clamp(0.55rem, calc(0.5rem + 0.3vmin), 0.75rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
    }
    
    .inline-nav-btn:hover:not(.disabled) {
        background: rgba(var(--color-accent-info), 0.3);
    }
    
    .inline-nav-btn.disabled {
        opacity: 0.4;
        cursor: not-allowed;
    }
    
    .step-indicator {
        color: rgba(var(--color-text-primary), 0.6);
        font-size: clamp(0.55rem, calc(0.5rem + 0.25vmin), 0.7rem);
    }
    
    /* Full Explanation View (replaces list in landscape sidebar) */
    .explanation-view {
        display: flex;
        flex-direction: column;
        height: 100%;
        gap: clamp(8px, 1.5vmin, 14px);
    }
    
    .explanation-view-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: clamp(8px, 1.2vmin, 12px);
        flex-shrink: 0;
    }
    
    .explanation-back-btn {
        padding: clamp(6px, 1vmin, 10px) clamp(10px, 1.5vmin, 14px);
        border: none;
        border-radius: clamp(4px, 0.8vmin, 8px);
        background: rgba(var(--color-text-primary), 0.1);
        color: rgb(var(--color-text-primary));
        font-size: clamp(0.65rem, calc(0.6rem + 0.35vmin), 0.85rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
    }
    
    .explanation-back-btn:hover {
        background: rgba(var(--color-text-primary), 0.2);
    }
    
    .hint-position-badge {
        background: rgba(var(--color-accent-warning), 0.2);
        color: rgb(var(--color-accent-warning));
        padding: clamp(4px, 0.6vmin, 6px) clamp(8px, 1vmin, 12px);
        border-radius: clamp(4px, 0.6vmin, 6px);
        font-size: clamp(0.6rem, calc(0.55rem + 0.3vmin), 0.75rem);
        font-weight: 600;
    }
    
    .explanation-technique-info {
        background: rgba(var(--color-accent-warning), 0.15);
        border-radius: clamp(6px, 1vmin, 10px);
        padding: clamp(10px, 1.5vmin, 16px);
        border-left: 3px solid rgb(var(--color-accent-warning));
        flex-shrink: 0;
    }
    
    .explanation-technique-name {
        font-size: clamp(0.8rem, calc(0.75rem + 0.4vmin), 1rem);
        font-weight: 700;
        color: rgb(var(--color-accent-warning));
        margin-bottom: clamp(4px, 0.6vmin, 8px);
    }
    
    .explanation-technique-desc {
        font-size: clamp(0.65rem, calc(0.6rem + 0.3vmin), 0.85rem);
        color: rgba(var(--color-text-primary), 0.8);
        line-height: 1.4;
    }
    
    .explanation-eureka {
        background: rgba(var(--color-bg-tertiary), 0.3);
        border-radius: clamp(4px, 0.6vmin, 6px);
        padding: clamp(8px, 1.2vmin, 12px);
        font-family: 'JetBrains Mono', 'Fira Code', monospace;
        font-size: clamp(0.55rem, calc(0.5rem + 0.25vmin), 0.7rem);
        overflow-x: auto;
        flex-shrink: 0;
    }
    
    .explanation-step-content {
        flex: 1;
        background: rgba(var(--color-bg-tertiary), 0.2);
        border-radius: clamp(6px, 1vmin, 10px);
        padding: clamp(10px, 1.5vmin, 16px);
        overflow-y: auto;
    }
    
    .explanation-step-content .step-header {
        display: flex;
        align-items: center;
        gap: clamp(8px, 1.2vmin, 12px);
        margin-bottom: clamp(8px, 1.2vmin, 12px);
    }
    
    .explanation-step-content .step-description {
        color: rgba(var(--color-text-primary), 0.85);
        font-size: clamp(0.65rem, calc(0.6rem + 0.3vmin), 0.85rem);
        line-height: 1.5;
    }
    
    .explanation-nav {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: clamp(8px, 1.2vmin, 12px);
        flex-shrink: 0;
    }
    
    .explanation-nav-btn {
        padding: clamp(6px, 1vmin, 10px) clamp(12px, 1.8vmin, 18px);
        border: none;
        border-radius: clamp(4px, 0.8vmin, 8px);
        background: rgba(var(--color-accent-info), 0.2);
        color: rgb(var(--color-accent-info));
        font-size: clamp(0.6rem, calc(0.55rem + 0.3vmin), 0.8rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
    }
    
    .explanation-nav-btn:hover:not(.disabled) {
        background: rgba(var(--color-accent-info), 0.3);
    }
    
    .explanation-nav-btn.disabled {
        opacity: 0.4;
        cursor: not-allowed;
    }
    
    .hint-empty {
        text-align: center;
        color: rgba(var(--color-text-primary), 0.5);
        padding: clamp(16px, 3vmin, 32px);
    }

    .hint-close-btn {
        margin-top: clamp(8px, 1.5vmin, 16px);
        padding: clamp(8px, 1.5vmin, 14px);
        border: none;
        border-radius: clamp(6px, 1vmin, 10px);
        background: rgba(var(--color-accent-error), 0.3);
        color: rgb(var(--color-accent-error));
        font-size: clamp(0.7rem, calc(0.65rem + 0.4vmin), 0.9rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
        flex-shrink: 0;
    }

    .hint-close-btn:hover {
        background: rgba(var(--color-accent-error), 0.4);
    }
    
    /* Portrait Hint Card */
    .hint-card {
        background: rgba(var(--color-bg-tertiary), 0.1);
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
        background: rgba(var(--color-accent-warning), 0.2);
        color: rgb(var(--color-accent-warning));
        font-size: clamp(0.65rem, calc(0.6rem + 0.35vmin), 0.85rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
    }
    
    .hint-nav-btn:hover:not(.disabled) {
        background: rgba(var(--color-accent-warning), 0.3);
    }
    
    .hint-nav-btn.disabled {
        opacity: 0.4;
        cursor: not-allowed;
    }
    
    .hint-position {
        color: rgba(var(--color-text-primary), 0.6);
        font-size: clamp(0.7rem, calc(0.65rem + 0.3vmin), 0.9rem);
    }
    
    .hint-close-btn-small {
        padding: clamp(4px, 0.8vmin, 8px) clamp(8px, 1.5vmin, 14px);
        border: none;
        border-radius: clamp(4px, 0.8vmin, 8px);
        background: rgba(var(--color-accent-error), 0.2);
        color: rgb(var(--color-accent-error));
        font-size: clamp(0.7rem, calc(0.65rem + 0.4vmin), 0.9rem);
        font-weight: bold;
        cursor: pointer;
        transition: all 0.15s ease;
    }
    
    .hint-close-btn-small:hover {
        background: rgba(var(--color-accent-error), 0.3);
    }
    
    .hint-content {
        padding: clamp(8px, 1.5vmin, 16px);
        background: rgba(var(--color-bg-tertiary), 0.05);
        border-radius: clamp(6px, 1vmin, 10px);
    }
    
    .hint-content .hint-technique {
        margin-bottom: clamp(6px, 1vmin, 12px);
    }
    
    .hint-content.hint-empty {
        text-align: center;
        color: rgba(var(--color-text-primary), 0.5);
    }
    
    /* Hint item with explain button */
    .hint-item-content {
        flex: 1;
    }
    
    .hint-explain-btn {
        margin-top: clamp(6px, 1vmin, 10px);
        padding: clamp(4px, 0.8vmin, 8px) clamp(8px, 1.5vmin, 14px);
        border: none;
        border-radius: clamp(4px, 0.8vmin, 8px);
        background: rgba(var(--color-accent-info), 0.3);
        color: rgb(var(--color-accent-info));
        font-size: clamp(0.65rem, calc(0.6rem + 0.35vmin), 0.8rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
        display: block;
        width: 100%;
    }
    
    .hint-explain-btn:hover {
        background: rgba(var(--color-accent-info), 0.4);
        transform: translateY(-1px);
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
        background: rgba(var(--color-accent-desat), var(--color-btn-opacity));
        color: rgb(var(--color-text-primary));
    }
    
    .num-btn:hover { background: rgba(var(--color-accent-desat), 0.25); transform: scale(1.05); }
    .num-btn:active { transform: scale(0.95); }
    
    .status {
        text-align: center;
        padding: clamp(6px, 1.5vmin, 12px);
        color: rgba(var(--color-text-primary), 0.6);
        font-size: clamp(0.7rem, calc(0.65rem + 0.4vmin), 0.9rem);
        flex-shrink: 0;
    }
    
    .toast {
        position: fixed;
        bottom: clamp(60px, 15vh, 120px);
        left: 50%;
        transform: translateX(-50%);
        background: rgba(var(--color-bg-primary), 0.9);
        color: rgb(var(--color-text-primary));
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
        background: rgba(var(--color-bg-tertiary), 0.1);
        border-radius: clamp(6px, 1.5vmin, 12px);
        padding: clamp(10px, 2.5vmin, 20px);
        margin-bottom: clamp(10px, 2.5vmin, 20px);
    }
    
    .section h2 {
        color: rgb(var(--color-text-primary));
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
        background: rgba(var(--color-bg-tertiary), 0.2);
        color: rgba(var(--color-text-primary), 0.7);
        transition: all 0.15s ease;
    }

    .tab-btn:hover { background: rgba(var(--color-bg-tertiary), 0.4); }
    .tab-btn.active {
        background: rgba(var(--color-accent-primary), 0.4);
        color: rgb(var(--color-text-primary));
    }
    
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
        background: rgba(var(--color-bg-tertiary), 0.5);
        border-radius: 8px;
        transition: all 0.15s ease;
    }

    .puzzle-item:hover, .game-item:hover {
        background: rgba(var(--color-bg-tertiary), 0.2);
    }
    
    .puzzle-item.completed { opacity: 0.6; }
    
    .puzzle-num {
        color: rgba(var(--color-text-primary), 0.5);
        font-size: 0.8rem;
        width: 30px;
    }
    
    .difficulty {
        color: rgb(var(--color-accent-warning));
        font-size: 0.8rem;
    }
    
    .status {
        font-size: 0.75rem;
        padding: 2px 6px;
        border-radius: 4px;
    }
    
    .status.completed { background: rgba(var(--color-accent-success), 0.3); color: rgb(var(--color-accent-success)); }
    .status.progress { background: rgba(var(--color-accent-warning), 0.3); color: rgb(var(--color-accent-warning)); }
    
    /* Game item text elements */
    .game-item .progress {
        color: rgba(var(--color-text-primary), 0.8);
        font-size: 0.8rem;
    }

    .game-item .time {
        color: rgba(var(--color-text-primary), 0.6);
        font-size: 0.75rem;
    }

    .game-item .mistakes {
        color: rgba(var(--color-text-primary), 0.6);
        font-size: 0.75rem;
    }
    
    .play-btn, .resume-btn {
        margin-left: auto;
        padding: 6px 12px;
        border: none;
        border-radius: 6px;
        font-size: 0.75rem;
        font-weight: 600;
        cursor: pointer;
        background: rgba(var(--color-accent-desat), var(--color-btn-opacity));
        color: rgba(var(--color-text-primary), 0.8);
        transition: all 0.15s ease;
    }

    .play-btn:hover, .resume-btn:hover {
        background: rgba(var(--color-accent-desat), var(--color-btn-hover-opacity));
        transform: translateY(-1px);
    }
    
    /* Import/Export styles */
    .export-option {
        margin-bottom: 12px;
    }
    
    .export-option label {
        display: block;
        color: rgba(var(--color-text-primary), 0.7);
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
        border: 1px solid rgba(var(--color-border), 0.3);
        border-radius: 6px;
        background: rgba(var(--color-bg-primary), 0.4);
        color: rgba(var(--color-text-primary), 0.8);
        font-family: monospace;
        font-size: 0.75rem;
    }
    
    .copy-btn, .paste-btn, .load-btn {
        background: rgba(var(--color-accent-desat), var(--color-btn-opacity));
        color: rgba(var(--color-text-primary), 0.8);
        padding: 8px 12px;
        border: none;
        border-radius: 6px;
        font-size: 0.75rem;
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
    }

    .copy-btn:hover, .paste-btn:hover, .load-btn:hover
    {         
        background: rgba(var(--color-accent-desat), var(--color-btn-hover-opacity));
    }
    
    .hint {
        color: rgba(var(--color-text-primary), 0.5);
        font-size: 0.75rem;
        margin-bottom: 8px;
    }
    
    .import-field {
        width: 100%;
        height: 80px;
        padding: 12px;
        border: 1px solid rgba(var(--color-border), 0.3);
        border-radius: 8px;
        background: rgba(var(--color-bg-primary), 0.4);
        color: rgba(var(--color-text-primary), 0.8);
        font-family: monospace;
        font-size: 0.8rem;
        resize: none;
        margin-bottom: 12px;
    }
    
    .import-actions {
        display: flex;
        gap: 8px;
    }
    
    
    /* Highlight styles */
    .cell.highlight-primary { background: rgba(var(--color-accent-primary), 0.3); } /* Light blue */
    .cell.highlight-secondary { background: rgba(var(--color-accent-secondary), 0.3); } /* Light red */
    .cell.highlight-both { background: rgba(var(--color-accent-tertiary), 0.3); } /* Light purple */

    .cell.highlight-primary:hover { background: rgba(var(--color-accent-primary), 0.4); }
    .cell.highlight-secondary:hover { background: rgba(var(--color-accent-secondary), 0.4); }
    .cell.highlight-both:hover { background: rgba(var(--color-accent-tertiary), 0.4); }
    
    /* All candidates covered - striped/hashed pattern (easy to customize) */
    .cell.all-candidates-covered {
        --stripe-color: rgba(var(--color-accent-warning), 0.25);
        --stripe-size: 6px;
        --stripe-angle: 45deg;
        background-image: repeating-linear-gradient(
            var(--stripe-angle),
            var(--stripe-color),
            var(--stripe-color) 2px,
            transparent 2px,
            transparent var(--stripe-size)
        );
    }
    /* Combine with existing highlight colors */
    .cell.all-candidates-covered.highlight-primary {
        background: 
            repeating-linear-gradient(var(--stripe-angle), var(--stripe-color), var(--stripe-color) 2px, transparent 2px, transparent var(--stripe-size)),
            rgba(var(--color-accent-primary), 0.3);
    }
    .cell.all-candidates-covered.highlight-secondary {
        background: 
            repeating-linear-gradient(var(--stripe-angle), var(--stripe-color), var(--stripe-color) 2px, transparent 2px, transparent var(--stripe-size)),
            rgba(var(--color-accent-secondary), 0.3);
    }
    .cell.all-candidates-covered.highlight-both {
        background: 
            repeating-linear-gradient(var(--stripe-angle), var(--stripe-color), var(--stripe-color) 2px, transparent 2px, transparent var(--stripe-size)),
            rgba(var(--color-accent-tertiary), 0.3);
    }
    
    /* Hint cell highlighting - new system */
    .cell.hint-cover-area { 
        background: rgba(var(--color-accent-primary), 0.3); /* Light blue for cover area */
    }
    .cell.hint-cover-area:hover { 
        background: rgba(var(--color-accent-primary), 0.4); 
    }
    .cell.hint-solved-cell { 
        background: rgba(var(--color-accent-success), 0.45); /* Stronger green for solution cell */
        box-shadow: inset 0 0 0 2px rgba(var(--color-accent-success), 0.8);
    }
    .cell.hint-solved-cell:hover { 
        background: rgba(var(--color-accent-success), 0.55); 
    }
    
    /* Number button selection states */
    .num-btn.primary {
        background: rgba(var(--color-accent-primary), 0.5);
        box-shadow: inset 0 0 0 2px rgb(var(--color-accent-primary));
    }
    .num-btn.primary:hover {
        background: rgba(var(--color-accent-primary), 0.6);
    }
    .num-btn.secondary {
        background: rgba(var(--color-accent-secondary), 0.5);
        box-shadow: inset 0 0 0 2px rgb(var(--color-accent-secondary));
    }
    .num-btn.secondary:hover {
        background: rgba(var(--color-accent-secondary), 0.6);
    }
    .num-btn.both {
        background: rgba(206, 147, 216, 0.5);
        box-shadow: inset 0 0 0 2px rgb(206, 147, 216);
    }
    .num-btn.both:hover {
        background: rgba(206, 147, 216, 0.6);
    }
    
    /* Pencil mark highlighting - color coded */
    .candidate.pencil-highlight-primary {
        color: rgb(var(--color-accent-primary));
        font-weight: bold;
        background: rgba(var(--color-accent-primary), 0.3);
        border-radius: 2px;
    }
    .candidate.pencil-highlight-secondary {
        color: rgb(var(--color-accent-secondary));
        font-weight: bold;
        background: rgba(var(--color-accent-secondary), 0.3);
        border-radius: 2px;
    }
    .candidate.pencil-highlight-both {
        color: rgb(var(--color-accent-warning));
        font-weight: bold;
        background: rgba(var(--color-accent-warning), 0.35);
        border-radius: 2px;
    }
    
    /* Hint candidate highlighting */
    .candidate.hint-elimination {
        color: rgb(var(--color-accent-error)); /* Red number */
        text-decoration: line-through;
        font-weight: bold;
        background: rgba(var(--color-grid-neutral), 0.4); /* Grey background */
        border-radius: 2px;
    }

    .candidate.hint-matching-not-eliminated {
        color: rgb(var(--color-accent-success)); /* Green number */
        font-weight: bold;
        background: rgba(var(--color-accent-success), 0.5); /* Light green background */
        border-radius: 2px;
    }

    .candidate.hint-solved {
        color: rgb(var(--color-accent-success));
        font-weight: bold;
        background: rgba(var(--color-accent-success), 0.35);
        border-radius: 2px;
        box-shadow: 0 0 0 1px rgba(var(--color-accent-success), 0.5);
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
        background: rgba(var(--color-bg-tertiary), 0.15);
        color: rgba(var(--color-text-primary), 0.7);
        font-weight: 600;
    }
    
    .mode-badge.highlight-mode { background: rgba(var(--color-accent-primary), 0.3); color: rgb(var(--color-accent-primary)); }
    .mode-badge.play-mode.fast { background: rgba(var(--color-accent-success), 0.3); color: rgb(var(--color-accent-success)); }
    .mode-badge.play-mode.advanced { background: rgba(var(--color-accent-warning), 0.3); color: rgb(var(--color-accent-warning)); }
    .mode-badge.clickable { cursor: pointer; transition: all 0.15s ease; }
    .mode-badge.clickable:hover { transform: scale(1.05); filter: brightness(1.1); }
    
    /* Selected number badges - inline with mode indicators */
    .selected-num {
        font-size: clamp(0.55rem, calc(0.5rem + 0.4vmin), 0.7rem);
        font-weight: 700;
        padding: clamp(2px, 0.5vmin, 4px) clamp(4px, 1vmin, 8px);
        border-radius: clamp(2px, 0.5vmin, 4px);
        line-height: 1.2;
    }
    
    .selected-num.primary { background: rgba(var(--color-accent-primary), 0.4); color: rgb(var(--color-accent-primary)); }
    .selected-num.secondary { background: rgba(var(--color-accent-secondary), 0.4); color: rgb(var(--color-accent-secondary)); }
    
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
        background: rgba(var(--color-accent-primary), 0.3);
        color: rgb(var(--color-accent-primary));
    }
    .action-btn.set-btn.primary:hover {
        background: rgba(var(--color-accent-primary), 0.5);
    }

    .action-btn.set-btn.secondary {
        background: rgba(var(--color-accent-secondary), 0.3);
        color: rgb(var(--color-accent-secondary));
    }
    .action-btn.set-btn.secondary:hover {
        background: rgba(var(--color-accent-secondary), 0.5);
    }

    .action-btn.set-btn.both {
        background: rgba(var(--color-accent-warning), 0.3);
        color: rgb(var(--color-accent-warning));
    }
    .action-btn.set-btn.both:hover {
        background: rgba(var(--color-accent-warning), 0.5);
    }

    /* Clear pencil mark buttons - primary (blue) and secondary (red) */
    .action-btn.clr-btn.primary {
        background: rgba(var(--color-accent-primary), var(--color-btn-opacity));
        color: rgb(var(--color-accent-primary));
        border: 1px solid rgba(var(--color-accent-primary), 0.3);
    }
    .action-btn.clr-btn.primary:hover {
        background: rgba(var(--color-accent-primary), 0.3);
    }

    .action-btn.clr-btn.secondary {
        background: rgba(var(--color-accent-secondary), 0.15);
        color: rgb(var(--color-accent-secondary));
        border: 1px solid rgba(var(--color-accent-secondary), 0.3);
    }
    .action-btn.clr-btn.secondary:hover {
        background: rgba(var(--color-accent-secondary), 0.3);
    }

    .action-btn.clr-btn.other {
        background: rgba(var(--color-accent-warning), 0.15);
        color: rgb(var(--color-accent-warning));
        border: 1px solid rgba(var(--color-accent-warning), 0.3);
    }
    .action-btn.clr-btn.other:hover {
        background: rgba(var(--color-accent-warning), 0.3);
    }

    /* Clear selection button (X) */
    .action-btn.clear-btn {
        background: rgba(var(--color-bg-tertiary), 0.2);
        color: rgba(var(--color-text-primary), 0.7);
    }
    .action-btn.clear-btn:hover {
        background: rgba(var(--color-bg-tertiary), 0.3);
    }
    
    /* Deselect cell button */
    .action-btn.deselect-btn {
        background: rgba(var(--color-accent-tertiary), 0.2);
        color: rgb(var(--color-accent-tertiary));
    }
    .action-btn.deselect-btn:hover {
        background: rgba(var(--color-accent-tertiary), 0.35);
    }
    
    /* Dual number pads in advanced mode */
    .number-pad.primary {
        margin-bottom: clamp(4px, 1vmin, 8px);
    }
    .number-pad.secondary {
        margin-top: clamp(2px, 0.5vmin, 4px);
    }
    
    /* Settings screen styles */
    .settings .section {
        background: rgba(var(--color-bg-tertiary), 0.1);
        border-radius: 12px;
        padding: 16px;
        margin-bottom: 16px;
    }

    .settings .section h2 {
        color: rgb(var(--color-text-primary));
        font-size: 1rem;
        margin-bottom: 8px;
    }
    
    .setting-desc {
        color: rgba(var(--color-text-primary), 0.5);
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
        background: rgba(var(--color-accent-desat), var(--color-btn-opacity));
        color: rgba(var(--color-text-primary), 0.8);
        transition: all 0.15s ease;
    }

    .settings-nav-btn:hover {
        background: rgba(var(--color-accent-desat), var(--color-btn-hover-opacity));
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
        background: rgba(var(--color-accent-desat), var(--color-btn-opacity));
        color: rgba(var(--color-text-primary), 0.7);
        transition: all 0.15s ease;
    }

    .mode-btn:hover {
        background: rgba(var(--color-accent-desat), var(--color-btn-hover-opacity));
    }
    
    .mode-btn.active {
        background: rgba(var(--color-accent-primary), 0.4);
        color: rgb(var(--color-text-primary));
        box-shadow: inset 0 0 0 2px rgb(var(--color-accent-primary));
    }
    
    .play-modes .mode-btn.fast.active {
        background: rgba(var(--color-accent-success), 0.4);
        color: rgb(var(--color-text-primary));
        box-shadow: inset 0 0 0 2px rgb(var(--color-accent-success));
    }
    
    .play-modes .mode-btn.advanced.active {
        background: rgba(var(--color-accent-warning), 0.4);
        color: rgb(var(--color-text-primary));
        box-shadow: inset 0 0 0 2px rgb(var(--color-accent-warning));
    }

    /* Theme options */
    .theme-options {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: clamp(4px, 1vmin, 8px);
        margin-bottom: clamp(8px, 2vmin, 16px);
    }

    .theme-btn.dark.active {
        background: rgba(0, 0, 0, 0.4);
        color: rgb(255, 255, 255);
        box-shadow: inset 0 0 0 2px rgb(51, 51, 51);
    }

    .theme-btn.blue.active {
        background: rgba(26, 26, 46, 0.4);
        color: rgb(255, 255, 255);
        box-shadow: inset 0 0 0 2px rgb(22, 33, 62);
    }

    .theme-btn.light.active {
        background: rgba(248, 249, 250, 0.4);
        color: rgb(33, 37, 41);
        box-shadow: inset 0 0 0 2px rgb(222, 226, 230);
    }

    .theme-btn.epaper.active {
        background: rgba(248, 248, 248, 0.4);
        color: rgb(0, 0, 0);
        box-shadow: inset 0 0 0 2px rgb(153, 153, 153);
    }
    
    .mode-explanation {
        background: rgba(var(--color-bg-primary), 0.2);
        border-radius: 6px;
        padding: 10px 12px;
        font-size: 0.75rem;
        color: rgba(var(--color-text-primary), 0.6);
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
        color: rgba(var(--color-text-primary), 0.7);
    }
    
    .color-box {
        width: 24px;
        height: 24px;
        border-radius: 4px;
    }
    
    .color-box.primary { background: rgba(var(--color-accent-primary), 0.5); }
    .color-box.secondary { background: rgba(var(--color-accent-secondary), 0.5); }
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
    
    /* Modal styles */
    .modal-overlay {
        position: fixed;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background: rgba(var(--color-shadow), 0.85);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 1000;
        padding: clamp(16px, 4vmin, 32px);
        animation: fadeInOverlay 0.2s ease;
    }
    
    @keyframes fadeInOverlay {
        from { opacity: 0; }
        to { opacity: 1; }
    }
    
    .modal-content {
        background: var(--gradient-bg);
        border-radius: clamp(12px, 3vmin, 24px);
        padding: clamp(20px, 4vmin, 40px);
        max-width: 500px;
        width: 100%;
        max-height: 85vh;
        overflow-y: auto;
        position: relative;
        box-shadow: 0 20px 60px rgba(var(--color-shadow), 0.5);
        border: 1px solid rgba(var(--color-border), 0.1);
        animation: slideUp 0.3s ease;
    }
    
    @keyframes slideUp {
        from { opacity: 0; transform: translateY(20px); }
        to { opacity: 1; transform: translateY(0); }
    }
    
    .modal-close {
        position: absolute;
        top: clamp(12px, 2vmin, 20px);
        right: clamp(12px, 2vmin, 20px);
        background: rgba(var(--color-accent-error), 0.2);
        border: none;
        color: rgb(var(--color-accent-error));
        font-size: clamp(1rem, calc(0.9rem + 0.5vmin), 1.3rem);
        width: clamp(32px, 6vmin, 40px);
        height: clamp(32px, 6vmin, 40px);
        border-radius: 50%;
        cursor: pointer;
        transition: all 0.15s ease;
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .modal-close:hover {
        background: rgba(var(--color-accent-error), 0.4);
        transform: scale(1.1);
    }
    
    .about-modal h1 {
        color: rgb(var(--color-accent-primary));
        font-size: clamp(1.5rem, calc(1.3rem + 1vmin), 2rem);
        margin-bottom: clamp(12px, 2vmin, 20px);
        text-align: center;
    }

    .about-tagline {
        color: rgb(var(--color-accent-info));
        font-size: clamp(0.95rem, calc(0.85rem + 0.5vmin), 1.15rem);
        text-align: center;
        margin-bottom: clamp(8px, 1.5vmin, 16px);
        font-weight: 500;
    }

    .about-description {
        color: rgba(var(--color-text-primary), 0.8);
        font-size: clamp(0.85rem, calc(0.75rem + 0.4vmin), 1rem);
        text-align: center;
        line-height: 1.5;
        margin-bottom: clamp(20px, 4vmin, 32px);
        padding-bottom: clamp(16px, 3vmin, 24px);
        border-bottom: 1px solid rgba(var(--color-border), 0.2);
    }
    
    .about-section {
        margin-bottom: clamp(16px, 3vmin, 24px);
    }
    
    .about-section h3 {
        color: rgb(var(--color-accent-warning));
        font-size: clamp(0.9rem, calc(0.8rem + 0.4vmin), 1.1rem);
        margin-bottom: clamp(6px, 1vmin, 10px);
    }
    
    .about-section p {
        color: rgba(var(--color-text-primary), 0.7);
        font-size: clamp(0.8rem, calc(0.7rem + 0.35vmin), 0.95rem);
        line-height: 1.5;
        margin-bottom: clamp(4px, 0.8vmin, 8px);
    }
    
    .about-section a {
        color: rgb(var(--color-accent-primary));
        text-decoration: none;
        transition: color 0.15s ease;
    }
    
    .about-section a:hover {
        color: rgb(var(--color-accent-info));
        text-decoration: underline;
    }
    
    .about-section strong {
        color: rgb(var(--color-accent-info));
    }
    
    .help-modal h1 {
        color: rgb(var(--color-accent-primary));
        font-size: clamp(1.5rem, calc(1.3rem + 1vmin), 2rem);
        margin-bottom: clamp(12px, 2vmin, 20px);
        text-align: center;
    }
    
    .help-intro {
        color: rgba(var(--color-text-primary), 0.8);
        font-size: clamp(0.85rem, calc(0.75rem + 0.4vmin), 1rem);
        text-align: center;
        line-height: 1.5;
        margin-bottom: clamp(20px, 4vmin, 32px);
        padding-bottom: clamp(16px, 3vmin, 24px);
        border-bottom: 1px solid rgba(var(--color-border), 0.2);
    }
    
    .help-section {
        margin-bottom: clamp(20px, 4vmin, 32px);
    }
    
    .help-section h2 {
        color: rgb(var(--color-accent-warning));
        font-size: clamp(1.1rem, calc(1rem + 0.5vmin), 1.4rem);
        margin-bottom: clamp(12px, 2vmin, 20px);
        margin-top: clamp(16px, 3vmin, 24px);
        border-bottom: 1px solid rgba(var(--color-border), 0.2);
        padding-bottom: clamp(8px, 1.5vmin, 12px);
    }
    
    .help-section h2:first-of-type {
        margin-top: 0;
    }
    
    .help-section h3 {
        color: rgb(var(--color-accent-info));
        font-size: clamp(0.95rem, calc(0.85rem + 0.4vmin), 1.15rem);
        margin-bottom: clamp(8px, 1.5vmin, 12px);
        margin-top: clamp(12px, 2vmin, 16px);
    }
    
    .help-section ul,
    .help-section ol {
        color: rgba(var(--color-text-primary), 0.7);
        font-size: clamp(0.8rem, calc(0.7rem + 0.35vmin), 0.95rem);
        line-height: 1.6;
        margin-bottom: clamp(12px, 2vmin, 16px);
        padding-left: clamp(20px, 4vmin, 30px);
    }
    
    .help-section li {
        margin-bottom: clamp(6px, 1vmin, 10px);
    }
    
    .help-section ul ul,
    .help-section ul ol,
    .help-section ol ul,
    .help-section ol ol {
        margin-top: clamp(6px, 1vmin, 10px);
        margin-bottom: clamp(6px, 1vmin, 10px);
    }
    
    .help-section strong {
        color: rgb(var(--color-accent-info));
        font-weight: 600;
    }
    
    .greeting-modal {
        max-width: 600px;
    }
    
    .greeting-modal h1 {
        color: rgb(var(--color-accent-primary));
        font-size: clamp(1.5rem, calc(1.3rem + 1vmin), 2rem);
        margin-bottom: clamp(12px, 2vmin, 20px);
        text-align: center;
    }
    
    .greeting-content {
        color: rgba(var(--color-text-primary), 0.8);
        font-size: clamp(0.85rem, calc(0.75rem + 0.4vmin), 1rem);
        line-height: 1.6;
    }
    
    .greeting-content p {
        margin-bottom: clamp(12px, 2vmin, 16px);
    }
    
    .greeting-signature {
        margin-top: clamp(20px, 4vmin, 32px);
        font-style: italic;
        color: rgba(var(--color-text-primary), 0.9);
    }
    
    .help-section .greeting-content {
        margin-top: clamp(8px, 1.5vmin, 12px);
    }
    
    /* Completion modal styles */
    .completion-modal {
        max-width: 400px;
        text-align: center;
    }
    
    .completion-icon {
        font-size: clamp(3rem, calc(2.5rem + 3vmin), 5rem);
        margin-bottom: clamp(8px, 2vmin, 16px);
    }
    
    .completion-modal h1 {
        color: rgb(var(--color-accent-success));
        font-size: clamp(1.5rem, calc(1.3rem + 1vmin), 2rem);
        margin-bottom: clamp(12px, 2vmin, 20px);
    }
    
    .completion-content p {
        color: rgba(var(--color-text-primary), 0.8);
        font-size: clamp(0.9rem, calc(0.8rem + 0.4vmin), 1.1rem);
        margin-bottom: clamp(16px, 3vmin, 24px);
    }
    
    .completion-stats {
        display: flex;
        justify-content: center;
        gap: clamp(16px, 3vmin, 32px);
        margin-bottom: clamp(20px, 4vmin, 32px);
    }
    
    .completion-stats .stat {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 4px;
    }
    
    .completion-stats .stat-icon {
        font-size: clamp(1.5rem, calc(1.2rem + 1vmin), 2rem);
    }
    
    .completion-stats .stat-label {
        font-size: clamp(0.65rem, calc(0.6rem + 0.3vmin), 0.8rem);
        color: rgba(var(--color-text-primary), 0.5);
        text-transform: uppercase;
    }

    .completion-stats .stat-value {
        font-size: clamp(1rem, calc(0.9rem + 0.5vmin), 1.3rem);
        font-weight: 700;
        color: rgb(var(--color-text-primary));
    }
    
    .completion-actions {
        display: flex;
        justify-content: center;
        gap: clamp(8px, 2vmin, 16px);
    }
    
    .completion-actions button {
        padding: clamp(10px, 2vmin, 14px) clamp(20px, 4vmin, 32px);
        border: none;
        border-radius: clamp(6px, 1.5vmin, 10px);
        font-size: clamp(0.85rem, calc(0.75rem + 0.4vmin), 1rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.2s ease;
    }
    
    .completion-actions .close-btn {
        background: rgba(var(--color-bg-tertiary), 0.2);
        color: rgba(var(--color-text-primary), 0.8);
    }

    .completion-actions .close-btn:hover {
        background: rgba(var(--color-bg-tertiary), 0.3);
    }
    
    .completion-actions .next-btn {
        background: rgba(var(--color-accent-success), 0.8);
        color: rgb(var(--color-text-primary));
    }
    
    .completion-actions .next-btn:hover {
        background: rgb(var(--color-accent-success));
        transform: translateY(-2px);
    }
    
    /* Number pad completed button style */
    .num-btn.completed {
        opacity: 0.3;
        pointer-events: none;
        visibility: hidden;
    }
    
    /* Category header with toggle */
    .category-header {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        justify-content: space-between;
        gap: clamp(8px, 2vmin, 16px);
        margin-bottom: clamp(8px, 2vmin, 12px);
    }
    
    /* Hide completed toggle */
    .toggle-label {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: clamp(0.7rem, calc(0.65rem + 0.3vmin), 0.85rem);
        color: rgba(var(--color-text-primary), 0.7);
        cursor: pointer;
        white-space: nowrap;
    }
    
    .toggle-checkbox {
        width: 16px;
        height: 16px;
        cursor: pointer;
        accent-color: rgb(var(--color-accent-primary));
    }
    
    /* Completion stats in puzzle list */
    .completion-stats {
        font-size: clamp(0.65rem, calc(0.6rem + 0.25vmin), 0.75rem);
        color: rgba(var(--color-text-primary), 0.5);
    }
    
    /* Delete button for saved games */
    .delete-btn {
        padding: clamp(4px, 1vmin, 8px) clamp(8px, 2vmin, 12px);
        border: none;
        border-radius: clamp(4px, 1vmin, 6px);
        font-size: clamp(0.7rem, calc(0.65rem + 0.3vmin), 0.85rem);
        cursor: pointer;
        background: rgba(var(--color-accent-error), 0.2);
        color: rgb(var(--color-accent-error));
        transition: all 0.15s ease;
    }
    
    .delete-btn:hover {
        background: rgba(var(--color-accent-error), 0.4);
    }
    
    /* Empty message for custom puzzles */
    .empty-message {
        padding: clamp(16px, 3vmin, 24px);
        text-align: center;
        color: rgba(var(--color-text-primary), 0.5);
        font-size: clamp(0.8rem, calc(0.75rem + 0.3vmin), 0.95rem);
        line-height: 1.5;
    }
    
    /* Version indicator - fixed position bottom left */
    .version-indicator {
        position: fixed;
        bottom: 12px;
        left: 12px;
        font-size: clamp(0.65rem, calc(0.6rem + 0.25vmin), 0.75rem);
        color: rgba(var(--color-text-primary), 0.35);
        cursor: pointer;
        padding: 4px 8px;
        border-radius: 4px;
        transition: all 0.15s ease;
        z-index: 100;
        user-select: none;
    }

    .version-indicator:hover {
        color: rgba(var(--color-text-primary), 0.7);
        background: rgba(var(--color-bg-tertiary), 0.2);
    }
    
    /* Version modal styles */
    .version-modal {
        max-width: 600px;
    }
    
    .version-modal h1 {
        color: rgb(var(--color-accent-primary));
        font-size: clamp(1.5rem, calc(1.3rem + 1vmin), 2rem);
        margin-bottom: clamp(12px, 2vmin, 20px);
        text-align: center;
    }
    
    .changelog-content {
        color: rgba(var(--color-text-primary), 0.85);
        font-size: clamp(0.85rem, calc(0.75rem + 0.4vmin), 1rem);
        line-height: 1.6;
        max-height: 60vh;
        overflow-y: auto;
        padding-right: 8px;
    }
    
    .changelog-content h2.changelog-version {
        color: rgb(var(--color-accent-info));
        font-size: clamp(1.1rem, calc(1rem + 0.5vmin), 1.3rem);
        margin-top: clamp(16px, 3vmin, 24px);
        margin-bottom: clamp(8px, 1.5vmin, 12px);
        padding-bottom: clamp(6px, 1vmin, 10px);
        border-bottom: 1px solid rgba(var(--color-border), 0.2);
    }
    
    .changelog-content h2.changelog-version:first-child {
        margin-top: 0;
    }
    
    .changelog-content h3.changelog-section {
        color: rgb(var(--color-accent-warning));
        font-size: clamp(0.95rem, calc(0.85rem + 0.4vmin), 1.1rem);
        margin-top: clamp(12px, 2vmin, 16px);
        margin-bottom: clamp(6px, 1vmin, 10px);
    }
    
    .changelog-content ul {
        margin: 0 0 clamp(8px, 1.5vmin, 12px) 0;
        padding-left: clamp(16px, 3vmin, 24px);
    }
    
    .changelog-content li {
        margin-bottom: clamp(4px, 0.8vmin, 8px);
        color: rgba(var(--color-text-primary), 0.8);
    }
    
    .changelog-content li.changelog-subitem {
        color: rgba(var(--color-text-primary), 0.6);
        font-size: 0.95em;
        margin-left: 16px;
    }
    
    .changelog-content strong {
        color: rgb(var(--color-accent-error));
    }
    
    .changelog-content del {
        color: rgba(var(--color-text-primary), 0.4);
        text-decoration: line-through;
    }
    
    .changelog-content code {
        background: rgba(var(--color-bg-primary), 0.5);
        padding: 2px 6px;
        border-radius: 4px;
        font-family: 'Monaco', 'Consolas', monospace;
        font-size: 0.9em;
    }
    
    .changelog-content p {
        margin-bottom: clamp(8px, 1.5vmin, 12px);
    }
    
    .version-actions {
        display: flex;
        justify-content: center;
        margin-top: clamp(16px, 3vmin, 24px);
        padding-top: clamp(12px, 2vmin, 16px);
        border-top: 1px solid rgba(var(--color-border), 0.2);
    }
    
    .version-actions .close-btn {
        padding: clamp(10px, 2vmin, 14px) clamp(24px, 5vmin, 40px);
        border: none;
        border-radius: clamp(6px, 1.5vmin, 10px);
        font-size: clamp(0.9rem, calc(0.8rem + 0.4vmin), 1.05rem);
        font-weight: 600;
        cursor: pointer;
        background: rgba(var(--color-accent-info), 0.8);
        color: rgb(var(--color-text-primary));
        transition: all 0.2s ease;
    }
    
    .version-actions .close-btn:hover {
        background: rgb(var(--color-accent-info));
        transform: translateY(-2px);
    }
""".trimIndent()
