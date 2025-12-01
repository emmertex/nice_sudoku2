package domain

/**
 * Domain model for a Sudoku cell
 * 
 * Candidates are split into two components:
 * - candidates: Auto-calculated possible values based on Sudoku rules (can be regenerated)
 * - userEliminations: Candidates the user has manually eliminated (persisted separately)
 * 
 * The displayed candidates = candidates - userEliminations
 */
data class SudokuCell(
    val index: Int,
    val value: Int? = null, // 1-9 for solved cells, null for empty
    val candidates: Set<Int> = emptySet(), // Auto-calculated possible values (1-9)
    val userEliminations: Set<Int> = emptySet(), // Candidates manually eliminated by user
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
    
    /**
     * The candidates to display = auto-calculated candidates minus user eliminations.
     * This is what should be shown in the UI and sent to the API.
     */
    val displayCandidates: Set<Int> get() = candidates - userEliminations

    fun withValue(newValue: Int?, given: Boolean = false): SudokuCell {
        return copy(
            value = newValue,
            candidates = if (newValue != null) emptySet() else candidates,
            userEliminations = if (newValue != null) emptySet() else userEliminations,
            isGiven = if (newValue != null) given else isGiven
        )
    }

    fun withCandidates(newCandidates: Set<Int>): SudokuCell {
        return copy(
            candidates = newCandidates,
            value = if (newCandidates.isEmpty() && value == null) null else value
        )
    }
    
    /**
     * Set user eliminations (candidates the user has manually removed)
     */
    fun withUserEliminations(eliminations: Set<Int>): SudokuCell {
        return copy(userEliminations = eliminations)
    }

    /**
     * Toggle a user elimination. If the candidate is eliminated, restore it.
     * If the candidate is not eliminated, eliminate it.
     */
    fun toggleUserElimination(candidate: Int): SudokuCell {
        val newEliminations = if (candidate in userEliminations) {
            userEliminations - candidate
        } else {
            userEliminations + candidate
        }
        return copy(userEliminations = newEliminations)
    }

    /**
     * @deprecated Use toggleUserElimination instead for user actions.
     * This method is kept for backward compatibility with auto-calculation.
     */
    fun toggleCandidate(candidate: Int): SudokuCell {
        // When user toggles a candidate, we toggle the user elimination
        return toggleUserElimination(candidate)
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
        fun empty(index: Int): SudokuCell = SudokuCell(
            index = index, 
            candidates = (1..9).toSet(),
            userEliminations = emptySet()
        )
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


