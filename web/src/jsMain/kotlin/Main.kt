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
import view.*
import domain.*
import helpers.importExport.*


class SudokuApp {
    internal val gameEngine = GameEngine()
    internal var selectedCell: Int? = null
    internal var isNotesMode = false

    // Current game state
    internal var currentGame: SavedGameState? = null
    internal var solution: String? = null  // Background solved solution for mistake detection
    internal var gameStartTime: Long = 0L
    internal var pausedTime: Long = 0L
    
    // UI state
    internal var currentScreen = AppScreen.GAME
    private var selectedCategory: DifficultyCategory = DifficultyCategory.EASY
    internal var toastMessage: String? = null
    private var showSettingsMenu = false
    
    // Highlight and Play mode state (loaded from preferences)
    internal var highlightMode = GameStateManager.getHighlightMode()
    internal var playMode = GameStateManager.getPlayMode()
    private var currentTheme = GameStateManager.getTheme()
    private var mistakeDetectionMode = GameStateManager.getMistakeDetectionMode()
    internal var selectedNumbers1: MutableSet<Int> = mutableSetOf()  // Primary selected numbers (light blue)
    internal var selectedNumbers2: MutableSet<Int> = mutableSetOf()  // Secondary selected numbers (light red)
    
    // Hint system state
    internal var showHints = false  // Whether hint panel is visible
    internal var selectedHintIndex: Int = 0  // Currently selected hint in the list
    internal var isLandscape = false  // Responsive layout detection
    internal var isBackendAvailable = false  // Whether hint system can be used
    internal var expandedHintIndex: Int? = null  // Which hint is expanded in landscape mode (null = none)
    
    // Explanation overlay state
    internal var showExplanation = false  // Whether explanation overlay is visible
    internal var explanationStepIndex: Int = 0  // Current step in explanation
    
    // Interactive chain highlighting state
    internal var highlightedLinkIndex: Int? = null  // Index of link being highlighted (for SVG line)
    internal var highlightedNodeCell: Int? = null  // Cell index being highlighted from notation
    internal var highlightedNodeCandidate: Int? = null  // Candidate being highlighted from notation
    
    // Cached hint list for event delegation
    internal var currentHintList: List<TechniqueMatchInfo> = emptyList()
    
    // Modal state
    internal var showAboutModal = false
    internal var showHelpModal = false
    internal var showGreetingModal = false
    internal var showCompletionModal = false
    internal var completionShownForPuzzle: String? = null  // Track which puzzle we've shown completion for
    internal var showVersionModal = false
    private var showPuzzleInfoModal = false
    private var puzzleInfoTarget: PuzzleDefinition? = null  // Puzzle to show info for
    
    // Version info (loaded from CHANGELOG.md)
    private var currentVersion: String = ""
    private var changelogContent: String = ""
    
    // Puzzle browser state
    private var hideCompletedPuzzles = GameStateManager.getHideCompleted()
    
    internal val appRoot: Element get() = document.getElementById("app")!!

