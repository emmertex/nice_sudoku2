package service.hint.explanations

import service.hint.helpers.*
import sudoku.match.TechniqueMatch
import dto.*

    fun generateBugSteps(
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        val eliminationCells = eliminations.flatMap { it.cells }.distinct()
        // In BUG+1, the elimination usually happens in the cell that has 3 candidates (or the extra one).
        // Or it sets the value of that cell.
        
        val targetCell = eliminationCells.firstOrNull() ?: 0
        val r = targetCell / 9
        val c = targetCell % 9
        
        // Step 1: Explain the state
        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Identify the Bivalue Universal Grave (BUG)",
                description = "Notice that almost every unsolved cell on the board has exactly two candidates. " +
                              "If ALL cells had two candidates in a specific pattern, the puzzle would have two solutions (a 'Grave' state).",
                highlightCells = emptyList() // Maybe highlight all bivalue cells? Too noisy.
            )
        )
        
        // Step 2: The Exception
        steps.add(
            ExplanationStepDto(
                stepNumber = 2,
                title = "Spot the Exception",
                description = "Cell R${r+1}C${c+1} is the only one breaking the pattern (it has 3 candidates or an extra candidate). " +
                              "For the puzzle to have a unique solution, this cell must be the key.",
                highlightCells = listOf(targetCell),
                colouredCells = listOf(ColouredCellDto(targetCell, "warning"))
            )
        )

        if (eliminations.isNotEmpty()) {
            val eliminationDesc = summarizeEliminations(eliminations)
            steps.add(
                ExplanationStepDto(
                    stepNumber = 3,
                    title = "Resolve the BUG",
                    description = eliminationDesc ?: "We must pick the candidate that prevents the BUG state.",
                    highlightCells = eliminationCells,
                    colouredCandidates = eliminationCandidates(eliminations)
                )
            )
        }

        return steps
    }

    fun generateSueDeCoqSteps(
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
                    colouredCandidates = eliminationCandidates(eliminations)
                )
            )
        }

        return steps
    }

    fun generateForcingChainSteps(
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
                    colouredCandidates = eliminationCandidates(eliminations)
                )
            )
        }

        return steps
    }

    fun generateNishioSteps(
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
                    colouredCandidates = eliminationCandidates(eliminations)
                )
            )
        }

        return steps
    }

    fun generateChainLikeSteps(
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
                    colouredCandidates = eliminationCandidates(eliminations)
                )
            )
        }

        return steps
    }

    fun generateIntersectionSteps(
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
            ColouredRegionDto(baseSectorType ?: "box", baseSectorIndex % 9, "primary")
        } else null

        val coverRegion = if (coverSectorIndex != null && coverSectorIndex >= 0) {
            ColouredRegionDto(coverSectorType ?: "row", coverSectorIndex % 9, "secondary")
        } else null

        val regions = listOfNotNull(baseRegion, coverRegion)

        // Coloured cells for intersection
        val intersectionColouredCells = intersectionCells.map { ColouredCellDto(it, "warning") }

        // Coloured candidates in intersection
        val intersectionCandidates = intersectionCells.map { cell ->
            ColouredCandidateDto(cell / 9, cell % 9, digit, "target")
        }

        if (isPointing) {
            // Pointing Candidates: digit in box is restricted to a line, eliminate from rest of line
            steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = "Identify the Pointing Candidates",
                description = "In $baseHouseName, candidate $digit only appears in cells that also belong to $coverHouseName.",
                highlightCells = intersectionCells,
                regions = regions,
                colouredCells = intersectionColouredCells,
                colouredCandidates = intersectionCandidates
            ))

            // Step 2: Eliminate from the rest of the cover house (line)
            if (eliminations.isNotEmpty()) {
                val eliminationCells = eliminations.flatMap { it.cells }
                val eliminationCandidates = eliminationCells.map { cell ->
                    ColouredCandidateDto(cell / 9, cell % 9, digit, "elimination")
                }
                val eliminationCellNames = eliminationCells.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")

                steps.add(ExplanationStepDto(
                    stepNumber = 2,
                    title = "Eliminate from $coverHouseName",
                    description = "Eliminate $digit from other cells in $coverHouseName: $eliminationCellNames",
                    highlightCells = eliminationCells,
                    regions = listOfNotNull(coverRegion),
                    colouredCells = intersectionColouredCells,
                    colouredCandidates = intersectionCandidates + eliminationCandidates
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
                colouredCells = intersectionColouredCells,
                colouredCandidates = intersectionCandidates
            ))

            // Step 2: Eliminate from the rest of the cover house (box)
            if (eliminations.isNotEmpty()) {
                val eliminationCells = eliminations.flatMap { it.cells }
                val eliminationCandidates = eliminationCells.map { cell ->
                    ColouredCandidateDto(cell / 9, cell % 9, digit, "elimination")
                }
                val eliminationCellNames = eliminationCells.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")

                steps.add(ExplanationStepDto(
                    stepNumber = 2,
                    title = "Eliminate from $coverHouseName",
                    description = "Eliminate $digit from other cells in $coverHouseName: $eliminationCellNames",
                    highlightCells = eliminationCells,
                    regions = listOfNotNull(coverRegion),
                    colouredCells = intersectionColouredCells,
                    colouredCandidates = intersectionCandidates + eliminationCandidates
                ))
            }
        }

        return steps
    }
