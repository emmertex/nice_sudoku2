enum class AppScreen {
    GAME,
    PUZZLE_BROWSER,
    IMPORT_EXPORT,
    SETTINGS
}

enum class HighlightMode {
    CELL,        // Cells with matching numbers
    RCB_SELECTED, // Rows, Columns, Boxes of selected cell
    RCB_ALL,     // Rows, Columns, Boxes of all matching numbers
    PENCIL       // Matching pencil marks
}

enum class PlayMode {
    FAST,        // Click number then cell to fill
    ADVANCED     // Click number to highlight, then choose action
}

enum class MistakeDetectionMode {
    OFF,         // No mistake detection
    PLACEMENT,   // Only detect wrong number placements
    CANDIDATE    // Detect wrong placements AND wrong candidate removals
}
