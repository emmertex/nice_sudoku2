import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import org.w3c.dom.Element
import view.parseMarkdownToHtml
import domain.PuzzleDefinition
import domain.SavedGameState
import domain.getLocalizedDisplayName
import i18n.LanguageConfig

/**
 * Extension functions for rendering modal dialogues in SudokuApp.
 * Note: Help modal is in HelpModal.kt due to its size.
 */

internal fun SudokuApp.renderAboutModal() {
    appRoot.append {
        div("modal-overlay") {
            onClickFunction = { event ->
                // Close when clicking overlay (not the modal content)
                if ((event.target as? Element)?.classList?.contains("modal-overlay") == true) {
                    showAboutModal = false
                    render()
                }
            }
            div("modal-content about-modal") {
                button(classes = "modal-close") {
                    +"✕"
                    onClickFunction = {
                        showAboutModal = false
                        render()
                    }
                }
                
                h1 { +LanguageConfig.getString("ui.modals.about.title") }
                
                p("about-tagline") {
                    +LanguageConfig.getString("ui.modals.about.tagline")
                }
                
                p("about-description") {
                    +LanguageConfig.getString("ui.modals.about.description")
                }
                
                div("about-section") {
                    h3 { +LanguageConfig.getString("ui.modals.about.feedback") }
                    p {
                        +(LanguageConfig.getString("ui.modals.about.feedbackText") + " ")
                        a(href = "https://github.com/emmertex/nice_sudoku2", target = "_blank") {
                            +"github.com/emmertex/nice_sudoku2"
                        }
                    }
                }
                
                div("about-section") {
                    h3 { +LanguageConfig.getString("ui.modals.about.solvers") }
                    p {
                        +(LanguageConfig.getString("ui.modals.about.solversText") + " ")
                        strong { +"StormDoku" }
                    }
                    p {
                        a(href = "https://www.reddit.com/user/strmckr/", target = "_blank") {
                            +"reddit.com/user/strmckr"
                        }
                    }
                    p {
                        +"Reddit Wiki: "
                        a(href = "https://www.reddit.com/r/sudoku/wiki/index/", target = "_blank") {
                            +"r/sudoku wiki"
                        }
                    }
                    p {
                        +"StrmCkr's GitHub: "
                        a(href = "https://github.com/strmckr", target = "_blank") {
                            +"github.com/strmckr"
                        }
                    }
                }
                
                div("about-section") {
                    h3 { +LanguageConfig.getString("ui.modals.about.uiux") }
                    p {
                        +(LanguageConfig.getString("ui.modals.about.uiuxText") + " ")
                        a(href = "https://github.com/emmertex", target = "_blank") {
                            +"github.com/emmertex"
                        }
                    }
                }
                
                div("about-section") {
                    h3 { +LanguageConfig.getString("ui.modals.about.puzzles") }
                    p {
                        +(LanguageConfig.getString("ui.modals.about.puzzlesText") + " ")
                        a(href = "https://github.com/stephenostermiller/qqwing", target = "_blank") {
                            +"qqwing"
                        }
                    }
                }
            }
        }
    }
}

