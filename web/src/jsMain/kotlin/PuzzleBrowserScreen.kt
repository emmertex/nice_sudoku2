import kotlinx.browser.document
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.onInputFunction
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
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
                        browserSelectedPuzzleId = null
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
                                    browserSelectedPuzzleId = null
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
                
                val puzzles = PuzzleLibrary.getPuzzlesForCategory(selectedCategory)
                val isLoading = PuzzleLibrary.isPuzzlesLoading(selectedCategory)
                if (puzzles.isEmpty() && !isLoading && selectedCategory != DifficultyCategory.CUSTOM) {
                    PuzzleLibrary.getPuzzlesForCategoryAsync(selectedCategory) {
                        render()
                    }
                }

                // Puzzle list
                div("puzzle-list") {
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
                        
                        val puzzleTitleDisplay = if (selectedCategory == DifficultyCategory.CUSTOM) {
                            puzzle.title.ifBlank { existingGame?.title.orEmpty() }
                        } else {
                            puzzle.title
                        }
                        div("puzzle-item ${if (isCompleted) "completed" else ""} ${if (puzzle.id == browserSelectedPuzzleId) "puzzle-item-selected" else ""}") {
                            span("puzzle-num puzzle-num-select") {
                                +"#${index + 1}"
                                onClickFunction = {
                                    browserSelectedPuzzleId =
                                        if (browserSelectedPuzzleId == puzzle.id) null else puzzle.id
                                    render()
                                }
                            }
                            val puzzleUrl = puzzle.url
                            if (puzzleTitleDisplay.isNotEmpty()) {
                                if (puzzleUrl.isNotEmpty()) {
                                    a(href = puzzleUrl, target = "_blank", classes = "puzzle-title-link") {
                                        +puzzleTitleDisplay
                                    }
                                } else {
                                    span("puzzle-title") { +puzzleTitleDisplay }
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
                                        if (browserSelectedPuzzleId == puzzle.id) {
                                            browserSelectedPuzzleId = null
                                        }
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

                browserSelectedPuzzleId?.let { selId ->
                    puzzles.find { it.id == selId }?.let { sp ->
                        val saved = GameStateManager.loadGame(selId)
                        val customPuzzle = GameStateManager.loadCustomPuzzles().find { it.id == selId }
                        val locks = saved?.metadataImportLocks ?: sp.metadataImportLocks
                        fun eff(savedStr: String?, puzzleStr: String) =
                            savedStr?.takeIf { it.isNotBlank() } ?: puzzleStr
                        val titleEff = eff(saved?.title, sp.title)
                        val authorEff = eff(saved?.author, sp.author)
                        val contactEff = eff(saved?.authorContact, sp.authorContact)
                        val descEff = eff(saved?.description, sp.description)
                        val canSaveMetadata = (customPuzzle != null || saved != null) &&
                            !(locks.title && locks.author && locks.authorContact && locks.description)

                        div("puzzle-details-section") {
                            h2 { +LanguageConfig.getString("ui.puzzleBrowser.puzzleDetails") }
                            button(classes = "details-clear-btn") {
                                +LanguageConfig.getString("ui.puzzleBrowser.clearSelection")
                                onClickFunction = {
                                    browserSelectedPuzzleId = null
                                    render()
                                }
                            }
                            div("detail-fields") {
                                fun FlowContent.detailRow(
                                    labelKey: String,
                                    lock: Boolean,
                                    fieldValue: String,
                                    inputId: String,
                                    multiline: Boolean = false
                                ) {
                                    div("detail-field-row") {
                                        span("detail-label") { +LanguageConfig.getString(labelKey) }
                                        if (!lock) {
                                            if (multiline) {
                                                textArea(classes = "detail-textarea") {
                                                    id = inputId
                                                    +fieldValue
                                                }
                                            } else {
                                                input(InputType.text, classes = "detail-input") {
                                                    id = inputId
                                                    value = fieldValue
                                                }
                                            }
                                        } else {
                                            span("detail-value detail-locked") {
                                                +(if (fieldValue.isBlank()) LanguageConfig.getString("ui.puzzleBrowser.metadataDash") else fieldValue)
                                            }
                                        }
                                    }
                                }
                                detailRow("ui.modals.puzzleInfo.titleLabel", locks.title, titleEff, "browser-detail-title")
                                detailRow("ui.modals.puzzleInfo.author", locks.author, authorEff, "browser-detail-author")
                                detailRow("ui.modals.puzzleInfo.contact", locks.authorContact, contactEff, "browser-detail-contact")
                                detailRow("ui.modals.puzzleInfo.description", locks.description, descEff, "browser-detail-description", multiline = true)
                            }
                            if (saved != null) {
                                div("detail-stats-row") {
                                    span("detail-label") { +LanguageConfig.getString("ui.puzzleBrowser.statsPlayTime") }
                                    span("detail-value") { +formatTime(saved.elapsedTimeMs) }
                                    span("detail-label") { +LanguageConfig.getString("ui.puzzleBrowser.statsMistakes") }
                                    span("detail-value") { +"${saved.mistakeCount}" }
                                    span("detail-label") { +LanguageConfig.getString("ui.puzzleBrowser.statsHints") }
                                    span("detail-value") { +"${saved.hintCount}" }
                                }
                                button(classes = "reset-puzzle-btn") {
                                    +LanguageConfig.getString("ui.puzzleBrowser.resetPuzzle")
                                    onClickFunction = {
                                        resetSavedPuzzleToGivens(selId)
                                    }
                                }
                            }
                            if (canSaveMetadata) {
                                button(classes = "save-metadata-btn") {
                                    +LanguageConfig.getString("ui.puzzleBrowser.saveMetadata")
                                    onClickFunction = {
                                        val t = (document.getElementById("browser-detail-title") as? HTMLInputElement)?.value?.trim() ?: ""
                                        val a = (document.getElementById("browser-detail-author") as? HTMLInputElement)?.value?.trim() ?: ""
                                        val c = (document.getElementById("browser-detail-contact") as? HTMLInputElement)?.value?.trim() ?: ""
                                        val d = (document.getElementById("browser-detail-description") as? HTMLTextAreaElement)?.value?.trim() ?: ""
                                        savePuzzleBrowserMetadata(selId, t, a, c, d)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

