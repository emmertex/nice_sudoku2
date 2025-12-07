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

class SudokuApp {
    internal val gameEngine = GameEngine()
    internal var selectedCell: Int? = null
    internal var isNotesMode = false

    // Current game state
    private var currentGame: SavedGameState? = null
    private var solution: String? = null  // Background solved solution for mistake detection
    private var gameStartTime: Long = 0L
    private var pausedTime: Long = 0L
    
    // UI state
    internal var currentScreen = AppScreen.GAME
    private var selectedCategory: DifficultyCategory = DifficultyCategory.EASY
    private var toastMessage: String? = null
    private var showSettingsMenu = false
    
    // Highlight and Play mode state (loaded from preferences)
    private var highlightMode = GameStateManager.getHighlightMode()
    internal var playMode = GameStateManager.getPlayMode()
    private var currentTheme = GameStateManager.getTheme()
    private var mistakeDetectionMode = GameStateManager.getMistakeDetectionMode()
    internal var selectedNumbers1: MutableSet<Int> = mutableSetOf()  // Primary selected numbers (light blue)
    internal var selectedNumbers2: MutableSet<Int> = mutableSetOf()  // Secondary selected numbers (light red)
    
    // Hint system state
    internal var showHints = false  // Whether hint panel is visible
    internal var selectedHintIndex: Int = 0  // Currently selected hint in the list
    private var isLandscape = false  // Responsive layout detection
    internal var isBackendAvailable = false  // Whether hint system can be used
    private var expandedHintIndex: Int? = null  // Which hint is expanded in landscape mode (null = none)
    
    // Explanation overlay state
    internal var showExplanation = false  // Whether explanation overlay is visible
    internal var explanationStepIndex: Int = 0  // Current step in explanation
    
    // Interactive chain highlighting state
    private var highlightedLinkIndex: Int? = null  // Index of link being highlighted (for SVG line)
    private var highlightedNodeCell: Int? = null  // Cell index being highlighted from notation
    private var highlightedNodeCandidate: Int? = null  // Candidate being highlighted from notation
    
    // Cached hint list for event delegation
    private var currentHintList: List<TechniqueMatchInfo> = emptyList()
    
    // Modal state
    internal var showAboutModal = false
    internal var showHelpModal = false
    internal var showGreetingModal = false
    private var showCompletionModal = false
    private var completionShownForPuzzle: String? = null  // Track which puzzle we've shown completion for
    internal var showVersionModal = false
    private var showPuzzleInfoModal = false
    private var puzzleInfoTarget: PuzzleDefinition? = null  // Puzzle to show info for
    
    // Version info (loaded from CHANGELOG.md)
    private var currentVersion: String = ""
    private var changelogContent: String = ""
    
    // Puzzle browser state
    private var hideCompletedPuzzles = GameStateManager.getHideCompleted()
    
    private val appRoot: Element get() = document.getElementById("app")!!

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
    
