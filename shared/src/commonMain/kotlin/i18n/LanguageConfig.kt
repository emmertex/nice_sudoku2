package i18n

/**
 * Language configuration and convenience functions
 */
object LanguageConfig {
    /**
     * Current language code (hardcoded to English for now)
     */
    var currentLanguage: String = "en"
        private set

    init {
        // Load default language on initialization
        LanguageManager.loadLanguage(currentLanguage)
    }

    /**
     * Get a localized string by key
     * Convenience function that delegates to LanguageManager
     */
    fun getString(key: String): String {
        return LanguageManager.getString(key)
    }

    /**
     * Switch to a different language (for future use)
     */
    fun setLanguage(language: String): Boolean {
        val success = LanguageManager.loadLanguage(language)
        if (success) {
            currentLanguage = language
        }
        return success
    }
}

