package service

import dto.*
import sudoku.DataStorage.BasicGrid
import sudoku.DataStorage.SBRCGrid
import sudoku.read.SudokuGridParser
import sudoku.Solvingtech.FindBasics
import sudoku.Solvingtech.FindAll
import sudoku.Solvingtech.BruteForceSolver
import sudoku.HelpingTools.cardinals
import sudoku.match.TechniqueMatch
import sudoku.match.AICMatch
import sudoku.match.ALSMatch
import sudoku.match.SubsetMatch
import sudoku.match.FishMatch
import sudoku.solvingtechClassifier.Technique
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

/**
 * Service layer that wraps StormDoku functionality
 */
class SudokuService {
    
    // Store technique matches by ID for later application
    private val matchCache = ConcurrentHashMap<String, CachedMatch>()
    
    private data class CachedMatch(
        val match: TechniqueMatch,
        val technique: Technique,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    // Technique priority mapping - ordered by human difficulty (lower = easier)
    private val techniquePriority = mapOf(
        // BEGINNER (1-4): Singles + Intersection
        "NAKED_SINGLE" to 1, "Naked Singles" to 1,
        "HIDDEN_SINGLE" to 2, "Hidden Singles" to 2,
        "POINTING_CANDIDATES" to 3, "Pointing Candidates" to 3, "Pointing Pairs" to 3,
        "CLAIMING_CANDIDATES" to 4, "Claiming Candidates" to 4, "Box/Line Reduction" to 4,
        // EASY (5-7): Basic subsets
        "NAKED_PAIR" to 5, "Naked Pairs" to 5,
        "NAKED_TRIPLE" to 6, "Naked Triples" to 6,
        "HIDDEN_PAIR" to 7, "Hidden Pairs" to 7,
        // MEDIUM (8-10): Harder subsets
        "HIDDEN_TRIPLE" to 8, "Hidden Triples" to 8,
        "NAKED_QUADRUPLE" to 9, "Naked Quadruples" to 9,
        "HIDDEN_QUADRUPLE" to 10, "Hidden Quadruples" to 10,
        // TOUGH (11-15): Fish & single-digit patterns
        "X_WING_FISH" to 11, "X-Wing" to 11,
        "SKYSCRAPER_FISH" to 12, "Skyscraper" to 12,
        "TWO_STRING_KITE_FISH" to 13, "2-String Kite" to 13,
        "FINNED_X_WING_FISH" to 14, "Finned X-Wing" to 14,
        "SASHIMI_X_WING_FISH" to 15, "Sashimi X-Wing" to 15,
        // HARD (16-22): Coloring, uniqueness, wings, swordfish
        "SIMPLE_COLOURING" to 16, "Simple Colouring" to 16, "Simple Coloring" to 16,
        "UNIQUE_RECTANGLE" to 17, "Unique Rectangles" to 17,
        "BUG" to 18, "Bivalue Universal Grave" to 18,
        "Y_WING" to 19, "XY-Wing" to 19, "XY_WING" to 19,
        "EMPTY_RECTANGLE" to 20, "Empty Rectangles" to 20, "Empty Rectangle" to 20,
        "SWORDFISH_FISH" to 21, "Swordfish" to 21,
        "FINNED_SWORDFISH_FISH" to 22, "Finned Swordfish" to 22,
        // EXPERT (23-28): Advanced wings, chains, 3D Medusa
        "XYZ_WING" to 23, "XYZ Wing" to 23, "XYZ-Wing" to 23,
        "X_CYCLES" to 24, "X-Cycles" to 24, "X-Cycle" to 24,
        "XY_CHAIN" to 25, "XY-Chain" to 25,
        "WXYZ_WING" to 26, "WXYZ Wing" to 26, "WXYZ-Wing" to 26,
        "JELLYFISH_FISH" to 27, "Jellyfish" to 27,
        "MEDUSA_3D" to 28, "3D Medusa" to 28, "THREE_D_MEDUSA" to 28,
        // EXTREME (29-34): Franken/mutant fish, grouped techniques
        "GROUPED_X_CYCLES" to 29, "Grouped X-Cycles" to 29,
        "FRANKEN_X_WING_FISH" to 30, "Franken X-Wing" to 30,
        "FINNED_FRANKEN_X_WING_FISH" to 31,
        "FINNED_MUTANT_X_WING_FISH" to 32,
        "FRANKEN_SWORDFISH_FISH" to 33,
        "FINNED_JELLYFISH_FISH" to 34,
        // DIABOLICAL (35-38): AIC, ALS, Sue-de-Coq, Forcing Chains
        "AIC" to 35, "Alternating Inference Chains" to 35,
        "ALMOST_LOCKED_SETS" to 36, "Almost Locked Sets" to 36,
        "SUE_DE_COQ" to 37, "Sue-de-Coq" to 37,
        "FORCING_CHAINS" to 38, "Forcing Chains" to 38,
        "NISHIO" to 39, "Nishio" to 39,
    )
    
    private fun getTechniquePriority(techniqueName: String): Int {
        return techniquePriority[techniqueName] 
            ?: techniquePriority[techniqueName.uppercase()]
            ?: techniquePriority[techniqueName.replace("_", " ")]
            ?: 100 // Unknown techniques get high priority (harder)
    }
    
    /**
     * Find the easiest applicable technique (optimized for hints)
     * Uses tiered search: tries basics first, only falls back to advanced if needed
     */
    fun findHint(puzzleString: String): HintResponse {
        val startTime = System.currentTimeMillis()
        
        return try {
            val basicGrid = SudokuGridParser.readPuzzleString(puzzleString)
            val sbrcGrid = SBRCGrid(basicGrid)
            
            // Clear old cache entries
            val cutoff = System.currentTimeMillis() - 5 * 60 * 1000
            matchCache.entries.removeIf { it.value.timestamp < cutoff }
            
            // TIER 1: Try basic techniques first (very fast)
            var matches = FindBasics.invoke(sbrcGrid, false)
            var foundInBasics = true
            
            // TIER 2: Only if no basics found, try all techniques
            if (matches.isEmpty() || matches.values.all { it.isEmpty() }) {
                matches = FindAll.invoke(sbrcGrid)
                foundInBasics = false
            }
            
            // Find the EASIEST technique (lowest priority)
            var bestMatch: TechniqueMatch? = null
            var bestTechnique: Technique? = null
            var bestPriority = Int.MAX_VALUE
            
            for ((technique, techniqueMatches) in matches) {
                if (techniqueMatches.isEmpty()) continue
                
                val priority = getTechniquePriority(technique.name)
                if (priority < bestPriority) {
                    bestPriority = priority
                    bestMatch = techniqueMatches.first()
                    bestTechnique = technique
                }
            }
            
            val elapsed = System.currentTimeMillis() - startTime
            
            if (bestMatch == null || bestTechnique == null) {
                return HintResponse(
                    success = true,
                    hint = null,
                    difficulty = 0,
                    searchTimeMs = elapsed,
                    error = "No techniques found - puzzle may need brute force"
                )
            }
            
            // Cache the match for later application
            val matchId = UUID.randomUUID().toString()
            matchCache[matchId] = CachedMatch(bestMatch, bestTechnique)
            
            val hintDto = techniqueMatchToDto(matchId, bestTechnique, bestMatch)
            
            HintResponse(
                success = true,
                hint = hintDto,
                difficulty = bestPriority,
                searchTimeMs = elapsed
            )
        } catch (e: Exception) {
            e.printStackTrace()
            HintResponse(
                success = false,
                error = "Failed to find hint: ${e.message}",
                searchTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * Grade a puzzle by solving it step-by-step and tracking all techniques used
     * Returns technique counts sorted by difficulty
     */
    fun gradePuzzle(puzzleString: String): GradePuzzleResponse {
        val startTime = System.currentTimeMillis()
        val techniqueCounts = mutableMapOf<String, Int>()
        var maxDifficulty = 0
        var totalSteps = 0
        val maxIterations = 500  // Safety limit
        
        return try {
            var basicGrid = SudokuGridParser.readPuzzleString(puzzleString)
            
            while (totalSteps < maxIterations) {
                // Check if solved
                var isSolved = true
                for (cell in 0 until cardinals.Length) {
                    if (!basicGrid.getSolved(cell).isPresent) {
                        isSolved = false
                        break
                    }
                }
                
                if (isSolved) {
                    val elapsed = System.currentTimeMillis() - startTime
                    return GradePuzzleResponse(
                        success = true,
                        solved = true,
                        techniques = techniqueCounts.map { (name, count) ->
                            TechniqueCount(name, count, getTechniquePriority(name))
                        }.sortedBy { it.priority },
                        maxDifficulty = maxDifficulty,
                        totalSteps = totalSteps,
                        searchTimeMs = elapsed
                    )
                }
                
                // Find easiest technique
                val sbrcGrid = SBRCGrid(basicGrid)
                
                // Try basics first
                var matches = FindBasics.invoke(sbrcGrid, false)
                
                // Fall back to all techniques if no basics
                if (matches.isEmpty() || matches.values.all { it.isEmpty() }) {
                    matches = FindAll.invoke(sbrcGrid)
                }
                
                // Find easiest available technique
                var bestMatch: TechniqueMatch? = null
                var bestTechniqueName: String? = null
                var bestPriority = Int.MAX_VALUE
                
                for ((technique, techniqueMatches) in matches) {
                    if (techniqueMatches.isEmpty()) continue
                    
                    val priority = getTechniquePriority(technique.name)
                    if (priority < bestPriority) {
                        bestPriority = priority
                        bestMatch = techniqueMatches.first()
                        bestTechniqueName = technique.name
                    }
                }
                
                if (bestMatch == null || bestTechniqueName == null) {
                    // No technique found - puzzle may need brute force
                    val elapsed = System.currentTimeMillis() - startTime
                    return GradePuzzleResponse(
                        success = true,
                        solved = false,
                        techniques = techniqueCounts.map { (name, count) ->
                            TechniqueCount(name, count, getTechniquePriority(name))
                        }.sortedBy { it.priority },
                        maxDifficulty = maxDifficulty,
                        totalSteps = totalSteps,
                        searchTimeMs = elapsed,
                        error = "Could not solve completely - may need brute force"
                    )
                }
                
                // Track this technique
                techniqueCounts[bestTechniqueName] = (techniqueCounts[bestTechniqueName] ?: 0) + 1
                maxDifficulty = maxOf(maxDifficulty, bestPriority)
                totalSteps++
                
                // Apply the technique
                for ((digit, cells) in bestMatch.eliminations) {
                    var cell = cells.nextSetBit(0)
                    while (cell >= 0) {
                        basicGrid.clearCandidate(cell, digit)
                        cell = cells.nextSetBit(cell + 1)
                    }
                }
                
                for ((cell, digit) in bestMatch.solvedCells) {
                    basicGrid.setSolved(cell, digit, false)
                }
                
                basicGrid.cleanUpCandidates()
            }
            
            // Hit iteration limit
            val elapsed = System.currentTimeMillis() - startTime
            GradePuzzleResponse(
                success = true,
                solved = false,
                techniques = techniqueCounts.map { (name, count) ->
                    TechniqueCount(name, count, getTechniquePriority(name))
                }.sortedBy { it.priority },
                maxDifficulty = maxDifficulty,
                totalSteps = totalSteps,
                searchTimeMs = elapsed,
                error = "Hit iteration limit"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            GradePuzzleResponse(
                success = false,
                error = "Failed to grade puzzle: ${e.message}",
                searchTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * Load a puzzle from string representation
     */
    fun loadPuzzle(puzzleString: String): LoadPuzzleResponse {
        return try {
            val basicGrid = SudokuGridParser.readPuzzleString(puzzleString)
            val gridDto = basicGridToDto(basicGrid)
            LoadPuzzleResponse(success = true, grid = gridDto)
        } catch (e: Exception) {
            LoadPuzzleResponse(success = false, error = "Failed to parse puzzle: ${e.message}")
        }
    }
    
    /**
     * Set a cell value and recalculate candidates
     */
    fun setCell(request: SetCellRequest): SetCellResponse {
        return try {
            val basicGrid = dtoToBasicGrid(request.grid)
            
            if (request.value != null && request.value in 1..9) {
                // Set as solved (not given)
                basicGrid.setSolved(request.cellIndex, request.value - 1, false)
            } else {
                // Clear the cell - create a new grid with this cell reset
                val newGrid = BasicGrid()
                for (cell in 0 until cardinals.Length) {
                    if (cell == request.cellIndex) {
                        // Leave this cell with all candidates (will be cleaned up)
                    } else {
                        val solved = basicGrid.getSolved(cell)
                        if (solved.isPresent) {
                            newGrid.setSolved(cell, solved.asInt, basicGrid.isGiven(cell))
                        } else {
                            // Copy candidates
                            for (d in 0 until 9) {
                                if (!basicGrid.hasCandidate(cell, d)) {
                                    newGrid.clearCandidate(cell, d)
                                }
                            }
                        }
                    }
                }
                newGrid.cleanUpCandidates()
                return SetCellResponse(success = true, grid = basicGridToDto(newGrid))
            }
            
            basicGrid.cleanUpCandidates()
            SetCellResponse(success = true, grid = basicGridToDto(basicGrid))
        } catch (e: Exception) {
            SetCellResponse(success = false, error = "Failed to set cell: ${e.message}")
        }
    }
    
    /**
     * Brute-force solve the puzzle from grid DTO
     */
    fun solve(request: SolveRequest): SolveResponse {
        return try {
            val basicGrid = dtoToBasicGrid(request.grid)
            val solved = IntArray(cardinals.Length)
            val numSolutions = BruteForceSolver.solve(basicGrid, solved, 1)
            
            if (numSolutions >= 1) {
                val solvedGrid = BasicGrid.fromDigits(solved)
                SolveResponse(
                    success = true,
                    grid = basicGridToDto(solvedGrid),
                    hasSolution = true
                )
            } else {
                SolveResponse(
                    success = true,
                    grid = null,
                    hasSolution = false,
                    error = "No solution found"
                )
            }
        } catch (e: Exception) {
            SolveResponse(success = false, error = "Failed to solve: ${e.message}")
        }
    }
    
    /**
     * Brute-force solve from puzzle string (more reliable)
     */
    fun solveFromPuzzle(puzzleString: String): SolveFromPuzzleResponse {
        return try {
            val basicGrid = SudokuGridParser.readPuzzleString(puzzleString)
            val solved = IntArray(cardinals.Length)
            val numSolutions = BruteForceSolver.solve(basicGrid, solved, 1)
            
            if (numSolutions >= 1) {
                // Convert to solution string (1-9 values)
                val solutionStr = solved.joinToString("") { (it + 1).toString() }
                SolveFromPuzzleResponse(
                    success = true,
                    solution = solutionStr,
                    hasSolution = true
                )
            } else {
                SolveFromPuzzleResponse(
                    success = true,
                    solution = null,
                    hasSolution = false,
                    error = "No solution found"
                )
            }
        } catch (e: Exception) {
            SolveFromPuzzleResponse(success = false, error = "Failed to solve: ${e.message}")
        }
    }
    
    /**
     * Find all applicable techniques from a puzzle string (simpler, more reliable)
     */
    fun findTechniquesFromPuzzle(puzzleString: String, basicOnly: Boolean): FindTechniquesResponse {
        return try {
            val basicGrid = SudokuGridParser.readPuzzleString(puzzleString)
            val sbrcGrid = SBRCGrid(basicGrid)
            
            // Clear old cache entries (older than 5 minutes)
            val cutoff = System.currentTimeMillis() - 5 * 60 * 1000
            matchCache.entries.removeIf { it.value.timestamp < cutoff }
            
            val matches: Map<Technique, List<TechniqueMatch>> = if (basicOnly) {
                FindBasics.invoke(sbrcGrid, false)
            } else {
                FindAll.invoke(sbrcGrid)
            }
            
            // Sort techniques by priority (simpler first) to ensure easier techniques
            // are returned before harder ones when limits are applied
            val sortedTechniques = matches.entries.sortedBy { (technique, _) ->
                getTechniquePriority(technique.name)
            }
            
            val techniquesDto = mutableMapOf<String, List<TechniqueMatchDto>>()
            var totalMatches = 0
            
            // Limit matches per technique to prevent massive responses
            val maxMatchesPerTechnique = 5
            val maxTotalMatches = 50
            
            for ((technique, techniqueMatches) in sortedTechniques) {
                if (totalMatches >= maxTotalMatches) break
                if (techniqueMatches.isEmpty()) continue
                
                val limitedMatches = techniqueMatches.take(maxMatchesPerTechnique)
                val matchDtos = limitedMatches.map { match ->
                    val matchId = UUID.randomUUID().toString()
                    matchCache[matchId] = CachedMatch(match, technique)
                    techniqueMatchToDto(matchId, technique, match)
                }
                if (matchDtos.isNotEmpty()) {
                    techniquesDto[technique.name] = matchDtos
                    totalMatches += matchDtos.size
                }
            }
            
            FindTechniquesResponse(
                success = true,
                techniques = techniquesDto,
                totalMatches = totalMatches
            )
        } catch (e: Exception) {
            e.printStackTrace()
            FindTechniquesResponse(success = false, error = "Failed to find techniques: ${e.message}")
        }
    }
    
    /**
     * Find all applicable techniques from grid DTO
     */
    fun findTechniques(request: FindTechniquesRequest): FindTechniquesResponse {
        return try {
            val basicGrid = dtoToBasicGrid(request.grid)
            val sbrcGrid = SBRCGrid(basicGrid)
            
            // Clear old cache entries (older than 5 minutes)
            val cutoff = System.currentTimeMillis() - 5 * 60 * 1000
            matchCache.entries.removeIf { it.value.timestamp < cutoff }
            
            val matches: Map<Technique, List<TechniqueMatch>> = if (request.basicOnly) {
                FindBasics.invoke(sbrcGrid, false)
            } else {
                FindAll.invoke(sbrcGrid)
            }
            
            // Sort techniques by priority (simpler first) to ensure easier techniques
            // are returned before harder ones when limits are applied
            val sortedTechniques = matches.entries.sortedBy { (technique, _) ->
                getTechniquePriority(technique.name)
            }
            
            val techniquesDto = mutableMapOf<String, List<TechniqueMatchDto>>()
            var totalMatches = 0
            
            // Limit matches per technique to prevent massive responses
            val maxMatchesPerTechnique = 5
            val maxTotalMatches = 50
            
            for ((technique, techniqueMatches) in sortedTechniques) {
                if (totalMatches >= maxTotalMatches) break
                if (techniqueMatches.isEmpty()) continue
                
                val limitedMatches = techniqueMatches.take(maxMatchesPerTechnique)
                val matchDtos = limitedMatches.map { match ->
                    val matchId = UUID.randomUUID().toString()
                    // Cache the match for later application
                    matchCache[matchId] = CachedMatch(match, technique)
                    
                    techniqueMatchToDto(matchId, technique, match)
                }
                if (matchDtos.isNotEmpty()) {
                    techniquesDto[technique.name] = matchDtos
                    totalMatches += matchDtos.size
                }
            }
            
            FindTechniquesResponse(
                success = true,
                techniques = techniquesDto,
                totalMatches = totalMatches
            )
        } catch (e: Exception) {
            e.printStackTrace()
            FindTechniquesResponse(success = false, error = "Failed to find techniques: ${e.message}")
        }
    }
    
    /**
     * Apply a specific technique match
     */
    fun applyTechnique(request: ApplyTechniqueRequest): ApplyTechniqueResponse {
        return try {
            val cached = matchCache[request.techniqueId]
                ?: return ApplyTechniqueResponse(success = false, error = "Technique match not found or expired")
            
            val basicGrid = dtoToBasicGrid(request.grid)
            val match = cached.match
            
            // Apply eliminations
            for ((digit, cells) in match.eliminations) {
                var cell = cells.nextSetBit(0)
                while (cell >= 0) {
                    basicGrid.clearCandidate(cell, digit)
                    cell = cells.nextSetBit(cell + 1)
                }
            }
            
            // Apply solved cells
            for ((cell, digit) in match.solvedCells) {
                basicGrid.setSolved(cell, digit, false)
            }
            
            basicGrid.cleanUpCandidates()
            
            // Remove from cache after use
            matchCache.remove(request.techniqueId)
            
            ApplyTechniqueResponse(success = true, grid = basicGridToDto(basicGrid))
        } catch (e: Exception) {
            ApplyTechniqueResponse(success = false, error = "Failed to apply technique: ${e.message}")
        }
    }
    
    // === Conversion helpers ===
    
    private fun basicGridToDto(basicGrid: BasicGrid): GridDto {
        val cells = (0 until cardinals.Length).map { cellIndex ->
            val solved = basicGrid.getSolved(cellIndex)
            if (solved.isPresent) {
                CellDto(
                    index = cellIndex,
                    value = solved.asInt + 1, // StormDoku uses 0-8, we use 1-9
                    candidates = emptySet(),
                    isGiven = basicGrid.isGiven(cellIndex)
                )
            } else {
                val candidates = (0 until 9)
                    .filter { basicGrid.hasCandidate(cellIndex, it) }
                    .map { it + 1 }
                    .toSet()
                CellDto(
                    index = cellIndex,
                    value = null,
                    candidates = candidates,
                    isGiven = false
                )
            }
        }
        
        return GridDto(
            cells = cells,
            isComplete = cells.all { it.value != null },
            isValid = true // Could add validation
        )
    }
    
    private fun dtoToBasicGrid(gridDto: GridDto): BasicGrid {
        // BasicGrid starts with all candidates for all cells
        val basicGrid = BasicGrid()

        // Create a map of cell index to cell data for quick lookup
        val cellMap = gridDto.cells.associateBy { it.index }

        // Set solved cells
        for (cellIndex in 0 until cardinals.Length) {
            val cellDto = cellMap[cellIndex]
            if (cellDto?.value != null) {
                basicGrid.setSolved(cellIndex, cellDto.value - 1, cellDto.isGiven)
            }
        }

        // Clean up candidates based on solved cells (standard Sudoku rules)
        basicGrid.cleanUpCandidates()
        
        // IMPORTANT: Also apply any additional candidate eliminations from the GridDto
        // This preserves technique eliminations (e.g., X-Wing eliminations)
        for (cellIndex in 0 until cardinals.Length) {
            val cellDto = cellMap[cellIndex]
            if (cellDto != null && cellDto.value == null && cellDto.candidates.isNotEmpty()) {
                // Cell is unsolved but has explicit candidates - apply them
                for (digit in 1..9) {
                    val digitIndex = digit - 1
                    // If the candidate is NOT in the GridDto's candidate set, remove it
                    if (digit !in cellDto.candidates && basicGrid.hasCandidate(cellIndex, digitIndex)) {
                        basicGrid.clearCandidate(cellIndex, digitIndex)
                    }
                }
            }
        }
        
        return basicGrid
    }

    // === Visual Data Extraction Helpers ===

    /**
     * Convert a BitSet to a List of integers
     */
    private fun bitSetToList(bitSet: java.util.BitSet): List<Int> {
        val result = mutableListOf<Int>()
        var bit = bitSet.nextSetBit(0)
        while (bit >= 0) {
            result.add(bit)
            bit = bitSet.nextSetBit(bit + 1)
        }
        return result
    }

    /**
     * Get all cells in a given sector
     */
    private fun getSectorCells(sectorIndex: Int): List<Int> {
        return cardinals.SecSet[sectorIndex]?.toList() ?: emptyList()
    }

    /**
     * Determine the type of sector: "row", "column", or "box"
     */
    private fun getSectorType(sectorIndex: Int): String {
        return when {
            sectorIndex < 9 -> "row"
            sectorIndex < 18 -> "column"
            else -> "box"
        }
    }
    
    private fun techniqueMatchToDto(id: String, technique: Technique, match: TechniqueMatch): TechniqueMatchDto {
        val eliminations = match.eliminations.map { (digit, cells) ->
            val cellList = mutableListOf<Int>()
            var cell = cells.nextSetBit(0)
            while (cell >= 0) {
                cellList.add(cell)
                cell = cells.nextSetBit(cell + 1)
            }
            EliminationDto(digit = digit + 1, cells = cellList) // Convert to 1-9
        }
        
        val solvedCells = match.solvedCells.map { (cell, digit) ->
            SolvedCellDto(cell = cell, digit = digit + 1) // Convert to 1-9
        }
        
        // Collect all cells involved for highlighting
        val highlightCells = mutableSetOf<Int>()
        eliminations.forEach { highlightCells.addAll(it.cells) }
        solvedCells.forEach { highlightCells.add(it.cell) }
        
        // Extract visual data based on match type
        val (lines, groups, eurekaNotation) = extractVisualData(match, technique)
        
        // Generate explanation steps
        val explanationSteps = generateExplanationSteps(technique, match, eliminations, solvedCells)
        
        return TechniqueMatchDto(
            id = id,
            techniqueName = technique.getName(),
            description = match.toString(),
            eliminations = eliminations,
            solvedCells = solvedCells,
            highlightCells = highlightCells.toList(),
            lines = lines,
            groups = groups,
            explanationSteps = explanationSteps,
            eurekaNotation = eurekaNotation
        )
    }
    
    /**
     * Extract visual data (lines, groups, eureka notation) from a TechniqueMatch
     */
    private fun extractVisualData(match: TechniqueMatch, technique: Technique): Triple<List<LineDto>, List<GroupDto>, String?> {
        return when (match) {
            is AICMatch -> extractAICVisualData(match)
            is ALSMatch -> extractALSVisualData(match)
            is SubsetMatch -> extractSubsetVisualData(match)
            is FishMatch -> extractFishVisualData(match, technique.getName())
            else -> Triple(emptyList(), emptyList(), null)
        }
    }
    
    /**
     * Extract lines and groups from AIC (Alternating Inference Chain) matches
     */
    private fun extractAICVisualData(match: AICMatch): Triple<List<LineDto>, List<GroupDto>, String?> {
        val chain = match.chain
        val nodes = chain.nodes
        val lines = mutableListOf<LineDto>()
        val groups = mutableListOf<GroupDto>()
        
        // Extract Eureka notation
        val eurekaBuilder = StringBuilder()
        chain.toEurekaString(eurekaBuilder)
        val eurekaNotation = eurekaBuilder.toString()
        
        // Extract groups for multi-cell nodes
        nodes.forEachIndexed { index, node ->
            val groupType = if (index % 2 == 0) "chain-off" else "chain-on"
            
            // Create group for the node's cells
            val candidates = mutableListOf<CandidateLocationDto>()
            var cell = node.cells().nextSetBit(0)
            while (cell >= 0) {
                val row = cell / 9
                val col = cell % 9
                candidates.add(CandidateLocationDto(row, col, node.digit() + 1))
                cell = node.cells().nextSetBit(cell + 1)
            }
            
            if (candidates.isNotEmpty()) {
                groups.add(GroupDto(
                    candidates = candidates,
                    groupType = groupType,
                    colorIndex = index % 2
                ))
            }
            
            // Handle ALS cells if present
            if (node.alsCells() != null) {
                val alsCandidates = mutableListOf<CandidateLocationDto>()
                var alsCell = node.alsCells().nextSetBit(0)
                while (alsCell >= 0) {
                    val row = alsCell / 9
                    val col = alsCell % 9
                    // For ALS, we add all digits in the ALS
                    if (node.alsDigits() != null) {
                        var digit = node.alsDigits().nextSetBit(0)
                        while (digit >= 0) {
                            alsCandidates.add(CandidateLocationDto(row, col, digit + 1))
                            digit = node.alsDigits().nextSetBit(digit + 1)
                        }
                    }
                    alsCell = node.alsCells().nextSetBit(alsCell + 1)
                }
                if (alsCandidates.isNotEmpty()) {
                    groups.add(GroupDto(
                        candidates = alsCandidates,
                        groupType = "als",
                        colorIndex = groups.size
                    ))
                }
            }
        }
        
        // Extract lines between consecutive nodes
        for (i in 1 until nodes.size) {
            val prevNode = nodes[i - 1]
            val currNode = nodes[i]
            
            // Find the closest pair of cells between nodes
            var minDist = Double.MAX_VALUE
            var fromCell = -1
            var toCell = -1
            
            var cellA = prevNode.cells().nextSetBit(0)
            while (cellA >= 0) {
                var cellB = currNode.cells().nextSetBit(0)
                while (cellB >= 0) {
                    val rowA = cellA / 9
                    val colA = cellA % 9
                    val rowB = cellB / 9
                    val colB = cellB % 9
                    val dist = Math.sqrt(((rowA - rowB) * (rowA - rowB) + (colA - colB) * (colA - colB)).toDouble())
                    if (dist < minDist) {
                        minDist = dist
                        fromCell = cellA
                        toCell = cellB
                    }
                    cellB = currNode.cells().nextSetBit(cellB + 1)
                }
                cellA = prevNode.cells().nextSetBit(cellA + 1)
            }
            
            if (fromCell >= 0 && toCell >= 0) {
                val isStrongLink = chain.isFirstLinkStrong xor (i % 2 == 0)
                val curveOffset = if (i % 2 == 0) 0.1 else -0.1
                
                lines.add(LineDto(
                    from = CandidateLocationDto(fromCell / 9, fromCell % 9, prevNode.digit() + 1),
                    to = CandidateLocationDto(toCell / 9, toCell % 9, currNode.digit() + 1),
                    curveX = curveOffset,
                    curveY = 0.5,
                    isStrongLink = isStrongLink
                ))
            }
        }
        
        return Triple(lines, groups, eurekaNotation)
    }
    
    /**
     * Extract lines and groups from ALS (Almost Locked Set) matches
     */
    private fun extractALSVisualData(match: ALSMatch): Triple<List<LineDto>, List<GroupDto>, String?> {
        val chain = match.getChain()
        val nodes = chain.getNodes()
        val lines = mutableListOf<LineDto>()
        val groups = mutableListOf<GroupDto>()
        
        // Extract Eureka notation
        val eurekaBuilder = StringBuilder()
        chain.toEurekaALSString(eurekaBuilder)
        val eurekaNotation = eurekaBuilder.toString()
        
        // Process each collective (node) in the ALS chain
        var colorIndex = 0
        for (collective in nodes) {
            // Add ALS groups
            val alsList = collective.alsList()
            for (als in alsList) {
                val candidates = mutableListOf<CandidateLocationDto>()
                var cell = als.alsAllCells.nextSetBit(0)
                while (cell >= 0) {
                    val row = cell / 9
                    val col = cell % 9
                    // Add each digit in the ALS
                    var digit = als.alsDigits.nextSetBit(0)
                    while (digit >= 0) {
                        candidates.add(CandidateLocationDto(row, col, digit + 1))
                        digit = als.alsDigits.nextSetBit(digit + 1)
                    }
                    cell = als.alsAllCells.nextSetBit(cell + 1)
                }
                if (candidates.isNotEmpty()) {
                    groups.add(GroupDto(
                        candidates = candidates,
                        groupType = "als",
                        colorIndex = colorIndex
                    ))
                    colorIndex++
                }
            }
            
            // Add RCC (Restricted Common Candidate) links
            val startRCCs = collective.StartRCCnode()
            val linkRCCs = collective.LinkRCCnodes()
            
            // Process start RCCs
            for (rcc in startRCCs) {
                val rccCandidates = mutableListOf<CandidateLocationDto>()
                var cell = rcc.rccCells.nextSetBit(0)
                while (cell >= 0) {
                    val row = cell / 9
                    val col = cell % 9
                    rccCandidates.add(CandidateLocationDto(row, col, rcc.rccDigit + 1))
                    cell = rcc.rccCells.nextSetBit(cell + 1)
                }
                if (rccCandidates.size >= 2) {
                    // Draw lines between RCC cells
                    for (i in 0 until rccCandidates.size - 1) {
                        lines.add(LineDto(
                            from = rccCandidates[i],
                            to = rccCandidates[i + 1],
                            isStrongLink = true,
                            lineType = "rcc"
                        ))
                    }
                }
            }
            
            // Process link RCCs
            for (rcc in linkRCCs) {
                val rccCandidates = mutableListOf<CandidateLocationDto>()
                var cell = rcc.rccCells.nextSetBit(0)
                while (cell >= 0) {
                    val row = cell / 9
                    val col = cell % 9
                    rccCandidates.add(CandidateLocationDto(row, col, rcc.rccDigit + 1))
                    cell = rcc.rccCells.nextSetBit(cell + 1)
                }
                if (rccCandidates.size >= 2) {
                    // Draw lines between RCC cells
                    for (i in 0 until rccCandidates.size - 1) {
                        lines.add(LineDto(
                            from = rccCandidates[i],
                            to = rccCandidates[i + 1],
                            isStrongLink = false,
                            lineType = "rcc-link"
                        ))
                    }
                }
            }
        }
        
        return Triple(lines, groups, eurekaNotation)
    }

    /**
     * Extract visual data from SubsetMatch (Naked/Hidden Singles through Quadruples)
     */
    private fun extractSubsetVisualData(match: TechniqueMatch): Triple<List<LineDto>, List<GroupDto>, String?> {
        val lines = mutableListOf<LineDto>()
        val groups = mutableListOf<GroupDto>()

        try {
            // Use reflection to access private fields
            val matchClass = match.javaClass
            val digitsField = matchClass.getDeclaredField("digits")
            val cellsField = matchClass.getDeclaredField("cells")
            val sectorsField = matchClass.getDeclaredField("sectors")
            val nakedField = matchClass.getDeclaredField("naked")

            digitsField.isAccessible = true
            cellsField.isAccessible = true
            sectorsField.isAccessible = true
            nakedField.isAccessible = true

            val digits = digitsField.get(match) as java.util.BitSet
            val cells = cellsField.get(match) as java.util.BitSet
            val sectors = sectorsField.get(match) as java.util.BitSet
            val naked = nakedField.get(match) as Boolean

            // Convert BitSets to lists
            val digitList = bitSetToList(digits).map { it + 1 } // Convert to 1-9
            val cellList = bitSetToList(cells)

            if (naked) {
                // Naked subsets: all digits in the subset cells
                val candidates = mutableListOf<CandidateLocationDto>()
                for (cell in cellList) {
                    val row = cell / 9
                    val col = cell % 9
                    for (digit in digitList) {
                        candidates.add(CandidateLocationDto(row, col, digit))
                    }
                }

                if (candidates.isNotEmpty()) {
                    groups.add(GroupDto(
                        candidates = candidates,
                        groupType = "naked-subset",
                        colorIndex = 0
                    ))
                }
            } else {
                // Hidden subsets: create two groups
                // Group 1: The subset cells with their digits
                val subsetCandidates = mutableListOf<CandidateLocationDto>()
                for (cell in cellList) {
                    val row = cell / 9
                    val col = cell % 9
                    for (digit in digitList) {
                        subsetCandidates.add(CandidateLocationDto(row, col, digit))
                    }
                }

                if (subsetCandidates.isNotEmpty()) {
                    groups.add(GroupDto(
                        candidates = subsetCandidates,
                        groupType = "hidden-subset-cells",
                        colorIndex = 0
                    ))
                }

                // Group 2: Other cells in the sectors that have these digits (showing why they're hidden)
                val eliminationCandidates = mutableListOf<CandidateLocationDto>()
                var sector = sectors.nextSetBit(0)
                while (sector >= 0) {
                    val sectorCells = getSectorCells(sector)
                    for (cell in sectorCells) {
                        if (!cellList.contains(cell)) {
                            val row = cell / 9
                            val col = cell % 9
                            for (digit in digitList) {
                                eliminationCandidates.add(CandidateLocationDto(row, col, digit))
                            }
                        }
                    }
                    sector = sectors.nextSetBit(sector + 1)
                }

                if (eliminationCandidates.isNotEmpty()) {
                    groups.add(GroupDto(
                        candidates = eliminationCandidates,
                        groupType = "hidden-subset-eliminations",
                        colorIndex = 1
                    ))
                }
            }

        } catch (e: Exception) {
            // If reflection fails, return empty data
            e.printStackTrace()
        }

        return Triple(lines, groups, null)
    }

    /**
     * Extract visual data from FishMatch (Pointing/Claiming Candidates)
     */
    private fun extractFishVisualData(match: TechniqueMatch, techniqueName: String): Triple<List<LineDto>, List<GroupDto>, String?> {
        val lines = mutableListOf<LineDto>()
        val groups = mutableListOf<GroupDto>()

        // Only process Pointing and Claiming Candidates
        if (!techniqueName.contains("Pointing", ignoreCase = true) &&
            !techniqueName.contains("Claiming", ignoreCase = true)) {
            return Triple(lines, groups, null)
        }

        try {
            // Use reflection to access private fields
            val matchClass = match.javaClass
            val digitField = matchClass.getDeclaredField("digit")
            val baseSecsField = matchClass.getDeclaredField("baseSecs")
            val coverSecsField = matchClass.getDeclaredField("coverSecs")

            digitField.isAccessible = true
            baseSecsField.isAccessible = true
            coverSecsField.isAccessible = true

            val digit = (digitField.get(match) as Int) + 1 // Convert to 1-9
            val baseSecs = baseSecsField.get(match) as java.util.BitSet
            val coverSecs = coverSecsField.get(match) as java.util.BitSet

            val isPointing = techniqueName.contains("Pointing", ignoreCase = true)

            // Get the sectors involved
            val baseSector = baseSecs.nextSetBit(0)
            val coverSector = coverSecs.nextSetBit(0)

            if (baseSector >= 0 && coverSector >= 0) {
                val baseSectorType = getSectorType(baseSector)
                val coverSectorType = getSectorType(coverSector)

                if (isPointing) {
                    // Pointing Candidates: digit is restricted to a line within a box
                    // baseSecs is the box, coverSecs is the line (row/col)
                    val boxCells = getSectorCells(baseSector)
                    val lineCells = getSectorCells(coverSector)

                    // Group 1: Cells in the box that contain the digit
                    val boxCandidates = mutableListOf<CandidateLocationDto>()
                    for (cell in boxCells) {
                        val row = cell / 9
                        val col = cell % 9
                        boxCandidates.add(CandidateLocationDto(row, col, digit))
                    }

                    if (boxCandidates.isNotEmpty()) {
                        groups.add(GroupDto(
                            candidates = boxCandidates,
                            groupType = "pointing-box",
                            colorIndex = 0
                        ))
                    }

                    // Group 2: Cells in the line that get eliminated
                    val lineEliminations = mutableListOf<CandidateLocationDto>()
                    for (cell in lineCells) {
                        if (!boxCells.contains(cell)) {
                            val row = cell / 9
                            val col = cell % 9
                            lineEliminations.add(CandidateLocationDto(row, col, digit))
                        }
                    }

                    if (lineEliminations.isNotEmpty()) {
                        groups.add(GroupDto(
                            candidates = lineEliminations,
                            groupType = "pointing-eliminations",
                            colorIndex = 1
                        ))
                    }

                } else {
                    // Claiming Candidates: digit is restricted to a box within a line
                    // baseSecs is the line (row/col), coverSecs is the box
                    val lineCells = getSectorCells(baseSector)
                    val boxCells = getSectorCells(coverSector)

                    // Group 1: Cells in the line that contain the digit
                    val lineCandidates = mutableListOf<CandidateLocationDto>()
                    for (cell in lineCells) {
                        val row = cell / 9
                        val col = cell % 9
                        lineCandidates.add(CandidateLocationDto(row, col, digit))
                    }

                    if (lineCandidates.isNotEmpty()) {
                        groups.add(GroupDto(
                            candidates = lineCandidates,
                            groupType = "claiming-line",
                            colorIndex = 0
                        ))
                    }

                    // Group 2: Cells in the box that get eliminated
                    val boxEliminations = mutableListOf<CandidateLocationDto>()
                    for (cell in boxCells) {
                        if (!lineCells.contains(cell)) {
                            val row = cell / 9
                            val col = cell % 9
                            boxEliminations.add(CandidateLocationDto(row, col, digit))
                        }
                    }

                    if (boxEliminations.isNotEmpty()) {
                        groups.add(GroupDto(
                            candidates = boxEliminations,
                            groupType = "claiming-eliminations",
                            colorIndex = 1
                        ))
                    }
                }
            }

        } catch (e: Exception) {
            // If reflection fails, return empty data
            e.printStackTrace()
        }

        return Triple(lines, groups, null)
    }

    /**
     * Generate step-by-step explanation for a technique
     */
    private fun generateExplanationSteps(
        technique: Technique,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>,
        solvedCells: List<SolvedCellDto>
    ): List<ExplanationStepDto> {
        val techniqueName = technique.getName()
        val steps = mutableListOf<ExplanationStepDto>()
        
        when {
            techniqueName.contains("Single", ignoreCase = true) -> {
                steps.addAll(generateSingleSteps(techniqueName, match, eliminations, solvedCells))
            }
            techniqueName.contains("Pair", ignoreCase = true) || 
            techniqueName.contains("Triple", ignoreCase = true) ||
            techniqueName.contains("Quadruple", ignoreCase = true) -> {
                steps.addAll(generateSubsetSteps(techniqueName, match, eliminations))
            }
            techniqueName.contains("Pointing", ignoreCase = true) ||
            techniqueName.contains("Claiming", ignoreCase = true) ||
            techniqueName.contains("Box/Line", ignoreCase = true) -> {
                steps.addAll(generateIntersectionSteps(techniqueName, match, eliminations))
            }
            match is AICMatch -> {
                steps.addAll(generateChainSteps(techniqueName, match, eliminations))
            }
            match is ALSMatch -> {
                steps.addAll(generateALSSteps(techniqueName, match, eliminations))
            }
            else -> {
                // Generic explanation for other techniques
                steps.addAll(generateGenericSteps(techniqueName, match, eliminations, solvedCells))
            }
        }
        
        return steps
    }
    
    private fun generateSingleSteps(
        techniqueName: String,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>,
        solvedCells: List<SolvedCellDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        
        if (solvedCells.isEmpty()) return steps
        
        val solved = solvedCells.first()
        val cellIndex = solved.cell
        val row = cellIndex / 9
        val col = cellIndex % 9
        val digit = solved.digit
        
        // Try to extract sector info from SubsetMatch
        var sectorIndex: Int? = null
        var sectorType: String? = null
        try {
            val matchClass = match.javaClass
            val sectorsField = matchClass.getDeclaredField("sectors")
            sectorsField.isAccessible = true
            val sectors = sectorsField.get(match) as java.util.BitSet
            sectorIndex = sectors.nextSetBit(0)
            if (sectorIndex != null && sectorIndex >= 0) {
                sectorType = getSectorType(sectorIndex)
            }
        } catch (e: Exception) {
            // Fallback if reflection fails
        }
        
        val houseName = when (sectorType) {
            "row" -> "Row ${row + 1}"
            "column" -> "Column ${col + 1}"
            "box" -> "Box ${(row / 3) * 3 + (col / 3) + 1}"
            else -> "this house"
        }
        
        if (techniqueName.contains("Naked", ignoreCase = true)) {
            // Naked Single: cell has only one candidate
            // For Step 1, highlight the cell itself (no region needed - it's about the cell's candidates)
            steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = "Identify the Naked Single",
                description = "Cell R${row + 1}C${col + 1} has only one possible candidate: $digit",
                highlightCells = listOf(cellIndex),
                coloredCells = listOf(ColoredCellDto(cellIndex, "warning")),
                coloredCandidates = listOf(ColoredCandidateDto(row, col, digit, "target"))
            ))
            
            // Step 2: Show eliminations from peers - highlight all three houses
            val peerEliminations = eliminations.filter { it.digit == digit }
            val eliminationCandidates = peerEliminations.flatMap { elim ->
                elim.cells.map { c ->
                    ColoredCandidateDto(c / 9, c % 9, elim.digit, "elimination")
                }
            }
            
            val boxNum = (row / 3) * 3 + (col / 3) + 1
            val eliminationDesc = if (peerEliminations.isNotEmpty()) {
                val cells = peerEliminations.flatMap { it.cells }.map { "R${it/9 + 1}C${it%9 + 1}" }
                "Place $digit in R${row + 1}C${col + 1}. Eliminate $digit from Row ${row + 1}, Column ${col + 1}, and Box $boxNum: ${cells.joinToString(", ")}"
            } else {
                "Place $digit in R${row + 1}C${col + 1}"
            }
            
            // Highlight all three houses that see this cell
            val allRegions = listOf(
                ColoredRegionDto("row", row, "primary"),
                ColoredRegionDto("column", col, "primary"),
                ColoredRegionDto("box", (row / 3) * 3 + (col / 3), "primary")
            )
            
            steps.add(ExplanationStepDto(
                stepNumber = 2,
                title = "Place Value and Eliminate",
                description = eliminationDesc,
                highlightCells = listOf(cellIndex),
                regions = allRegions,
                coloredCells = listOf(ColoredCellDto(cellIndex, "target")),
                coloredCandidates = listOf(ColoredCandidateDto(row, col, digit, "target")) + eliminationCandidates
            ))
        } else {
            // Hidden Single: digit can only go in one cell in a house
            
            // Separate eliminations: those in the cell itself vs those in peer cells
            val cellEliminations = eliminations.filter { it.cells.contains(cellIndex) && it.digit != digit }
            val peerEliminations = eliminations.filter { it.digit == digit && !it.cells.all { c -> c == cellIndex } }
            
            // Step 1: Identify the hidden single in the house
            val regions = if (sectorIndex != null && sectorIndex >= 0) {
                listOf(ColoredRegionDto(sectorType ?: "row", sectorIndex % 9, "primary"))
            } else {
                emptyList()
            }
            
            steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = "Identify the Hidden Single",
                description = "In $houseName, only R${row + 1}C${col + 1} can be $digit. Thus, eliminate other candidates from this cell.",
                highlightCells = listOf(cellIndex),
                regions = regions,
                coloredCells = listOf(ColoredCellDto(cellIndex, "warning")),
                coloredCandidates = listOf(ColoredCandidateDto(row, col, digit, "target")) +
                    cellEliminations.flatMap { elim ->
                        elim.cells.filter { it == cellIndex }.map { c ->
                            ColoredCandidateDto(c / 9, c % 9, elim.digit, "elimination")
                        }
                    }
            ))
            
            // Step 2: Show eliminations from peer cells (row, column, box)
            if (peerEliminations.isNotEmpty()) {
                val peerCells = peerEliminations.flatMap { it.cells }.filter { it != cellIndex }.distinct()
                val peerCellNames = peerCells.map { "R${it/9 + 1}C${it%9 + 1}" }
                val boxNum = (row / 3) * 3 + (col / 3) + 1
                
                // Highlight all three houses that see this cell
                val allRegions = listOf(
                    ColoredRegionDto("row", row, "primary"),
                    ColoredRegionDto("column", col, "primary"),
                    ColoredRegionDto("box", (row / 3) * 3 + (col / 3), "primary")
                )
                
                steps.add(ExplanationStepDto(
                    stepNumber = 2,
                    title = "Eliminate from Peers",
                    description = "In Row ${row + 1}, Column ${col + 1}, and Box $boxNum, $digit can only be in R${row + 1}C${col + 1}. Eliminate $digit from: ${peerCellNames.joinToString(", ")}",
                    highlightCells = peerCells,
                    regions = allRegions,
                    coloredCells = listOf(ColoredCellDto(cellIndex, "target")),
                    coloredCandidates = listOf(ColoredCandidateDto(row, col, digit, "target")) +
                        peerEliminations.flatMap { elim ->
                            elim.cells.filter { it != cellIndex }.map { c ->
                                ColoredCandidateDto(c / 9, c % 9, elim.digit, "elimination")
                            }
                        }
                ))
            }
        }
        
        return steps
    }
    
    private fun generateSubsetSteps(
        techniqueName: String,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        
        val isNaked = techniqueName.contains("Naked", ignoreCase = true)
        val subsetType = when {
            techniqueName.contains("Pair", ignoreCase = true) -> "Pair"
            techniqueName.contains("Triple", ignoreCase = true) -> "Triple"
            techniqueName.contains("Quadruple", ignoreCase = true) -> "Quadruple"
            else -> "Subset"
        }
        
        // Extract data from SubsetMatch using reflection
        var subsetCells = listOf<Int>()
        var subsetDigits = listOf<Int>()
        var sectorIndex: Int? = null
        var sectorType: String? = null
        
        try {
            val matchClass = match.javaClass
            val digitsField = matchClass.getDeclaredField("digits")
            val cellsField = matchClass.getDeclaredField("cells")
            val sectorsField = matchClass.getDeclaredField("sectors")
            
            digitsField.isAccessible = true
            cellsField.isAccessible = true
            sectorsField.isAccessible = true
            
            val digits = digitsField.get(match) as java.util.BitSet
            val cells = cellsField.get(match) as java.util.BitSet
            val sectors = sectorsField.get(match) as java.util.BitSet
            
            subsetDigits = bitSetToList(digits).map { it + 1 } // Convert to 1-9
            subsetCells = bitSetToList(cells)
            sectorIndex = sectors.nextSetBit(0)
            if (sectorIndex != null && sectorIndex >= 0) {
                sectorType = getSectorType(sectorIndex)
            }
        } catch (e: Exception) {
            // Fallback: use elimination data
            subsetDigits = eliminations.map { it.digit }.distinct().sorted()
        }
        
        val houseName = when (sectorType) {
            "row" -> "Row ${(sectorIndex ?: 0) % 9 + 1}"
            "column" -> "Column ${(sectorIndex ?: 9) % 9 + 1}"
            "box" -> "Box ${(sectorIndex ?: 18) % 9 + 1}"
            else -> "this house"
        }
        
        val cellNames = subsetCells.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
        val digitNames = subsetDigits.joinToString(", ")
        
        // Build regions for highlighting
        val regions = if (sectorIndex != null && sectorIndex >= 0) {
            listOf(ColoredRegionDto(sectorType ?: "row", sectorIndex % 9, "primary"))
        } else {
            emptyList()
        }
        
        // Build colored cells for the subset
        val coloredSubsetCells = subsetCells.map { ColoredCellDto(it, "warning") }
        
        // Build colored candidates for the subset digits in subset cells
        val subsetCandidates = subsetCells.flatMap { cell ->
            val r = cell / 9
            val c = cell % 9
            subsetDigits.map { digit -> ColoredCandidateDto(r, c, digit, "target") }
        }
        
        // Get cells in the main sector to separate normal vs pointing eliminations
        val sectorCells = if (sectorIndex != null && sectorIndex >= 0) {
            getSectorCells(sectorIndex).toSet()
        } else {
            emptySet()
        }
        
        // Separate eliminations: normal (within sector) vs pointing (outside sector, locked candidates effect)
        val normalEliminations = eliminations.filter { elim ->
            elim.cells.all { it in sectorCells }
        }
        val pointingEliminations = eliminations.filter { elim ->
            elim.cells.any { it !in sectorCells }
        }
        
        if (isNaked) {
            // Naked Subset: cells only contain these digits
            steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = "Identify the Naked $subsetType",
                description = "In $houseName, cells $cellNames only contain candidates {$digitNames}. These digits are locked to these cells.",
                highlightCells = subsetCells,
                regions = regions,
                coloredCells = coloredSubsetCells,
                coloredCandidates = subsetCandidates
            ))
            
            var stepNumber = 2
            
            // Step 2: Normal eliminations within the sector
            if (normalEliminations.isNotEmpty()) {
                val eliminationCandidates = normalEliminations.flatMap { elim ->
                    elim.cells.map { c ->
                        ColoredCandidateDto(c / 9, c % 9, elim.digit, "elimination")
                    }
                }
                val eliminationCells = normalEliminations.flatMap { it.cells }.distinct()
                val eliminationCellNames = eliminationCells.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
                val elimDigitNames = normalEliminations.map { it.digit }.distinct().sorted().joinToString(", ")
                
                steps.add(ExplanationStepDto(
                    stepNumber = stepNumber++,
                    title = "Eliminate from $houseName",
                    description = "Eliminate {$elimDigitNames} from other cells in $houseName: $eliminationCellNames",
                    highlightCells = eliminationCells,
                    regions = regions,
                    coloredCells = coloredSubsetCells,
                    coloredCandidates = subsetCandidates + eliminationCandidates
                ))
            }
            
            // Step 3 (or 2 if no normal eliminations): Pointing eliminations (locked candidates effect)
            if (pointingEliminations.isNotEmpty()) {
                // Group pointing eliminations by the box they affect
                val pointingByBox = pointingEliminations.flatMap { elim ->
                    elim.cells.filter { it !in sectorCells }.map { cell ->
                        val boxIndex = (cell / 9 / 3) * 3 + (cell % 9 / 3)
                        Triple(boxIndex, elim.digit, cell)
                    }
                }.groupBy { it.first }
                
                for ((boxIndex, elimsInBox) in pointingByBox) {
                    val affectedDigits = elimsInBox.map { it.second }.distinct().sorted()
                    val affectedCells = elimsInBox.map { it.third }.distinct()
                    val affectedCellNames = affectedCells.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
                    val affectedDigitNames = affectedDigits.joinToString(", ")
                    
                    // Find which subset cells are in this box
                    val subsetCellsInBox = subsetCells.filter { cell ->
                        val cellBox = (cell / 9 / 3) * 3 + (cell % 9 / 3)
                        cellBox == boxIndex
                    }
                    val subsetCellsInBoxNames = subsetCellsInBox.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
                    
                    val eliminationCandidates = affectedCells.flatMap { cell ->
                        affectedDigits.map { digit ->
                            ColoredCandidateDto(cell / 9, cell % 9, digit, "elimination")
                        }
                    }
                    
                    // Highlight the subset cells that are in this box with target candidates
                    val boxSubsetCandidates = subsetCellsInBox.flatMap { cell ->
                        affectedDigits.map { digit ->
                            ColoredCandidateDto(cell / 9, cell % 9, digit, "target")
                        }
                    }
                    
                    // Regions: highlight both the main sector and the affected box
                    val pointingRegions = listOfNotNull(
                        if (sectorIndex != null && sectorIndex >= 0) {
                            ColoredRegionDto(sectorType ?: "row", sectorIndex % 9, "primary")
                        } else null,
                        ColoredRegionDto("box", boxIndex, "secondary")
                    )
                    
                    steps.add(ExplanationStepDto(
                        stepNumber = stepNumber++,
                        title = "Pointing: Eliminate $affectedDigitNames from Box ${boxIndex + 1}",
                        description = "In Box ${boxIndex + 1}, $affectedDigitNames can only be in $subsetCellsInBoxNames (part of the $subsetType in $houseName). Eliminate $affectedDigitNames from $affectedCellNames.",
                        highlightCells = affectedCells + subsetCellsInBox,
                        regions = pointingRegions,
                        coloredCells = subsetCellsInBox.map { ColoredCellDto(it, "warning") },
                        coloredCandidates = boxSubsetCandidates + eliminationCandidates
                    ))
                }
            }
        } else {
            // Hidden Subset: digits only appear in these cells
            steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = "Identify the Hidden $subsetType",
                description = "In $houseName, candidates {$digitNames} only appear in cells $cellNames. These cells must contain these digits.",
                highlightCells = subsetCells,
                regions = regions,
                coloredCells = coloredSubsetCells,
                coloredCandidates = subsetCandidates
            ))
            
            var stepNumber = 2
            
            // Step 2: Normal eliminations (other candidates from subset cells)
            val cellEliminations = eliminations.filter { elim ->
                elim.cells.any { it in subsetCells }
            }
            if (cellEliminations.isNotEmpty()) {
                val eliminationCandidates = cellEliminations.flatMap { elim ->
                    elim.cells.filter { it in subsetCells }.map { c ->
                        ColoredCandidateDto(c / 9, c % 9, elim.digit, "elimination")
                    }
                }
                val otherDigits = cellEliminations.map { it.digit }.distinct().sorted()
                val otherDigitNames = otherDigits.joinToString(", ")
                
                steps.add(ExplanationStepDto(
                    stepNumber = stepNumber++,
                    title = "Eliminate Other Candidates",
                    description = "Since cells $cellNames must contain {$digitNames}, eliminate other candidates {$otherDigitNames} from these cells.",
                    highlightCells = subsetCells,
                    regions = regions,
                    coloredCells = coloredSubsetCells,
                    coloredCandidates = subsetCandidates + eliminationCandidates
                ))
            }
            
            // Step 3: Pointing eliminations for hidden subsets (similar logic)
            val peerEliminations = eliminations.filter { elim ->
                elim.cells.any { it !in subsetCells }
            }
            if (peerEliminations.isNotEmpty()) {
                // Group by box affected
                val pointingByBox = peerEliminations.flatMap { elim ->
                    elim.cells.filter { it !in subsetCells && it !in sectorCells }.map { cell ->
                        val boxIndex = (cell / 9 / 3) * 3 + (cell % 9 / 3)
                        Triple(boxIndex, elim.digit, cell)
                    }
                }.groupBy { it.first }
                
                for ((boxIndex, elimsInBox) in pointingByBox) {
                    val affectedDigits = elimsInBox.map { it.second }.distinct().sorted()
                    val affectedCells = elimsInBox.map { it.third }.distinct()
                    val affectedCellNames = affectedCells.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
                    val affectedDigitNames = affectedDigits.joinToString(", ")
                    
                    val subsetCellsInBox = subsetCells.filter { cell ->
                        val cellBox = (cell / 9 / 3) * 3 + (cell % 9 / 3)
                        cellBox == boxIndex
                    }
                    val subsetCellsInBoxNames = subsetCellsInBox.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
                    
                    val eliminationCandidates = affectedCells.flatMap { cell ->
                        affectedDigits.map { digit ->
                            ColoredCandidateDto(cell / 9, cell % 9, digit, "elimination")
                        }
                    }
                    
                    val boxSubsetCandidates = subsetCellsInBox.flatMap { cell ->
                        affectedDigits.map { digit ->
                            ColoredCandidateDto(cell / 9, cell % 9, digit, "target")
                        }
                    }
                    
                    val pointingRegions = listOfNotNull(
                        if (sectorIndex != null && sectorIndex >= 0) {
                            ColoredRegionDto(sectorType ?: "row", sectorIndex % 9, "primary")
                        } else null,
                        ColoredRegionDto("box", boxIndex, "secondary")
                    )
                    
                    steps.add(ExplanationStepDto(
                        stepNumber = stepNumber++,
                        title = "Pointing: Eliminate $affectedDigitNames from Box ${boxIndex + 1}",
                        description = "In Box ${boxIndex + 1}, $affectedDigitNames can only be in $subsetCellsInBoxNames (part of the $subsetType in $houseName). Eliminate $affectedDigitNames from $affectedCellNames.",
                        highlightCells = affectedCells + subsetCellsInBox,
                        regions = pointingRegions,
                        coloredCells = subsetCellsInBox.map { ColoredCellDto(it, "warning") },
                        coloredCandidates = boxSubsetCandidates + eliminationCandidates
                    ))
                }
            }
        }
        
        return steps
    }
    
