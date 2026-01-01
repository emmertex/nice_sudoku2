package i18n

/**
 * Interpolates hint strings containing language keys and variable substitutions.
 * 
 * Format: "{{key|var1=value1|var2=value2}}"
 * 
 * Example: "{{hints.naked_single.step1.title|cell=R1C1|digit=5}}"
 * 
 * If the string doesn't start with "{{", it's returned as-is (backwards compatibility)
 */
object HintStringInterpolation {
    
    /**
     * Interpolate a hint string that may contain language keys and variables
     */
    fun interpolate(input: String): String {
        // Check if this is a key-based string
        if (!input.startsWith("{{") || !input.endsWith("}}")) {
            // Not a key-based string, return as-is
            return input
        }
        
        // Extract the content between {{ and }}
        val content = input.substring(2, input.length - 2)
        
        // Split by | to get key and variables
        val parts = content.split("|")
        if (parts.isEmpty()) return input
        
        val key = parts[0]
        
        // Parse variables into a map
        val variables = mutableMapOf<String, String>()
        for (i in 1 until parts.size) {
            val varPart = parts[i]
            val eqIndex = varPart.indexOf('=')
            if (eqIndex > 0) {
                val varName = varPart.substring(0, eqIndex).trim()
                val varValue = varPart.substring(eqIndex + 1).trim()
                variables[varName] = varValue
            }
        }
        
        // Get the base string from language manager
        val baseString = LanguageManager.getString(key)
        
        // If the key wasn't found (returns [key]), return it as-is
        if (baseString.startsWith("[") && baseString.endsWith("]")) {
            return baseString
        }
        
        // Interpolate variables into the string
        return interpolateVariables(baseString, variables)
    }
    
    /**
     * Replace {{variable}} placeholders in a string with actual values
     */
    private fun interpolateVariables(template: String, variables: Map<String, String>): String {
        var result = template
        
        for ((varName, varValue) in variables) {
            // Replace {{varName}} with varValue
            result = result.replace("{{$varName}}", varValue)
        }
        
        return result
    }
    
    /**
     * Check if a string is a hint key (starts with {{)
     */
    fun isHintKey(input: String): Boolean {
        return input.startsWith("{{") && input.endsWith("}}")
    }
}

