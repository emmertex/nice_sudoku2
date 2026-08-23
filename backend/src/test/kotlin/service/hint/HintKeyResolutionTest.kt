package service.hint

import dto.EliminationDto
import i18n.HintStringInterpolation
import i18n.LanguageConfig
import service.hint.explanations.generateUniqueRectangleSteps
import sudoku.match.SubsetMatch
import java.util.BitSet
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Phase 2 regression net.
 *
 * The keys that used to render as the literal missing-key marker `[...]` —
 * the per-ALS "Identify …" titles (gap 1) and the Unique-Rectangle elimination
 * templates for types 3–6 (gap 2) — must now resolve through the real
 * [LanguageManager] + English-fallback pipeline.
 *
 * New hint keys are authored in `en.json` only (per the plan); every other
 * locale must fall back to English (Phase 1) and never show the bracketed
 * marker. The last two tests drive the real [generateUniqueRectangleSteps]
 * generator to confirm a type-3 match emits the type-3 template and a match
 * with no parseable type falls back to the generic key.
 */
class HintKeyResolutionTest {

    /** A rendered hint string is broken when it is the literal `[key]` marker. */
    private fun isMissingMarker(s: String) = s.startsWith("[") && s.endsWith("]")

    private fun render(locale: String, keyWithVars: String): String {
        assertTrue(LanguageConfig.setLanguage(locale), "could not load locale '$locale'")
        return HintStringInterpolation.interpolate(keyWithVars)
    }

    private fun assertResolves(locale: String, keyWithVars: String) {
        val out = render(locale, keyWithVars)
        assertTrue(!isMissingMarker(out),
            "[$locale] $keyWithVars rendered as the missing-key marker: $out")
    }

    // A match that declares no `type` field, so the generator falls back to
    // parsing the type from the technique name ("… Type N").
    private val dummyMatch = SubsetMatch.naked(
        BitSet().apply { set(1) },            // one digit
        BitSet().apply { set(0); set(1) },    // two cells
        BitSet().apply { set(0) }             // one sector
    )
    private val dummyEliminations = listOf(EliminationDto(digit = 7, cells = listOf(1, 2, 3)))

    @Test
    fun `gap1 als per-set title resolves in en`() {
        // Per-ALS titles now use the fixed hints.als.step2.title key.
        assertResolves("en", "{{hints.als.step2.title|alsName=ALS A}}")
        assertResolves("en", "{{hints.als.step2.title|alsName=ALS B}}")
    }

    @Test
    fun `gap2 ur elimination types 3 to 6 resolve in en`() {
        // Written as explicit concrete keys (not `$t` interpolation) so the
        // guardrail's literal `{{hints…}}` scan sees real leaf keys, not a
        // truncated template fragment.
        assertResolves("en", "{{hints.unique_rectangle_elim_type3.step1.description|digits=7|cells=R1C1}}")
        assertResolves("en", "{{hints.unique_rectangle_elim_type4.step1.description|digits=7|cells=R1C1}}")
        assertResolves("en", "{{hints.unique_rectangle_elim_type5.step1.description|digits=7|cells=R1C1}}")
        assertResolves("en", "{{hints.unique_rectangle_elim_type6.step1.description|digits=7|cells=R1C1}}")
    }

    @Test
    fun `gap2 ur type-0 falls back to the generic key`() {
        assertResolves("en", "{{hints.unique_rectangle_generic.step1.description}}")
    }

    @Test
    fun `gap1 and gap2 new en-only keys resolve under non-en locales via the english fallback`() {
        // Phase 2 checkpoint: "no [...] from gaps 1/2 in any locale".
        // The new keys exist only in en.json, so every other locale must render
        // them via the English fallback (Phase 1) — never the bracketed marker.
        for (locale in listOf("de", "es", "fr")) {
            assertResolves(locale, "{{hints.als.step2.title|alsName=ALS A}}")
            assertResolves(locale, "{{hints.unique_rectangle_elim_type3.step1.description|digits=7|cells=R1C1}}")
            assertResolves(locale, "{{hints.unique_rectangle_elim_type4.step1.description|digits=7|cells=R1C1}}")
            assertResolves(locale, "{{hints.unique_rectangle_elim_type5.step1.description|digits=7|cells=R1C1}}")
            assertResolves(locale, "{{hints.unique_rectangle_elim_type6.step1.description|digits=7|cells=R1C1}}")
            assertResolves(locale, "{{hints.unique_rectangle_generic.step1.description}}")
        }
    }

    @Test
    fun `ur type-3 match emits the type-3 elimination template that resolves`() {
        val steps = generateUniqueRectangleSteps("Unique Rectangle Type 3", dummyMatch, dummyEliminations)
        val step3 = steps.firstOrNull { it.stepNumber == 3 }
        assertTrue(step3 != null, "expected an elimination step for a UR with eliminations")
        assertTrue(step3!!.description.contains("unique_rectangle_elim_type3"),
            "expected the type-3 elimination template, got: ${step3.description}")
        val rendered = HintStringInterpolation.interpolate(step3.description)
        assertTrue(!isMissingMarker(rendered), "UR type-3 elimination rendered as a missing-key marker: $rendered")
    }

    @Test
    fun `ur with no parseable type falls back to the generic key and still resolves`() {
        val steps = generateUniqueRectangleSteps("Unique Rectangle", dummyMatch, dummyEliminations)
        val step3 = steps.firstOrNull { it.stepNumber == 3 }
        assertTrue(step3 != null, "expected an elimination step for a UR with eliminations")
        assertTrue(step3!!.description.contains("unique_rectangle_generic"),
            "expected the generic fallback template, got: ${step3.description}")
        val rendered = HintStringInterpolation.interpolate(step3.description)
        assertTrue(!isMissingMarker(rendered), "UR type-0 fallback rendered as a missing-key marker: $rendered")
    }
}
