package repository

import domain.GameState
import kotlinx.coroutines.flow.StateFlow

expect class GameViewModel(
    repository: GameRepository = GameRepository()
) {
    val gameState: StateFlow<GameState>

    fun onCellClick(cellIndex: Int)
    fun onNumberInput(number: Int)
    fun onEraseInput()
    fun onTechniqueClick(technique: String)
    fun onFindTechniques()
    fun onApplyTechnique()
    fun onUndo()
    fun onRedo()
    fun onSolve()
    fun loadSamplePuzzle(index: Int = 0)
    fun canUndo(): Boolean
    fun canRedo(): Boolean
}
