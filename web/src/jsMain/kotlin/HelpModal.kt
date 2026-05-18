import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import org.w3c.dom.Element
import i18n.LanguageConfig

/**
 * Extension function for rendering the Help modal in SudokuApp.
 */
internal fun SudokuApp.renderHelpModal() {
    appRoot.append {
        div("modal-overlay") {
            onClickFunction = { event ->
                // Close when clicking overlay (not the modal content)
                if ((event.target as? Element)?.classList?.contains("modal-overlay") == true) {
                    showHelpModal = false
                    render()
                }
            }
            div("modal-content help-modal") {
                button(classes = "modal-close") {
                    +"✕"
                    onClickFunction = {
                        showHelpModal = false
                        render()
                    }
                }
                
                h1 { +LanguageConfig.getString("ui.help.title") }
                
                div("help-section") {
                    h2 { +LanguageConfig.getString("ui.help.welcome") }
                        p {
                            +LanguageConfig.getString("ui.help.welcomeText")
                        }
                        
                        
                        p {
                            +LanguageConfig.getString("ui.help.author")
                        }
                    
                }
                
                div("help-section") {
                    h2 { +LanguageConfig.getString("ui.help.keyboardShortcuts") }
                }
                
                div("help-section") {
                    h2 { +LanguageConfig.getString("ui.help.navigation") }
                    
                    h3 { +LanguageConfig.getString("ui.help.cellSelection") }
                    ul {
                        li {
                            strong { +LanguageConfig.getString("ui.help.arrowKeys") }
                            +LanguageConfig.getString("ui.help.arrowKeysDesc")
                        }
                        li {
                            strong { +LanguageConfig.getString("ui.help.ctrlArrowKeys") }
                            +LanguageConfig.getString("ui.help.ctrlArrowKeysDesc")
                        }
                        li {
                            strong { +LanguageConfig.getString("ui.help.home") }
                            +LanguageConfig.getString("ui.help.homeDesc")
                        }
                        li {
                            strong { +LanguageConfig.getString("ui.help.end") }
                            +LanguageConfig.getString("ui.help.endDesc")
                        }
                        li {
                            strong { +LanguageConfig.getString("ui.help.ctrlHome") }
                            +LanguageConfig.getString("ui.help.ctrlHomeDesc")
                        }
                        li {
                            strong { +LanguageConfig.getString("ui.help.ctrlEnd") }
                            +LanguageConfig.getString("ui.help.ctrlEndDesc")
                        }
                    }
                }
                
                div("help-section") {
                    h2 { +LanguageConfig.getString("ui.help.numberEntry") }

                    h3 { +LanguageConfig.getString("ui.help.basicEntry") }
                    ul {
                        li {
                            strong { +LanguageConfig.getString("ui.help.numberKeys") }
                            +LanguageConfig.getString("ui.help.numberKeysDesc")
                        }
                    }
                    
                    h3 { +LanguageConfig.getString("ui.help.candidateEntry") }
                    ul {
                        li {
                            strong { +LanguageConfig.getString("ui.help.ctrlNumberKeys") }
                            +LanguageConfig.getString("ui.help.ctrlNumberKeysDesc")
                        }
                        li {
                            strong { +LanguageConfig.getString("ui.help.space") }
                            +LanguageConfig.getString("ui.help.spaceDesc")
                        }
                        li {
                            strong { +LanguageConfig.getString("ui.help.n") }
                            +LanguageConfig.getString("ui.help.nDesc")
                        }
                    }
                }
                
                div("help-section") {
                    h2 { +LanguageConfig.getString("ui.help.editing") }
                    ul {
                        li {
                            strong { +LanguageConfig.getString("ui.help.undoButton") }
                            +LanguageConfig.getString("ui.help.undoButtonDesc")
                        }
                        li {
                            strong { +LanguageConfig.getString("ui.help.escape") }
                            +LanguageConfig.getString("ui.help.escapeDesc")
                        }
                    }
                }
                
                div("help-section") {
                    h2 { +LanguageConfig.getString("ui.help.filters") }
                    ul {
                        li {
                            strong { +LanguageConfig.getString("ui.help.f1f9") }
                            +LanguageConfig.getString("ui.help.f1f9Desc")
                        }
                        li {
                            strong { +LanguageConfig.getString("ui.help.shiftF1f9") }
                            +LanguageConfig.getString("ui.help.shiftF1f9Desc")
                        }
                    }
                }
                
                div("help-section") {
                    h2 { +LanguageConfig.getString("ui.help.gameActions") }
                    
                    h3 { +LanguageConfig.getString("ui.help.hintSystem") }
                    ul {
                        li {
                            strong { +LanguageConfig.getString("ui.help.h") }
                            +LanguageConfig.getString("ui.help.hDesc")
                            ul {
                                li { +LanguageConfig.getString("ui.help.requiresBackend") }
                                li {
                                    +LanguageConfig.getString("ui.help.whenHintsVisible")
                                    ul {
                                        li {
                                            strong { +LanguageConfig.getString("ui.help.arrowUpDown") }
                                            +LanguageConfig.getString("ui.help.arrowUpDownDesc")
                                        }
                                        li {
                                            strong { +LanguageConfig.getString("ui.help.pageUp") }
                                            +LanguageConfig.getString("ui.help.pageUpDesc")
                                        }
                                        li {
                                            strong { +LanguageConfig.getString("ui.help.pageDown") }
                                            +LanguageConfig.getString("ui.help.pageDownDesc")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                }
                
                div("help-section") {
                    h2 { +LanguageConfig.getString("ui.help.screenNavigation") }
                    ul {
                        li {
                            strong { +LanguageConfig.getString("ui.help.m") }
                            +LanguageConfig.getString("ui.help.mDesc")
                        }
                        li {
                            strong { +LanguageConfig.getString("ui.help.b") }
                            +LanguageConfig.getString("ui.help.bDesc")
                        }
                        li {
                            strong { +LanguageConfig.getString("ui.help.i") }
                            +LanguageConfig.getString("ui.help.iDesc")
                        }
                        li {
                            strong { +LanguageConfig.getString("ui.help.escapeScreen") }
                            +LanguageConfig.getString("ui.help.escapeScreenDesc")
                            ul {
                                li { +LanguageConfig.getString("ui.help.closeModals") }
                                li { +LanguageConfig.getString("ui.help.closeHintPanel") }
                                li { +LanguageConfig.getString("ui.help.returnToGame") }
                                li { +LanguageConfig.getString("ui.help.clearSelections") }
                            }
                        }
                    }
                }
                
                div("help-section") {
                    h2 { +"Play Modes" }

                    h3 { +"Placement Mode" }
                    p { +"Select a number from the number bar (it appears bold when selected), then click a cell to place it." }

                    h3 { +"✏️ Pencil / Notes Mode" }
                    ul {
                        li { +"Toggle with the ✏️ button or the N key" }
                        li { +"When active, matching candidates in cells show a slash — clicking a cell removes that candidate" }
                    }

                    h3 { +"✏️✏️ Multi-Select Notes Mode" }
                    ul {
                        li { +"Toggle with the ✏️✏️ button. Select multiple numbers from the number bar to choose which candidates to target." }
                        li {
                            strong { +"Clr Sel:" }
                            +" Remove the selected numbers as candidates from the clicked cell"
                        }
                        li {
                            strong { +"Clr Oth:" }
                            +" Remove all candidates EXCEPT the selected numbers from the clicked cell"
                        }
                    }
                }
                
                div("help-section") {
                    h2 { +LanguageConfig.getString("ui.help.notes") }
                    ol {
                        li { +LanguageConfig.getString("ui.help.shortcutsDisabled") }
                        li { +LanguageConfig.getString("ui.help.hodokuConventions") }
                        li { +LanguageConfig.getString("ui.help.fullyPlayable") }
                        li { +LanguageConfig.getString("ui.help.f1f9Note") }
                    }
                }
                
                div("help-section") {
                    h2 { +LanguageConfig.getString("ui.help.tips") }
                    ul {
                        li {
                            +(LanguageConfig.getString("ui.help.tip1") + " ")
                            strong { +LanguageConfig.getString("ui.help.tip1CtrlArrow") }
                            +(" " + LanguageConfig.getString("ui.help.tip1Desc"))
                        }
                        li {
                            +(LanguageConfig.getString("ui.help.tip2") + " ")
                            strong { +LanguageConfig.getString("ui.help.tip2F1f9") }
                            +(" " + LanguageConfig.getString("ui.help.tip2Desc"))
                        }
                        li {
                            +(LanguageConfig.getString("ui.help.tip3") + " ")
                            strong { +LanguageConfig.getString("ui.help.tip3H") }
                            +(" " + LanguageConfig.getString("ui.help.tip3Desc"))
                        }
                        li {
                            strong { +LanguageConfig.getString("ui.help.tip4") }
                            +(" " + LanguageConfig.getString("ui.help.tip4Desc"))
                        }
                    }
                }
            }
        }
    }
}

