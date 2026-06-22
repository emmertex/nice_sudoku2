import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import adapter.GameEngine
import adapter.TechniqueMatchInfo
import view.*
import domain.*
import helpers.importExport.*
import i18n.LanguageConfig
import kotlin.js.Date


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
    internal var candidateMode = GameStateManager.getCandidateMode()
    internal var trackPlayTime = GameStateManager.getTrackPlayTime()
    internal var selectedNumbers1: MutableSet<Int> = mutableSetOf()  // Selected numbers (light blue)
    internal var multiSelectAction: MultiSelectAction = MultiSelectAction.CLEAR_SELECTED
    
    // Hint system state
    internal var showHints = false  // Whether hint panel is visible
    internal var selectedHintIndex: Int = 0  // Currently selected hint in the list
    internal var useHintSidebar = false  // Place hints in a right-hand sidebar (vs a card below)
    internal var isBackendAvailable = false  // Whether hint system can be used
    internal var expandedHintIndex: Int? = null  // Which hint is expanded in landscape mode (null = none)
    internal var isLoadingHints = false  // Whether backend is currently processing hints
    
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
    internal var showButtonHelpModal = false  // Quick reference modal for buttons/icons
    internal var showCompletionModal = false
    internal var completionShownForPuzzle: String? = null  // Track which puzzle we've shown completion for
    internal var showVersionModal = false
    internal var showPuzzleInfoModal = false
    internal var puzzleInfoTarget: Any? = null  // Puzzle or SavedGameState to show info for
    
    // Version info (loaded from CHANGELOG.md)
    internal var currentVersion: String = ""
    internal var changelogContent: String = ""
    
    // Puzzle browser state
    internal var hideCompletedPuzzles = GameStateManager.getHideCompleted()
    internal var browserSelectedPuzzleId: String? = null

    /** Import/Export: "original" | "current" */
    internal var exportPanelScope: String = "current"
    /** Original: "81" | "coach"; Current: "729" | "891" | "coach" */
    internal var exportPanelSubKey: String = "coach"
    
    // Timer update interval
    internal var timerIntervalId: Int? = null
    internal var isPaused = false
    internal var pauseStartTime: Long = 0L
    
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
        
        // Fix for Firefox touch support - convert touchend to click.
        // We dispatch our own (untrusted) click; the browser still fires a
        // trailing "ghost" click for the same tap. Track when we synthesize so
        // the buster below can swallow that ghost.
        var lastSynthClickTime = 0.0
        document.addEventListener("touchend", { event ->
            val touchEvent = event.asDynamic()
            val target = touchEvent.target as? HTMLElement
            if (target != null) {
                // Check if target or parent is a button or cell
                val clickable = target.closest("button, .cell") as? HTMLElement
                if (clickable != null) {
                    event.preventDefault()
                    lastSynthClickTime = Date.now()
                    clickable.click()
                }
            }
        }, js("{ passive: false }"))

        // Ghost-click buster: when a handler re-renders the whole screen
        // (innerHTML = ""), the browser's trailing trusted click from the same
        // tap can land on a freshly-rendered element at the same coordinates
        // (e.g. tapping "Menu" opens Settings, then the ghost click hits the
        // Settings "Back" button and bounces straight back to the paused game).
        // Swallow any trusted click that arrives right after our synthetic one.
        document.addEventListener("click", { event ->
            val clickEvent = event.asDynamic()
            val trusted = (clickEvent.isTrusted as? Boolean) ?: true
            if (trusted && Date.now() - lastSynthClickTime < 700.0) {
                event.preventDefault()
                event.stopPropagation()
                clickEvent.stopImmediatePropagation()
            }
        }, js("{ capture: true }"))
        
        // Global event delegation for chain notation interactions
        setupChainInteractionDelegation()
        
        // Decide hint placement (sidebar vs card-below) from available space.
        useHintSidebar = computeUseHintSidebar()

        // Re-evaluate placement on resize. The board itself fits via pure CSS now,
        // so JS only needs to re-render when the sidebar/below decision actually flips.
        var resizeTimeout: Int? = null
        window.addEventListener("resize", {
            resizeTimeout?.let { window.clearTimeout(it) }
            resizeTimeout = window.setTimeout({
                if (currentScreen == AppScreen.GAME) {
                    val next = computeUseHintSidebar()
                    val flipped = next != useHintSidebar
                    useHintSidebar = next
                    // A full re-render re-runs applyBoardSize; otherwise just resize the board.
                    if (flipped && showHints) render() else applyBoardSize()
                }
            }, 100)
        })
        
        // Set up window focus/blur listeners for auto-pause
        window.addEventListener("blur", {
            if (trackPlayTime && currentScreen == AppScreen.GAME && currentGame != null && !isPaused) {
                pauseGame()
            }
        })
        
        window.addEventListener("focus", {
            // Don't auto-resume - let user click to resume
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
        
        // Set up callback for loading state changes
        gameEngine.onLoadingStateChanged = { isLoading ->
            isLoadingHints = isLoading
            render()  // Re-render to show/hide loading indicator
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
        
        // Stop timer when switching away from game screen
        if (currentScreen != AppScreen.GAME) {
            stopTimer()
        }
        
        when (currentScreen) {
            AppScreen.GAME -> renderGameScreen()
            AppScreen.PUZZLE_BROWSER -> renderPuzzleBrowser()
            AppScreen.IMPORT_EXPORT -> renderImportExport()
            AppScreen.SETTINGS -> renderSettings()
        }
        if (currentScreen != AppScreen.GAME) {
            document.title = "Nice Sudoku"
        }
        
        // About modal (can appear over any screen)
        if (showAboutModal) {
            renderAboutModal()
        }
        
        // Help modal (can appear over any screen)
        if (showHelpModal) {
            renderHelpModal()
        }
        
        // Button help modal (quick reference for game screen buttons)
        if (showButtonHelpModal) {
            renderButtonHelpModal()
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
        
        // Size the board for the current viewport + hint layout (sets --board-size).
        if (currentScreen == AppScreen.GAME) {
            applyBoardSize()
        }

        // Start timer if on game screen with active game
        if (currentScreen == AppScreen.GAME && currentGame != null) {
            startTimer()
        }
    }

    /**
     * Decide whether hints belong in a right-hand sidebar (true) or a card below
     * the board (false). The rule is about *room*, not viewport aspect ratio:
     * place the board as it would sit in the below layout (limited by leftover
     * height), and use a sidebar only if that leaves enough horizontal slack for
     * a usable one. This is correct at every aspect ratio, unlike a fixed cutoff.
     */
    private fun computeUseHintSidebar(): Boolean {
        val w = window.innerWidth.toDouble()
        val h = window.innerHeight.toDouble()
        // Approx. non-board chrome stacked with the board in the below layout
        // (header + controls + number pad + gaps/padding).
        val chrome = 240.0
        val boardIfBelow = minOf(w, h - chrome)
        val slack = w - boardIfBelow
        return slack >= 300.0  // ~ sidebar min width (240–340) + gap
    }

    private fun startTimer() {
        // Clear existing timer if any
        stopTimer()
        
        // Start new interval that updates every second
        timerIntervalId = window.setInterval({
            updateTimerDisplay()
        }, 1000)
    }
    
    private fun stopTimer() {
        timerIntervalId?.let { window.clearInterval(it) }
        timerIntervalId = null
    }
    
    private fun updateTimerDisplay() {
        // Only update if we're on the game screen with an active game
        if (currentScreen != AppScreen.GAME || currentGame == null) {
            stopTimer()
            return
        }
        
        // Calculate current elapsed time (don't increment if paused)
        val currentElapsed = if (!trackPlayTime) {
            0L
        } else if (isPaused) {
            pausedTime + (pauseStartTime - gameStartTime)
        } else {
            pausedTime + (currentTimeMillis() - gameStartTime)
        }
        
        // Find and update the timer element
        val timerElement = document.querySelector(".timer") as? HTMLElement
        timerElement?.textContent = "⏱ ${formatTime(currentElapsed)}"
    }
    
    internal fun pauseGame() {
        if (!trackPlayTime || isPaused || currentGame == null) return
        
        isPaused = true
        pauseStartTime = currentTimeMillis()
        render()
    }
    
    internal fun resumeGame() {
        if (!trackPlayTime || !isPaused || currentGame == null) return
        
        pausedTime += pauseStartTime - gameStartTime
        gameStartTime = currentTimeMillis()
        
        isPaused = false
        render()
    }
    
    internal fun togglePause() {
        if (!trackPlayTime) return
        if (isPaused) {
            resumeGame()
        } else {
            pauseGame()
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
        
        // Detect language from URL path (e.g., /en/, /zh/, /de/, /es/)
        detectAndApplyLanguageFromUrl()
        
        // Start the app
        val app = SudokuApp()
        app.start()
    }
}

/**
 * Detect language from URL path and apply it
 * Supports paths like /en/, /zh/, /de/, /es/ or /en, /zh, etc.
 */
private fun detectAndApplyLanguageFromUrl() {
    val pathname = window.location.pathname
    val supportedLanguages = setOf("en", "zh", "de", "es", "hi", "fr", "ar", "bn", "ru", "pt", "ur")
    
    // Match language code at the start of the path: /en/, /en, /zh/, etc.
    val langMatch = Regex("^/([a-z]{2})(?:/|$)").find(pathname)
    val urlLang = langMatch?.groupValues?.getOrNull(1)
    
    if (urlLang != null && urlLang in supportedLanguages) {
        LanguageConfig.setLanguage(urlLang)
    } else {
        // Try to get language from localStorage or browser preference
        val savedLang = try {
            window.localStorage.getItem("sudoku_language")
        } catch (e: Throwable) {
            null
        }
        
        if (savedLang != null && savedLang in supportedLanguages) {
            LanguageConfig.setLanguage(savedLang)
        } else {
            // Default to browser language if supported
            val browserLang = window.navigator.language.take(2).lowercase()
            if (browserLang in supportedLanguages) {
                LanguageConfig.setLanguage(browserLang)
            }
            // Otherwise stays as "en" (default)
        }
    }
}

/**
 * Update URL to include language code and save preference
 */
fun setLanguageWithUrl(languageCode: String) {
    val success = LanguageConfig.setLanguage(languageCode)
    if (success) {
        // Save to localStorage
        try {
            window.localStorage.setItem("sudoku_language", languageCode)
        } catch (e: Throwable) {
            // Ignore localStorage errors
        }
        
        // Update URL path to include language
        val currentPath = window.location.pathname
        val hash = window.location.hash
        val search = window.location.search
        
        // Remove existing language prefix if present
        val pathWithoutLang = currentPath.replace(Regex("^/[a-z]{2}(?=/|$)"), "")
        val cleanPath = if (pathWithoutLang.isEmpty() || pathWithoutLang == "/") "/" else pathWithoutLang
        
        // Build new path with language prefix
        val newPath = "/$languageCode$cleanPath"
        
        // Use replaceState to update URL without navigation
        window.history.replaceState(null, "", "$newPath$search$hash")
    }
}
