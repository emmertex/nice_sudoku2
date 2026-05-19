import domain.SudokuGrid

/**
 * Keyboard handling functions extracted from SudokuApp
 */

class KeyboardHandler {
    companion object {
        fun handleKeyPress(
            app: SudokuApp,
            key: String,
            ctrlKey: Boolean,
            shiftKey: Boolean,
            altKey: Boolean,
            metaKey: Boolean,
            grid: SudokuGrid,
            event: dynamic
        ): Boolean {
            // Handle Escape - always works regardless of screen
            if (key.lowercase() == "escape") {
                if (app.showExplanation) {
                    app.showExplanation = false
                    app.explanationStepIndex = 0
                    app.render()
                    return true
                }
                if (app.showAboutModal) {
                    app.showAboutModal = false
                    app.render()
                    return true
                }
                if (app.showHelpModal) {
                    app.showHelpModal = false
                    app.render()
                    return true
                }
                if (app.showVersionModal) {
                    app.showVersionModal = false
                    app.render()
                    return true
                }
                if (app.showHints) {
                    app.showHints = false
                    app.render()
                    return true
                }
                if (app.currentScreen != AppScreen.GAME) {
                    app.currentScreen = AppScreen.GAME
                    app.render()
                    return true
                }
                // Clear selections in game screen
                app.selectedNumbers1.clear()
                app.selectedCell = null
                app.render()
                return true
            }

            // Handle screen-specific shortcuts
            when (app.currentScreen) {
                AppScreen.GAME -> {
                    return handleGameScreenKeys(app, key, ctrlKey, shiftKey, altKey, metaKey, grid, event)
                }
                AppScreen.PUZZLE_BROWSER -> {
                    return handlePuzzleBrowserKeys(app, key, ctrlKey, shiftKey, altKey, metaKey, grid, event)
                }
                AppScreen.IMPORT_EXPORT -> {
                    return handleImportExportKeys(app, key, ctrlKey, shiftKey, altKey, metaKey, grid, event)
                }
                AppScreen.SETTINGS -> {
                    return handleSettingsKeys(app, key, ctrlKey, shiftKey, altKey, metaKey, grid, event)
                }
            }
            return false
        }

        private fun handleGameScreenKeys(
            app: SudokuApp,
            key: String,
            ctrlKey: Boolean,
            shiftKey: Boolean,
            altKey: Boolean,
            metaKey: Boolean,
            grid: SudokuGrid,
            event: dynamic
        ): Boolean {
            // Hint navigation takes priority when hints are shown
            if (app.showHints && (key == "ArrowUp" || key == "ArrowDown" || key == "PageUp" || key == "PageDown")) {
                when (key) {
                    "ArrowUp" -> {
                        if (app.selectedHintIndex > 0) {
                            app.selectedHintIndex--
                            app.explanationStepIndex = 0
                            app.render()
                            return true
                        }
                    }
                    "ArrowDown" -> {
                        val hints = app.gameEngine.getHints()
                        if (app.selectedHintIndex < hints.size - 1) {
                            app.selectedHintIndex++
                            app.explanationStepIndex = 0
                            app.render()
                            return true
                        }
                    }
                    "PageUp" -> {
                        app.selectedHintIndex = 0
                        app.explanationStepIndex = 0
                        app.render()
                        return true
                    }
                    "PageDown" -> {
                        val hints = app.gameEngine.getHints()
                        app.selectedHintIndex = hints.size - 1
                        app.explanationStepIndex = 0
                        app.render()
                        return true
                    }
                }
            }

            // Arrow key navigation for cells
            when (key) {
                "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight" -> {
                    return handleArrowNavigation(app, key, ctrlKey, shiftKey, grid)
                }
                "Home" -> {
                    app.selectedCell?.let { cellIndex ->
                        val row = cellIndex / 9
                        app.selectedCell = row * 9  // Move to first column of current row
                        app.render()
                        return true
                    }
                }
                "End" -> {
                    app.selectedCell?.let { cellIndex ->
                        val row = cellIndex / 9
                        app.selectedCell = row * 9 + 8  // Move to last column of current row
                        app.render()
                        return true
                    }
                }
            }

            // Ctrl+Home/End for row navigation
            if (ctrlKey && (key == "Home" || key == "End")) {
                if (key == "Home") {
                    app.selectedCell = 0  // Top-left
                    app.render()
                    return true
                } else {
                    app.selectedCell = 80  // Bottom-right
                    app.render()
                    return true
                }
            }

            // Handle F1-F9 for filter digits (similar to HoDoKu)
            if (key.startsWith("F") && key.length == 2) {
                val fNum = key.substring(1).toIntOrNull()
                if (fNum != null && fNum in 1..9) {
                    if (fNum in app.selectedNumbers1) {
                        app.selectedNumbers1.remove(fNum)
                    } else {
                        if (app.playMode == PlayMode.PLACE) app.selectedNumbers1.clear()
                        app.selectedNumbers1.add(fNum)
                    }
                    app.render()
                    return true
                }
            }

            // Handle number keys 1-9
            val num = key.toIntOrNull()
            if (num != null && num in 1..9) {
                if (ctrlKey) {
                    // Ctrl+number: Toggle candidate (pencil mark)
                    app.selectedCell?.let { cellIndex ->
                        val cell = grid.getCell(cellIndex)
                        if (!cell.isGiven && !cell.isSolved) {
                            val isCandidatePresent = num in cell.displayCandidates
                            val wasMistake = app.checkCandidateRemovalMistake(cellIndex, num, isCandidatePresent)
                            if (wasMistake) app.showToast("❌ Wrong candidate removed!")
                            // Only record elimination if candidate was present (we're removing it)
                            if (isCandidatePresent) {
                                app.gameEngine.recordAction(app.gameEngine.createEliminationAction(cellIndex, num))
                            }
                            app.gameEngine.toggleCandidate(cellIndex, num)
                            app.saveCurrentState()
                            app.render()
                            return true
                        }
                    }
                } else {
                    // Regular number: Handle based on play mode
                    app.handleNumberClick(num, grid)
                    return true
                }
            }

            // Handle other keys
            when (key.lowercase()) {
                "n" -> {
                    if (!ctrlKey && !shiftKey && !altKey && !metaKey) {
                        if (app.isNotesMode) {
                            app.isNotesMode = false
                        } else {
                            app.isNotesMode = true
                            if (app.playMode == PlayMode.MULTI_SELECT_NOTES) {
                                app.playMode = PlayMode.PLACE
                                GameStateManager.setPlayMode(app.playMode)
                                app.selectedNumbers1.clear()
                                app.selectedCell = null
                            }
                        }
                        app.render()
                        return true
                    }
                }
                "h" -> {
                    if (!ctrlKey && !shiftKey && !altKey && !metaKey) {
                        // Toggle hints (only in AUTO mode)
                        if (app.isBackendAvailable && !app.isLoadingHints && app.candidateMode == CandidateMode.AUTO) {
                            app.showHints = !app.showHints
                            if (app.showHints) {
                                app.selectedHintIndex = 0
                                app.gameEngine.findAllTechniques()
                                // Increment hint counter
                                app.saveCurrentState(newHint = true)
                            }
                            app.render()
                            return true
                        }
                    }
                }
                " " -> {
                    // Space: If a filter (selected number) is set, toggle the candidate
                    app.selectedCell?.let { cellIndex ->
                        val num = app.selectedNumbers1.singleOrNull()
                        if (num != null) {
                            val cell = grid.getCell(cellIndex)
                            if (!cell.isGiven && !cell.isSolved) {
                                val isCandidatePresent = num in cell.displayCandidates
                                val wasMistake = app.checkCandidateRemovalMistake(cellIndex, num, isCandidatePresent)
                                if (wasMistake) app.showToast("❌ Wrong candidate removed!")
                                // Only record elimination if candidate was present (we're removing it)
                                if (isCandidatePresent) {
                                    app.gameEngine.recordAction(app.gameEngine.createEliminationAction(cellIndex, num))
                                }
                                app.gameEngine.toggleCandidate(cellIndex, num)
                                app.saveCurrentState()
                                app.render()
                                return true
                            }
                        }
                    }
                }
            }

            // Screen navigation shortcuts
            when (key.lowercase()) {
                "m" -> {
                    if (!ctrlKey && !shiftKey && !altKey && !metaKey) {
                        // M: Open menu/settings
                        app.prepareLeaveGameScreen()
                        app.currentScreen = AppScreen.SETTINGS
                        app.render()
                        return true
                    }
                }
                "b" -> {
                    if (!ctrlKey && !shiftKey && !altKey && !metaKey) {
                        // B: Open puzzle browser
                        app.prepareLeaveGameScreen()
                        app.currentScreen = AppScreen.PUZZLE_BROWSER
                        app.render()
                        return true
                    }
                }
                "i" -> {
                    if (!ctrlKey && !shiftKey && !altKey && !metaKey) {
                        // I: Open import/export
                        app.prepareLeaveGameScreen()
                        app.currentScreen = AppScreen.IMPORT_EXPORT
                        app.render()
                        return true
                    }
                }
            }

            return false
        }

        private fun handleArrowNavigation(app: SudokuApp, key: String, ctrlKey: Boolean, shiftKey: Boolean, grid: SudokuGrid): Boolean {
            val current = app.selectedCell ?: 0
            var row = current / 9
            var col = current % 9

            when (key) {
                "ArrowUp" -> {
                    if (ctrlKey) {
                        // Ctrl+Up: Jump to next unsolved cell above
                        val result = findNextUnsolvedCell(app, row, col, -1, 0, grid)
                        app.selectedCell = result
                        app.render()
                        return true
                    } else {
                        row = (row - 1 + 9) % 9
                    }
                }
                "ArrowDown" -> {
                    if (ctrlKey) {
                        // Ctrl+Down: Jump to next unsolved cell below
                        val result = findNextUnsolvedCell(app, row, col, 1, 0, grid)
                        app.selectedCell = result
                        app.render()
                        return true
                    } else {
                        row = (row + 1) % 9
                    }
                }
                "ArrowLeft" -> {
                    if (ctrlKey) {
                        // Ctrl+Left: Jump to next unsolved cell to the left
                        val result = findNextUnsolvedCell(app, row, col, 0, -1, grid)
                        app.selectedCell = result
                        app.render()
                        return true
                    } else {
                        col = (col - 1 + 9) % 9
                    }
                }
                "ArrowRight" -> {
                    if (ctrlKey) {
                        // Ctrl+Right: Jump to next unsolved cell to the right
                        val result = findNextUnsolvedCell(app, row, col, 0, 1, grid)
                        app.selectedCell = result
                        app.render()
                        return true
                    } else {
                        col = (col + 1) % 9
                    }
                }
            }

            app.selectedCell = row * 9 + col
            app.render()
            return true
        }

        private fun findNextUnsolvedCell(app: SudokuApp, startRow: Int, startCol: Int, rowDelta: Int, colDelta: Int, grid: SudokuGrid): Int {
            var row = startRow
            var col = startCol
            var attempts = 0

            while (attempts < 81) {
                row = (row + rowDelta + 9) % 9
                col = (col + colDelta + 9) % 9
                val cellIndex = row * 9 + col
                val cell = grid.getCell(cellIndex)

                if (!cell.isGiven && !cell.isSolved) {
                    return cellIndex
                }

                attempts++
                // If we've wrapped around to the starting position, stop
                if (row == startRow && col == startCol) {
                    break
                }
            }

            // If no unsolved found, return current position
            return startRow * 9 + startCol
        }

        private fun handlePuzzleBrowserKeys(app: SudokuApp, key: String, ctrlKey: Boolean, shiftKey: Boolean, altKey: Boolean, metaKey: Boolean, grid: SudokuGrid, event: dynamic): Boolean {
            // Arrow keys for navigation in puzzle list
            when (key) {
                "ArrowUp", "ArrowDown" -> {
                    // Could be used for puzzle list navigation in future
                    return false
                }
            }

            // Escape already handled at top level
            return false
        }

        private fun handleImportExportKeys(app: SudokuApp, key: String, ctrlKey: Boolean, shiftKey: Boolean, altKey: Boolean, metaKey: Boolean, grid: SudokuGrid, event: dynamic): Boolean {
            // Escape already handled at top level
            return false
        }

        private fun handleSettingsKeys(app: SudokuApp, key: String, ctrlKey: Boolean, shiftKey: Boolean, altKey: Boolean, metaKey: Boolean, grid: SudokuGrid, event: dynamic): Boolean {
            // Escape already handled at top level
            return false
        }
    }
}
