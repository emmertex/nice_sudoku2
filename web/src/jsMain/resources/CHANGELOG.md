# v0.9.0 - 2026-04-01

### Features
- Viewing, Changing, Importing and Exporting Puzzle Metadata
- Export of more formats.
- - Origional Puzzle
- - - 81 Char String, the standard format shared online in most places
- - - Complete Format.  Compatible with Sudoku Coach, but with additional metadata.
- - - - Givens
- - - - Pencil Marks
- - - - Origional Puzzle
- - - - Puzzle Title
- - - - Puzzle Author
- - - - Puzzle Author Contact Info
- - - - Puzzle Description
- - Current State
- - - 729 Char String, Pencil Marks Only. This is commonly used in apps like HoDoKu.
- - - 891 Char String, Current Puzzle State, Pencil Marks, And Origional Puzzle.  Intended to resolve limitations of HoDoKu format.
- - - Complete, Compatible with Sudoku Coach, but with additional metadata.
- - - - Everything mentioned for Origional Puzzle, but additionally.
- - - - Play Time
- - - - Mistake Count
- - - - Hints Used Count 
- Puzzle Max Size increased 1.5x
- Display Puzzle Title on main screen.
- Title to show Puzzle Info.
- New Setting.  Track Play Time.
- - When enabled, any movement from the play screen will pause the timer, and block the play screen.
- - When this is disabled, the "Pause" screen will not show up, and play time will always stay as 0.
- - Puzzle Time will not start counting unless a new puzzle ist started AND this setting is enabled

# v0.8.3 - 2026-04-01

### Fixes
- Resolved issue of pencil marks appearing outside the cells boundaries.

# v0.8.2 - 2025-12-30

### Features
- Help Button
- - Because of buttons without text, this opens a modal that explains all icons
- - Did not use tooltips, as using with touchscreen on notebooks was causing stuck tooltips
- - A link to the Main Help modal is also in the Help Button modal, explaining how the game works, keybaord shortcuts, and alike

### Fixes
- CORS


# v0.8.1 - 2025-12-18

### Features
- Language now supports Hints! 
- - Kind of - It is still WIP, but but the language files are in place, and most parsers.
- - Main pupose is to be able to start working on language files.


# v0.8.0 - 2025-12-17

### Features
- Improvements to hints
- - Almost all that are unfinished, should now be useful
- More code cleanup
- - Hopefully no more crashing backend

### Todo
- Continue working on hints
- Language Support for Hints
- Move solver Offline
- Scale better for weird aspect ratios
- - Galaxy Flip when closed, too small
- - Galazy Fold and Pixel Fold when open, too small

# v0.7.1 - 2025-12-12

### Features
- Support Multiple Languages ( Machine Translated )
- - ar, bn, de, en, es, fr, hi, pt, ru, ur, zh

### Bugfix
- International English and Simplified English mismatch causing issues.
- - All codebase refactored to use International English, for consistency.

# v0.6.1 - 2025-12-10

### Features
- Backend caches responses and processing, for much faster response times, and reduce server load and memory usage.
- ePaper Theme Tweaks - still needs much more work
- Code Cleanup


# v0.6.0 - 2025-12-10

### Anouncement
This release is special, because...
- Feature Parity (of features I want to keep), and in some areas, in front of my previous Sudoku Game written in Godot
- - Except solving algorithms, as they are external for now
- All puzzles playable, with no known issues of actual gameplay

Roadmap for now includes these milestones, intended to be, but not necessarily this order
- Increase Test Coverage
- Fix Theming and UI Issues
- Hint Caching
- Implement detailed explanations for all hint types
- Android App
- Native Local Solvers (100% Offline)

### Features
- **Support for Sudoku Coach Format**
- - Thank you Jan!  https://sudoku.coach
- - Importing of SCv7_32 format
- - Exporting of SCv7_32 format
- **Sudoku Coach Optional Extended Meta Data**: Ignored by sudoku.coach, but shareable between Nice Sudoku
- - Puzzle: Title
- - Puzzle: Author
- - Puzzle: Author Contact
- - Puzzle: Description
- - Player: Play Time
- - Player: Mistakes Made
- - Player: Hints Used
- **New Game Mode**
- - **Cell First**: Traditional cell first experience.
- - **Fast Mode**: as previous, number first, click cell to place
- - **Advanced Mode**: as previous, Select multiple numbers first.
- **Hint Counter**
  - **Hint Counter**: Added a counter to track the number of hints used in the current puzzle
