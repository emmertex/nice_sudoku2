import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import org.w3c.dom.Element

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
                
                h1 { +"Help" }
                
                div("help-section") {
                    h2 { +"Welcome" }
                    div("greeting-content") {
                        p {
                            +"Thank you for testing Nice Sudoku."
                        }
                        
                        p {
                            +"In short, I, Andrew, wanted a good Android Sudoku app."
                        }
                        
                        p {
                            +"I am not even good at Sudoku, I crap out after X Wings. So I wanted a way to genuinely learn."
                        }
                        
                        p {
                            +"Well, I tried almost all the apps, and they were all crap, Ad ridden nonsense, or alike."
                        }
                        
                        p {
                            +"I also wanted to learn godot, so wrote a sudoku app. I got all basic solvers working, but then StrmCkr seen my work, and things got hard and complex. After getting a few intermediate solvers working nice, and a world of UI issues, I threw it all away."
                        }
                        
                        p {
                            +"This is the second version, written in Kotlin, with the intent to be native on all platforms. Using not just the knowledge of StrmCkr, but his years of knowledge making solvers as a backend, and a new frontend, hooking into it as an API."
                        }
                        
                        p {
                            +"Currently this means it must be online, but over time I intend to make it all offline."
                        }
                        
                        p {
                            +"I am asking nothing more than community support for me and StrmCkr, and I will endeavour to make this app something I want to use."
                        }
                        
                        p {
                            +"This isn't a first version, it is way too early for that, it is a feedback gathering exercise."
                        }
                        
                        p {
                            +"If you try it, please, offer feedback. The earlier in the development process I get feedback, the better the chance I can make it happen."
                        }
                        
                        p("greeting-signature") {
                            +"Thanks for testing,"
                            br
                            +"Andrew"
                        }
                    }
                }
                
                div("help-section") {
                    h2 { +"Keyboard Shortcuts" }
                }
                
                div("help-section") {
                    h2 { +"Navigation" }
                    
                    h3 { +"Cell Selection" }
                    ul {
                        li {
                            strong { +"Arrow Keys (↑ ← ↓ →)" }
                            +": Move the cursor between cells"
                        }
                        li {
                            strong { +"Ctrl + Arrow Keys" }
                            +": Jump to the next unsolved cell in that direction"
                        }
                        li {
                            strong { +"Home" }
                            +": Move to the first column of the current row"
                        }
                        li {
                            strong { +"End" }
                            +": Move to the last column of the current row"
                        }
                        li {
                            strong { +"Ctrl + Home" }
                            +": Move to the top-left cell (cell 0)"
                        }
                        li {
                            strong { +"Ctrl + End" }
                            +": Move to the bottom-right cell (cell 80)"
                        }
                    }
                }
                
                div("help-section") {
                    h2 { +"Number Entry" }
                    
                    h3 { +"Basic Entry" }
                    ul {
                        li {
                            strong { +"1-9" }
                            +": Enter numbers based on play mode:"
                            ul {
                                li {
                                    strong { +"Fast Mode" }
                                    +": Selects the number for highlighting. If a cell is selected, applies the number to that cell"
                                }
                                li {
                                    strong { +"Advanced Mode" }
                                    +": Toggles the number in the primary selection. Use the two number bars for clicking (primary=blue, secondary=red)"
                                }
                            }
                        }
                    }
                    
                    h3 { +"Candidate Entry (Pencil Marks)" }
                    ul {
                        li {
                            strong { +"Ctrl + 1-9" }
                            +": Toggle pencil mark candidate in the selected cell"
                        }
                        li {
                            strong { +"Space" }
                            +": If a number is selected (filter), toggle its candidate in the selected cell"
                        }
                        li {
                            strong { +"N" }
                            +": Toggle notes/pencil mode on/off"
                        }
                    }
                }
                
                div("help-section") {
                    h2 { +"Editing" }
                    ul {
                        li {
                            strong { +"Undo button (↩)" }
                            +": Undo your last action (placements and candidate eliminations)"
                        }
                        li {
                            strong { +"Escape" }
                            +": Clear all selections (selected numbers and cell)"
                        }
                    }
                }
                
                div("help-section") {
                    h2 { +"Filters and Highlighting" }
                    ul {
                        li {
                            strong { +"F1-F9" }
                            +": Set/change the filtered (selected) digit for highlighting"
                        }
                        li {
                            strong { +"Shift + F1-F9" }
                            +": Set/change the filtered digit (future: toggle filter mode)"
                        }
                    }
                }
                
                div("help-section") {
                    h2 { +"Game Actions" }
                    
                    h3 { +"Hint System" }
                    ul {
                        li {
                            strong { +"H" }
                            +": Toggle hint panel (show/hide available solving techniques)"
                            ul {
                                li { +"Requires backend connection to be available" }
                                li {
                                    +"When hints are visible:"
                                    ul {
                                        li {
                                            strong { +"Arrow Up/Down" }
                                            +": Navigate through available hints"
                                        }
                                        li {
                                            strong { +"Page Up" }
                                            +": Jump to first hint"
                                        }
                                        li {
                                            strong { +"Page Down" }
                                            +": Jump to last hint"
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    h3 { +"Advanced Mode Actions" }
                    ul {
                        li {
                            strong { +"Enter" }
                            +" or "
                            strong { +"S" }
                            +": Set the value in the selected cell (only works when exactly one number is selected in primary)"
                        }
                        li {
                            strong { +"Deselect button" }
                            +": Click to deselect the cell and show number bars again"
                        }
                    }
                }
                
                div("help-section") {
                    h2 { +"Screen Navigation" }
                    ul {
                        li {
                            strong { +"M" }
                            +": Open Menu/Settings screen"
                        }
                        li {
                            strong { +"B" }
                            +": Open Puzzle Browser screen"
                        }
                        li {
                            strong { +"I" }
                            +": Open Import/Export screen"
                        }
                        li {
                            strong { +"Escape" }
                            +":"
                            ul {
                                li { +"Close modals (About, etc.)" }
                                li { +"Close hint panel" }
                                li { +"Return to Game screen from any other screen" }
                                li { +"Clear selections in Game screen" }
                            }
                        }
                    }
                }
                
                div("help-section") {
                    h2 { +"Mode Switching" }
                    ul {
                        li {
                            strong { +"N" }
                            +": Toggle Notes/Pencil mode"
                            ul {
                                li { +"When enabled, number entry adds/removes pencil marks instead of values" }
                            }
                        }
                    }
                }
                
                div("help-section") {
                    h2 { +"Notes" }
                    ol {
                        li { +"All shortcuts are disabled when typing in input fields or text areas to prevent conflicts" }
                        li { +"Keyboard shortcuts follow HoDoKu conventions for consistency with standard Sudoku software" }
                        li { +"The game is fully playable using only keyboard input" }
                        li { +"Some shortcuts may vary slightly in behavior between Fast, Cell First, and Advanced play modes" }
                        li {
                            +"F1-F9 keys: Some browsers use F-keys for developer tools (e.g., F12) or other functions. If a browser shortcut conflicts, you may need to disable the browser's shortcut or use number keys 1-9 instead for filtering"
                        }
                    }
                }
                
                div("help-section") {
                    h2 { +"Play Mode Differences" }
                    
                    h3 { +"Fast Mode" }
                    ul {
                        li { +"Number keys select numbers for highlighting first" }
                        li { +"If a cell is selected, the number is immediately applied" }
                        li { +"Quick, streamlined input for faster solving" }
                        li { +"Single number selection for highlighting" }
                    }
                    
                    h3 { +"Cell First Mode" }
                    ul {
                        li { +"Click a cell first to select it" }
                        li { +"Then click a number to fill that cell" }
                        li { +"Highlighting is based on the selected cell's value" }
                        li { +"Ideal for methodical cell-by-cell solving" }
                    }
                    
                    h3 { +"Advanced Mode" }
                    ul {
                        li { +"Two number bars: primary (blue) and secondary (red)" }
                        li { +"Toggle multiple numbers in each bar - cells must contain ALL selected numbers to highlight" }
                        li { +"When a cell is selected, number bars hide and action buttons appear" }
                        li { +"Use Deselect button to show number bars again" }
                        li { +"Use Set/Clr buttons to modify cells, or Enter/S for single-number selections" }
                        li { +"Click Fast/Cell/Adv badge in header to quickly cycle modes" }
                        li { +"Supports two-number highlighting for complex solving techniques" }
                    }
                }
                
                div("help-section") {
                    h2 { +"Tips" }
                    ul {
                        li {
                            +"Use "
                            strong { +"Ctrl + Arrow Keys" }
                            +" to quickly jump between unsolved cells"
                        }
                        li {
                            +"Use "
                            strong { +"F1-F9" }
                            +" for quick number filtering and highlighting"
                        }
                        li {
                            +"Use "
                            strong { +"H" }
                            +" to access hints and learn new solving techniques"
                        }
                        li {
                            strong { +"Escape" }
                            +" is your universal \"go back\" key - use it to return to the game from any screen or modal"
                        }
                    }
                }
            }
        }
    }
}

