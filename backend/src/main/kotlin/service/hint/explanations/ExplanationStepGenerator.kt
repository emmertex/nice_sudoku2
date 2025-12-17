package service.hint.explanations

import sudoku.match.TechniqueMatch
import sudoku.match.AICMatch
import sudoku.match.ALSMatch
import sudoku.solvingtechClassifier.Technique
import dto.*
import service.hint.explanations.generateSingleSteps
import service.hint.explanations.generateSubsetSteps
import service.hint.explanations.generateIntersectionSteps
import service.hint.techniques.generateKiteSteps
import service.hint.techniques.generateFishSteps
import service.hint.techniques.generateSkyscraperSteps
import service.hint.techniques.generateFinnedFishSteps
import service.hint.techniques.generateWingSteps
import service.hint.explanations.generateCycleSteps
import service.hint.explanations.generateColouringSteps
import service.hint.explanations.generateUniqueRectangleSteps
import service.hint.explanations.generateBugSteps
import service.hint.explanations.generateEmptyRectangleSteps
import service.hint.explanations.generateSueDeCoqSteps
import service.hint.explanations.generateForcingChainSteps
import service.hint.explanations.generateNishioSteps
import service.hint.explanations.generateChainLikeSteps
import service.hint.explanations.generateChainSteps
import service.hint.explanations.generateALSSteps
import service.hint.explanations.generateGenericSteps

/**
 * Generate step-by-step explanation for a technique
 */
fun generateExplanationSteps(
    technique: Technique,
    match: TechniqueMatch,
    eliminations: List<EliminationDto>,
    solvedCells: List<SolvedCellDto>,
    puzzleString: String
): List<ExplanationStepDto> {
    val techniqueName = technique.getName()
    val steps = mutableListOf<ExplanationStepDto>()
    
    when {
        techniqueName.contains("Single", ignoreCase = true) -> {
            steps.addAll(generateSingleSteps(techniqueName, match, eliminations, solvedCells))
        }
        techniqueName.contains("Pair", ignoreCase = true) || 
        techniqueName.contains("Triple", ignoreCase = true) ||
        techniqueName.contains("Quadruple", ignoreCase = true) -> {
            steps.addAll(generateSubsetSteps(techniqueName, match, eliminations))
        }
        techniqueName.contains("Pointing", ignoreCase = true) ||
        techniqueName.contains("Claiming", ignoreCase = true) ||
        techniqueName.contains("Box/Line", ignoreCase = true) -> {
            steps.addAll(generateIntersectionSteps(techniqueName, match, eliminations))
        }
        techniqueName.contains("2-String Kite", ignoreCase = true) ||
        (techniqueName.contains("Kite", ignoreCase = true) && !techniqueName.contains("String", ignoreCase = true)) -> {
            steps.addAll(generateKiteSteps(techniqueName, match, eliminations, puzzleString))
        }
        techniqueName.contains("Skyscraper", ignoreCase = true) -> {
            steps.addAll(generateSkyscraperSteps(techniqueName, match, eliminations))
        }
        techniqueName.contains("Finned", ignoreCase = true) || 
        techniqueName.contains("Sashimi", ignoreCase = true) -> {
            steps.addAll(generateFinnedFishSteps(techniqueName, match, eliminations))
        }
        techniqueName.contains("Fish", ignoreCase = true) ||
        techniqueName.contains("X-Wing", ignoreCase = true) ||
        techniqueName.contains("Swordfish", ignoreCase = true) ||
        techniqueName.contains("Jellyfish", ignoreCase = true) -> {
            steps.addAll(generateFishSteps(techniqueName, match, eliminations))
        }
        techniqueName.contains("Wing", ignoreCase = true) -> {
            steps.addAll(generateWingSteps(techniqueName, match, eliminations))
        }
        techniqueName.contains("Cycle", ignoreCase = true) -> {
            steps.addAll(generateCycleSteps(techniqueName, match, eliminations))
        }
        techniqueName.contains("Colour", ignoreCase = true) ||
        techniqueName.contains("Color", ignoreCase = true) ||
        techniqueName.contains("Medusa", ignoreCase = true) -> {
            steps.addAll(generateColouringSteps(techniqueName, match, eliminations))
        }
        techniqueName.contains("Unique Rectangle", ignoreCase = true) -> {
            steps.addAll(generateUniqueRectangleSteps(techniqueName, match, eliminations))
        }
        techniqueName.equals("BUG", ignoreCase = true) || techniqueName.contains("Bivalue", ignoreCase = true) -> {
            steps.addAll(generateBugSteps(eliminations))
        }
        techniqueName.contains("Empty Rectangle", ignoreCase = true) -> {
            steps.addAll(generateEmptyRectangleSteps(techniqueName, match, eliminations))
        }
        techniqueName.contains("Sue", ignoreCase = true) -> {
            steps.addAll(generateSueDeCoqSteps(eliminations))
        }
        techniqueName.contains("Forcing", ignoreCase = true) -> {
            steps.addAll(generateForcingChainSteps(techniqueName, eliminations))
        }
        techniqueName.contains("Nishio", ignoreCase = true) -> {
            steps.addAll(generateNishioSteps(eliminations))
        }
        techniqueName.contains("Chain", ignoreCase = true) -> {
            steps.addAll(generateChainLikeSteps(techniqueName, eliminations))
        }
        match is AICMatch -> {
            steps.addAll(generateChainSteps(techniqueName, match, eliminations))
        }
        match is ALSMatch -> {
            steps.addAll(generateALSSteps(techniqueName, match, eliminations))
        }
        else -> {
            // Generic explanation for other techniques
            steps.addAll(generateGenericSteps(techniqueName, match, eliminations, solvedCells))
        }
    }
    
    return steps
}

