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
import kotlinx.serialization.json.Json
import dto.*
import service.SudokuService

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8181
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val sudokuService = SudokuService()
    
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
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
                    val request = call.receive<SolveFromPuzzleRequest>()
                    val response = sudokuService.solveFromPuzzle(request.puzzle)
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
                    val request = call.receive<FindTechniquesRequest>()
                    val response = sudokuService.findTechniques(request)
                    call.respond(response)
                }
                
                // Find all applicable techniques (from puzzle string - simpler)
                post("/find-from-puzzle") {
                    val request = call.receive<FindTechniquesFromPuzzleRequest>()
                    val response = sudokuService.findTechniquesFromPuzzle(request.puzzle, request.basicOnly)
                    call.respond(response)
                }
                
                // Apply a specific technique
                post("/apply") {
                    val request = call.receive<ApplyTechniqueRequest>()
                    val response = sudokuService.applyTechnique(request)
                    call.respond(response)
                }
            }
        }
    }
}

