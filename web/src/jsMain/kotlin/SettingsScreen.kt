import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import domain.*
import view.Theme
import view.applyTheme

/**
 * Extension function for rendering the Settings screen in SudokuApp.
 */
internal fun SudokuApp.renderSettings() {
    appRoot.append {
        div("sudoku-container settings") {
            div("header") {
                button(classes = "back-btn") {
                    +"← Back"
                    onClickFunction = {
                        currentScreen = AppScreen.GAME
                        render()
                    }
                }
                h1 { +"Settings" }
            }
            
            // Navigation section
            div("section") {
                h2 { +"📁 Navigation" }
                div("nav-grid") {
                    button(classes = "settings-nav-btn") {
                        +"📚 Puzzles"
                        onClickFunction = {
                            currentScreen = AppScreen.PUZZLE_BROWSER
                            render()
                        }
                    }
                    button(classes = "settings-nav-btn") {
                        +"📋 Import/Export"
                        onClickFunction = {
                            currentScreen = AppScreen.IMPORT_EXPORT
                            render()
                        }
                    }
                    button(classes = "settings-nav-btn") {
                        +"ℹ️ About"
                        onClickFunction = {
                            showAboutModal = true
                            render()
                        }
                    }
                    button(classes = "settings-nav-btn") {
                        +"❓ Help"
                        onClickFunction = {
                            showHelpModal = true
                            render()
                        }
                    }
                }
            }
            
            // Highlight Mode section
            div("section") {
                h2 { +"🎨 Highlight Mode" }
                p("setting-desc") { +"Choose how numbers are highlighted when selected" }
                
                div("mode-options") {
                    // Filter highlight modes based on play mode
                    val availableModes = when (playMode) {
                        PlayMode.FAST, PlayMode.ADVANCED -> listOf(
                            HighlightMode.PENCIL,
                            HighlightMode.RCB_ALL,
                            HighlightMode.PLACED
                        )
                        PlayMode.CELL_FIRST -> listOf(
                            HighlightMode.PLACED,
                            HighlightMode.RCB_SELECTED,
                            HighlightMode.RCB_ALL
                        )
                    }
                    
                    for (mode in availableModes) {
                        val isActive = highlightMode == mode
                        button(classes = "mode-btn ${if (isActive) "active" else ""}") {
                            +when (mode) {
                                HighlightMode.PLACED -> "Placed"
                                HighlightMode.RCB_SELECTED -> "RCB Selected"
                                HighlightMode.RCB_ALL -> "RCB All"
                                HighlightMode.PENCIL -> "Pencil"
                            }
                            onClickFunction = {
                                highlightMode = mode
                                GameStateManager.setHighlightMode(mode)
                                render()
                            }
                        }
                    }
                }
                
                div("mode-explanation") {
                    +when (highlightMode) {
                        HighlightMode.PLACED -> "Highlights cells with matching placed numbers"
                        HighlightMode.RCB_SELECTED -> "Highlights Row, Column, Box of selected cell"
                        HighlightMode.RCB_ALL -> "Highlights all Row/Column/Box containing the number"
                        HighlightMode.PENCIL -> "Highlights cells with matching pencil marks"
                    }
                }
            }
            
            // Theme section
            div("section") {
                h2 { +"🎨 Theme" }
                p("setting-desc") { +"Choose your visual theme" }

                div("mode-options theme-options") {
                    for (theme in Theme.entries) {
                        val isActive = currentTheme == theme
                        button(classes = "mode-btn theme-btn ${theme.name.lowercase()} ${if (isActive) "active" else ""}") {
                            +when (theme) {
                                Theme.DARK -> "🌙 Dark"
                                Theme.BLUE -> "🔵 Blue"
                                Theme.LIGHT -> "☀️ Light"
                                Theme.EPAPER -> "📖 ePaper"
                            }
                            onClickFunction = {
                                currentTheme = theme
                                GameStateManager.setTheme(theme)
                                applyTheme(theme)
                                render()
                            }
                        }
                    }
                }

                div("mode-explanation") {
                    +when (currentTheme) {
                        Theme.DARK -> "Pure dark theme with high contrast"
                        Theme.BLUE -> "Classic blue gradient theme"
                        Theme.LIGHT -> "Clean light theme"
                        Theme.EPAPER -> "High contrast for ePaper Devices"
                    }
                }
            }

            // Play Mode section
            div("section") {
                h2 { +"🎮 Play Mode" }
                p("setting-desc") { +"Choose your input style" }
                
                div("mode-options play-modes") {
                    button(classes = "mode-btn fast ${if (playMode == PlayMode.FAST) "active" else ""}") {
                        +"⚡ Fast"
                        onClickFunction = {
                            playMode = PlayMode.FAST
                            GameStateManager.setPlayMode(PlayMode.FAST)
                            // Clear all state when switching to FAST mode
                            selectedNumbers1.clear()
                            selectedNumbers2.clear()
                            selectedCell = null
                            // If RCB Selected is active, switch to Pencil
                            if (highlightMode == HighlightMode.RCB_SELECTED) {
                                highlightMode = HighlightMode.PENCIL
                                GameStateManager.setHighlightMode(HighlightMode.PENCIL)
                            }
                            render()
                        }
                    }
                    button(classes = "mode-btn cell-first ${if (playMode == PlayMode.CELL_FIRST) "active" else ""}") {
                        +"🎯 Cell First"
                        onClickFunction = {
                            playMode = PlayMode.CELL_FIRST
                            GameStateManager.setPlayMode(PlayMode.CELL_FIRST)
                            // Clear all state when switching to CELL_FIRST mode
                            selectedNumbers1.clear()
                            selectedNumbers2.clear()
                            selectedCell = null
                            // If Pencil mode is selected, switch to RCB All
                            if (highlightMode == HighlightMode.PENCIL) {
                                highlightMode = HighlightMode.RCB_ALL
                                GameStateManager.setHighlightMode(HighlightMode.RCB_ALL)
                            }
                            render()
                        }
                    }
                    button(classes = "mode-btn advanced ${if (playMode == PlayMode.ADVANCED) "active" else ""}") {
                        +"🔧 Advanced"
                        onClickFunction = {
                            playMode = PlayMode.ADVANCED
                            GameStateManager.setPlayMode(PlayMode.ADVANCED)
                            // If RCB Selected is active, switch to Pencil
                            if (highlightMode == HighlightMode.RCB_SELECTED) {
                                highlightMode = HighlightMode.PENCIL
                                GameStateManager.setHighlightMode(HighlightMode.PENCIL)
                            }
                            render()
                        }
                    }
                }
                
                div("mode-explanation") {
                    +when (playMode) {
                        PlayMode.FAST -> "Click number, then click cell to fill. Quick and simple."
                        PlayMode.CELL_FIRST -> "Click cell first, then click number to fill. Highlights based on selected cell."
                        PlayMode.ADVANCED -> "Two number rows for highlighting. Select multiple numbers per colour. Cells with ALL selected numbers highlight. Click cell for action buttons."
                    }
                }
            }
            
            // Candidate Mode section
            div("section") {
                h2 { +"📝 Candidate Mode" }
                p("setting-desc") { +"Choose how pencil marks are managed" }
                
                div("mode-options") {
                    button(classes = "mode-btn ${if (candidateMode == CandidateMode.AUTO) "active" else ""}") {
                        +"Auto"
                        onClickFunction = {
                            if (candidateMode != CandidateMode.AUTO) {
                                // Show confirmation before switching
                                if (kotlinx.browser.window.confirm("Switching to Auto mode will reset all pencil marks in the current puzzle. Continue?")) {
                                    candidateMode = CandidateMode.AUTO
                                    GameStateManager.setCandidateMode(CandidateMode.AUTO)
                                    // Reset current game's pencil marks
                                    currentGame?.let { game ->
                                        val puzzleString = game.puzzleString
                                        val newState = puzzleString + "0".repeat(729)
                                        currentGame = game.copy(currentState = newState)
                                        GameStateManager.saveGame(currentGame!!)
                                        // Reload the puzzle
                                        gameEngine.loadPuzzle(puzzleString)
                                        val (values, userEliminations) = SavedGameState.parseStateString(newState)
                                        for (i in 0 until 81) {
                                            val originalValue = puzzleString[i].digitToIntOrNull() ?: 0
                                            if (values[i] != 0 && values[i] != originalValue) {
                                                gameEngine.setCellValue(i, values[i])
                                            }
                                            // Apply user eliminations (empty set in AUTO mode)
                                            gameEngine.setUserEliminations(i, userEliminations[i])
                                        }
                                    }
                                    render()
                                }
                            }
                        }
                    }
                    button(classes = "mode-btn ${if (candidateMode == CandidateMode.MANUAL) "active" else ""}") {
                        +"Manual"
                        onClickFunction = {
                            if (candidateMode != CandidateMode.MANUAL) {
                                // Show confirmation before switching
                                if (kotlinx.browser.window.confirm("Switching to Manual mode will:\n• Reset all pencil marks\n• Disable hints\n• Disable pencil mark error detection\n\nContinue?")) {
                                    candidateMode = CandidateMode.MANUAL
                                    GameStateManager.setCandidateMode(CandidateMode.MANUAL)
                                    showHints = false  // Disable hints when switching to manual
                                    // Reset current game's pencil marks to all eliminated (blank)
                                    currentGame?.let { game ->
                                        val puzzleString = game.puzzleString
                                        val newState = puzzleString + "1".repeat(729)
                                        currentGame = game.copy(currentState = newState)
                                        GameStateManager.saveGame(currentGame!!)
                                        // Reload the puzzle
                                        gameEngine.loadPuzzle(puzzleString)
                                        val (values, userEliminations) = SavedGameState.parseStateString(newState)
                                        for (i in 0 until 81) {
                                            val originalValue = puzzleString[i].digitToIntOrNull() ?: 0
                                            if (values[i] != 0 && values[i] != originalValue) {
                                                gameEngine.setCellValue(i, values[i])
                                            }
                                            // Apply user eliminations (all candidates eliminated in MANUAL mode)
                                            gameEngine.setUserEliminations(i, userEliminations[i])
                                        }
                                    }
                                    render()
                                }
                            }
                        }
                    }
                }
                
                div("mode-explanation") {
                    +when (candidateMode) {
                        CandidateMode.AUTO -> "Auto-calculate pencil marks based on placed numbers. Hints and error detection enabled."
                        CandidateMode.MANUAL -> "Start with blank pencil marks. Fill them in yourself. Hints and pencil mark error detection disabled."
                    }
                }
            }
            
            // Mistake Detection section
            div("section") {
                h2 { +"⚠️ Mistake Detection" }
                p("setting-desc") { +"Choose when mistakes are detected and counted" }
                
                div("mode-options") {
                    button(classes = "mode-btn ${if (mistakeDetectionMode == MistakeDetectionMode.OFF) "active" else ""}") {
                        +"Off"
                        onClickFunction = {
                            mistakeDetectionMode = MistakeDetectionMode.OFF
                            GameStateManager.setMistakeDetectionMode(MistakeDetectionMode.OFF)
                            render()
                        }
                    }
                    button(classes = "mode-btn ${if (mistakeDetectionMode == MistakeDetectionMode.PLACEMENT) "active" else ""}") {
                        +"Placement"
                        onClickFunction = {
                            mistakeDetectionMode = MistakeDetectionMode.PLACEMENT
                            GameStateManager.setMistakeDetectionMode(MistakeDetectionMode.PLACEMENT)
                            render()
                        }
                    }
                    button(classes = "mode-btn ${if (mistakeDetectionMode == MistakeDetectionMode.CANDIDATE) "active" else ""}") {
                        +"Candidate"
                        onClickFunction = {
                            mistakeDetectionMode = MistakeDetectionMode.CANDIDATE
                            GameStateManager.setMistakeDetectionMode(MistakeDetectionMode.CANDIDATE)
                            render()
                        }
                    }
                }
                
                div("mode-explanation") {
                    +when (mistakeDetectionMode) {
                        MistakeDetectionMode.OFF -> "No mistake detection - play without feedback"
                        MistakeDetectionMode.PLACEMENT -> "Alert when placing a wrong number in a cell"
                        MistakeDetectionMode.CANDIDATE -> "Alert for wrong placements AND removing correct candidates"
                    }
                }
            }
        }
    }
}

