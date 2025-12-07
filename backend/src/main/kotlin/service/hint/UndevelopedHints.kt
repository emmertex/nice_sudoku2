package service.hint

import service.hintHelpers.*
import sudoku.match.AICMatch
import sudoku.match.ALSMatch
import sudoku.match.TechniqueMatch
import sudoku.HelpingTools.cardinals
import sudoku.DataStorage.BasicGrid
import sudoku.DataStorage.SBRCGrid
import sudoku.read.SudokuGridParser
import dto.*


    fun generateCycleSteps(
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

    fun generateColoringSteps(
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

    fun generateUniqueRectangleSteps(
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

    fun generateBugSteps(
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

    fun generateEmptyRectangleSteps(
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
                    coloredCandidates = eliminationCandidates(eliminations)
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
                    coloredCandidates = eliminationCandidates(eliminations)
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
                    coloredCandidates = eliminationCandidates(eliminations)
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
                    coloredCandidates = eliminationCandidates(eliminations)
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
    
    fun generateChainSteps(
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
    
    fun generateALSSteps(
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
    
    fun generateGenericSteps(
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