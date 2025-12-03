package domain

/**
 * Android implementation of converters - stub since Android doesn't use StormDoku.jar
 */
actual object Converters {
    actual fun basicGridToSudokuGrid(basicGrid: Any): SudokuGrid {
        // Not used on Android - would come from network API
        return SudokuGrid.empty()
    }
    
    actual fun sudokuGridToBasicGrid(grid: SudokuGrid): Any {
        // Not used on Android - would be sent to network API
        return Unit
    }
    
    actual fun techniqueMatchToDomainHighlight(match: Any): Pair<Set<Int>, HighlightColor> {
        // Not used on Android - would come from network API
        return Pair(emptySet(), HighlightColor.RED)
    }
}
