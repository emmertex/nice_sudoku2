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
                
                // Top Bar Icons Section
                div("help-section") {
                    h2 { +LanguageConfig.getString("ui.buttonHelp.topBar") }
                    
                    div("button-help-grid") {
                        // Menu button
                        div("button-help-item") {
                            div("button-help-icon") { +"☰" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.menuBtn") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.menuBtnDesc") }
                            }
                        }
                        
                        // Highlight mode badge
                        div("button-help-item") {
                            div("button-help-icon badge highlight-mode") { +"RCB" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.highlightMode") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.highlightModeDesc") }
                            }
                        }
                        
                        // Play mode badge
                        div("button-help-item") {
                            div("button-help-icon badge play-mode fast") { +"Fast" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.playMode") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.playModeDesc") }
                            }
                        }
                    }
                }
                
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
                        
                        // Undo
                        div("button-help-item") {
                            div("button-help-icon btn-preview") { +"↶" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.undoBtn") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.undoBtnDesc") }
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
                        
                        // Primary row (Advanced mode)
                        div("button-help-item") {
                            div("button-help-icon badge primary") { +"1,5" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.primaryRow") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.primaryRowDesc") }
                            }
                        }
                        
                        // Secondary row (Advanced mode)
                        div("button-help-item") {
                            div("button-help-icon badge secondary") { +"3,7" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.secondaryRow") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.secondaryRowDesc") }
                            }
                        }
                    }
                }
                
                // Advanced Mode Actions Section
                div("help-section") {
                    h2 { +LanguageConfig.getString("ui.buttonHelp.advancedActions") }
                    
                    div("button-help-grid") {
                        // Deselect
                        div("button-help-item") {
                            div("button-help-icon btn-preview deselect") { +"Deselect" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.deselectBtn") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.deselectBtnDesc") }
                            }
                        }
                        
                        // Set buttons
                        div("button-help-item") {
                            div("button-help-icon btn-preview set") { +"Set 5" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.setBtn") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.setBtnDesc") }
                            }
                        }
                        
                        // Clear pencil marks
                        div("button-help-item") {
                            div("button-help-icon btn-preview clr") { +"Clr 3,7" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.clrBtn") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.clrBtnDesc") }
                            }
                        }
                        
                        // Clear other
                        div("button-help-item") {
                            div("button-help-icon btn-preview clr-other") { +"Clr ✕" }
                            div("button-help-content") {
                                div("button-help-name") { +LanguageConfig.getString("ui.buttonHelp.clrOtherBtn") }
                                div("button-help-desc") { +LanguageConfig.getString("ui.buttonHelp.clrOtherBtnDesc") }
                            }
                        }
                    }
                }
                
                // Quick tip about mode switching
                div("help-section tip-section") {
                    h2 { +LanguageConfig.getString("ui.buttonHelp.quickTip") }
                    p("tip-text") {
                        +LanguageConfig.getString("ui.buttonHelp.quickTipText")
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

