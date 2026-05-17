import domain.*

/**
 * Extension functions for highlight computation in SudokuApp.
 */

internal fun SudokuApp.getPrimaryHighlightCells(grid: SudokuGrid): Set<Int> {
    if (selectedNumbers1.isEmpty()) return emptySet()
    return selectedNumbers1.map { getHighlightCellsForNumber(grid, it) }
        .reduce { acc, set -> acc.intersect(set) }
}

internal fun SudokuApp.getHighlightCellsForNumber(grid: SudokuGrid, number: Int): Set<Int> {
    return when (highlightMode) {
        HighlightMode.PLACED -> {
            grid.cells.filter { it.value == number }.map { it.index }.toSet()
        }
        HighlightMode.RCB_SELECTED -> {
            val result = mutableSetOf<Int>()
            grid.cells.filter { it.value == number }.forEach { result.add(it.index) }
            selectedCell?.let { cellIdx ->
                val selectedCellData = grid.getCell(cellIdx)
                result.addAll(getRelatedCellIndices(selectedCellData.row, selectedCellData.col, selectedCellData.box))
            }
            result
        }
        HighlightMode.RCB_ALL -> {
            val result = mutableSetOf<Int>()
            grid.cells.filter { it.value == number }.forEach { cell ->
                result.addAll(getRelatedCellIndices(cell.row, cell.col, cell.box))
                result.add(cell.index)
            }
            result
        }
        HighlightMode.PENCIL -> {
            grid.cells.filter { number in it.displayCandidates }.map { it.index }.toSet()
        }
    }
}

internal fun SudokuApp.getRelatedCellIndices(row: Int, col: Int, box: Int): Set<Int> {
    val result = mutableSetOf<Int>()
    for (c in 0 until 9) result.add(row * 9 + c)
    for (r in 0 until 9) result.add(r * 9 + col)
    val boxStartRow = (box / 3) * 3
    val boxStartCol = (box % 3) * 3
    for (r in boxStartRow until boxStartRow + 3) {
        for (c in boxStartCol until boxStartCol + 3) {
            result.add(r * 9 + c)
        }
    }
    return result
}
