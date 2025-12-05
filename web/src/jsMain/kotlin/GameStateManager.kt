import domain.*
import kotlinx.browser.localStorage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.w3c.dom.get
import org.w3c.dom.set
import Theme
import MistakeDetectionMode

/**
 * Manages game state persistence in localStorage
 */
object GameStateManager {
    
    private const val SAVED_GAMES_KEY = "nice_sudoku_saved_games"
    private const val CURRENT_GAME_KEY = "nice_sudoku_current_game"
    private const val HIGHLIGHT_MODE_KEY = "nice_sudoku_highlight_mode"
    private const val PLAY_MODE_KEY = "nice_sudoku_play_mode"
    private const val GREETING_SHOWN_KEY = "nice_sudoku_greeting_shown"
    private const val CUSTOM_PUZZLES_KEY = "nice_sudoku_custom_puzzles"
    private const val HIDE_COMPLETED_KEY = "nice_sudoku_hide_completed"
    private const val LAST_SEEN_VERSION_KEY = "nice_sudoku_last_seen_version"
    private const val THEME_KEY = "nice_sudoku_theme"
    private const val MISTAKE_DETECTION_KEY = "nice_sudoku_mistake_detection"
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true  // Handles unknown enum values by using default
    }
    
    /**
     * Save a game state
     */
    fun saveGame(state: SavedGameState) {
        val games = loadAllGames().toMutableMap()
        games[state.puzzleId] = state
        
        try {
            localStorage[SAVED_GAMES_KEY] = json.encodeToString(games)
        } catch (e: Exception) {
            console.log("Error saving game: ${e.message}")
        }
    }
    
    /**
     * Load a specific saved game
     */
    fun loadGame(puzzleId: String): SavedGameState? {
        return loadAllGames()[puzzleId]
    }
    
    /**
     * Load all saved games
     */
    fun loadAllGames(): Map<String, SavedGameState> {
        return try {
            val data = localStorage[SAVED_GAMES_KEY]
            if (data != null) {
                json.decodeFromString<Map<String, SavedGameState>>(data)
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            console.log("Error loading games: ${e.message}")
            emptyMap()
        }
    }
    
    /**
     * Delete a saved game
     */
    fun deleteGame(puzzleId: String) {
        val games = loadAllGames().toMutableMap()
        games.remove(puzzleId)
        try {
            localStorage[SAVED_GAMES_KEY] = json.encodeToString(games)
        } catch (e: Exception) {
            console.log("Error deleting game: ${e.message}")
        }
    }
    
    /**
     * Get summaries of all saved games (for list display)
     */
    fun getGameSummaries(): List<GameSummary> {
        return loadAllGames().values.map { state ->
            val (values, _) = SavedGameState.parseStateString(state.currentState)
            val filledCells = values.count { it != 0 }
            val totalCells = 81
            val givenCells = state.puzzleString.count { it != '0' && it != '.' }
            val solvableCells = totalCells - givenCells
            val solvedCells = filledCells - givenCells
            val progress = if (solvableCells > 0) (solvedCells * 100) / solvableCells else 100
            
            GameSummary(
                puzzleId = state.puzzleId,
                category = state.category,
                difficulty = state.difficulty,
                elapsedTimeMs = state.elapsedTimeMs,
                mistakeCount = state.mistakeCount,
                isCompleted = state.isCompleted,
                progressPercent = progress.coerceIn(0, 100),
                lastPlayedTimestamp = state.lastPlayedTimestamp
            )
        }.sortedByDescending { it.lastPlayedTimestamp }
    }
    
    /**
     * Get incomplete games only
     */
    fun getIncompleteGames(): List<GameSummary> {
        return getGameSummaries().filter { !it.isCompleted }
    }
    
    /**
     * Set current game ID
     */
    fun setCurrentGameId(puzzleId: String?) {
        if (puzzleId != null) {
            localStorage[CURRENT_GAME_KEY] = puzzleId
        } else {
            localStorage.removeItem(CURRENT_GAME_KEY)
        }
    }
    
    /**
     * Get current game ID
     */
    fun getCurrentGameId(): String? {
        return localStorage[CURRENT_GAME_KEY]
    }
    
    /**
     * Save highlight mode preference
     */
    fun setHighlightMode(mode: HighlightMode) {
        localStorage[HIGHLIGHT_MODE_KEY] = mode.name
    }
    
    /**
     * Get saved highlight mode preference
     */
    fun getHighlightMode(): HighlightMode {
        return try {
            val name = localStorage[HIGHLIGHT_MODE_KEY]
            if (name != null) HighlightMode.valueOf(name) else HighlightMode.PENCIL
        } catch (e: Exception) {
            HighlightMode.PENCIL
        }
    }
    
    /**
     * Save play mode preference
     */
    fun setPlayMode(mode: PlayMode) {
        localStorage[PLAY_MODE_KEY] = mode.name
    }
    
    /**
     * Get saved play mode preference
     */
    fun getPlayMode(): PlayMode {
        return try {
            val name = localStorage[PLAY_MODE_KEY]
            if (name != null) PlayMode.valueOf(name) else PlayMode.FAST
        } catch (e: Exception) {
            PlayMode.FAST
        }
    }
    
    /**
     * Check if greeting has been shown
     */
    fun hasGreetingBeenShown(): Boolean {
        return try {
            localStorage[GREETING_SHOWN_KEY] == "true"
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Mark greeting as shown
     */
    fun markGreetingAsShown() {
        try {
            localStorage[GREETING_SHOWN_KEY] = "true"
        } catch (e: Exception) {
            console.log("Error marking greeting as shown: ${e.message}")
        }
    }
    
    /**
     * Save hide completed preference
     */
    fun setHideCompleted(hide: Boolean) {
        try {
            localStorage[HIDE_COMPLETED_KEY] = hide.toString()
        } catch (e: Exception) {
            console.log("Error saving hide completed preference: ${e.message}")
        }
    }
    
    /**
     * Get hide completed preference (default: true)
     */
    fun getHideCompleted(): Boolean {
        return try {
            localStorage[HIDE_COMPLETED_KEY]?.toBoolean() ?: true
        } catch (e: Exception) {
            true
        }
    }
    
    /**
     * Get last seen version
     */
    fun getLastSeenVersion(): String? {
        return try {
            localStorage[LAST_SEEN_VERSION_KEY]
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Set last seen version
     */
    fun setLastSeenVersion(version: String) {
        try {
            localStorage[LAST_SEEN_VERSION_KEY] = version
        } catch (e: Exception) {
            console.log("Error saving last seen version: ${e.message}")
        }
    }

    /**
     * Get theme preference (default: BLUE)
     */
    fun getTheme(): Theme {
        return try {
            val name = localStorage[THEME_KEY]
            if (name != null) Theme.valueOf(name) else Theme.BLUE
        } catch (e: Exception) {
            Theme.BLUE
        }
    }

    /**
     * Set theme preference
     */
    fun setTheme(theme: Theme) {
        try {
            localStorage[THEME_KEY] = theme.name
        } catch (e: Exception) {
            console.log("Error saving theme preference: ${e.message}")
        }
    }
    
    /**
     * Get mistake detection mode preference (default: CANDIDATE)
     */
    fun getMistakeDetectionMode(): MistakeDetectionMode {
        return try {
            val name = localStorage[MISTAKE_DETECTION_KEY]
            if (name != null) MistakeDetectionMode.valueOf(name) else MistakeDetectionMode.CANDIDATE
        } catch (e: Exception) {
            MistakeDetectionMode.CANDIDATE
        }
    }
    
    /**
     * Set mistake detection mode preference
     */
    fun setMistakeDetectionMode(mode: MistakeDetectionMode) {
        try {
            localStorage[MISTAKE_DETECTION_KEY] = mode.name
        } catch (e: Exception) {
            console.log("Error saving mistake detection mode: ${e.message}")
        }
    }
    
    /**
     * Save a custom puzzle to the library
     */
    fun saveCustomPuzzle(puzzle: PuzzleDefinition) {
        val puzzles = loadCustomPuzzles().toMutableList()
        // Avoid duplicates by puzzle string
        if (puzzles.none { it.puzzleString == puzzle.puzzleString }) {
            puzzles.add(0, puzzle) // Add to beginning (most recent first)
        }
        try {
            localStorage[CUSTOM_PUZZLES_KEY] = json.encodeToString(puzzles)
        } catch (e: Exception) {
            console.log("Error saving custom puzzle: ${e.message}")
        }
    }
    
    /**
     * Load all custom puzzles
     */
    fun loadCustomPuzzles(): List<PuzzleDefinition> {
        return try {
            val data = localStorage[CUSTOM_PUZZLES_KEY]
            if (data != null) {
                json.decodeFromString<List<PuzzleDefinition>>(data)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            console.log("Error loading custom puzzles: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Delete a custom puzzle
     */
    fun deleteCustomPuzzle(puzzleId: String) {
        val puzzles = loadCustomPuzzles().toMutableList()
        puzzles.removeAll { it.id == puzzleId }
        try {
            localStorage[CUSTOM_PUZZLES_KEY] = json.encodeToString(puzzles)
        } catch (e: Exception) {
            console.log("Error deleting custom puzzle: ${e.message}")
        }
    }
    
    /**
     * Create a new saved game state from a puzzle.
     * 
     * The state format is: 81 values + 729 user eliminations
     * Initial state has 729 zeros meaning NO user eliminations.
     * All candidates can be shown (if auto-calculation allows).
     */
    fun createNewGame(puzzle: PuzzleDefinition, solution: String? = null): SavedGameState {
        // Initialize state with puzzle string (81 chars) + no user eliminations (729 zeros)
        // 0 = not eliminated by user = can be shown if auto-calc allows
        val initialState = puzzle.puzzleString + "0".repeat(729)
        
        return SavedGameState(
            puzzleId = puzzle.id,
            puzzleString = puzzle.puzzleString,
            currentState = initialState,
            solution = solution,
            category = puzzle.category,
            difficulty = puzzle.difficulty,
            elapsedTimeMs = 0L,
            mistakeCount = 0,
            isCompleted = false,
            lastPlayedTimestamp = currentTimeMillis()
        )
    }
    
    /**
     * Update game state from current grid
     */
    fun updateGameState(
        currentGame: SavedGameState,
        grid: SudokuGrid,
        additionalTimeMs: Long = 0,
        newMistake: Boolean = false
    ): SavedGameState {
        val stateString = SavedGameState.createStateString(grid)
        val isComplete = grid.isComplete && grid.isValid
        
        return currentGame.copy(
            currentState = stateString,
            elapsedTimeMs = currentGame.elapsedTimeMs + additionalTimeMs,
            mistakeCount = currentGame.mistakeCount + (if (newMistake) 1 else 0),
            isCompleted = isComplete,
            lastPlayedTimestamp = currentTimeMillis()
        )
    }
    
    /**
     * Check if a cell value is a mistake (doesn't match solution)
     */
    fun isMistake(solution: String?, cellIndex: Int, value: Int): Boolean {
        if (solution == null || cellIndex < 0 || cellIndex >= 81) return false
        val correctValue = solution[cellIndex].digitToIntOrNull() ?: return false
        return value != correctValue
    }
}

// currentTimeMillis() is defined in PlatformUtils.kt

