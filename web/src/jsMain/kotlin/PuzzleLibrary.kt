import domain.DifficultyCategory
import domain.PuzzleDefinition
import kotlinx.browser.window
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.fetch.Response
import kotlin.js.Promise

/**
 * JSON format for puzzle files
 */
@Serializable
private data class PuzzleJson(
    val puzzleId: Int,
    val difficulty: Float,
    val givens: String,
    val solution: String,
    val quality: Float? = null,
    val techniques: Map<String, Int>? = null,
    val title: String? = null,
    val url: String? = null
)

@Serializable
private data class PuzzleFileJson(
    val puzzles: List<PuzzleJson>
)

/**
 * Puzzle library that loads puzzles from bundled JSON files
 */
object PuzzleLibrary {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    // Cache for loaded puzzles per category
    private val puzzleCache = mutableMapOf<DifficultyCategory, List<PuzzleDefinition>>()
    
    // Loading state per category
    private val loadingState = mutableMapOf<DifficultyCategory, Boolean>()
    
    // Callbacks waiting for puzzles to load
    private val loadCallbacks = mutableMapOf<DifficultyCategory, MutableList<(List<PuzzleDefinition>) -> Unit>>()
    
    /**
     * Map category to JSON filename
     */
    private fun getFilenameForCategory(category: DifficultyCategory): String? {
        return when (category) {
            DifficultyCategory.BEGINNER -> "beginner.json"
            DifficultyCategory.EASY -> "easy.json"
            DifficultyCategory.MEDIUM -> "medium.json"
            DifficultyCategory.TOUGH -> "tough.json"
            DifficultyCategory.HARD -> "hard.json"
            DifficultyCategory.EXPERT -> "expert.json"
            DifficultyCategory.DIABOLICAL -> "diabolical.json"
            DifficultyCategory.CUSTOM -> null // Custom puzzles are stored separately
        }
    }
    
    /**
     * Get puzzles for a category synchronously (returns cached or empty list)
     * Use getPuzzlesForCategoryAsync for guaranteed results
     */
    fun getPuzzlesForCategory(category: DifficultyCategory): List<PuzzleDefinition> {
        if (category == DifficultyCategory.CUSTOM) {
            return GameStateManager.loadCustomPuzzles()
        }
        
        // Return cached if available
        puzzleCache[category]?.let { return it }
        
        // Trigger async load if not already loading
        if (loadingState[category] != true) {
            loadPuzzlesAsync(category)
        }
        
        return emptyList()
    }
    
    /**
     * Get puzzles for a category with callback when loaded
     */
    fun getPuzzlesForCategoryAsync(category: DifficultyCategory, onLoaded: (List<PuzzleDefinition>) -> Unit) {
        if (category == DifficultyCategory.CUSTOM) {
            onLoaded(GameStateManager.loadCustomPuzzles())
            return
        }
        
        // Return cached immediately if available
        puzzleCache[category]?.let {
            onLoaded(it)
            return
        }
        
        // Add to callbacks
        loadCallbacks.getOrPut(category) { mutableListOf() }.add(onLoaded)
        
        // Trigger async load if not already loading
        if (loadingState[category] != true) {
            loadPuzzlesAsync(category)
        }
    }
    
    /**
     * Load puzzles asynchronously from JSON file using Promise
     */
    private fun loadPuzzlesAsync(category: DifficultyCategory) {
        val filename = getFilenameForCategory(category) ?: return
        
        loadingState[category] = true
        
        val fetchPromise = window.asDynamic().fetch("puzzles/$filename") as Promise<Response>
        fetchPromise.then { response: Response ->
            if (response.ok) {
                response.text().then { text: dynamic ->
                    try {
                        val textStr = text as String
                        val puzzleFile = json.decodeFromString<PuzzleFileJson>(textStr)
                        
                        val puzzles = puzzleFile.puzzles.map { p ->
                            PuzzleDefinition(
                                id = "${category.name.lowercase()}_${p.puzzleId}",
                                puzzleString = p.givens,
                                difficulty = p.difficulty,
                                category = category,
                                solution = p.solution,
                                quality = p.quality,
                                techniques = p.techniques,
                                title = p.title,
                                url = p.url
                            )
                        }
                        
                        puzzleCache[category] = puzzles
                        loadingState[category] = false
                        println("Loaded ${puzzles.size} ${category.displayName} puzzles")
                        
                        // Notify all waiting callbacks
                        loadCallbacks[category]?.forEach { callback ->
                            callback(puzzles)
                        }
                        loadCallbacks.remove(category)
                    } catch (e: Exception) {
                        println("Error parsing puzzles for $category: ${e.message}")
                        puzzleCache[category] = emptyList()
                        loadingState[category] = false
                        loadCallbacks[category]?.forEach { it(emptyList()) }
                        loadCallbacks.remove(category)
                    }
                }
            } else {
                println("Failed to load puzzles for $category: ${response.status}")
                puzzleCache[category] = emptyList()
                loadingState[category] = false
                loadCallbacks[category]?.forEach { it(emptyList()) }
                loadCallbacks.remove(category)
            }
        }.catch { error: dynamic ->
            println("Error loading puzzles for $category: $error")
            puzzleCache[category] = emptyList()
            loadingState[category] = false
            loadCallbacks[category]?.forEach { it(emptyList()) }
            loadCallbacks.remove(category)
        }
    }
    
    /**
     * Check if puzzles are loaded for a category
     */
    fun isPuzzlesLoaded(category: DifficultyCategory): Boolean {
        return category == DifficultyCategory.CUSTOM || puzzleCache.containsKey(category)
    }
    
    /**
     * Check if puzzles are currently loading for a category
     */
    fun isPuzzlesLoading(category: DifficultyCategory): Boolean {
        return loadingState[category] == true
    }
    
    /**
     * Preload all puzzle categories
     */
    fun preloadAll() {
        DifficultyCategory.entries.forEach { category ->
            if (category != DifficultyCategory.CUSTOM && !isPuzzlesLoaded(category)) {
                loadPuzzlesAsync(category)
            }
        }
    }
    
    fun getPuzzle(category: DifficultyCategory, index: Int): PuzzleDefinition? {
        return getPuzzlesForCategory(category).getOrNull(index)
    }
    
    fun getRandomPuzzle(category: DifficultyCategory): PuzzleDefinition? {
        val list = getPuzzlesForCategory(category)
        return list.randomOrNull()
    }
    
    /**
     * Get random puzzle with callback (ensures puzzles are loaded first)
     */
    fun getRandomPuzzleAsync(category: DifficultyCategory, onResult: (PuzzleDefinition?) -> Unit) {
        getPuzzlesForCategoryAsync(category) { puzzles ->
            onResult(puzzles.randomOrNull())
        }
    }
    
    fun getAllPuzzles(): List<PuzzleDefinition> {
        return puzzleCache.values.flatten() + GameStateManager.loadCustomPuzzles()
    }
    
    /**
     * Get count of puzzles for a category (returns 0 if not loaded yet)
     */
    fun getPuzzleCount(category: DifficultyCategory): Int {
        return if (category == DifficultyCategory.CUSTOM) {
            GameStateManager.loadCustomPuzzles().size
        } else {
            puzzleCache[category]?.size ?: 0
        }
    }
}
