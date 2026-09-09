import kotlin.test.*
import helpers.importExport.SudokuCoachFormat
import domain.SudokuGrid

class SudokuCoachFormatTest {
    @Test fun existingCoachFixtureStillImports() {
        val result = SudokuCoachFormat.importFromSudokuCoach("SCv7_32_f2eaqkebdq1j047s2ufliqfso1feeinvk33ql1tk41i0i98i14ur9qlveu6q2a09nlqlehlrpim7dhnuabuphmn7ujcqigtcqjtnie2fsd2hqnmkrco7bqlla9uvlq2e9ukkulvplmg02m600c33ak5q6n6dkh9p7b062pgai33h002uu3d87oc4lub0rjdqb5avu75ic6t79or6s4v3c0qpkn5p53f8mfm0l9gvf0rt6uipm3jg1eulj9vr0s2br783mhqd6s6crsekkrr7hrfibq79sg4e94tke88od6h62ok3bhsp0ttps867gps84d9m1ds0uei3o55huk17n2gm08plic4lr7g761ei6cem9e7khskpftubcbie7i248eqli9vas2khd7dmpppapo2pumm583482c8u1ct488nt5irnpreknm6u81uvu0401afp6")
        assertNotNull(result)
        assertEquals("010003006002050000030000000000200300000900017040006000021000070083000009560804200", result.originalPuzzle)
    }
    @Test fun unicodeMetadataAndBlankNotesRoundTrip() {
        val puzzle = "0".repeat(81)
        val grid = SudokuGrid.empty().withCellUserEliminations(0, (1..9).toSet())
        val encoded = SudokuCoachFormat.exportToSudokuCoach(grid, puzzle, title = "日本語 & **notes**")!!
        val imported = SudokuCoachFormat.importFromSudokuCoach(encoded)!!
        assertEquals("日本語 & **notes**", imported.title)
        assertEquals((1..9).toSet(), imported.userEliminations[0])
    }
    @Test fun excessiveInputAndInvalidGivensAreRejected() {
        assertNull(SudokuCoachFormat.importFromSudokuCoach("SCv7_32_" + "a".repeat(262144)))
        val encoded = SudokuCoachFormat.exportToSudokuCoach(SudokuGrid.empty(), "1".repeat(81))!!
        assertNull(SudokuCoachFormat.importFromSudokuCoach(encoded))
    }
}
