package i18n

/**
 * Language configuration and convenience functions
 */
object LanguageConfig {
    /**
     * Current language code
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
     * Switch to a different language
     * Returns true if successful
     */
    fun setLanguage(language: String): Boolean {
        val success = LanguageManager.loadLanguage(language)
        if (success) {
            currentLanguage = language
        }
        return success
    }
    
    /**
     * Get the native name of the current language
     */
    fun getCurrentLanguageName(): String {
        return LanguageManager.getCurrentLanguageName()
    }
    
    /**
     * Get list of available languages with their native names
     */
    fun getAvailableLanguages(): List<LanguageInfo> {
        return LanguageManager.getAvailableLanguages()
    }
}

