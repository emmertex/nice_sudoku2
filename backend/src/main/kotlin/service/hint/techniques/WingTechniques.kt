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

        val colouredCells = mutableListOf<ColouredCellDto>()
        pivotCells.forEach { colouredCells.add(ColouredCellDto(it, "warning")) }
        pincerCells.forEach { colouredCells.add(ColouredCellDto(it, "info")) }
        supportingCells.forEach { colouredCells.add(ColouredCellDto(it, "secondary")) }

        val linkRegions = buildWingRegions(pivotCells, pincerCells)

        val targetCandidates = mutableListOf<ColouredCandidateDto>()
        if (targetDigit != null) {
            val candidateCells = when (wingType) {
                "XY-Wing", "W-Wing" -> if (pincerCells.isNotEmpty()) pincerCells else wingCells
                else -> if (wingCells.isNotEmpty()) wingCells else eliminationCells
            }
            candidateCells.forEach { cell ->
                targetCandidates.add(ColouredCandidateDto(cell / 9, cell % 9, targetDigit, "target"))
            }
        }

        val wingDigitText = if (wingDigits.isNotEmpty()) wingDigits.joinToString(", ") else "the shared digit"
        val pivotText = if (pivotCells.isNotEmpty()) {
            "hinge ${pivotCells.joinToString(", ") { formatCellName(it) }}"
        } else {
            "a hinge cell"
        }
        val pincerText = if (pincerCells.isNotEmpty()) {
            "pincers ${pincerCells.joinToString(", ") { formatCellName(it) }}"
        } else {
            "two pincers"
        }

        val introDescription = when (wingType) {
            "XY-Wing" -> "$wingType uses a $pivotText with two candidates. Each of the $pincerText shares one candidate with the hinge and they see each other, so any cell seeing both pincers cannot keep $wingDigitText."
            "XYZ-Wing" -> "$wingType keeps all three digits in the hinge. The two pincers each match two of those digits, so the third digit ($wingDigitText) is forced out of any cell seeing all three."
            "WXYZ-Wing" -> "$wingType spreads four digits over four cells. One digit is common to all, and any cell that can see every wing cell must drop $wingDigitText."
            "W-Wing" -> "$wingType links two matching bivalue cells through a strong link on one digit, forcing the other digit to be eliminated where both cells look."
            else -> "$techniqueName links a hinge cell to two pincers; the shared candidate can be removed where both pincers see."
        }

        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Spot the $wingType shape",
                description = introDescription,
                highlightCells = if (wingCells.isNotEmpty()) wingCells else eliminationCells,
                regions = linkRegions,
                colouredCells = colouredCells,
                colouredCandidates = targetCandidates
            )
        )

        if (pincerCells.isNotEmpty() || eliminationCells.isNotEmpty()) {
            val seeingText = if (pincerCells.isNotEmpty()) {
                "Any cell that sees both pincers must drop $wingDigitText."
            } else {
                "Cells that see the highlighted wing must drop $wingDigitText."
            }
            val eliminationDesc = summarizeEliminations(eliminations)
            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = "Where the pincers meet",
                    description = eliminationDesc ?: seeingText,
                    highlightCells = if (eliminationCells.isNotEmpty()) eliminationCells else wingCells,
                    regions = linkRegions,
                    colouredCells = colouredCells,
                    colouredCandidates = targetCandidates + eliminationCandidates(eliminations)
                )
            )
        }

        if (eliminationCells.isNotEmpty()) {
            val eliminationNames = eliminationCells.joinToString(", ") { formatCellName(it) }
            val elimDigit = targetDigit?.toString() ?: wingDigitText
            steps.add(
                ExplanationStepDto(
                    stepNumber = 3,
                    title = "Eliminate the shared candidate",
                    description = "Because both pincers cover the same spots, remove $elimDigit from $eliminationNames.",
                    highlightCells = eliminationCells,
                    regions = linkRegions,
                    colouredCandidates = targetCandidates + eliminationCandidates(eliminations)
                )
            )
        }

        return steps
    }
