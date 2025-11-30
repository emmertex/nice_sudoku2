package com.nicesudoku.android

import androidx.lifecycle.ViewModel
import repository.GameRepository

class GameViewModel(
    private val repository: GameRepository = GameRepository()
) : ViewModel() {
    val gameState = repository.gameState

    fun onCellClick(cellIndex: Int) {
        repository.selectCell(cellIndex)
    }

    fun onNumberInput(number: Int) {
        val selectedCell = gameState.value.selectedCell
        if (selectedCell != null) {
            repository.setCellValue(selectedCell, number)
        }
    }

    fun onEraseInput() {
        val selectedCell = gameState.value.selectedCell
        if (selectedCell != null) {
            repository.setCellValue(selectedCell, null)
        }
    }

    fun onTechniqueClick(technique: String) {
        repository.selectTechnique(technique)
    }

    fun onFindTechniques() {
        repository.findAllTechniques()
    }

    fun onApplyTechnique() {
        repository.applySelectedTechnique()
    }

    fun onUndo() {
        repository.undo()
    }

    fun onRedo() {
        repository.redo()
    }

    fun onSolve() {
        repository.solve()
    }

    fun loadSamplePuzzle(index: Int = 0) {
        val puzzles = GameRepository.samplePuzzles
        if (index in puzzles.indices) {
            repository.loadPuzzle(puzzles[index])
        }
    }

    fun canUndo(): Boolean = repository.canUndo()
    fun canRedo(): Boolean = repository.canRedo()
}
