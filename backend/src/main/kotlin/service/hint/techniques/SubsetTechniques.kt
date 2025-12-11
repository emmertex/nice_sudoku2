package service.hint.techniques

import sudoku.match.SubsetMatch
import service.hint.helpers.*
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
                        colourIndex = 0
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
                        colourIndex = 0
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
                        colourIndex = 1
                    ))
                }
            }

        } catch (e: Exception) {
            // If reflection fails, return empty data
            e.printStackTrace()
        }

        return Triple(lines, groups, null)
    }
