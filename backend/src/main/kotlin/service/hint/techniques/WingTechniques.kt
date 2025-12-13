package service.hint.techniques

import sudoku.match.FishMatch
import service.hint.helpers.*
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
        val lower = techniqueName.lowercase()
        return when {
            lower.contains("wxyz") -> "WXYZ-Wing"
            lower.contains("xyz") -> "XYZ-Wing"
            lower.contains("w-wing") || lower.contains("w wing") -> "W-Wing"
            lower.contains("xy") || lower.contains("y-wing") -> "XY-Wing"
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

        // Step 1: Find the pattern (ELI5 style)
        val step1Description = when (wingType) {
            "XY-Wing" -> "Look at the yellow cell at $pivotName - it's called the 'hinge' and has exactly two candidates. " +
                "Now look at the two green cells at $pincerNamesText - these are the 'pincers'. " +
                "Each pincer shares one candidate with the hinge, but they both have $targetDigitText in common. " +
                "Here's the key: no matter which candidate goes in the hinge, one of the pincers MUST end up with $targetDigitText."
            
            "XYZ-Wing" -> "Look at the yellow cell at $pivotName - it has three candidates: $wingDigitText. " +
                "The two green cells at $pincerNamesText each share two of those candidates with the hinge. " +
                "The digit $targetDigitText appears in ALL three cells. Since every outcome forces $targetDigitText into one of these cells, " +
                "any cell that can see all three cannot have $targetDigitText."
            
            "WXYZ-Wing" -> "This pattern uses four cells that together contain four different candidates. " +
                "The digit $targetDigitText is special - it appears in a way that guarantees one of these cells will have it. " +
                "Think of it like musical chairs: $targetDigitText must sit somewhere in this group."
            
            "W-Wing" -> "Look at the two green cells at $pincerNamesText. They both have the same two candidates. " +
                "These cells are connected by a 'strong link' (shown as a solid line) on one of those digits. " +
                "This means: if the linking digit is removed from one cell, the other cell MUST have it. " +
                "Either way, one of these cells will have $targetDigitText."
            
            else -> "This $wingType pattern connects cells in a way that forces $targetDigitText to appear in specific locations."
        }

        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Find the $wingType pattern",
                description = step1Description,
                highlightCells = wingCells,
                regions = linkRegions,
                colouredCells = colouredCells,
                colouredCandidates = allWingCandidates,
                lines = wingLines
            )
        )

        // Step 2: Explain WHY the elimination works
        val step2Description = when (wingType) {
            "XY-Wing" -> "Since one pincer MUST have $targetDigitText, any cell that can see BOTH pincers cannot have $targetDigitText. " +
                "It would conflict with whichever pincer ends up with that digit. " +
                "The solid line shows that one pincer must be true for $targetDigitText."
            
            "XYZ-Wing" -> "The three cells form a 'closed group' for $targetDigitText. " +
                "No matter what happens, $targetDigitText will end up in one of them. " +
                "Any cell that sees all three is blocked from having $targetDigitText."
            
            "WXYZ-Wing" -> "The four cells 'lock' $targetDigitText within their group. " +
                "Any outside cell that can see all four must give up $targetDigitText."
            
            "W-Wing" -> "Because the two cells are connected by a strong link, their 'other' candidate ($targetDigitText) is restricted. " +
                "Any cell seeing both green cells would conflict with whichever one ends up with $targetDigitText."
            
            else -> "The pattern guarantees $targetDigitText appears in one of the highlighted cells, blocking it elsewhere."
        }

        if (pincerCells.isNotEmpty() || eliminationCells.isNotEmpty()) {
            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = "Understand why it works",
                    description = step2Description,
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
                    title = "Remove $targetDigit from cells that see the pattern",
                    description = "The cell(s) at $eliminationNames can see both pincers (or all wing cells), " +
                        "so $targetDigit cannot go there. Remove $targetDigit from: $eliminationNames.",
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
