import kotlinx.browser.document
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLTextAreaElement
import domain.*
import helpers.importExport.buildShareUrl

/**
 * Extension function for rendering the Import/Export screen in SudokuApp.
 */
internal fun SudokuApp.renderImportExport() {
    val grid = gameEngine.getCurrentGrid()
    val game = currentGame
    
    // Create export strings
    val puzzleString = game?.puzzleString ?: ""
    val currentValues = grid.cells.joinToString("") { 
        (it.value ?: 0).toString() 
    }
    // Use new export format (user eliminations: 1 = eliminated) with original puzzle
    val stateString891 = SavedGameState.createStateStringFor891Export(grid, game?.puzzleString ?: "")
    
    appRoot.append {
        div("sudoku-container import-export") {
            div("header") {
                button(classes = "back-btn") {
                    +"← Back"
                    onClickFunction = {
                        currentScreen = AppScreen.GAME
                        render()
                    }
                }
                h1 { +"Import / Export" }
            }
            
            // Export section
            div("section") {
                h2 { +"📤 Export" }
                
                div("export-option") {
                    label { +"Original Puzzle (81 chars)" }
                    div("export-row") {
                        input(InputType.text, classes = "export-field") {
                            value = puzzleString
                            readonly = true
                        }
                        button(classes = "copy-btn") {
                            +"Copy"
                            onClickFunction = {
                                ClipboardUtils.copyToClipboard(puzzleString,
                                    onSuccess = { showToast("✓ Copied puzzle!") },
                                    onError = { showToast("Failed to copy") }
                                )
                            }
                        }
                        button(classes = "copy-btn") {
                            +"Copy URL"
                            onClickFunction = {
                                val shareUrl = buildShareUrl(puzzleString)
                                ClipboardUtils.copyToClipboard(shareUrl,
                                    onSuccess = { showToast("✓ Copied puzzle URL!") },
                                    onError = { showToast("Failed to copy URL") }
                                )
                            }
                        }
                    }
                }
                
                div("export-option") {
                    label { +"Current State (81 chars)" }
                    div("export-row") {
                        input(InputType.text, classes = "export-field") {
                            value = currentValues
                            readonly = true
                        }
                        button(classes = "copy-btn") {
                            +"Copy"
                            onClickFunction = {
                                ClipboardUtils.copyToClipboard(currentValues,
                                    onSuccess = { showToast("✓ Copied state!") },
                                    onError = { showToast("Failed to copy") }
                                )
                            }
                        }
                        button(classes = "copy-btn") {
                            +"Copy URL"
                            onClickFunction = {
                                val shareUrl = buildShareUrl(currentValues)
                                ClipboardUtils.copyToClipboard(shareUrl,
                                    onSuccess = { showToast("✓ Copied state URL!") },
                                    onError = { showToast("Failed to copy URL") }
                                )
                            }
                        }
                    }
                }
                
                div("export-option") {
                    label { +"State, Givens and Eliminations (891 chars)" }
                    div("export-row") {
                        input(InputType.text, classes = "export-field") {
                            value = stateString891
                            readonly = true
                        }
                        button(classes = "copy-btn") {
                            +"Copy"
                            onClickFunction = {
                                ClipboardUtils.copyToClipboard(stateString891,
                                    onSuccess = { showToast("✓ Copied full state!") },
                                    onError = { showToast("Failed to copy") }
                                )
                            }
                        }
                        button(classes = "copy-btn") {
                            +"Copy URL"
                            onClickFunction = {
                                val shareUrl = buildShareUrl(stateString891)
                                ClipboardUtils.copyToClipboard(shareUrl,
                                    onSuccess = { showToast("✓ Copied full state URL!") },
                                    onError = { showToast("Failed to copy URL") }
                                )
                            }
                        }
                    }
                }
            }
            
            // Import section
            div("section") {
                h2 { +"📥 Import" }
                p("hint") { +"Paste an 81-char puzzle, 810-char state, or 891-char full state string" }
                
                textArea(classes = "import-field") {
                    id = "import-input"
                    placeholder = "Paste puzzle string here..."
                }
                
                div("import-actions") {
                    button(classes = "paste-btn") {
                        +"📋 Paste from Clipboard"
                        onClickFunction = {
                            ClipboardUtils.readFromClipboard(
                                onSuccess = { text ->
                                    val input = document.getElementById("import-input") as? HTMLTextAreaElement
                                    if (input != null) {
                                        input.value = text
                                    }
                                    render()
                                },
                                onError = { showToast("Failed to read clipboard") }
                            )
                        }
                    }
                    
                    button(classes = "load-btn") {
                        +"Load Puzzle"
                        onClickFunction = {
                            val input = document.getElementById("import-input") as? HTMLTextAreaElement
                            val text = input?.value?.trim() ?: ""
                            importGameFromString(text)
                        }
                    }
                }
            }
            
            // Toast
            if (toastMessage != null) {
                div("toast") { +toastMessage!! }
            }
        }
    }
}

