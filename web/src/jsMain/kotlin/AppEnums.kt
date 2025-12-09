enum class AppScreen {
    GAME,
    PUZZLE_BROWSER,
    IMPORT_EXPORT,
    SETTINGS
}

enum class HighlightMode {
    PLACED,      // Cells with matching placed numbers
    RCB_SELECTED, // Rows, Columns, Boxes of selected cell
    RCB_ALL,     // Rows, Columns, Boxes of all matching numbers
    PENCIL       // Matching pencil marks
}

enum class PlayMode {
    FAST,        // Click number then cell to fill
    CELL_FIRST,  // Click cell then number to fill
    ADVANCED     // Click number to highlight, then choose action
}

enum class MistakeDetectionMode {
    OFF,         // No mistake detection
    PLACEMENT,   // Only detect wrong number placements
    CANDIDATE    // Detect wrong placements AND wrong candidate removals
}

enum class CandidateMode {
    AUTO,        // Auto-calculate candidates, enable hints and error detection
    MANUAL       // Start with blank pencil marks, disable hints and error detection
}
