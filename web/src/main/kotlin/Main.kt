import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.CanvasBasedWindow
import domain.GameState
import domain.HighlightEngine
import domain.HighlightMode
import domain.PlayMode
import domain.SudokuGrid
import ui.GameScreen
import ui.SudokuTheme
import kotlin.js.Date

// Platform-specific time function for JS
private fun currentTimeMillis(): Long = Date.now().toLong()

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(canvasElementId = "ComposeTarget") {
        var gameState by remember { 
            mutableStateOf(
                GameState(
                    grid = SudokuGrid.fromString(
                        // Sample puzzle for testing
                        "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
                    ) ?: SudokuGrid.empty()
                )
            )
        }

        // Track last number press for double-tap detection
        var lastNumberPressed by remember { mutableStateOf<Int?>(null) }
        var lastPressTime by remember { mutableStateOf(0L) }
        val doubleTapThreshold = 300L

        // Helper to apply highlights to grid
        fun applyHighlights(state: GameState): GameState {
            val highlights = HighlightEngine.computeHighlights(
                grid = state.grid,
                highlightMode = state.highlightMode,
                selectedNumber1 = state.selectedNumber1,
                selectedNumber2 = state.selectedNumber2,
                selectedCell = state.selectedCell
            )
            val highlightedGrid = HighlightEngine.applyHighlights(state.grid, highlights)
            return state.copy(grid = highlightedGrid)
        }

        // Helper to handle number selection
        fun selectNumber(number: Int, state: GameState): GameState {
            return if (state.selectedNumber1 == number) {
                // Clicking same number clears it
                state.copy(selectedNumber1 = null)
            } else if (state.selectedNumber1 != null && state.playMode == PlayMode.ADVANCED) {
                // In Advanced mode, if we have primary, set/toggle secondary
                if (state.selectedNumber2 == number) {
                    state.copy(selectedNumber2 = null)
                } else {
                    state.copy(selectedNumber2 = number)
                }
            } else {
                // Set as primary
                state.copy(selectedNumber1 = number, selectedNumber2 = null)
            }
        }

        SudokuTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                GameScreen(
                    gameState = gameState,
                    onCellClick = { cellIndex ->
                        val cell = gameState.grid.getCell(cellIndex)
                        
                        // In Advanced mode, clicking a solved cell with different number activates secondary highlight
                        if (gameState.playMode == PlayMode.ADVANCED && cell.isSolved &&
                            gameState.selectedNumber1 != null && cell.value != gameState.selectedNumber1) {
                            gameState = applyHighlights(gameState.copy(
                                selectedCell = cellIndex,
                                selectedNumber2 = cell.value
                            ))
                            return@GameScreen
                        }
                        
                        // Select the cell
                        var newState = gameState.withSelectedCell(cellIndex)
                        
                        // In Fast mode with a number selected, apply immediately
                        if (gameState.playMode == PlayMode.FAST && gameState.selectedNumber1 != null && !cell.isGiven) {
                            if (gameState.isPencilMode) {
                                // Toggle candidate
                                val newGrid = newState.grid.toggleCandidate(cellIndex, gameState.selectedNumber1!!)
                                newState = newState.copy(grid = newGrid)
                            } else if (!cell.isSolved) {
                                // Set value
                                val newGrid = newState.grid.withCellValue(cellIndex, gameState.selectedNumber1!!)
                                newState = newState.copy(grid = newGrid)
                            }
                        }
                        
                        gameState = applyHighlights(newState)
                    },
                    onNumberInput = { number ->
                        val currentTime = currentTimeMillis()
                        
                        // Check for double-tap in Fast mode to toggle pencil mode
                        if (gameState.playMode == PlayMode.FAST) {
                            if (lastNumberPressed == number && 
                                (currentTime - lastPressTime) < doubleTapThreshold) {
                                // Double tap - toggle pencil mode
                                gameState = gameState.togglePencilMode()
                                lastNumberPressed = null
                                lastPressTime = 0
                                return@GameScreen
                            }
                            lastNumberPressed = number
                            lastPressTime = currentTime
                        }
                        
                        // Select the number for highlighting
                        var newState = selectNumber(number, gameState)
                        
                        // In Fast mode, if a cell is selected, apply the number
                        if (gameState.playMode == PlayMode.FAST && gameState.selectedCell != null) {
                            val cell = gameState.grid.getCell(gameState.selectedCell!!)
                            if (!cell.isGiven) {
                                if (gameState.isPencilMode) {
                                    val newGrid = newState.grid.toggleCandidate(gameState.selectedCell!!, number)
                                    newState = newState.copy(grid = newGrid)
                                } else if (!cell.isSolved) {
                                    val newGrid = newState.grid.withCellValue(gameState.selectedCell!!, number)
                                    newState = newState.copy(grid = newGrid)
                                }
                            }
                        }
                        
                        gameState = applyHighlights(newState)
                    },
                    onEraseInput = {
                        gameState.selectedCell?.let { cellIndex ->
                            val cell = gameState.grid.getCell(cellIndex)
                            if (!cell.isGiven) {
                                val newGrid = if (cell.isSolved) {
                                    gameState.grid.withCellValue(cellIndex, null)
                                } else {
                                    // Clear all candidates
                                    gameState.grid.withCellCandidates(cellIndex, emptySet())
                                }
                                gameState = applyHighlights(gameState.copy(grid = newGrid))
                            }
                        }
                    },
                    onTechniqueClick = { technique ->
                        gameState = gameState.withSelectedTechnique(technique)
                    },
                    onFindTechniques = {
                        // TODO: Implement technique finding
                    },
                    onApplyTechnique = {
                        // TODO: Implement technique application
                    },
                    onHighlightModeChange = { mode ->
                        gameState = applyHighlights(gameState.withHighlightMode(mode))
                    },
                    onPlayModeChange = { mode ->
                        var newState = gameState.withPlayMode(mode)
                        // Clear secondary selection when switching to Fast mode
                        if (mode == PlayMode.FAST) {
                            newState = newState.copy(selectedNumber2 = null)
                        }
                        gameState = applyHighlights(newState)
                    },
                    onPencilToggle = {
                        gameState = gameState.togglePencilMode()
                    },
                    onSetNumberInCell = {
                        val cellIndex = gameState.selectedCell ?: return@GameScreen
                        val number = gameState.selectedNumber1 ?: return@GameScreen
                        val cell = gameState.grid.getCell(cellIndex)
                        
                        if (!cell.isGiven && !cell.isSolved) {
                            val newGrid = gameState.grid.withCellValue(cellIndex, number)
                            gameState = applyHighlights(gameState.copy(grid = newGrid))
                        }
                    },
                    onRemovePencilMark = {
                        val cellIndex = gameState.selectedCell ?: return@GameScreen
                        val number = gameState.selectedNumber1 ?: return@GameScreen
                        val cell = gameState.grid.getCell(cellIndex)
                        
                        if (number in cell.candidates) {
                            val newGrid = gameState.grid.toggleCandidate(cellIndex, number)
                            gameState = applyHighlights(gameState.copy(grid = newGrid))
                        }
                    }
                )
            }
        }
    }
}
