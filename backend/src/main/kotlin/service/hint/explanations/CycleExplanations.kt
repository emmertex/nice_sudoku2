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
        
        if (match is AICMatch) {
            val (lines, groups, _) = service.hint.techniques.extractAICVisualData(match)
            val chain = match.chain
            val nodes = chain.nodes
            
            steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = "Identify the Cycle",
                description = "The cycle connects candidates in a continuous loop using alternating strong and weak links.",
                highlightCells = nodes.flatMap { dirNode -> 
                     val list = mutableListOf<Int>()
                     var c = dirNode.cells().nextSetBit(0)
                     while(c >= 0) { list.add(c); c = dirNode.cells().nextSetBit(c+1) }
                     list
                }.distinct(),
                lines = lines,
                groups = groups,
                colouredCandidates = groups.flatMap { g ->
                    val type = if (g.colourIndex % 2 == 0) "target" else "highlight"
                    g.candidates.map { ColouredCandidateDto(it.row, it.col, it.candidate, type) }
                }
            ))

            if (eliminations.isNotEmpty()) {
                val eliminationDesc = summarizeEliminations(eliminations)
                 steps.add(ExplanationStepDto(
                    stepNumber = 2,
                    title = "Apply Eliminations",
                    description = eliminationDesc ?: "Any candidate seeing two contradictory endpoints of the cycle can be eliminated.",
                    highlightCells = eliminations.flatMap { it.cells },
                    colouredCandidates = eliminationCandidates(eliminations),
                    lines = lines,
                    groups = groups
                 ))
            }
            return steps
        }

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
        
        if (match is AICMatch) {
             val (lines, groups, _) = service.hint.techniques.extractAICVisualData(match)
             val chain = match.chain
             val nodes = chain.nodes
             
             steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = "Colour the graph",
                description = "Assign two colors (Green and Yellow) to candidates connected by strong links. " +
                              "Candidates of the same color are linked; candidates of opposite colors are weakly linked.",
                highlightCells = nodes.flatMap { n -> 
                     val list = mutableListOf<Int>()
                     var c = n.cells().nextSetBit(0)
                     while(c >= 0) { list.add(c); c = n.cells().nextSetBit(c+1) }
                     list
                }.distinct(),
                lines = lines,
                groups = groups,
                colouredCandidates = groups.flatMap { g ->
                    val type = if (g.colourIndex % 2 == 0) "target" else "highlight"
                    g.candidates.map { ColouredCandidateDto(it.row, it.col, it.candidate, type) }
                }
             ))
             
            if (eliminations.isNotEmpty()) {
                val eliminationDesc = summarizeEliminations(eliminations)
                 steps.add(ExplanationStepDto(
                    stepNumber = 2,
                    title = "Eliminate Conflicts",
                    description = eliminationDesc ?: "If a cell sees both colors (Green and Yellow), it cannot contain the candidate. " + 
                                  "Or if two 'Green' nodes share a weak link (are in same house), 'Green' is impossible.",
                    highlightCells = eliminations.flatMap { it.cells },
                    colouredCandidates = eliminationCandidates(eliminations),
                    lines = lines,
                    groups = groups
                 ))
            }
             
             return steps
        }

        val eliminationCells = eliminations.flatMap { it.cells }

        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Colour the candidate",
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
                    colouredCandidates = eliminationCandidates(eliminations)
                )
            )
        }

        return steps
    }