- **New Candidate Mode setting**:
- - **Auto Mode**: (default): Auto-calculate pencil marks, hints and error detection enabled
- - **Manual Mode**: Start with blank pencil marks, fill them yourself, hints and pencil mark error detection disabled
- - Switching modes resets current puzzle's pencil marks with confirmation dialogue
- **Highlight Modes** are now fully implemented.
- - **Pencil Mode**: Highlighting of cells with that numbers pencil mark present (Fast and Advanced Mode Only)
- - **Placed**: Highlighting of cells with that number, placed and given.
- - **RCB Selected**: Highlighting of the Row, Column, and Box of the selected cell. (Cell First Mode Only)
- - **RCB All**: Highlighting of every Row, Column and Box of every Cell with that number, placed and given.
- **Better Handling of Imported Puzzles**
- - Puzzles are graded on import, showing a difficulty
- - If Puzzles have attribution or other Metadata, it is retained and kept
- - Sharing of play game state, including puzzle state, pencil marks, and timer.
- - Custom Puzzles can now be deleted.

### Bug Fixes
- Loading a puzzle via URI will now clear the URL as to avoid unintentional re-importing of the same puzzle
- Difficulties show correctly in the Resume Game Browser
- Resume Game Browser now scrollable
- Removed the Welcome Message



# v0.5.2 - 2025-12-09

### Fixed Issues
- Loss of connection to the backend can now recover without reload of the page.
- Hint button how changes to indicate that it is waiting on the backend to solve.
- - It used to show nothing, and appear as a silent fail.
- Added more missing descriptions for techneques.
- - Missing description was causing valid solves to be incorrectly filtered.


# v0.5.1 - 2025-12-09

### Fixed Issues
- Fixed issue where imported puzzle had mismatched frontend and backend.

# v0.5.0 - 2025-12-09

### Features
- Support Sudoku Coach Format import/export
- - Givens, Puzzle State and Candidates
- - Colours are not supported, and are stripped if imported
- Timer Pausing, with Blur over Game Screen
- - Manual Pause next to timer
- - Pause on loss of window focus

# v0.4.4 - 2025-12-08

### Features
- Increased Candidate Font Size
- Revised themes
- - Prioritised readability over appearance
- - Dark Themes are Okay
- - Light Theme is still bad
- - ePaper Theme is now usable on ePaper, but bad.
- - If anyone is good at theming, comtact me!


# v0.4.3 - 2025-12-08

### Complete Code Cleanup and Organisation
- Frontend: All done!

# v0.4.2 - 2025-12-08

### Features
- Share games, both original and game state, with URL!
- Return the 81, 810 and 891 format game states for import and export
- Complete code reorganization


# v0.4.1 - 2025-12-08

### Complete Code Cleanup and Organisation
- Backend: Finished for now
- Frontned: Finished for now, but needs a LOT more!


# v0.4.0 - 2025-12-08

### Complete Code Cleanup and Organisation
- Backend: Mostly Done
- Frontned: Started


# v0.3.5 - 2025-12-07

### Fixed Issues
- Fixed edge case where 2-string kite would display a wrong chain link


# v0.3.4 - 2025-12-07

### Fixed Issues
- Hint Highlighting only worked in horizontal, now both.


# v0.3.3 - 2025-12-06

### Fixed Issues
- Hint Lines now under numbers and grid -- not great, but something

### Hint System
- Added X-Wing Explanation and Highlighting
- Added Two-String Kite Explanation and Highlighting


# v0.3.2 - 2025-12-06

### Fixed Issues
- Restored missing Next/Prev button on vertical hint view.
- Error on changelog loading on startup will no longer show
- Hint Priority sorting by backend, as during gameplay, simpler hints were not being shown.

# v0.3.1 - 2025-12-06

### Features
- Mistake Detection Settings
- - Off: Mistakes are not detected, and not counted
- - Placement: Mistakes are detected and counted when placing an invalid number
- - Candidate: Mistakes are detected and counted when removing a valid candidate
- Action/Undo Stack
- - Every action taken is stored
- - Undo walks back the action, not affected by natural candidates
- - Saved with the puzzle even after completion.


# v0.3.0 - 2025-12-06

### Features
- Hint System
- - Reordered technique difficulty
- - New user experience to minimise clicks/taps and scrolling
- Training Puzzles
- - Initial support for training Puzzles (not included)
- New puzzles, and puzzle format
- - Every puzzzle ranked
- - Puzzle generator based on qqwing
- - All source for generating and ranking puzzles included
- - New puzzle format, to include solution, elminating need to solve puzzle on load to detect mistakes
- - New difficulty rankings, to make it more logical and intuitive
- - Quality ratings, based on distribution and range of techniques needed.
- - Puzzle format can be expanded on as needed without breaking saves in the future.
- - **Lots** of difficulties, with information to see what techniques you need to know.

### Fixed Issues
- Fixed issue where Hint would remain on the wrong stage

### Known Issues
- Hint panel remains open, but does not load hints when loading a new puzzle
- Mistakes on Pencil/Candidate removal not detected

### Hint System Status
- Complete
- - Basic Techniques
- Features Implemented
- - Cell and Candidate Highlighting
- - Universal Interactive Descriptions
- - Vector Lines for Chains


# v0.2.4 - 2025-12-04

### Fixed Issues
- Rebuilt StormDoku to return all techniques.
- - Include StormDoku.jar in repo


# v0.2.3 - 2025-12-04

