import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import dto.*
import service.SudokuService
import service.CacheService
import database.CacheDatabase

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8181
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

/**
 * Handle a POST whose response is cached by exact request body.
 *
 * The body is read raw (bypassing ContentNegotiation) so it can double as the
 * cache key. On a hit the cached JSON is replayed; on a miss [process] runs and
 * its result is stored before responding.
 */
private suspend inline fun <reified Req : Any, reified Resp : Any> PipelineContext<Unit, ApplicationCall>.cachedPost(
    endpoint: String,
    cache: CacheService,
    json: Json,
    process: (Req) -> Resp,
) {
    val requestJson = call.receive<ByteReadChannel>().readRemaining().readText().trim()

    cache.getCachedResponse(endpoint, requestJson)?.let { cached ->
        call.application.log.debug("Cache HIT {}", endpoint)
        call.respond(json.decodeFromString<Resp>(cached))
        return
    }

    call.application.log.debug("Cache MISS {}", endpoint)
    val response = process(json.decodeFromString<Req>(requestJson))
    cache.storeCachedResponse(endpoint, requestJson, json.encodeToString(response))
    call.respond(response)
}

fun Application.module() {
    // Initialize database
    CacheDatabase.initialize()

    val sudokuService = SudokuService()
    val cacheService = CacheService()
    val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    install(ContentNegotiation) {
        json(json)
    }

    install(CORS) {
        // Allow requests from any origin (including sudoku.emmertex.com)
        anyHost()

        // Allow common HTTP methods
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)

        // Allow common headers
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.Origin)

        // Expose headers to the client
        exposeHeader(HttpHeaders.ContentType)

        // Note: allowCredentials is intentionally NOT set (defaults to false)
        // because the frontend doesn't send credentials with fetch requests.
        // Setting allowCredentials=true with anyHost() causes CORS issues
        // since Access-Control-Allow-Origin cannot be "*" when credentials are allowed.
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception for ${call.request.local.uri}", cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (cause.message ?: "Unknown error")))
        }
    }

    routing {
        // Health check
        get("/health") {
            call.respond(mapOf("status" to "ok", "service" to "stormdoku-backend"))
        }

        route("/api") {
            // Cache info endpoint
            route("/cache") {
                get("/info") {
                    val dbSize = cacheService.getDatabaseFileSize()
                    call.respond(CacheInfoResponse(
                        databaseFileSizeBytes = dbSize,
                        databaseFileSizeMB = dbSize / (1024.0 * 1024.0),
                        note = "Use SQLite tools to query cache statistics directly"
                    ))
                }
            }

            route("/puzzle") {
                // Load a puzzle from string
                post("/load") {
                    val request = call.receive<LoadPuzzleRequest>()
                    call.respond(sudokuService.loadPuzzle(request.puzzle))
                }

                // Brute-force solve from grid DTO
                post("/solve") {
                    val request = call.receive<SolveRequest>()
                    call.respond(sudokuService.solve(request))
                }

                // Brute-force solve from puzzle string (recommended, cached)
                post("/solve-from-puzzle") {
                    cachedPost<SolveFromPuzzleRequest, SolveFromPuzzleResponse>(
                        "/api/puzzle/solve-from-puzzle", cacheService, json
                    ) { sudokuService.solveFromPuzzle(it.puzzle) }
                }
            }

            route("/cell") {
                // Set cell value
                post("/set") {
                    val request = call.receive<SetCellRequest>()
                    call.respond(sudokuService.setCell(request))
                }
            }

            route("/techniques") {
                // Find all applicable techniques (from grid DTO, cached)
                post("/find") {
                    cachedPost<FindTechniquesRequest, FindTechniquesResponse>(
                        "/api/techniques/find", cacheService, json
                    ) { sudokuService.findTechniques(it) }
                }

                // Find all applicable techniques (from puzzle string, cached)
                post("/find-from-puzzle") {
                    cachedPost<FindTechniquesFromPuzzleRequest, FindTechniquesResponse>(
                        "/api/techniques/find-from-puzzle", cacheService, json
                    ) { sudokuService.findTechniquesFromPuzzle(it.puzzle, it.basicOnly) }
                }

                // Apply a specific technique
                post("/apply") {
                    val request = call.receive<ApplyTechniqueRequest>()
                    call.respond(sudokuService.applyTechnique(request))
                }

                // OPTIMIZED: Get single easiest hint (tiered search, cached)
                post("/hint") {
                    cachedPost<HintRequest, HintResponse>(
                        "/api/techniques/hint", cacheService, json
                    ) { sudokuService.findHint(it.puzzle) }
                }

                // Grade puzzle - solve and return technique counts (cached)
                post("/grade") {
                    cachedPost<GradePuzzleRequest, GradePuzzleResponse>(
                        "/api/techniques/grade", cacheService, json
                    ) { sudokuService.gradePuzzle(it.puzzle) }
                }
            }
        }
    }
}
