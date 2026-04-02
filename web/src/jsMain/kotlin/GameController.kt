import adapter.GameEngine
import domain.*
import domain.getLocalizedDisplayName
import i18n.LanguageConfig

/**
 * Extension functions for game lifecycle management in SudokuApp.
 */

internal fun SudokuApp.startNewGame(puzzle: PuzzleDefinition) {
    gameEngine.loadPuzzle(puzzle.puzzleString)

    // Use pre-loaded solution from puzzle definition
    solution = puzzle.solution

    // Create new saved game with solution (includes candidate mode-based eliminations)
    currentGame = GameStateManager.createNewGame(puzzle, puzzle.solution)
    
    // Apply user eliminations based on candidate mode
    currentGame?.let { game ->
        val (_, userEliminations) = SavedGameState.parseStateString(game.currentState)
        for (i in 0 until 81) {
            // Apply user eliminations for this cell
            // In AUTO mode: empty sets (show all auto-calculated candidates)
            // In MANUAL mode: all 9 candidates eliminated (blank pencil marks)
            gameEngine.setUserEliminations(i, userEliminations[i])
        }
        
        GameStateManager.saveGame(game)
        GameStateManager.setCurrentGameId(game.puzzleId)
    }

    gameStartTime = currentTimeMillis()
    pausedTime = 0L
    isPaused = false
    selectedCell = null
    completionShownForPuzzle = null  // Reset completion modal tracking for new game
    currentScreen = AppScreen.GAME
    render()

    val puzzleSolution = puzzle.solution
    if (puzzleSolution != null) {
        println("Solution pre-loaded: ${puzzleSolution.take(20)}...")
    } else {
        // Fallback: solve in background for custom puzzles without solutions
        val solverEngine = GameEngine()
        solverEngine.loadPuzzle(puzzle.puzzleString)
        solverEngine.getSolutionString(
            onStatus = { status ->
                showToast("⏳ $status")
            },
            onComplete = { solutionStr ->
                if (solutionStr != null) {
                    solution = solutionStr
                    currentGame = currentGame?.copy(solution = solutionStr)
                    currentGame?.let { GameStateManager.saveGame(it) }
                    showToast("✓ Ready for mistake checking")
                    println("Solution loaded: ${solutionStr.take(20)}...")
                } else {
                    showToast("⚠️ Could not verify solution")
                    println("Failed to get solution for puzzle")
                }
            }
        )
    }
}

internal fun SudokuApp.resumeGame(saved: SavedGameState) {
    // Parse the state - returns user eliminations (1 = eliminated by user)
    val (values, userEliminations) = SavedGameState.parseStateString(saved.currentState)

    // Load into engine - this calculates auto-candidates
    gameEngine.loadPuzzle(saved.puzzleString)

    // Restore action stack from saved game
    gameEngine.setActionStack(saved.actionStack)

    // Apply saved values and user eliminations
    for (i in 0 until 81) {
        val originalValue = saved.puzzleString[i].digitToIntOrNull() ?: 0
        if (values[i] != 0 && values[i] != originalValue) {
            // User-entered value - use setCellValue to properly update engine
            gameEngine.setCellValue(i, values[i])
        }
        if (userEliminations[i].isNotEmpty()) {
            // Set user eliminations for this cell
            gameEngine.setUserEliminations(i, userEliminations[i])
        }
    }

    solution = saved.solution
    val loaded = if (trackPlayTime) saved else saved.copy(elapsedTimeMs = 0L)
    currentGame = loaded
    GameStateManager.setCurrentGameId(loaded.puzzleId)
    if (!trackPlayTime && saved.elapsedTimeMs != 0L) {
        GameStateManager.saveGame(loaded)
    }

    gameStartTime = currentTimeMillis()
    pausedTime = loaded.elapsedTimeMs
    isPaused = false
    selectedCell = null
    // If already completed, mark as shown so we don't re-show modal when resuming
    completionShownForPuzzle = if (loaded.isCompleted) loaded.puzzleId else null
    currentScreen = AppScreen.GAME
    render()

    // If no solution saved, solve in background
    if (saved.solution == null) {
        val solverEngine = GameEngine()
        solverEngine.loadPuzzle(saved.puzzleString)
        solverEngine.getSolutionString(
            onStatus = { status -> showToast("⏳ $status") },
            onComplete = { solutionStr ->
                if (solutionStr != null) {
                    solution = solutionStr
                    currentGame = currentGame?.copy(solution = solutionStr)
                    currentGame?.let { GameStateManager.saveGame(it) }
                    showToast("✓ Ready for mistake checking")
                }
            }
        )
    }
}

