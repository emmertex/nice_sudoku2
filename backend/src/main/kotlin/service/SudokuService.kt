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
        // DIABOLICAL (35-42): AIC, ALS, Sue-de-Coq, Forcing Chains, Rings
        "AIC" to 35, "Alternating Inference Chains" to 35, "AIC Type 2" to 35,
        "ALMOST_LOCKED_SETS" to 36, "Almost Locked Sets" to 36,
        "ALS_XY" to 36, "ALS-XY" to 36,
        "ALS_XZ" to 36, "ALS-XZ" to 36,
        "SUE_DE_COQ" to 37, "Sue-de-Coq" to 37,
        "FORCING_CHAINS" to 38, "Forcing Chains" to 38,
        "NISHIO" to 39, "Nishio" to 39,
        "RING" to 40, "Ring" to 40,
        "L_WING" to 41, "L-Wing" to 41, "L(3)-Wing" to 41,
        "W_WING" to 19, "W-Wing" to 19,
    )

    private fun normalizeTechniqueKey(name: String): String {
        return name.uppercase()
            .replace("-", "_")
            .replace(" ", "_")
            .replace("/", "_")
            .replace("(", "_")
            .replace(")", "_")
    }

    private val techniqueDescriptions = mapOf(
        // Singles / basics
        "NAKED_SINGLE" to "Only one candidate fits in this cell, so place it.",
        "NAKED_SINGLES" to "Only one candidate fits in this cell, so place it.",
        "HIDDEN_SINGLE" to "A digit appears in only one cell of a house, forcing its placement.",
        "HIDDEN_SINGLES" to "A digit appears in only one cell of a house, forcing its placement.",
        "POINTING_CANDIDATES" to "A digit is locked in one line of a box, so it can be removed from that line outside the box.",
        "POINTING_PAIRS" to "A digit is locked in one line of a box, so it can be removed from that line outside the box.",
        "CLAIMING_CANDIDATES" to "A digit is locked in one box of a line, so it can be removed from that box outside the line.",
        "BOX_LINE_REDUCTION" to "A digit is locked in one box of a line, so it can be removed from that box outside the line.",
        // Subsets
        "NAKED_PAIR" to "Two cells in a house contain exactly the same two candidates; they claim those digits.",
        "NAKED_PAIRS" to "Two cells in a house contain exactly the same two candidates; they claim those digits.",
        "NAKED_TRIPLE" to "Three cells in a house contain exactly the same three candidates; they claim those digits.",
        "NAKED_TRIPLES" to "Three cells in a house contain exactly the same three candidates; they claim those digits.",
        "NAKED_QUADRUPLE" to "Four cells in a house contain exactly the same four candidates; they claim those digits.",
        "NAKED_QUADRUPLES" to "Four cells in a house contain exactly the same four candidates; they claim those digits.",
        "HIDDEN_PAIR" to "Two digits can only appear in two cells of a house; other candidates in those cells are removed.",
        "HIDDEN_PAIRS" to "Two digits can only appear in two cells of a house; other candidates in those cells are removed.",
        "HIDDEN_TRIPLE" to "Three digits can only appear in three cells of a house; other candidates in those cells are removed.",
        "HIDDEN_TRIPLES" to "Three digits can only appear in three cells of a house; other candidates in those cells are removed.",
        "HIDDEN_QUADRUPLE" to "Four digits can only appear in four cells of a house; other candidates in those cells are removed.",
        "HIDDEN_QUADRUPLES" to "Four digits can only appear in four cells of a house; other candidates in those cells are removed.",
        // Fish and single-digit patterns
        "X_WING_FISH" to "Rows and columns form an X-Wing so a digit can be eliminated from the covering lines.",
        "X_WING" to "Rows and columns form an X-Wing so a digit can be eliminated from the covering lines.",
        "SKYSCRAPER_FISH" to "Two strong links on a digit form a skyscraper, allowing eliminations in the shared columns/rows.",
        "SKYSCRAPER" to "Two strong links on a digit form a skyscraper, allowing eliminations in the shared columns/rows.",
        "TWO_STRING_KITE_FISH" to "A two-string kite uses 2 strong links, with a weak link in the same box, to eliminate a candidate at the intersection between the two strong links.",
        "2_STRING_KITE" to "A two-string kite uses 2 strong links, with a weak link in the same box, to eliminate a candidate at the intersection between the two strong links.",
        "2_STRING_KITE_FISH" to "A two-string kite uses 2 strong links, with a weak link in the same box, to eliminate a candidate at the intersection between the two strong links.",
        "FINNED_X_WING_FISH" to "An X-Wing with a fin; cells seeing the fin can lose that digit.",
        "FINNED_X_WING" to "An X-Wing with a fin; cells seeing the fin can lose that digit.",
        "SASHIMI_X_WING_FISH" to "A sashimi X-Wing has a fin plus a missing base; cells seeing the fin can be eliminated.",
        "SASHIMI_X_WING" to "A sashimi X-Wing has a fin plus a missing base; cells seeing the fin can be eliminated.",
        "SWORDFISH_FISH" to "Three lines align candidates in three columns/rows, allowing eliminations (swordfish).",
        "SWORDFISH" to "Three lines align candidates in three columns/rows, allowing eliminations (swordfish).",
        "FINNED_SWORDFISH_FISH" to "A swordfish with a fin; cells seeing the fin lose that digit.",
        "FINNED_SWORDFISH" to "A swordfish with a fin; cells seeing the fin lose that digit.",
        "JELLYFISH_FISH" to "Four lines align candidates in four columns/rows, allowing eliminations (jellyfish).",
        "JELLYFISH" to "Four lines align candidates in four columns/rows, allowing eliminations (jellyfish).",
        "FINNED_JELLYFISH_FISH" to "A jellyfish with a fin; cells seeing the fin lose that digit.",
        "FINNED_JELLYFISH" to "A jellyfish with a fin; cells seeing the fin lose that digit.",
        "FRANKEN_X_WING_FISH" to "A Franken fish mixes boxes and lines to form the base/cover, enabling eliminations.",
        "FRANKEN_X_WING" to "A Franken fish mixes boxes and lines to form the base/cover, enabling eliminations.",
        "FINNED_FRANKEN_X_WING_FISH" to "A Franken fish with a fin; cells seeing the fin lose that digit.",
        "FINNED_FRANKEN_X_WING" to "A Franken fish with a fin; cells seeing the fin lose that digit.",
        "FINNED_MUTANT_X_WING_FISH" to "A mutant fish with a fin; eliminations come from cells seeing the fin.",
        "FINNED_MUTANT_X_WING" to "A mutant fish with a fin; eliminations come from cells seeing the fin.",
        "FRANKEN_SWORDFISH_FISH" to "A box/line swordfish variant enabling eliminations on the cover lines.",
        "FRANKEN_SWORDFISH" to "A box/line swordfish variant enabling eliminations on the cover lines.",
        "GROUPED_X_CYCLES" to "Grouped X-Cycles alternate strong and weak links on one digit to prove eliminations.",
        // Coloring, uniqueness, wings
        "SIMPLE_COLOURING" to "Color one digit into two sets of strong links; a contradiction color is eliminated.",
        "SIMPLE_COLORING" to "Color one digit into two sets of strong links; a contradiction color is eliminated.",
        "MEDUSA_3D" to "3D Medusa colors multiple digits; contradictions eliminate candidates or place values.",
        "3D_MEDUSA" to "3D Medusa colors multiple digits; contradictions eliminate candidates or place values.",
        "THREE_D_MEDUSA" to "3D Medusa colors multiple digits; contradictions eliminate candidates or place values.",
        "UNIQUE_RECTANGLE" to "Prevent a deadly pattern by forcing or eliminating around a four-cell rectangle.",
        "UNIQUE_RECTANGLES" to "Prevent a deadly pattern by forcing or eliminating around a four-cell rectangle.",
        "BUG" to "Bivalue Universal Grave: only one candidate breaks the stalemate; place or eliminate accordingly.",
        "BIVALUE_UNIVERSAL_GRAVE" to "Bivalue Universal Grave: only one candidate breaks the stalemate; place or eliminate accordingly.",
        "Y_WING" to "XY-Wing (hinge + two pincers) eliminates a shared candidate seen by both pincers.",
        "XY_WING" to "XY-Wing (hinge + two pincers) eliminates a shared candidate seen by both pincers.",
        "XYZ_WING" to "XYZ-Wing (triad with XYZ) removes Z from cells seeing all three cells.",
        "WXYZ_WING" to "WXYZ-Wing removes the shared candidate from peers of all four cells.",
        "W_WING" to "W-Wing links two matching bivalue cells through a strong link, forcing eliminations.",
        "L_3__WING" to "L-Wing with 3 cells links candidates to force eliminations.",
        "L_WING" to "L-Wing links candidates in an L-shaped pattern to force eliminations.",
        "EMPTY_RECTANGLE" to "An empty rectangle forces a conjugate pair interaction to eliminate a candidate.",
        "EMPTY_RECTANGLES" to "An empty rectangle forces a conjugate pair interaction to eliminate a candidate.",
        // Chains / advanced
        "X_CYCLES" to "X-Cycles alternate strong/weak links on one digit; contradictions remove candidates.",
        "X_CYCLE" to "X-Cycles alternate strong/weak links on one digit; contradictions remove candidates.",
        "XY_CHAIN" to "XY-Chains link bivalue cells; the target digit is eliminated where both ends see.",
        "AIC" to "Alternating Inference Chain proves eliminations via alternating strong/weak links.",
        "AIC_TYPE_2" to "Alternating Inference Chain (Type 2) proves eliminations via alternating strong/weak links.",
        "ALTERNATING_INFERENCE_CHAINS" to "Alternating Inference Chain proves eliminations via alternating strong/weak links.",
        "FORCING_CHAINS" to "Forcing chains explore both outcomes and keep the deduction common to all paths.",
        "RING" to "A ring structure of strong and weak links forces eliminations at junction points.",
        // ALS / Sue / Nishio
        "ALMOST_LOCKED_SETS" to "Almost Locked Sets connect via restricted candidates to force eliminations.",
        "ALS_XY" to "ALS-XY links two almost locked sets through a shared restricted candidate.",
        "ALS_XZ" to "ALS-XZ links two almost locked sets through shared restricted candidates.",
        "SUE_DE_COQ" to "Sue-de-Coq divides a box-line overlap into disjoint digit sets, forcing eliminations.",
        "NISHIO" to "Nishio assumes a digit placement and prunes branches that lead to contradiction.",
    )

    internal fun describeTechnique(name: String): String? {
        return techniqueDescriptions[normalizeTechniqueKey(name)]
    }

    internal fun missingDescriptionsForPriority(): List<String> {
        return techniquePriority.keys
            .map { normalizeTechniqueKey(it) }
            .distinct()
            .filterNot { techniqueDescriptions.containsKey(it) }
    }

    init {
        val missingDescriptions = techniquePriority.keys
            .map { normalizeTechniqueKey(it) }
            .filterNot { techniqueDescriptions.containsKey(it) }
            .distinct()
        if (missingDescriptions.isNotEmpty()) {
            println("WARN: Missing descriptions for techniques: ${missingDescriptions.joinToString(", ")}")
        }
    }
    
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

    private fun formatCellName(cell: Int): String {
        return "R${cell / 9 + 1}C${cell % 9 + 1}"
    }

    private fun summarizeEliminations(eliminations: List<EliminationDto>): String? {
        if (eliminations.isEmpty()) return null
        return eliminations.joinToString("; ") { elim ->
            val cells = elim.cells.joinToString(", ") { formatCellName(it) }
            "${elim.digit} from $cells"
        }
    }

    private fun summarizeSolvedCells(solvedCells: List<SolvedCellDto>): String? {
        if (solvedCells.isEmpty()) return null
        return solvedCells.joinToString("; ") { solved ->
            "${formatCellName(solved.cell)} = ${solved.digit}"
        }
    }

    private fun buildFallbackDescription(
        techniqueName: String,
        eliminations: List<EliminationDto>,
        solvedCells: List<SolvedCellDto>,
        match: TechniqueMatch
    ): String {
        val solvedSummary = summarizeSolvedCells(solvedCells)
        val eliminationSummary = summarizeEliminations(eliminations)

        return when {
            solvedSummary != null && eliminationSummary != null ->
                "$techniqueName places $solvedSummary and eliminates $eliminationSummary."
            solvedSummary != null -> "$techniqueName places $solvedSummary."
            eliminationSummary != null -> "$techniqueName eliminates $eliminationSummary."
            else -> match.toString()
        }
    }

    private fun getTechniqueDescription(
        technique: Technique,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>,
        solvedCells: List<SolvedCellDto>
    ): String {
        val candidates = listOf(technique.name, technique.getName())
        for (name in candidates) {
            val desc = describeTechnique(name)
            if (desc != null) return desc
        }

        val fallback = buildFallbackDescription(technique.getName(), eliminations, solvedCells, match)
        println("WARN: Missing description for technique ${technique.name}")
        return fallback
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
        
        val description = getTechniqueDescription(technique, match, eliminations, solvedCells)

        return TechniqueMatchDto(
            id = id,
            techniqueName = technique.getName(),
            description = description,
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
        val techniqueName = technique.getName()
        return when (match) {
            is AICMatch -> extractAICVisualData(match)
            is ALSMatch -> extractALSVisualData(match)
            is SubsetMatch -> extractSubsetVisualData(match)
            is FishMatch -> {
                if (techniqueName.contains("2-String Kite", ignoreCase = true) ||
                    (techniqueName.contains("Kite", ignoreCase = true) && !techniqueName.contains("String", ignoreCase = true))) {
                    extractKiteVisualData(match, techniqueName)
                } else {
                    extractFishVisualData(match, techniqueName)
                }
            }
            else -> {
                if (techniqueName.contains("Wing", ignoreCase = true) ||
                    techniqueName.contains("Fish", ignoreCase = true) ||
                    techniqueName.contains("Cycle", ignoreCase = true) ||
                    techniqueName.contains("Colour", ignoreCase = true) ||
                    techniqueName.contains("Color", ignoreCase = true) ||
                    techniqueName.contains("Medusa", ignoreCase = true) ||
                    techniqueName.contains("Rectangle", ignoreCase = true) ||
                    techniqueName.contains("Forcing", ignoreCase = true) ||
                    techniqueName.contains("Sue", ignoreCase = true) ||
                    techniqueName.contains("Nishio", ignoreCase = true) ||
                    techniqueName.contains("BUG", ignoreCase = true)
                ) {
                    return extractEliminationVisuals(match)
                }
                Triple(emptyList(), emptyList(), null)
            }
        }
    }

    private fun extractEliminationVisuals(match: TechniqueMatch): Triple<List<LineDto>, List<GroupDto>, String?> {
        val groups = mutableListOf<GroupDto>()
        match.eliminations.forEach { (digit, cells) ->
            val candidates = mutableListOf<CandidateLocationDto>()
            var cell = cells.nextSetBit(0)
            while (cell >= 0) {
                candidates.add(CandidateLocationDto(cell / 9, cell % 9, digit + 1))
                cell = cells.nextSetBit(cell + 1)
            }
            if (candidates.isNotEmpty()) {
                groups.add(
                    GroupDto(
                        candidates = candidates,
                        groupType = "elimination",
                        colorIndex = groups.size % 2
                    )
                )
            }
        }
        return Triple(emptyList(), groups, null)
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
            val isClaiming = techniqueName.contains("Claiming", ignoreCase = true)

            // For other fish (X-Wing, Swordfish, etc.), fall back to candidate-based visuals
            if (!isPointing && !isClaiming) {
                return extractEliminationVisuals(match)
            }

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
            techniqueName.contains("2-String Kite", ignoreCase = true) ||
            (techniqueName.contains("Kite", ignoreCase = true) && !techniqueName.contains("String", ignoreCase = true)) -> {
                steps.addAll(generateKiteSteps(techniqueName, match, eliminations))
            }
            techniqueName.contains("Fish", ignoreCase = true) ||
            techniqueName.contains("X-Wing", ignoreCase = true) ||
            techniqueName.contains("Swordfish", ignoreCase = true) ||
            techniqueName.contains("Jellyfish", ignoreCase = true) ||
            techniqueName.contains("Skyscraper", ignoreCase = true) -> {
                steps.addAll(generateFishSteps(techniqueName, match, eliminations))
            }
            techniqueName.contains("Wing", ignoreCase = true) -> {
                steps.addAll(generateWingSteps(techniqueName, match, eliminations))
            }
            techniqueName.contains("Cycle", ignoreCase = true) -> {
                steps.addAll(generateCycleSteps(techniqueName, eliminations))
            }
            techniqueName.contains("Colour", ignoreCase = true) ||
            techniqueName.contains("Color", ignoreCase = true) ||
            techniqueName.contains("Medusa", ignoreCase = true) -> {
                steps.addAll(generateColoringSteps(techniqueName, eliminations))
            }
            techniqueName.contains("Unique Rectangle", ignoreCase = true) -> {
                steps.addAll(generateUniqueRectangleSteps(techniqueName, eliminations))
            }
            techniqueName.equals("BUG", ignoreCase = true) || techniqueName.contains("Bivalue", ignoreCase = true) -> {
                steps.addAll(generateBugSteps(eliminations))
            }
            techniqueName.contains("Empty Rectangle", ignoreCase = true) -> {
                steps.addAll(generateEmptyRectangleSteps(techniqueName, eliminations))
            }
            techniqueName.contains("Sue", ignoreCase = true) -> {
                steps.addAll(generateSueDeCoqSteps(eliminations))
            }
            techniqueName.contains("Forcing", ignoreCase = true) -> {
                steps.addAll(generateForcingChainSteps(techniqueName, eliminations))
            }
            techniqueName.contains("Nishio", ignoreCase = true) -> {
                steps.addAll(generateNishioSteps(eliminations))
            }
            techniqueName.contains("Chain", ignoreCase = true) -> {
                steps.addAll(generateChainLikeSteps(techniqueName, eliminations))
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
    
    private fun eliminationCandidates(
        eliminations: List<EliminationDto>,
        color: String = "elimination"
    ): List<ColoredCandidateDto> {
        val candidates = mutableListOf<ColoredCandidateDto>()
        for (elim in eliminations) {
            for (cell in elim.cells) {
                candidates.add(
                    ColoredCandidateDto(
                        row = cell / 9,
                        col = cell % 9,
                        candidate = elim.digit,
                        colorType = color
                    )
                )
            }
        }
        return candidates
    }

    private fun generateFishSteps(
        techniqueName: String,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        val eliminationCells = eliminations.flatMap { it.cells }.distinct()
        
        // Extract fish pattern data
        var digit = eliminations.firstOrNull()?.digit ?: 0
        val baseIndices = mutableListOf<Int>()
        val coverIndices = mutableListOf<Int>()
        
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
            
            // Extract all base sectors
            var idx = baseSecs.nextSetBit(0)
            while (idx >= 0) {
                baseIndices.add(idx)
                idx = baseSecs.nextSetBit(idx + 1)
            }
            
            // Extract all cover sectors
            idx = coverSecs.nextSetBit(0)
            while (idx >= 0) {
                coverIndices.add(idx)
                idx = coverSecs.nextSetBit(idx + 1)
            }
        } catch (e: Exception) {
            // Fallback if reflection fails
        }
        
        // Determine base and cover types
        val baseType = if (baseIndices.isNotEmpty()) getSectorType(baseIndices.first()) else null
        val coverType = if (coverIndices.isNotEmpty()) getSectorType(coverIndices.first()) else null
        
        val baseTypeText = when (baseType) {
            "row" -> "rows"
            "column" -> "columns"
            "box" -> "boxes"
            else -> "lines"
        }
        
        val coverTypeText = when (coverType) {
            "row" -> "rows"
            "column" -> "columns"
            "box" -> "boxes"
            else -> "lines"
        }
        
        // Build region highlighting: base = primary (blue), cover = primary (blue)
        val baseRegions = baseIndices.map { idx ->
            ColoredRegionDto(baseType ?: "row", idx % 9, "primary")
        }
        val coverRegions = coverIndices.map { idx ->
            ColoredRegionDto(coverType ?: "row", idx % 9, "primary")
        }
        val allRegions = baseRegions + coverRegions
        
        // Get all cells in base sectors
        val baseCells = mutableListOf<Int>()
        for (idx in baseIndices) {
            baseCells.addAll(getSectorCells(idx))
        }
        
        // Get all cells in cover sectors
        val coverCells = mutableListOf<Int>()
        for (idx in coverIndices) {
            coverCells.addAll(getSectorCells(idx))
        }
        
        // Find intersection cells (where base and cover sectors meet - the actual X-Wing pattern)
        val intersectionCells = baseCells.filter { it in coverCells }.distinct()
        
        // Pattern cells are the intersection cells
        val patternCells = intersectionCells
        
        // Colored candidates: pattern cells get "target", elimination cells get "elimination"
        val patternCandidates = patternCells.map { cell ->
            ColoredCandidateDto(cell / 9, cell % 9, digit, "target")
        }
        
        val eliminationCandidates = eliminationCandidates(eliminations)
        
        // Build base line names
        val baseNames = baseIndices.map { idx ->
            when (baseType) {
                "row" -> "Row ${idx % 9 + 1}"
                "column" -> "Column ${idx % 9 + 1}"
                "box" -> "Box ${idx % 9 + 1}"
                else -> "Line ${idx + 1}"
            }
        }.joinToString(" and ")
        
        val coverNames = coverIndices.map { idx ->
            when (coverType) {
                "row" -> "Row ${idx % 9 + 1}"
                "column" -> "Column ${idx % 9 + 1}"
                "box" -> "Box ${idx % 9 + 1}"
                else -> "Line ${idx + 1}"
            }
        }.joinToString(" and ")
        
        // Step 1: Identify the pattern
        val patternDescription = when {
            techniqueName.contains("X-Wing", ignoreCase = true) -> 
                "In $baseNames, digit $digit appears in exactly ${baseIndices.size} $baseTypeText. " +
                "These candidates line up perfectly in $coverNames. " +
                "Because $digit must be placed in one of these positions in each base $baseTypeText, " +
                "it locks $digit into the highlighted $baseTypeText."
            
            techniqueName.contains("Swordfish", ignoreCase = true) -> 
                "In $baseNames, digit $digit appears in exactly three $baseTypeText. " +
                "These candidates align across three $coverTypeText: $coverNames. " +
                "The swordfish pattern locks $digit into these base $baseTypeText."
            
            techniqueName.contains("Jellyfish", ignoreCase = true) -> 
                "In $baseNames, digit $digit appears in exactly four $baseTypeText. " +
                "These candidates align across four $coverTypeText: $coverNames. " +
                "The jellyfish pattern locks $digit into these base $baseTypeText."
            
            else -> 
                "Digit $digit candidates align on base $baseTypeText ($baseNames) and cover $coverTypeText ($coverNames) to form a $techniqueName pattern."
        }
        
        // Colored cells for the intersection points (yellow border)
        val intersectionColoredCells = intersectionCells.map { ColoredCellDto(it, "warning") }
        
        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Identify the $techniqueName pattern",
                description = patternDescription,
                highlightCells = patternCells,
                regions = baseRegions,
                coloredCells = intersectionColoredCells,
                coloredCandidates = patternCandidates
            )
        )

        // Step 2: Explain why eliminations work
        if (eliminations.isNotEmpty()) {
            val eliminationExplanation = 
                "Since $digit is locked in $baseNames (highlighted in the base $baseTypeText), " +
                "any other $digit candidates in $coverNames must be false. " +
                "Eliminate $digit from cells in the cover $coverTypeText that aren't part of the pattern."
            
            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = "Eliminate from cover $coverTypeText",
                    description = eliminationExplanation,
                    highlightCells = eliminationCells,
                    regions = coverRegions,
                    coloredCandidates = patternCandidates + eliminationCandidates
                )
            )
            
            // Step 3: Show specific eliminations with pattern cells highlighted in green
            val eliminationNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")
            val eliminationColoredCells = eliminationCells.map { ColoredCellDto(it, "warning") }
            
            steps.add(
                ExplanationStepDto(
                    stepNumber = 3,
                    title = "Remove candidate $digit",
                    description = "Remove $digit from: $eliminationNames. These cells see the fish pattern and cannot contain $digit.",
                    highlightCells = eliminationCells,
                    regions = allRegions,
                    coloredCells = intersectionColoredCells + eliminationColoredCells,
                    coloredCandidates = patternCandidates + eliminationCandidates
                )
            )
        }

        return steps
    }

    /**
     * Extract visual data for 2-String Kite (show strong link between kite cells)
     */
    private fun extractKiteVisualData(match: TechniqueMatch, techniqueName: String): Triple<List<LineDto>, List<GroupDto>, String?> {
        val lines = mutableListOf<LineDto>()
        val groups = mutableListOf<GroupDto>()

        var kiteCells: List<Int> = emptyList()
        var rowIndex: Int? = null
        var colIndex: Int? = null
        var rowFin: Int? = null
        var colFin: Int? = null

        try {
            val matchClass = match.javaClass
            val digitField = matchClass.getDeclaredField("digit")
            val baseSecsField = matchClass.getDeclaredField("baseSecs")
            val coverSecsField = matchClass.getDeclaredField("coverSecs")
            val finsField = matchClass.getDeclaredField("fins")

            digitField.isAccessible = true
            baseSecsField.isAccessible = true
            coverSecsField.isAccessible = true
            finsField.isAccessible = true

            val digit = (digitField.get(match) as Int) + 1 // Convert to 1-9
            val baseSecs = baseSecsField.get(match) as java.util.BitSet
            val coverSecs = coverSecsField.get(match) as java.util.BitSet
            val fins = finsField.get(match) as java.util.BitSet

            // Collect base and cover sectors
            val baseIndices = mutableListOf<Int>()
            var idx = baseSecs.nextSetBit(0)
            while (idx >= 0) {
                baseIndices.add(idx)
                idx = baseSecs.nextSetBit(idx + 1)
            }

            val coverIndices = mutableListOf<Int>()
            idx = coverSecs.nextSetBit(0)
            while (idx >= 0) {
                coverIndices.add(idx)
                idx = coverSecs.nextSetBit(idx + 1)
            }

            // Derive row/col from base sectors
            rowIndex = baseIndices.firstOrNull { it < 9 }
            colIndex = baseIndices.firstOrNull { it in 9..17 }?.let { it - 9 }

            // Extract fin cells (these are CELL indices 0-80!)
            // The commented AIC in TwoStringKite.java shows:
            // Node 0: rowOuties (cell in row, outside box) = rowFin
            // Node 1: difference(inRow, rowOuties) (cell in row, inside box)
            // Node 2: difference(inCol, colOuties) (cell in column, inside box)
            // Node 3: colOuties (cell in column, outside box) = colFin
            
            val finCells = mutableListOf<Int>()
            var finIdx = fins.nextSetBit(0)
            while (finIdx >= 0) {
                finCells.add(finIdx)
                finIdx = fins.nextSetBit(finIdx + 1)
            }
            
            // Identify which fin is in the row and which is in the column
            // rowFin: in row rowIndex, but NOT in column colIndex (outside box in that row)
            // colFin: in column colIndex, but NOT in row rowIndex (outside box in that column)
            if (finCells.size >= 2 && rowIndex != null && colIndex != null) {
                for (cellIdx in finCells) {
                    val cellRow = cellIdx / 9
                    val cellCol = cellIdx % 9
                    if (cellRow == rowIndex && cellCol != colIndex) {
                        rowFin = cellIdx  // This cell is in the row (outside box)
                    } else if (cellCol == colIndex && cellRow != rowIndex) {
                        colFin = cellIdx  // This cell is in the column (outside box)
                    }
                }
            }

            // Cells in base (row/col) and cover (box)
            val baseCells = baseIndices.flatMap { getSectorCells(it) }
            val coverCells = coverIndices.flatMap { getSectorCells(it) }
            
            // Get the box cells (coverCells is the actual box)
            // Use coverCells directly, not baseCells filtered
            
            // Get all cells in the row and column  
            val rowCells = if (rowIndex != null) getSectorCells(rowIndex) else emptyList()
            val colCells = if (colIndex != null) getSectorCells(colIndex + 9) else emptyList()
            
            // The row/col intersection cell (which must NOT have the digit)
            val rowColIntersection = if (rowIndex != null && colIndex != null) {
                rowIndex * 9 + colIndex
            } else null
            
            // The difference operations from the AIC:
            // inRow \ rowOuties = cells in row that are IN THE BOX and are NOT rowFin
            // inCol \ colOuties = cells in column that are IN THE BOX and are NOT colFin
            
            val rowCellsInBox = if (rowFin != null && rowIndex != null) {
                // Cells in the row, inside THE COVER BOX, but NOT the rowFin or intersection
                rowCells.filter { cell ->
                    cell in coverCells && 
                    cell != rowFin && 
                    cell != rowColIntersection
                }
            } else emptyList()
            
            val colCellsInBox = if (colFin != null && colIndex != null) {
                // Cells in the column, inside THE COVER BOX, but NOT the colFin or intersection
                colCells.filter { cell ->
                    cell in coverCells && 
                    cell != colFin && 
                    cell != rowColIntersection
                }
            } else emptyList()

            // The kite cells ARE the fin cells (they're already cell indices)
            if (rowFin != null && colFin != null) {
                kiteCells = listOf(rowFin, colFin)
            } else if (finCells.size >= 2) {
                // Fallback: use the fin cells directly
                kiteCells = finCells.take(2)
            } else {
                // Double fallback: find cells in base sectors that are NOT in cover sectors
                kiteCells = baseCells.filter { it !in coverCells }.distinct().take(2)
            }

            if (kiteCells.size >= 2 && rowIndex != null && colIndex != null) {
                val c1 = kiteCells[0]
                val c2 = kiteCells[1]
                val r1 = c1 / 9
                val c1c = c1 % 9
                val r2 = c2 / 9
                val c2c = c2 % 9

                // Use the computed cells from the difference operations
                // The two cells in the box MUST be in the same box (for the weak link)
                val rowCellInBox = rowCellsInBox.lastOrNull()
                
                val colCellInBox = if (colCellsInBox.size > 1 && rowCellInBox != null) {
                    // Pick the colCellInBox that's in the same box as rowCellInBox
                    val rowCellBox = (rowCellInBox / 27) * 3 + ((rowCellInBox % 9) / 3)
                    colCellsInBox.firstOrNull { cell ->
                        val cellBox = (cell / 27) * 3 + ((cell % 9) / 3)
                        cellBox == rowCellBox
                    } ?: colCellsInBox.firstOrNull()
                } else {
                    colCellsInBox.firstOrNull()
                }

                // Strong link in the row (between kite endpoint and cell in box)
                if (rowCellInBox != null) {
                    val rr = rowCellInBox / 9
                    val rc = rowCellInBox % 9
                    lines.add(
                        LineDto(
                            from = CandidateLocationDto(r1, c1c, digit),
                            to = CandidateLocationDto(rr, rc, digit),
                            isStrongLink = true,
                            lineType = "kite-strong-row",
                            description = "Strong link: if $digit is not in R${r1+1}C${c1c+1}, it must be in R${rr+1}C${rc+1}"
                        )
                    )
                }

                // Strong link in the column (between kite endpoint and cell in box)
                if (colCellInBox != null) {
                    val cr = colCellInBox / 9
                    val cc = colCellInBox % 9
                    lines.add(
                        LineDto(
                            from = CandidateLocationDto(r2, c2c, digit),
                            to = CandidateLocationDto(cr, cc, digit),
                            isStrongLink = true,
                            lineType = "kite-strong-col",
                            description = "Strong link: if $digit is not in R${r2+1}C${c2c+1}, it must be in R${cr+1}C${cc+1}"
                        )
                    )
                }

                // Weak link (dashed) connecting the two cells in the box
                if (rowCellInBox != null && colCellInBox != null) {
                    val rr = rowCellInBox / 9
                    val rc = rowCellInBox % 9
                    val cr = colCellInBox / 9
                    val cc = colCellInBox % 9
                    lines.add(
                        LineDto(
                            from = CandidateLocationDto(rr, rc, digit),
                            to = CandidateLocationDto(cr, cc, digit),
                            isStrongLink = false,
                            lineType = "kite-weak",
                            description = "Weak link: $digit cannot be in both R${rr+1}C${rc+1} and R${cr+1}C${cc+1}"
                        )
                    )
                }

                // Group each kite endpoint
                groups.add(
                    GroupDto(
                        candidates = listOf(CandidateLocationDto(r1, c1c, digit)),
                        groupType = "kite-end",
                        colorIndex = 0
                    )
                )
                groups.add(
                    GroupDto(
                        candidates = listOf(CandidateLocationDto(r2, c2c, digit)),
                        groupType = "kite-end",
                        colorIndex = 1
                    )
                )
            }
        } catch (e: Exception) {
            // Fallback: no lines/groups
        }

        return Triple(lines, groups, null)
    }

    /**
     * Generate step-by-step explanation for 2-String Kite
     */
    private fun generateKiteSteps(
        techniqueName: String,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        val eliminationCells = eliminations.flatMap { it.cells }.distinct()

        // Extract kite pattern data
        var digit = eliminations.firstOrNull()?.digit ?: 0
        val baseIndices = mutableListOf<Int>()
        val coverIndices = mutableListOf<Int>()
        var rowIndex: Int? = null
        var colIndex: Int? = null
        var fins = java.util.BitSet()

        try {
            val matchClass = match.javaClass
            val digitField = matchClass.getDeclaredField("digit")
            val baseSecsField = matchClass.getDeclaredField("baseSecs")
            val coverSecsField = matchClass.getDeclaredField("coverSecs")
            val finsField = matchClass.getDeclaredField("fins")

            digitField.isAccessible = true
            baseSecsField.isAccessible = true
            coverSecsField.isAccessible = true
            finsField.isAccessible = true

            digit = (digitField.get(match) as Int) + 1 // Convert to 1-9
            val baseSecs = baseSecsField.get(match) as java.util.BitSet
            val coverSecs = coverSecsField.get(match) as java.util.BitSet
            fins = finsField.get(match) as java.util.BitSet

            // Extract all base sectors
            var idx = baseSecs.nextSetBit(0)
            while (idx >= 0) {
                baseIndices.add(idx)
                idx = baseSecs.nextSetBit(idx + 1)
            }

            // Extract all cover sectors
            idx = coverSecs.nextSetBit(0)
            while (idx >= 0) {
                coverIndices.add(idx)
                idx = coverSecs.nextSetBit(idx + 1)
            }

            // Derive row/col from base sectors
            rowIndex = baseIndices.firstOrNull { it < 9 }
            colIndex = baseIndices.firstOrNull { it in 9..17 }?.let { it - 9 }
        } catch (e: Exception) {
            // Fallback if reflection fails
        }

        // Determine base and cover types
        val baseType = if (baseIndices.isNotEmpty()) getSectorType(baseIndices.first()) else null
        val coverType = if (coverIndices.isNotEmpty()) getSectorType(coverIndices.first()) else null

        // Get cells in base and cover sectors
        val baseCells = mutableListOf<Int>()
        for (idx in baseIndices) {
            baseCells.addAll(getSectorCells(idx))
        }
        val coverCells = mutableListOf<Int>()
        for (idx in coverIndices) {
            coverCells.addAll(getSectorCells(idx))
        }

        // Use the same logic as extractKiteVisualData to identify the pattern cells
        // Extract fin cells
        val finCells = mutableListOf<Int>()
        var finIdx = fins.nextSetBit(0)
        while (finIdx >= 0) {
            finCells.add(finIdx)
            finIdx = fins.nextSetBit(finIdx + 1)
        }
        
        // Identify which fin is in the row and which is in the column
        // rowFin: in row rowIndex, but NOT in column colIndex (outside box in that row)
        // colFin: in column colIndex, but NOT in row rowIndex (outside box in that column)
        var rowFin: Int? = null
        var colFin: Int? = null
        if (finCells.size >= 2 && rowIndex != null && colIndex != null) {
            for (cellIdx in finCells) {
                val cellRow = cellIdx / 9
                val cellCol = cellIdx % 9
                if (cellRow == rowIndex && cellCol != colIndex) {
                    rowFin = cellIdx  // This cell is in the row (outside box)
                } else if (cellCol == colIndex && cellRow != rowIndex) {
                    colFin = cellIdx  // This cell is in the column (outside box)
                }
            }
        }

        // Calculate the actual kite endpoint cells
        val kiteCells = if (rowFin != null && colFin != null) {
            listOf(rowFin, colFin)
        } else if (finCells.size >= 2) {
            finCells.take(2)
        } else {
            // Fallback: find cells in base sectors that are NOT in cover sectors
            baseCells.filter { it !in coverCells }.distinct().take(2)
        }

        // Only show the cover region (box), not all the base regions
        val coverRegions = coverIndices.map { idx ->
            ColoredRegionDto(coverType ?: "box", idx % 9, "secondary")
        }
        val allRegions = coverRegions  // Only highlight the box

        // Get all cells in the row and column
        val rowCells = if (rowIndex != null) getSectorCells(rowIndex) else emptyList()
        val colCells = if (colIndex != null) getSectorCells(colIndex + 9) else emptyList()
        
        // The row/col intersection cell (which must NOT have the digit)
        val rowColIntersection = if (rowIndex != null && colIndex != null) {
            rowIndex * 9 + colIndex
        } else null
        
        // The difference operations from the AIC chain:
        // inRow \ rowOuties = cells in row that are IN THE BOX and are NOT rowFin
        // inCol \ colOuties = cells in column that are IN THE BOX and are NOT colFin
        val rowCellsInBox = if (rowFin != null && rowIndex != null) {
            // Cells in the row, inside THE COVER BOX, but NOT the rowFin or intersection
            rowCells.filter { cell ->
                cell in coverCells && 
                cell != rowFin && 
                cell != rowColIntersection
            }
        } else emptyList()
        
        val colCellsInBox = if (colFin != null && colIndex != null) {
            // Cells in the column, inside THE COVER BOX, but NOT the colFin or intersection
            colCells.filter { cell ->
                cell in coverCells && 
                cell != colFin && 
                cell != rowColIntersection
            }
        } else emptyList()
        
        // The two cells in the box MUST be in the same box (for the weak link)
        val rowCellInBox = rowCellsInBox.lastOrNull()
        
        val colCellInBox = if (colCellsInBox.size > 1 && rowCellInBox != null) {
            // Pick the colCellInBox that's in the same box as rowCellInBox
            val rowCellBox = (rowCellInBox / 27) * 3 + ((rowCellInBox % 9) / 3)
            colCellsInBox.firstOrNull { cell ->
                val cellBox = (cell / 27) * 3 + ((cell % 9) / 3)
                cellBox == rowCellBox
            } ?: colCellsInBox.firstOrNull()
        } else {
            colCellsInBox.firstOrNull()
        }
        
        val boxCells = listOfNotNull(rowCellInBox, colCellInBox)

        // All cells in the kite pattern (4 cells total: 2 endpoints + 2 in box)
        val allPatternCells = kiteCells + boxCells

        // Colored cells for kite endpoints (yellow borders)
        val kiteColoredCells = kiteCells.map { ColoredCellDto(it, "warning") }

        // Colored candidates: highlight all 4 cells in the kite pattern
        val patternCandidates = allPatternCells.map { cell ->
            val colorType = when (cell) {
                in kiteCells -> "target"  // Kite endpoints in green
                else -> "highlight"  // Box cells in yellow
            }
            ColoredCandidateDto(cell / 9, cell % 9, digit, colorType)
        }

        val eliminationCandidates = eliminationCandidates(eliminations)

        // Build sector names
        val baseNames = baseIndices.map { idx ->
            when (baseType) {
                "row" -> "Row ${idx % 9 + 1}"
                "column" -> "Column ${idx % 9 + 1}"
                else -> "Base ${idx + 1}"
            }
        }

        val coverNames = coverIndices.map { idx ->
            when (coverType) {
                "box" -> "Box ${idx % 9 + 1}"
                "row" -> "Row ${idx % 9 + 1}"
                "column" -> "Column ${idx % 9 + 1}"
                else -> "Cover ${idx + 1}"
            }
        }

        val baseNamesText = if (baseNames.isNotEmpty()) baseNames.joinToString(" and ") else "the base line"
        val coverNamesText = if (coverNames.isNotEmpty()) coverNames.joinToString(" and ") else "the cover box"

        val kiteNames = kiteCells.map { formatCellName(it) }
        val kiteNamesText = kiteNames.joinToString(" and ")
        val firstCell = kiteNames.firstOrNull() ?: "the first cell"
        val secondCell = kiteNames.getOrNull(1) ?: "the second cell"

        // Step 1: Identify the pattern
        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Find the kite pattern",
                description = "Look at the two green $digit candidates at $kiteNamesText. " +
                    "These are the endpoints of the kite. They're connected through $coverNamesText, " +
                    "which contains two more $digit candidates (shown in yellow). " +
                    "Think of it like this: one of the green cells MUST have $digit, but they can't BOTH have it.",
                highlightCells = kiteCells,
                regions = allRegions,  // Only show box
                coloredCells = kiteColoredCells,
                coloredCandidates = patternCandidates
            )
        )

        // Step 2: Explain the logic with lines showing the kite pattern
        if (eliminations.isNotEmpty()) {
            val eliminationNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")
            
            // Build lines for Step 2 to show the kite pattern
            val step2Lines = mutableListOf<LineDto>()
            val step2Groups = mutableListOf<GroupDto>()
            
            // Need to identify which fin is which for drawing correct lines
            val rowFinCell = if (rowFin != null && rowFin in kiteCells) rowFin else null
            val colFinCell = if (colFin != null && colFin in kiteCells) colFin else null
            
            if (rowFinCell != null && colFinCell != null && rowIndex != null && colIndex != null) {
                val rowFinRow = rowFinCell / 9
                val rowFinCol = rowFinCell % 9
                val colFinRow = colFinCell / 9
                val colFinCol = colFinCell % 9

                // Strong link in the row (between rowFin and cell in box)
                if (rowCellInBox != null) {
                    val rr = rowCellInBox / 9
                    val rc = rowCellInBox % 9
                    step2Lines.add(
                        LineDto(
                            from = CandidateLocationDto(rowFinRow, rowFinCol, digit),
                            to = CandidateLocationDto(rr, rc, digit),
                            isStrongLink = true,
                            lineType = "kite-strong-row",
                            description = "Strong link: if $digit is not in R${rowFinRow+1}C${rowFinCol+1}, it must be in R${rr+1}C${rc+1}"
                        )
                    )
                }

                                // Weak link connecting the two cells in the box
                if (rowCellInBox != null && colCellInBox != null) {
                    val rr = rowCellInBox / 9
                    val rc = rowCellInBox % 9
                    val cr = colCellInBox / 9
                    val cc = colCellInBox % 9
                    step2Lines.add(
                        LineDto(
                            from = CandidateLocationDto(rr, rc, digit),
                            to = CandidateLocationDto(cr, cc, digit),
                            isStrongLink = false,
                            lineType = "kite-weak",
                            description = "Weak link: $digit cannot be in both R${rr+1}C${rc+1} and R${cr+1}C${cc+1}"
                        )
                    )
                }

                // Strong link in the column (between cell in box and colFin)
                if (colCellInBox != null) {
                    val cr = colCellInBox / 9
                    val cc = colCellInBox % 9
                    step2Lines.add(
                        LineDto(
                            from = CandidateLocationDto(cr, cc, digit),
                            to = CandidateLocationDto(colFinRow, colFinCol, digit),
                            isStrongLink = true,
                            lineType = "kite-strong-col",
                            description = "Strong link: if $digit is not in R${cr+1}C${cc+1}, it must be in R${colFinRow+1}C${colFinCol+1}"
                        )
                    )
                }



                // Group each kite endpoint
                step2Groups.add(
                    GroupDto(
                        candidates = listOf(CandidateLocationDto(rowFinRow, rowFinCol, digit)),
                        groupType = "kite-end",
                        colorIndex = 0
                    )
                )
                step2Groups.add(
                    GroupDto(
                        candidates = listOf(CandidateLocationDto(colFinRow, colFinCol, digit)),
                        groupType = "kite-end",
                        colorIndex = 1
                    )
                )
            }
            
            // Build interactive chain description matching the visual line order
            val chainDescription = buildString {
                if (rowFinCell != null && colFinCell != null && rowCellInBox != null && colCellInBox != null) {
                    // The chain follows the kite pattern
                    append("($digit)${formatCellName(rowFinCell)}")
                    append(" --[strong]--> ($digit)${formatCellName(rowCellInBox)}")
                    append(" --[weak]--> ($digit)${formatCellName(colCellInBox)}")
                    append(" --[strong]--> ($digit)${formatCellName(colFinCell)}")
                }
            }
            
            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = "Follow the chain",
                    description = "The kite forms a chain: $chainDescription. " +
                    "The solid lines indicate strong links, " +
                    "and the dashed line indacates a weak link. " +
                    "No matter which green cell has $digit, " +
                    "the weak link ensures that at least one of the yellow cells in the box will have it. " +
                    "Any cell that can see BOTH green cells at $kiteNamesText cannot have $digit.",
                    highlightCells = kiteCells + eliminationCells,
                    regions = allRegions,
                    coloredCells = kiteColoredCells,
                    coloredCandidates = patternCandidates,
                    lines = step2Lines,
                    groups = step2Groups
                )
            )

            // Step 3: Show eliminations - highlight the row and column that form the kite intersection
            val eliminationRegions = eliminationCells.flatMap { cell ->
                val row = cell / 9
                val col = cell % 9
                listOf(
                    ColoredRegionDto("row", row, "secondary"),
                    ColoredRegionDto("column", col, "secondary")
                )
            }.distinctBy { "${it.type}-${it.index}" }
            
            steps.add(
                ExplanationStepDto(
                    stepNumber = 3,
                    title = "Make the elimination",
                    description = "Since $eliminationNames can see both green cells, " +
                        "we know $digit cannot go there. Eliminate $digit from: $eliminationNames.",
                    highlightCells = eliminationCells,
                    regions = eliminationRegions,
                    coloredCells = kiteColoredCells + eliminationCells.map { ColoredCellDto(it, "warning") },
                    coloredCandidates = patternCandidates + eliminationCandidates
                )
            )
        }

        return steps
    }

    private data class WingMetadata(
        val pivotCells: List<Int> = emptyList(),
        val pincerCells: List<Int> = emptyList(),
        val otherCells: List<Int> = emptyList(),
        val digits: List<Int> = emptyList()
    ) {
        val allCells: List<Int> = (pivotCells + pincerCells + otherCells).distinct()
    }

    private fun detectWingType(techniqueName: String): String {
        val lower = techniqueName.lowercase()
        return when {
            lower.contains("wxyz") -> "WXYZ-Wing"
            lower.contains("xyz") -> "XYZ-Wing"
            lower.contains("w-wing") || lower.contains("w wing") -> "W-Wing"
            lower.contains("xy") || lower.contains("y-wing") -> "XY-Wing"
            else -> techniqueName
        }
    }

    private fun extractWingMetadata(match: TechniqueMatch): WingMetadata {
        val pivotCells = mutableListOf<Int>()
        val pincerCells = mutableListOf<Int>()
        val otherCells = mutableListOf<Int>()
        val digits = mutableListOf<Int>()

        try {
            val matchClass = match.javaClass
            for (field in matchClass.declaredFields) {
                try {
                    field.isAccessible = true
                    val name = field.name.lowercase()
                    val value = field.get(match)

                    fun addCells(target: MutableList<Int>, cells: List<Int>) {
                        target.addAll(cells)
                    }

                    when (value) {
                        is java.util.BitSet -> {
                            val list = bitSetToList(value)
                            when {
                                name.contains("digit") -> digits.addAll(list.map { it + 1 })
                                name.contains("hinge") || name.contains("pivot") -> addCells(pivotCells, list)
                                name.contains("pincer") || name.contains("wing") -> addCells(pincerCells, list)
                                name.contains("cell") -> addCells(otherCells, list)
                            }
                        }
                        is IntArray -> {
                            val list = value.toList()
                            when {
                                name.contains("digit") -> digits.addAll(list.map { it + 1 })
                                name.contains("hinge") || name.contains("pivot") -> addCells(pivotCells, list)
                                name.contains("pincer") || name.contains("wing") -> addCells(pincerCells, list)
                                name.contains("cell") -> addCells(otherCells, list)
                            }
                        }
                        is Int -> {
                            when {
                                name.contains("digit") -> digits.add(value + 1)
                                name.contains("hinge") || name.contains("pivot") -> addCells(pivotCells, listOf(value))
                                name.contains("pincer") || name.contains("wing") -> addCells(pincerCells, listOf(value))
                                name.contains("cell") -> addCells(otherCells, listOf(value))
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Ignore individual field extraction issues
                }
            }
        } catch (_: Exception) {
            // Ignore reflection issues and fall back to eliminations only
        }

        return WingMetadata(
            pivotCells = pivotCells.distinct(),
            pincerCells = pincerCells.distinct(),
            otherCells = otherCells.distinct(),
            digits = digits.distinct()
        )
    }

    private fun buildWingRegions(pivotCells: List<Int>, pincerCells: List<Int>): List<ColoredRegionDto> {
        val regions = mutableSetOf<Pair<String, Int>>()

        for (pivot in pivotCells) {
            val pivotRow = pivot / 9
            val pivotCol = pivot % 9
            val pivotBox = (pivotRow / 3) * 3 + (pivotCol / 3)

            for (pincer in pincerCells) {
                val row = pincer / 9
                val col = pincer % 9
                val box = (row / 3) * 3 + (col / 3)

                if (row == pivotRow) regions.add("row" to row)
                if (col == pivotCol) regions.add("column" to col)
                if (box == pivotBox) regions.add("box" to box)
            }
        }

        return regions.map { (type, index) -> ColoredRegionDto(type, index, "primary") }
    }

    private fun generateWingSteps(
        techniqueName: String,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        val eliminationCells = eliminations.flatMap { it.cells }.distinct()
        val targetDigit = eliminations.firstOrNull()?.digit
        val wingType = detectWingType(techniqueName)

        val metadata = extractWingMetadata(match)
        val wingCells = if (metadata.allCells.isNotEmpty()) metadata.allCells else eliminationCells
        val pivotCells = if (metadata.pivotCells.isNotEmpty()) metadata.pivotCells else wingCells.take(1)
        val pincerCells = metadata.pincerCells
        val supportingCells = metadata.otherCells.filterNot { pivotCells.contains(it) || pincerCells.contains(it) }
        val wingDigits = if (metadata.digits.isNotEmpty()) metadata.digits else listOfNotNull(targetDigit)

        val coloredCells = mutableListOf<ColoredCellDto>()
        pivotCells.forEach { coloredCells.add(ColoredCellDto(it, "warning")) }
        pincerCells.forEach { coloredCells.add(ColoredCellDto(it, "info")) }
        supportingCells.forEach { coloredCells.add(ColoredCellDto(it, "secondary")) }

        val linkRegions = buildWingRegions(pivotCells, pincerCells)

        val targetCandidates = mutableListOf<ColoredCandidateDto>()
        if (targetDigit != null) {
            val candidateCells = when (wingType) {
                "XY-Wing", "W-Wing" -> if (pincerCells.isNotEmpty()) pincerCells else wingCells
                else -> if (wingCells.isNotEmpty()) wingCells else eliminationCells
            }
            candidateCells.forEach { cell ->
                targetCandidates.add(ColoredCandidateDto(cell / 9, cell % 9, targetDigit, "target"))
            }
        }

        val wingDigitText = if (wingDigits.isNotEmpty()) wingDigits.joinToString(", ") else "the shared digit"
        val pivotText = if (pivotCells.isNotEmpty()) {
            "hinge ${pivotCells.joinToString(", ") { formatCellName(it) }}"
        } else {
            "a hinge cell"
        }
        val pincerText = if (pincerCells.isNotEmpty()) {
            "pincers ${pincerCells.joinToString(", ") { formatCellName(it) }}"
        } else {
            "two pincers"
        }

        val introDescription = when (wingType) {
            "XY-Wing" -> "$wingType uses a $pivotText with two candidates. Each of the $pincerText shares one candidate with the hinge and they see each other, so any cell seeing both pincers cannot keep $wingDigitText."
            "XYZ-Wing" -> "$wingType keeps all three digits in the hinge. The two pincers each match two of those digits, so the third digit ($wingDigitText) is forced out of any cell seeing all three."
            "WXYZ-Wing" -> "$wingType spreads four digits over four cells. One digit is common to all, and any cell that can see every wing cell must drop $wingDigitText."
            "W-Wing" -> "$wingType links two matching bivalue cells through a strong link on one digit, forcing the other digit to be eliminated where both cells look."
            else -> "$techniqueName links a hinge cell to two pincers; the shared candidate can be removed where both pincers see."
        }

        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Spot the $wingType shape",
                description = introDescription,
                highlightCells = if (wingCells.isNotEmpty()) wingCells else eliminationCells,
                regions = linkRegions,
                coloredCells = coloredCells,
                coloredCandidates = targetCandidates
            )
        )

        if (pincerCells.isNotEmpty() || eliminationCells.isNotEmpty()) {
            val seeingText = if (pincerCells.isNotEmpty()) {
                "Any cell that sees both pincers must drop $wingDigitText."
            } else {
                "Cells that see the highlighted wing must drop $wingDigitText."
            }
            val eliminationDesc = summarizeEliminations(eliminations)
            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = "Where the pincers meet",
                    description = eliminationDesc ?: seeingText,
                    highlightCells = if (eliminationCells.isNotEmpty()) eliminationCells else wingCells,
                    regions = linkRegions,
                    coloredCells = coloredCells,
                    coloredCandidates = targetCandidates + eliminationCandidates(eliminations)
                )
            )
        }

        if (eliminationCells.isNotEmpty()) {
            val eliminationNames = eliminationCells.joinToString(", ") { formatCellName(it) }
            val elimDigit = targetDigit?.toString() ?: wingDigitText
            steps.add(
                ExplanationStepDto(
                    stepNumber = 3,
                    title = "Eliminate the shared candidate",
                    description = "Because both pincers cover the same spots, remove $elimDigit from $eliminationNames.",
                    highlightCells = eliminationCells,
                    regions = linkRegions,
                    coloredCandidates = targetCandidates + eliminationCandidates(eliminations)
                )
            )
        }

        return steps
    }

    private fun generateCycleSteps(
        techniqueName: String,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        val eliminationCells = eliminations.flatMap { it.cells }

        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Follow the cycle",
                description = "$techniqueName alternates strong and weak links on one digit; any contradiction forces eliminations.",
                highlightCells = eliminationCells
            )
        )

        if (eliminations.isNotEmpty()) {
            val eliminationDesc = summarizeEliminations(eliminations)
            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = "Apply eliminations",
                    description = eliminationDesc ?: "Remove the digit from the highlighted cells reached by the weak links.",
                    highlightCells = eliminationCells,
                    coloredCandidates = eliminationCandidates(eliminations)
                )
            )
        }

        return steps
    }

    private fun generateColoringSteps(
        techniqueName: String,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        val eliminationCells = eliminations.flatMap { it.cells }

        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Color the candidate",
                description = "$techniqueName splits the candidate into two color sets along strong links; any cell seeing both colors is invalid.",
                highlightCells = eliminationCells
            )
        )

        if (eliminations.isNotEmpty()) {
            val eliminationDesc = summarizeEliminations(eliminations)
            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = "Remove the conflict color",
                    description = eliminationDesc ?: "Cells seeing both colors cannot keep the candidate; remove it.",
                    highlightCells = eliminationCells,
                    coloredCandidates = eliminationCandidates(eliminations)
                )
            )
        }

        return steps
    }

    private fun generateUniqueRectangleSteps(
        techniqueName: String,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        val eliminationCells = eliminations.flatMap { it.cells }

        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Avoid the deadly pattern",
                description = "$techniqueName finds four cells that could form two solutions; adjust one cell to break the rectangle.",
                highlightCells = eliminationCells
            )
        )

        if (eliminations.isNotEmpty()) {
            val eliminationDesc = summarizeEliminations(eliminations)
            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = "Eliminate or place to break uniqueness",
                    description = eliminationDesc ?: "Use the marked cell(s) to prevent the deadly rectangle.",
                    highlightCells = eliminationCells,
                    coloredCandidates = eliminationCandidates(eliminations)
                )
            )
        }

        return steps
    }

    private fun generateBugSteps(
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        val eliminationCells = eliminations.flatMap { it.cells }

        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Spot the BUG pattern",
                description = "A BUG leaves every unsolved cell with two candidates except one inconsistency. Resolve that cell to break the pattern.",
                highlightCells = eliminationCells
            )
        )

        if (eliminations.isNotEmpty()) {
            val eliminationDesc = summarizeEliminations(eliminations)
            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = "Resolve the contradiction",
                    description = eliminationDesc ?: "Clear the conflicting candidate shown in the highlighted cells.",
                    highlightCells = eliminationCells,
                    coloredCandidates = eliminationCandidates(eliminations)
                )
            )
        }

        return steps
    }

    private fun generateEmptyRectangleSteps(
        techniqueName: String,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        val eliminationCells = eliminations.flatMap { it.cells }

        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Find the empty rectangle",
                description = "A box has only one candidate on a row/column; combined with a conjugate pair it triggers eliminations.",
                highlightCells = eliminationCells
            )
        )

        if (eliminations.isNotEmpty()) {
            val eliminationDesc = summarizeEliminations(eliminations)
            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = "Use the conjugate to eliminate",
                    description = eliminationDesc ?: "Remove the digit from peers of the conjugate pair.",
                    highlightCells = eliminationCells,
                    coloredCandidates = eliminationCandidates(eliminations)
                )
            )
        }

        return steps
    }

    private fun generateSueDeCoqSteps(
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        val eliminationCells = eliminations.flatMap { it.cells }

        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Partition the overlap",
                description = "Sue-de-Coq splits the box-line overlap into disjoint digit sets, forcing eliminations around it.",
                highlightCells = eliminationCells
            )
        )

        if (eliminations.isNotEmpty()) {
            val eliminationDesc = summarizeEliminations(eliminations)
            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = "Eliminate outside the partition",
                    description = eliminationDesc ?: "Remove digits that conflict with the partitioned sets.",
                    highlightCells = eliminationCells,
                    coloredCandidates = eliminationCandidates(eliminations)
                )
            )
        }

        return steps
    }

    private fun generateForcingChainSteps(
        techniqueName: String,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        val eliminationCells = eliminations.flatMap { it.cells }

        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Branch both possibilities",
                description = "$techniqueName explores both outcomes from a start node; any conclusion common to all branches is forced.",
                highlightCells = eliminationCells
            )
        )

        if (eliminations.isNotEmpty()) {
            val eliminationDesc = summarizeEliminations(eliminations)
            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = "Keep the common deduction",
                    description = eliminationDesc ?: "Remove candidates invalid in every branch.",
                    highlightCells = eliminationCells,
                    coloredCandidates = eliminationCandidates(eliminations)
                )
            )
        }

        return steps
    }

    private fun generateNishioSteps(
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        val eliminationCells = eliminations.flatMap { it.cells }

        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Assume and test",
                description = "Nishio assumes a single digit placement and discards branches that lead to contradiction.",
                highlightCells = eliminationCells
            )
        )

        if (eliminations.isNotEmpty()) {
            val eliminationDesc = summarizeEliminations(eliminations)
            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = "Discard impossible placements",
                    description = eliminationDesc ?: "Remove the candidates that fail under every assumption.",
                    highlightCells = eliminationCells,
                    coloredCandidates = eliminationCandidates(eliminations)
                )
            )
        }

        return steps
    }

    private fun generateChainLikeSteps(
        techniqueName: String,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        val eliminationCells = eliminations.flatMap { it.cells }

        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Trace the chain",
                description = "$techniqueName links candidates so that one end forces eliminations at the other end.",
                highlightCells = eliminationCells
            )
        )

        if (eliminations.isNotEmpty()) {
            val eliminationDesc = summarizeEliminations(eliminations)
            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = "Eliminate the target candidate",
                    description = eliminationDesc ?: "Cells seen by both ends cannot keep the target candidate.",
                    highlightCells = eliminationCells,
                    coloredCandidates = eliminationCandidates(eliminations)
                )
            )
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

