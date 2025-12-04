#!/usr/bin/env kotlin

@file:DependsOn("com.squareup.okhttp3:okhttp:4.12.0")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.*
import java.io.File
import java.util.concurrent.TimeUnit

// Technique priority mapping (from GameEngine.kt)
val techniquePriority = mapOf(
    "NAKED_SINGLE" to 1, "Naked Singles" to 1,
    "HIDDEN_SINGLE" to 1, "Hidden Singles" to 1,
    "POINTING_CANDIDATES" to 2, "Pointing Candidates" to 2, "Pointing Pairs" to 2,
    "CLAIMING_CANDIDATES" to 2, "Claiming Candidates" to 2, "Box/Line Reduction" to 2,
    "NAKED_PAIR" to 3, "Naked Pairs" to 3,
    "NAKED_TRIPLE" to 3, "Naked Triples" to 3,
    "HIDDEN_PAIR" to 4, "Hidden Pairs" to 4,
    "HIDDEN_TRIPLE" to 4, "Hidden Triples" to 4,
    "NAKED_QUADRUPLE" to 5, "Naked Quadruples" to 5,
    "HIDDEN_QUADRUPLE" to 5, "Hidden Quadruples" to 5,
    "X_WING_FISH" to 8, "X-Wing" to 8,
    "SKYSCRAPER_FISH" to 8, "Skyscraper" to 8,
    "TWO_STRING_KITE_FISH" to 8, "2-String Kite" to 8,
    "FINNED_X_WING_FISH" to 9, "Finned X-Wing" to 9,
    "SASHIMI_X_WING_FISH" to 9, "Sashimi X-Wing" to 9,
    "SIMPLE_COLOURING" to 10, "Simple Colouring" to 10,
    "UNIQUE_RECTANGLE" to 11, "Unique Rectangles" to 11,
    "BUG" to 11,
    "Y_WING" to 12, "XY-Wing" to 12,
    "EMPTY_RECTANGLE" to 13, "Empty Rectangles" to 13,
    "SWORDFISH_FISH" to 14, "Swordfish" to 14,
    "FINNED_SWORDFISH_FISH" to 15, "Finned Swordfish" to 15,
    "XYZ_WING" to 16, "XYZ Wing" to 16,
    "X_CYCLES" to 17, "X-Cycles" to 17,
    "XY_CHAIN" to 18, "XY-Chain" to 18,
    "WXYZ_WING" to 19, "WXYZ Wing" to 19,
    "JELLYFISH_FISH" to 20, "Jellyfish" to 20,
    "MEDUSA_3D" to 21, "3D Medusa" to 21,
    "GROUPED_X_CYCLES" to 27, "Grouped X-Cycles" to 27,
    "FRANKEN_X_WING_FISH" to 28, "Franken X-Wing" to 28,
    "FINNED_FRANKEN_X_WING_FISH" to 29,
    "FINNED_MUTANT_X_WING_FISH" to 29,
    "FRANKEN_SWORDFISH_FISH" to 29,
    "FINNED_JELLYFISH_FISH" to 30,
    "AIC" to 31, "Alternating Inference Chains" to 31,
    "ALMOST_LOCKED_SETS" to 32, "Almost Locked Sets" to 32,
    "SUE_DE_COQ" to 33, "Sue-de-Coq" to 33,
    "FORCING_CHAINS" to 40, "Forcing Chains" to 40,
)

// Difficulty categories
enum class DifficultyCategory(val minPriority: Int, val maxPriority: Int, val targetCount: Int, val minCount: Int, val maxCount: Int) {
    BASIC(1, 2, 50, 50, 50),
    EASY(3, 4, 250, 250, 250),
    TOUGH(5, 9, 250, 250, 250),
    HARD(10, 16, 100, 100, 100),
    DIABOLICAL(17, 25, 50, 25, 100),
    EXTREME(26, 40, 30, 10, 50);  // Note: User said 16-40, but 26-40 avoids overlap with Diabolical
    
    val fileName: String get() = name.lowercase() + ".json"
}