/**
 * Import a game from a puzzle string (81, 810, 891 chars, or Sudoku Coach format)
 */
internal fun SudokuApp.importGameFromString(rawInput: String, fromUrl: Boolean = false): Boolean {
    val text = rawInput.trim()
    val isCoachImport = text.startsWith("SCv7_32_")

    // Check if it's Sudoku Coach format
    val importResult = if (isCoachImport) {
        helpers.importExport.SudokuCoachFormat.importFromSudokuCoach(text)
    } else {
        val cleaned = text.trim().replace("\\s+".toRegex(), "")
        if (!PuzzleStringParser.isValidPuzzleString(cleaned)) {
            showToast(if (fromUrl) "Invalid shared game link" else "Invalid puzzle string")
            return false
        }

        // 729 = notes-only (no leading value grid); otherwise normalize '.' in first 81 value chars
        val normalized = if (cleaned.length == 729) cleaned
        else cleaned.take(81).map { if (it == '.') '0' else it }.joinToString("") + cleaned.drop(81)
        val basicImport = SavedGameState.parseImportStateString(normalized)
        // Convert to ImportResult format
        if (basicImport != null) {
            val (values, userEliminations, originalPuzzle) = basicImport
            helpers.importExport.SudokuCoachFormat.ImportResult(
                values = values,
                userEliminations = userEliminations,
                originalPuzzle = originalPuzzle
            )
        } else {
            null
        }
    }
    
    if (importResult == null) {
        showToast(if (fromUrl) "❌ Invalid shared game link" else "❌ Failed to parse import string")
        return false
    }

    val values = importResult.values
    val userEliminations = importResult.userEliminations
    val originalPuzzleStr = importResult.originalPuzzle

    // Start new game with puzzle and apply imported state
    gameEngine.loadPuzzle(originalPuzzleStr)
    var hasStateData = false
    for (i in 0 until 81) {
        val originalValue = originalPuzzleStr[i].digitToIntOrNull() ?: 0
        if (values[i] != 0 && values[i] != originalValue) {
            gameEngine.setCellValue(i, values[i])
            hasStateData = true
        }
        if (userEliminations[i].isNotEmpty()) {
            gameEngine.setUserEliminations(i, userEliminations[i])
            hasStateData = true
        }
    }

    val metadataImportLocks = if (isCoachImport) {
        MetadataImportLocks(
            title = importResult.title.isNotBlank(),
            author = importResult.author.isNotBlank(),
            authorContact = importResult.authorContact.isNotBlank(),
            description = importResult.description.isNotBlank()
        )
    } else {
        MetadataImportLocks()
    }

    // Create initial puzzle definition with metadata from import
    val puzzleId = "custom_${currentTimeMillis()}"
    var puzzle = PuzzleDefinition(
        id = puzzleId,
        puzzleString = originalPuzzleStr,
        difficulty = 0f,
        category = DifficultyCategory.CUSTOM,
        title = importResult.title,
        author = importResult.author,
        authorContact = importResult.authorContact,
        description = importResult.description,
        metadataImportLocks = metadataImportLocks
    )
    
    // Save custom puzzle immediately so it appears in Custom section
    GameStateManager.saveCustomPuzzle(puzzle)

    // Create saved game with elimination format and metadata
    val grid = gameEngine.getCurrentGrid()
    val stateWithEliminations = SavedGameState.createStateString(grid)
    currentGame = SavedGameState(
        puzzleId = puzzle.id,
        puzzleString = originalPuzzleStr,
        currentState = stateWithEliminations,
        solution = null,
        category = DifficultyCategory.CUSTOM,
        difficulty = 0f,
        elapsedTimeMs = if (trackPlayTime) importResult.playTimeMs else 0L,
        mistakeCount = importResult.mistakes,
        hintCount = importResult.hints,
        isCompleted = false,
        lastPlayedTimestamp = currentTimeMillis(),
        title = importResult.title,
        author = importResult.author,
        authorContact = importResult.authorContact,
        description = importResult.description,
        metadataImportLocks = metadataImportLocks
    )
    currentGame?.let {
        GameStateManager.saveGame(it)
        GameStateManager.setCurrentGameId(it.puzzleId)
    }

    // Reset timers/selection and render game screen
    gameStartTime = currentTimeMillis()
    pausedTime = 0L
    isPaused = false
    selectedCell = null
    currentScreen = AppScreen.GAME
    solution = null
    render()

    val successMessage = when {
        fromUrl -> "✓ Shared game loaded!"
        hasStateData -> "✓ Full state imported!"
        else -> "✓ Puzzle loaded and saved to Custom!"
    }
    showToast(successMessage)
    
    // Grade puzzle difficulty in background (async)
    if (isBackendAvailable) {
        gameEngine.gradePuzzle(
            puzzleString = originalPuzzleStr,
            onStatus = { status -> 
                // Silent grading, don't show status to user
            },
            onComplete = { difficulty, solutionStr, techniques ->
                if (difficulty > 0) {
                    // Determine category from difficulty
                    val category = DifficultyCategory.fromDifficulty(difficulty.toFloat())
                    
                    // Update puzzle definition with graded difficulty
                    puzzle = puzzle.copy(
                        difficulty = difficulty.toFloat(),
                        category = category,
                        solution = solutionStr,
                        techniques = techniques
                    )
                    GameStateManager.saveCustomPuzzle(puzzle)
                    
                    // Update current game with graded difficulty
                    currentGame = currentGame?.copy(
                        difficulty = difficulty.toFloat(),
                        category = category,
                        solution = solutionStr
                    )
                    currentGame?.let { game ->
                        GameStateManager.saveGame(game)
                        solution = solutionStr
                    }
                    
                    println("Imported puzzle graded: difficulty=$difficulty, category=${category.getLocalizedDisplayName()}")
                    showToast("✓ Graded as ${category.getLocalizedDisplayName()} (difficulty: $difficulty)")
                }
            }
        )
    }
    
    return true
}

