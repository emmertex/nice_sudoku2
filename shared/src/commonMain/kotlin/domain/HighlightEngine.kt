package domain

/**
 * Engine for computing cell highlights based on the current highlight mode and selected numbers.
 */
object HighlightEngine {

    /**
     * Compute highlight types for all cells based on the current state.
     * 
     * @param grid The current Sudoku grid
     * @param highlightMode The active highlight mode
     * @param selectedNumber1 The primary selected number (1-9), or null if none
     * @param selectedNumber2 The secondary selected number (1-9), or null if none
     * @param selectedCell The currently selected cell index, if any (for RCB_SELECTED mode)
     * @return A map of cell index to CellHighlightType
     */
    fun computeHighlights(
        grid: SudokuGrid,
        highlightMode: HighlightMode,
        selectedNumber1: Int?,
        selectedNumber2: Int?,
        selectedCell: Int?
    ): Map<Int, CellHighlightType> {
        val primaryCells = mutableSetOf<Int>()
        val secondaryCells = mutableSetOf<Int>()

        // Compute primary highlights based on mode and selectedNumber1
        if (selectedNumber1 != null) {
            primaryCells.addAll(getCellsToHighlight(grid, highlightMode, selectedNumber1, selectedCell))
        }

        // Compute secondary highlights based on selectedNumber2
        if (selectedNumber2 != null) {
            secondaryCells.addAll(getCellsToHighlight(grid, highlightMode, selectedNumber2, selectedCell))
        }

        // Build result map
        val result = mutableMapOf<Int, CellHighlightType>()
        
        for (i in 0 until 81) {
            val isPrimary = i in primaryCells
            val isSecondary = i in secondaryCells
            
            result[i] = when {
                isPrimary && isSecondary -> CellHighlightType.INTERSECTION
                isPrimary -> CellHighlightType.PRIMARY
                isSecondary -> CellHighlightType.SECONDARY
                else -> CellHighlightType.NONE
            }
        }

        return result
    }

    /**
     * Get the set of cell indices to highlight for a given number based on the highlight mode.
     */
    private fun getCellsToHighlight(
        grid: SudokuGrid,
        highlightMode: HighlightMode,
        number: Int,
        selectedCell: Int?
    ): Set<Int> {
        return when (highlightMode) {
            HighlightMode.CELL -> getCellHighlights(grid, number)
            HighlightMode.RCB_SELECTED -> getRCBSelectedHighlights(grid, number, selectedCell)
            HighlightMode.RCB_ALL -> getRCBAllHighlights(grid, number)
            HighlightMode.PENCIL -> getPencilHighlights(grid, number)
        }
    }

    /**
     * CELL mode: Highlight cells with matching solved values.
     */
    private fun getCellHighlights(grid: SudokuGrid, number: Int): Set<Int> {
        return grid.cells
            .filter { it.value == number }
            .map { it.index }
            .toSet()
    }

    /**
     * RCB_SELECTED mode: Highlight the row, column, and box of the selected cell,
     * but only if those cells are relevant to the selected number.
     */
    private fun getRCBSelectedHighlights(
        grid: SudokuGrid,
        number: Int,
        selectedCell: Int?
    ): Set<Int> {
        if (selectedCell == null) return emptySet()

        val cell = grid.getCell(selectedCell)
        val result = mutableSetOf<Int>()

        // Get all cells in the same row, column, and box
        val relatedCells = getRelatedCellIndices(cell.row, cell.col, cell.box)
        
        for (idx in relatedCells) {
            val relatedCell = grid.getCell(idx)
            // Include if cell has the number as value or as candidate
            if (relatedCell.value == number || number in relatedCell.candidates) {
                result.add(idx)
            }
        }

        // Also include the selected cell itself if it contains the number
        if (cell.value == number || number in cell.candidates) {
            result.add(selectedCell)
        }

        return result
    }

    /**
     * RCB_ALL mode: For each cell containing the number, highlight its row, column, and box.
     */
    private fun getRCBAllHighlights(grid: SudokuGrid, number: Int): Set<Int> {
        val result = mutableSetOf<Int>()

        // Find all cells that contain this number
        val cellsWithNumber = grid.cells.filter { it.value == number }

        for (cell in cellsWithNumber) {
            // Add all cells in the same row, column, and box
            result.addAll(getRelatedCellIndices(cell.row, cell.col, cell.box))
            result.add(cell.index)
        }

        return result
    }

    /**
     * PENCIL mode: Highlight cells with matching pencil marks (candidates).
     */
    private fun getPencilHighlights(grid: SudokuGrid, number: Int): Set<Int> {
        return grid.cells
            .filter { number in it.candidates }
            .map { it.index }
            .toSet()
    }

    /**
     * Get all cell indices in the same row, column, or box.
     */
    private fun getRelatedCellIndices(row: Int, col: Int, box: Int): Set<Int> {
        val result = mutableSetOf<Int>()

        // Row cells
        for (c in 0 until 9) {
            result.add(row * 9 + c)
        }

        // Column cells
        for (r in 0 until 9) {
            result.add(r * 9 + col)
        }

        // Box cells
        val boxStartRow = (box / 3) * 3
        val boxStartCol = (box % 3) * 3
        for (r in boxStartRow until boxStartRow + 3) {
            for (c in boxStartCol until boxStartCol + 3) {
                result.add(r * 9 + c)
            }
        }

        return result
    }

    /**
     * Apply computed highlights to a grid.
     */
    fun applyHighlights(grid: SudokuGrid, highlights: Map<Int, CellHighlightType>): SudokuGrid {
        return SudokuGrid(
            cells = grid.cells.map { cell ->
                cell.withHighlightType(highlights[cell.index] ?: CellHighlightType.NONE)
            }
        )
    }
}