internal fun SudokuApp.renderCompletionModal() {
    val game = currentGame ?: return
    
    appRoot.append {
        div("modal-overlay") {
            onClickFunction = { event ->
                // Close when clicking overlay (not the modal content)
                if ((event.target as? Element)?.classList?.contains("modal-overlay") == true) {
                    showCompletionModal = false
                    render()
                }
            }
            div("modal-content completion-modal") {
                button(classes = "modal-close") {
                    +"✕"
                    onClickFunction = {
                        showCompletionModal = false
                        render()
                    }
                }
                
                div("completion-icon") { +"🎉" }
                h1 { +LanguageConfig.getString("ui.modals.completion.title") }
                
                div("completion-content") {
                    p { +LanguageConfig.getString("ui.modals.completion.message") }
                    
                    div("completion-stats") {
                        div("stat") {
                            span("stat-icon") { +"⏱️" }
                            span("stat-label") { +LanguageConfig.getString("ui.modals.completion.time") }
                            span("stat-value") { +formatTime(game.elapsedTimeMs) }
                        }
                        div("stat") {
                            span("stat-icon") { +"❌" }
                            span("stat-label") { +LanguageConfig.getString("ui.modals.completion.mistakes") }
                            span("stat-value") { +"${game.mistakeCount}" }
                        }
                        div("stat") {
                            span("stat-icon") { +"💡" }
                            span("stat-label") { +LanguageConfig.getString("ui.modals.completion.hints") }
                            span("stat-value") { +"${game.hintCount}" }
                        }
                        div("stat") {
                            span("stat-icon") { +"📊" }
                            span("stat-label") { +LanguageConfig.getString("ui.modals.completion.difficulty") }
                            span("stat-value") { +game.category.getLocalizedDisplayName() }
                        }
                    }
                }
                
                div("completion-actions") {
                    button(classes = "close-btn") {
                        +LanguageConfig.getString("ui.modals.completion.close")
                        onClickFunction = {
                            showCompletionModal = false
                            render()
                        }
                    }
                    button(classes = "next-btn") {
                        +LanguageConfig.getString("ui.modals.completion.nextGame")
                        onClickFunction = {
                            showCompletionModal = false
                            loadNextUncompletedGame(game.category)
                        }
                    }
                }
            }
        }
    }
}

internal fun SudokuApp.renderVersionModal() {
    appRoot.append {
        div("modal-overlay") {
            onClickFunction = { event ->
                // Close when clicking overlay (not the modal content)
                if ((event.target as? Element)?.classList?.contains("modal-overlay") == true) {
                    showVersionModal = false
                    render()
                }
            }
            div("modal-content version-modal") {
                button(classes = "modal-close") {
                    +"✕"
                    onClickFunction = {
                        showVersionModal = false
                        render()
                    }
                }
                
                h1 { +LanguageConfig.getString("ui.modals.version.title") }
                
                // Render changelog content as formatted HTML
                div("changelog-content") {
                    unsafe {
                        +parseMarkdownToHtml(changelogContent)
                    }
                }
                
                div("version-actions") {
                    button(classes = "close-btn") {
                        +LanguageConfig.getString("ui.modals.version.gotIt")
                        onClickFunction = {
                            showVersionModal = false
                            render()
                        }
                    }
                }
            }
        }
    }
}

