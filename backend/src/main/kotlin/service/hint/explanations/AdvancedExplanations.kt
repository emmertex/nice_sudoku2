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
        val targetCell = eliminationCells.firstOrNull()
        val targetCellName = targetCell?.let { formatCellName(it) } ?: ""
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
        
        if (eliminations.isNotEmpty()) {
            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = hintKey("bug", 2, "title"),
                    description = hintKey("bug", 2, "description",
                        "cell" to targetCellName
                    ),
                    highlightCells = targetCell?.let { listOf(it) } ?: emptyList(),
                    colouredCells = targetCell?.let { listOf(ColouredCellDto(it, "warning")) } ?: emptyList(),
                    colouredCandidates = targetCell?.let { tc -> 
                        eliminationDigits.map { d ->
                            ColouredCandidateDto(tc / 9, tc % 9, d, "target")
                        }
                    } ?: emptyList()
                )
            )
        } else {
            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = hintKey("bug", 2, "title"),
                    description = hintKey("bug", 2, "descriptionGeneric"),
                    highlightCells = emptyList(),
                    colouredCells = emptyList(),
                    colouredCandidates = emptyList()
                )
            )
        }

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
                    colouredCells = listOf(ColouredCellDto(targetCell!!, "target")),
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
        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = hintKey("sue_de_coq", 1, "title"),
                description = hintKey("sue_de_coq", 1, "description"),
                highlightCells = eliminationCells
            )
        )

        // Step 2: Explain the partition logic
        steps.add(
            ExplanationStepDto(
                stepNumber = 2,
                title = hintKey("sue_de_coq", 2, "title"),
                description = hintKey("sue_de_coq", 2, "description"),
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
            steps.add(
                ExplanationStepDto(
                    stepNumber = 3,
                    title = hintKey("sue_de_coq", 3, "title"),
                    description = hintKey("sue_de_coq", 3, "description",
                        "digits" to eliminationDigitText,
                        "cells" to eliminationCellNames
                    ),
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

        // Step 1: Explain the branching idea (ELI5). The highlighted cells are where
        // the conclusion applies (the eliminations) - not the starting cell itself.
        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = hintKey("forcing_chains", 1, "title"),
                description = hintKey("forcing_chains", 1, "description"),
                highlightCells = eliminationCells
            )
        )

        // Step 2: Explain convergence
        steps.add(
            ExplanationStepDto(
                stepNumber = 2,
                title = hintKey("forcing_chains", 2, "title"),
                description = hintKey("forcing_chains", 2, "description",
                    "digits" to eliminationDigitText,
                    "cells" to eliminationCellNames
                ),
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
            steps.add(
                ExplanationStepDto(
                    stepNumber = 3,
                    title = hintKey("forcing_chains", 3, "title"),
                    description = hintKey("forcing_chains", 3, "description",
                        "digits" to eliminationDigitText,
                        "cells" to eliminationCellNames
                    ),
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

        // Step 1: Explain Nishio concept (ELI5) - naming the assumed candidate
        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = hintKey("nishio", 1, "title"),
                description = hintKey("nishio", 1, "description",
                    "digits" to eliminationDigitText,
                    "cells" to eliminationCellNames
                ),
                highlightCells = eliminationCells
            )
        )

        // Step 2: Show the contradiction path
        steps.add(
            ExplanationStepDto(
                stepNumber = 2,
                title = hintKey("nishio", 2, "title"),
                description = hintKey("nishio", 2, "description",
                    "digits" to eliminationDigitText,
                    "cells" to eliminationCellNames
                ),
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
            steps.add(
                ExplanationStepDto(
                    stepNumber = 3,
                    title = hintKey("nishio", 3, "title"),
                    description = hintKey("nishio", 3, "description",
                        "digits" to eliminationDigitText,
                        "cells" to eliminationCellNames
                    ),
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

        // XY-chains are weak-link-only chains of bivalue cells; every other chain
        // routed here (AICs, ALS-chains, X-chains, Kraken chains, ...) uses
        // alternating strong and weak links. Branch the keys accordingly.
        val chainKey = if (techniqueName.contains("XY", ignoreCase = true)) "xy_chain" else "generic_chain"

        // Step 1: Explain the chain concept (ELI5)
        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = hintKey(chainKey, 1, "title"),
                description = hintKey(chainKey, 1, "description"),
                highlightCells = eliminationCells
            )
        )

        // Step 2: Explain the elimination logic
        steps.add(
            ExplanationStepDto(
                stepNumber = 2,
                title = hintKey(chainKey, 2, "title"),
                description = hintKey(chainKey, 2, "description",
                    "digits" to eliminationDigitText,
                    "cells" to eliminationCellNames
                ),
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
            steps.add(
                ExplanationStepDto(
                    stepNumber = 3,
                    title = hintKey(chainKey, 3, "title"),
                    description = hintKey(chainKey, 3, "description",
                        "digits" to eliminationDigitText,
                        "cells" to eliminationCellNames
                    ),
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
                // R1 (Phase 8): shared with claiming via hints.common.boxLineInteraction
                // so the two direction-agnostic s1 texts can't drift apart.
                description = commonKey("boxLineInteraction",
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
                // R1 (Phase 8): shared with pointing via hints.common.boxLineInteraction
                // so the two direction-agnostic s1 texts can't drift apart.
                description = commonKey("boxLineInteraction",
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
