import kotlinx.browser.document
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLTextAreaElement
import domain.*
import helpers.importExport.buildShareUrl
import helpers.importExport.SudokuCoachFormat
import i18n.LanguageConfig

/**
 * Extension function for rendering the Import/Export screen in SudokuApp.
 */
internal fun SudokuApp.renderImportExport() {
    val grid = gameEngine.getCurrentGrid()
    val game = currentGame

    val puzzleString = game?.puzzleString ?: ""
    val state891 = SavedGameState.createStateStringFor891Export(grid, puzzleString)
    val state810 = SavedGameState.createStateStringForExport(grid)
    val state729 = if (state810.length >= 810) state810.substring(81, 810) else ""

    if (exportPanelScope != "original" && exportPanelScope != "current") {
        exportPanelScope = "current"
    }
    if (exportPanelScope == "original" && exportPanelSubKey != "81" && exportPanelSubKey != "coach") {
        exportPanelSubKey = "coach"
    }
    if (exportPanelScope == "current" && exportPanelSubKey != "729" && exportPanelSubKey != "891" && exportPanelSubKey != "coach") {
        exportPanelSubKey = "coach"
    }

    val exportString = when (exportPanelScope) {
        "original" -> when (exportPanelSubKey) {
            "81" -> puzzleString
            "coach" -> SudokuCoachFormat.exportToSudokuCoachOriginalPuzzle(
                originalPuzzle = puzzleString,
                title = game?.title ?: "",
                author = game?.author ?: "",
                authorContact = game?.authorContact ?: "",
                description = game?.description ?: ""
            ) ?: ""
            else -> puzzleString
        }
        else -> when (exportPanelSubKey) {
            "729" -> state729
            "891" -> state891
            "coach" -> SudokuCoachFormat.exportToSudokuCoach(
                grid = grid,
                originalPuzzle = puzzleString,
                title = game?.title ?: "",
                author = game?.author ?: "",
                authorContact = game?.authorContact ?: "",
                description = game?.description ?: "",
                playTimeMs = game?.elapsedTimeMs ?: 0L,
                mistakes = game?.mistakeCount ?: 0,
                hints = game?.hintCount ?: 0
            ) ?: ""
            else -> state891
        }
    }

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

            div("section") {
                h2 { +LanguageConfig.getString("ui.importExport.export") }

                div("export-panel-scope") {
                    button(classes = "export-scope-btn ${if (exportPanelScope == "original") "active" else ""}") {
                        +LanguageConfig.getString("ui.importExport.scopeOriginal")
                        onClickFunction = {
                            exportPanelScope = "original"
                            if (exportPanelSubKey != "81" && exportPanelSubKey != "coach") {
                                exportPanelSubKey = "81"
                            }
                            render()
                        }
                    }
                    button(classes = "export-scope-btn ${if (exportPanelScope == "current") "active" else ""}") {
                        +LanguageConfig.getString("ui.importExport.scopeCurrent")
                        onClickFunction = {
                            exportPanelScope = "current"
                            if (exportPanelSubKey != "729" && exportPanelSubKey != "891" && exportPanelSubKey != "coach") {
                                exportPanelSubKey = "coach"
                            }
                            render()
                        }
                    }
                }

                if (exportPanelScope == "original") {
                    div("export-panel-formats") {
                        button(classes = "export-format-btn ${if (exportPanelSubKey == "81") "active" else ""}") {
                            +LanguageConfig.getString("ui.importExport.fmt81")
                            onClickFunction = {
                                exportPanelSubKey = "81"
                                render()
                            }
                        }
                        button(classes = "export-format-btn ${if (exportPanelSubKey == "coach") "active" else ""}") {
                            +LanguageConfig.getString("ui.importExport.fmtCoachComplete")
                            onClickFunction = {
                                exportPanelSubKey = "coach"
                                render()
                            }
                        }
                    }
                } else {
                    div("export-panel-formats") {
                        button(classes = "export-format-btn ${if (exportPanelSubKey == "729") "active" else ""}") {
                            +LanguageConfig.getString("ui.importExport.fmt729")
                            onClickFunction = {
                                exportPanelSubKey = "729"
                                render()
                            }
                        }
                        button(classes = "export-format-btn ${if (exportPanelSubKey == "891") "active" else ""}") {
                            +LanguageConfig.getString("ui.importExport.fmt891")
                            onClickFunction = {
                                exportPanelSubKey = "891"
                                render()
                            }
                        }
                        button(classes = "export-format-btn ${if (exportPanelSubKey == "coach") "active" else ""}") {
                            +LanguageConfig.getString("ui.importExport.fmtCoachCurrentFull")
                            onClickFunction = {
                                exportPanelSubKey = "coach"
                                render()
                            }
                        }
                    }
                }

                p("export-complete-hint") {
                    +LanguageConfig.getString("ui.importExport.completeFormatDescription")
                }

                div("export-row") {
                    input(InputType.text, classes = "export-field") {
                        value = exportString
                        readonly = true
                    }
                    button(classes = "copy-btn") {
                        +LanguageConfig.getString("ui.importExport.copy")
                        onClickFunction = {
                            ClipboardUtils.copyToClipboard(exportString,
                                onSuccess = { showToast(LanguageConfig.getString("ui.importExport.copiedExport")) },
                                onError = { showToast(LanguageConfig.getString("ui.importExport.failedToCopy")) }
                            )
                        }
                    }
                    button(classes = "copy-btn") {
                        +LanguageConfig.getString("ui.importExport.copyUrl")
                        onClickFunction = {
                            val shareUrl = buildShareUrl(exportString)
                            ClipboardUtils.copyToClipboard(shareUrl,
                                onSuccess = { showToast(LanguageConfig.getString("ui.importExport.copiedExportUrl")) },
                                onError = { showToast(LanguageConfig.getString("ui.importExport.failedToCopyUrl")) }
                            )
                        }
                    }
                }
            }

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

            if (toastMessage != null) {
                div("toast") { +toastMessage!! }
            }
        }
    }
}
