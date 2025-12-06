package domain

import kotlinx.serialization.Serializable

/**
 * Represents a puzzle definition from the puzzle library
 */
@Serializable
data class PuzzleDefinition(
    val id: String,           // Unique identifier (puzzleId from JSON)
    val puzzleString: String, // 81-char puzzle string (givens)
    val difficulty: Float,    // Difficulty rating from solver
    val category: DifficultyCategory = DifficultyCategory.EASY,  // Default for backwards compatibility
    val solution: String? = null,  // 81-char solution string (pre-computed)
    val quality: Float? = null,    // Quality rating (0-10)
    val techniques: Map<String, Int>? = null,  // Techniques used: name -> count
    val title: String? = null,     // Optional title (for training puzzles)
    val url: String? = null        // Optional URL (for training puzzles)
)

enum class DifficultyCategory(val displayName: String) {
    BEGINNER("Beginner"),
    EASY("Easy"),
    MEDIUM("Medium"),
    TOUGH("Tough"),
    HARD("Hard"),
    EXPERT("Expert"),
    DIABOLICAL("Diabolical"),
    CUSTOM("Custom");
    
    companion object {
        /**
         * Determine the appropriate category based on difficulty value.
         * Used for displaying saved games when the original category might not exist.
         */
        fun fromDifficulty(difficulty: Float): DifficultyCategory {
            return when {
                difficulty < 10f -> BEGINNER
                difficulty < 18f -> EASY
                difficulty < 25f -> MEDIUM
                difficulty < 40f -> TOUGH
                difficulty < 60f -> HARD
                difficulty < 80f -> EXPERT
                else -> DIABOLICAL
            }
        }
    }
}

/**
 * Represents a saved game state
 * 
 * The storage format uses USER ELIMINATIONS rather than shown notes.
 * This ensures user eliminations are never lost when auto-calculated candidates change.
 * 
 * Storage: 81 values + 729 eliminations (1 = user eliminated, 0 = not eliminated)
 * Display: displayCandidates = auto-calculated candidates - userEliminations
 */
