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
            
            // Limit matches per technique to prevent massive responses
            val maxMatchesPerTechnique = 5
            val maxTotalMatches = 50
            
            for ((technique, techniqueMatches) in matches) {
                if (totalMatches >= maxTotalMatches) break
                
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
            
            val techniquesDto = mutableMapOf<String, List<TechniqueMatchDto>>()
            var totalMatches = 0
            
            // Limit matches per technique to prevent massive responses
            val maxMatchesPerTechnique = 5
            val maxTotalMatches = 50
            
            for ((technique, techniqueMatches) in matches) {
                if (totalMatches >= maxTotalMatches) break
                
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
        
        // Extract visual data based on match type
        val (lines, groups, eurekaNotation) = extractVisualData(match)
        
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
    private fun extractVisualData(match: TechniqueMatch): Triple<List<LineDto>, List<GroupDto>, String?> {
        return when (match) {
            is AICMatch -> extractAICVisualData(match)
            is ALSMatch -> extractALSVisualData(match)
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
        
        if (solvedCells.isNotEmpty()) {
            val solved = solvedCells.first()
            val row = solved.cell / 9 + 1
            val col = solved.cell % 9 + 1
            
            if (techniqueName.contains("Naked", ignoreCase = true)) {
                steps.add(ExplanationStepDto(
                    stepNumber = 1,
                    title = "Find the Naked Single",
                    description = "Cell R${row}C${col} has only one possible candidate: ${solved.digit}",
                    highlightCells = listOf(solved.cell),
                    highlightCandidates = listOf(CandidateLocationDto(row - 1, col - 1, solved.digit))
                ))
                steps.add(ExplanationStepDto(
                    stepNumber = 2,
                    title = "Place the Value",
                    description = "Since ${solved.digit} is the only candidate, it must be the solution for R${row}C${col}",
                    highlightCells = listOf(solved.cell)
                ))
            } else {
                // Hidden Single
                steps.add(ExplanationStepDto(
                    stepNumber = 1,
                    title = "Find the Hidden Single",
                    description = "In this house, ${solved.digit} can only go in cell R${row}C${col}",
                    highlightCells = listOf(solved.cell),
                    highlightCandidates = listOf(CandidateLocationDto(row - 1, col - 1, solved.digit))
                ))
                steps.add(ExplanationStepDto(
                    stepNumber = 2,
                    title = "Place the Value",
                    description = "Since R${row}C${col} is the only place for ${solved.digit} in this house, place it there",
                    highlightCells = listOf(solved.cell)
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
            techniqueName.contains("Pair", ignoreCase = true) -> "pair"
            techniqueName.contains("Triple", ignoreCase = true) -> "triple"
            else -> "quadruple"
        }
        
        // Get all elimination cells to identify the subset cells
        val eliminationCells = eliminations.flatMap { it.cells }.toSet()
        val digits = eliminations.map { it.digit }.toSet()
        
        steps.add(ExplanationStepDto(
            stepNumber = 1,
            title = "Identify the ${if (isNaked) "Naked" else "Hidden"} ${subsetType.replaceFirstChar { it.uppercase() }}",
            description = "Found ${digits.size} cells that ${if (isNaked) "only contain" else "are the only places for"} the digits ${digits.sorted().joinToString(", ")}",
            highlightCells = eliminationCells.toList()
        ))
        
        if (eliminations.isNotEmpty()) {
            val eliminationDesc = eliminations.joinToString("; ") { elim ->
                val cells = elim.cells.map { "R${it/9 + 1}C${it%9 + 1}" }
                "${elim.digit} from ${cells.joinToString(", ")}"
            }
            steps.add(ExplanationStepDto(
                stepNumber = 2,
                title = "Eliminate Candidates",
                description = "Remove: $eliminationDesc",
                highlightCells = eliminationCells.toList()
            ))
        }
        
        return steps
    }
    
    private fun generateIntersectionSteps(
        techniqueName: String,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        
        val eliminationCells = eliminations.flatMap { it.cells }.toSet()
        val digits = eliminations.map { it.digit }.toSet()
        
        steps.add(ExplanationStepDto(
            stepNumber = 1,
            title = "Find the Intersection",
            description = "The digit(s) ${digits.sorted().joinToString(", ")} are restricted to a line within a box (or vice versa)",
            highlightCells = eliminationCells.toList()
        ))
        
        if (eliminations.isNotEmpty()) {
            steps.add(ExplanationStepDto(
                stepNumber = 2,
                title = "Eliminate Outside the Intersection",
                description = "Remove ${digits.sorted().joinToString(", ")} from cells outside the intersection but in the same house",
                highlightCells = eliminationCells.toList()
            ))
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

