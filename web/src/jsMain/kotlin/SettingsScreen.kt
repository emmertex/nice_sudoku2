import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import domain.*
import view.Theme
import view.applyTheme
import i18n.LanguageConfig

/**
 * Extension function for rendering the Settings screen in SudokuApp.
 */
internal fun SudokuApp.renderSettings() {
    appRoot.append {
        div("sudoku-container settings") {
            div("header") {
                button(classes = "back-btn") {
                    +LanguageConfig.getString("ui.settings.back")
                    onClickFunction = {
                        currentScreen = AppScreen.GAME
                        render()
                    }
                }
                h1 { +LanguageConfig.getString("ui.settings.title") }
            }

            // Navigation section
            div("section") {
                h2 { +LanguageConfig.getString("ui.settings.navigation") }
                div("nav-grid") {
                    button(classes = "settings-nav-btn") {
                        +LanguageConfig.getString("ui.settings.puzzles")
                        onClickFunction = {
                            currentScreen = AppScreen.PUZZLE_BROWSER
                            render()
                        }
                    }
                    button(classes = "settings-nav-btn") {
                        +LanguageConfig.getString("ui.settings.importExport")
                        onClickFunction = {
                            currentScreen = AppScreen.IMPORT_EXPORT
                            render()
                        }
                    }
                    button(classes = "settings-nav-btn") {
                        +LanguageConfig.getString("ui.settings.about")
                        onClickFunction = {
                            showAboutModal = true
                            render()
                        }
                    }
                    button(classes = "settings-nav-btn") {
                        +LanguageConfig.getString("ui.settings.help")
                        onClickFunction = {
                            showHelpModal = true
                            render()
                        }
                    }
                }
            }

            // Highlight Mode section
            div("section") {
                h2 { +LanguageConfig.getString("ui.settings.highlightMode") }
                p("setting-desc") { +LanguageConfig.getString("ui.settings.highlightModeDesc") }

                div("mode-options") {
                    val availableModes = listOf(
                        HighlightMode.PENCIL,
                        HighlightMode.RCB_ALL,
                        HighlightMode.RCB_SELECTED,
                        HighlightMode.PLACED
                    )

                    for (mode in availableModes) {
                        val isActive = highlightMode == mode
                        button(classes = "mode-btn ${if (isActive) "active" else ""}") {
                            +when (mode) {
                                HighlightMode.PLACED -> LanguageConfig.getString("ui.modes.placed")
                                HighlightMode.RCB_SELECTED -> LanguageConfig.getString("ui.modes.rcbSel")
                                HighlightMode.RCB_ALL -> LanguageConfig.getString("ui.modes.rcbAll")
                                HighlightMode.PENCIL -> LanguageConfig.getString("ui.modes.pencil")
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
                        HighlightMode.PLACED -> LanguageConfig.getString("ui.modes.placedDescription")
                        HighlightMode.RCB_SELECTED -> LanguageConfig.getString("ui.modes.rcbSelectedDescription")
                        HighlightMode.RCB_ALL -> LanguageConfig.getString("ui.modes.rcbAllDescription")
                        HighlightMode.PENCIL -> LanguageConfig.getString("ui.modes.pencilDescription")
                    }
                }
            }

            // Theme section
            div("section") {
                h2 { +LanguageConfig.getString("ui.settings.theme") }
                p("setting-desc") { +LanguageConfig.getString("ui.settings.themeDesc") }

                div("mode-options theme-options") {
                    for (theme in Theme.entries) {
                        val isActive = currentTheme == theme
                        button(classes = "mode-btn theme-btn ${theme.name.lowercase()} ${if (isActive) "active" else ""}") {
                            +when (theme) {
                                Theme.DARK -> LanguageConfig.getString("ui.settings.dark")
                                Theme.BLUE -> LanguageConfig.getString("ui.settings.blue")
                                Theme.LIGHT -> LanguageConfig.getString("ui.settings.light")
                                Theme.EPAPER -> LanguageConfig.getString("ui.settings.ePaper")
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
                        Theme.DARK -> LanguageConfig.getString("ui.settings.darkDesc")
                        Theme.BLUE -> LanguageConfig.getString("ui.settings.blueDesc")
                        Theme.LIGHT -> LanguageConfig.getString("ui.settings.lightDesc")
                        Theme.EPAPER -> LanguageConfig.getString("ui.settings.ePaperDesc")
                    }
                }
            }

            // Candidate Mode section
            div("section") {
                h2 { +LanguageConfig.getString("ui.settings.candidateMode") }
                p("setting-desc") { +LanguageConfig.getString("ui.settings.candidateModeDesc") }

                div("mode-options") {
                    button(classes = "mode-btn ${if (candidateMode == CandidateMode.AUTO) "active" else ""}") {
                        +LanguageConfig.getString("ui.settings.auto")
                        onClickFunction = {
                            if (candidateMode != CandidateMode.AUTO) {
                                // Show confirmation before switching
                                if (kotlinx.browser.window.confirm(LanguageConfig.getString("ui.settings.autoConfirm"))) {
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
                        +LanguageConfig.getString("ui.settings.manual")
                        onClickFunction = {
                            if (candidateMode != CandidateMode.MANUAL) {
                                // Show confirmation before switching
                                if (kotlinx.browser.window.confirm(LanguageConfig.getString("ui.settings.manualConfirm"))) {
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
                        CandidateMode.AUTO -> LanguageConfig.getString("ui.settings.autoDesc")
                        CandidateMode.MANUAL -> LanguageConfig.getString("ui.settings.manualDesc")
                    }
                }
            }

            // Hint Placement section
            div("section") {
                h2 { +LanguageConfig.getString("ui.settings.hintPlacement") }
                p("setting-desc") { +LanguageConfig.getString("ui.settings.hintPlacementDesc") }

                div("mode-options") {
                    button(classes = "mode-btn ${if (hintPlacement == HintPlacement.AUTO) "active" else ""}") {
                        +LanguageConfig.getString("ui.settings.hintPlacementAuto")
                        onClickFunction = {
                            if (hintPlacement != HintPlacement.AUTO) {
                                hintPlacement = HintPlacement.AUTO
                                GameStateManager.setHintPlacement(HintPlacement.AUTO)
                                useHintSidebar = resolveUseHintSidebar()
                                render()
                            }
                        }
                    }
                    button(classes = "mode-btn ${if (hintPlacement == HintPlacement.SIDEBAR) "active" else ""}") {
                        +LanguageConfig.getString("ui.settings.hintPlacementSide")
                        onClickFunction = {
                            if (hintPlacement != HintPlacement.SIDEBAR) {
                                hintPlacement = HintPlacement.SIDEBAR
                                GameStateManager.setHintPlacement(HintPlacement.SIDEBAR)
                                useHintSidebar = resolveUseHintSidebar()
                                render()
                            }
                        }
                    }
                    button(classes = "mode-btn ${if (hintPlacement == HintPlacement.BELOW) "active" else ""}") {
                        +LanguageConfig.getString("ui.settings.hintPlacementBelow")
                        onClickFunction = {
                            if (hintPlacement != HintPlacement.BELOW) {
                                hintPlacement = HintPlacement.BELOW
                                GameStateManager.setHintPlacement(HintPlacement.BELOW)
                                useHintSidebar = resolveUseHintSidebar()
                                render()
                            }
                        }
                    }
                }

                div("mode-explanation") {
                    +when (hintPlacement) {
                        HintPlacement.AUTO -> LanguageConfig.getString("ui.settings.hintPlacementAutoDesc")
                        HintPlacement.SIDEBAR -> LanguageConfig.getString("ui.settings.hintPlacementSideDesc")
                        HintPlacement.BELOW -> LanguageConfig.getString("ui.settings.hintPlacementBelowDesc")
                    }
                }
            }

            // Mistake Detection section
            div("section") {
                h2 { +LanguageConfig.getString("ui.settings.mistakeDetection") }
                p("setting-desc") { +LanguageConfig.getString("ui.settings.mistakeDetectionDesc") }

                div("mode-options") {
                    button(classes = "mode-btn ${if (mistakeDetectionMode == MistakeDetectionMode.OFF) "active" else ""}") {
                        +LanguageConfig.getString("ui.settings.off")
                        onClickFunction = {
                            mistakeDetectionMode = MistakeDetectionMode.OFF
                            GameStateManager.setMistakeDetectionMode(MistakeDetectionMode.OFF)
                            render()
                        }
                    }
                    button(classes = "mode-btn ${if (mistakeDetectionMode == MistakeDetectionMode.PLACEMENT) "active" else ""}") {
                        +LanguageConfig.getString("ui.settings.placement")
                        onClickFunction = {
                            mistakeDetectionMode = MistakeDetectionMode.PLACEMENT
                            GameStateManager.setMistakeDetectionMode(MistakeDetectionMode.PLACEMENT)
                            render()
                        }
                    }
                    button(classes = "mode-btn ${if (mistakeDetectionMode == MistakeDetectionMode.CANDIDATE) "active" else ""}") {
                        +LanguageConfig.getString("ui.settings.candidate")
                        onClickFunction = {
                            mistakeDetectionMode = MistakeDetectionMode.CANDIDATE
                            GameStateManager.setMistakeDetectionMode(MistakeDetectionMode.CANDIDATE)
                            render()
                        }
                    }
                }

                div("mode-explanation") {
                    +when (mistakeDetectionMode) {
                        MistakeDetectionMode.OFF -> LanguageConfig.getString("ui.settings.offDesc")
                        MistakeDetectionMode.PLACEMENT -> LanguageConfig.getString("ui.settings.placementDesc")
                        MistakeDetectionMode.CANDIDATE -> LanguageConfig.getString("ui.settings.candidateDesc")
                    }
                }
            }

            div("section") {
                h2 { +LanguageConfig.getString("ui.settings.trackPlayTime") }
                p("setting-desc") { +LanguageConfig.getString("ui.settings.trackPlayTimeDesc") }
                div("mode-options") {
                    button(classes = "mode-btn ${if (trackPlayTime) "active" else ""}") {
                        +LanguageConfig.getString("ui.settings.trackPlayTimeOn")
                        onClickFunction = {
                            if (!trackPlayTime) {
                                trackPlayTime = true
                                GameStateManager.setTrackPlayTime(true)
                                currentGame?.let {
                                    pausedTime = it.elapsedTimeMs
                                    gameStartTime = currentTimeMillis()
                                }
                                render()
                            }
                        }
                    }
                    button(classes = "mode-btn ${if (!trackPlayTime) "active" else ""}") {
                        +LanguageConfig.getString("ui.settings.trackPlayTimeOff")
                        onClickFunction = {
                            if (trackPlayTime) {
                                trackPlayTime = false
                                GameStateManager.setTrackPlayTime(false)
                                isPaused = false
                                if (currentGame != null) {
                                    saveCurrentState()
                                }
                                render()
                            }
                        }
                    }
                }
                div("mode-explanation") {
                    +if (trackPlayTime) {
                        LanguageConfig.getString("ui.settings.trackPlayTimeOnDesc")
                    } else {
                        LanguageConfig.getString("ui.settings.trackPlayTimeOffDesc")
                    }
                }
            }

            // Language section
            div("section") {
                h2 { +LanguageConfig.getString("ui.settings.language") }
                p("setting-desc") { +LanguageConfig.getString("ui.settings.languageDesc") }

                div("mode-options language-options") {
                    val availableLanguages = LanguageConfig.getAvailableLanguages()
                    val currentLang = LanguageConfig.currentLanguage

                    for (lang in availableLanguages) {
                        val isActive = currentLang == lang.code
                        button(classes = "mode-btn ${if (isActive) "active" else ""}") {
                            +lang.name
                            onClickFunction = {
                                if (lang.code != currentLang) {
                                    setLanguageWithUrl(lang.code)
                                    render()
                                }
                            }
                        }
                    }
                }
            }

        }
    }
}
