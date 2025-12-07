package service.hint

import sudoku.match.FishMatch
import service.hintHelpers.*
import sudoku.match.TechniqueMatch
import sudoku.HelpingTools.cardinals
import sudoku.DataStorage.BasicGrid
import sudoku.DataStorage.SBRCGrid
import sudoku.read.SudokuGridParser
import dto.*

    /**
     * Extract visual data from FishMatch (Pointing/Claiming Candidates)
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
                            colorIndex = 0
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
                            colorIndex = 1
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
                            colorIndex = 0
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
                            colorIndex = 1
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
            ColoredRegionDto(baseType ?: "row", idx % 9, "primary")
        }
        val coverRegions = coverIndices.map { idx ->
            ColoredRegionDto(coverType ?: "row", idx % 9, "primary")
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
        
        // Colored candidates: pattern cells get "target", elimination cells get "elimination"
        val patternCandidates = patternCells.map { cell ->
            ColoredCandidateDto(cell / 9, cell % 9, digit, "target")
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
        
        // Colored cells for the intersection points (yellow border)
        val intersectionColoredCells = intersectionCells.map { ColoredCellDto(it, "warning") }
        
        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Identify the $techniqueName pattern",
                description = patternDescription,
                highlightCells = patternCells,
                regions = baseRegions,
                coloredCells = intersectionColoredCells,
                coloredCandidates = patternCandidates
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
                    coloredCandidates = patternCandidates + eliminationCandidates
                )
            )
            
            // Step 3: Show specific eliminations with pattern cells highlighted in green
            val eliminationNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")
            val eliminationColoredCells = eliminationCells.map { ColoredCellDto(it, "warning") }
            
            steps.add(
                ExplanationStepDto(
                    stepNumber = 3,
                    title = "Remove candidate $digit",
                    description = "Remove $digit from: $eliminationNames. These cells see the fish pattern and cannot contain $digit.",
                    highlightCells = eliminationCells,
                    regions = allRegions,
                    coloredCells = intersectionColoredCells + eliminationColoredCells,
                    coloredCandidates = patternCandidates + eliminationCandidates
                )
            )
        }

        return steps
    }

    /**
     * Extract visual data for 2-String Kite (show strong link between kite cells)
     */
    fun extractKiteVisualData(match: TechniqueMatch, techniqueName: String, puzzleString: String): Triple<List<LineDto>, List<GroupDto>, String?> {
        val lines = mutableListOf<LineDto>()
        val groups = mutableListOf<GroupDto>()

        var kiteCells: List<Int> = emptyList()
        var rowIndex: Int? = null
        var colIndex: Int? = null
        var rowFin: Int? = null
        var colFin: Int? = null

        try {
            val matchClass = match.javaClass
            val digitField = matchClass.getDeclaredField("digit")
            val baseSecsField = matchClass.getDeclaredField("baseSecs")
            val coverSecsField = matchClass.getDeclaredField("coverSecs")
            val finsField = matchClass.getDeclaredField("fins")
            val endofinsField = matchClass.getDeclaredField("endofins")

            digitField.isAccessible = true
            baseSecsField.isAccessible = true
            coverSecsField.isAccessible = true
            finsField.isAccessible = true
            endofinsField.isAccessible = true

            val digit = (digitField.get(match) as Int) + 1 // Convert to 1-9
            val baseSecs = baseSecsField.get(match) as java.util.BitSet
            val coverSecs = coverSecsField.get(match) as java.util.BitSet
            val fins = finsField.get(match) as java.util.BitSet
            val endofins = endofinsField.get(match) as java.util.BitSet
            
            // Get elimination cells for this digit
            val eliminationCells = mutableListOf<Int>()
            val eliminations = match.eliminations[digit - 1] // eliminations use 0-8
            if (eliminations != null) {
                var elimCell = eliminations.nextSetBit(0)
                while (elimCell >= 0) {
                    eliminationCells.add(elimCell)
                    elimCell = eliminations.nextSetBit(elimCell + 1)
                }
            }

            // Collect base and cover sectors
            val baseIndices = mutableListOf<Int>()
            var idx = baseSecs.nextSetBit(0)
            while (idx >= 0) {
                baseIndices.add(idx)
                idx = baseSecs.nextSetBit(idx + 1)
            }

            val coverIndices = mutableListOf<Int>()
            idx = coverSecs.nextSetBit(0)
            while (idx >= 0) {
                coverIndices.add(idx)
                idx = coverSecs.nextSetBit(idx + 1)
            }

            // Derive row/col from base sectors
            rowIndex = baseIndices.firstOrNull { it < 9 }
            colIndex = baseIndices.firstOrNull { it in 9..17 }?.let { it - 9 }

            // Extract fin cells (these are CELL indices 0-80!)
            // The commented AIC in TwoStringKite.java shows:
            // Node 0: rowOuties (cell in row, outside box) = rowFin
            // Node 1: difference(inRow, rowOuties) (cell in row, inside box)
            // Node 2: difference(inCol, colOuties) (cell in column, inside box)
            // Node 3: colOuties (cell in column, outside box) = colFin
            
            val finCells = mutableListOf<Int>()
            var finIdx = fins.nextSetBit(0)
            while (finIdx >= 0) {
                finCells.add(finIdx)
                finIdx = fins.nextSetBit(finIdx + 1)
            }
            
            val endofinCells = mutableListOf<Int>()
            var endoIdx = endofins.nextSetBit(0)
            while (endoIdx >= 0) {
                endofinCells.add(endoIdx)
                endoIdx = endofins.nextSetBit(endoIdx + 1)
            }
            
            println("DEBUG Kite extract: digit=$digit")
            println("  fins=$finCells -> ${finCells.map { formatCellName(it) }}")
            println("  endofins=$endofinCells -> ${endofinCells.map { formatCellName(it) }}")
            println("  eliminations=$eliminationCells -> ${eliminationCells.map { formatCellName(it) }}")
            
            // Identify which fin is in the row and which is in the column
            // rowFin: in row rowIndex, but NOT in column colIndex (outside box in that row)
            // colFin: in column colIndex, but NOT in row rowIndex (outside box in that column)
            if (finCells.size >= 2 && rowIndex != null && colIndex != null) {
                for (cellIdx in finCells) {
                    val cellRow = cellIdx / 9
                    val cellCol = cellIdx % 9
                    if (cellRow == rowIndex && cellCol != colIndex) {
                        rowFin = cellIdx  // This cell is in the row (outside box)
                    } else if (cellCol == colIndex && cellRow != rowIndex) {
                        colFin = cellIdx  // This cell is in the column (outside box)
                    }
                }
            }

            // Cells in base (row/col) and cover (box)
            val baseCells = baseIndices.flatMap { getSectorCells(it) }
            val coverCells = coverIndices.flatMap { getSectorCells(it) }
            
            // Fish cells are the intersection of base and cover - these are the cells with the candidate in the pattern
            val fishCells = baseCells.filter { it in coverCells }
            
            // Get all cells in the row and column  
            val rowCells = if (rowIndex != null) getSectorCells(rowIndex) else emptyList()
            val colCells = if (colIndex != null) getSectorCells(colIndex + 9) else emptyList()
            
            // The row/col intersection cell (which must NOT have the digit)
            val rowColIntersection = if (rowIndex != null && colIndex != null) {
                rowIndex * 9 + colIndex
            } else null
            
            // The difference operations from the AIC:
            // inRow \ rowOuties = cells in row that are IN THE BOX and have the candidate (verified from grid)
            // inCol \ colOuties = cells in column that are IN THE BOX and have the candidate (verified from grid)
            
            // Option 2: Parse the puzzle and find cells that actually have the candidate
            // Simpler approach: iterate through the box to find all cells with the digit
            val cellsWithCandidateInBox = mutableSetOf<Int>()
            try {
                val basicGrid = SudokuGridParser.readPuzzleString(puzzleString)
                val sbrcGrid = SBRCGrid(basicGrid)
                
                // Check each cell in the cover (box) to see if it has the candidate
                for (cellIdx in coverCells) {
                    val cellPencilMarks = sbrcGrid.pm[cellIdx]
                    if (cellPencilMarks != null && cellPencilMarks.get(digit - 1)) {  // digit is 1-9, pencilMarks are 0-8
                        cellsWithCandidateInBox.add(cellIdx)
                    }
                }
            } catch (e: Exception) {
                // If we can't parse, fall back to fishCells
                cellsWithCandidateInBox.addAll(fishCells)
            }
            
            println("DEBUG: Box cells with candidate $digit: ${cellsWithCandidateInBox.map { formatCellName(it) }}")
            
            val rowCellsInBox = if (rowFin != null && rowIndex != null) {
                // Cells in the row, inside the box, WITH the candidate (verified from grid), but NOT the rowFin or intersection
                rowCells.filter { cell ->
                    cell in cellsWithCandidateInBox && 
                    cell != rowFin && 
                    cell != rowColIntersection
                }
            } else emptyList()
            
            val colCellsInBox = if (colFin != null && colIndex != null) {
                // Cells in the column, inside the box, WITH the candidate (verified from grid), but NOT the colFin or intersection
                colCells.filter { cell ->
                    cell in cellsWithCandidateInBox && 
                    cell != colFin && 
                    cell != rowColIntersection
                }
            } else emptyList()

            // The kite cells ARE the fin cells (they're already cell indices)
            if (rowFin != null && colFin != null) {
                kiteCells = listOf(rowFin, colFin)
            } else if (finCells.size >= 2) {
                // Fallback: use the fin cells directly
                kiteCells = finCells.take(2)
            } else {
                // Double fallback: find cells in base sectors that are NOT in cover sectors
                kiteCells = baseCells.filter { it !in coverCells }.distinct().take(2)
            }

            if (kiteCells.size >= 2 && rowIndex != null && colIndex != null) {
                val c1 = kiteCells[0]
                val c2 = kiteCells[1]
                val r1 = c1 / 9
                val c1c = c1 % 9
                val r2 = c2 / 9
                val c2c = c2 % 9

                // Use the computed cells from the difference operations
                // The two cells in the box MUST be in the same box (for the weak link)
                // rowCellsInBox and colCellsInBox are already filtered to only include cells with the candidate
                val rowCellInBox = rowCellsInBox.firstOrNull()
                
                println("DEBUG: rowCellsInBox candidates=${rowCellsInBox.map { formatCellName(it) }}, selected=${if (rowCellInBox != null) formatCellName(rowCellInBox) else "null"}")
                
                val colCellInBox = if (colCellsInBox.size > 1 && rowCellInBox != null) {
                    // Pick the colCellInBox that's in the same box as rowCellInBox
                    val rowCellBox = (rowCellInBox / 27) * 3 + ((rowCellInBox % 9) / 3)
                    colCellsInBox.firstOrNull { cell ->
                        val cellBox = (cell / 27) * 3 + ((cell % 9) / 3)
                        cellBox == rowCellBox
                    } ?: colCellsInBox.firstOrNull()
                } else {
                    colCellsInBox.firstOrNull()
                }
                
                println("DEBUG: colCellsInBox candidates=${colCellsInBox.map { formatCellName(it) }}, selected=${if (colCellInBox != null) formatCellName(colCellInBox) else "null"}")

                // Strong link in the row (between kite endpoint and cell in box)
                if (rowCellInBox != null) {
                    val rr = rowCellInBox / 9
                    val rc = rowCellInBox % 9
                    lines.add(
                        LineDto(
                            from = CandidateLocationDto(r1, c1c, digit),
                            to = CandidateLocationDto(rr, rc, digit),
                            isStrongLink = true,
                            lineType = "kite-strong-row",
                            description = "Strong link: if $digit is not in R${r1+1}C${c1c+1}, it must be in R${rr+1}C${rc+1}"
                        )
                    )
                }

                // Strong link in the column (between kite endpoint and cell in box)
                if (colCellInBox != null) {
                    val cr = colCellInBox / 9
                    val cc = colCellInBox % 9
                    lines.add(
                        LineDto(
                            from = CandidateLocationDto(r2, c2c, digit),
                            to = CandidateLocationDto(cr, cc, digit),
                            isStrongLink = true,
                            lineType = "kite-strong-col",
                            description = "Strong link: if $digit is not in R${r2+1}C${c2c+1}, it must be in R${cr+1}C${cc+1}"
                        )
                    )
                }

                // Weak link (dashed) connecting the two cells in the box
                if (rowCellInBox != null && colCellInBox != null) {
                    val rr = rowCellInBox / 9
                    val rc = rowCellInBox % 9
                    val cr = colCellInBox / 9
                    val cc = colCellInBox % 9
                    lines.add(
                        LineDto(
                            from = CandidateLocationDto(rr, rc, digit),
                            to = CandidateLocationDto(cr, cc, digit),
                            isStrongLink = false,
                            lineType = "kite-weak",
                            description = "Weak link: $digit cannot be in both R${rr+1}C${rc+1} and R${cr+1}C${cc+1}"
                        )
                    )
                }

                // Group each kite endpoint
                groups.add(
                    GroupDto(
                        candidates = listOf(CandidateLocationDto(r1, c1c, digit)),
                        groupType = "kite-end",
                        colorIndex = 0
                    )
                )
                groups.add(
                    GroupDto(
                        candidates = listOf(CandidateLocationDto(r2, c2c, digit)),
                        groupType = "kite-end",
                        colorIndex = 1
                    )
                )
            }
        } catch (e: Exception) {
            // Fallback: no lines/groups
        }

        return Triple(lines, groups, null)
    }

    /**
     * Generate step-by-step explanation for 2-String Kite
     */
    fun generateKiteSteps(
        techniqueName: String,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>,
        puzzleString: String
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        val eliminationCells = eliminations.flatMap { it.cells }.distinct()

        // Extract kite pattern data
        var digit = eliminations.firstOrNull()?.digit ?: 0
        val baseIndices = mutableListOf<Int>()
        val coverIndices = mutableListOf<Int>()
        var rowIndex: Int? = null
        var colIndex: Int? = null
        var fins = java.util.BitSet()
        var endofins = java.util.BitSet()

        try {
            val matchClass = match.javaClass
            val digitField = matchClass.getDeclaredField("digit")
            val baseSecsField = matchClass.getDeclaredField("baseSecs")
            val coverSecsField = matchClass.getDeclaredField("coverSecs")
            val finsField = matchClass.getDeclaredField("fins")
            val endofinsField = matchClass.getDeclaredField("endofins")

            digitField.isAccessible = true
            baseSecsField.isAccessible = true
            coverSecsField.isAccessible = true
            finsField.isAccessible = true
            endofinsField.isAccessible = true

            digit = (digitField.get(match) as Int) + 1 // Convert to 1-9
            val baseSecs = baseSecsField.get(match) as java.util.BitSet
            val coverSecs = coverSecsField.get(match) as java.util.BitSet
            fins = finsField.get(match) as java.util.BitSet
            endofins = endofinsField.get(match) as java.util.BitSet

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

            // Derive row/col from base sectors
            rowIndex = baseIndices.firstOrNull { it < 9 }
            colIndex = baseIndices.firstOrNull { it in 9..17 }?.let { it - 9 }
        } catch (e: Exception) {
            // Fallback if reflection fails
        }

        // Determine base and cover types
        val baseType = if (baseIndices.isNotEmpty()) getSectorType(baseIndices.first()) else null
        val coverType = if (coverIndices.isNotEmpty()) getSectorType(coverIndices.first()) else null

        // Get cells in base and cover sectors
        val baseCells = mutableListOf<Int>()
        for (idx in baseIndices) {
            baseCells.addAll(getSectorCells(idx))
        }
        val coverCells = mutableListOf<Int>()
        for (idx in coverIndices) {
            coverCells.addAll(getSectorCells(idx))
        }

        // Use the same logic as extractKiteVisualData to identify the pattern cells
        // Extract fin cells
        val finCells = mutableListOf<Int>()
        var finIdx = fins.nextSetBit(0)
        while (finIdx >= 0) {
            finCells.add(finIdx)
            finIdx = fins.nextSetBit(finIdx + 1)
        }
        
        // Extract endofin cells
        val endofinCells = mutableListOf<Int>()
        var endoIdx = endofins.nextSetBit(0)
        while (endoIdx >= 0) {
            endofinCells.add(endoIdx)
            endoIdx = endofins.nextSetBit(endoIdx + 1)
        }
        
        // Identify which fin is in the row and which is in the column
        // rowFin: in row rowIndex, but NOT in column colIndex (outside box in that row)
        // colFin: in column colIndex, but NOT in row rowIndex (outside box in that column)
        var rowFin: Int? = null
        var colFin: Int? = null
        if (finCells.size >= 2 && rowIndex != null && colIndex != null) {
            for (cellIdx in finCells) {
                val cellRow = cellIdx / 9
                val cellCol = cellIdx % 9
                if (cellRow == rowIndex && cellCol != colIndex) {
                    rowFin = cellIdx  // This cell is in the row (outside box)
                } else if (cellCol == colIndex && cellRow != rowIndex) {
                    colFin = cellIdx  // This cell is in the column (outside box)
                }
            }
        }

        // Calculate the actual kite endpoint cells
        val kiteCells = if (rowFin != null && colFin != null) {
            listOf(rowFin, colFin)
        } else if (finCells.size >= 2) {
            finCells.take(2)
        } else {
            // Fallback: find cells in base sectors that are NOT in cover sectors
            baseCells.filter { it !in coverCells }.distinct().take(2)
        }

        // Only show the cover region (box), not all the base regions
        val coverRegions = coverIndices.map { idx ->
            ColoredRegionDto(coverType ?: "box", idx % 9, "secondary")
        }
        val allRegions = coverRegions  // Only highlight the box

            // Fish cells are the intersection of base and cover - cells by position
            val fishCells = baseCells.filter { it in coverCells }
            
            // Option 2: Parse the puzzle and find cells that actually have the candidate
            // Simpler approach: iterate through the box to find all cells with the digit
            val cellsWithCandidateInBox = mutableSetOf<Int>()
            try {
                val basicGrid = SudokuGridParser.readPuzzleString(puzzleString)
                val sbrcGrid = SBRCGrid(basicGrid)
                
                // Check each cell in the cover (box) to see if it has the candidate
                for (cellIdx in coverCells) {
                    val cellPencilMarks = sbrcGrid.pm[cellIdx]
                    if (cellPencilMarks != null && cellPencilMarks.get(digit - 1)) {  // digit is 1-9, pencilMarks are 0-8
                        cellsWithCandidateInBox.add(cellIdx)
                    }
                }
            } catch (e: Exception) {
                // If we can't parse, fall back to fishCells
                cellsWithCandidateInBox.addAll(fishCells)
            }
            
            println("DEBUG: Box cells with candidate $digit: ${cellsWithCandidateInBox.map { formatCellName(it) }}")

        // Get all cells in the row and column
        val rowCells = if (rowIndex != null) getSectorCells(rowIndex) else emptyList()
        val colCells = if (colIndex != null) getSectorCells(colIndex + 9) else emptyList()
        
        // The row/col intersection cell (which must NOT have the digit)
        val rowColIntersection = if (rowIndex != null && colIndex != null) {
            rowIndex * 9 + colIndex
        } else null
        
        // The difference operations from the AIC chain:
        // inRow \ rowOuties = cells in row that are IN THE BOX and have the candidate (verified from grid)
        // inCol \ colOuties = cells in column that are IN THE BOX and have the candidate (verified from grid)
        val rowCellsInBox = if (rowFin != null && rowIndex != null) {
            // Cells in the row, inside the box, WITH the candidate (verified from grid), but NOT the rowFin or intersection
            rowCells.filter { cell ->
                cell in cellsWithCandidateInBox && 
                cell != rowFin && 
                cell != rowColIntersection
            }
        } else emptyList()
        
        val colCellsInBox = if (colFin != null && colIndex != null) {
            // Cells in the column, inside the box, WITH the candidate (verified from grid), but NOT the colFin or intersection
            colCells.filter { cell ->
                cell in cellsWithCandidateInBox && 
                cell != colFin && 
                cell != rowColIntersection
            }
        } else emptyList()
        
        // The two cells in the box MUST be in the same box (for the weak link)
        // rowCellsInBox and colCellsInBox are already filtered to only include cells with the candidate
        val rowCellInBox = rowCellsInBox.firstOrNull()
        
        val colCellInBox = if (colCellsInBox.size > 1 && rowCellInBox != null) {
            // Pick the colCellInBox that's in the same box as rowCellInBox
            val rowCellBox = (rowCellInBox / 27) * 3 + ((rowCellInBox % 9) / 3)
            colCellsInBox.firstOrNull { cell ->
                val cellBox = (cell / 27) * 3 + ((cell % 9) / 3)
                cellBox == rowCellBox
            } ?: colCellsInBox.firstOrNull()
        } else {
            colCellsInBox.firstOrNull()
        }
        
        val boxCells = listOfNotNull(rowCellInBox, colCellInBox)

        // All cells in the kite pattern (4 cells total: 2 endpoints + 2 in box)
        val allPatternCells = kiteCells + boxCells

        // Colored cells for kite endpoints (yellow borders)
        val kiteColoredCells = kiteCells.map { ColoredCellDto(it, "warning") }

        // Colored candidates: highlight all 4 cells in the kite pattern
        val patternCandidates = allPatternCells.map { cell ->
            val colorType = when (cell) {
                in kiteCells -> "target"  // Kite endpoints in green
                else -> "highlight"  // Box cells in yellow
            }
            ColoredCandidateDto(cell / 9, cell % 9, digit, colorType)
        }

        val eliminationCandidates = eliminationCandidates(eliminations)

        // Build sector names
        val baseNames = baseIndices.map { idx ->
            when (baseType) {
                "row" -> "Row ${idx % 9 + 1}"
                "column" -> "Column ${idx % 9 + 1}"
                else -> "Base ${idx + 1}"
            }
        }

        val coverNames = coverIndices.map { idx ->
            when (coverType) {
                "box" -> "Box ${idx % 9 + 1}"
                "row" -> "Row ${idx % 9 + 1}"
                "column" -> "Column ${idx % 9 + 1}"
                else -> "Cover ${idx + 1}"
            }
        }

        val baseNamesText = if (baseNames.isNotEmpty()) baseNames.joinToString(" and ") else "the base line"
        val coverNamesText = if (coverNames.isNotEmpty()) coverNames.joinToString(" and ") else "the cover box"

        val kiteNames = kiteCells.map { formatCellName(it) }
        val kiteNamesText = kiteNames.joinToString(" and ")
        val firstCell = kiteNames.firstOrNull() ?: "the first cell"
        val secondCell = kiteNames.getOrNull(1) ?: "the second cell"

        // Step 1: Identify the pattern
        steps.add(
            ExplanationStepDto(
                stepNumber = 1,
                title = "Find the kite pattern",
                description = "Look at the two green $digit candidates at $kiteNamesText. " +
                    "These are the endpoints of the kite. They're connected through $coverNamesText, " +
                    "which contains two more $digit candidates (shown in yellow). " +
                    "Think of it like this: one of the green cells MUST have $digit, but they can't BOTH have it.",
                highlightCells = kiteCells,
                regions = allRegions,  // Only show box
                coloredCells = kiteColoredCells,
                coloredCandidates = patternCandidates
            )
        )

        // Step 2: Explain the logic with lines showing the kite pattern
        if (eliminations.isNotEmpty()) {
            val eliminationNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")
            
            // Build lines for Step 2 to show the kite pattern
            val step2Lines = mutableListOf<LineDto>()
            val step2Groups = mutableListOf<GroupDto>()
            
            // Need to identify which fin is which for drawing correct lines
            val rowFinCell = if (rowFin != null && rowFin in kiteCells) rowFin else null
            val colFinCell = if (colFin != null && colFin in kiteCells) colFin else null
            
            if (rowFinCell != null && colFinCell != null && rowIndex != null && colIndex != null) {
                val rowFinRow = rowFinCell / 9
                val rowFinCol = rowFinCell % 9
                val colFinRow = colFinCell / 9
                val colFinCol = colFinCell % 9

                // Strong link in the row (between rowFin and cell in box)
                if (rowCellInBox != null) {
                    val rr = rowCellInBox / 9
                    val rc = rowCellInBox % 9
                    step2Lines.add(
                        LineDto(
                            from = CandidateLocationDto(rowFinRow, rowFinCol, digit),
                            to = CandidateLocationDto(rr, rc, digit),
                            isStrongLink = true,
                            lineType = "kite-strong-row",
                            description = "Strong link: if $digit is not in R${rowFinRow+1}C${rowFinCol+1}, it must be in R${rr+1}C${rc+1}"
                        )
                    )
                }

                                // Weak link connecting the two cells in the box
                if (rowCellInBox != null && colCellInBox != null) {
                    val rr = rowCellInBox / 9
                    val rc = rowCellInBox % 9
                    val cr = colCellInBox / 9
                    val cc = colCellInBox % 9
                    step2Lines.add(
                        LineDto(
                            from = CandidateLocationDto(rr, rc, digit),
                            to = CandidateLocationDto(cr, cc, digit),
                            isStrongLink = false,
                            lineType = "kite-weak",
                            description = "Weak link: $digit cannot be in both R${rr+1}C${rc+1} and R${cr+1}C${cc+1}"
                        )
                    )
                }

                // Strong link in the column (between cell in box and colFin)
                if (colCellInBox != null) {
                    val cr = colCellInBox / 9
                    val cc = colCellInBox % 9
                    step2Lines.add(
                        LineDto(
                            from = CandidateLocationDto(cr, cc, digit),
                            to = CandidateLocationDto(colFinRow, colFinCol, digit),
                            isStrongLink = true,
                            lineType = "kite-strong-col",
                            description = "Strong link: if $digit is not in R${cr+1}C${cc+1}, it must be in R${colFinRow+1}C${colFinCol+1}"
                        )
                    )
                }



                // Group each kite endpoint
                step2Groups.add(
                    GroupDto(
                        candidates = listOf(CandidateLocationDto(rowFinRow, rowFinCol, digit)),
                        groupType = "kite-end",
                        colorIndex = 0
                    )
                )
                step2Groups.add(
                    GroupDto(
                        candidates = listOf(CandidateLocationDto(colFinRow, colFinCol, digit)),
                        groupType = "kite-end",
                        colorIndex = 1
                    )
                )
            }
            
            // Build interactive chain description matching the visual line order
            val chainDescription = buildString {
                if (rowFinCell != null && colFinCell != null && rowCellInBox != null && colCellInBox != null) {
                    // The chain follows the kite pattern
                    append("($digit)${formatCellName(rowFinCell)}")
                    append(" --[strong]--> ($digit)${formatCellName(rowCellInBox)}")
                    append(" --[weak]--> ($digit)${formatCellName(colCellInBox)}")
                    append(" --[strong]--> ($digit)${formatCellName(colFinCell)}")
                }
            }
            
            steps.add(
                ExplanationStepDto(
                    stepNumber = 2,
                    title = "Follow the chain",
                    description = "The kite forms a chain: $chainDescription. " +
                    "The solid lines indicate strong links, " +
                    "and the dashed line indacates a weak link. " +
                    "No matter which green cell has $digit, " +
                    "the weak link ensures that at least one of the yellow cells in the box will have it. " +
                    "Any cell that can see BOTH green cells at $kiteNamesText cannot have $digit.",
                    highlightCells = kiteCells + eliminationCells,
                    regions = allRegions,
                    coloredCells = kiteColoredCells,
                    coloredCandidates = patternCandidates,
                    lines = step2Lines,
                    groups = step2Groups
                )
            )

            // Step 3: Show eliminations - highlight the row and column that form the kite intersection
            val eliminationRegions = eliminationCells.flatMap { cell ->
                val row = cell / 9
                val col = cell % 9
                listOf(
                    ColoredRegionDto("row", row, "secondary"),
                    ColoredRegionDto("column", col, "secondary")
                )
            }.distinctBy { "${it.type}-${it.index}" }
            
            steps.add(
                ExplanationStepDto(
                    stepNumber = 3,
                    title = "Make the elimination",
                    description = "Since $eliminationNames can see both green cells, " +
                        "we know $digit cannot go there. Eliminate $digit from: $eliminationNames.",
                    highlightCells = eliminationCells,
                    regions = eliminationRegions,
                    coloredCells = kiteColoredCells + eliminationCells.map { ColoredCellDto(it, "warning") },
                    coloredCandidates = patternCandidates + eliminationCandidates
                )
            )
        }

        return steps
    }

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

    fun buildWingRegions(pivotCells: List<Int>, pincerCells: List<Int>): List<ColoredRegionDto> {
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

        return regions.map { (type, index) -> ColoredRegionDto(type, index, "primary") }
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

        val coloredCells = mutableListOf<ColoredCellDto>()
        pivotCells.forEach { coloredCells.add(ColoredCellDto(it, "warning")) }
        pincerCells.forEach { coloredCells.add(ColoredCellDto(it, "info")) }
        supportingCells.forEach { coloredCells.add(ColoredCellDto(it, "secondary")) }

        val linkRegions = buildWingRegions(pivotCells, pincerCells)

        val targetCandidates = mutableListOf<ColoredCandidateDto>()
        if (targetDigit != null) {
            val candidateCells = when (wingType) {
                "XY-Wing", "W-Wing" -> if (pincerCells.isNotEmpty()) pincerCells else wingCells
                else -> if (wingCells.isNotEmpty()) wingCells else eliminationCells
            }
            candidateCells.forEach { cell ->
                targetCandidates.add(ColoredCandidateDto(cell / 9, cell % 9, targetDigit, "target"))
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
                coloredCells = coloredCells,
                coloredCandidates = targetCandidates
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
                    coloredCells = coloredCells,
                    coloredCandidates = targetCandidates + eliminationCandidates(eliminations)
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
                    coloredCandidates = targetCandidates + eliminationCandidates(eliminations)
                )
            )
        }

        return steps
    }
