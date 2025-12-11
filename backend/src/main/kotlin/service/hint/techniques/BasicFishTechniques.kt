package service.hint.techniques

import sudoku.match.FishMatch
import service.hint.helpers.*
import sudoku.match.TechniqueMatch
import sudoku.HelpingTools.cardinals
import sudoku.DataStorage.BasicGrid
import sudoku.DataStorage.SBRCGrid
import sudoku.read.SudokuGridParser
import dto.*

    /**
     * Extract visual data from FishMatch (Pointing/Claiming Candidates and basic fish)
     */
    fun extractFishVisualData(match: TechniqueMatch, techniqueName: String): Triple<List<LineDto>, List<GroupDto>, String?> {
        val lines = mutableListOf<LineDto>()
        val groups = mutableListOf<GroupDto>()

        try {
            // Use reflection to access private fields
            val matchClass = match.javaClass
            val digitField = matchClass.getDeclaredField("digit")
            val baseSecsField = matchClass.getDeclaredField("baseSecs")
            val coverSecsField = matchClass.getDeclaredField("coverSecs")

            digitField.isAccessible = true
            baseSecsField.isAccessible = true
            coverSecsField.isAccessible = true

            val digit = (digitField.get(match) as Int) + 1 // Convert to 1-9
            val baseSecs = baseSecsField.get(match) as java.util.BitSet
            val coverSecs = coverSecsField.get(match) as java.util.BitSet

            val isPointing = techniqueName.contains("Pointing", ignoreCase = true)
            val isClaiming = techniqueName.contains("Claiming", ignoreCase = true)

            // For other fish (X-Wing, Swordfish, etc.), fall back to candidate-based visuals
            if (!isPointing && !isClaiming) {
                return extractEliminationVisuals(match)
            }

            // Get the sectors involved
            val baseSector = baseSecs.nextSetBit(0)
            val coverSector = coverSecs.nextSetBit(0)

            if (baseSector >= 0 && coverSector >= 0) {
                val baseSectorType = getSectorType(baseSector)
                val coverSectorType = getSectorType(coverSector)

                if (isPointing) {
                    // Pointing Candidates: digit is restricted to a line within a box
                    // baseSecs is the box, coverSecs is the line (row/col)
                    val boxCells = getSectorCells(baseSector)
                    val lineCells = getSectorCells(coverSector)

                    // Group 1: Cells in the box that contain the digit
                    val boxCandidates = mutableListOf<CandidateLocationDto>()
                    for (cell in boxCells) {
                        val row = cell / 9
                        val col = cell % 9
                        boxCandidates.add(CandidateLocationDto(row, col, digit))
                    }

                    if (boxCandidates.isNotEmpty()) {
                        groups.add(GroupDto(
                            candidates = boxCandidates,
                            groupType = "pointing-box",
                            colourIndex = 0
                        ))
                    }

                    // Group 2: Cells in the line that get eliminated
                    val lineEliminations = mutableListOf<CandidateLocationDto>()
                    for (cell in lineCells) {
                        if (!boxCells.contains(cell)) {
                            val row = cell / 9
                            val col = cell % 9
                            lineEliminations.add(CandidateLocationDto(row, col, digit))
                        }
                    }

                    if (lineEliminations.isNotEmpty()) {
                        groups.add(GroupDto(
                            candidates = lineEliminations,
                            groupType = "pointing-eliminations",
                            colourIndex = 1
                        ))
                    }

                } else {
                    // Claiming Candidates: digit is restricted to a box within a line
                    // baseSecs is the line (row/col), coverSecs is the box
                    val lineCells = getSectorCells(baseSector)
                    val boxCells = getSectorCells(coverSector)

                    // Group 1: Cells in the line that contain the digit
                    val lineCandidates = mutableListOf<CandidateLocationDto>()
                    for (cell in lineCells) {
                        val row = cell / 9
                        val col = cell % 9
                        lineCandidates.add(CandidateLocationDto(row, col, digit))
                    }

                    if (lineCandidates.isNotEmpty()) {
                        groups.add(GroupDto(
                            candidates = lineCandidates,
                            groupType = "claiming-line",
                            colourIndex = 0
                        ))
                    }

                    // Group 2: Cells in the box that get eliminated
                    val boxEliminations = mutableListOf<CandidateLocationDto>()
                    for (cell in boxCells) {
                        if (!lineCells.contains(cell)) {
                            val row = cell / 9
                            val col = cell % 9
                            boxEliminations.add(CandidateLocationDto(row, col, digit))
                        }
                    }

                    if (boxEliminations.isNotEmpty()) {
                        groups.add(GroupDto(
                            candidates = boxEliminations,
                            groupType = "claiming-eliminations",
                            colourIndex = 1
                        ))
                    }
                }
            }

        } catch (e: Exception) {
            // If reflection fails, return empty data
            e.printStackTrace()
        }

        return Triple(lines, groups, null)
    }

    fun generateFishSteps(
        techniqueName: String,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        val eliminationCells = eliminations.flatMap { it.cells }.distinct()

        // Extract fish pattern data
        var digit = eliminations.firstOrNull()?.digit ?: 0
        val baseIndices = mutableListOf<Int>()
        val coverIndices = mutableListOf<Int>()

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

            // Extract all base sectors
            var idx = baseSecs.nextSetBit(0)
            while (idx >= 0) {
                baseIndices.add(idx)
                idx = baseSecs.nextSetBit(idx + 1)
            }

            // Extract all cover sectors
            idx = coverSecs.nextSetBit(0)
            while (idx >= 0) {
                coverIndices.add(idx)
                idx = coverSecs.nextSetBit(idx + 1)
            }
        } catch (e: Exception) {
            // Fallback if reflection fails
        }

        // Determine base and cover types
        val baseType = if (baseIndices.isNotEmpty()) getSectorType(baseIndices.first()) else null
        val coverType = if (coverIndices.isNotEmpty()) getSectorType(coverIndices.first()) else null

        val baseTypeText = when (baseType) {
            "row" -> "rows"
            "column" -> "columns"
            "box" -> "boxes"
            else -> "lines"
        }

        val coverTypeText = when (coverType) {
            "row" -> "rows"
            "column" -> "columns"
            "box" -> "boxes"
            else -> "lines"
        }

        // Build region highlighting: base = primary (blue), cover = primary (blue)
        val baseRegions = baseIndices.map { idx ->
            ColouredRegionDto(baseType ?: "row", idx % 9, "primary")
        }
        val coverRegions = coverIndices.map { idx ->
            ColouredRegionDto(coverType ?: "row", idx % 9, "primary")
        }
        val allRegions = baseRegions + coverRegions

        // Get all cells in base sectors
        val baseCells = mutableListOf<Int>()
        for (idx in baseIndices) {
            baseCells.addAll(getSectorCells(idx))
        }

        // Get all cells in cover sectors
        val coverCells = mutableListOf<Int>()
        for (idx in coverIndices) {
            coverCells.addAll(getSectorCells(idx))
        }

        // Find intersection cells (where base and cover sectors meet - the actual X-Wing pattern)
        val intersectionCells = baseCells.filter { it in coverCells }.distinct()

        // Pattern cells are the intersection cells
        val patternCells = intersectionCells

        // Coloured candidates: pattern cells get "target", elimination cells get "elimination"
        val patternCandidates = patternCells.map { cell ->
            ColouredCandidateDto(cell / 9, cell % 9, digit, "target")
        }

        val eliminationCandidates = eliminationCandidates(eliminations)

        // Build base line names
        val baseNames = baseIndices.map { idx ->
            when (baseType) {
                "row" -> "Row ${idx % 9 + 1}"
                "column" -> "Column ${idx % 9 + 1}"
                "box" -> "Box ${idx % 9 + 1}"
                else -> "Line ${idx + 1}"
            }
        }.joinToString(" and ")

        val coverNames = coverIndices.map { idx ->
            when (coverType) {
                "row" -> "Row ${idx % 9 + 1}"
                "column" -> "Column ${idx % 9 + 1}"
                "box" -> "Box ${idx % 9 + 1}"
                else -> "Line ${idx + 1}"
            }
        }.joinToString(" and ")

        // Step 1: Identify the pattern
        val patternDescription = when {
            techniqueName.contains("X-Wing", ignoreCase = true) ->
                "In $baseNames, digit $digit appears in exactly ${baseIndices.size} $baseTypeText. " +
                "These candidates line up perfectly in $coverNames. " +
                "Because $digit must be placed in one of these positions in each base $baseTypeText, " +
                "it locks $digit into the highlighted $baseTypeText."

            techniqueName.contains("Swordfish", ignoreCase = true) ->
                "In $baseNames, digit $digit appears in exactly three $baseTypeText. " +
                "These candidates align across three $coverTypeText: $coverNames. " +
                "The swordfish pattern locks $digit into these base $baseTypeText."

            techniqueName.contains("Jellyfish", ignoreCase = true) ->
                "In $baseNames, digit $digit appears in exactly four $baseTypeText. " +
                "These candidates align across four $coverTypeText: $coverNames. " +
                "The jellyfish pattern locks $digit into these base $baseTypeText."

            else ->
                "Digit $digit candidates align on base $baseTypeText ($baseNames) and cover $coverTypeText ($coverNames) to form a $techniqueName pattern."
        }

        // Coloured cells for the intersection points (yellow border)
        val intersectionColouredCells = intersectionCells.map { ColouredCellDto(it, "warning") }

        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Identify the $techniqueName pattern",
                description = patternDescription,
                highlightCells = patternCells,
                regions = baseRegions,
                colouredCells = intersectionColouredCells,
                colouredCandidates = patternCandidates
            )
        )

        // Step 2: Explain why eliminations work
        if (eliminations.isNotEmpty()) {
            val eliminationExplanation =
                "Since $digit is locked in $baseNames (highlighted in the base $baseTypeText), " +
                "any other $digit candidates in $coverNames must be false. " +
                "Eliminate $digit from cells in the cover $coverTypeText that aren't part of the pattern."

            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = "Eliminate from cover $coverTypeText",
                    description = eliminationExplanation,
                    highlightCells = eliminationCells,
                    regions = coverRegions,
                    colouredCandidates = patternCandidates + eliminationCandidates
                )
            )

            // Step 3: Show specific eliminations with pattern cells highlighted in green
            val eliminationNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")
            val eliminationColouredCells = eliminationCells.map { ColouredCellDto(it, "warning") }

            steps.add(
                ExplanationStepDto(
                    stepNumber = 3,
                    title = "Remove candidate $digit",
                    description = "Remove $digit from: $eliminationNames. These cells see the fish pattern and cannot contain $digit.",
                    highlightCells = eliminationCells,
                    regions = allRegions,
                    colouredCells = intersectionColouredCells + eliminationColouredCells,
                    colouredCandidates = patternCandidates + eliminationCandidates
                )
            )
        }

        return steps
    }
