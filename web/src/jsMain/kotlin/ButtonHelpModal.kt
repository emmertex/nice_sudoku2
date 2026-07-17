import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import org.w3c.dom.Element
import i18n.LanguageConfig

/**
 * Extension function for rendering the Button Help modal in SudokuApp.
 * Provides a quick reference guide for all buttons and icons on the game screen.
 */
internal fun SudokuApp.renderButtonHelpModal() {
    appRoot.append {
        div("modal-overlay") {
            onClickFunction = { event ->
                // Close when clicking overlay (not the modal content)
                if ((event.target as? Element)?.classList?.contains("modal-overlay") == true) {
                    showButtonHelpModal = false
                    render()
                }
            }
            div("modal-content button-help-modal") {
                button(classes = "modal-close") {
                    +"✕"
                    onClickFunction = {
                        showButtonHelpModal = false
                        render()
                    }
                }

                h1 { +LanguageConfig.getString("ui.buttonHelp.title") }

                // Game Info Row Section
                div("help-section") {
                    h2 { +LanguageConfig.getString("ui.buttonHelp.gameInfo") }

                    div("button-help-grid") {
                        // Category badge
                        div("button-help-item") {
                            div("button-help-icon badge category easy") { +"Easy" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.category") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.categoryDesc") }
                            }
                        }

                        // Difficulty
                        div("button-help-item") {
                            div("button-help-icon") { +"★ 2.5" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.difficulty") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.difficultyDesc") }
                            }
                        }

                        // Timer
                        div("button-help-item") {
                            div("button-help-icon") { +"⏱ 5:23" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.timer") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.timerDesc") }
                            }
                        }

                        // Pause button
                        div("button-help-item") {
                            div("button-help-icon btn-preview") { +"⏸" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.pauseBtn") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.pauseBtnDesc") }
                            }
                        }

                        // Mistakes
                        div("button-help-item") {
                            div("button-help-icon") { +"❌ 0" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.mistakes") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.mistakesDesc") }
                            }
                        }

                        // Hints used
                        div("button-help-item") {
                            div("button-help-icon") { +"💡 0" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.hintsUsed") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.hintsUsedDesc") }
                            }
                        }
                    }
                }

                // Control Buttons Section
                div("help-section") {
                    h2 { +LanguageConfig.getString("ui.buttonHelp.controls") }

                    div("button-help-grid") {
                        // Notes toggle
                        div("button-help-item") {
                            div("button-help-icon btn-preview") { +"✏️" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.notesBtn") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.notesBtnDesc") }
                            }
                        }
                        // Multi Select toggle
                        div("button-help-item") {
                            div("button-help-icon btn-preview") { +"✏️✏️" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.notes2Btn") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.notes2BtnDesc") }
                            }
                        }
                        // Undo
                        div("button-help-item") {
                            div("button-help-icon btn-preview") { +"↶" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.undoBtn") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.undoBtnDesc") }
                            }
                        }

                        // Erase
                        div("button-help-item") {
                            div("button-help-icon btn-preview") { +"⌫" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.eraseBtn") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.eraseBtnDesc") }
                            }
                        }

                        // Hint
                        div("button-help-item") {
                            div("button-help-icon btn-preview hint") { +"💡" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.hintBtn") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.hintBtnDesc") }
                            }
                        }

                        // Clear selection (X)
                        div("button-help-item") {
                            div("button-help-icon btn-preview") { +"✕" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.clearBtn") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.clearBtnDesc") }
                            }
                        }
                    }
                }

                // Number Pads Section
                div("help-section") {
                    h2 { +LanguageConfig.getString("ui.buttonHelp.numberPads") }

                    div("button-help-grid") {
                        // Number buttons
                        div("button-help-item") {
                            div("button-help-icon number-pad-preview") {
                                span("num") { +"1" }
                                span("num") { +"2" }
                                span("num dots") { +"..." }
                                span("num") { +"9" }
                            }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.numberBtns") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.numberBtnsDesc") }
                            }
                        }
                    }
                }

                // Multi Select Mode Actions Section
                div("help-section") {
                    h2 { +LanguageConfig.getString("ui.buttonHelp.advancedActions") }


                        // Clear pencil marks
                        div("button-help-item") {
                            div("button-help-icon btn-preview clr") { +"Clr Sel" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.clrBtn") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.clrBtnDesc") }
                            }
                        }

                        // Clear other
                        div("button-help-item") {
                            div("button-help-icon btn-preview clr-other") { +"Clr Oth" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.clrOtherBtn") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.clrOtherBtnDesc") }
                            }
                        }

                }

                // Link to full help modal
                div("help-section link-section") {
                    p("help-link-text") {
                        +LanguageConfig.getString("ui.buttonHelp.moreHelp")
                        +" "
                        a(classes = "help-link") {
                            +LanguageConfig.getString("ui.buttonHelp.keyboardShortcuts")
                            onClickFunction = {
                                showButtonHelpModal = false
                                showHelpModal = true
                                render()
                            }
                        }
                    }
                }

                // Close button
                div("button-help-actions") {
                    button(classes = "close-btn") {
                        +LanguageConfig.getString("ui.buttonHelp.gotIt")
                        onClickFunction = {
                            showButtonHelpModal = false
                            render()
                        }
                    }
                }
            }
        }
    }
}
