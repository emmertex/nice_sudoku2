# Keyboard Shortcuts

This document describes all keyboard shortcuts available in Nice Sudoku. The shortcuts follow standard conventions similar to HoDoKu, making the game fully playable from the keyboard.

## Navigation

### Cell Selection
- **Arrow Keys (↑ ← ↓ →)**: Move the cursor between cells
- **Ctrl + Arrow Keys**: Jump to the next unsolved cell in that direction
- **Home**: Move to the first column of the current row
- **End**: Move to the last column of the current row
- **Ctrl + Home**: Move to the top-left cell (cell 0)
- **Ctrl + End**: Move to the bottom-right cell (cell 80)

## Number Entry

### Basic Entry
- **1-9**: Enter numbers based on play mode:
  - **Fast Mode**: Selects the number for highlighting. If a cell is selected, applies the number to that cell
  - **Advanced Mode**: Sets number highlighting (primary/secondary). Pressing the same number again clears the selection

### Candidate Entry (Pencil Marks)
- **Ctrl + 1-9**: Toggle pencil mark candidate in the selected cell
- **Space**: If a number is selected (filter), toggle its candidate in the selected cell
- **N**: Toggle notes/pencil mode on/off

## Editing

- **Delete** or **Backspace**: Clear the value in the selected cell (cannot clear given cells)
- **Escape**: Clear all selections (selected numbers and cell)

## Filters and Highlighting

- **F1-F9**: Set/change the filtered (selected) digit for highlighting
- **Shift + F1-F9**: Set/change the filtered digit (future: toggle filter mode)

## Game Actions

### Hint System
- **H**: Toggle hint panel (show/hide available solving techniques)
  - Requires backend connection to be available
  - When hints are visible:
    - **Arrow Up/Down**: Navigate through available hints
    - **Page Up**: Jump to first hint
    - **Page Down**: Jump to last hint

### Advanced Mode Actions
- **Enter** or **S**: Set the primary selected number in the selected cell (when in Advanced mode)

## Screen Navigation

- **M**: Open Menu/Settings screen
- **B**: Open Puzzle Browser screen
- **I**: Open Import/Export screen
- **Escape**: 
  - Close modals (About, etc.)
  - Close hint panel
  - Return to Game screen from any other screen
  - Clear selections in Game screen

## Mode Switching

- **N**: Toggle Notes/Pencil mode
  - When enabled, number entry adds/removes pencil marks instead of values

## Notes

1. All shortcuts are disabled when typing in input fields or text areas to prevent conflicts
2. Keyboard shortcuts follow HoDoKu conventions for consistency with standard Sudoku software
3. The game is fully playable using only keyboard input
4. Some shortcuts may vary slightly in behavior between Fast and Advanced play modes
5. **F1-F9 keys**: Some browsers use F-keys for developer tools (e.g., F12) or other functions. If a browser shortcut conflicts, you may need to disable the browser's shortcut or use number keys 1-9 instead for filtering

## Play Mode Differences

### Fast Mode
- Number keys immediately apply to selected cells when appropriate
- Quick, streamlined input for faster solving

### Advanced Mode
- Number keys primarily control highlighting
- Use action buttons or Enter/S to set values
- Supports two-number highlighting for complex solving techniques

## Tips

- Use **Ctrl + Arrow Keys** to quickly jump between unsolved cells
- Use **F1-F9** for quick number filtering and highlighting
- Use **H** to access hints and learn new solving techniques
- **Escape** is your universal "go back" key - use it to return to the game from any screen or modal

