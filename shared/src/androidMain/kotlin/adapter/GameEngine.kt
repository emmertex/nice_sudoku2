package adapter

import domain.SudokuGrid

/**
 * Android implementation - connects to backend API via network
 * TODO: Implement network calls to backend API
 */
actual class GameEngine actual constructor() {
    
    private var grid: SudokuGrid = SudokuGrid.empty()
    private var currentMatches: Map<String, List<Any>> = emptyMap()
    private var selectedTechniqueKey: String? = null
    
    actual fun loadPuzzle(puzzle: String): Boolean {
        val parsed = SudokuGrid.fromString(puzzle)
        return if (parsed != null) {
            grid = parsed
            currentMatches = emptyMap()
            selectedTechniqueKey = null
            true
        } else {
            false
        }
    }
    
    actual fun setCellValue(cellIndex: Int, value: Int?) {
        val cell = grid.getCell(cellIndex)
        if (!cell.isGiven) {
            grid = grid.withCellValue(cellIndex, value)
        }
    }
    
    actual fun findAllTechniques() {
        // TODO: Implement network call to backend
        currentMatches = emptyMap()
    }
    
    actual fun findBasicTechniques() {
        // TODO: Implement network call to backend
        currentMatches = emptyMap()
    }
    
    actual fun applyBasicTechniques() {
        // TODO: Implement network call to backend
    }
    
    actual fun solve() {
        // TODO: Implement network call to backend
    }
    
    actual fun selectTechnique(technique: String) {
        selectedTechniqueKey = technique
    }
    
    actual fun getCurrentGrid(): SudokuGrid {
        return grid
    }
    
    actual fun getMatches(): Map<String, List<Any>> {
        return currentMatches
    }
    
    actual fun getSelectedTechnique(): String? {
        return selectedTechniqueKey
    }
    
    actual fun clearMatches() {
        currentMatches = emptyMap()
        selectedTechniqueKey = null
    }
}
