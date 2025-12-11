package i18n

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
 * Represents an available language
 */
data class LanguageInfo(
    val code: String,
    val name: String
)

/**
 * Manages loading and accessing language strings from JSON files
 */
object LanguageManager {
    private val json = Json { ignoreUnknownKeys = true }
    private var languageData: JsonObject? = null
    private var currentLanguage: String = "en"
    
    /**
     * List of supported language codes
     * When adding a new language, add its code here
     */
    private val supportedLanguages = listOf("en", "es", "de", "zh", "hi", "fr", "ar", "bn", "ru", "pt", "ur")

    /**
     * Load language file
     */
    fun loadLanguage(language: String = "en"): Boolean {
        currentLanguage = language
        // Use absolute path to avoid issues with URL-based language prefixes
        val path = "/languages/$language.json"
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
    
    /**
     * Get the native name of the current language (from languageName field)
     */
    fun getCurrentLanguageName(): String {
        val langName = languageData?.get("languageName")
        return langName?.jsonPrimitive?.content ?: currentLanguage.uppercase()
    }
    
    /**
     * Get list of available languages with their native names
     */
    fun getAvailableLanguages(): List<LanguageInfo> {
        return supportedLanguages.mapNotNull { code ->
            val name = getLanguageName(code)
            if (name != null) {
                LanguageInfo(code, name)
            } else {
                // Fallback to code if can't load
                LanguageInfo(code, code.uppercase())
            }
        }
    }
    
    /**
     * Get the native name for a specific language code
     */
    private fun getLanguageName(code: String): String? {
        // If it's the current language, use cached data
        if (code == currentLanguage && languageData != null) {
            return languageData?.get("languageName")?.jsonPrimitive?.content
        }
        
        // Otherwise, load and parse just the languageName field
        // Use absolute path to avoid issues with URL-based language prefixes
        val path = "/languages/$code.json"
        val content = ResourceLoader.loadResource(path) ?: return null
        
        return try {
            val parsed = json.parseToJsonElement(content).jsonObject
            parsed["languageName"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }
}

