import domain.*
import kotlinx.browser.localStorage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.w3c.dom.get
import org.w3c.dom.set
import view.Theme
import MistakeDetectionMode
import CandidateMode

/**
 * Manages game state persistence in localStorage
 */
object GameStateManager {
    
    private const val SAVED_GAMES_KEY = "nice_sudoku_saved_games"
    private const val CURRENT_GAME_KEY = "nice_sudoku_current_game"
    private const val HIGHLIGHT_MODE_KEY = "nice_sudoku_highlight_mode"
    private const val PLAY_MODE_KEY = "nice_sudoku_play_mode"
    private const val CUSTOM_PUZZLES_KEY = "nice_sudoku_custom_puzzles"
    private const val HIDE_COMPLETED_KEY = "nice_sudoku_hide_completed"
    private const val LAST_SEEN_VERSION_KEY = "nice_sudoku_last_seen_version"
    private const val THEME_KEY = "nice_sudoku_theme"
    private const val MISTAKE_DETECTION_KEY = "nice_sudoku_mistake_detection"
    private const val CANDIDATE_MODE_KEY = "nice_sudoku_candidate_mode"
    private const val TRACK_PLAY_TIME_KEY = "nice_sudoku_track_play_time"
    
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
                hintCount = state.hintCount,
                isCompleted = state.isCompleted,
                progressPercent = progress.coerceIn(0, 100),
                lastPlayedTimestamp = state.lastPlayedTimestamp,
                title = state.title,
                author = state.author,
                authorContact = state.authorContact,
                description = state.description
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
            if (name != null) {
                // Migration: CELL was renamed to PLACED in v0.6.0
                val migratedName = if (name == "CELL") "PLACED" else name
                val mode = HighlightMode.valueOf(migratedName)
                // If we migrated, save the new value
                if (name == "CELL") {
                    setHighlightMode(mode)
                }
                mode
            } else {
                HighlightMode.PENCIL
            }
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
            when (name) {
                "FAST", "CELL_FIRST", "PLACE" -> PlayMode.PLACE
                "ADVANCED", "MULTI_SELECT_NOTES" -> PlayMode.MULTI_SELECT_NOTES
                else -> PlayMode.PLACE
            }
        } catch (e: Exception) {
            PlayMode.PLACE
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
     * Get theme preference (default: DARK)
     */
    fun getTheme(): Theme {
        return try {
            val name = localStorage[THEME_KEY]
            if (name != null) Theme.valueOf(name) else Theme.DARK
        } catch (e: Exception) {
            Theme.DARK
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
     * Get candidate mode preference (default: AUTO)
     */
    fun getCandidateMode(): CandidateMode {
        return try {
            val name = localStorage[CANDIDATE_MODE_KEY]
            if (name != null) CandidateMode.valueOf(name) else CandidateMode.AUTO
        } catch (e: Exception) {
            CandidateMode.AUTO
        }
    }
    
    /**
     * Set candidate mode preference
     */
    fun setCandidateMode(mode: CandidateMode) {
        try {
            localStorage[CANDIDATE_MODE_KEY] = mode.name
        } catch (e: Exception) {
            console.log("Error saving candidate mode: ${e.message}")
        }
    }
    
    /**
     * Whether puzzle time is tracked and pause/resume UX is used (default: true).
     */
    fun getTrackPlayTime(): Boolean {
        return try {
            when (localStorage[TRACK_PLAY_TIME_KEY]) {
                null, "true" -> true
                "false" -> false
                else -> true
            }
        } catch (e: Exception) {
            true
        }
    }
    
    fun setTrackPlayTime(enabled: Boolean) {
        try {
            localStorage[TRACK_PLAY_TIME_KEY] = if (enabled) "true" else "false"
        } catch (e: Exception) {
            console.log("Error saving track play time preference: ${e.message}")
        }
    }
    
    /**
     * Save a custom puzzle to the library
     * Updates existing puzzle if it already exists (by ID), otherwise adds new
     */
    fun saveCustomPuzzle(puzzle: PuzzleDefinition) {
        val puzzles = loadCustomPuzzles().toMutableList()
        val existingIndex = puzzles.indexOfFirst { it.id == puzzle.id }
        
        if (existingIndex >= 0) {
            // Update existing puzzle
            puzzles[existingIndex] = puzzle
        } else {
            // Add new puzzle to beginning (most recent first)
            puzzles.add(0, puzzle)
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
     * 
     * AUTO mode: 729 zeros = NO user eliminations, all candidates shown (auto-calculated)
     * MANUAL mode: 729 ones = ALL eliminated, start with blank pencil marks
     */
    fun createNewGame(puzzle: PuzzleDefinition, solution: String? = null): SavedGameState {
        val initialState = initialCurrentStateString(puzzle.puzzleString)
        
        return SavedGameState(
            puzzleId = puzzle.id,
            puzzleString = puzzle.puzzleString,
            currentState = initialState,
            solution = solution,
            category = puzzle.category,
            difficulty = puzzle.difficulty,
            elapsedTimeMs = 0L,
            mistakeCount = 0,
            hintCount = 0,
            isCompleted = false,
            lastPlayedTimestamp = currentTimeMillis(),
            title = puzzle.title,
            author = puzzle.author,
            authorContact = puzzle.authorContact,
            description = puzzle.description,
            metadataImportLocks = puzzle.metadataImportLocks
        )
    }

    /**
     * Fresh saved-state string for a puzzle (givens + elimination row per candidate mode).
     * Used for new games and reset-to-givens.
     */
    fun initialCurrentStateString(puzzleString: String): String {
        val candidateMode = getCandidateMode()
        val eliminationString = when (candidateMode) {
            CandidateMode.AUTO -> "0".repeat(729)
            CandidateMode.MANUAL -> "1".repeat(729)
        }
        return puzzleString + eliminationString
    }
    
    /**
     * Update game state from current grid
     */
    fun updateGameState(
        currentGame: SavedGameState,
        grid: SudokuGrid,
        actionStack: List<String> = emptyList(),
        additionalTimeMs: Long = 0,
        newMistake: Boolean = false,
        newHint: Boolean = false
    ): SavedGameState {
        val stateString = SavedGameState.createStateString(grid)
        val isComplete = grid.isComplete && grid.isValid
        
        return currentGame.copy(
            currentState = stateString,
            actionStack = actionStack,
            elapsedTimeMs = currentGame.elapsedTimeMs + additionalTimeMs,
            mistakeCount = currentGame.mistakeCount + (if (newMistake) 1 else 0),
            hintCount = currentGame.hintCount + (if (newHint) 1 else 0),
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

