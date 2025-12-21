package service.hint.explanations

import service.hint.helpers.*
import service.hint.helpers.LanguageKeyBuilder.hintKey
import service.hint.helpers.LanguageKeyBuilder.commonKey
import sudoku.match.TechniqueMatch
import dto.*

    fun generateBugSteps(
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        val eliminationCells = eliminations.flatMap { it.cells }.distinct()
        val eliminationDigits = eliminations.map { it.digit }.distinct()
        
        // The target cell is usually the one with the extra candidate (BUG+1)
        val targetCell = eliminationCells.firstOrNull() ?: 0
        val targetCellName = formatCellName(targetCell)
        val eliminationDigitText = eliminationDigits.joinToString(", ")

        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = hintKey("bug", 1, "title"),
                description = hintKey("bug", 1, "description"),
                highlightCells = emptyList(),
                colouredCells = emptyList()
            )
        )
        
        steps.add(
            ExplanationStepDto(
                stepNumber = 2,
                title = hintKey("bug", 2, "title"),
                description = hintKey("bug", 2, "description",
                    "cell" to targetCellName
                ),
                highlightCells = listOf(targetCell),
                colouredCells = listOf(ColouredCellDto(targetCell, "warning")),
                colouredCandidates = eliminationDigits.map { d ->
                    ColouredCandidateDto(targetCell / 9, targetCell % 9, d, "target")
                }
            )
        )

        // Step 3: Make the elimination/placement
        if (eliminations.isNotEmpty()) {
            steps.add(
                ExplanationStepDto(
                    stepNumber = 3,
                    title = hintKey("bug", 3, "title"),
                    description = hintKey("bug", 3, "description",
                        "digits" to eliminationDigitText,
                        "cell" to targetCellName
                    ),
                    highlightCells = eliminationCells,
                    colouredCells = listOf(ColouredCellDto(targetCell, "target")),
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
        val eliminationCells = eliminations.flatMap { it.cells }.distinct()
        val eliminationDigits = eliminations.map { it.digit }.distinct()
        val eliminationDigitText = eliminationDigits.joinToString(", ")
        val eliminationCellNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")

        // Step 1: Explain Sue-de-Coq concept (ELI5)
        val step1Description = "Sue-de-Coq is a powerful technique that works on the intersection of a box and a line (row or column). " +
            "Here's the key insight: " +
            "\n\nLook at where a box and a line overlap (2-3 cells). " +
            "Count the candidates in these intersection cells - let's say there are N candidates. " +
            "Now look for 'almost locked sets' (ALS) in the rest of the box AND the rest of the line " +
            "that share some of these candidates. " +
            "\n\nIf the candidates 'partition' correctly between the intersection and these ALS cells, " +
            "we can make eliminations!"

        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Find the Box-Line Intersection",
                description = step1Description,
                highlightCells = eliminationCells
            )
        )

        // Step 2: Explain the partition logic
        val step2Description = "The partition works like this: " +
            "\n\nImagine the candidates in the intersection split into groups. " +
            "Some candidates are 'claimed' by the ALS in the line, others by the ALS in the box. " +
            "Together, these claims account for all the candidates in the intersection. " +
            "\n\nThis means: any cell OUTSIDE these groups that shares a house with them " +
            "cannot have the partitioned candidates. They're fully accounted for!"

        steps.add(
            ExplanationStepDto(
                stepNumber = 2,
                title = "Understand the Partition",
                description = step2Description,
                highlightCells = eliminationCells,
                colouredCandidates = eliminationDigits.flatMap { digit ->
                    eliminationCells.map { cell ->
                        ColouredCandidateDto(cell / 9, cell % 9, digit, "highlight")
                    }
                }
            )
        )

        // Step 3: Make eliminations
        if (eliminations.isNotEmpty()) {
            val step3Description = "Based on the partition, these candidates are 'locked' within the Sue-de-Coq structure. " +
                "\n\nRemove $eliminationDigitText from $eliminationCellNames. " +
                "These cells see the structure but aren't part of it."

            steps.add(
                ExplanationStepDto(
                    stepNumber = 3,
                    title = "Eliminate Outside the Partition",
                    description = step3Description,
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
        val eliminationCells = eliminations.flatMap { it.cells }.distinct()
        val eliminationDigits = eliminations.map { it.digit }.distinct()
        val eliminationDigitText = eliminationDigits.joinToString(", ")
        val eliminationCellNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")

        // Step 1: Explain Forcing Chains concept (ELI5)
        val step1Description = "A Forcing Chain starts from a cell with 2 (or more) candidates and asks: " +
            "'What happens if each candidate is true?' " +
            "\n\nWe explore BOTH possibilities like branching paths: " +
            "• Branch A: Assume candidate X is true, follow the consequences... " +
            "• Branch B: Assume candidate Y is true, follow the consequences... " +
            "\n\nIf both branches lead to the SAME conclusion about some other cell, " +
            "that conclusion MUST be true - because one of the starting candidates must be correct!"

        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Branch from the Starting Cell",
                description = step1Description,
                highlightCells = eliminationCells
            )
        )

        // Step 2: Explain convergence
        val step2Description = "Following both branches through chains of logical deductions, " +
            "we find that they converge on a common result. " +
            "\n\nThis is like two roads that start in different directions but both lead to the same destination. " +
            "No matter which starting candidate is correct, the destination is certain. " +
            "\n\nThe common conclusion is: $eliminationDigitText cannot be in $eliminationCellNames."

        steps.add(
            ExplanationStepDto(
                stepNumber = 2,
                title = "Both Paths Converge",
                description = step2Description,
                highlightCells = eliminationCells,
                colouredCandidates = eliminationDigits.flatMap { digit ->
                    eliminationCells.map { cell ->
                        ColouredCandidateDto(cell / 9, cell % 9, digit, "highlight")
                    }
                }
            )
        )

        // Step 3: Apply the forced conclusion
        if (eliminations.isNotEmpty()) {
            val step3Description = "Since both branches agree on this conclusion, we can apply it with certainty. " +
                "\n\nRemove $eliminationDigitText from $eliminationCellNames."

            steps.add(
                ExplanationStepDto(
                    stepNumber = 3,
                    title = "Apply the Forced Conclusion",
                    description = step3Description,
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
        val eliminationCells = eliminations.flatMap { it.cells }.distinct()
        val eliminationDigits = eliminations.map { it.digit }.distinct()
        val eliminationDigitText = eliminationDigits.joinToString(", ")
        val eliminationCellNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")

        // Step 1: Explain Nishio concept (ELI5)
        val step1Description = "Nishio is a 'what if' technique. We pick a candidate and assume it's TRUE, " +
            "then follow the chain of consequences to see what happens. " +
            "\n\nIt's like a detective saying: 'Let's assume the butler did it. " +
            "If that were true, then X would happen, then Y would happen...' " +
            "\n\nIf following this chain leads to an IMPOSSIBLE situation (a contradiction), " +
            "then our assumption was wrong - the candidate cannot be true!"

        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Make an Assumption",
                description = step1Description,
                highlightCells = eliminationCells
            )
        )

        // Step 2: Show the contradiction path
        val step2Description = "We assumed $eliminationDigitText is true in $eliminationCellNames and followed the logic: " +
            "\n\nAs we trace through the consequences (eliminating candidates, forcing placements), " +
            "we eventually reach an impossible state - perhaps a cell with no candidates, " +
            "or a digit that can't go anywhere in a house, or a cell that must have two different values. " +
            "\n\nThis contradiction proves our assumption was wrong!"

        steps.add(
            ExplanationStepDto(
                stepNumber = 2,
                title = "Find the Contradiction",
                description = step2Description,
                highlightCells = eliminationCells,
                colouredCandidates = eliminationDigits.flatMap { digit ->
                    eliminationCells.map { cell ->
                        ColouredCandidateDto(cell / 9, cell % 9, digit, "warning")
                    }
                }
            )
        )

        // Step 3: Apply the elimination
        if (eliminations.isNotEmpty()) {
            val step3Description = "Since assuming $eliminationDigitText leads to a contradiction, " +
                "it cannot be true. " +
                "\n\nRemove $eliminationDigitText from $eliminationCellNames."

            steps.add(
                ExplanationStepDto(
                    stepNumber = 3,
                    title = "Eliminate the Impossible",
                    description = step3Description,
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
        val eliminationCells = eliminations.flatMap { it.cells }.distinct()
        val eliminationDigits = eliminations.map { it.digit }.distinct()
        val eliminationDigitText = eliminationDigits.joinToString(", ")
        val eliminationCellNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")

        // Step 1: Explain the chain concept (ELI5)
        val step1Description = "$techniqueName works by connecting candidates in a logical chain. " +
            "\n\nThink of it like dominoes: if this candidate is true, then that one must be false, " +
            "which means this other one must be true, and so on... " +
            "\n\nThe chain uses two types of connections: " +
            "• Strong links (solid): if one is false, the other is true " +
            "• Weak links (dashed): both cannot be true at the same time"

        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Follow the Chain",
                description = step1Description,
                highlightCells = eliminationCells
            )
        )

        // Step 2: Explain the elimination logic
        val step2Description = "By following the chain from start to finish, we can prove that certain candidates " +
            "must be eliminated. " +
            "\n\nThe chain's endpoints (or the logic along the way) show that $eliminationDigitText " +
            "cannot be in $eliminationCellNames - it would create a contradiction."

        steps.add(
            ExplanationStepDto(
                stepNumber = 2,
                title = "Reach the Conclusion",
                description = step2Description,
                highlightCells = eliminationCells,
                colouredCandidates = eliminationDigits.flatMap { digit ->
                    eliminationCells.map { cell ->
                        ColouredCandidateDto(cell / 9, cell % 9, digit, "highlight")
                    }
                }
            )
        )

        // Step 3: Make the elimination
        if (eliminations.isNotEmpty()) {
            val step3Description = "Based on the chain logic, remove $eliminationDigitText from $eliminationCellNames."

            steps.add(
                ExplanationStepDto(
                    stepNumber = 3,
                    title = "Make the Elimination",
                    description = step3Description,
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
                title = hintKey("pointing_candidates", 1, "title"),
                description = hintKey("pointing_candidates", 1, "description",
                    "baseHouse" to baseHouseName,
                    "digit" to digit.toString(),
                    "coverHouse" to coverHouseName
                ),
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
                    title = hintKey("pointing_candidates", 2, "title",
                        "house" to coverHouseName
                    ),
                    description = hintKey("pointing_candidates", 2, "description",
                        "digit" to digit.toString(),
                        "house" to coverHouseName,
                        "cells" to eliminationCellNames
                    ),
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
                title = hintKey("claiming_candidates", 1, "title"),
                description = hintKey("claiming_candidates", 1, "description",
                    "baseHouse" to baseHouseName,
                    "digit" to digit.toString(),
                    "coverHouse" to coverHouseName
                ),
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
                    title = hintKey("claiming_candidates", 2, "title",
                        "house" to coverHouseName
                    ),
                    description = hintKey("claiming_candidates", 2, "description",
                        "digit" to digit.toString(),
                        "house" to coverHouseName,
                        "cells" to eliminationCellNames
                    ),
                    highlightCells = eliminationCells,
                    regions = listOfNotNull(coverRegion),
                    colouredCells = intersectionColouredCells,
                    colouredCandidates = intersectionCandidates + eliminationCandidates
                ))
            }
        }

        return steps
    }
