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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Semaphore

// Trusted proxy addresses for X-Real-IP / X-Forwarded-For handling.
// When the backend sits behind an upstream reverse proxy, that proxy's address
// must be listed here. Empty means no forwarded headers are trusted.
private val trustedProxyAddrs: Set<String> = System.getenv("TRUSTED_PROXY_ADDRS")
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.toSet()
    ?: emptySet()

private val solverSlots = Semaphore((System.getenv("SOLVER_CONCURRENCY")?.toIntOrNull() ?: 2).coerceIn(1, 16))
private class SolverBusy : RuntimeException("Solver is busy; please retry shortly")
private suspend fun <T> runSolver(block: () -> T): T {
    if (!solverSlots.tryAcquire()) throw SolverBusy()
    try { return withContext(Dispatchers.Default) { block() } }
    finally { solverSlots.release() }
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8181
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

/**
 * Read the request body as text, enforcing a byte limit during the stream
 * rather than after consuming the whole body.
 *
 * This prevents DoS from oversized bodies (with or without Content-Length) and
 * ensures the byte budget is respected even when the stream is large.
 *
 * @throws ApiValidationException if the body exceeds [maxBytes].
 */
internal class RequestBodyTooLarge : RuntimeException("Request body exceeds maximum size of $MAX_REQUEST_BODY_BYTES bytes")

internal suspend fun readLimitedBody(channel: ByteReadChannel, maxBytes: Int = MAX_REQUEST_BODY_BYTES): String {
    val buf = ByteArray(maxBytes + 1)
    var total = 0
    try {
        while (total <= maxBytes) {
            val read = channel.readAvailable(buf, total, buf.size - total)
            if (read == -1) return buf.decodeToString(0, total).trim()
            total += read
        }
        throw RequestBodyTooLarge()
    } finally {
        channel.cancel()
    }
}

private suspend fun ApplicationCall.readBody(): String = readLimitedBody(receive<ByteReadChannel>())

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
    crossinline process: (Req) -> Resp,
) {
    val requestJson = call.readBody()
    if (requestJson.toByteArray(Charsets.UTF_8).size > MAX_REQUEST_BODY_BYTES) {
        throw ApiValidationException("Request body exceeds maximum size of $MAX_REQUEST_BODY_BYTES bytes")
    }

    cache.getCachedResponse(endpoint, requestJson)?.let { cached ->
        call.application.log.debug("Cache HIT {}", endpoint)
        try {
            call.respond(json.decodeFromString<Resp>(cached))
            return
        } catch (_: SerializationException) {
            call.application.log.warn("Ignoring incompatible cached response for {}", endpoint)
        }
    }

    call.application.log.debug("Cache MISS {}", endpoint)
    val request = json.decodeFromString<Req>(requestJson)
    validate(request)
    val response = runSolver { process(request) }
    cache.storeCachedResponse(endpoint, requestJson, json.encodeToString(response))
    call.respond(response)
}

/**
 * Determine the real client IP for rate limiting.
 *
 * If the request comes from a trusted proxy (configured via TRUSTED_PROXY_ADDRS),
 * extract the real client address from X-Forwarded-For. Otherwise use the immediate
 * peer address (direct connection).
 */
internal fun resolveClientIp(peer: String, realIp: String?, forwarded: String?, trusted: Set<String>): String {
    if (peer !in trusted) return peer
    realIp?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return forwarded?.split(",")?.map { it.trim() }
        ?.lastOrNull { it.isNotEmpty() && it !in trusted } ?: peer
}

private fun ApplicationCall.clientIp(): String {
    val trustedPeers = trustedProxyAddrs.flatMap { address ->
        try { java.net.InetAddress.getAllByName(address).map { it.hostAddress } }
        catch (_: java.net.UnknownHostException) { emptyList() }
    }.toSet()
    return resolveClientIp(request.local.remoteHost, request.header("X-Real-IP"),
        request.header("X-Forwarded-For"), trustedPeers)
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

fun Application.module() = sudokuModule()

fun Application.sudokuModule(cachePath: String = CacheDatabase.DEFAULT_DB_PATH) {
    // Initialize database
    CacheDatabase.initialize(cachePath)

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
        exception<SolverBusy> { call, cause ->
            call.response.header(HttpHeaders.RetryAfter, "5")
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to cause.message))
        }
        exception<RequestBodyTooLarge> { call, cause ->
            call.respond(HttpStatusCode.PayloadTooLarge, mapOf("error" to cause.message))
        }
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
                        val requestJson = call.readBody()
                        val request = json.decodeFromString<LoadPuzzleRequest>(requestJson)
                        requireValidPuzzle(request.puzzle)
                        call.respond(sudokuService.loadPuzzle(request.puzzle))
                    }

                    post("/solve") {
                        val requestJson = call.readBody()
                        val request = json.decodeFromString<SolveRequest>(requestJson)
                        requireValidGrid(request.grid)
                        call.respond(runSolver { sudokuService.solve(request) })
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
                        val requestJson = call.readBody()
                        val request = json.decodeFromString<SetCellRequest>(requestJson)
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
                        val requestJson = call.readBody()
                        val request = json.decodeFromString<ApplyTechniqueRequest>(requestJson)
                        requireValidGrid(request.grid)
                        requireValidTechniqueId(request.techniqueId)
                        call.respond(runSolver { sudokuService.applyTechnique(request) })
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
