import dto.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.*
import service.SudokuService
import service.hint.helpers.dtoToBasicGrid

class ApiLifecycleTest {
    private val puzzle = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"

    @Test fun cachedIdsApplyAfterRestartAndForIndependentClients() = testApplication {
        val directory = Files.createTempDirectory("sudoku-api-test")
        application { sudokuModule(directory.resolve("cache.db").toString()) }
        suspend fun find() = client.post("/api/techniques/find-from-puzzle") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(FindTechniquesFromPuzzleRequest(puzzle, true)))
        }
        val first = Json.decodeFromString<FindTechniquesResponse>(find().bodyAsText())
        val cached = Json.decodeFromString<FindTechniquesResponse>(find().bodyAsText())
        val id = first.techniques.values.flatten().first().id
        assertEquals(id, cached.techniques.values.flatten().first().id)
        assertTrue(Regex("[0-9a-f]{64}").matches(id))
        val grid = SudokuService().loadPuzzle(puzzle).grid!!
        // A fresh service has no process-local handle cache.
        assertTrue(SudokuService().applyTechnique(ApplyTechniqueRequest(grid, id)).success)
        repeat(2) {
            val response = client.post("/api/techniques/apply") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(ApplyTechniqueRequest(grid, id)))
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(Json.decodeFromString<ApplyTechniqueResponse>(response.bodyAsText()).success)
        }
        val oversized = client.post("/api/puzzle/load") { setBody(" ".repeat(65537)) }
        assertEquals(HttpStatusCode.PayloadTooLarge, oversized.status)
    }

    @Test fun explicitlyEmptyCandidatesStayEmpty() {
        val grid = GridDto((0..80).map { CellDto(index = it, candidates = if (it == 0) emptySet() else (1..9).toSet()) })
        val converted = dtoToBasicGrid(grid)
        assertTrue((0..8).none { converted.hasCandidate(0, it) })
    }
}
