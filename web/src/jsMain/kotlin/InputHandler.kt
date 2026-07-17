import domain.*
import i18n.LanguageConfig

/**
 * Extension functions for handling user input in SudokuApp.
 */

internal fun SudokuApp.handleNumberClick(num: Int, grid: SudokuGrid) {
    when (playMode) {
        PlayMode.PLACE -> {
            // Single selection - clear and select the new number
            if (num in selectedNumbers1) {
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
                        if (wasMistake) showToast(LanguageConfig.getString("ui.toasts.wrongCandidate"))
                        if (isCandidatePresent) {
                            gameEngine.recordAction(gameEngine.createEliminationAction(cellIndex, num))
                        }
                        gameEngine.toggleCandidate(cellIndex, num)
                    } else {
                        val wasMistake = checkMistake(cellIndex, num)
                        if (wasMistake) showToast(LanguageConfig.getString("ui.toasts.wrongNumber"))
                        gameEngine.recordAction(gameEngine.createPlacementAction(cellIndex, num))
                        gameEngine.setCellValue(cellIndex, num)
                    }
                    saveCurrentState()
                }
            }
        }
        PlayMode.MULTI_SELECT_NOTES -> {
            // Multi-selection - toggle numbers
            if (num in selectedNumbers1) {
                selectedNumbers1.remove(num)
            } else {
                selectedNumbers1.add(num)
            }
        }
    }
    render()
}

/**
 * Clear the selected cell: erase a placed value, or all pencil marks if empty.
 * Undoable. Given cells cannot be erased.
 */
internal fun SudokuApp.handleErase(grid: SudokuGrid) {
    val cellIndex = selectedCell ?: return
    val cell = grid.getCell(cellIndex)
    if (cell.isGiven) return

    if (cell.isSolved && cell.value != null) {
        val previous = cell.value!!
        gameEngine.recordAction(gameEngine.createClearAction(cellIndex, previous))
        gameEngine.setCellValue(cellIndex, null)
        saveCurrentState()
        render()
        return
    }

    if (!cell.isSolved && cell.displayCandidates.isNotEmpty()) {
        val toRemove = cell.displayCandidates.toList()
        toRemove.forEach { num ->
            gameEngine.recordAction(gameEngine.createEliminationAction(cellIndex, num))
            gameEngine.toggleCandidate(cellIndex, num)
        }
        saveCurrentState()
        render()
    }
}

internal fun SudokuApp.handleCellClick(cellIndex: Int, grid: SudokuGrid) {
    val cell = grid.getCell(cellIndex)

    if (playMode == PlayMode.PLACE) {
        val selectedNum = selectedNumbers1.singleOrNull()
        if (selectedNum != null && !cell.isGiven) {
            if (isNotesMode) {
                if (!cell.isSolved) {
                    val isCandidatePresent = selectedNum in cell.displayCandidates
                    val wasMistake = checkCandidateRemovalMistake(cellIndex, selectedNum, isCandidatePresent)
                    if (wasMistake) showToast(LanguageConfig.getString("ui.toasts.wrongCandidate"))
                    if (isCandidatePresent) {
                        gameEngine.recordAction(gameEngine.createEliminationAction(cellIndex, selectedNum))
                    }
                    gameEngine.toggleCandidate(cellIndex, selectedNum)
                    saveCurrentState()
                }
                selectedCell = null
            } else if (!cell.isSolved) {
                val wasMistake = checkMistake(cellIndex, selectedNum)
                if (wasMistake) showToast(LanguageConfig.getString("ui.toasts.wrongNumber"))
                gameEngine.recordAction(gameEngine.createPlacementAction(cellIndex, selectedNum))
                gameEngine.setCellValue(cellIndex, selectedNum)
                saveCurrentState()
                selectedCell = null
            } else {
                selectedCell = cellIndex
            }
            render()
            return
        }

        selectedCell = cellIndex
        render()
        return
    }

    if (playMode == PlayMode.MULTI_SELECT_NOTES) {
        if (!cell.isGiven && !cell.isSolved && selectedNumbers1.isNotEmpty()) {
            when (multiSelectAction) {
                MultiSelectAction.CLEAR_SELECTED -> {
                    val toRemove = selectedNumbers1.filter { it in cell.displayCandidates }
                    var hadMistake = false
                    toRemove.forEach { num ->
                        if (checkCandidateRemovalMistake(cellIndex, num, true)) hadMistake = true
                        gameEngine.recordAction(gameEngine.createEliminationAction(cellIndex, num))
                        gameEngine.toggleCandidate(cellIndex, num)
                    }
                    if (hadMistake) showToast(LanguageConfig.getString("ui.toasts.wrongCandidate"))
                    if (toRemove.isNotEmpty()) saveCurrentState()
                }
                MultiSelectAction.CLEAR_OTHER -> {
                    val toRemove = cell.displayCandidates.filter { it !in selectedNumbers1 }
                    var hadMistake = false
                    toRemove.forEach { num ->
                        if (checkCandidateRemovalMistake(cellIndex, num, true)) hadMistake = true
                        gameEngine.recordAction(gameEngine.createEliminationAction(cellIndex, num))
                        gameEngine.toggleCandidate(cellIndex, num)
                    }
                    if (hadMistake) showToast(LanguageConfig.getString("ui.toasts.wrongCandidate"))
                    if (toRemove.isNotEmpty()) saveCurrentState()
                }
            }
        }
        render()
        return
    }
}

/**
 * Check if placing a value is a mistake.
 */
internal fun SudokuApp.checkMistake(cellIndex: Int, value: Int): Boolean {
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
 */
internal fun SudokuApp.checkCandidateRemovalMistake(cellIndex: Int, candidate: Int, isCurrentlyPresent: Boolean): Boolean {
    if (candidateMode == CandidateMode.MANUAL) return false
    if (mistakeDetectionMode != MistakeDetectionMode.CANDIDATE) return false
    if (!isCurrentlyPresent) return false
    if (solution == null || cellIndex < 0 || cellIndex >= 81) return false
    val correctValue = solution!![cellIndex].digitToIntOrNull() ?: return false

    if (candidate == correctValue) {
        currentGame = currentGame?.copy(mistakeCount = (currentGame?.mistakeCount ?: 0) + 1)
        currentGame?.let { GameStateManager.saveGame(it) }
        return true
    }
    return false
}
