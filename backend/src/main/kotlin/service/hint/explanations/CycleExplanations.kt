package service.hint.explanations

import service.hint.helpers.*
import sudoku.match.TechniqueMatch
import sudoku.match.AICMatch
import dto.*

    fun generateCycleSteps(
        techniqueName: String,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        val digit = eliminations.firstOrNull()?.digit ?: 0
        
        if (match is AICMatch) {
            val (lines, groups, _) = service.hint.techniques.extractAICVisualData(match)
            val chain = match.chain
            val nodes = chain.nodes
            
            // Extract all cells in the cycle
            val cycleCells = nodes.flatMap { dirNode -> 
                val list = mutableListOf<Int>()
                var c = dirNode.cells().nextSetBit(0)
                while(c >= 0) { list.add(c); c = dirNode.cells().nextSetBit(c+1) }
                list
            }.distinct()
            
            val cycleSize = nodes.size
            val cellNames = cycleCells.take(4).map { formatCellName(it) }
            val cellNamesText = if (cycleCells.size > 4) {
                cellNames.joinToString(", ") + ", and ${cycleCells.size - 4} more"
            } else {
                cellNames.joinToString(", ")
            }

            // Step 1: Explain what an X-Cycle is (ELI5)
            val step1Description = "An X-Cycle is a chain of cells that loops back to where it started. " +
                "This cycle has $cycleSize nodes involving digit $digit at cells: $cellNamesText. " +
                "\n\nThe key is that the chain alternates between 'strong links' (solid lines) and 'weak links' (dashed lines). " +
                "Strong link = if one is false, the other must be true. " +
                "Weak link = both cannot be true at the same time. " +
                "\n\nBecause the chain forms a complete loop, certain candidates MUST be eliminated to avoid a contradiction."

            steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = "Find the X-Cycle",
                description = step1Description,
                highlightCells = cycleCells,
                lines = lines,
                groups = groups,
                colouredCandidates = groups.flatMap { g ->
                    val type = if (g.colourIndex % 2 == 0) "target" else "highlight"
                    g.candidates.map { ColouredCandidateDto(it.row, it.col, it.candidate, type) }
                }
            ))

            // Step 2: Explain the cycle logic
            val isNiceLoop = cycleSize % 2 == 0
            val step2Description = if (isNiceLoop) {
                "This is a 'Nice Loop' (even number of nodes). " +
                "In a nice loop, we can color the nodes alternately - let's call them 'Green' and 'Yellow'. " +
                "All green nodes would have $digit, OR all yellow nodes would have $digit. " +
                "\n\nAny cell that can see BOTH a green and a yellow node cannot have $digit - " +
                "no matter which color is true, that cell would conflict with it."
            } else {
                "This is a 'Discontinuous Loop' (odd number of nodes). " +
                "With an odd cycle, something must 'break' - we find a contradiction. " +
                "\n\nFollowing the chain logic, we can prove certain candidates must be eliminated " +
                "because keeping them would create an impossible situation."
            }

            steps.add(ExplanationStepDto(
                stepNumber = 2,
                title = "Understand the Cycle Logic",
                description = step2Description,
                highlightCells = cycleCells,
                lines = lines,
                groups = groups,
                colouredCandidates = groups.flatMap { g ->
                    val type = if (g.colourIndex % 2 == 0) "target" else "highlight"
                    g.candidates.map { ColouredCandidateDto(it.row, it.col, it.candidate, type) }
                }
            ))

            // Step 3: Make eliminations
            if (eliminations.isNotEmpty()) {
                val eliminationCells = eliminations.flatMap { it.cells }.distinct()
                val eliminationNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")
                
                val step3Description = "The cell(s) at $eliminationNames can see nodes of both 'colors' in the cycle. " +
                    "Since one color must be true, these cells would always conflict. " +
                    "\n\nRemove $digit from: $eliminationNames."

                steps.add(ExplanationStepDto(
                    stepNumber = 3,
                    title = "Eliminate from Cells Seeing Both Colors",
                    description = step3Description,
                    highlightCells = eliminationCells,
                    colouredCandidates = eliminationCandidates(eliminations) + groups.flatMap { g ->
                        val type = if (g.colourIndex % 2 == 0) "target" else "highlight"
                        g.candidates.map { ColouredCandidateDto(it.row, it.col, it.candidate, type) }
                    },
                    lines = lines,
                    groups = groups
                ))
            }
            return steps
        }

        // Fallback for non-AICMatch
        val eliminationCells = eliminations.flatMap { it.cells }
        
        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Find the X-Cycle Pattern",
                description = "This $techniqueName creates a loop of cells connected by alternating strong and weak links on digit $digit. " +
                    "Strong links (solid) mean: if one is false, the other is true. " +
                    "Weak links (dashed) mean: both cannot be true together. " +
                    "\n\nThe complete loop creates a logical constraint that forces eliminations.",
                highlightCells = eliminationCells
            )
        )

        if (eliminations.isNotEmpty()) {
            val eliminationNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")
            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = "Apply the Cycle Logic",
                    description = "Following the cycle's alternating links reveals that $digit cannot be in certain cells. " +
                        "\n\nRemove $digit from: $eliminationNames.",
                    highlightCells = eliminationCells,
                    colouredCandidates = eliminationCandidates(eliminations)
                )
            )
        }

        return steps
    }

    fun generateColouringSteps(
        techniqueName: String,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        val digit = eliminations.firstOrNull()?.digit ?: 0
        
        // Determine if this is Simple Colouring or 3D Medusa
        val is3DMedusa = techniqueName.contains("Medusa", ignoreCase = true) || 
                         techniqueName.contains("3D", ignoreCase = true)
        
        if (match is AICMatch) {
            val (lines, groups, _) = service.hint.techniques.extractAICVisualData(match)
            val chain = match.chain
            val nodes = chain.nodes
            
            // Extract all cells
            val colouringCells = nodes.flatMap { n -> 
                val list = mutableListOf<Int>()
                var c = n.cells().nextSetBit(0)
                while(c >= 0) { list.add(c); c = n.cells().nextSetBit(c+1) }
                list
            }.distinct()

            // Count cells by color
            val greenCells = mutableListOf<Int>()
            val yellowCells = mutableListOf<Int>()
            nodes.forEachIndexed { index, node ->
                var c = node.cells().nextSetBit(0)
                while (c >= 0) {
                    if (index % 2 == 0) greenCells.add(c) else yellowCells.add(c)
                    c = node.cells().nextSetBit(c + 1)
                }
            }

            // Step 1: Explain what Colouring is (ELI5)
            val step1Title = if (is3DMedusa) "Understand 3D Medusa Colouring" else "Understand Simple Colouring"
            val step1Description = if (is3DMedusa) {
                "3D Medusa extends colouring to multiple digits. Here's the idea: " +
                "\n\nStart with any candidate and call it 'Green'. Every candidate connected by a strong link " +
                "gets the opposite color ('Yellow'). Strong links exist when: " +
                "\n• A digit appears only twice in a row, column, or box " +
                "\n• A cell has only two candidates (bivalue cell) " +
                "\n\nWe color ${greenCells.size} cells Green and ${yellowCells.size} cells Yellow. " +
                "One entire color group is TRUE, the other is FALSE - we just don't know which yet."
            } else {
                "Simple Colouring works on a single digit ($digit). Here's the idea: " +
                "\n\nFind all the 'strong links' for digit $digit. A strong link exists when $digit appears " +
                "in exactly two cells of a row, column, or box - if one is false, the other must be true. " +
                "\n\nNow color these cells alternately - start with 'Green', connected cells become 'Yellow', " +
                "their connections become 'Green' again, and so on. " +
                "\n\nWe have ${greenCells.size} Green cells and ${yellowCells.size} Yellow cells. " +
                "Either ALL green cells have $digit, OR ALL yellow cells have $digit."
            }

            steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = step1Title,
                description = step1Description,
                highlightCells = colouringCells,
                lines = lines,
                groups = groups,
                colouredCandidates = groups.flatMap { g ->
                    val type = if (g.colourIndex % 2 == 0) "target" else "highlight"
                    g.candidates.map { ColouredCandidateDto(it.row, it.col, it.candidate, type) }
                }
            ))

            // Step 2: Explain elimination rules
            val step2Description = "Now we look for contradictions or 'traps': " +
                "\n\n**Color Trap**: If an uncolored cell can see both a Green and a Yellow cell " +
                "(in the same row, column, or box), that cell cannot have the digit. " +
                "Why? Because one of those colors must be true, and either would eliminate the digit from that cell. " +
                "\n\n**Color Wrap**: If two cells of the SAME color can see each other, that color is impossible! " +
                "They can't both have the digit. So the OTHER color must be entirely true."

            steps.add(ExplanationStepDto(
                stepNumber = 2,
                title = "Find Color Traps and Wraps",
                description = step2Description,
                highlightCells = colouringCells,
                lines = lines,
                groups = groups,
                colouredCandidates = groups.flatMap { g ->
                    val type = if (g.colourIndex % 2 == 0) "target" else "highlight"
                    g.candidates.map { ColouredCandidateDto(it.row, it.col, it.candidate, type) }
                }
            ))

            // Step 3: Apply eliminations
            if (eliminations.isNotEmpty()) {
                val eliminationCells = eliminations.flatMap { it.cells }.distinct()
                val eliminationNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")
                
                // Try to determine if this is a trap or wrap
                val step3Description = "Based on the coloring analysis: " +
                    "\n\nThe cell(s) at $eliminationNames either see both colors (Color Trap) " +
                    "or are part of a color that contradicts itself (Color Wrap). " +
                    "\n\nRemove the candidate(s) from: $eliminationNames."

                steps.add(ExplanationStepDto(
                    stepNumber = 3,
                    title = "Make the Elimination",
                    description = step3Description,
                    highlightCells = eliminationCells,
                    colouredCandidates = eliminationCandidates(eliminations) + groups.flatMap { g ->
                        val type = if (g.colourIndex % 2 == 0) "target" else "highlight"
                        g.candidates.map { ColouredCandidateDto(it.row, it.col, it.candidate, type) }
                    },
                    lines = lines,
                    groups = groups
                ))
            }
             
            return steps
        }

        // Fallback for non-AICMatch
        val eliminationCells = eliminations.flatMap { it.cells }
        
        val step1Title = if (is3DMedusa) "3D Medusa Colouring" else "Simple Colouring"
        val step1Description = if (is3DMedusa) {
            "3D Medusa colors multiple digits connected by strong links. " +
            "Start with one candidate as 'Green', alternate to 'Yellow' along strong links. " +
            "Strong links exist between: cells with only 2 candidates, or digits appearing exactly twice in a house. " +
            "\n\nOne color is entirely TRUE, the other entirely FALSE."
        } else {
            "Simple Colouring works on digit $digit. Color cells alternately along strong links " +
            "(where $digit appears exactly twice in a row/column/box). " +
            "\n\nOne color is entirely TRUE, the other entirely FALSE."
        }

        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = step1Title,
                description = step1Description,
                highlightCells = eliminationCells
            )
        )

        if (eliminations.isNotEmpty()) {
            val eliminationNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")
            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = "Apply Color Logic",
                    description = "Cells that see both colors cannot have the candidate (Color Trap). " +
                        "Or if same-color cells see each other, that color is false (Color Wrap). " +
                        "\n\nRemove candidate(s) from: $eliminationNames.",
                    highlightCells = eliminationCells,
                    colouredCandidates = eliminationCandidates(eliminations)
                )
            )
        }

        return steps
    }
