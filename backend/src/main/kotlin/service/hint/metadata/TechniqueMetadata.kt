package service.hint.metadata


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
    // HARD (16-22): Coloring, uniqueness, wings, swordfish
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
    "WXYZ_WING" to 26, "WXYZ Wing" to 26, "WXYZ-Wing" to 26,
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
    "ALMOST_LOCKED_SETS" to 36, "Almost Locked Sets" to 36,
    "ALS_XY" to 36, "ALS-XY" to 36,
    "ALS_XZ" to 36, "ALS-XZ" to 36,
    "SUE_DE_COQ" to 37, "Sue-de-Coq" to 37,
    "FORCING_CHAINS" to 38, "Forcing Chains" to 38,
    "NISHIO" to 39, "Nishio" to 39,
    "RING" to 40, "Ring" to 40,
    "L_WING" to 41, "L-Wing" to 41, "L(3)-Wing" to 41,
    "W_WING" to 19, "W-Wing" to 19,
)

private val techniqueDescriptions = mapOf(
    // Singles / basics
    "NAKED_SINGLE" to "Only one candidate fits in this cell, so place it.",
    "NAKED_SINGLES" to "Only one candidate fits in this cell, so place it.",
    "HIDDEN_SINGLE" to "A digit appears in only one cell of a house, forcing its placement.",
    "HIDDEN_SINGLES" to "A digit appears in only one cell of a house, forcing its placement.",
    "POINTING_CANDIDATES" to "A digit is locked in one line of a box, so it can be removed from that line outside the box.",
    "POINTING_PAIRS" to "A digit is locked in one line of a box, so it can be removed from that line outside the box.",
    "CLAIMING_CANDIDATES" to "A digit is locked in one box of a line, so it can be removed from that box outside the line.",
    "BOX_LINE_REDUCTION" to "A digit is locked in one box of a line, so it can be removed from that box outside the line.",
    // Subsets
    "NAKED_PAIR" to "Two cells in a house contain exactly the same two candidates; they claim those digits.",
    "NAKED_PAIRS" to "Two cells in a house contain exactly the same two candidates; they claim those digits.",
    "LOCKED_PAIR" to "Two cells in a house contain exactly the same two candidates; they claim those digits.",
    "LOCKED_PAIRS" to "Two cells in a house contain exactly the same two candidates; they claim those digits.",
    "NAKED_TRIPLE" to "Three cells in a house contain exactly the same three candidates; they claim those digits.",
    "LOCKED_TRIPLES" to "Three cells in a house contain exactly the same three candidates; they claim those digits.",
    "NAKED_TRIPLES" to "Three cells in a house contain exactly the same three candidates; they claim those digits.",
    "NAKED_QUADRUPLE" to "Four cells in a house contain exactly the same four candidates; they claim those digits.",
    "LOCKED_QUADRUPLES" to "Four cells in a house contain exactly the same four candidates; they claim those digits.",
    "NAKED_QUADRUPLES" to "Four cells in a house contain exactly the same four candidates; they claim those digits.",
    "HIDDEN_PAIR" to "Two digits can only appear in two cells of a house; other candidates in those cells are removed.",
    "HIDDEN_PAIRS" to "Two digits can only appear in two cells of a house; other candidates in those cells are removed.",
    "HIDDEN_TRIPLE" to "Three digits can only appear in three cells of a house; other candidates in those cells are removed.",
    "HIDDEN_TRIPLES" to "Three digits can only appear in three cells of a house; other candidates in those cells are removed.",
    "HIDDEN_QUADRUPLE" to "Four digits can only appear in four cells of a house; other candidates in those cells are removed.",
    "HIDDEN_QUADRUPLES" to "Four digits can only appear in four cells of a house; other candidates in those cells are removed.",
    // Fish and single-digit patterns
    "X_WING_FISH" to "Rows and columns form an X-Wing so a digit can be eliminated from the covering lines.",
    "X_WING" to "Rows and columns form an X-Wing so a digit can be eliminated from the covering lines.",
    "SKYSCRAPER_FISH" to "Two strong links on a digit form a skyscraper, allowing eliminations in the shared columns/rows.",
    "SKYSCRAPER" to "Two strong links on a digit form a skyscraper, allowing eliminations in the shared columns/rows.",
    "TWO_STRING_KITE_FISH" to "A two-string kite uses 2 strong links, with a weak link in the same box, to eliminate a candidate at the intersection between the two strong links.",
    "2_STRING_KITE" to "A two-string kite uses 2 strong links, with a weak link in the same box, to eliminate a candidate at the intersection between the two strong links.",
    "2_STRING_KITE_FISH" to "A two-string kite uses 2 strong links, with a weak link in the same box, to eliminate a candidate at the intersection between the two strong links.",
    "FINNED_X_WING_FISH" to "An X-Wing with a fin; cells seeing the fin can lose that digit.",
    "FINNED_X_WING" to "An X-Wing with a fin; cells seeing the fin can lose that digit.",
    "SASHIMI_X_WING_FISH" to "A sashimi X-Wing has a fin plus a missing base; cells seeing the fin can be eliminated.",
    "SASHIMI_X_WING" to "A sashimi X-Wing has a fin plus a missing base; cells seeing the fin can be eliminated.",
    "SWORDFISH_FISH" to "Three lines align candidates in three columns/rows, allowing eliminations (swordfish).",
    "SWORDFISH" to "Three lines align candidates in three columns/rows, allowing eliminations (swordfish).",
    "FINNED_SWORDFISH_FISH" to "A swordfish with a fin; cells seeing the fin lose that digit.",
    "FINNED_SWORDFISH" to "A swordfish with a fin; cells seeing the fin lose that digit.",
    "JELLYFISH_FISH" to "Four lines align candidates in four columns/rows, allowing eliminations (jellyfish).",
    "JELLYFISH" to "Four lines align candidates in four columns/rows, allowing eliminations (jellyfish).",
    "FINNED_JELLYFISH_FISH" to "A jellyfish with a fin; cells seeing the fin lose that digit.",
    "FINNED_JELLYFISH" to "A jellyfish with a fin; cells seeing the fin lose that digit.",
    "FRANKEN_X_WING_FISH" to "A Franken fish mixes boxes and lines to form the base/cover, enabling eliminations.",
    "FRANKEN_X_WING" to "A Franken fish mixes boxes and lines to form the base/cover, enabling eliminations.",
    "FINNED_FRANKEN_X_WING_FISH" to "A Franken fish with a fin; cells seeing the fin lose that digit.",
    "FINNED_FRANKEN_X_WING" to "A Franken fish with a fin; cells seeing the fin lose that digit.",
    "FINNED_MUTANT_X_WING_FISH" to "A mutant fish with a fin; eliminations come from cells seeing the fin.",
    "FINNED_MUTANT_X_WING" to "A mutant fish with a fin; eliminations come from cells seeing the fin.",
    "FRANKEN_SWORDFISH_FISH" to "A box/line swordfish variant enabling eliminations on the cover lines.",
    "FRANKEN_SWORDFISH" to "A box/line swordfish variant enabling eliminations on the cover lines.",
    "GROUPED_X_CYCLES" to "Grouped X-Cycles alternate strong and weak links on one digit to prove eliminations.",
    // Coloring, uniqueness, wings
    "SIMPLE_COLOURING" to "Color one digit into two sets of strong links; a contradiction color is eliminated.",
    "SIMPLE_COLORING" to "Color one digit into two sets of strong links; a contradiction color is eliminated.",
    "MEDUSA_3D" to "3D Medusa colors multiple digits; contradictions eliminate candidates or place values.",
    "3D_MEDUSA" to "3D Medusa colors multiple digits; contradictions eliminate candidates or place values.",
    "THREE_D_MEDUSA" to "3D Medusa colors multiple digits; contradictions eliminate candidates or place values.",
    "UNIQUE_RECTANGLE" to "Prevent a deadly pattern by forcing or eliminating around a four-cell rectangle.",
    "UNIQUE_RECTANGLES" to "Prevent a deadly pattern by forcing or eliminating around a four-cell rectangle.",
    "BUG" to "Bivalue Universal Grave: only one candidate breaks the stalemate; place or eliminate accordingly.",
    "BIVALUE_UNIVERSAL_GRAVE" to "Bivalue Universal Grave: only one candidate breaks the stalemate; place or eliminate accordingly.",
    "Y_WING" to "XY-Wing (hinge + two pincers) eliminates a shared candidate seen by both pincers.",
    "XY_WING" to "XY-Wing (hinge + two pincers) eliminates a shared candidate seen by both pincers.",
    "XYZ_WING" to "XYZ-Wing (triad with XYZ) removes Z from cells seeing all three cells.",
    "WXYZ_WING" to "WXYZ-Wing removes the shared candidate from peers of all four cells.",
    "W_WING" to "W-Wing links two matching bivalue cells through a strong link, forcing eliminations.",
    "L_3__WING" to "L-Wing with 3 cells links candidates to force eliminations.",
    "L_WING" to "L-Wing links candidates in an L-shaped pattern to force eliminations.",
    "EMPTY_RECTANGLE" to "An empty rectangle forces a conjugate pair interaction to eliminate a candidate.",
    "EMPTY_RECTANGLES" to "An empty rectangle forces a conjugate pair interaction to eliminate a candidate.",
    // Chains / advanced
    "X_CYCLES" to "X-Cycles alternate strong/weak links on one digit; contradictions remove candidates.",
    "X_CYCLE" to "X-Cycles alternate strong/weak links on one digit; contradictions remove candidates.",
    "XY_CHAIN" to "XY-Chains link bivalue cells; the target digit is eliminated where both ends see.",
    "AIC" to "Alternating Inference Chain proves eliminations via alternating strong/weak links.",
    "AIC_TYPE_2" to "Alternating Inference Chain (Type 2) proves eliminations via alternating strong/weak links.",
    "ALTERNATING_INFERENCE_CHAINS" to "Alternating Inference Chain proves eliminations via alternating strong/weak links.",
    "FORCING_CHAINS" to "Forcing chains explore both outcomes and keep the deduction common to all paths.",
    "RING" to "A ring structure of strong and weak links forces eliminations at junction points.",
    // ALS / Sue / Nishio
    "ALMOST_LOCKED_SETS" to "Almost Locked Sets connect via restricted candidates to force eliminations.",
    "ALS_XY" to "ALS-XY links two almost locked sets through a shared restricted candidate.",
    "ALS_XZ" to "ALS-XZ links two almost locked sets through shared restricted candidates.",
    "SUE_DE_COQ" to "Sue-de-Coq divides a box-line overlap into disjoint digit sets, forcing eliminations.",
    "NISHIO" to "Nishio assumes a digit placement and prunes branches that lead to contradiction.",
    // Advanced AIC and chain variants
    "AIC___ALS" to "Alternating Inference Chain combined with Almost Locked Sets to force eliminations.",
    "AIC_TYPE_1" to "Alternating Inference Chain (Type 1) uses strong/weak link alternation to prove eliminations.",
    "XY_CHAINS__TYPE_1" to "XY-Chain (Type 1) links bivalue cells; the target digit is eliminated where both ends see.",
    "ALS_CHAIN" to "ALS-Chain links multiple almost locked sets in sequence to force eliminations.",
    // Wing variants
    "SPLIT_WING" to "Split-Wing uses a split pattern of candidates to force eliminations.",
    "STRONG_WING__WINDMILL_" to "Strong-Wing (Windmill) uses a windmill pattern of strong links to eliminate candidates.",
    "INVERTED_H_3__WING" to "Inverted H(3)-Wing uses an inverted H-shaped pattern with 3 cells to force eliminations.",
    "INVERTED_M_3__WING" to "Inverted M(3)-Wing uses an inverted M-shaped pattern with 3 cells to force eliminations.",
    "INVERTED_L_3__WINGS" to "Inverted L(3)-Wing uses an inverted L-shaped pattern with 3 cells to force eliminations.",
    "UVWXYZ_WING" to "UVWXYZ-Wing (6-cell wing) removes the shared candidate from peers of all six cells.",
    // Hidden chain variants
    "HIDDEN_XY__TYPE_1" to "Hidden XY (Type 1) uses hidden bivalue cells in an XY-chain pattern to eliminate candidates.",
)

private fun normalizeTechniqueKey(name: String): String {
    return name.uppercase()
        .replace("-", "_")
        .replace(" ", "_")
        .replace("/", "_")
        .replace("(", "_")
        .replace(")", "_")
}

fun describeTechnique(name: String): String? {
    return techniqueDescriptions[normalizeTechniqueKey(name)]
}

fun missingDescriptionsForPriority(): List<String> {
    return techniquePriority.keys
        .map { normalizeTechniqueKey(it) }
        .distinct()
        .filterNot { techniqueDescriptions.containsKey(it) }
}

fun getTechniquePriority(techniqueName: String): Int {
    return techniquePriority[techniqueName]
        ?: techniquePriority[techniqueName.uppercase()]
        ?: techniquePriority[techniqueName.replace("_", " ")]
        ?: 100 // Unknown techniques get high priority (harder)
}


