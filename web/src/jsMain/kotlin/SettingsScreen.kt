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
                    for (mode in HighlightMode.entries) {
                        val isActive = highlightMode == mode
                        button(classes = "mode-btn ${if (isActive) "active" else ""}") {
                            +when (mode) {
                                HighlightMode.CELL -> "Cell"
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
                        HighlightMode.CELL -> "Highlights cells with matching solved numbers"
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
                            render()
                        }
                    }
                    button(classes = "mode-btn advanced ${if (playMode == PlayMode.ADVANCED) "active" else ""}") {
                        +"🎯 Advanced"
                        onClickFunction = {
                            playMode = PlayMode.ADVANCED
                            GameStateManager.setPlayMode(PlayMode.ADVANCED)
                            render()
                        }
                    }
                }
                
                div("mode-explanation") {
                    +when (playMode) {
                        PlayMode.FAST -> "Click number, then click cell to fill. Quick and simple."
                        PlayMode.ADVANCED -> "Two number rows for highlighting. Select multiple numbers per color. Cells with ALL selected numbers highlight. Click cell for action buttons."
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
            
            // Two-number highlight info
            div("section highlight-info") {
                h2 { +"🔵🔴 Multi-Number Highlighting" }
                div("color-legend") {
                    div("legend-item") {
                        span("color-box primary") {}
                        span { +"Primary row (blue)" }
                    }
                    div("legend-item") {
                        span("color-box secondary") {}
                        span { +"Secondary row (red)" }
                    }
                    div("legend-item") {
                        span("color-box both") {}
                        span { +"Intersection (both colors)" }
                    }
                }
                p("setting-desc") {
                    +"In Advanced mode, toggle numbers in each row. Only cells containing ALL selected numbers in a row will highlight in that color."
                }
            }
        }
    }
}

