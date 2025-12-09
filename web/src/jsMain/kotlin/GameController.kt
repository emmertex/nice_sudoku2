import adapter.GameEngine
import domain.*

/**
 * Extension functions for game lifecycle management in SudokuApp.
 */

internal fun SudokuApp.startNewGame(puzzle: PuzzleDefinition) {
    gameEngine.loadPuzzle(puzzle.puzzleString)

    // Use pre-loaded solution from puzzle definition
    solution = puzzle.solution

    // Create new saved game with solution
    currentGame = GameStateManager.createNewGame(puzzle, puzzle.solution)
    currentGame?.let {
        GameStateManager.saveGame(it)
        GameStateManager.setCurrentGameId(it.puzzleId)
    }

    gameStartTime = currentTimeMillis()
    pausedTime = 0L
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
    currentGame = saved
    GameStateManager.setCurrentGameId(saved.puzzleId)

    gameStartTime = currentTimeMillis()
    pausedTime = saved.elapsedTimeMs
    selectedCell = null
    // If already completed, mark as shown so we don't re-show modal when resuming
    completionShownForPuzzle = if (saved.isCompleted) saved.puzzleId else null
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

    // Check if it's Sudoku Coach format
    val importResult = if (text.startsWith("SCv7_32_")) {
        helpers.importExport.SudokuCoachFormat.importFromSudokuCoach(text)
    } else {
        if (!PuzzleStringParser.isValidPuzzleString(text)) {
            showToast(if (fromUrl) "Invalid shared game link" else "Invalid puzzle string")
            return false
        }

        // Normalize first 81 chars by converting '.' to '0' for parsing
        val normalized = text.take(81).map { if (it == '.') '0' else it }.joinToString("") + text.drop(81)
        SavedGameState.parseImportStateString(normalized)
    }
    
    if (importResult == null) {
        showToast(if (fromUrl) "❌ Invalid shared game link" else "❌ Failed to parse import string")
        return false
    }

    val (values, userEliminations, originalPuzzleStr) = importResult

    val puzzle = PuzzleDefinition(
        id = "custom_${currentTimeMillis()}",
        puzzleString = originalPuzzleStr,
        difficulty = 0f,
        category = DifficultyCategory.CUSTOM
    )

    // Save to custom puzzles library for reuse
    GameStateManager.saveCustomPuzzle(puzzle)

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

    // Create saved game with elimination format
    val grid = gameEngine.getCurrentGrid()
    val stateWithEliminations = SavedGameState.createStateString(grid)
    currentGame = SavedGameState(
        puzzleId = puzzle.id,
        puzzleString = originalPuzzleStr,
        currentState = stateWithEliminations,
        solution = null,
        category = DifficultyCategory.CUSTOM,
        difficulty = 0f,
        elapsedTimeMs = 0L,
        mistakeCount = 0,
        isCompleted = false,
        lastPlayedTimestamp = currentTimeMillis()
    )
    currentGame?.let {
        GameStateManager.saveGame(it)
        GameStateManager.setCurrentGameId(it.puzzleId)
    }

    // Reset timers/selection and render game screen
    gameStartTime = currentTimeMillis()
    pausedTime = 0L
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
internal fun SudokuApp.saveCurrentState() {
    val game = currentGame ?: return
    val grid = gameEngine.getCurrentGrid()
    val elapsedSinceStart = currentTimeMillis() - gameStartTime

    // updateGameState uses createStateString which saves user eliminations
    val updated = GameStateManager.updateGameState(
        currentGame = game,
        grid = grid,
        actionStack = gameEngine.getActionStack(),
        additionalTimeMs = elapsedSinceStart
    )
    currentGame = updated
    GameStateManager.saveGame(updated)

    // Reset timer
    gameStartTime = currentTimeMillis()
    pausedTime = updated.elapsedTimeMs
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
        showToast("All ${category.displayName} puzzles started! 🎉")
        render()
    }
}

