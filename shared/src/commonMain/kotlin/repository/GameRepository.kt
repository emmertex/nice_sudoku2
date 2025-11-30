package repository

import adapter.GameEngine
import domain.GameState
import domain.HighlightEngine
import domain.HighlightMode
import domain.PlayMode
import domain.SudokuGrid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GameRepository {

    private val gameEngine = GameEngine()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _gameState = MutableStateFlow(GameState.initial())
    val gameState: StateFlow<GameState> = _gameState

    private val gameHistory = mutableListOf<GameState>()
    private var historyIndex = -1

    init {
        // Update the game state periodically to reflect engine changes
        repositoryScope.launch {
            while (true) {
                kotlinx.coroutines.delay(100) // Update every 100ms
                updateGameState()
            }
        }
    }

    private fun updateGameState() {
        val currentGrid = gameEngine.getCurrentGrid()
        val matches = gameEngine.getMatches().mapKeys { it.key.toString() }
        val selectedTechnique = gameEngine.getSelectedTechnique()?.toString()

        // Apply highlights based on current selection state
        val highlightedGrid = applySelectionHighlights(currentGrid)

        _gameState.value = _gameState.value.copy(
            grid = highlightedGrid,
            matches = matches,
            selectedTechnique = selectedTechnique
        )
    }

    /**
     * Apply highlights to the grid based on current selection state.
     */
    private fun applySelectionHighlights(grid: SudokuGrid): SudokuGrid {
        val state = _gameState.value
        val highlights = HighlightEngine.computeHighlights(
            grid = grid,
            highlightMode = state.highlightMode,
            selectedNumber1 = state.selectedNumber1,
            selectedNumber2 = state.selectedNumber2,
            selectedCell = state.selectedCell
        )
        return HighlightEngine.applyHighlights(grid, highlights)
    }

    fun selectCell(cellIndex: Int?) {
        val state = _gameState.value
        val grid = state.grid

        // In Advanced mode, clicking on a given/solved cell with a different number
        // should activate the second highlight
        if (state.playMode == PlayMode.ADVANCED && cellIndex != null) {
            val cell = grid.getCell(cellIndex)
            if (cell.isSolved && cell.value != state.selectedNumber1) {
                // Activate second highlight for the clicked cell's number
                _gameState.value = state.copy(
                    selectedCell = cellIndex,
                    selectedNumber2 = cell.value
                )
                return
            }
        }

        _gameState.value = state.withSelectedCell(cellIndex)
    }

    /**
     * Handle number input based on current play mode.
     * Note: Double-tap detection for toggling pencil mode should be handled at the UI level
     * where platform-specific time functions are available. Use the pencil toggle button
     * or onPencilToggle callback for that functionality.
     */
    fun handleNumberInput(number: Int) {
        val state = _gameState.value

        // Select the number for highlighting
        selectNumber(number)

        // In Fast mode, if a cell is selected, apply the number
        if (state.playMode == PlayMode.FAST && state.selectedCell != null) {
            val cell = state.grid.getCell(state.selectedCell)
            if (!cell.isGiven) {
                if (state.isPencilMode) {
                    toggleCandidateInCell(state.selectedCell, number)
                } else {
                    setCellValue(state.selectedCell, number)
                }
            }
        }
    }

    /**
     * Select a number for highlighting. 
     * If the number is already selected as primary, clear it.
     * In Advanced mode, shift-clicking or clicking a different number sets secondary.
     */
    fun selectNumber(number: Int) {
        val state = _gameState.value

        val newState = if (state.selectedNumber1 == number) {
            // Clicking the same number clears it
            state.copy(selectedNumber1 = null)
        } else if (state.selectedNumber1 != null && state.playMode == PlayMode.ADVANCED) {
            // In Advanced mode, if we already have a primary, set secondary
            if (state.selectedNumber2 == number) {
                // Clicking secondary again clears it
                state.copy(selectedNumber2 = null)
            } else {
                state.copy(selectedNumber2 = number)
            }
        } else {
            // Set as primary
            state.copy(selectedNumber1 = number, selectedNumber2 = null)
        }

        _gameState.value = newState
    }

    /**
     * Clear all number selections.
     */
    fun clearNumberSelections() {
        _gameState.value = _gameState.value.copy(
            selectedNumber1 = null,
            selectedNumber2 = null
        )
    }

    fun setCellValue(cellIndex: Int, value: Int?) {
        val cell = _gameState.value.grid.getCell(cellIndex)
        if (cell.isGiven) return // Can't modify given cells

        saveToHistory()
        gameEngine.setCellValue(cellIndex, value)
    }

    fun toggleCandidateInCell(cellIndex: Int, candidate: Int) {
        val cell = _gameState.value.grid.getCell(cellIndex)
        if (cell.isGiven || cell.isSolved) return // Can't modify given/solved cells

        saveToHistory()
        // Toggle candidate in the current grid state
        val currentGrid = _gameState.value.grid
        val newGrid = currentGrid.toggleCandidate(cellIndex, candidate)
        
        // Update game state directly for now (GameEngine doesn't support candidates yet)
        _gameState.value = _gameState.value.copy(grid = newGrid)
    }

    /**
     * Remove a pencil mark from the selected cell (for Advanced mode).
     */
    fun removePencilMarkFromSelectedCell() {
        val state = _gameState.value
        val cellIndex = state.selectedCell ?: return
        val number = state.selectedNumber1 ?: return

        val cell = state.grid.getCell(cellIndex)
        if (number in cell.candidates) {
            toggleCandidateInCell(cellIndex, number)
        }
    }

    /**
     * Set the selected number in the selected cell (for Advanced mode).
     */
    fun setNumberInSelectedCell() {
        val state = _gameState.value
        val cellIndex = state.selectedCell ?: return
        val number = state.selectedNumber1 ?: return

        setCellValue(cellIndex, number)
    }

    /**
     * Handle cell click based on play mode.
     */
    fun handleCellClick(cellIndex: Int) {
        val state = _gameState.value
        val cell = state.grid.getCell(cellIndex)

        // In Advanced mode, clicking a solved cell with a different number activates secondary highlight
        if (state.playMode == PlayMode.ADVANCED && cell.isSolved && 
            state.selectedNumber1 != null && cell.value != state.selectedNumber1) {
            _gameState.value = state.copy(
                selectedCell = cellIndex,
                selectedNumber2 = cell.value
            )
            return
        }

        // Select the cell
        selectCell(cellIndex)

        // In Fast mode with a number selected, apply immediately
        if (state.playMode == PlayMode.FAST && state.selectedNumber1 != null && !cell.isGiven) {
            if (state.isPencilMode) {
                toggleCandidateInCell(cellIndex, state.selectedNumber1)
            } else if (!cell.isSolved) {
                setCellValue(cellIndex, state.selectedNumber1)
            }
        }
    }

    fun toggleCandidate(cellIndex: Int, candidate: Int) {
        toggleCandidateInCell(cellIndex, candidate)
    }

    fun loadPuzzle(puzzleString: String): Boolean {
        saveToHistory()
        val success = gameEngine.loadPuzzle(puzzleString)
        if (success) {
            clearHistory()
            // Reset selection state when loading new puzzle
            _gameState.value = _gameState.value.copy(
                selectedNumber1 = null,
                selectedNumber2 = null,
                selectedCell = null,
                isPencilMode = false
            )
        }
        return success
    }

    // Highlight and Play Mode setters
    fun setHighlightMode(mode: HighlightMode) {
        _gameState.value = _gameState.value.withHighlightMode(mode)
    }

    fun setPlayMode(mode: PlayMode) {
        _gameState.value = _gameState.value.withPlayMode(mode)
        // Clear secondary selection when switching to Fast mode
        if (mode == PlayMode.FAST) {
            _gameState.value = _gameState.value.copy(selectedNumber2 = null)
        }
    }

    fun togglePencilMode() {
        _gameState.value = _gameState.value.togglePencilMode()
    }

    fun findAllTechniques() {
        gameEngine.findAllTechniques()
    }

    fun findBasicTechniques() {
        gameEngine.findBasicTechniques()
    }

    fun applyBasicTechniques() {
        saveToHistory()
        gameEngine.applyBasicTechniques()
    }

    fun solve() {
        saveToHistory()
        gameEngine.solve()
    }

    fun selectTechnique(technique: String) {
        _gameState.value = _gameState.value.withSelectedTechnique(technique)
        // TODO: Convert string back to Technique enum for GameEngine
        // gameEngine.selectTechnique(Technique.valueOf(technique))
    }

    fun applySelectedTechnique() {
        saveToHistory()
        // TODO: Implement applying selected technique
        gameEngine.applyBasicTechniques()
    }

    fun clearMatches() {
        gameEngine.clearMatches()
    }

    fun erase() {
        val state = _gameState.value
        val cellIndex = state.selectedCell ?: return
        val cell = state.grid.getCell(cellIndex)
        
        if (cell.isGiven) return

        if (cell.isSolved) {
            // Clear the value
            setCellValue(cellIndex, null)
        } else {
            // Clear all candidates
            saveToHistory()
            val newGrid = state.grid.withCellCandidates(cellIndex, emptySet())
            _gameState.value = state.copy(grid = newGrid)
        }
    }

    fun undo(): Boolean {
        if (canUndo()) {
            historyIndex--
            val previousState = gameHistory[historyIndex]
            // Restore the previous state - this is simplified
            // In a real implementation, you'd need to restore the Java state as well
            _gameState.value = previousState
            return true
        }
        return false
    }

    fun redo(): Boolean {
        if (canRedo()) {
            historyIndex++
            val nextState = gameHistory[historyIndex]
            _gameState.value = nextState
            return true
        }
        return false
    }

    fun canUndo(): Boolean = historyIndex > 0

    fun canRedo(): Boolean = historyIndex < gameHistory.size - 1

    private fun saveToHistory() {
        // Remove any states after current index (for when we're not at the end)
        while (gameHistory.size > historyIndex + 1) {
            gameHistory.removeAt(gameHistory.size - 1)
        }

        gameHistory.add(_gameState.value.copy())
        historyIndex = gameHistory.size - 1

        // Limit history size
        if (gameHistory.size > 50) {
            gameHistory.removeAt(0)
            historyIndex--
        }
    }

    private fun clearHistory() {
        gameHistory.clear()
        gameHistory.add(_gameState.value.copy())
        historyIndex = 0
    }

    fun getPuzzleString(): String {
        // TODO: Implement grid string representation
        return "Grid representation not implemented"
    }

    companion object {
        val samplePuzzles = listOf(
            "530070000600195000098000060800060003400803001700020006060000280000419005000080079", // Easy
            "004006079000000602056092300078061030009000400020540890007410920301000000460370100", // Medium
            "000000000000003085001020000000507000004000100090000000500000073002010000000040009"  // Hard
        )
    }
}
