package domain

import kotlinx.serialization.Serializable

/**
 * Represents a puzzle definition from the puzzle library
 */
@Serializable
data class PuzzleDefinition(
    val id: String,           // Unique identifier (checksum from file)
    val puzzleString: String, // 81-char puzzle string
    val difficulty: Float,    // Difficulty rating (1.2 = easy, 8.5 = hardest)
    val category: DifficultyCategory
) {
    companion object {
        fun fromLine(line: String, category: DifficultyCategory): PuzzleDefinition? {
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size < 3) return null
            
            val checksum = parts[0]
            val puzzleString = parts[1]
            val difficulty = parts[2].toFloatOrNull() ?: return null
            
            if (puzzleString.length != 81) return null
            
            return PuzzleDefinition(
                id = checksum,
                puzzleString = puzzleString,
                difficulty = difficulty,
                category = category
            )
        }
    }
}

enum class DifficultyCategory(val displayName: String) {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard"),
    DIABOLICAL("Diabolical"),
    CUSTOM("Custom")
}

/**
 * Represents a saved game state
 */
@Serializable
data class SavedGameState(
    val puzzleId: String,
    val puzzleString: String,        // Original 81-char puzzle
    val currentState: String,        // 810-char state (81 values + 729 notes)
    val solution: String?,           // 81-char solution (from background solver)
    val category: DifficultyCategory,
    val difficulty: Float,
    val elapsedTimeMs: Long,         // Time spent on puzzle
    val mistakeCount: Int,           // Number of mistakes made
    val isCompleted: Boolean,
    val lastPlayedTimestamp: Long
) {
    companion object {
        /**
         * Parse a 810-char state string into values and notes
         * First 81 chars: cell values (1-9 for solved, 0 for empty)
         * Next 729 chars: notes (9 chars per cell, 1 if note is on, 0 if off)
         */
        fun parseStateString(state: String): Pair<IntArray, Array<Set<Int>>> {
            val values = IntArray(81)
            val notes = Array<Set<Int>>(81) { emptySet() }
            
            if (state.length >= 81) {
                // Parse values
                for (i in 0 until 81) {
                    values[i] = state[i].digitToIntOrNull() ?: 0
                }
                
                // Parse notes if present
                if (state.length >= 810) {
                    for (cell in 0 until 81) {
                        val noteSet = mutableSetOf<Int>()
                        for (n in 0 until 9) {
                            val idx = 81 + cell * 9 + n
                            if (state[idx] == '1') {
                                noteSet.add(n + 1)
                            }
                        }
                        notes[cell] = noteSet
                    }
                }
            }
            
            return Pair(values, notes)
        }
        
        /**
         * Create a 810-char state string from grid
         * Format: 81 chars for cell values + 729 chars for candidates (9 per cell)
         * 
         * IMPORTANT: This function includes all candidates/pencil marks in the saved state.
         * Each cell has 9 characters representing candidates 1-9 (1 = present, 0 = absent).
         */
        fun createStateString(grid: SudokuGrid): String {
            val sb = StringBuilder(810)
            
            // First 81 chars: values (1-9 for solved cells, 0 for empty)
            for (i in 0 until 81) {
                val cell = grid.getCell(i)
                sb.append(if (cell.isSolved) cell.value else 0)
            }
            
            // Next 729 chars: candidates/pencil marks (9 chars per cell, 1 if present, 0 if absent)
            for (i in 0 until 81) {
                val cell = grid.getCell(i)
                for (n in 1..9) {
                    // Only save candidates for unsolved cells
                    sb.append(if (n in cell.candidates && !cell.isSolved) '1' else '0')
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
    val category: DifficultyCategory,
    val difficulty: Float,
    val elapsedTimeMs: Long,
    val mistakeCount: Int,
    val isCompleted: Boolean,
    val progressPercent: Int,    // 0-100
    val lastPlayedTimestamp: Long
)

