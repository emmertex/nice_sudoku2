package i18n

/**
 * Platform-specific resource loading interface
 */
internal expect object ResourceLoader {
    /**
     * Load a resource file as a string
     * @param path Path to the resource file (e.g., "languages/en.json")
     * @return The file contents as a string, or null if not found
     */
    fun loadResource(path: String): String?
}

