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
    PLACE,              // Click number then cell to fill (or toggle pencil mark in notes mode)
    MULTI_SELECT_NOTES  // Select multiple numbers, apply clear action to cells
}

enum class MultiSelectAction {
    CLEAR_SELECTED,  // Clear pencil marks of selected numbers from clicked cell
    CLEAR_OTHER      // Clear pencil marks NOT in selected numbers from clicked cell
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

enum class HintPlacement {
    AUTO,     // Decide from available space (sidebar when there's room, card below otherwise)
    SIDEBAR,  // Always place hints in a right-hand sidebar
    BELOW     // Always place hints in a card below the board
}
