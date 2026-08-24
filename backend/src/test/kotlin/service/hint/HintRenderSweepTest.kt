package service.hint

import dto.EliminationDto
import dto.SolvedCellDto
import i18n.HintStringInterpolation
import i18n.LanguageConfig
import service.hint.explanations.generateExplanationSteps
import sudoku.match.SubsetMatch
import sudoku.match.TechniqueMatch
import sudoku.solvingtechClassifier.Technique
import java.util.BitSet
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Phase 10 validation pass: drives the REAL [generateExplanationSteps] with every
 * production-reachable solver variant in en + de and asserts no fabricated values,
 * no missing keys, no literal placeholders, and correct step counts.
 */
class HintRenderSweepTest {
    // We use cell 5 and 6 so that cell 0 (R1C1) is never a legitimate part of the input.
    // If "R1C1" shows up in the output, it is definitively a fabricated fallback.
    private val dummyMatch: TechniqueMatch =
        SubsetMatch.naked(
            BitSet().apply { set(1) },
            BitSet().apply {
                set(5)
                set(6)
            },
            BitSet().apply { set(1) },
        )

    private val puzzle =
        "530070000600195000098000060800060003400803001700020006060000280000419005000080079"

    // Align the solved and eliminated digits so that step generators (like single)
    // find matching peers in the elimination list.
    private val dummySolved = listOf(SolvedCellDto(cell = 5, digit = 1))
    private val dummyEliminations = listOf(EliminationDto(digit = 1, cells = listOf(6, 7, 8, 14, 23)))

    // slot, enum, minSteps, maxSteps
    private val variants: List<Array<Any>> =
        listOf(
            arrayOf(1, "NAKED_SINGLE", 1, 2),
            arrayOf(2, "HIDDEN_SINGLE", 1, 2),
            arrayOf(3, "POINTING_CANDIDATES", 1, 2),
            arrayOf(4, "CLAIMING_CANDIDATES", 1, 2),
            arrayOf(5, "NAKED_PAIR", 1, 3),
            arrayOf(5, "LOCKED_NAKED_PAIR", 1, 3),
            arrayOf(6, "NAKED_TRIPLE", 1, 3),
            arrayOf(6, "LOCKED_NAKED_TRIPLE", 1, 3),
            arrayOf(7, "HIDDEN_PAIR", 1, 3),
            arrayOf(8, "HIDDEN_TRIPLE", 1, 3),
            arrayOf(9, "NAKED_QUADRUPLE", 1, 3),
            arrayOf(10, "HIDDEN_QUADRUPLE", 1, 3),
            arrayOf(11, "X_WING_FISH", 1, 3),
            arrayOf(12, "SKYSCRAPER_FISH", 2, 3),
            arrayOf(13, "TWO_STRING_KITE_FISH", 1, 3), // Phase 6 suppression can reduce kite to 2
            arrayOf(14, "FINNED_X_WING_FISH", 1, 3),
            arrayOf(15, "SASHIMI_X_WING_FISH", 1, 3),
            arrayOf(16, "SIMPLE_COLOURING", 1, 3),
            arrayOf(17, "UNIQUE_RECTANGLE", 2, 3),
            arrayOf(18, "BIVALUE_UNIVERSAL_GRAVE", 2, 3),
            arrayOf(19, "XY_WING", 1, 3),
            arrayOf(19, "W_WING", 1, 3),
            arrayOf(20, "EMPTY_RECTANGLE", 1, 3),
            arrayOf(21, "SWORDFISH_FISH", 1, 3),
            arrayOf(22, "FINNED_SWORDFISH_FISH", 1, 3),
            arrayOf(23, "XYZ_WING", 1, 3),
            arrayOf(24, "X_CYCLE", 1, 3),
            arrayOf(25, "XY_CHAINS", 1, 3),
            arrayOf(25, "XY_CHAINS_TypeONE", 1, 3),
            arrayOf(25, "XY_CHAINS_TypeTWO", 1, 3),
            arrayOf(26, "WXYZ_WING", 1, 3),
            arrayOf(27, "UVWXYZ_WING", 1, 3),
            arrayOf(27, "JELLYFISH_FISH", 1, 3),
            arrayOf(28, "THREE_D_MEDUSA", 1, 3),
            arrayOf(30, "FRANKEN_X_WING_FISH", 1, 3),
            arrayOf(31, "FINNED_FRANKEN_X_WING_FISH", 1, 3),
            arrayOf(32, "FINNED_MUTANT_X_WING_FISH", 1, 3),
            arrayOf(33, "FRANKEN_SWORDFISH_FISH", 1, 3),
            arrayOf(34, "FINNED_JELLYFISH_FISH", 1, 3),
            arrayOf(35, "AIC_TYPE_ONE", 1, 3),
            arrayOf(35, "AIC_TYPE_TWO", 1, 3),
            arrayOf(35, "AIC_TYPE_LOOP", 1, 3),
            arrayOf(35, "ALTERNATING_INFERENCE_CHAINS", 1, 3),
            arrayOf(36, "ALMOST_LOCKED_SETS", 1, 10),
            arrayOf(36, "AIC_WITH_ALS", 1, 10),
            arrayOf(36, "HIDDEN_XY_TYPEONE", 1, 10),
            arrayOf(36, "HIDDEN_XY_TYPETWO", 1, 10),
            arrayOf(37, "SUE_DE_COQ", 1, 3),
            arrayOf(38, "FORCING_NETS", 1, 3),
            arrayOf(39, "NISHIO", 1, 3),
            arrayOf(40, "XYZ_RING", 1, 3),
            arrayOf(41, "L3_WING", 1, 3),
            arrayOf(41, "L1_WING", 1, 3),
            arrayOf(41, "L2_WING", 1, 3),
        )

