package service.hint.metadata

import i18n.LanguageConfig




// Technique priority mapping - ordered by human difficulty (lower = easier)
private val techniquePriority = mapOf(
    // BEGINNER (1-4): Singles + Intersection
    "NAKED_SINGLE" to 1, "Naked Singles" to 1,
    "HIDDEN_SINGLE" to 2, "Hidden Singles" to 2,
    "POINTING_CANDIDATES" to 3, "Pointing Candidates" to 3, "Pointing Pairs" to 3,
    "CLAIMING_CANDIDATES" to 4, "Claiming Candidates" to 4, "Box/Line Reduction" to 4,
    // EASY (5-7): Basic subsets
    "NAKED_PAIR" to 5, "Naked Pairs" to 5, "Locked Pairs" to 5, "LOCKED_PAIR" to 5, "LOCKED_PAIRS" to 5,
    "NAKED_TRIPLE" to 6, "Naked Triples" to 6, "Locked Triples" to 6,
    "HIDDEN_PAIR" to 7, "Hidden Pairs" to 7,
    // MEDIUM (8-10): Harder subsets
    "HIDDEN_TRIPLE" to 8, "Hidden Triples" to 8,
    "NAKED_QUADRUPLE" to 9, "Naked Quadruples" to 9, "Locked Quadruples" to 9,
    "HIDDEN_QUADRUPLE" to 10, "Hidden Quadruples" to 10,
    // TOUGH (11-15): Fish & single-digit patterns
    "X_WING_FISH" to 11, "X-Wing" to 11,
    "SKYSCRAPER_FISH" to 12, "Skyscraper" to 12,
    "TWO_STRING_KITE_FISH" to 13, "2-String Kite" to 13,
    "FINNED_X_WING_FISH" to 14, "Finned X-Wing" to 14,
    "SASHIMI_X_WING_FISH" to 15, "Sashimi X-Wing" to 15,
    // HARD (16-22): Colouring, uniqueness, wings, swordfish
    "SIMPLE_COLOURING" to 16, "Simple Colouring" to 16, "Simple Coloring" to 16,
    "UNIQUE_RECTANGLE" to 17, "Unique Rectangles" to 17,
    "BUG" to 18, "Bivalue Universal Grave" to 18,
    "Y_WING" to 19, "XY-Wing" to 19, "XY_WING" to 19,
    "EMPTY_RECTANGLE" to 20, "Empty Rectangles" to 20, "Empty Rectangle" to 20,
    "SWORDFISH_FISH" to 21, "Swordfish" to 21,
    "FINNED_SWORDFISH_FISH" to 22, "Finned Swordfish" to 22,
    // EXPERT (23-28): Advanced wings, chains, 3D Medusa
    "XYZ_WING" to 23, "XYZ Wing" to 23, "XYZ-Wing" to 23,
    "X_CYCLES" to 24, "X-Cycles" to 24, "X-Cycle" to 24,
    "XY_CHAIN" to 25, "XY-Chain" to 25,
    "XY_CHAINS_TypeONE" to 25,
    "HIDDEN_XY_TYPEONE" to 25,
    "WXYZ_WING" to 26, "WXYZ Wing" to 26, "WXYZ-Wing" to 26,
    "UVWXYZ_WING" to 27,
    "JELLYFISH_FISH" to 27, "Jellyfish" to 27,
    "MEDUSA_3D" to 28, "3D Medusa" to 28, "THREE_D_MEDUSA" to 28,
    // EXTREME (29-34): Franken/mutant fish, grouped techniques
    "GROUPED_X_CYCLES" to 29, "Grouped X-Cycles" to 29,
    "FRANKEN_X_WING_FISH" to 30, "Franken X-Wing" to 30,
    "FINNED_FRANKEN_X_WING_FISH" to 31,
    "FINNED_MUTANT_X_WING_FISH" to 32,
    "FRANKEN_SWORDFISH_FISH" to 33,
    "FINNED_JELLYFISH_FISH" to 34,
    // DIABOLICAL (35-42): AIC, ALS, Sue-de-Coq, Forcing Chains, Rings
    "AIC" to 35, "Alternating Inference Chains" to 35, "AIC Type 2" to 35,
    "AIC_TYPE_ONE" to 35, "AIC_TYPE_TWO" to 35, "ALTERNATING_INFERENCE_CHAINS" to 35,
    "ALMOST_LOCKED_SETS" to 36, "Almost Locked Sets" to 36,
    "ALS_XY" to 36, "ALS-XY" to 36,
    "ALS_XZ" to 36, "ALS-XZ" to 36,
    "AIC_WITH_ALS" to 36,
    "SUE_DE_COQ" to 37, "Sue-de-Coq" to 37,
    "FORCING_CHAINS" to 38, "Forcing Chains" to 38,
    "NISHIO" to 39, "Nishio" to 39,
    "RING" to 40, "Ring" to 40,
    "L_WING" to 41, "L-Wing" to 41, "L(3)-Wing" to 41,
    "L3_WING" to 41,
    "W_WING" to 19, "W-Wing" to 19,
)

private fun normalizeTechniqueKey(name: String): String {
    return name.uppercase()
        .replace("-", "_")
        .replace(" ", "_")
        .replace("/", "_")
        .replace("(", "_")
        .replace(")", "_")
        .replace("+", "_")
        .replace(":", "_")
}

fun describeTechnique(name: String): String? {
    val normalizedKey = normalizeTechniqueKey(name)
    // Try to get from language file first
    val langKey = "backend.techniques.$normalizedKey"
    val langString = LanguageConfig.getString(langKey)
    
    // If we got a valid string (not the fallback key), return it
    if (langString != "[$langKey]") {
        return langString
    }
    
    // Fallback to null if not found
    return null
}

fun missingDescriptionsForPriority(): List<String> {
    val result = mutableListOf<String>()
    for (key in techniquePriority.keys.distinct()) {
        val langString = LanguageConfig.getString("backend.techniques.${normalizeTechniqueKey(key)}")
        if (langString == "[backend.techniques.${normalizeTechniqueKey(key)}]") {
            // If it's a machine name, check if its display name resolves
            val enumValue = try { sudoku.solvingtechClassifier.Technique.valueOf(key) } catch (e: Exception) { null }
            if (enumValue != null) {
                val displayKey = normalizeTechniqueKey(enumValue.getName())
                val displayString = LanguageConfig.getString("backend.techniques.$displayKey")
                if (displayString == "[backend.techniques.$displayKey]") {
                    result.add(key)
                }
            } else {
                result.add(key)
            }
        }
    }
    return result
}

fun getTechniquePriority(techniqueName: String): Int {
    return techniquePriority[techniqueName]
        ?: techniquePriority[techniqueName.uppercase()]
        ?: techniquePriority[techniqueName.replace("_", " ")]
        ?: 100 // Unknown techniques get high priority (harder)
}


