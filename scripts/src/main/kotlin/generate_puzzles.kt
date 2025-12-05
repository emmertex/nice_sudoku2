import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// Technique priority mapping - ordered by human difficulty (unique, no gaps)
// Lower = easier to spot and apply, Higher = harder
val techniquePriority = mapOf(
    // === BEGINNER (1-4): Singles + Intersection (pure scanning) ===
    "NAKED_SINGLE" to 1, "Naked Singles" to 1,           // Only one candidate in cell
    "HIDDEN_SINGLE" to 2, "Hidden Singles" to 2,         // Only place for a digit in house
    "POINTING_CANDIDATES" to 3, "Pointing Candidates" to 3, "Pointing Pairs" to 3,
    "CLAIMING_CANDIDATES" to 4, "Claiming Candidates" to 4, "Box/Line Reduction" to 4,

    // === EASY (5-7): Basic subsets ===
    "NAKED_PAIR" to 5, "Naked Pairs" to 5,               // Two cells, two candidates
    "NAKED_TRIPLE" to 6, "Naked Triples" to 6,           // Three cells, three candidates
    "HIDDEN_PAIR" to 7, "Hidden Pairs" to 7,             // Two candidates in only two cells

    // === MEDIUM (8-10): Harder subsets ===
    "HIDDEN_TRIPLE" to 8, "Hidden Triples" to 8,         // Three candidates in only three cells
    "NAKED_QUADRUPLE" to 9, "Naked Quadruples" to 9,     // Four cells, four candidates
    "HIDDEN_QUADRUPLE" to 10, "Hidden Quadruples" to 10, // Four candidates in only four cells

    // === TOUGH (11-15): Fish & single-digit patterns ===
    "X_WING_FISH" to 11, "X-Wing" to 11,                 // 2x2 fish - fundamental pattern
    "SKYSCRAPER_FISH" to 12, "Skyscraper" to 12,         // Two-string turbot fish
    "TWO_STRING_KITE_FISH" to 13, "2-String Kite" to 13, // Box + row/col intersection
    "FINNED_X_WING_FISH" to 14, "Finned X-Wing" to 14,   // X-Wing with extra candidate
    "SASHIMI_X_WING_FISH" to 15, "Sashimi X-Wing" to 15, // Finned X-Wing with missing candidate

    // === HARD (16-22): Coloring, uniqueness, wings, swordfish ===
    "SIMPLE_COLOURING" to 16, "Simple Colouring" to 16,  // Single-digit coloring
    "UNIQUE_RECTANGLE" to 17, "Unique Rectangles" to 17, // Avoid deadly patterns
    "BUG" to 18,                                          // Bivalue Universal Grave
    "Y_WING" to 19, "XY-Wing" to 19,                     // Three bivalue cells forming wing
    "EMPTY_RECTANGLE" to 20, "Empty Rectangles" to 20,   // Box-based single-digit pattern
    "SWORDFISH_FISH" to 21, "Swordfish" to 21,           // 3x3 fish
    "FINNED_SWORDFISH_FISH" to 22, "Finned Swordfish" to 22,

    // === EXPERT (23-28): Advanced wings, chains, 3D Medusa ===
    "XYZ_WING" to 23, "XYZ Wing" to 23,                  // Three-cell wing with shared digit
    "X_CYCLES" to 24, "X-Cycles" to 24,                  // Single-digit nice loops
    "XY_CHAIN" to 25, "XY-Chain" to 25,                  // Chain of bivalue cells
    "WXYZ_WING" to 26, "WXYZ Wing" to 26,                // Four-cell wing pattern
    "JELLYFISH_FISH" to 27, "Jellyfish" to 27,           // 4x4 fish
    "MEDUSA_3D" to 28, "3D Medusa" to 28,                // Multi-digit coloring

    // === EXTREME (29-34): Franken/mutant fish, grouped techniques ===
    "GROUPED_X_CYCLES" to 29, "Grouped X-Cycles" to 29,  // X-Cycles with grouped links
    "FRANKEN_X_WING_FISH" to 30, "Franken X-Wing" to 30, // Fish using boxes
    "FINNED_FRANKEN_X_WING_FISH" to 31,                  // Franken X-Wing with fin
    "FINNED_MUTANT_X_WING_FISH" to 32,                   // Mutant fish with fin
    "FRANKEN_SWORDFISH_FISH" to 33,                      // 3x3 Franken fish
    "FINNED_JELLYFISH_FISH" to 34,                       // Jellyfish with fin

    // === DIABOLICAL (35-38): AIC, ALS, Sue-de-Coq, Forcing Chains ===
    "AIC" to 35, "Alternating Inference Chains" to 35,   // General inference chains
    "ALMOST_LOCKED_SETS" to 36, "Almost Locked Sets" to 36,
    "SUE_DE_COQ" to 37, "Sue-de-Coq" to 37,              // Two-sector disjoint subsets
    "FORCING_CHAINS" to 38, "Forcing Chains" to 38,      // Near trial-and-error
)

