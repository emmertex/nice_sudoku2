package domain

import sudoku.DataStorage.BasicGrid
import sudoku.HelpingTools.cardinals

/**
 * JVM implementation of conversion functions using StormDoku library types
 */
actual object Converters {
    
    actual fun basicGridToSudokuGrid(basicGrid: Any): SudokuGrid {
        val grid = basicGrid as BasicGrid
        val cells = (0 until cardinals.Length).map { cellIndex ->
            val solved = grid.getSolved(cellIndex)
            if (solved.isPresent) {
                SudokuCell.solved(
                    index = cellIndex,
                    value = solved.asInt + 1, // StormDoku uses 0-8, we use 1-9
                    given = grid.isGiven(cellIndex)
                )
            } else {
                // Get candidates
                val candidates = (0 until 9)
                    .filter { grid.hasCandidate(cellIndex, it) }
                    .map { it + 1 } // Convert 0-8 to 1-9
                    .toSet()
                SudokuCell(
                    index = cellIndex,
                    value = null,
                    candidates = candidates,
                    isGiven = false
                )
            }
        }
        return SudokuGrid(cells)
    }
    
    actual fun sudokuGridToBasicGrid(grid: SudokuGrid): Any {
        val basicGrid = BasicGrid()
        for (cell in grid.cells) {
            if (cell.isSolved && cell.value != null) {
                basicGrid.setSolved(cell.index, cell.value - 1, cell.isGiven)
            } else {
                // Set candidates from displayCandidates (auto-calculated minus user eliminations)
                // This ensures the backend sees what the user sees
                for (d in 0 until 9) {
                    if ((d + 1) !in cell.displayCandidates) {
                        basicGrid.clearCandidate(cell.index, d)
                    }
                }
            }
        }
        basicGrid.cleanUpCandidates()
        return basicGrid
    }
    
    actual fun techniqueMatchToDomainHighlight(match: Any): Pair<Set<Int>, HighlightColor> {
        // For now, return a basic highlight
        // Full implementation would extract cells from TechniqueMatch
        val techniqueMatch = match as? sudoku.match.TechniqueMatch
        if (techniqueMatch != null) {
            val cells = mutableSetOf<Int>()
            // Add elimination cells
            for ((_, cellSet) in techniqueMatch.eliminations) {
                var cell = cellSet.nextSetBit(0)
                while (cell >= 0) {
                    cells.add(cell)
                    cell = cellSet.nextSetBit(cell + 1)
                }
            }
            // Add solved cells
            cells.addAll(techniqueMatch.solvedCells.keys)
            return Pair(cells, HighlightColor.BLUE)
        }
        return Pair(emptySet(), HighlightColor.BLUE)
    }
}
