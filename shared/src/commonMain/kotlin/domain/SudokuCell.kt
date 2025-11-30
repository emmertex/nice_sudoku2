package domain

/**
 * Domain model for a Sudoku cell
 */
data class SudokuCell(
    val index: Int,
    val value: Int? = null, // 1-9 for solved cells, null for empty
    val candidates: Set<Int> = emptySet(), // Set of possible values (1-9)
    val isGiven: Boolean = false, // Whether this was part of the original puzzle
    val isHighlighted: Boolean = false, // For technique highlighting
    val highlightColor: HighlightColor? = null,
    // Multi-layer highlight support for number selection
    val highlightType: CellHighlightType = CellHighlightType.NONE
) {
    val row: Int get() = index / 9
    val col: Int get() = index % 9
    val box: Int get() = (row / 3) * 3 + (col / 3)

    val isSolved: Boolean get() = value != null

    fun withValue(newValue: Int?, given: Boolean = false): SudokuCell {
        return copy(
            value = newValue,
            candidates = if (newValue != null) emptySet() else candidates,
            isGiven = if (newValue != null) given else isGiven
        )
    }

    fun withCandidates(newCandidates: Set<Int>): SudokuCell {
        return copy(
            candidates = newCandidates,
            value = if (newCandidates.isEmpty() && value == null) null else value
        )
    }

    fun toggleCandidate(candidate: Int): SudokuCell {
        val newCandidates = if (candidate in candidates) {
            candidates - candidate
        } else {
            candidates + candidate
        }
        return copy(candidates = newCandidates)
    }

    fun withHighlight(color: HighlightColor?): SudokuCell {
        return copy(
            isHighlighted = color != null,
            highlightColor = color
        )
    }

    fun withHighlightType(type: CellHighlightType): SudokuCell {
        return copy(highlightType = type)
    }

    companion object {
        fun empty(index: Int): SudokuCell = SudokuCell(index = index, candidates = (1..9).toSet())
        fun solved(index: Int, value: Int, given: Boolean = false): SudokuCell =
            SudokuCell(index = index, value = value, isGiven = given)
    }
}

enum class HighlightColor {
    RED,
    BLUE,
    GREEN,
    YELLOW,
    PURPLE,
    ORANGE,
    PINK,
    CYAN
}

/**
 * Multi-layer highlight types for number selection highlighting
 */
enum class CellHighlightType {
    /** No highlight */
    NONE,
    /** Primary highlight (light blue) - first selected number */
    PRIMARY,
    /** Secondary highlight (light red) - second selected number */
    SECONDARY,
    /** Intersection highlight (light purple) - both numbers */
    INTERSECTION
}


