package service.hint.helpers

/**
 * Helper to build language keys with variable substitution for hint explanations.
 * Format: {{key|var1=value1|var2=value2}}
 */
object LanguageKeyBuilder {
    
    /**
     * Build a language key with optional variables
     * 
     * Example: key("hints.naked_single.step1.title", "cell" to "R1C1", "digit" to "5")
     * Returns: "{{hints.naked_single.step1.title|cell=R1C1|digit=5}}"
     */
    fun key(keyPath: String, vararg variables: Pair<String, Any>): String {
        if (variables.isEmpty()) {
            return "{{$keyPath}}"
        }
        
        val varString = variables.joinToString("|") { (name, value) ->
            "$name=$value"
        }
        
        return "{{$keyPath|$varString}}"
    }
    
    /**
     * Build a common key for hints
     */
    fun hintKey(techniqueName: String, stepNum: Int, field: String, vararg variables: Pair<String, Any>): String {
        val techKey = normalizeTechniqueName(techniqueName)
        return key("hints.$techKey.step$stepNum.$field", *variables)
    }
    
    /**
     * Build a common hint key
     */
    fun commonKey(field: String, vararg variables: Pair<String, Any>): String {
        return key("hints.common.$field", *variables)
    }
    
    /**
     * Normalize technique name to a key format (lowercase, underscores)
     */
    fun normalizeTechniqueName(name: String): String {
        return name.lowercase()
            .replace("-", "_")
            .replace(" ", "_")
            .replace("/", "_")
            .replace("(", "")
            .replace(")", "")
    }
}

