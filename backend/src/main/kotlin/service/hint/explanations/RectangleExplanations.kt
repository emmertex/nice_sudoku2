package service.hint.explanations

import service.hint.helpers.*
import dto.*

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
