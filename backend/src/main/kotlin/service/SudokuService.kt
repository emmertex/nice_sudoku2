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
import sudoku.solvingtechClassifier.Technique
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import org.slf4j.LoggerFactory
import service.hint.metadata.missingDescriptionsForPriority
import service.hint.metadata.getTechniquePriority
import service.hint.converters.techniqueMatchToDto
import service.hint.helpers.basicGridToDto
import service.hint.helpers.dtoToBasicGrid
/**
 * Service layer that wraps StormDoku functionality
 */
class SudokuService {

    private val logger = LoggerFactory.getLogger(SudokuService::class.java)
    
    // Store technique matches by ID for later application
    private val matchCache = ConcurrentHashMap<String, CachedMatch>()
    private val maxMatchCacheEntries = 1_000

    private data class CachedMatch(
            val match: TechniqueMatch,
            val technique: Technique,
            val puzzleString: String,
            val timestamp: Long = System.currentTimeMillis()
        )

    /** Drop expired entries, then evict oldest if over [maxMatchCacheEntries]. */
    private fun pruneMatchCache() {
        val cutoff = System.currentTimeMillis() - 5 * 60 * 1000
        matchCache.entries.removeIf { it.value.timestamp < cutoff }
        if (matchCache.size <= maxMatchCacheEntries) return
        val overflow = matchCache.size - maxMatchCacheEntries
        matchCache.entries
            .sortedBy { it.value.timestamp }
            .take(overflow)
            .forEach { matchCache.remove(it.key) }
    }
    
    init {
        val missingDescriptions = missingDescriptionsForPriority()
        if (missingDescriptions.isNotEmpty()) {
            logger.warn("Missing descriptions for techniques: {}", missingDescriptions.joinToString(", "))
        }
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
            
            // Clear old cache entries / enforce size cap
            pruneMatchCache()
            
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
            matchCache[matchId] = CachedMatch(bestMatch, bestTechnique, puzzleString)
            
            val hintDto = techniqueMatchToDto(matchId, bestTechnique, bestMatch, puzzleString)
            
            HintResponse(
                success = true,
                hint = hintDto,
                difficulty = bestPriority,
                searchTimeMs = elapsed
            )
        } catch (e: Exception) {
            logger.error("Failed to find hint", e)
            HintResponse(
                success = false,
                error = "Failed to find hint",
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
            logger.error("Failed to grade puzzle", e)
            GradePuzzleResponse(
                success = false,
                error = "Failed to grade puzzle",
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
            logger.warn("Failed to parse puzzle", e)
            LoadPuzzleResponse(success = false, error = "Failed to parse puzzle")
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
            logger.warn("Failed to set cell", e)
            SetCellResponse(success = false, error = "Failed to set cell")
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
            logger.warn("Failed to solve grid", e)
            SolveResponse(success = false, error = "Failed to solve")
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
            logger.warn("Failed to solve puzzle", e)
            SolveFromPuzzleResponse(success = false, error = "Failed to solve")
        }
    }
    
    /**
     * Find all applicable techniques from a puzzle string (simpler, more reliable)
     */
    fun findTechniquesFromPuzzle(puzzleString: String, basicOnly: Boolean): FindTechniquesResponse {
        return try {
            val basicGrid = SudokuGridParser.readPuzzleString(puzzleString)
            val sbrcGrid = SBRCGrid(basicGrid)
            
            // Clear old cache entries / enforce size cap
            pruneMatchCache()
            
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
                    matchCache[matchId] = CachedMatch(match, technique, puzzleString)
                    techniqueMatchToDto(matchId, technique, match, puzzleString)
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
            logger.error("Failed to find techniques from puzzle", e)
            FindTechniquesResponse(success = false, error = "Failed to find techniques")
        }
    }
    
    /**
     * Find all applicable techniques from grid DTO
     */
    fun findTechniques(request: FindTechniquesRequest): FindTechniquesResponse {
        return try {
            val basicGrid = dtoToBasicGrid(request.grid)
            val sbrcGrid = SBRCGrid(basicGrid)
            
            // Convert grid to puzzle string for candidate checking
            val puzzleString = buildString {
                for (i in 0..80) {
                    val solved = basicGrid.getSolved(i)
                    append(if (solved.isPresent) ('0'.code + solved.asInt + 1).toChar() else '.')
                }
            }
            
            // Clear old cache entries / enforce size cap
            pruneMatchCache()
            
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
                    matchCache[matchId] = CachedMatch(match, technique, puzzleString)
                    
                    techniqueMatchToDto(matchId, technique, match, puzzleString)
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
            logger.error("Failed to find techniques from grid", e)
            FindTechniquesResponse(success = false, error = "Failed to find techniques")
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
            logger.warn("Failed to apply technique", e)
            ApplyTechniqueResponse(success = false, error = "Failed to apply technique")
        }
    }
}
    