    private fun startNewGame(puzzle: PuzzleDefinition) {
        gameEngine.loadPuzzle(puzzle.puzzleString)

        // Use pre-loaded solution from puzzle definition
        solution = puzzle.solution

        // Create new saved game with solution
        currentGame = GameStateManager.createNewGame(puzzle, puzzle.solution)
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

        val puzzleSolution = puzzle.solution
        if (puzzleSolution != null) {
            println("Solution pre-loaded: ${puzzleSolution.take(20)}...")
        } else {
            // Fallback: solve in background for custom puzzles without solutions
            val solverEngine = GameEngine()
            solverEngine.loadPuzzle(puzzle.puzzleString)
            solverEngine.getSolutionString(
                onStatus = { status ->
                    showToast("⏳ $status")
                },
                onComplete = { solutionStr ->
                    if (solutionStr != null) {
                        solution = solutionStr
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
    }

    private fun resumeGame(saved: SavedGameState) {
        // Parse the state - returns user eliminations (1 = eliminated by user)
        val (values, userEliminations) = SavedGameState.parseStateString(saved.currentState)

        // Load into engine - this calculates auto-candidates
        gameEngine.loadPuzzle(saved.puzzleString)

        // Restore action stack from saved game
        gameEngine.setActionStack(saved.actionStack)

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
     * Import a game from a puzzle string (81, 810, or 891 chars)
     */
    private fun importGameFromString(rawInput: String, fromUrl: Boolean = false): Boolean {
        val text = rawInput.trim()

        if (!PuzzleStringParser.isValidPuzzleString(text)) {
            showToast(if (fromUrl) "Invalid shared game link" else "Invalid puzzle string")
            return false
        }

        // Normalize first 81 chars by converting '.' to '0' for parsing
        val normalized = text.take(81).map { if (it == '.') '0' else it }.joinToString("") + text.drop(81)
        val importResult = SavedGameState.parseImportStateString(normalized)
        if (importResult == null) {
            showToast(if (fromUrl) "❌ Invalid shared game link" else "❌ Failed to parse import string")
            return false
        }

        val (values, userEliminations, originalPuzzleStr) = importResult

        val puzzle = PuzzleDefinition(
            id = "custom_${currentTimeMillis()}",
            puzzleString = originalPuzzleStr,
            difficulty = 0f,
            category = DifficultyCategory.CUSTOM
        )

        // Save to custom puzzles library for reuse
        GameStateManager.saveCustomPuzzle(puzzle)

        // Start new game with puzzle and apply imported state
        gameEngine.loadPuzzle(originalPuzzleStr)
        var hasStateData = false
        for (i in 0 until 81) {
            val originalValue = originalPuzzleStr[i].digitToIntOrNull() ?: 0
            if (values[i] != 0 && values[i] != originalValue) {
                gameEngine.setCellValue(i, values[i])
                hasStateData = true
            }
            if (userEliminations[i].isNotEmpty()) {
                gameEngine.setUserEliminations(i, userEliminations[i])
                hasStateData = true
            }
        }

        // Create saved game with elimination format
        val grid = gameEngine.getCurrentGrid()
        val stateWithEliminations = SavedGameState.createStateString(grid)
        currentGame = SavedGameState(
            puzzleId = puzzle.id,
            puzzleString = originalPuzzleStr,
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

        // Reset timers/selection and render game screen
        gameStartTime = currentTimeMillis()
        pausedTime = 0L
        selectedCell = null
        currentScreen = AppScreen.GAME
        solution = null
        render()

        val successMessage = when {
            fromUrl -> "✓ Shared game loaded!"
            hasStateData -> "✓ Full state imported!"
            else -> "✓ Puzzle loaded and saved to Custom!"
        }
        showToast(successMessage)
        return true
    }

    /**
     * Save the current game state to persistent storage.
     * This includes all cell values AND all user eliminations.
     * Must be called after any manual modification to candidates or cell values.
     *
     * User eliminations are stored separately from auto-calculated candidates.
     * This ensures user eliminations are never lost when candidates are recalculated.
     */
    internal fun saveCurrentState() {
        val game = currentGame ?: return
        val grid = gameEngine.getCurrentGrid()
        val elapsedSinceStart = currentTimeMillis() - gameStartTime

        // updateGameState uses createStateString which saves user eliminations
        val updated = GameStateManager.updateGameState(
            currentGame = game,
            grid = grid,
            actionStack = gameEngine.getActionStack(),
            additionalTimeMs = elapsedSinceStart
        )
        currentGame = updated
        GameStateManager.saveGame(updated)

        // Reset timer
        gameStartTime = currentTimeMillis()
        pausedTime = updated.elapsedTimeMs
    }

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
                val handled = KeyboardHandler.handleKeyPress(this, key, ctrlKey, shiftKey, altKey, metaKey, grid, event)
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
        
        // Handle shared game URLs when hash changes
        window.addEventListener("hashchange", {
            handleSharedGameLinkFromUrl(::showToast, ::importGameFromString)
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
        
        // Preload all puzzle categories for better UX
        PuzzleLibrary.preloadAll()
        
        // Handle shared game links from URL before resuming/starting a game
        if (handleSharedGameLinkFromUrl(::showToast, ::importGameFromString)) {
            return
        }
        
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
        PuzzleLibrary.getRandomPuzzleAsync(DifficultyCategory.EASY) { puzzle ->
            if (puzzle != null) {
                startNewGame(puzzle)
            } else {
                render()
            }
        }
    }
    
    private fun loadChangelog() {
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

    private fun checkMistake(cellIndex: Int, value: Int): Boolean {
        // Don't check if mistake detection is off
        if (mistakeDetectionMode == MistakeDetectionMode.OFF) return false

        val isMistake = GameStateManager.isMistake(solution, cellIndex, value)
        if (isMistake) {
            currentGame = currentGame?.copy(mistakeCount = (currentGame?.mistakeCount ?: 0) + 1)
            currentGame?.let { GameStateManager.saveGame(it) }
        }
        return isMistake
    }

    /**
     * Check if removing a candidate would be a mistake (removing the correct answer).
     * Only counts as mistake if:
     * - Mistake detection is CANDIDATE mode
     * - The candidate being removed equals the solution for that cell
     * - The candidate is currently present (being removed, not added)
     */
    internal fun checkCandidateRemovalMistake(cellIndex: Int, candidate: Int, isCurrentlyPresent: Boolean): Boolean {
        // Only check in CANDIDATE mode
        if (mistakeDetectionMode != MistakeDetectionMode.CANDIDATE) return false

        // Only check if we're removing (candidate is currently present)
        if (!isCurrentlyPresent) return false

        // Check if this candidate is the correct answer
        if (solution == null || cellIndex < 0 || cellIndex >= 81) return false
        val correctValue = solution!![cellIndex].digitToIntOrNull() ?: return false

        if (candidate == correctValue) {
            currentGame = currentGame?.copy(mistakeCount = (currentGame?.mistakeCount ?: 0) + 1)
            currentGame?.let { GameStateManager.saveGame(it) }
            return true
        }
        return false
    }

    internal fun formatTime(ms: Long): String {
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
    
    internal fun showToast(message: String) {
        toastMessage = message
        render()
        window.setTimeout({
            toastMessage = null
            render()
        }, 2000)
    }
    
    // Compute which cells should have primary highlight (light blue)
    // Uses AND logic - only cells containing ALL selected numbers are highlighted
    internal fun getPrimaryHighlightCells(grid: SudokuGrid): Set<Int> {
        if (selectedNumbers1.isEmpty()) return emptySet()
        // Get cells for each number, then intersect (AND logic)
        return selectedNumbers1.map { getHighlightCellsForNumber(grid, it) }
            .reduce { acc, set -> acc.intersect(set) }
    }
    
    // Compute which cells should have secondary highlight (light red)
    // Uses AND logic - only cells containing ALL selected numbers are highlighted
    internal fun getSecondaryHighlightCells(grid: SudokuGrid): Set<Int> {
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
    
    internal fun handleNumberClick(num: Int, grid: SudokuGrid) {
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
                            val isCandidatePresent = num in cell.displayCandidates
                            val wasMistake = checkCandidateRemovalMistake(cellIndex, num, isCandidatePresent)
                            if (wasMistake) showToast("❌ Wrong candidate removed!")
                            // Only record elimination if candidate was present (we're removing it)
                            if (isCandidatePresent) {
                                gameEngine.recordAction(gameEngine.createEliminationAction(cellIndex, num))
                            }
                            gameEngine.toggleCandidate(cellIndex, num)
                        } else {
                            val wasMistake = checkMistake(cellIndex, num)
                            if (wasMistake) showToast("❌ Wrong number!")
                            gameEngine.recordAction(gameEngine.createPlacementAction(cellIndex, num))
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
    internal fun togglePrimaryNumber(num: Int) {
        if (num in selectedNumbers1) {
            selectedNumbers1.remove(num)
        } else {
            selectedNumbers1.add(num)
        }
        render()
    }
    
    // Toggle number in secondary selection (for advanced mode secondary number bar)
    internal fun toggleSecondaryNumber(num: Int) {
        if (num in selectedNumbers2) {
            selectedNumbers2.remove(num)
        } else {
            selectedNumbers2.add(num)
        }
        render()
    }
    
    internal fun handleCellClick(cellIndex: Int, grid: SudokuGrid) {
        val cell = grid.getCell(cellIndex)
        
        // In FAST mode with a number selected, apply it to the cell
        val selectedNum = selectedNumbers1.singleOrNull()
        if (playMode == PlayMode.FAST && selectedNum != null && !cell.isGiven) {
            if (isNotesMode) {
                // Toggle pencil mark
                if (!cell.isSolved) {
                    val isCandidatePresent = selectedNum in cell.displayCandidates
                    val wasMistake = checkCandidateRemovalMistake(cellIndex, selectedNum, isCandidatePresent)
                    if (wasMistake) showToast("❌ Wrong candidate removed!")
                    // Only record elimination if candidate was present (we're removing it)
                    if (isCandidatePresent) {
                        gameEngine.recordAction(gameEngine.createEliminationAction(cellIndex, selectedNum))
                    }
                    gameEngine.toggleCandidate(cellIndex, selectedNum)
                    saveCurrentState()
                    selectedCell = null
                }
                selectedCell = null
            } else if (!cell.isSolved) {
                val wasMistake = checkMistake(cellIndex, selectedNum)
                if (wasMistake) showToast("❌ Wrong number!")
                gameEngine.recordAction(gameEngine.createPlacementAction(cellIndex, selectedNum))
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
    
    
    
    
    internal fun render() {
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
        
        // Puzzle info modal
        if (showPuzzleInfoModal && puzzleInfoTarget != null) {
            renderPuzzleInfoModal(puzzleInfoTarget!!)
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
                            +"Puzzles generated by: "
                            a(href = "https://github.com/stephenostermiller/qqwing", target = "_blank") {
                                +"qqwing"
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
                                strong { +"Undo button (↩)" }
                                +": Undo your last action (placements and candidate eliminations)"
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
                        

                        
                        p("greeting-signature") {
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
    
    private fun renderPuzzleInfoModal(puzzle: PuzzleDefinition) {
        appRoot.append {
            div("modal-overlay") {
                onClickFunction = { event ->
                    if ((event.target as? Element)?.classList?.contains("modal-overlay") == true) {
                        showPuzzleInfoModal = false
                        puzzleInfoTarget = null
                        render()
                    }
                }
                div("modal-content puzzle-info-modal") {
                    button(classes = "modal-close") {
                        +"✕"
                        onClickFunction = {
                            showPuzzleInfoModal = false
                            puzzleInfoTarget = null
                            render()
                        }
                    }
                    h2 { +"Puzzle Info" }
                    
                    div("info-grid") {
                        // Title (if available)
                        val puzzleTitle = puzzle.title
                        val puzzleUrl = puzzle.url
                        if (puzzleTitle != null) {
                            div("info-row") {
                                span("info-label") { +"Title:" }
                                if (puzzleUrl != null) {
                                    a(href = puzzleUrl, target = "_blank", classes = "info-value link") {
                                        +puzzleTitle
                                    }
                                } else {
                                    span("info-value") { +puzzleTitle }
                                }
                            }
                        }
                        
                        // Puzzle ID
                        div("info-row") {
                            span("info-label") { +"Puzzle ID:" }
                            span("info-value") { +puzzle.id }
                        }
                        
                        // Difficulty
                        div("info-row") {
                            span("info-label") { +"Difficulty:" }
                            span("info-value") { +"${puzzle.difficulty}" }
                        }
                        
                        // Quality (if available)
                        if (puzzle.quality != null) {
                            div("info-row") {
                                span("info-label") { +"Quality:" }
                                span("info-value") { +"${puzzle.quality}/10" }
                            }
                        }
                        
                        // Category
                        div("info-row") {
                            span("info-label") { +"Category:" }
                            span("info-value category ${puzzle.category.name.lowercase()}") { 
                                +puzzle.category.displayName 
                            }
                        }
                        
                        // Techniques (if available)
                        val techniques = puzzle.techniques
                        if (!techniques.isNullOrEmpty()) {
                            div("info-section") {
                                h3 { +"Techniques Used" }
                                div("techniques-list") {
                                    techniques.entries.sortedByDescending { it.value }.forEach { (technique, count) ->
                                        div("technique-row") {
                                            span("technique-name") { +technique }
                                            span("technique-count") { +"×$count" }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    div("modal-actions") {
                        button(classes = "close-btn") {
                            +"Close"
                            onClickFunction = {
                                showPuzzleInfoModal = false
                                puzzleInfoTarget = null
                                render()
                            }
                        }
                        button(classes = "play-btn") {
                            +"Play Puzzle"
                            onClickFunction = {
                                showPuzzleInfoModal = false
                                puzzleInfoTarget = null
                                startNewGame(puzzle)
                            }
                        }
                    }
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
                                // Use difficulty-based category for display (handles old games with removed categories)
                                val displayCategory = DifficultyCategory.fromDifficulty(summary.difficulty)
                                div("game-item") {
                                    span("category ${displayCategory.name.lowercase()}") { 
                                        +displayCategory.displayName 
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
                        val isLoading = PuzzleLibrary.isPuzzlesLoading(selectedCategory)
                        
                        // Trigger async load with callback to re-render
                        if (puzzles.isEmpty() && !isLoading && selectedCategory != DifficultyCategory.CUSTOM) {
                            PuzzleLibrary.getPuzzlesForCategoryAsync(selectedCategory) {
                                render()
                            }
                        }
                        
                        if (isLoading) {
                            div("empty-message") {
                                +"Loading puzzles..."
                            }
                        } else if (puzzles.isEmpty() && selectedCategory == DifficultyCategory.CUSTOM) {
                            div("empty-message") {
                                +"No custom puzzles yet. Import a puzzle from the Import/Export page to add it here."
                            }
                        } else if (puzzles.isEmpty()) {
                            div("empty-message") {
                                +"No puzzles available for this category."
                            }
                        }
                        for ((index, puzzle) in puzzles.withIndex()) {
                            val existingGame = summaries.find { it.puzzleId == puzzle.id }
                            val isCompleted = existingGame?.isCompleted == true
                            
                            // Skip completed puzzles if hide completed is enabled
                            if (hideCompletedPuzzles && isCompleted) continue
                            
                            div("puzzle-item ${if (isCompleted) "completed" else ""}") {
                                span("puzzle-num") { +"#${index + 1}" }
                                // Show title if available (as link if URL exists)
                                val puzzleTitle = puzzle.title
                                val puzzleUrl = puzzle.url
                                if (puzzleTitle != null) {
                                    if (puzzleUrl != null) {
                                        a(href = puzzleUrl, target = "_blank", classes = "puzzle-title-link") {
                                            +puzzleTitle
                                        }
                                    } else {
                                        span("puzzle-title") { +puzzleTitle }
                                    }
                                }
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
                                button(classes = "info-btn") {
                                    +"ℹ️"
                                    attributes["title"] = "Puzzle info"
                                    onClickFunction = {
                                        puzzleInfoTarget = puzzle
                                        showPuzzleInfoModal = true
                                        render()
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
        // Use new export format (user eliminations: 1 = eliminated) with original puzzle
        val stateString891 = SavedGameState.createStateStringFor891Export(grid, game?.puzzleString ?: "")
        
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
                            button(classes = "copy-btn") {
                                +"Copy URL"
                                onClickFunction = {
                                    val shareUrl = buildShareUrl(puzzleString)
                                    ClipboardUtils.copyToClipboard(shareUrl,
                                        onSuccess = { showToast("✓ Copied puzzle URL!") },
                                        onError = { showToast("Failed to copy URL") }
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
                            button(classes = "copy-btn") {
                                +"Copy URL"
                                onClickFunction = {
                                    val shareUrl = buildShareUrl(currentValues)
                                    ClipboardUtils.copyToClipboard(shareUrl,
                                        onSuccess = { showToast("✓ Copied state URL!") },
                                        onError = { showToast("Failed to copy URL") }
                                    )
                                }
                            }
                        }
                    }
                    
                    div("export-option") {
                        label { +"State, Givens and Eliminations (891 chars)" }
                        div("export-row") {
                            input(InputType.text, classes = "export-field") {
                                value = stateString891
                                readonly = true
                            }
                            button(classes = "copy-btn") {
                                +"Copy"
                                onClickFunction = {
                                    ClipboardUtils.copyToClipboard(stateString891,
                                        onSuccess = { showToast("✓ Copied full state!") },
                                        onError = { showToast("Failed to copy") }
                                    )
                                }
                            }
                            button(classes = "copy-btn") {
                                +"Copy URL"
                                onClickFunction = {
                                    val shareUrl = buildShareUrl(stateString891)
                                    ClipboardUtils.copyToClipboard(shareUrl,
                                        onSuccess = { showToast("✓ Copied full state URL!") },
                                        onError = { showToast("Failed to copy URL") }
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Import section
                div("section") {
                    h2 { +"📥 Import" }
                    p("hint") { +"Paste an 81-char puzzle, 810-char state, or 891-char full state string" }
                    
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
                                importGameFromString(text)
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
                
                // Mistake Detection section
                div("section") {
                    h2 { +"⚠️ Mistake Detection" }
                    p("setting-desc") { +"Choose when mistakes are detected and counted" }
                    
                    div("mode-options") {
                        button(classes = "mode-btn ${if (mistakeDetectionMode == MistakeDetectionMode.OFF) "active" else ""}") {
                            +"Off"
                            onClickFunction = {
                                mistakeDetectionMode = MistakeDetectionMode.OFF
                                GameStateManager.setMistakeDetectionMode(MistakeDetectionMode.OFF)
                                render()
                            }
                        }
                        button(classes = "mode-btn ${if (mistakeDetectionMode == MistakeDetectionMode.PLACEMENT) "active" else ""}") {
                            +"Placement"
                            onClickFunction = {
                                mistakeDetectionMode = MistakeDetectionMode.PLACEMENT
                                GameStateManager.setMistakeDetectionMode(MistakeDetectionMode.PLACEMENT)
                                render()
                            }
                        }
                        button(classes = "mode-btn ${if (mistakeDetectionMode == MistakeDetectionMode.CANDIDATE) "active" else ""}") {
                            +"Candidate"
                            onClickFunction = {
                                mistakeDetectionMode = MistakeDetectionMode.CANDIDATE
                                GameStateManager.setMistakeDetectionMode(MistakeDetectionMode.CANDIDATE)
                                render()
                            }
                        }
                    }
                    
                    div("mode-explanation") {
                        +when (mistakeDetectionMode) {
                            MistakeDetectionMode.OFF -> "No mistake detection - play without feedback"
                            MistakeDetectionMode.PLACEMENT -> "Alert when placing a wrong number in a cell"
                            MistakeDetectionMode.CANDIDATE -> "Alert for wrong placements AND removing correct candidates"
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
    // Set up global error handlers to prevent unhandled promise rejections
    // This prevents the webpack-dev-server overlay from showing errors on first launch
    window.addEventListener("unhandledrejection", { event ->
        // Silently handle unhandled promise rejections during initialization
        // These are often non-critical (e.g., failed network requests)
        val error = event.asDynamic().reason
        println("Unhandled promise rejection (suppressed): $error")
        event.preventDefault() // Prevent default error handling
    })
    
    window.addEventListener("error", { event ->
        // Only log errors, don't let them crash the app
        val error = event.asDynamic().error
        if (error != null) {
            println("Global error caught: $error")
        }
        // Don't prevent default - let browser handle critical errors
    })
    
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
