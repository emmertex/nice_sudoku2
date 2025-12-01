package domain

/**
 * Domain model for a complete Sudoku grid
 */
data class SudokuGrid(
    val cells: List<SudokuCell> = List(81) { SudokuCell.empty(it) }
) {
    init {
        require(cells.size == 81) { "Sudoku grid must have exactly 81 cells" }
        require(cells.indices.all { cells[it].index == it }) { "Cell indices must match their positions" }
    }

    val isComplete: Boolean get() = cells.all { it.isSolved }

    val isValid: Boolean get() {
        // Check rows, columns, and boxes for duplicates
        for (i in 0..8) {
            if (!isUnitValid(getRow(i)) ||
                !isUnitValid(getColumn(i)) ||
                !isUnitValid(getBox(i))) {
                return false
            }
        }
        return true
    }

    fun getCell(index: Int): SudokuCell = cells[index]

    fun getCell(row: Int, col: Int): SudokuCell = cells[row * 9 + col]

    fun getRow(row: Int): List<SudokuCell> = cells.slice(row * 9 until (row + 1) * 9)

    fun getColumn(col: Int): List<SudokuCell> = (0..8).map { row -> getCell(row, col) }

    fun getBox(box: Int): List<SudokuCell> {
        val startRow = (box / 3) * 3
        val startCol = (box % 3) * 3
        return (0..8).map { i ->
            val row = startRow + i / 3
            val col = startCol + i % 3
            getCell(row, col)
        }
    }

    fun updateCell(cellIndex: Int, updater: (SudokuCell) -> SudokuCell): SudokuGrid {
        return copy(cells = cells.mapIndexed { index, cell ->
            if (index == cellIndex) updater(cell) else cell
        })
    }

    fun withCellValue(cellIndex: Int, value: Int?, given: Boolean = false): SudokuGrid {
        return updateCell(cellIndex) { it.withValue(value, given) }
    }

    fun withCellCandidates(cellIndex: Int, candidates: Set<Int>): SudokuGrid {
        return updateCell(cellIndex) { it.withCandidates(candidates) }
    }
    
    fun withCellUserEliminations(cellIndex: Int, eliminations: Set<Int>): SudokuGrid {
        return updateCell(cellIndex) { it.withUserEliminations(eliminations) }
    }

    fun toggleCandidate(cellIndex: Int, candidate: Int): SudokuGrid {
        return updateCell(cellIndex) { it.toggleCandidate(candidate) }
    }
    
    /**
     * Toggle a user elimination for a specific cell.
     * This is what should be called when user manually toggles a pencil mark.
     */
    fun toggleUserElimination(cellIndex: Int, candidate: Int): SudokuGrid {
        return updateCell(cellIndex) { it.toggleUserElimination(candidate) }
    }

    fun highlightCells(cellIndices: Set<Int>, color: HighlightColor?): SudokuGrid {
        return copy(cells = cells.mapIndexed { index, cell ->
            if (index in cellIndices) {
                cell.withHighlight(color)
            } else {
                cell
            }
        })
    }

    fun clearHighlights(): SudokuGrid {
        return copy(cells = cells.map { it.withHighlight(null) })
    }

    companion object {
        fun empty(): SudokuGrid = SudokuGrid()

        fun fromString(puzzleString: String): SudokuGrid? {
            if (puzzleString.length != 81) return null

            val cells = puzzleString.mapIndexed { index, char ->
                when {
                    char.isDigit() && char != '0' -> {
                        SudokuCell.solved(index, char.digitToInt(), true)
                    }
                    char == '.' || char == '0' -> {
                        SudokuCell.empty(index)
                    }
                    else -> return null
                }
            }

            return SudokuGrid(cells)
        }

        private fun isUnitValid(unit: List<SudokuCell>): Boolean {
            val solvedValues = unit.mapNotNull { it.value }.toSet()
            return solvedValues.size == solvedValues.count()
        }
    }
}


