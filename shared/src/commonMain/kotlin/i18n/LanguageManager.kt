package i18n

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Language data structure
 */
@Serializable
private data class LanguageData(
    val ui: JsonObject? = null,
    val backend: JsonObject? = null,
    val domain: JsonObject? = null
)

/**
 * Manages loading and accessing language strings from JSON files
 */
object LanguageManager {
    private val json = Json { ignoreUnknownKeys = true }
    private var languageData: JsonObject? = null
    private var currentLanguage: String = "en"

    /**
     * Load language file
     */
    fun loadLanguage(language: String = "en"): Boolean {
        currentLanguage = language
        val path = "languages/$language.json"
        val content = ResourceLoader.loadResource(path)
        
        return if (content != null) {
            try {
                languageData = json.parseToJsonElement(content).jsonObject
                true
            } catch (e: Exception) {
                println("Failed to parse language file: ${e.message}")
                false
            }
        } else {
            println("Failed to load language file: $path")
            false
        }
    }

    /**
     * Get a string by key path (e.g., "ui.game.solved")
     * Supports nested keys separated by dots
     */
    fun getString(key: String): String {
        if (languageData == null) {
            // Try to load default language if not loaded
            loadLanguage("en")
        }
        
        val keys = key.split(".")
        var current: JsonObject? = languageData
        
        for (i in keys.indices) {
            val keyPart = keys[i]
            val element = current?.get(keyPart)
            
            if (element == null) {
                // Return the key if not found
                return "[$key]"
            }
            
            if (i == keys.size - 1) {
                // Last key - return the string value
                return element.toString().trim('"')
            } else {
                // Navigate deeper
                current = element.jsonObject
            }
        }
        
        return "[$key]"
    }

    /**
     * Get current language code
     */
    fun getCurrentLanguage(): String = currentLanguage
}