// Difficulty categories (aligned with technique priority ranges)
enum class DifficultyCategory(val minPriority: Int, val maxPriority: Int, val targetCount: Int, val minCount: Int, val maxCount: Int) {
    BEGINNER(1, 4, 100, 100, 50000),     // Singles + Intersection (pure scanning)
    EASY(5, 7, 300, 100, 50000),        // Naked Pair/Triple, Hidden Pair
    MEDIUM(8, 10, 500, 250, 50000),     // Hidden Triple, Naked/Hidden Quad
    TOUGH(11, 15, 300, 250, 50000),     // Fish & single-digit patterns
    HARD(16, 22, 300, 100, 50000),      // Coloring, uniqueness, wings, swordfish
    EXPERT(23, 28, 100, 50, 50000),     // Advanced wings, chains, 3D Medusa
    EXTREME(29, 34, 100, 25, 50000),     // Franken/mutant fish, grouped techniques
    DIABOLICAL(35, 50, 25, 10, 50000); // AIC, ALS, Sue-de-Coq, Forcing Chains
    
    val fileName: String get() = name.lowercase() + ".json"
}

@Serializable
data class Puzzle(
    val puzzleId: Int,
    val difficulty: Double,
    val givens: String,
    val solution: String,
    val title: String? = null,
    val techniques: Map<String, Int> = emptyMap()  // Technique name -> count
)

@Serializable
data class PuzzleFile(
    val puzzles: List<Puzzle>
)

@Serializable
data class FindTechniquesFromPuzzleRequest(
    val puzzle: String,
    val basicOnly: Boolean = false
)

@Serializable
data class TechniqueMatchDto(
    val id: String,
    val techniqueName: String
)

@Serializable
data class FindTechniquesResponse(
    val success: Boolean,
    val techniques: Map<String, List<TechniqueMatchDto>> = emptyMap(),
    val error: String? = null
)

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

@Serializable
data class GradePuzzleRequest(
    val puzzle: String
)

@Serializable
data class TechniqueCount(
    val technique: String,
    val count: Int,
    val priority: Int
)

@Serializable
data class GradePuzzleResponse(
    val success: Boolean,
    val solved: Boolean = false,
    val techniques: List<TechniqueCount> = emptyList(),
    val maxDifficulty: Int = 0,
    val totalSteps: Int = 0,
    val searchTimeMs: Long = 0,
    val error: String? = null
)

@Serializable
data class FindTechniquesRequest(
    val grid: GridDto,
    val basicOnly: Boolean = false
)

