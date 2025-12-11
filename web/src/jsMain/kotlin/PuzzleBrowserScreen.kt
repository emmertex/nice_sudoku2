import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.onInputFunction
import domain.*
import domain.getLocalizedDisplayName
import i18n.LanguageConfig

/**
 * Extension function for rendering the Puzzle Browser screen in SudokuApp.
 */
internal fun SudokuApp.renderPuzzleBrowser() {
    val summaries = GameStateManager.getGameSummaries()
    val incompleteSummaries = summaries.filter { !it.isCompleted }
    
    appRoot.append {
        div("sudoku-container browser") {
            div("header") {
                button(classes = "back-btn") {
                    +LanguageConfig.getString("ui.puzzleBrowser.back")
                    onClickFunction = {
                        currentScreen = AppScreen.GAME
                        render()
                    }
                }
                h1 { +LanguageConfig.getString("ui.puzzleBrowser.title") }
            }
            
            // Resume incomplete games
            if (incompleteSummaries.isNotEmpty()) {
                div("section") {
                    h2 { +LanguageConfig.getString("ui.puzzleBrowser.resumeGame") }
                    div("game-list") {
                        for (summary in incompleteSummaries) {
                            // Use stored category, fallback to difficulty-based for edge cases
                            val displayCategory = summary.category
                            div("game-item") {
                                // Show title if available
                                if (summary.title.isNotEmpty()) {
                                    span("game-title") { 
                                        +summary.title
                                        // Info button if there's any metadata
                                        if (summary.author.isNotEmpty() || summary.description.isNotEmpty()) {
                                            +" ℹ️"
                                        }
                                    }
                                    attributes["data-has-info"] = "true"
                                    onClickFunction = { event ->
                                        val target = event.target
                                        val classList = (target as? org.w3c.dom.HTMLElement)?.classList
                                        // Only show info if clicking on the title or info icon
                                        if (classList?.contains("game-title") == true || 
                                            (target as? org.w3c.dom.Element)?.textContent?.contains("ℹ️") == true) {
                                            val saved = GameStateManager.loadGame(summary.puzzleId)
                                            if (saved != null) {
                                                puzzleInfoTarget = saved
                                                showPuzzleInfoModal = true
                                                render()
                                            }
                                        }
                                    }
                                }
                                span("category ${displayCategory.name.lowercase()}") { 
                                    +displayCategory.getLocalizedDisplayName()
                                }
                                span("progress") { +"${summary.progressPercent}%" }
                                span("time") { +formatTime(summary.elapsedTimeMs) }
                                span("mistakes") { +"❌${summary.mistakeCount}" }
                                span("hints") { +"💡${summary.hintCount}" }
                                button(classes = "resume-btn") {
                                    +LanguageConfig.getString("ui.puzzleBrowser.resume")
                                    onClickFunction = {
                                val saved = GameStateManager.loadGame(summary.puzzleId)
                                if (saved != null) {
                                    resumeGame(saved)
                                }
                                    }
                                }
                                button(classes = "delete-btn") {
                                    +LanguageConfig.getString("ui.puzzleBrowser.delete")
                                    attributes["title"] = LanguageConfig.getString("ui.puzzleBrowser.deleteTitle")
                                    onClickFunction = {
                                        GameStateManager.deleteGame(summary.puzzleId)
                                        showToast(LanguageConfig.getString("ui.puzzleBrowser.gameDeleted"))
                                        render()
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Category selector
            div("section") {
                h2 { +LanguageConfig.getString("ui.puzzleBrowser.newPuzzle") }
                div("category-header") {
                    div("category-tabs") {
                        for (cat in DifficultyCategory.entries) {
                            button(classes = "tab-btn ${if (selectedCategory == cat) "active" else ""}") {
                                +cat.getLocalizedDisplayName()
                                onClickFunction = {
                                    selectedCategory = cat
                                    render()
                                }
                            }
                        }
                    }
                    // Hide completed toggle
                    label(classes = "toggle-label") {
                        input(InputType.checkBox, classes = "toggle-checkbox") {
                            checked = hideCompletedPuzzles
                            onInputFunction = {
                                hideCompletedPuzzles = !hideCompletedPuzzles
                                GameStateManager.setHideCompleted(hideCompletedPuzzles)
                                render()
                            }
                        }
                        span { +LanguageConfig.getString("ui.puzzleBrowser.hideCompleted") }
                    }
                }
                
                // Puzzle list
                div("puzzle-list") {
                    val puzzles = PuzzleLibrary.getPuzzlesForCategory(selectedCategory)
                    val isLoading = PuzzleLibrary.isPuzzlesLoading(selectedCategory)
                    
                    // Trigger async load with callback to re-render
                    if (puzzles.isEmpty() && !isLoading && selectedCategory != DifficultyCategory.CUSTOM) {
                        PuzzleLibrary.getPuzzlesForCategoryAsync(selectedCategory) {
                            render()
                        }
                    }
                    
                    if (isLoading) {
                        div("empty-message") {
                            +LanguageConfig.getString("ui.puzzleBrowser.loadingPuzzles")
                        }
                    } else if (puzzles.isEmpty() && selectedCategory == DifficultyCategory.CUSTOM) {
                        div("empty-message") {
                            +LanguageConfig.getString("ui.puzzleBrowser.noCustomPuzzles")
                        }
                    } else if (puzzles.isEmpty()) {
                        div("empty-message") {
                            +LanguageConfig.getString("ui.puzzleBrowser.noPuzzlesAvailable")
                        }
                    }
                    for ((index, puzzle) in puzzles.withIndex()) {
                        val existingGame = summaries.find { it.puzzleId == puzzle.id }
                        val isCompleted = existingGame?.isCompleted == true
                        
                        // Skip completed puzzles if hide completed is enabled
                        if (hideCompletedPuzzles && isCompleted) continue
                        
                        div("puzzle-item ${if (isCompleted) "completed" else ""}") {
                            span("puzzle-num") { +"#${index + 1}" }
                            // Show title if available (as link if URL exists)
                            val puzzleTitle = puzzle.title
                            val puzzleUrl = puzzle.url
                            if (puzzleTitle.isNotEmpty()) {
                                if (puzzleUrl.isNotEmpty()) {
                                    a(href = puzzleUrl, target = "_blank", classes = "puzzle-title-link") {
                                        +puzzleTitle
                                    }
                                } else {
                                    span("puzzle-title") { +puzzleTitle }
                                }
                                // Show info button if there's additional metadata
                                if (puzzle.author.isNotEmpty() || puzzle.description.isNotEmpty()) {
                                    button(classes = "info-btn") {
                                        +"ℹ️"
                                        attributes["title"] = "Show puzzle information"
                                        onClickFunction = {
                                            puzzleInfoTarget = puzzle
                                            showPuzzleInfoModal = true
                                            render()
                                        }
                                    }
                                }
                            }
                            // Show category for graded custom puzzles
                            if (selectedCategory == DifficultyCategory.CUSTOM && puzzle.difficulty > 0) {
                                val displayCategory = DifficultyCategory.fromDifficulty(puzzle.difficulty)
                                span("category ${displayCategory.name.lowercase()}") { 
                                    +displayCategory.getLocalizedDisplayName()
                                }
                            }
                            if (puzzle.difficulty > 0) {
                                span("difficulty") { +"★ ${puzzle.difficulty}" }
                            }
                            if (existingGame != null) {
                                if (isCompleted) {
                                    span("status completed") { +LanguageConfig.getString("ui.puzzleBrowser.completed") }
                                    span("completion-stats") { 
                                        +"${formatTime(existingGame.elapsedTimeMs)} · ❌${existingGame.mistakeCount} · 💡${existingGame.hintCount}"
                                    }
                                } else {
                                    span("status progress") { +"${existingGame.progressPercent}%" }
                                }
                            }
                            button(classes = "info-btn") {
                                +"ℹ️"
                                attributes["title"] = LanguageConfig.getString("ui.puzzleBrowser.puzzleInfo")
                                onClickFunction = {
                                    puzzleInfoTarget = puzzle
                                    showPuzzleInfoModal = true
                                    render()
                                }
                            }
                            // Show delete button for custom puzzles
                            if (selectedCategory == DifficultyCategory.CUSTOM) {
                                button(classes = "delete-btn") {
                                    +LanguageConfig.getString("ui.puzzleBrowser.delete")
                                    attributes["title"] = LanguageConfig.getString("ui.puzzleBrowser.deleteCustomPuzzle")
                                    onClickFunction = {
                                        GameStateManager.deleteCustomPuzzle(puzzle.id)
                                        // Also delete the saved game if it exists
                                        GameStateManager.deleteGame(puzzle.id)
                                        showToast(LanguageConfig.getString("ui.puzzleBrowser.customPuzzleDeleted"))
                                        render()
                                    }
                                }
                            }
                            button(classes = "play-btn") {
                                +if (isCompleted) LanguageConfig.getString("ui.puzzleBrowser.replay") else LanguageConfig.getString("ui.puzzleBrowser.play")
                                onClickFunction = {
                                    startNewGame(puzzle)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

