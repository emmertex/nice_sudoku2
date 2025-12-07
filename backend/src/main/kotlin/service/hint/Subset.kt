package service.hint

import sudoku.match.SubsetMatch
import service.hintHelpers.*
import sudoku.match.TechniqueMatch
import dto.*

    /**
     * Extract visual data from SubsetMatch (Naked/Hidden Singles through Quadruples)
     */
    fun extractSubsetVisualData(match: TechniqueMatch): Triple<List<LineDto>, List<GroupDto>, String?> {
        val lines = mutableListOf<LineDto>()
        val groups = mutableListOf<GroupDto>()

        try {
            // Use reflection to access private fields
            val matchClass = match.javaClass
            val digitsField = matchClass.getDeclaredField("digits")
            val cellsField = matchClass.getDeclaredField("cells")
            val sectorsField = matchClass.getDeclaredField("sectors")
            val nakedField = matchClass.getDeclaredField("naked")

            digitsField.isAccessible = true
            cellsField.isAccessible = true
            sectorsField.isAccessible = true
            nakedField.isAccessible = true

            val digits = digitsField.get(match) as java.util.BitSet
            val cells = cellsField.get(match) as java.util.BitSet
            val sectors = sectorsField.get(match) as java.util.BitSet
            val naked = nakedField.get(match) as Boolean

            // Convert BitSets to lists
            val digitList = bitSetToList(digits).map { it + 1 } // Convert to 1-9
            val cellList = bitSetToList(cells)

            if (naked) {
                // Naked subsets: all digits in the subset cells
                val candidates = mutableListOf<CandidateLocationDto>()
                for (cell in cellList) {
                    val row = cell / 9
                    val col = cell % 9
                    for (digit in digitList) {
                        candidates.add(CandidateLocationDto(row, col, digit))
                    }
                }

                if (candidates.isNotEmpty()) {
                    groups.add(GroupDto(
                        candidates = candidates,
                        groupType = "naked-subset",
                        colorIndex = 0
                    ))
                }
            } else {
                // Hidden subsets: create two groups
                // Group 1: The subset cells with their digits
                val subsetCandidates = mutableListOf<CandidateLocationDto>()
                for (cell in cellList) {
                    val row = cell / 9
                    val col = cell % 9
                    for (digit in digitList) {
                        subsetCandidates.add(CandidateLocationDto(row, col, digit))
                    }
                }

                if (subsetCandidates.isNotEmpty()) {
                    groups.add(GroupDto(
                        candidates = subsetCandidates,
                        groupType = "hidden-subset-cells",
                        colorIndex = 0
                    ))
                }

                // Group 2: Other cells in the sectors that have these digits (showing why they're hidden)
                val eliminationCandidates = mutableListOf<CandidateLocationDto>()
                var sector = sectors.nextSetBit(0)
                while (sector >= 0) {
                    val sectorCells = getSectorCells(sector)
                    for (cell in sectorCells) {
                        if (!cellList.contains(cell)) {
                            val row = cell / 9
                            val col = cell % 9
                            for (digit in digitList) {
                                eliminationCandidates.add(CandidateLocationDto(row, col, digit))
                            }
                        }
                    }
                    sector = sectors.nextSetBit(sector + 1)
                }

                if (eliminationCandidates.isNotEmpty()) {
                    groups.add(GroupDto(
                        candidates = eliminationCandidates,
                        groupType = "hidden-subset-eliminations",
                        colorIndex = 1
                    ))
                }
            }

        } catch (e: Exception) {
            // If reflection fails, return empty data
            e.printStackTrace()
        }

        return Triple(lines, groups, null)
    }


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
            if (sectorIndex != null && sectorIndex >= 0) {
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
            listOf(ColoredRegionDto(sectorType ?: "row", sectorIndex % 9, "primary"))
        } else {
            emptyList()
        }
        
        // Build colored cells for the subset
        val coloredSubsetCells = subsetCells.map { ColoredCellDto(it, "warning") }
        
        // Build colored candidates for the subset digits in subset cells
        val subsetCandidates = subsetCells.flatMap { cell ->
            val r = cell / 9
            val c = cell % 9
            subsetDigits.map { digit -> ColoredCandidateDto(r, c, digit, "target") }
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
                description = "In $houseName, cells $cellNames only contain candidates {$digitNames}. These digits are locked to these cells.",
                highlightCells = subsetCells,
                regions = regions,
                coloredCells = coloredSubsetCells,
                coloredCandidates = subsetCandidates
            ))
            
            var stepNumber = 2
            
            // Step 2: Normal eliminations within the sector
            if (normalEliminations.isNotEmpty()) {
                val eliminationCandidates = normalEliminations.flatMap { elim ->
                    elim.cells.map { c ->
                        ColoredCandidateDto(c / 9, c % 9, elim.digit, "elimination")
                    }
                }
                val eliminationCells = normalEliminations.flatMap { it.cells }.distinct()
                val eliminationCellNames = eliminationCells.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
                val elimDigitNames = normalEliminations.map { it.digit }.distinct().sorted().joinToString(", ")
                
                steps.add(ExplanationStepDto(
                    stepNumber = stepNumber++,
                    title = "Eliminate from $houseName",
                    description = "Eliminate {$elimDigitNames} from other cells in $houseName: $eliminationCellNames",
                    highlightCells = eliminationCells,
                    regions = regions,
                    coloredCells = coloredSubsetCells,
                    coloredCandidates = subsetCandidates + eliminationCandidates
                ))
            }
            
            // Step 3 (or 2 if no normal eliminations): Pointing eliminations (locked candidates effect)
            if (pointingEliminations.isNotEmpty()) {
                // Group pointing eliminations by the box they affect
                val pointingByBox = pointingEliminations.flatMap { elim ->
                    elim.cells.filter { it !in sectorCells }.map { cell ->
                        val boxIndex = (cell / 9 / 3) * 3 + (cell % 9 / 3)
                        Triple(boxIndex, elim.digit, cell)
                    }
                }.groupBy { it.first }
                
                for ((boxIndex, elimsInBox) in pointingByBox) {
                    val affectedDigits = elimsInBox.map { it.second }.distinct().sorted()
                    val affectedCells = elimsInBox.map { it.third }.distinct()
                    val affectedCellNames = affectedCells.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
                    val affectedDigitNames = affectedDigits.joinToString(", ")
                    
                    // Find which subset cells are in this box
                    val subsetCellsInBox = subsetCells.filter { cell ->
                        val cellBox = (cell / 9 / 3) * 3 + (cell % 9 / 3)
                        cellBox == boxIndex
                    }
                    val subsetCellsInBoxNames = subsetCellsInBox.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
                    
                    val eliminationCandidates = affectedCells.flatMap { cell ->
                        affectedDigits.map { digit ->
                            ColoredCandidateDto(cell / 9, cell % 9, digit, "elimination")
                        }
                    }
                    
                    // Highlight the subset cells that are in this box with target candidates
                    val boxSubsetCandidates = subsetCellsInBox.flatMap { cell ->
                        affectedDigits.map { digit ->
                            ColoredCandidateDto(cell / 9, cell % 9, digit, "target")
                        }
                    }
                    
                    // Regions: highlight both the main sector and the affected box
                    val pointingRegions = listOfNotNull(
                        if (sectorIndex != null && sectorIndex >= 0) {
                            ColoredRegionDto(sectorType ?: "row", sectorIndex % 9, "primary")
                        } else null,
                        ColoredRegionDto("box", boxIndex, "secondary")
                    )
                    
                    steps.add(ExplanationStepDto(
                        stepNumber = stepNumber++,
                        title = "Pointing: Eliminate $affectedDigitNames from Box ${boxIndex + 1}",
                        description = "In Box ${boxIndex + 1}, $affectedDigitNames can only be in $subsetCellsInBoxNames (part of the $subsetType in $houseName). Eliminate $affectedDigitNames from $affectedCellNames.",
                        highlightCells = affectedCells + subsetCellsInBox,
                        regions = pointingRegions,
                        coloredCells = subsetCellsInBox.map { ColoredCellDto(it, "warning") },
                        coloredCandidates = boxSubsetCandidates + eliminationCandidates
                    ))
                }
            }
        } else {
            // Hidden Subset: digits only appear in these cells
            steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = "Identify the Hidden $subsetType",
                description = "In $houseName, candidates {$digitNames} only appear in cells $cellNames. These cells must contain these digits.",
                highlightCells = subsetCells,
                regions = regions,
                coloredCells = coloredSubsetCells,
                coloredCandidates = subsetCandidates
            ))
            
            var stepNumber = 2
            
            // Step 2: Normal eliminations (other candidates from subset cells)
            val cellEliminations = eliminations.filter { elim ->
                elim.cells.any { it in subsetCells }
            }
            if (cellEliminations.isNotEmpty()) {
                val eliminationCandidates = cellEliminations.flatMap { elim ->
                    elim.cells.filter { it in subsetCells }.map { c ->
                        ColoredCandidateDto(c / 9, c % 9, elim.digit, "elimination")
                    }
                }
                val otherDigits = cellEliminations.map { it.digit }.distinct().sorted()
                val otherDigitNames = otherDigits.joinToString(", ")
                
                steps.add(ExplanationStepDto(
                    stepNumber = stepNumber++,
                    title = "Eliminate Other Candidates",
                    description = "Since cells $cellNames must contain {$digitNames}, eliminate other candidates {$otherDigitNames} from these cells.",
                    highlightCells = subsetCells,
                    regions = regions,
                    coloredCells = coloredSubsetCells,
                    coloredCandidates = subsetCandidates + eliminationCandidates
                ))
            }
            
            // Step 3: Pointing eliminations for hidden subsets (similar logic)
            val peerEliminations = eliminations.filter { elim ->
                elim.cells.any { it !in subsetCells }
            }
            if (peerEliminations.isNotEmpty()) {
                // Group by box affected
                val pointingByBox = peerEliminations.flatMap { elim ->
                    elim.cells.filter { it !in subsetCells && it !in sectorCells }.map { cell ->
                        val boxIndex = (cell / 9 / 3) * 3 + (cell % 9 / 3)
                        Triple(boxIndex, elim.digit, cell)
                    }
                }.groupBy { it.first }
                
                for ((boxIndex, elimsInBox) in pointingByBox) {
                    val affectedDigits = elimsInBox.map { it.second }.distinct().sorted()
                    val affectedCells = elimsInBox.map { it.third }.distinct()
                    val affectedCellNames = affectedCells.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
                    val affectedDigitNames = affectedDigits.joinToString(", ")
                    
                    val subsetCellsInBox = subsetCells.filter { cell ->
                        val cellBox = (cell / 9 / 3) * 3 + (cell % 9 / 3)
                        cellBox == boxIndex
                    }
                    val subsetCellsInBoxNames = subsetCellsInBox.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
                    
                    val eliminationCandidates = affectedCells.flatMap { cell ->
                        affectedDigits.map { digit ->
                            ColoredCandidateDto(cell / 9, cell % 9, digit, "elimination")
                        }
                    }
                    
                    val boxSubsetCandidates = subsetCellsInBox.flatMap { cell ->
                        affectedDigits.map { digit ->
                            ColoredCandidateDto(cell / 9, cell % 9, digit, "target")
                        }
                    }
                    
                    val pointingRegions = listOfNotNull(
                        if (sectorIndex != null && sectorIndex >= 0) {
                            ColoredRegionDto(sectorType ?: "row", sectorIndex % 9, "primary")
                        } else null,
                        ColoredRegionDto("box", boxIndex, "secondary")
                    )
                    
                    steps.add(ExplanationStepDto(
                        stepNumber = stepNumber++,
                        title = "Pointing: Eliminate $affectedDigitNames from Box ${boxIndex + 1}",
                        description = "In Box ${boxIndex + 1}, $affectedDigitNames can only be in $subsetCellsInBoxNames (part of the $subsetType in $houseName). Eliminate $affectedDigitNames from $affectedCellNames.",
                        highlightCells = affectedCells + subsetCellsInBox,
                        regions = pointingRegions,
                        coloredCells = subsetCellsInBox.map { ColoredCellDto(it, "warning") },
                        coloredCandidates = boxSubsetCandidates + eliminationCandidates
                    ))
                }
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
            if (sectorIndex != null && sectorIndex >= 0) {
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
                coloredCells = listOf(ColoredCellDto(cellIndex, "warning")),
                coloredCandidates = listOf(ColoredCandidateDto(row, col, digit, "target"))
            ))
            
            // Step 2: Show eliminations from peers - highlight all three houses
            val peerEliminations = eliminations.filter { it.digit == digit }
            val eliminationCandidates = peerEliminations.flatMap { elim ->
                elim.cells.map { c ->
                    ColoredCandidateDto(c / 9, c % 9, elim.digit, "elimination")
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
                ColoredRegionDto("row", row, "primary"),
                ColoredRegionDto("column", col, "primary"),
                ColoredRegionDto("box", (row / 3) * 3 + (col / 3), "primary")
            )
            
            steps.add(ExplanationStepDto(
                stepNumber = 2,
                title = "Place Value and Eliminate",
                description = eliminationDesc,
                highlightCells = listOf(cellIndex),
                regions = allRegions,
                coloredCells = listOf(ColoredCellDto(cellIndex, "target")),
                coloredCandidates = listOf(ColoredCandidateDto(row, col, digit, "target")) + eliminationCandidates
            ))
        } else {
            // Hidden Single: digit can only go in one cell in a house
            
            // Separate eliminations: those in the cell itself vs those in peer cells
            val cellEliminations = eliminations.filter { it.cells.contains(cellIndex) && it.digit != digit }
            val peerEliminations = eliminations.filter { it.digit == digit && !it.cells.all { c -> c == cellIndex } }
            
            // Step 1: Identify the hidden single in the house
            val regions = if (sectorIndex != null && sectorIndex >= 0) {
                listOf(ColoredRegionDto(sectorType ?: "row", sectorIndex % 9, "primary"))
            } else {
                emptyList()
            }
            
            steps.add(ExplanationStepDto(
                stepNumber = 1,
                title = "Identify the Hidden Single",
                description = "In $houseName, only R${row + 1}C${col + 1} can be $digit. Thus, eliminate other candidates from this cell.",
                highlightCells = listOf(cellIndex),
                regions = regions,
                coloredCells = listOf(ColoredCellDto(cellIndex, "warning")),
                coloredCandidates = listOf(ColoredCandidateDto(row, col, digit, "target")) +
                    cellEliminations.flatMap { elim ->
                        elim.cells.filter { it == cellIndex }.map { c ->
                            ColoredCandidateDto(c / 9, c % 9, elim.digit, "elimination")
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
                    ColoredRegionDto("row", row, "primary"),
                    ColoredRegionDto("column", col, "primary"),
                    ColoredRegionDto("box", (row / 3) * 3 + (col / 3), "primary")
                )
                
                steps.add(ExplanationStepDto(
                    stepNumber = 2,
                    title = "Eliminate from Peers",
                    description = "In Row ${row + 1}, Column ${col + 1}, and Box $boxNum, $digit can only be in R${row + 1}C${col + 1}. Eliminate $digit from: ${peerCellNames.joinToString(", ")}",
                    highlightCells = peerCells,
                    regions = allRegions,
                    coloredCells = listOf(ColoredCellDto(cellIndex, "target")),
                    coloredCandidates = listOf(ColoredCandidateDto(row, col, digit, "target")) +
                        peerEliminations.flatMap { elim ->
                            elim.cells.filter { it != cellIndex }.map { c ->
                                ColoredCandidateDto(c / 9, c % 9, elim.digit, "elimination")
                            }
                        }
                ))
            }
        }
        
        return steps
    }
