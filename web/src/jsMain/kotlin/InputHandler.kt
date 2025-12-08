import domain.*

/**
 * Extension functions for handling user input in SudokuApp.
 */

internal fun SudokuApp.handleNumberClick(num: Int, grid: SudokuGrid) {
    when (playMode) {
        PlayMode.FAST -> {
            // Select number for highlighting (single selection)
            if (num in selectedNumbers1) {
                // Double click clears selection
                selectedNumbers1.clear()
            } else {
                selectedNumbers1.clear()
                selectedNumbers1.add(num)
            }
            
            // If cell is selected, apply the number
            selectedCell?.let { cellIndex ->
                val cell = grid.getCell(cellIndex)
                if (!cell.isGiven && !cell.isSolved) {
                    if (isNotesMode) {
                        val isCandidatePresent = num in cell.displayCandidates
                        val wasMistake = checkCandidateRemovalMistake(cellIndex, num, isCandidatePresent)
                        if (wasMistake) showToast("❌ Wrong candidate removed!")
                        // Only record elimination if candidate was present (we're removing it)
                        if (isCandidatePresent) {
                            gameEngine.recordAction(gameEngine.createEliminationAction(cellIndex, num))
                        }
                        gameEngine.toggleCandidate(cellIndex, num)
                    } else {
                        val wasMistake = checkMistake(cellIndex, num)
                        if (wasMistake) showToast("❌ Wrong number!")
                        gameEngine.recordAction(gameEngine.createPlacementAction(cellIndex, num))
                        gameEngine.setCellValue(cellIndex, num)
                    }
                    saveCurrentState()
                }
            }
        }
        PlayMode.ADVANCED -> {
            // In advanced mode, this is only used for keyboard input
            // The dual number pads handle clicking directly
            // For keyboard: toggle in primary set
            if (num in selectedNumbers1) {
                selectedNumbers1.remove(num)
            } else {
                selectedNumbers1.add(num)
            }
        }
    }
    render()
}

// Toggle number in primary selection (for advanced mode primary number bar)
internal fun SudokuApp.togglePrimaryNumber(num: Int) {
    if (num in selectedNumbers1) {
        selectedNumbers1.remove(num)
    } else {
        selectedNumbers1.add(num)
    }
    render()
}

// Toggle number in secondary selection (for advanced mode secondary number bar)
internal fun SudokuApp.toggleSecondaryNumber(num: Int) {
    if (num in selectedNumbers2) {
        selectedNumbers2.remove(num)
    } else {
        selectedNumbers2.add(num)
    }
    render()
}

internal fun SudokuApp.handleCellClick(cellIndex: Int, grid: SudokuGrid) {
    val cell = grid.getCell(cellIndex)
    
    // In FAST mode with a number selected, apply it to the cell
    val selectedNum = selectedNumbers1.singleOrNull()
    if (playMode == PlayMode.FAST && selectedNum != null && !cell.isGiven) {
        if (isNotesMode) {
            // Toggle pencil mark
            if (!cell.isSolved) {
                val isCandidatePresent = selectedNum in cell.displayCandidates
                val wasMistake = checkCandidateRemovalMistake(cellIndex, selectedNum, isCandidatePresent)
                if (wasMistake) showToast("❌ Wrong candidate removed!")
                // Only record elimination if candidate was present (we're removing it)
                if (isCandidatePresent) {
                    gameEngine.recordAction(gameEngine.createEliminationAction(cellIndex, selectedNum))
                }
                gameEngine.toggleCandidate(cellIndex, selectedNum)
                saveCurrentState()
                selectedCell = null
            }
            selectedCell = null
        } else if (!cell.isSolved) {
            val wasMistake = checkMistake(cellIndex, selectedNum)
            if (wasMistake) showToast("❌ Wrong number!")
            gameEngine.recordAction(gameEngine.createPlacementAction(cellIndex, selectedNum))
            gameEngine.setCellValue(cellIndex, selectedNum)
            saveCurrentState()
            // Auto-deselect cell after setting value in FAST mode
            selectedCell = null
        } else {
            selectedCell = cellIndex
        }
        render()
        return
    }
    
    selectedCell = cellIndex
    render()
}

/**
 * Check if placing a value is a mistake.
 */
internal fun SudokuApp.checkMistake(cellIndex: Int, value: Int): Boolean {
    // Don't check if mistake detection is off
    if (mistakeDetectionMode == MistakeDetectionMode.OFF) return false

    val isMistake = GameStateManager.isMistake(solution, cellIndex, value)
    if (isMistake) {
        currentGame = currentGame?.copy(mistakeCount = (currentGame?.mistakeCount ?: 0) + 1)
        currentGame?.let { GameStateManager.saveGame(it) }
    }
    return isMistake
}

/**
 * Check if removing a candidate would be a mistake (removing the correct answer).
 * Only counts as mistake if:
 * - Mistake detection is CANDIDATE mode
 * - The candidate being removed equals the solution for that cell
 * - The candidate is currently present (being removed, not added)
 */
internal fun SudokuApp.checkCandidateRemovalMistake(cellIndex: Int, candidate: Int, isCurrentlyPresent: Boolean): Boolean {
    // Only check in CANDIDATE mode
    if (mistakeDetectionMode != MistakeDetectionMode.CANDIDATE) return false

    // Only check if we're removing (candidate is currently present)
    if (!isCurrentlyPresent) return false

    // Check if this candidate is the correct answer
    if (solution == null || cellIndex < 0 || cellIndex >= 81) return false
    val correctValue = solution!![cellIndex].digitToIntOrNull() ?: return false

    if (candidate == correctValue) {
        currentGame = currentGame?.copy(mistakeCount = (currentGame?.mistakeCount ?: 0) + 1)
        currentGame?.let { GameStateManager.saveGame(it) }
        return true
    }
    return false
}

