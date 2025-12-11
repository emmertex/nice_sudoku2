import kotlinx.browser.document
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLTextAreaElement
import domain.*
import helpers.importExport.buildShareUrl
import i18n.LanguageConfig

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
    
    // Sudoku Coach format with metadata
    val sudokuCoachString = helpers.importExport.SudokuCoachFormat.exportToSudokuCoach(
        grid = grid,
        originalPuzzle = game?.puzzleString ?: "",
        title = game?.title ?: "",
        author = game?.author ?: "",
        authorContact = game?.authorContact ?: "",
        description = game?.description ?: "",
        playTimeMs = game?.elapsedTimeMs ?: 0L,
        mistakes = game?.mistakeCount ?: 0,
        hints = game?.hintCount ?: 0
    )
    
    appRoot.append {
        div("sudoku-container import-export") {
            div("header") {
                button(classes = "back-btn") {
                    +LanguageConfig.getString("ui.importExport.back")
                    onClickFunction = {
                        currentScreen = AppScreen.GAME
                        render()
                    }
                }
                h1 { +LanguageConfig.getString("ui.importExport.title") }
            }
            
            // Export section
            div("section") {
                h2 { +LanguageConfig.getString("ui.importExport.export") }
                
                div("export-option") {
                    label { +LanguageConfig.getString("ui.importExport.originalPuzzle") }
                    div("export-row") {
                        input(InputType.text, classes = "export-field") {
                            value = puzzleString
                            readonly = true
                        }
                        button(classes = "copy-btn") {
                            +LanguageConfig.getString("ui.importExport.copy")
                            onClickFunction = {
                                ClipboardUtils.copyToClipboard(puzzleString,
                                    onSuccess = { showToast(LanguageConfig.getString("ui.importExport.copiedPuzzle")) },
                                    onError = { showToast(LanguageConfig.getString("ui.importExport.failedToCopy")) }
                                )
                            }
                        }
                        button(classes = "copy-btn") {
                            +LanguageConfig.getString("ui.importExport.copyUrl")
                            onClickFunction = {
                                val shareUrl = buildShareUrl(puzzleString)
                                ClipboardUtils.copyToClipboard(shareUrl,
                                    onSuccess = { showToast(LanguageConfig.getString("ui.importExport.copiedPuzzleUrl")) },
                                    onError = { showToast(LanguageConfig.getString("ui.importExport.failedToCopyUrl")) }
                                )
                            }
                        }
                    }
                }
                
                div("export-option") {
                    label { +LanguageConfig.getString("ui.importExport.currentState") }
                    div("export-row") {
                        input(InputType.text, classes = "export-field") {
                            value = currentValues
                            readonly = true
                        }
                        button(classes = "copy-btn") {
                            +LanguageConfig.getString("ui.importExport.copy")
                            onClickFunction = {
                                ClipboardUtils.copyToClipboard(currentValues,
                                    onSuccess = { showToast(LanguageConfig.getString("ui.importExport.copiedState")) },
                                    onError = { showToast(LanguageConfig.getString("ui.importExport.failedToCopy")) }
                                )
                            }
                        }
                        button(classes = "copy-btn") {
                            +LanguageConfig.getString("ui.importExport.copyUrl")
                            onClickFunction = {
                                val shareUrl = buildShareUrl(currentValues)
                                ClipboardUtils.copyToClipboard(shareUrl,
                                    onSuccess = { showToast(LanguageConfig.getString("ui.importExport.copiedStateUrl")) },
                                    onError = { showToast(LanguageConfig.getString("ui.importExport.failedToCopyUrl")) }
                                )
                            }
                        }
                    }
                }
                
                div("export-option") {
                    label { +LanguageConfig.getString("ui.importExport.fullState") }
                    div("export-row") {
                        input(InputType.text, classes = "export-field") {
                            value = stateString891
                            readonly = true
                        }
                        button(classes = "copy-btn") {
                            +LanguageConfig.getString("ui.importExport.copy")
                            onClickFunction = {
                                ClipboardUtils.copyToClipboard(stateString891,
                                    onSuccess = { showToast(LanguageConfig.getString("ui.importExport.copiedFullState")) },
                                    onError = { showToast(LanguageConfig.getString("ui.importExport.failedToCopy")) }
                                )
                            }
                        }
                        button(classes = "copy-btn") {
                            +LanguageConfig.getString("ui.importExport.copyUrl")
                            onClickFunction = {
                                val shareUrl = buildShareUrl(stateString891)
                                ClipboardUtils.copyToClipboard(shareUrl,
                                    onSuccess = { showToast(LanguageConfig.getString("ui.importExport.copiedFullStateUrl")) },
                                    onError = { showToast(LanguageConfig.getString("ui.importExport.failedToCopyUrl")) }
                                )
                            }
                        }
                    }
                }
                
                if (sudokuCoachString != null) {
                    div("export-option") {
                        label { +LanguageConfig.getString("ui.importExport.sudokuCoachFormat") }
                        div("export-row") {
                            input(InputType.text, classes = "export-field") {
                                value = sudokuCoachString
                                readonly = true
                            }
                            button(classes = "copy-btn") {
                                +LanguageConfig.getString("ui.importExport.copy")
                                onClickFunction = {
                                    ClipboardUtils.copyToClipboard(sudokuCoachString,
                                        onSuccess = { showToast(LanguageConfig.getString("ui.importExport.copiedSudokuCoach")) },
                                        onError = { showToast(LanguageConfig.getString("ui.importExport.failedToCopy")) }
                                    )
                                }
                            }
                            button(classes = "copy-btn") {
                                +LanguageConfig.getString("ui.importExport.copyUrl")
                                onClickFunction = {
                                    val shareUrl = buildShareUrl(sudokuCoachString)
                                    ClipboardUtils.copyToClipboard(shareUrl,
                                        onSuccess = { showToast(LanguageConfig.getString("ui.importExport.copiedSudokuCoachUrl")) },
                                        onError = { showToast(LanguageConfig.getString("ui.importExport.failedToCopyUrl")) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Import section
            div("section") {
                h2 { +LanguageConfig.getString("ui.importExport.import") }
                p("hint") {
                    +(LanguageConfig.getString("ui.importExport.importHint") + " ")
                    a(href = "https://sudoku.coach", target = "_blank") {
                        +"Sudoku Coach"
                    }
                    +" format"
                }
                
                textArea(classes = "import-field") {
                    id = "import-input"
                    placeholder = LanguageConfig.getString("ui.importExport.placeholder")
                }
                
                div("import-actions") {
                    button(classes = "paste-btn") {
                        +LanguageConfig.getString("ui.importExport.pasteFromClipboard")
                        onClickFunction = {
                            ClipboardUtils.readFromClipboard(
                                onSuccess = { text ->
                                    val input = document.getElementById("import-input") as? HTMLTextAreaElement
                                    if (input != null) {
                                        input.value = text
                                    }
                                    render()
                                },
                                onError = { showToast(LanguageConfig.getString("ui.importExport.failedToReadClipboard")) }
                            )
                        }
                    }
                    
                    button(classes = "load-btn") {
                        +LanguageConfig.getString("ui.importExport.loadPuzzle")
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

