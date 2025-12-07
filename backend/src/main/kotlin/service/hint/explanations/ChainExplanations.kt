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
