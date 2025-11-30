package adapter

import domain.SudokuGrid
import domain.SudokuCell
import domain.HighlightColor
import domain.Converters
import sudoku.DataStorage.BasicGrid
import sudoku.DataStorage.SBRCGrid
import sudoku.read.SudokuGridParser
import sudoku.Solvingtech.FindBasics
import sudoku.Solvingtech.BruteForceSolver
import sudoku.match.TechniqueMatch
import sudoku.solvingtechClassifier.Technique
import sudoku.HelpingTools.cardinals

/**
 * JVM implementation of the game engine using the Java StormDoku library
 */
actual class GameEngine actual constructor() {
    
    private var basicGrid: BasicGrid = BasicGrid()
    private var sbrcGrid: SBRCGrid? = null
    private var currentMatches: Map<Technique, List<TechniqueMatch>> = emptyMap()
    private var selectedTechniqueKey: Technique? = null
    
    init {
        // Initialize with an empty grid
        basicGrid = BasicGrid()
    }
    
    actual fun loadPuzzle(puzzle: String): Boolean {
        return try {
            basicGrid = SudokuGridParser.readPuzzleString(puzzle)
            rebuildSBRC()
            currentMatches = emptyMap()
            selectedTechniqueKey = null
            true
        } catch (e: Exception) {
            println("Error loading puzzle: ${e.message}")
            false
        }
    }
    
    actual fun setCellValue(cellIndex: Int, value: Int?) {
        if (value != null && value in 1..9) {
            // Set as solved (not given)
            basicGrid.setSolved(cellIndex, value - 1, false)
        } else {
            // Clear the cell - need to reset candidates
            // This is tricky with BasicGrid - we need to recreate candidates
            val newGrid = BasicGrid()
            for (cell in 0 until cardinals.Length) {
                if (cell == cellIndex) {
                    // Reset this cell to all candidates
                    // BasicGrid initializes with all candidates, so we leave it
                } else {
                    val solved = basicGrid.getSolved(cell)
                    if (solved.isPresent) {
                        newGrid.setSolved(cell, solved.asInt, basicGrid.isGiven(cell))
                    } else {
                        // Copy candidates
                        for (d in 0 until 9) {
                            if (!basicGrid.hasCandidate(cell, d)) {
                                newGrid.clearCandidate(cell, d)
                            }
                        }
                    }
                }
            }
            newGrid.cleanUpCandidates()
            basicGrid = newGrid
        }
        rebuildSBRC()
    }
    
    fun toggleCandidate(cellIndex: Int, candidate: Int) {
        val solved = basicGrid.getSolved(cellIndex)
        if (!solved.isPresent && !basicGrid.isGiven(cellIndex)) {
            // candidate is 1-9, but BasicGrid uses 0-8
            val digitIndex = candidate - 1
            if (basicGrid.hasCandidate(cellIndex, digitIndex)) {
                basicGrid.clearCandidate(cellIndex, digitIndex)
            } else {
                // BasicGrid doesn't have an addCandidate method, so we need to recreate the grid
                // For now, we can't easily toggle a candidate back on
                // But we can implement it by checking if the candidate would be valid
            }
            rebuildSBRC()
        }
    }
    
    actual fun findAllTechniques() {
        rebuildSBRC()
        sbrcGrid?.let { sbrc ->
            currentMatches = FindBasics.invoke(sbrc, false)
            println("Found ${currentMatches.values.flatten().size} technique matches")
        }
    }
    
    actual fun findBasicTechniques() {
        findAllTechniques() // For now, same as findAll
    }
    
    actual fun applyBasicTechniques() {
        // Apply the first available match
        val firstMatch = currentMatches.values.flatten().firstOrNull()
        if (firstMatch != null) {
            applyMatch(firstMatch)
            rebuildSBRC()
            currentMatches = emptyMap()
        }
    }
    
    actual fun solve() {
        val solved = IntArray(cardinals.Length)
        val numSolutions = BruteForceSolver.solve(basicGrid, solved, 1)
        if (numSolutions >= 1) {
            basicGrid = BasicGrid.fromDigits(solved)
            rebuildSBRC()
        }
    }
    
    actual fun selectTechnique(technique: String) {
        selectedTechniqueKey = try {
            Technique.valueOf(technique)
        } catch (e: Exception) {
            // Try to find by name
            Technique.entries.find { it.name == technique || it.getName() == technique }
        }
    }
    
    actual fun getCurrentGrid(): SudokuGrid {
        return Converters.basicGridToSudokuGrid(basicGrid)
    }
    
    actual fun getMatches(): Map<String, List<Any>> {
        return currentMatches.mapKeys { it.key.name }
            .mapValues { entry -> entry.value.map { it as Any } }
    }
    
    actual fun getSelectedTechnique(): String? {
        return selectedTechniqueKey?.name
    }
    
    actual fun clearMatches() {
        currentMatches = emptyMap()
        selectedTechniqueKey = null
    }
    
    private fun rebuildSBRC() {
        sbrcGrid = SBRCGrid(basicGrid)
    }
    
    private fun applyMatch(match: TechniqueMatch) {
        // Apply eliminations
        for ((digit, cells) in match.eliminations) {
            var cell = cells.nextSetBit(0)
            while (cell >= 0) {
                basicGrid.clearCandidate(cell, digit)
                cell = cells.nextSetBit(cell + 1)
            }
        }
        
        // Apply solved cells
        for ((cell, digit) in match.solvedCells) {
            basicGrid.setSolved(cell, digit, false)
        }
        
        basicGrid.cleanUpCandidates()
    }
}
