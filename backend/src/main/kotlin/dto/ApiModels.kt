package dto

import kotlinx.serialization.Serializable

/**
 * API Data Transfer Objects for StormDoku backend
 */

@Serializable
data class CellDto(
    val index: Int,
    val value: Int? = null,
    val candidates: Set<Int> = emptySet(),
    val isGiven: Boolean = false
)

@Serializable
data class GridDto(
    val cells: List<CellDto>,
    val isComplete: Boolean = false,
    val isValid: Boolean = true
)

@Serializable
data class LoadPuzzleRequest(
    val puzzle: String
)

@Serializable
data class LoadPuzzleResponse(
    val success: Boolean,
    val grid: GridDto? = null,
    val error: String? = null
)

@Serializable
data class SetCellRequest(
    val grid: GridDto,
    val cellIndex: Int,
    val value: Int?
)

@Serializable
data class SetCellResponse(
    val success: Boolean,
    val grid: GridDto? = null,
    val error: String? = null
)

@Serializable
data class SolveRequest(
    val grid: GridDto
)

@Serializable
data class SolveResponse(
    val success: Boolean,
    val grid: GridDto? = null,
    val hasSolution: Boolean = false,
    val error: String? = null
)

@Serializable
data class EliminationDto(
    val digit: Int,
    val cells: List<Int>
)

@Serializable
data class SolvedCellDto(
    val cell: Int,
    val digit: Int
)

/**
 * Represents a candidate location (row, col, candidate digit)
 */
@Serializable
data class CandidateLocationDto(
    val row: Int,
    val col: Int,
    val candidate: Int  // 1-9
)

/**
 * Represents a line connecting two candidates (for chain visualizations)
 */
@Serializable
data class LineDto(
    val from: CandidateLocationDto,
    val to: CandidateLocationDto,
    val curveX: Double? = null,  // Curve control point X offset
    val curveY: Double? = null,  // Curve control point Y offset
    val isStrongLink: Boolean = false,  // Strong (=) vs weak (-) link
    val lineType: String? = null  // "strong", "weak", "rcc", "rcc-link", etc.
)

/**
 * Represents a group of candidates to highlight together
 */
@Serializable
data class GroupDto(
    val candidates: List<CandidateLocationDto>,
    val groupType: String? = null,  // "chain-on", "chain-off", "als", etc.
    val colorIndex: Int = 0  // For distinguishing multiple groups
)

/**
 * Represents a single step in a technique explanation
 */
@Serializable
data class ExplanationStepDto(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val highlightCells: List<Int> = emptyList(),
    val highlightCandidates: List<CandidateLocationDto> = emptyList(),
    val lines: List<LineDto> = emptyList(),
    val groups: List<GroupDto> = emptyList()
)

@Serializable
data class TechniqueMatchDto(
    val id: String,
    val techniqueName: String,
    val description: String,
    val eliminations: List<EliminationDto> = emptyList(),
    val solvedCells: List<SolvedCellDto> = emptyList(),
    val highlightCells: List<Int> = emptyList(),
    // New visual data fields
    val lines: List<LineDto> = emptyList(),
    val groups: List<GroupDto> = emptyList(),
    val explanationSteps: List<ExplanationStepDto> = emptyList(),
    val eurekaNotation: String? = null
)

@Serializable
data class FindTechniquesRequest(
    val grid: GridDto,
    val basicOnly: Boolean = false
)

@Serializable
data class FindTechniquesResponse(
    val success: Boolean,
    val techniques: Map<String, List<TechniqueMatchDto>> = emptyMap(),
    val totalMatches: Int = 0,
    val error: String? = null
)

@Serializable
data class ApplyTechniqueRequest(
    val grid: GridDto,
    val techniqueId: String
)

@Serializable
data class ApplyTechniqueResponse(
    val success: Boolean,
    val grid: GridDto? = null,
    val error: String? = null
)

@Serializable
data class FindTechniquesFromPuzzleRequest(
    val puzzle: String,
    val basicOnly: Boolean = false
)

@Serializable
data class SolveFromPuzzleRequest(
    val puzzle: String
)

@Serializable
data class SolveFromPuzzleResponse(
    val success: Boolean,
    val solution: String? = null,
    val hasSolution: Boolean = false,
    val error: String? = null
)