@Serializable
data class Puzzle(
    val puzzleId: Int,
    val difficulty: Double,
    val givens: String,
    val solution: String,
    val title: String? = null
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
data class FindTechniquesRequest(
    val grid: GridDto,
    val basicOnly: Boolean = false
)

class PuzzleGenerator(private val apiBaseUrl: String = "http://localhost:8181") {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
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
            val requestBody = json.encodeToString(FindTechniquesFromPuzzleRequest(puzzle, basicOnly))
            val request = Request.Builder()
                .url("$apiBaseUrl/api/techniques/find-from-puzzle")
                .post(RequestBody.create(MediaType.get("application/json"), requestBody))
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body()?.string() ?: return emptyMap()
            
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
            val requestBody = json.encodeToString(SolveFromPuzzleRequest(puzzle))
            val request = Request.Builder()
                .url("$apiBaseUrl/api/puzzle/solve-from-puzzle")
                .post(RequestBody.create(MediaType.get("application/json"), requestBody))
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body()?.string() ?: return null
            
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
    
    /**
     * Generate puzzle using qqwing
     */
    fun generatePuzzle(): String? {
        return try {
            val process = ProcessBuilder("qqwing", "--generate", "1", "--one-line")
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            
            if (process.exitValue() == 0 && output.length == 81) {
                output
            } else {
                null
            }
        } catch (e: Exception) {
            println("Error generating puzzle: ${e.message}")
            null
        }
    }
    
    /**
     * Load puzzle and get GridDto
     */
    private fun loadPuzzle(puzzle: String): GridDto? {
        return try {
            val requestBody = json.encodeToString(LoadPuzzleRequest(puzzle))
            val request = Request.Builder()
                .url("$apiBaseUrl/api/puzzle/load")
                .post(RequestBody.create(MediaType.get("application/json"), requestBody))
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body()?.string() ?: return null
            
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
            val requestBody = json.encodeToString(FindTechniquesRequest(grid, basicOnly))
            val request = Request.Builder()
                .url("$apiBaseUrl/api/techniques/find")
                .post(RequestBody.create(MediaType.get("application/json"), requestBody))
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body()?.string() ?: return emptyMap()
            
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
            val requestBody = json.encodeToString(ApplyTechniqueRequest(grid, techniqueId))
            val request = Request.Builder()
                .url("$apiBaseUrl/api/techniques/apply")
                .post(RequestBody.create(MediaType.get("application/json"), requestBody))
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body()?.string() ?: return null
            
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
     * Grade puzzle by solving step-by-step and tracking max technique priority
     */
    fun gradePuzzleSimple(puzzle: String): Pair<Int, String?> {
        val solution = getSolution(puzzle) ?: return Pair(0, null)
        
        // Load initial grid
        var currentGrid = loadPuzzle(puzzle) ?: return Pair(1, solution)
        var maxPriority = 0
        var iterations = 0
        val maxIterations = 200
        
        var previousPuzzleString = gridDtoToPuzzleString(currentGrid)
        
        while (iterations < maxIterations) {
            iterations++
            
            // Check if solved
            if (currentGrid.isComplete) {
                break
            }
            
            // Try basic techniques first
            var techniques = findTechniquesFromGrid(currentGrid, basicOnly = true)
            if (techniques.isEmpty()) {
                // Try all techniques
                techniques = findTechniquesFromGrid(currentGrid, basicOnly = false)
            }
            
            if (techniques.isEmpty()) {
                // No techniques found - might need brute force
                break
            }
            
            // Find technique with highest priority
            var bestMatch: TechniqueMatchDto? = null
            var bestPriority = 0
            
            for ((_, matches) in techniques) {
                for (match in matches) {
                    val priority = getTechniquePriority(match.techniqueName)
                    if (priority > bestPriority) {
                        bestPriority = priority
                        bestMatch = match
                    }
                }
            }
            
            if (bestMatch == null) {
                break
            }
            
            maxPriority = maxOf(maxPriority, bestPriority)
            
            // Apply the technique
            val updatedGrid = applyTechniqueToGrid(currentGrid, bestMatch.id)
            if (updatedGrid == null) {
                // Technique application failed
                break
            }
            
            // Check if we made progress
            val newPuzzleString = gridDtoToPuzzleString(updatedGrid)
            if (newPuzzleString == previousPuzzleString) {
                // No progress made
                break
            }
            
            currentGrid = updatedGrid
            previousPuzzleString = newPuzzleString
        }
        
        // Default to basic if nothing found
        if (maxPriority == 0) {
            maxPriority = 1
        }
        
        return Pair(maxPriority, solution)
    }
}

fun main(args: Array<String>) {
    val puzzlesDir = File("puzzles")
    if (!puzzlesDir.exists()) {
        puzzlesDir.mkdirs()
    }
    
    val apiUrl = args.getOrNull(0) ?: "http://localhost:8181"
    val generator = PuzzleGenerator(apiUrl)
    
    // Check if backend is available
    println("Checking backend availability at $apiUrl...")
    try {
        val client = OkHttpClient()
        val request = Request.Builder().url("$apiUrl/health").build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            println("ERROR: Backend not available at $apiUrl")
            return
        }
        println("Backend is available!")
    } catch (e: Exception) {
        println("ERROR: Cannot connect to backend: ${e.message}")
        println("Please start the backend server first.")
        return
    }
    
    // Track puzzles by category
    val puzzlesByCategory = DifficultyCategory.values().associateWith { mutableListOf<Puzzle>() }
    val nextIds = DifficultyCategory.values().associateWith { 1 }.toMutableMap()
    
    // Load existing puzzles
    for (category in DifficultyCategory.values()) {
        val file = File(puzzlesDir, category.fileName)
        if (file.exists()) {
            try {
                val content = file.readText()
                val puzzleFile = Json.decodeFromString<PuzzleFile>(content)
                puzzlesByCategory[category]!!.addAll(puzzleFile.puzzles)
                nextIds[category] = (puzzleFile.puzzles.maxOfOrNull { it.puzzleId } ?: 0) + 1
            } catch (e: Exception) {
                println("Warning: Could not load ${category.fileName}: ${e.message}")
            }
        }
    }
    
    println("\nCurrent puzzle counts:")
    for (category in DifficultyCategory.values()) {
        val count = puzzlesByCategory[category]!!.size
        val target = category.targetCount
        println("  ${category.name}: $count / $target")
    }
    
    // Generate puzzles
    var totalGenerated = 0
    var attempts = 0
    val maxAttempts = 10000
    
    println("\nGenerating puzzles...")
    
    while (attempts < maxAttempts) {
        attempts++
        
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
        
        // Generate puzzle
        val puzzle = generator.generatePuzzle() ?: continue
        
        // Grade puzzle
        val (priority, solution) = generator.gradePuzzleSimple(puzzle)
        if (solution == null) {
            continue
        }
        
        val score = (priority.toDouble() / 40.0) * 100.0
        
        // Find matching category
        var matchedCategory: DifficultyCategory? = null
        for (category in DifficultyCategory.values()) {
            if (priority in category.minPriority..category.maxPriority) {
                val count = puzzlesByCategory[category]!!.size
                if (count < category.maxCount) {
                    matchedCategory = category
                    break
                }
            }
        }
        
        if (matchedCategory != null) {
            val puzzleId = nextIds[matchedCategory]!!
            nextIds[matchedCategory] = puzzleId + 1
            
            val puzzleObj = Puzzle(
                puzzleId = puzzleId,
                difficulty = score,
                givens = puzzle,
                solution = solution
            )
            
            puzzlesByCategory[matchedCategory]!!.add(puzzleObj)
            totalGenerated++
            
            val count = puzzlesByCategory[matchedCategory]!!.size
            println("Generated ${matchedCategory.name} puzzle #$puzzleId (priority $priority, score ${"%.1f".format(score)}) - $count/${matchedCategory.targetCount}")
            
            // Save after each puzzle
            val file = File(puzzlesDir, matchedCategory!!.fileName)
            val puzzleFile = PuzzleFile(puzzlesByCategory[matchedCategory]!!.toList())
            file.writeText(json.encodeToString(PuzzleFile.serializer(), puzzleFile))
        } else {
            if (attempts % 100 == 0) {
                print(".")
            }
        }
        
        // Progress update
        if (attempts % 1000 == 0) {
            println("\nProgress: $attempts attempts, $totalGenerated puzzles generated")
            for (category in DifficultyCategory.values()) {
                val count = puzzlesByCategory[category]!!.size
                println("  ${category.name}: $count / ${category.targetCount}")
            }
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
    
    println("\nTotal puzzles generated: $totalGenerated")
    println("Total attempts: $attempts")
}

