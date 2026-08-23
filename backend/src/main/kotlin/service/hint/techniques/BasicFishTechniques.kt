package service.hint.techniques

import sudoku.match.FishMatch
import service.hint.helpers.*
import service.hint.helpers.LanguageKeyBuilder.hintKey
import service.hint.helpers.formatCellName
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

        // Determine base and cover types from the *whole* sector set, so a mixed
        // base (e.g. a Franken fish's row + box) reads "rows and boxes" rather than
        // being labelled by its first sector's type only.
        val baseTypeText = describeSectorTypeText(baseIndices)
        val coverTypeText = describeSectorTypeText(coverIndices)

        // Build region highlighting: base = primary (blue), cover = primary (blue)
        val baseRegions = baseIndices.map { idx ->
            ColouredRegionDto(getSectorType(idx), idx % 9, "primary")
        }
        val coverRegions = coverIndices.map { idx ->
            ColouredRegionDto(getSectorType(idx), idx % 9, "primary")
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

        // Build base/cover sector names using each sector's real type.
        val baseNames = baseIndices.map { formatSectorName(it) }.joinToString(" and ")
        val coverNames = coverIndices.map { formatSectorName(it) }.joinToString(" and ")

        // Determine the fish technique key based on pattern name
        val fishKey = when {
            techniqueName.contains("X-Wing", ignoreCase = true) -> "x_wing"
            techniqueName.contains("Swordfish", ignoreCase = true) -> "swordfish"
            techniqueName.contains("Jellyfish", ignoreCase = true) -> "jellyfish"
            else -> "basic_fish"
        }

        // Coloured cells for the intersection points (yellow border)
        val intersectionColouredCells = intersectionCells.map { ColouredCellDto(it, "warning") }

        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = hintKey(fishKey, 1, "title"),
                description = hintKey(fishKey, 1, "description",
                    "baseNames" to baseNames,
                    "digit" to digit.toString(),
                    "baseTypeText" to baseTypeText,
                    "coverNames" to coverNames,
                    "coverTypeText" to coverTypeText
                ),
                highlightCells = patternCells,
                regions = baseRegions,
                colouredCells = intersectionColouredCells,
                colouredCandidates = patternCandidates
            )
        )

        // Step 2: Explain why eliminations work
        if (eliminations.isNotEmpty()) {
            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = hintKey(fishKey, 2, "title",
                        "coverTypeText" to coverTypeText
                    ),
                    description = hintKey(fishKey, 2, "description",
                        "digit" to digit.toString(),
                        "baseNames" to baseNames,
                        "baseTypeText" to baseTypeText,
                        "coverNames" to coverNames,
                        "coverTypeText" to coverTypeText
                    ),
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
                    title = hintKey(fishKey, 3, "title",
                        "digit" to digit.toString()
                    ),
                    description = hintKey(fishKey, 3, "description",
                        "digit" to digit.toString(),
                        "cells" to eliminationNames
                    ),
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

        steps.add(ExplanationStepDto(
            stepNumber = 1,
            title = hintKey("skyscraper", 1, "title"),
            description = hintKey("skyscraper", 1, "description",
                "baseNamesText" to baseNamesText,
                "baseTypeText" to baseTypeText,
                "digit" to digit.toString()
            ),
            highlightCells = patternCells,
            colouredCells = endpointColouredCells,
            colouredCandidates = patternCandidates,
            lines = skyscraperLines
        ))

        steps.add(ExplanationStepDto(
            stepNumber = 2,
            title = hintKey("skyscraper", 2, "title"),
            description = hintKey("skyscraper", 2, "description",
                "digit" to digit.toString()
            ),
            highlightCells = actualEndpoints,
            colouredCells = endpointColouredCells,
            colouredCandidates = patternCandidates,
            lines = skyscraperLines
        ))

        // Step 3: Make eliminations
        if (eliminations.isNotEmpty()) {
            val eliminationNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")

            steps.add(ExplanationStepDto(
                stepNumber = 3,
                title = hintKey("skyscraper", 3, "title"),
                description = hintKey("skyscraper", 3, "description",
                    "digit" to digit.toString(),
                    "cells" to eliminationNames
                ),
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

        val coverTypeText = describeSectorTypeText(coverIndices)

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

        val baseNames = baseIndices.map { formatSectorName(it) }.joinToString(", ")
        val coverNames = coverIndices.map { formatSectorName(it) }.joinToString(", ")

        // Build visual elements
        val patternCandidates = patternCells.map { cell ->
            ColouredCandidateDto(cell / 9, cell % 9, digit, "target")
        }
        val finCandidates = finCellsList.map { cell ->
            ColouredCandidateDto(cell / 9, cell % 9, digit, "warning")
        }
        val finColouredCells = finCellsList.map { ColouredCellDto(it, "warning") }

        val finCellNames = finCellsList.map { formatCellName(it) }.joinToString(", ")

        val finnedKey = if (isSashimi) "sashimi_fish" else "finned_fish"

        // s1: name the fin if we could extract it; otherwise use a variant that drops
        // the "fin is at:" clause, so an empty fin list never renders a dangling
        // "The fin is at:" with nothing after it.
        val s1Description = if (finCellNames.isEmpty()) {
            hintKey(finnedKey, 1, "descriptionNoFin",
                "fishType" to fishType,
                "baseNames" to baseNames,
                "digit" to digit.toString(),
                "coverNames" to coverNames
            )
        } else {
            hintKey(finnedKey, 1, "description",
                "fishType" to fishType,
                "baseNames" to baseNames,
                "digit" to digit.toString(),
                "coverNames" to coverNames,
                "finCells" to finCellNames
            )
        }

        steps.add(ExplanationStepDto(
            stepNumber = 1,
            title = hintKey(finnedKey, 1, "title",
                "fishType" to fishType
            ),
            description = s1Description,
            highlightCells = patternCells + finCellsList,
            colouredCells = finColouredCells,
            colouredCandidates = patternCandidates + finCandidates
        ))

        steps.add(ExplanationStepDto(
            stepNumber = 2,
            // R2 (Phase 8): s2/s3 are shared verbatim with the sashimi variant —
            // the fin logic is identical, so both families reference the finned_fish
            // keys; sashimi_fish keeps only its own (different) s1.
            title = hintKey("finned_fish", 2, "title"),
            description = hintKey("finned_fish", 2, "description",
                "digit" to digit.toString()
            ),
            highlightCells = finCellsList,
            colouredCells = finColouredCells,
            colouredCandidates = patternCandidates + finCandidates,
            regions = if (finBox >= 0) listOf(ColouredRegionDto("box", finBox, "secondary")) else emptyList()
        ))

        // Step 3: Make eliminations
        if (eliminations.isNotEmpty()) {
            val eliminationNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")

            steps.add(ExplanationStepDto(
                stepNumber = 3,
                title = hintKey("finned_fish", 3, "title"),
                description = hintKey("finned_fish", 3, "description",
                    "coverTypeText" to coverTypeText,
                    "digit" to digit.toString(),
                    "cells" to eliminationNames
                ),
                highlightCells = eliminationCells,
                colouredCells = finColouredCells + eliminationCells.map { ColouredCellDto(it, "warning") },
                colouredCandidates = patternCandidates + finCandidates + eliminationCandidates(eliminations),
                regions = if (finBox >= 0) listOf(ColouredRegionDto("box", finBox, "secondary")) else emptyList()
            ))
        }

        return steps
    }
