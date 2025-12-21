package service.hint.explanations

import service.hint.helpers.*
import service.hint.helpers.LanguageKeyBuilder.hintKey
import service.hint.helpers.LanguageKeyBuilder.commonKey
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

        steps.add(ExplanationStepDto(
            stepNumber = 1,
            title = hintKey("aic", 1, "title"),
            description = hintKey("aic", 1, "description",
                "nodeCount" to nodes.size.toString(),
                "startDesc" to startDesc,
                "endDesc" to endDesc
            ),
            highlightCells = endpointCells.distinct(),
            lines = lines,
            groups = groups,
            colouredCandidates = groups.flatMap { g ->
                val type = if (g.colourIndex % 2 == 0) "target" else "highlight"
                g.candidates.map { ColouredCandidateDto(it.row, it.col, it.candidate, type) }
            }
        ))

        // Build chain steps description for variable
        val chainSteps = if (nodes.size <= 6) {
            buildString {
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
            }
        } else {
            ""
        }
        
        steps.add(ExplanationStepDto(
            stepNumber = 2,
            title = hintKey("aic", 2, "title"),
            description = hintKey("aic", 2, "description",
                "nodeCount" to nodes.size.toString(),
                "startDesc" to startDesc,
                "endDesc" to endDesc,
                "chainSteps" to chainSteps,
                "isLong" to (nodes.size > 6).toString()
            ),
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

            val elimCandidates = eliminations.flatMap { elim ->
                elim.cells.map { cell -> 
                    ColouredCandidateDto(cell / 9, cell % 9, elim.digit, "elimination")
                }
            }

            steps.add(ExplanationStepDto(
                stepNumber = 3,
                title = hintKey("aic", 3, "title"),
                description = hintKey("aic", 3, "description",
                    "digits" to eliminationDigitText,
                    "cells" to eliminationNames
                ),
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

        steps.add(ExplanationStepDto(
            stepNumber = 1,
            title = hintKey("als", 1, "title"),
            description = hintKey("als", 1, "description",
                "alsType" to alsType,
                "numALS" to numALS.toString()
            ),
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
                
                steps.add(ExplanationStepDto(
                    stepNumber = stepNum++,
                    title = hintKey("als", stepNum - 1, "title",
                        "alsName" to alsName
                    ),
                    description = hintKey("als_identify", 1, "description",
                        "alsName" to alsName,
                        "cellCount" to cells.size.toString(),
                        "cells" to cells.joinToString(", "),
                        "digitCount" to digits.size.toString(),
                        "digits" to digits.joinToString(", ")
                    ),
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
                    steps.add(ExplanationStepDto(
                        stepNumber = stepNum++,
                        title = hintKey("als_connecting", 1, "title"),
                        description = hintKey("als_connecting", 1, "description",
                            "connections" to connectingDescriptions.joinToString("\n")
                        ),
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

            steps.add(ExplanationStepDto(
                stepNumber = stepNum,
                title = hintKey("als_elimination", 1, "title"),
                description = hintKey("als_elimination", 1, "description",
                    "digits" to eliminationDigitText,
                    "cells" to eliminationNames
                ),
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

        steps.add(ExplanationStepDto(
            stepNumber = 1,
            title = hintKey("generic", 1, "title",
                "technique" to techniqueName
            ),
            description = hintKey("generic", 1, "description",
                "technique" to techniqueName
            ),
            highlightCells = eliminationCells + solvedCells.map { it.cell }
        ))

        // Step 2: Show eliminations
        if (eliminations.isNotEmpty()) {
            val eliminationNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")
            val eliminationDigitText = eliminationDigits.joinToString(", ")

            steps.add(ExplanationStepDto(
                stepNumber = 2,
                title = hintKey("generic", 2, "title"),
                description = hintKey("generic", 2, "description",
                    "digits" to eliminationDigitText,
                    "cells" to eliminationNames
                ),
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
                title = hintKey("generic", 3, "title"),
                description = hintKey("generic", 3, "description",
                    "solutions" to solvedDesc
                ),
                highlightCells = solvedCells.map { it.cell },
                colouredCandidates = solvedCells.map { solved ->
                    ColouredCandidateDto(solved.cell / 9, solved.cell % 9, solved.digit, "target")
                }
            ))
        }

        return steps
    }
