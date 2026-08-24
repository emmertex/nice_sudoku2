package service.hint.techniques

import sudoku.match.FishMatch
import service.hint.helpers.*
import service.hint.helpers.LanguageKeyBuilder.hintKey
import service.hint.helpers.LanguageKeyBuilder.commonKey
import service.hint.helpers.formatCellName
import sudoku.match.TechniqueMatch
import dto.*

    data class WingMetadata(
        val pivotCells: List<Int> = emptyList(),
        val pincerCells: List<Int> = emptyList(),
        val otherCells: List<Int> = emptyList(),
        val digits: List<Int> = emptyList()
    ) {
        val allCells: List<Int> = (pivotCells + pincerCells + otherCells).distinct()
    }
    fun detectWingType(techniqueName: String): String {
        val lower = service.hint.helpers.LanguageKeyBuilder.normalizeTechniqueName(techniqueName)
        return when {
            // Longest prefixes first: "uvwxyz" contains "vwxyz" contains "wxyz"…
            lower.contains("uvwxyz") -> "UVWXYZ-Wing"
            lower.contains("vwxyz") -> "VWXYZ-Wing"
            lower.contains("wxyz") -> "WXYZ-Wing"
            lower.contains("xyz") -> "XYZ-Wing"
            lower.contains("w_wing") -> "W-Wing"
            lower.contains("xy") || lower.contains("y_wing") -> "XY-Wing"
            else -> techniqueName
        }
    }

    fun extractWingMetadata(match: TechniqueMatch): WingMetadata {
        val pivotCells = mutableListOf<Int>()
        val pincerCells = mutableListOf<Int>()
        val otherCells = mutableListOf<Int>()
        val digits = mutableListOf<Int>()

        try {
            val matchClass = match.javaClass
            for (field in matchClass.declaredFields) {
                try {
                    field.isAccessible = true
                    val name = field.name.lowercase()
                    val value = field.get(match)

                    fun addCells(target: MutableList<Int>, cells: List<Int>) {
                        target.addAll(cells)
                    }

                    when (value) {
                        is java.util.BitSet -> {
                            val list = bitSetToList(value)
                            when {
                                name.contains("digit") -> digits.addAll(list.map { it + 1 })
                                name.contains("hinge") || name.contains("pivot") -> addCells(pivotCells, list)
                                name.contains("pincer") || name.contains("wing") -> addCells(pincerCells, list)
                                name.contains("cell") -> addCells(otherCells, list)
                            }
                        }
                        is IntArray -> {
                            val list = value.toList()
                            when {
                                name.contains("digit") -> digits.addAll(list.map { it + 1 })
                                name.contains("hinge") || name.contains("pivot") -> addCells(pivotCells, list)
                                name.contains("pincer") || name.contains("wing") -> addCells(pincerCells, list)
                                name.contains("cell") -> addCells(otherCells, list)
                            }
                        }
                        is Int -> {
                            when {
                                name.contains("digit") -> digits.add(value + 1)
                                name.contains("hinge") || name.contains("pivot") -> addCells(pivotCells, listOf(value))
                                name.contains("pincer") || name.contains("wing") -> addCells(pincerCells, listOf(value))
                                name.contains("cell") -> addCells(otherCells, listOf(value))
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Ignore individual field extraction issues
                }
            }
        } catch (_: Exception) {
            // Ignore reflection issues and fall back to eliminations only
        }

        return WingMetadata(
            pivotCells = pivotCells.distinct(),
            pincerCells = pincerCells.distinct(),
            otherCells = otherCells.distinct(),
            digits = digits.distinct()
        )
    }

    fun buildWingRegions(pivotCells: List<Int>, pincerCells: List<Int>): List<ColouredRegionDto> {
        val regions = mutableSetOf<Pair<String, Int>>()

        for (pivot in pivotCells) {
            val pivotRow = pivot / 9
            val pivotCol = pivot % 9
            val pivotBox = (pivotRow / 3) * 3 + (pivotCol / 3)

            for (pincer in pincerCells) {
                val row = pincer / 9
                val col = pincer % 9
                val box = (row / 3) * 3 + (col / 3)

                if (row == pivotRow) regions.add("row" to row)
                if (col == pivotCol) regions.add("column" to col)
                if (box == pivotBox) regions.add("box" to box)
            }
        }

        return regions.map { (type, index) -> ColouredRegionDto(type, index, "primary") }
    }

    /**
     * Build visual lines connecting wing cells to show the pattern
     */
    fun buildWingLines(
        wingType: String,
        pivotCells: List<Int>,
        pincerCells: List<Int>,
        targetDigit: Int?,
        wingDigits: List<Int>
    ): List<LineDto> {
        val lines = mutableListOf<LineDto>()
        
        if (pivotCells.isEmpty() || targetDigit == null) return lines
        
        val pivotCell = pivotCells.first()
        val pivotRow = pivotCell / 9
        val pivotCol = pivotCell % 9
        
        // For XY-Wing: Draw weak links from pivot to each pincer
        // The pincers share the elimination digit, pivot connects them
        when (wingType) {
            "XY-Wing" -> {
                // Draw lines from pivot (hinge) to each pincer
                for (pincer in pincerCells) {
                    val pincerRow = pincer / 9
                    val pincerCol = pincer % 9
                    lines.add(LineDto(
                        from = CandidateLocationDto(pivotRow, pivotCol, targetDigit),
                        to = CandidateLocationDto(pincerRow, pincerCol, targetDigit),
                        isStrongLink = false, // Weak link - shares a candidate
                        lineType = "wing-pivot-pincer",
                        description = "The hinge shares a candidate with this pincer"
                    ))
                }
                // Draw a conceptual line between pincers showing what they both see
                if (pincerCells.size >= 2) {
                    val p1 = pincerCells[0]
                    val p2 = pincerCells[1]
                    lines.add(LineDto(
                        from = CandidateLocationDto(p1 / 9, p1 % 9, targetDigit),
                        to = CandidateLocationDto(p2 / 9, p2 % 9, targetDigit),
                        isStrongLink = true, // Strong link - one must be true
                        lineType = "wing-pincer-pincer",
                        description = "One of these pincers must have $targetDigit"
                    ))
                }
            }
            "XYZ-Wing" -> {
                // Pivot has all 3 digits, pincers each have 2
                for (pincer in pincerCells) {
                    val pincerRow = pincer / 9
                    val pincerCol = pincer % 9
                    lines.add(LineDto(
                        from = CandidateLocationDto(pivotRow, pivotCol, targetDigit),
                        to = CandidateLocationDto(pincerRow, pincerCol, targetDigit),
                        isStrongLink = false,
                        lineType = "wing-xyz",
                        description = "Connected through shared candidates"
                    ))
                }
            }
            "W-Wing" -> {
                // Two bivalue cells connected by a strong link on one digit
                if (pincerCells.size >= 2) {
                    val p1 = pincerCells[0]
                    val p2 = pincerCells[1]
                    // The strong link digit (not the elimination digit)
                    val linkDigit = wingDigits.firstOrNull { it != targetDigit } ?: targetDigit
                    lines.add(LineDto(
                        from = CandidateLocationDto(p1 / 9, p1 % 9, linkDigit),
                        to = CandidateLocationDto(p2 / 9, p2 % 9, linkDigit),
                        isStrongLink = true,
                        lineType = "wing-w-strong",
                        description = "Strong link on $linkDigit connects the two cells"
                    ))
                }
            }
            "WXYZ-Wing" -> {
                // Four cells, one common digit
                val allCells = pivotCells + pincerCells
                for (i in allCells.indices) {
                    for (j in i + 1 until allCells.size) {
                        val c1 = allCells[i]
                        val c2 = allCells[j]
                        lines.add(LineDto(
                            from = CandidateLocationDto(c1 / 9, c1 % 9, targetDigit),
                            to = CandidateLocationDto(c2 / 9, c2 % 9, targetDigit),
                            isStrongLink = false,
                            lineType = "wing-wxyz",
                            description = "Part of the WXYZ pattern"
                        ))
                    }
                }
            }
        }
        
        return lines
    }

    fun generateWingSteps(
        techniqueName: String,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        val eliminationCells = eliminations.flatMap { it.cells }.distinct()
        val targetDigit = eliminations.firstOrNull()?.digit
        val wingType = detectWingType(techniqueName)

        val metadata = extractWingMetadata(match)
        val wingCells = if (metadata.allCells.isNotEmpty()) metadata.allCells else eliminationCells
        val pivotCells = if (metadata.pivotCells.isNotEmpty()) metadata.pivotCells else wingCells.take(1)
        val pincerCells = metadata.pincerCells
        val supportingCells = metadata.otherCells.filterNot { pivotCells.contains(it) || pincerCells.contains(it) }
        val wingDigits = if (metadata.digits.isNotEmpty()) metadata.digits else listOfNotNull(targetDigit)

        // Build visual elements
        val colouredCells = mutableListOf<ColouredCellDto>()
        pivotCells.forEach { colouredCells.add(ColouredCellDto(it, "warning")) }  // Yellow for pivot/hinge
        pincerCells.forEach { colouredCells.add(ColouredCellDto(it, "target")) }   // Green for pincers
        supportingCells.forEach { colouredCells.add(ColouredCellDto(it, "secondary")) }

        val linkRegions = buildWingRegions(pivotCells, pincerCells)
        
        // Build lines connecting wing cells
        val wingLines = buildWingLines(wingType, pivotCells, pincerCells, targetDigit, wingDigits)

        // Build candidate highlighting
        val allWingCandidates = mutableListOf<ColouredCandidateDto>()
        
        // Highlight all wing digits in pivot cells
        pivotCells.forEach { cell ->
            wingDigits.forEach { digit ->
                allWingCandidates.add(ColouredCandidateDto(cell / 9, cell % 9, digit, "highlight"))
            }
        }
        
        // Highlight target digit in pincer cells (green - these are the "endpoints")
        if (targetDigit != null) {
            pincerCells.forEach { cell ->
                allWingCandidates.add(ColouredCandidateDto(cell / 9, cell % 9, targetDigit, "target"))
            }
        }

        // Build descriptive text based on wing type
        val pivotName = pivotCells.firstOrNull()?.let { formatCellName(it) } ?: "the hinge"
        val pincerNames = pincerCells.map { formatCellName(it) }
        val pincerNamesText = if (pincerNames.size >= 2) "${pincerNames[0]} and ${pincerNames[1]}" else pincerNames.joinToString(", ")
        val wingDigitText = if (wingDigits.isNotEmpty()) wingDigits.joinToString(", ") else "the wing digits"
        val targetDigitText = targetDigit?.toString() ?: "the shared digit"

        // Determine the wing key
        val wingKey = when (wingType) {
            "XY-Wing" -> "xy_wing"
            "XYZ-Wing" -> "xyz_wing"
            "WXYZ-Wing" -> "wxyz_wing"
            "W-Wing" -> "w_wing"
            else -> "generic_wing"
        }

        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = hintKey(wingKey, 1, "title",
                    "wingType" to wingType
                ),
                description = hintKey(wingKey, 1, "description",
                    "wingType" to wingType,
                    "pivotName" to pivotName,
                    "pincerNamesText" to pincerNamesText,
                    "targetDigitText" to targetDigitText,
                    "wingDigitText" to wingDigitText
                ),
                highlightCells = wingCells,
                regions = linkRegions,
                colouredCells = colouredCells,
                colouredCandidates = allWingCandidates,
                lines = wingLines
            )
        )

        // Step 2: Explain WHY the elimination works
        if (pincerCells.isNotEmpty() || eliminationCells.isNotEmpty()) {
            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = hintKey(wingKey, 2, "title"),
                    description = hintKey(wingKey, 2, "description",
                        "targetDigitText" to targetDigitText
                    ),
                    highlightCells = pincerCells.ifEmpty { wingCells },
                    regions = linkRegions,
                    colouredCells = colouredCells,
                    colouredCandidates = allWingCandidates,
                    lines = wingLines
                )
            )
        }

        // Step 3: Make the elimination
        if (eliminationCells.isNotEmpty() && targetDigit != null) {
            val eliminationNames = eliminationCells.joinToString(", ") { formatCellName(it) }
            val eliminationColouredCells = eliminationCells.map { ColouredCellDto(it, "warning") }

            steps.add(
                ExplanationStepDto(
                    stepNumber = 3,
                    title = hintKey(wingKey, 3, "title",
                        "digit" to targetDigit.toString()
                    ),
                    // R3 (Phase 8): one shared s3 for all wing shapes — "both pincers"
                    // was wrong for W-Wing/WXYZ/generic; elimination cells see the
                    // wing cells, whatever the shape is.
                    description = commonKey("wingElimination",
                        "cells" to eliminationNames,
                        "digit" to targetDigit.toString()
                    ),
                    highlightCells = eliminationCells,
                    regions = linkRegions,
                    colouredCells = colouredCells + eliminationColouredCells,
                    colouredCandidates = allWingCandidates + eliminationCandidates(eliminations),
                    lines = wingLines
                )
            )
        }

        return steps
    }
