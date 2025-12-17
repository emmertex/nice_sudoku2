package service.hint.explanations

import service.hint.helpers.*
import sudoku.match.TechniqueMatch
import dto.*

    fun generateSubsetSteps(
        techniqueName: String,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()

        val isNaked = techniqueName.contains("Naked", ignoreCase = true)
        val subsetType = when {
            techniqueName.contains("Pair", ignoreCase = true) -> "Pair"
            techniqueName.contains("Triple", ignoreCase = true) -> "Triple"
            techniqueName.contains("Quadruple", ignoreCase = true) -> "Quadruple"
            else -> "Subset"
        }

        // Extract data from SubsetMatch using reflection
        var subsetCells = listOf<Int>()
        var subsetDigits = listOf<Int>()
        var sectorIndex: Int? = null
        var sectorType: String? = null

        try {
            val matchClass = match.javaClass
            val digitsField = matchClass.getDeclaredField("digits")
            val cellsField = matchClass.getDeclaredField("cells")
            val sectorsField = matchClass.getDeclaredField("sectors")

            digitsField.isAccessible = true
            cellsField.isAccessible = true
            sectorsField.isAccessible = true

            val digits = digitsField.get(match) as java.util.BitSet
            val cells = cellsField.get(match) as java.util.BitSet
            val sectors = sectorsField.get(match) as java.util.BitSet

            subsetDigits = bitSetToList(digits).map { it + 1 } // Convert to 1-9
            subsetCells = bitSetToList(cells)
            sectorIndex = sectors.nextSetBit(0)
            if (sectorIndex >= 0) {
                sectorType = getSectorType(sectorIndex)
            }
        } catch (e: Exception) {
            // Fallback: use elimination data
            subsetDigits = eliminations.map { it.digit }.distinct().sorted()
        }

        val houseName = when (sectorType) {
            "row" -> "Row ${(sectorIndex ?: 0) % 9 + 1}"
            "column" -> "Column ${(sectorIndex ?: 9) % 9 + 1}"
            "box" -> "Box ${(sectorIndex ?: 18) % 9 + 1}"
            else -> "this house"
        }

        val cellNames = subsetCells.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
        val digitNames = subsetDigits.joinToString(", ")

        // Build regions for highlighting
        val regions = if (sectorIndex != null && sectorIndex >= 0) {
            listOf(ColouredRegionDto(sectorType ?: "row", sectorIndex % 9, "primary"))
        } else {
            emptyList()
        }

        // Build coloured cells for the subset
        val colouredSubsetCells = subsetCells.map { ColouredCellDto(it, "warning") }

        // Build coloured candidates for the subset digits in subset cells
        val subsetCandidates = subsetCells.flatMap { cell ->
            val r = cell / 9
            val c = cell % 9
            subsetDigits.map { digit -> ColouredCandidateDto(r, c, digit, "target") }
        }

        // Get cells in the main sector to separate normal vs pointing eliminations
        val sectorCells = if (sectorIndex != null && sectorIndex >= 0) {
            getSectorCells(sectorIndex).toSet()
        } else {
            emptySet()
        }

        // Separate eliminations: normal (within sector) vs pointing (outside sector, locked candidates effect)
        val normalEliminations = eliminations.filter { elim ->
            elim.cells.all { it in sectorCells }
        }
        val pointingEliminations = eliminations.filter { elim ->
            elim.cells.any { it !in sectorCells }
        }

        if (isNaked) {
            // Naked Subset: cells only contain these digits
            steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = "Identify the Naked $subsetType",
                description = "Cells $cellNames in $houseName can only contain $digitNames. These digits are 'locked' to these cells.",
                highlightCells = subsetCells,
                regions = regions,
                colouredCells = colouredSubsetCells,
                colouredCandidates = subsetCandidates
            ))

            if (normalEliminations.isNotEmpty()) {
                // Step 2: Show eliminations within the same house
                val normalEliminationCandidates = normalEliminations.flatMap { elim ->
                    elim.cells.map { c ->
                        ColouredCandidateDto(c / 9, c % 9, elim.digit, "elimination")
                    }
                }

                val normalDesc = if (normalEliminations.size == 1) {
                    val elim = normalEliminations.first()
                    val digit = elim.digit
                    val cells = elim.cells.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
                    "Since $digitNames can only be in $cellNames, eliminate $digit from $cells in $houseName."
                } else {
                    "Since $digitNames can only be in $cellNames, eliminate these digits from other cells in $houseName."
                }

                steps.add(ExplanationStepDto(
                    stepNumber = 2,
                    title = "Eliminate from Same House",
                    description = normalDesc,
                    highlightCells = subsetCells + normalEliminations.flatMap { it.cells },
                    regions = regions,
                    colouredCells = colouredSubsetCells,
                    colouredCandidates = subsetCandidates + normalEliminationCandidates
                ))
            }

            if (pointingEliminations.isNotEmpty()) {
                // Step 3: Show pointing eliminations (locked candidates)
                val pointingEliminationCandidates = pointingEliminations.flatMap { elim ->
                    elim.cells.map { c ->
                        ColouredCandidateDto(c / 9, c % 9, elim.digit, "elimination")
                    }
                }

                val pointingDesc = if (pointingEliminations.size == 1) {
                    val elim = pointingEliminations.first()
                    val digit = elim.digit
                    val cells = elim.cells.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
                    "The Naked $subsetType locks $digit to $houseName, so eliminate $digit from $cells."
                } else {
                    "The Naked $subsetType locks these digits to $houseName, eliminating them from other houses."
                }

                steps.add(ExplanationStepDto(
                    stepNumber = if (normalEliminations.isNotEmpty()) 3 else 2,
                    title = "Apply Locked Candidates",
                    description = pointingDesc,
                    highlightCells = subsetCells + pointingEliminations.flatMap { it.cells },
                    regions = regions,
                    colouredCells = colouredSubsetCells,
                    colouredCandidates = subsetCandidates + pointingEliminationCandidates
                ))
            }
        } else {
            // Hidden Subset: digits can only be in these cells
            steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = "Identify the Hidden $subsetType",
                description = "In $houseName, $digitNames can only be placed in $cellNames. These cells are 'locked' to these digits.",
                highlightCells = subsetCells,
                regions = regions,
                colouredCells = colouredSubsetCells,
                colouredCandidates = subsetCandidates
            ))

            if (normalEliminations.isNotEmpty()) {
                // Step 2: Show eliminations within the same house
                val normalEliminationCandidates = normalEliminations.flatMap { elim ->
                    elim.cells.map { c ->
                        ColouredCandidateDto(c / 9, c % 9, elim.digit, "elimination")
                    }
                }

                val normalDesc = if (normalEliminations.size == 1) {
                    val elim = normalEliminations.first()
                    val digit = elim.digit
                    val cells = elim.cells.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
                    "Since $digitNames can only be in $cellNames, eliminate other candidates from these cells."
                } else {
                    "Since $digitNames can only be in $cellNames, eliminate other candidates from these cells."
                }

                steps.add(ExplanationStepDto(
                    stepNumber = 2,
                    title = "Eliminate from Same House",
                    description = normalDesc,
                    highlightCells = subsetCells + normalEliminations.flatMap { it.cells },
                    regions = regions,
                    colouredCells = colouredSubsetCells,
                    colouredCandidates = subsetCandidates + normalEliminationCandidates
                ))
            }

            if (pointingEliminations.isNotEmpty()) {
                // Step 3: Show pointing eliminations (hidden locked candidates)
                val pointingEliminationCandidates = pointingEliminations.flatMap { elim ->
                    elim.cells.map { c ->
                        ColouredCandidateDto(c / 9, c % 9, elim.digit, "elimination")
                    }
                }

                val pointingDesc = if (pointingEliminations.size == 1) {
                    val elim = pointingEliminations.first()
                    val digit = elim.digit
                    val cells = elim.cells.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
                    "The Hidden $subsetType locks $digit to $houseName, so eliminate $digit from $cells."
                } else {
                    "The Hidden $subsetType locks these digits to $houseName, eliminating them from other houses."
                }

                steps.add(ExplanationStepDto(
                    stepNumber = if (normalEliminations.isNotEmpty()) 3 else 2,
                    title = "Apply Hidden Locked Candidates",
                    description = pointingDesc,
                    highlightCells = subsetCells + pointingEliminations.flatMap { it.cells },
                    regions = regions,
                    colouredCells = colouredSubsetCells,
                    colouredCandidates = subsetCandidates + pointingEliminationCandidates
                ))
            }
        }

        return steps
    }

    fun generateSingleSteps(
        techniqueName: String,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>,
        solvedCells: List<SolvedCellDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()

        if (solvedCells.isEmpty()) return steps

        val solved = solvedCells.first()
        val cellIndex = solved.cell
        val row = cellIndex / 9
        val col = cellIndex % 9
        val digit = solved.digit

        // Try to extract sector info from SubsetMatch
        var sectorIndex: Int? = null
        var sectorType: String? = null
        try {
            val matchClass = match.javaClass
            val sectorsField = matchClass.getDeclaredField("sectors")
            sectorsField.isAccessible = true
            val sectors = sectorsField.get(match) as java.util.BitSet
            sectorIndex = sectors.nextSetBit(0)
            if (sectorIndex >= 0) {
                sectorType = getSectorType(sectorIndex)
            }
        } catch (e: Exception) {
            // Fallback if reflection fails
        }

        val houseName = when (sectorType) {
            "row" -> "Row ${row + 1}"
            "column" -> "Column ${col + 1}"
            "box" -> "Box ${(row / 3) * 3 + (col / 3) + 1}"
            else -> "this house"
        }

        if (techniqueName.contains("Naked", ignoreCase = true)) {
            // Naked Single: cell has only one candidate
            // For Step 1, highlight the cell itself (no region needed - it's about the cell's candidates)
            steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = "Identify the Naked Single",
                description = "Cell R${row + 1}C${col + 1} has only one possible candidate: $digit",
                highlightCells = listOf(cellIndex),
                colouredCells = listOf(ColouredCellDto(cellIndex, "warning")),
                colouredCandidates = listOf(ColouredCandidateDto(row, col, digit, "target"))
            ))

            // Step 2: Show eliminations from peers - highlight all three houses
            val peerEliminations = eliminations.filter { it.digit == digit }
            val eliminationCandidates = peerEliminations.flatMap { elim ->
                elim.cells.map { c ->
                    ColouredCandidateDto(c / 9, c % 9, elim.digit, "elimination")
                }
            }

            val boxNum = (row / 3) * 3 + (col / 3) + 1
            val eliminationDesc = if (peerEliminations.isNotEmpty()) {
                val cells = peerEliminations.flatMap { it.cells }.map { "R${it/9 + 1}C${it%9 + 1}" }
                "Place $digit in R${row + 1}C${col + 1}. Eliminate $digit from Row ${row + 1}, Column ${col + 1}, and Box $boxNum: ${cells.joinToString(", ")}"
            } else {
                "Place $digit in R${row + 1}C${col + 1}"
            }

            // Highlight all three houses that see this cell
            val allRegions = listOf(
                ColouredRegionDto("row", row, "primary"),
                ColouredRegionDto("column", col, "primary"),
                ColouredRegionDto("box", (row / 3) * 3 + (col / 3), "primary")
            )

            steps.add(ExplanationStepDto(
                stepNumber = 2,
                title = "Place Value and Eliminate",
                description = eliminationDesc,
                highlightCells = listOf(cellIndex),
                regions = allRegions,
                colouredCells = listOf(ColouredCellDto(cellIndex, "target")),
                colouredCandidates = listOf(ColouredCandidateDto(row, col, digit, "target")) + eliminationCandidates
            ))
        } else {
            // Hidden Single: digit can only go in one cell in a house

            // Separate eliminations: those in the cell itself vs those in peer cells
            val cellEliminations = eliminations.filter { it.cells.contains(cellIndex) && it.digit != digit }
            val peerEliminations = eliminations.filter { it.digit == digit && !it.cells.all { c -> c == cellIndex } }

            // Step 1: Identify the hidden single in the house
            val regions = if (sectorIndex != null && sectorIndex >= 0) {
                listOf(ColouredRegionDto(sectorType ?: "row", sectorIndex % 9, "primary"))
            } else {
                emptyList()
            }

            steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = "Identify the Hidden Single",
                description = "In $houseName, only R${row + 1}C${col + 1} can be $digit. Thus, eliminate other candidates from this cell.",
                highlightCells = listOf(cellIndex),
                regions = regions,
                colouredCells = listOf(ColouredCellDto(cellIndex, "warning")),
                colouredCandidates = listOf(ColouredCandidateDto(row, col, digit, "target")) +
                    cellEliminations.flatMap { elim ->
                        elim.cells.filter { it == cellIndex }.map { c ->
                            ColouredCandidateDto(c / 9, c % 9, elim.digit, "elimination")
                        }
                    }
            ))

            // Step 2: Show eliminations from peer cells (row, column, box)
            if (peerEliminations.isNotEmpty()) {
                val peerCells = peerEliminations.flatMap { it.cells }.filter { it != cellIndex }.distinct()
                val peerCellNames = peerCells.map { "R${it/9 + 1}C${it%9 + 1}" }
                val boxNum = (row / 3) * 3 + (col / 3) + 1

                // Highlight all three houses that see this cell
                val allRegions = listOf(
                    ColouredRegionDto("row", row, "primary"),
                    ColouredRegionDto("column", col, "primary"),
                    ColouredRegionDto("box", (row / 3) * 3 + (col / 3), "primary")
                )

                steps.add(ExplanationStepDto(
                    stepNumber = 2,
                    title = "Eliminate from Peers",
                    description = "In Row ${row + 1}, Column ${col + 1}, and Box $boxNum, $digit can only be in R${row + 1}C${col + 1}. Eliminate $digit from: ${peerCellNames.joinToString(", ")}",
                    highlightCells = peerCells,
                    regions = allRegions,
                    colouredCells = listOf(ColouredCellDto(cellIndex, "target")),
                    colouredCandidates = listOf(ColouredCandidateDto(row, col, digit, "target")) +
                        peerEliminations.flatMap { elim ->
                            elim.cells.filter { it != cellIndex }.map { c ->
                                ColouredCandidateDto(c / 9, c % 9, elim.digit, "elimination")
                            }
                        }
                ))
            }
        }

        return steps
    }
