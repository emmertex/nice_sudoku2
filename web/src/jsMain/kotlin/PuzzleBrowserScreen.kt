import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.onInputFunction
import domain.*

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
                    +"← Back"
                    onClickFunction = {
                        currentScreen = AppScreen.GAME
                        render()
                    }
                }
                h1 { +"Puzzle Browser" }
            }
            
            // Resume incomplete games
            if (incompleteSummaries.isNotEmpty()) {
                div("section") {
                    h2 { +"⏸ Resume Game" }
                    div("game-list") {
                        for (summary in incompleteSummaries.take(5)) {
                            // Use difficulty-based category for display (handles old games with removed categories)
                            val displayCategory = DifficultyCategory.fromDifficulty(summary.difficulty)
                            div("game-item") {
                                span("category ${displayCategory.name.lowercase()}") { 
                                    +displayCategory.displayName 
                                }
                                span("progress") { +"${summary.progressPercent}%" }
                                span("time") { +formatTime(summary.elapsedTimeMs) }
                                span("mistakes") { +"❌${summary.mistakeCount}" }
                                button(classes = "resume-btn") {
                                    +"Resume"
                                    onClickFunction = {
                                val saved = GameStateManager.loadGame(summary.puzzleId)
                                if (saved != null) {
                                    resumeGame(saved)
                                }
                                    }
                                }
                                button(classes = "delete-btn") {
                                    +"🗑️"
                                    attributes["title"] = "Delete saved game"
                                    onClickFunction = {
                                        GameStateManager.deleteGame(summary.puzzleId)
                                        showToast("Game deleted")
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
                h2 { +"🎯 New Puzzle" }
                div("category-header") {
                    div("category-tabs") {
                        for (cat in DifficultyCategory.entries) {
                            button(classes = "tab-btn ${if (selectedCategory == cat) "active" else ""}") {
                                +cat.displayName
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
                        span { +"Hide Completed" }
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
                            +"Loading puzzles..."
                        }
                    } else if (puzzles.isEmpty() && selectedCategory == DifficultyCategory.CUSTOM) {
                        div("empty-message") {
                            +"No custom puzzles yet. Import a puzzle from the Import/Export page to add it here."
                        }
                    } else if (puzzles.isEmpty()) {
                        div("empty-message") {
                            +"No puzzles available for this category."
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
                            if (puzzleTitle != null) {
                                if (puzzleUrl != null) {
                                    a(href = puzzleUrl, target = "_blank", classes = "puzzle-title-link") {
                                        +puzzleTitle
                                    }
                                } else {
                                    span("puzzle-title") { +puzzleTitle }
                                }
                            }
                            if (puzzle.difficulty > 0) {
                                span("difficulty") { +"★ ${puzzle.difficulty}" }
                            }
                            if (existingGame != null) {
                                if (isCompleted) {
                                    span("status completed") { +"✓ Completed" }
                                    span("completion-stats") { 
                                        +"${formatTime(existingGame.elapsedTimeMs)} · ❌${existingGame.mistakeCount}"
                                    }
                                } else {
                                    span("status progress") { +"${existingGame.progressPercent}%" }
                                }
                            }
                            button(classes = "info-btn") {
                                +"ℹ️"
                                attributes["title"] = "Puzzle info"
                                onClickFunction = {
                                    puzzleInfoTarget = puzzle
                                    showPuzzleInfoModal = true
                                    render()
                                }
                            }
                            button(classes = "play-btn") {
                                +if (isCompleted) "Replay" else "Play"
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

