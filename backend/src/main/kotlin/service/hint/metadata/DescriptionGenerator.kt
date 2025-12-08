package service.hint.metadata
import service.hint.helpers.*
import sudoku.match.TechniqueMatch
import sudoku.solvingtechClassifier.Technique
import sudoku.HelpingTools.cardinals
import sudoku.DataStorage.BasicGrid
import sudoku.DataStorage.SBRCGrid
import sudoku.read.SudokuGridParser
import dto.*

    data class CachedMatch(
        val match: TechniqueMatch,
        val technique: Technique,
        val puzzleString: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun buildFallbackDescription(
        techniqueName: String,
        eliminations: List<EliminationDto>,
        solvedCells: List<SolvedCellDto>,
        match: TechniqueMatch
    ): String {
        val solvedSummary = summarizeSolvedCells(solvedCells)
        val eliminationSummary = summarizeEliminations(eliminations)

        return when {
            solvedSummary != null && eliminationSummary != null ->
                "$techniqueName places $solvedSummary and eliminates $eliminationSummary."
            solvedSummary != null -> "$techniqueName places $solvedSummary."
            eliminationSummary != null -> "$techniqueName eliminates $eliminationSummary."
            else -> match.toString()
        }
    }

    fun getTechniqueDescription(
        technique: Technique,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>,
        solvedCells: List<SolvedCellDto>
    ): String {
        val candidates = listOf(technique.name)
        for (name in candidates) {
            val desc = describeTechnique(name)
            if (desc != null) return desc
        }

        val fallback = buildFallbackDescription(technique.name, eliminations, solvedCells, match)
        println("WARN: Missing description for technique ${technique.name}")
        return fallback
    }
    