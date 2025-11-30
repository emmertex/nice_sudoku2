package domain

/**
 * Conversion functions between Java models and Kotlin domain models
 * Note: These functions are implemented in platform-specific code
 */
expect object Converters {
    fun basicGridToSudokuGrid(basicGrid: Any): SudokuGrid
    fun sudokuGridToBasicGrid(grid: SudokuGrid): Any
    fun techniqueMatchToDomainHighlight(match: Any): Pair<Set<Int>, HighlightColor>
}