    private fun handleCellClick(cellIndex: Int, grid: SudokuGrid) {
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
                // Collapse any expanded hint when showing explanation
                expandedHintIndex = null
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
                            val isExpanded = expandedHintIndex == index
                            div("hint-item ${if (isSelected) "selected" else ""} ${if (isExpanded) "expanded" else ""}") {
                                // Header row (always visible) - title only
                                div("hint-item-header") {
                                    onClickFunction = { e ->
                                        // Select this hint
                                        selectedHintIndex = index
                                        explanationStepIndex = 0
                                        // Toggle expansion: if clicking the same one, collapse it; otherwise expand this one
                                        expandedHintIndex = if (isExpanded) null else index
                                        render()
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
                        expandedHintIndex = null
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
            // Compact header: [Prev] [Title or Position] [Next] [Collapse/Close]
            div("hint-card-header-compact") {
                button(classes = "hint-nav-btn-small ${if (selectedHintIndex <= 0) "disabled" else ""}") {
                    +"◀ Prev"
                    onClickFunction = {
                        if (selectedHintIndex > 0) {
                            selectedHintIndex--
                            explanationStepIndex = 0
                            render()
                        }
                    }
                }
                
                // Title area: show technique name, or position if no hint selected
                div("hint-title-area") {
                    if (selectedHint != null) {
                        span("hint-technique-compact") { +selectedHint.techniqueName }
                        span("hint-position-small") { +"${selectedHintIndex + 1}/${hints.size}" }
                    } else {
                        span("hint-position") { +"${selectedHintIndex + 1} / ${hints.size}" }
                    }
                }
                
                button(classes = "hint-nav-btn-small ${if (selectedHintIndex >= hints.size - 1) "disabled" else ""}") {
                    +"Next ▶"
                    onClickFunction = {
                        if (selectedHintIndex < hints.size - 1) {
                            selectedHintIndex++
                            explanationStepIndex = 0
                            render()
                        }
                    }
                }
                
                // Close button
                
                button(classes = "hint-close-btn-small") {
                    +"✕"
                    onClickFunction = {
                        showHints = false
                        render()
                    }
                }
            }
            
            if (selectedHint != null) {
                // Show explanation with step navigation
                renderInlineExplanationCompact(selectedHint)
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
    private fun TagConsumer<HTMLElement>.renderInlineExplanationCompact(hint: TechniqueMatchInfo) {
        val steps = if (hint.explanationSteps.isNotEmpty()) {
            hint.explanationSteps
        } else {
            generateFallbackExplanationSteps(hint)
        }
        val currentStep = steps.getOrNull(explanationStepIndex)
        
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
                            button(classes = "step-nav-btn-small ${if (explanationStepIndex <= 0) "disabled" else ""}") {
                                +"◀"
                                onClickFunction = { e ->
                                    e.stopPropagation()
                                    if (explanationStepIndex > 0) {
                                        explanationStepIndex--
                                        render()
                                    }
                                }
                            }
                        }
                        
                        span("step-badge") { +"Step ${currentStep.stepNumber}" }
                        span("step-title") { +currentStep.title }
                        
                        // Step navigation buttons (only show if more than one step)
                        if (steps.size > 1) {
                            button(classes = "step-nav-btn-small ${if (explanationStepIndex >= steps.size - 1) "disabled" else ""}") {
                                +"▶"
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
                    div("step-description") {
                        renderInteractiveDescription(currentStep.description, hint)
                    }
                }
                } else {
                    div("inline-step") {
                        div("step-description") {
                            renderInteractiveDescription(hint.description, hint)
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
    private fun TagConsumer<HTMLElement>.renderInteractiveDescription(description: String, hint: TechniqueMatchInfo) {
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
                attachDescriptionInteractionListeners(containerId, hint)
            }, 50)  // Small delay to ensure DOM is ready
        }
    }
    
    /**
     * Set up global event delegation for interactive hint description elements
     * This uses event delegation so we don't need to re-attach listeners on each render
     */
    private fun setupChainInteractionDelegation() {
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
    private fun highlightCells(cellIndices: List<Int>) {
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
    private fun clearCellHighlights() {
        document.querySelectorAll(".hover-highlight-cell").asList().forEach { element ->
            (element as? HTMLElement)?.classList?.remove("hover-highlight-cell")
        }
    }
    
    /**
     * Highlight digits across the grid
     */
    private fun highlightDigits(digits: List<Int>) {
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
    private fun clearDigitHighlights() {
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
    private fun highlightHouse(houseType: String, houseIndex: Int) {
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
    private fun clearHouseHighlights() {
        document.querySelectorAll(".hover-highlight-house").asList().forEach { element ->
            (element as? HTMLElement)?.classList?.remove("hover-highlight-house")
        }
    }
    
    /**
     * Attach listeners for interactive description elements (uses global delegation)
     */
    private fun attachDescriptionInteractionListeners(containerId: String, hint: TechniqueMatchInfo) {
        // Event delegation is handled globally in setupChainInteractionDelegation()
        // This function is kept for potential future use
    }
    
    /**
     * Legacy alias for attachDescriptionInteractionListeners
     */
    private fun attachChainInteractionListeners(containerId: String, hint: TechniqueMatchInfo) {
        attachDescriptionInteractionListeners(containerId, hint)
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
                        explanationStepIndex = 0
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
                    explanationStepIndex = 0
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
                        explanationStepIndex = 0
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
                        renderInteractiveDescription(currentStep.description, hint)
                    }
                }
            } else {
                // Fallback if no steps at all
                div("inline-step") {
                    div("step-description") {
                        renderInteractiveDescription(hint.description, hint)
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
                        // Always highlight selected numbers when viewing explanations, otherwise respect highlightMode
                        val pencilHighlightClass = if (highlightMode == HighlightMode.PENCIL || showExplanation) {
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
