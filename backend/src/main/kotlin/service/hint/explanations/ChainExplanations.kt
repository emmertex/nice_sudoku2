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
        
        // Collect all cells in the chain
        val allChainCells = nodes.flatMap { node -> 
            val list = mutableListOf<Int>()
            var c = node.cells().nextSetBit(0)
            while(c >= 0) { list.add(c); c = node.cells().nextSetBit(c+1) }
            list
        }.distinct()
        
        val endpointCells = mutableListOf<Int>()
        var startDesc = ""
        var endDesc = ""
        var startDigit = 0
        var endDigit = 0
        
        if (nodes.isNotEmpty()) {
            val startNode = nodes.first()
            val endNode = nodes.last()
            startDigit = startNode.digit() + 1
            endDigit = endNode.digit() + 1
            
            val startC = mutableListOf<String>()
            var sc = startNode.cells().nextSetBit(0)
            while (sc >= 0) {
                endpointCells.add(sc)
                startC.add(formatCellName(sc))
                sc = startNode.cells().nextSetBit(sc + 1)
            }
            startDesc = "($startDigit)${startC.joinToString(",")}"

            val endC = mutableListOf<String>()
            var ec = endNode.cells().nextSetBit(0)
            while (ec >= 0) {
                endpointCells.add(ec)
                endC.add(formatCellName(ec))
                ec = endNode.cells().nextSetBit(ec + 1)
            }
            endDesc = "($endDigit)${endC.joinToString(",")}"
        }

        // Step 1: Explain what an AIC is (ELI5)
        val step1Description = "An Alternating Inference Chain (AIC) connects candidates in a logical sequence. " +
            "Think of it like a chain of dominoes where each piece affects the next. " +
            "\n\nThis chain has ${nodes.size} links, starting at $startDesc and ending at $endDesc. " +
            "\n\nThe chain alternates between two types of connections: " +
            "• **Strong links** (solid lines): 'If this is FALSE, then that must be TRUE' " +
            "• **Weak links** (dashed lines): 'If this is TRUE, then that must be FALSE' " +
            "\n\nBy following the chain, we can prove certain candidates must be eliminated."

        steps.add(ExplanationStepDto(
            stepNumber = 1,
            title = "Find the Chain",
            description = step1Description,
            highlightCells = endpointCells.distinct(),
            lines = lines,
            groups = groups,
            colouredCandidates = groups.flatMap { g ->
                val type = if (g.colourIndex % 2 == 0) "target" else "highlight"
                g.candidates.map { ColouredCandidateDto(it.row, it.col, it.candidate, type) }
            }
        ))

        // Step 2: Walk through the chain logic (simplified)
        val step2Description = buildString {
            append("Let's follow the chain step by step:\n\n")
            
            if (nodes.size <= 6) {
                // Show full chain for shorter chains
                for (i in 0 until nodes.size - 1) {
                    val curr = nodes[i]
                    val next = nodes[i+1]
                    val isStrong = chain.isFirstLinkStrong xor (i % 2 == 0)
                    
                    val currC = mutableListOf<String>()
                    var cc = curr.cells().nextSetBit(0)
                    while (cc >= 0) { currC.add(formatCellName(cc)); cc = curr.cells().nextSetBit(cc + 1) }
                    val currDigit = curr.digit() + 1
                    
                    val nextC = mutableListOf<String>()
                    var nc = next.cells().nextSetBit(0)
                    while (nc >= 0) { nextC.add(formatCellName(nc)); nc = next.cells().nextSetBit(nc + 1) }
                    val nextDigit = next.digit() + 1
                    
                    if (isStrong) {
                        append("• If $currDigit is NOT in ${currC.joinToString(",")}, ")
                        append("then $nextDigit MUST be in ${nextC.joinToString(",")}\n")
                    } else {
                        append("• If $currDigit IS in ${currC.joinToString(",")}, ")
                        append("then $nextDigit is NOT in ${nextC.joinToString(",")}\n")
                    }
                }
            } else {
                // Summarize for longer chains
                append("The chain has ${nodes.size} steps, alternating strong and weak links.\n")
                append("Starting from $startDesc, following the logic leads us to $endDesc.\n")
            }
            
            append("\nThe chain endpoints are critical - any cell that can 'see' both endpoints ")
            append("cannot have the target candidate.")
        }
        
        steps.add(ExplanationStepDto(
            stepNumber = 2,
            title = "Follow the Logic",
            description = step2Description,
            highlightCells = allChainCells,
            lines = lines,
            groups = groups,
            colouredCandidates = groups.flatMap { g ->
                val type = if (g.colourIndex % 2 == 0) "target" else "highlight"
                g.candidates.map { ColouredCandidateDto(it.row, it.col, it.candidate, type) }
            }
        ))

        // Step 3: Explain and apply eliminations
        if (eliminations.isNotEmpty()) {
            val eliminationCells = eliminations.flatMap { it.cells }.distinct()
            val eliminationDigits = eliminations.map { it.digit }.distinct()
            val eliminationNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")
            val eliminationDigitText = eliminationDigits.joinToString(", ")
            
            val step3Description = "The chain proves that $eliminationDigitText cannot be in $eliminationNames. " +
                "\n\nWhy? Because the chain's endpoints create a 'trap': no matter which endpoint is true, " +
                "the elimination cell would see a cell that has the candidate. " +
                "\n\nRemove $eliminationDigitText from: $eliminationNames."

            val elimCandidates = eliminations.flatMap { elim ->
                elim.cells.map { cell -> 
                    ColouredCandidateDto(cell / 9, cell % 9, elim.digit, "elimination")
                }
            }

            steps.add(ExplanationStepDto(
                stepNumber = 3,
                title = "Make the Elimination",
                description = step3Description,
                highlightCells = eliminationCells,
                colouredCandidates = elimCandidates + groups.flatMap { g ->
                    val type = if (g.colourIndex % 2 == 0) "target" else "highlight"
                    g.candidates.map { ColouredCandidateDto(it.row, it.col, it.candidate, type) }
                },
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

        // Determine the ALS type
        val numALS = chain.getNumALS()
        val alsType = when {
            techniqueName.contains("XY", ignoreCase = true) -> "ALS-XY"
            techniqueName.contains("XZ", ignoreCase = true) -> "ALS-XZ"
            techniqueName.contains("Wing", ignoreCase = true) -> "ALS-Wing"
            else -> "ALS Chain"
        }

        // Step 1: Explain what an ALS is (ELI5)
        val step1Description = "An **Almost Locked Set** (ALS) is a group of cells that's 'almost' solved. " +
            "Here's the key idea: if you have N cells containing exactly N+1 different candidates, " +
            "then removing just ONE candidate would 'lock' the remaining candidates to those cells. " +
            "\n\nFor example: 3 cells with candidates {1,2,3,4} = almost locked. Remove any one digit, " +
            "and the other 3 digits MUST go in those 3 cells. " +
            "\n\nThis $alsType technique uses $numALS Almost Locked Sets that share 'connecting digits' - " +
            "digits that can only be in one ALS or the other, linking them together."

        steps.add(ExplanationStepDto(
            stepNumber = 1,
            title = "Understand Almost Locked Sets",
            description = step1Description,
            highlightCells = emptyList()
        ))

        // Step 2: Identify each ALS with ELI5 description
        var stepNum = 2
        val allAlsCells = mutableListOf<Int>()
        val allAlsCandidates = mutableListOf<ColouredCandidateDto>()

        for ((nodeIndex, collective) in nodes.withIndex()) {
            val alsList = collective.alsList()
            for ((alsIndex, als) in alsList.withIndex()) {
                val cells = mutableListOf<String>()
                val cellIndices = mutableListOf<Int>()
                var cell = als.alsAllCells.nextSetBit(0)
                while (cell >= 0) {
                    cells.add(formatCellName(cell))
                    cellIndices.add(cell)
                    allAlsCells.add(cell)
                    cell = als.alsAllCells.nextSetBit(cell + 1)
                }

                val digits = mutableListOf<Int>()
                var digit = als.alsDigits.nextSetBit(0)
                while (digit >= 0) {
                    digits.add(digit + 1)
                    // Add colored candidates for this ALS
                    cellIndices.forEach { c ->
                        allAlsCandidates.add(ColouredCandidateDto(c / 9, c % 9, digit + 1, 
                            if (nodeIndex % 2 == 0) "target" else "highlight"))
                    }
                    digit = als.alsDigits.nextSetBit(digit + 1)
                }

                val alsName = "ALS ${('A'.code + alsIndex).toChar()}"
                val alsDescription = "Look at $alsName: ${cells.size} cells (${cells.joinToString(", ")}) " +
                    "with ${digits.size} candidates {${digits.joinToString(", ")}}. " +
                    "\n\nThis is ${cells.size} cells with ${digits.size} candidates - that's 'almost locked' " +
                    "because ${digits.size} = ${cells.size} + 1."

                steps.add(ExplanationStepDto(
                    stepNumber = stepNum++,
                    title = "Find $alsName",
                    description = alsDescription,
                    highlightCells = cellIndices,
                    colouredCandidates = cellIndices.flatMap { c ->
                        digits.map { d -> ColouredCandidateDto(c / 9, c % 9, d, 
                            if (nodeIndex % 2 == 0) "target" else "highlight") }
                    }
                ))
            }

            // Explain the connecting digits (formerly RCC)
            val startRCCs = collective.StartRCCnode()
            val linkRCCs = collective.LinkRCCnodes()

            if (startRCCs.isNotEmpty() || linkRCCs.isNotEmpty()) {
                val connectingDescriptions = mutableListOf<String>()
                val connectingCells = mutableListOf<Int>()

                for (rcc in startRCCs + linkRCCs) {
                    val rccCells = mutableListOf<String>()
                    var cell = rcc.rccCells.nextSetBit(0)
                    while (cell >= 0) {
                        rccCells.add(formatCellName(cell))
                        connectingCells.add(cell)
                        cell = rcc.rccCells.nextSetBit(cell + 1)
                    }
                    connectingDescriptions.add("Digit ${rcc.rccDigit + 1} at ${rccCells.joinToString(", ")}")
                }

                if (connectingDescriptions.isNotEmpty()) {
                    val rccDescription = "The ALSs are connected by **shared digits** that can only appear in one ALS or the other: " +
                        "\n\n${connectingDescriptions.joinToString("\n")} " +
                        "\n\nThese 'connecting digits' act like bridges - if the digit is used in one ALS, " +
                        "it's locked out of the other ALS, which then locks the remaining digits."

                    steps.add(ExplanationStepDto(
                        stepNumber = stepNum++,
                        title = "Find the Connecting Digits",
                        description = rccDescription,
                        highlightCells = connectingCells,
                        colouredCandidates = allAlsCandidates
                    ))
                }
            }
        }

        // Final step: Explain and apply eliminations
        if (eliminations.isNotEmpty()) {
            val eliminationCells = eliminations.flatMap { it.cells }.distinct()
            val eliminationDigits = eliminations.map { it.digit }.distinct()
            val eliminationNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")
            val eliminationDigitText = eliminationDigits.joinToString(", ")

            val eliminationDescription = "Now for the payoff! Look at the digit(s) $eliminationDigitText. " +
                "\n\nBecause of how the ALSs are connected, $eliminationDigitText must end up in one of the ALS cells. " +
                "Any cell that can 'see' ALL the places where $eliminationDigitText could be in BOTH ALSs " +
                "cannot have $eliminationDigitText - it would always conflict. " +
                "\n\nRemove $eliminationDigitText from: $eliminationNames."

            steps.add(ExplanationStepDto(
                stepNumber = stepNum,
                title = "Make the Elimination",
                description = eliminationDescription,
                highlightCells = eliminationCells,
                colouredCandidates = eliminationCandidates(eliminations) + allAlsCandidates
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
        val eliminationCells = eliminations.flatMap { it.cells }.distinct()
        val eliminationDigits = eliminations.map { it.digit }.distinct()

        // Step 1: Introduction to the technique
        val step1Description = "This technique is called **$techniqueName**. " +
            "\n\nIt uses logical deduction to prove that certain candidates must be eliminated " +
            "or that certain cells must have specific values. " +
            "\n\nThe pattern involves the highlighted cells and candidates."

        steps.add(ExplanationStepDto(
            stepNumber = 1,
            title = techniqueName,
            description = step1Description,
            highlightCells = eliminationCells + solvedCells.map { it.cell }
        ))

        // Step 2: Show eliminations
        if (eliminations.isNotEmpty()) {
            val eliminationNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")
            val eliminationDigitText = eliminationDigits.joinToString(", ")
            
            val eliminationDescription = "Based on the pattern, we can eliminate: " +
                "\n\nRemove $eliminationDigitText from $eliminationNames."

            steps.add(ExplanationStepDto(
                stepNumber = 2,
                title = "Apply Eliminations",
                description = eliminationDescription,
                highlightCells = eliminationCells,
                colouredCandidates = eliminationCandidates(eliminations)
            ))
        }

        // Step 3: Show solved cells
        if (solvedCells.isNotEmpty()) {
            val solvedDesc = solvedCells.joinToString("\n") { solved ->
                "• ${formatCellName(solved.cell)} = ${solved.digit}"
            }
            
            steps.add(ExplanationStepDto(
                stepNumber = steps.size + 1,
                title = "Place Values",
                description = "The technique reveals these solutions:\n\n$solvedDesc",
                highlightCells = solvedCells.map { it.cell },
                colouredCandidates = solvedCells.map { solved ->
                    ColouredCandidateDto(solved.cell / 9, solved.cell % 9, solved.digit, "target")
                }
            ))
        }

        return steps
    }
