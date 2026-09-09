import kotlin.test.*
import domain.*
import kotlinx.browser.document
import kotlinx.browser.localStorage
import view.parseMarkdownToHtml
import view.formatInlineMarkdown

class ReviewRegressionTest {
    @BeforeTest fun setup() { localStorage.clear(); document.body!!.innerHTML = "<div id='app'></div>" }

    @Test fun pausedTimeIsPersistedExactlyOnce() {
        val app = SudokuApp()
        app.gameEngine.loadPuzzle("0".repeat(81))
        app.currentGame = SavedGameState(puzzleId = "timer-test", puzzleString = "0".repeat(81),
            currentState = "0".repeat(810), solution = null, category = DifficultyCategory.CUSTOM,
            difficulty = 0f, elapsedTimeMs = 0, mistakeCount = 0, isCompleted = false, lastPlayedTimestamp = 0)
        app.accumulatedTime = 15000L
        app.segmentStart = null
        app.isPaused = true
        app.saveCurrentState()
        app.saveCurrentState()
        assertEquals(15000L, GameStateManager.loadGame("timer-test")!!.elapsedTimeMs)
        assertEquals(17000L, run { app.segmentStart = 1000; app.elapsedPlayTime(3000) })
    }

    @Test fun listsAreNestedAndMarkupInsideCodeStaysLiteral() {
        val root = document.createElement("div")
        root.innerHTML = parseMarkdownToHtml("- Parent\n  - Child\n- Sibling\n- - Legacy")
        assertEquals(2, root.querySelectorAll("ul > li > ul").length)
        assertEquals("<code>**literal**</code>", formatInlineMarkdown("`**literal**`"))
        root.innerHTML = formatInlineMarkdown("<img src=x onerror=alert(1)> [bad](javascript:alert(1)) https://example.com")
        assertEquals(0, root.querySelectorAll("img").length)
        assertEquals(1, root.querySelectorAll("a").length)
    }

    @Test fun releaseCardsKeepDatesAndOldReleasesCollapsed() {
        val root = document.createElement("div")
        root.innerHTML = parseMarkdownToHtml("# v1.2.0 - 2026-09-09\n- New\n# v1.1.0 - 2026-09-01\n- Old")
        assertEquals(1, root.querySelectorAll("article.latest-release").length)
        assertEquals(1, root.querySelectorAll("details:not([open])").length)
        assertEquals(2, root.querySelectorAll("time[datetime]").length)
    }

    @Test fun pausedAndModalCoveredBoardsRejectPointerMutations() {
        val app = SudokuApp()
        app.gameEngine.loadPuzzle("0".repeat(81))
        app.selectedCell = 0
        app.gameEngine.recordAction(app.gameEngine.createPlacementAction(1, 2))
        app.gameEngine.setCellValue(1, 2)
        app.isPaused = true
        app.handleUndo()
        assertEquals(2, app.gameEngine.getCurrentGrid().getCell(1).value)
        app.handleNumberClick(1, app.gameEngine.getCurrentGrid())
        assertNull(app.gameEngine.getCurrentGrid().getCell(0).value)
        app.isPaused = false
        app.showButtonHelpModal = true
        app.handleNumberClick(1, app.gameEngine.getCurrentGrid())
        assertNull(app.gameEngine.getCurrentGrid().getCell(0).value)
    }

    @Test fun unreadableSavedGamesAreNotOverwritten() {
        val corrupt = "{not valid JSON"
        localStorage.setItem("nice_sudoku_saved_games", corrupt)
        val state = SavedGameState(puzzleId = "recovery-test", puzzleString = "0".repeat(81),
            currentState = "0".repeat(810), solution = null, difficulty = 0f,
            elapsedTimeMs = 0, mistakeCount = 0, isCompleted = false, lastPlayedTimestamp = 0)
        GameStateManager.saveGame(state)
        assertEquals(corrupt, localStorage.getItem("nice_sudoku_saved_games"))
        assertNotNull(GameStateManager.storageWarning)
        assertTrue(GameStateManager.recoveryData().contains("not valid JSON"))
        localStorage.setItem("nice_sudoku_saved_games", "{}")
        GameStateManager.loadAllGames()
    }
}