/**
 * Save the current game state to persistent storage.
 * This includes all cell values AND all user eliminations.
 * Must be called after any manual modification to candidates or cell values.
 *
 * User eliminations are stored separately from auto-calculated candidates.
 * This ensures user eliminations are never lost when candidates are recalculated.
 */
/**
 * Reset saved progress to givens, zero timer/mistakes/hints, clear undo stack.
 * If this puzzle is the active game, reload the engine from the reset state.
 */
/**
 * Persist metadata from Puzzle Browser details. Respects import locks per field.
 * Updates custom puzzle entry when present; always updates saved game when present.
 */
internal fun SudokuApp.savePuzzleBrowserMetadata(
    puzzleId: String,
    title: String,
    author: String,
    authorContact: String,
    description: String
) {
    val saved = GameStateManager.loadGame(puzzleId)
    val customPuzzle = GameStateManager.loadCustomPuzzles().find { it.id == puzzleId }
    val locks = saved?.metadataImportLocks ?: customPuzzle?.metadataImportLocks ?: MetadataImportLocks()
    if (customPuzzle != null) {
        val updated = customPuzzle.copy(
            title = if (locks.title) customPuzzle.title else title,
            author = if (locks.author) customPuzzle.author else author,
            authorContact = if (locks.authorContact) customPuzzle.authorContact else authorContact,
            description = if (locks.description) customPuzzle.description else description
        )
        GameStateManager.saveCustomPuzzle(updated)
    }
    if (saved != null) {
        val updatedGame = saved.copy(
            title = if (locks.title) saved.title else title,
            author = if (locks.author) saved.author else author,
            authorContact = if (locks.authorContact) saved.authorContact else authorContact,
            description = if (locks.description) saved.description else description
        )
        GameStateManager.saveGame(updatedGame)
        if (currentGame?.puzzleId == puzzleId) {
            currentGame = updatedGame
        }
    }
    showToast(LanguageConfig.getString("ui.puzzleBrowser.metadataSaved"))
    render()
}