class PuzzleGenerator(private val apiBaseUrl: String = "http://localhost:8181") {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .writeTimeout(240, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    
    /**
     * Normalize technique priority to 0-100 score
     * Jellyfish (priority 20) should map to 50
     * Max priority is 40 (Forcing Chains)
     */
    private fun priorityToScore(priority: Int): Double {
        // Linear mapping: priority 1 -> ~2.5, priority 40 -> 100
        // Jellyfish (20) -> 50: (20/40) * 100 = 50 ✓
        return (priority.toDouble() / 40.0) * 100.0
    }
    
    /**
     * Get technique priority from technique name
     */
    private fun getTechniquePriority(techniqueName: String): Int {
        return techniquePriority[techniqueName] ?: 100
    }
    
    private fun findTechniques(puzzle: String, basicOnly: Boolean): Map<String, List<TechniqueMatchDto>> {
        return try {
            val requestBody = json.encodeToString(FindTechniquesFromPuzzleRequest.serializer(), FindTechniquesFromPuzzleRequest(puzzle, basicOnly))
            val request = Request.Builder()
                .url("$apiBaseUrl/api/techniques/find-from-puzzle")
                .post(requestBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return emptyMap()
            
            if (!response.isSuccessful) {
                return emptyMap()
            }
            
            val result = json.decodeFromString<FindTechniquesResponse>(responseBody)
            if (result.success) {
                result.techniques
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            println("Error finding techniques: ${e.message}")
            emptyMap()
        }
    }
    
    private fun applyTechnique(puzzle: String, techniqueId: String): Boolean {
        // This is complex - we'd need to convert puzzle to GridDto
        // For now, we'll use a simpler grading approach
        return false
    }
    
    private fun getCurrentState(puzzle: String, techniqueId: String): String {
        // Placeholder
        return puzzle
    }
    
    private fun getSolution(puzzle: String): String? {
        return try {
            val requestBody = json.encodeToString(SolveFromPuzzleRequest.serializer(), SolveFromPuzzleRequest(puzzle))
            val request = Request.Builder()
                .url("$apiBaseUrl/api/puzzle/solve-from-puzzle")
                .post(requestBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null
            
            if (!response.isSuccessful) {
                return null
            }
            
            val result = json.decodeFromString<SolveFromPuzzleResponse>(responseBody)
            result.solution
        } catch (e: Exception) {
            println("Error getting solution: ${e.message}")
            null
        }
    }
    
    // Cache for generated puzzles to avoid duplicates
    private val puzzleCache = mutableSetOf<String>()
    
    // qqwing difficulty levels to cycle through
    private val qqwingDifficulties = listOf("simple", "easy", "intermediate", "expert")
    private var currentDifficultyIndex = 0
    
    /**
     * Get next difficulty level to use (cycles through options)
     */
    private fun getNextDifficulty(): String {
        val difficulty = qqwingDifficulties[currentDifficultyIndex]
        currentDifficultyIndex = (currentDifficultyIndex + 1) % qqwingDifficulties.size
        return difficulty
    }
    
    /**
     * Generate multiple puzzles in a batch using qqwing's batch generation
     * Cycles through difficulty levels to get better distribution
     */
    fun generatePuzzleBatch(count: Int): List<String> {
        // Use current difficulty level (cycles through: simple, easy, intermediate, expert)
        val difficulty = "expert" //getNextDifficulty()
        
        return try {
            val process = ProcessBuilder("qqwing", "--generate", count.toString(), "--one-line", "--difficulty", difficulty)
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            
            if (process.exitValue() == 0) {
                // qqwing with --one-line outputs one puzzle per line
                // But we need to handle the output correctly
                var allPuzzles = mutableListOf<String>()
                
                // Method 1: Split by lines (most common)
                // qqwing uses dots (.) for empty cells, not zeros
                val lines = output.lines().map { it.trim() }.filter { it.isNotEmpty() }
                for (line in lines) {
                    if (line.length == 81) {
                        // Check if it's a valid puzzle (should have some dots/zeros for empty cells)
                        // Accept digits 1-9 and dots (.) for empty cells
                        if (line.all { c -> c in '1'..'9' || c == '.' || c == '0' }) {
                            // A valid puzzle must have at least some empty cells (dots or zeros)
                            if (line.contains('.') || line.contains('0')) {
                                // Convert dots to zeros for consistency
                                val puzzle = line.replace('.', '0')
                                allPuzzles.add(puzzle)
                            }
                        }
                    } else if (line.length > 81) {
                        // Line might contain multiple puzzles (unlikely but possible)
                        val chunks = line.chunked(81)
                        for (chunk in chunks) {
                            if (chunk.length == 81 && chunk.all { c -> c in '1'..'9' || c == '.' || c == '0' }) {
                                if (chunk.contains('.') || chunk.contains('0')) {
                                    val puzzle = chunk.replace('.', '0')
                                    allPuzzles.add(puzzle)
                                }
                            }
                        }
                    }
                }
                
                // Method 2: If no puzzles found, try splitting by any non-digit character
                if (allPuzzles.isEmpty() && output.length >= 81) {
                    // Try to extract puzzles by removing newlines and splitting
                    val cleaned = output.replace("\n", "").replace("\r", "")
                    if (cleaned.length >= 81) {
                        val chunks = cleaned.chunked(81)
                        for (chunk in chunks) {
                            if (chunk.length == 81 && chunk.all { c -> c in '1'..'9' || c == '.' || c == '0' }) {
                                if (chunk.contains('.') || chunk.contains('0')) {
                                    val puzzle = chunk.replace('.', '0')
                                    allPuzzles.add(puzzle)
                                }
                            }
                        }
                    }
                }
                
                // Debug output
                if (allPuzzles.isEmpty()) {
                    println("Warning: No valid puzzles parsed from qqwing output")
                    println("  Output length: ${output.length} chars")
                    println("  First 200 chars: ${output.take(200)}")
                    println("  Last 200 chars: ${output.takeLast(200)}")
                    println("  Line count: ${output.lines().size}")
                    println("  First few lines:")
                    output.lines().take(5).forEachIndexed { i, line ->
                        println("    Line $i (${line.length} chars): ${line.take(100)}")
                    }
                    return emptyList()
                }
                
                // Filter out duplicates (those already in cache)
                val uniquePuzzles = allPuzzles.filter { puzzle ->
                    !puzzleCache.contains(puzzle)
                }
                
                val duplicatesCount = allPuzzles.size - uniquePuzzles.size
                if (duplicatesCount > 0) {
                    println("  Filtered out $duplicatesCount duplicates (cache has ${puzzleCache.size} puzzles)")
                }
                
                // Add new unique puzzles to cache
                puzzleCache.addAll(uniquePuzzles)
                
                if (uniquePuzzles.isNotEmpty()) {
                    println("  Generated ${uniquePuzzles.size} puzzles using qqwing difficulty: $difficulty")
                }
                
                uniquePuzzles
            } else {
                println("Warning: qqwing exited with code ${process.exitValue()}")
                val errorOutput = process.errorStream?.bufferedReader()?.readText() ?: "no error output"
                println("Error output: $errorOutput")
                emptyList()
            }
        } catch (e: Exception) {
            println("Error generating puzzle batch: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Initialize cache with existing puzzles to avoid regenerating them
     */
    fun initializeCache(puzzles: List<String>) {
        puzzleCache.addAll(puzzles)
    }
    
    /**
     * Generate a single puzzle (fallback method)
     */
    private fun generatePuzzle(): String? {
        val batch = generatePuzzleBatch(1)
        return batch.firstOrNull()
    }
    
    /**
     * Load puzzle and get GridDto
     */
    private fun loadPuzzle(puzzle: String): GridDto? {
        return try {
            val requestBody = json.encodeToString(LoadPuzzleRequest.serializer(), LoadPuzzleRequest(puzzle))
            val request = Request.Builder()
                .url("$apiBaseUrl/api/puzzle/load")
                .post(requestBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null
            
            if (!response.isSuccessful) {
                return null
            }
            
            val result = json.decodeFromString<LoadPuzzleResponse>(responseBody)
            result.grid
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Convert GridDto to puzzle string
     */
    private fun gridDtoToPuzzleString(grid: GridDto): String {
        return grid.cells.joinToString("") { cell ->
            if (cell.value != null) cell.value.toString() else "0"
        }
    }
    
    /**
     * Find techniques from GridDto
     */
    private fun findTechniquesFromGrid(grid: GridDto, basicOnly: Boolean): Map<String, List<TechniqueMatchDto>> {
        return try {
            val requestBody = json.encodeToString(FindTechniquesRequest.serializer(), FindTechniquesRequest(grid, basicOnly))
            val request = Request.Builder()
                .url("$apiBaseUrl/api/techniques/find")
                .post(requestBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return emptyMap()
            
            if (!response.isSuccessful) {
                return emptyMap()
            }
            
            val result = json.decodeFromString<FindTechniquesResponse>(responseBody)
            if (result.success) {
                result.techniques
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    /**
     * Apply technique and get updated grid
     */
    private fun applyTechniqueToGrid(grid: GridDto, techniqueId: String): GridDto? {
        return try {
            val requestBody = json.encodeToString(ApplyTechniqueRequest.serializer(), ApplyTechniqueRequest(grid, techniqueId))
            val request = Request.Builder()
                .url("$apiBaseUrl/api/techniques/apply")
                .post(requestBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null
            
            if (!response.isSuccessful) {
                return null
            }
            
            val result = json.decodeFromString<ApplyTechniqueResponse>(responseBody)
            result.grid
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Grade puzzle using the backend's optimized grade endpoint.
     * Returns priority, solution, and technique counts.
     */
    fun gradePuzzle(puzzle: String, debug: Boolean = false): Triple<Int, String?, Map<String, Int>> {
        return try {
            val requestBody = json.encodeToString(GradePuzzleRequest.serializer(), GradePuzzleRequest(puzzle))
            val request = Request.Builder()
                .url("$apiBaseUrl/api/techniques/grade")
                .post(requestBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return Triple(0, null, emptyMap())
            
            if (!response.isSuccessful) {
                if (debug) println("DEBUG: Grade request failed: ${response.code}")
                return Triple(0, null, emptyMap())
            }
            
            val result = json.decodeFromString<GradePuzzleResponse>(responseBody)
            
            if (!result.success) {
                if (debug) println("DEBUG: Grade failed: ${result.error}")
                return Triple(0, null, emptyMap())
            }
            
            if (!result.solved) {
                if (debug) println("DEBUG: Puzzle not fully solved: ${result.error}")
                // Still return the techniques found, but mark as unsolved
                val techniques = result.techniques.associate { it.technique to it.count }
                return Triple(result.maxDifficulty, null, techniques)
            }
            
            // Get solution
            val solution = getSolution(puzzle)
            
            // Convert techniques list to map
            val techniques = result.techniques.associate { it.technique to it.count }
            
            if (debug) {
                println("DEBUG: Graded in ${result.searchTimeMs}ms, maxDifficulty=${result.maxDifficulty}, steps=${result.totalSteps}")
                println("DEBUG: Techniques: $techniques")
            }
            
            Triple(result.maxDifficulty, solution, techniques)
        } catch (e: Exception) {
            if (debug) println("DEBUG: Grade exception: ${e.message}")
            Triple(0, null, emptyMap())
        }
    }
    
    /**
     * Legacy wrapper for compatibility - calls the new gradePuzzle
     */
    fun gradePuzzleSimple(puzzle: String, debug: Boolean = false): Pair<Int, String?> {
        val (priority, solution, _) = gradePuzzle(puzzle, debug)
        return Pair(priority, solution)
    }
}

// Data class for grading results
data class GradingResult(
    val puzzle: String,
    val priority: Int,
    val solution: String?,
    val techniques: Map<String, Int> = emptyMap()  // Technique name -> count
)

fun main(args: Array<String>) = runBlocking {
    val puzzlesDir = File("puzzles")
    if (!puzzlesDir.exists()) {
        puzzlesDir.mkdirs()
    }
    
    val apiUrl = args.getOrNull(0) ?: "http://localhost:8181"
    val inputFile = args.getOrNull(1)?.takeIf { it.isNotBlank() }  // Optional: path to file with puzzle strings
    val parallelism = args.getOrNull(2)?.toIntOrNull() ?: 12  // Default 8 parallel workers
    val generator = PuzzleGenerator(apiUrl)
    val json = Json { ignoreUnknownKeys = true; isLenient = true }
    
    println("Parallel processing with $parallelism workers")
    
    // If input file provided, load puzzles from it
    var inputPuzzles: List<String>? = null
    if (inputFile != null) {
        val file = File(inputFile)
        if (file.exists()) {
            println("Loading puzzles from file: $inputFile")
            inputPuzzles = file.readLines()
                .map { it.trim() }
                .filter { it.length == 81 }
                .map { line ->
                    // Convert dots to zeros if present
                    line.replace('.', '0')
                }
            println("Loaded ${inputPuzzles.size} puzzles from file")
        } else {
            println("ERROR: Input file not found: $inputFile")
            return@runBlocking
        }
    }
    
    // Check if backend is available
    println("Checking backend availability at $apiUrl...")
    try {
        val client = OkHttpClient()
        val request = Request.Builder().url("$apiUrl/health").build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            println("ERROR: Backend not available at $apiUrl")
            return@runBlocking
        }
        println("Backend is available!")
    } catch (e: Exception) {
        println("ERROR: Cannot connect to backend: ${e.message}")
        println("Please start the backend server first.")
        return@runBlocking
    }
    
    // Thread-safe data structures
    val puzzlesByCategory = ConcurrentHashMap<DifficultyCategory, MutableList<Puzzle>>()
    val nextIds = ConcurrentHashMap<DifficultyCategory, AtomicInteger>()
    val fileMutex = Mutex()
    
    // Initialize
    for (category in DifficultyCategory.values()) {
        puzzlesByCategory[category] = mutableListOf()
        nextIds[category] = AtomicInteger(1)
    }
    
    // Load existing puzzles
    val allExistingPuzzles = mutableListOf<String>()
    for (category in DifficultyCategory.values()) {
        val file = File(puzzlesDir, category.fileName)
        if (file.exists()) {
            try {
                val content = file.readText()
                val puzzleFile = Json.decodeFromString<PuzzleFile>(content)
                puzzlesByCategory[category]!!.addAll(puzzleFile.puzzles)
                nextIds[category]!!.set((puzzleFile.puzzles.maxOfOrNull { it.puzzleId } ?: 0) + 1)
                // Collect all existing puzzle strings for cache
                allExistingPuzzles.addAll(puzzleFile.puzzles.map { it.givens })
            } catch (e: Exception) {
                println("Warning: Could not load ${category.fileName}: ${e.message}")
            }
        }
    }
    
    // Initialize cache with existing puzzles to avoid duplicates
    generator.initializeCache(allExistingPuzzles)
    println("Initialized cache with ${allExistingPuzzles.size} existing puzzles")
    
    println("\nCurrent puzzle counts:")
    for (category in DifficultyCategory.values()) {
        val count = puzzlesByCategory[category]!!.size
        val target = category.targetCount
        println("  ${category.name}: $count / $target")
    }
    
    // Counters
    val totalGenerated = AtomicInteger(0)
    val totalProcessed = AtomicInteger(0)
    
    // Dispatcher for parallel processing
    @OptIn(ExperimentalCoroutinesApi::class)
    val dispatcher = Dispatchers.IO.limitedParallelism(parallelism)
    
    if (inputPuzzles != null) {
        // Process input file in parallel batches
        println("\nProcessing ${inputPuzzles.size} puzzles from input file with $parallelism parallel workers...")
        
        val validPuzzles = inputPuzzles.filter { it.contains('0') }
        println("${validPuzzles.size} puzzles have empty cells to solve")
        
        // Process in chunks for better progress reporting
        val chunkSize = parallelism * 2
        val chunks = validPuzzles.chunked(chunkSize)
        
        for ((chunkIndex, chunk) in chunks.withIndex()) {
            // Process chunk in parallel
            val results = chunk.mapIndexed { idx, puzzle ->
                val globalIdx = chunkIndex * chunkSize + idx
                async(dispatcher) {
                    try {
                        // Enable debug for first 3 puzzles to diagnose grading
                        val debug = globalIdx < 3
                        val (priority, solution, techniques) = generator.gradePuzzle(puzzle, debug)
                        GradingResult(puzzle, priority, solution, techniques)
                    } catch (e: Exception) {
                        GradingResult(puzzle, 0, null, emptyMap())
                    }
                }
            }.awaitAll()
            
            // Process results (sequentially for thread safety)
            for (result in results) {
                totalProcessed.incrementAndGet()
                
                if (result.solution == null || result.puzzle == result.solution) {
                    continue
                }
                
                val score = (result.priority.toDouble() / 40.0) * 100.0
                
                // Find matching category
                var matchedCategory: DifficultyCategory? = null
                for (category in DifficultyCategory.values()) {
                    if (result.priority in category.minPriority..category.maxPriority) {
                        val count = puzzlesByCategory[category]!!.size
                        if (count < category.maxCount) {
                            matchedCategory = category
                            break
                        }
                    }
                }
                
                if (matchedCategory != null) {
                    val puzzleId = nextIds[matchedCategory]!!.getAndIncrement()
                    
                    val puzzleObj = Puzzle(
                        puzzleId = puzzleId,
                        difficulty = score,
                        givens = result.puzzle,
                        solution = result.solution,
                        techniques = result.techniques
                    )
                    
                    synchronized(puzzlesByCategory[matchedCategory]!!) {
                        puzzlesByCategory[matchedCategory]!!.add(puzzleObj)
                    }
                    totalGenerated.incrementAndGet()
                    
                    val count = puzzlesByCategory[matchedCategory]!!.size
                    val techSummary = result.techniques.entries.sortedByDescending { it.value }.take(3)
                        .joinToString(", ") { "${it.key}: ${it.value}" }
                    println("${matchedCategory.name} #$puzzleId (priority ${result.priority}, score ${"%.1f".format(score)}) - $count/${matchedCategory.targetCount} [$techSummary]")
                }
            }
            
            // Save progress after each chunk
            fileMutex.withLock {
                for (category in DifficultyCategory.values()) {
                    if (puzzlesByCategory[category]!!.isNotEmpty()) {
                        val file = File(puzzlesDir, category.fileName)
                        val puzzleFile = PuzzleFile(puzzlesByCategory[category]!!.toList())
                        file.writeText(json.encodeToString(PuzzleFile.serializer(), puzzleFile))
                    }
                }
            }
            
            // Progress update
            val processed = totalProcessed.get()
            val generated = totalGenerated.get()
            println("Progress: $processed/${validPuzzles.size} processed, $generated categorized")
        }
        
    } else {
        // qqwing mode - sequential generation, parallel grading
        println("\nGenerating puzzles using qqwing...")
        
        val batchSize = 12
        var attempts = 0
        val maxAttempts = 10000
        
        while (attempts < maxAttempts) {
            // Check if all categories are full
            var allFull = true
            for (category in DifficultyCategory.values()) {
                val count = puzzlesByCategory[category]!!.size
                if (count < category.maxCount) {
                    allFull = false
                    break
                }
            }
            if (allFull) {
                println("\nAll categories are full!")
                break
            }
            
            println("Generating batch of $batchSize puzzles...")
            val batch = generator.generatePuzzleBatch(batchSize)
            
            if (batch.isEmpty()) {
                println("Warning: No puzzles generated, retrying...")
                delay(1000)
                attempts++
                continue
            }
            
            val validBatch = batch.filter { it.contains('0') }
            
            // Grade batch in parallel
            val results = validBatch.map { puzzle ->
                async(dispatcher) {
                    try {
                        val (priority, solution, techniques) = generator.gradePuzzle(puzzle)
                        GradingResult(puzzle, priority, solution, techniques)
                    } catch (e: Exception) {
                        GradingResult(puzzle, 0, null, emptyMap())
                    }
                }
            }.awaitAll()
            
            // Process results
            for (result in results) {
                attempts++
                
                if (result.solution == null || result.puzzle == result.solution) {
                    continue
                }
                
                val score = (result.priority.toDouble() / 40.0) * 100.0
                
                // Find matching category
                var matchedCategory: DifficultyCategory? = null
                for (category in DifficultyCategory.values()) {
                    if (result.priority in category.minPriority..category.maxPriority) {
                        val count = puzzlesByCategory[category]!!.size
                        if (count < category.maxCount) {
                            matchedCategory = category
                            break
                        }
                    }
                }
                
                if (matchedCategory != null) {
                    val puzzleId = nextIds[matchedCategory]!!.getAndIncrement()
                    
                    val puzzleObj = Puzzle(
                        puzzleId = puzzleId,
                        difficulty = score,
                        givens = result.puzzle,
                        solution = result.solution,
                        techniques = result.techniques
                    )
                    
                    synchronized(puzzlesByCategory[matchedCategory]!!) {
                        puzzlesByCategory[matchedCategory]!!.add(puzzleObj)
                    }
                    totalGenerated.incrementAndGet()
                    
                    val count = puzzlesByCategory[matchedCategory]!!.size
                    val techSummary = result.techniques.entries.sortedByDescending { it.value }.take(3)
                        .joinToString(", ") { "${it.key}: ${it.value}" }
                    println("${matchedCategory.name} #$puzzleId (priority ${result.priority}, score ${"%.1f".format(score)}) - $count/${matchedCategory.targetCount} [$techSummary]")
                }
            }
            
            // Save progress after each batch
            fileMutex.withLock {
                for (category in DifficultyCategory.values()) {
                    if (puzzlesByCategory[category]!!.isNotEmpty()) {
                        val file = File(puzzlesDir, category.fileName)
                        val puzzleFile = PuzzleFile(puzzlesByCategory[category]!!.toList())
                        file.writeText(json.encodeToString(PuzzleFile.serializer(), puzzleFile))
                    }
                }
            }
            
            // Progress update
            val generated = totalGenerated.get()
            println("Batch complete - saved $generated total puzzles to disk")
        }
    }
    
    println("\n\nFinal results:")
    for (category in DifficultyCategory.values()) {
        val count = puzzlesByCategory[category]!!.size
        val file = File(puzzlesDir, category.fileName)
        val puzzleFile = PuzzleFile(puzzlesByCategory[category]!!.toList())
        file.writeText(json.encodeToString(PuzzleFile.serializer(), puzzleFile))
        println("  ${category.name}: $count puzzles saved to ${category.fileName}")
    }
    
    println("\nTotal puzzles categorized: ${totalGenerated.get()}")
    println("Total puzzles processed: ${totalProcessed.get()}")
}

