package com.nicesudoku.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import domain.GameState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GameViewModel() : ViewModel() {
    private val _gameState = MutableStateFlow(GameState.initial())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    fun onCellClick(cellIndex: Int) {
        // TODO: Implement cell selection
        println("Cell clicked: $cellIndex")
    }

    fun onNumberInput(number: Int) {
        // TODO: Implement number input
        println("Number input: $number")
    }

    fun onEraseInput() {
        // TODO: Implement erase input
        println("Erase input")
    }

    fun onTechniqueClick(technique: String) {
        // TODO: Implement technique selection
        println("Technique clicked: $technique")
    }

    fun onFindTechniques() {
        // TODO: Implement technique finding
        println("Find techniques")
    }

    fun onApplyTechnique() {
        // TODO: Implement technique application
        println("Apply technique")
    }

    fun onUndo() {
        // TODO: Implement undo
        println("Undo")
    }

    fun onRedo() {
        // TODO: Implement redo
        println("Redo")
    }

    fun onSolve() {
        // TODO: Implement solve
        println("Solve")
    }

    fun loadSamplePuzzle(index: Int = 0) {
        // TODO: Implement puzzle loading
        println("Load sample puzzle: $index")
    }

    fun canUndo(): Boolean = false
    fun canRedo(): Boolean = false
}
