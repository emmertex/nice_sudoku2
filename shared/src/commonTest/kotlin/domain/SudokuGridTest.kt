package domain

import kotlin.test.*

class SudokuGridTest {
    @Test fun duplicateUnitsAndFullInvalidGrid() {
        assertFalse(SudokuGrid.fromString("1".repeat(81))!!.isValid)
        for (other in listOf(1, 9, 10)) {
            val grid = SudokuGrid.empty().withCellValue(0, 5).withCellValue(other, 5)
            assertFalse(grid.isValid, "duplicate at $other")
        }
        assertTrue(SudokuGrid.empty().withCellValue(0, 5).withCellValue(40, 5).isValid)
    }
}
