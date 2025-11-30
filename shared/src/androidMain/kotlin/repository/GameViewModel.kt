package repository

import androidx.lifecycle.ViewModel

actual class GameViewModel actual constructor(
    private val repository: GameRepository
) : ViewModel() {
    actual val gameState = repository.gameState

    actual fun onCellClick(cellIndex: Int) {
        repository.selectCell(cellIndex)
    }

    actual fun onNumberInput(number: Int) {
        val selectedCell = gameState.value.selectedCell
        if (selectedCell != null) {
            repository.setCellValue(selectedCell, number)
        }
    }

    actual fun onEraseInput() {
        val selectedCell = gameState.value.selectedCell
        if (selectedCell != null) {
            repository.setCellValue(selectedCell, null)
        }
    }

    actual fun onTechniqueClick(technique: String) {
        repository.selectTechnique(technique)
    }

    actual fun onFindTechniques() {
        repository.findAllTechniques()
    }

    actual fun onApplyTechnique() {
        repository.applySelectedTechnique()
    }

    actual fun onUndo() {
        repository.undo()
    }

    actual fun onRedo() {
        repository.redo()
    }

    actual fun onSolve() {
        repository.solve()
    }

    actual fun loadSamplePuzzle(index: Int) {
        val puzzles = GameRepository.samplePuzzles
        if (index in puzzles.indices) {
            repository.loadPuzzle(puzzles[index])
        }
    }

    actual fun canUndo(): Boolean = repository.canUndo()
    actual fun canRedo(): Boolean = repository.canRedo()
}
