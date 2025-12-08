import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import adapter.GameEngine
import adapter.TechniqueMatchInfo
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
    internal var selectedCategory: DifficultyCategory = DifficultyCategory.EASY
    internal var toastMessage: String? = null
    
    // Highlight and Play mode state (loaded from preferences)
    internal var highlightMode = GameStateManager.getHighlightMode()
    internal var playMode = GameStateManager.getPlayMode()
    internal var currentTheme = GameStateManager.getTheme()
    internal var mistakeDetectionMode = GameStateManager.getMistakeDetectionMode()
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
    internal var highlightedLinkIndex: Int? = null  // Index of link being highlighting (for SVG line)
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
    internal var showPuzzleInfoModal = false
    internal var puzzleInfoTarget: PuzzleDefinition? = null  // Puzzle to show info for
    
    // Version info (loaded from CHANGELOG.md)
    internal var currentVersion: String = ""
    internal var changelogContent: String = ""
    
    // Puzzle browser state
    internal var hideCompletedPuzzles = GameStateManager.getHideCompleted()
    
    internal val appRoot: Element get() = document.getElementById("app")!!

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
        
        // Always show version number in bottom left corner (if loaded)
        if (currentVersion.isNotEmpty()) {
            renderVersionIndicator()
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
