package service.hint.explanations

import service.hint.helpers.*
import sudoku.match.AICMatch
import sudoku.match.ALSMatch
import sudoku.match.TechniqueMatch
import dto.*

    fun generateChainSteps(
        techniqueName: String,
        match: AICMatch,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        
        // Extract visual data (lines and groups)
        val (lines, groups, _) = service.hint.techniques.extractAICVisualData(match)
        
        val chain = match.chain
        val nodes = chain.nodes
        
        // Helper to format node description
        // Using Any type to avoid import issues, assuming structure matches what AICTechniques expects
        fun formatNode(node: Any): String {
            try {
                // Use reflection or standard access if type was known
                // Since we know AICTechniques uses node.digit() and node.cells(), let's mimic that loosely here
                // BUT actually 'nodes' comes from 'chain.nodes' which is visible. 
                // The error was 'Unresolved reference graph'.
                // AICTechniques imports nothing special but uses node methods.
                // Let's rely on type inference which works for 'curr' and 'next' but not for function args.
                // We will cast to the specific interface if we knew it, or just use dynamic approaches?
                // No, we can just define this lambda inside where 'node' is inferred from usage.
                return "Node" // Placeholder if we can't access it?
            } catch (e: Exception) { return "Node" }
        }
        
        // Better approach: Inline the logic so we don't need to name the type in argument
        // OR better yet, just copy the logic from AICTechniques which iterates nodes.
        
        val endpointCells = mutableListOf<Int>()
        var startDesc = ""
        var endDesc = ""
        
        if (nodes.isNotEmpty()) {
            val startNode = nodes.first()
            val endNode = nodes.last()
            
            // We can call methods on startNode because its type is inferred from the list
            val startC = mutableListOf<String>()
            var sc = startNode.cells().nextSetBit(0)
            while (sc >= 0) {
                endpointCells.add(sc)
                startC.add("R${sc/9 + 1}C${sc%9 + 1}")
                sc = startNode.cells().nextSetBit(sc + 1)
            }
            startDesc = "(${startNode.digit() + 1})${startC.joinToString(",")}"

            val endC = mutableListOf<String>()
            var ec = endNode.cells().nextSetBit(0)
            while (ec >= 0) {
                endpointCells.add(ec)
                endC.add("R${ec/9 + 1}C${ec%9 + 1}")
                ec = endNode.cells().nextSetBit(ec + 1)
            }
            endDesc = "(${endNode.digit() + 1})${endC.joinToString(",")}"
            
            steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = "Identify the chain endpoints",
                description = "The chain connects $startDesc to $endDesc. We look for a contradiction between these points.",
                highlightCells = endpointCells.distinct(),
                colouredCandidates = groups.flatMap { g ->
                    val type = if (g.colourIndex % 2 == 0) "target" else "highlight"
                    // Fix: use .candidate instead of .digit if checking DTO, but here 'it' is CandidateLocationDto
                    g.candidates.map { ColouredCandidateDto(it.row, it.col, it.candidate, type) }
                }
            ))
        }

        // Step 2: Follow the chain
        val logicDescription = buildString {
            append("Follow the alternating links:\n")
            for (i in 0 until nodes.size - 1) {
                val curr = nodes[i]
                val next = nodes[i+1]
                val isStrong = chain.isFirstLinkStrong xor (i % 2 == 0)
                val type = if (isStrong) "Solid (Strong)" else "Dashed (Weak)"
                
                // Formulate descriptions inline
                val currC = mutableListOf<String>()
                var cc = curr.cells().nextSetBit(0)
                while (cc >= 0) { currC.add("R${cc/9 + 1}C${cc%9 + 1}"); cc = curr.cells().nextSetBit(cc + 1) }
                val currStr = "(${curr.digit() + 1})${currC.joinToString(",")}"

                val nextC = mutableListOf<String>()
                var nc = next.cells().nextSetBit(0)
                while (nc >= 0) { nextC.add("R${nc/9 + 1}C${nc%9 + 1}"); nc = next.cells().nextSetBit(nc + 1) }
                val nextStr = "(${next.digit() + 1})${nextC.joinToString(",")}"

                val logic = if (isStrong) "If $currStr is FALSE, then $nextStr is TRUE" 
                           else "If $currStr is TRUE, then $nextStr is FALSE"
                append("- $type link: $logic\n")
            }
        }
        
        steps.add(ExplanationStepDto(
            stepNumber = 2,
            title = "Follow the chain logic",
            description = logicDescription,
            highlightCells = nodes.flatMap { node -> 
                val list = mutableListOf<Int>()
                var c = node.cells().nextSetBit(0)
                while(c >= 0) { list.add(c); c = node.cells().nextSetBit(c+1) }
                list
            }.distinct(),
            lines = lines,
            groups = groups,
             colouredCandidates = groups.flatMap { g ->
                val type = if (g.colourIndex % 2 == 0) "target" else "highlight"
                // Fix: use .candidate
                g.candidates.map { ColouredCandidateDto(it.row, it.col, it.candidate, type) }
            }
        ))

        // Step 3: Conclusion
        if (eliminations.isNotEmpty()) {
            val eliminationDesc = eliminations.joinToString("; ") { elim ->
                val cells = elim.cells.map { "R${it/9 + 1}C${it%9 + 1}" }
                "${elim.digit} from ${cells.joinToString(", ")}"
            }
            
            val highlightElims = eliminations.flatMap { it.cells }
            val elimCandidates = eliminations.flatMap { elim ->
                elim.cells.flatMap { cell -> 
                    val r = cell / 9
                    val c = cell % 9
                    // Add elimination candidate
                    listOf(ColouredCandidateDto(r, c, elim.digit, "elimination"))
                }
            }

            steps.add(ExplanationStepDto(
                stepNumber = 3,
                title = "Apply Eliminations",
                description = "Eliminate candidates that would cause a contradiction in the chain:\n\n$eliminationDesc",
                highlightCells = highlightElims,
                colouredCandidates = elimCandidates,
                lines = lines, 
                groups = groups
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
