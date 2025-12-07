package service.hintHelpers
import sudoku.HelpingTools.cardinals
import sudoku.DataStorage.BasicGrid
import sudoku.DataStorage.SBRCGrid
import sudoku.read.SudokuGridParser
import dto.*

    fun eliminationCandidates(
        eliminations: List<EliminationDto>,
        color: String = "elimination"
    ): List<ColoredCandidateDto> {
        val candidates = mutableListOf<ColoredCandidateDto>()
        for (elim in eliminations) {
            for (cell in elim.cells) {
                candidates.add(
                    ColoredCandidateDto(
                        row = cell / 9,
                        col = cell % 9,
                        candidate = elim.digit,
                        colorType = color
                    )
                )
            }
        }
        return candidates
    }


    fun formatCellName(cell: Int): String {
        return "R${cell / 9 + 1}C${cell % 9 + 1}"
    }

    fun summarizeEliminations(eliminations: List<EliminationDto>): String? {
        if (eliminations.isEmpty()) return null
        return eliminations.joinToString("; ") { elim ->
            val cells = elim.cells.joinToString(", ") { formatCellName(it) }
            "${elim.digit} from $cells"
        }
    }

    fun summarizeSolvedCells(solvedCells: List<SolvedCellDto>): String? {
        if (solvedCells.isEmpty()) return null
        return solvedCells.joinToString("; ") { solved ->
            "${formatCellName(solved.cell)} = ${solved.digit}"
        }
    }

    fun basicGridToDto(basicGrid: BasicGrid): GridDto {
        val cells = (0 until cardinals.Length).map { cellIndex ->
            val solved = basicGrid.getSolved(cellIndex)
            if (solved.isPresent) {
                CellDto(
                    index = cellIndex,
                    value = solved.asInt + 1, // StormDoku uses 0-8, we use 1-9
                    candidates = emptySet(),
                    isGiven = basicGrid.isGiven(cellIndex)
                )
            } else {
                val candidates = (0 until 9)
                    .filter { basicGrid.hasCandidate(cellIndex, it) }
                    .map { it + 1 }
                    .toSet()
                CellDto(
                    index = cellIndex,
                    value = null,
                    candidates = candidates,
                    isGiven = false
                )
            }
        }
        
        return GridDto(
            cells = cells,
            isComplete = cells.all { it.value != null },
            isValid = true // Could add validation
        )
    }
    
    fun dtoToBasicGrid(gridDto: GridDto): BasicGrid {
        // BasicGrid starts with all candidates for all cells
        val basicGrid = BasicGrid()

        // Create a map of cell index to cell data for quick lookup
        val cellMap = gridDto.cells.associateBy { it.index }

        // Set solved cells
        for (cellIndex in 0 until cardinals.Length) {
            val cellDto = cellMap[cellIndex]
            if (cellDto?.value != null) {
                basicGrid.setSolved(cellIndex, cellDto.value - 1, cellDto.isGiven)
            }
        }

        // Clean up candidates based on solved cells (standard Sudoku rules)
        basicGrid.cleanUpCandidates()
        
        // IMPORTANT: Also apply any additional candidate eliminations from the GridDto
        // This preserves technique eliminations (e.g., X-Wing eliminations)
        for (cellIndex in 0 until cardinals.Length) {
            val cellDto = cellMap[cellIndex]
            if (cellDto != null && cellDto.value == null && cellDto.candidates.isNotEmpty()) {
                // Cell is unsolved but has explicit candidates - apply them
                for (digit in 1..9) {
                    val digitIndex = digit - 1
                    // If the candidate is NOT in the GridDto's candidate set, remove it
                    if (digit !in cellDto.candidates && basicGrid.hasCandidate(cellIndex, digitIndex)) {
                        basicGrid.clearCandidate(cellIndex, digitIndex)
                    }
                }
            }
        }
        
        return basicGrid
    }
