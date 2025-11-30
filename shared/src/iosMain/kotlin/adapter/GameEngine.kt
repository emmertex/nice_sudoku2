package adapter

import domain.SudokuGrid
import sudoku.match.TechniqueMatch
import sudoku.solvingtechClassifier.Technique

/**
 * iOS implementation - TODO: Implement actual game logic for iOS
 */
actual class GameEngine actual constructor() {

    actual fun loadPuzzle(puzzle: String): Boolean {
        // TODO: Implement iOS-specific puzzle loading
        println("iOS: Loading puzzle (not implemented)")
        return false
    }

    actual fun setCellValue(cellIndex: Int, value: Int?) {
        // TODO: Implement iOS-specific cell value setting
        println("iOS: Setting cell $cellIndex to $value (not implemented)")
    }

    actual fun findAllTechniques() {
        // TODO: Implement iOS-specific technique finding
        println("iOS: Finding techniques (not implemented)")
    }

    actual fun findBasicTechniques() {
        // TODO: Implement iOS-specific basic technique finding
        println("iOS: Finding basic techniques (not implemented)")
    }

    actual fun applyBasicTechniques() {
        // TODO: Implement iOS-specific technique application
        println("iOS: Applying techniques (not implemented)")
    }

    actual fun solve() {
        // TODO: Implement iOS-specific solving
        println("iOS: Solving (not implemented)")
    }

    actual fun selectTechnique(technique: Technique) {
        // TODO: Implement iOS-specific technique selection
        println("iOS: Selecting technique $technique (not implemented)")
    }

    actual fun getCurrentGrid(): SudokuGrid {
        // TODO: Return actual grid state
        return SudokuGrid.empty()
    }

    actual fun getMatches(): Map<Technique, List<TechniqueMatch>> {
        // TODO: Return actual matches
        return emptyMap()
    }

    actual fun getSelectedTechnique(): Technique? {
        // TODO: Return actual selected technique
        return null
    }

    actual fun clearMatches() {
        // TODO: Clear matches
        println("iOS: Clearing matches (not implemented)")
    }
}


