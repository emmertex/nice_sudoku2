package service.hint.helpers
import sudoku.match.TechniqueMatch
import dto.*
import sudoku.HelpingTools.cardinals
import service.hint.helpers.eliminationCandidates






    fun extractEliminationVisuals(match: TechniqueMatch): Triple<List<LineDto>, List<GroupDto>, String?> {
        val groups = mutableListOf<GroupDto>()
        match.eliminations.forEach { (digit, cells) ->
            val candidates = mutableListOf<CandidateLocationDto>()
            var cell = cells.nextSetBit(0)
            while (cell >= 0) {
                candidates.add(CandidateLocationDto(cell / 9, cell % 9, digit + 1))
                cell = cells.nextSetBit(cell + 1)
            }
            if (candidates.isNotEmpty()) {
                groups.add(
                    GroupDto(
                        candidates = candidates,
                        groupType = "elimination",
                        colourIndex = groups.size % 2
                    )
                )
            }
        }
        return Triple(emptyList(), groups, null)
    }


    // === Visual Data Extraction Helpers ===

    /**
     * Convert a BitSet to a List of integers
     */
    fun bitSetToList(bitSet: java.util.BitSet): List<Int> {
        val result = mutableListOf<Int>()
        var bit = bitSet.nextSetBit(0)
        while (bit >= 0) {
            result.add(bit)
            bit = bitSet.nextSetBit(bit + 1)
        }
        return result
    }

    /**
     * Get all cells in a given sector
     */
    fun getSectorCells(sectorIndex: Int): List<Int> {
        return cardinals.SecSet[sectorIndex].toList()
    }

    /**
     * Determine the type of sector: "row", "column", or "box"
     */
    fun getSectorType(sectorIndex: Int): String {
        return when {
            sectorIndex < 9 -> "row"
            sectorIndex < 18 -> "column"
            else -> "box"
        }
    }

    /**
     * Human-readable name for a sector, using its real type (row/column/box) so a
     * mixed sector set (e.g. a Franken fish's row + box) is named correctly per sector.
     */
    fun formatSectorName(sectorIndex: Int): String {
        return when (getSectorType(sectorIndex)) {
            "row" -> "Row ${sectorIndex % 9 + 1}"
            "column" -> "Column ${sectorIndex % 9 + 1}"
            "box" -> "Box ${sectorIndex % 9 + 1}"
            else -> "Line ${sectorIndex + 1}"
        }
    }

    /**
     * Describe a *set* of sectors by type. A uniform set reads "rows"/"columns"/"boxes";
     * a mixed set (e.g. a Franken fish's row + box base) reads "rows and boxes" instead
     * of being labelled by its first sector only.
     */
    fun describeSectorTypeText(sectorIndices: List<Int>): String {
        if (sectorIndices.isEmpty()) return "lines"
        val plural = mapOf("row" to "rows", "column" to "columns", "box" to "boxes")
        val words = sectorIndices
            .map { getSectorType(it) }
            .distinct()
            .mapNotNull { plural[it] ?: "lines" }
            .distinct()
        return words.joinToString(" and ")
    }

