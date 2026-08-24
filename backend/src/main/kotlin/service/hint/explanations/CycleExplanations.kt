package service.hint.explanations

import service.hint.helpers.*
import service.hint.helpers.LanguageKeyBuilder.hintKey
import service.hint.helpers.LanguageKeyBuilder.commonKey
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
            // Digits actually involved in the cycle (derived from the chain nodes, never a fabricated 0)
            val cycleDigits = nodes.map { it.digit() + 1 }.distinct().sorted()
            val digitClause = when {
                cycleDigits.isEmpty() -> ""
                cycleDigits.size == 1 -> " involving digit ${cycleDigits.first()}"
                else -> " involving digits " + cycleDigits.joinToString(", ")
            }
            
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

            steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = hintKey("x_cycle", 1, "title"),
                description = hintKey("x_cycle", 1, "description",
                    "cycleSize" to cycleSize.toString(),
                    "digitClause" to digitClause,
                    "cells" to cellNamesText
                ),
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
            
            steps.add(ExplanationStepDto(
                stepNumber = 2,
                title = hintKey("x_cycle", 2, "title"),
                description = hintKey(if (isNiceLoop) "x_cycle_nice_loop" else "x_cycle_discontinuous", 1, "description",
                    "digit" to cycleDigits.joinToString(", ")
                ),
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
                val elimDigits = eliminations.map { it.digit }.distinct().sorted()

                steps.add(ExplanationStepDto(
                    stepNumber = 3,
                    title = hintKey("x_cycle", 3, "title"),
                    description = hintKey("x_cycle", 3, "description",
                        "cells" to eliminationNames,
                        "digit" to elimDigits.joinToString(", ")
                    ),
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
        
        val fbDigitClause = if (digit != 0) " on digit $digit" else ""

        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = hintKey("x_cycle_generic", 1, "title"),
                description = hintKey("x_cycle_generic", 1, "description",
                    "techniqueName" to techniqueName,
                    "digitClause" to fbDigitClause
                ),
                highlightCells = eliminationCells
            )
        )

        if (eliminations.isNotEmpty()) {
            val eliminationNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")
            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = hintKey("x_cycle_generic", 2, "title"),
                    description = hintKey("x_cycle_generic", 2, "description",
                        "digit" to digit.toString(),
                        "cells" to eliminationNames
                    ),
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
            // Digits actually involved in the colouring (from the chain nodes —
            // never a fabricated 0 when there are no eliminations to steal one from).
            val coloringDigits = nodes.map { it.digit() + 1 }.distinct().sorted()
            val digitText = coloringDigits.joinToString(", ")
                .ifEmpty { digit.takeIf { it > 0 }?.toString() ?: "" }
            
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

            steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = hintKey(if (is3DMedusa) "3d_medusa" else "simple_coloring", 1, "title"),
                description = hintKey(if (is3DMedusa) "3d_medusa" else "simple_coloring", 1, "description",
                    "digit" to digitText,
                    "greenCount" to greenCells.size.toString(),
                    "yellowCount" to yellowCells.size.toString()
                ),
                highlightCells = colouringCells,
                lines = lines,
                groups = groups,
                colouredCandidates = groups.flatMap { g ->
                    val type = if (g.colourIndex % 2 == 0) "target" else "highlight"
                    g.candidates.map { ColouredCandidateDto(it.row, it.col, it.candidate, type) }
                }
            ))

            steps.add(ExplanationStepDto(
                stepNumber = 2,
                title = hintKey("coloring", 2, "title"),
                description = hintKey("coloring", 2, "description"),
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
                val elimDigits = eliminations.map { it.digit }.distinct().sorted()

                steps.add(ExplanationStepDto(
                    stepNumber = 3,
                    title = hintKey("coloring", 3, "title"),
                    description = hintKey("coloring", 3, "description",
                        "cells" to eliminationNames,
                        "digit" to elimDigits.joinToString(", ")
                    ),
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
                title = hintKey("coloring_generic", 1, "title"),
                description = hintKey("coloring_generic", 1, "description"),
                highlightCells = eliminationCells
            )
        )

        if (eliminations.isNotEmpty()) {
            val eliminationNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")
            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = hintKey("coloring_generic", 2, "title"),
                    description = hintKey("coloring_generic", 2, "description",
                        "cells" to eliminationNames
                    ),
                    highlightCells = eliminationCells,
                    colouredCandidates = eliminationCandidates(eliminations)
                )
            )
        }

        return steps
    }