    private fun generateIntersectionSteps(
        techniqueName: String,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        
        val isPointing = techniqueName.contains("Pointing", ignoreCase = true)
        
        // Extract data from FishMatch using reflection
        var digit = 0
        var baseSectorIndex: Int? = null
        var coverSectorIndex: Int? = null
        
        try {
            val matchClass = match.javaClass
            val digitField = matchClass.getDeclaredField("digit")
            val baseSecsField = matchClass.getDeclaredField("baseSecs")
            val coverSecsField = matchClass.getDeclaredField("coverSecs")
            
            digitField.isAccessible = true
            baseSecsField.isAccessible = true
            coverSecsField.isAccessible = true
            
            digit = (digitField.get(match) as Int) + 1 // Convert to 1-9
            val baseSecs = baseSecsField.get(match) as java.util.BitSet
            val coverSecs = coverSecsField.get(match) as java.util.BitSet
            
            baseSectorIndex = baseSecs.nextSetBit(0)
            coverSectorIndex = coverSecs.nextSetBit(0)
        } catch (e: Exception) {
            // Fallback: use elimination data
            digit = eliminations.firstOrNull()?.digit ?: 0
        }
        
        val baseSectorType = if (baseSectorIndex != null && baseSectorIndex >= 0) getSectorType(baseSectorIndex) else null
        val coverSectorType = if (coverSectorIndex != null && coverSectorIndex >= 0) getSectorType(coverSectorIndex) else null
        
        val baseHouseName = when (baseSectorType) {
            "row" -> "Row ${(baseSectorIndex ?: 0) % 9 + 1}"
            "column" -> "Column ${(baseSectorIndex ?: 9) % 9 + 1}"
            "box" -> "Box ${(baseSectorIndex ?: 18) % 9 + 1}"
            else -> "the base house"
        }
        
        val coverHouseName = when (coverSectorType) {
            "row" -> "Row ${(coverSectorIndex ?: 0) % 9 + 1}"
            "column" -> "Column ${(coverSectorIndex ?: 9) % 9 + 1}"
            "box" -> "Box ${(coverSectorIndex ?: 18) % 9 + 1}"
            else -> "the cover house"
        }
        
        // Get base and cover cells
        val baseCells = if (baseSectorIndex != null && baseSectorIndex >= 0) getSectorCells(baseSectorIndex) else emptyList()
        val coverCells = if (coverSectorIndex != null && coverSectorIndex >= 0) getSectorCells(coverSectorIndex) else emptyList()
        
        // Intersection cells are in both base and cover
        val intersectionCells = baseCells.filter { it in coverCells }
        
        // Build regions for highlighting
        val baseRegion = if (baseSectorIndex != null && baseSectorIndex >= 0) {
            ColoredRegionDto(baseSectorType ?: "box", baseSectorIndex % 9, "primary")
        } else null
        
        val coverRegion = if (coverSectorIndex != null && coverSectorIndex >= 0) {
            ColoredRegionDto(coverSectorType ?: "row", coverSectorIndex % 9, "secondary")
        } else null
        
        val regions = listOfNotNull(baseRegion, coverRegion)
        
        // Colored cells for intersection
        val intersectionColoredCells = intersectionCells.map { ColoredCellDto(it, "warning") }
        
        // Colored candidates in intersection
        val intersectionCandidates = intersectionCells.map { cell ->
            ColoredCandidateDto(cell / 9, cell % 9, digit, "target")
        }
        
        if (isPointing) {
            // Pointing Candidates: digit in box is restricted to a line, eliminate from rest of line
            steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = "Identify the Pointing Candidates",
                description = "In $baseHouseName, candidate $digit only appears in cells that also belong to $coverHouseName.",
                highlightCells = intersectionCells,
                regions = regions,
                coloredCells = intersectionColoredCells,
                coloredCandidates = intersectionCandidates
            ))
            
