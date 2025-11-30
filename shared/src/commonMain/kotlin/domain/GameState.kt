package domain

/**
 * Domain model for the overall game state
 */
data class GameState(
    val grid: SudokuGrid = SudokuGrid.empty(),
    val selectedCell: Int? = null,
    val selectedTechnique: String? = null, // Using String instead of Technique enum
    val matches: Map<String, List<Any>> = emptyMap(), // Using Any instead of TechniqueMatch
    val isLoading: Boolean = false,
    val error: String? = null,
    val gameMode: GameMode = GameMode.PLAY,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    // Highlight and Play Mode State
    val highlightMode: HighlightMode = HighlightMode.CELL,
    val playMode: PlayMode = PlayMode.FAST,
    val selectedNumber1: Int? = null, // Primary selected number (1-9)
    val selectedNumber2: Int? = null, // Secondary selected number (1-9) for advanced highlighting
    val isPencilMode: Boolean = false
) {
    val selectedMatch: Any? get() {
        return selectedTechnique?.let { matches[it]?.firstOrNull() }
    }

    val canUndo: Boolean get() = true // TODO: Implement undo stack
    val canRedo: Boolean get() = false // TODO: Implement redo stack

    val isSolved: Boolean get() = grid.isComplete && grid.isValid

    fun withSelectedCell(cell: Int?): GameState {
        return copy(selectedCell = cell)
    }

    fun withGrid(newGrid: SudokuGrid): GameState {
        return copy(grid = newGrid)
    }

    fun withMatches(newMatches: Map<String, List<Any>>): GameState {
        return copy(matches = newMatches)
    }

    fun withSelectedTechnique(technique: String?): GameState {
        return copy(selectedTechnique = technique)
    }

    fun withLoading(loading: Boolean): GameState {
        return copy(isLoading = loading)
    }

    fun withError(error: String?): GameState {
        return copy(error = error)
    }

    fun withGameMode(mode: GameMode): GameState {
        return copy(gameMode = mode)
    }

    fun withHighlightMode(mode: HighlightMode): GameState {
        return copy(highlightMode = mode)
    }

    fun withPlayMode(mode: PlayMode): GameState {
        return copy(playMode = mode)
    }

    fun withSelectedNumber1(number: Int?): GameState {
        return copy(selectedNumber1 = number)
    }

    fun withSelectedNumber2(number: Int?): GameState {
        return copy(selectedNumber2 = number)
    }

    fun withPencilMode(pencil: Boolean): GameState {
        return copy(isPencilMode = pencil)
    }

    fun togglePencilMode(): GameState {
        return copy(isPencilMode = !isPencilMode)
    }

    companion object {
        fun initial(): GameState = GameState()
    }
}

enum class GameMode {
    PLAY,
    EDIT,
    SOLVE
}

enum class Difficulty {
    EASY,
    MEDIUM,
    HARD,
    EXPERT
}

/**
 * Highlight modes for number selection
 */
enum class HighlightMode {
    /** Highlight cells with matching numbers */
    CELL,
    /** Highlight Rows, Columns, and Boxes of selected cell(s) - Advanced */
    RCB_SELECTED,
    /** Highlight Rows, Columns, and Boxes of all cells matching the number */
    RCB_ALL,
    /** Highlight cells with matching pencil marks */
    PENCIL
}

/**
 * Play modes for user interaction
 */
enum class PlayMode {
    /** 
     * Fast mode: 
     * - Pressing a number selects it and highlights
     * - Clicking a cell sets that cell to the selected number
     * - Double-pressing a number (or pencil button) toggles pencil mode
     */
    FAST,
    /**
     * Advanced mode:
     * - Pressing a number highlights but doesn't auto-fill
     * - Must choose the number again to set it
     * - Space removes pencil mark, Enter sets selected number
     * - Clicking on a given/solved cell with different number activates second highlight
     */
    ADVANCED
}
