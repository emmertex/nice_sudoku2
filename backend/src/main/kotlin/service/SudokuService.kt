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
            
            val techniquesDto = mutableMapOf<String, List<TechniqueMatchDto>>()
            var totalMatches = 0
            
            for ((technique, techniqueMatches) in matches) {
                val matchDtos = techniqueMatches.map { match ->
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
            
            val techniquesDto = mutableMapOf<String, List<TechniqueMatchDto>>()
            var totalMatches = 0
            
            for ((technique, techniqueMatches) in matches) {
                val matchDtos = techniqueMatches.map { match ->
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
        
        // Recalculate candidates based on solved cells
        // This ensures StormDoku has a consistent state
        basicGrid.cleanUpCandidates()
        return basicGrid
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
        
        return TechniqueMatchDto(
            id = id,
            techniqueName = technique.getName(),
            description = match.toString(),
            eliminations = eliminations,
            solvedCells = solvedCells,
            highlightCells = highlightCells.toList()
        )
    }
}

