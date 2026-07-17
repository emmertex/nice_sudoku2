# Keyboard Shortcuts

This document describes all keyboard shortcuts available in Nice Sudoku. The shortcuts follow conventions similar to HoDoKu, making the game fully playable from the keyboard.

## Navigation

### Cell Selection
- **Arrow Keys (↑ ← ↓ →)**: Move the cursor between cells
- **Ctrl + Arrow Keys**: Jump to the next unsolved cell in that direction
- **Home**: Move to the first column of the current row
- **End**: Move to the last column of the current row
- **Ctrl + Home**: Move to the top-left cell (cell 0)
- **Ctrl + End**: Move to the bottom-right cell (cell 80)

## Number Entry

### Place Mode
- **1-9**: Select a number on the number bar. If a cell is selected, place that number (or toggle a pencil mark when Notes mode is on)
- Click a cell with a number already selected to place it in Place mode

### Candidate Entry (Pencil Marks)
- **Ctrl + 1-9**: Toggle pencil mark candidate in the selected cell
- **Space**: If a number is selected (filter), toggle its candidate in the selected cell
- **N**: Toggle notes/pencil mode on/off (switches out of Multi-Select Notes if needed)

## Editing

- **Delete** or **Backspace**: Clear the selected cell (placed value or all pencil marks; cannot clear given cells)
- **Ctrl + Z** / **Cmd + Z**: Undo the last action
- **Escape**: Clear all selections (selected numbers and cell)

## Filters and Highlighting

- **F1-F9**: Set/change the filtered (selected) digit for highlighting

## Game Actions

### Hint System
- **H**: Toggle hint panel (show/hide available solving techniques)
  - Requires backend connection and Auto candidate mode
  - When hints are visible:
    - **Arrow Up/Down**: Navigate through available hints
    - **Page Up**: Jump to first hint
    - **Page Down**: Jump to last hint

## Screen Navigation

- **M**: Open Menu/Settings screen
- **B**: Open Puzzle Browser screen
- **I**: Open Import/Export screen
- **Escape**:
  - Close modals (About, Help, etc.)
  - Close hint panel
  - Return to Game screen from any other screen
  - Clear selections in Game screen

## Play Modes

### Place Mode
- Select a number, then click a cell to place it
- With Notes (✏️) on, the same flow toggles pencil marks instead
- Number keys select the digit and apply it when a cell is already selected

### Multi-Select Notes Mode (✏️✏️)
- Select one or more numbers on the number bar
- Choose **Clr Sel** (clear selected candidates) or **Clr Oth** (clear all other candidates)
- Click a cell to apply the chosen clear action

## Notes

1. All shortcuts are disabled when typing in input fields or text areas to prevent conflicts
2. Keyboard shortcuts follow HoDoKu conventions for consistency with standard Sudoku software
3. The game is fully playable using only keyboard input
4. **F1-F9 keys**: Some browsers use F-keys for developer tools (e.g., F12) or other functions. If a browser shortcut conflicts, you may need to disable the browser's shortcut or use number keys 1-9 instead for filtering

## Tips

- Use **Ctrl + Arrow Keys** to quickly jump between unsolved cells
- Use **F1-F9** for quick number filtering and highlighting
- Use **H** to access hints and learn new solving techniques
- Use **Delete** / **Backspace** to erase a misplaced digit
- **Escape** is your universal "go back" key - use it to return to the game from any screen or modal