    private fun render(
        text: String,
        locale: String,
    ): String {
        LanguageConfig.setLanguage(locale)
        return HintStringInterpolation.interpolate(text)
    }

    private val missingKeyRe = Regex("\\[[a-zA-Z][\\w.]*\\]")

    @Test
    fun `render sweep verifies output quality`() {
        val failures = mutableListOf<String>()

        for (locale in listOf("en", "de")) {
            for (row in variants) {
                val slot = row[0] as Int
                val enumName = row[1] as String
                val minSteps = row[2] as Int
                val maxSteps = row[3] as Int

                val technique = Technique.valueOf(enumName)
                val scenarios =
                    listOf(
                        Triple("data", dummyEliminations, dummySolved),
                        Triple("empty", emptyList<EliminationDto>(), emptyList<SolvedCellDto>()),
                    )

                for ((scenario, eliminations, solved) in scenarios) {
                    try {
                        val steps = generateExplanationSteps(technique, dummyMatch, eliminations, solved, puzzle)

                        // 0 steps is always valid (frontend uses hints.common fallback)
                        if (steps.isNotEmpty() && (steps.size < minSteps || steps.size > maxSteps)) {
                            failures += "[$locale] $enumName $scenario: Expected 0 or $minSteps-$maxSteps steps, got ${steps.size}"
                        }

                        for (s in steps) {
                            val t = render(s.title, locale)
                            val d = render(s.description, locale)
                            for (text in listOf(t, d)) {
                                if (missingKeyRe.containsMatchIn(text)) failures += "[$locale] $enumName $scenario: MISSING-KEY in '$text'"
                                if (text.contains("{{")) failures += "[$locale] $enumName $scenario: PLACEHOLDER leak in '$text'"
                                if (text.contains("R1C1")) failures += "[$locale] $enumName $scenario: FAB-R1C1 in '$text'"
                                if (text.contains("digit 0") ||
                                    text.contains("Ziffer 0")
                                ) {
                                    failures += "[$locale] $enumName $scenario: FAB-DIGIT0 in '$text'"
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        failures += "[$locale] $enumName $scenario: THREW ${e::class.simpleName}: ${e.message}"
                    }
                }
            }
        }

        assertTrue(failures.isEmpty(), "Render sweep found defects:\n" + failures.joinToString("\n"))
    }
}
