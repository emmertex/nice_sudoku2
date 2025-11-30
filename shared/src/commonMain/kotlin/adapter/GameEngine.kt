package adapter

import domain.SudokuGrid

/**
 * Platform-agnostic interface for the game engine
 */
expect class GameEngine() {
    fun loadPuzzle(puzzle: String): Boolean
    fun setCellValue(cellIndex: Int, value: Int?)
    fun findAllTechniques()
    fun findBasicTechniques()
    fun applyBasicTechniques()
    fun solve()
    fun selectTechnique(technique: String)
    fun getCurrentGrid(): SudokuGrid
    fun getMatches(): Map<String, List<Any>>
    fun getSelectedTechnique(): String?
    fun clearMatches()
}