internal fun SudokuApp.renderPuzzleInfoModal(target: Any) {
    // Extract data from either PuzzleDefinition or SavedGameState
    val (title, author, authorContact, description, difficulty, category, puzzleId, quality, techniques, url) = when (target) {
        is PuzzleDefinition -> {
            InfoData(
                title = target.title,
                author = target.author,
                authorContact = target.authorContact,
                description = target.description,
                difficulty = target.difficulty,
                category = target.category,
                puzzleId = target.id,
                quality = target.quality,
                techniques = target.techniques,
                url = target.url
            )
        }
        is SavedGameState -> {
            InfoData(
                title = target.title,
                author = target.author,
                authorContact = target.authorContact,
                description = target.description,
                difficulty = target.difficulty,
                category = target.category,
                puzzleId = target.puzzleId,
                quality = null,
                techniques = null,
                url = ""
            )
        }
        else -> return
    }
    
    appRoot.append {
        div("modal-overlay") {
            onClickFunction = { event ->
                if ((event.target as? Element)?.classList?.contains("modal-overlay") == true) {
                    showPuzzleInfoModal = false
                    puzzleInfoTarget = null
                    render()
                }
            }
            div("modal-content puzzle-info-modal") {
                button(classes = "modal-close") {
                    +"✕"
                    onClickFunction = {
                        showPuzzleInfoModal = false
                        puzzleInfoTarget = null
                        render()
                    }
                }
                h2 { +LanguageConfig.getString("ui.modals.puzzleInfo.title") }
                
                div("info-grid") {
                    // Title (if available)
                    if (title.isNotEmpty()) {
                        div("info-row") {
                            span("info-label") { +LanguageConfig.getString("ui.modals.puzzleInfo.titleLabel") }
                            if (url.isNotEmpty()) {
                                a(href = url, target = "_blank", classes = "info-value link") {
                                    +title
                                }
                            } else {
                                span("info-value") { +title }
                            }
                        }
                    }
                    
                    // Author (if available)
                    if (author.isNotEmpty()) {
                        div("info-row") {
                            span("info-label") { +LanguageConfig.getString("ui.modals.puzzleInfo.author") }
                            span("info-value") { +author }
                        }
                    }
                    
                    // Author Contact (if available)
                    if (authorContact.isNotEmpty()) {
                        div("info-row") {
                            span("info-label") { +LanguageConfig.getString("ui.modals.puzzleInfo.contact") }
                            // Check if it's a URL or email
                            if (authorContact.startsWith("http://") || authorContact.startsWith("https://")) {
                                a(href = authorContact, target = "_blank", classes = "info-value link") {
                                    +authorContact
                                }
                            } else if (authorContact.contains("@")) {
                                a(href = "mailto:$authorContact", classes = "info-value link") {
                                    +authorContact
                                }
                            } else {
                                span("info-value") { +authorContact }
                            }
                        }
                    }
                    
                    // Description (if available)
                    if (description.isNotEmpty()) {
                        div("info-row full-width") {
                            span("info-label") { +LanguageConfig.getString("ui.modals.puzzleInfo.description") }
                            p("info-value description") { +description }
                        }
                    }
                    
                    // Difficulty
                    if (difficulty > 0) {
                        div("info-row") {
                            span("info-label") { +LanguageConfig.getString("ui.modals.puzzleInfo.difficulty") }
                            span("info-value") { +"${difficulty}" }
                        }
                    }
                    
                    // Category
                    div("info-row") {
                        span("info-label") { +LanguageConfig.getString("ui.modals.puzzleInfo.category") }
                        span("info-value category ${category.name.lowercase()}") { 
                            +category.getLocalizedDisplayName() 
                        }
                    }
                    
                    // Quality (if available)
                    if (quality != null) {
                        div("info-row") {
                            span("info-label") { +LanguageConfig.getString("ui.modals.puzzleInfo.quality") }
                            span("info-value") { +"${quality}/10" }
                        }
                    }
                    
                    // Puzzle ID
                    div("info-row") {
                        span("info-label") { +LanguageConfig.getString("ui.modals.puzzleInfo.puzzleId") }
                        span("info-value puzzle-id") { +puzzleId }
                    }
                    
                    // Techniques (if available)
                    if (!techniques.isNullOrEmpty()) {
                        div("info-section") {
                            h3 { +LanguageConfig.getString("ui.modals.puzzleInfo.techniquesUsed") }
                            div("techniques-list") {
                                techniques.entries.sortedByDescending { it.value }.forEach { (technique, count) ->
                                    div("technique-row") {
                                        span("technique-name") { +technique }
                                        span("technique-count") { +"×$count" }
                                    }
                                }
                            }
                        }
                    }
                }
                
                div("modal-actions") {
                    button(classes = "close-btn") {
                        +LanguageConfig.getString("ui.modals.puzzleInfo.close")
                        onClickFunction = {
                            showPuzzleInfoModal = false
                            puzzleInfoTarget = null
                            render()
                        }
                    }
                    // Only show play button for PuzzleDefinition
                    if (target is PuzzleDefinition) {
                        button(classes = "play-btn") {
                            +LanguageConfig.getString("ui.modals.puzzleInfo.playPuzzle")
                            onClickFunction = {
                                showPuzzleInfoModal = false
                                puzzleInfoTarget = null
                                startNewGame(target)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Data class to hold puzzle info extracted from either PuzzleDefinition or SavedGameState
 */
private data class InfoData(
    val title: String,
    val author: String,
    val authorContact: String,
    val description: String,
    val difficulty: Float,
    val category: domain.DifficultyCategory,
    val puzzleId: String,
    val quality: Float?,
    val techniques: Map<String, Int>?,
    val url: String
)
