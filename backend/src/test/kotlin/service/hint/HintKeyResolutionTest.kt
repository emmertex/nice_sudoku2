package service.hint

import dto.EliminationDto
import i18n.HintStringInterpolation
import i18n.LanguageConfig
import service.hint.explanations.generateUniqueRectangleSteps
import sudoku.match.SubsetMatch
import java.util.BitSet
import kotlin.test.Test
import kotlin.test.assertTrue
import service.hint.helpers.describeSectorTypeText
import service.hint.helpers.formatSectorName
import service.hint.techniques.generateFinnedFishSteps
import service.hint.techniques.generateFishSteps
import kotlin.test.assertEquals

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

    // --- Phase 4: fish & finned text accuracy (gaps 4, 5, 9-fin) ---

    @Test
    fun `phase4 a mixed base reads as rows and boxes not the first sector only`() {
        // A Franken fish's base is a line + a box; the type text must reflect the
        // whole set, not just its first sector (gap 5b).
        assertEquals("rows and boxes", describeSectorTypeText(listOf(0, 18)))
        assertEquals("columns", describeSectorTypeText(listOf(9, 14)))
        assertEquals("rows", describeSectorTypeText(listOf(0, 5)))
        assertEquals("lines", describeSectorTypeText(emptyList()))
    }

    @Test
    fun `phase4 sector names use each sector's real type`() {
        assertEquals("Row 3", formatSectorName(2))
        assertEquals("Column 5", formatSectorName(13))
        assertEquals("Box 8", formatSectorName(25))
    }

    @Test
    fun `phase4 finned s1 suppresses the fin-is-at clause when fins cannot be extracted`() {
        // A non-fish match has no baseSecs/coverSecs/fins fields, so the finned
        // generator's reflection fails -> the fin list is empty -> the "fin is at:"
        // clause must be suppressed (gap 9), never rendered dangling.
        LanguageConfig.setLanguage("en")
        val steps = generateFinnedFishSteps("Finned X-Wing", dummyMatch, dummyEliminations)
        val s1 = steps.first { it.stepNumber == 1 }
        assertTrue(s1.description.contains("descriptionNoFin"),
            "expected the no-fin s1 variant when the fin list is empty, got: ${s1.description}")
        val rendered = HintStringInterpolation.interpolate(s1.description)
        assertTrue(!rendered.contains("fin is at"),
            "empty fin list must not render a dangling 'fin is at:' clause: $rendered")
        assertTrue(!isMissingMarker(rendered),
            "finned no-fin s1 rendered as a missing-key marker: $rendered")
    }

    @Test
    fun `phase4 reworded fish s1 renders without the missing-key marker`() {
        LanguageConfig.setLanguage("en")
        val steps = generateFishSteps("X-Wing", dummyMatch, dummyEliminations)
        val s1 = steps.first { it.stepNumber == 1 }
        val rendered = HintStringInterpolation.interpolate(s1.description)
        assertTrue(!isMissingMarker(rendered),
            "reworded x-wing s1 rendered as a missing-key marker: $rendered")
        assertTrue(rendered.contains("digit 7"), "x-wing s1 should name the digit: $rendered")
    }

    @Test
    fun `phase4 finned and sashimi s2 no longer claim eliminations are inside the fin box`() {
        // gap 4: the old text said eliminations are "restricted to {{boxName}}
        // (where the fin is)" — wrong. The corrected text must reference cells that
        // SEE the fin (and lie in a cover line).
        LanguageConfig.setLanguage("en")
        for (name in listOf("Finned X-Wing", "Sashimi X-Wing")) {
            val steps = generateFinnedFishSteps(name, dummyMatch, dummyEliminations)
            val s2 = steps.first { it.stepNumber == 2 }
            val rendered = HintStringInterpolation.interpolate(s2.description)
            assertTrue(rendered.contains("see the fin"),
                "[$name] finned s2 must reference cells that see the fin: $rendered")
            assertTrue(!rendered.contains("where the fin is"),
                "[$name] finned s2 must not claim eliminations are inside the fin's box: $rendered")
        }
    }

    @Test
    fun `phase4 reworded fish s1 and finned no-fin keys resolve under en and a non-en locale`() {
        val keys = listOf(
            "{{hints.x_wing.step1.description|baseNames=Row 2 and Row 7|digit=5|baseTypeText=rows|coverNames=Column 3 and Column 7|coverTypeText=columns}}",
            "{{hints.swordfish.step1.description|baseNames=Row 1 and Row 5 and Row 9|digit=7|baseTypeText=rows|coverNames=Column 2 and Column 4 and Column 6|coverTypeText=columns}}",
            "{{hints.jellyfish.step1.description|baseNames=Row 1 and Row 2 and Row 3 and Row 4|digit=9|baseTypeText=rows|coverNames=Column 1 and Column 2 and Column 3 and Column 4|coverTypeText=columns}}",
            "{{hints.basic_fish.step1.description|digit=5|baseTypeText=rows and boxes|baseNames=Row 3 and Box 4|coverTypeText=columns|coverNames=Column 2 and Column 5}}",
            "{{hints.finned_fish.step1.descriptionNoFin|fishType=X-Wing|baseNames=Row 2|digit=5|coverNames=Column 3}}",
            "{{hints.finned_fish.step2.description|digit=5}}",
            "{{hints.finned_fish.step3.description|coverTypeText=columns|digit=5|cells=R1C1, R1C9}}",
            "{{hints.sashimi_fish.step1.descriptionNoFin|fishType=X-Wing|baseNames=Row 2|digit=5|coverNames=Column 3}}",
            "{{hints.sashimi_fish.step3.description|coverTypeText=columns|digit=5|cells=R1C1}}"
        )
        for (locale in listOf("en", "de")) {
            for (k in keys) assertResolves(locale, k)
        }
    }
}
