import domain.*

/**
 * Extension functions for highlight computation in SudokuApp.
 */

// Compute which cells should have primary highlight (light blue)
// Uses AND logic - only cells containing ALL selected numbers are highlighted
internal fun SudokuApp.getPrimaryHighlightCells(grid: SudokuGrid): Set<Int> {
    // In CELL_FIRST mode with no selected number, use RCB_SELECTED mode based on selected cell
    if (playMode == PlayMode.CELL_FIRST && selectedNumbers1.isEmpty() && selectedCell != null) {
        // For CELL_FIRST mode without a number, fall back to RCB_SELECTED behavior
        val effectiveMode = when (highlightMode) {
            HighlightMode.RCB_ALL, HighlightMode.PENCIL -> HighlightMode.RCB_SELECTED
            else -> highlightMode
        }
        // Get all cells in RCB of selected cell
        if (effectiveMode == HighlightMode.RCB_SELECTED) {
            val cell = grid.getCell(selectedCell!!)
            return getRelatedCellIndices(cell.row, cell.col, cell.box)
        }
        return emptySet()
    }
    
    if (selectedNumbers1.isEmpty()) return emptySet()
    // Get cells for each number, then intersect (AND logic)
    return selectedNumbers1.map { getHighlightCellsForNumber(grid, it) }
        .reduce { acc, set -> acc.intersect(set) }
}

// Compute which cells should have secondary highlight (light red)
// Uses AND logic - only cells containing ALL selected numbers are highlighted
internal fun SudokuApp.getSecondaryHighlightCells(grid: SudokuGrid): Set<Int> {
    if (selectedNumbers2.isEmpty()) return emptySet()
    // Get cells for each number, then intersect (AND logic)
    return selectedNumbers2.map { getHighlightCellsForNumber(grid, it) }
        .reduce { acc, set -> acc.intersect(set) }
}

internal fun SudokuApp.getHighlightCellsForNumber(grid: SudokuGrid, number: Int): Set<Int> {
    return when (highlightMode) {
        HighlightMode.PLACED -> {
            // Highlight cells with matching placed values
            grid.cells.filter { it.value == number }.map { it.index }.toSet()
        }
        HighlightMode.RCB_SELECTED -> {
            // Highlight all cells with matching number (like PLACED)
            // PLUS the entire Row, Column, and Box of the selected cell
            val result = mutableSetOf<Int>()
            
            // Add all cells with the matching number
            grid.cells.filter { it.value == number }.forEach { 
                result.add(it.index) 
            }
            
            // Add all cells in RCB of selected cell
            selectedCell?.let { cellIdx ->
                val selectedCellData = grid.getCell(cellIdx)
                result.addAll(getRelatedCellIndices(selectedCellData.row, selectedCellData.col, selectedCellData.box))
            }
            
            result
        }
        HighlightMode.RCB_ALL -> {
            // For each cell with the number, highlight its row, column, box
            val result = mutableSetOf<Int>()
            grid.cells.filter { it.value == number }.forEach { cell ->
                result.addAll(getRelatedCellIndices(cell.row, cell.col, cell.box))
                result.add(cell.index)
            }
            result
        }
        HighlightMode.PENCIL -> {
            // Highlight cells with matching pencil marks
            grid.cells.filter { number in it.displayCandidates }.map { it.index }.toSet()
        }
    }
}

internal fun SudokuApp.getRelatedCellIndices(row: Int, col: Int, box: Int): Set<Int> {
    val result = mutableSetOf<Int>()
    // Row cells
    for (c in 0 until 9) result.add(row * 9 + c)
    // Column cells
    for (r in 0 until 9) result.add(r * 9 + col)
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

