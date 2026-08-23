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
    val originalTechniqueName = technique.getName()
    val techniqueName = service.hint.helpers.LanguageKeyBuilder.normalizeTechniqueName(originalTechniqueName)
    val steps = mutableListOf<ExplanationStepDto>()
    
    when {
        techniqueName.contains("single") -> {
            steps.addAll(generateSingleSteps(originalTechniqueName, match, eliminations, solvedCells))
        }
        techniqueName.contains("pair") || 
        techniqueName.contains("triple") ||
        techniqueName.contains("quadruple") -> {
            steps.addAll(generateSubsetSteps(originalTechniqueName, match, eliminations))
        }
        techniqueName.contains("pointing") ||
        techniqueName.contains("claiming") ||
        techniqueName.contains("box_line") -> {
            steps.addAll(generateIntersectionSteps(originalTechniqueName, match, eliminations))
        }
        techniqueName.contains("kite") -> {
            steps.addAll(generateKiteSteps(originalTechniqueName, match, eliminations, puzzleString))
        }
        techniqueName.contains("skyscraper") -> {
            steps.addAll(generateSkyscraperSteps(originalTechniqueName, match, eliminations))
        }
        techniqueName.contains("finned") || 
        techniqueName.contains("sashimi") -> {
            steps.addAll(generateFinnedFishSteps(originalTechniqueName, match, eliminations))
        }
        techniqueName.contains("fish") ||
        techniqueName.contains("x_wing") ||
        techniqueName.contains("swordfish") ||
        techniqueName.contains("jellyfish") -> {
            steps.addAll(generateFishSteps(originalTechniqueName, match, eliminations))
        }
        techniqueName.contains("wing") -> {
            steps.addAll(generateWingSteps(originalTechniqueName, match, eliminations))
        }
        techniqueName.contains("cycle") -> {
            steps.addAll(generateCycleSteps(originalTechniqueName, match, eliminations))
        }
        techniqueName.contains("colour") ||
        techniqueName.contains("color") ||
        techniqueName.contains("medusa") -> {
            steps.addAll(generateColouringSteps(originalTechniqueName, match, eliminations))
        }
        techniqueName.contains("unique_rectangle") -> {
            steps.addAll(generateUniqueRectangleSteps(originalTechniqueName, match, eliminations))
        }
        techniqueName == "bug" || techniqueName.contains("bivalue") -> {
            steps.addAll(generateBugSteps(eliminations))
        }
        techniqueName.contains("empty_rectangle") -> {
            steps.addAll(generateEmptyRectangleSteps(originalTechniqueName, match, eliminations))
        }
        techniqueName.contains("sue") -> {
            steps.addAll(generateSueDeCoqSteps(eliminations))
        }
        techniqueName.contains("forcing") -> {
            steps.addAll(generateForcingChainSteps(originalTechniqueName, eliminations))
        }
        techniqueName.contains("nishio") -> {
            steps.addAll(generateNishioSteps(eliminations))
        }
        techniqueName.contains("chain") -> {
            steps.addAll(generateChainLikeSteps(originalTechniqueName, eliminations))
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

