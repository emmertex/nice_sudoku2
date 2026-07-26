import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import dto.*
import service.SudokuService
import service.CacheService
import database.CacheDatabase
import validation.*
import kotlin.time.Duration.Companion.seconds

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
    validate: (Req) -> Unit = {},
    process: (Req) -> Resp,
) {
    val requestJson = call.receive<ByteReadChannel>().readRemaining().readText().trim()
    requireBodySize(requestJson)

    cache.getCachedResponse(endpoint, requestJson)?.let { cached ->
        call.application.log.debug("Cache HIT {}", endpoint)
        call.respond(json.decodeFromString<Resp>(cached))
        return
    }

    call.application.log.debug("Cache MISS {}", endpoint)
    val request = json.decodeFromString<Req>(requestJson)
    validate(request)
    val response = process(request)
    cache.storeCachedResponse(endpoint, requestJson, json.encodeToString(response))
    call.respond(response)
}

/** Client IP for rate limiting — prefers proxy headers set by nginx. */
private fun ApplicationCall.clientIp(): String {
    request.header("X-Real-IP")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    request.header("X-Forwarded-For")
        ?.substringBefore(",")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }
    return request.local.remoteHost
}

/**
 * Allowed browser origins for CORS.
 * Override with comma-separated CORS_ALLOWED_ORIGINS (e.g. production + local dev).
 */
private fun corsAllowedOrigins(): List<String> {
    val fromEnv = System.getenv("CORS_ALLOWED_ORIGINS")
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
    if (!fromEnv.isNullOrEmpty()) return fromEnv
    return listOf(
        "https://sudoku.emmertex.com",
        "http://sudoku.emmertex.com",
        "http://localhost:8080",
        "http://localhost:8081",
        "http://127.0.0.1:8080",
        "http://127.0.0.1:8081",
    )
}

fun Application.module() {
    // Initialize database
    CacheDatabase.initialize()

    val sudokuService = SudokuService()
    val cacheService = CacheService()
    val json = Json {
        prettyPrint = true
        // Strict JSON: reject malformed numbers / unquoted keys from clients.
        isLenient = false
        ignoreUnknownKeys = true
    }

    install(ContentNegotiation) {
        json(json)
    }

    install(CORS) {
        corsAllowedOrigins()
            .groupBy { it.removePrefix("https://").removePrefix("http://") }
            .forEach { (host, origins) ->
                val schemes = origins.map { o ->
                    if (o.startsWith("https://")) "https" else "http"
                }.distinct()
                allowHost(host, schemes = schemes)
            }

        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)

        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)

        exposeHeader(HttpHeaders.ContentType)

        // Credentials intentionally off — frontend uses credential-less fetch.
    }

    // Abuse protection for the public solver/hint API (no auth by design).
    val apiRateLimit = System.getenv("API_RATE_LIMIT")?.toIntOrNull() ?: 60
    install(RateLimit) {
        register(RateLimitName("api")) {
            rateLimiter(limit = apiRateLimit, refillPeriod = 60.seconds)
            requestKey { call -> call.clientIp() }
        }
    }

    install(StatusPages) {
        exception<ApiValidationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "Invalid request")))
        }
        exception<SerializationException> { call, cause ->
            call.application.log.warn("Malformed JSON for ${call.request.local.uri}: ${cause.message}")
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Malformed JSON request"))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.application.log.warn("Bad request for ${call.request.local.uri}: ${cause.message}")
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request"))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception for ${call.request.local.uri}", cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
        }
    }

    // Reject oversized bodies early when Content-Length is present (nginx also enforces 64k).
    intercept(ApplicationCallPipeline.Plugins) {
        val length = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
        if (length != null && length > MAX_REQUEST_BODY_BYTES) {
            call.respond(
                HttpStatusCode.PayloadTooLarge,
                mapOf("error" to "Request body exceeds maximum size of $MAX_REQUEST_BODY_BYTES bytes")
            )
            finish()
        }
    }

    routing {
        // Health check (not rate-limited — for probes / docker healthchecks)
        get("/health") {
            call.respond(mapOf("status" to "ok", "service" to "stormdoku-backend"))
        }

        rateLimit(RateLimitName("api")) {
            route("/api") {
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
                    post("/load") {
                        val request = call.receive<LoadPuzzleRequest>()
                        requireValidPuzzle(request.puzzle)
                        call.respond(sudokuService.loadPuzzle(request.puzzle))
                    }

                    post("/solve") {
                        val request = call.receive<SolveRequest>()
                        requireValidGrid(request.grid)
                        call.respond(sudokuService.solve(request))
                    }

                    post("/solve-from-puzzle") {
                        cachedPost<SolveFromPuzzleRequest, SolveFromPuzzleResponse>(
                            "/api/puzzle/solve-from-puzzle", cacheService, json,
                            validate = { requireValidPuzzle(it.puzzle) }
                        ) { sudokuService.solveFromPuzzle(it.puzzle) }
                    }
                }

                route("/cell") {
                    post("/set") {
                        val request = call.receive<SetCellRequest>()
                        requireValidGrid(request.grid)
                        requireValidCellIndex(request.cellIndex)
                        requireValidCellValue(request.value)
                        call.respond(sudokuService.setCell(request))
                    }
                }

                route("/techniques") {
                    post("/find") {
                        cachedPost<FindTechniquesRequest, FindTechniquesResponse>(
                            "/api/techniques/find", cacheService, json,
                            validate = { requireValidGrid(it.grid) }
                        ) { sudokuService.findTechniques(it) }
                    }

                    post("/find-from-puzzle") {
                        cachedPost<FindTechniquesFromPuzzleRequest, FindTechniquesResponse>(
                            "/api/techniques/find-from-puzzle", cacheService, json,
                            validate = { requireValidPuzzle(it.puzzle) }
                        ) { sudokuService.findTechniquesFromPuzzle(it.puzzle, it.basicOnly) }
                    }

                    post("/apply") {
                        val request = call.receive<ApplyTechniqueRequest>()
                        requireValidGrid(request.grid)
                        requireValidTechniqueId(request.techniqueId)
                        call.respond(sudokuService.applyTechnique(request))
                    }

                    post("/hint") {
                        cachedPost<HintRequest, HintResponse>(
                            "/api/techniques/hint", cacheService, json,
                            validate = { requireValidPuzzle(it.puzzle) }
                        ) { sudokuService.findHint(it.puzzle) }
                    }

                    post("/grade") {
                        cachedPost<GradePuzzleRequest, GradePuzzleResponse>(
                            "/api/techniques/grade", cacheService, json,
                            validate = { requireValidPuzzle(it.puzzle) }
                        ) { sudokuService.gradePuzzle(it.puzzle) }
                    }
                }
            }
        }
    }
}
