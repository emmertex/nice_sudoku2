package service.hint.converters

import sudoku.match.TechniqueMatch
import sudoku.solvingtechClassifier.Technique
import dto.*
import service.hint.techniques.extractVisualData
import service.hint.explanations.generateExplanationSteps
import service.hint.metadata.getTechniqueDescription

/**
 * Convert a TechniqueMatch to a TechniqueMatchDto
 */
fun techniqueMatchToDto(id: String, technique: Technique, match: TechniqueMatch, puzzleString: String): TechniqueMatchDto {
    val eliminations = match.eliminations.map { (digit, cells) ->
        val cellList = mutableListOf<Int>()
        var cell = cells.nextSetBit(0)
        while (cell >= 0) {
            cellList.add(cell)
            cell = cells.nextSetBit(cell + 1)
        }
        EliminationDto(digit = digit + 1, cells = cellList) // Convert to 1-9
    }
    
    val solvedCells = match.solvedCells.map { (cell, digit) ->
        SolvedCellDto(cell = cell, digit = digit + 1) // Convert to 1-9
    }
    
    // Collect all cells involved for highlighting
    val highlightCells = mutableSetOf<Int>()
    eliminations.forEach { highlightCells.addAll(it.cells) }
    solvedCells.forEach { highlightCells.add(it.cell) }
    
    // Extract visual data based on match type
    val (lines, groups, eurekaNotation) = extractVisualData(match, technique, puzzleString)
    
    // Generate explanation steps
    val explanationSteps = generateExplanationSteps(technique, match, eliminations, solvedCells, puzzleString)
    
    val description = getTechniqueDescription(technique, match, eliminations, solvedCells)

    return TechniqueMatchDto(
        id = id,
        techniqueName = technique.getName(),
        description = description,
        eliminations = eliminations,
        solvedCells = solvedCells,
        highlightCells = highlightCells.toList(),
        lines = lines,
        groups = groups,
        explanationSteps = explanationSteps,
        eurekaNotation = eurekaNotation
    )
}