### Fixed Issues
- The Hint System for vertical layout now displays properly.
- UI Layout Improvements, much more vertically compact.


# v0.2.2 - 2025-12-03

### Features
- Hint System
- - Universal Interactive Descriptions
- - - Should work for **all** techniques, even before they are fully implemented
- - Naked Single, Double, Triple, Quadruple
- - - Including Pointing Eliminations
- - Hidden Single, Double, Triple, Quadruple
- - - Including Pointing Eliminations
- - Pointing Candidates


# v0.2.1 - 2025-12-03

### Minor Changes
- Bump versions
- Not moving ahead with Android for now, but got first build (unusable) working.


# v0.2.0 - 2025-12-03

### Features
- Hint system is now under development
- - UI now handles hint space required properly
- - - Landscape hint sidebar (right of game area)
- - - Portrait hint sidebar (bottom of game area)
- - Hints are validated before being shown
- - Highlighting of key cells and candidates started
- - Hints have "Explain button"
- - - Hints can have multiple steps
- - SVG Lines for ALS

### Known Issues
- Highlighting modes other than Pencil are erroneous.
- Returned hints are not logically chosen. 


# v0.1.2 - 2025-12-02

### Fixed Issues
- Returned hints are now valid
- - Takes into account the eliminated candidates

### Known Issues
- Hints are not really usable.
- - Highlighting does not show properly.
- - Highlighting modes other than Pencil are erroneous.
- - Returned hints are not logically chosen. 


# v0.1.1 - 2025-12-02

### Fixed Issues
- UI No longer exceeds viewport boundaries.

### Known Issues
- Hints are not really usable.
- - Highlighting does not show properly.
- - Highlighting modes other than Pencil are erroneous.
- - Returned hints are not logically chosen. 


# v0.1.0 - 2025-12-02

### Features
- Complete revamp on Advanced mode
- - Select any number of candidates per colour
- - Dedicated number bar per colour
- - Clicking badge in top right will switch between Fast and Advanced mode
- - All available (and only available) actions in a cell are available when selected.  This is based on the highlighting used.
- More Highlighting Types
- - If every candidate is satisfied, the cell will be hashed
- - Candidates highlighted in the colour used to highlight it

### Fixed Issues
- Switching between Fast and Advanced mode no longer causes stuck highlights.

### Known Issues
- Hints are not really usable.
- - Even if notes are eliminated, hints using them can be shown
- - Highlighting does not show properly.
- - Highlighting modes other than Pencil are erroneous.
- - Returned hints are not logically chosen. 
- UI occasionally exceeds viewport boundaries.
- - This is getting worse, now that Advanced needs more vertical space.


# v0.0.3 - 2025-12-01

### Features
- Themes
- - Blue, similar to dark original
- - Dark
- - Light, incomplete
- - EPaper, for e-ink devices, work in progress

### Fixed Issues

### Known Issues
- Hints are not really usable.
- - Even if notes are eliminated, hints using them can be shown
- - Highlighting does not show properly.
- - Highlighting modes other than Pencil are erroneous.
- - Returned hints are not logically chosen. 
- UI occasionally exceeds viewport boundaries.
- Leaving Advanced mode, if red is selected, it gets stuck on.
- In Menu, Two-Number Highlighting should only display when Advanced is selected.


# v0.0.2 - 2025-12-01

### Features  
- Changed the way notes are stored, to eliminate the bug where eliminations were being lost.  
- - **Breaking** Due to this new method of storing notes, existing games will not show any notes.  
- Added version information to the app. 

### Fixed Issues
- When finishing a game, clicked new game will start a game that has not been started.
- - The bug was, that if the next game had been started, it would restarted losing progress.
- Notes are occasionally lost.
- Colour choices in some places are bad
- - Red buttons in settings need to me changed to something better.
- - Black text in level selection page needs to be something better.
- Menu Back Button should be left top, not center justified.

### Known Issues
- Hints are not really usable.
- - Even if notes are eliminated, hints using them can be shown
- - Highlighting does not show properly.
- - Highlighting modes other than Pencil are erroneous.
- - Returned hints are not logically chosen. 
- UI occasionally exceeds viewport boundaries.
- Leaving Advanced mode, if red is selected, it gets stuck on.
- In Menu, Two-Number Highlighting should only display when Advanced is selected.


# v0.0.1 - 2025-11-30

### Features  
- Initial Preview Release  

### Known Issues
- Notes are occasionally lost.
- Hints are not really usable.
- - Even if notes are eliminated, hints using them can be shown
- - Highlighting does not show properly.
- - Highlighting modes other than Pencil are erroneous.
- - Returned hints are not logically chosen. 
- UI occasionally exceeds viewport boundaries.
- Leaving Advanced mode, if red is selected, it gets stuck on.
- Colour choices in some places are bad
- - Red buttons in settings need to me changed to something better.
- - Black text in level selection page needs to be something better.
- Menu Back Button should be left top, not center justified.
- In Menu, Two-Number Highlighting should only display when Advanced is selected.