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

    // English fallback cache — a standalone copy of en.json, loaded once. Kept
    // separate from `languageData` so looking it up never clobbers the active
    // locale (see `enFallback()`).
    private var enFallbackData: JsonObject? = null
    private var enFallbackLoaded = false

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
     * Supports nested keys separated by dots.
     *
     * Resolution order:
     *  1. The currently loaded locale.
     *  2. English (`en.json`) — so any key that exists only in the English file
     *     still renders correctly under every other locale.
     *  3. `"[key]"` — only for keys missing from *both* locales (truly broken).
     */
    fun getString(key: String): String {
        if (languageData == null) {
            // Try to load default language if not loaded
            loadLanguage("en")
        }

        // 1. Current locale
        languageData?.let { locale ->
            resolveKeyInObject(locale, key)?.let { return it }
        }

        // 2. English fallback
        enFallback()?.let { en ->
            resolveKeyInObject(en, key)?.let { return it }
        }

        // 3. Missing from both locales
        return "[$key]"
    }

    /**
     * Navigate a dot-separated key path through a JSON object tree.
     * Returns the leaf string value, or null if any segment is absent or the
     * path passes through a non-object node.
     */
    private fun resolveKeyInObject(root: JsonObject, key: String): String? {
        val keys = key.split(".")
        var current: JsonObject? = root

        for (i in keys.indices) {
            val element = current?.get(keys[i]) ?: return null

            if (i == keys.size - 1) {
                // Last key - return the string value
                return element.toString().trim('"')
            } else {
                // Navigate deeper
                current = element as? JsonObject ?: return null
            }
        }

        return null
    }

    /**
     * Load (once) and cache the English language file as a standalone object.
     *
     * Deliberately does *not* call `loadLanguage("en")` — that would clobber
     * `currentLanguage`. Instead it reads `/languages/en.json` into a separate
     * `JsonObject` and resolves against it. Cached so repeated lookups are cheap.
     */
    private fun enFallback(): JsonObject? {
        if (enFallbackLoaded) return enFallbackData
        enFallbackLoaded = true

        val content = ResourceLoader.loadResource("/languages/en.json") ?: return null
        return try {
            enFallbackData = json.parseToJsonElement(content).jsonObject
            enFallbackData
        } catch (e: Exception) {
            println("Failed to parse EN fallback: ${e.message}")
            null
        }
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
