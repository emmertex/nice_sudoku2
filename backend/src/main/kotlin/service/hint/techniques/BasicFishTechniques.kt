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

    /**
     * Generate step-by-step explanation for Skyscraper pattern
     */
    fun generateSkyscraperSteps(
        techniqueName: String,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        val eliminationCells = eliminations.flatMap { it.cells }.distinct()

        // Extract pattern data
        var digit = eliminations.firstOrNull()?.digit ?: 0
        val baseIndices = mutableListOf<Int>()
        val coverIndices = mutableListOf<Int>()
        var finCells = java.util.BitSet()

        try {
            val matchClass = match.javaClass
            val digitField = matchClass.getDeclaredField("digit")
            val baseSecsField = matchClass.getDeclaredField("baseSecs")
            val coverSecsField = matchClass.getDeclaredField("coverSecs")

            digitField.isAccessible = true
            baseSecsField.isAccessible = true
            coverSecsField.isAccessible = true

            digit = (digitField.get(match) as Int) + 1
            val baseSecs = baseSecsField.get(match) as java.util.BitSet
            val coverSecs = coverSecsField.get(match) as java.util.BitSet

            var idx = baseSecs.nextSetBit(0)
            while (idx >= 0) {
                baseIndices.add(idx)
                idx = baseSecs.nextSetBit(idx + 1)
            }

            idx = coverSecs.nextSetBit(0)
            while (idx >= 0) {
                coverIndices.add(idx)
                idx = coverSecs.nextSetBit(idx + 1)
            }

            // Try to get fin cells (endpoints of skyscraper)
            try {
                val finsField = matchClass.getDeclaredField("fins")
                finsField.isAccessible = true
                finCells = finsField.get(match) as java.util.BitSet
            } catch (e: Exception) {}

        } catch (e: Exception) {}

        val baseType = if (baseIndices.isNotEmpty()) getSectorType(baseIndices.first()) else "row"
        val baseTypeText = if (baseType == "row") "rows" else "columns"
        
        // Get cells involved
        val baseCells = baseIndices.flatMap { getSectorCells(it) }
        val coverCells = coverIndices.flatMap { getSectorCells(it) }
        
        // Intersection cells (the 4 corners of the skyscraper pattern)
        val patternCells = baseCells.filter { it in coverCells }.distinct()
        
        // Get endpoint cells (fins)
        val endpointCells = mutableListOf<Int>()
        var finIdx = finCells.nextSetBit(0)
        while (finIdx >= 0) {
            endpointCells.add(finIdx)
            finIdx = finCells.nextSetBit(finIdx + 1)
        }
        
        // If we couldn't get fin cells, try to identify them from the pattern
        val actualEndpoints = if (endpointCells.isNotEmpty()) endpointCells else {
            // Endpoints are cells that are in the pattern but not shared
            patternCells.filter { cell ->
                val row = cell / 9
                val col = cell % 9
                val cellsInSameBase = patternCells.filter { other ->
                    if (baseType == "row") other / 9 == row else other % 9 == col
                }
                cellsInSameBase.size == 1
            }
        }

        val baseNames = baseIndices.map { idx ->
            if (baseType == "row") "Row ${idx % 9 + 1}" else "Column ${idx % 9 + 1}"
        }
        val baseNamesText = baseNames.joinToString(" and ")

        // Build visual elements
        val patternCandidates = patternCells.map { cell ->
            ColouredCandidateDto(cell / 9, cell % 9, digit, "target")
        }
        val endpointColouredCells = actualEndpoints.map { ColouredCellDto(it, "warning") }
        
        // Build lines connecting the strong links
        val skyscraperLines = mutableListOf<LineDto>()
        
        // For each base sector, find the strong link (2 cells with the digit)
        for (baseIdx in baseIndices) {
            val cellsInBase = patternCells.filter { cell ->
                val sectorCells = getSectorCells(baseIdx)
                cell in sectorCells
            }
            if (cellsInBase.size >= 2) {
                val c1 = cellsInBase[0]
                val c2 = cellsInBase[1]
                skyscraperLines.add(LineDto(
                    from = CandidateLocationDto(c1 / 9, c1 % 9, digit),
                    to = CandidateLocationDto(c2 / 9, c2 % 9, digit),
                    isStrongLink = true,
                    lineType = "skyscraper-strong",
                    description = "Strong link: exactly 2 candidates for $digit"
                ))
            }
        }

        // Step 1: Explain Skyscraper concept (ELI5)
        val step1Description = "A Skyscraper is a pattern with two **strong links** on the same digit. " +
            "\n\nLook at $baseNamesText. In each of these $baseTypeText, digit $digit appears in exactly TWO cells. " +
            "That's a strong link - if one cell doesn't have $digit, the other MUST have it. " +
            "\n\nThe two strong links are connected: one end of each strong link shares the same column (or row). " +
            "The other ends - the 'roof' of the skyscraper (shown in yellow) - are the key cells."

        steps.add(ExplanationStepDto(
            stepNumber = 1,
            title = "Find the Two Strong Links",
            description = step1Description,
            highlightCells = patternCells,
            colouredCells = endpointColouredCells,
            colouredCandidates = patternCandidates,
            lines = skyscraperLines
        ))

        // Step 2: Explain the logic
        val step2Description = "Here's why the Skyscraper works: " +
            "\n\nThe connected ends of the two strong links are in the same column/row. " +
            "If $digit is NOT in one of the connected cells, the strong link forces it to be in that cell's partner. " +
            "But that partner is the 'roof' cell! " +
            "\n\nSo no matter what: either $digit is in one roof cell, or it's in the other roof cell " +
            "(or possibly both). One of the roof cells MUST have $digit."

        steps.add(ExplanationStepDto(
            stepNumber = 2,
            title = "Follow the Logic",
            description = step2Description,
            highlightCells = actualEndpoints,
            colouredCells = endpointColouredCells,
            colouredCandidates = patternCandidates,
            lines = skyscraperLines
        ))

        // Step 3: Make eliminations
        if (eliminations.isNotEmpty()) {
            val eliminationNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")
            
            val step3Description = "Any cell that can see BOTH roof cells cannot have $digit. " +
                "\n\nWhy? Because one of the roof cells must have $digit, and that would eliminate $digit from any cell seeing it. " +
                "Since the elimination cell sees both roof cells, it will always be eliminated. " +
                "\n\nRemove $digit from: $eliminationNames."

            steps.add(ExplanationStepDto(
                stepNumber = 3,
                title = "Eliminate from Cells Seeing Both Roofs",
                description = step3Description,
                highlightCells = eliminationCells,
                colouredCells = endpointColouredCells + eliminationCells.map { ColouredCellDto(it, "warning") },
                colouredCandidates = patternCandidates + eliminationCandidates(eliminations),
                lines = skyscraperLines
            ))
        }

        return steps
    }

    /**
     * Generate step-by-step explanation for Finned Fish patterns
     */
    fun generateFinnedFishSteps(
        techniqueName: String,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        val eliminationCells = eliminations.flatMap { it.cells }.distinct()

        // Determine fish type
        val isSashimi = techniqueName.contains("Sashimi", ignoreCase = true)
        val fishType = when {
            techniqueName.contains("Jellyfish", ignoreCase = true) -> "Jellyfish"
            techniqueName.contains("Swordfish", ignoreCase = true) -> "Swordfish"
            else -> "X-Wing"
        }

        // Extract pattern data
        var digit = eliminations.firstOrNull()?.digit ?: 0
        val baseIndices = mutableListOf<Int>()
        val coverIndices = mutableListOf<Int>()
        var finCells = java.util.BitSet()

        try {
            val matchClass = match.javaClass
            val digitField = matchClass.getDeclaredField("digit")
            val baseSecsField = matchClass.getDeclaredField("baseSecs")
            val coverSecsField = matchClass.getDeclaredField("coverSecs")
            val finsField = matchClass.getDeclaredField("fins")

            digitField.isAccessible = true
            baseSecsField.isAccessible = true
            coverSecsField.isAccessible = true
            finsField.isAccessible = true

            digit = (digitField.get(match) as Int) + 1
            val baseSecs = baseSecsField.get(match) as java.util.BitSet
            val coverSecs = coverSecsField.get(match) as java.util.BitSet
            finCells = finsField.get(match) as java.util.BitSet

            var idx = baseSecs.nextSetBit(0)
            while (idx >= 0) {
                baseIndices.add(idx)
                idx = baseSecs.nextSetBit(idx + 1)
            }

            idx = coverSecs.nextSetBit(0)
            while (idx >= 0) {
                coverIndices.add(idx)
                idx = coverSecs.nextSetBit(idx + 1)
            }
        } catch (e: Exception) {}

        val baseType = if (baseIndices.isNotEmpty()) getSectorType(baseIndices.first()) else "row"
        val baseTypeText = if (baseType == "row") "rows" else "columns"
        val coverTypeText = if (baseType == "row") "columns" else "rows"

        // Get cells
        val baseCells = baseIndices.flatMap { getSectorCells(it) }
        val coverCells = coverIndices.flatMap { getSectorCells(it) }
        val patternCells = baseCells.filter { it in coverCells }.distinct()

        // Get fin cells
        val finCellsList = mutableListOf<Int>()
        var finIdx = finCells.nextSetBit(0)
        while (finIdx >= 0) {
            finCellsList.add(finIdx)
            finIdx = finCells.nextSetBit(finIdx + 1)
        }

        // Find the box containing the fin
        val finBox = if (finCellsList.isNotEmpty()) {
            val cell = finCellsList.first()
            (cell / 27) * 3 + ((cell % 9) / 3)
        } else -1

        val baseNames = baseIndices.map { idx ->
            if (baseType == "row") "Row ${idx % 9 + 1}" else "Column ${idx % 9 + 1}"
        }.joinToString(", ")

        val coverNames = coverIndices.map { idx ->
            if (baseType == "row") "Column ${idx % 9 + 1}" else "Row ${idx % 9 + 1}"
        }.joinToString(", ")

        // Build visual elements
        val patternCandidates = patternCells.map { cell ->
            ColouredCandidateDto(cell / 9, cell % 9, digit, "target")
        }
        val finCandidates = finCellsList.map { cell ->
            ColouredCandidateDto(cell / 9, cell % 9, digit, "warning")
        }
        val finColouredCells = finCellsList.map { ColouredCellDto(it, "warning") }

        val finCellNames = finCellsList.map { formatCellName(it) }.joinToString(", ")
        val finDescription = if (isSashimi) "Sashimi fin" else "fin"

        // Step 1: Explain the base fish pattern
        val step1Description = "This is a **Finned $fishType**. Let's start with the basic pattern: " +
            "\n\nLook at $baseNames. In a normal $fishType, digit $digit would appear only in cells that align " +
            "with $coverNames. But here, there's an extra cell (or cells) that doesn't fit - that's the '$finDescription'. " +
            "\n\nThe fin is at: $finCellNames (shown in yellow). " +
            if (isSashimi) "\n\nIn a Sashimi variant, the fish would be incomplete without the fin - " +
                "the fin is actually 'filling in' for a missing base cell." else ""

        steps.add(ExplanationStepDto(
            stepNumber = 1,
            title = "Find the Finned Pattern",
            description = step1Description,
            highlightCells = patternCells + finCellsList,
            colouredCells = finColouredCells,
            colouredCandidates = patternCandidates + finCandidates
        ))

        // Step 2: Explain how the fin affects eliminations
        val boxName = if (finBox >= 0) "Box ${finBox + 1}" else "the fin's box"
        
        val step2Description = "The fin changes what we can eliminate. Here's the logic: " +
            "\n\n**If the fin is TRUE** (has $digit): The normal fish pattern applies to the rest, " +
            "but we can only eliminate from cells that ALSO see the fin. " +
            "\n\n**If the fin is FALSE**: Then $digit must be somewhere else in the fish pattern, " +
            "and the normal fish eliminations would apply. " +
            "\n\nEither way, any cell that sees both the fin AND the fish pattern can be eliminated! " +
            "This means eliminations are restricted to $boxName (where the fin is)."

        steps.add(ExplanationStepDto(
            stepNumber = 2,
            title = "Understand the Fin's Effect",
            description = step2Description,
            highlightCells = finCellsList,
            colouredCells = finColouredCells,
            colouredCandidates = patternCandidates + finCandidates,
            regions = if (finBox >= 0) listOf(ColouredRegionDto("box", finBox, "secondary")) else emptyList()
        ))

        // Step 3: Make eliminations
        if (eliminations.isNotEmpty()) {
            val eliminationNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")
            
            val step3Description = "Cells that can see the fin AND are in the fish's cover lines can be eliminated. " +
                "\n\nThese cells are in $boxName (so they see the fin) and also in one of the cover $coverTypeText " +
                "(so the fish pattern affects them). " +
                "\n\nRemove $digit from: $eliminationNames."

            steps.add(ExplanationStepDto(
                stepNumber = 3,
                title = "Eliminate Where Fin and Fish Meet",
                description = step3Description,
                highlightCells = eliminationCells,
                colouredCells = finColouredCells + eliminationCells.map { ColouredCellDto(it, "warning") },
                colouredCandidates = patternCandidates + finCandidates + eliminationCandidates(eliminations),
                regions = if (finBox >= 0) listOf(ColouredRegionDto("box", finBox, "secondary")) else emptyList()
            ))
        }

        return steps
    }
