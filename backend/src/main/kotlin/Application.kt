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
import io.ktor.utils.io.*
import kotlinx.serialization.json.Json
import dto.*
import service.SudokuService
import service.CacheService
import database.CacheDatabase

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8181
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
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
        // Allow requests from the web frontend (typically runs on different port)
        anyHost() // For development - restrict in production
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowCredentials = true
    }
    
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (cause.message ?: "Unknown error")))
        }
    }
    
    routing {
        // Health check
        get("/health") {
            call.respond(mapOf("status" to "ok", "service" to "stormdoku-backend"))
        }
        
        route("/api") {
            route("/puzzle") {
                // Load a puzzle from string
                post("/load") {
                    val request = call.receive<LoadPuzzleRequest>()
                    val response = sudokuService.loadPuzzle(request.puzzle)
                    call.respond(response)
                }
                
                // Brute-force solve from grid DTO
                post("/solve") {
                    val request = call.receive<SolveRequest>()
                    val response = sudokuService.solve(request)
                    call.respond(response)
                }
                
                // Brute-force solve from puzzle string (recommended)
                post("/solve-from-puzzle") {
                    val endpoint = "/api/puzzle/solve-from-puzzle"
                    println("[CACHE] ${endpoint}: Request received")
                    
                    // Read raw request body as string for cache key (bypass ContentNegotiation)
                    val channel = call.receive<ByteReadChannel>()
                    val requestJson = channel.readRemaining().readText().trim()
                    println("[CACHE] ${endpoint}: Request body read, checking cache...")
                    
                    // Check cache FIRST - if hit, return immediately without any processing
                    val cachedResponse = cacheService.getCachedResponse(endpoint, requestJson)
                    if (cachedResponse != null) {
                        println("[CACHE] ${endpoint}: CACHE HIT - returning cached response, skipping processing")
                        val response = json.decodeFromString<SolveFromPuzzleResponse>(cachedResponse)
                        call.respond(response)
                        return@post  // Explicit early return - no further processing
                    }
                    
                    // Only reach here on cache miss - deserialize and process
                    println("[CACHE] ${endpoint}: CACHE MISS - processing request...")
                    val request = json.decodeFromString<SolveFromPuzzleRequest>(requestJson)
                    val response = sudokuService.solveFromPuzzle(request.puzzle)
                    println("[CACHE] ${endpoint}: Processing complete, storing in cache")
                    // Cache the response for next time
                    val responseJson = json.encodeToString(SolveFromPuzzleResponse.serializer(), response)
                    cacheService.storeCachedResponse(endpoint, requestJson, responseJson)
                    call.respond(response)
                }
            }
            
            route("/cell") {
                // Set cell value
                post("/set") {
                    val request = call.receive<SetCellRequest>()
                    val response = sudokuService.setCell(request)
                    call.respond(response)
                }
            }
            
            route("/techniques") {
                // Find all applicable techniques (from grid DTO)
                post("/find") {
                    val endpoint = "/api/techniques/find"
                    println("[CACHE] ${endpoint}: Request received")
                    
                    // Read raw request body as string for cache key (bypass ContentNegotiation)
                    val channel = call.receive<ByteReadChannel>()
                    val requestJson = channel.readRemaining().readText().trim()
                    println("[CACHE] ${endpoint}: Request body read, checking cache...")
                    
                    // Check cache FIRST - if hit, return immediately without any processing
                    val cachedResponse = cacheService.getCachedResponse(endpoint, requestJson)
                    if (cachedResponse != null) {
                        println("[CACHE] ${endpoint}: CACHE HIT - returning cached response, skipping processing")
                        val response = json.decodeFromString<FindTechniquesResponse>(cachedResponse)
                        call.respond(response)
                        return@post  // Explicit early return - no further processing
                    }
                    
                    // Only reach here on cache miss - deserialize and process
                    println("[CACHE] ${endpoint}: CACHE MISS - processing request...")
                    val request = json.decodeFromString<FindTechniquesRequest>(requestJson)
                    val response = sudokuService.findTechniques(request)
                    println("[CACHE] ${endpoint}: Processing complete, storing in cache")
                    // Cache the response for next time
                    val responseJson = json.encodeToString(FindTechniquesResponse.serializer(), response)
                    cacheService.storeCachedResponse(endpoint, requestJson, responseJson)
                    call.respond(response)
                }
                
                // Find all applicable techniques (from puzzle string - simpler)
                post("/find-from-puzzle") {
                    val endpoint = "/api/techniques/find-from-puzzle"
                    println("[CACHE] ${endpoint}: Request received")
                    
                    // Read raw request body as string for cache key (bypass ContentNegotiation)
                    val channel = call.receive<ByteReadChannel>()
                    val requestJson = channel.readRemaining().readText().trim()
                    println("[CACHE] ${endpoint}: Request body read, checking cache...")
                    
                    // Check cache FIRST - if hit, return immediately without any processing
                    val cachedResponse = cacheService.getCachedResponse(endpoint, requestJson)
                    if (cachedResponse != null) {
                        println("[CACHE] ${endpoint}: CACHE HIT - returning cached response, skipping processing")
                        val response = json.decodeFromString<FindTechniquesResponse>(cachedResponse)
                        call.respond(response)
                        return@post  // Explicit early return - no further processing
                    }
                    
                    // Only reach here on cache miss - deserialize and process
                    println("[CACHE] ${endpoint}: CACHE MISS - processing request...")
                    val request = json.decodeFromString<FindTechniquesFromPuzzleRequest>(requestJson)
                    val response = sudokuService.findTechniquesFromPuzzle(request.puzzle, request.basicOnly)
                    println("[CACHE] ${endpoint}: Processing complete, storing in cache")
                    // Cache the response for next time
                    val responseJson = json.encodeToString(FindTechniquesResponse.serializer(), response)
                    cacheService.storeCachedResponse(endpoint, requestJson, responseJson)
                    call.respond(response)
                }
                
                // Apply a specific technique
                post("/apply") {
                    val request = call.receive<ApplyTechniqueRequest>()
                    val response = sudokuService.applyTechnique(request)
                    call.respond(response)
                }
                
                // OPTIMIZED: Get single easiest hint (tiered search)
                post("/hint") {
                    val endpoint = "/api/techniques/hint"
                    println("[CACHE] ${endpoint}: Request received")
                    
                    // Read raw request body as string for cache key (bypass ContentNegotiation)
                    val channel = call.receive<ByteReadChannel>()
                    val requestJson = channel.readRemaining().readText().trim()
                    println("[CACHE] ${endpoint}: Request body read, checking cache...")
                    
                    // Check cache FIRST - if hit, return immediately without any processing
                    val cachedResponse = cacheService.getCachedResponse(endpoint, requestJson)
                    if (cachedResponse != null) {
                        println("[CACHE] ${endpoint}: CACHE HIT - returning cached response, skipping processing")
                        val response = json.decodeFromString<HintResponse>(cachedResponse)
                        call.respond(response)
                        return@post  // Explicit early return - no further processing
                    }
                    
                    // Only reach here on cache miss - deserialize and process
                    println("[CACHE] ${endpoint}: CACHE MISS - processing request...")
                    val request = json.decodeFromString<HintRequest>(requestJson)
                    val response = sudokuService.findHint(request.puzzle)
                    println("[CACHE] ${endpoint}: Processing complete, storing in cache")
                    // Cache the response for next time
                    val responseJson = json.encodeToString(HintResponse.serializer(), response)
                    cacheService.storeCachedResponse(endpoint, requestJson, responseJson)
                    call.respond(response)
                }
                
                // Grade puzzle - solve and return technique counts
                post("/grade") {
                    val endpoint = "/api/techniques/grade"
                    println("[CACHE] ${endpoint}: Request received")
                    
                    // Read raw request body as string for cache key (bypass ContentNegotiation)
                    val channel = call.receive<ByteReadChannel>()
                    val requestJson = channel.readRemaining().readText().trim()
                    println("[CACHE] ${endpoint}: Request body read, checking cache...")
                    
                    // Check cache FIRST - if hit, return immediately without any processing
                    val cachedResponse = cacheService.getCachedResponse(endpoint, requestJson)
                    if (cachedResponse != null) {
                        println("[CACHE] ${endpoint}: CACHE HIT - returning cached response, skipping processing")
                        val response = json.decodeFromString<GradePuzzleResponse>(cachedResponse)
                        call.respond(response)
                        return@post  // Explicit early return - no further processing
                    }
                    
                    // Only reach here on cache miss - deserialize and process
                    println("[CACHE] ${endpoint}: CACHE MISS - processing request...")
                    val request = json.decodeFromString<GradePuzzleRequest>(requestJson)
                    val response = sudokuService.gradePuzzle(request.puzzle)
                    println("[CACHE] ${endpoint}: Processing complete, storing in cache")
                    // Cache the response for next time
                    val responseJson = json.encodeToString(GradePuzzleResponse.serializer(), response)
                    cacheService.storeCachedResponse(endpoint, requestJson, responseJson)
                    call.respond(response)
                }
            }
        }
    }
}

