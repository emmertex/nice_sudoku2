package service.hint.techniques

import sudoku.match.TechniqueMatch
import sudoku.match.AICMatch
import sudoku.match.ALSMatch
import sudoku.match.SubsetMatch
import sudoku.match.FishMatch
import sudoku.solvingtechClassifier.Technique
import dto.*
import service.hint.helpers.extractEliminationVisuals
import service.hint.techniques.extractFishVisualData
import service.hint.techniques.extractKiteVisualData
import service.hint.techniques.extractSubsetVisualData

/**
 * Extract visual data (lines, groups, eureka notation) from a TechniqueMatch
 * Dispatches to specific extraction functions based on match type
 */
fun extractVisualData(match: TechniqueMatch, technique: Technique, puzzleString: String): Triple<List<LineDto>, List<GroupDto>, String?> {
    val techniqueName = technique.getName()
    return when (match) {
        is AICMatch -> extractAICVisualData(match)
        is ALSMatch -> extractALSVisualData(match)
        is SubsetMatch -> extractSubsetVisualData(match)
        is FishMatch -> {
            if (techniqueName.contains("2-String Kite", ignoreCase = true) ||
                (techniqueName.contains("Kite", ignoreCase = true) && !techniqueName.contains("String", ignoreCase = true))) {
                extractKiteVisualData(match, techniqueName, puzzleString)
            } else {
                extractFishVisualData(match, techniqueName)
            }
        }
        else -> {
            if (techniqueName.contains("Wing", ignoreCase = true) ||
                techniqueName.contains("Fish", ignoreCase = true) ||
                techniqueName.contains("Cycle", ignoreCase = true) ||
                techniqueName.contains("Colour", ignoreCase = true) ||
                techniqueName.contains("Color", ignoreCase = true) ||
                techniqueName.contains("Medusa", ignoreCase = true) ||
                techniqueName.contains("Rectangle", ignoreCase = true) ||
                techniqueName.contains("Forcing", ignoreCase = true) ||
                techniqueName.contains("Sue", ignoreCase = true) ||
                techniqueName.contains("Nishio", ignoreCase = true) ||
                techniqueName.contains("BUG", ignoreCase = true)
            ) {
                return extractEliminationVisuals(match)
            }
            Triple(emptyList(), emptyList(), null)
        }
    }
}