            // Step 2: Eliminate from the rest of the cover house (line)
            if (eliminations.isNotEmpty()) {
                val eliminationCells = eliminations.flatMap { it.cells }
                val eliminationCandidates = eliminationCells.map { cell ->
                    ColoredCandidateDto(cell / 9, cell % 9, digit, "elimination")
                }
                val eliminationCellNames = eliminationCells.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
                
                steps.add(ExplanationStepDto(
                    stepNumber = 2,
                    title = "Eliminate from $coverHouseName",
                    description = "Eliminate $digit from other cells in $coverHouseName: $eliminationCellNames",
                    highlightCells = eliminationCells,
                    regions = listOfNotNull(coverRegion),
                    coloredCells = intersectionColoredCells,
                    coloredCandidates = intersectionCandidates + eliminationCandidates
                ))
            }
        } else {
            // Claiming/Box-Line Reduction: digit in line is restricted to a box, eliminate from rest of box
            steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = "Identify the Claiming Candidates",
                description = "In $baseHouseName, candidate $digit only appears in cells that also belong to $coverHouseName.",
                highlightCells = intersectionCells,
                regions = regions,
                coloredCells = intersectionColoredCells,
                coloredCandidates = intersectionCandidates
            ))
            
            // Step 2: Eliminate from the rest of the cover house (box)
            if (eliminations.isNotEmpty()) {
                val eliminationCells = eliminations.flatMap { it.cells }
                val eliminationCandidates = eliminationCells.map { cell ->
                    ColoredCandidateDto(cell / 9, cell % 9, digit, "elimination")
                }
                val eliminationCellNames = eliminationCells.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
                
                steps.add(ExplanationStepDto(
                    stepNumber = 2,
                    title = "Eliminate from $coverHouseName",
                    description = "Eliminate $digit from other cells in $coverHouseName: $eliminationCellNames",
                    highlightCells = eliminationCells,
                    regions = listOfNotNull(coverRegion),
                    coloredCells = intersectionColoredCells,
                    coloredCandidates = intersectionCandidates + eliminationCandidates
                ))
            }
        }
        
        return steps
    }
    
    private fun generateChainSteps(
        techniqueName: String,
        match: AICMatch,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        val chain = match.chain
        val nodes = chain.nodes
        
        // Step 1: Introduction
        steps.add(ExplanationStepDto(
            stepNumber = 1,
            title = "Chain Overview",
            description = "This is a ${nodes.size}-node chain. Follow the alternating strong (=) and weak (-) links.",
            highlightCells = emptyList()
        ))
        
        // Step 2: Walk through the chain
        val chainDescription = StringBuilder()
        nodes.forEachIndexed { index, node ->
            if (index > 0) {
                val linkType = if (chain.isFirstLinkStrong xor (index % 2 == 0)) "strong" else "weak"
                chainDescription.append(" --[$linkType]--> ")
            }
            val cells = mutableListOf<String>()
            var cell = node.cells().nextSetBit(0)
            while (cell >= 0) {
                cells.add("R${cell/9 + 1}C${cell%9 + 1}")
                cell = node.cells().nextSetBit(cell + 1)
            }
            chainDescription.append("(${node.digit() + 1})${cells.joinToString(",")}")
        }
        
        steps.add(ExplanationStepDto(
            stepNumber = 2,
            title = "Follow the Chain",
            description = chainDescription.toString(),
            highlightCells = emptyList()
        ))
        
        // Step 3: Conclusion
        if (eliminations.isNotEmpty()) {
            val eliminationDesc = eliminations.joinToString("; ") { elim ->
                val cells = elim.cells.map { "R${it/9 + 1}C${it%9 + 1}" }
                "${elim.digit} from ${cells.joinToString(", ")}"
            }
            steps.add(ExplanationStepDto(
                stepNumber = 3,
                title = "Apply Eliminations",
                description = "The chain proves we can eliminate: $eliminationDesc",
                highlightCells = eliminations.flatMap { it.cells }
            ))
        }
        
        return steps
    }
    
    private fun generateALSSteps(
        techniqueName: String,
        match: ALSMatch,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        val chain = match.getChain()
        val nodes = chain.getNodes()
        
        // Step 1: Introduction to ALS
        val numALS = chain.getNumALS()
        val alsType = when {
            techniqueName.contains("XY", ignoreCase = true) -> "ALS-XY"
            techniqueName.contains("XZ", ignoreCase = true) -> "ALS-XZ"
            techniqueName.contains("Wing", ignoreCase = true) -> "ALS-Wing"
            else -> "ALS Chain"
        }
        
        steps.add(ExplanationStepDto(
            stepNumber = 1,
            title = "What is an ALS?",
            description = "An Almost Locked Set (ALS) is a group of N cells containing N+1 candidates. " +
                "If any candidate is eliminated, the remaining N candidates must fill the N cells. " +
                "This $alsType uses $numALS Almost Locked Sets.",
            highlightCells = emptyList()
        ))
        
        // Step 2: Identify the ALS components
        val alsDescriptions = mutableListOf<String>()
        var stepNum = 2
        
        for ((nodeIndex, collective) in nodes.withIndex()) {
            val alsList = collective.alsList()
            for ((alsIndex, als) in alsList.withIndex()) {
                val cells = mutableListOf<String>()
                var cell = als.alsAllCells.nextSetBit(0)
                while (cell >= 0) {
                    cells.add("R${cell/9 + 1}C${cell%9 + 1}")
                    cell = als.alsAllCells.nextSetBit(cell + 1)
                }
                
                val digits = mutableListOf<Int>()
                var digit = als.alsDigits.nextSetBit(0)
                while (digit >= 0) {
                    digits.add(digit + 1)
                    digit = als.alsDigits.nextSetBit(digit + 1)
                }
                
                val alsName = "ALS ${('A'.code + alsIndex).toChar()}"
                alsDescriptions.add("$alsName: Cells ${cells.joinToString(", ")} with candidates {${digits.joinToString(", ")}}")
                
                // Get highlight cells for this ALS
                val highlightCells = mutableListOf<Int>()
                cell = als.alsAllCells.nextSetBit(0)
                while (cell >= 0) {
                    highlightCells.add(cell)
                    cell = als.alsAllCells.nextSetBit(cell + 1)
                }
                
                steps.add(ExplanationStepDto(
                    stepNumber = stepNum++,
                    title = "Identify $alsName",
                    description = "$alsName contains ${cells.size} cells with ${digits.size} candidates: " +
                        "${cells.joinToString(", ")} = {${digits.joinToString(", ")}}",
                    highlightCells = highlightCells
                ))
            }
            
            // Step: Identify RCCs (Restricted Common Candidates)
            val startRCCs = collective.StartRCCnode()
            val linkRCCs = collective.LinkRCCnodes()
            
            if (startRCCs.isNotEmpty() || linkRCCs.isNotEmpty()) {
                val rccDescriptions = mutableListOf<String>()
                
                for (rcc in startRCCs + linkRCCs) {
                    val rccCells = mutableListOf<String>()
                    var cell = rcc.rccCells.nextSetBit(0)
                    while (cell >= 0) {
                        rccCells.add("R${cell/9 + 1}C${cell%9 + 1}")
                        cell = rcc.rccCells.nextSetBit(cell + 1)
                    }
                    rccDescriptions.add("Digit ${rcc.rccDigit + 1} at ${rccCells.joinToString(", ")}")
                }
                
                if (rccDescriptions.isNotEmpty()) {
                    steps.add(ExplanationStepDto(
                        stepNumber = stepNum++,
                        title = "Restricted Common Candidates",
                        description = "The ALSs are connected by RCCs (digits that can only appear in one ALS or the other): " +
                            rccDescriptions.joinToString("; "),
                        highlightCells = emptyList()
                    ))
                }
            }
        }
        
        // Final step: Eliminations
        if (eliminations.isNotEmpty()) {
            val eliminationDesc = eliminations.joinToString("; ") { elim ->
                val cells = elim.cells.map { "R${it/9 + 1}C${it%9 + 1}" }
                "${elim.digit} from ${cells.joinToString(", ")}"
            }
            steps.add(ExplanationStepDto(
                stepNumber = stepNum,
                title = "Apply Eliminations",
                description = "Any cell that sees all instances of a digit in both ALSs can have that digit eliminated: $eliminationDesc",
                highlightCells = eliminations.flatMap { it.cells }
            ))
        }
        
        return steps
    }
    
    private fun generateGenericSteps(
        techniqueName: String,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>,
        solvedCells: List<SolvedCellDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        
        steps.add(ExplanationStepDto(
            stepNumber = 1,
            title = techniqueName,
            description = match.toString(),
            highlightCells = eliminations.flatMap { it.cells } + solvedCells.map { it.cell }
        ))
        
        if (eliminations.isNotEmpty()) {
            val eliminationDesc = eliminations.joinToString("; ") { elim ->
                val cells = elim.cells.map { "R${it/9 + 1}C${it%9 + 1}" }
                "${elim.digit} from ${cells.joinToString(", ")}"
            }
            steps.add(ExplanationStepDto(
                stepNumber = 2,
                title = "Eliminations",
                description = eliminationDesc,
                highlightCells = eliminations.flatMap { it.cells }
            ))
        }
        
        if (solvedCells.isNotEmpty()) {
            val solvedDesc = solvedCells.joinToString("; ") { solved ->
                "R${solved.cell/9 + 1}C${solved.cell%9 + 1} = ${solved.digit}"
            }
            steps.add(ExplanationStepDto(
                stepNumber = steps.size + 1,
                title = "Solutions",
                description = solvedDesc,
                highlightCells = solvedCells.map { it.cell }
            ))
        }
        
        return steps
    }
}

