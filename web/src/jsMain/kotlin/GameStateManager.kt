import domain.*
import kotlinx.browser.localStorage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.w3c.dom.get
import org.w3c.dom.set

/**
 * Manages game state persistence in localStorage
 */
object GameStateManager {
    
    private const val SAVED_GAMES_KEY = "nice_sudoku_saved_games"
    private const val CURRENT_GAME_KEY = "nice_sudoku_current_game"
    private const val HIGHLIGHT_MODE_KEY = "nice_sudoku_highlight_mode"
    private const val PLAY_MODE_KEY = "nice_sudoku_play_mode"
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
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
            if (name != null) HighlightMode.valueOf(name) else HighlightMode.RCB_ALL
        } catch (e: Exception) {
            HighlightMode.RCB_ALL
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
     * Create a new saved game state from a puzzle
     */
    fun createNewGame(puzzle: PuzzleDefinition, solution: String? = null): SavedGameState {
        // Initialize state with puzzle string (81 chars) + all notes off (729 zeros)
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

