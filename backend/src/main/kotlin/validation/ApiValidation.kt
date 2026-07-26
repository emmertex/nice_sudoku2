package validation

import dto.GridDto
import java.util.UUID

/** Client-safe validation failure (maps to HTTP 400). */
class ApiValidationException(message: String) : IllegalArgumentException(message)

/** Max raw JSON body size for cached POST endpoints (grid+candidates payloads). */
const val MAX_REQUEST_BODY_BYTES: Int = 64 * 1024

private val PUZZLE_CHAR_REGEX = Regex("^[0-9.]{81}$")

/**
 * Sudoku puzzle strings are exactly 81 characters: digits 0-9 (0 = empty) or '.'.
 */
fun requireValidPuzzle(puzzle: String, field: String = "puzzle") {
    if (puzzle.length != 81) {
        throw ApiValidationException("$field must be exactly 81 characters")
    }
    if (!PUZZLE_CHAR_REGEX.matches(puzzle)) {
        throw ApiValidationException("$field must contain only digits 0-9 and '.'")
    }
}

/**
 * Validate a grid DTO: 81 cells with indices 0..80, values/candidates in 1..9.
 */
fun requireValidGrid(grid: GridDto, field: String = "grid") {
    if (grid.cells.size != 81) {
        throw ApiValidationException("$field must contain exactly 81 cells")
    }
    val seen = BooleanArray(81)
    for (cell in grid.cells) {
        if (cell.index !in 0..80) {
            throw ApiValidationException("$field cell index must be 0..80")
        }
        if (seen[cell.index]) {
            throw ApiValidationException("$field has duplicate cell index ${cell.index}")
        }
        seen[cell.index] = true
        val value = cell.value
        if (value != null && value !in 1..9) {
            throw ApiValidationException("$field cell value must be 1..9 or null")
        }
        if (cell.candidates.any { it !in 1..9 }) {
            throw ApiValidationException("$field candidates must be digits 1..9")
        }
    }
    if (seen.any { !it }) {
        throw ApiValidationException("$field is missing one or more cell indices")
    }
}

fun requireValidCellIndex(index: Int, field: String = "cellIndex") {
    if (index !in 0..80) {
        throw ApiValidationException("$field must be 0..80")
    }
}

fun requireValidCellValue(value: Int?, field: String = "value") {
    if (value != null && value !in 1..9) {
        throw ApiValidationException("$field must be 1..9 or null")
    }
}

/** Technique match IDs are UUIDs minted by the backend. */
fun requireValidTechniqueId(techniqueId: String) {
    try {
        UUID.fromString(techniqueId)
    } catch (_: IllegalArgumentException) {
        throw ApiValidationException("techniqueId must be a valid UUID")
    }
}

fun requireBodySize(body: String, maxBytes: Int = MAX_REQUEST_BODY_BYTES) {
    // Approximate UTF-8 size; rejects oversized cache keys / DoS payloads early.
    val size = body.toByteArray(Charsets.UTF_8).size
    if (size > maxBytes) {
        throw ApiValidationException("Request body exceeds maximum size of $maxBytes bytes")
    }
}