@Serializable
data class SavedGameState(
    val puzzleId: String,
    val puzzleString: String,        // Original 81-char puzzle
    val currentState: String,        // 810-char state (81 values + 729 user eliminations)
    val solution: String?,           // 81-char solution (from background solver)
    val category: DifficultyCategory = DifficultyCategory.EASY,  // Default for backwards compatibility
    val difficulty: Float,
    val elapsedTimeMs: Long,         // Time spent on puzzle
    val mistakeCount: Int,           // Number of mistakes made
    val isCompleted: Boolean,
    val lastPlayedTimestamp: Long,
    val actionStack: List<String> = emptyList()  // Eureka notation actions for undo (e.g., "R1C5=7", "R3C8<>4")
) {
    companion object {
        /**
         * Parse a 810-char state string into values and user eliminations.
         * 
         * First 81 chars: cell values (1-9 for solved, 0 for empty)
         * Next 729 chars: user eliminations (9 chars per cell, 1 = eliminated by user, 0 = not eliminated)
         * 
         * @return Pair of (values array, user eliminations array)
         */
        fun parseStateString(state: String): Pair<IntArray, Array<Set<Int>>> {
            val values = IntArray(81)
            val userEliminations = Array<Set<Int>>(81) { emptySet() }
            
            if (state.length >= 81) {
                // Parse values
                for (i in 0 until 81) {
                    values[i] = state[i].digitToIntOrNull() ?: 0
                }
                
                // Parse user eliminations if present
                if (state.length >= 810) {
                    for (cell in 0 until 81) {
                        val eliminationSet = mutableSetOf<Int>()
                        for (n in 0 until 9) {
                            val idx = 81 + cell * 9 + n
                            if (state[idx] == '1') {
                                // 1 = user eliminated this candidate
                                eliminationSet.add(n + 1)
                            }
                        }
                        userEliminations[cell] = eliminationSet
                    }
                }
            }
            
            return Pair(values, userEliminations)
        }
        
        /**
         * Parse a 810-char state string from the OLD format (notes shown) and convert to eliminations.
         * This is used for importing shared puzzles where 1 = note shown.
         * 
         * @return Pair of (values array, user eliminations array) with inverted notes
         */
        fun parseStateStringFromNotesFormat(state: String): Pair<IntArray, Array<Set<Int>>> {
            val values = IntArray(81)
            val userEliminations = Array<Set<Int>>(81) { emptySet() }
            
            if (state.length >= 81) {
                // Parse values
                for (i in 0 until 81) {
                    values[i] = state[i].digitToIntOrNull() ?: 0
                }
                
                // Parse notes and invert to eliminations
                if (state.length >= 810) {
                    for (cell in 0 until 81) {
                        val eliminationSet = mutableSetOf<Int>()
                        for (n in 0 until 9) {
                            val idx = 81 + cell * 9 + n
                            // In notes format: 1 = shown, 0 = hidden
                            // We want eliminations: 1 = eliminated (hidden), 0 = not eliminated (can be shown)
                            // So we invert: if note is 0 (hidden), elimination is 1 (eliminated)
                            if (state[idx] == '0') {
                                eliminationSet.add(n + 1)
                            }
                        }
                        userEliminations[cell] = eliminationSet
                    }
                }
            }
            
            return Pair(values, userEliminations)
        }
        
        /**
         * Create a 810-char state string from grid using USER ELIMINATIONS format.
         * Format: 81 chars for cell values + 729 chars for user eliminations (9 per cell)
         * 
         * Each cell has 9 characters representing candidates 1-9:
         * - '1' = user has eliminated this candidate
         * - '0' = user has not eliminated this candidate (can be shown if auto-calc allows)
         */
        fun createStateString(grid: SudokuGrid): String {
            val sb = StringBuilder(810)
            
            // First 81 chars: values (1-9 for solved cells, 0 for empty)
            for (i in 0 until 81) {
                val cell = grid.getCell(i)
                sb.append(if (cell.isSolved) cell.value else 0)
            }
            
            // Next 729 chars: user eliminations (1 = eliminated, 0 = not eliminated)
            for (i in 0 until 81) {
                val cell = grid.getCell(i)
                for (n in 1..9) {
                    // Save user eliminations - 1 if user eliminated, 0 if not
                    sb.append(if (n in cell.userEliminations && !cell.isSolved) '1' else '0')
                }
            }
            
            return sb.toString()
        }
        
        /**
         * Create a 810-char state string in the NOTES format for export/sharing.
         * Format: 81 chars for cell values + 729 chars for displayed notes (9 per cell)
         * 
         * Each cell has 9 characters representing candidates 1-9:
         * - '1' = note is displayed (candidate - userElimination)
         * - '0' = note is not displayed
         * 
         * This format is compatible with other Sudoku apps for sharing.
         */
        fun createStateStringForExport(grid: SudokuGrid): String {
            val sb = StringBuilder(810)
            
            // First 81 chars: values (1-9 for solved cells, 0 for empty)
            for (i in 0 until 81) {
                val cell = grid.getCell(i)
                sb.append(if (cell.isSolved) cell.value else 0)
            }
            
            // Next 729 chars: displayed notes (1 = shown, 0 = hidden)
            // displayCandidates = candidates - userEliminations
            for (i in 0 until 81) {
                val cell = grid.getCell(i)
                for (n in 1..9) {
                    // Export the displayed candidates (what user sees)
                    sb.append(if (n in cell.displayCandidates && !cell.isSolved) '1' else '0')
                }
            }
            
            return sb.toString()
        }
    }
}

/**
 * Summary of a saved game for display in list
 */
@Serializable
data class GameSummary(
    val puzzleId: String,
    val category: DifficultyCategory = DifficultyCategory.EASY,  // Default for backwards compatibility
    val difficulty: Float,
    val elapsedTimeMs: Long,
    val mistakeCount: Int,
    val isCompleted: Boolean,
    val progressPercent: Int,    // 0-100
    val lastPlayedTimestamp: Long
)

