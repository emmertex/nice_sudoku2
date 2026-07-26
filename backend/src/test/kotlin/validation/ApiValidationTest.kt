package validation

import dto.CellDto
import dto.GridDto
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ApiValidationTest {

    @Test
    fun `accepts valid puzzle string`() {
        requireValidPuzzle("0".repeat(81))
        requireValidPuzzle(".".repeat(81))
        requireValidPuzzle("123456789".repeat(9))
    }

    @Test
    fun `rejects wrong length puzzle`() {
        assertFailsWith<ApiValidationException> { requireValidPuzzle("123") }
        assertFailsWith<ApiValidationException> { requireValidPuzzle("0".repeat(82)) }
    }

    @Test
    fun `rejects invalid puzzle characters`() {
        assertFailsWith<ApiValidationException> {
            requireValidPuzzle("x".repeat(81))
        }
    }

    @Test
    fun `accepts valid grid`() {
        val grid = GridDto(cells = (0..80).map { CellDto(index = it, value = if (it < 9) it + 1 else null) })
        requireValidGrid(grid)
    }

    @Test
    fun `rejects grid with wrong cell count`() {
        assertFailsWith<ApiValidationException> {
            requireValidGrid(GridDto(cells = listOf(CellDto(index = 0))))
        }
    }

    @Test
    fun `rejects invalid technique id`() {
        assertFailsWith<ApiValidationException> { requireValidTechniqueId("not-a-uuid") }
        requireValidTechniqueId("550e8400-e29b-41d4-a716-446655440000")
    }

    @Test
    fun `rejects oversized body`() {
        assertFailsWith<ApiValidationException> {
            requireBodySize("a".repeat(MAX_REQUEST_BODY_BYTES + 1))
        }
        requireBodySize("ok")
    }

    @Test
    fun `rejects duplicate cell indices`() {
        val cells = (0..80).map { CellDto(index = if (it == 80) 0 else it) }
        val ex = assertFailsWith<ApiValidationException> { requireValidGrid(GridDto(cells = cells)) }
        assertTrue(ex.message!!.contains("duplicate"))
    }
}
