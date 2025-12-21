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
                        colourIndex = 0
                    )
                )
                groups.add(
                    GroupDto(
                        candidates = listOf(CandidateLocationDto(r2, c2c, digit)),
                        groupType = "kite-end",
                        colourIndex = 1
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
            ColouredRegionDto(coverType ?: "box", idx % 9, "secondary")
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

        // Coloured cells for kite endpoints (yellow borders)
        val kiteColouredCells = kiteCells.map { ColouredCellDto(it, "warning") }

        // Coloured candidates: highlight all 4 cells in the kite pattern
        val patternCandidates = allPatternCells.map { cell ->
            val colourType = when (cell) {
                in kiteCells -> "target"  // Kite endpoints in green
                else -> "highlight"  // Box cells in yellow
            }
            ColouredCandidateDto(cell / 9, cell % 9, digit, colourType)
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
                title = hintKey("kite", 1, "title"),
                description = hintKey("kite", 1, "description",
                    "digit" to digit.toString(),
                    "kiteNames" to kiteNamesText,
                    "coverNames" to coverNamesText
                ),
                highlightCells = kiteCells,
                regions = allRegions,  // Only show box
                colouredCells = kiteColouredCells,
                colouredCandidates = patternCandidates
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
                        colourIndex = 0
                    )
                )
                step2Groups.add(
                    GroupDto(
                        candidates = listOf(CandidateLocationDto(colFinRow, colFinCol, digit)),
                        groupType = "kite-end",
                        colourIndex = 1
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
                    title = hintKey("kite", 2, "title"),
                    description = hintKey("kite", 2, "description",
                        "chain" to chainDescription,
                        "digit" to digit.toString(),
                        "kiteNames" to kiteNamesText
                    ),
                    highlightCells = kiteCells + eliminationCells,
                    regions = allRegions,
                    colouredCells = kiteColouredCells,
                    colouredCandidates = patternCandidates,
                    lines = step2Lines,
                    groups = step2Groups
                )
            )

            // Step 3: Show eliminations - highlight the row and column that form the kite intersection
            val eliminationRegions = eliminationCells.flatMap { cell ->
                val row = cell / 9
                val col = cell % 9
                listOf(
                    ColouredRegionDto("row", row, "secondary"),
                    ColouredRegionDto("column", col, "secondary")
                )
            }.distinctBy { "${it.type}-${it.index}" }

            steps.add(
                ExplanationStepDto(
                    stepNumber = 3,
                    title = hintKey("kite", 3, "title"),
                    description = hintKey("kite", 3, "description",
                        "cells" to eliminationNames,
                        "digit" to digit.toString()
                    ),
                    highlightCells = eliminationCells,
                    regions = eliminationRegions,
                    colouredCells = kiteColouredCells + eliminationCells.map { ColouredCellDto(it, "warning") },
                    colouredCandidates = patternCandidates + eliminationCandidates
                )
            )
        }

        return steps
    }
