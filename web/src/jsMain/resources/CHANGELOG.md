# v0.2.2 - 2025-12-03c

### Features
- Hint System
- - Universal Interactive Descriptions
- - - Should work for **all** techniques, even before they are fully implemented
- - Naked Single, Double, Triple, Quadruple
- - - Includeing Pointing Eliminations
- - Hidden Single, Double, Triple, Quadruple
- - - Including Pointing Eliminations
- - Pointing Candidates

# v0.2.1 - 2025-12-03b

### Minor Changes
- Bump versions
- Not moving ahead with Android for now, but got first build (unusable) working.

# v0.2.0 - 2025-12-03

### Features
- Hint system is now under development
- - UI now handles hint space requried properly
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


# v0.1.2 - 2025-12-02c

### Fixed Issues
- Returned hints are now valid
- - Takes into account the eliminated candidates

### Known Issues
- Hints are not really usable.
- - Highlighting does not show properly.
- - Highlighting modes other than Pencil are erroneous.
- - Returned hints are not logically chosen. 

# v0.1.1 - 2025-12-02b

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
- - Clicking badge in top right will switch beteen Fast and Advanced mode
- - All available (and only available) actions in a cell are available when selected.  This is based on the highlighting used.
- More Highlighting Types
- - If every candidate is satisfied, the cell will be hashed
- - Candidates highlighted in the colour used to highlight it

### Fixed Issues
- Switching between Fast and Advanced mode no longer causes stuch highlights.

### Known Issues
- Hints are not really usable.
- - Even if notes are eliminated, hints using them can be shown
- - Highlighting does not show properly.
- - Highlighting modes other than Pencil are erroneous.
- - Returned hints are not logically chosen. 
- UI occasionally exceeds viewport boundaries.
- - This is getting worse, now that Advanced needs more vertical space.

# v0.0.3 - 2025-12-01b

### Features
- Themes
- - Blue, similar to dark origional
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
- When finishing a game, clickend new game will start a game that has not been started.
- - The bug was, that if the next game had been started, it would restarted losing progress.
- Notes are occasionally lost.
- Clour choices in some places are bad
- - Red buttons in setings need to me chaeged to sometehing better.
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
- Clour choices in some places are bad
- - Red buttons in setings need to me chaeged to sometehing better.
- - Black text in level selection page needs to be something better.
- Menu Back Button should be left top, not center justified.
- In Menu, Two-Number Highlighting should only display when Advanced is selected.