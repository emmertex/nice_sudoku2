package service.hint.explanations

import service.hint.helpers.*
import service.hint.helpers.LanguageKeyBuilder.hintKey
import service.hint.helpers.LanguageKeyBuilder.commonKey
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
            val techKey = "naked_${subsetType.lowercase()}"
            steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = hintKey(techKey, 1, "title"),
                description = hintKey(techKey, 1, "description",
                    "cells" to cellNames,
                    "house" to houseName,
                    "digits" to digitNames
                ),
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

                val elimCells = normalEliminations.flatMap { it.cells }.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
                val techKey = "naked_${subsetType.lowercase()}"
                
                steps.add(ExplanationStepDto(
                    stepNumber = 2,
                    title = hintKey(techKey, 2, "title"),
                    description = hintKey(techKey, 2, "description",
                        "digits" to digitNames,
                        "subsetCells" to cellNames,
                        "elimCells" to elimCells,
                        "house" to houseName
                    ),
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

                val elimCells = pointingEliminations.flatMap { it.cells }.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
                val elimDigits = pointingEliminations.map { it.digit }.distinct().joinToString(", ")
                val techKey = "naked_${subsetType.lowercase()}"
                
                steps.add(ExplanationStepDto(
                    stepNumber = if (normalEliminations.isNotEmpty()) 3 else 2,
                    title = hintKey(techKey, 3, "title"),
                    description = hintKey(techKey, 3, "description",
                        "subsetType" to subsetType,
                        "digits" to elimDigits,
                        "house" to houseName,
                        "cells" to elimCells
                    ),
                    highlightCells = subsetCells + pointingEliminations.flatMap { it.cells },
                    regions = regions,
                    colouredCells = colouredSubsetCells,
                    colouredCandidates = subsetCandidates + pointingEliminationCandidates
                ))
            }
        } else {
            // Hidden Subset: digits can only be in these cells
            val techKey = "hidden_${subsetType.lowercase()}"
            steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = hintKey(techKey, 1, "title"),
                description = hintKey(techKey, 1, "description",
                    "house" to houseName,
                    "digits" to digitNames,
                    "cells" to cellNames
                ),
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

                val techKey = "hidden_${subsetType.lowercase()}"
                
                steps.add(ExplanationStepDto(
                    stepNumber = 2,
                    title = hintKey(techKey, 2, "title"),
                    description = hintKey(techKey, 2, "description",
                        "digits" to digitNames,
                        "cells" to cellNames
                    ),
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

                val elimCells = pointingEliminations.flatMap { it.cells }.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
                val elimDigits = pointingEliminations.map { it.digit }.distinct().joinToString(", ")
                val techKey = "hidden_${subsetType.lowercase()}"
                
                steps.add(ExplanationStepDto(
                    stepNumber = if (normalEliminations.isNotEmpty()) 3 else 2,
                    title = hintKey(techKey, 3, "title"),
                    description = hintKey(techKey, 3, "description",
                        "subsetType" to subsetType,
                        "digits" to elimDigits,
                        "house" to houseName,
                        "cells" to elimCells
                    ),
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
            val cellName = "R${row + 1}C${col + 1}"
            steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = hintKey("naked_single", 1, "title"),
                description = hintKey("naked_single", 1, "description", 
                    "cell" to cellName,
                    "digit" to digit.toString()
                ),
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
            val cellName = "R${row + 1}C${col + 1}"
            val cells = peerEliminations.flatMap { it.cells }.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
            
            // Highlight all three houses that see this cell
            val allRegions = listOf(
                ColouredRegionDto("row", row, "primary"),
                ColouredRegionDto("column", col, "primary"),
                ColouredRegionDto("box", (row / 3) * 3 + (col / 3), "primary")
            )

            steps.add(ExplanationStepDto(
                stepNumber = 2,
                title = hintKey("naked_single", 2, "title"),
                description = hintKey("naked_single", 2, "description",
                    "cell" to cellName,
                    "digit" to digit.toString(),
                    "row" to (row + 1).toString(),
                    "col" to (col + 1).toString(),
                    "box" to boxNum.toString(),
                    "cells" to cells
                ),
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

            val cellName = "R${row + 1}C${col + 1}"
            steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = hintKey("hidden_single", 1, "title"),
                description = hintKey("hidden_single", 1, "description",
                    "house" to houseName,
                    "cell" to cellName,
                    "digit" to digit.toString()
                ),
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
                val peerCellNames = peerCells.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
                val boxNum = (row / 3) * 3 + (col / 3) + 1
                val cellName = "R${row + 1}C${col + 1}"

                // Highlight all three houses that see this cell
                val allRegions = listOf(
                    ColouredRegionDto("row", row, "primary"),
                    ColouredRegionDto("column", col, "primary"),
                    ColouredRegionDto("box", (row / 3) * 3 + (col / 3), "primary")
                )

                steps.add(ExplanationStepDto(
                    stepNumber = 2,
                    title = hintKey("hidden_single", 2, "title"),
                    description = hintKey("hidden_single", 2, "description",
                        "row" to (row + 1).toString(),
                        "col" to (col + 1).toString(),
                        "box" to boxNum.toString(),
                        "digit" to digit.toString(),
                        "cell" to cellName,
                        "cells" to peerCellNames
                    ),
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