internal fun SudokuApp.resetSavedPuzzleToGivens(puzzleId: String) {
    val saved = GameStateManager.loadGame(puzzleId) ?: return
    val initialState = GameStateManager.initialCurrentStateString(saved.puzzleString)
    val reset = saved.copy(
        currentState = initialState,
        elapsedTimeMs = 0L,
        mistakeCount = 0,
        hintCount = 0,
        isCompleted = false,
        actionStack = emptyList(),
        lastPlayedTimestamp = currentTimeMillis()
    )
    GameStateManager.saveGame(reset)
    if (currentGame?.puzzleId == puzzleId) {
        resumeGame(reset)
        showToast(LanguageConfig.getString("ui.puzzleBrowser.resetDone"))
    } else {
        showToast(LanguageConfig.getString("ui.puzzleBrowser.resetDone"))
        render()
    }
}

internal fun SudokuApp.saveCurrentState(newHint: Boolean = false) {
    val game = currentGame ?: return
    val grid = gameEngine.getCurrentGrid()
    val additionalTimeMs = when {
        !trackPlayTime -> 0L
        isPaused -> (pauseStartTime - gameStartTime).coerceAtLeast(0L)
        else -> currentTimeMillis() - gameStartTime
    }

    val updated = GameStateManager.updateGameState(
        currentGame = game,
        grid = grid,
        actionStack = gameEngine.getActionStack(),
        additionalTimeMs = additionalTimeMs,
        newHint = newHint
    ).let { state ->
        if (!trackPlayTime) state.copy(elapsedTimeMs = 0L) else state
    }
    currentGame = updated
    GameStateManager.saveGame(updated)

    gameStartTime = currentTimeMillis()
    pausedTime = updated.elapsedTimeMs
    if (isPaused) {
        pauseStartTime = gameStartTime
    }
}

/**
 * Call before switching away from the game screen while a puzzle is active.
 * Persists progress and, when play time tracking is on, leaves the game paused so the board is blocked until resume.
 */
internal fun SudokuApp.prepareLeaveGameScreen() {
    if (currentGame == null) return
    saveCurrentState()
    if (trackPlayTime && !isPaused) {
        pauseGame()
    }
}

internal fun SudokuApp.loadNextUncompletedGame(category: DifficultyCategory) {
    val summaries = GameStateManager.getGameSummaries()
    val puzzles = PuzzleLibrary.getPuzzlesForCategory(category)
    
    // Find the first puzzle that has NOT been started (no saved game exists)
    val nextPuzzle = puzzles.firstOrNull { puzzle ->
        val existingGame = summaries.find { it.puzzleId == puzzle.id }
        existingGame == null
    }
    
    if (nextPuzzle != null) {
        startNewGame(nextPuzzle)
    } else {
        // All puzzles in this category have been started
        showToast("All ${category.getLocalizedDisplayName()} puzzles started! 🎉")
        render()
    }
}

