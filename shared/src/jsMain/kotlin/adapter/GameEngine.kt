package adapter

import domain.SudokuGrid
import domain.SudokuCell
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.w3c.fetch.RequestInit
import kotlin.js.json

/**
 * JavaScript implementation that delegates to StormDoku backend API
 */
actual class GameEngine actual constructor() {
    
    private var grid: SudokuGrid = SudokuGrid.empty()
    private var currentMatches: Map<String, List<TechniqueMatchInfo>> = emptyMap()
    private var selectedTechniqueKey: String? = null
    private var currentPuzzleString: String? = null  // Keep track of original puzzle
    
    // Backend API base URL - configurable via environment or defaults to sudoku.emmertex.com
    private val apiBaseUrl: String = js("window.STORMDOKU_API_URL || 'https://sudoku.emmertex.com'") as String
    
    // Backend availability flag - checked on startup
    var isBackendAvailable: Boolean = false
        private set
    
    // Callback for when hints are ready (async)
    var onHintsReady: (() -> Unit)? = null
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }
    
    /**
     * Check if backend API is available. Call this on app startup.
     */
    fun checkBackendAvailable(onResult: (Boolean) -> Unit) {
        MainScope().launch {
            isBackendAvailable = try {
                val response = apiGet("/health")
                response.contains("ok")
            } catch (e: Exception) {
                println("JS: Backend health check failed: ${e.message}")
                false
            }
            println("JS: Backend available: $isBackendAvailable")
            onResult(isBackendAvailable)
        }
    }
    
    actual fun loadPuzzle(puzzle: String): Boolean {
        // Synchronous local parsing first
        val parsed = SudokuGrid.fromString(puzzle)
        return if (parsed != null) {
            grid = parsed
            currentPuzzleString = puzzle  // Store for technique finding
            currentMatches = emptyMap()
            selectedTechniqueKey = null
            
            // Calculate candidates locally immediately so UI shows correct state
            grid = calculateAllCandidates(grid)
            
            // Also load from backend asynchronously (may have more accurate candidates)
            MainScope().launch {
                try {
                    val response = apiPost("/api/puzzle/load", LoadPuzzleRequest(puzzle))
                    val result = json.decodeFromString<LoadPuzzleResponse>(response)
                    if (result.success && result.grid != null) {
                        grid = gridDtoToSudokuGrid(result.grid)
                        println("JS: Updated puzzle from backend with candidates")
                    }
                } catch (e: Exception) {
                    println("JS: Backend unavailable, using local candidates: ${e.message}")
                }
            }
            true
        } else {
            false
        }
    }
    
    actual fun setCellValue(cellIndex: Int, value: Int?) {
        val cell = grid.getCell(cellIndex)
        if (!cell.isGiven) {
            grid = grid.withCellValue(cellIndex, value)
            // Only remove the placed value from related cells - don't recalculate all candidates
            // This preserves manual eliminations the user has made
            if (value != null) {
                grid = removeCandidateFromPeers(grid, cellIndex, value)
            }
            
            // Sync with backend
            MainScope().launch {
                try {
                    val request = SetCellRequest(
                        grid = sudokuGridToDto(grid),
                        cellIndex = cellIndex,
                        value = value
                    )
                    val response = apiPost("/api/cell/set", request)
                    val result = json.decodeFromString<SetCellResponse>(response)
                    if (result.success && result.grid != null) {
                        grid = gridDtoToSudokuGrid(result.grid)
                    }
                } catch (e: Exception) {
                    println("JS: Backend unavailable for setCellValue: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Toggle a user elimination for a cell.
     * This is what gets called when the user manually toggles a pencil mark.
     * The user elimination is persisted and won't be lost when candidates are recalculated.
     */
    fun toggleCandidate(cellIndex: Int, candidate: Int) {
        val cell = grid.getCell(cellIndex)
        if (!cell.isGiven && !cell.isSolved) {
            grid = grid.toggleUserElimination(cellIndex, candidate)
        }
    }
    
    /**
     * Set user eliminations for a cell (overwrite existing eliminations).
     * Used when restoring saved game state.
     */
    fun setUserEliminations(cellIndex: Int, eliminations: Set<Int>) {
        val cell = grid.getCell(cellIndex)
        if (!cell.isGiven && !cell.isSolved) {
            grid = grid.withCellUserEliminations(cellIndex, eliminations)
        }
    }
    
    /**
     * @deprecated Use setUserEliminations instead for setting eliminations.
     * Set candidates for a cell (overwrite existing candidates)
     */
    fun toggleCandidate(cellIndex: Int, candidates: Set<Int>) {
        val cell = grid.getCell(cellIndex)
        if (!cell.isGiven && !cell.isSolved) {
            // Convert old-style "set shown candidates" to user eliminations
            // The eliminations are: all possible (1-9) minus what should be shown
            val eliminations = (1..9).toSet() - candidates
            grid = grid.withCellUserEliminations(cellIndex, eliminations)
        }
    }
    
    // Technique priority order (lower = simpler/preferred)
    private val techniquePriority = mapOf(
        // Basic strategies (1-6)
        "NAKED_SINGLE" to 1, "Naked Singles" to 1,
        "HIDDEN_SINGLE" to 1, "Hidden Singles" to 1,
        "NAKED_PAIR" to 2, "Naked Pairs" to 2,
        "NAKED_TRIPLE" to 2, "Naked Triples" to 2,
        "HIDDEN_PAIR" to 3, "Hidden Pairs" to 3,
        "HIDDEN_TRIPLE" to 3, "Hidden Triples" to 3,
        "NAKED_QUADRUPLE" to 4, "Naked Quadruples" to 4,
        "HIDDEN_QUADRUPLE" to 4, "Hidden Quadruples" to 4,
        "POINTING_CANDIDATES" to 5, "Pointing Candidates" to 5, "Pointing Pairs" to 5,
        "CLAIMING_CANDIDATES" to 6, "Claiming Candidates" to 6, "Box/Line Reduction" to 6,
        
        // Tough strategies (7-15)
        "BUG" to 7,
        "X_WING_FISH" to 8, "X-Wing" to 8,
        "UNIQUE_RECTANGLE" to 9, "Unique Rectangles" to 9,
        "SIMPLE_COLOURING" to 11, "Simple Colouring" to 11,
        "Y_WING" to 12, "XY-Wing" to 12,
        "EMPTY_RECTANGLE" to 13, "Empty Rectangles" to 13,
        "SWORDFISH_FISH" to 14, "Swordfish" to 14,
        "XYZ_WING" to 15, "XYZ Wing" to 15,
        
        // Diabolical strategies (16-25)
        "X_CYCLES" to 16, "X-Cycles" to 16,
        "XY_CHAIN" to 17, "XY-Chain" to 17,
        "MEDUSA_3D" to 18, "3D Medusa" to 18,
        "JELLYFISH_FISH" to 19, "Jellyfish" to 19,
        "WXYZ_WING" to 24, "WXYZ Wing" to 24,
        
        // Extreme strategies (26-40)
        "GROUPED_X_CYCLES" to 27, "Grouped X-Cycles" to 27,
        "FINNED_X_WING_FISH" to 28, "Finned X-Wing" to 28,
        "FINNED_SWORDFISH_FISH" to 29, "Finned Swordfish" to 29,
        "AIC" to 30, "Alternating Inference Chains" to 30,
        "ALMOST_LOCKED_SETS" to 31, "Almost Locked Sets" to 31,
        "SUE_DE_COQ" to 32, "Sue-de-Coq" to 32,
        "FORCING_CHAINS" to 33, "Forcing Chains" to 33,
        
        // Fish variants
        "SKYSCRAPER_FISH" to 8, "Skyscraper" to 8,
        "TWO_STRING_KITE_FISH" to 8, "2-String Kite" to 8,
        "FRANKEN_X_WING_FISH" to 28, "Franken X-Wing" to 28,
        "SASHIMI_X_WING_FISH" to 28, "Sashimi X-Wing" to 28,
        "FINNED_FRANKEN_X_WING_FISH" to 29,
        "FINNED_MUTANT_X_WING_FISH" to 29,
        "FRANKEN_SWORDFISH_FISH" to 29,
        "FINNED_JELLYFISH_FISH" to 30,
    )
    
    private val MAX_HINTS_TOTAL = 10
    private val MAX_HINTS_PER_TECHNIQUE = 3
    
    actual fun findAllTechniques() {
        findTechniquesWithFallback()
    }
    
    actual fun findBasicTechniques() {
        findTechniquesAsync(basicOnly = true)
    }
    
    /**
     * Try basic techniques first, fall back to all techniques if none found.
     * This ensures simpler techniques are shown before advanced ones.
     */
    private fun findTechniquesWithFallback() {
        MainScope().launch {
            val puzzleStr = getCurrentStateString(grid)
            
            try {
                // First try basic techniques
                val basicMatches = fetchTechniques(puzzleStr, basicOnly = true)
                
                if (basicMatches.isNotEmpty()) {
                    currentMatches = filterAndLimitHints(basicMatches)
                    println("JS: Found ${currentMatches.values.flatten().size} basic technique matches (filtered)")
                    onHintsReady?.invoke()
                } else {
                    // No basic techniques found, try all techniques
                    println("JS: No basic techniques found, searching all techniques...")
                    val allMatches = fetchTechniques(puzzleStr, basicOnly = false)
                    currentMatches = filterAndLimitHints(allMatches)
                    println("JS: Found ${currentMatches.values.flatten().size} advanced technique matches (filtered)")
                    onHintsReady?.invoke()
                }
            } catch (e: Exception) {
                println("JS: Backend unavailable for findTechniques: ${e.message}")
                findLocalBasicTechniques()
                onHintsReady?.invoke()
            }
        }
    }
    
    /**
     * Filter hints: max 3 per technique, max 10 total, ordered by difficulty
     */
    private fun filterAndLimitHints(matches: Map<String, List<TechniqueMatchInfo>>): Map<String, List<TechniqueMatchInfo>> {
        // Flatten all hints with their technique names
        val allHints = matches.flatMap { (techniqueName, hints) ->
            hints.map { hint -> techniqueName to hint }
        }
        
        // Sort by technique priority (simpler first)
        val sortedHints = allHints.sortedBy { (techniqueName, _) ->
            techniquePriority[techniqueName] ?: 100 // Unknown techniques go last
        }
        
        // Limit per technique and total
        val techCounts = mutableMapOf<String, Int>()
        val filteredHints = mutableListOf<Pair<String, TechniqueMatchInfo>>()
        
        for ((techniqueName, hint) in sortedHints) {
            val currentCount = techCounts.getOrElse(techniqueName) { 0 }
            if (currentCount < MAX_HINTS_PER_TECHNIQUE && filteredHints.size < MAX_HINTS_TOTAL) {
                filteredHints.add(techniqueName to hint)
                techCounts[techniqueName] = currentCount + 1
            }
        }
        
        // Group back into map format
        return filteredHints.groupBy({ it.first }, { it.second })
    }
    
    /**
     * Check if a hint is still valid given the current grid state.
     * A hint is invalid if:
     * - Any of its solvedCells are already solved in the grid
     * - Any of its eliminations target candidates that are not present in displayCandidates
     *   (i.e., candidates the user has already manually eliminated)
     */
    private fun isHintValid(hint: TechniqueMatchInfo, grid: SudokuGrid): Boolean {
        // Check if any solved cells in the hint are already solved
        for (solvedCell in hint.solvedCells) {
            val cell = grid.getCell(solvedCell.cell)
            if (cell.isSolved) {
                return false // Cell is already solved
            }
        }
        
        // Check if any eliminations target candidates that are already eliminated
        for (elimination in hint.eliminations) {
            for (cellIndex in elimination.cells) {
                val cell = grid.getCell(cellIndex)
                if (!cell.isSolved && elimination.digit !in cell.displayCandidates) {
                    return false // Candidate already eliminated by user
                }
            }
        }
        
        return true
    }
    
    /**
     * Filter hints to remove those that are no longer valid given the current grid state.
     */
    private fun filterInvalidHints(matches: Map<String, List<TechniqueMatchInfo>>): Map<String, List<TechniqueMatchInfo>> {
        return matches.mapValues { (_, hints) ->
            hints.filter { hint -> isHintValid(hint, grid) }
        }.filterValues { it.isNotEmpty() }
    }
    
    /**
     * Fetch techniques from backend API
     */
    private suspend fun fetchTechniques(puzzleStr: String, basicOnly: Boolean): Map<String, List<TechniqueMatchInfo>> {
        val request = FindTechniquesFromPuzzleRequest(
            puzzle = puzzleStr,
            basicOnly = basicOnly
        )
        val response = apiPost("/api/techniques/find-from-puzzle", request)
        val result = json.decodeFromString<FindTechniquesResponse>(response)
        
        return if (result.success) {
            val allMatches = result.techniques.mapValues { (_, matches) ->
                matches.map { dto ->
                    TechniqueMatchInfo(
                        id = dto.id,
                        techniqueName = dto.techniqueName,
                        description = dto.description,
                        highlightCells = dto.highlightCells,
                        eliminations = dto.eliminations,
                        solvedCells = dto.solvedCells
                    )
                }
            }
            // Filter out hints that are no longer valid due to user eliminations or already-solved cells
            filterInvalidHints(allMatches)
        } else {
            println("JS: Backend technique search failed: ${result.error}")
            emptyMap()
        }
    }
    
    private fun findTechniquesAsync(basicOnly: Boolean) {
        MainScope().launch {
            // Use current grid state (including user-solved cells) for accurate hints
            val puzzleStr = getCurrentStateString(grid)
            
            try {
                val rawMatches = fetchTechniques(puzzleStr, basicOnly)
                currentMatches = filterAndLimitHints(rawMatches)
                println("JS: Found ${currentMatches.values.flatten().size} technique matches from backend (filtered)")
                onHintsReady?.invoke()
            } catch (e: Exception) {
                println("JS: Backend unavailable for findTechniques: ${e.message}")
                findLocalBasicTechniques()
                onHintsReady?.invoke()
            }
        }
    }
    
    private fun gridToPuzzleString(grid: SudokuGrid): String {
        return grid.cells.map { cell ->
            if (cell.isGiven && cell.value != null) cell.value.toString() else "0"
        }.joinToString("")
    }
    
    /**
     * Get current grid state as a puzzle string, including all solved cells (given + user-entered)
     */
    private fun getCurrentStateString(grid: SudokuGrid): String {
        return grid.cells.map { cell ->
            if (cell.isSolved && cell.value != null) cell.value.toString() else "0"
        }.joinToString("")
    }
    
    private fun findLocalBasicTechniques() {
        val matches = mutableMapOf<String, MutableList<TechniqueMatchInfo>>()
        
        // Find naked singles locally using displayCandidates (what user sees)
        for (cellIndex in 0 until 81) {
            val cell = grid.getCell(cellIndex)
            if (!cell.isSolved && cell.displayCandidates.size == 1) {
                val value = cell.displayCandidates.first()
                matches.getOrPut("NAKED_SINGLE") { mutableListOf() }.add(
                    TechniqueMatchInfo(
                        id = "local-ns-$cellIndex",
                        techniqueName = "Naked Single",
                        description = "R${cellIndex/9 + 1}C${cellIndex%9 + 1} = $value",
                        highlightCells = listOf(cellIndex),
                        eliminations = emptyList(),
                        solvedCells = listOf(SolvedCellDto(cellIndex, value))
                    )
                )
            }
        }
        
        currentMatches = matches
        println("JS: Found ${matches.values.flatten().size} local basic technique matches")
    }
    
    actual fun applyBasicTechniques() {
        val firstMatch = currentMatches.values.flatten().firstOrNull()
        if (firstMatch != null) {
            applyTechniqueById(firstMatch.id)
        }
    }
    
    private fun applyTechniqueById(techniqueId: String) {
        MainScope().launch {
            try {
                val request = ApplyTechniqueRequest(
                    grid = sudokuGridToDto(grid),
                    techniqueId = techniqueId
                )
                val response = apiPost("/api/techniques/apply", request)
                val result = json.decodeFromString<ApplyTechniqueResponse>(response)
                
                if (result.success && result.grid != null) {
                    grid = gridDtoToSudokuGrid(result.grid)
                    currentMatches = emptyMap()
                    println("JS: Applied technique from backend")
                } else {
                    println("JS: Failed to apply technique: ${result.error}")
                    // Try local application for basic techniques
                    applyLocalTechnique(techniqueId)
                }
            } catch (e: Exception) {
                println("JS: Backend unavailable for applyTechnique: ${e.message}")
                applyLocalTechnique(techniqueId)
            }
        }
    }
    
    private fun applyLocalTechnique(techniqueId: String) {
        if (techniqueId.startsWith("local-")) {
            val match = currentMatches.values.flatten().find { it.id == techniqueId }
            if (match != null) {
                // Apply solved cells
                for (solved in match.solvedCells) {
                    grid = grid.withCellValue(solved.cell, solved.digit)
                }
                // Apply eliminations as user eliminations
                for (elim in match.eliminations) {
                    for (cell in elim.cells) {
                        val currentCell = grid.getCell(cell)
                        if (!currentCell.isSolved) {
                            // Add to user eliminations instead of modifying auto-candidates
                            val newEliminations = currentCell.userEliminations + elim.digit
                            grid = grid.withCellUserEliminations(cell, newEliminations)
                        }
                    }
                }
                // Recalculate candidates after placing values (but keep user eliminations)
                grid = calculateAllCandidates(grid)
                currentMatches = emptyMap()
            }
        }
    }
    
    actual fun solve() {
        MainScope().launch {
            try {
                val request = SolveRequest(grid = sudokuGridToDto(grid))
                val response = apiPost("/api/puzzle/solve", request)
                val result = json.decodeFromString<SolveResponse>(response)
                
                if (result.success && result.hasSolution && result.grid != null) {
                    grid = gridDtoToSudokuGrid(result.grid)
                    println("JS: Solved puzzle via backend")
                } else {
                    println("JS: Backend solve failed: ${result.error}")
                    // Fall back to local brute force
                    val solution = bruteForceSolve(grid)
                    if (solution != null) {
                        grid = solution
                    }
                }
            } catch (e: Exception) {
                println("JS: Backend unavailable for solve: ${e.message}")
                val solution = bruteForceSolve(grid)
                if (solution != null) {
                    grid = solution
                }
            }
        }
    }
    
    /**
     * Get solution string for mistake detection.
     * Uses callback since we need async API call.
     * @param onStatus Called with status updates ("Solving remotely..." or "Solving locally...")
     * @param onComplete Called with the solution string (or null if failed)
     */
    fun getSolutionString(
        onStatus: ((String) -> Unit)? = null,
        onComplete: (String?) -> Unit
    ) {
        MainScope().launch {
            val solutionStr = getSolutionStringAsync(onStatus)
            onComplete(solutionStr)
        }
    }
    
    private suspend fun getSolutionStringAsync(onStatus: ((String) -> Unit)?): String? {
        val puzzleStr = currentPuzzleString ?: gridToPuzzleString(grid)
        
        // Try backend first using puzzle string (more reliable)
        onStatus?.invoke("Solving remotely...")
        try {
            val request = SolveFromPuzzleRequest(puzzle = puzzleStr)
            val response = apiPost("/api/puzzle/solve-from-puzzle", request)
            val result = json.decodeFromString<SolveFromPuzzleResponse>(response)
            
            if (result.success && result.hasSolution && result.solution != null) {
                return result.solution
            }
        } catch (e: Exception) {
            println("JS: Backend unavailable for getSolution: ${e.message}")
        }
        
        // Fall back to local brute force
        onStatus?.invoke("Solving locally...")
        val solution = bruteForceSolve(grid)
        return if (solution != null) {
            solution.cells.joinToString("") { (it.value ?: 0).toString() }
        } else {
            null
        }
    }
    
    actual fun selectTechnique(technique: String) {
        selectedTechniqueKey = technique
    }
    
    actual fun getCurrentGrid(): SudokuGrid {
        return grid
    }
    
    actual fun getMatches(): Map<String, List<Any>> {
        return currentMatches.mapValues { it.value as List<Any> }
    }
    
    actual fun getSelectedTechnique(): String? {
        return selectedTechniqueKey
    }
    
    actual fun clearMatches() {
        currentMatches = emptyMap()
        selectedTechniqueKey = null
    }
    
    /**
     * Get all current technique matches as a flat list
     */
    fun getHints(): List<TechniqueMatchInfo> {
        return currentMatches.values.flatten()
    }
    
    // ===== HTTP helpers =====
    
    private suspend fun apiGet(endpoint: String): String {
        return suspendCancellableCoroutine { cont ->
            val url = "$apiBaseUrl$endpoint"
            val init = js("{}")
            init.method = "GET"
            
            window.fetch(url, init.unsafeCast<RequestInit>())
                .then { response: org.w3c.fetch.Response ->
                    response.text().then { text: String ->
                        cont.resume(text)
                        Unit
                    }
                    Unit
                }
                .catch { error: Throwable ->
                    cont.resumeWithException(Exception(error.toString()))
                    Unit
                }
        }
    }
    
    private suspend fun apiPost(endpoint: String, body: Any): String {
        val bodyJson = when (body) {
            is LoadPuzzleRequest -> json.encodeToString(body)
            is SetCellRequest -> json.encodeToString(body)
            is SolveRequest -> json.encodeToString(body)
            is SolveFromPuzzleRequest -> json.encodeToString(body)
            is FindTechniquesRequest -> json.encodeToString(body)
            is FindTechniquesFromPuzzleRequest -> json.encodeToString(body)
            is ApplyTechniqueRequest -> json.encodeToString(body)
            else -> throw IllegalArgumentException("Unknown request type")
        }
        
        return suspendCancellableCoroutine { cont ->
            val url = "$apiBaseUrl$endpoint"
            val init = js("{}")
            init.method = "POST"
            init.headers = js("({'Content-Type': 'application/json'})")
            init.body = bodyJson
            
            window.fetch(url, init.unsafeCast<RequestInit>())
                .then { response: org.w3c.fetch.Response ->
                    response.text().then { text: String ->
                        cont.resume(text)
                        Unit
                    }
                    Unit
                }
                .catch { error: Throwable ->
                    cont.resumeWithException(Exception(error.toString()))
                    Unit
                }
        }
    }
    
    // ===== Local fallback helpers =====
    
    private fun calculateAllCandidates(grid: SudokuGrid): SudokuGrid {
        var result = grid
        for (cellIndex in 0 until 81) {
            val cell = result.getCell(cellIndex)
            if (!cell.isSolved) {
                val validCandidates = calculateCandidates(result, cellIndex)
                // Update auto-calculated candidates while preserving user eliminations
                result = result.withCellCandidates(cellIndex, validCandidates)
            }
        }
        return result
    }
    
    /**
     * Remove a candidate from all peer cells (same row, column, box).
     * This preserves manual eliminations the user has made.
     */
    private fun removeCandidateFromPeers(grid: SudokuGrid, cellIndex: Int, value: Int): SudokuGrid {
        val row = cellIndex / 9
        val col = cellIndex % 9
        val boxStartRow = (row / 3) * 3
        val boxStartCol = (col / 3) * 3
        
        var result = grid
        
        // Remove from row
        for (c in 0 until 9) {
            val peerIndex = row * 9 + c
            if (peerIndex != cellIndex) {
                val peerCell = result.getCell(peerIndex)
                if (!peerCell.isSolved && value in peerCell.candidates) {
                    result = result.withCellCandidates(peerIndex, peerCell.candidates - value)
                }
            }
        }
        
        // Remove from column
        for (r in 0 until 9) {
            val peerIndex = r * 9 + col
            if (peerIndex != cellIndex) {
                val peerCell = result.getCell(peerIndex)
                if (!peerCell.isSolved && value in peerCell.candidates) {
                    result = result.withCellCandidates(peerIndex, peerCell.candidates - value)
                }
            }
        }
        
        // Remove from box
        for (r in boxStartRow until boxStartRow + 3) {
            for (c in boxStartCol until boxStartCol + 3) {
                val peerIndex = r * 9 + c
                if (peerIndex != cellIndex) {
                    val peerCell = result.getCell(peerIndex)
                    if (!peerCell.isSolved && value in peerCell.candidates) {
                        result = result.withCellCandidates(peerIndex, peerCell.candidates - value)
                    }
                }
            }
        }
        
        return result
    }
    
    private fun calculateCandidates(grid: SudokuGrid, cellIndex: Int): Set<Int> {
        val row = cellIndex / 9
        val col = cellIndex % 9
        val box = (row / 3) * 3 + (col / 3)
        
        val usedValues = mutableSetOf<Int>()
        
        // Check row
        for (c in 0 until 9) {
            grid.getCell(row * 9 + c).value?.let { usedValues.add(it) }
        }
        
        // Check column
        for (r in 0 until 9) {
            grid.getCell(r * 9 + col).value?.let { usedValues.add(it) }
        }
        
        // Check box
        val boxStartRow = (box / 3) * 3
        val boxStartCol = (box % 3) * 3
        for (r in boxStartRow until boxStartRow + 3) {
            for (c in boxStartCol until boxStartCol + 3) {
                grid.getCell(r * 9 + c).value?.let { usedValues.add(it) }
            }
        }
        
        return (1..9).filter { it !in usedValues }.toSet()
    }
    
    private fun bruteForceSolve(grid: SudokuGrid): SudokuGrid? {
        val emptyCell = (0 until 81).find { !grid.getCell(it).isSolved }
            ?: return grid
        
        val candidates = grid.getCell(emptyCell).candidates
        for (value in candidates) {
            val newGrid = grid.withCellValue(emptyCell, value)
            val withCandidates = calculateAllCandidates(newGrid)
            
            val isValid = (0 until 81).all { cellIndex ->
                val cell = withCandidates.getCell(cellIndex)
                cell.isSolved || cell.candidates.isNotEmpty()
            }
            
            if (isValid) {
                val solution = bruteForceSolve(withCandidates)
                if (solution != null) return solution
            }
        }
        
        return null
    }
    
    // ===== DTO conversion =====
    
    private fun sudokuGridToDto(grid: SudokuGrid): GridDto {
        return GridDto(
            cells = grid.cells.map { cell ->
                CellDto(
                    index = cell.index,
                    value = cell.value,
                    // Use displayCandidates (candidates - userEliminations) for API calls
                    // This ensures the API sees what the user sees
                    candidates = cell.displayCandidates,
                    isGiven = cell.isGiven
                )
            },
            isComplete = grid.isComplete,
            isValid = grid.isValid
        )
    }
    
    private fun gridDtoToSudokuGrid(dto: GridDto): SudokuGrid {
        val cells = dto.cells.map { cellDto ->
            // Preserve user eliminations from current grid when updating from API
            val existingCell = if (cellDto.index < grid.cells.size) grid.getCell(cellDto.index) else null
            val userEliminations = existingCell?.userEliminations ?: emptySet()
            
            if (cellDto.value != null) {
                SudokuCell.solved(cellDto.index, cellDto.value, cellDto.isGiven)
            } else {
                SudokuCell(
                    index = cellDto.index,
                    value = null,
                    candidates = cellDto.candidates,
                    userEliminations = userEliminations,  // Preserve user eliminations
                    isGiven = false
                )
            }
        }
        return SudokuGrid(cells)
    }
}

// ===== API DTOs (matching backend) =====

@Serializable
data class CellDto(
    val index: Int,
    val value: Int? = null,
    val candidates: Set<Int> = emptySet(),
    val isGiven: Boolean = false
)

@Serializable
data class GridDto(
    val cells: List<CellDto>,
    val isComplete: Boolean = false,
    val isValid: Boolean = true
)

@Serializable
data class LoadPuzzleRequest(val puzzle: String)

@Serializable
data class LoadPuzzleResponse(
    val success: Boolean,
    val grid: GridDto? = null,
    val error: String? = null
)

@Serializable
data class SetCellRequest(
    val grid: GridDto,
    val cellIndex: Int,
    val value: Int?
)

@Serializable
data class SetCellResponse(
    val success: Boolean,
    val grid: GridDto? = null,
    val error: String? = null
)

@Serializable
data class SolveRequest(val grid: GridDto)

@Serializable
data class SolveResponse(
    val success: Boolean,
    val grid: GridDto? = null,
    val hasSolution: Boolean = false,
    val error: String? = null
)

@Serializable
data class SolveFromPuzzleRequest(val puzzle: String)

@Serializable
data class SolveFromPuzzleResponse(
    val success: Boolean,
    val solution: String? = null,
    val hasSolution: Boolean = false,
    val error: String? = null
)

@Serializable
data class EliminationDto(
    val digit: Int,
    val cells: List<Int>
)

@Serializable
data class SolvedCellDto(
    val cell: Int,
    val digit: Int
)

@Serializable
data class TechniqueMatchDto(
    val id: String,
    val techniqueName: String,
    val description: String,
    val eliminations: List<EliminationDto> = emptyList(),
    val solvedCells: List<SolvedCellDto> = emptyList(),
    val highlightCells: List<Int> = emptyList()
)

@Serializable
data class FindTechniquesRequest(
    val grid: GridDto,
    val basicOnly: Boolean = false
)

@Serializable
data class FindTechniquesFromPuzzleRequest(
    val puzzle: String,
    val basicOnly: Boolean = false
)

@Serializable
data class FindTechniquesResponse(
    val success: Boolean,
    val techniques: Map<String, List<TechniqueMatchDto>> = emptyMap(),
    val totalMatches: Int = 0,
    val error: String? = null
)

@Serializable
data class ApplyTechniqueRequest(
    val grid: GridDto,
    val techniqueId: String
)

@Serializable
data class ApplyTechniqueResponse(
    val success: Boolean,
    val grid: GridDto? = null,
    val error: String? = null
)

// Local technique match info
data class TechniqueMatchInfo(
    val id: String,
    val techniqueName: String,
    val description: String,
    val highlightCells: List<Int>,
    val eliminations: List<EliminationDto>,
    val solvedCells: List<SolvedCellDto>
) {
    override fun toString() = "$techniqueName: $description"
}
