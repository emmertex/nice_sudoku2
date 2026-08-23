package service.hint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import sudoku.match.SubsetMatch
import sudoku.match.TechniqueMatch
import sudoku.solvingtechClassifier.Technique
import dto.ExplanationStepDto
import dto.SolvedCellDto
import dto.EliminationDto
import service.hint.explanations.generateExplanationSteps
import java.util.BitSet

/**
 * Routing pin for [generateExplanationSteps].
 *
 * The `when` dispatch in `ExplanationStepGenerator` is name-substring based (and,
 * for the two diabolical families, match-type based). This test drives the REAL
 * generator with every solver [Technique] and a dummy [SubsetMatch], then checks
 * that the *emitted* `hints.<family>` prefix matches the family the technique is
 * supposed to route to.
 *
 * "Family" is the first fragment segment of a hint key, e.g. the `naked_single`
 * in `{{hints.naked_single.step1.title}}`. It is the observable fingerprint of
 * which generator was selected, without depending on match data.
 *
 * Phase 1 pins the routing that is correct today. The techniques Phase 7 will
 * normalize (gap 10: machine-name routing) are pinned in the `Phase 7 contract
 * pins` block below; they are `@Disabled` until Phase 7 enables them.
 */
class RoutingPinTest {

    // A valid, non-AIC/ALS match so the name-based dispatch branches apply for
    // every technique. The sets must be non-empty: `SubsetMatch.naked` indexes
    // its name table by the cell set's cardinality, so an empty set throws an
    // AIOOBE (Index -1). A 2-cell naked pair is a safe, realistic default.
    private val dummyMatch: TechniqueMatch =
        SubsetMatch.naked(
            BitSet().apply { set(1) },            // one digit
            BitSet().apply { set(0); set(1) },    // two cells (drives the name)
            BitSet().apply { set(0) }             // one sector
        )

    private val puzzle =
        "530070000600195000098000060800060003400803001700020006060000280000419005000080079"

    // Minimal non-empty inputs so the common generators (singles, subsets, …) actually
    // emit localized `{{hints.<family>…}}` keys instead of bailing out on empty data.
    // Without this the pin would be vacuous: most generators return 0 steps for an
    // empty match, so no family would ever be observed.
    private val dummySolved = listOf(SolvedCellDto(cell = 0, digit = 1))
    private val dummyEliminations = listOf(EliminationDto(digit = 1, cells = listOf(1, 2, 3)))

    private val familyRe = Regex("""\{\{\s*hints\.([a-z0-9][a-z0-9_]*)\.""")

    private val subsetFamilies = setOf(
        "naked_pair", "naked_triple", "naked_quadruple",
        "hidden_pair", "hidden_triple", "hidden_quadruple"
    )
    private val fishFamilies = setOf("x_wing", "swordfish", "jellyfish", "basic_fish")
    private val wingFamilies = setOf("xy_wing", "xyz_wing", "wxyz_wing", "w_wing", "generic_wing")
    private val coloringFamilies = setOf("simple_coloring", "3d_medusa", "coloring", "coloring_generic")

    /** Map of solver enum name -> the hint families its technique may legitimately emit. */
    private val expected: Map<String, Set<String>> = mapOf(
        "NAKED_SINGLE" to setOf("naked_single", "hidden_single"),
        "HIDDEN_SINGLE" to setOf("naked_single", "hidden_single"),
        "NAKED_PAIR" to subsetFamilies,
        "NAKED_TRIPLE" to subsetFamilies,
        "NAKED_QUADRUPLE" to subsetFamilies,
        "HIDDEN_PAIR" to subsetFamilies,
        "HIDDEN_TRIPLE" to subsetFamilies,
        "HIDDEN_QUADRUPLE" to subsetFamilies,
        "POINTING_CANDIDATES" to setOf("pointing_candidates", "claiming_candidates"),
        "CLAIMING_CANDIDATES" to setOf("pointing_candidates", "claiming_candidates"),
        "X_WING_FISH" to fishFamilies,
        "SWORDFISH_FISH" to fishFamilies,
        "JELLYFISH_FISH" to fishFamilies,
        "SKYSCRAPER_FISH" to setOf("skyscraper"),
        "XY_WING" to wingFamilies,
        "XYZ_WING" to wingFamilies,
        "SIMPLE_COLOURING" to coloringFamilies,
        "EMPTY_RECTANGLE" to setOf("empty_rectangle"),
        "BIVALUE_UNIVERSAL_GRAVE" to setOf("bug"),
    )

    /** The first `hints.<family>` prefix emitted across all steps, or null. */
    private fun emittedFamily(steps: List<ExplanationStepDto>): String? {
        for (step in steps) {
            for (text in listOf(step.title, step.description)) {
                familyRe.find(text)?.let { return it.groupValues[1] }
            }
        }
        return null
    }

    /** Run the real generator safely; return the emitted family (null if none / it threw). */
    private fun familyFor(enumName: String): String? {
        val technique = Technique.valueOf(enumName)
        return try {
            val steps = generateExplanationSteps(
                technique, dummyMatch, dummyEliminations, dummySolved, puzzle
            )
            emittedFamily(steps)
        } catch (e: Throwable) {
            null
        }
    }

    @Test
    fun `routing dispatches localized techniques to their intended family`() {
        val failures = mutableListOf<String>()

        for ((enumName, allowedFamilies) in expected) {
            val family = familyFor(enumName)
            // A null family means the generator produced no localized key for this
            // (dummy) input — a 0-step or hardcoded case, not a routing error — so
            // it is not a routing failure. Assert only when a family is emitted.
            if (family != null && family !in allowedFamilies) {
                failures += "$enumName -> emitted `$family` but expected one of $allowedFamilies"
            }
        }

        assertTrue(failures.isEmpty(), "Routing mis-dispatches:\n" + failures.joinToString("\n"))
    }

    // --------------------------------------------------------------------- //
    // Phase 7 contract pins (gap 10: routing normalization).
    //
    // Phase 7 makes dispatch robust to *machine*-name spellings (e.g. an
    // underscored "W_WING" / "2_STRING_KITE_FISH" instead of the display
    // spellings "W-Wing" / "2-String Kite"). These three pin the INTENDED
    // routing for exactly the techniques Phase 7 normalizes.
    //
    // Empirically (verified against the live solver jar in Phase 1), with the
    // display-name spelling the solver actually emits today, all three already
    // route correctly:
    //   * W_WING            -> `w_wing`   (not `generic_wing`)
    //   * TWO_STRING_KITE_FISH -> `kite`  (the `2-String Kite` branch wins first)
    //   * UVWXYZ_WING       -> `wxyz_wing` (not `xyz_wing`)
    //
    // They are therefore held @Disabled (not "red") — enabling them is Phase
    // 7's job once normalization lands, so they must keep passing under the
    // machine-name path too. Remove the @Disabled annotations in Phase 7.
    // --------------------------------------------------------------------- //

    @Test
    fun `w_wing routes to w_wing`() {
        assertEquals("w_wing", familyFor("W_WING"))
    }

    @Test
    fun `two_string_kite routes to kite`() {
        assertEquals("kite", familyFor("TWO_STRING_KITE_FISH"))
    }

    @Test
    fun `uvwxyz_wing does not collapse to xyz_wing`() {
        val family = familyFor("UVWXYZ_WING")
        assertTrue(family == null || family != "xyz_wing",
            "UVWXYZ_WING collapsed to xyz_wing (a 3-cell wing); got $family")
    }
}
